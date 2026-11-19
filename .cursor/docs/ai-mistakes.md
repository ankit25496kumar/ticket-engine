# AI Mistakes — Verified Development History

This document records **actual** mistakes made during AI-assisted development of the Support Ticket AI project. Each entry is traceable to the agent transcript (`29b1ff2f-53f0-4e3b-945b-a06651d4bf4d`) and/or `docs/prompt-history.md`. Nothing here is fabricated.

**Current default test baseline:** backend **212 tests, 0 failures**; frontend **30 tests, 0 failures**. `OllamaRetrievalCalibrationEvaluation` is environment-gated (`RUN_OLLAMA_CALIBRATION=true`) and is not part of the default backend suite.

**Intentional test inputs are excluded.** The password-reset “24 hours vs 30 minutes” answer in `PasswordResetKnowledgeFixture.HALLUCINATED_ANSWER` was deliberately introduced to prove the review process works (prompt-history entry **010**). It is not listed below as an accidental mistake.

---

## Mistake 1 — Flawed irrelevant-document retrieval evaluation test

**Date:** 2026-09-08 (Phase 9 — Evaluation)  
**Category:** Incorrect RAG / retrieval test design  
**Prompt-history reference:** Entry **019**

### 1. Original AI output

During Phase 9, the assistant added `RetrievalEvaluationTest.rejectsIrrelevantDocumentsBelowThreshold()` with this structure:

```java
@Test
void rejectsIrrelevantDocumentsBelowThreshold() {
    seedChunk(
            "TKT-7002",
            "CSV export corrupt on Windows",
            "Enable UTF-8 BOM in exporter v2.1.",
            IRRELEVANT_QUERY);

    List<RetrievedChunk> kept =
            ragRetrievalService.retrieve(embeddingClient.embed(IRRELEVANT_QUERY));

    assertThat(kept).isEmpty();
}
```

The shared helper `seedChunk(..., queryText)` stores an embedding derived from `queryText`, not from the chunk body:

```119:121:backend/src/test/java/com/example/eval/RetrievalEvaluationTest.java
    private void seedChunk(String ticketId, String title, String content, String queryText) {
        seedChunkWithEmbedding(ticketId, title, content, embeddingClient.embed(queryText));
    }
```

`TestEmbeddingClient` (test profile) produces deterministic vectors from UTF-8 bytes cycling through the input string — it does **not** model semantic similarity between unrelated texts:

```22:29:backend/src/main/java/com/example/rag/ingestion/TestEmbeddingClient.java
    public float[] embed(String text) {
        float[] embedding = new float[embeddingProperties.getDimension()];
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index < embedding.length; index++) {
            embedding[index] = bytes[index % bytes.length] / 255.0f;
        }
        return embedding;
    }
```

### 2. Why it was wrong

The test claimed to verify **irrelevant document rejection** (chunks below the similarity threshold should not be kept). In practice:

- Passing `IRRELEVANT_QUERY` into `seedChunk` stored a vector built from that same query string.
- Retrieval then searched with the identical query embedding, yielding a **self-match** (similarity ≈ 1.0), well above the configured threshold (0.72).
- Even when different query strings were involved, `TestEmbeddingClient` does not guarantee low cosine similarity between semantically unrelated texts, so the test could not reliably exercise threshold filtering.

The test therefore did not validate the behavior required by the evaluation plan — it could pass or fail for reasons unrelated to retrieval quality.

### 3. How it was detected

`mvn test` was run as part of Phase 9 final verification. The new `RetrievalEvaluationTest` failed on `rejectsIrrelevantDocumentsBelowThreshold`. The assistant noted: *“`TestEmbeddingClient` produces similar embeddings for different queries”* and applied a fix before reporting Phase 9 complete (transcript + prompt-history entry **019**).

### 4. What specification it violated

| Spec | Requirement |
|------|-------------|
| `spec/evaluation-strategy.md` §2, §4.2 | Retrieval evaluation must cover irrelevant-document rejection and no-match behavior |
| `spec/requirements.md` **RET-4** | Results below the similarity threshold must be excluded; empty keep-set drives explicit no-match |
| `spec/rag-ingestion.md` | Default `similarity-threshold: 0.72` must meaningfully filter weak neighbors |

