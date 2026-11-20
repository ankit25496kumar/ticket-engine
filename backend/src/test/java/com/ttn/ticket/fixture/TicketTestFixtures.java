package com.ttn.ticket.fixture;

import com.ttn.rag.retrieval.RetrievedChunk;
import com.ttn.ticket.domain.Comment;
import com.ttn.ticket.domain.Ticket;
import com.ttn.ticket.domain.TicketPriority;
import com.ttn.ticket.domain.TicketStatus;
import java.time.Instant;
import java.util.UUID;

public final class TicketTestFixtures {

    public static final Instant FIXED_INSTANT = Instant.parse("2025-01-15T10:00:00Z");

    private TicketTestFixtures() {}

    public static Ticket ticket(String ticketId, TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setTicketId(ticketId);
        ticket.setTitle("Sample title");
        ticket.setDescription("Sample description");
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setStatus(status);
        ticket.setAssignee("agent@example.com");
        ticket.setCategory("general");
        ticket.setCreatedAt(FIXED_INSTANT);
        ticket.setUpdatedAt(FIXED_INSTANT);
        return ticket;
    }

    public static Comment comment(String ticketId, String content) {
        Comment comment = new Comment();
        comment.setId(UUID.fromString("00000000-0000-4000-8000-000000000001"));
        comment.setTicketId(ticketId);
        comment.setAuthor("user@example.com");
        comment.setContent(content);
        comment.setCreatedAt(FIXED_INSTANT);
        return comment;
    }

    public static RetrievedChunk retrievedChunk(String ticketId, double similarity) {
        return new RetrievedChunk(
                ticketId,
                "Chunk title",
                "Chunk content for " + ticketId,
                TicketStatus.OPEN,
                TicketPriority.MEDIUM,
                "agent@example.com",
                "general",
                0.1,
                similarity);
    }
}