# RAG Ingestion

**Status:** Technical specification only. No Java/application code.  
**Depends on:** `spec/requirements.md`, `spec/architecture.md`, `spec/data-model.md`  
**Also uses:** this design brief (payload fields, metadata fields, refresh on update and on close).  
**Does not decide:** RAG HTTP API JSON, LLM generation prompts, UI.  
**State machine:** ingestion runs **after** a successful ticket persist (including legal status change to `CLOSED`). Illegal transitions never ingest.

This document is the contract for implementing ingestion, chunking, embeddings, PGVector persistence, and retrieval knobs. Implementers must not pick a different vector store, embedding host, chunking family, or metadata set without a spec change.

---

## 0. Classification of statements

| Kind | Meaning |
|------|---------|
| **Confirmed** | From `requirements.md` / architecture / data model / this brief’s explicit lists |
| **Decision (`ING-D-*`)** | Chosen here so implementation is not blocked |
| **Assumption (`ING-A-*`)** | Needed to complete the pipeline; not a product requirement |
| **Open (`ING-Q-*`)** | Still unspecified; do not invent product behavior |

---

## 1. RAG ingestion flow

Ingestion turns one ticket (plus its comments) into vector rows and keeps those rows aligned with PostgreSQL when the ticket changes.

```
Ticket persist succeeds (create/update/close)
        │
        ▼
Load Ticket + Comments (ordered by createdAt)
        │
        ▼
Build knowledge document (plain text, §2)
        │
        ├── no usable content? → delete existing vectors for ticketId; stop (§9)
        │
        ▼
Build metadata snapshot (§3) — same object on every chunk
        │
        ▼
Chunk document (§4)
        │
        ▼
Embed each chunk (§5) — same model/dimension for all rows
        │
        ▼
Replace vectors for this ticketId (§7, §10) — PGVector (§6)
```

| Step | Input | Output |
|------|--------|--------|
| Ticket data | `Ticket` + `Comment[]` from PostgreSQL | Canonical field values |
| Knowledge document | title, description, comments, resolution notes | One UTF-8 string |
| Metadata | ticket snapshot | Map in §3 |
| Chunking | document + ticketId + title for prefixes | Ordered chunks `0..n-1` |
| Embedding | each chunk text | `float[]` of fixed dimension |
| Persistence | chunks + embeddings + metadata | PGVector rows for that `ticketId` only |

**ING-D-1:** Ingestion is an **internal backend pipeline**, invoked by the ticket application service after a successful persist. Clients cannot ingest as the sole sync mechanism (architecture).

**ING-D-2:** Run ingestion **synchronously after commit** of the ticket (and comments) transaction so a later RAG query in the same process does not read pre-update vectors. Ticket OLTP commit is **not** rolled back if ingestion fails (§9).

Logical components (single Spring Boot app, ARCH-A-1): `TicketIngestionService` orchestrates; `KnowledgeDocumentBuilder`; `Chunker`; `EmbeddingClient`; `TicketVectorStore` (PGVector).

---

## 2. Knowledge document (exactly what is indexed)

**Confirmed (this brief):** searchable knowledge is built from:

| Ticket / comment field | In document? | Role |
|------------------------|--------------|------|
| `title` | Yes | Payload |
| `description` | Yes | Payload |
| `comments` (`author` + `content`, chronological) | Yes | Payload |
| `resolutionNotes` | Yes | Payload |
| `ticketId` | Prefix on every **chunk** (§4.5), not a substitute for metadata | Context preservation |
| `status`, `priority`, `assignee`, `category` | **Metadata only** (§3), not body text | Filters / citation support |
| `id` (internal PK) | No | — |
| `createdAt` / `updatedAt` | No | — |

**ING-D-3:** Document template (omit a section if that field is null/blank after trim):

```
Ticket: {ticketId}
Title: {title}

Description:
{description}

Comments:
[{createdAt}] {author}: {content}
[{createdAt}] {author}: {content}
...

Resolution notes:
{resolutionNotes}
```

Comment lines use `Comment.createdAt` ascending. If there are no comments, omit the `Comments:` heading. If `resolutionNotes` is blank, omit that section.

**Usable content:** at least one of `title`, `description`, any comment `content`, or `resolutionNotes` is non-blank after trim. Otherwise §9 (no usable content).

This closes data-model DM-Q-8 / DM-Q-9 for **ingestion payload**: comments and resolution notes **are** included.

---

## 3. Metadata stored with every chunk

**Confirmed (this brief):** every chunk row **must** store:

