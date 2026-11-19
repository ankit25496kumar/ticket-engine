# RAG API Contract

**Status:** AI response contract (JSON). No implementation.  
**Depends on:** `spec/requirements.md`, `spec/architecture.md`, `spec/data-model.md`, `spec/rag-ingestion.md`  
**Scope:** RAG query endpoint request/response only. Ticket CRUD is `spec/api-contract.md` (not written).

This document defines the **stable JSON structure** the backend promises to the frontend for AI/RAG answers, including normal answers, citations, no-match, failures, and optional UI hints.

---

## 0. Classification

| Kind | Meaning |
|------|---------|
| **Requirement** | From `requirements.md` / architecture / data model |
| **Decision (`RAG-API-D-*`)** | Chosen here so frontend and backend can integrate |
| **Assumption (`RAG-API-A-*`)** | Needed for the contract; not a product requirement |
| **Open (`RAG-API-Q-*`)** | Unspecified; do not treat as promised behavior |

---

## 1. Endpoint (decision)

**RAG-API-D-1:** `POST /api/v1/rag/query`

| | |
|--|--|
| Request `Content-Type` | `application/json` |
| Response `Content-Type` | `application/json` |
| Auth | Unspecified (`RAG-API-Q-1`) |

Requirements only mandate *some* interface (API-2); this path is a design decision.

---

## 2. Request body

```json
{
  "query": "How was the CSV export issue fixed?"
}
```

| Field | Type | Required | Source |
|-------|------|----------|--------|
| `query` | string | yes | API-2; non-blank after trim |

**Not in v1 request:** `topK`, `similarityThreshold` (global config per `rag-ingestion.md` §8; `ING-Q-4`).

Validation failure → **ERROR** outcome (§5.4), HTTP **400**.

---

## 3. Response envelope (all outcomes)

Every response uses the same top-level shape so the frontend can parse one schema.

```json
{
  "outcome": "ANSWER",
  "query": "How was the CSV export issue fixed?",
  "answer": { },
  "citations": [ ],
  "confidence": null,
  "escalation": null,
  "error": null
}
```

| Field | Type | Required | Source |
|-------|------|----------|--------|
| `outcome` | enum | yes | Discriminator; see §4 |
| `query` | string | yes | Echo of request query (after trim) |
| `answer` | object \| null | yes | Present when `outcome=ANSWER`; otherwise `null` |
| `citations` | array | yes | May be empty; see §4 |
| `confidence` | object \| null | yes | Optional signal; see §6 |
| `escalation` | object \| null | yes | UI hint; see §7 |
| `error` | object \| null | yes | Present when `outcome=ERROR`; otherwise `null` |

**RAG-API-D-2:** Use a discriminated **`outcome`** field so no-match is explicit in the body (FR-10, VAL-2, RET-4), not inferred from missing fields alone.

---

## 4. Outcomes

### 4.1 `outcome` enum

| Value | Meaning | HTTP (decision) |
|-------|---------|-----------------|
| `ANSWER` | Grounded answer with citations | **200** |
| `NO_MATCH` | No relevant tickets after retrieval gate | **200** |
| `ERROR` | Request invalid or pipeline failure | **400** or **503** (see §5) |

**RAG-API-D-3:** `NO_MATCH` returns **HTTP 200** with `outcome: "NO_MATCH"`. It is a successful, typed result—not a fabricated answer (FR-10). Requirements left HTTP mapping open; this closes it for the frontend.

---

### 4.2 `ANSWER` — normal grounded response

**Requirements:** FR-8, FR-9, RET-1, RET-5, API-2.

```json
{
  "outcome": "ANSWER",
  "query": "How was the CSV export issue fixed?",
  "answer": {
    "text": "The CSV export was fixed by enabling UTF-8 BOM in exporter v2.1."
  },
  "citations": [
    {
      "ticketId": "TKT-1200",
      "title": "CSV export corrupt on Windows"
    }
  ],
  "confidence": {
    "score": 0.88,
    "basis": "RETRIEVAL"
  },
  "escalation": {
    "recommended": false,
    "reason": null
  },
  "error": null
}
```

#### `answer` object

| Field | Type | Required | Source |
|-------|------|----------|--------|
| `text` | string | yes | Generated **only** from retrieved ticket context (FR-8) |

