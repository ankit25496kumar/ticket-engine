package com.ttn.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttn.ticket.domain.InvalidStatusTransitionException;
import com.ttn.ticket.domain.Ticket;
import com.ttn.ticket.domain.TicketNotFoundException;
import com.ttn.ticket.domain.TicketPriority;
import com.ttn.ticket.domain.TicketStatus;
import com.ttn.ticket.domain.TicketStatusTransitionPolicy;
import com.ttn.ticket.fixture.TicketTestFixtures;
import com.ttn.ticket.repository.TicketRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketIdGenerator ticketIdGenerator;
    @Mock private TicketIngestionHooks ingestionHooks;

    private final TicketStatusTransitionPolicy transitionPolicy = new TicketStatusTransitionPolicy();
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService =
                new TicketService(
                        ticketRepository,
                        ticketIdGenerator,
                        transitionPolicy,
                        Optional.of(ingestionHooks));
    }

    @Test
    void createSavesOpenTicketAndTriggersIngest() {
        when(ticketIdGenerator.nextTicketId()).thenReturn("TKT-1001");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket created =
                ticketService.create(
                        "Title", "Desc", TicketPriority.HIGH, "a@t.com", "billing");

        assertThat(created.getTicketId()).isEqualTo("TKT-1001");
        assertThat(created.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(created.getPriority()).isEqualTo(TicketPriority.HIGH);
        verify(ingestionHooks).afterTicketPersisted("TKT-1001");
    }

    @Test
    void createDefaultsNullPriorityToMedium() {
        when(ticketIdGenerator.nextTicketId()).thenReturn("TKT-1002");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket created = ticketService.create("T", "D", null, null, null);

        assertThat(created.getPriority()).isEqualTo(TicketPriority.MEDIUM);
    }

    @Test
    void getByTicketIdReturnsTicketWhenFound() {
        Ticket ticket = TicketTestFixtures.ticket("TKT-1", TicketStatus.OPEN);
        when(ticketRepository.findByTicketId("TKT-1")).thenReturn(Optional.of(ticket));

        assertThat(ticketService.getByTicketId("TKT-1")).isSameAs(ticket);
    }

    @Test
    void getByTicketIdThrowsWhenMissing() {
        when(ticketRepository.findByTicketId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getByTicketId("missing"))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void listDelegatesToRepository() {
        List<Ticket> expected = List.of(TicketTestFixtures.ticket("TKT-1", TicketStatus.OPEN));
        when(ticketRepository.findFiltered(
                        TicketStatus.OPEN, TicketPriority.HIGH, "a@t.com", "billing"))
                .thenReturn(expected);

        assertThat(
                        ticketService.list(
                                TicketStatus.OPEN, TicketPriority.HIGH, "a@t.com", "billing"))
                .isEqualTo(expected);
    }

    @Test
    void updateFieldsAppliesPartialUpdatesAndTriggersIngest() {
        Ticket existing = TicketTestFixtures.ticket("TKT-9", TicketStatus.OPEN);
        when(ticketRepository.findByTicketId("TKT-9")).thenReturn(Optional.of(existing));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket updated = ticketService.updateFields("TKT-9", "New title", null, null, null, null, null);

        assertThat(updated.getTitle()).isEqualTo("New title");
        assertThat(updated.getDescription()).isEqualTo("Sample description");
        verify(ingestionHooks).afterTicketPersisted("TKT-9");
    }

    @Test
    void changeStatusAllowsLegalTransitionAndSaves() {
        Ticket existing = TicketTestFixtures.ticket("TKT-3", TicketStatus.OPEN);
        when(ticketRepository.findByTicketId("TKT-3")).thenReturn(Optional.of(existing));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        Ticket updated = ticketService.changeStatus("TKT-3", TicketStatus.IN_PROGRESS);

        assertThat(updated.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        verify(ingestionHooks).afterTicketPersisted("TKT-3");
    }

    @Test
    void changeStatusRejectsIllegalTransitionWithoutSave() {
        Ticket existing = TicketTestFixtures.ticket("TKT-4", TicketStatus.OPEN);
        when(ticketRepository.findByTicketId("TKT-4")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> ticketService.changeStatus("TKT-4", TicketStatus.CLOSED))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(ticketRepository, never()).save(any());
        verify(ingestionHooks, never()).afterTicketPersisted(eq("TKT-4"));
    }
}