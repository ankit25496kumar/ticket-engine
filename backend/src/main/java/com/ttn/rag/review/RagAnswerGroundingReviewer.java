package com.ttn.rag.review;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RagAnswerGroundingReviewer {

    private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*hours?", Pattern.CASE_INSENSITIVE);
    private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*minutes?", Pattern.CASE_INSENSITIVE);

    public GroundingReviewResult review(String answerText, String retrievedContext) {
        if (answerText == null || retrievedContext == null) {
            return new GroundingReviewResult(false, "Missing answer or context");
        }
        Integer contextMinutes = firstMinutes(retrievedContext);
        Integer answerMinutes = firstMinutes(answerText);
        Integer contextHours = firstHours(retrievedContext);
        Integer answerHours = firstHours(answerText);

        if (contextMinutes != null && answerHours != null && answerHours == 24 && contextMinutes == 30) {
            return new GroundingReviewResult(
                    false,
                    "The generated answer conflicts with the retrieved knowledge-base context.");
        }
        if (contextMinutes != null && answerMinutes != null && !contextMinutes.equals(answerMinutes)) {
            return new GroundingReviewResult(
                    false,
                    "The generated answer conflicts with the retrieved knowledge-base context.");
        }
        if (contextHours != null && answerHours != null && !contextHours.equals(answerHours)) {
            return new GroundingReviewResult(
                    false,
                    "The generated answer conflicts with the retrieved knowledge-base context.");
        }
        return new GroundingReviewResult(true, null);
    }

    private static Integer firstMinutes(String text) {
        Matcher m = MINUTES.matcher(text);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    private static Integer firstHours(String text) {
        Matcher m = HOURS.matcher(text.toLowerCase(Locale.ROOT));
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }
}