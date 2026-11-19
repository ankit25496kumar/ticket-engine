# Java / Spring Boot Engineering Rules

**Status:** Mandatory engineering guidelines for backend development  
**Applies To:** `backend/src/main/java`, `backend/src/test/java`  
**Technology Stack:** Java 17, Spring Boot 3.5.6  
**Build Configuration:** `backend/pom.xml`

---

## 1. Project Structure and Architecture

The backend application must follow a modular, layered architecture with clear separation of responsibilities.

### Recommended Package Structure

```text
com.ttn
├── SupportTicketAiApplication.java
│
├── ticket/
│   ├── api/          # REST controllers, request/response DTOs, exception handlers
│   ├── domain/       # Entities, enums, domain policies, domain exceptions
│   ├── service/      # Application services and business orchestration
│   └── repository/   # Spring Data JPA repositories
│
└── rag/
    ├── api/          # RAG-related REST endpoints and DTOs
    ├── config/       # Configuration properties and RAG configuration
    ├── ingestion/    # Document processing, chunking, and embedding
    ├── retrieval/    # Vector search and context retrieval
    ├── generation/   # LLM integration and response generation
    └── review/       # Grounding validation and response reviewers
```

### Architecture Rules

- **RULE-J-1:** Business logic, including state transitions and RAG grounding policies, must reside in the domain or service layer. Controllers must not contain business rules.
- **RULE-J-2:** Maintain strict module boundaries. RAG-related functionality must remain under `rag.*`, while ticket management and CRUD functionality must remain under `ticket.*`.
- Package names must follow the existing project conventions. `com.ttn` is currently a placeholder and must be finalized based on the actual project configuration.

---

## 2. Spring Boot Development Conventions

Follow standard Spring Boot practices to ensure maintainability, testability, and consistency.

| Rule | Engineering Requirement |
|---|---|
| **RULE-J-3** | Use constructor-based dependency injection exclusively. Avoid field injection using `@Autowired`. |
| **RULE-J-4** | Externalize configurable values using `@ConfigurationProperties` and `application.yaml`. Do not hardcode business policies or RAG configuration values such as `rag.retrieval.top-k` and `similarity-threshold`. |
| **RULE-J-5** | Annotate configuration property classes with `@Validated` and apply appropriate Jakarta validation constraints. |
| **RULE-J-6** | Apply `@Transactional` at the application service layer for operations that require transactional persistence. |
| **RULE-J-7** | Exclude Spring Boot auto-configurations only when necessary. Every exclusion must have a clear, documented justification, including exclusions specific to test profiles. |

---

## 3. Domain Model and Business Logic

The domain layer must represent business concepts accurately and enforce the application's business constraints.

| Rule | Engineering Requirement |
|---|---|
| **RULE-J-8** | Implement `TicketStatus` and `TicketPriority` as Java enums, strictly matching the values and definitions provided in the specifications. |
| **RULE-J-9** | Implement `TicketStatusTransitionPolicy` as a pure Java class without mandatory Spring dependencies or annotations. It must be independently testable. |
| **RULE-J-10** | All ticket status updates after creation must pass through `TicketService` or an appropriate domain method that enforces the transition policy. |
| **RULE-J-11** | Generate `ticketId` on the server during ticket creation. Never accept the ticket identifier as client-controlled input. |
| **RULE-J-12** | The `Comment.ticketId` foreign key must reference `Ticket.ticketId`, not the internal database-generated `Ticket.id`. |

---

## 4. REST API and Controller Layer

The API layer must remain lightweight and focus on HTTP communication, request validation, and response mapping.

| Rule | Engineering Requirement |
|---|---|
| **RULE-J-13** | Controllers must remain thin. Their responsibilities are limited to request validation, service invocation, and response mapping. |
| **RULE-J-14** | Use Jakarta Bean Validation annotations such as `@Valid`, `@NotBlank`, `@NotNull`, and `@NotEmpty` wherever applicable on request DTOs. |
| **RULE-J-15** | Handle domain and application exceptions centrally using `@RestControllerAdvice`, following the response structure defined in `api-contract.md`. |
| **RULE-J-16** | Never expose internal database identifiers such as `Ticket.id` in API responses or external JSON contracts. |

---

## 5. Persistence and Database Standards

Use PostgreSQL with Spring Data JPA while maintaining consistency between the domain model and database schema.

| Rule | Engineering Requirement |
|---|---|
| **RULE-J-17** | Map JPA entities correctly to PostgreSQL tables. Apply appropriate constraints to enum-backed columns as defined in DM-D-3 and DM-D-4. |
| **RULE-J-18** | Maintain the required database indexes: a unique index or constraint on `ticket_id` and an index on `comment.ticket_id`, as specified in DM-D-6. |
| **RULE-J-19** | Repositories must focus exclusively on data access and query execution. Business rules and application orchestration must remain in the service or domain layer. |

---

## 6. RAG Integration Standards

All RAG components must follow the defined ingestion, retrieval, generation, and grounding-validation workflows.

