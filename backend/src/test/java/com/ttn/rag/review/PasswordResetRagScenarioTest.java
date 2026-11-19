package com.ttn.rag.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.ttn.rag.fixture.PasswordResetKnowledgeFixture;
import org.junit.jupiter.api.Test;

class PasswordResetRagScenarioTest {

    private final RagOutputReviewer reviewer = new RagOutputReviewer();

    @Test
    void detectsHallucinatedPasswordResetExpiry() {
        var result =
                reviewer.review(
                        PasswordResetKnowledgeFixture.USER_QUESTION,
                        PasswordResetKnowledgeFixture.HALLUCINATED_ANSWER,
                        PasswordResetKnowledgeFixture.RETRIEVED_CONTEXT);
        assertThat(result.passed()).isFalse();
    }

    @Test
    void acceptsCorrectPasswordResetAnswer() {
        var result =
                reviewer.review(
                        PasswordResetKnowledgeFixture.USER_QUESTION,
                        PasswordResetKnowledgeFixture.CORRECT_ANSWER,
                        PasswordResetKnowledgeFixture.RETRIEVED_CONTEXT);
        assertThat(result.passed()).isTrue();
    }
}