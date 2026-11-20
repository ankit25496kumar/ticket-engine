package com.ttn.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.ttn.rag.generation.GeneratedAnswer;
import com.ttn.rag.generation.GeneratorClient;
import com.ttn.rag.generation.GeneratorException;
import com.ttn.rag.ingestion.EmbeddingClient;
import com.ttn.rag.ingestion.EmbeddingException;
import com.ttn.rag.retrieval.RagRetrievalService;
import com.ttn.rag.retrieval.RetrievedChunk;
import com.ttn.rag.review.RagOutputReviewer;
import com.ttn.ticket.domain.TicketStatus;
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
class RagQueryServiceTest {

    @Mock private EmbeddingClient embeddingClient;
    @Mock private RagRetrievalService retrievalService;
    @Mock private GeneratorClient generatorClient;
    @Mock private RagOutputReviewer outputReviewer;
    @Mock private TicketRepository ticketRepository;

    private RagQueryService service;

    @BeforeEach
    void setUp() {
        service =
                new RagQueryService(
                        embeddingClient,
                        retrievalService,
                        generatorClient,
                        outputReviewer,
                        ticketRepository);
    }

    @Test
    void queryBlankReturnsInvalidQueryError() {
        var response = service.query("   ");

        assertThat(response.outcome()).isEqualTo("ERROR");
        assertThat(response.error().code()).isEqualTo("INVALID_QUERY");
    }

    @Test
    void queryEmptyRetrievalReturnsNoMatch() throws EmbeddingException {
        when(embeddingClient.embed("billing")).thenReturn(new float[] {1f});
        when(retrievalService.retrieve(any())).thenReturn(List.of());

        var response = service.query("billing");

        assertThat(response.outcome()).isEqualTo("NO_MATCH");
        assertThat(response.answer()).isNull();
    }

    @Test
    void queryFailedReviewReturnsNoMatch() throws Exception {
        when(embeddingClient.embed("q")).thenReturn(new float[] {1f});
        RetrievedChunk chunk = TicketTestFixtures.retrievedChunk("TKT-1", 0.85);
        when(retrievalService.retrieve(any())).thenReturn(List.of(chunk));
        when(generatorClient.generate(anyString(), anyString(), anyList()))
                .thenReturn(new GeneratedAnswer("bad answer", List.of()));
        when(outputReviewer.review(anyString(), anyString(), anyString()))
                .thenReturn(new RagOutputReviewer.ReviewResult(false, "conflict"));

        var response = service.query("q");

        assertThat(response.outcome()).isEqualTo("NO_MATCH");
    }

    @Test
    void queryInvalidCitationsReturnNoMatch() throws Exception {
        when(embeddingClient.embed("q")).thenReturn(new float[] {1f});
        RetrievedChunk chunk = TicketTestFixtures.retrievedChunk("TKT-1", 0.85);
        when(retrievalService.retrieve(any())).thenReturn(List.of(chunk));
        when(generatorClient.generate(anyString(), anyString(), anyList()))
                .thenReturn(new GeneratedAnswer("ok", List.of("TKT-OTHER")));
        when(outputReviewer.review(anyString(), anyString(), anyString()))
                .thenReturn(new RagOutputReviewer.ReviewResult(true, null));

        var response = service.query("q");

        assertThat(response.outcome()).isEqualTo("NO_MATCH");
    }

    @Test
    void queryHappyPathReturnsAnswer() throws Exception {
        when(embeddingClient.embed("reset")).thenReturn(new float[] {1f});
        RetrievedChunk chunk = TicketTestFixtures.retrievedChunk("TKT-1", 0.9);
        when(retrievalService.retrieve(any())).thenReturn(List.of(chunk));
        when(generatorClient.generate(anyString(), anyString(), anyList()))
                .thenReturn(new GeneratedAnswer("Use the reset link.", List.of("TKT-1")));
        when(outputReviewer.review(anyString(), anyString(), anyString()))
                .thenReturn(new RagOutputReviewer.ReviewResult(true, null));
        when(ticketRepository.findByTicketId("TKT-1"))
                .thenReturn(Optional.of(TicketTestFixtures.ticket("TKT-1", TicketStatus.OPEN)));

        var response = service.query("reset");

        assertThat(response.outcome()).isEqualTo("ANSWER");
        assertThat(response.answer().text()).isEqualTo("Use the reset link.");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).ticketId()).isEqualTo("TKT-1");
    }

    @Test
    void queryEmbeddingFailureReturnsError() throws EmbeddingException {
        when(embeddingClient.embed("x")).thenThrow(new EmbeddingException("down"));

        var response = service.query("x");

        assertThat(response.outcome()).isEqualTo("ERROR");
        assertThat(response.error().code()).isEqualTo("EMBEDDING_UNAVAILABLE");
    }

    @Test
    void queryGenerationFailureReturnsError() throws Exception {
        when(embeddingClient.embed("x")).thenReturn(new float[] {1f});
        when(retrievalService.retrieve(any()))
                .thenReturn(List.of(TicketTestFixtures.retrievedChunk("TKT-1", 0.9)));
        when(generatorClient.generate(anyString(), anyString(), anyList()))
                .thenThrow(new GeneratorException("fail"));

        var response = service.query("x");

        assertThat(response.outcome()).isEqualTo("ERROR");
        assertThat(response.error().code()).isEqualTo("GENERATION_FAILED");
    }
}