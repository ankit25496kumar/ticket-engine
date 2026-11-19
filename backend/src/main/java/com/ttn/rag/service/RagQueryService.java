package com.ttn.rag.service;

import com.ttn.rag.api.dto.RagQueryResponse;
import com.ttn.rag.generation.CitationValidator;
import com.ttn.rag.generation.GeneratedAnswer;
import com.ttn.rag.generation.GeneratorClient;
import com.ttn.rag.generation.GeneratorException;
import com.ttn.rag.generation.GeneratorPromptBuilder;
import com.ttn.rag.ingestion.EmbeddingClient;
import com.ttn.rag.ingestion.EmbeddingException;
import com.ttn.rag.retrieval.RagRetrievalService;
import com.ttn.rag.retrieval.RetrievedChunk;
import com.ttn.rag.review.RagOutputReviewer;
import com.ttn.ticket.repository.TicketRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RagQueryService {

    private final EmbeddingClient embeddingClient;
    private final RagRetrievalService retrievalService;
    private final GeneratorClient generatorClient;
    private final RagOutputReviewer outputReviewer;
    private final TicketRepository ticketRepository;

    public RagQueryService(
            EmbeddingClient embeddingClient,
            RagRetrievalService retrievalService,
            GeneratorClient generatorClient,
            RagOutputReviewer outputReviewer,
            TicketRepository ticketRepository) {
        this.embeddingClient = embeddingClient;
        this.retrievalService = retrievalService;
        this.generatorClient = generatorClient;
        this.outputReviewer = outputReviewer;
        this.ticketRepository = ticketRepository;
    }

    public RagQueryResponse query(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isBlank()) {
            return RagQueryResponse.error(query, "INVALID_QUERY", "Query must not be blank");
        }
        try {
            float[] embedding = embeddingClient.embed(query);
            List<RetrievedChunk> kept = retrievalService.retrieve(embedding);
            if (kept.isEmpty()) {
                return RagQueryResponse.noMatch(query);
            }
            List<String> contextChunks = kept.stream().map(RetrievedChunk::content).toList();
            Set<String> keepTicketIds = kept.stream().map(RetrievedChunk::ticketId).collect(Collectors.toSet());
            String contextJoined = String.join("\n---\n", contextChunks);
            GeneratedAnswer generated =
                    generatorClient.generate(
                            query, GeneratorPromptBuilder.GROUNDING_INSTRUCTIONS, contextChunks);
            var review = outputReviewer.review(query, generated.text(), contextJoined);
            if (!review.passed()) {
                return RagQueryResponse.noMatch(query);
            }
            List<String> cited =
                    generated.citedTicketIds().isEmpty()
                            ? new ArrayList<>(keepTicketIds)
                            : generated.citedTicketIds();
            if (!CitationValidator.citationsSubsetOfKeepSet(cited, keepTicketIds)) {
                return RagQueryResponse.noMatch(query);
            }
            double maxSimilarity =
                    kept.stream().mapToDouble(RetrievedChunk::similarity).max().orElse(0.0);
            List<RagQueryResponse.Citation> citations = buildCitations(kept, cited);
            return RagQueryResponse.answer(query, generated.text(), citations, maxSimilarity);
        } catch (EmbeddingException ex) {
            return RagQueryResponse.error(query, "EMBEDDING_UNAVAILABLE", ex.getMessage());
        } catch (GeneratorException ex) {
            return RagQueryResponse.error(query, "GENERATION_FAILED", ex.getMessage());
        } catch (Exception ex) {
            return RagQueryResponse.error(query, "INTERNAL_ERROR", "Unexpected error");
        }
    }

    private List<RagQueryResponse.Citation> buildCitations(List<RetrievedChunk> kept, List<String> citedIds) {
        Map<String, RetrievedChunk> byId = new LinkedHashMap<>();
        for (RetrievedChunk chunk : kept) {
            byId.putIfAbsent(chunk.ticketId(), chunk);
        }
        LinkedHashSet<String> ordered = new LinkedHashSet<>(citedIds);
        List<RagQueryResponse.Citation> out = new ArrayList<>();
        List<String> sorted = new ArrayList<>(ordered);
        sorted.sort(
                Comparator.<String>comparingDouble(
                                id -> byId.containsKey(id) ? byId.get(id).similarity() : 0.0)
                        .reversed()
                        .thenComparing(id -> id));
        for (String ticketId : sorted) {
            String title =
                    ticketRepository
                            .findByTicketId(ticketId)
                            .map(t -> t.getTitle())
                            .orElseGet(() -> byId.containsKey(ticketId) ? byId.get(ticketId).title() : ticketId);
            out.add(new RagQueryResponse.Citation(ticketId, title));
        }
        return out;
    }
}