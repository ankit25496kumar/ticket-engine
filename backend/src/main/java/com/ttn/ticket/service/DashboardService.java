package com.ttn.ticket.service;

import com.ttn.ticket.api.dto.AssigneeCount;
import com.ttn.ticket.api.dto.DashboardSummaryResponse;
import com.ttn.ticket.api.dto.StatusCount;
import com.ttn.ticket.api.dto.TicketResponse;
import com.ttn.ticket.domain.TicketStatus;
import com.ttn.ticket.repository.TicketAssigneeCountView;
import com.ttn.ticket.repository.TicketRepository;
import com.ttn.ticket.repository.TicketStatusCountView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final int RECENT_LIMIT = 10;

    private final TicketRepository ticketRepository;

    public DashboardService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        long total = ticketRepository.count();
        List<StatusCount> byStatus = buildStatusCounts();
        List<AssigneeCount> byAssignee = buildAssigneeCounts();
        List<TicketResponse> recent =
                ticketRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, RECENT_LIMIT)).stream()
                        .map(TicketResponse::from)
                        .toList();
        return new DashboardSummaryResponse(total, byStatus, byAssignee, recent);
    }

    private List<StatusCount> buildStatusCounts() {
        Map<TicketStatus, Long> counts = new EnumMap<>(TicketStatus.class);
        for (TicketStatus status : TicketStatus.values()) {
            counts.put(status, 0L);
        }
        for (TicketStatusCountView row : ticketRepository.countByStatus()) {
            counts.put(row.getStatus(), row.getCount());
        }
        List<StatusCount> result = new ArrayList<>();
        for (TicketStatus status : TicketStatus.values()) {
            result.add(new StatusCount(status, counts.get(status)));
        }
        return result;
    }

    private List<AssigneeCount> buildAssigneeCounts() {
        List<AssigneeCount> result = new ArrayList<>();
        for (TicketAssigneeCountView row : ticketRepository.countByAssignee()) {
            result.add(new AssigneeCount(row.getAssignee(), row.getCount()));
        }
        result.sort((a, b) -> Long.compare(b.count(), a.count()));
        return result;
    }
}