| Field | Source | Notes |
|-------|--------|--------|
| `ticketId` | `Ticket.ticketId` | Citation id (`TKT-1001`). FR-9, DM-A-2, DM-D-2 |
| `status` | `Ticket.status` | Enum names, e.g. `CLOSED` |
| `priority` | `Ticket.priority` | `LOW` / `MEDIUM` / `HIGH` |
| `assignee` | `Ticket.assignee` | Stored as on the ticket (string; no User entity) |
| `category` | `Ticket.category` | As on the ticket |

This **extends** data-model DM-D-7: `assignee` **is** required metadata here (the earlier data-model note to omit assignee is superseded for ingestion). `title` and `updatedAt` are **not** required metadata in this spec (title remains in the payload).

**ING-D-4:** Also persist on each row (implementation, not product fields):

| Field | Purpose |
|-------|---------|
| `chunkIndex` | `0..n-1` order |
| `content` / `source_text` | Chunk string that was embedded |
| `contentFingerprint` | Hash of (document text + metadata canonical form + chunker version + embedding model id) |
| `embedding` | Vector |
| `ingestedAt` | Server time of this replace |

All chunks of one ingest share the **same** metadata snapshot (status/priority/assignee/category/ticketId). They differ in `chunkIndex`, `content`, and `embedding`.

Do not store internal `Ticket.id` as citation metadata.

---

## 4. Chunking strategy

### 4.1 Comparison

| Strategy | How | Fit for support tickets |
|----------|-----|-------------------------|
| **Paragraph / section-based** | Split on document structure (title+description, comments, resolution) | Tickets already have sections. Keeps a comment or resolution intact. Weak if one description is huge. |
| **Fixed-size** | N tokens/characters + overlap, ignore structure | Predictable size. Can split mid-sentence or tear a comment in half. |
| **Semantic** | Extra model to split at topic boundaries | Better for long unstructured articles. Overkill for short tickets; extra latency/cost; another failure mode. |

### 4.2 Recommendation

**ING-D-5:** Use **section-based chunking with fixed-size overflow**.

1. Build the document (§2).  
2. If the full document (plus context prefix) is **≤ target size**, emit **one chunk** (common for small tickets).  
3. Otherwise split into sections: (A) title+description block, (B) **each** comment line as its own section, (C) resolution-notes block.  
4. If a **section** is still **> target size**, split that section with **fixed-size windows and overlap** (§4.4).  
5. Never use semantic chunking in v1.

**Why:** Support tickets are short, structured records, not books. Section boundaries match how agents think (problem vs comment vs resolution). Semantic splitting adds an embedding/LLM call per ticket without a specified quality target. Pure fixed-size would break comments arbitrarily when a single-chunk ticket would have been enough.

### 4.3 Target chunk size and overlap

| Parameter | Value | Kind |
|-----------|--------|------|
| Target chunk size | **512 tokens** (encode with the same tokenizer as the embedding model if available; else **~2000 characters** as a stand-in) | `ING-D-6` |
| Overlap | **64 tokens** (~**250 characters** if using char heuristic) | `ING-D-6` |
| Chunker version | Config string, e.g. `section-v1`, included in fingerprint | `ING-D-6` |

Put size/overlap in configuration (`rag.chunk.max-tokens`, `rag.chunk.overlap-tokens`) **as well**, so they are not magic numbers in code. Defaults = table above.

### 4.4 Section too large

1. Split into windows of `max-tokens` with `overlap-tokens`.  
2. Windows stay ordered; `chunkIndex` increases.  
3. Do not drop the overflow.  
4. Apply the context prefix (§4.5) to **each** window **after** splitting so overlap is computed on section body, not double-counted prefix (prefix is added per chunk and may make the stored string slightly larger than the body window).

### 4.5 Preserve ticket context across chunks

**ING-D-7:** Every chunk **starts** with:

```
[Ticket {ticketId}] {title}
```

then a newline, then the section/window body.

Retrieval and generation always see which ticket the fragment belongs to, even if the generator only receives a subset of chunks. Citations still use metadata `ticketId`, not this prefix alone.

---

## 5. Embedding model

### 5.1 Comparison

