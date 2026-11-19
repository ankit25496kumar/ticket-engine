package com.ttn.ticket.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketIdGenerator {

    private final JdbcTemplate jdbcTemplate;

    public TicketIdGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public String nextTicketId() {
        Long next = jdbcTemplate.queryForObject("SELECT next_value FROM ticket_id_sequence", Long.class);
        jdbcTemplate.update("UPDATE ticket_id_sequence SET next_value = ?", next + 1);
        return "TKT-" + next;
    }
}