package com.mundia.backend.admin;

import com.mundia.backend.notification.NotificationService;
import com.mundia.backend.scoring.ScoringService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {


    record SetResultRequest(int homeGoals, int awayGoals) {}
    record AddMemberRequest(long poolId, String displayName, String email) {}
    record MemberDto(long memberId, long userId, String displayName, String email,
                     String role, String paymentStatus, String joinedAt) {}

    private final JdbcTemplate jdbc;
    private final ScoringService scoringService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notifications;

    public AdminController(JdbcTemplate jdbc, ScoringService scoringService,
                           PasswordEncoder passwordEncoder, NotificationService notifications) {
        this.jdbc = jdbc;
        this.scoringService = scoringService;
        this.passwordEncoder = passwordEncoder;
        this.notifications = notifications;
    }

    // ─── Auth helpers ─────────────────────────────────────────────────────────

    private long userId(JwtAuthenticationToken auth) {
        return Long.parseLong(auth.getToken().getSubject());
    }

    private void requirePoolAdmin(long userId, long poolId) {
        String role = jdbc.query(
                "SELECT role FROM pool_members WHERE pool_id = ? AND user_id = ?",
                (rs, i) -> rs.getString("role"), poolId, userId)
                .stream().findFirst().orElse("");
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el admin de la porra puede realizar esta acción");
        }
    }

    private void requireAnyAdmin(long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pool_members WHERE user_id = ? AND role = 'ADMIN'",
                Integer.class, userId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo los admins pueden realizar esta acción");
        }
    }

    // ─── Listar miembros ──────────────────────────────────────────────────────

    @GetMapping("/members/{poolId}")
    public List<MemberDto> listMembers(@PathVariable long poolId, JwtAuthenticationToken auth) {
        requirePoolAdmin(userId(auth), poolId);
        return jdbc.query("""
                SELECT
                  pm.id            member_id,
                  u.id             user_id,
                  u.display_name,
                  u.email,
                  pm.role,
                  pm.joined_at,
                  COALESCE(pay.status, 'NONE') payment_status
                FROM pool_members pm
                JOIN users u ON u.id = pm.user_id
                LEFT JOIN payments pay ON pay.pool_member_id = pm.id
                WHERE pm.pool_id = ?
                ORDER BY pm.role DESC, u.display_name
                """,
                (rs, i) -> new MemberDto(
                        rs.getLong("member_id"),
                        rs.getLong("user_id"),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getString("role"),
                        rs.getString("payment_status"),
                        rs.getString("joined_at")),
                poolId);
    }

    // ─── Eliminar miembro de la porra ─────────────────────────────────────────

    @DeleteMapping("/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable long memberId, JwtAuthenticationToken auth) {
        long poolId = jdbc.query(
                "SELECT pool_id FROM pool_members WHERE id = ?",
                (rs, i) -> rs.getLong("pool_id"), memberId)
                .stream().findFirst().orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found: " + memberId));
        requirePoolAdmin(userId(auth), poolId);
        // Cascade: score_breakdowns, prize_projections, champion_predictions,
        //          match_predictions, bracket_predictions, prediction_sets,
        //          payments, pool_member
        jdbc.update("DELETE FROM score_breakdowns WHERE pool_member_id = ?", memberId);
        jdbc.update("DELETE FROM prize_projections WHERE pool_member_id = ?", memberId);
        jdbc.update("DELETE FROM champion_predictions WHERE pool_member_id = ?", memberId);
        jdbc.update("""
                DELETE mp FROM match_predictions mp
                JOIN prediction_sets ps ON ps.id = mp.prediction_set_id
                WHERE ps.pool_member_id = ?
                """, memberId);
        jdbc.update("""
                DELETE bp FROM bracket_predictions bp
                JOIN prediction_sets ps ON ps.id = bp.prediction_set_id
                WHERE ps.pool_member_id = ?
                """, memberId);
        jdbc.update("DELETE FROM prediction_sets WHERE pool_member_id = ?", memberId);
        jdbc.update("DELETE FROM payments WHERE pool_member_id = ?", memberId);
        int rows = jdbc.update("DELETE FROM pool_members WHERE id = ?", memberId);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found: " + memberId);
        }
    }

    // ─── Recalcular scoring manualmente ──────────────────────────────────────

    @PostMapping("/scoring/recalculate")
    public Map<String, Object> recalculateScoring(JwtAuthenticationToken auth) {
        requireAnyAdmin(userId(auth));
        int processed = scoringService.recalculateAll();
        return Map.of("processed", processed);
    }

    // ─── Eliminar porra (soft-delete) ─────────────────────────────────────────

    @DeleteMapping("/pools/{poolId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePool(@PathVariable long poolId, JwtAuthenticationToken auth) {
        requirePoolAdmin(userId(auth), poolId);
        int rows = jdbc.update("UPDATE pools SET status = 'DELETED' WHERE id = ?", poolId);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Porra no encontrada: " + poolId);
        }
    }

    // ─── Confirmar pago ───────────────────────────────────────────────────────

    @PostMapping("/payments/{id}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPayment(@PathVariable long id, JwtAuthenticationToken auth) {
        long poolId = jdbc.query("""
                SELECT pm.pool_id FROM payments p
                JOIN pool_members pm ON pm.id = p.pool_member_id
                WHERE p.id = ?
                """,
                (rs, i) -> rs.getLong("pool_id"), id)
                .stream().findFirst().orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + id));
        requirePoolAdmin(userId(auth), poolId);
        int rows = jdbc.update("""
                UPDATE payments SET status = 'CONFIRMED', confirmed_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND status = 'PENDING'
                """, id);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found or already confirmed");
        }
    }

    // ─── Resultado manual ─────────────────────────────────────────────────────

    @PostMapping("/matches/{id}/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setMatchResult(@PathVariable long id, @RequestBody SetResultRequest req,
                               JwtAuthenticationToken auth) {
        requireAnyAdmin(userId(auth));
        int rows = jdbc.update("""
                UPDATE matches
                SET home_goals = ?, away_goals = ?, status = 'CLOSED',
                    manual_override = TRUE, result_source = 'MANUAL',
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                """, req.homeGoals(), req.awayGoals(), id);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found: " + id);
        }
        scoringService.calculateForMatch(id);
    }

    // ─── Añadir participante ──────────────────────────────────────────────────

    @PostMapping("/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMember(@RequestBody AddMemberRequest req, JwtAuthenticationToken auth) {
        if (req.displayName() == null || req.displayName().isBlank() ||
            req.email() == null || req.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName and email required");
        }
        requirePoolAdmin(userId(auth), req.poolId());

        // Obtener invite code de la porra para usarlo como contraseña inicial
        String inviteCode = jdbc.queryForObject(
                "SELECT invite_code FROM pools WHERE id = ?", String.class, req.poolId());
        if (inviteCode == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pool not found: " + req.poolId());
        }

        // Get or create user — contraseña inicial = invite code
        List<Long> userIds = jdbc.query(
                "SELECT id FROM users WHERE email = ?",
                (rs, i) -> rs.getLong("id"),
                req.email().trim().toLowerCase());

        long userId;
        boolean isNewUser;
        if (!userIds.isEmpty()) {
            userId = userIds.get(0);
            isNewUser = false;
        } else {
            String passwordHash = passwordEncoder.encode(inviteCode);
            jdbc.update("INSERT INTO users (email, display_name, password_hash) VALUES (?, ?, ?)",
                    req.email().trim().toLowerCase(), req.displayName().trim(), passwordHash);
            Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            if (id == null) throw new IllegalStateException("Failed to create user");
            userId = id;
            isNewUser = true;
        }

        long poolId = req.poolId();

        // Idempotent: skip if already a member
        List<Long> memberIds = jdbc.query(
                "SELECT id FROM pool_members WHERE pool_id = ? AND user_id = ?",
                (rs, i) -> rs.getLong("id"),
                poolId, userId);
        if (!memberIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member");
        }

        // Create pool_member
        jdbc.update("""
                INSERT INTO pool_members (pool_id, user_id, role, status, joined_at)
                VALUES (?, ?, 'PLAYER', 'ACTIVE', CURRENT_TIMESTAMP(6))
                """, poolId, userId);
        Long memberId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (memberId == null) throw new IllegalStateException("Failed to create pool member");

        // Create pending payment
        int entryFee = jdbc.queryForObject(
                "SELECT entry_fee_cents FROM pools WHERE id = ?", Integer.class, poolId);
        jdbc.update("""
                INSERT INTO payments (pool_member_id, amount_cents, method, status, notes)
                VALUES (?, ?, 'BIZUM', 'PENDING', 'Pendiente de confirmar por admin')
                """, memberId, entryFee);

        // Notify the new participant by email
        String poolName = jdbc.queryForObject(
                "SELECT name FROM pools WHERE id = ?", String.class, poolId);
        notifications.sendPoolInvite(
                req.email().trim().toLowerCase(),
                req.displayName().trim(),
                poolName != null ? poolName : "Mundia",
                isNewUser ? inviteCode : null);   // include password only for new users
    }
}