| | **Local (Ollama)** | **Cloud embedding API** |
|--|--------------------|-------------------------|
| **Quality** | Good for support-ticket English with a current embedding model (e.g. `nomic-embed-text`). Cloud SOTA models may score slightly higher on mixed-domain benchmarks. No quality SLA is specified for this project. | Often stronger multilingual/general benchmarks; vendor-dependent. |
| **Latency** | Depends on local GPU/CPU. First-load cold start. Fine for ingest-on-write at moderate volume (unspecified scale). | Usually low and stable; network RTT. |
| **Cost** | Hardware/electricity; no per-token invoice. | Per-token/per-request; grows with re-ingest on every update/close. |
| **Privacy** | Ticket text (title, description, comments, resolution notes, assignee in metadata) **stays on the operator’s machine**. Aligns with unspecified but material PII risk (requirements AI-R-8). | Ticket bodies leave the boundary; DPA/residency **not specified** — high compliance risk if used without a later security spec. |
| **Operational complexity** | Run Ollama + pull model; Spring calls HTTP localhost. Model/dimension pinned in config. | API key, quotas, outages, data-retention policies. Simpler if ops already forbid local GPUs. |

### 5.2 Environment strategy

**Requirements:** None. Embedding provider is not specified in `requirements.md` (open question in NFR §2).

**ING-D-8 (development):** Use a **local Ollama** embedding model.

| Reason | Note |
|--------|------|
| No API cost during development | No per-token billing while iterating |
| Easy experimentation | Swap models locally without cloud accounts |
| Keeps ticket data local | Title, description, comments, resolution notes stay on the developer machine (mitigates AI-R-8 in dev) |

Default dev model: **`nomic-embed-text`** (dimension **768** — verify at startup with a probe embedding; fail fast on mismatch).

**ING-D-21 (production alternative):** Production **may** use a **cloud embedding API** instead of Ollama if higher retrieval quality or operational simplicity (managed service, no local GPU) is required. This is optional, not mandatory. Choosing cloud in production requires a separate security/privacy review (ticket PII leaves the boundary; not specified in requirements).

| Environment | Default provider | Override |
|-------------|------------------|----------|
| `dev` / `local` | `ollama` | `application-dev.yml` |
| `prod` | Configurable: `ollama` **or** `cloud` | `application-prod.yml` / env vars |

**ING-D-9:** Provider, model name, base URL / API endpoint, API key (cloud only), and dimension are **configuration** — not hardcoded in service logic.

```yaml
# application-dev.yml (default development)
rag:
  embedding:
    provider: ollama
    base-url: http://localhost:11434
    model: nomic-embed-text
    dimension: 768

# application-prod.yml (example — cloud is optional)
rag:
  embedding:
    provider: cloud          # or ollama if ops runs Ollama in prod
    model: <vendor-model>    # chosen at deploy time; not fixed in code
    dimension: <must-match-model>
    # api-key via env / secret store — never in repo
```

Within a **single deployed environment**, ingest and query **must** use the **same** provider, model, and dimension. Mixing models in one PGVector corpus without re-indexing produces invalid retrieval.

Changing provider or model in an environment requires **full re-index** of all tickets (`ING-Q-1`). Dev and prod vector data must not be copied between environments if embedding models differ.

**Do not** assume dev Ollama vectors are portable to prod cloud embeddings.

---

## 6. Vector store

### 6.1 Comparison and recommendation

| | **PGVector** (PostgreSQL `vector`) | **Chroma** |
|--|-----------------------------------|------------|
| Ops | Same database as tickets (`pom.xml` PostgreSQL, NFR-A-1, ARCH-A-3) | Separate process/volume |
| Metadata | SQL columns + filter `WHERE` | Collections + metadata maps |
| Transactions with tickets | Same DB; ingest still **after** OLTP commit (`ING-D-2`) | Cross-store sync is harder |
| Similarity | `ORDER BY embedding <=> :q` (cosine) + `LIMIT` | Built-in ANN query |

**ING-D-10:** Use **PGVector in the same PostgreSQL instance** as tickets.

**Why:** One backing store, metadata as real columns (`ticketId`, `status`, …), delete-by-`ticketId` is a SQL `DELETE`, fits JPA/JDBC already on the classpath. Chroma would add a second system without a specified need.

### 6.2 How embeddings and metadata are stored

Logical table `ticket_chunk` (name may be snake_case entity `TicketChunk`):

| Column | Type (logical) |
|--------|----------------|
| `id` | UUID PK (or bigserial) — chunk row id, **not** cited |
| `ticket_id` | `VARCHAR` unique-with-index **not** unique alone; indexed; value = `TKT-n` |
| `chunk_index` | `INT` |
| `content` | `TEXT` (prefixed chunk) |
| `status` | `VARCHAR` / ticket-status enum string |
| `priority` | `VARCHAR` / priority enum string |
| `assignee` | `VARCHAR` nullable |
| `category` | `VARCHAR` nullable |
| `content_fingerprint` | `VARCHAR` |
| `ingested_at` | timestamptz |
| `embedding` | `vector(dimension)` with dimension = config |

