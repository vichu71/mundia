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

    /** GET /api/predictions/initial/status/{poolId} — has the user submitted initial bet? */
    @GetMapping("/initial/status/{poolId}")
    public java.util.Map<String, Object> initialStatus(JwtAuthenticationToken auth, @PathVariable long poolId) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        boolean has = predictionService.hasInitialBet(userId, poolId);
        // count how many matches have initial prediction
        long memberId = predictionService.requirePoolMember(userId, poolId);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM match_predictions mp
                JOIN prediction_sets ps ON ps.id = mp.prediction_set_id
                WHERE ps.pool_member_id = ? AND ps.type = 'INITIAL'
                """, Integer.class, memberId);
        return java.util.Map.of("hasInitialBet", has, "predictedMatches", count != null ? count : 0);
    }

    /**
     * POST /api/predictions/ai/{matchId}
     * Returns an AI-generated (or random fallback) score prediction for the given match.
     */
    @PostMapping("/ai/{matchId}")
    public AiPredictionResponse predictMatch(@PathVariable long matchId) {
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
        PredictionResult result = aiService.predict(match.home(), match.away(), match.roundName());

        return new AiPredictionResponse(
                matchId,
                match.home(),
                match.away(),
                result.homeGoals(),
                result.awayGoals(),
                result.homeGoals() + " - " + result.awayGoals(),
                result.source(),
                result.reasoning(),
                null
        );
    }

    /**
     * POST /api/predictions/bulk-random
     * Generates and saves a random prediction for every open match, then returns them.
     */
    record BulkRandomRequest(long poolId) {}

    @PostMapping("/bulk-random")
    public List<AiPredictionResponse> bulkRandom(JwtAuthenticationToken auth, @RequestBody BulkRandomRequest req) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        long memberId = predictionService.requirePoolMember(userId, req.poolId());
        long setId    = predictionService.getOrCreatePredictionSet(memberId);

        var matches = jdbc.query("""
                SELECT m.id, ht.name home, at.name away, r.name round_name
                FROM matches m
                JOIN teams  ht ON ht.id = m.home_team_id
                JOIN teams  at ON at.id = m.away_team_id
                JOIN rounds r  ON r.id  = m.round_id
                WHERE m.status IN ('OPEN','LIVE')
                  AND (m.home_goals IS NULL OR m.away_goals IS NULL)
                ORDER BY m.kickoff_at IS NULL, m.kickoff_at, m.id
                """,
                (rs, i) -> new MatchInfo(
                        rs.getLong("id"),
                        rs.getString("home"),
                        rs.getString("away"),
                        rs.getString("round_name")));

        return matches.stream().map(m -> {
            PredictionResult r = aiService.randomPrediction();
            predictionService.savePrediction(setId, m.id(), r.homeGoals(), r.awayGoals());
            return new AiPredictionResponse(
                    m.id(), m.home(), m.away(),
                    r.homeGoals(), r.awayGoals(),
                    r.homeGoals() + " - " + r.awayGoals(),
                    "random", null, null);
        }).toList();
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
