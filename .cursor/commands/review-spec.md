# Review Specification Command

## Objective

Review specification documents for consistency, completeness, and traceability to `spec/requirements.md` before implementation proceeds.

Do **not** modify specifications during the review unless explicitly asked.

---

## Review Inputs

Read all documents under `spec/`:

| Document | Purpose |
|----------|---------|
| `requirements.md` | Authoritative specified behavior |
| `architecture.md` | Component boundaries and flows |
| `data-model.md` | Entities, identifiers, metadata |
| `state-machine.md` | Transition matrix and enforcement |
| `api-contract.md` | Ticket/comment HTTP contract |
| `rag-api-contract.md` | RAG query/response contract |
| `rag-ingestion.md` | Ingestion, chunking, PGVector, retrieval config |
| `evaluation-strategy.md` | RAG quality evaluation |
| `test-strategy.md` | Test layers and obligations |
| `ui-flow.md` | User-facing flows |

---

## Review Process

### 1. Requirements traceability

For each **Specified** requirement (FR-*, SM-*, RET-*, ING-*, API-*, T-*):

* Is it covered by at least one downstream spec?
* Is coverage explicit (section reference or ID), not implied?

Flag **missing coverage**.

### 2. Contradiction check

Compare specs pairwise:

| Check | Example conflict |
|-------|------------------|
| Data model vs API | Field exposed that model says is internal |
| State machine vs API | Transition allowed in API but rejected in SM |
| RAG ingestion vs data model | Different metadata fields |
| RAG API vs requirements | `NO_MATCH` not explicit |
| Architecture vs ingestion | Async vs sync refresh mismatch |

Flag every **contradiction** with both file references.

### 3. Invented requirements

Flag any downstream spec that:

* Adds product behavior not in `requirements.md` without labeling it **Decision** or **Assumption**
* States SLAs, auth, or roles as requirements when they are open questions
* Hardcodes values requirements left open without `*-D-*` decision ID

### 4. Decision hygiene

Verify design decisions are labeled:

* `API-D-*`, `SM-D-*`, `ING-D-*`, `RAG-API-D-*`, `DM-D-*`, etc.
* Assumptions: `*-A-*`
* Open questions: `*-Q-*`

Flag decisions presented as requirements.

### 5. Implementation readiness

| Area | Ready? |
|------|--------|
| Ticket CRUD + status | api-contract + state-machine + data-model |
| RAG query response | rag-api-contract |
| Ingestion pipeline | rag-ingestion |
| Retrieval config | rag-ingestion §8 |
| Tests | test-strategy + state-machine §5 |
| UI | ui-flow (logical only) |

List **blockers** — open questions that prevent a specific implementation step.

### 6. Cross-reference integrity

* Citations use `Ticket.ticketId` consistently
* RAG endpoint path consistent (`POST /api/v1/rag/query`)
* Enum values identical across data-model, API, and state-machine
* Embedding refresh triggers aligned between architecture and rag-ingestion

---

## Review Output

### Summary

* Overall status: **PASS** / **PASS WITH ISSUES** / **FAIL**
* Missing traceability: `<count>`
* Contradictions: `<count>`
* Invented requirements: `<count>`
* Open blockers: `<count>`

### Findings

```text
[SEVERITY]

Spec:
spec/...

Section:
...

Problem:
...

Why it matters:
...

Recommendation:
...
```

Severities: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`

### Traceability gaps

List requirement IDs with no spec coverage.

### Contradictions

List each with both sides cited.

### Open blockers for implementation

List decisions still needed before coding each major feature.

### Final recommendation

* **APPROVE** — specs consistent; implementation may proceed
* **APPROVE AFTER FIXES** — minor gaps; fix before coding affected area
* **REJECT** — contradictions or missing contracts block implementation

---

## Rules

* Do not invent requirements to fill gaps — record them as open questions.
* Prefer existing decision IDs over new terminology.
* Empty or stub specs are automatic **FAIL** for that area.
