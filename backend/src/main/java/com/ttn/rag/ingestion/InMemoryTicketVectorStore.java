package com.ttn.rag.ingestion;

import com.ttn.rag.retrieval.RetrievedChunk;
import com.ttn.rag.retrieval.VectorSearchPort;
import com.ttn.ticket.domain.TicketPriority;
import com.ttn.ticket.domain.TicketStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class InMemoryTicketVectorStore implements TicketVectorStore, VectorSearchPort {

    private final Map<String, List<VectorChunk>> byTicket = new ConcurrentHashMap<>();

    @Override
    public void replaceByTicketId(String ticketId, List<VectorChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            byTicket.remove(ticketId);
            return;
        }
        byTicket.put(ticketId, List.copyOf(chunks));
    }

    @Override
    public void deleteByTicketId(String ticketId) {
        byTicket.remove(ticketId);
    }

    @Override
    public List<RetrievedChunk> findNearest(float[] queryEmbedding, int limit) {
        List<Scored> all = new ArrayList<>();
        for (List<VectorChunk> chunks : byTicket.values()) {
            for (VectorChunk chunk : chunks) {
                double distance = VectorMath.cosineDistance(queryEmbedding, chunk.embedding());
                double similarity = VectorMath.similarityFromDistance(distance);
                String title = extractTitle(chunk.content());
                all.add(new Scored(chunk, distance, similarity, title));
            }
        }
        all.sort(Comparator.comparingDouble(Scored::distance));
        return all.stream()
                .limit(limit)
                .map(s -> new RetrievedChunk(
                        s.chunk().ticketId(),
                        s.title(),
                        s.chunk().content(),
                        s.chunk().status(),
                        s.chunk().priority(),
                        s.chunk().assignee(),
                        s.chunk().category(),
                        s.distance(),
                        s.similarity()))
                .toList();
    }

    private static String extractTitle(String content) {
        int nl = content.indexOf('\n');
        String first = nl > 0 ? content.substring(0, nl) : content;
        if (first.startsWith("[Ticket ")) {
            int close = first.indexOf(']');
            if (close > 0 && close + 2 < first.length()) {
                return first.substring(close + 2).trim();
            }
        }
        return first;
    }

    private record Scored(VectorChunk chunk, double distance, double similarity, String title) {}
}