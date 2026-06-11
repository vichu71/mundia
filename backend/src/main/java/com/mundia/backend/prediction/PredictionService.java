package com.mundia.backend.prediction;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PredictionService {

    private static final String PRED_TYPE   = "LIVE";
    private static final String PRED_STATUS = "DRAFT";

    private final JdbcTemplate jdbc;

    public PredictionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns the pool_member id for this user in the given pool, or throws 403. */
    public long requirePoolMember(long userId, long poolId) {
        List<Long> ids = jdbc.query("""
                SELECT id FROM pool_members
                WHERE user_id = ? AND pool_id = ? AND role IN ('PLAYER','ADMIN')
                """,
                (rs, i) -> rs.getLong("id"),
                userId, poolId);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of pool " + poolId);
        }
        return ids.get(0);
    }

    /** Returns the LIVE prediction_set id for this pool member, creating it if needed. */
    public long getOrCreatePredictionSet(long poolMemberId) {
        List<Long> ids = jdbc.query("""
                SELECT id FROM prediction_sets
                WHERE pool_member_id = ? AND type = ?
                """,
                (rs, i) -> rs.getLong("id"),
                poolMemberId, PRED_TYPE);
        if (!ids.isEmpty()) return ids.get(0);

        jdbc.update("""
                INSERT INTO prediction_sets (pool_member_id, type, status)
                VALUES (?, ?, ?)
                """,
                poolMemberId, PRED_TYPE, PRED_STATUS);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) throw new IllegalStateException("Failed to create prediction_set");
        return id;
    }

    /** Upserts a match prediction. */
    public void savePrediction(long predictionSetId, long matchId, int homeGoals, int awayGoals) {
        jdbc.update("""
                INSERT INTO match_predictions (prediction_set_id, match_id, home_goals, away_goals)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE home_goals = VALUES(home_goals), away_goals = VALUES(away_goals)
                """,
                predictionSetId, matchId, homeGoals, awayGoals);
    }

    /** Full pipeline: member check → cutoff check → group-lock check → get/create LIVE set → upsert prediction. */
    public void saveForUser(long userId, long poolId, long matchId, int homeGoals, int awayGoals) {
        long memberId = requirePoolMember(userId, poolId);
        checkCutoff(matchId);
        checkGroupStageLock(memberId, matchId);
        long setId    = getOrCreatePredictionSet(memberId);
        savePrediction(setId, matchId, homeGoals, awayGoals);
    }

    private void checkGroupStageLock(long memberId, long matchId) {
        String stage = jdbc.query(
                "SELECT r.stage FROM matches m JOIN rounds r ON r.id = m.round_id WHERE m.id = ?",
                (rs, i) -> rs.getString("stage"), matchId)
                .stream().findFirst().orElse(null);
        if (!"GROUP_STAGE".equals(stage)) return;
        Integer submitted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM prediction_sets WHERE pool_member_id = ? AND type = 'INITIAL' AND status = 'SUBMITTED'",
                Integer.class, memberId);
        if (submitted != null && submitted > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Las predicciones de fase de grupos están bloqueadas: ya guardaste tu apuesta inicial");
        }
    }

    /**
     * Saves initial bet prediction. Only allowed before the match kickoff.
     * Creates INITIAL set if it doesn't exist — once saved, individual matches
     * cannot be changed after their cutoff.
     */
    public void saveInitialForUser(long userId, long poolId, long matchId, int homeGoals, int awayGoals) {
        long memberId = requirePoolMember(userId, poolId);
        checkCutoff(matchId);
        long setId = getOrCreateInitialSet(memberId);
        savePrediction(setId, matchId, homeGoals, awayGoals);
    }

    public long getOrCreateInitialSet(long poolMemberId) {
        List<Long> ids = jdbc.query("""
                SELECT id FROM prediction_sets
                WHERE pool_member_id = ? AND type = 'INITIAL'
                """,
                (rs, i) -> rs.getLong("id"),
                poolMemberId);
        if (!ids.isEmpty()) return ids.get(0);

        jdbc.update("""
                INSERT INTO prediction_sets (pool_member_id, type, status)
                VALUES (?, 'INITIAL', 'DRAFT')
                """,
                poolMemberId);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) throw new IllegalStateException("Failed to create initial prediction_set");
        return id;
    }

    public boolean hasInitialBet(long userId, long poolId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM prediction_sets ps
                JOIN pool_members pm ON pm.id = ps.pool_member_id
                WHERE pm.user_id = ? AND pm.pool_id = ? AND ps.type = 'INITIAL' AND ps.status = 'SUBMITTED'
                """, Integer.class, userId, poolId);
        return count != null && count > 0;
    }

    /** Returns closed predictions for a given pool member (matches whose cutoff has passed). */
    public List<ClosedPrediction> getClosedPredictionsForMember(long memberId, long poolId) {
        // Verify the memberId belongs to the poolId
        List<Long> check = jdbc.query(
                "SELECT id FROM pool_members WHERE id = ? AND pool_id = ?",
                (rs, i) -> rs.getLong("id"), memberId, poolId);
        if (check.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Member does not belong to this pool");
        }

        return jdbc.query("""
                SELECT
                  m.id          AS match_id,
                  ht.name       AS home,
                  at.name       AS away,
                  ht.country_code AS home_fl,
                  at.country_code AS away_fl,
                  mp.home_goals AS pred_home,
                  mp.away_goals AS pred_away,
                  m.home_goals  AS real_home,
                  m.away_goals  AS real_away,
                  m.status,
                  m.kickoff_at,
                  r.name        AS round_name,
                  COALESCE(sb.total_points, 0) AS points
                FROM prediction_sets ps
                JOIN match_predictions mp ON mp.prediction_set_id = ps.id
                JOIN matches m            ON m.id = mp.match_id
                JOIN teams ht             ON ht.id = m.home_team_id
                JOIN teams at             ON at.id = m.away_team_id
                JOIN rounds r             ON r.id = m.round_id
                LEFT JOIN (
                  SELECT prediction_set_id, match_id, SUM(points) AS total_points
                  FROM score_breakdowns
                  GROUP BY prediction_set_id, match_id
                ) sb ON sb.prediction_set_id = ps.id AND sb.match_id = m.id
                WHERE ps.pool_member_id = ?
                  AND ps.type = 'LIVE'
                  AND (m.status IN ('LIVE','CLOSED','FINISHED') OR m.kickoff_at <= NOW())
                ORDER BY m.kickoff_at ASC
                """,
                (rs, i) -> {
                    java.sql.Timestamp ts = rs.getTimestamp("kickoff_at");
                    String kickoff = ts != null ? ts.toInstant().toString() : null;
                    return new ClosedPrediction(
                            rs.getLong("match_id"),
                            rs.getString("home"),
                            rs.getString("away"),
                            rs.getString("home_fl"),
                            rs.getString("away_fl"),
                            rs.getInt("pred_home"),
                            rs.getInt("pred_away"),
                            (Integer) rs.getObject("real_home"),
                            (Integer) rs.getObject("real_away"),
                            rs.getString("status"),
                            kickoff,
                            rs.getString("round_name"),
                            rs.getInt("points")
                    );
                }, memberId);
    }

    public record ClosedPrediction(
            long matchId,
            String home, String away, String homeFl, String awayFl,
            Integer predHome, Integer predAway,
            Integer realHome, Integer realAway,
            String status, String kickoff, String roundName, int points
    ) {}

    private static final int CUTOFF_MINUTES = 60;

    private void checkCutoff(long matchId) {
        List<java.sql.Timestamp> kickoffs = jdbc.query(
                "SELECT kickoff_at FROM matches WHERE id = ?",
                (rs, i) -> rs.getTimestamp("kickoff_at"),
                matchId);
        if (kickoffs.isEmpty() || kickoffs.get(0) == null) return; // sin kickoff → permitir
        java.time.Instant cutoff = kickoffs.get(0).toInstant()
                .minus(CUTOFF_MINUTES, java.time.temporal.ChronoUnit.MINUTES);
        if (java.time.Instant.now().isAfter(cutoff)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El plazo para predecir este partido ha cerrado");
        }
    }
}