Constraints:

- Index on `ticket_id` (delete/replace and retrieval grouping).  
- Unique `(ticket_id, chunk_index)` **after** a successful ingest (no duplicate slots).  
- ANN index on `embedding` (HNSW or IVFFlat; **ING-D-11:** HNSW cosine).

Flyway/Liquibase is unspecified; implementers may use Hibernate `ddl-auto` only in local/dev (`ING-A-1`). Production migration tool is `ING-Q-2`.

### 6.3 Similarity search (ingest-adjacent; used at retrieval)

**Confirmed:** top-K and similarity threshold are configurable (FR-12, FR-13).

**ING-D-12:** Metric = **cosine distance**. pgvector: `embedding <=> query_embedding`. Convert to similarity `s = 1 - distance` (for cosine distance in pgvector this matches the usual “higher is closer” gate).

Retrieval algorithm (for RAG API later):

1. Embed the query with the **same** Ollama model.  
2. `SELECT ... ORDER BY embedding <=> :q LIMIT :topK`.  
3. Keep rows with `similarity >= threshold`.  
4. If none remain → **no-match** (FR-10); do **not** generate from empty context.  
5. Remaining `content` + `ticketId` metadata = retrieved ticket context (FR-8, FR-9).

Do not implement the RAG HTTP API in this document.

---

## 7. Re-ingestion / update strategy

### 7.1 When embeddings must be refreshed

**Confirmed:**

| Trigger | Source |
|---------|--------|
| Ticket **data** changes | FR-11, ING-2 |
| Ticket is **updated** | This brief |
| Ticket is **closed** (`status` → `CLOSED`) | This brief |

**ING-D-13:** Invoke full replace-ingest (§7.2) when **any** of these succeed in the ticket service:

| Event | Refresh? |
|-------|----------|
| Ticket **created** | Yes (`ING-A-2`: create is the first update of empty → data) |
| Ticket fields updated (`title`, `description`, `resolutionNotes`, `priority`, `assignee`, `category`, or other persisted ticket columns) | Yes (“ticket is updated”) |
| Comment **created** on the ticket | Yes (comments are payload) |
| Legal transition to **`CLOSED`** | Yes (explicit) |
| Any other **legal** status change (`OPEN→IN_PROGRESS`, cancel, etc.) | Yes (`ING-A-3`: status is chunk metadata; leaving old `status` on vectors would be stale metadata) |
| Illegal transition rejected | **No** (no persist) |
| Comment-only read | No |

If comment **update/delete** is added later (today unspecified), that must also re-ingest (`ING-Q-3`).

### 7.2 Stale chunk handling

**ING-D-14:** **Delete-then-insert by `ticketId`** in one vector-store unit of work:

1. `DELETE FROM ticket_chunk WHERE ticket_id = :ticketId`  
2. `INSERT` new chunks (or insert nothing if no usable content).  
3. Commit vector writes.

No in-place patch of individual old embeddings. No “add chunks” without delete. After success, **zero** rows for that ticket may contain previous `content` or old metadata.

If step 2 fails after step 1, the ticket has **no** vectors until retry (§9) — prefer **insert new rows with a batch then delete leftovers by fingerprint/generation**, or wrap 1–2 in a **single DB transaction** so a failure leaves the **previous** chunks intact (**ING-D-15:** **replace in one PostgreSQL transaction**: delete + insert atomic. On failure, old chunks remain. On success, only new chunks remain.)

**ING-D-15** wins over a naive delete-first without a transaction.

---

## 8. Retrieval configuration (Spring Boot)

**Confirmed (requirements):** `top-K` and similarity threshold must be configurable (FR-12, FR-13, RET-2, RET-3). They must **not** be hardcoded in the retrieval service. Configurable values must actually affect retrieval (T-6).

**Scope:** Global, deployment-level settings for v1. Per-request override is **not** specified (`ING-Q-4`). `spec/rag-api-contract.md` is empty; this section is the retrieval-knob contract until that spec is written.

### 8.1 Configuration properties

**ING-D-22:** Bind retrieval settings with Spring Boot `@ConfigurationProperties` under prefix **`rag.retrieval`**.

| Property (YAML) | Java field | Type | Purpose |
|-----------------|------------|------|---------|
| `rag.retrieval.top-k` | `topK` | `int` | Maximum neighbors fetched from PGVector before threshold filter |
| `rag.retrieval.similarity-threshold` | `similarityThreshold` | `double` | Minimum **similarity score** (0.0–1.0) to keep a neighbor |

