# Implementation Plan — Support Ticket AI

**Status:** Planning only. No implementation in this document.  
**Based on:** Approved files in `spec/`, `rules/`, and `commands/`  
**Existing code:** Retrieval config (`RagRetrievalProperties`, `RagRetrievalService`), grounding review utilities (`RagOutputReviewer`, `RagAnswerGroundingReviewer`), regression tests for password-reset scenario.

**Assumptions for this plan** (from specs; resolve open questions before coding affected tasks):

| ID | Assumption |
|----|------------|
| PLAN-A-1 | `Comment.id` = UUID (per `api-contract.md` API-D-3) |
| PLAN-A-2 | Create ticket: `title` required; `priority` defaults to `MEDIUM` (API-D-5) |
| PLAN-A-3 | Client-supplied `ticketId` on create → **400** rejected |
| PLAN-A-4 | Status-only changes trigger **full re-ingest** per `rag-ingestion.md` ING-D-13 |
| PLAN-A-5 | Citation `title` resolved from **ticket store** at response time (RAG-API-A-2) |
| PLAN-A-6 | Dev LLM = Ollama chat; dev embeddings = Ollama `nomic-embed-text` (mirror ING-D-8 pattern) |
| PLAN-A-7 | No auth in v1 (API-A-1) |
| PLAN-A-8 | List API: filters only, no pagination in v1 (API-Q-4 deferred) |

---

## Dependency overview

```
BF (foundation)
 └── DB (schema + migrations)
      ├── SM (state machine) ──┐
      ├── TK (ticket CRUD)  ──┼── CM (comments)
      │                       │
      └── SF (search/filter) ─┘
           │
           ├── VS (vector store / PGVector)
           │    └── ING (RAG ingestion)
           │         └── RAG (AI ask API)
           │              ├── EV (RAG evaluation)
           │              └── IT (integration tests)
           └── FE (frontend)
                └── IT

RH (review/hardening) — continuous; final pass after IT
```

---

## 1. Backend foundation

### BF-001 — Project structure and package layout

| Field | Value |
|-------|--------|
| **Description** | Establish `ticket.*` and `rag.*` package structure per `rules/java-springboot.md`. Remove or repoint starter `Main.java`. |
| **Files/components** | `SupportTicketAiApplication.java`, `backend/pom.xml` (if new deps needed later), package dirs under `com.example.ticket.*`, `com.example.rag.*` |
| **Dependencies** | None |
| **Acceptance criteria** | Single Spring Boot entry point; packages exist for `api`, `domain`, `service`, `repository` (ticket) and existing `rag` subpackages |
| **Tests required** | `SupportTicketAiApplication` context smoke test (may fail until BF-002) |

### BF-002 — Enable persistence and profiles

| Field | Value |
|-------|--------|
| **Description** | Re-enable JPA/DataSource auto-config. Add `application.yaml` profiles: `dev`, `test`. Configure PostgreSQL connection placeholders. |
| **Files/components** | `SupportTicketAiApplication.java`, `application.yaml`, `application-dev.yaml`, `application-test.yaml` |
| **Dependencies** | BF-001 |
| **Acceptance criteria** | App starts with `dev` profile when DB available; JPA auto-config active; DataSource not globally excluded |
| **Tests required** | `@SpringBootTest` loads context with test profile + Testcontainers or H2 (if used only for non-vector tests) |

### BF-003 — Global API error handling

| Field | Value |
|-------|--------|
| **Description** | `@RestControllerAdvice` mapping domain exceptions to `api-contract.md` error envelope (`error.code`, `error.message`). |
| **Files/components** | `ticket.api.GlobalExceptionHandler`, `ticket.api.ErrorResponse`, domain exception types (stub) |
| **Dependencies** | BF-001 |
| **Acceptance criteria** | Validation errors → 400 `VALIDATION_ERROR`; consistent JSON error shape |
| **Tests required** | `@WebMvcTest` with a stub controller throwing validation error |

### BF-004 — API base path and CORS (dev)

| Field | Value |
|-------|--------|
| **Description** | Configure `/api/v1` as controller base. Optional dev CORS for future frontend. |
| **Files/components** | `ticket.api` config or `@RequestMapping` on controllers |
| **Dependencies** | BF-001 |
| **Acceptance criteria** | All ticket endpoints under `/api/v1`; matches `rules/api-standards.md` RULE-API-1 |
| **Tests required** | MockMvc verifies path prefix on one endpoint |

---

## 2. Database

### DB-001 — Flyway/Liquibase setup

