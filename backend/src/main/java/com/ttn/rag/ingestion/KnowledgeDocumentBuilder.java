package com.ttn.rag.ingestion;

import com.ttn.ticket.domain.Comment;
import com.ttn.ticket.domain.Ticket;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeDocumentBuilder {

    public String build(Ticket ticket, List<Comment> comments) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ticket: ").append(ticket.getTicketId()).append('\n');
        sb.append("Title: ").append(nullToEmpty(ticket.getTitle())).append("\n\n");
        String description = trimToNull(ticket.getDescription());
        if (description != null) {
            sb.append("Description:\n").append(description).append("\n\n");
        }
        if (comments != null && !comments.isEmpty()) {
            sb.append("Comments:\n");
            for (Comment c : comments) {
                sb.append('[')
                        .append(c.getCreatedAt())
                        .append("] ")
                        .append(nullToEmpty(c.getAuthor()))
                        .append(": ")
                        .append(c.getContent())
                        .append('\n');
            }
            sb.append('\n');
        }
        String notes = trimToNull(ticket.getResolutionNotes());
        if (notes != null) {
            sb.append("Resolution notes:\n").append(notes).append('\n');
        }
        return sb.toString().trim();
    }

    public boolean hasUsableContent(Ticket ticket, List<Comment> comments) {
        if (trimToNull(ticket.getTitle()) != null) {
            return true;
        }
        if (trimToNull(ticket.getDescription()) != null) {
            return true;
        }
        if (trimToNull(ticket.getResolutionNotes()) != null) {
            return true;
        }
        if (comments != null) {
            for (Comment c : comments) {
                if (trimToNull(c.getContent()) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}