package com.mundia.backend.sports;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mundia.backend.scoring.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Syncs data from the free worldcup26.ir API (no key required).
 * Maps the worldcup26 data model to the mundia DB schema.
 */
@Service
public class WorldCup26SyncService {

    private static final Logger log = LoggerFactory.getLogger(WorldCup26SyncService.class);
    // local_date format in worldcup26 API: "06/11/2026 13:00"
    private static final DateTimeFormatter WC26_DATE_FMT =
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");

    private final WorldCup26Client client;
    private final JdbcTemplate jdbc;
    private final ScoringService scoringService;
    private final ObjectMapper mapper = new ObjectMapper();

    public WorldCup26SyncService(WorldCup26Client client, JdbcTemplate jdbc, ScoringService scoringService) {
        this.client = client;
        this.jdbc = jdbc;
        this.scoringService = scoringService;
    }

    // ─── public entry points (mirror SportsSyncService interface) ───────────

    @Transactional
    public SportsSyncService.SyncResult syncTeams() {
        Instant start = Instant.now();
        try {
            String raw = client.getTeams();
            JsonNode root = mapper.readTree(raw);
            int count = 0;
            for (JsonNode t : root.path("teams")) {
                String name   = t.path("name_en").asText("Unknown");
                String iso2   = normalizeIso2(name, t.path("iso2").asText("un").toLowerCase());
                String wc26Id = t.path("id").asText();
                // use negative IDs to avoid collision with API-Football IDs
                long extId = -(Long.parseLong(wc26Id));
                jdbc.update("""
                        INSERT INTO teams (external_team_id, name, country_code)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE name = VALUES(name), country_code = VALUES(country_code)
                        """, extId, name, iso2);
                count++;
            }
            recordRun("WC26_TEAMS", "SUCCESS", start, null);
            return new SportsSyncService.SyncResult("WC26_TEAMS", "OK", count);
        } catch (Exception e) {
            log.error("wc26 syncTeams failed", e);
            recordRun("WC26_TEAMS", "FAILED", start, e.getMessage());
            return new SportsSyncService.SyncResult("WC26_TEAMS", "FAILED: " + e.getMessage(), 0);
        }
    }

    @Transactional
    public SportsSyncService.SyncResult syncFixtures() {
        Instant start = Instant.now();
        try {
            // ensure teams are loaded first so we can resolve team IDs
            String teamsRaw = client.getTeams();
            Map<String, Long> teamIdByWc26Id = buildTeamIdMap(teamsRaw);

            String gamesRaw = client.getGames();
            JsonNode root = mapper.readTree(gamesRaw);
            int count = 0;
            for (JsonNode g : root.path("games")) {
                if (upsertGame(g, teamIdByWc26Id)) {
                    count++;
                }
            }
            if (count > 0) scoringService.recalculateAll();
            recordRun("WC26_FIXTURES", "SUCCESS", start, null);
            return new SportsSyncService.SyncResult("WC26_FIXTURES", "OK", count);
        } catch (Exception e) {
            log.error("wc26 syncFixtures failed", e);
            recordRun("WC26_FIXTURES", "FAILED", start, e.getMessage());
            return new SportsSyncService.SyncResult("WC26_FIXTURES", "FAILED: " + e.getMessage(), 0);
        }
    }

    @Transactional
    public SportsSyncService.SyncResult syncGroups() {
        Instant start = Instant.now();
        try {
            String raw = client.getGroups();
            jdbc.update("""
                    INSERT INTO standings_snapshots (round_name, provider, payload_json)
                    VALUES ('Groups', 'WC26_IR', ?)
                    """, raw);
            recordRun("WC26_GROUPS", "SUCCESS", start, null);
            return new SportsSyncService.SyncResult("WC26_GROUPS", "OK", 1);
        } catch (Exception e) {
            log.error("wc26 syncGroups failed", e);
            recordRun("WC26_GROUPS", "FAILED", start, e.getMessage());
            return new SportsSyncService.SyncResult("WC26_GROUPS", "FAILED: " + e.getMessage(), 0);
        }
    }

    // ─── internals ──────────────────────────────────────────────────────────

    /**
     * Builds a map from worldcup26 team_id → local DB teams.id,
     * upserting teams as it goes so all 48 are available.
     */
    private Map<String, Long> buildTeamIdMap(String teamsRaw) throws Exception {
        JsonNode root = mapper.readTree(teamsRaw);
        Map<String, Long> map = new HashMap<>();
        for (JsonNode t : root.path("teams")) {
            String wc26Id = t.path("id").asText();
            String name   = t.path("name_en").asText("Unknown");
            String iso2   = normalizeIso2(name, t.path("iso2").asText("un").toLowerCase());
            long extId    = -(Long.parseLong(wc26Id));
            jdbc.update("""
                    INSERT INTO teams (external_team_id, name, country_code)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE name = VALUES(name), country_code = VALUES(country_code)
                    """, extId, name, iso2);
            Long localId = jdbc.queryForObject(
                    "SELECT id FROM teams WHERE external_team_id = ?", Long.class, extId);
            map.put(wc26Id, localId);
        }
        return map;
    }

