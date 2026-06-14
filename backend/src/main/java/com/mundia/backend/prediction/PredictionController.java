package com.mundia.backend.prediction;

import java.util.List;

import com.mundia.backend.prediction.AiPredictionService.PredictionResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    record SavePredictionRequest(long poolId, long matchId, int homeGoals, int awayGoals) {}

    private final AiPredictionService aiService;
    private final PredictionService predictionService;
    private final JdbcTemplate jdbc;

    public PredictionController(AiPredictionService aiService, PredictionService predictionService, JdbcTemplate jdbc) {
        this.aiService = aiService;
        this.predictionService = predictionService;
        this.jdbc = jdbc;
    }

    /** POST /api/predictions/match — save or update a single LIVE match prediction */
    @PostMapping("/match")
    public void saveMatch(JwtAuthenticationToken auth, @RequestBody SavePredictionRequest req) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        predictionService.saveForUser(userId, req.poolId(), req.matchId(), req.homeGoals(), req.awayGoals());
    }

    /** POST /api/predictions/initial — save or update a single INITIAL match prediction */
    @PostMapping("/initial")
    public void saveInitial(JwtAuthenticationToken auth, @RequestBody SavePredictionRequest req) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        predictionService.saveInitialForUser(userId, req.poolId(), req.matchId(), req.homeGoals(), req.awayGoals());
    }

    /** GET /api/predictions/initial/{poolId} — saved initial predictions for this user */
    @GetMapping("/initial/{poolId}")
    public java.util.Map<String, Object> getInitialPreds(JwtAuthenticationToken auth, @PathVariable long poolId) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        long memberId = predictionService.requirePoolMember(userId, poolId);
        var preds = jdbc.query("""
                SELECT mp.match_id, mp.home_goals, mp.away_goals
                FROM match_predictions mp
                JOIN prediction_sets ps ON ps.id = mp.prediction_set_id
                WHERE ps.pool_member_id = ? AND ps.type = 'INITIAL'
                """,
                (rs, i) -> java.util.Map.of(
                        "matchId",    rs.getLong("match_id"),
                        "homeGoals",  rs.getInt("home_goals"),
                        "awayGoals",  rs.getInt("away_goals")),
                memberId);
        return java.util.Map.of("predictions", preds);
    }

    /** GET /api/predictions/initial/status/{poolId} — initial bet progress & submission status */
    @GetMapping("/initial/status/{poolId}")
    public java.util.Map<String, Object> initialStatus(JwtAuthenticationToken auth, @PathVariable long poolId) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        long memberId = predictionService.requirePoolMember(userId, poolId);

        // Submission status
        List<String> statuses = jdbc.query(
                "SELECT status FROM prediction_sets WHERE pool_member_id = ? AND type = 'INITIAL'",
                (rs, i) -> rs.getString("status"), memberId);
        boolean submitted = !statuses.isEmpty() && "SUBMITTED".equals(statuses.get(0));

        // Group stage predictions in LIVE set
        Integer groupsDone = jdbc.queryForObject("""
                SELECT COUNT(*) FROM match_predictions mp
                JOIN prediction_sets ps ON ps.id = mp.prediction_set_id
                JOIN matches m ON m.id = mp.match_id
                JOIN rounds r ON r.id = m.round_id
                WHERE ps.pool_member_id = ? AND ps.type = 'LIVE' AND r.stage = 'GROUP_STAGE'
                """, Integer.class, memberId);
        Integer groupsTotal = jdbc.queryForObject("""
                SELECT COUNT(*) FROM matches m JOIN rounds r ON r.id = m.round_id
                WHERE r.stage = 'GROUP_STAGE'
                """, Integer.class);

        // Knockout predictions in LIVE set
        Integer knockoutDone = jdbc.queryForObject("""
                SELECT COUNT(*) FROM match_predictions mp
                JOIN prediction_sets ps ON ps.id = mp.prediction_set_id
                JOIN matches m ON m.id = mp.match_id
                JOIN rounds r ON r.id = m.round_id
                WHERE ps.pool_member_id = ? AND ps.type = 'LIVE' AND r.stage != 'GROUP_STAGE'
                """, Integer.class, memberId);
        Integer knockoutTotal = jdbc.queryForObject("""
                SELECT COUNT(*) FROM matches m JOIN rounds r ON r.id = m.round_id
                WHERE r.stage != 'GROUP_STAGE'
                """, Integer.class);

        return java.util.Map.of(
                "submitted",     submitted,
                "groupsDone",    groupsDone    != null ? groupsDone    : 0,
                "groupsTotal",   groupsTotal   != null ? groupsTotal   : 72,
                "knockoutDone",  knockoutDone  != null ? knockoutDone  : 0,
                "knockoutTotal", knockoutTotal != null ? knockoutTotal : 31
        );
    }

    /** POST /api/predictions/initial/submit — copy LIVE→INITIAL and lock it */
    @PostMapping("/initial/submit")
    public java.util.Map<String, Object> submitInitialBet(
            JwtAuthenticationToken auth,
            @RequestBody java.util.Map<String, Long> body) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        long poolId = body.get("poolId");
        long memberId = predictionService.requirePoolMember(userId, poolId);

        long liveSetId    = predictionService.getOrCreatePredictionSet(memberId);
        long initialSetId = predictionService.getOrCreateInitialSet(memberId);

        // Idempotency guard
        List<String> statuses = jdbc.query(
                "SELECT status FROM prediction_sets WHERE id = ?",
                (rs, i) -> rs.getString("status"), initialSetId);
        if (!statuses.isEmpty() && "SUBMITTED".equals(statuses.get(0))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Initial bet already submitted");
        }

        // Copy LIVE predictions → INITIAL set (overwrite any draft values)
        jdbc.update("""
                INSERT INTO match_predictions (prediction_set_id, match_id, home_goals, away_goals)
                SELECT ?, match_id, home_goals, away_goals
                FROM match_predictions WHERE prediction_set_id = ?
                ON DUPLICATE KEY UPDATE home_goals = VALUES(home_goals), away_goals = VALUES(away_goals)
                """, initialSetId, liveSetId);

        // Lock
        jdbc.update("UPDATE prediction_sets SET status = 'SUBMITTED' WHERE id = ?", initialSetId);

        return java.util.Map.of("ok", true);
    }

    /** GET /api/predictions/pool/{poolId}/member/{memberId}/closed — closed predictions for a member */
    @GetMapping("/pool/{poolId}/member/{memberId}/closed")
    public List<PredictionService.ClosedPrediction> getClosedPredictions(
            JwtAuthenticationToken auth,
            @PathVariable long poolId,
            @PathVariable long memberId) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        predictionService.requirePoolMember(userId, poolId);
        return predictionService.getClosedPredictionsForMember(memberId, poolId);
    }

    /**
     * POST /api/predictions/ai/{matchId}
     * Returns an AI-generated (or random fallback) score prediction for the given match.
     * Optional body {home, away}: display names from the client's bracket cascade —
     * knockout placeholders are 'Pendiente' in DB, so the client sends the teams
     * the user actually sees and the AI reasons about those.
     */
    @PostMapping("/ai/{matchId}")
    public AiPredictionResponse predictMatch(JwtAuthenticationToken auth, @PathVariable long matchId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        // Fetch match info from DB
        var rows = jdbc.query("""
                SELECT m.id, ht.name home, at.name away, r.name round_name
                FROM matches m
                JOIN teams ht ON ht.id = m.home_team_id
                JOIN teams at ON at.id = m.away_team_id
                JOIN rounds r  ON r.id  = m.round_id
                WHERE m.id = ?
                """,
                (rs, i) -> new MatchInfo(
                        rs.getLong("id"),
                        rs.getString("home"),
                        rs.getString("away"),
                        rs.getString("round_name")),
                matchId);

        if (rows.isEmpty()) {
            return AiPredictionResponse.error("Partido no encontrado: " + matchId);
        }

        MatchInfo match = rows.get(0);
        String home = pickTeamName(body != null ? body.get("home") : null, match.home());
        String away = pickTeamName(body != null ? body.get("away") : null, match.away());

        // Sin equipos reales no hay nada que analizar: random sin razonamiento inventado
        PredictionResult result = (isPlaceholder(home) || isPlaceholder(away))
                ? aiService.randomPrediction()
                : aiService.predict(home, away, match.roundName());

        return new AiPredictionResponse(
                matchId,
                home,
                away,
                result.homeGoals(),
                result.awayGoals(),
                result.homeGoals() + " - " + result.awayGoals(),
                result.source(),
                result.reasoning(),
                null
        );
    }

    private static String pickTeamName(String fromClient, String fromDb) {
        return fromClient != null && !fromClient.isBlank() && !isPlaceholder(fromClient) ? fromClient : fromDb;
    }

    private static boolean isPlaceholder(String name) {
        return name == null || name.isBlank()
                || "Pendiente".equals(name) || "?".equals(name) || "TBD".equals(name);
    }

    /**
     * POST /api/predictions/bulk-random
     * Generates and saves a random prediction for every open match, then returns them.
     */
    record BulkRandomRequest(long poolId) {}

    /** DELETE /api/predictions/all?poolId=X — delete all LIVE + INITIAL predictions for the user */
    @DeleteMapping("/all")
    public void deleteAll(JwtAuthenticationToken auth, @RequestParam long poolId) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        long memberId = predictionService.requirePoolMember(userId, poolId);

        // LIVE set
        long liveSetId = predictionService.getOrCreatePredictionSet(memberId);
        jdbc.update("DELETE FROM score_breakdowns WHERE prediction_set_id = ?", liveSetId);
        jdbc.update("DELETE FROM match_predictions WHERE prediction_set_id = ?", liveSetId);

        // INITIAL set — reset to DRAFT so the user can redo the initial bet
        long initialSetId = predictionService.getOrCreateInitialSet(memberId);
        jdbc.update("DELETE FROM score_breakdowns WHERE prediction_set_id = ?", initialSetId);
        jdbc.update("DELETE FROM match_predictions WHERE prediction_set_id = ?", initialSetId);
        jdbc.update("UPDATE prediction_sets SET status = 'DRAFT' WHERE id = ?", initialSetId);
    }

    @PostMapping("/bulk-random")
    public List<AiPredictionResponse> bulkRandom(JwtAuthenticationToken auth, @RequestBody BulkRandomRequest req) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        long memberId     = predictionService.requirePoolMember(userId, req.poolId());
        long liveSetId    = predictionService.getOrCreatePredictionSet(memberId);
        long initialSetId = predictionService.getOrCreateInitialSet(memberId);

        var matches = jdbc.query("""
                SELECT m.id,
                       COALESCE(ht.name, 'TBD') home,
                       COALESCE(at.name, 'TBD') away,
                       r.name round_name
                FROM matches m
                LEFT JOIN teams  ht ON ht.id = m.home_team_id
                LEFT JOIN teams  at ON at.id = m.away_team_id
                JOIN rounds r  ON r.id  = m.round_id
                WHERE m.status IN ('OPEN','LIVE')
                  AND (m.home_goals IS NULL OR m.away_goals IS NULL)
                  AND NOT EXISTS (
                    SELECT 1 FROM match_predictions mp
                    WHERE mp.prediction_set_id = ? AND mp.match_id = m.id
                  )
                ORDER BY m.kickoff_at IS NULL, m.kickoff_at, m.id
                """,
                (rs, i) -> new MatchInfo(
                        rs.getLong("id"),
                        rs.getString("home"),
                        rs.getString("away"),
                        rs.getString("round_name")),
                liveSetId);

        List<AiPredictionResponse> results = matches.stream().map(m -> {
            PredictionResult r = aiService.randomPrediction();
            predictionService.savePrediction(liveSetId, m.id(), r.homeGoals(), r.awayGoals());
            // También guardar como apuesta inicial (para el premio INITIAL_BET)
            predictionService.savePrediction(initialSetId, m.id(), r.homeGoals(), r.awayGoals());
            return new AiPredictionResponse(
                    m.id(), m.home(), m.away(),
                    r.homeGoals(), r.awayGoals(),
                    r.homeGoals() + " - " + r.awayGoals(),
                    "random", null, null);
        }).toList();

        // Pick a random champion only if the user doesn't have one yet
        Integer existingChampion = jdbc.queryForObject(
                "SELECT COUNT(*) FROM champion_predictions WHERE pool_member_id = ?",
                Integer.class, memberId);
        if (existingChampion == null || existingChampion == 0) {
            List<Long> randomTeam = jdbc.queryForList(
                    "SELECT id FROM teams WHERE name != 'Pendiente' ORDER BY RAND() LIMIT 1",
                    Long.class);
            if (!randomTeam.isEmpty()) {
                jdbc.update("""
                        INSERT INTO champion_predictions (pool_member_id, team_id)
                        VALUES (?, ?)
                        ON DUPLICATE KEY UPDATE team_id = VALUES(team_id), updated_at = CURRENT_TIMESTAMP(6)
                        """, memberId, randomTeam.get(0));
            }
        }

        return results;
    }

    // ─── records ─────────────────────────────────────────────────────────────

    private record MatchInfo(long id, String home, String away, String roundName) {}

    public record AiPredictionResponse(
            Long matchId,
            String home,
            String away,
            Integer homeGoals,
            Integer awayGoals,
            String prediction,
            String source,
            String reasoning,
            String error
    ) {
        static AiPredictionResponse error(String msg) {
            return new AiPredictionResponse(null, null, null, null, null, null, null, null, msg);
        }
    }
}
