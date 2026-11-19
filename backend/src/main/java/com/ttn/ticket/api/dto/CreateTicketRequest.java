package com.ttn.ticket.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ttn.ticket.domain.TicketPriority;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateTicketRequest(
        @NotBlank String title,
        String description,
        TicketPriority priority,
        String assignee,
        String category,
        String ticketId,
        String status) {}