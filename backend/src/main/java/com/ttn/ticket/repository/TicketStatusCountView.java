package com.ttn.ticket.repository;

import com.ttn.ticket.domain.TicketStatus;

public interface TicketStatusCountView {
    TicketStatus getStatus();

    long getCount();
}