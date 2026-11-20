package com.ttn.rag.ingestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttn.rag.config.RagEmbeddingProperties;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Profile("!test")
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final RagEmbeddingProperties properties;
    private final ObjectMapper objectMapper;

    public OllamaEmbeddingClient(RagEmbeddingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).build();
    }

    @Override
    public float[] embed(String text) {
        try {
            String body =
                    objectMapper.writeValueAsString(
                            Map.of("model", properties.getModel(), "prompt", text));
            EmbedResponse response =
                    restClient
                            .post()
                            .uri("/api/embeddings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(EmbedResponse.class);
            if (response == null || response.embedding == null) {
                throw new EmbeddingException("Empty embedding response");
            }
            float[] vector = new float[response.embedding.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = response.embedding.get(i).floatValue();
            }
            if (vector.length != properties.getDimension()) {
                throw new EmbeddingException("Embedding dimension mismatch");
            }
            return vector;
        } catch (HttpStatusCodeException ex) {
            throw new EmbeddingException(ollamaErrorMessage(ex.getResponseBodyAsString()), ex);
        } catch (RestClientException | java.io.IOException ex) {
            throw new EmbeddingException("Embedding failed: is Ollama running on " + properties.getBaseUrl() + "?", ex);
        }
    }

    private static String ollamaErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "Embedding failed";
        }
        try {
            var node = new ObjectMapper().readTree(responseBody);
            if (node.has("error")) {
                String err = node.get("error").asText();
                if (err.contains("not found")) {
                    return err + " (run: ollama pull <model>)";
                }
                return err;
            }
        } catch (java.io.IOException ignored) {
            // fall through
        }
        return "Embedding failed";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EmbedResponse {
        public List<Double> embedding;
    }
}