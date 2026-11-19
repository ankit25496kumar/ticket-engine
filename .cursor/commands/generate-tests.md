# Generate Tests Command

## Objective

Generate or extend tests from approved requirements, specifications, and existing implementation — filling gaps identified in `spec/test-strategy.md` without weakening production code.

---

## Inputs

Read before generating:

| Source | Use |
|--------|-----|
| `spec/requirements.md` | T-1–T-7 obligations |
| `spec/state-machine.md` §5 | Transition test matrix |
| `spec/api-contract.md` | HTTP contract oracles |
| `spec/rag-api-contract.md` §10 | RAG contract tests |
| `spec/rag-ingestion.md` §13 | Ingestion/retrieval tests |
| `spec/test-strategy.md` | Layer and scope |
| `rules/testing.md` | Naming, tools, conventions |
| `backend/src/main` | Classes under test |
| `backend/src/test` | Existing tests (extend, don't duplicate) |

---

## Process

### 1. Identify the behavior under test

Map the feature to requirement IDs (e.g. FR-6 → T-2). State the **observable behavior**, not the implementation.

### 2. Choose the test layer

| Behavior | Layer | Example |
|----------|-------|---------|
| Transition policy | Unit (no Spring) | `TicketStatusTransitionPolicyTest` |
| Service orchestration | Unit with mocks | `TicketServiceTest` |
| HTTP contract | `@WebMvcTest` | `TicketControllerTest` |
| DB + vectors | Integration + Testcontainers | `TicketIngestionIntegrationTest` |
| Grounding conflict | Unit | `PasswordResetRagScenarioTest` |

### 3. Generate test cases

For each behavior, include:

* **Happy path** — expected success
* **Primary failure path** — expected rejection with correct HTTP/code
* **Boundary** — empty input, same-state, empty retrieval, threshold edge

### 4. Use fixtures

* Place shared data in `...fixture` package
* Intentional wrong AI answers **only** in test fixtures
* Reuse `PasswordResetKnowledgeFixture` for grounding scenarios

### 5. Mock external systems in CI

| Dependency | CI approach |
|------------|-------------|
| LLM | Mock fixed JSON response |
| Embeddings | Mock or precomputed vectors |
| Ollama | Not called in default `mvn test` |
| PostgreSQL | Testcontainers when integration layer |

### 6. Assert completely

| Bad | Good |
|-----|------|
| `verify(save).called()` only | Assert status unchanged + HTTP 400 |
| Assert exception type only | Assert error `code` and body shape |
| Test private methods | Test public behavior via service/API |

### 7. Run tests

```bash
cd backend && mvn test
# or targeted:
mvn -Dtest=ClassNameTest test
```

Do not claim success without execution.

---

## Test templates by area

### State machine (required)

```text
Parameterized test: all 25 from×to pairs
Expect: allow == true for 5 allowed edges; false otherwise
Same-state: all five reject
```

### API status transition

```text
Given ticket OPEN
When PATCH status CLOSED
Then 400 INVALID_STATUS_TRANSITION
And GET still OPEN
```

### RAG no-match

```text
Given empty vector results
When POST /api/v1/rag/query
Then HTTP 200, outcome NO_MATCH, answer null, citations []
```

### RAG grounding regression

```text
Given context "30 minutes"
When answer "24 hours"
Then RagOutputReviewer result FAIL
And findings include CONTEXT_CONFLICT
```

### Config

```text
Given top-k=0 in properties
When context starts
Then failure
```

---

## Output

When generating tests, provide:

1. **Test class(es) added or updated** with path
2. **Requirement IDs covered** (T-1–T-7, RAG-API-T-*, ING-T-*)
3. **Layer** (unit / integration / API)
4. **Commands run** and result

---

## Rules

* Do not generate trivial tests (getter/setter, enum count only).
* Do not weaken production validation to make tests pass.
* Do not duplicate existing tests — extend coverage gaps only.
* Match naming and package conventions in `rules/testing.md`.
* One logical behavior per test method.

---

## References

* `commands/review-code.md` — verify generated tests during code review
* `commands/review-rag-output.md` — grounding scenario definition
* `spec/evaluation-strategy.md` — eval fixtures vs CI tests
