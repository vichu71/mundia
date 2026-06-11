package com.mundia.backend.dashboard;

import static com.mundia.backend.dashboard.DashboardResponse.BracketMatchDto;
import static com.mundia.backend.dashboard.DashboardResponse.BracketRoundDto;
import static com.mundia.backend.dashboard.DashboardResponse.GroupStandingDto;
import static com.mundia.backend.dashboard.DashboardResponse.InitialRankingDto;
import static com.mundia.backend.dashboard.DashboardResponse.MatchDto;
import static com.mundia.backend.dashboard.DashboardResponse.PendingPaymentDto;
import static com.mundia.backend.dashboard.DashboardResponse.PoolDto;
import static com.mundia.backend.dashboard.DashboardResponse.PrizeRowDto;
import static com.mundia.backend.dashboard.DashboardResponse.RankingDto;
import static com.mundia.backend.dashboard.DashboardResponse.RecommendationDto;
import static com.mundia.backend.dashboard.DashboardResponse.TeamStandingDto;

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
                findInitialRanking(poolId),
                findPrizeRows(poolId),
                findBracketRounds(userId, poolId),
                findPendingPayments(poolId),
                recommendations(matches),
                findGroupStandings()
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
                  COUNT(CASE WHEN pm.role IN ('PLAYER','ADMIN') THEN 1 END) members,
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
                  m.kickoff_at,
                  r.name round_name,
                  r.stage
                FROM matches m
                JOIN rounds r ON r.id = m.round_id
                JOIN teams ht ON ht.id = m.home_team_id
                JOIN teams at ON at.id = m.away_team_id
                WHERE (r.stage = 'GROUP_STAGE' AND m.result_source IN (?, 'SIM'))
                   OR r.stage = 'KNOCKOUT'
                ORDER BY
                  CASE r.stage WHEN 'GROUP_STAGE' THEN 0 ELSE 1 END,
                  r.sort_order,
                  m.kickoff_at IS NULL,
                  m.kickoff_at,
                  m.id
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
                    rs.getString("result_source"),
                    rs.getString("round_name"),
                    rs.getString("stage")
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
                    sb.pool_member_id,
                    SUM(sb.points) points,
                    SUM(CASE WHEN sb.category = 'EXACT_RESULT' AND sb.points > 0 THEN 1 ELSE 0 END) exact,
                    SUM(CASE WHEN sb.category = 'WINNER' AND sb.points > 0 THEN 1 ELSE 0 END) winners
                  FROM score_breakdowns sb
                  JOIN prediction_sets ps ON ps.id = sb.prediction_set_id AND ps.type = 'LIVE'
                  GROUP BY sb.pool_member_id
                ) scores ON scores.pool_member_id = pm.id
                LEFT JOIN (
                  SELECT pool_member_id, SUM(current_amount_cents) prize_cents
                  FROM prize_projections
                  GROUP BY pool_member_id
                ) prizes ON prizes.pool_member_id = pm.id
                WHERE p.id = ?
                  AND pm.role IN ('PLAYER','ADMIN')
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

    private List<InitialRankingDto> findInitialRanking(long poolId) {
        return jdbcTemplate.query("""
                SELECT
                  ROW_NUMBER() OVER (ORDER BY SUM(sb.points) DESC, u.display_name ASC) pos,
                  u.display_name name,
                  COALESCE(SUM(sb.points), 0) points,
                  COALESCE(SUM(CASE WHEN sb.category = 'EXACT_RESULT' THEN 1 ELSE 0 END), 0) exact_count,
                  COALESCE(SUM(CASE WHEN sb.category = 'WINNER'       THEN 1 ELSE 0 END), 0) winner_count
                FROM pool_members pm
                JOIN users u ON u.id = pm.user_id
                LEFT JOIN prediction_sets ps ON ps.pool_member_id = pm.id AND ps.type = 'INITIAL'
                LEFT JOIN score_breakdowns sb ON sb.prediction_set_id = ps.id
                WHERE pm.pool_id = ? AND pm.role IN ('PLAYER','ADMIN')
                GROUP BY pm.id, u.display_name
                ORDER BY points DESC, u.display_name ASC
                """, (rs, rowNum) -> new InitialRankingDto(
                rs.getInt("pos"),
                rs.getString("name"),
                rs.getInt("points"),
                rs.getInt("exact_count"),
                rs.getInt("winner_count"),
                rs.getInt("points") == 0 ? "Sin apuesta inicial" : rs.getInt("points") + " pts"
        ), poolId);
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
                SELECT
                  pr.category,
                  pr.percentage_when_perfect_alive pct,
                  COALESCE(SUM(pp.current_amount_cents), 0) awarded_cents,
                  COUNT(DISTINCT pp.pool_member_id) winners,
                  GROUP_CONCAT(DISTINCT u.display_name ORDER BY u.display_name SEPARATOR ', ') winner_names,
                  MAX(pp.status) pp_status
                FROM prize_rules pr
                LEFT JOIN prize_projections pp ON pp.category = pr.category
                  AND pp.current_amount_cents > 0
                  AND EXISTS (
                    SELECT 1 FROM pool_members pm2
                    WHERE pm2.id = pp.pool_member_id AND pm2.pool_id = ?
                  )
                LEFT JOIN pool_members pm ON pm.id = pp.pool_member_id
                LEFT JOIN users u ON u.id = pm.user_id
                WHERE pr.pool_id = ? AND pr.enabled = TRUE
                GROUP BY pr.category, pr.percentage_when_perfect_alive
                ORDER BY FIELD(pr.category, 'PERFECT_WINNERS', 'GENERAL', 'INITIAL_BET', 'EXACT_RESULTS', 'WINNERS', 'CHAMPION')
                """, (rs, rowNum) -> {
            int pct        = rs.getBigDecimal("pct").intValue();
            int awarded    = centsToEuros(rs.getInt("awarded_cents"));
            int winners    = rs.getInt("winners");
            String names   = rs.getString("winner_names");
            String ppStatus = rs.getString("pp_status");

            String state;
            String stateType;
            if ("EXTINCT".equals(ppStatus)) {
                state = "Extinto"; stateType = "closed";
            } else if (winners > 0 && names != null) {
                state = names; stateType = "active";
            } else {
                state = "Pendiente"; stateType = "pending";
            }

            return new PrizeRowDto(
                    prizeLabel(rs.getString("category")),
                    awarded > 0 ? awarded : Math.round(pot * pct / 100.0f),
                    state,
                    stateType,
                    winners,
                    pct
            );
        }, poolId, poolId);
    }

    private List<BracketRoundDto> findBracketRounds(long userId, long poolId) {
        Source active = sourceConfig.getActive();
        String sourceFilter = active == Source.API_FOOTBALL ? "API_FOOTBALL" : "WC26_IR";
        String sourceIn = "('" + sourceFilter + "', 'SIM')";

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
                JOIN matches m ON m.round_id = r.id
                  AND (r.stage = 'GROUP_STAGE' AND m.result_source IN (?, 'SIM')
                       OR r.stage = 'KNOCKOUT')
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
                            row.matchId(),
                            valueOrPending(row.home()),
                            valueOrFlag(row.homeFlag()),
                            valueOrPending(row.away()),
                            valueOrFlag(row.awayFlag()),
                            pred,
                            real,
                            winner,
                            "CLOSED".equals(row.status()) || "FINISHED".equals(row.status())
                    ));
        }

        return rounds.entrySet().stream()
                .map(entry -> new BracketRoundDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<GroupStandingDto> findGroupStandings() {
        Source active = sourceConfig.getActive();
        String src = active == Source.API_FOOTBALL ? "API_FOOTBALL" : "WC26_IR";

        List<Object[]> rows = jdbcTemplate.query("""
                SELECT
                  all_teams.round_name,
                  all_teams.sort_order,
                  all_teams.team_name,
                  all_teams.flag,
                  COALESCE(stats.played, 0) played,
                  COALESCE(stats.won,    0) won,
                  COALESCE(stats.drawn,  0) drawn,
                  COALESCE(stats.lost,   0) lost,
                  COALESCE(stats.gf,     0) gf,
                  COALESCE(stats.ga,     0) ga,
                  COALESCE(stats.pts,    0) pts
                FROM (
                  SELECT DISTINCT r.name round_name, r.sort_order, ht.id team_id, ht.name team_name, ht.country_code flag
                  FROM rounds r
                  JOIN matches m  ON m.round_id = r.id AND m.result_source = ?
                  JOIN teams ht   ON ht.id = m.home_team_id
                  WHERE r.stage = 'GROUP_STAGE'
                  UNION
                  SELECT DISTINCT r.name, r.sort_order, at.id, at.name, at.country_code
                  FROM rounds r
                  JOIN matches m  ON m.round_id = r.id AND m.result_source = ?
                  JOIN teams at   ON at.id = m.away_team_id
                  WHERE r.stage = 'GROUP_STAGE'
                ) all_teams
                LEFT JOIN (
                  SELECT team_id, round_name,
                    SUM(played) played, SUM(won) won, SUM(drawn) drawn,
                    SUM(lost) lost, SUM(gf) gf, SUM(ga) ga, SUM(pts) pts
                  FROM (
                    SELECT r.name round_name, ht.id team_id,
                      1 played,
                      CASE WHEN m.home_goals > m.away_goals THEN 1 ELSE 0 END won,
                      CASE WHEN m.home_goals = m.away_goals THEN 1 ELSE 0 END drawn,
                      CASE WHEN m.home_goals < m.away_goals THEN 1 ELSE 0 END lost,
                      m.home_goals gf, m.away_goals ga,
                      CASE WHEN m.home_goals > m.away_goals THEN 3
                           WHEN m.home_goals = m.away_goals THEN 1 ELSE 0 END pts
                    FROM matches m
                    JOIN rounds r ON r.id = m.round_id AND r.stage = 'GROUP_STAGE'
                    JOIN teams ht ON ht.id = m.home_team_id
                    WHERE m.result_source = ? AND m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL
                    UNION ALL
                    SELECT r.name, at.id,
                      1,
                      CASE WHEN m.away_goals > m.home_goals THEN 1 ELSE 0 END,
                      CASE WHEN m.home_goals = m.away_goals THEN 1 ELSE 0 END,
                      CASE WHEN m.away_goals < m.home_goals THEN 1 ELSE 0 END,
                      m.away_goals, m.home_goals,
                      CASE WHEN m.away_goals > m.home_goals THEN 3
                           WHEN m.home_goals = m.away_goals THEN 1 ELSE 0 END
                    FROM matches m
                    JOIN rounds r ON r.id = m.round_id AND r.stage = 'GROUP_STAGE'
                    JOIN teams at ON at.id = m.away_team_id
                    WHERE m.result_source = ? AND m.home_goals IS NOT NULL AND m.away_goals IS NOT NULL
                  ) ms
                  GROUP BY team_id, round_name
                ) stats ON stats.team_id = all_teams.team_id AND stats.round_name = all_teams.round_name
                ORDER BY all_teams.sort_order, pts DESC, (COALESCE(stats.gf,0) - COALESCE(stats.ga,0)) DESC, all_teams.team_name
                """,
                (rs, i) -> new Object[]{
                        rs.getString("round_name"),
                        rs.getString("team_name"),
                        rs.getString("flag"),
                        rs.getInt("played"),
                        rs.getInt("won"),
                        rs.getInt("drawn"),
                        rs.getInt("lost"),
                        rs.getInt("gf"),
                        rs.getInt("ga"),
                        rs.getInt("pts")
                }, src, src, src, src);

        Map<String, List<TeamStandingDto>> byRound = new LinkedHashMap<>();
        for (Object[] row : rows) {
            byRound.computeIfAbsent((String) row[0], k -> new ArrayList<>())
                    .add(new TeamStandingDto(
                            (String) row[1], (String) row[2],
                            (int) row[3], (int) row[4], (int) row[5],
                            (int) row[6], (int) row[7], (int) row[8], (int) row[9]
                    ));
        }

        return byRound.entrySet().stream()
                .map(e -> new GroupStandingDto(e.getKey(), e.getValue()))
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

