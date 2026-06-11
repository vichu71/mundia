package com.mundia.backend.prediction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ChampionPredictionController {

    private final JdbcTemplate jdbc;
    private final PredictionService predictionService;

    public ChampionPredictionController(JdbcTemplate jdbc, PredictionService predictionService) {
        this.jdbc = jdbc;
        this.predictionService = predictionService;
    }

    /** GET /api/teams — all teams sorted by name */
    @GetMapping("/api/teams")
    public List<TeamDto> allTeams(JwtAuthenticationToken auth) {
        return jdbc.query(
                "SELECT id, name, country_code FROM teams ORDER BY name",
                (rs, i) -> new TeamDto(rs.getLong("id"), rs.getString("name"), rs.getString("country_code")));
    }

    /** GET /api/predictions/champion/status/{poolId} — my champion pick + pool stats */
    @GetMapping("/api/predictions/champion/status/{poolId}")
    public ChampionStatusDto status(JwtAuthenticationToken auth, @PathVariable long poolId) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        long memberId = predictionService.requirePoolMember(userId, poolId);

        // My pick
        List<TeamDto> myPick = jdbc.query("""
                SELECT t.id, t.name, t.country_code
                FROM champion_predictions cp
                JOIN teams t ON t.id = cp.team_id
                WHERE cp.pool_member_id = ?
                """, (rs, i) -> new TeamDto(rs.getLong("id"), rs.getString("name"), rs.getString("country_code")),
                memberId);

        int totalPlayers = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pool_members WHERE pool_id = ? AND role IN ('PLAYER','ADMIN')",
                Integer.class, poolId);

        if (myPick.isEmpty()) {
            return new ChampionStatusDto(false, null, null, null, 0, totalPlayers, 0.0, 0, false);
        }

        TeamDto pick = myPick.get(0);

        // How many others picked the same team
        int pickerCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM champion_predictions cp
                JOIN pool_members pm ON pm.id = cp.pool_member_id
                WHERE pm.pool_id = ? AND cp.team_id = ?
                """, Integer.class, poolId, pick.id());

        double pickersPct = totalPlayers > 0 ? Math.round(pickerCount * 100.0 / totalPlayers * 10) / 10.0 : 0.0;

        // Estimated champion prize (CHAMPION rule % * pot / pickerCount)
        Integer potCents = jdbc.queryForObject("""
                SELECT COALESCE(SUM(pay.amount_cents), 0)
                FROM payments pay JOIN pool_members pm ON pm.id = pay.pool_member_id
                WHERE pm.pool_id = ? AND pay.status = 'CONFIRMED'
                """, Integer.class, poolId);
        Integer champPctRaw = jdbc.queryForObject("""
                SELECT ROUND(percentage_when_perfect_extinct) FROM prize_rules
                WHERE pool_id = ? AND category = 'CHAMPION' AND enabled = TRUE
                """, Integer.class, poolId);
        int champPct = champPctRaw != null ? champPctRaw : 5;
        int champPoolCents = Math.round((potCents != null ? potCents : 0) * champPct / 100.0f);
        int myShareCents = pickerCount > 0 ? Math.round(champPoolCents / (float) pickerCount) : champPoolCents;
        int myShareEuros = Math.round(myShareCents / 100.0f);

        // Is team still alive? (hasn't lost a knockout match)
        int lostKnockout = jdbc.queryForObject("""
                SELECT COUNT(*) FROM matches m
                JOIN rounds r ON r.id = m.round_id
                WHERE r.stage != 'GROUP_STAGE'
                  AND m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL
                  AND (
                    (m.home_team_id = ? AND m.home_goals < m.away_goals)
                    OR (m.away_team_id = ? AND m.away_goals < m.home_goals)
                  )
                """, Integer.class, pick.id(), pick.id());

        return new ChampionStatusDto(
                true, pick.id(), pick.name(), pick.flag(),
                pickerCount, totalPlayers, pickersPct, myShareEuros, lostKnockout == 0);
    }

    /** DELETE /api/predictions/champion?poolId=X — remove champion pick */
    @DeleteMapping("/api/predictions/champion")
    public void deleteChampion(JwtAuthenticationToken auth, @RequestParam long poolId) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        long memberId = predictionService.requirePoolMember(userId, poolId);
        jdbc.update("DELETE FROM champion_predictions WHERE pool_member_id = ?", memberId);
    }

    /** POST /api/predictions/champion — save or update champion pick */
    @PostMapping("/api/predictions/champion")
    public void saveChampion(JwtAuthenticationToken auth, @RequestBody SaveChampionRequest req) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        long memberId = predictionService.requirePoolMember(userId, req.poolId());

        // Verify team exists
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM teams WHERE id = ?", Integer.class, req.teamId());
        if (count == null || count == 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Team not found: " + req.teamId());
        }

        jdbc.update("""
                INSERT INTO champion_predictions (pool_member_id, team_id)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE team_id = VALUES(team_id), updated_at = CURRENT_TIMESTAMP(6)
                """, memberId, req.teamId());
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public record TeamDto(long id, String name, String flag) {}

    public record ChampionStatusDto(
            boolean hasChampionPick,
            Long teamId,
            String teamName,
            String flag,
            int pickerCount,
            int totalPlayers,
            double pickersPct,
            int championPrize,
            boolean teamAlive
    ) {}

    record SaveChampionRequest(long poolId, long teamId) {}
}
