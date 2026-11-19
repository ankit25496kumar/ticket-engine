package com.ttn.ticket.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ttn.ticket.domain.TicketPriority;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateTicketRequest(
        String title,
        String description,
        TicketPriority priority,
        String assignee,
        String category,
        String resolutionNotes,
        String status) {}