| Field | Value |
|-------|--------|
| **Description** | Add migration tool to `pom.xml`. Create `db/migration` (or equivalent) directory. |
| **Files/components** | `pom.xml`, `src/main/resources/db/migration/` |
| **Dependencies** | BF-002 |
| **Acceptance criteria** | Migrations run on app startup; version tracked |
| **Tests required** | Integration test: migrations apply cleanly on empty Testcontainers DB |

### DB-002 — Ticket table migration

| Field | Value |
|-------|--------|
| **Description** | DDL for `ticket`: PK `id`, unique `ticket_id`, enums/checks for `status` and `priority`, timestamps. Per `data-model.md` DM-D-3, DM-D-4, DM-D-6. |
| **Files/components** | `V1__create_ticket.sql` (or equivalent) |
| **Dependencies** | DB-001 |
| **Acceptance criteria** | Unique index on `ticket_id`; status/priority constrained to spec enums |
| **Tests required** | Migration integration test; reject invalid enum at DB level (optional) |

### DB-003 — Comment table migration

| Field | Value |
|-------|--------|
| **Description** | DDL for `comment`: PK UUID, FK `ticket_id` → `ticket.ticket_id`, indexes per DM-D-6. |
| **Files/components** | `V2__create_comment.sql` |
| **Dependencies** | DB-002 |
| **Acceptance criteria** | FK enforced; orphan comments impossible |
| **Tests required** | Integration test: insert comment fails without parent ticket |

### DB-004 — PGVector extension and ticket_chunk table

| Field | Value |
|-------|--------|
| **Description** | Enable `vector` extension. Create `ticket_chunk` per `rag-ingestion.md` §6: embedding column, metadata columns, HNSW index (ING-D-10, ING-D-11). |
| **Files/components** | `V3__pgvector_ticket_chunk.sql` |
| **Dependencies** | DB-001 |
| **Acceptance criteria** | pgvector extension enabled; `ticket_chunk` with cosine HNSW index |
| **Tests required** | Testcontainers PostgreSQL+pgvector: migration succeeds; vector column accepts inserts |

### DB-005 — ticketId sequence / generator

| Field | Value |
|-------|--------|
| **Description** | Server-side `TKT-1001+` generation strategy (sequence table or atomic counter). Per DM-D-1, DM-D-2. |
| **Files/components** | Migration for sequence; `TicketIdGenerator` service |
| **Dependencies** | DB-002 |
| **Acceptance criteria** | Sequential `TKT-{n}` from 1001; unique; never reused |
| **Tests required** | Unit/integration: concurrent creates produce unique ids |

---

## 3. Ticket CRUD

### TK-001 — Domain enums and Ticket entity

| Field | Value |
|-------|--------|
| **Description** | JPA `Ticket` entity, `TicketStatus`, `TicketPriority` enums matching specs. |
| **Files/components** | `ticket.domain.Ticket`, `TicketStatus`, `TicketPriority` |
| **Dependencies** | DB-002, BF-002 |
| **Acceptance criteria** | Entity maps to migration; enums match data-model exactly |
| **Tests required** | JPA mapping test (save/load round-trip) |

### TK-002 — TicketRepository

| Field | Value |
|-------|--------|
| **Description** | Spring Data JPA repository: find by `ticketId`, list with filters. |
| **Files/components** | `ticket.repository.TicketRepository` |
| **Dependencies** | TK-001 |
| **Acceptance criteria** | `findByTicketId`, query methods for status/priority/assignee/category |
| **Tests required** | `@DataJpaTest` with Testcontainers |

### TK-003 — TicketIdGenerator integration

| Field | Value |
|-------|--------|
| **Description** | Wire `TicketIdGenerator` into create flow. |
| **Files/components** | `ticket.service.TicketIdGenerator`, `TicketService` (partial) |
| **Dependencies** | DB-005, TK-001 |
| **Acceptance criteria** | New tickets receive `TKT-1001` format ids |
| **Tests required** | Unit test for generator; integration create assigns id |

### TK-004 — Create ticket API

| Field | Value |
|-------|--------|
| **Description** | `POST /api/v1/tickets` — create with `status=OPEN` (SM-D-3). Reject client `ticketId`. Default priority MEDIUM. |
| **Files/components** | `TicketController`, `CreateTicketRequest`, `TicketResponse`, `TicketService.create` |
| **Dependencies** | TK-003, BF-003, BF-004, SM-002 (create sets OPEN — can stub status until SM-002) |
| **Acceptance criteria** | 201 + ticket body; `status: OPEN`; no client `ticketId` |
| **Tests required** | `@WebMvcTest`: 201 happy path; 400 if `ticketId` sent; 400 blank title |

### TK-005 — Get ticket API

