# Architecture

**Status:** Logical architecture only. No implementation.  
**Depends on:** `spec/requirements.md`  
**Does not depend on:** data model, API contract, state-machine detail spec, RAG ingestion spec, RAG API spec, UI flow, test/evaluation specs (those come later).

**Source rule:** Components and flows below exist only to satisfy **Specified** requirements and **Observed** stack choices. Paths, schemas, vendors, and field lists that are not in requirements are **Open questions** or **Assumptions**, not decisions.

---

## 1. Purpose

The system manages support tickets with a server-enforced lifecycle and answers questions using retrieval-augmented generation (RAG) over ticket context.

Architecture must guarantee:

| Constraint | Architectural placement |
|------------|-------------------------|
| Invalid status transitions rejected by the backend (FR-6, SM-1, VAL-1) | Ticket write path; not the UI |
| Answers use only retrieved ticket context (FR-8, RET-1) | RAG pipeline after retrieval, before/at generation |
| Answers cite ticket IDs (FR-9, RET-5) | RAG response assembly |
| No relevant tickets → explicit no-match, no fabricated answer (FR-10, RET-4, VAL-2) | Gate between retrieval and generation |
| Embeddings refreshed when ticket data changes (FR-11, ING-2) | Coupled to ticket persist/update path |
| `top-K` and similarity threshold configurable (FR-12, FR-13, RET-2, RET-3) | Retrieval configuration, not hardcoded in generation |

---

## 2. Context

### Specified actors and neighbors

Requirements do not name users, identity providers, or LLM/vector vendors.

### Observed neighbors

| Neighbor | Basis |
|----------|--------|
| HTTP clients (browser, tools, or a future frontend in `frontend/`) | `spring-boot-starter-web`; empty `frontend/` |
| PostgreSQL | `postgresql` driver; assumption NFR-A-1: system of record for tickets |
| Embedding model and LLM | Required by RAG (FR-7–FR-13); **provider unspecified** |
| Vector index | Required to retrieve ticket context; **store unspecified** (could be PostgreSQL extension or a separate store) |

```
[ Client ]  --HTTP/JSON (assumed NFR-A-2)--  [ Backend (Spring Boot) ]
                                                    |
                    +-------------------------------+-------------------------------+
                    |                               |                               |
            [ PostgreSQL tickets ]     [ Vector index (TBD) ]     [ Embedding + LLM (TBD) ]
```

---

## 3. Logical components

These are responsibility boundaries, not packages or microservices. A single Spring Boot process may host all of them. **Microservice split is not required and not specified.**

| Component | Responsibility | Must / must not |
|-----------|----------------|-----------------|
| **API boundary** | Accept requests; apply request validation (NFR-A-3); return ticket results, grounded answers, or explicit no-match; return backend rejection for illegal transitions | Must not be the only place that knows allowed transitions |
| **Ticket application service** | Orchestrate ticket persistence and status changes | Must invoke the state machine before persist of a new status |
| **State machine** | Decide whether `from → to` is an allowed transition | Must reject anything not in the specified graph (A-3) |
| **Ticket store** | Persist tickets and current status | PostgreSQL assumed (NFR-A-1); schema deferred to data model |
| **Ingestion / embedding refresher** | When ticket **data** changes, update vectors used for retrieval (ING-A-1) | Must run as a consequence of ticket change, not as a client honor system |
| **Vector index** | Store embeddings + association to ticket IDs | Implementation deferred |
| **Retriever** | Embed query; fetch top-K candidates; apply similarity threshold | Must read configurable K and threshold |
| **Grounding gate** | If retrieved set is empty / nothing relevant → **stop**; emit explicit no-match | Must not call the generator to invent an answer in that case |
| **Generator** | Produce an answer using **only** retrieved ticket context; include ticket ID citations | Must not use unretrieved parametric knowledge as a substitute for tickets |
| **Retrieval configuration** | Supply `top-K` and similarity threshold | Mechanism (env, file, admin API, per-request) is an open question |

`Main.java` in the repo is a starter stub (R-8). It is not a component.

---

## 4. Trust and enforcement

```
Client  →  API boundary  →  Ticket application  →  State machine  →  Ticket store
                                                              ↘
                                                                 Ingestion refresher → Vector index

Client  →  API boundary  →  Retriever  →  Grounding gate  →  (no-match | Generator)  →  Response
```

| Rule | Where it is enforced |
|------|----------------------|
| Lifecycle legality | **State machine inside the backend**, on every status change. UI may hide illegal actions later; that is not sufficient (FR-6). |
| RAG grounding | **Grounding gate + generator inputs.** Generation is skipped on no-match. Generator input is retrieved ticket context only. |
| Citations | **Response assembly** after generation (or as part of a constrained generate step). Cited IDs are ticket identifiers (A-5). |
| Stale vectors | **Ingestion path** triggered by ticket data change, not by a separate optional client call as the sole mechanism. |

AuthN/AuthZ, tenancy, and PII controls are **unspecified** (open questions). Architecture does not add a security layer as a requirement.

---

## 5. Flows

### 5.1 Status change

1. Client requests a new status for a ticket.  
2. API validates the request shape (fields unspecified).  
3. Application loads current status from the ticket store.  
4. State machine evaluates `current → requested`.  
5. **If invalid:** backend rejects; store unchanged.  
6. **If valid:** new status persisted.  
7. If this change counts as ticket **data** change (open question A-6 / requirements §6), ingestion refreshes embeddings.

Allowed edges are only those in requirements §5. Unlisted edges, including skip and reverse, are rejected. Same-state transitions remain an **open question**.