No markdown/HTML guarantee unless specified later (`RAG-API-Q-2`).

#### `citations` array (required on ANSWER)

**Requirements:** FR-9, RET-5, DM-A-2 — cite **`ticketId`** (`TKT-1001` form), not internal `id`, not comment ids.

| Field | Type | Required | Source |
|-------|------|----------|--------|
| `ticketId` | string | yes | Pattern `^TKT-[0-9]+$` (DM-D-2) |
| `title` | string | yes | From chunk metadata / ticket; display only |

**RAG-API-D-4:** One citation entry per **distinct** `ticketId` used in grounding, ordered by descending retrieval similarity (ties: ascending `ticketId`).

**RAG-API-D-5:** Every `ticketId` in `citations` must appear in the retrieved keep-set. The backend must not cite tickets that were not retrieved (FR-8, FR-9).

**Not cited:** chunk ids, comment ids, similarity scores in citations (requirements cite ticket IDs only).

---

### 4.3 `NO_MATCH` — explicit no relevant tickets

**Requirements:** FR-10, RET-4, VAL-2, API-2.

```json
{
  "outcome": "NO_MATCH",
  "query": "How do I configure SAML for Mars colony?",
  "answer": null,
  "citations": [],
  "confidence": null,
  "escalation": {
    "recommended": true,
    "reason": "NO_RELEVANT_TICKETS"
  },
  "error": null
}
```

| Field | Rule |
|-------|------|
| `answer` | **Must be `null`** — no generated guess (VAL-2) |
| `citations` | **Must be `[]`** |
| `confidence` | **Must be `null`** |

**RAG-API-D-6:** Include stable machine-readable **`message`** is optional; if present:

```json
"noMatch": {
  "code": "NO_RELEVANT_TICKETS",
  "message": "No similar tickets were found in the knowledge base."
}
```

Exact human `message` text is a **design default**, not a requirement (requirements open question). `code` is stable for the frontend.

Triggered when: retrieval keep-set is empty after top-K + threshold (`rag-ingestion.md` §8.7 step 5). Generator is **not** called.

---

### 4.4 `ERROR` — failures (not no-match)

**Requirements:** Partial — validation and pipeline errors are not fully specified; this contract distinguishes them from `NO_MATCH`.

```json
{
  "outcome": "ERROR",
  "query": "How was the CSV export issue fixed?",
  "answer": null,
  "citations": [],
  "confidence": null,
  "escalation": null,
  "error": {
    "code": "EMBEDDING_UNAVAILABLE",
    "message": "Unable to process the query at this time."
  }
}
```

| Field | Rule |
|-------|------|
| `answer` | **Must be `null`** |
| `citations` | **Must be `[]`** |

#### Error codes (`error.code`)

| Code | When | HTTP (decision) |
|------|------|-----------------|
| `INVALID_QUERY` | Missing/blank `query` | **400** |
| `EMBEDDING_UNAVAILABLE` | Query embedding failed (Ollama/cloud down) | **503** |
| `VECTOR_STORE_UNAVAILABLE` | PGVector query failed | **503** |
| `GENERATION_FAILED` | LLM/generator error after successful retrieval | **503** |
| `INTERNAL_ERROR` | Unexpected server error | **500** |

**RAG-API-D-7:** Infrastructure failures return **`outcome: "ERROR"`**, not `NO_MATCH`. `NO_MATCH` means “searched, nothing relevant”—not “system broken.”

---

## 5. HTTP status summary

| `outcome` | Typical HTTP | Notes |
|-----------|--------------|-------|
| `ANSWER` | 200 | |
| `NO_MATCH` | 200 | Explicit empty grounding (FR-10) |
| `ERROR` | 400 / 503 / 500 | See §4.4 |

---

## 6. Confidence (design — not a requirement)

**Requirements:** Do **not** require a confidence score (requirements §3 open: “Schema of citations, confidence”).

**RAG-API-D-8:** Include optional **`confidence`** on `ANSWER` only, for UI hints. It is **informational**, not a guarantee of correctness.

```json
"confidence": {
  "score": 0.88,
  "basis": "RETRIEVAL"
}
```

| Field | Type | Rule |
|-------|------|------|
| `score` | number | **0.0–1.0**; `RAG-API-A-1`: max similarity among **kept** chunks after threshold |
| `basis` | enum | **`RETRIEVAL`** only in v1 (similarity from vector search) |

