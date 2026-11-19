# RAG & Vector Store Rules

**Status:** Implementation rules for ingestion, retrieval, and PGVector.  
**Specification:** `spec/rag-ingestion.md`, `spec/rag-api-contract.md`  
**Architecture:** `spec/architecture.md`

---

## 1. Vector store

| Rule | Detail |
|------|--------|
| **RULE-RAG-1** | Use **PGVector in the same PostgreSQL instance** as tickets (ING-D-10) |
| **RULE-RAG-2** | Do not introduce a separate vector DB without spec change |
| **RULE-RAG-3** | Cosine distance metric; pgvector operator `<=>` (ING-D-12) |
| **RULE-RAG-4** | Similarity for threshold: `s = 1 - distance` (ING-A-6) — verify against pgvector version at implementation |
| **RULE-RAG-5** | HNSW index with cosine ops (ING-D-11) |

---

## 2. Configuration

| Property prefix | Purpose |
|-----------------|---------|
| `rag.retrieval.top-k` | Max neighbors before threshold (FR-12) |
| `rag.retrieval.similarity-threshold` | Minimum similarity 0.0–1.0 (FR-13) |
| `rag.embedding.*` | Provider, model, dimension, URL (ING-D-9) |
| `rag.chunk.*` | Chunker version, target size, overlap (ING-D-6) |

| Rule | Detail |
|------|--------|
| **RULE-RAG-6** | Bind via `@ConfigurationProperties`; validate at startup |
| **RULE-RAG-7** | **Forbidden:** literals `5`, `0.72`, or other policy values in `RagRetrievalService` |
| **RULE-RAG-8** | Defaults: `top-k: 5`, `similarity-threshold: 0.72` in `application.yaml` only |
| **RULE-RAG-9** | Retrieval order: **K first, then threshold** (ING-D-16) |

---

## 3. Embeddings

| Rule | Detail |
|------|--------|
| **RULE-RAG-10** | Development: local **Ollama** embedding model (ING-D-8) |
| **RULE-RAG-11** | Production: may use cloud embedding API (ING-D-21) — requires privacy review |
| **RULE-RAG-12** | Same model and dimension for ingest and query |
| **RULE-RAG-13** | Do not assume dev vectors portable to prod if model changes |
| **RULE-RAG-14** | Embedding failures: log; do not roll back ticket OLTP commit (ING-D-2, §9) |

---

## 4. Ingestion

| Rule | Detail |
|------|--------|
| **RULE-RAG-15** | Ingest after successful ticket/comment persist — internal only (ING-D-1) |
| **RULE-RAG-16** | Payload fields: `title`, `description`, `comments`, `resolutionNotes` (ING-D-3) |
| **RULE-RAG-17** | Metadata on every chunk: `ticketId`, `status`, `priority`, `assignee`, `category` (ING-D-4) |
| **RULE-RAG-18** | Chunking: section-based with fixed-size overflow (ING-D-5) |
| **RULE-RAG-19** | Prefix every chunk: `[Ticket {ticketId}] {title}` (ING-D-7) |
| **RULE-RAG-20** | Replace vectors by `ticketId` in **one PostgreSQL transaction** (ING-D-15) |
| **RULE-RAG-21** | No usable content → delete existing vectors for `ticketId` |
| **RULE-RAG-22** | Never cite internal `Ticket.id` — only `ticketId` (DM-A-2) |

---

## 5. Retrieval & grounding

| Rule | Detail |
|------|--------|
| **RULE-RAG-23** | Empty keep-set → **do not call generator**; return `NO_MATCH` (FR-10) |
| **RULE-RAG-24** | Generator input = retrieved chunks only (FR-8) |
| **RULE-RAG-25** | Enforce §8.1 grounding instructions from `rag-api-contract.md` in prompt |
| **RULE-RAG-26** | Validate `citations[].ticketId` ⊆ keep-set before `ANSWER` (RAG-API-D-12) |
| **RULE-RAG-27** | Run `RagOutputReviewer` for post-generation conflict checks where applicable |
| **RULE-RAG-28** | Insufficient grounding → `NO_MATCH`, not guessed `ANSWER` (RAG-API-D-13) |

---

## 6. Ports and testability

| Rule | Detail |
|------|--------|
| **RULE-RAG-29** | `VectorSearchPort`, `EmbeddingClient`, generator behind interfaces |
| **RULE-RAG-30** | Test profile uses `InMemoryTicketVectorStore` as `VectorSearchPort`; production uses `PgVectorTicketVectorStore` — no no-op search port |
| **RULE-RAG-31** | Mock ports in unit tests; real PGVector in integration tests |

---

## 7. Observability

| Rule | Detail |
|------|--------|
| **RULE-RAG-32** | Log at debug: `topK`, threshold, raw hit count, kept count (ING-D-20) |
| **RULE-RAG-33** | Log ingestion failures with `ticketId`; no embedding vectors in logs |
| **RULE-RAG-34** | Do not log full ticket bodies at INFO in production |

---

## 8. Security

| Rule | Detail |
|------|--------|
| **RULE-RAG-35** | Treat ticket text as untrusted input to the LLM (prompt injection risk AI-R-7) |
| **RULE-RAG-36** | No API keys in code, YAML committed secrets, or logs |
| **RULE-RAG-37** | Client cannot supply retrieval context or override keep-set |

---

## 9. Open decisions

* Corpus eligibility by `status` (DM-Q-10)  
* Cloud vs Ollama in production  
* Metadata filters at retrieval time  

Do not implement filters not in spec without amendment.

---

## 10. References

* `spec/rag-ingestion.md`  
* `spec/rag-api-contract.md`  
* `commands/review-ai.md`  
* `commands/review-rag-output.md`  
