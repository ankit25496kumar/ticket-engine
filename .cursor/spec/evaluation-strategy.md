# Evaluation Strategy (AI / RAG Quality)

**Status:** Evaluation design only. No implementation.  
**Depends on:** `spec/requirements.md`, `spec/rag-api-contract.md`, `spec/rag-ingestion.md`, `commands/review-rag-output.md`

**Source rule:** Metrics below verify **Specified** grounding behavior (FR-8–FR-10). Numeric quality targets are **Assumptions** unless later promoted to requirements.

---

## 1. Purpose

Measure whether the RAG pipeline:

1. Returns grounded answers supported by retrieved ticket context  
2. Cites correct ticket IDs  
3. Returns explicit `NO_MATCH` when retrieval is empty  
4. Avoids parametric hallucinations when context exists  

Requirements do not define SLAs or accuracy percentages (`requirements.md` §2). This document defines **how** to evaluate, not production NFRs.

---

## 2. Evaluation dimensions

| Dimension | What it measures | Requirement link |
|-----------|------------------|------------------|
| **Groundedness** | Answer claims supported by retrieved chunks | FR-8, RET-1 |
| **Citation correctness** | Cited `ticketId`s ⊆ retrieved keep-set | FR-9, RAG-API-D-5 |
| **No-match correctness** | Empty keep-set → `NO_MATCH`, not invented answer | FR-10, RET-4, VAL-2 |
| **Retrieval relevance** | Retrieved tickets relate to the query | AI-R-3 (risk) |
| **Conflict detection** | Answer contradicts retrieved context | AI-R-2, `review-rag-output.md` |
| **Staleness** | Post-update retrieval reflects new content | FR-11, T-5 |

---

## 3. Evaluation methods

### 3.1 Automated regression scenarios (required in CI)

| ID | Method | Example |
|----|--------|---------|
| **EVAL-D-1** | Fixture-based grounding review | Password-reset: context says 30 minutes; wrong answer says 24 hours → `FAIL` (`RagOutputReviewer`) |
| **EVAL-D-2** | Contract tests | `NO_MATCH` shape, `ANSWER` citations, HTTP 200 for no-match (`rag-api-contract.md` §10) |
| **EVAL-D-3** | Citation validator tests | Generator cites `TKT-9999` not in keep-set → reject or downgrade |
| **EVAL-D-4** | Retrieval config tests | Changing `top-k` / threshold changes keep-set (T-6) |

### 3.2 Golden dataset (recommended before production)

**EVAL-A-1:** Maintain a small curated set (10–30 cases) of:

* `(query, ticket fixtures, expected outcome)`  
* Expected: `ANSWER` with known citations, or `NO_MATCH`

| Field | Description |
|-------|-------------|
| `query` | User question |
| `tickets` | Seed data in DB + vector index |
| `expectedOutcome` | `ANSWER` \| `NO_MATCH` |
| `expectedTicketIds` | Citation set (if `ANSWER`) |
| `mustNotContain` | Phrases that indicate hallucination |

Run manually or in a scheduled job — **not required in default CI** if LLM is non-deterministic.

### 3.3 LLM-as-judge (optional, open)

**EVAL-Q-1:** Use a separate model to score entailment between answer and chunks. Not required by requirements; useful for exploratory eval.

### 3.4 Human review (optional)

Agent review of sampled production or staging queries. No process defined in requirements.

---

## 4. Metrics

### 4.1 Core binary metrics (track per eval run)

| Metric | Definition | Target (assumption) |
|--------|------------|---------------------|
| **Groundedness pass rate** | % of `ANSWER` cases where `RagOutputReviewer` passes | **100%** on golden set |
| **No-match precision** | % of empty-retrieval cases returning `NO_MATCH` | **100%** |
| **Citation validity rate** | % of `ANSWER` where citations ⊆ keep-set | **100%** |
| **Conflict detection rate** | % of injected wrong answers flagged | **100%** on regression fixtures |

**EVAL-A-2:** Targets above apply to **test/golden fixtures**, not live traffic, until product sets SLAs.

### 4.2 Retrieval diagnostics (informational)

| Metric | Use |
|--------|-----|
| Average keep-set size | Tune `top-k` / threshold |
| No-match rate | Corpus coverage |
| Similarity score distribution | Threshold calibration |

Log at debug/info per `rag-ingestion.md` ING-D-20.

### 4.3 Retrieval threshold calibration (2026-09)

Real-Ollama calibration was performed against production-shaped ticket chunks stored in pgvector (`OllamaRetrievalCalibrationEvaluation`, profile `ollama-calibration`, model `nomic-embed-text`). This is **informational evidence** for the canonical default in `rag-ingestion.md` §8.4; re-run when the golden dataset grows or the embedding model changes.

| Group | Observed similarity range (TKT-1001 target) |
|-------|-----------------------------------------------|
| **Relevant** (4 queries) | **0.726439** – **0.789242** |
| **Irrelevant** (4 queries) | **0.378966** – **0.483622** |

**Production evidence:** For ticket `TKT-1001` and query *"How long is a password reset link valid?"*, pgvector returned cosine distance `0.2551023656` → similarity **0.7448976344**. At threshold **0.75** this clearly relevant query was rejected (`kept = false`, `NO_MATCH` before generation).

**Decision:** Canonical `similarity-threshold` changed from **0.75** to **0.72** because:

* **0.72** accepts all four relevant calibration queries while excluding all four irrelevant calibration queries.
* **0.75** rejected the production password-reset query at similarity **0.7448976344**.

**Caveat:** Based on the current calibration dataset (8 queries, 4 tickets). Revisit threshold when the golden retrieval set expands or corpus/embedding strategy changes (`evaluation-strategy.md` §5).

---

## 5. When to evaluate

| Trigger | Evaluation |
|---------|------------|
| Every CI build | Regression fixtures (§3.1), contract tests |
| Before release | Golden dataset manual run |
| Config change (`top-k`, threshold, embedding model) | Re-run golden set + staleness test (T-5) |
| Prompt / generator change | Groundedness + conflict scenarios |

---

## 6. Failure handling in evaluation

| Finding | Action |
|---------|--------|
| Grounding conflict | Block merge; fix generator, gate, or validator |
| Citation outside keep-set | Block merge (RAG-API-D-12) |
| `NO_MATCH` missing when keep-set empty | Block merge (FR-10) |
| Retrieval staleness after update | Block merge (T-5) |

---

## 7. Out of scope

* End-user satisfaction / CSAT  
* Latency SLAs  
* Multi-language evaluation  
* AuthZ impact on retrieval  

---

## 8. Assumptions

| ID | Assumption |
|----|------------|
| EVAL-A-1 | Golden dataset is maintained alongside specs |
| EVAL-A-2 | 100% pass on fixtures is the bar for CI, not a product SLA |
| EVAL-A-3 | `RagOutputReviewer` covers claim-level checks for durations; broader entailment is future work |

---

## 9. Open questions

| ID | Question |
|----|----------|
| EVAL-Q-1 | Use LLM-as-judge in CI? |
| EVAL-Q-2 | Minimum golden-set size for release gate |
| EVAL-Q-3 | Production sampling rate for human review |
| EVAL-Q-4 | Acceptable no-match rate in production |

---

## 10. Traceability

| Requirement / test ID | Evaluation |
|----------------------|------------|
| FR-8, FR-9, FR-10 | §2, §3.1, §4.1 |
| T-3, T-4, T-7 | §3.1, golden dataset |
| T-5 | Staleness integration eval |
| T-6 | Retrieval config eval |
| AI-R-2, AI-R-3 | §2 conflict + relevance |
