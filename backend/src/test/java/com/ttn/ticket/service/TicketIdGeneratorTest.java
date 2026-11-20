package com.ttn.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class TicketIdGeneratorTest {

    @Mock private JdbcTemplate jdbcTemplate;

    @Test
    void nextTicketIdIncrementsSequenceAndFormatsId() {
        when(jdbcTemplate.queryForObject("SELECT next_value FROM ticket_id_sequence", Long.class))
                .thenReturn(1001L);
        TicketIdGenerator generator = new TicketIdGenerator(jdbcTemplate);

        String id = generator.nextTicketId();

        assertThat(id).isEqualTo("TKT-1001");
        verify(jdbcTemplate).update("UPDATE ticket_id_sequence SET next_value = ?", 1002L);
    }
}