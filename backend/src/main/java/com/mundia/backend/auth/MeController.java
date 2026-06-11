package com.mundia.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/me")
public class MeController {

    record UserPool(long id, String name, String code, String role) {}
    record MeResponse(long id, String name, String email, String avatarUrl,
                      String prefTheme, Boolean prefCompact, List<UserPool> pools) {}
    record DisplayNameRequest(String displayName) {}
    record PreferencesRequest(String theme, Boolean compact) {}

    private final JdbcTemplate jdbc;

    public MeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public MeResponse getMe(JwtAuthenticationToken auth) {
        long userId = Long.parseLong(auth.getToken().getSubject());

        var users = jdbc.query(
                "SELECT id, display_name, email, avatar_url, pref_theme, pref_compact FROM users WHERE id = ?",
                (rs, i) -> new Object[]{ rs.getLong("id"), rs.getString("display_name"),
                        rs.getString("email"), rs.getString("avatar_url"),
                        rs.getString("pref_theme"), rs.getObject("pref_compact", Boolean.class) },
                userId);

        if (users.isEmpty()) {
            return new MeResponse(userId, "", "", null, null, null, List.of());
        }
        Object[] u = users.get(0);

        List<UserPool> pools = jdbc.query("""
                SELECT p.id, p.name, p.invite_code, pm.role
                FROM pool_members pm
                JOIN pools p ON p.id = pm.pool_id
                WHERE pm.user_id = ? AND p.status <> 'DELETED'
                ORDER BY p.created_at
                """,
                (rs, i) -> new UserPool(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("invite_code"),
                        rs.getString("role")),
                userId);

        return new MeResponse(userId, (String) u[1], (String) u[2], (String) u[3],
                (String) u[4], (Boolean) u[5], pools);
    }

    @PatchMapping("/preferences")
    public void updatePreferences(JwtAuthenticationToken auth, @RequestBody PreferencesRequest req) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        if (req.theme() != null && !List.of("dark", "light", "auto").contains(req.theme())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tema inválido");
        }
        jdbc.update("""
                UPDATE users SET pref_theme = COALESCE(?, pref_theme),
                                 pref_compact = COALESCE(?, pref_compact)
                WHERE id = ?
                """, req.theme(), req.compact(), userId);
    }

    @PatchMapping("/display-name")
    public void updateDisplayName(JwtAuthenticationToken auth, @RequestBody DisplayNameRequest req) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        String name = req.displayName() == null ? "" : req.displayName().trim();
        if (name.isEmpty() || name.length() > 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre inválido (1-40 caracteres)");
        }
        jdbc.update("UPDATE users SET display_name = ? WHERE id = ?", name, userId);
    }
}