| Field | Value |
|-------|--------|
| **Description** | `GET /api/v1/tickets/{ticketId}` |
| **Files/components** | `TicketController`, `TicketService.getByTicketId` |
| **Dependencies** | TK-002, TK-004 |
| **Acceptance criteria** | 200 with full ticket; 404 `TICKET_NOT_FOUND`; internal `id` not exposed |
| **Tests required** | MockMvc: 200, 404 |

### TK-006 — Update ticket fields API

| Field | Value |
|-------|--------|
| **Description** | `PATCH /api/v1/tickets/{ticketId}` — field updates only; no `status` (API-D-7). |
| **Files/components** | `UpdateTicketRequest`, `TicketController`, `TicketService.update` |
| **Dependencies** | TK-005 |
| **Acceptance criteria** | 200 with updated fields; `status` in body ignored or 400 |
| **Tests required** | MockMvc: partial update; status field rejected |

---

## 4. State machine

### SM-001 — TicketStatusTransitionPolicy (pure domain)

| Field | Value |
|-------|--------|
| **Description** | `allow(from, to)` implementing full 5×5 matrix per `state-machine.md` §2.3. |
| **Files/components** | `ticket.domain.TicketStatusTransitionPolicy` |
| **Dependencies** | TK-001 (enum only) |
| **Acceptance criteria** | 5 allowed edges true; all other 20 cells false; same-state false (SM-D-2) |
| **Tests required** | **Unit:** table-driven 25-case test per state-machine §5.1 (no Spring) |

### SM-002 — TicketService.create sets OPEN

| Field | Value |
|-------|--------|
| **Description** | Create path sets `OPEN` without calling transition policy (SM-D-3). |
| **Files/components** | `TicketService.create` |
| **Dependencies** | SM-001, TK-003 |
| **Acceptance criteria** | Every created ticket has `status=OPEN`; policy not invoked on create |
| **Tests required** | Service test: create → OPEN |

### SM-003 — TicketService.changeStatus

| Field | Value |
|-------|--------|
| **Description** | Load persisted status, call policy, save or reject. Per state-machine §3.1. |
| **Files/components** | `TicketService.changeStatus`, `InvalidStatusTransitionException` |
| **Dependencies** | SM-001, TK-002 |
| **Acceptance criteria** | Illegal transition: no save; legal: persist new status |
| **Tests required** | Service tests per state-machine §5.2 (mock repo, real policy) |

### SM-004 — Status transition API

| Field | Value |
|-------|--------|
| **Description** | `PATCH /api/v1/tickets/{ticketId}/status` |
| **Files/components** | `ChangeStatusRequest`, `TicketController` |
| **Dependencies** | SM-003, BF-003, TK-005 |
| **Acceptance criteria** | Legal → 200; illegal → 400 `INVALID_STATUS_TRANSITION`; GET unchanged after reject |
| **Tests required** | MockMvc T-1/T-2 scenarios; state-machine §5.3 |

---

## 5. Comments

### CM-001 — Comment entity and repository

| Field | Value |
|-------|--------|
| **Description** | JPA `Comment` entity (UUID PK), `CommentRepository`. |
| **Files/components** | `ticket.domain.Comment`, `ticket.repository.CommentRepository` |
| **Dependencies** | DB-003, TK-001 |
| **Acceptance criteria** | FK to `ticket.ticketId`; append-only (no `updatedAt`) |
| **Tests required** | `@DataJpaTest` save/list by ticketId |

### CM-002 — CommentService

| Field | Value |
|-------|--------|
| **Description** | Add comment; verify parent ticket exists; does not change ticket status (SM-D-4). |
| **Files/components** | `CommentService` |
| **Dependencies** | CM-001, TK-002 |
| **Acceptance criteria** | 404 if ticket missing; status unchanged after comment |
| **Tests required** | Service unit tests with mocked repos |

### CM-003 — Add comment API

| Field | Value |
|-------|--------|
| **Description** | `POST /api/v1/tickets/{ticketId}/comments` |
| **Files/components** | `CommentController`, `CreateCommentRequest`, `CommentResponse` |
| **Dependencies** | CM-002, BF-003 |
| **Acceptance criteria** | 201 + comment; 404 missing ticket |
| **Tests required** | MockMvc happy path + 404 |

### CM-004 — List comments API

| Field | Value |
|-------|--------|
| **Description** | `GET /api/v1/tickets/{ticketId}/comments` ordered by `createdAt` asc. |
| **Files/components** | `CommentController` |
| **Dependencies** | CM-002 |
| **Acceptance criteria** | 200 array; chronological order |
| **Tests required** | MockMvc with two comments verifies order |

---

## 6. Search / filter

### SF-001 — List tickets with filters API