| Rule | Engineering Requirement |
|---|---|
| **RULE-J-20** | Trigger document ingestion only after the corresponding ticket has been successfully persisted, following ING-D-1 and ING-D-2. |
| **RULE-J-21** | Execute the grounding gate before invoking the LLM generator. If the resulting keep-set is empty, return `NO_MATCH` without generating an answer. |
| **RULE-J-22** | Before returning an `ANSWER`, validate that every generated citation belongs to the approved keep-set, as specified in RAG-API-D-12. |
| **RULE-J-23** | Use `RagAnswerGroundingReviewer` and `RagOutputReviewer` for post-generation validation wherever applicable. |

### RAG Processing Principles

- Never bypass the grounding validation workflow.
- Do not generate answers when no relevant context is available.
- Never return citations that are not part of the approved retrieval context.
- Keep ingestion, retrieval, generation, and review responsibilities independent.

---

## 7. Error Handling and Logging

Implement predictable exception handling and meaningful logging across the application.

| Rule | Engineering Requirement |
|---|---|
| **RULE-J-24** | Use strongly typed domain exceptions, such as `InvalidStatusTransitionException`, instead of generic exceptions for known business failures. |
| **RULE-J-25** | Never silently ignore document ingestion or processing failures. Log failures and surface them according to the error-handling requirements in `rag-ingestion.md`, Section 9. |
| **RULE-J-26** | Never log secrets, API keys, authentication tokens, or sensitive ticket information at INFO level. Follow secure logging practices across all log levels. |

---

## 8. Java Code Quality and Style

Write clean, readable, maintainable, and testable Java code.

| Rule | Engineering Requirement |
|---|---|
| **RULE-J-27** | Prefer Java `record` types for immutable DTOs and value objects wherever appropriate. |
| **RULE-J-28** | Do not use `eval`, unsafe deserialization mechanisms, or configurations that disable TLS certificate verification. |
| **RULE-J-29** | Follow the existing formatting, naming conventions, and coding style of the file or module being modified. |
| **RULE-J-30** | Remove or correctly repoint the starter `Main.java` class if `SupportTicketAiApplication` is designated as the application's primary entry point. |

### Additional Code Quality Expectations

- Follow SOLID principles where applicable.
- Avoid unnecessary code duplication.
- Prefer small, focused methods with clear responsibilities.
- Avoid introducing unnecessary abstractions or design patterns.
- Use meaningful variable, method, and class names.
- Keep implementation consistent with the existing architecture.
- Do not introduce unrelated changes while implementing a feature or fixing a bug.

---

## 9. Dependency Management

Keep the application lightweight and avoid unnecessary third-party dependencies.

| Rule | Engineering Requirement |
|---|---|
| **RULE-J-31** | Do not introduce new dependencies without a valid technical justification. All dependency versions must be explicitly managed and pinned in `pom.xml`. |
| **RULE-J-32** | Add vector database, embedding, and related AI client dependencies only when implementing the corresponding RAG ingestion or retrieval functionality defined in `rag-ingestion.md`. |

### Dependency Guidelines

- Prefer existing Spring Boot and Java capabilities before introducing external libraries.
- Avoid duplicate libraries that provide overlapping functionality.
- Evaluate compatibility with Java 17 and Spring Boot 3.5.6 before adding dependencies.
- Keep dependency versions consistent and centrally managed wherever possible.

---

## 10. Project References and Specifications

Before implementing or modifying functionality, review the relevant project specifications and engineering guidelines.

| Reference | Purpose |
|---|---|
| `spec/architecture.md` | Application architecture, module boundaries, and component responsibilities. |
| `spec/state-machine.md` | Ticket status definitions and transition policies. |
| `rules/api-standards.md` | REST API conventions, HTTP status codes, and JSON response standards. |
| `rules/rag-vector-store.md` | Vector database and PGVector implementation standards. |
| `rules/testing.md` | Unit testing, integration testing, and test coverage conventions. |
| `rag-ingestion.md` | RAG ingestion workflow and failure-handling requirements. |
| `api-contract.md` | API request and response contracts. |

---

## 11. Mandatory Instructions for AI Coding Assistants

When implementing any backend functionality, AI coding assistants must:

1. Read and understand the relevant architecture and specification documents before making changes.
2. Follow all applicable engineering rules defined in this document.
3. Inspect existing implementations before introducing new classes, methods, or dependencies.
4. Reuse existing components and utilities wherever appropriate.
5. Avoid modifying unrelated files or introducing unnecessary refactoring.
6. Maintain backward compatibility unless the requirement explicitly states otherwise.
7. Update or introduce unit and integration tests for the implemented functionality.
8. Verify compilation and relevant tests after implementation.
9. Highlight any conflicts between implementation requirements and existing project specifications instead of making assumptions.
10. Document any deviations from these rules with a clear technical justification.

**Enforcement:** These engineering rules are mandatory for all backend development activities, including new feature implementation, bug fixes, refactoring, code reviews, and AI-assisted code generation.