Logical class name: `RagRetrievalProperties` (package unspecified). Enable with `@EnableConfigurationProperties` or `@ConfigurationPropertiesScan`. Validate at startup with Bean Validation (`spring-boot-starter-validation` is on the classpath per NFR-OBS-2).

**Forbidden:** literals `5`, `0.72`, or any other policy values inside `RagRetrievalService` (or equivalent). The service reads `RagRetrievalProperties` only.

### 8.2 Recommended `application.yaml` structure

Keep retrieval knobs under `rag.retrieval`; do not mix with chunk or embedding settings.

```yaml
rag:
  retrieval:
    top-k: 10
    similarity-threshold: 0.72
  chunk:
    max-tokens: 512
    overlap-tokens: 64
  embedding:
    provider: ollama          # dev default; prod may override (ING-D-8, ING-D-21)
    base-url: http://localhost:11434
    model: nomic-embed-text
    dimension: 768
```

Profile overrides without Java changes:

```yaml
# application-prod.yml
rag:
  retrieval:
    top-k: 8
    similarity-threshold: 0.80
```

Environment variables (Spring relaxed binding):

| Env var | Maps to |
|---------|---------|
| `RAG_RETRIEVAL_TOP_K` | `rag.retrieval.top-k` |
| `RAG_RETRIEVAL_SIMILARITY_THRESHOLD` | `rag.retrieval.similarity-threshold` |

Restart the application (or use Spring Cloud refresh only if that stack is adopted later — **not** assumed now) to apply changes.

### 8.3 Injection into the retrieval service

**ING-D-23:** `RagRetrievalService` (or `TicketChunkRetriever`) receives `RagRetrievalProperties` by **constructor injection**. It does not read `Environment` directly and does not parse YAML.

```
RagRetrievalProperties  →  RagRetrievalService.retrieve(query)
         ↑
@ConfigurationProperties("rag.retrieval") + @Validated
```

The RAG API layer (future) calls the retrieval service; it does not pass K/threshold from the HTTP request in v1.

### 8.4 Default values and rationale

Defaults are **design decisions**, not requirements (requirements §7 lists defaults as unspecified).

| Property | Default | Why reasonable |
|----------|---------|----------------|
| `top-k` | **5** | Small-to-medium ticket corpus in early deployments; limits latency and context size for generation; leaves headroom after threshold filter. Aligns with ING-D-16. |
| `similarity-threshold` | **0.72** | On cosine **similarity** (0–1), excludes weak neighbors while not requiring near-duplicate matches; supports RET-4 no-match when nothing is strong enough. Calibrated via real-Ollama evaluation (`evaluation-strategy.md` §4.3). Tunable via config without code (mitigates requirements R-4). |

Operators tune via YAML/env; no code deploy required for knob changes.

### 8.5 Validation rules

Validate on application startup (`@Validated` on the properties class). **Fail fast** if invalid.

| Property | Rule | On violation |
|----------|------|--------------|
| `topK` | **> 0** (integer) | Context fails to start; log clear message |
| `similarityThreshold` | **>= 0.0 and <= 1.0** | Context fails to start |

Assumption **ING-A-6:** threshold is compared against **similarity** in the 0–1 range (higher = more similar), not raw pgvector distance. See §8.7.

Optional upper bound on `topK` (e.g. <= 100) is **not** required by requirements; may be added as a guardrail in implementation.

### 8.6 Changing configuration without modifying business logic

| Mechanism | Changes retrieval behavior? | Java change? |
|-----------|----------------------------|--------------|
| `application.yaml` / `application-{profile}.yml` | Yes | No |
| Environment variables | Yes | No |
| External config server | Yes (if adopted) | No |
| Editing `RagRetrievalService` | — | **Not allowed** for K/threshold |

Business logic stays fixed: embed query → vector search → apply K → apply threshold → return chunks or empty (no-match path).

### 8.7 How the retrieval service uses these values

**ING-D-12** (unchanged): metric = **cosine distance**; pgvector operator `<=>`.

Algorithm for each RAG query:

1. Embed the query vector (same provider/model as ingest, §5).  
2. Query PGVector: order by cosine distance ascending, **`LIMIT topK`** from `RagRetrievalProperties`.  
3. For each row, compute **similarity** `s = 1 - distance` (cosine distance from pgvector).  
4. **Keep** rows where `s >= similarityThreshold`.  
5. If the keep-set is **empty** → grounding gate stops; **explicit no-match** (FR-10, RET-4, VAL-2). Do not call the generator.  
6. Otherwise pass kept chunks’ `content` and metadata `ticketId` to generation (FR-8, FR-9).

