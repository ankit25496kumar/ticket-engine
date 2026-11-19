package com.ttn.rag.config;

import com.ttn.rag.ingestion.PgVectorTicketVectorStore;
import com.ttn.rag.ingestion.TicketVectorStore;
import com.ttn.rag.retrieval.VectorSearchPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class RagVectorStoreConfig {

    @Bean
    @ConditionalOnBean(PgVectorTicketVectorStore.class)
    @Primary
    public VectorSearchPort vectorSearchPort(PgVectorTicketVectorStore store) {
        return store;
    }

    @Bean
    @ConditionalOnBean(PgVectorTicketVectorStore.class)
    public TicketVectorStore ticketVectorStore(PgVectorTicketVectorStore store) {
        return store;
    }
}