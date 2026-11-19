package com.ttn.ticket.domain;

public class InvalidStatusTransitionException extends RuntimeException {

    private final TicketStatus from;
    private final TicketStatus to;

    public InvalidStatusTransitionException(TicketStatus from, TicketStatus to) {
        super("Cannot transition from " + from + " to " + to + ".");
        this.from = from;
        this.to = to;
    }

    public TicketStatus getFrom() {
        return from;
    }

    public TicketStatus getTo() {
        return to;
    }
}