On `NO_MATCH` and `ERROR`: `confidence` is **`null`**.

**Not promised:** calibrated probability, entailment checks, or claim-level confidence (AI-R-2).

---

## 7. Escalation (design — not a requirement)

**Requirements:** Do **not** mention escalation, human review, or routing. This section is a **frontend hint only** (`RAG-API-D-9`).

```json
"escalation": {
  "recommended": true,
  "reason": "NO_RELEVANT_TICKETS"
}
```

| Field | Type | Values |
|-------|------|--------|
| `recommended` | boolean | |
| `reason` | string \| null | Stable enum-like string |

#### `reason` values

| `reason` | When `recommended` |
|----------|-------------------|
| `null` | `false` on successful `ANSWER` |
| `NO_RELEVANT_TICKETS` | `true` on `NO_MATCH` |
| `LOW_CONFIDENCE` | `true` on `ANSWER` when `confidence.score` < configurable `rag.retrieval.escalation-threshold` (**optional** future config; **not** in requirements — default omit or set threshold so it never fires in v1) |

**RAG-API-D-10:** On `NO_MATCH`, set `escalation.recommended: true` and `reason: "NO_RELEVANT_TICKETS"` so the UI can suggest “create ticket” or “contact agent” without the backend implementing workflow.

**RAG-API-D-11:** Escalation does **not** create tickets, assign agents, or change ticket status. UI-only.

---

## 8. Pipeline mapping

```
POST /api/v1/rag/query { query }
        │
        ▼
   Validate query ──invalid──► ERROR (INVALID_QUERY)
        │
        ▼
   Embed query ──fail──► ERROR (EMBEDDING_UNAVAILABLE)
        │
        ▼
   Retrieve (top-K, threshold) ──fail──► ERROR (VECTOR_STORE_UNAVAILABLE)
        │
        ├── keep-set empty ──► NO_MATCH (+ escalation hint)
        │
        ▼
   Generate from keep-set only ──fail──► ERROR (GENERATION_FAILED)
        │
        ▼
   ANSWER { text, citations[], confidence?, escalation.recommended: false }
```

### 8.1 Generator grounding instructions (required)

**Requirements:** FR-8, FR-9, FR-10, RET-1, VAL-2, T-7.

The generator (LLM or equivalent) **must** receive the user query plus **only** the retrieved ticket chunk text and metadata supplied by the backend. The following instructions are **mandatory** system/developer prompt text. Implementations must not weaken or omit them.

```
Answer the user's question using only the supplied ticket context.

Do not use general knowledge to answer support-specific questions.

If the supplied context does not contain enough information to answer
the question, explicitly state that no relevant tickets were found.

Do not invent ticket IDs, events, causes, resolutions, users, or dates.

Every factual claim about a ticket must be supported by the retrieved
ticket context.
```

#### Enforcement mapping

| Instruction | API / backend behavior |
|-------------|------------------------|
| Only supplied ticket context | Generator input = keep-set chunks only (§8 pipeline). No web, no parametric knowledge as substitute (FR-8, RET-1). |
| No general knowledge for support-specific questions | Same; support-specific = queries about tickets, fixes, incidents in the corpus. |
| Insufficient context → state no relevant tickets found | **Preferred:** grounding gate returns `NO_MATCH` before generation when keep-set is empty (RET-4). If generator runs but cannot answer from chunks, response must align with `NO_MATCH` or `answer.text` must state that no relevant information was found — **must not** invent an answer (FR-10, VAL-2). |
| Do not invent ticket IDs, events, causes, resolutions, users, dates | `citations[].ticketId` must be ⊆ retrieved ids (RAG-API-D-5). `answer.text` must not mention `TKT-*` ids not in the keep-set. |
| Every factual claim supported by context | Test obligation RAG-API-T-7; evaluation strategy may add golden sets later. |

**RAG-API-D-12:** The backend **validates** that every `ticketId` in `citations` appears in the retrieval keep-set before returning `ANSWER`. Reject or downgrade to `NO_MATCH` if the generator cites ids outside the keep-set.

**RAG-API-D-13:** When the generator cannot ground an answer in the supplied chunks, the API returns **`outcome: "NO_MATCH"`** (not `ANSWER` with a guess). Wording in `noMatch.message` may echo “no relevant tickets were found”; exact copy is `RAG-API-Q-5`.

