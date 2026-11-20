package com.ttn.config;

import com.ttn.rag.ingestion.TicketIngestionService;
import com.ttn.ticket.domain.Ticket;
import com.ttn.ticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Re-embeds tickets when the vector index is empty (e.g. seed ran before Ollama models were pulled).
 */
@Component
@Profile("dev")
@Order(1001)
public class DevRagReindexRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevRagReindexRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final TicketRepository ticketRepository;
    private final TicketIngestionService ticketIngestionService;
    private final String vectorStore;

    public DevRagReindexRunner(
            JdbcTemplate jdbcTemplate,
            TicketRepository ticketRepository,
            TicketIngestionService ticketIngestionService,
            @Value("${rag.vector-store:pgvector}") String vectorStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.ticketRepository = ticketRepository;
        this.ticketIngestionService = ticketIngestionService;
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        long ticketCount = ticketRepository.count();
        if (ticketCount == 0) {
            return;
        }
        if ("in-memory".equalsIgnoreCase(vectorStore)) {
            reingestAll(
                    ticketCount,
                    "Re-ingesting tickets into in-memory vector index (dev); ensure Ollama models are pulled");
            return;
        }
        if (!ticketChunkTableExists()) {
            log.warn(
                    "ticket_chunk table is missing; install pgvector and set rag.vector-store=pgvector with Flyway, "
                            + "or use dev profile default rag.vector-store=in-memory");
            return;
        }
        Long chunkCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_chunk", Long.class);
        if (chunkCount != null && chunkCount > 0) {
            return;
        }
        reingestAll(
                ticketCount,
                "ticket_chunk is empty but tickets exist; re-ingesting (ensure Ollama models are pulled)");
    }

    private void reingestAll(long ticketCount, String reason) {
        log.warn("{} ticket(s): {}", ticketCount, reason);
        for (Ticket ticket : ticketRepository.findAll()) {
            try {
                ticketIngestionService.ingest(ticket.getTicketId());
            } catch (Exception ex) {
                log.error("Re-ingest failed for ticketId={}", ticket.getTicketId(), ex);
            }
        }
    }

    private boolean ticketChunkTableExists() {
        Integer found =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name = 'ticket_chunk'
                        """,
                        Integer.class);
        return found != null && found > 0;
    }
}