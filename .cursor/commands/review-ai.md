# AI Review Command

## Objective

Review the AI implementation of the Support Ticket AI system against the approved requirements, specifications, architecture, API contracts, RAG design, and project rules.

The review must identify correctness, reliability, safety, maintainability, and RAG-quality issues.

## Review Inputs

Review the following sources before reviewing the implementation:

1. `spec/requirements.md`
2. `spec/architecture.md`
3. `spec/rag-ingestion.md`
4. `spec/rag-api-contract.md`
5. `spec/evaluation-strategy.md`
6. `spec/test-strategy.md`
7. `rules/java-springboot.md`
8. `rules/api-standards.md`
9. `rules/rag-vector-store.md`

Also review the relevant implementation and tests under:

* `backend/src/main`
* `backend/src/test`

## Review Process

Follow this order:

### 1. Requirements Compliance

Verify that the AI implementation satisfies all approved requirements.

Identify:

* Missing functionality
* Incorrect functionality
* Unimplemented requirements
* Behavior that differs from the specification

Do not assume missing requirements.

### 2. Architecture Compliance

Verify that the implementation follows the approved architecture.

Check:

* Separation of controller, service, repository, and AI/RAG components
* Dependency boundaries
* Responsibility of each component
* Configuration management
* Error handling

### 3. AI Response Contract

Verify that the AI response conforms to:

`spec/rag-api-contract.md`

Check:

* Required fields
* Optional fields
* Data types
* Validation
* Error responses
* HTTP status codes
* Confidence handling
* Escalation behavior
* Source/citation information

The LLM must not be allowed to arbitrarily change the public API contract.

### 4. RAG Review

Review the complete RAG flow:

User Query
→ Query Processing
→ Embedding
→ Vector Search
→ Retrieval
→ Context Construction
→ Prompt
→ LLM
→ Structured Response

Check:

* Chunking strategy
* Metadata preservation
* Embedding configuration
* Retrieval parameters
* Top-K configuration
* Similarity threshold
* Filtering
* Context size
* Prompt construction
* Source attribution
* Handling of insufficient context
* Hallucination risk

Verify that retrieval parameters are configurable rather than hard-coded where the specification requires configuration.

### 5. Groundedness

Determine whether the generated answer is supported by the retrieved context.

Identify:

* Unsupported claims
* Hallucinations
* Information not present in retrieved documents
* Incorrect source attribution
* Answers generated when relevant context is unavailable

The system should prefer saying that sufficient information is unavailable over inventing an answer.

#### 5.1 Answer–Context–Citation Conflict Review (required)

For every AI answer under review, explicitly compare:

| Source | What to inspect |
|--------|-----------------|
| **Retrieved context** | Full text of kept chunks passed to the generator |
| **Generated answer** | Final `answer.text` (or equivalent) returned to the client |
| **Citations** | `citations[].ticketId` and whether cited ticket content supports the answer |

**Mandatory checks:**

1. **Factual alignment:** Every concrete factual claim in the answer (numbers, durations, statuses, policies, names, dates) must be supported by the retrieved context for the cited ticket(s).
2. **No contradictions:** If the answer states a fact that conflicts with retrieved context (e.g. context says "30 minutes" but answer says "24 hours"), flag as **CONTEXT_CONFLICT** and **POTENTIAL_HALLUCINATION**.
3. **No unsupported claims:** If the answer introduces facts absent from retrieved context, flag as **UNSUPPORTED_CLAIM**.
4. **Citation support:** Citations must not only be present — the cited ticket's retrieved chunks must actually support the claims made about that ticket.
5. **Expected answer basis:** The correct answer must be derivable from retrieved context alone (FR-8). If the answer would require general model knowledge instead of the chunks, it is ungrounded.

**Programmatic check (when available):** Run `RagAnswerGroundingReviewer.review(answerText, retrievedContext)` and treat `grounded == false` as a review failure. For structured output review (user question + expected vs generated claims), use `RagOutputReviewer.review(...)` per `commands/review-rag-output.md`.

**Regression scenario:** Password-reset expiry — document: `"Password Reset Policy"`; context: `"Password reset links expire after 30 minutes."`; user question: `"How long is a password reset link valid?"`; incorrect answer: `"The password reset link is valid for 24 hours."` must be detected as ungrounded with `Expected: 30 minutes`, `Generated: 24 hours`, `Result: FAIL`.

### 6. AI Response Reliability

Check:

* Invalid LLM responses
* Malformed structured output
* Missing fields
* Unexpected model output
* Timeout handling
* Retry behavior
* AI provider failures
* Vector-store failures
* Empty retrieval results

Verify that failures are handled deterministically.

### 7. Security

Check for:

* Prompt injection risks
* Sensitive information exposure
* Secrets in prompts or source documents
* Unsafe user-controlled prompt construction
* Improper access to knowledge-base documents
* Logging of sensitive information

Do not expose API keys, credentials, or sensitive data in logs or responses.

### 8. Performance

Review:

* Number of embedding calls
* Number of vector-store queries
* Retrieval size
* Context size
* Unnecessary LLM calls
* Database queries
* Blocking operations
* Timeout configuration

Identify unnecessary expensive operations.

### 9. Testing

Verify that tests cover:

* Successful AI response
* No relevant context
* Low-confidence response
* Escalation
* Invalid LLM output
* AI provider failure
* Vector-store failure
* API contract validation
* RAG retrieval behavior
* Source attribution
* **Answer–context conflict / hallucination detection** (see §5.1)

Check whether tests validate behavior rather than only implementation details.

### 10. Observability

Check whether important AI/RAG operations can be diagnosed through appropriate logging and metrics.

Verify that logs provide useful information without exposing sensitive information.

## Review Output

Return the review in this format:

### Summary

* Overall status: PASS / PASS WITH ISSUES / FAIL
* Critical issues: `<number>`
* High issues: `<number>`
* Medium issues: `<number>`
* Low issues: `<number>`

### Findings

For every issue provide:

* Severity
* File
* Class/method or relevant location
* Problem
* Why it matters
* Expected behavior
* Recommended fix

Use this format:

```text
[HIGH]

File:
backend/src/main/...

Location:
ClassName.methodName()

Problem:
...

Why it matters:
...

Expected:
...

Recommended fix:
...
```

### Specification Violations

List every violation of:

* Requirements
* Architecture
* API contract
* RAG specification
* Project rules

### RAG Quality Assessment

Evaluate:

* Retrieval quality
* Context relevance
* Groundedness
* Citation correctness
* No-context handling
* Hallucination risk

### Test Gaps

List missing or insufficient tests.

### Final Recommendation

Conclude with one of:

* APPROVE
* APPROVE AFTER FIXES
* REJECT

Do not modify the code during the review.

Do not invent requirements.

Prioritize correctness, groundedness, security, and contract compliance over stylistic preferences.
