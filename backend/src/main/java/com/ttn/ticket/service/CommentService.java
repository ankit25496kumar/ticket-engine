package com.ttn.ticket.service;

import com.ttn.ticket.domain.Comment;
import com.ttn.ticket.domain.TicketNotFoundException;
import com.ttn.ticket.repository.CommentRepository;
import com.ttn.ticket.repository.TicketRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final Optional<TicketIngestionHooks> ingestionHooks;

    public CommentService(
            CommentRepository commentRepository,
            TicketRepository ticketRepository,
            Optional<TicketIngestionHooks> ingestionHooks) {
        this.commentRepository = commentRepository;
        this.ticketRepository = ticketRepository;
        this.ingestionHooks = ingestionHooks;
    }

    @Transactional
    public Comment addComment(String ticketId, String author, String content) {
        if (!ticketRepository.findByTicketId(ticketId).isPresent()) {
            throw new TicketNotFoundException(ticketId);
        }
        Comment comment = new Comment();
        comment.setId(UUID.randomUUID());
        comment.setTicketId(ticketId);
        comment.setAuthor(author);
        comment.setContent(content);
        comment.setCreatedAt(Instant.now());
        Comment saved = commentRepository.save(comment);
        ingestionHooks.ifPresent(hooks -> hooks.afterTicketPersisted(ticketId));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Comment> listComments(String ticketId) {
        if (!ticketRepository.findByTicketId(ticketId).isPresent()) {
            throw new TicketNotFoundException(ticketId);
        }
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }
}