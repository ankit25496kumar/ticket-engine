package com.ttn.ticket.api.dto;

import com.ttn.ticket.domain.TicketStatus;

public record StatusCount(TicketStatus status, long count) {}