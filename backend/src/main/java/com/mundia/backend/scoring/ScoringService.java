package com.mundia.backend.scoring;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ScoringService {

    private final JdbcTemplate jdbc;
    private PrizeCalculationService prizeCalculationService;

    public ScoringService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Autowired
    public void setPrizeCalculationService(PrizeCalculationService p) {
        this.prizeCalculationService = p;
    }

    /**
     * Recalculates score_breakdowns for every CLOSED/FINISHED match that has a real result.
     * Safe to call multiple times - deletes and rewrites rows for each match+prediction_set.
     */
    @Transactional
    public int recalculateAll() {
        List<Long> closedMatchIds = jdbc.query("""
                SELECT id FROM matches
                WHERE home_goals IS NOT NULL AND away_goals IS NOT NULL
                  AND status IN ('CLOSED', 'FINISHED')
                """,
                (rs, i) -> rs.getLong("id"));

        int affected = 0;
        for (long matchId : closedMatchIds) {
            affected += calculateForMatch(matchId);
        }

        // Recalculate prizes for all pools
        try {
            List<Long> poolIds = jdbc.query(
                    "SELECT DISTINCT pool_id FROM pool_members WHERE role IN ('PLAYER','ADMIN')",
                    (rs, i) -> rs.getLong("pool_id"));
            for (long poolId : poolIds) {
                prizeCalculationService.recalculate(poolId);
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(ScoringService.class)
                    .warn("Prize calculation failed: {}", e.getMessage());
        }

        return affected;
    }

    /**
     * Calculates and persists score_breakdowns for a single match.
     * Returns number of prediction rows processed.
     */
    @Transactional
    public int calculateForMatch(long matchId) {
        var results = jdbc.query("""
                SELECT home_goals, away_goals FROM matches
                WHERE id = ? AND home_goals IS NOT NULL AND away_goals IS NOT NULL
                """,
                (rs, i) -> new int[]{ rs.getInt("home_goals"), rs.getInt("away_goals") },
                matchId);

        if (results.isEmpty()) return 0;
        int realHome = results.get(0)[0];
        int realAway = results.get(0)[1];
        int realSign = Integer.signum(realHome - realAway);

        record PredRow(long predSetId, long poolMemberId, int homeGoals, int awayGoals, String type) {}
        List<PredRow> preds = jdbc.query("""
                SELECT mp.prediction_set_id, ps.pool_member_id, mp.home_goals, mp.away_goals, ps.type
                FROM match_predictions mp
                JOIN prediction_sets ps ON ps.id = mp.prediction_set_id
                WHERE mp.match_id = ? AND ps.type IN ('LIVE', 'INITIAL')
                """,
                (rs, i) -> new PredRow(
                        rs.getLong("prediction_set_id"),
                        rs.getLong("pool_member_id"),
                        rs.getInt("home_goals"),
                        rs.getInt("away_goals"),
                        rs.getString("type")),
                matchId);

        for (PredRow pred : preds) {
            jdbc.update("DELETE FROM score_breakdowns WHERE match_id = ? AND prediction_set_id = ?",
                    matchId, pred.predSetId());

            int predSign = Integer.signum(pred.homeGoals() - pred.awayGoals());
            boolean correctWinner = realSign == predSign;
            boolean exactResult   = pred.homeGoals() == realHome && pred.awayGoals() == realAway;
            boolean correctHomeGoals = pred.homeGoals() == realHome;
            boolean correctAwayGoals = pred.awayGoals() == realAway;

            if (correctHomeGoals) {
                jdbc.update("""
                        INSERT INTO score_breakdowns
                          (pool_member_id, prediction_set_id, match_id, category, points, details_json)
                        VALUES (?, ?, ?, 'HOME_GOALS', 1, JSON_OBJECT(
                            'predHome', ?, 'realHome', ?))
                        """,
                        pred.poolMemberId(), pred.predSetId(), matchId,
                        pred.homeGoals(), realHome);
            }

            if (correctAwayGoals) {
                jdbc.update("""
                        INSERT INTO score_breakdowns
                          (pool_member_id, prediction_set_id, match_id, category, points, details_json)
                        VALUES (?, ?, ?, 'AWAY_GOALS', 1, JSON_OBJECT(
                            'predAway', ?, 'realAway', ?))
                        """,
                        pred.poolMemberId(), pred.predSetId(), matchId,
                        pred.awayGoals(), realAway);
            }

            if (!correctWinner) continue;

            jdbc.update("""
                    INSERT INTO score_breakdowns
                      (pool_member_id, prediction_set_id, match_id, category, points, details_json)
                    VALUES (?, ?, ?, 'WINNER', 2, JSON_OBJECT(
                        'predHome', ?, 'predAway', ?, 'realHome', ?, 'realAway', ?))
                    """,
                    pred.poolMemberId(), pred.predSetId(), matchId,
                    pred.homeGoals(), pred.awayGoals(), realHome, realAway);

            if (exactResult) {
                jdbc.update("""
                        INSERT INTO score_breakdowns
                          (pool_member_id, prediction_set_id, match_id, category, points, details_json)
                        VALUES (?, ?, ?, 'EXACT_RESULT', 2, JSON_OBJECT(
                            'predHome', ?, 'predAway', ?, 'realHome', ?, 'realAway', ?))
                        """,
                        pred.poolMemberId(), pred.predSetId(), matchId,
                        pred.homeGoals(), pred.awayGoals(), realHome, realAway);
            }
        }

        return preds.size();
    }
}

