package com.ttn.ticket.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttn.ticket.api.dto.CreateTicketRequest;
import com.ttn.ticket.domain.TicketPriority;
import com.ttn.ticket.domain.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Test
    void createTicketReturnsOpenStatus() throws Exception {
        var request = new CreateTicketRequest("Test title", "Body", TicketPriority.HIGH, "a@test.com", "billing", null, null);
        mockMvc.perform(
                        post("/api/v1/tickets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.ticketId").value(org.hamcrest.Matchers.startsWith("TKT-")));
    }

    @Test
    void rejectsOpenToClosed() throws Exception {
        var create = new CreateTicketRequest("T", "D", null, null, null, null, null);
        String body =
                mockMvc.perform(
                                post("/api/v1/tickets")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(create)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String ticketId = objectMapper.readTree(body).get("ticketId").asText();
        String statusBody = objectMapper.writeValueAsString(java.util.Map.of("status", TicketStatus.CLOSED));
        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(statusBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));
    }
}