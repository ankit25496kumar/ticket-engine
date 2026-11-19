package com.ttn.rag.ingestion;

import com.ttn.ticket.domain.Comment;
import com.ttn.ticket.domain.Ticket;
import com.ttn.ticket.repository.CommentRepository;
import com.ttn.ticket.repository.TicketRepository;
import com.ttn.ticket.service.TicketIngestionHooks;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TicketIngestionService implements TicketIngestionHooks {

    private static final Logger log = LoggerFactory.getLogger(TicketIngestionService.class);

    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final KnowledgeDocumentBuilder documentBuilder;
    private final SectionChunker chunker;
    private final EmbeddingClient embeddingClient;
    private final TicketVectorStore ticketVectorStore;

    public TicketIngestionService(
            TicketRepository ticketRepository,
            CommentRepository commentRepository,
            KnowledgeDocumentBuilder documentBuilder,
            SectionChunker chunker,
            EmbeddingClient embeddingClient,
            TicketVectorStore ticketVectorStore) {
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.documentBuilder = documentBuilder;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.ticketVectorStore = ticketVectorStore;
    }

    @Override
    public void afterTicketPersisted(String ticketId) {
        try {
            ingest(ticketId);
        } catch (Exception ex) {
            log.error("Ingestion failed for ticketId={}", ticketId, ex);
        }
    }

    public void ingest(String ticketId) {
        Ticket ticket =
                ticketRepository
                        .findByTicketId(ticketId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown ticket " + ticketId));
        List<Comment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        if (!documentBuilder.hasUsableContent(ticket, comments)) {
            ticketVectorStore.deleteByTicketId(ticketId);
            return;
        }
        String document = documentBuilder.build(ticket, comments);
        List<String> texts = chunker.chunk(ticket.getTicketId(), ticket.getTitle(), document);
        String fingerprint = fingerprint(document);
        List<VectorChunk> chunks = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            float[] embedding = embeddingClient.embed(text);
            chunks.add(
                    new VectorChunk(
                            i,
                            text,
                            ticket.getTicketId(),
                            ticket.getStatus(),
                            ticket.getPriority(),
                            ticket.getAssignee(),
                            ticket.getCategory(),
                            fingerprint,
                            embedding));
        }
        ticketVectorStore.replaceByTicketId(ticketId, chunks);
        log.debug("Ingested ticketId={} chunkCount={}", ticketId, chunks.size());
    }

    private static String fingerprint(String document) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(document.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            return Integer.toHexString(document.hashCode());
        }
    }
}