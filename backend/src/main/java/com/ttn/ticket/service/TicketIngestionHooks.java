package com.ttn.ticket.service;

public interface TicketIngestionHooks {

    void afterTicketPersisted(String ticketId);
}