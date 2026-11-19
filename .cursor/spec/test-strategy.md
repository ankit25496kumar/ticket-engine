# Test Strategy

**Status:** Test design only. No implementation.  
**Depends on:** `spec/requirements.md`, `spec/state-machine.md`, `spec/api-contract.md`, `spec/rag-api-contract.md`, `spec/rag-ingestion.md`, `rules/testing.md`

---

## 1. Purpose

Define how to verify specified behavior (T-1–T-7) across layers without inventing product requirements.

**Principle:** Test **behavior** at the right boundary — state machine as pure functions, APIs via HTTP contracts, RAG via grounded outcomes — not implementation trivia.

---

## 2. Test layers

| Layer | Scope | Tools | Runs in CI |
|-------|--------|-------|------------|
| **Unit** | Pure domain logic, validators, config binding | JUnit 5, AssertJ, Mockito | Yes |
| **Slice / Web** | Controllers + JSON + validation | `@WebMvcTest`, MockMvc | Yes |
| **Integration** | JPA, PostgreSQL, PGVector, ingestion | `@SpringBootTest`, Testcontainers | Yes (where feasible) |
| **Contract** | API + RAG JSON shapes | MockMvc / REST Assured | Yes |
| **RAG pipeline** | Retrieve → gate → generate (mocked LLM) | Spring test context | Yes |
| **AI eval regression** | Grounding conflict fixtures | `RagOutputReviewer` tests | Yes |
| **E2E** | Full stack + optional real LLM | Manual / nightly | Optional |

---

## 3. Coverage by requirement

| ID | Behavior | Primary tests |
|----|----------|---------------|
| **T-1** | Allowed transitions succeed | State machine unit (25 matrix cells) + service test |
| **T-2** | Illegal transitions rejected | State machine unit + API 400 + no persist |
| **T-3** | RAG answers cite ticket IDs | RAG contract + generator assembly test |
| **T-4** | No-match when nothing retrieved | RAG contract HTTP 200 + `outcome: NO_MATCH` |
| **T-5** | Embeddings refresh after data change | Integration: update ticket → query retrieves new text |
| **T-6** | `top-k` and threshold configurable | `RagRetrievalProperties` + service tests; no literals in service |
| **T-7** | No parametric answers | Grounding gate skips generation; `RagOutputReviewer` conflict tests |

---

## 4. Unit tests (must have)

### 4.1 State machine (`state-machine.md` §5.1)

* Table-driven 5×5 transition matrix  
* Same-state rejection for all five statuses  
* No Spring context

### 4.2 Configuration

* `RagRetrievalProperties` binds `rag.retrieval.*`  
* Invalid `top-k` / threshold fail startup  
* **CFG-T-3:** no literals `5` / `0.72` in `RagRetrievalService`

### 4.3 Grounding review

* `RagAnswerGroundingReviewer` / `RagOutputReviewer`  
* Password-reset regression: wrong duration → `FAIL`; correct → `PASS`  
* See `commands/review-rag-output.md`

---

## 5. Application service tests

* `TicketService.changeStatus`: policy enforced; `save` not called on reject  
* Create ticket → `status == OPEN`  
* Mock repositories; **real** transition policy

---

## 6. API tests

When controllers exist:

| Area | Examples |
|------|----------|
| Tickets | Create 201, get 404, list filters |
| Status | Legal transition 200; `OPEN→CLOSED` 400 `INVALID_STATUS_TRANSITION` |
| Comments | 201 on ticket; 404 on missing ticket |
| RAG | `POST /api/v1/rag/query` — `ANSWER`, `NO_MATCH`, `ERROR` shapes |

Use `api-contract.md` and `rag-api-contract.md` as oracles.

---

## 7. Integration tests

### 7.1 Persistence

* Testcontainers PostgreSQL  
* FK `Comment.ticketId` → `Ticket.ticketId`  
* Unique `ticketId`

### 7.2 Vector store / ingestion (`rag-ingestion.md` §13)

* Ingest ticket → rows in `ticket_chunk` (or equivalent)  
* Update description → old chunks replaced  
* Re-ingest atomicity (ING-D-15)  
* Empty document → vectors deleted for `ticketId`

### 7.3 RAG retrieval

* Known embeddings fixture → top-K then threshold  
* Empty corpus → empty keep-set

**TEST-A-1:** Mock `EmbeddingClient` and LLM in CI; use fixed vectors for deterministic retrieval tests.

---

## 8. AI / RAG-specific testing

| Concern | Approach |
|---------|----------|
| LLM non-determinism | Mock generator in CI; return fixed JSON |
| Embedding cost | Mock or local Ollama in dev; stub in CI |
| Hallucination detection | `PasswordResetRagScenarioTest` + future golden fixtures |
| Citation validation | Unit test: cite id not in keep-set → `NO_MATCH` or error |
| Invalid LLM JSON | Parser test → `ERROR` outcome |

Do **not** weaken production validation to make tests pass.

---

## 9. Test data

| Fixture | Use |
|---------|-----|
| `PasswordResetKnowledgeFixture` | Grounding regression |
| Ticket builders | API + ingestion tests |
| Precomputed embeddings | Retrieval threshold tests |

Keep intentional wrong answers in **test** packages only.

---

## 10. CI expectations

**Minimum CI gate:**

1. All unit + slice tests pass  
2. Integration tests pass (or tagged `@Tag("integration")` on default branch)  
3. RAG contract tests pass  
4. Grounding regression tests pass  

**Open (`TEST-Q-1`):** Whether Testcontainers runs on every PR or only main.

---

## 11. Commands

| Command | When |
|---------|------|
| `commands/generate-tests.md` | Adding features; fill gaps from this strategy |
| `commands/review-code.md` | PR review against specs + rules |
| `commands/review-ai.md` | AI/RAG implementation review |
| `commands/review-rag-output.md` | Per-answer grounding review |

---

## 12. Assumptions

| ID | Assumption |
|----|------------|
| TEST-A-1 | CI uses mocked embeddings/LLM for determinism |
| TEST-A-2 | `spring-boot-starter-test` is the test stack |
| TEST-A-3 | Testcontainers used for PostgreSQL + pgvector when integration tests run |

---

## 13. Open questions

| ID | Question |
|----|----------|
| TEST-Q-1 | Integration test scope in CI |
| TEST-Q-2 | Frontend test strategy (no UI spec beyond `ui-flow.md`) |
| TEST-Q-3 | Nightly job with real Ollama |

---

## 14. Traceability

| Source | This strategy |
|--------|---------------|
| T-1 – T-7 | §3 |
| `state-machine.md` §5 | §4.1, §5 |
| `rag-ingestion.md` §13 | §7.2 |
| `rag-api-contract.md` §10 | §6, §8 |
| `evaluation-strategy.md` | §8 AI regression |
