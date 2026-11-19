package com.ttn.ticket.repository;

import com.ttn.ticket.domain.Ticket;
import com.ttn.ticket.domain.TicketPriority;
import com.ttn.ticket.domain.TicketStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    Optional<Ticket> findByTicketId(String ticketId);

    default List<Ticket> findFiltered(
            TicketStatus status, TicketPriority priority, String assignee, String category) {
        return findAll((root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (assignee != null && !assignee.isBlank()) {
                predicates.add(cb.equal(root.get("assignee"), assignee));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        });
    }

    @Query("SELECT t.status AS status, COUNT(t) AS count FROM Ticket t GROUP BY t.status")
    List<TicketStatusCountView> countByStatus();

    @Query("SELECT t.assignee AS assignee, COUNT(t) AS count FROM Ticket t GROUP BY t.assignee")
    List<TicketAssigneeCountView> countByAssignee();

    List<Ticket> findAllByOrderByCreatedAtDesc(Pageable pageable);
}