### 5. Correction made

The test was corrected to inject a vector **guaranteed** to be dissimilar from the query embedding, bypassing the flawed `seedChunk` helper for this case:

```64:76:backend/src/test/java/com/example/eval/RetrievalEvaluationTest.java
    void rejectsIrrelevantDocumentsBelowThreshold() {
        float[] queryEmbedding = embeddingClient.embed(IRRELEVANT_QUERY);
        float[] orthogonalEmbedding = orthogonalTo(queryEmbedding);
        seedChunkWithEmbedding(
                "TKT-7002",
                "CSV export corrupt on Windows",
                "Enable UTF-8 BOM in exporter v2.1.",
                orthogonalEmbedding);

        List<RetrievedChunk> kept = ragRetrievalService.retrieve(queryEmbedding);

        assertThat(kept).isEmpty();
    }
```

A private `orthogonalTo()` helper and `seedChunkWithEmbedding()` were added. **No production code was changed.**

### 6. Test added

The failing test was fixed in place (`RetrievalEvaluationTest`). The broader Phase 9 suite (27 new backend tests including `GroundingEvaluationTest`, `HallucinationEvaluationTest`, `GoldenDatasetEvaluationTest`) was added in the same phase. Final run: **208 backend tests, 0 failures**.

### 7. Lesson learned

Evaluation tests must isolate the property under test. When a helper embeds the query string instead of document content, and the embedding client is a deterministic hash rather than a semantic model, “irrelevant rejection” tests need explicit control of vectors — not assumptions that unrelated strings produce low similarity.

---

## Mistake 2 — Empty `commands/review-ai.md` blocked hallucination detection

**Date:** 2026-09-08 (before prompt-history entry **010**)  
**Category:** Unsupported assumption / incomplete process artifact  
**Prompt-history reference:** Entry **010** (correction); entry **009** (intended creation)

### 1. Original AI output

When the user requested a controlled hallucination scenario (password-reset 30 minutes vs incorrect 24-hour answer), the assistant attempted to run the project’s AI review process. The assistant reported:

> **`commands/review-ai.md` was empty and could not detect this.**

The repository was expected to have a repeatable review command (prompt-history entry **009** had been tasked with creating it), but the file had no usable review workflow at the time the hallucination test was introduced.

### 2. Why it was wrong

The project’s evaluation strategy (`spec/evaluation-strategy.md` **EVAL-D-1**) and test strategy depend on fixture-based grounding review (`RagOutputReviewer`, `commands/review-ai.md`). An empty command file meant:

- The mandated review step could not be executed.
- Context–answer conflicts (hallucinations) could not be caught through the documented process.
- The assistant implicitly assumed review tooling was ready when it was not.

### 3. How it was detected

Detection occurred during the user’s explicit request to *“intentionally introduce and detect an AI mistake”* (transcript, entry **010**). The assistant read `commands/review-ai.md`, found it empty, and reported that the review process could not flag the intentional 24-hour hallucination.

### 4. What specification it violated

| Spec / artifact | Requirement |
|-----------------|-------------|
| `spec/evaluation-strategy.md` **EVAL-D-1** | Fixture-based grounding review must detect wrong answers (e.g., 30 min vs 24 h) |
| `commands/review-ai.md` (project convention) | Repeatable AI/RAG implementation review against specs and rules |
| `spec/test-strategy.md` | Grounding and hallucination scenarios must be reviewable in CI |

### 5. Correction made

`commands/review-ai.md` was populated with a full review workflow, including **§5.1 Answer–Context–Citation Conflict Review** (flags `CONTEXT_CONFLICT`, `UNSUPPORTED_CLAIM`, etc.). `commands/review-rag-output.md` was aligned. Supporting Java reviewers were wired:

- `RagOutputReviewer`
- `RagAnswerGroundingReviewer`

### 6. Test added

Yes — regression coverage for the password-reset scenario:

