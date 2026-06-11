package com.mundia.backend.simulator;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/sim")
public class SimulatorController {

    private final SimulatorService service;
    private final JdbcTemplate jdbc;

    public SimulatorController(SimulatorService service, JdbcTemplate jdbc) {
        this.service = service;
        this.jdbc    = jdbc;
    }

    @GetMapping("/status/{poolId}")
    public SimulatorService.SimStatus status(@PathVariable long poolId, JwtAuthenticationToken auth) {
        requireAdmin(auth, poolId);
        return service.getStatus(poolId);
    }

    @PostMapping("/users/{poolId}")
    public ResponseEntity<Map<String, Object>> createUsers(
            @PathVariable long poolId,
            @RequestBody Map<String, Integer> body,
            JwtAuthenticationToken auth) {
        requireAdmin(auth, poolId);
        int count   = body.getOrDefault("count", 5);
        int created = service.createSimUsers(poolId, Math.min(count, 15));
        return ResponseEntity.ok(Map.of("created", created));
    }

    @PostMapping("/advance/{poolId}")
    public ResponseEntity<SimulatorService.SimDayResult> advance(
            @PathVariable long poolId, JwtAuthenticationToken auth) {
        requireAdmin(auth, poolId);
        return ResponseEntity.ok(service.advanceDay(poolId));
    }

    @DeleteMapping("/reset/{poolId}")
    public ResponseEntity<Map<String, String>> reset(
            @PathVariable long poolId, JwtAuthenticationToken auth) {
        requireAdmin(auth, poolId);
        service.resetSimulation(poolId);
        return ResponseEntity.ok(Map.of("status", "reset complete"));
    }

    @PostMapping("/full/{poolId}")
    public ResponseEntity<Map<String, String>> simulateFull(
            @PathVariable long poolId, JwtAuthenticationToken auth) {
        requireAdmin(auth, poolId);
        String result = service.simulateFullTournament(poolId);
        return ResponseEntity.ok(Map.of("result", result));
    }

    @PostMapping("/knockout/round32/{poolId}")
    public ResponseEntity<SimulatorService.KnockoutGenResult> genRound32(
            @PathVariable long poolId, JwtAuthenticationToken auth) {
        requireAdmin(auth, poolId);
        var result = service.generateRoundOf32(poolId);
        service.playRound("Round of 32");   // generate + play immediately
        return ResponseEntity.ok(result);
    }

    @PostMapping("/knockout/next/{poolId}")
    public ResponseEntity<SimulatorService.KnockoutGenResult> genNextRound(
            @PathVariable long poolId,
            @RequestBody Map<String, String> body,
            JwtAuthenticationToken auth) {
        requireAdmin(auth, poolId);
        String current = body.get("currentRound");
        String next    = body.get("nextRound");
        int sortOrder  = Integer.parseInt(body.getOrDefault("sortOrder", "11"));
        String[] dates = body.getOrDefault("dates", "").split(",");
        var result = service.advanceKnockoutRound(poolId, current, next, sortOrder, dates);
        service.playRound(next);   // generate + play immediately
        return ResponseEntity.ok(result);
    }

    private static final String SUPER_ADMIN_EMAIL = "victor.huecas@gmail.com";

    private void requireAdmin(JwtAuthenticationToken auth, long poolId) {
        String email = auth.getToken().getClaimAsString("email");
        if (!SUPER_ADMIN_EMAIL.equals(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Simulador solo disponible para el administrador principal");
        }
        long userId = Long.parseLong(auth.getToken().getSubject());
        String role = jdbc.query(
                "SELECT role FROM pool_members WHERE pool_id = ? AND user_id = ?",
                (rs, i) -> rs.getString("role"), poolId, userId)
                .stream().findFirst().orElse("");
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
    }
}
