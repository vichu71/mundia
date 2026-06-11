package com.mundia.backend.pool;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Random;
import java.util.UUID;

@RestController
@RequestMapping("/api/pools")
public class PoolController {

    record CreatePoolRequest(String name, String description, int entryFeeCents, String currency) {}
    record CreatePoolResponse(long id, String name, String inviteCode) {}
    record JoinPoolRequest(String inviteCode) {}
    record JoinPoolResponse(long poolId, String poolName) {}

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
        String name = (req.name() != null && !req.name().isBlank()) ? req.name() : generatePoolName();

        jdbc.update("""
                INSERT INTO pools (owner_user_id, name, description, invite_code,
                                   entry_fee_cents, currency, status, initial_bonus_enabled)
                VALUES (?, ?, ?, ?, ?, ?, 'OPEN', TRUE)
                """, userId, name, req.description(), inviteCode, entryFee, currency);

        Long poolId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (poolId == null) throw new IllegalStateException("Failed to create pool");

        // Creator becomes ADMIN member
        jdbc.update("""
                INSERT INTO pool_members (pool_id, user_id, role, status, joined_at)
                VALUES (?, ?, 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP(6))
                """, poolId, userId);

        // Admin also gets a pending payment like any other participant
        Long adminMemberId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (adminMemberId != null) {
            jdbc.update("""
                    INSERT INTO payments (pool_member_id, amount_cents, method, status, notes)
                    VALUES (?, ?, 'BIZUM', 'PENDING', 'Pendiente de confirmar')
                    """, adminMemberId, entryFee);
        }

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

        return new CreatePoolResponse(poolId, name, inviteCode);
    }

    @PostMapping("/join")
    public JoinPoolResponse joinPool(JwtAuthenticationToken auth, @RequestBody JoinPoolRequest req) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        String code = req.inviteCode() == null ? "" : req.inviteCode().trim().toUpperCase();

        List<Long> poolIds = jdbc.query(
                "SELECT id FROM pools WHERE UPPER(invite_code) = ? AND status <> 'DELETED'",
                (rs, i) -> rs.getLong("id"), code);
        if (poolIds.isEmpty())
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Código no encontrado");

        long poolId = poolIds.get(0);

        // Idempotent: ya es miembro
        boolean already = !jdbc.query(
                "SELECT id FROM pool_members WHERE pool_id = ? AND user_id = ?",
                (rs, i) -> rs.getLong("id"), poolId, userId).isEmpty();
        if (!already) {
            jdbc.update("""
                    INSERT INTO pool_members (pool_id, user_id, role, status, joined_at)
                    VALUES (?, ?, 'PLAYER', 'ACTIVE', CURRENT_TIMESTAMP(6))
                    """, poolId, userId);
            Long memberId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            if (memberId != null) {
                Integer entryFee = jdbc.queryForObject(
                        "SELECT entry_fee_cents FROM pools WHERE id = ?", Integer.class, poolId);
                jdbc.update("""
                        INSERT INTO payments (pool_member_id, amount_cents, method, status, notes)
                        VALUES (?, ?, 'BIZUM', 'PENDING', 'Pendiente de confirmar')
                        """, memberId, entryFee != null ? entryFee : 1000);
                // Create prediction sets
                for (String type : new String[]{"LIVE", "INITIAL"}) {
                    jdbc.update("""
                            INSERT INTO prediction_sets (pool_member_id, type, status)
                            VALUES (?, ?, 'DRAFT')
                            """, memberId, type);
                }
            }
        }

        String poolName = jdbc.queryForObject("SELECT name FROM pools WHERE id = ?", String.class, poolId);
        return new JoinPoolResponse(poolId, poolName);
    }

    private String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private static final String[] ADJ  = {
        "Lokos", "Fieras", "Cracks", "Leyendas", "Galácticos", "Mágicos",
        "Eternos", "Invictos", "Salvajes", "Gloriosos", "Épicos", "Míticos"
    };
    private static final String[] NOUN = {
        "del Balón", "del Área", "del Offside", "del VAR", "del Penalty",
        "del Córner", "del Hat-trick", "del Mundial", "de la Red", "del Gol"
    };
    private final Random rng = new Random();

    private String generatePoolName() {
        return ADJ[rng.nextInt(ADJ.length)] + " " + NOUN[rng.nextInt(NOUN.length)];
    }
}