| Field | Value |
|-------|--------|
| **Description** | `GET /api/v1/tickets` with optional `status`, `priority`, `assignee`, `category` (API-D-6). |
| **Files/components** | `TicketController`, `TicketRepository` query, `TicketService.list` |
| **Dependencies** | TK-002, TK-004 |
| **Acceptance criteria** | Filters combine correctly; empty list valid; no pagination in v1 |
| **Tests required** | `@DataJpaTest` or integration: seed tickets, filter by status; MockMvc list endpoint |

---

## 7. RAG ingestion

### ING-001 — Embedding configuration properties

| Field | Value |
|-------|--------|
| **Description** | `RagEmbeddingProperties` under `rag.embedding.*` per ING-D-9. Chunk config `rag.chunk.*`. |
| **Files/components** | `rag.config.RagEmbeddingProperties`, `RagChunkProperties`, `application.yaml` |
| **Dependencies** | BF-002 (existing `RagRetrievalProperties` pattern) |
| **Acceptance criteria** | Provider, model, dimension, base-url configurable; validated at startup |
| **Tests required** | `ApplicationContextRunner` binding tests (mirror existing retrieval config tests) |

### ING-002 — KnowledgeDocumentBuilder

| Field | Value |
|-------|--------|
| **Description** | Build plain-text document from ticket + comments per `rag-ingestion.md` §2 template (ING-D-3). |
| **Files/components** | `rag.ingestion.KnowledgeDocumentBuilder` |
| **Dependencies** | TK-001, CM-001 |
| **Acceptance criteria** | Correct section order; omits empty sections; includes comments chronologically |
| **Tests required** | Unit tests with fixture tickets (no Spring) |

### ING-003 — Section chunker

| Field | Value |
|-------|-------|
| **Description** | Section-based chunking with fixed-size overflow (ING-D-5–D-7). Prefix `[Ticket {ticketId}] {title}`. |
| **Files/components** | `rag.ingestion.Chunker`, `Chunk` value type |
| **Dependencies** | ING-002 |
| **Acceptance criteria** | Small ticket → one chunk; large section → windows with overlap; prefix on every chunk |
| **Tests required** | Unit tests per `rag-ingestion.md` §13 (ING-T-* chunk cases) |

### ING-004 — EmbeddingClient port and Ollama adapter

| Field | Value |
|-------|--------|
| **Description** | `EmbeddingClient` interface; Ollama HTTP implementation for dev (ING-D-8). |
| **Files/components** | `rag.ingestion.EmbeddingClient`, `OllamaEmbeddingClient` |
| **Dependencies** | ING-001 |
| **Acceptance criteria** | Returns `float[]` of configured dimension; fails fast on dimension mismatch |
| **Tests required** | Unit test with mocked HTTP; optional manual test against local Ollama |

### ING-005 — TicketIngestionService orchestration

| Field | Value |
|-------|--------|
| **Description** | Orchestrate: load ticket + comments → document → chunk → embed → persist. Triggers per ING-D-13. |
| **Files/components** | `rag.ingestion.TicketIngestionService` |
| **Dependencies** | ING-002, ING-003, ING-004, VS-002 |
| **Acceptance criteria** | Called after ticket create/update, comment create, legal status change; not on illegal transition |
| **Tests required** | Service test with mocked `TicketVectorStore` and `EmbeddingClient`; verify invoke on events |

### ING-006 — Wire ingestion to TicketService and CommentService

| Field | Value |
|-------|--------|
| **Description** | Post-commit hook after successful persist (ING-D-2). Ticket OLTP not rolled back on ingest failure. |
| **Files/components** | `TicketService`, `CommentService`, `TicketIngestionService` |
| **Dependencies** | ING-005, TK-006, SM-003, CM-002 |
| **Acceptance criteria** | Create/update/comment/status success triggers ingest; illegal transition does not |
| **Tests required** | Integration: update title → ingest called (mock or spy); T-5 prep |

### ING-007 — Ingestion failure handling

| Field | Value |
|-------|--------|
| **Description** | Log failures; optional retry; no rollback of ticket per `rag-ingestion.md` §9. |
| **Files/components** | `TicketIngestionService`, logging/metrics |
| **Dependencies** | ING-006 |
| **Acceptance criteria** | Ticket saved even if embedding fails; error logged with `ticketId` |
| **Tests required** | Service test: embedding throws → ticket still persisted |

---

## 8. Vector store

### VS-001 — TicketVectorStore port