**Order:** **K first, then threshold** (ING-D-16). Requirements left order open; this is a fixed design decision.

Log at debug: `topK`, `similarityThreshold`, raw hit count, kept count (ING-D-20).

### 8.8 Testing configuration

| Test | Assert | Requirement |
|------|--------|-------------|
| **ING-T-12** | `@SpringBootTest` with `rag.retrieval.top-k=2` and a fixture corpus → at most 2 candidates before threshold; changing test `application.properties` changes behavior | T-6 |
| **ING-T-13** | Threshold set high (e.g. 0.99) → empty keep-set → no-match; generator not invoked | RET-4, T-4 |
| **CFG-T-1** | `top-k: 0` or negative → application context fails to start | Validation |
| **CFG-T-2** | `similarity-threshold: 1.5` or `-0.1` → context fails to start | Validation |
| **CFG-T-3** | `RagRetrievalService` has no literal `5` or `0.72` for policy (static review or arch unit test) | FR-12, FR-13 |

Use `@TestPropertySource` or `application-test.yml` for retrieval tests; do not hardcode overrides in test Java except via properties.

### 8.9 Assumptions (metric and vector store)

| ID | Assumption |
|----|------------|
| **ING-A-6** | `similarity-threshold` is on a **0.0–1.0 similarity scale** where higher is better; not on raw pgvector distance. |
| **ING-A-7** | PGVector cosine distance operator `<=>` is used; ANN index is HNSW with cosine ops (ING-D-11). |
| **ING-A-8** | Similarity conversion `s = 1 - distance` is valid for the chosen pgvector distance definition — verify against pgvector docs at implementation time. |
| **ING-A-9** | Threshold is applied **after** `LIMIT topK`, not as a SQL `WHERE` before limit (unless an equivalent semantic is proven). |
| **ING-A-10** | One global retrieval profile per deployment; no per-tenant K/threshold until specified. |

If the embedding provider or pgvector version uses a different distance scale, **update config docs and validation**, not scattered literals in the service.

### 8.10 Design decision summary

| ID | Decision |
|----|----------|
| **ING-D-16** | Property prefix `rag.retrieval.*`; defaults `top-k: 5`, `similarity-threshold: 0.72` |
| **ING-D-22** | `@ConfigurationProperties` + `@Validated` class `RagRetrievalProperties` |
| **ING-D-23** | Constructor-inject properties into `RagRetrievalService`; no hardcoded K/threshold in service code |

**ING-D-16** (legacy one-liner): superseded by §8.1–§8.10 for Spring Boot binding detail.

Threshold applies **after** top-K fetch (§6.3, §8.7).

---

## 9. Failure handling

| Situation | Behavior (`ING-D-17`) |
|-----------|------------------------|
| **Embedding generation fails** (Ollama down, cloud API error, timeout, non-200) | Ticket OLTP remains committed. Vector replace **does not commit**. Previous chunks remain (thanks to `ING-D-15`). Log error + metric. Surface **ingestion failed** to logs; do not fail the HTTP ticket API with 5xx **unless** a later API contract says otherwise (`ING-A-4`: ticket writes stay available). Retry: next update or a manual/admin re-ingest (`ING-Q-5`). |
| **Vector store unavailable** | Same as above: no partial replace; old vectors kept if transaction rolls back. Log + metric. |
| **Ticket has no usable content** | `DELETE` chunks for `ticketId` (atomic). Ticket row remains. Retrieval will not match this ticket. Log at info with `ticketId`. |
| **Re-ingestion fails** mid-batch | Transaction abort → **pre-replace** chunks still present (may be stale vs ticket — **ingest lag**). Metric `rag.ingest.failure`. Ticket data is source of truth; vectors may lag until retry. |
| **Empty embedding returned** | Treat as embedding failure; do not persist zero vectors as searchable. |

Do not invent a dead-letter table unless `ING-Q-5` is closed. Implementers may add an `ingestion_status` column on `Ticket` later; **not required** now (`ING-Q-6`).

---

## 10. Idempotency

Re-ingesting the same ticket must **not** accumulate duplicate or mixed-generation rows.

| Mechanism | Rule |
|-----------|------|
| Replace key | All vector rows for a ticket are keyed by `ticket_id` |
| Atomic replace | `ING-D-15` |
| Stable chunk ids (optional) | `id` may be new UUIDs each ingest; uniqueness is `(ticket_id, chunk_index)` after commit |
| Same content twice | Fingerprint unchanged still **replace** (simplest). Optional skip-if-fingerprint-match is allowed **only** if metadata also matches (`ING-D-18`: skip rewrite when fingerprint equal **and** row count equal — performance only, not required for correctness) |
| Concurrent two ingests | Last committed transaction wins; unique `(ticket_id, chunk_index)` prevents double rows in one generation |

