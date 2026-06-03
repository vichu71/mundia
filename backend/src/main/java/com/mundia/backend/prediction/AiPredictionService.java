package com.mundia.backend.prediction;

import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AiPredictionService {

    private static final Logger log = LoggerFactory.getLogger(AiPredictionService.class);
    private static final Random RNG = new Random();

    private final String apiKey;
    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public AiPredictionService(
            @Value("${openai.api-key:}") String apiKey,
            RestClient.Builder builder) {
        this.apiKey = apiKey;
        this.restClient = builder
                .baseUrl("https://api.openai.com")
                .build();
    }

    public record PredictionResult(int homeGoals, int awayGoals, String source) {}

    /**
     * Asks GPT to predict the score. Falls back to a random 0-3 scoreline
     * if the key is missing or the call fails.
     */
    public PredictionResult predict(String homeTeam, String awayTeam, String roundName) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OPENAI_API_KEY not set — returning random prediction");
            return randomScore("random-no-key");
        }
        try {
            return callGpt(homeTeam, awayTeam, roundName);
        } catch (Exception e) {
            log.warn("OpenAI call failed ({}), falling back to random score", e.getMessage());
            return randomScore("random-fallback");
        }
    }

    // ─── internals ───────────────────────────────────────────────────────────

    private PredictionResult callGpt(String home, String away, String round) throws Exception {
        String prompt = String.format(
                """
                Eres un experto en fútbol. Predice el resultado del partido %s vs %s en la fase '%s' del Mundial 2026.
                Responde ÚNICAMENTE con el marcador en el formato exacto: HOME-AWAY (por ejemplo: 2-1).
                No incluyas ningún texto adicional. Máximo 3 goles por equipo.
                """,
                home, away, round != null ? round : "Mundial 2026");

        Map<String, Object> body = Map.of(
                "model", "gpt-4o-mini",
                "max_tokens", 10,
                "temperature", 0.8,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        String responseJson = restClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        return parseGptResponse(responseJson);
    }

    private PredictionResult parseGptResponse(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        String content = root
                .path("choices").get(0)
                .path("message")
                .path("content")
                .asText("")
                .trim();

        log.debug("GPT raw response: '{}'", content);

        // Expect format like "2-1" or "1 - 0"
        String cleaned = content.replaceAll("\\s", "");
        String[] parts = cleaned.split("-");
        if (parts.length == 2) {
            int home = Math.min(3, Math.max(0, Integer.parseInt(parts[0].replaceAll("[^0-9]", ""))));
            int away = Math.min(3, Math.max(0, Integer.parseInt(parts[1].replaceAll("[^0-9]", ""))));
            return new PredictionResult(home, away, "gpt-4o-mini");
        }
        log.warn("Could not parse GPT response '{}', using random", content);
        return randomScore("random-parse-error");
    }

    /** Public entry point for callers that explicitly want a random score. */
    public PredictionResult randomPrediction() {
        return randomScore("random");
    }

    private static PredictionResult randomScore(String source) {
        // Weighted: most matches end 0-0 to 2-1 range, rarely 3-x
        int[] goals = {0, 0, 1, 1, 1, 2, 2, 2, 3};
        int home = goals[RNG.nextInt(goals.length)];
        int away = goals[RNG.nextInt(goals.length)];
        return new PredictionResult(home, away, source);
    }
}
