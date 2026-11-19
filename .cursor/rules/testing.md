# Testing Rules

**Status:** Conventions for backend tests.  
**Applies to:** `backend/src/test/java`  
**Strategy:** `spec/test-strategy.md`

---

## 1. Stack

| Tool | Use |
|------|-----|
| JUnit 5 | Test framework |
| AssertJ | Assertions (`assertThat`) |
| Mockito | Mocking dependencies |
| Spring Boot Test | `@SpringBootTest`, `@WebMvcTest`, `ApplicationContextRunner` |
| Testcontainers | PostgreSQL + pgvector integration (when enabled) |

**RULE-T-1:** Prefer AssertJ over JUnit legacy assertions.

---

## 2. Naming and layout

| Rule | Detail |
|------|--------|
| **RULE-T-2** | Test class: `{ClassUnderTest}Test` or `{Scenario}Test` |
| **RULE-T-3** | Method names describe behavior: `rejectsOpenToClosedTransition`, `detectsHallucinatedPasswordResetExpiry` |
| **RULE-T-4** | Mirror `main` package structure under `src/test/java` |
| **RULE-T-5** | Test fixtures in `...fixture` package (e.g. `PasswordResetKnowledgeFixture`) |

---

## 3. Unit tests

| Rule | Detail |
|------|--------|
| **RULE-T-6** | State machine tests: **no Spring context**; pure JUnit |
| **RULE-T-7** | Table-driven tests for the full 5×5 transition matrix (25 cases) |
| **RULE-T-8** | Config tests may use `ApplicationContextRunner` for isolated binding |
| **RULE-T-9** | Mock external ports (`VectorSearchPort`, `EmbeddingClient`, LLM) in unit tests |

---

## 4. Integration tests

| Rule | Detail |
|------|--------|
| **RULE-T-10** | Use `@SpringBootTest` + Testcontainers for DB/pgvector when testing persistence |
| **RULE-T-11** | Tag slow tests `@Tag("integration")` if CI splits fast/slow suites |
| **RULE-T-12** | Clean up vector rows per test or use isolated schemas to avoid cross-test pollution |
| **RULE-T-13** | T-5 staleness test: update ticket → assert retrieval returns new content |

---

## 5. API / contract tests

| Rule | Detail |
|------|--------|
| **RULE-T-14** | Use MockMvc or REST Assured against controllers |
| **RULE-T-15** | Assert HTTP status **and** JSON body shape |
| **RULE-T-16** | RAG: assert `outcome`, `citations`, `answer` null on `NO_MATCH` |
| **RULE-T-17** | Illegal transition: assert 400 **and** GET still shows old status |

---

## 6. RAG / AI tests

| Rule | Detail |
|------|--------|
| **RULE-T-18** | Mock LLM in CI; do not call live APIs in default test run |
| **RULE-T-19** | Intentional wrong answers live in **test fixtures only** |
| **RULE-T-20** | Grounding regression must fail if contradiction detection is removed |
| **RULE-T-21** | Test behavior (grounded/not grounded), not private method internals |
| **RULE-T-22** | Citation validation: ids outside keep-set must fail |

---

## 7. What not to test

| Rule | Detail |
|------|--------|
| **RULE-T-23** | Do not test Spring Boot or third-party library behavior |
| **RULE-T-24** | Do not add tests that only assert mocks were called with no behavior claim |
| **RULE-T-25** | Do not weaken production validation to make tests pass |

---

## 8. Running tests

```bash
cd backend && mvn test
```

**RULE-T-26:** Do not claim tests passed unless `mvn test` (or targeted `-Dtest=...`) was executed.

**RULE-T-27:** Fix or document pre-existing failures before adding new tests that depend on full context load.

---

## 9. Coverage expectations

Minimum before merging features:

* Happy path + primary failure path  
* Requirement traceability (T-1–T-7 where applicable)  
* New public API endpoints have contract tests  

**Open:** Coverage percentage gate.

---

## 10. References

* `spec/test-strategy.md`  
* `spec/evaluation-strategy.md`  
* `commands/generate-tests.md`  
* `commands/review-rag-output.md`  
