# API Standards

**Status:** HTTP/JSON conventions for backend APIs.  
**Applies to:** Ticket APIs (`spec/api-contract.md`), RAG API (`spec/rag-api-contract.md`)

---

## 1. General

| Rule | Standard |
|------|----------|
| **RULE-API-1** | Base path `/api/v1` for all public endpoints |
| **RULE-API-2** | JSON bodies; `Content-Type: application/json` |
| **RULE-API-3** | Field names **camelCase** in JSON |
| **RULE-API-4** | Timestamps ISO-8601 UTC with `Z` suffix (e.g. `2026-09-08T10:00:00Z`) |
| **RULE-API-5** | Enum values as uppercase strings matching Java enum names |

---

## 2. HTTP methods and resources

| Operation | Method | Pattern |
|-----------|--------|---------|
| Create ticket | `POST` | `/api/v1/tickets` |
| List tickets | `GET` | `/api/v1/tickets` |
| Get ticket | `GET` | `/api/v1/tickets/{ticketId}` |
| Update ticket fields | `PATCH` | `/api/v1/tickets/{ticketId}` |
| Change status | `PATCH` | `/api/v1/tickets/{ticketId}/status` |
| Add comment | `POST` | `/api/v1/tickets/{ticketId}/comments` |
| List comments | `GET` | `/api/v1/tickets/{ticketId}/comments` |
| Dashboard summary | `GET` | `/api/v1/dashboard/summary` |
| RAG query | `POST` | `/api/v1/rag/query` |

**RULE-API-6:** Use `ticketId` (`TKT-1001`) in path parameters, not internal database id.

**RULE-API-7:** Status changes use a dedicated sub-resource, not generic PATCH on the ticket body.

---

## 3. HTTP status codes

| Code | Usage |
|------|--------|
| **200** | Successful GET, PATCH, RAG `ANSWER` / `NO_MATCH` |
| **201** | Successful POST create (ticket, comment) |
| **400** | Validation error, illegal status transition, RAG request invalid |
| **404** | Ticket not found |
| **503** | RAG pipeline unavailable (embedding/LLM/vector store failure) |
| **500** | Unexpected server error (avoid for expected domain failures) |

**RULE-API-8:** `NO_MATCH` is **HTTP 200** with `outcome: "NO_MATCH"` — not 404, not 204.

**RULE-API-9:** Illegal transitions are **400**, not 409, unless a future spec changes this.

---

## 4. Request validation

| Rule | Detail |
|------|--------|
| **RULE-API-10** | Validate at API boundary with Jakarta Bean Validation |
| **RULE-API-11** | Reject unknown enum strings before domain layer |
| **RULE-API-12** | Trim string inputs where whitespace-only is invalid (`query` in RAG) |
| **RULE-API-13** | Reject client-supplied `ticketId` on create |

---

## 5. Error responses

### 5.1 Ticket APIs

```json
{
  "error": {
    "code": "INVALID_STATUS_TRANSITION",
    "message": "Human-readable message.",
    "details": []
  }
}
```

| Rule | Detail |
|------|--------|
| **RULE-API-14** | `code` is a stable machine-readable string |
| **RULE-API-15** | `message` is safe to show in UI (no stack traces) |
| **RULE-API-16** | Field validation may use `details: [{ "field": "title", "message": "..." }]` |

### 5.2 RAG API

Use the envelope in `rag-api-contract.md` (`outcome`, `error` object). Do not mix ticket error shape into RAG responses.

---

## 6. Response consistency

| Rule | Detail |
|------|--------|
| **RULE-API-17** | Successful ticket GET/PATCH returns the full ticket resource |
| **RULE-API-18** | RAG responses always include `outcome`, `query`, `citations`, `answer`, `confidence`, `escalation`, `error` keys (null where unused) |
| **RULE-API-19** | Do not add undocumented fields to stable contract without spec update |

---

## 7. Versioning

**RULE-API-20:** Path version `/api/v1`. Breaking changes require `/api/v2` or spec amendment.

**Open:** Deprecation policy.

---

## 8. Security (minimum)

| Rule | Detail |
|------|--------|
| **RULE-API-21** | No secrets in URLs or response bodies |
| **RULE-API-22** | Auth is unspecified — do not assume public access in production without explicit decision |
| **RULE-API-23** | Validate `ticketId` path format (`^TKT-[0-9]+$`) before DB lookup where practical |

---

## 9. References

* `spec/api-contract.md`  
* `spec/rag-api-contract.md`  
* `rules/java-springboot.md`  