    private boolean upsertGame(JsonNode g, Map<String, Long> teamIdByWc26Id) {
        String wc26GameId    = g.path("id").asText();
        String homeWc26Id    = g.path("home_team_id").asText();
        String awayWc26Id    = g.path("away_team_id").asText();
        String homeScoreStr  = g.path("home_score").asText("0");
        String awayScoreStr  = g.path("away_score").asText("0");
        String finishedStr   = g.path("finished").asText("FALSE");
        String timeElapsed   = g.path("time_elapsed").asText("notstarted");
        String type          = g.path("type").asText("group");  // group / knockout
        String group         = g.path("group").asText(null);
        String localDate     = g.path("local_date").asText(null);
        String homeNameEn    = g.path("home_team_name_en").asText("Unknown");
        String awayNameEn    = g.path("away_team_name_en").asText("Unknown");

        Long homeTeamId = teamIdByWc26Id.get(homeWc26Id);
        Long awayTeamId = teamIdByWc26Id.get(awayWc26Id);
        if (homeTeamId == null || awayTeamId == null) {
            log.debug("wc26 game {} has unresolved teams home={} away={}", wc26GameId, homeWc26Id, awayWc26Id);
            return false;
        }

        // resolve / create round
        String roundName = resolveRoundName(type, group);
        long roundId = upsertRound(roundName, type);

        // status mapping
        String status = mapStatus(finishedStr, timeElapsed);
        String statusShort = mapStatusShort(finishedStr, timeElapsed);
        Integer elapsed = parseElapsed(timeElapsed);

        // goals (null if not started)
        boolean started = !"notstarted".equalsIgnoreCase(timeElapsed);
        Integer homeGoals = started ? parseGoals(homeScoreStr) : null;
        Integer awayGoals = started ? parseGoals(awayScoreStr) : null;

        // kickoff — use UTC interpretation of local_date
        java.sql.Timestamp kickoff = parseKickoff(localDate);

        // use negative wc26 game id as external_fixture_id (avoids collision)
        long extFixtureId = -(Long.parseLong(wc26GameId));

        boolean knockout = !"group".equalsIgnoreCase(type);
        if (knockout) {
            upsertKnockoutGame(extFixtureId, roundId, homeTeamId, awayTeamId,
                    kickoff, status, statusShort, elapsed, homeGoals, awayGoals);
            return true;
        }

        jdbc.update("""
                INSERT INTO matches
                  (external_fixture_id, round_id, home_team_id, away_team_id,
                   kickoff_at, status, status_short, elapsed, home_goals, away_goals,
                   result_source, last_synced_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'WC26_IR', CURRENT_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE
                  status         = VALUES(status),
                  status_short   = VALUES(status_short),
                  elapsed        = VALUES(elapsed),
                  home_goals     = VALUES(home_goals),
                  away_goals     = VALUES(away_goals),
                  last_synced_at = VALUES(last_synced_at)
                """,
                extFixtureId, roundId, homeTeamId, awayTeamId,
                kickoff, status, statusShort, elapsed, homeGoals, awayGoals);
        return true;
    }

    /**
     * Knockout matches follow the canonical model: one row per match, forever.
     * A synced game first tries the row already claimed via external_fixture_id,
     * then claims the lowest free placeholder of its round (keeping the match id —
     * and with it every user prediction). Only inserts when no placeholder is free.
     */
    private void upsertKnockoutGame(long extFixtureId, long roundId, long homeTeamId, long awayTeamId,
                                    java.sql.Timestamp kickoff, String status, String statusShort,
                                    Integer elapsed, Integer homeGoals, Integer awayGoals) {
        int updated = jdbc.update("""
                UPDATE matches
                SET round_id = ?, home_team_id = ?, away_team_id = ?, kickoff_at = ?,
                    status = ?, status_short = ?, elapsed = ?, home_goals = ?, away_goals = ?,
                    result_source = 'WC26_IR', last_synced_at = CURRENT_TIMESTAMP(6)
                WHERE external_fixture_id = ?
                """,
                roundId, homeTeamId, awayTeamId, kickoff,
                status, statusShort, elapsed, homeGoals, awayGoals, extFixtureId);
        if (updated > 0) return;

        List<Long> freeSlot = jdbc.query("""
                SELECT id FROM matches
                WHERE round_id = ? AND external_fixture_id IS NULL AND result_source = 'NONE'
                ORDER BY id
                LIMIT 1
                """, (rs, i) -> rs.getLong("id"), roundId);
        if (!freeSlot.isEmpty()) {
            jdbc.update("""
                    UPDATE matches
                    SET external_fixture_id = ?, home_team_id = ?, away_team_id = ?, kickoff_at = ?,
                        status = ?, status_short = ?, elapsed = ?, home_goals = ?, away_goals = ?,
                        result_source = 'WC26_IR', last_synced_at = CURRENT_TIMESTAMP(6)
                    WHERE id = ?
                    """,
                    extFixtureId, homeTeamId, awayTeamId, kickoff,
                    status, statusShort, elapsed, homeGoals, awayGoals, freeSlot.get(0));
            return;
        }

        jdbc.update("""
                INSERT INTO matches
                  (external_fixture_id, round_id, home_team_id, away_team_id,
                   kickoff_at, status, status_short, elapsed, home_goals, away_goals,
                   result_source, last_synced_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'WC26_IR', CURRENT_TIMESTAMP(6))
                """,
                extFixtureId, roundId, homeTeamId, awayTeamId,
                kickoff, status, statusShort, elapsed, homeGoals, awayGoals);
    }