**Correctness criterion:** after ingest completes successfully, `SELECT * FROM ticket_chunk WHERE ticket_id = ?` is exactly the new chunk set.

---

## 11. Security and data isolation

**Confirmed:** none (requirements: auth/tenancy unspecified).

**ING-D-19 (engineering, not a product NFR):**

- PGVector is **not** exposed to the internet; only the Spring backend speaks SQL.  
- Ollama is local; ticket payload is not sent to a cloud embedding API (`ING-D-8`).  
- Retrieval runs **in process** with the same grounding gate (architecture): no client-supplied “extra context”.  
- Metadata includes **assignee** and ticket text; treat logs as sensitive (do not log full `content` at info; `ticketId` only at info, content at debug).  
- **Authorization:** no role/tenant model exists. **ING-A-5:** v1 is **single-tenant**. Do not add fake tenant_id. When auth is specified, retrieval **must** filter chunks the caller may not see; until then, any caller of the future RAG API could retrieve any ingested ticket — that gap is `ING-Q-7`, not solved by PGVector RLS unless added later.

Corpus eligibility (exclude `CANCELLED`) remains **not specified** (DM-Q-10). **Do not** drop cancelled tickets from the index unless a later spec says so. Closed tickets **stay** indexed (refresh on close is required).

---

## 12. Observability

**ING-D-20:** Log/metric names (implement with Micrometer + structured logs). No PII in metric tags except `ticketId` where useful.

| Area | Logs | Metrics |
|------|------|---------|
| Ingestion start/end | `ticketId`, trigger (`create`/`update`/`close`/`comment`), duration, chunkCount, success | `rag.ingest.count` (ok/fail), `rag.ingest.duration` |
| Chunk count | chunkCount per ticket | `rag.ingest.chunks` (histogram) |
| Embedding failures | provider, model, exception class, `ticketId` | `rag.embed.failure` |
| Vector write | rows deleted/inserted | `rag.vector.write` |
| Retrieval | `topK`, threshold, hitCount | `rag.retrieve.count` |
| Similarity scores | max/min similarity of kept hits at **debug** | `rag.retrieve.similarity` (histogram of **kept** scores) |
| No-match | query length (not raw query at info), hitCount=0 | `rag.retrieve.no_match` |

Do not log raw user questions or full ticket bodies at INFO.

---

## 13. Testing requirements

Tests may mock Ollama; use Testcontainers PostgreSQL + pgvector **or** an in-memory fake store that still enforces delete-by-ticketId. Do not require a live LLM.

| ID | Test | Expect |
|----|------|--------|
| ING-T-1 | **Document creation** | Fixture ticket with title, description, two comments, resolution notes → document contains all four; omits empty Comments/Resolution sections when blank |
| ING-T-2 | **Chunking** | Short ticket → 1 chunk; long description → multiple chunks; each chunk starts with `[Ticket TKT-…]` prefix; overflow uses overlap |
| ING-T-3 | **Metadata** | Every chunk has the same `ticketId`, `status`, `priority`, `assignee`, `category` as the ticket |
| ING-T-4 | **Embedding** | Chunker output texts are passed to embedding client; stored vectors have configured dimension; failure → no committed new rows (old rows remain) |
| ING-T-5 | **Vector persistence** | After ingest, rows exist for `ticketId` with matching `chunk_index` sequence |
| ING-T-6 | **Re-ingestion** | Ingest, change description, ingest again → row count is the new set; contents reflect new description |
| ING-T-7 | **Stale prevention** | Old phrase from previous description is **not** in any `content` for that `ticketId`; no leftover extra `chunk_index` |
| ING-T-8 | **Idempotency** | Ingest twice without changes → no duplicate `(ticket_id, chunk_index)` |
| ING-T-9 | **No usable content** | Blank title/description/comments/notes → zero rows for `ticketId` |
| ING-T-10 | **Close trigger** | Ticket moved to `CLOSED` (legal) → chunks’ metadata `status=CLOSED` |
| ING-T-11 | **Illegal transition** | Rejected status change → vectors unchanged |
| ING-T-12 | **Retrieval knobs** | Changing `top-k` / `similarity-threshold` in test config changes keep-set; values not hardcoded in the retriever class |
| ING-T-13 | **Retrieval / threshold** | Below-threshold neighbors dropped; empty keep-set is no-match (generation not called if generator is mocked) |
| ING-T-14 | **Comment add** | New comment appears in payload after re-ingest |

