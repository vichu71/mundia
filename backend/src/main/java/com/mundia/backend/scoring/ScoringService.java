package com.mundia.backend.scoring;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ScoringService {

    private final JdbcTemplate jdbc;

    public ScoringService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Recalculates score_breakdowns for every CLOSED/FINISHED match that has a real result.
     * Safe to call multiple times — deletes and rewrites rows for each match+prediction_set.
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
        return affected;
    }

    /**
     * Calculates and persists score_breakdowns for a single match.
     * Returns number of prediction rows processed.
     */
    @Transactional
    public int calculateForMatch(long matchId) {
        // 1. Get real result
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

        // 2. Get all predictions for this match (LIVE prediction sets only)
        record PredRow(long predSetId, long poolMemberId, int homeGoals, int awayGoals) {}
        List<PredRow> preds = jdbc.query("""
                SELECT mp.prediction_set_id, ps.pool_member_id, mp.home_goals, mp.away_goals
                FROM match_predictions mp
                JOIN prediction_sets ps ON ps.id = mp.prediction_set_id
                WHERE mp.match_id = ? AND ps.type = 'LIVE'
                """,
                (rs, i) -> new PredRow(
                        rs.getLong("prediction_set_id"),
                        rs.getLong("pool_member_id"),
                        rs.getInt("home_goals"),
                        rs.getInt("away_goals")),
                matchId);

        for (PredRow pred : preds) {
            // 3. Wipe previous calculation for this match+set
            jdbc.update("DELETE FROM score_breakdowns WHERE match_id = ? AND prediction_set_id = ?",
                    matchId, pred.predSetId());

            int predSign = Integer.signum(pred.homeGoals() - pred.awayGoals());
            boolean correctWinner = realSign == predSign;
            boolean exactResult   = pred.homeGoals() == realHome && pred.awayGoals() == realAway;

            if (!correctWinner) continue;

            // WINNER: +2 for correct winner or draw
            jdbc.update("""
                    INSERT INTO score_breakdowns
                      (pool_member_id, prediction_set_id, match_id, category, points, details_json)
                    VALUES (?, ?, ?, 'WINNER', 2, JSON_OBJECT(
                        'predHome', ?, 'predAway', ?, 'realHome', ?, 'realAway', ?))
                    """,
                    pred.poolMemberId(), pred.predSetId(), matchId,
                    pred.homeGoals(), pred.awayGoals(), realHome, realAway);

            if (exactResult) {
                // EXACT_RESULT: +2 bonus on top of WINNER
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
