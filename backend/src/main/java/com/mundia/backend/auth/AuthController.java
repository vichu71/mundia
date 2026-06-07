package com.mundia.backend.auth;

import com.mundia.backend.notification.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    record GoogleLoginRequest(String credential, String accessCode) {}
    record EmailLoginRequest(String email, String password, String accessCode) {}
    record RegisterRequest(String email, String password, String displayName, String inviteCode, String accessCode) {}
    record AuthResponse(String token, String name, String email, String avatarUrl) {}
    record GoogleTokenInfo(String sub, String email, String name, String picture) {}

    private final JwtService jwtService;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notifications;
    private final String googleClientId;
    private final boolean accessCodeEnabled;
    private final String accessCodeValue;
    private final RestClient restClient = RestClient.create();

    public AuthController(
            JwtService jwtService,
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            NotificationService notifications,
            @Value("${mundia.google.client-id}") String googleClientId,
            @Value("${mundia.access-code.enabled:false}") boolean accessCodeEnabled,
            @Value("${mundia.access-code.value:}") String accessCodeValue
    ) {
        this.jwtService = jwtService;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.notifications = notifications;
        this.googleClientId = googleClientId;
        this.accessCodeEnabled = accessCodeEnabled;
        this.accessCodeValue = accessCodeValue;
    }

    private void checkAccessCode(String provided) {
        if (!accessCodeEnabled) return;
        if (accessCodeValue.isBlank() || !accessCodeValue.equals(provided)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Código de acceso incorrecto");
        }
    }

    // ─── Google OAuth ─────────────────────────────────────────────────────────

    @PostMapping("/google")
    public AuthResponse googleLogin(@RequestBody GoogleLoginRequest req) {
        checkAccessCode(req.accessCode());
        GoogleTokenInfo info = verifyGoogleToken(req.credential());
        boolean isNew = isNewGoogleUser(info);
        long userId = upsertGoogleUser(info);
        if (isNew) notifications.sendWelcome(info.email(), info.name());
        String token = jwtService.createToken(userId, info.email(), info.name());
        return new AuthResponse(token, info.name(), info.email(), info.picture());
    }

    private boolean isNewGoogleUser(GoogleTokenInfo info) {
        List<Long> bySub   = jdbc.query("SELECT id FROM users WHERE google_sub = ?",
                (rs, i) -> rs.getLong("id"), info.sub());
        List<Long> byEmail = jdbc.query("SELECT id FROM users WHERE email = ?",
                (rs, i) -> rs.getLong("id"), info.email());
        return bySub.isEmpty() && byEmail.isEmpty();
    }

    // ─── Email + Contraseña ───────────────────────────────────────────────────

    @PostMapping("/login")
    public AuthResponse emailLogin(@RequestBody EmailLoginRequest req) {
        checkAccessCode(req.accessCode());
        if (req.email() == null || req.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email y contraseña requeridos");
        }

        record UserRow(long id, String name, String hash, String googleSub) {}
        List<UserRow> rows = jdbc.query(
                "SELECT id, display_name, password_hash, google_sub FROM users WHERE email = ?",
                (rs, i) -> new UserRow(rs.getLong("id"), rs.getString("display_name"),
                        rs.getString("password_hash"), rs.getString("google_sub")),
                req.email().trim().toLowerCase());

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email o contraseña incorrectos");
        }

        UserRow user = rows.get(0);

        if (user.googleSub() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta cuenta usa Google para iniciar sesión");
        }

        if (user.hash() == null || !passwordEncoder.matches(req.password(), user.hash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email o contraseña incorrectos");
        }

        String token = jwtService.createToken(user.id(), req.email(), user.name());
        return new AuthResponse(token, user.name(), req.email(), null);
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest req) {
        checkAccessCode(req.accessCode());
        if (req.email() == null || req.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El email es obligatorio");
        }
        if (req.password() == null || req.password().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 6 caracteres");
        }
        if (req.displayName() == null || req.displayName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre es obligatorio");
        }

        String email = req.email().trim().toLowerCase();

        // Check email not already registered
        List<Long> existing = jdbc.query("SELECT id FROM users WHERE email = ?",
                (rs, i) -> rs.getLong("id"), email);
        if (!existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este email ya está registrado");
        }

        // Create user
        String hash = passwordEncoder.encode(req.password());
        jdbc.update("INSERT INTO users (email, display_name, password_hash) VALUES (?, ?, ?)",
                email, req.displayName().trim(), hash);
        Long userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (userId == null) throw new IllegalStateException("Failed to create user");

        // Join pool if invite code provided
        if (req.inviteCode() != null && !req.inviteCode().isBlank()) {
            joinPoolByCode(userId, req.inviteCode().trim().toUpperCase());
        }

        String token = jwtService.createToken(userId, email, req.displayName().trim());
        notifications.sendWelcome(email, req.displayName().trim());
        return new AuthResponse(token, req.displayName().trim(), email, null);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void joinPoolByCode(long userId, String inviteCode) {
        List<Long> poolIds = jdbc.query(
                "SELECT id FROM pools WHERE UPPER(invite_code) = ? AND status <> 'DELETED'",
                (rs, i) -> rs.getLong("id"), inviteCode);

        if (poolIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Código de porra no encontrado: " + inviteCode);
        }
        long poolId = poolIds.get(0);

        // Idempotent
        List<Long> memberCheck = jdbc.query(
                "SELECT id FROM pool_members WHERE pool_id = ? AND user_id = ?",
                (rs, i) -> rs.getLong("id"), poolId, userId);
        if (!memberCheck.isEmpty()) return;

        jdbc.update("""
                INSERT INTO pool_members (pool_id, user_id, role, status, joined_at)
                VALUES (?, ?, 'PLAYER', 'ACTIVE', CURRENT_TIMESTAMP(6))
                """, poolId, userId);
        Long memberId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (memberId == null) return;

        Integer entryFee = jdbc.queryForObject(
                "SELECT entry_fee_cents FROM pools WHERE id = ?", Integer.class, poolId);
        jdbc.update("""
                INSERT INTO payments (pool_member_id, amount_cents, method, status, notes)
                VALUES (?, ?, 'PENDIENTE', 'PENDING', 'Pendiente de confirmar por admin')
                """, memberId, entryFee != null ? entryFee : 1000);
    }

    private GoogleTokenInfo verifyGoogleToken(String credential) {
        Map<String, Object> payload;
        try {
            payload = restClient.get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?id_token=" + credential)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token");
        }
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Empty token response");
        }
        if (!googleClientId.isBlank()) {
            String aud = (String) payload.get("aud");
            if (!googleClientId.equals(aud)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token audience mismatch");
            }
        }
        return new GoogleTokenInfo(
                (String) payload.get("sub"),
                (String) payload.get("email"),
                (String) payload.getOrDefault("name", payload.get("email")),
                (String) payload.getOrDefault("picture", null)
        );
    }

    private long upsertGoogleUser(GoogleTokenInfo info) {
        List<Long> ids = jdbc.query("SELECT id FROM users WHERE google_sub = ?",
                (rs, i) -> rs.getLong("id"), info.sub());
        if (!ids.isEmpty()) {
            jdbc.update("UPDATE users SET email = ?, display_name = ?, avatar_url = ? WHERE google_sub = ?",
                    info.email(), info.name(), info.picture(), info.sub());
            return ids.get(0);
        }
        // Check if email already exists without google_sub (email+password user)
        List<Long> byEmail = jdbc.query("SELECT id FROM users WHERE email = ?",
                (rs, i) -> rs.getLong("id"), info.email());
        if (!byEmail.isEmpty()) {
            // Link Google account to existing email user
            jdbc.update("UPDATE users SET google_sub = ?, display_name = ?, avatar_url = ? WHERE email = ?",
                    info.sub(), info.name(), info.picture(), info.email());
            return byEmail.get(0);
        }
        jdbc.update("INSERT INTO users (google_sub, email, display_name, avatar_url) VALUES (?, ?, ?, ?)",
                info.sub(), info.email(), info.name(), info.picture());
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) throw new IllegalStateException("Failed to get new user ID");
        return id;
    }
}