| Field | Value |
|-------|--------|
| **Description** | Interface: `replaceByTicketId(ticketId, chunks)`, `deleteByTicketId`, `findNearest(embedding, limit)`. |
| **Files/components** | `rag.retrieval.TicketVectorStore` (or `rag.ingestion` package) |
| **Dependencies** | DB-004 |
| **Acceptance criteria** | Port defined; decouples ingestion from retrieval |
| **Tests required** | N/A (interface only) |

### VS-002 — PgVectorTicketVectorStore implementation

| Field | Value |
|-------|--------|
| **Description** | JDBC/JPA implementation: transactional replace (ING-D-15), cosine search (ING-D-12). |
| **Files/components** | `rag.ingestion.PgVectorTicketVectorStore` |
| **Dependencies** | VS-001, DB-004 |
| **Acceptance criteria** | Replace atomic; delete+insert in one transaction; metadata columns populated |
| **Tests required** | Testcontainers integration: insert, replace, delete by ticketId |

### VS-003 — VectorSearchPort PGVector adapter

| Field | Value |
|-------|--------|
| **Description** | Implement `VectorSearchPort` using `TicketVectorStore`; return `RetrievedChunk` with cosine distance. |
| **Files/components** | `rag.retrieval.PgVectorSearchPort`; test profile uses `InMemoryTicketVectorStore` |
| **Dependencies** | VS-002, existing `RagRetrievalService` |
| **Acceptance criteria** | `RagRetrievalService` uses real store in `dev`; NoOp only in isolated unit tests |
| **Tests required** | Integration: known vectors return expected neighbors; existing `RagRetrievalServiceTest` still passes with mock |

### VS-004 — Retrieval integration with metadata

| Field | Value |
|-------|--------|
| **Description** | Ensure retrieved chunks carry `ticketId`, `content`, distance for threshold filter and citations. |
| **Files/components** | `RetrievedChunk`, `PgVectorSearchPort` |
| **Dependencies** | VS-003 |
| **Acceptance criteria** | Similarity = 1 - distance; threshold filter in `RagRetrievalService` works end-to-end |
| **Tests required** | Integration ING-T-12, ING-T-13; existing config tests |

---

## 9. AI ask API

### RAG-001 — Generator port and Ollama chat adapter

| Field | Value |
|-------|--------|
| **Description** | `GeneratorClient` interface; Ollama chat implementation. Enforce §8.1 prompt text (PLAN-A-6). |
| **Files/components** | `rag.generation.GeneratorClient`, `OllamaGeneratorClient`, `GeneratorPromptBuilder` |
| **Dependencies** | ING-001 |
| **Acceptance criteria** | Prompt includes mandatory grounding instructions; input = query + chunks only |
| **Tests required** | Unit test: prompt builder contains §8.1 text; mock generator returns fixed JSON |

### RAG-002 — Citation validator

| Field | Value |
|-------|--------|
| **Description** | Validate `citations[].ticketId` ⊆ keep-set (RAG-API-D-12). Reject/downgrade to `NO_MATCH`. |
| **Files/components** | `rag.generation.CitationValidator` |
| **Dependencies** | None (pure logic) |
| **Acceptance criteria** | Citing non-retrieved id fails validation |
| **Tests required** | Unit tests: valid/invalid citation sets |

### RAG-003 — RagQueryService orchestration

| Field | Value |
|-------|--------|
| **Description** | Embed query → retrieve → grounding gate → generate → validate → assemble response. |
| **Files/components** | `rag.service.RagQueryService` |
| **Dependencies** | ING-004, VS-003, RAG-001, RAG-002, existing `RagAnswerGroundingReviewer` |
| **Acceptance criteria** | Empty keep-set → `NO_MATCH` without generator call; pipeline failures → `ERROR` codes per contract |
| **Tests required** | Service tests with mocked ports: ANSWER, NO_MATCH, ERROR paths |

### RAG-004 — RAG response DTOs and mapper

| Field | Value |
|-------|--------|
| **Description** | JSON envelope per `rag-api-contract.md` §3: `outcome`, `query`, `answer`, `citations`, `confidence`, `escalation`, `error`, `noMatch`. |
| **Files/components** | `rag.api.RagQueryRequest`, `RagQueryResponse`, `RagResponseMapper` |
| **Dependencies** | RAG-003 |
| **Acceptance criteria** | All three outcomes match contract shapes; `NO_MATCH` → HTTP 200 |
| **Tests required** | Unit tests on mapper; JSON snapshot or contract tests |

### RAG-005 — RagQueryController

| Field | Value |
|-------|--------|
| **Description** | `POST /api/v1/rag/query` (RAG-API-D-1). |
| **Files/components** | `rag.api.RagQueryController` |
| **Dependencies** | RAG-004, BF-003 |
| **Acceptance criteria** | Blank query → 400 `INVALID_QUERY`; valid query → 200 with discriminated `outcome` |
| **Tests required** | RAG-API-T-1 through T-7 per `rag-api-contract.md` §10 (MockMvc) |

