package com.ttn.ticket.api;

import com.ttn.ticket.api.dto.CommentResponse;
import com.ttn.ticket.api.dto.CreateCommentRequest;
import com.ttn.ticket.domain.Comment;
import com.ttn.ticket.service.CommentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public ResponseEntity<CommentResponse> add(
            @PathVariable String ticketId, @Valid @RequestBody CreateCommentRequest request) {
        Comment comment = commentService.addComment(ticketId, request.author(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(comment));
    }

    @GetMapping
    public List<CommentResponse> list(@PathVariable String ticketId) {
        return commentService.listComments(ticketId).stream().map(CommentResponse::from).toList();
    }
}