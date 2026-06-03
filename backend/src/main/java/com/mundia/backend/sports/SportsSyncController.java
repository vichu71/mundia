package com.mundia.backend.sports;

import java.util.List;
import java.util.Map;

import com.mundia.backend.config.FootballApiProperties;
import com.mundia.backend.sports.SportsSyncService.SyncResult;
import com.mundia.backend.sports.SyncSourceConfig.Source;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sports-sync")
public class SportsSyncController {

    private final FootballApiProperties properties;
    private final SportsSyncService syncService;
    private final WorldCup26SyncService wc26SyncService;
    private final SyncSourceConfig sourceConfig;

    public SportsSyncController(FootballApiProperties properties,
                                SportsSyncService syncService,
                                WorldCup26SyncService wc26SyncService,
                                SyncSourceConfig sourceConfig) {
        this.properties = properties;
        this.syncService = syncService;
        this.wc26SyncService = wc26SyncService;
        this.sourceConfig = sourceConfig;
    }

    @GetMapping("/status")
    public SportsSyncStatus status() {
        return SportsSyncStatus.from(properties, sourceConfig.getActive());
    }

    // ─── fuente activa ────────────────────────────────────────────────────

    /** Cambia la fuente de datos activa. Body: {"source": "WC26_IR"} o {"source": "API_FOOTBALL"} */
    @PostMapping("/source")
    public Map<String, String> setSource(@RequestBody Map<String, String> body) {
        String raw = body.getOrDefault("source", "WC26_IR").toUpperCase();
        Source s = switch (raw) {
            case "API_FOOTBALL" -> Source.API_FOOTBALL;
            default             -> Source.WC26_IR;
        };
        sourceConfig.setActive(s);
        return Map.of("activeSource", s.name());
    }

    // ─── API-Football endpoints ───────────────────────────────────────────

    @PostMapping("/fixtures/live")
    public SportsSyncCommandResponse syncLiveFixtures() {
        return SportsSyncCommandResponse.from(syncService.syncLiveFixtures());
    }

    @PostMapping("/fixtures/today")
    public SportsSyncCommandResponse syncTodayFixtures() {
        return SportsSyncCommandResponse.from(syncService.syncTodayFixtures());
    }

    @PostMapping("/rounds")
    public SportsSyncCommandResponse syncRounds() {
        return SportsSyncCommandResponse.from(syncService.syncRounds());
    }

    @PostMapping("/standings")
    public SportsSyncCommandResponse syncStandings() {
        return SportsSyncCommandResponse.from(syncService.syncStandings());
    }

    // ─── worldcup26.ir endpoints (free, no key) ───────────────────────────

    @PostMapping("/wc26/teams")
    public SportsSyncCommandResponse syncWc26Teams() {
        return SportsSyncCommandResponse.from(wc26SyncService.syncTeams());
    }

    @PostMapping("/wc26/fixtures")
    public SportsSyncCommandResponse syncWc26Fixtures() {
        return SportsSyncCommandResponse.from(wc26SyncService.syncFixtures());
    }

    @PostMapping("/wc26/groups")
    public SportsSyncCommandResponse syncWc26Groups() {
        return SportsSyncCommandResponse.from(wc26SyncService.syncGroups());
    }

    /** Convenience: syncs teams + fixtures + groups in one shot */
    @PostMapping("/wc26/all")
    public List<SportsSyncCommandResponse> syncWc26All() {
        return List.of(
                SportsSyncCommandResponse.from(wc26SyncService.syncTeams()),
                SportsSyncCommandResponse.from(wc26SyncService.syncFixtures()),
                SportsSyncCommandResponse.from(wc26SyncService.syncGroups())
        );
    }

    // ─── response records ─────────────────────────────────────────────────

    public record SportsSyncStatus(
            String provider,
            String mode,
            boolean configured,
            int league,
            int season,
            int livePollingSeconds,
            String activeSource,
            List<SportsSyncJobStatus> jobs
    ) {
        static SportsSyncStatus from(FootballApiProperties properties, Source active) {
            return new SportsSyncStatus(
                    "API-Football",
                    properties.mode(),
                    properties.configured(),
                    properties.league(),
                    properties.season(),
                    properties.livePollingSeconds(),
                    active.name(),
                    List.of(
                            new SportsSyncJobStatus("LIVE_FIXTURES",  "Fixtures en directo",    "PENDING"),
                            new SportsSyncJobStatus("DAILY_FIXTURES", "Fixtures del día",       "PENDING"),
                            new SportsSyncJobStatus("ROUNDS",         "Rondas del torneo",      "PENDING"),
                            new SportsSyncJobStatus("STANDINGS",      "Clasificaciones",        "PENDING"),
                            new SportsSyncJobStatus("WC26_FIXTURES",  "2026 vía worldcup26.ir", "PENDING"),
                            new SportsSyncJobStatus("WC26_TEAMS",     "48 equipos 2026",        "PENDING"),
                            new SportsSyncJobStatus("WC26_GROUPS",    "Grupos 2026",            "PENDING")
                    )
            );
        }
    }

    public record SportsSyncJobStatus(String key, String label, String status) {}

    public record SportsSyncCommandResponse(String syncType, String status, int count) {
        static SportsSyncCommandResponse from(SyncResult r) {
            return new SportsSyncCommandResponse(r.syncType(), r.status(), r.count());
        }
    }
}
