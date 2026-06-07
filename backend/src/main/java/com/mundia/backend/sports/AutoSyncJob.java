package com.mundia.backend.sports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps match data fresh automatically so the app works even when nobody
 * is watching. Critical during phase transitions (groups → knockouts).
 *
 * Schedule summary:
 *  - Every 4h:  full WC26 sync (teams + fixtures + groups)
 *  - Every 30s: live fixtures sync when a match is in progress
 *  - Every 15min: fixtures sync on match days
 */
@Component
public class AutoSyncJob {

    private static final Logger log = LoggerFactory.getLogger(AutoSyncJob.class);

    private final WorldCup26SyncService wc26;
    private final SportsSyncService apiFootball;
    private final SyncSourceConfig sourceConfig;
    private final JdbcTemplate jdbc;

    public AutoSyncJob(WorldCup26SyncService wc26,
                       SportsSyncService apiFootball,
                       SyncSourceConfig sourceConfig,
                       JdbcTemplate jdbc) {
        this.wc26         = wc26;
        this.apiFootball  = apiFootball;
        this.sourceConfig = sourceConfig;
        this.jdbc         = jdbc;
    }

    // ─── Full WC26 sync every 4 hours ────────────────────────────────────────
    // Picks up new knockout fixtures as soon as worldcup26.ir publishes them.
    @Scheduled(cron = "0 0 */4 * * *", zone = "UTC")
    public void fullWc26Sync() {
        log.info("[AutoSync] Starting full WC26 sync");
        try {
            wc26.syncTeams();
            wc26.syncFixtures();
            wc26.syncGroups();
            log.info("[AutoSync] Full WC26 sync completed");
        } catch (Exception e) {
            log.error("[AutoSync] Full WC26 sync failed: {}", e.getMessage());
        }
    }

    // ─── Live fixtures every 30s when matches are in progress ────────────────
    @Scheduled(fixedDelay = 30_000)
    public void liveSyncIfNeeded() {
        if (!isMatchInProgress()) return;
        try {
            if (sourceConfig.getActive() == SyncSourceConfig.Source.WC26_IR) {
                wc26.syncFixtures();
            } else {
                apiFootball.syncLiveFixtures();
            }
        } catch (Exception e) {
            log.warn("[AutoSync] Live sync failed: {}", e.getMessage());
        }
    }

    // ─── Fixtures sync every 15min on match days ─────────────────────────────
    @Scheduled(cron = "0 */15 * * * *", zone = "UTC")
    public void matchDaySync() {
        if (!isMatchDay()) return;
        log.info("[AutoSync] Match day — syncing fixtures");
        try {
            if (sourceConfig.getActive() == SyncSourceConfig.Source.WC26_IR) {
                wc26.syncFixtures();
            } else {
                apiFootball.syncTodayFixtures();
            }
        } catch (Exception e) {
            log.warn("[AutoSync] Match day sync failed: {}", e.getMessage());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private boolean isMatchInProgress() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM matches WHERE status = 'LIVE'",
                Integer.class);
        return count != null && count > 0;
    }

    private boolean isMatchDay() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM matches
                WHERE DATE(kickoff_at) = CURDATE()
                """, Integer.class);
        return count != null && count > 0;
    }
}
