package com.ttn.rag.retrieval;

import com.ttn.rag.config.RagRetrievalProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RagRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalService.class);

    private final VectorSearchPort vectorSearchPort;
    private final RagRetrievalProperties properties;

    public RagRetrievalService(VectorSearchPort vectorSearchPort, RagRetrievalProperties properties) {
        this.vectorSearchPort = vectorSearchPort;
        this.properties = properties;
    }

    public List<RetrievedChunk> retrieve(float[] queryEmbedding) {
        int topK = properties.getTopK();
        double threshold = properties.getSimilarityThreshold();
        List<RetrievedChunk> candidates = vectorSearchPort.findNearest(queryEmbedding, topK);
        List<RetrievedChunk> kept = new ArrayList<>();
        for (RetrievedChunk chunk : candidates) {
            if (chunk.similarity() >= threshold) {
                kept.add(chunk);
            }
        }
        kept.sort(Comparator.comparingDouble(RetrievedChunk::similarity).reversed());
        log.debug("retrieval topK={} threshold={} rawHits={} kept={}", topK, threshold, candidates.size(), kept.size());
        return kept;
    }
}