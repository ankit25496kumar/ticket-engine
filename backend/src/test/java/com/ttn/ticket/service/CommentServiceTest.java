package com.ttn.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttn.ticket.domain.Comment;
import com.ttn.ticket.domain.TicketNotFoundException;
import com.ttn.ticket.domain.TicketStatus;
import com.ttn.ticket.fixture.TicketTestFixtures;
import com.ttn.ticket.repository.CommentRepository;
import com.ttn.ticket.repository.TicketRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private TicketIngestionHooks ingestionHooks;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService =
                new CommentService(
                        commentRepository, ticketRepository, Optional.of(ingestionHooks));
    }

    @Test
    void addCommentSavesWhenTicketExists() {
        when(ticketRepository.findByTicketId("TKT-1")).thenReturn(Optional.of(
                TicketTestFixtures.ticket("TKT-1", TicketStatus.OPEN)));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        Comment saved = commentService.addComment("TKT-1", "author@t.com", "Hello");

        assertThat(saved.getTicketId()).isEqualTo("TKT-1");
        assertThat(saved.getContent()).isEqualTo("Hello");
        verify(ingestionHooks).afterTicketPersisted("TKT-1");
    }

    @Test
    void addCommentThrowsWhenTicketMissing() {
        when(ticketRepository.findByTicketId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.addComment("missing", "a", "c"))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void listCommentsReturnsOrderedList() {
        when(ticketRepository.findByTicketId("TKT-2")).thenReturn(Optional.of(
                TicketTestFixtures.ticket("TKT-2", TicketStatus.OPEN)));
        List<Comment> comments =
                List.of(
                        TicketTestFixtures.comment("TKT-2", "first"),
                        TicketTestFixtures.comment("TKT-2", "second"));
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc("TKT-2")).thenReturn(comments);

        assertThat(commentService.listComments("TKT-2")).isEqualTo(comments);
    }

    @Test
    void listCommentsThrowsWhenTicketMissing() {
        when(ticketRepository.findByTicketId("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.listComments("nope"))
                .isInstanceOf(TicketNotFoundException.class);
    }
}