package com.ttn.ticket.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure transition policy — full 5×5 matrix per spec/state-machine.md §2.3.
 */
public final class TicketStatusTransitionPolicy {

    private final Map<TicketStatus, Set<TicketStatus>> allowed = new EnumMap<>(TicketStatus.class);

    public TicketStatusTransitionPolicy() {
        allowed.put(TicketStatus.OPEN, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));
        allowed.put(
                TicketStatus.IN_PROGRESS,
                EnumSet.of(TicketStatus.RESOLVED, TicketStatus.CANCELLED));
        allowed.put(TicketStatus.RESOLVED, EnumSet.of(TicketStatus.CLOSED));
    }

    public boolean allow(TicketStatus from, TicketStatus to) {
        if (from == null || to == null) {
            return false;
        }
        Set<TicketStatus> targets = allowed.get(from);
        return targets != null && targets.contains(to);
    }
}