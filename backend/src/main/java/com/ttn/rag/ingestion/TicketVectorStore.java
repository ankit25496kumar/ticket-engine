package com.ttn.rag.ingestion;

import java.util.List;

public interface TicketVectorStore {

    void replaceByTicketId(String ticketId, List<VectorChunk> chunks);

    void deleteByTicketId(String ticketId);
}