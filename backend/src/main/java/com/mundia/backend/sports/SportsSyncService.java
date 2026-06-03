package com.mundia.backend.sports;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mundia.backend.config.FootballApiProperties;
import com.mundia.backend.scoring.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SportsSyncService {

    private static final Logger log = LoggerFactory.getLogger(SportsSyncService.class);

    private final FootballApiClient client;
    private final FootballApiProperties properties;
    private final JdbcTemplate jdbc;
    private final ScoringService scoringService;
    private final ObjectMapper mapper;

    public SportsSyncService(FootballApiClient client,
                             FootballApiProperties properties,
                             JdbcTemplate jdbc,
                             ScoringService scoringService) {
        this.client = client;
        this.properties = properties;
        this.jdbc = jdbc;
        this.scoringService = scoringService;
        this.mapper = new ObjectMapper();
    }

    // ─── public entry points ────────────────────────────────────────────────

    @Transactional
    public SyncResult syncLiveFixtures() {
        return syncFixtures("LIVE_FIXTURES", client.getLiveFixtures());
    }

    @Transactional
    public SyncResult syncTodayFixtures() {
        return syncFixtures("DAILY_FIXTURES", client.getFixturesByDate(LocalDate.now()));
    }

    @Transactional
    public SyncResult syncRounds() {
        Instant start = Instant.now();
        String raw = null;
        try {
            raw = client.getRounds();
            JsonNode root = mapper.readTree(raw);
            int count = 0;
            for (JsonNode item : root.path("response")) {
                String name = item.asText();
                if (name == null || name.isBlank()) continue;
                jdbc.update("""
                        INSERT INTO rounds (name, stage, sort_order, external_name)
                        VALUES (?, 'GROUP_STAGE', 0, ?)
                        ON DUPLICATE KEY UPDATE external_name = VALUES(external_name)
                        """, name, name);
                count++;
            }
            recordRun("ROUNDS", "SUCCESS", null, start, null);
            return new SyncResult("ROUNDS", "OK", count);
        } catch (Exception e) {
            log.error("syncRounds failed", e);
            recordRun("ROUNDS", "FAILED", null, start, e.getMessage());
            return new SyncResult("ROUNDS", "FAILED: " + e.getMessage(), 0);
        }
    }

    @Transactional
    public SyncResult syncStandings() {
        Instant start = Instant.now();
        String raw = null;
        try {
            raw = client.getStandings();
            JsonNode root = mapper.readTree(raw);
            int count = 0;
            for (JsonNode league : root.path("response")) {
                JsonNode leagueNode = league.path("league");
                String roundName = leagueNode.path("round").asText(null);
                String payload = leagueNode.toString();
                jdbc.update("""
                        INSERT INTO standings_snapshots (round_name, provider, payload_json)
                        VALUES (?, 'API_FOOTBALL', ?)
                        """, roundName, payload);
                count++;
            }
            recordRun("STANDINGS", "SUCCESS", null, start, null);
            return new SyncResult("STANDINGS", "OK", count);
        } catch (Exception e) {
            log.error("syncStandings failed", e);
            recordRun("STANDINGS", "FAILED", null, start, e.getMessage());
            return new SyncResult("STANDINGS", "FAILED: " + e.getMessage(), 0);
        }
    }

    // ─── fixtures shared logic ───────────────────────────────────────────────

    private SyncResult syncFixtures(String syncType, String raw) {
        Instant start = Instant.now();
        try {
            JsonNode root = mapper.readTree(raw);
            int count = 0;
            for (JsonNode item : root.path("response")) {
                upsertFixture(item);
                count++;
            }
            if (count > 0) scoringService.recalculateAll();
            recordRun(syncType, "SUCCESS", null, start, null);
            return new SyncResult(syncType, "OK", count);
        } catch (Exception e) {
            log.error("syncFixtures({}) failed", syncType, e);
            recordRun(syncType, "FAILED", null, start, e.getMessage());
            return new SyncResult(syncType, "FAILED: " + e.getMessage(), 0);
        }
    }

    private void upsertFixture(JsonNode item) {
        JsonNode fixture  = item.path("fixture");
        JsonNode teams    = item.path("teams");
        JsonNode goals    = item.path("goals");
        JsonNode leagueNode = item.path("league");

        long   externalFixtureId = fixture.path("id").asLong();
        String statusShort       = fixture.path("status").path("short").asText("NS");
        Integer elapsed          = fixture.path("status").path("elapsed").isNull() ? null
                                   : fixture.path("status").path("elapsed").asInt();
        String kickoffStr        = fixture.path("date").asText(null);
        Instant kickoff          = kickoffStr != null ? OffsetDateTime.parse(kickoffStr).toInstant() : null;

        JsonNode homeNode  = teams.path("home");
        JsonNode awayNode  = teams.path("away");
        long extHomeId     = homeNode.path("id").asLong();
        long extAwayId     = awayNode.path("id").asLong();
        String homeName    = homeNode.path("name").asText("Unknown");
        String awayName    = awayNode.path("name").asText("Unknown");

        Integer homeGoals  = goals.path("home").isNull() ? null : goals.path("home").asInt();
        Integer awayGoals  = goals.path("away").isNull() ? null : goals.path("away").asInt();

        String roundName   = leagueNode.path("round").asText(null);

        // upsert teams
        long homeTeamId = upsertTeam(extHomeId, homeName);
        long awayTeamId = upsertTeam(extAwayId, awayName);

        // upsert round — matches.round_id is NOT NULL so we always need one
        long roundId = upsertRound(roundName != null ? roundName : "Unknown");

        String matchStatus = mapStatus(statusShort);
        String hash        = sha256(item.toString());

        jdbc.update("""
                INSERT INTO matches
                  (external_fixture_id, round_id, home_team_id, away_team_id,
                   kickoff_at, status, status_short, elapsed, home_goals, away_goals,
                   result_source, last_synced_at, raw_payload_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'API_FOOTBALL', CURRENT_TIMESTAMP(6), ?)
                ON DUPLICATE KEY UPDATE
                  status            = VALUES(status),
                  status_short      = VALUES(status_short),
                  elapsed           = VALUES(elapsed),
                  home_goals        = VALUES(home_goals),
                  away_goals        = VALUES(away_goals),
                  last_synced_at    = VALUES(last_synced_at),
                  raw_payload_hash  = VALUES(raw_payload_hash)
                """,
                externalFixtureId, roundId, homeTeamId, awayTeamId,
                kickoff != null ? java.sql.Timestamp.from(kickoff) : null,
                matchStatus, statusShort, elapsed, homeGoals, awayGoals, hash);
    }

    private long upsertTeam(long externalId, String name) {
        String countryCode = countryCode(name);
        jdbc.update("""
                INSERT INTO teams (external_team_id, name, country_code)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE name = VALUES(name), country_code = VALUES(country_code)
                """, externalId, name, countryCode);
        return jdbc.queryForObject(
                "SELECT id FROM teams WHERE external_team_id = ?", Long.class, externalId);
    }

    private long upsertRound(String name) {
        String stage = name.toLowerCase().contains("group") ? "GROUP_STAGE" : "KNOCKOUT";
        jdbc.update("""
                INSERT INTO rounds (name, stage, sort_order, external_name)
                VALUES (?, ?, 0, ?)
                ON DUPLICATE KEY UPDATE stage = VALUES(stage), external_name = VALUES(external_name)
                """, name, stage, name);
        return jdbc.queryForObject("SELECT id FROM rounds WHERE name = ?", Long.class, name);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private String mapStatus(String short_) {
        return switch (short_) {
            case "1H", "2H", "HT", "ET", "BT", "P", "LIVE" -> "LIVE";
            case "FT", "AET", "PEN"                         -> "FINISHED";
            case "PST", "CANC", "ABD", "AWD", "WO"          -> "CANCELLED";
            default                                          -> "OPEN";
        };
    }

    /** Best-effort ISO-3166-1 alpha-2 from team name. Falls back to "un". */
    private String countryCode(String teamName) {
        return switch (teamName.toLowerCase()) {
            case "spain", "españa", "espana"         -> "es";
            case "germany", "alemania"               -> "de";
            case "brazil", "brasil"                  -> "br";
            case "portugal"                          -> "pt";
            case "argentina"                         -> "ar";
            case "france", "francia"                 -> "fr";
            case "england", "inglaterra"             -> "gb-eng";
            case "italy", "italia"                   -> "it";
            case "netherlands", "holanda"            -> "nl";
            case "belgium", "bélgica", "belgica"     -> "be";
            case "croatia", "croacia"                -> "hr";
            case "morocco", "marruecos"              -> "ma";
            case "usa", "united states"              -> "us";
            case "mexico", "méxico"                  -> "mx";
            case "colombia"                          -> "co";
            case "uruguay"                           -> "uy";
            case "japan", "japón", "japon"           -> "jp";
            case "south korea", "corea del sur"      -> "kr";
            case "senegal"                           -> "sn";
            case "ghana"                             -> "gh";
            case "nigeria"                           -> "ng";
            case "cameroon", "camerún"               -> "cm";
            case "australia"                         -> "au";
            case "iran"                              -> "ir";
            case "saudi arabia", "arabia saudí"      -> "sa";
            case "qatar"                             -> "qa";
            case "poland", "polonia"                 -> "pl";
            case "denmark", "dinamarca"              -> "dk";
            case "switzerland", "suiza"              -> "ch";
            case "serbia"                            -> "rs";
            case "ecuador"                           -> "ec";
            case "canada", "canadá"                  -> "ca";
            case "wales", "gales"                    -> "gb-wls";
            case "scotland", "escocia"               -> "gb-sct";
            default                                  -> "un";
        };
    }

    private void recordRun(String syncType, String result, String requestUrl,
                           Instant start, String error) {
        try {
            jdbc.update("""
                    INSERT INTO sports_sync_runs
                      (provider, sync_type, status, request_url, started_at, finished_at, error_message)
                    VALUES ('API_FOOTBALL', ?, ?, ?, ?, ?, ?)
                    """,
                    syncType, result,
                    requestUrl != null ? requestUrl : "n/a",
                    java.sql.Timestamp.from(start),
                    java.sql.Timestamp.from(Instant.now()),
                    error);
        } catch (Exception e) {
            log.warn("Could not record sync run: {}", e.getMessage());
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    public record SyncResult(String syncType, String status, int count) {}
}
