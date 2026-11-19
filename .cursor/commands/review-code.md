# Review Code Command

## Objective

Review backend implementation against approved specifications and project rules. Identify correctness, spec compliance, security, and maintainability issues.

Do **not** modify code during the review unless explicitly asked.

---

## Review Inputs

### Specifications

* `spec/requirements.md`
* `spec/architecture.md`
* `spec/data-model.md`
* `spec/state-machine.md`
* `spec/api-contract.md`
* `spec/rag-api-contract.md`
* `spec/rag-ingestion.md`
* `spec/test-strategy.md`

### Rules

* `rules/java-springboot.md`
* `rules/api-standards.md`
* `rules/testing.md`
* `rules/rag-vector-store.md`

### Implementation

* `backend/src/main`
* `backend/src/test`

### Specialized reviews (when AI/RAG code changed)

* `commands/review-ai.md` — full AI/RAG implementation review
* `commands/review-rag-output.md` — per-answer grounding review

---

## Review Process

### 1. Scope identification

List changed files and map each to spec/rule obligations.

### 2. Architecture compliance

| Check | Rule |
|-------|------|
| Controllers thin | RULE-J-13 |
| State machine in domain/service | RULE-J-9, RULE-J-10 |
| Ingestion after persist | RULE-RAG-15 |
| Grounding gate before generator | RULE-RAG-23 |
| Config via `@ConfigurationProperties` | RULE-J-4, RULE-RAG-6 |

### 3. API compliance

* Paths, methods, status codes per `api-contract.md` and `rules/api-standards.md`
* RAG envelope per `rag-api-contract.md`
* Error shapes consistent
* `NO_MATCH` returns HTTP 200

### 4. Data model compliance

* `ticketId` server-generated, `TKT-{n}` format
* Enum constraints on status/priority
* Comment FK to `Ticket.ticketId`
* Internal `id` not exposed

### 5. State machine compliance

* Full 5×5 matrix enforced
* Same-state rejected (SM-D-2)
* Create sets `OPEN` (SM-D-3)
* Illegal transition: no persist, API 400

### 6. RAG / vector store compliance

* PGVector, cosine, configurable K/threshold
* No hardcoded retrieval policy
* Citation validation
* Chunking and metadata per `rag-ingestion.md`

Run `commands/review-ai.md` when reviewing AI/RAG changes.

### 7. Security

* No secrets in code/logs
* No client-supplied retrieval context
* Input validation at API boundary

### 8. Testing

* Tests exist for new behavior per `spec/test-strategy.md`
* T-1–T-7 covered where applicable
* Grounding regression tests present for RAG
* Tests assert behavior, not trivia (`rules/testing.md`)

### 9. Code quality

* Constructor injection
* Typed exceptions
* No unrelated refactors in the same change

---

## Review Output

### Summary

* Overall status: **PASS** / **PASS WITH ISSUES** / **FAIL**
* Critical: `<n>` | High: `<n>` | Medium: `<n>` | Low: `<n>`

### Findings

```text
[SEVERITY]

File:
backend/src/main/...

Location:
ClassName.methodName()

Problem:
...

Spec/rule violated:
...

Expected:
...

Recommended fix:
...
```

### Specification violations

List requirement IDs and spec sections violated.

### Test gaps

List missing tests referenced in `test-strategy.md`.

### Final recommendation

* **APPROVE**
* **APPROVE AFTER FIXES**
* **REJECT**

---

## Rules

* Do not approve code that bypasses the state machine or grounding gate.
* Do not approve hardcoded `top-k` or similarity threshold in service logic.
* Flag intentional test-only fixtures separately from production bugs.
