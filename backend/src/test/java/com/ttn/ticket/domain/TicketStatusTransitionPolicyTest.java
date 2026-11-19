package com.ttn.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TicketStatusTransitionPolicyTest {

    private final TicketStatusTransitionPolicy policy = new TicketStatusTransitionPolicy();

    @Test
    void allowsSpecifiedEdges() {
        assertThat(policy.allow(TicketStatus.OPEN, TicketStatus.IN_PROGRESS)).isTrue();
        assertThat(policy.allow(TicketStatus.OPEN, TicketStatus.CANCELLED)).isTrue();
        assertThat(policy.allow(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED)).isTrue();
        assertThat(policy.allow(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED)).isTrue();
        assertThat(policy.allow(TicketStatus.RESOLVED, TicketStatus.CLOSED)).isTrue();
    }

    @Test
    void rejectsSameStateAndSkips() {
        for (TicketStatus s : TicketStatus.values()) {
            assertThat(policy.allow(s, s)).isFalse();
        }
        assertThat(policy.allow(TicketStatus.OPEN, TicketStatus.CLOSED)).isFalse();
        assertThat(policy.allow(TicketStatus.OPEN, TicketStatus.RESOLVED)).isFalse();
        assertThat(policy.allow(TicketStatus.RESOLVED, TicketStatus.CANCELLED)).isFalse();
        assertThat(policy.allow(TicketStatus.CLOSED, TicketStatus.OPEN)).isFalse();
        assertThat(policy.allow(TicketStatus.CANCELLED, TicketStatus.OPEN)).isFalse();
    }
}