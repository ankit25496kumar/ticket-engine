package com.ttn.ticket.config;

import com.ttn.ticket.domain.TicketStatusTransitionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TicketDomainConfig {

    @Bean
    public TicketStatusTransitionPolicy ticketStatusTransitionPolicy() {
        return new TicketStatusTransitionPolicy();
    }
}