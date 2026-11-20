# AI-Powered Support Ticket Management System

Backend (Spring Boot 3.5 / Java 17) and frontend SPA implementing specs under `.cursor/spec/` and rules under `.cursor/rules/`.

## Prerequisites

- Java 17+, Maven 3.9+
- PostgreSQL 14+ (15+ recommended); [pgvector](https://github.com/pgvector/pgvector) optional for local **dev** (in-memory vectors by default)
- Node.js 20+ (frontend)
- [Ollama](https://ollama.com/) for local embeddings/chat in **dev** (required for Ask AI; tests use deterministic stubs)

Before using RAG (`POST /api/v1/rag/query`) or Ask AI in dev:

```bash
ollama pull nomic-embed-text
ollama pull llama3.2
```

If tickets were seeded before models were available, restart the backend with the `dev` profile—it will auto re-ingest when `ticket_chunk` is empty.

## Backend

```bash
cd backend
# Create DB: support_ticket_ai
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The **dev** profile sets `rag.vector-store=in-memory` so RAG works **without** installing the pgvector extension. Vectors are rebuilt on each dev start via `DevRagReindexRunner`.

To use PostgreSQL/pgvector instead (persistent vectors), install pgvector for your Postgres version, then in `application-dev.yaml` set `rag.vector-store: pgvector`, enable Flyway (`baseline-on-migrate: true`, `baseline-version: 2`), and apply `V3__pgvector_ticket_chunk.sql`. If a previous Flyway run failed on V3, repair with `flyway repair` or remove the failed row from `flyway_schema_history` before restarting.

On first startup with an empty database, the **dev** profile runs `DevDataSeeder`, which inserts sample tickets (mixed statuses, assignees, and a password-reset ticket for Ask AI). Restarting with existing data skips seeding.

Default API: `http://localhost:8080/api/v1`

### Tests

```bash
cd backend && mvn test
```

Uses H2 + in-memory vector store (`spring.profiles.active=test`).

Service-layer method coverage summary: [backend/docs/service-test-report.md](backend/docs/service-test-report.md). Surefire XML/TXT: `backend/target/surefire-reports/`.

### Configuration

See `backend/src/main/resources/application.yaml` — `rag.retrieval.top-k` (default **5**), `similarity-threshold` (**0.72**), Ollama URLs/models.

## Frontend

```bash
cd frontend
npm install
npm run dev
```

Set `VITE_API_BASE_URL=http://localhost:8080` if needed (default).

**Admin UI:** Default route `/` is a **dashboard** (KPIs, Recharts status/assignee charts, recent tickets) backed by `GET /api/v1/dashboard/summary`. Ticket list is at `/tickets`. Layout is mobile-first with a collapsible sidebar.

## Prompt history

Strict user-prompt log: [prompt-history.md](prompt-history.md). Archive: `.cursor/docs/prompt-history.md`.

## Spec traceability

Implementation follows `.cursor/docs/implementation-plan.md` task acceptance criteria and requirements **T-1–T-7** / **FR-1–FR-13**.