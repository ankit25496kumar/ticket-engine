package com.ttn.ticket.api.dto;

import com.ttn.ticket.domain.Ticket;
import com.ttn.ticket.domain.TicketPriority;
import com.ttn.ticket.domain.TicketStatus;
import java.time.Instant;

public record TicketResponse(
        String ticketId,
        String title,
        String description,
        TicketPriority priority,
        TicketStatus status,
        String assignee,
        String category,
        String resolutionNotes,
        Instant createdAt,
        Instant updatedAt) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getTicketId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getAssignee(),
                ticket.getCategory(),
                ticket.getResolutionNotes(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }
}