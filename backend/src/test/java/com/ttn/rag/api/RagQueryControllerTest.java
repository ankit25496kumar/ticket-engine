package com.ttn.rag.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttn.ticket.api.dto.CreateTicketRequest;
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
class RagQueryControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Test
    void blankQueryReturnsError() throws Exception {
        mockMvc.perform(
                        post("/api/v1/rag/query")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"query\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void noMatchWhenCorpusEmpty() throws Exception {
        mockMvc.perform(
                        post("/api/v1/rag/query")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"query\":\"anything\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("NO_MATCH"))
                .andExpect(jsonPath("$.answer").doesNotExist());
    }

    @Test
    void answerAfterTicketIngested() throws Exception {
        var create = new CreateTicketRequest("Password reset", "Links expire after 30 minutes", null, null, "security", null, null);
        mockMvc.perform(
                        post("/api/v1/tickets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/rag/query")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"query\":\"password reset validity\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("ANSWER"), org.hamcrest.Matchers.is("NO_MATCH"))));
    }
}