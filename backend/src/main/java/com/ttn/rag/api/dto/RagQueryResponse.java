package com.ttn.rag.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record RagQueryResponse(
        String outcome,
        String query,
        Answer answer,
        java.util.List<Citation> citations,
        Confidence confidence,
        Escalation escalation,
        ErrorBody error,
        NoMatch noMatch) {

    public record Answer(String text) {}

    public record Citation(String ticketId, String title) {}

    public record Confidence(Double score, String basis) {}

    public record Escalation(boolean recommended, String reason) {}

    public record ErrorBody(String code, String message) {}

    public record NoMatch(String code, String message) {}

    public static RagQueryResponse answer(
            String query, String text, java.util.List<Citation> citations, double maxSimilarity) {
        return new RagQueryResponse(
                "ANSWER",
                query,
                new Answer(text),
                citations,
                new Confidence(maxSimilarity, "RETRIEVAL"),
                new Escalation(false, null),
                null,
                null);
    }

    public static RagQueryResponse noMatch(String query) {
        return new RagQueryResponse(
                "NO_MATCH",
                query,
                null,
                java.util.List.of(),
                null,
                new Escalation(true, "NO_RELEVANT_TICKETS"),
                null,
                new NoMatch(
                        "NO_RELEVANT_TICKETS", "No similar tickets were found in the knowledge base."));
    }

    public static RagQueryResponse error(String query, String code, String message) {
        return new RagQueryResponse(
                "ERROR",
                query,
                null,
                java.util.List.of(),
                null,
                null,
                new ErrorBody(code, message),
                null);
    }
}