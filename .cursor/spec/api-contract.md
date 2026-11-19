# API Contract (Tickets & Comments)

**Status:** HTTP/JSON contract. No implementation.  
**Depends on:** `spec/requirements.md`, `spec/architecture.md`, `spec/data-model.md`, `spec/state-machine.md`  
**RAG query:** `spec/rag-api-contract.md` (`POST /api/v1/rag/query`)

**Source rule:** Endpoints and payloads below satisfy **Specified** requirements and **Design decisions (`API-D-*`)**. Auth, pagination, and delete are **Open** unless noted.

---

## 0. Classification

| Kind | Meaning |
|------|---------|
| **Requirement** | From `requirements.md` |
| **Decision (`API-D-*`)** | Chosen here for implementable HTTP contract |
| **Assumption (`API-A-*`)** | Needed for integration; not a product requirement |
| **Open (`API-Q-*`)** | Unspecified; do not treat as promised |

---

## 1. Conventions

**API-D-1:** Base path `/api/v1`. JSON request/response bodies. Field names **camelCase**.

**API-D-2:** Public ticket identifier in URLs is `ticketId` (`TKT-1001`), not internal `id` (DM-D-2, DM-A-2).

**API-A-1:** No authentication in v1 contract. All endpoints are open until `API-Q-1` is resolved.

| Header | Value |
|--------|--------|
| Request `Content-Type` | `application/json` (when body present) |
| Response `Content-Type` | `application/json` |

---

## 2. Common types

### 2.1 TicketStatus / TicketPriority

Enum strings match data model (case-sensitive):  
`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`  
`LOW`, `MEDIUM`, `HIGH`

### 2.2 Ticket response

```json
{
  "ticketId": "TKT-1001",
  "title": "CSV export corrupt on Windows",
  "description": "Export produces garbled characters.",
  "priority": "HIGH",
  "status": "OPEN",
  "assignee": "agent@example.com",
  "category": "billing",
  "resolutionNotes": null,
  "createdAt": "2026-09-08T10:00:00Z",
  "updatedAt": "2026-09-08T10:00:00Z"
}
```

| Field | In response | Notes |
|-------|-------------|--------|
| `ticketId` | yes | Server-generated (DM-D-1) |
| Internal `id` | **no** | Not exposed (DM-A-2) |