- `backend/src/test/java/com/example/rag/fixture/PasswordResetKnowledgeFixture.java`
- `backend/src/test/java/com/example/rag/review/PasswordResetRagScenarioTest.java`
- `backend/src/test/java/com/example/rag/api/RagQueryGroundingRegressionTest.java`

Phase 9 later extended this with `HallucinationEvaluationTest` and golden-dataset cases.

### 7. Lesson learned

Do not assume process artifacts are complete because a prior prompt requested them. Verify command files are non-empty and executable before claiming the review pipeline can detect mistakes.

---

## Mistake 3 — Initial data model draft omitted required entities and fields

**Date:** 2026-09-07 (SDD — Data model)  
**Category:** Unsupported assumption / incomplete specification draft  
**Prompt-history reference:** Entry **003**

### 1. Original AI output

The first draft of `spec/data-model.md` did not include several fields the user later supplied explicitly:

- `priority` on tickets
- `Comment` entity (and its relationship to tickets)
- `resolutionNotes` on tickets

The user had to provide a field inventory and clarifying questions before the model was complete (prompt-history entry **003**, Q&A block).

### 2. Why it was wrong

A support-ticket data model without comments, priority, or resolution notes is incomplete relative to the product intent reflected in `spec/requirements.md` (ticket lifecycle, comments, prioritization). Proceeding from an incomplete draft risked downstream API and RAG metadata gaps.

### 3. How it was detected

The **user** detected the omission by posting the full field list and follow-up questions (`Should priority be an enum?`, `How should comments relate to tickets?`, etc.). This was iterative SDD review, not an automated test failure.

### 4. What specification it violated

| Spec | Requirement |
|------|-------------|
| `spec/requirements.md` | Ticket management includes comments, status/priority, and resolution context (FR-2–FR-6 family) |
| SDD process (`spec/data-model.md` purpose) | Entity model must cover all persisted ticket and comment fields before implementation |

### 5. Correction made

User-provided field inventory and Q&A updated `spec/data-model.md` with:

- `TicketStatus` and `TicketPriority` enums
- `Comment` entity with FK to ticket
- `resolutionNotes`, `assignee`, `category`, and RAG metadata fields

### 6. Test added

No test at spec time. Implementation tests later enforce the corrected model (e.g., `TicketIntegrationTest`, comment API tests, RAG metadata in `RetrievalEvaluationTest.metadataPreservedInStoredChunks`).

### 7. Lesson learned

Before drafting a data model, elicit a complete field inventory (or read `requirements.md` field-by-field). Do not infer minimal CRUD fields when the domain clearly includes comments, priority, and resolution workflow.

---

## Related gaps (documented, not corrected as mistakes above)

These are known deviations or limitations identified in audits; they were **not** caught and fixed as discrete mistakes during development in the same way as the entries above:

| Topic | Status | Notes |
|-------|--------|-------|
| `SectionChunker` uses `max-characters`, not `max-tokens` | Open limitation | `ING-D-6` prefers token windows; README §14 documents the gap |
| `RagAnswerGroundingReviewer` checks duration claims only | Partial implementation | Deep RAG audit item 13 — broader factual grounding not enforced |
| Frontend list filters | Partial UI | API supports `priority` / `assignee` / `category`; UI wires `status` and client-side keyword only |
| `.specstory/history/` described as absent in first `prompt-history.md` draft | Documentation error, later fixed | Directory exists but is empty; authoritative history remains `docs/prompt-history.md` |

---

## Sources

| Source | Role |
|--------|------|
| Agent transcript `29b1ff2f-53f0-4e3b-945b-a06651d4bf4d` | Primary evidence for Phase 9 test failure, empty `review-ai.md`, hallucination workflow |
| `docs/prompt-history.md` entries **003**, **009**, **010**, **019** | Structured cross-reference for each mistake |
| `backend/src/test/java/com/example/eval/RetrievalEvaluationTest.java` | Corrected test (Mistake 1) |
| `commands/review-ai.md` | Populated review command (Mistake 2) |
| `spec/data-model.md` | Corrected data model (Mistake 3) |
