package com.ttn.rag.ingestion;

import com.ttn.rag.config.RagEmbeddingProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class DeterministicEmbeddingClient implements EmbeddingClient {

    private final int dimension;

    public DeterministicEmbeddingClient(RagEmbeddingProperties properties) {
        this.dimension = properties.getDimension();
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        int hash = text.hashCode();
        for (int i = 0; i < dimension; i++) {
            hash = 31 * hash + i;
            vector[i] = (hash % 1000) / 1000.0f;
        }
        normalize(vector);
        return vector;
    }

    private static void normalize(float[] vector) {
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }
}