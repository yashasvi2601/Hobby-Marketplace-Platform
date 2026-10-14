package backend.hobbiebackend.service.impl;

import backend.hobbiebackend.service.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Calls Gemini's embedContent endpoint
 * (https://ai.google.dev/gemini-api/docs/embeddings) to turn hobby/profile
 * text into a vector for semantic similarity matching.
 * <p>
 * If {@code gemini.api.key} is not configured, or the request fails for any
 * reason, {@link #embed(String)} returns null so callers can fall back to a
 * non-AI matching strategy instead of breaking.
 */
@Service
public class GeminiEmbeddingServiceImpl implements EmbeddingService {
    private final RestTemplate restTemplate;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent}")
    private String apiUrl;

    @Value("${gemini.api.model:models/text-embedding-004}")
    private String model;

    @Autowired
    public GeminiEmbeddingServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public double[] embed(String text) {
        if (apiKey == null || apiKey.isBlank() || text == null || text.isBlank()) {
            return null;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "content", Map.of("parts", List.of(Map.of("text", text)))
            );

            String url = UriComponentsBuilder.fromHttpUrl(apiUrl)
                    .queryParam("key", apiKey)
                    .toUriString();

            JsonNode response = this.restTemplate.postForObject(
                    url, new HttpEntity<>(body, headers), JsonNode.class);

            if (response == null) {
                return null;
            }
            JsonNode embeddingNode = response.path("embedding").path("values");
            if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
                return null;
            }
            double[] embedding = new double[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = embeddingNode.get(i).asDouble();
            }
            return embedding;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
