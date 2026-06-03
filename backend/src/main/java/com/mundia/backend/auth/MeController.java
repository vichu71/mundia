package com.mundia.backend.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me")
public class MeController {

    record UserPool(long id, String name, String code, String role) {}
    record MeResponse(long id, String name, String email, String avatarUrl, List<UserPool> pools) {}

    private final JdbcTemplate jdbc;

    public MeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public MeResponse getMe(JwtAuthenticationToken auth) {
        long userId = Long.parseLong(auth.getToken().getSubject());

        var users = jdbc.query(
                "SELECT id, display_name, email, avatar_url FROM users WHERE id = ?",
                (rs, i) -> new Object[]{ rs.getLong("id"), rs.getString("display_name"),
                        rs.getString("email"), rs.getString("avatar_url") },
                userId);

        if (users.isEmpty()) {
            return new MeResponse(userId, "", "", null, List.of());
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

        return new MeResponse(userId, (String) u[1], (String) u[2], (String) u[3], pools);
    }
}
