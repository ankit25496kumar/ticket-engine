package com.ttn.ticket.api.dto;

import com.ttn.ticket.domain.Comment;
import java.time.Instant;
import java.util.UUID;

public record CommentResponse(UUID id, String ticketId, String author, String content, Instant createdAt) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTicketId(),
                comment.getAuthor(),
                comment.getContent(),
                comment.getCreatedAt());
    }
}