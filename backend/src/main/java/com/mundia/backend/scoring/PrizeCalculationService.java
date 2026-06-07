package com.mundia.backend.scoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Calculates and persists prize_projections for each pool member.
 * Called after scoring recalculation.
 *
 * Categories:
 *   PERFECT_WINNERS — 75% if pleno alive, 0% if extinct
 *   GENERAL         — 10%/40% → player(s) with most total points
 *   INITIAL_BET     — 5%/15%  → player(s) with most INITIAL set points
 *   EXACT_RESULTS   — 5%/20%  → player(s) with most exact results
 *   WINNERS         — 5%/20%  → player(s) with most winner predictions
 *   CHAMPION        — 0%/5%   → player(s) who predicted the champion
 */
@Service
public class PrizeCalculationService {

    private static final Logger log = LoggerFactory.getLogger(PrizeCalculationService.class);
    private final JdbcTemplate jdbc;

    public PrizeCalculationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void recalculate(long poolId) {
        // ── Pot ──────────────────────────────────────────────────────────────
        Integer potCents = jdbc.queryForObject("""
                SELECT COALESCE(SUM(pay.amount_cents), 0)
                FROM payments pay
                JOIN pool_members pm ON pm.id = pay.pool_member_id
                WHERE pm.pool_id = ? AND pay.status = 'CONFIRMED'
                """, Integer.class, poolId);
        if (potCents == null || potCents == 0) return;

        // ── Pleno alive? ──────────────────────────────────────────────────────
        // A player is alive if they have no wrong WINNER prediction on any closed match
        boolean plenoAlive = !jdbc.query("""
                SELECT pm.id FROM pool_members pm
                WHERE pm.pool_id = ? AND pm.role = 'PLAYER'
                  AND NOT EXISTS (
                    SELECT 1 FROM matches m
                    JOIN rounds r ON r.id = m.round_id
                    WHERE m.home_goals IS NOT NULL
                      AND m.result_source IN ('WC26_IR','SIM','API_FOOTBALL')
                      AND NOT EXISTS (
                        SELECT 1 FROM match_predictions mp
                        JOIN prediction_sets ps ON ps.id = mp.prediction_set_id
                        WHERE ps.pool_member_id = pm.id AND ps.type = 'LIVE'
                          AND mp.match_id = m.id
                          AND SIGN(mp.home_goals - mp.away_goals) = SIGN(m.home_goals - m.away_goals)
                      )
                  )
                """, (rs, i) -> rs.getLong("id"), poolId).isEmpty();

        // ── Prize rules ───────────────────────────────────────────────────────
        record Rule(String category, int pctAlive, int pctExtinct) {}
        List<Rule> rules = jdbc.query("""
                SELECT category,
                       ROUND(percentage_when_perfect_alive)   pct_alive,
                       ROUND(percentage_when_perfect_extinct) pct_extinct
                FROM prize_rules WHERE pool_id = ? AND enabled = TRUE
                """, (rs, i) -> new Rule(
                rs.getString("category"),
                rs.getInt("pct_alive"),
                rs.getInt("pct_extinct")), poolId);

        // ── Member list ───────────────────────────────────────────────────────
        List<Long> members = jdbc.query("""
                SELECT id FROM pool_members WHERE pool_id = ? AND role = 'PLAYER'
                """, (rs, i) -> rs.getLong("id"), poolId);

        // ── Clear previous projections ────────────────────────────────────────
        jdbc.update("""
                DELETE pp FROM prize_projections pp
                JOIN pool_members pm ON pm.id = pp.pool_member_id
                WHERE pm.pool_id = ?
                """, poolId);

        // ── Calculate each category ───────────────────────────────────────────
        for (Rule rule : rules) {
            int pct = plenoAlive ? rule.pctAlive() : rule.pctExtinct();
            int categoryPot = Math.round(potCents * pct / 100.0f);

            List<Long> winners = switch (rule.category()) {
                case "PERFECT_WINNERS" -> plenoAlive ? findPlenoSurvivors(poolId) : List.of();
                case "GENERAL"         -> findTopByPoints(poolId, "LIVE");
                case "INITIAL_BET"     -> findTopByPoints(poolId, "INITIAL");
                case "EXACT_RESULTS"   -> findTopByCategory(poolId, "EXACT_RESULT");
                case "WINNERS"         -> findTopByCategory(poolId, "WINNER");
                case "CHAMPION"        -> findChampionPredictors(poolId);
                default                -> List.of();
            };

            int share = winners.isEmpty() ? 0 : Math.round(categoryPot / (float) winners.size());
            String status = winners.isEmpty() ? "PENDING"
                    : categoryPot == 0 ? "EXTINCT"
                    : "AWARDED";

            for (long memberId : members) {
                int amount = winners.contains(memberId) ? share : 0;
                jdbc.update("""
                        INSERT INTO prize_projections
                          (pool_member_id, category, current_amount_cents, max_possible_amount_cents, status)
                        VALUES (?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          current_amount_cents = VALUES(current_amount_cents),
                          max_possible_amount_cents = VALUES(max_possible_amount_cents),
                          status = VALUES(status),
                          calculated_at = CURRENT_TIMESTAMP(6)
                        """, memberId, rule.category(), amount, categoryPot, status);
            }
        }

        log.info("[Prizes] Recalculated for pool {} (pot={}c, plenoAlive={})", poolId, potCents, plenoAlive);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Long> findPlenoSurvivors(long poolId) {
        return jdbc.query("""
                SELECT pm.id FROM pool_members pm
                WHERE pm.pool_id = ? AND pm.role = 'PLAYER'
                  AND NOT EXISTS (
                    SELECT 1 FROM matches m
                    WHERE m.home_goals IS NOT NULL
                      AND m.result_source IN ('WC26_IR','SIM','API_FOOTBALL')
                      AND NOT EXISTS (
                        SELECT 1 FROM match_predictions mp
                        JOIN prediction_sets ps ON ps.id = mp.prediction_set_id
                        WHERE ps.pool_member_id = pm.id AND ps.type = 'LIVE'
                          AND mp.match_id = m.id
                          AND SIGN(mp.home_goals - mp.away_goals) = SIGN(m.home_goals - m.away_goals)
                      )
                  )
                """, (rs, i) -> rs.getLong("id"), poolId);
    }

    private List<Long> findTopByPoints(long poolId, String predType) {
        return jdbc.query("""
                SELECT pm.id FROM pool_members pm
                LEFT JOIN prediction_sets ps ON ps.pool_member_id = pm.id AND ps.type = ?
                LEFT JOIN score_breakdowns sb ON sb.prediction_set_id = ps.id
                WHERE pm.pool_id = ? AND pm.role = 'PLAYER'
                GROUP BY pm.id
                HAVING SUM(COALESCE(sb.points, 0)) = (
                  SELECT MAX(total) FROM (
                    SELECT SUM(COALESCE(sb2.points, 0)) total
                    FROM pool_members pm2
                    LEFT JOIN prediction_sets ps2 ON ps2.pool_member_id = pm2.id AND ps2.type = ?
                    LEFT JOIN score_breakdowns sb2 ON sb2.prediction_set_id = ps2.id
                    WHERE pm2.pool_id = ? AND pm2.role = 'PLAYER'
                    GROUP BY pm2.id
                  ) totals
                )
                """, (rs, i) -> rs.getLong("id"), predType, poolId, predType, poolId);
    }

    private List<Long> findTopByCategory(long poolId, String category) {
        return jdbc.query("""
                SELECT pm.id FROM pool_members pm
                LEFT JOIN prediction_sets ps ON ps.pool_member_id = pm.id AND ps.type = 'LIVE'
                LEFT JOIN score_breakdowns sb ON sb.prediction_set_id = ps.id AND sb.category = ?
                WHERE pm.pool_id = ? AND pm.role = 'PLAYER'
                GROUP BY pm.id
                HAVING COUNT(sb.id) = (
                  SELECT MAX(cnt) FROM (
                    SELECT COUNT(sb2.id) cnt
                    FROM pool_members pm2
                    LEFT JOIN prediction_sets ps2 ON ps2.pool_member_id = pm2.id AND ps2.type = 'LIVE'
                    LEFT JOIN score_breakdowns sb2 ON sb2.prediction_set_id = ps2.id AND sb2.category = ?
                    WHERE pm2.pool_id = ? AND pm2.role = 'PLAYER'
                    GROUP BY pm2.id
                  ) counts
                )
                """, (rs, i) -> rs.getLong("id"), category, poolId, category, poolId);
    }

    private List<Long> findChampionPredictors(long poolId) {
        // Find the team that won the Final
        List<Long> champions = jdbc.query("""
                SELECT CASE WHEN m.home_goals > m.away_goals THEN m.home_team_id
                            ELSE m.away_team_id END champion_id
                FROM matches m
                JOIN rounds r ON r.id = m.round_id
                WHERE r.name = 'Final'
                  AND m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL
                LIMIT 1
                """, (rs, i) -> rs.getLong("champion_id"));

        if (champions.isEmpty()) return List.of();
        long championId = champions.get(0);

        // Find players who predicted this team to win the Final
        return jdbc.query("""
                SELECT pm.id FROM pool_members pm
                JOIN prediction_sets ps ON ps.pool_member_id = pm.id AND ps.type = 'LIVE'
                JOIN match_predictions mp ON mp.prediction_set_id = ps.id
                JOIN matches m ON m.id = mp.match_id
                JOIN rounds r ON r.id = m.round_id
                WHERE pm.pool_id = ? AND pm.role = 'PLAYER'
                  AND r.name = 'Final'
                  AND (
                    (m.home_team_id = ? AND mp.home_goals > mp.away_goals)
                    OR
                    (m.away_team_id = ? AND mp.away_goals > mp.home_goals)
                  )
                """, (rs, i) -> rs.getLong("id"), poolId, championId, championId);
    }
}
