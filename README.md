# AI-Powered Support Ticket Management System

Backend (Spring Boot 3.5 / Java 17) and frontend SPA implementing specs under `.cursor/spec/` and rules under `.cursor/rules/`.

## Prerequisites

- Java 17+, Maven 3.9+
- PostgreSQL 15+ with [pgvector](https://github.com/pgvector/pgvector)
- Node.js 20+ (frontend)
- [Ollama](https://ollama.com/) for local embeddings/chat in `dev` (optional; tests use deterministic stubs)

## Backend

```bash
cd backend
# Create DB: support_ticket_ai
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Default API: `http://localhost:8080/api/v1`

### Tests

```bash
cd backend && mvn test
```

Uses H2 + in-memory vector store (`spring.profiles.active=test`).

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