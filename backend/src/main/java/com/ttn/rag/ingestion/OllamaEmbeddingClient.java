package com.ttn.rag.ingestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttn.rag.config.RagEmbeddingProperties;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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
        } catch (RestClientException | java.io.IOException ex) {
            throw new EmbeddingException("Embedding failed", ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EmbedResponse {
        public List<Double> embedding;
    }
}