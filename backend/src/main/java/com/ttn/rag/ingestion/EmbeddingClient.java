package com.ttn.rag.ingestion;

public interface EmbeddingClient {

    float[] embed(String text);
}