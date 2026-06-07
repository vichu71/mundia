package com.mundia.backend.pool;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/pools")
public class PoolController {

    record CreatePoolRequest(String name, String description, int entryFeeCents, String currency) {}
    record CreatePoolResponse(long id, String name, String inviteCode) {}

    private final JdbcTemplate jdbc;

    public PoolController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatePoolResponse createPool(JwtAuthenticationToken auth, @RequestBody CreatePoolRequest req) {
        long userId = Long.parseLong(auth.getToken().getSubject());

        String inviteCode = generateInviteCode();
        String currency = req.currency() != null && !req.currency().isBlank() ? req.currency() : "EUR";
        int entryFee = req.entryFeeCents() > 0 ? req.entryFeeCents() : 1000;

        jdbc.update("""
                INSERT INTO pools (owner_user_id, name, description, invite_code,
                                   entry_fee_cents, currency, status, initial_bonus_enabled)
                VALUES (?, ?, ?, ?, ?, ?, 'OPEN', TRUE)
                """, userId, req.name(), req.description(), inviteCode, entryFee, currency);

        Long poolId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (poolId == null) throw new IllegalStateException("Failed to create pool");

        // Creator becomes ADMIN member (joined immediately, payment not required)
        jdbc.update("""
                INSERT INTO pool_members (pool_id, user_id, role, status, joined_at)
                VALUES (?, ?, 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP(6))
                """, poolId, userId);

        // Default prize rules
        jdbc.update("""
                INSERT INTO prize_rules (pool_id, category, percentage_when_perfect_alive,
                                         percentage_when_perfect_extinct, enabled)
                VALUES
                  (?, 'PERFECT_WINNERS', 75.00, 0.00,  TRUE),
                  (?, 'GENERAL',         10.00, 40.00, TRUE),
                  (?, 'INITIAL_BET',      5.00, 15.00, TRUE),
                  (?, 'EXACT_RESULTS',    5.00, 20.00, TRUE),
                  (?, 'WINNERS',          5.00, 20.00, TRUE),
                  (?, 'CHAMPION',         0.00,  5.00, TRUE)
                """, poolId, poolId, poolId, poolId, poolId, poolId);

        // Note: no welcome email here — the user already received it on
        // registration / first Google login. Sending again would duplicate it.

        return new CreatePoolResponse(poolId, req.name(), inviteCode);
    }

    private String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
