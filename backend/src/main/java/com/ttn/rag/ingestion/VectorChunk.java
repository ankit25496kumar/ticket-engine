package com.ttn.rag.ingestion;

import com.ttn.ticket.domain.TicketPriority;
import com.ttn.ticket.domain.TicketStatus;

public record VectorChunk(
        int chunkIndex,
        String content,
        String ticketId,
        TicketStatus status,
        TicketPriority priority,
        String assignee,
        String category,
        String contentFingerprint,
        float[] embedding) {}