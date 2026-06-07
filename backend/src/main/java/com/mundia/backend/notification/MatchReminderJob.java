package com.mundia.backend.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MatchReminderJob {

    private static final Logger log = LoggerFactory.getLogger(MatchReminderJob.class);
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("EEE d MMM, HH:mm").withLocale(java.util.Locale.forLanguageTag("es"))
                    .withZone(ZoneId.of("Europe/Madrid"));
    private static final int CUTOFF_MINUTES = 60;

    private final JdbcTemplate jdbc;
    private final NotificationService notificationService;

    public MatchReminderJob(JdbcTemplate jdbc, NotificationService notificationService) {
        this.jdbc = jdbc;
        this.notificationService = notificationService;
    }

    // Runs every day at 09:00 UTC (11:00 Madrid summer time)
    @Scheduled(cron = "0 0 9 * * *", zone = "UTC")
    public void sendDailyReminders() {
        log.info("MatchReminderJob starting");

        // Find all matches starting in the next 24h that are still open for prediction
        Instant now    = Instant.now();
        Instant in24h  = now.plusSeconds(24 * 3600);
        Instant cutoff = now.plusSeconds(CUTOFF_MINUTES * 60L);

        List<MatchRow> todayMatches = jdbc.query("""
                SELECT m.id, ht.name home, at.name away, m.kickoff_at
                FROM matches m
                JOIN teams ht ON ht.id = m.home_team_id
                JOIN teams at ON at.id = m.away_team_id
                WHERE m.kickoff_at >= ? AND m.kickoff_at <= ?
                  AND m.kickoff_at > ?
                ORDER BY m.kickoff_at
                """,
                (rs, i) -> new MatchRow(
                        rs.getLong("id"),
                        rs.getString("home"),
                        rs.getString("away"),
                        rs.getTimestamp("kickoff_at").toInstant()
                ),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(in24h),
                java.sql.Timestamp.from(cutoff.minusSeconds(1)));

        if (todayMatches.isEmpty()) {
            log.info("No matches today, skipping reminders");
            return;
        }

        // Find users who haven't predicted all today's matches
        List<long[]> matchIds = todayMatches.stream()
                .map(m -> new long[]{ m.id() })
                .toList();

        // For each match, find users who haven't predicted it
        Map<Long, UserInfo> usersToNotify = new LinkedHashMap<>();
        Map<Long, List<MatchRow>> pendingByUser = new LinkedHashMap<>();

        for (MatchRow match : todayMatches) {
            List<UserInfo> missing = jdbc.query("""
                    SELECT DISTINCT u.id, u.display_name, u.email
                    FROM pool_members pm
                    JOIN users u ON u.id = pm.user_id
                    WHERE pm.role IN ('PLAYER','ADMIN')
                      AND u.email IS NOT NULL AND u.email != ''
                      AND NOT EXISTS (
                        SELECT 1
                        FROM match_predictions mp
                        JOIN prediction_sets ps ON ps.id = mp.prediction_set_id
                        WHERE ps.pool_member_id = pm.id
                          AND ps.type = 'LIVE'
                          AND mp.match_id = ?
                      )
                    """,
                    (rs, i) -> new UserInfo(rs.getLong("id"), rs.getString("display_name"), rs.getString("email")),
                    match.id());

            for (UserInfo user : missing) {
                usersToNotify.put(user.id(), user);
                pendingByUser.computeIfAbsent(user.id(), k -> new ArrayList<>()).add(match);
            }
        }

        log.info("Sending reminders to {} users", usersToNotify.size());

        for (UserInfo user : usersToNotify.values()) {
            List<NotificationService.MatchReminder> reminders = pendingByUser.get(user.id()).stream()
                    .map(m -> new NotificationService.MatchReminder(
                            m.home(), "", m.away(), "",
                            FMT.format(m.kickoff()),
                            FMT.format(m.kickoff().minusSeconds(CUTOFF_MINUTES * 60L))
                    ))
                    .toList();
            notificationService.sendMatchReminder(user.email(), user.displayName(), reminders);
        }

        log.info("MatchReminderJob done");
    }

    private record MatchRow(long id, String home, String away, Instant kickoff) {}
    private record UserInfo(long id, String displayName, String email) {}
}
