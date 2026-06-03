package com.mundia.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    record GoogleLoginRequest(String credential) {}
    record AuthResponse(String token, String name, String email, String avatarUrl) {}
    record GoogleTokenInfo(String sub, String email, String name, String picture) {}

    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;
    private final String googleClientId;
    private final RestClient restClient = RestClient.create();

    public AuthController(
            JwtService jwtService,
            JdbcTemplate jdbcTemplate,
            @Value("${mundia.google.client-id}") String googleClientId
    ) {
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
        this.googleClientId = googleClientId;
    }

    @PostMapping("/google")
    public AuthResponse googleLogin(@RequestBody GoogleLoginRequest req) {
        GoogleTokenInfo info = verifyGoogleToken(req.credential());
        long userId = upsertUser(info);
        String token = jwtService.createToken(userId, info.email(), info.name());
        return new AuthResponse(token, info.name(), info.email(), info.picture());
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

        // Skip audience check in dev when client-id is not configured
        if (!googleClientId.isBlank()) {
            String aud = (String) payload.get("aud");
            if (!googleClientId.equals(aud)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token audience mismatch");
            }
        }

        return new GoogleTokenInfo(
                (String) payload.get("sub"),
                (String) payload.get("email"),
                (String) payload.getOrDefault("name", (String) payload.get("email")),
                (String) payload.getOrDefault("picture", null)
        );
    }

    private long upsertUser(GoogleTokenInfo info) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM users WHERE google_sub = ?",
                (rs, i) -> rs.getLong("id"),
                info.sub()
        );
        if (!ids.isEmpty()) {
            jdbcTemplate.update(
                    "UPDATE users SET email = ?, display_name = ?, avatar_url = ? WHERE google_sub = ?",
                    info.email(), info.name(), info.picture(), info.sub()
            );
            return ids.get(0);
        }
        jdbcTemplate.update(
                "INSERT INTO users (google_sub, email, display_name, avatar_url) VALUES (?, ?, ?, ?)",
                info.sub(), info.email(), info.name(), info.picture()
        );
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) throw new IllegalStateException("Failed to get new user ID");
        return id;
    }

}
