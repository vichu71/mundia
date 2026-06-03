package com.mundia.backend.admin;

import com.mundia.backend.scoring.ScoringService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {


    record SetResultRequest(int homeGoals, int awayGoals) {}
    record AddMemberRequest(long poolId, String displayName, String email) {}

    private final JdbcTemplate jdbc;
    private final ScoringService scoringService;

    public AdminController(JdbcTemplate jdbc, ScoringService scoringService) {
        this.jdbc = jdbc;
        this.scoringService = scoringService;
    }

    // ─── Recalcular scoring manualmente ──────────────────────────────────────

    @PostMapping("/scoring/recalculate")
    public Map<String, Object> recalculateScoring() {
        int processed = scoringService.recalculateAll();
        return Map.of("processed", processed);
    }

    // ─── Confirmar pago ───────────────────────────────────────────────────────

    @PostMapping("/payments/{id}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPayment(@PathVariable long id) {
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
    public void setMatchResult(@PathVariable long id, @RequestBody SetResultRequest req) {
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
    public void addMember(@RequestBody AddMemberRequest req) {
        if (req.displayName() == null || req.displayName().isBlank() ||
            req.email() == null || req.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName and email required");
        }

        // Get or create user
        List<Long> userIds = jdbc.query(
                "SELECT id FROM users WHERE email = ?",
                (rs, i) -> rs.getLong("id"),
                req.email());

        long userId;
        if (!userIds.isEmpty()) {
            userId = userIds.get(0);
        } else {
            jdbc.update("INSERT INTO users (email, display_name) VALUES (?, ?)",
                    req.email(), req.displayName());
            Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            if (id == null) throw new IllegalStateException("Failed to create user");
            userId = id;
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
    }
}
