package com.mundia.backend.simulator;

import com.mundia.backend.scoring.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Random;

@Service
public class SimulatorService {

    private static final Logger log = LoggerFactory.getLogger(SimulatorService.class);
    private static final Random RNG = new Random();

    private final JdbcTemplate jdbc;
    private final ScoringService scoring;

    public SimulatorService(JdbcTemplate jdbc, ScoringService scoring) {
        this.jdbc    = jdbc;
        this.scoring = scoring;
    }

    public record SimStatus(
            int simDay,
            String currentDate,
            int totalDays,
            int simUsers,
            List<SimDayInfo> days
    ) {}

    public record SimDayInfo(int day, String date, int matches, boolean done) {}

    public record SimDayResult(int day, String date, int matchesSimulated) {}

    public SimStatus getStatus(long poolId) {
        int simDay = getSimDay(poolId);

        List<SimDayInfo> days = jdbc.query("""
                SELECT DATE(kickoff_at) d, COUNT(*) matches,
                       SUM(CASE WHEN home_goals IS NOT NULL THEN 1 ELSE 0 END) done_count
                FROM matches
                WHERE result_source IN ('WC26_IR','SIM') AND kickoff_at IS NOT NULL
                GROUP BY DATE(kickoff_at)
                ORDER BY d
                """, (rs, i) -> new SimDayInfo(
                i + 1,
                rs.getString("d"),
                rs.getInt("matches"),
                rs.getInt("done_count") == rs.getInt("matches")
        ));

        int simUsers = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pool_members pm
                JOIN users u ON u.id = pm.user_id
                WHERE pm.pool_id = ? AND u.is_sim = TRUE
                """, Integer.class, poolId);

        String currentDate = simDay > 0 && simDay <= days.size()
                ? days.get(simDay - 1).date() : null;

        return new SimStatus(simDay, currentDate, days.size(), simUsers, days);
    }

    @Transactional
    public int createSimUsers(long poolId, int count) {
        String[] names = {
            "Pepito", "Manolita", "Curro", "Rocio", "Paquito",
            "Lola", "Fali", "Trini", "Pepe", "Carmen",
            "Juanito", "Marisol", "Tio Paco", "La Abuela", "El Primo"
        };

        int created = 0;
        for (int i = 0; i < count; i++) {
            String name  = names[i % names.length] + " (sim)";
            String email = "sim_user_" + i + "_" + poolId + "@mundia.test";

            jdbc.update("""
                    INSERT INTO users (email, display_name, is_sim)
                    VALUES (?, ?, TRUE)
                    ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
                    """, email, name);

            Long userId = jdbc.queryForObject(
                    "SELECT id FROM users WHERE email = ?", Long.class, email);
            if (userId == null) continue;

            int exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pool_members WHERE pool_id = ? AND user_id = ?",
                    Integer.class, poolId, userId);
            if (exists == 0) {
                jdbc.update("""
                        INSERT INTO pool_members (pool_id, user_id, role, status, joined_at)
                        VALUES (?, ?, 'PLAYER', 'ACTIVE', CURRENT_TIMESTAMP(6))
                        """, poolId, userId);
            }

            Long memberId = jdbc.queryForObject(
                    "SELECT id FROM pool_members WHERE pool_id = ? AND user_id = ?",
                    Long.class, poolId, userId);
            if (memberId == null) continue;

            // Create confirmed payment (sim users are always "paid")
            int payExists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE pool_member_id = ?",
                    Integer.class, memberId);
            if (payExists == 0) {
                Integer entryFee = jdbc.queryForObject(
                        "SELECT entry_fee_cents FROM pools WHERE id = ?", Integer.class, poolId);
                jdbc.update("""
                        INSERT INTO payments (pool_member_id, amount_cents, method, status, notes)
                        VALUES (?, ?, 'SIM', 'CONFIRMED', 'Pago simulado')
                        """, memberId, entryFee != null ? entryFee : 1000);
            }

            for (String type : new String[]{"LIVE", "INITIAL"}) {
                int setExists = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM prediction_sets WHERE pool_member_id = ? AND type = ?",
                        Integer.class, memberId, type);
                if (setExists == 0) {
                    jdbc.update("""
                            INSERT INTO prediction_sets (pool_member_id, type, status)
                            VALUES (?, ?, 'DRAFT')
                            """, memberId, type);
                }
            }

            Long liveSetId = jdbc.queryForObject(
                    "SELECT id FROM prediction_sets WHERE pool_member_id = ? AND type = 'LIVE'",
                    Long.class, memberId);
            Long initialSetId = jdbc.queryForObject(
                    "SELECT id FROM prediction_sets WHERE pool_member_id = ? AND type = 'INITIAL'",
                    Long.class, memberId);
            if (liveSetId == null || initialSetId == null) continue;

            List<Long> matchIds = jdbc.query(
                    "SELECT id FROM matches WHERE result_source = 'WC26_IR'",
                    (rs, idx) -> rs.getLong("id"));

            for (long matchId : matchIds) {
                int h = randomGoals();
                int a = randomGoals();
                jdbc.update("""
                        INSERT INTO match_predictions (prediction_set_id, match_id, home_goals, away_goals)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE home_goals = VALUES(home_goals), away_goals = VALUES(away_goals)
                        """, liveSetId, matchId, h, a);
                jdbc.update("""
                        INSERT INTO match_predictions (prediction_set_id, match_id, home_goals, away_goals)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE home_goals = VALUES(home_goals), away_goals = VALUES(away_goals)
                        """, initialSetId, matchId, h, a);
            }

            created++;
        }

        log.info("[Sim] Created {} sim users for pool {}", created, poolId);
        return created;
    }

    @Transactional
    public SimDayResult advanceDay(long poolId) {
        int simDay = getSimDay(poolId);

        List<String> days = jdbc.query("""
                SELECT DISTINCT DATE(kickoff_at) d
                FROM matches
                WHERE result_source IN ('WC26_IR','SIM') AND kickoff_at IS NOT NULL
                ORDER BY d
                """, (rs, i) -> rs.getString("d"));

        if (simDay >= days.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya se han simulado todos los dias disponibles");
        }

        String targetDate = days.get(simDay);

        List<Long> matchIds = jdbc.query("""
                SELECT id FROM matches
                WHERE result_source IN ('WC26_IR','SIM')
                  AND DATE(kickoff_at) = ?
                  AND home_goals IS NULL
                """, (rs, i) -> rs.getLong("id"), targetDate);

        for (long matchId : matchIds) {
            int h = randomGoals();
            int a = randomGoals();
            jdbc.update("""
                    UPDATE matches SET home_goals = ?, away_goals = ?, status = 'FINISHED', result_source = 'SIM'
                    WHERE id = ?
                    """, h, a, matchId);
        }

        scoring.recalculateAll();
        setSimDay(poolId, simDay + 1);

        log.info("[Sim] Pool {} advanced to day {} ({}) - {} matches simulated",
                poolId, simDay + 1, targetDate, matchIds.size());

        return new SimDayResult(simDay + 1, targetDate, matchIds.size());
    }

    @Transactional
    public void resetSimulation(long poolId) {
        log.info("[Sim] Resetting simulation for pool {}", poolId);

        // 0. Delete prize_projections for ALL members of this pool (recalculated later)
        jdbc.update("""
                DELETE pp FROM prize_projections pp
                JOIN pool_members pm ON pm.id = pp.pool_member_id
                WHERE pm.pool_id = ?
                """, poolId);

        // 1. Delete payments for sim users
        jdbc.update("""
                DELETE pay FROM payments pay
                JOIN pool_members pm ON pm.id = pay.pool_member_id
                JOIN users u         ON u.id  = pm.user_id
                WHERE pm.pool_id = ? AND u.is_sim = TRUE
                """, poolId);

        // 2. Delete ALL score_breakdowns and match_predictions referencing SIM or NONE matches
        //    (any user, including real admin who may have predicted knockout games)
        jdbc.update("""
                DELETE sb FROM score_breakdowns sb
                JOIN matches m ON m.id = sb.match_id
                WHERE m.result_source IN ('SIM', 'NONE')
                """);
        jdbc.update("""
                DELETE mp FROM match_predictions mp
                JOIN matches m ON m.id = mp.match_id
                WHERE m.result_source IN ('SIM', 'NONE')
                """);

        // 3. Delete match_predictions for sim users (any match)
        jdbc.update("""
                DELETE mp FROM match_predictions mp
                JOIN prediction_sets ps ON ps.id = mp.prediction_set_id
                JOIN pool_members pm    ON pm.id = ps.pool_member_id
                JOIN users u            ON u.id  = pm.user_id
                WHERE pm.pool_id = ? AND u.is_sim = TRUE
                """, poolId);

        // 4. Delete score_breakdowns for sim users
        jdbc.update("""
                DELETE sb FROM score_breakdowns sb
                JOIN pool_members pm ON pm.id = sb.pool_member_id
                JOIN users u         ON u.id  = pm.user_id
                WHERE pm.pool_id = ? AND u.is_sim = TRUE
                """, poolId);

        // 5. Delete prediction_sets for sim users
        jdbc.update("""
                DELETE ps FROM prediction_sets ps
                JOIN pool_members pm ON pm.id = ps.pool_member_id
                JOIN users u         ON u.id  = pm.user_id
                WHERE pm.pool_id = ? AND u.is_sim = TRUE
                """, poolId);

        // 6. Delete pool_members for sim users
        jdbc.update("""
                DELETE pm FROM pool_members pm
                JOIN users u ON u.id = pm.user_id
                WHERE pm.pool_id = ? AND u.is_sim = TRUE
                """, poolId);

        // 7. Delete orphan sim users
        jdbc.update("DELETE FROM users WHERE is_sim = TRUE");

        // 8. Only touch WC26_IR match data if no real finished matches exist yet
        //    (once the tournament is live, real results must not be wiped by a sim reset)
        Integer finishedReal = jdbc.queryForObject(
                "SELECT COUNT(*) FROM matches WHERE result_source = 'WC26_IR' AND status IN ('FINISHED','CLOSED')",
                Integer.class);
        if (finishedReal == null || finishedReal == 0) {
            // Pre-tournament: safe to wipe test/preview goals and their scoring
            jdbc.update("""
                    DELETE sb FROM score_breakdowns sb
                    JOIN matches m ON m.id = sb.match_id
                    WHERE m.result_source = 'WC26_IR'
                    """);
            jdbc.update("""
                    UPDATE matches SET home_goals = NULL, away_goals = NULL, status = 'OPEN'
                    WHERE result_source = 'WC26_IR'
                    """);
        } else {
            log.info("[Sim] Tournament already live ({} finished matches) — skipping WC26_IR data wipe", finishedReal);
        }

        // 9. Restore group-stage matches that were simulated (SIM) back to WC26_IR + OPEN
        //    (these are real WC matches that had simulation results applied)
        jdbc.update("""
                UPDATE matches m
                JOIN rounds r ON r.id = m.round_id
                SET m.home_goals = NULL, m.away_goals = NULL, m.status = 'OPEN', m.result_source = 'WC26_IR'
                WHERE m.result_source = 'SIM' AND r.stage = 'GROUP_STAGE'
                """);

        // 10a. Knockout rows that came from a real sync but were overwritten by the sim:
        //      restore them as WC26_IR shells (keep teams/kickoff, wipe results)
        jdbc.update("""
                UPDATE matches m
                JOIN rounds r ON r.id = m.round_id
                SET m.home_goals = NULL, m.away_goals = NULL,
                    m.status = 'OPEN', m.status_short = 'NS', m.result_source = 'WC26_IR'
                WHERE r.stage = 'KNOCKOUT' AND m.result_source = 'SIM'
                  AND m.external_fixture_id IS NOT NULL
                """);

        // 10b. Third place is not part of the 31-match canonical bracket: drop sim-created rows
        jdbc.update("""
                DELETE m FROM matches m
                JOIN rounds r ON r.id = m.round_id
                WHERE r.name = 'Third place' AND m.result_source IN ('SIM','NONE')
                  AND m.external_fixture_id IS NULL
                """);

        // 10c. Revert every remaining sim-only knockout row to an empty Pendiente placeholder.
        //      Includes 'NONE' rows so no residual results can survive a reset.
        //      Match ids are preserved (canonical knockout model).
        jdbc.update("""
                UPDATE matches m
                JOIN rounds r ON r.id = m.round_id
                JOIN teams t  ON t.name = 'Pendiente'
                SET m.home_team_id = t.id, m.away_team_id = t.id,
                    m.home_goals = NULL, m.away_goals = NULL, m.kickoff_at = NULL,
                    m.status = 'OPEN', m.status_short = 'NS', m.result_source = 'NONE'
                WHERE r.stage = 'KNOCKOUT' AND m.result_source IN ('SIM','NONE')
                  AND m.external_fixture_id IS NULL
                """);

        // 10d. Any leftover SIM matches outside knockout rounds (defensive)
        jdbc.update("DELETE FROM matches WHERE result_source = 'SIM'");

        setSimDay(poolId, 0);

        log.info("[Sim] Reset complete for pool {}", poolId);
    }

    private int getSimDay(long poolId) {
        List<Integer> rows = jdbc.query(
                "SELECT sim_day FROM sim_state WHERE pool_id = ?",
                (rs, i) -> rs.getInt("sim_day"), poolId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private void setSimDay(long poolId, int day) {
        jdbc.update("""
                INSERT INTO sim_state (pool_id, sim_day) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE sim_day = VALUES(sim_day)
                """, poolId, day);
    }

    // ─── Full tournament simulation ──────────────────────────────────────────

    @Transactional
    public String simulateFullTournament(long poolId) {
        StringBuilder log = new StringBuilder();

        // 1. Advance all group stage days
        List<String> groupDays = jdbc.query("""
                SELECT DISTINCT DATE(kickoff_at) d FROM matches
                WHERE result_source = 'WC26_IR' AND kickoff_at IS NOT NULL
                  AND home_goals IS NULL
                ORDER BY d
                """, (rs, i) -> rs.getString("d"));

        for (String date : groupDays) {
            List<Long> ids = jdbc.query("""
                    SELECT id FROM matches
                    WHERE result_source IN ('WC26_IR','SIM') AND DATE(kickoff_at) = ? AND home_goals IS NULL
                    """, (rs, i) -> rs.getLong("id"), date);
            for (long id : ids) {
                int h = randomGoals(); int a = randomGoals();
                jdbc.update("UPDATE matches SET home_goals=?, away_goals=?, status='FINISHED', result_source='SIM' WHERE id=?", h, a, id);
            }
            setSimDay(poolId, getSimDay(poolId) + 1);
            log.append("Grupos ").append(date).append(": ").append(ids.size()).append(" partidos\n");
        }
        scoring.recalculateAll();

        // 2. Round of 32
        generateRoundOf32(poolId);
        log.append("Round of 32 generado\n");
        simulateRound("Round of 32", poolId, log);

        // 3. Round of 16
        advanceKnockoutRound(poolId, "Round of 32", "Round of 16", 11, new String[]{
            "2026-07-10 18:00","2026-07-10 21:00","2026-07-11 18:00","2026-07-11 21:00",
            "2026-07-12 18:00","2026-07-12 21:00","2026-07-13 18:00","2026-07-13 21:00"});
        log.append("Round of 16 generado\n");
        simulateRound("Round of 16", poolId, log);

        // 4. Quarter-finals
        advanceKnockoutRound(poolId, "Round of 16", "Quarter-finals", 12, new String[]{
            "2026-07-17 18:00","2026-07-17 21:00","2026-07-18 18:00","2026-07-18 21:00"});
        log.append("Quarter-finals generado\n");
        simulateRound("Quarter-finals", poolId, log);

        // 5. Semi-finals
        advanceKnockoutRound(poolId, "Quarter-finals", "Semi-finals", 13, new String[]{
            "2026-07-21 21:00","2026-07-22 21:00"});
        log.append("Semi-finals generado\n");
        simulateRound("Semi-finals", poolId, log);

        // 6. 3rd place + Final
        advanceKnockoutRound(poolId, "Semi-finals", "Third place", 14, new String[]{"2026-07-25 18:00"});
        advanceKnockoutRound(poolId, "Semi-finals", "Final", 15, new String[]{"2026-07-26 21:00"});
        log.append("3rd place y Final generados\n");
        simulateRound("Third place", poolId, log);
        simulateRound("Final", poolId, log);

        scoring.recalculateAll();
        log.append("✅ Torneo completado");
        this.log.info("[Sim] Full tournament simulation complete for pool {}", poolId);
        return log.toString();
    }

    private void simulateRound(String roundName, long poolId, StringBuilder log) {
        int n = playRound(roundName);
        log.append(roundName).append(": ").append(n).append(" partidos\n");
    }

    /** Plays (assigns random results to) all unplayed SIM matches of a round. Returns count. */
    @Transactional
    public int playRound(String roundName) {
        // Only SIM matches: NONE placeholders must never receive results
        // (that was the source of residual scores after a reset)
        List<Long> ids = jdbc.query("""
                SELECT m.id FROM matches m
                JOIN rounds r ON r.id = m.round_id
                WHERE r.name = ? AND m.home_goals IS NULL AND m.result_source = 'SIM'
                """, (rs, i) -> rs.getLong("id"), roundName);
        for (long id : ids) {
            int h = randomGoals(); int a = randomGoals();
            // In knockout, avoid draw — winner is home if h==a
            if (h == a) h++;
            jdbc.update("UPDATE matches SET home_goals=?, away_goals=?, status='FINISHED' WHERE id=?", h, a, id);
        }
        scoring.recalculateAll();
        return ids.size();
    }

    // ─── Knockout bracket generation ─────────────────────────────────────────

    public record KnockoutGenResult(String roundName, int matchesCreated) {}

    @Transactional
    public KnockoutGenResult generateRoundOf32(long poolId) {
        // Compute top 2 from each group + 8 best 3rds
        List<Long> classified = computeClassified();
        if (classified.size() < 32) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Fase de grupos no completada. Partidos pendientes de resultado.");
        }

        // Official WC2026 Round of 32 pairing: 1A vs 2B, 1B vs 2A... (simplified sequential)
        String roundName = "Round of 32";
        long roundId = upsertSimRound(roundName, "KNOCKOUT", 10);

        // Dates: July 1-4, 2026 (approximate)
        String[] dates = {
            "2026-07-01 18:00:00", "2026-07-01 21:00:00", "2026-07-02 18:00:00", "2026-07-02 21:00:00",
            "2026-07-03 18:00:00", "2026-07-03 21:00:00", "2026-07-04 18:00:00", "2026-07-04 21:00:00",
            "2026-07-05 18:00:00", "2026-07-05 21:00:00", "2026-07-06 18:00:00", "2026-07-06 21:00:00",
            "2026-07-07 18:00:00", "2026-07-07 21:00:00", "2026-07-08 18:00:00", "2026-07-08 21:00:00",
        };

        int created = 0;
        for (int i = 0; i < 16; i++) {
            long homeId = classified.get(i * 2);
            long awayId = classified.get(i * 2 + 1);
            long matchId = fillKnockoutSlot(roundId, i, homeId, awayId, dates[i]);
            generateSimPredictions(matchId, poolId);
            created++;
        }

        log.info("[Sim] Generated Round of 32 with {} matches", created);
        return new KnockoutGenResult(roundName, created);
    }

    @Transactional
    public KnockoutGenResult advanceKnockoutRound(long poolId, String currentRound, String nextRound, int sortOrder, String[] dates) {
        // Third place is fed by the LOSERS of the semi-finals, every other round by the winners
        boolean thirdPlace = "Third place".equals(nextRound);
        String advancerExpr = thirdPlace
                ? "CASE WHEN m.home_goals >= m.away_goals THEN m.away_team_id ELSE m.home_team_id END"
                : "CASE WHEN m.home_goals >= m.away_goals THEN m.home_team_id ELSE m.away_team_id END";
        List<Long> advancers = jdbc.query("""
                SELECT %s advancer_id
                FROM matches m
                JOIN rounds r ON r.id = m.round_id
                WHERE r.name = ? AND m.result_source = 'SIM'
                  AND m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL
                ORDER BY m.kickoff_at, m.id
                """.formatted(advancerExpr), (rs, i) -> rs.getLong("advancer_id"), currentRound);

        if (advancers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ronda " + currentRound + " no completada");
        }

        long roundId = upsertSimRound(nextRound, "KNOCKOUT", sortOrder);
        int created = 0;
        for (int i = 0; i < advancers.size() / 2; i++) {
            long matchId = fillKnockoutSlot(roundId, i, advancers.get(i * 2), advancers.get(i * 2 + 1), dates[i]);
            generateSimPredictions(matchId, poolId);
            created++;
        }

        log.info("[Sim] Generated {} with {} matches", nextRound, created);
        return new KnockoutGenResult(nextRound, created);
    }

    private long upsertSimRound(String name, String stage, int sortOrder) {
        jdbc.update("""
                INSERT INTO rounds (name, stage, sort_order, external_name)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE stage = VALUES(stage)
                """, name, stage, sortOrder, name);
        Long id = jdbc.queryForObject("SELECT id FROM rounds WHERE name = ?", Long.class, name);
        if (id == null) throw new IllegalStateException("Failed to upsert round: " + name);
        return id;
    }

    /**
     * Fills the nth placeholder slot of a knockout round with real teams, keeping the
     * match id stable so existing user predictions survive. Falls back to INSERT only
     * when the round has no free slot (e.g. Third place, outside the 31-match bracket).
     */
    private long fillKnockoutSlot(long roundId, int slotIndex, long homeId, long awayId, String kickoff) {
        List<Long> slots = jdbc.query(
                "SELECT id FROM matches WHERE round_id = ? ORDER BY id",
                (rs, i) -> rs.getLong("id"), roundId);
        if (slotIndex < slots.size()) {
            long matchId = slots.get(slotIndex);
            jdbc.update("""
                    UPDATE matches
                    SET home_team_id = ?, away_team_id = ?, kickoff_at = ?,
                        status = 'OPEN', status_short = 'NS',
                        home_goals = NULL, away_goals = NULL, result_source = 'SIM'
                    WHERE id = ?
                    """, homeId, awayId, kickoff, matchId);
            return matchId;
        }
        jdbc.update("""
                INSERT INTO matches (round_id, home_team_id, away_team_id, kickoff_at, status, status_short, result_source)
                VALUES (?, ?, ?, ?, 'OPEN', 'NS', 'SIM')
                """, roundId, homeId, awayId, kickoff);
        Long id = jdbc.queryForObject(
                "SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) throw new IllegalStateException("Failed to insert match");
        return id;
    }

    private void generateSimPredictions(long matchId, long poolId) {
        // For all sim users in this pool
        List<Long> setIds = jdbc.query("""
                SELECT ps.id FROM prediction_sets ps
                JOIN pool_members pm ON pm.id = ps.pool_member_id
                JOIN users u         ON u.id  = pm.user_id
                WHERE pm.pool_id = ? AND u.is_sim = TRUE AND ps.type IN ('LIVE','INITIAL')
                """, (rs, i) -> rs.getLong("id"), poolId);

        for (long setId : setIds) {
            int h = randomGoals();
            int a = randomGoals();
            jdbc.update("""
                    INSERT INTO match_predictions (prediction_set_id, match_id, home_goals, away_goals)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE home_goals = VALUES(home_goals), away_goals = VALUES(away_goals)
                    """, setId, matchId, h, a);
        }
    }

    private List<Long> computeClassified() {
        // Compute standings per group
        String standingsQuery = """
                SELECT team_id, round_name,
                       SUM(pts) pts, SUM(gd) gd, SUM(gf) gf,
                       ROW_NUMBER() OVER (PARTITION BY round_name ORDER BY SUM(pts) DESC, SUM(gd) DESC, SUM(gf) DESC) rn
                FROM (
                  SELECT ht.id team_id, r.name round_name,
                    CASE WHEN m.home_goals > m.away_goals THEN 3 WHEN m.home_goals = m.away_goals THEN 1 ELSE 0 END pts,
                    (m.home_goals - m.away_goals) gd, m.home_goals gf
                  FROM matches m JOIN rounds r ON r.id = m.round_id AND r.stage = 'GROUP_STAGE'
                  JOIN teams ht ON ht.id = m.home_team_id
                  WHERE m.home_goals IS NOT NULL AND m.result_source IN ('WC26_IR','SIM')
                  UNION ALL
                  SELECT at.id, r.name,
                    CASE WHEN m.away_goals > m.home_goals THEN 3 WHEN m.home_goals = m.away_goals THEN 1 ELSE 0 END,
                    (m.away_goals - m.home_goals), m.away_goals
                  FROM matches m JOIN rounds r ON r.id = m.round_id AND r.stage = 'GROUP_STAGE'
                  JOIN teams at ON at.id = m.away_team_id
                  WHERE m.home_goals IS NOT NULL AND m.result_source IN ('WC26_IR','SIM')
                ) t GROUP BY team_id, round_name
                """;

        // Top 2 from each group (24 teams)
        List<Long> top2 = jdbc.query(
                "SELECT team_id FROM (" + standingsQuery + ") ranked WHERE rn <= 2 ORDER BY round_name, rn",
                (rs, i) -> rs.getLong("team_id"));

        // Best 8 third-place teams
        List<Long> thirds = jdbc.query(
                "SELECT team_id FROM (" + standingsQuery + ") ranked WHERE rn = 3 ORDER BY pts DESC, gd DESC, gf DESC LIMIT 8",
                (rs, i) -> rs.getLong("team_id"));

        List<Long> all = new java.util.ArrayList<>(top2);
        all.addAll(thirds);
        return all;
    }

    private static int randomGoals() {
        int[] weights = {20, 30, 25, 15, 7, 3};
        int total = 0;
        for (int w : weights) total += w;
        int r = RNG.nextInt(total);
        int cum = 0;
        for (int i = 0; i < weights.length; i++) {
            cum += weights[i];
            if (r < cum) return i;
        }
        return 0;
    }
}

