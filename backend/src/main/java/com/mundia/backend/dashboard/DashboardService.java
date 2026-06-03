package com.mundia.backend.dashboard;

import static com.mundia.backend.dashboard.DashboardResponse.BracketMatchDto;
import static com.mundia.backend.dashboard.DashboardResponse.BracketRoundDto;
import static com.mundia.backend.dashboard.DashboardResponse.InitialRankingDto;
import static com.mundia.backend.dashboard.DashboardResponse.MatchDto;
import static com.mundia.backend.dashboard.DashboardResponse.PendingPaymentDto;
import static com.mundia.backend.dashboard.DashboardResponse.PoolDto;
import static com.mundia.backend.dashboard.DashboardResponse.PrizeRowDto;
import static com.mundia.backend.dashboard.DashboardResponse.RankingDto;
import static com.mundia.backend.dashboard.DashboardResponse.RecommendationDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mundia.backend.sports.SyncSourceConfig;
import com.mundia.backend.sports.SyncSourceConfig.Source;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;
    private final SyncSourceConfig sourceConfig;

    public DashboardService(JdbcTemplate jdbcTemplate, SyncSourceConfig sourceConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.sourceConfig = sourceConfig;
    }

    public DashboardResponse getDashboard(long userId, long poolId) {
        // Verify user is a member of this pool
        List<String> roles = jdbcTemplate.query(
                "SELECT role FROM pool_members WHERE pool_id = ? AND user_id = ?",
                (rs, i) -> rs.getString("role"), poolId, userId);
        if (roles.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Not a member of this pool");
        }

        List<MatchDto> matches = findMatches(userId, poolId);
        List<RankingDto> ranking = findRanking(poolId);

        return new DashboardResponse(
                findPools(userId),
                matches,
                ranking,
                findInitialRanking(ranking),
                findPrizeRows(poolId),
                findBracketRounds(userId, poolId),
                findPendingPayments(poolId),
                recommendations(matches)
        );
    }

    private List<PoolDto> findPools(long userId) {
        return jdbcTemplate.query("""
                SELECT
                  p.id,
                  p.name,
                  p.invite_code code,
                  p.status,
                  pm_user.role user_role,
                  COUNT(CASE WHEN pm.role = 'PLAYER' THEN 1 END) members,
                  COUNT(CASE WHEN pay.status = 'CONFIRMED' THEN 1 END) paid,
                  COALESCE(SUM(CASE WHEN pay.status = 'CONFIRMED' THEN pay.amount_cents ELSE 0 END), 0) pot_cents
                FROM pools p
                JOIN pool_members pm_user ON pm_user.pool_id = p.id AND pm_user.user_id = ?
                LEFT JOIN pool_members pm ON pm.pool_id = p.id
                LEFT JOIN payments pay ON pay.pool_member_id = pm.id
                WHERE p.status <> 'DELETED'
                GROUP BY p.id, p.name, p.invite_code, p.status, pm_user.role
                ORDER BY p.created_at
                """, (rs, rowNum) -> new PoolDto(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("code"),
                statusLabel(rs.getString("status")),
                statusType(rs.getString("status")),
                rs.getString("user_role"),
                rs.getInt("members"),
                rs.getInt("paid"),
                centsToEuros(rs.getInt("pot_cents"))
        ), userId);
    }

    private List<MatchDto> findMatches(long userId, long poolId) {
        Source active = sourceConfig.getActive();
        String sourceFilter = active == Source.API_FOOTBALL ? "API_FOOTBALL" : "WC26_IR";

        // Load user's predictions into a map matchId → "hg - ag"
        java.util.Map<Long, String> userPreds = new java.util.HashMap<>();
        jdbcTemplate.query("""
                SELECT mp.match_id, mp.home_goals, mp.away_goals
                FROM match_predictions mp
                JOIN prediction_sets ps ON ps.id = mp.prediction_set_id
                JOIN pool_members pm    ON pm.id = ps.pool_member_id
                JOIN pools p            ON p.id  = pm.pool_id
                WHERE pm.user_id = ? AND p.id = ? AND ps.type = 'LIVE'
                """,
                (rs, i) -> {
                    userPreds.put(rs.getLong("match_id"),
                            rs.getInt("home_goals") + " - " + rs.getInt("away_goals"));
                    return null;
                }, userId, poolId);

        return jdbcTemplate.query("""
                SELECT
                  m.id,
                  ht.name home,
                  ht.country_code home_fl,
                  at.name away,
                  at.country_code away_fl,
                  m.status,
                  m.home_goals,
                  m.away_goals,
                  m.result_source,
                  m.kickoff_at
                FROM matches m
                JOIN rounds r ON r.id = m.round_id
                JOIN teams ht ON ht.id = m.home_team_id
                JOIN teams at ON at.id = m.away_team_id
                WHERE m.result_source = ?
                ORDER BY m.kickoff_at IS NULL, m.kickoff_at, m.id
                """, (rs, rowNum) -> {
            long matchId  = rs.getLong("id");
            Integer homeGoals = nullableInt(rs, "home_goals");
            Integer awayGoals = nullableInt(rs, "away_goals");
            String status = rs.getString("status");
            String real   = homeGoals == null || awayGoals == null ? "Pend." : homeGoals + " - " + awayGoals;
            String pred   = userPreds.getOrDefault(matchId, "? - ?");
            java.sql.Timestamp kickoffTs = rs.getTimestamp("kickoff_at");
            String kickoff = kickoffTs != null ? kickoffTs.toInstant().toString() : null;
            return new MatchDto(
                    matchId,
                    rs.getString("home"),
                    rs.getString("away"),
                    rs.getString("home_fl"),
                    rs.getString("away_fl"),
                    pred,
                    real,
                    null,
                    matchStatusLabel(status),
                    matchStatusType(status),
                    matchNote(rs.getString("result_source"), homeGoals, awayGoals),
                    kickoff,
                    rs.getString("result_source")
            );
        }, sourceFilter);
    }

    private List<RankingDto> findRanking(long poolId) {
        return jdbcTemplate.query("""
                SELECT
                  ROW_NUMBER() OVER (ORDER BY points DESC, u.display_name ASC) pos,
                  u.display_name name,
                  COALESCE(points, 0) points,
                  COALESCE(exact, 0) exact,
                  COALESCE(winners, 0) winners,
                  COALESCE(prize_cents, 0) prize_cents
                FROM pool_members pm
                JOIN pools p ON p.id = pm.pool_id
                JOIN users u ON u.id = pm.user_id
                LEFT JOIN (
                  SELECT
                    pool_member_id,
                    SUM(points) points,
                    SUM(CASE WHEN category = 'EXACT_RESULT' AND points > 0 THEN 1 ELSE 0 END) exact,
                    SUM(CASE WHEN category = 'WINNER' AND points > 0 THEN 1 ELSE 0 END) winners
                  FROM score_breakdowns
                  GROUP BY pool_member_id
                ) scores ON scores.pool_member_id = pm.id
                LEFT JOIN (
                  SELECT pool_member_id, SUM(current_amount_cents) prize_cents
                  FROM prize_projections
                  GROUP BY pool_member_id
                ) prizes ON prizes.pool_member_id = pm.id
                WHERE p.id = ?
                  AND pm.role = 'PLAYER'
                ORDER BY points DESC, u.display_name ASC
                """, (rs, rowNum) -> new RankingDto(
                rs.getInt("pos"),
                rs.getString("name"),
                initials(rs.getString("name")),
                rs.getInt("points"),
                rs.getInt("exact"),
                rs.getInt("winners"),
                centsToEuros(rs.getInt("prize_cents")),
                "=",
                true
        ), poolId);
    }

    private List<InitialRankingDto> findInitialRanking(List<RankingDto> ranking) {
        return ranking.stream()
                .limit(3)
                .map(row -> new InitialRankingDto(
                        row.pos(),
                        row.name(),
                        0,
                        0,
                        0,
                        "Sin bonus todavia"
                ))
                .toList();
    }

    private List<PrizeRowDto> findPrizeRows(long poolId) {
        Integer potCents = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(pay.amount_cents), 0)
                FROM payments pay
                JOIN pool_members pm ON pm.id = pay.pool_member_id
                WHERE pm.pool_id = ? AND pay.status = 'CONFIRMED'
                """, Integer.class, poolId);
        int pot = centsToEuros(potCents != null ? potCents : 0);

        return jdbcTemplate.query("""
                SELECT category, percentage_when_perfect_alive pct
                FROM prize_rules pr
                WHERE pr.pool_id = ? AND pr.enabled = TRUE
                ORDER BY FIELD(category, 'PERFECT_WINNERS', 'GENERAL', 'INITIAL_BET', 'EXACT_RESULTS', 'WINNERS', 'CHAMPION')
                """, (rs, rowNum) -> {
            int pct = rs.getBigDecimal("pct").intValue();
            return new PrizeRowDto(
                    prizeLabel(rs.getString("category")),
                    Math.round(pot * pct / 100.0f),
                    "Pendiente",
                    "pending",
                    0,
                    pct
            );
        }, poolId);
    }

    private List<BracketRoundDto> findBracketRounds(long userId, long poolId) {
        Source active = sourceConfig.getActive();
        String sourceFilter = active == Source.API_FOOTBALL ? "API_FOOTBALL" : "WC26_IR";

        // Load user predictions for all matches in this pool
        java.util.Map<Long, String> userPreds = new java.util.HashMap<>();
        jdbcTemplate.query("""
                SELECT mp.match_id, mp.home_goals, mp.away_goals
                FROM match_predictions mp
                JOIN prediction_sets ps ON ps.id = mp.prediction_set_id
                JOIN pool_members pm    ON pm.id = ps.pool_member_id
                WHERE pm.user_id = ? AND pm.pool_id = ? AND ps.type = 'LIVE'
                """,
                (rs, i) -> {
                    userPreds.put(rs.getLong("match_id"),
                            rs.getInt("home_goals") + "-" + rs.getInt("away_goals"));
                    return null;
                }, userId, poolId);

        List<BracketRow> rows = jdbcTemplate.query("""
                SELECT
                  r.name round_name,
                  r.stage,
                  r.sort_order,
                  m.id match_id,
                  ht.name home,
                  ht.country_code home_fl,
                  at.name away,
                  at.country_code away_fl,
                  m.home_goals,
                  m.away_goals,
                  m.status
                FROM rounds r
                JOIN matches m ON m.round_id = r.id AND m.result_source = ?
                LEFT JOIN teams ht ON ht.id = m.home_team_id
                LEFT JOIN teams at ON at.id = m.away_team_id
                ORDER BY
                  CASE r.stage WHEN 'GROUP_STAGE' THEN 0 ELSE 1 END,
                  r.sort_order,
                  m.kickoff_at IS NULL,
                  m.kickoff_at,
                  m.id
                """, (rs, rowNum) -> new BracketRow(
                rs.getLong("match_id"),
                rs.getString("round_name"),
                rs.getString("home"),
                rs.getString("home_fl"),
                rs.getString("away"),
                rs.getString("away_fl"),
                nullableInt(rs, "home_goals"),
                nullableInt(rs, "away_goals"),
                rs.getString("status")
        ), sourceFilter);

        Map<String, List<BracketMatchDto>> rounds = new LinkedHashMap<>();
        for (BracketRow row : rows) {
            String real = row.homeGoals() == null || row.awayGoals() == null
                    ? "Pend."
                    : row.homeGoals() + "-" + row.awayGoals();
            String winner = "Pendiente";
            if (row.homeGoals() != null && row.awayGoals() != null) {
                winner = row.homeGoals() > row.awayGoals()
                        ? row.home()
                        : row.awayGoals() > row.homeGoals() ? row.away() : "Empate";
            }
            String pred = userPreds.getOrDefault(row.matchId(), "?-?");
            rounds.computeIfAbsent(row.roundName(), ignored -> new ArrayList<>())
                    .add(new BracketMatchDto(
                            valueOrPending(row.home()),
                            valueOrFlag(row.homeFlag()),
                            valueOrPending(row.away()),
                            valueOrFlag(row.awayFlag()),
                            pred,
                            real,
                            winner,
                            "CLOSED".equals(row.status())
                    ));
        }

        return rounds.entrySet().stream()
                .map(entry -> new BracketRoundDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<PendingPaymentDto> findPendingPayments(long poolId) {
        return jdbcTemplate.query("""
                SELECT pay.id, u.display_name
                FROM payments pay
                JOIN pool_members pm ON pm.id = pay.pool_member_id
                JOIN users u ON u.id = pm.user_id
                WHERE pm.pool_id = ? AND pay.status = 'PENDING'
                ORDER BY u.display_name
                """, (rs, rowNum) -> new PendingPaymentDto(rs.getLong("id"), rs.getString("display_name")),
                poolId);
    }

    private List<RecommendationDto> recommendations(List<MatchDto> matches) {
        long openMatches = matches.stream().filter(match -> "open".equals(match.statusType())).count();
        return List.of(
                new RecommendationDto("info", "Datos cargados desde MariaDB. Ya no salen del mock del frontend."),
                new RecommendationDto("info", "Partidos abiertos en base de datos: " + openMatches + "."),
                new RecommendationDto("info", "API-Football se sincronizara desde Spring Boot cuando configures la clave.")
        );
    }

    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static int centsToEuros(int cents) {
        return Math.round(cents / 100.0f);
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "OPEN" -> "Abierta";
            case "DRAFT" -> "Borrador";
            case "CLOSED" -> "Cerrada";
            default -> status;
        };
    }

    private static String statusType(String status) {
        return switch (status) {
            case "OPEN" -> "open";
            case "DRAFT" -> "draft";
            case "CLOSED" -> "closed";
            default -> "pending";
        };
    }

    private static String matchStatusLabel(String status) {
        return switch (status) {
            case "OPEN" -> "Abierto";
            case "LIVE" -> "En directo";
            case "CLOSED", "FINISHED" -> "Cerrado";
            default -> "Pendiente";
        };
    }

    private static String matchStatusType(String status) {
        return switch (status) {
            case "OPEN" -> "open";
            case "LIVE" -> "warning";
            case "CLOSED", "FINISHED" -> "closed";
            default -> "pending";
        };
    }

    private static String matchNote(String resultSource, Integer homeGoals, Integer awayGoals) {
        if (homeGoals == null || awayGoals == null) {
            return "Sin resultado real. Fuente: " + resultSource + ".";
        }
        return "Resultado real cargado desde " + resultSource + ".";
    }

    private static String prizeLabel(String category) {
        return switch (category) {
            case "PERFECT_WINNERS" -> "Pleno de ganadores";
            case "GENERAL" -> "Clasificacion general";
            case "INITIAL_BET" -> "Mejor apuesta inicial";
            case "EXACT_RESULTS" -> "Mas exactos";
            case "WINNERS" -> "Mas ganadores";
            case "CHAMPION" -> "Campeon acertado";
            default -> category;
        };
    }

    private static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }

    private static String valueOrPending(String value) {
        return value == null ? "Pendiente" : value;
    }

    private static String valueOrFlag(String value) {
        return value == null ? "un" : value;
    }

    private record BracketRow(
            long matchId,
            String roundName,
            String home,
            String homeFlag,
            String away,
            String awayFlag,
            Integer homeGoals,
            Integer awayGoals,
            String status
    ) {
    }
}
