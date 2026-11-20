package com.ttn.rag.ingestion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttn.ticket.domain.Ticket;
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
class TicketIngestionServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private KnowledgeDocumentBuilder documentBuilder;
    @Mock private SectionChunker chunker;
    @Mock private EmbeddingClient embeddingClient;
    @Mock private TicketVectorStore ticketVectorStore;

    private TicketIngestionService service;

    @BeforeEach
    void setUp() {
        service =
                new TicketIngestionService(
                        ticketRepository,
                        commentRepository,
                        documentBuilder,
                        chunker,
                        embeddingClient,
                        ticketVectorStore);
    }

    @Test
    void ingestThrowsForUnknownTicket() {
        when(ticketRepository.findByTicketId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ingest("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void ingestDeletesVectorsWhenNoUsableContent() {
        Ticket ticket = emptyContentTicket("TKT-EMPTY");
        when(ticketRepository.findByTicketId("TKT-EMPTY")).thenReturn(Optional.of(ticket));
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc("TKT-EMPTY")).thenReturn(List.of());
        when(documentBuilder.hasUsableContent(ticket, List.of())).thenReturn(false);

        service.ingest("TKT-EMPTY");

        verify(ticketVectorStore).deleteByTicketId("TKT-EMPTY");
        verify(embeddingClient, never()).embed(anyString());
    }

    @Test
    void ingestEmbedsChunksAndReplacesVectors() throws EmbeddingException {
        Ticket ticket = TicketTestFixtures.ticket("TKT-1", TicketStatus.OPEN);
        when(ticketRepository.findByTicketId("TKT-1")).thenReturn(Optional.of(ticket));
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc("TKT-1")).thenReturn(List.of());
        when(documentBuilder.hasUsableContent(eq(ticket), anyList())).thenReturn(true);
        when(documentBuilder.build(eq(ticket), anyList())).thenReturn("document body");
        when(chunker.chunk("TKT-1", ticket.getTitle(), "document body"))
                .thenReturn(List.of("chunk-a", "chunk-b"));
        when(embeddingClient.embed(anyString())).thenReturn(new float[] {0.5f, 0.5f});

        service.ingest("TKT-1");

        verify(embeddingClient).embed("chunk-a");
        verify(embeddingClient).embed("chunk-b");
        verify(ticketVectorStore).replaceByTicketId(eq("TKT-1"), anyList());
    }

    @Test
    void afterTicketPersistedSwallowsIngestFailures() {
        when(ticketRepository.findByTicketId("TKT-X")).thenReturn(Optional.empty());

        service.afterTicketPersisted("TKT-X");

        verify(ticketVectorStore, never()).replaceByTicketId(anyString(), anyList());
    }

    private static Ticket emptyContentTicket(String ticketId) {
        Ticket ticket = new Ticket();
        ticket.setTicketId(ticketId);
        ticket.setTitle("   ");
        ticket.setDescription(null);
        ticket.setStatus(TicketStatus.OPEN);
        return ticket;
    }
}