Aligns with requirements T-5, T-6, T-4 (no-match). T-3/T-7 (citations / no parametric answers) belong to RAG API / generation specs.

---

## 14. Design decisions, assumptions, open questions

### Confirmed requirements

- RAG over ticket context; answers only from retrieved context; cite `ticketId`; no-match when nothing relevant (FR-7–FR-10).  
- Refresh embeddings when ticket data changes (FR-11); this brief: on **update** and on **close**.  
- Configurable top-K and similarity threshold (FR-12, FR-13).  
- Payload: title, description, comments, resolution notes.  
- Metadata per chunk: ticketId, status, priority, assignee, category.  
- Backend ticket store: PostgreSQL; Java 17 / Spring Boot observed.  
- Invalid transitions do not persist and therefore do not ingest.

### Design decisions

| ID | Decision |
|----|----------|
| ING-D-1 | Internal pipeline after persist |
| ING-D-2 | Sync ingest after OLTP commit; ingest failure does not roll back the ticket |
| ING-D-3 | Document template in §2 |
| ING-D-4 | Extra row fields: chunkIndex, fingerprint, ingestedAt |
| ING-D-5 | Section-based + fixed-size overflow |
| ING-D-6 | 512 token target, 64 overlap; also configurable |
| ING-D-7 | Prefix `[Ticket {ticketId}] {title}` on every chunk |
| ING-D-8 | **Development:** Ollama embeddings, default `nomic-embed-text` |
| ING-D-21 | **Production (optional):** cloud embedding API if quality/ops warrant; config-selected per environment |
| ING-D-9 | Embedding provider/model/dimension in config; same model for ingest + query per environment |
| ING-D-10 | PGVector in same PostgreSQL |
| ING-D-11 | HNSW cosine |
| ING-D-12 | Cosine; `s = 1 - distance`; K then threshold |
| ING-D-13 | Ingest on create, any ticket update, comment create, any legal status change (including close) |
| ING-D-14/15 | Atomic delete+insert by ticketId |
| ING-D-16 | `rag.retrieval.top-k` / `similarity-threshold` YAML; defaults 5 / 0.72 (§8) |
| ING-D-22 | `RagRetrievalProperties` via `@ConfigurationProperties` + `@Validated` |
| ING-D-23 | Constructor-inject into retrieval service; no hardcoded K/threshold in service |
| ING-D-17 | Failure table in §9 |
| ING-D-18 | Optional fingerprint skip |
| ING-D-19 | Vector store private; single-tenant until auth |
| ING-D-20 | Metrics/logs in §12 |

### Assumptions

| ID | Assumption |
|----|------------|
| ING-A-1 | Schema can be created in dev without a chosen migrator |
| ING-A-2 | Create triggers first ingest |
| ING-A-3 | Any legal status change refreshes metadata via full ingest |
| ING-A-4 | Ticket HTTP success does not depend on Ollama |
| ING-A-5 | Single-tenant v1 |
| ING-A-6 | Threshold is 0.0–1.0 similarity, not raw distance (§8.9) |
| ING-A-7 | PGVector cosine `<=>` + HNSW (§8.9) |

### Open questions

| ID | Question |
|----|----------|
| ING-Q-1 | Reindex procedure when embedding provider/model changes (required when moving dev Ollama → prod cloud) |
| ING-Q-2 | Production migration tool |
| ING-Q-3 | Comment update/delete |
| ING-Q-4 | Per-request top-K / threshold override |
| ING-Q-5 | Retry/outbox/admin re-ingest API |
| ING-Q-6 | Persist `ingestion_status` on Ticket |
| ING-Q-7 | AuthZ filters on retrieval |
| DM-Q-10 | Exclude `CANCELLED` from corpus? **Not done here** |

---

## 15. Traceability

| Source | This spec |
|--------|-----------|
| FR-8–FR-11, ING-1, ING-2 | Pipeline + replace on change |
| FR-12, FR-13, T-6 | §8 |
| FR-9, DM-A-2 | metadata.ticketId |
| Brief payload list | §2 |
| Brief metadata list | §3 |
| Brief close + update refresh | §7 |
| T-5 stale embeddings | §7.2, ING-T-6, ING-T-7 |
| ARCH ingestion component | §1 |
| PGVector vs Chroma | §6 |
| Ollama (dev) vs cloud (prod option) | §5 |
