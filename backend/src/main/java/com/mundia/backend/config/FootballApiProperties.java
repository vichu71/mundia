package com.mundia.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mundia.football-api")
public record FootballApiProperties(
        String mode,
        String baseUrl,
        String apiKey,
        String rapidapiHost,
        int league,
        int season,
        int livePollingSeconds
) {
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
