package com.ttn.rag.ingestion;

import com.pgvector.PGvector;
import com.ttn.rag.retrieval.RetrievedChunk;
import com.ttn.rag.retrieval.VectorSearchPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!test")
@ConditionalOnProperty(name = "rag.vector-store", havingValue = "pgvector", matchIfMissing = true)
public class PgVectorTicketVectorStore implements TicketVectorStore, VectorSearchPort {

    private final JdbcTemplate jdbcTemplate;

    public PgVectorTicketVectorStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void replaceByTicketId(String ticketId, List<VectorChunk> chunks) {
        jdbcTemplate.update("DELETE FROM ticket_chunk WHERE ticket_id = ?", ticketId);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        for (VectorChunk chunk : chunks) {
            jdbcTemplate.update(
                    """
                    INSERT INTO ticket_chunk (
                        id, ticket_id, chunk_index, content, status, priority, assignee, category,
                        content_fingerprint, ingested_at, embedding
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?)
                    """,
                    UUID.randomUUID(),
                    chunk.ticketId(),
                    chunk.chunkIndex(),
                    chunk.content(),
                    chunk.status().name(),
                    chunk.priority().name(),
                    chunk.assignee(),
                    chunk.category(),
                    chunk.contentFingerprint(),
                    new PGvector(chunk.embedding()));
        }
    }

    @Override
    @Transactional
    public void deleteByTicketId(String ticketId) {
        jdbcTemplate.update("DELETE FROM ticket_chunk WHERE ticket_id = ?", ticketId);
    }

    @Override
    public List<RetrievedChunk> findNearest(float[] queryEmbedding, int limit) {
        String sql =
                """
                SELECT ticket_id, content, status, priority, assignee, category,
                       (embedding <=> ?) AS distance
                FROM ticket_chunk
                ORDER BY embedding <=> ?
                LIMIT ?
                """;
        PGvector vector = new PGvector(queryEmbedding);
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapRow(rs),
                vector,
                vector,
                limit);
    }

    private RetrievedChunk mapRow(ResultSet rs) throws SQLException {
        double distance = rs.getDouble("distance");
        double similarity = VectorMath.similarityFromDistance(distance);
        String content = rs.getString("content");
        String ticketId = rs.getString("ticket_id");
        String title = content.contains("\n") ? content.substring(0, content.indexOf('\n')) : content;
        if (title.startsWith("[Ticket ")) {
            int close = title.indexOf(']');
            if (close > 0 && close + 2 < title.length()) {
                title = title.substring(close + 2).trim();
            }
        }
        return new RetrievedChunk(
                ticketId,
                title,
                content,
                com.ttn.ticket.domain.TicketStatus.valueOf(rs.getString("status")),
                com.ttn.ticket.domain.TicketPriority.valueOf(rs.getString("priority")),
                rs.getString("assignee"),
                rs.getString("category"),
                distance,
                similarity);
    }
}