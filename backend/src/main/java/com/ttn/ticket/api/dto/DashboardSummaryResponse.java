package com.ttn.ticket.api.dto;

import java.util.List;

public record DashboardSummaryResponse(
        long totalTickets,
        List<StatusCount> byStatus,
        List<AssigneeCount> byAssignee,
        List<TicketResponse> recentTickets) {}