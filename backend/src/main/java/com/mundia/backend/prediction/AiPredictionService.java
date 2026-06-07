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

    public record PredictionResult(int homeGoals, int awayGoals, String source, String reasoning) {}

    /**
     * Asks GPT to predict the score. Falls back to a random 0-3 scoreline
     * if the key is missing or the call fails.
     */
    public PredictionResult predict(String homeTeam, String awayTeam, String roundName) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OPENAI_API_KEY not set — returning random prediction");
            return randomScore("random-no-key", null);
        }
        try {
            return callGpt(homeTeam, awayTeam, roundName);
        } catch (Exception e) {
            log.warn("OpenAI call failed ({}), falling back to random score", e.getMessage());
            return randomScore("random-fallback", null);
        }
    }

    // ─── internals ───────────────────────────────────────────────────────────

    private PredictionResult callGpt(String home, String away, String round) throws Exception {
        String prompt = String.format(
                """
                Eres un experto en fútbol. Predice el resultado del partido %s vs %s en la fase '%s' del Mundial 2026.
                Responde ÚNICAMENTE con un JSON válido con este formato exacto (sin markdown, sin texto extra):
                {"score":"HOME-AWAY","reasoning":"Explicación breve en español (máx 2 frases) basada en estadísticas, historial o nivel de los equipos"}
                Ejemplo: {"score":"2-1","reasoning":"México tiene mejor historial reciente y juega en casa. Sudáfrica mostró debilidades defensivas en la fase de clasificación."}
                Máximo 3 goles por equipo.
                """,
                home, away, round != null ? round : "Mundial 2026");

        Map<String, Object> body = Map.of(
                "model", "gpt-4o-mini",
                "max_tokens", 150,
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

        try {
            JsonNode parsed = mapper.readTree(content);
            String score = parsed.path("score").asText("").replaceAll("\\s", "");
            String reasoning = parsed.path("reasoning").asText(null);
            String[] parts = score.split("-");
            if (parts.length == 2) {
                int home = Math.min(3, Math.max(0, Integer.parseInt(parts[0].replaceAll("[^0-9]", ""))));
                int away = Math.min(3, Math.max(0, Integer.parseInt(parts[1].replaceAll("[^0-9]", ""))));
                return new PredictionResult(home, away, "gpt-4o-mini", reasoning);
            }
        } catch (Exception e) {
            // fallback: intentar parsear como marcador simple "2-1"
            String cleaned = content.replaceAll("\\s", "");
            String[] parts = cleaned.split("-");
            if (parts.length == 2) {
                int home = Math.min(3, Math.max(0, Integer.parseInt(parts[0].replaceAll("[^0-9]", ""))));
                int away = Math.min(3, Math.max(0, Integer.parseInt(parts[1].replaceAll("[^0-9]", ""))));
                return new PredictionResult(home, away, "gpt-4o-mini", null);
            }
        }

        log.warn("Could not parse GPT response '{}', using random", content);
        return randomScore("random-parse-error", null);
    }

    /** Public entry point for callers that explicitly want a random score. */
    public PredictionResult randomPrediction() {
        return randomScore("random", null);
    }

    private static PredictionResult randomScore(String source, String reasoning) {
        // Weighted: most matches end 0-0 to 2-1 range, rarely 3-x
        int[] goals = {0, 0, 1, 1, 1, 2, 2, 2, 3};
        int home = goals[RNG.nextInt(goals.length)];
        int away = goals[RNG.nextInt(goals.length)];
        return new PredictionResult(home, away, source, reasoning);
    }
}