    private String resolveRoundName(String type, String group) {
        if ("group".equalsIgnoreCase(type) && group != null && !group.isBlank()) {
            return "Group " + group;
        }
        return switch (type.toLowerCase()) {
            case "round_of_32", "r32", "roundof32" -> "Round of 32";
            case "round_of_16", "r16", "roundof16" -> "Round of 16";
            case "quarterfinal", "quarter_final", "quarter-final", "qf" -> "Quarter-finals";
            case "semifinal", "semi_final", "semi-final", "sf" -> "Semi-finals";
            case "third_place", "third place"  -> "Third place";
            case "final"        -> "Final";
            default             -> {
                log.warn("Unknown round type from API: '{}' — using as-is", type);
                yield type;
            }
        };
    }

    private long upsertRound(String name, String type) {
        String stage = "group".equalsIgnoreCase(type) ? "GROUP_STAGE" : "KNOCKOUT";
        jdbc.update("""
                INSERT INTO rounds (name, stage, sort_order, external_name)
                VALUES (?, ?, 0, ?)
                ON DUPLICATE KEY UPDATE stage = VALUES(stage), external_name = VALUES(external_name)
                """, name, stage, name);
        return jdbc.queryForObject("SELECT id FROM rounds WHERE name = ?", Long.class, name);
    }

    private String mapStatus(String finished, String elapsed) {
        if ("TRUE".equalsIgnoreCase(finished))    return "FINISHED";
        if ("notstarted".equalsIgnoreCase(elapsed)) return "OPEN";
        return "LIVE";
    }

    private String mapStatusShort(String finished, String elapsed) {
        if ("TRUE".equalsIgnoreCase(finished))      return "FT";
        if ("notstarted".equalsIgnoreCase(elapsed)) return "NS";
        // elapsed could be "45", "HT", "90+2" etc.
        return elapsed.length() <= 10 ? elapsed : elapsed.substring(0, 10);
    }

    private Integer parseElapsed(String timeElapsed) {
        if (timeElapsed == null || "notstarted".equalsIgnoreCase(timeElapsed)
                || "HT".equalsIgnoreCase(timeElapsed) || "FT".equalsIgnoreCase(timeElapsed)) {
            return null;
        }
        // "45", "90+2", "67" etc. — extract leading digits
        try {
            return Integer.parseInt(timeElapsed.replaceAll("\\+.*", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseGoals(String val) {
        try { return Integer.parseInt(val.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private java.sql.Timestamp parseKickoff(String localDate) {
        if (localDate == null || localDate.isBlank()) return null;
        try {
            LocalDateTime ldt = LocalDateTime.parse(localDate, WC26_DATE_FMT);
            // worldcup26.ir dates are local (host-city time). We store as UTC approximation.
            // For a proper app you'd use the stadium timezone, but UTC-5 (US Central) is good enough.
            return java.sql.Timestamp.from(ldt.toInstant(ZoneOffset.ofHours(-5)));
        } catch (DateTimeParseException e) {
            log.warn("Cannot parse kickoff date: {}", localDate);
            return null;
        }
    }

    private static String normalizeIso2(String teamName, String iso2) {
        return switch (teamName.toLowerCase()) {
            case "england"       -> "gb-eng";
            case "scotland"      -> "gb-sct";
            case "wales"         -> "gb-wls";
            case "northern ireland" -> "gb-nir";
            default              -> iso2;
        };
    }

    private void recordRun(String syncType, String result, Instant start, String error) {
        try {
            jdbc.update("""
                    INSERT INTO sports_sync_runs
                      (provider, sync_type, status, request_url, started_at, finished_at, error_message)
                    VALUES ('WC26_IR', ?, ?, 'https://worldcup26.ir', ?, ?, ?)
                    """,
                    syncType, result,
                    java.sql.Timestamp.from(start),
                    java.sql.Timestamp.from(Instant.now()),
                    error);
        } catch (Exception e) {
            log.warn("Could not record wc26 sync run: {}", e.getMessage());
        }
    }
}
