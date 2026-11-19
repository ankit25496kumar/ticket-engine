# Requirements Analysis: AI-Powered Support Ticket Management System

**Status:** Analysis only. No implementation.

**Sources used (and only these):**

| Source | What it actually contains |
|--------|---------------------------|
| This analysis request | Product constraints for ticket lifecycle, RAG grounding, citations, no-match behavior, embedding refresh, configurable retrieval knobs |
| `backend/pom.xml` | Intended backend stack: Spring Boot 3.5.6, Java 17, `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, PostgreSQL, `spring-boot-starter-test` |
| Repository layout | Empty placeholders under `spec/`, `rules/`, `commands/`, `docs/`; empty `README.md`; empty `frontend/`; starter `backend/src/main/java/com/example/Main.java` (not product logic) |

**Source rule:** Nothing in `spec/*.md` (except this file), `rules/*.md`, `commands/*.md`, `docs/prompt-history.md`, or `README.md` is a requirement today — those files exist but are empty. Requirements below are either **Specified**, **Observed (repo artifact)**, **Assumption**, or **Open question**. Unspecified items are not invented.

---

## 1. Functional requirements

### Specified

| ID | Requirement |
|----|-------------|
| FR-1 | The system is an AI-powered support ticket management system. |
| FR-2 | Tickets have a lifecycle that includes at least the states `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, and `CANCELLED`. |
| FR-3 | The following transitions are allowed: `OPEN` → `IN_PROGRESS` → `RESOLVED` → `CLOSED`. |
| FR-4 | `OPEN` → `CANCELLED` is allowed. |
| FR-5 | `IN_PROGRESS` → `CANCELLED` is allowed. |
| FR-6 | Invalid state transitions must be rejected by the backend (server-side enforcement; client-only checks are not sufficient). |
| FR-7 | The system supports RAG (retrieval-augmented generation) over ticket context. |
| FR-8 | Generated answers must use only retrieved ticket context (no answering from general model knowledge in place of retrieved tickets). |
| FR-9 | Answers must cite ticket IDs. |
| FR-10 | If no relevant tickets are retrieved, the system must return an explicit no-match response (not a fabricated answer). |
| FR-11 | Embeddings must be refreshed when ticket data changes. |
| FR-12 | Retrieval `top-K` must be configurable. |
| FR-13 | Retrieval similarity threshold must be configurable. |

### Observed (not product behavior)

| ID | Observation |
|----|-------------|
| FR-OBS-1 | A backend module exists and is described as “AI-powered Support Ticket Management System.” |
| FR-OBS-2 | A `frontend/` directory exists but contains no UI requirements or code. |
| FR-OBS-3 | Placeholder spec filenames imply planned coverage of architecture, data model, API contract, RAG ingestion, RAG API, UI flow, test strategy, and evaluation — none of those documents are written yet. |

### Open questions (functional)

- What ticket operations exist besides status change (create, read, update fields, list, search, delete)?
- Who uses the system (agents, customers, admins) and what each role can do?
- Is RAG query a distinct user-facing feature (e.g. “ask about similar tickets”) or an assist on an existing ticket?
- What is the exact no-match payload or user-visible message?
- Must cancelled tickets remain in the RAG corpus?
- Are `RESOLVED` and `CLOSED` distinct user-visible outcomes, or only workflow states?

---

## 2. Non-functional requirements

### Specified

None. The request does not state latency, throughput, availability, security, privacy, audit, accessibility, or localization targets.

### Observed (technology starting point, not SLAs)

| ID | Observation |
|----|-------------|
| NFR-OBS-1 | Backend is Java 17. |
| NFR-OBS-2 | Backend uses Spring Boot 3.5.6 with Web, Bean Validation, Spring Data JPA. |
| NFR-OBS-3 | PostgreSQL is the declared runtime database driver. |
| NFR-OBS-4 | Tests are expected at least at the Spring Boot Test starter level (`spring-boot-starter-test`). |

### Assumptions

| ID | Assumption | Why it is an assumption |
|----|------------|-------------------------|
| NFR-A-1 | PostgreSQL is the system of record for tickets. | Driver is on the classpath; persistence mapping and schema are unspecified. |
| NFR-A-2 | HTTP/JSON APIs are the intended backend interface. | `spring-boot-starter-web` is present; protocol and resource design are unspecified. |
| NFR-A-3 | Request validation is required at the API boundary. | `spring-boot-starter-validation` is present; which fields and rules are unspecified. |

### Open questions (NFR)

- Performance: p95 latency for ticket APIs and RAG answers?
- Scale: expected ticket volume, concurrent users, embedding corpus size?
- Availability / RPO / RTO?
- Authentication, authorization, tenancy, PII handling?
- Embedding model, vector store, and LLM provider constraints (on-prem vs third-party, data residency)?
- Configurability mechanism for `top-K` and similarity threshold (env, config file, admin API, per-request)?
- Frontend stack, browsers, and deployment topology?

---

## 3. Domain entities

### Specified (minimally)

| Entity | What is specified | What is not specified |
|--------|-------------------|------------------------|
| **Ticket** | Has an identifier that can be cited; has a status that participates in the state machine; has “ticket data” that can change and must drive embedding refresh; can be retrieved as RAG context | Title, body, priority, assignee, comments, tags, timestamps, customer, category, attachments |
| **Ticket status** | Values include `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED` | Whether status is an enum in persistence, display labels, or additional states |
| **Retrieved ticket context** | The only allowed grounding for answers | Chunking unit, which ticket fields are indexed, metadata filters |
| **Answer** | Must cite ticket IDs; must be a no-match response when nothing relevant is retrieved | Schema of citations, confidence, multiple answers |
| **Embedding** | Must stay in sync when ticket data changes | Vector identity, dimension, model, storage location |

### Open questions (entities)

- User / Agent / Customer / Organization?
- Ticket comment / activity / audit log?
- Knowledge articles distinct from tickets, or tickets only?
- Query / RAG session history?
- Embedding / chunk records as first-class entities?

No additional entities are asserted.

---

## 4. API requirements

### Specified

| ID | Requirement |
|----|-------------|
| API-1 | Backend must reject invalid ticket status transitions (not only UI). |
| API-2 | Some interface must accept a question/query and return either a grounded answer with ticket ID citations or an explicit no-match. |
| API-3 | `top-K` and similarity threshold must be configurable (not necessarily per-request; mechanism unspecified). |

### Observed

- Spring Web is on the classpath → HTTP APIs are a plausible delivery mechanism, not a contracted resource model.
- `spec/api-contract.md` and `spec/rag-api-contract.md` exist and are empty.

### Not specified (do not invent)

- Paths, HTTP methods, status codes, pagination, filtering, idempotency keys
- Auth headers, error body schema
- Whether RAG is `POST /rag/query` or another shape
- Whether transition is `PATCH /tickets/{id}` with `{ "status": "..." }` or a dedicated action endpoint
- Versioning, OpenAPI, rate limits

### Open questions

- Exact REST (or other) contract for CRUD vs status transition vs RAG
- Error codes for invalid transition vs validation vs not found vs RAG no-match
- Whether no-match is HTTP 200 with a typed body or another status
- Sync vs async embedding refresh API (if any)

---

## 5. Ticket state-machine requirements

### Specified allowed transitions

```
OPEN         → IN_PROGRESS
IN_PROGRESS  → RESOLVED
RESOLVED     → CLOSED
OPEN         → CANCELLED
IN_PROGRESS  → CANCELLED
```

Happy path: `OPEN` → `IN_PROGRESS` → `RESOLVED` → `CLOSED`.

Cancellation: from `OPEN` or `IN_PROGRESS` only (as specified).

### Specified enforcement

| ID | Requirement |
|----|-------------|
| SM-1 | Invalid transitions must be rejected by the backend. |

### Implied by the specified graph (not extra product features)

Any transition not listed above is invalid, including (non-exhaustive):

- Skipping steps: `OPEN` → `RESOLVED`, `OPEN` → `CLOSED`, `IN_PROGRESS` → `CLOSED`
- Backward moves: `IN_PROGRESS` → `OPEN`, `RESOLVED` → `IN_PROGRESS`, `CLOSED` → anything, `CANCELLED` → anything
- Cancel from later states: `RESOLVED` → `CANCELLED`, `CLOSED` → `CANCELLED`
- Same-state no-ops (e.g. `OPEN` → `OPEN`) — **not specified**; treat as open question, not silently allowed

### Open questions (state machine)

- Initial status on create: must it be `OPEN`?
- Who may transition (any user vs assigned agent)?
- Are reasons required for `CANCELLED`, `RESOLVED`, or `CLOSED`?
- Is `CLOSED` terminal for RAG indexing?
- Concurrency: last-write-wins vs optimistic locking if two transitions race?
- Persistence of transition history?

---

## 6. RAG ingestion requirements

### Specified

| ID | Requirement |
|----|-------------|
| ING-1 | Ticket data is used as RAG context (tickets are in the corpus). |
| ING-2 | When ticket data changes, embeddings must be refreshed. |

### Not specified

- Which fields constitute “ticket data” for embedding (title, description, comments, status, etc.)
- Create vs update vs status-only change: does every change refresh embeddings, or only content fields?
- Delete / cancel: remove from index, keep, or mark metadata?
- Chunking, overlap, document ID scheme
- Embedding model, vector store (pgvector vs external)
- Sync in the ticket-write transaction vs async job
- Failure handling if embedding refresh fails (ticket write success vs rollback)
- Backfill / reindex of existing tickets
- Deduplication of chunks

### Assumptions

| ID | Assumption | Note |
|----|------------|------|
| ING-A-1 | “Refresh” means the vector representation used for retrieval is updated to match current ticket content so later queries do not use stale embeddings. | Mechanism (upsert vs delete-and-insert) is unspecified. |

### Open questions

See list above. `spec/rag-ingestion.md` is an empty placeholder.

---

## 7. RAG retrieval requirements

### Specified

| ID | Requirement |
|----|-------------|
| RET-1 | Retrieval returns ticket context used as the sole grounding for generation. |
| RET-2 | `top-K` is configurable. |
| RET-3 | Similarity threshold is configurable. |
| RET-4 | If nothing relevant is found (in practice: no results passing the similarity threshold / empty retrieved set), the system must not invent an answer; it must emit an explicit no-match response. |
| RET-5 | When an answer is produced, it must cite ticket IDs of the tickets used. |

### Not specified

- Similarity metric (cosine, L2, inner product)
- Whether threshold is applied before or after top-K
- Default values for K and threshold
- Metadata filters (status, date, customer)
- Hybrid search (keyword + vector)
- Ranking beyond similarity
- Maximum context window / token budget for retrieved chunks

### Open questions

- Config scope: global vs per-query override
- Citation of chunk vs ticket ID only (ticket ID is specified; chunk IDs are not)
- Ordering of citations
- Whether scores are returned to the client

---

## 8. AI grounding and hallucination risks

### Specified controls

| Risk | Specified mitigation |
|------|----------------------|
| Answer from parametric knowledge instead of tickets | Answers must use **only** retrieved ticket context |
| Confident answer when corpus has no match | **Explicit no-match** when no relevant tickets |
| Ungrounded or untraceable claims | Answers **must cite ticket IDs** |
| Stale context after edits | **Embeddings refreshed** when ticket data changes |
| Over-retrieval / weak neighbors | **Similarity threshold** plus **top-K**, both configurable |

### Risks that remain (unspecified, therefore unmitigated in requirements)

| ID | Risk |
|----|------|
| AI-R-1 | Model ignores the “use only context” instruction unless the application also constrains prompts, tools, and output validation. Prompt/guardrail design is unspecified. |
| AI-R-2 | Citations listed but claims not actually supported by those tickets (citation hallucination). Requirement says cite IDs; it does not require claim-level entailment checks. |
| AI-R-3 | Wrong tickets retrieved (semantic near-misses) → grounded but incorrect operational advice. No evaluation thresholds specified (`spec/evaluation-strategy.md` is empty). |
| AI-R-4 | Partial ticket text in chunks → answer misses resolution steps stored in non-indexed fields. Indexed fields unspecified. |
| AI-R-5 | Embedding refresh lag or failure → retrieval uses old content. Consistency model unspecified. |
| AI-R-6 | Cancelled or sensitive tickets retrieved into answers. Index eligibility unspecified. |
| AI-R-7 | Prompt injection via ticket body influencing the model. Not mentioned. |
| AI-R-8 | PII in tickets sent to an external embedding/LLM API. Provider and privacy rules unspecified. |

### Open questions

- Is the generator allowed to paraphrase retrieved text, or only extract?
- Required refusal language for no-match?
- Human review before answers are shown?

---

## 9. Validation and error-handling requirements

### Specified

| ID | Requirement |
|----|-------------|
| VAL-1 | Invalid ticket status transitions are rejected by the backend. |
| VAL-2 | RAG no-match is an explicit response, not a generated guess. |

### Observed

- Bean Validation starter is present → field-level API validation is a likely implementation tool, not a defined rule set.

### Not specified

- Required fields on ticket create/update
- Length limits, allowed character sets
- Query string empty/too long for RAG
- HTTP/problem+json vs custom error envelope
- Idempotency and retry semantics
- Distinguishing 400 (invalid transition / bad input) vs 404 vs 409 vs 422
- Embedding pipeline errors surfaced to API clients

### Open questions

- Exact error codes and messages for SM-1
- Whether no-match is an error or a successful empty-grounding result (behavior of “explicit no-match” is specified; transport mapping is not)

---

## 10. Testing requirements

### Specified (by necessary verification of stated behavior)

Tests are not enumerated in the repo (`rules/testing.md` and `spec/test-strategy.md` are empty). The following are **test obligations implied by specified behavior**, not an invented test strategy:

| ID | Must be verifiable |
|----|--------------------|
| T-1 | Allowed transitions succeed: `OPEN→IN_PROGRESS`, `IN_PROGRESS→RESOLVED`, `RESOLVED→CLOSED`, `OPEN→CANCELLED`, `IN_PROGRESS→CANCELLED`. |
| T-2 | Backend rejects unspecified transitions (including skip, reverse, and transitions from `CLOSED` / `CANCELLED`). |
| T-3 | RAG answers include ticket ID citations when context is retrieved. |
| T-4 | When retrieval yields no relevant tickets, response is explicit no-match and is not a free-form invented answer. |
| T-5 | After ticket data changes, subsequent retrieval uses refreshed embeddings (staleness is incorrect). |
| T-6 | `top-K` and similarity threshold are actually configurable and affect retrieval. |
| T-7 | Generation path does not answer from non-retrieved knowledge when tickets are the required context. |

### Observed

- `spring-boot-starter-test` is a dependency.

### Open questions

- Unit vs integration vs contract vs E2E split
- Golden sets for RAG evaluation
- LLM-in-the-loop vs mocked embeddings/LLM in CI
- Frontend tests (no frontend requirements written)

---

## 11. Ambiguities or missing requirements

Placeholder files that were expected to hold requirements but are empty:

- `spec/architecture.md`
- `spec/data-model.md`
- `spec/api-contract.md`
- `spec/rag-api-contract.md`
- `spec/rag-ingestion.md`
- `spec/state-machine.md`
- `spec/ui-flow.md`
- `spec/test-strategy.md`
- `spec/evaluation-strategy.md`
- `rules/api-standards.md`, `rules/java-springboot.md`, `rules/rag-vector-store.md`, `rules/testing.md`
- `README.md`

**Missing product definition (high level):**

1. Ticket fields and invariants  
2. Create / list / filter / search / update / delete semantics  
3. Users, roles, authentication, authorization  
4. UI screens and flows (`frontend/` empty; `spec/ui-flow.md` empty)  
5. REST/HTTP contract and error model  
6. Vector store and embedding/LLM vendors  
7. Chunking and index membership (which statuses/fields)  
8. Embedding refresh consistency (sync/async, failure)  
9. Defaults and config surface for `top-K` and threshold  
10. Exact no-match response contract  
11. Citation format  
12. Non-functional targets (SLA, security, privacy, scale)  
13. Evaluation metrics for RAG quality  
14. Whether knowledge is tickets-only or includes other documents  
15. Same-state transitions and unlisted edges of the state machine  
16. Audit, notifications, SLA clocks, assignment  

---

## 12. Risks and assumptions

### Assumptions (explicit)

| ID | Assumption |
|----|------------|
| A-1 | The only authoritative product constraints today are those listed as **Specified** in this document plus the observed Maven stack. Empty markdown files are not hidden requirements. |
| A-2 | Status names are exactly `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`. |
| A-3 | “Invalid transitions” means any transition not listed in section 5. |
| A-4 | “Retrieved ticket context” is the only allowed evidence for a non-no-match answer. |
| A-5 | Ticket IDs in citations refer to the same identifiers used in the ticket domain. |
| A-6 | Embedding refresh is required for ticket **data** changes; whether status-only changes count as “ticket data” is **not** assumed (open question). |
| A-7 | PostgreSQL + Spring Web + JPA + validation are the intended backend foundation, not a complete architecture. |
| A-8 | No application code should be written until further spec documents are filled; this file is analysis only. |

### Risks

| ID | Risk | Impact |
|----|------|--------|
| R-1 | Building APIs or a data model before field-level requirements exist will lock in invented schema. | Rework |
| R-2 | RAG quality and hallucination controls are specified at policy level, not at prompt/eval/enforcement level. | Ungrounded answers in production |
| R-3 | Embedding refresh without a consistency spec can diverge index vs database. | Wrong retrieval |
| R-4 | Configurable K/threshold without defaults or operator guidance can be set unsafely (K too high, threshold too low). | Weak matches treated as relevant |
| R-5 | State machine incomplete (actors, reasons, terminals) vs strict backend rejection. | Support process mismatches |
| R-6 | External LLM/embedding APIs (unspecified) may leak ticket PII. | Compliance |
| R-7 | Empty frontend and UI-flow specs vs “management system” expectation. | Scope surprise |
| R-8 | `Main.java` is a generic starter, not the application; treating it as architecture would mislead. | Confusion |

---

## Traceability: user-highlighted constraints

| Highlighted constraint | Section | IDs |
|------------------------|---------|-----|
| `OPEN` → `IN_PROGRESS` → `RESOLVED` → `CLOSED` | §5, §1 | FR-3, SM |
| `OPEN` → `CANCELLED` | §5, §1 | FR-4 |
| `IN_PROGRESS` → `CANCELLED` | §5, §1 | FR-5 |
| Invalid transitions rejected by backend | §5, §9, §10 | FR-6, SM-1, VAL-1, T-2 |
| RAG answers use only retrieved ticket context | §7, §8 | FR-8, RET-1 |
| Answers cite ticket IDs | §7, §8 | FR-9, RET-5, T-3 |
| No relevant tickets → explicit no-match | §7, §8, §9 | FR-10, RET-4, VAL-2, T-4 |
| Embeddings refreshed when ticket data changes | §6 | FR-11, ING-2, T-5 |
| `top-K` and similarity threshold configurable | §7 | FR-12, FR-13, RET-2, RET-3, T-6 |

---

## Recommended next spec documents (not written here)

These are gaps, not requirements: fill `spec/state-machine.md`, `spec/data-model.md`, `spec/api-contract.md`, `spec/rag-ingestion.md`, `spec/rag-api-contract.md`, `spec/architecture.md`, `spec/ui-flow.md`, `spec/test-strategy.md`, and `spec/evaluation-strategy.md` so implementation can proceed without inventing behavior.
