package com.ttn.ticket.api.dto;

import com.ttn.ticket.domain.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull TicketStatus status) {}