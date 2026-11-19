package com.ttn.rag.fixture;

public final class PasswordResetKnowledgeFixture {

    public static final String RETRIEVED_CONTEXT =
            "Password reset links expire after 30 minutes.";

    public static final String USER_QUESTION = "How long is a password reset link valid?";

    public static final String HALLUCINATED_ANSWER =
            "The password reset link is valid for 24 hours.";

    public static final String CORRECT_ANSWER = "Password reset links expire after 30 minutes.";

    private PasswordResetKnowledgeFixture() {}
}