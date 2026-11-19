package com.ttn.ticket.service;

import com.ttn.ticket.domain.InvalidStatusTransitionException;
import com.ttn.ticket.domain.Ticket;
import com.ttn.ticket.domain.TicketNotFoundException;
import com.ttn.ticket.domain.TicketPriority;
import com.ttn.ticket.domain.TicketStatus;
import com.ttn.ticket.domain.TicketStatusTransitionPolicy;
import com.ttn.ticket.repository.TicketRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketIdGenerator ticketIdGenerator;
    private final TicketStatusTransitionPolicy transitionPolicy;
    private final Optional<TicketIngestionHooks> ingestionHooks;

    public TicketService(
            TicketRepository ticketRepository,
            TicketIdGenerator ticketIdGenerator,
            TicketStatusTransitionPolicy transitionPolicy,
            Optional<TicketIngestionHooks> ingestionHooks) {
        this.ticketRepository = ticketRepository;
        this.ticketIdGenerator = ticketIdGenerator;
        this.transitionPolicy = transitionPolicy;
        this.ingestionHooks = ingestionHooks;
    }

    @Transactional
    public Ticket create(
            String title,
            String description,
            TicketPriority priority,
            String assignee,
            String category) {
        Instant now = Instant.now();
        Ticket ticket = new Ticket();
        ticket.setTicketId(ticketIdGenerator.nextTicketId());
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setPriority(priority != null ? priority : TicketPriority.MEDIUM);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setAssignee(assignee);
        ticket.setCategory(category);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        Ticket saved = ticketRepository.save(ticket);
        triggerIngest(saved.getTicketId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Ticket getByTicketId(String ticketId) {
        return ticketRepository
                .findByTicketId(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
    }

    @Transactional(readOnly = true)
    public List<Ticket> list(
            TicketStatus status, TicketPriority priority, String assignee, String category) {
        return ticketRepository.findFiltered(status, priority, assignee, category);
    }

    @Transactional
    public Ticket updateFields(
            String ticketId,
            String title,
            String description,
            TicketPriority priority,
            String assignee,
            String category,
            String resolutionNotes) {
        Ticket ticket = getByTicketId(ticketId);
        if (title != null) {
            ticket.setTitle(title);
        }
        if (description != null) {
            ticket.setDescription(description);
        }
        if (priority != null) {
            ticket.setPriority(priority);
        }
        if (assignee != null) {
            ticket.setAssignee(assignee);
        }
        if (category != null) {
            ticket.setCategory(category);
        }
        if (resolutionNotes != null) {
            ticket.setResolutionNotes(resolutionNotes);
        }
        ticket.setUpdatedAt(Instant.now());
        Ticket saved = ticketRepository.save(ticket);
        triggerIngest(saved.getTicketId());
        return saved;
    }

    @Transactional
    public Ticket changeStatus(String ticketId, TicketStatus to) {
        Ticket ticket = getByTicketId(ticketId);
        TicketStatus from = ticket.getStatus();
        if (!transitionPolicy.allow(from, to)) {
            throw new InvalidStatusTransitionException(from, to);
        }
        ticket.setStatus(to);
        ticket.setUpdatedAt(Instant.now());
        Ticket saved = ticketRepository.save(ticket);
        triggerIngest(saved.getTicketId());
        return saved;
    }

    private void triggerIngest(String ticketId) {
        ingestionHooks.ifPresent(hooks -> hooks.afterTicketPersisted(ticketId));
    }
}