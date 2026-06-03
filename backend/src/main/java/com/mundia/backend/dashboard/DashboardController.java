package com.mundia.backend.dashboard;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/{poolId}")
    DashboardResponse getDashboard(@PathVariable long poolId, JwtAuthenticationToken auth) {
        long userId = Long.parseLong(auth.getToken().getSubject());
        return dashboardService.getDashboard(userId, poolId);
    }
}
