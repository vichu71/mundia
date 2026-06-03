package com.mundia.backend.sports;

import java.time.LocalDate;

import com.mundia.backend.config.FootballApiProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
public class FootballApiClient {

    private static final String DIRECT_MODE = "direct";
    private static final String RAPIDAPI_MODE = "rapidapi";

    private final FootballApiProperties properties;
    private final RestClient restClient;

    public FootballApiClient(FootballApiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    public String getLiveFixtures() {
        return getFixtures(uriBuilder -> uriBuilder
                .path("/fixtures")
                .queryParam("live", "all")
                .queryParam("league", properties.league())
                .queryParam("season", properties.season()));
    }

    public String getFixturesByDate(LocalDate date) {
        return getFixtures(uriBuilder -> uriBuilder
                .path("/fixtures")
                .queryParam("date", date)
                .queryParam("league", properties.league())
                .queryParam("season", properties.season()));
    }

    public String getRounds() {
        return getFixtures(uriBuilder -> uriBuilder
                .path("/fixtures/rounds")
                .queryParam("league", properties.league())
                .queryParam("season", properties.season()));
    }

    public String getStandings() {
        return getFixtures(uriBuilder -> uriBuilder
                .path("/standings")
                .queryParam("league", properties.league())
                .queryParam("season", properties.season()));
    }

    private String getFixtures(java.util.function.Function<UriBuilder, UriBuilder> uriCustomizer) {
        if (!properties.configured()) {
            throw new FootballApiNotConfiguredException();
        }

        return restClient.get()
                .uri(uriBuilder -> uriCustomizer.apply(uriBuilder).build())
                .headers(this::applyAuthHeaders)
                .retrieve()
                .body(String.class);
    }

    private void applyAuthHeaders(HttpHeaders headers) {
        if (RAPIDAPI_MODE.equalsIgnoreCase(properties.mode())) {
            headers.set("x-rapidapi-key", properties.apiKey());
            headers.set("x-rapidapi-host", properties.rapidapiHost());
            return;
        }

        if (DIRECT_MODE.equalsIgnoreCase(properties.mode())) {
            headers.set("x-apisports-key", properties.apiKey());
            return;
        }

        throw new IllegalArgumentException("Unsupported football API mode: " + properties.mode());
    }

    public static class FootballApiNotConfiguredException extends RuntimeException {
        public FootballApiNotConfiguredException() {
            super("API_FOOTBALL_KEY is not configured");
        }
    }
}
