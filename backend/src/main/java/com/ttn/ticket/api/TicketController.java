package com.ttn.ticket.api;

import com.ttn.ticket.api.dto.ChangeStatusRequest;
import com.ttn.ticket.api.dto.CreateTicketRequest;
import com.ttn.ticket.api.dto.TicketResponse;
import com.ttn.ticket.api.dto.UpdateTicketRequest;
import com.ttn.ticket.domain.Ticket;
import com.ttn.ticket.domain.TicketPriority;
import com.ttn.ticket.domain.TicketStatus;
import com.ttn.ticket.service.TicketService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        if (request.ticketId() != null && !request.ticketId().isBlank()) {
            throw new IllegalArgumentException("Client must not supply ticketId");
        }
        if (request.status() != null) {
            throw new IllegalArgumentException("Client must not supply status on create");
        }
        Ticket ticket = ticketService.create(
                request.title(),
                request.description(),
                request.priority(),
                request.assignee(),
                request.category());
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketResponse.from(ticket));
    }

    @GetMapping("/{ticketId}")
    public TicketResponse get(@PathVariable String ticketId) {
        return TicketResponse.from(ticketService.getByTicketId(ticketId));
    }

    @GetMapping
    public List<TicketResponse> list(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false) String category) {
        return ticketService.list(status, priority, assignee, category).stream()
                .map(TicketResponse::from)
                .toList();
    }

    @PatchMapping("/{ticketId}")
    public TicketResponse update(@PathVariable String ticketId, @RequestBody UpdateTicketRequest request) {
        if (request.status() != null) {
            throw new IllegalArgumentException("Use PATCH /status to change status");
        }
        Ticket ticket = ticketService.updateFields(
                ticketId,
                request.title(),
                request.description(),
                request.priority(),
                request.assignee(),
                request.category(),
                request.resolutionNotes());
        return TicketResponse.from(ticket);
    }

    @PatchMapping("/{ticketId}/status")
    public TicketResponse changeStatus(
            @PathVariable String ticketId, @Valid @RequestBody ChangeStatusRequest request) {
        Ticket ticket = ticketService.changeStatus(ticketId, request.status());
        return TicketResponse.from(ticket);
    }
}