### 5.2 Ticket data change (non-status or unspecified write)

Requirements specify embedding refresh when ticket data changes, not the full set of write APIs.

Whenever a specified write that changes ticket data succeeds:

1. Ticket store is updated.  
2. Embeddings for that ticket are refreshed so later retrieval is not stale (FR-11, ING-A-1).

Create, field update, delete, and status-only vs content-only refresh are **open questions**. Failure coupling (ticket commit vs embedding commit) is **unspecified** (risk R-3).

### 5.3 RAG query

1. Client submits a question/query (API-2). Shape deferred to RAG API contract.  
2. Retriever embeds the query, requests neighbors with configured **top-K**, drops results below the **similarity threshold**.  
3. Grounding gate:  
   - **No remaining tickets** → explicit no-match; **do not generate** a ticket-based answer.  
   - **Otherwise** → pass only those ticket contexts to the generator.  
4. Generator answers from that context only and cites ticket IDs.  
5. API returns either the grounded cited answer or the no-match result.

Transport (HTTP status for no-match) is unspecified.

---

## 6. Configuration

| Parameter | Required by | Architectural note |
|-----------|-------------|-------------------|
| `top-K` | FR-12 | Consumed by the retriever. Not a generator constant. |
| Similarity threshold | FR-13 | Consumed by the retriever / gate. Defines “relevant.” |

Defaults, metric (cosine vs other), order of K vs threshold, and config surface are **open questions**. Architecture only requires that both values are externalizable and actually affect retrieval (T-6).

---

## 7. Deployment and process model

| Topic | Status |
|-------|--------|
| Single Spring Boot app vs multiple services | **Unspecified.** Observed repo is one backend module. **Assumption ARCH-A-1:** start as one deployable unless a later spec splits it. |
| Embedding refresh in-process vs async worker | **Unspecified** (open question). Architecture requires a defined hook after ticket data change, not a particular queue. |
| Frontend | Directory exists; stack and hosting unspecified. Architecture treats UI as an HTTP client of the backend. |
| Secrets for LLM/embedding APIs | Unspecified; if used, must not be hardcoded (engineering constraint, not a product NFR). |

---

## 8. Technology mapping (observed vs chosen)

| Layer | Observed | Architecture stance |
|-------|----------|---------------------|
| Language / runtime | Java 17, Spring Boot 3.5.6 | Use this backend |
| HTTP | `spring-boot-starter-web` | API boundary |
| Validation | `spring-boot-starter-validation` | API-boundary validation; **does not replace** state machine |
| Persistence | Spring Data JPA + PostgreSQL driver | Ticket store |
| Tests | `spring-boot-starter-test` | Backend verification of T-1–T-7 later |
| Vector store / embeddings / LLM | **Not in `pom.xml`** | Required capabilities; **do not invent a vendor here** |

---

## 9. Quality attributes

No SLAs were specified. Architecturally implied properties (not numeric targets):

- **Correctness of workflow:** illegal transitions never persist.  
- **Grounding:** no generated ticket answer without retrieval that passed the threshold.  
- **Traceability:** successful answers carry ticket IDs.  
- **Index freshness:** data changes drive embedding refresh.

Latency, HA, and scale remain open questions.

---

## 10. Assumptions

| ID | Assumption |
|----|------------|
| ARCH-A-1 | Ticket management and RAG run in one backend application unless a later spec splits them. |
| ARCH-A-2 | Clients talk to the backend over HTTP/JSON (NFR-A-2). |
| ARCH-A-3 | PostgreSQL holds tickets (NFR-A-1). The vector index may be the same database or another store; **not decided**. |
| ARCH-A-4 | The grounding gate runs in the backend so clients cannot bypass no-match by calling an LLM directly through this system. |
| ARCH-A-5 | Embedding refresh is an internal pipeline, not a substitute for ticket persistence. |

---

## 11. Open questions (architecture)

- Vector index: pgvector in PostgreSQL vs dedicated store.  
- Embedding and chat model: which APIs, on-prem vs cloud, data residency.  
- Sync vs async refresh; what the client sees if refresh fails.  
- Whether generation is an LLM call or a strictly extractive assembler.  
- Prompt/tool constraints to make FR-8 enforceable (AI-R-1).  
- Authn, multi-tenancy, network topology.  
- Config mechanism for K and threshold.  
- Whether cancelled/closed tickets are indexed (corpus eligibility).

---

## 12. Out of scope for this document

Do not treat the following as architecture decisions — they belong in later specs:

| Later spec | What it must decide |
|------------|---------------------|
| Data model | Ticket attributes, IDs, status storage, embedding records |
| API contract | Ticket HTTP resources, transition payload, error codes |
| State machine | Formal transition table, initial state, same-state, concurrency |
| RAG ingestion | Fields indexed, chunking, refresh triggers, delete/cancel |
| RAG API contract | Query/response JSON, no-match body, citation shape |
| Evaluation strategy | How grounding and retrieval quality are measured |
| UI flow | Screens and client-side behavior |
| Test strategy | Test layers and fixtures |

---

## 13. Traceability

| Requirement IDs | Architectural element |
|-----------------|------------------------|
| FR-2–FR-6, SM-1, VAL-1 | State machine on ticket write path |
| FR-7–FR-10, RET-1, RET-4, RET-5, VAL-2 | Retriever → grounding gate → generator / no-match |
| FR-11, ING-1, ING-2 | Ingestion refresher after ticket data change |
| FR-12, FR-13, RET-2, RET-3 | Retrieval configuration |
| API-1, API-2, API-3 | API boundary + backend pipelines |
| T-1–T-7 | Testable at component/flow boundaries above |