### RAG-006 — Confidence and escalation assembly

| Field | Value |
|-------|--------|
| **Description** | `confidence.score` = max similarity of kept chunks (RAG-API-A-1); escalation on `NO_MATCH` (RAG-API-D-10). |
| **Files/components** | `RagResponseMapper` |
| **Dependencies** | RAG-004 |
| **Acceptance criteria** | ANSWER has confidence; NO_MATCH has `escalation.recommended: true` |
| **Tests required** | Contract tests on response fields |

### RAG-007 — Post-generation grounding review integration

| Field | Value |
|-------|--------|
| **Description** | Run `RagOutputReviewer` on ANSWER; downgrade to `NO_MATCH` or `ERROR` on conflict (extends existing utility). |
| **Files/components** | `RagQueryService`, existing `RagOutputReviewer` |
| **Dependencies** | RAG-003, existing review package |
| **Acceptance criteria** | Password-reset regression scenario fails review in test; correct answer passes |
| **Tests required** | `PasswordResetRagScenarioTest` (exists); extend for API layer if needed |

### RAG-008 — Citation title from ticket store

| Field | Value |
|-------|--------|
| **Description** | Resolve `citations[].title` via ticket store lookup (PLAN-A-5). |
| **Files/components** | `RagResponseMapper`, `TicketRepository` |
| **Dependencies** | RAG-004, TK-002 |
| **Acceptance criteria** | Every citation includes non-empty `title` from ticket |
| **Tests required** | Service test: citation title matches ticket title |

---

## 10. Frontend

### FE-001 — Project scaffold

| Field | Value |
|-------|--------|
| **Description** | Initialize `frontend/` (framework TBD per UI-Q-5; recommend React or Vue). API client base URL config. |
| **Files/components** | `frontend/` project structure, env config |
| **Dependencies** | BF-004 (API available) |
| **Acceptance criteria** | Dev server runs; can reach backend `/api/v1` |
| **Tests required** | Smoke: app mounts |

### FE-002 — Ticket list page

| Field | Value |
|-------|--------|
| **Description** | List tickets with filters per `ui-flow.md` §4.1. |
| **Files/components** | `TicketList` component, API client |
| **Dependencies** | FE-001, SF-001 |
| **Acceptance criteria** | Displays tickets; filters work; navigates to detail |
| **Tests required** | Component test with mocked API (optional E2E later) |

### FE-003 — Create ticket page

| Field | Value |
|-------|--------|
| **Description** | Form per `ui-flow.md` §4.2. |
| **Files/components** | `CreateTicket` component |
| **Dependencies** | FE-001, TK-004 |
| **Acceptance criteria** | 201 redirects to detail; validation errors shown |
| **Tests required** | Component test: submit calls API |

### FE-004 — Ticket detail page

| Field | Value |
|-------|--------|
| **Description** | Show ticket, comments, status actions per `ui-flow.md` §4.3–4.5. |
| **Files/components** | `TicketDetail`, `CommentList`, `StatusActions` |
| **Dependencies** | FE-002, TK-005, CM-003, CM-004, SM-004 |
| **Acceptance criteria** | Only legal status buttons shown; illegal transition shows error |
| **Tests required** | Component tests for status action visibility |

### FE-005 — Ask AI (RAG) page

| Field | Value |
|-------|--------|
| **Description** | Query input; render ANSWER / NO_MATCH / ERROR per `ui-flow.md` §4.6 and `rag-api-contract.md` §9. |
| **Files/components** | `AskAi` component |
| **Dependencies** | FE-001, RAG-005 |
| **Acceptance criteria** | Branches on `outcome`; citations link to ticket detail; NO_MATCH not shown as server error |
| **Tests required** | Component tests with mocked ANSWER, NO_MATCH, ERROR responses |

---

## 11. Integration tests

### IT-001 — Ticket lifecycle integration

| Field | Value |
|-------|--------|
| **Description** | End-to-end: create → IN_PROGRESS → RESOLVED → CLOSED via HTTP. |
| **Files/components** | `TicketLifecycleIntegrationTest` |
| **Dependencies** | SM-004, TK-004, BF-002, DB-* |
| **Acceptance criteria** | T-1 scenarios pass against real DB |
| **Tests required** | `@SpringBootTest` + Testcontainers PostgreSQL |

### IT-002 — Illegal transition integration

