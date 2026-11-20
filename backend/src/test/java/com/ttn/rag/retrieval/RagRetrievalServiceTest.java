package com.ttn.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttn.rag.config.RagRetrievalProperties;
import com.ttn.ticket.fixture.TicketTestFixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceTest {

    @Mock private VectorSearchPort vectorSearchPort;

    private RagRetrievalService service;

    @BeforeEach
    void setUp() {
        RagRetrievalProperties properties = new RagRetrievalProperties();
        properties.setTopK(3);
        properties.setSimilarityThreshold(0.72);
        service = new RagRetrievalService(vectorSearchPort, properties);
    }

    @Test
    void retrieveAppliesTopKThresholdAndSortsBySimilarity() {
        float[] embedding = new float[] {0.1f, 0.2f};
        RetrievedChunk low = TicketTestFixtures.retrievedChunk("TKT-L", 0.5);
        RetrievedChunk high = TicketTestFixtures.retrievedChunk("TKT-H", 0.9);
        RetrievedChunk mid = TicketTestFixtures.retrievedChunk("TKT-M", 0.8);
        when(vectorSearchPort.findNearest(eq(embedding), eq(3)))
                .thenReturn(List.of(low, high, mid));

        List<RetrievedChunk> kept = service.retrieve(embedding);

        verify(vectorSearchPort).findNearest(any(), eq(3));
        assertThat(kept).extracting(RetrievedChunk::ticketId).containsExactly("TKT-H", "TKT-M");
        assertThat(kept).noneMatch(c -> c.ticketId().equals("TKT-L"));
    }

    @Test
    void retrieveReturnsEmptyWhenCorpusEmpty() {
        float[] embedding = new float[] {1f};
        when(vectorSearchPort.findNearest(embedding, 3)).thenReturn(List.of());

        assertThat(service.retrieve(embedding)).isEmpty();
    }
}