package com.ttn.rag.retrieval;

import java.util.List;

public interface VectorSearchPort {

    List<RetrievedChunk> findNearest(float[] queryEmbedding, int limit);
}