### 2.3 Comment response

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "ticketId": "TKT-1001",
  "author": "agent@example.com",
  "content": "Reproduced on Windows 11.",
  "createdAt": "2026-09-08T10:05:00Z"
}
```

**API-D-3:** `Comment.id` is a UUID string in JSON. **Open (`API-Q-2`):** integer IDs are acceptable if data model DM-Q-1 chooses integers — contract must be updated to match.

### 2.4 Error envelope

```json
{
  "error": {
    "code": "INVALID_STATUS_TRANSITION",
    "message": "Cannot transition from OPEN to CLOSED.",
    "details": []
  }
}
```

| HTTP | When | Example `code` |
|------|------|----------------|
| **400** | Validation failure, illegal transition | `VALIDATION_ERROR`, `INVALID_STATUS_TRANSITION` |
| **404** | Ticket not found | `TICKET_NOT_FOUND` |
| **409** | Conflict (optional; open) | `CONFLICT` |
| **500** | Unexpected server error | `INTERNAL_ERROR` |

**API-D-4:** Illegal status transitions return **400** with `INVALID_STATUS_TRANSITION` (SM-1, VAL-1). Exact copy is not a product requirement.

**API-A-2:** Error shape is consistent across ticket APIs. RAG uses its own envelope in `rag-api-contract.md`.

---

## 3. Tickets

### 3.1 Create ticket

`POST /api/v1/tickets`

**Request** (client must **not** send `ticketId` or `status`):

```json
{
  "title": "CSV export corrupt on Windows",
  "description": "Export produces garbled characters.",
  "priority": "HIGH",
  "assignee": "agent@example.com",
  "category": "billing"
}
```

| Field | Required | Notes |
|-------|----------|--------|
| `title` | **Open (`API-Q-3`)** | Recommend required |
| `description` | Open | |
| `priority` | Open | Default `MEDIUM` if omitted (**API-D-5**, closes DM-Q-3 partially) |
| `assignee` | Open | |
| `category` | Open | |
| `resolutionNotes` | no | Usually empty at create |

**Response:** `201 Created` + ticket body with `status: "OPEN"` (SM-D-3).

### 3.2 Get ticket

`GET /api/v1/tickets/{ticketId}`

**Response:** `200` + ticket body, or `404`.

### 3.3 List tickets

`GET /api/v1/tickets`

**API-D-6:** v1 supports optional query filters:

| Query param | Type | Example |
|-------------|------|---------|
| `status` | TicketStatus | `OPEN` |
| `priority` | TicketPriority | `HIGH` |
| `assignee` | string | `agent@example.com` |
| `category` | string | `billing` |

**Open (`API-Q-4`):** pagination (`page`, `size`), sort order, and max page size.

**Response:** `200` + JSON array of tickets (may be empty).

### 3.4 Update ticket fields

`PATCH /api/v1/tickets/{ticketId}`

Update content/metadata fields **without** changing status through this endpoint.

```json
{
  "title": "Updated title",
  "description": "Updated description",
  "priority": "MEDIUM",
  "assignee": "other@example.com",
  "category": "exports",
  "resolutionNotes": "Fixed in v2.1"
}
```

All fields optional; omitted fields unchanged.

**API-D-7:** `status` is **not** accepted on this endpoint. Use §3.5.

**Response:** `200` + updated ticket, or `404` / `400`.

Successful update triggers embedding refresh per `rag-ingestion.md` §7.

---

## 4. Status transition

`PATCH /api/v1/tickets/{ticketId}/status`

**Requirement:** API-1, FR-6, SM-1.

```json
{
  "status": "IN_PROGRESS"
}
```

| Rule | Behavior |
|------|----------|
| `from` status | Loaded from persistence only (SM-D-1) |
| Same-state | Rejected (SM-D-2) |
| Illegal edge | `400` `INVALID_STATUS_TRANSITION`; ticket unchanged |
| Legal edge | `200` + updated ticket |

Allowed transitions: `state-machine.md` §2.1.

---

## 5. Comments

### 5.1 Add comment

`POST /api/v1/tickets/{ticketId}/comments`

```json
{
  "author": "agent@example.com",
  "content": "Customer confirmed fix."
}
```

| Field | Required |
|-------|----------|
| `author` | Open (`API-Q-5`) — recommend required |
| `content` | recommend required |

**Response:** `201 Created` + comment body.

**404** if ticket does not exist. Comment does not change ticket status (SM-D-4).

Successful comment triggers embedding refresh per `rag-ingestion.md` §7 (comments in payload).

### 5.2 List comments

`GET /api/v1/tickets/{ticketId}/comments`

**Response:** `200` + JSON array ordered by `createdAt` ascending.

**Open (`API-Q-6`):** comment update/delete.

---

## 5.3 Dashboard summary

**API-D-9 (UI-D-1):** `GET /api/v1/dashboard/summary`

Admin UI metrics; read-only aggregation. No auth in v1 (API-A-1).

**Response:** `200` + JSON:

```json
{
  "totalTickets": 42,
  "byStatus": [
    { "status": "OPEN", "count": 12 },
    { "status": "IN_PROGRESS", "count": 8 },
    { "status": "RESOLVED", "count": 0 },
    { "status": "CLOSED", "count": 0 },
    { "status": "CANCELLED", "count": 0 }
  ],
  "byAssignee": [
    { "assignee": "agent@example.com", "count": 15 },
    { "assignee": null, "count": 3 }
  ],
  "recentTickets": []
}
```

| Field | Rule |
|-------|------|
| `byStatus` | Always **all five** `TicketStatus` values; `count` may be 0 |
| `byAssignee` | `GROUP BY assignee`; `assignee: null` = unassigned |
| `recentTickets` | Ticket bodies same as §3.2; **UI-D-2:** max **10**, `createdAt` descending |

---

## 6. RAG

Not defined here. See `spec/rag-api-contract.md`:

* `POST /api/v1/rag/query` — grounded answer or `NO_MATCH`

---

## 7. Validation rules (minimum)

| Rule | Layer |
|------|--------|
| Unknown enum value for `status` / `priority` | API boundary (`@Valid` / custom) → 400 |
| Blank `title` / `content` if marked required | API boundary → 400 |
| `ticketId` on create | Reject or ignore client value (DM-D-1) |
| Transition graph | Application service + state machine → 400 |

Field max lengths: **Open (`API-Q-7`)** — recommend 255 title, 10_000 description/comment until specified.

---

## 8. Assumptions

| ID | Assumption |
|----|------------|
| API-A-1 | No auth in v1 |
| API-A-2 | Ticket APIs share one error envelope |
| API-A-3 | Timestamps are ISO-8601 UTC in JSON |

---

## 9. Open questions

| ID | Question |
|----|----------|
| API-Q-1 | Authentication and authorization |
| API-Q-2 | Comment `id` type (UUID vs integer) |
| API-Q-3 | Required fields on create |
| API-Q-4 | Pagination and sorting for list |
| API-Q-5 | Required comment fields |
| API-Q-6 | Comment update/delete |
| API-Q-7 | Max string lengths |
| API-Q-8 | Ticket delete/archive |

---

## 10. Traceability

| Requirement | Contract |
|-------------|----------|
| API-1, FR-6, SM-1 | §4 status endpoint |
| API-2 | `rag-api-contract.md` |
| API-3 | Retrieval config (not per-request) |
| FR-2–FR-5 | Status values and transitions |
| FR-11 | PATCH/POST success → ingestion (internal) |
| DM-D-1, DM-D-2 | `ticketId` in paths and responses |
| T-1, T-2 | Testable via §4 + state machine |
