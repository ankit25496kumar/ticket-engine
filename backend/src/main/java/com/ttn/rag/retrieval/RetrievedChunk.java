package com.ttn.rag.retrieval;

import com.ttn.ticket.domain.TicketPriority;
import com.ttn.ticket.domain.TicketStatus;

public record RetrievedChunk(
        String ticketId,
        String title,
        String content,
        TicketStatus status,
        TicketPriority priority,
        String assignee,
        String category,
        double distance,
        double similarity) {}