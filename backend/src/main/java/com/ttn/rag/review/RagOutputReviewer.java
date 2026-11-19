package com.ttn.rag.review;

import org.springframework.stereotype.Component;

@Component
public class RagOutputReviewer {

    private final RagAnswerGroundingReviewer groundingReviewer;

    public RagOutputReviewer() {
        this.groundingReviewer = new RagAnswerGroundingReviewer();
    }

    public ReviewResult review(String userQuestion, String generatedAnswer, String retrievedContext) {
        GroundingReviewResult grounded = groundingReviewer.review(generatedAnswer, retrievedContext);
        if (!grounded.grounded()) {
            return new ReviewResult(false, grounded.reason());
        }
        return new ReviewResult(true, null);
    }

    public record ReviewResult(boolean passed, String reason) {}
}