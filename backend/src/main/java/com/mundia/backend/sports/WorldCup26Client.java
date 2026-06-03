package com.mundia.backend.sports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Free, no-key client for https://worldcup26.ir — real-time 2026 World Cup data.
 * Endpoints:
 *   GET /get/games   → all 104 matches with live scores
 *   GET /get/teams   → 48 teams with iso2 flag codes
 *   GET /get/groups  → group standings
 */
@Component
public class WorldCup26Client {

    private static final Logger log = LoggerFactory.getLogger(WorldCup26Client.class);
    private static final String BASE_URL = "https://worldcup26.ir";

    private final RestClient restClient;

    public WorldCup26Client(RestClient.Builder builder) {
        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    public String getGames() {
        log.debug("worldcup26 → GET /get/games");
        return get("/get/games");
    }

    public String getTeams() {
        log.debug("worldcup26 → GET /get/teams");
        return get("/get/teams");
    }

    public String getGroups() {
        log.debug("worldcup26 → GET /get/groups");
        return get("/get/groups");
    }

    private String get(String path) {
        return restClient.get()
                .uri(path)
                .retrieve()
                .body(String.class);
    }
}