---

## 9. Frontend parsing rules

1. Branch on **`outcome`** first.  
2. Render `answer.text` only when `outcome === "ANSWER"`.  
3. Show citation links from `citations[].ticketId` (and `title` for display).  
4. On `NO_MATCH`, show `noMatch.message` (if present) or a client default; do not show AI-generated text.  
5. On `ERROR`, show `error.message`; do not treat as no-match.  
6. Treat `confidence.score` as optional UI badge only.  
7. Use `escalation.recommended` for CTA visibility only; no backend side effects.

---

## 10. Testing obligations

| ID | Test | Requirement |
|----|------|-------------|
| RAG-API-T-1 | `ANSWER` response includes non-empty `citations[].ticketId` matching `^TKT-[0-9]+$` | T-3, FR-9 |
| RAG-API-T-2 | `NO_MATCH`: `answer` null, `citations` [], `outcome` `NO_MATCH`, HTTP 200 | T-4, FR-10 |
| RAG-API-T-3 | Empty keep-set does not return `ANSWER` with invented text | VAL-2, T-7 (with mocked generator) |
| RAG-API-T-4 | Embedding failure → `ERROR`, not `NO_MATCH` | RAG-API-D-7 |
| RAG-API-T-5 | Invalid query → `ERROR` `INVALID_QUERY`, HTTP 400 | — |
| RAG-API-T-6 | Cited `ticketId`s ⊆ retrieved ticket ids | FR-8, RAG-API-D-5 |
| RAG-API-T-7 | Generator prompt includes §8.1 instructions; answer cites only retrieved facts; no invented `TKT-*` ids | FR-8, FR-9, T-7 |

---

## 11. Design decisions summary

| ID | Decision |
|----|----------|
| RAG-API-D-1 | `POST /api/v1/rag/query` |
| RAG-API-D-2 | Top-level `outcome` discriminator |
| RAG-API-D-3 | `NO_MATCH` → HTTP 200 |
| RAG-API-D-4 | Distinct ticket citations, ordered by similarity |
| RAG-API-D-5 | Citations only from retrieved set |
| RAG-API-D-6 | Optional `noMatch.code` / `message` on `NO_MATCH` |
| RAG-API-D-7 | Pipeline failures → `ERROR`, not `NO_MATCH` |
| RAG-API-D-8 | Optional retrieval-based `confidence` on `ANSWER` |
| RAG-API-D-9 | `escalation` as UI hint only |
| RAG-API-D-10 | Recommend escalation on `NO_MATCH` |
| RAG-API-D-11 | Escalation has no backend workflow |
| RAG-API-D-12 | Validate citations ⊆ keep-set before `ANSWER` |
| RAG-API-D-13 | Insufficient grounding → `NO_MATCH`, not invented `ANSWER` |

---

## 12. Assumptions

| ID | Assumption |
|----|------------|
| RAG-API-A-1 | `confidence.score` = max similarity of chunks passed to generator |
| RAG-API-A-2 | `title` in citations is available from retrieval metadata or ticket store |
| RAG-API-A-3 | Single answer string in `answer.text` (not multiple alternatives) |

---

## 13. Open questions

| ID | Question |
|----|----------|
| RAG-API-Q-1 | Authentication / authorization on RAG endpoint |
| RAG-API-Q-2 | Markdown in `answer.text` |
| RAG-API-Q-3 | Return similarity scores to client in citations |
| RAG-API-Q-4 | Per-request top-K / threshold override |
| RAG-API-Q-5 | Exact no-match user-facing copy (requirements open) |
| RAG-API-Q-6 | Whether `LOW_CONFIDENCE` escalation threshold is added |

---

## 14. Traceability

| Requirement | Contract element |
|-------------|------------------|
| API-2 | Request `query`; `ANSWER` or `NO_MATCH` outcomes |
| FR-8 | `answer.text` from retrieved context only; §8.1 generator instructions; no answer on empty retrieval |
| FR-9, RET-5 | `citations[].ticketId` |
| FR-10, RET-4, VAL-2 | `outcome: "NO_MATCH"`, `answer: null` |
| T-7 | §8.1, RAG-API-T-7 |
| DM-D-2 | `ticketId` format in citations |
| T-3, T-4 | §10 tests |
