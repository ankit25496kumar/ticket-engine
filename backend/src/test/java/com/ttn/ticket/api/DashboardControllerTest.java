package com.ttn.ticket.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void summaryReturnsAllStatusesAndRecent() throws Exception {
        String create =
                """
                {"title":"Dash ticket","description":"d","priority":"MEDIUM","assignee":"a@t.com","category":"c"}
                """;
        mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/v1/tickets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(create))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTickets").value(1))
                .andExpect(jsonPath("$.byStatus.length()").value(5))
                .andExpect(jsonPath("$.recentTickets.length()").value(1))
                .andExpect(jsonPath("$.recentTickets[0].status").value("OPEN"));
    }
}