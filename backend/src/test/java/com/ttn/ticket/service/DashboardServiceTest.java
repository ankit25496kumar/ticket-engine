package com.ttn.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ttn.ticket.domain.Ticket;
import com.ttn.ticket.domain.TicketStatus;
import com.ttn.ticket.fixture.TicketTestFixtures;
import com.ttn.ticket.repository.TicketAssigneeCountView;
import com.ttn.ticket.repository.TicketRepository;
import com.ttn.ticket.repository.TicketStatusCountView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private TicketRepository ticketRepository;

    @Test
    void getSummaryAggregatesStatusAssigneeAndRecentTickets() {
        DashboardService service = new DashboardService(ticketRepository);
        when(ticketRepository.count()).thenReturn(3L);
        when(ticketRepository.countByStatus())
                .thenReturn(
                        List.of(
                                statusRow(TicketStatus.OPEN, 2),
                                statusRow(TicketStatus.RESOLVED, 1)));
        when(ticketRepository.countByAssignee())
                .thenReturn(
                        List.of(
                                assigneeRow("b@t.com", 2),
                                assigneeRow("a@t.com", 1)));
        Ticket recent = TicketTestFixtures.ticket("TKT-REC", TicketStatus.OPEN);
        when(ticketRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(List.of(recent));

        var summary = service.getSummary();

        assertThat(summary.totalTickets()).isEqualTo(3);
        assertThat(summary.byStatus()).hasSize(5);
        assertThat(summary.byStatus().stream().filter(s -> s.status() == TicketStatus.OPEN).findFirst())
                .get()
                .extracting("count")
                .isEqualTo(2L);
        assertThat(summary.byStatus().stream().filter(s -> s.status() == TicketStatus.CLOSED).findFirst())
                .get()
                .extracting("count")
                .isEqualTo(0L);
        assertThat(summary.byAssignee()).hasSize(2);
        assertThat(summary.byAssignee().get(0).assignee()).isEqualTo("b@t.com");
        assertThat(summary.byAssignee().get(0).count()).isEqualTo(2L);
        assertThat(summary.recentTickets()).hasSize(1);
        assertThat(summary.recentTickets().get(0).ticketId()).isEqualTo("TKT-REC");
    }

    private static TicketStatusCountView statusRow(TicketStatus status, long count) {
        return new TicketStatusCountView() {
            @Override
            public TicketStatus getStatus() {
                return status;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    private static TicketAssigneeCountView assigneeRow(String assignee, long count) {
        return new TicketAssigneeCountView() {
            @Override
            public String getAssignee() {
                return assignee;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }
}