| Field | Value |
|-------|--------|
| **Description** | `OPEN → CLOSED` rejected; DB unchanged. |
| **Files/components** | `TicketLifecycleIntegrationTest` |
| **Dependencies** | IT-001 |
| **Acceptance criteria** | T-2 satisfied at HTTP layer |
| **Tests required** | Same test class |

### IT-003 — Ingestion staleness (T-5)

| Field | Value |
|-------|--------|
| **Description** | Update ticket description → re-query RAG retrieves new content. |
| **Files/components** | `RagStalenessIntegrationTest` |
| **Dependencies** | ING-006, VS-003, RAG-005 |
| **Acceptance criteria** | Post-update retrieval reflects new text (mock or real embeddings) |
| **Tests required** | Integration with fixed embedding vectors |

### IT-004 — Full RAG happy path integration

| Field | Value |
|-------|--------|
| **Description** | Seed ticket → ingest → query → ANSWER with citations. |
| **Files/components** | `RagQueryIntegrationTest` |
| **Dependencies** | RAG-005, ING-006, VS-003 |
| **Acceptance criteria** | T-3, T-4 partial; ANSWER includes `citations[].ticketId` |
| **Tests required** | `@SpringBootTest` + Testcontainers; mock generator |

### IT-005 — RAG no-match integration

| Field | Value |
|-------|--------|
| **Description** | Query with no relevant tickets → `NO_MATCH`, HTTP 200. |
| **Files/components** | `RagQueryIntegrationTest` |
| **Dependencies** | IT-004 |
| **Acceptance criteria** | T-4; `answer` null; `citations` [] |
| **Tests required** | Integration test |

### IT-006 — Comment triggers re-ingest integration

| Field | Value |
|-------|--------|
| **Description** | Add comment → vector rows updated; new comment text retrievable. |
| **Files/components** | `IngestionIntegrationTest` |
| **Dependencies** | CM-003, ING-006 |
| **Acceptance criteria** | Comment content appears in retrieved chunks after ingest |
| **Tests required** | Testcontainers |

---

## 12. RAG evaluation

### EV-001 — Golden dataset fixture structure

| Field | Value |
|-------|--------|
| **Description** | Define JSON/YAML format for golden cases per `evaluation-strategy.md` §3.2. |
| **Files/components** | `backend/src/test/resources/eval/golden/` |
| **Dependencies** | RAG-005 |
| **Acceptance criteria** | At least 5 cases: ANSWER and NO_MATCH examples |
| **Tests required** | Schema validation test for fixture files |

### EV-002 — Golden dataset runner (manual/CI optional)

| Field | Value |
|-------|--------|
| **Description** | Test harness loading golden cases against live or mocked RAG pipeline. |
| **Files/components** | `GoldenDatasetEvaluator` |
| **Dependencies** | EV-001, IT-004 |
| **Acceptance criteria** | Reports groundedness pass rate, no-match precision per EVAL-D-1–4 |
| **Tests required** | Tagged `@Tag("eval")`; not in default CI unless configured |

### EV-003 — Expand grounding regression scenarios

| Field | Value |
|-------|--------|
| **Description** | Add fixtures beyond password-reset (unsupported claim, empty context). |
| **Files/components** | `backend/src/test/java/.../fixture/`, `RagAnswerGroundingReviewerTest` |
| **Dependencies** | RAG-007, existing review package |
| **Acceptance criteria** | 100% conflict detection on fixture set per `evaluation-strategy.md` §4.1 |
| **Tests required** | Unit tests per scenario |

### EV-004 — Retrieval threshold calibration script

| Field | Value |
|-------|--------|
| **Description** | Document/script to log similarity distributions for operator tuning (informational). |
| **Files/components** | `docs/` or admin endpoint (read-only) |
| **Dependencies** | VS-004 |
| **Acceptance criteria** | Debug logs include topK, threshold, hit counts (ING-D-20) |
| **Tests required** | Manual verification; optional log assertion in integration test |

---

## 13. Review / hardening

### RH-001 — Spec compliance review (mid-implementation)

| Field | Value |
|-------|--------|
| **Description** | Run `commands/review-spec.md` after ticket slice; fix doc drift. |
| **Files/components** | N/A (process) |
| **Dependencies** | IT-001, IT-002 |
| **Acceptance criteria** | No unresolved CRITICAL spec contradictions for implemented scope |
| **Tests required** | N/A |

### RH-002 — Code review against rules

| Field | Value |
|-------|--------|
| **Description** | Run `commands/review-code.md` per milestone. |
| **Files/components** | All `backend/src` |
| **Dependencies** | Each major section complete |
| **Acceptance criteria** | No RULE-J-* / RULE-RAG-* violations in implemented code |
| **Tests required** | `mvn test` green |

### RH-003 — AI/RAG review

