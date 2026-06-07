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

    /** Full pipeline: member check → cutoff check → get/create LIVE set → upsert prediction. */
    public void saveForUser(long userId, long poolId, long matchId, int homeGoals, int awayGoals) {
        long memberId = requirePoolMember(userId, poolId);
        checkCutoff(matchId);
        long setId    = getOrCreatePredictionSet(memberId);
        savePrediction(setId, matchId, homeGoals, awayGoals);
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
                WHERE pm.user_id = ? AND pm.pool_id = ? AND ps.type = 'INITIAL'
                """, Integer.class, userId, poolId);
        return count != null && count > 0;
    }

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
