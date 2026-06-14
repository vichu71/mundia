package com.mundia.backend.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private static final long CACHE_MINUTES = 30;

    private final String apiKey;
    private final RestClient restClient;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    // Simple in-memory cache: poolId → cached response
    private final Map<Long, CachedNews> cache = new ConcurrentHashMap<>();

    public NewsService(
            @Value("${openai.api-key:}") String apiKey,
            RestClient.Builder builder,
            JdbcTemplate jdbc) {
        this.apiKey = apiKey;
        this.restClient = builder.baseUrl("https://api.openai.com").build();
        this.jdbc = jdbc;
    }

    public record NewsResponse(List<String> items, String generatedAt) {}

    private record CachedNews(NewsResponse response, Instant cachedAt) {
        boolean isExpired() {
            return Instant.now().isAfter(cachedAt.plus(CACHE_MINUTES, ChronoUnit.MINUTES));
        }
    }

    public NewsResponse getNews(long poolId, boolean forceRefresh) {
        if (!forceRefresh) {
            CachedNews cached = cache.get(poolId);
            if (cached != null && !cached.isExpired()) {
                return cached.response();
            }
        }

        String poolName = fetchPoolName(poolId);
        String matchContext = buildMatchContext(poolId);
        String playerContext = buildPlayerContext(poolId);

        List<String> items;
        if (apiKey == null || apiKey.isBlank()) {
            items = fallbackNews(poolName);
        } else {
            try {
                items = callOpenAi(poolName, matchContext, playerContext);
            } catch (Exception e) {
                log.warn("OpenAI news generation failed: {}", e.getMessage());
                items = fallbackNews(poolName);
            }
        }

        NewsResponse response = new NewsResponse(items, Instant.now().toString());
        cache.put(poolId, new CachedNews(response, Instant.now()));
        return response;
    }

    public void invalidateCache(long poolId) {
        cache.remove(poolId);
    }

    // ─── data queries ─────────────────────────────────────────────────────────

    private String fetchPoolName(long poolId) {
        List<String> names = jdbc.query(
                "SELECT name FROM pools WHERE id = ?",
                (rs, i) -> rs.getString("name"), poolId);
        return names.isEmpty() ? "la porra" : names.get(0);
    }

    private String buildMatchContext(long poolId) {
        // Last 5 finished matches in this pool's associated tournament
        List<String> lines = jdbc.query("""
                SELECT ht.name AS home, at.name AS away,
                       m.home_goals, m.away_goals, r.name AS round
                FROM matches m
                JOIN teams ht ON ht.id = m.home_team_id
                JOIN teams at ON at.id = m.away_team_id
                JOIN rounds r ON r.id = m.round_id
                WHERE m.status IN ('FINISHED','CLOSED')
                  AND m.home_goals IS NOT NULL
                  AND m.result_source != 'SIM'
                ORDER BY m.kickoff_at DESC
                LIMIT 5
                """,
                (rs, i) -> String.format("%s %d-%d %s (%s)",
                        rs.getString("home"), rs.getInt("home_goals"),
                        rs.getInt("away_goals"), rs.getString("away"),
                        rs.getString("round")));
        return lines.isEmpty() ? "Sin partidos terminados aún" : String.join(", ", lines);
    }

    private String buildPlayerContext(long poolId) {
        // Top scorers in recent matches (last 48h of score_breakdowns)
        List<String> lines = jdbc.query("""
                SELECT u.display_name, SUM(sb.points) AS pts
                FROM score_breakdowns sb
                JOIN pool_members pm ON pm.id = sb.pool_member_id
                JOIN users u ON u.id = pm.user_id
                WHERE pm.pool_id = ?
                  AND sb.calculated_at >= NOW() - INTERVAL 48 HOUR
                  AND sb.points > 0
                GROUP BY u.id, u.display_name
                ORDER BY pts DESC
                LIMIT 8
                """,
                (rs, i) -> String.format("%s(%+d pts)", rs.getString("display_name"), rs.getInt("pts")),
                poolId);

        if (lines.isEmpty()) {
            // Fallback: show current ranking top 5
            lines = jdbc.query("""
                    SELECT u.display_name, SUM(sb.points) AS total
                    FROM score_breakdowns sb
                    JOIN pool_members pm ON pm.id = sb.pool_member_id
                    JOIN users u ON u.id = pm.user_id
                    WHERE pm.pool_id = ?
                    GROUP BY u.id, u.display_name
                    ORDER BY total DESC
                    LIMIT 5
                    """,
                    (rs, i) -> String.format("%s(%d pts)", rs.getString("display_name"), rs.getInt("total")),
                    poolId);
        }
        return lines.isEmpty() ? "Sin puntuaciones registradas aún" : String.join(", ", lines);
    }

    // ─── AI call ──────────────────────────────────────────────────────────────

    private List<String> callOpenAi(String poolName, String matches, String players) throws Exception {
        String prompt = String.format("""
                Eres el narrador oficial de la porra del Mundial 2026 llamada "%s".

                Últimos resultados: %s

                Puntuaciones recientes de los participantes: %s

                Genera exactamente 6 titulares de noticias cortos, divertidos y en español \
                sobre estos eventos, como si fuera un ticker de noticias deportivas. \
                Sé creativo, usa emojis y exagera un poco al estilo de la prensa deportiva. \
                Máximo 90 caracteres por titular. No inventes datos que no estén en el contexto.
                Responde ÚNICAMENTE con JSON válido (sin markdown): {"news":["...","...","...","...","...","..."]}
                """,
                poolName, matches, players);

        Map<String, Object> body = Map.of(
                "model", "gpt-4o-mini",
                "max_tokens", 600,
                "temperature", 0.9,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of("role", "user", "content", prompt)));

        String responseJson = restClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode root = mapper.readTree(responseJson);
        String content = root.path("choices").get(0).path("message").path("content").asText("{}").trim();
        log.debug("OpenAI news raw: {}", content);

        JsonNode parsed = mapper.readTree(content);
        JsonNode newsNode = parsed.path("news");
        List<String> items = new ArrayList<>();
        for (JsonNode n : newsNode) {
            String text = n.asText("").trim();
            if (!text.isBlank()) items.add(text);
        }
        return items.isEmpty() ? fallbackNews(poolName) : items;
    }

    // ─── fallback ─────────────────────────────────────────────────────────────

    private List<String> fallbackNews(String poolName) {
        return List.of(
                "⚽ El Mundial 2026 está en marcha — ¡que empiece la batalla!",
                "🏆 " + poolName + ": ¿quién se llevará el bote este año?",
                "📊 Cada gol cuenta — revisa tus predicciones",
                "🔥 La fase de grupos promete emociones al máximo",
                "🎯 Acertar el marcador exacto vale 4 puntos — ¡a por ello!",
                "👀 Mantente al día: los partidos no esperan a nadie"
        );
    }
}