| Field | Value |
|-------|--------|
| **Description** | Run `commands/review-ai.md` after RAG slice. |
| **Files/components** | `rag/*` |
| **Dependencies** | RAG-005, EV-003 |
| **Acceptance criteria** | Grounding gate, citation validation, config externalization verified |
| **Tests required** | All RAG contract + grounding tests pass |

### RH-004 — CFG-T-3 static check

| Field | Value |
|-------|--------|
| **Description** | ArchUnit or grep test: no literals `5`/`0.72` in `RagRetrievalService`. |
| **Files/components** | `backend/src/test/java/.../arch/` |
| **Dependencies** | VS-003 |
| **Acceptance criteria** | Test fails if policy literals reintroduced |
| **Tests required** | `CfgPolicyLiteralsTest` |

### RH-005 — Observability pass

| Field | Value |
|-------|--------|
| **Description** | Structured logging for ingest, retrieve, no-match; no PII/secrets in logs. |
| **Files/components** | `TicketIngestionService`, `RagQueryService` |
| **Dependencies** | ING-007, RAG-003 |
| **Acceptance criteria** | RULE-RAG-32–34 satisfied |
| **Tests required** | Manual/log capture review |

### RH-006 — README and runbook

| Field | Value |
|-------|--------|
| **Description** | Document local dev: PostgreSQL, pgvector, Ollama setup, env vars, `mvn test`. |
| **Files/components** | `README.md`, `docs/` |
| **Dependencies** | IT-004 |
| **Acceptance criteria** | New developer can run backend + one RAG query locally |
| **Tests required** | Manual walkthrough |

### RH-007 — Final integration and release checklist

| Field | Value |
|-------|--------|
| **Description** | Full `mvn test`; optional `@Tag("integration")` and `@Tag("eval")` suites; review open questions for v1 scope. |
| **Files/components** | CI config (if added) |
| **Dependencies** | All IT-*, EV-*, FE-* |
| **Acceptance criteria** | T-1–T-7 verified; `commands/review-code.md` → APPROVE or APPROVE AFTER FIXES |
| **Tests required** | Full test suite execution documented |

---

## Suggested implementation order (sprints)

| Sprint | Tasks | Delivers |
|--------|-------|----------|
| **1** | BF-001–004, DB-001–003, SM-001–002, TK-001–004 | App boots, tickets creatable, state machine unit-tested |
| **2** | SM-003–004, TK-005–006, CM-001–004, SF-001, IT-001–002 | Full ticket + comment API |
| **3** | DB-004–005, ING-001–004, VS-001–003 | PGVector + ingestion pipeline |
| **4** | ING-005–007, VS-004, IT-003, IT-006 | Ingestion wired; staleness test |
| **5** | RAG-001–008, IT-004–005, EV-003 | RAG query API end-to-end |
| **6** | FE-001–005, EV-001–002, RH-001–007 | UI + eval + hardening |

---

## Traceability matrix (requirements → tasks)

| Requirement | Tasks |
|-------------|-------|
| FR-1–FR-6 | TK-*, SM-*, IT-001–002 |
| FR-7–FR-10 | RAG-*, VS-*, ING-*, IT-004–005 |
| FR-11 | ING-005–007, IT-003, IT-006 |
| FR-12–FR-13 | Existing + VS-004 (BF-002 config) |
| T-1–T-2 | SM-001, SM-003–004, IT-001–002 |
| T-3–T-4 | RAG-005, IT-004–005 |
| T-5 | IT-003 |
| T-6 | Existing retrieval config tests + VS-004 |
| T-7 | RAG-001, RAG-007, EV-003 |

---

## Out of scope for v1 (per open spec questions)

* Authentication / authorization (API-Q-1)
* Ticket delete (API-Q-8)
* Pagination (API-Q-4)
* Comment update/delete (API-Q-6)
* Per-request top-K / threshold override (RAG-API-Q-4)
* Production cloud LLM/embedding (ING-D-21) — dev Ollama first
* LLM-as-judge CI eval (EVAL-Q-1)

---

## Existing work — reuse, do not redo

| Component | Status | Used by |
|-----------|--------|---------|
| `RagRetrievalProperties` | Done | VS-003, RAG-003 |
| `RagRetrievalService` | Done | VS-003, RAG-003 |
| `RagAnswerGroundingReviewer` | Done | RAG-007, EV-003 |
| `RagOutputReviewer` | Done | RAG-007 |
| `PasswordResetRagScenarioTest` | Done | EV-003, RH-003 |
| `NoOpVectorSearchPort` | Removed (obsolete) | VS-003 complete — use `InMemoryTicketVectorStore` / `PgVectorTicketVectorStore` |
