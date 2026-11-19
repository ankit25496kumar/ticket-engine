# UI Flow

**Status:** Logical UI flow only. No frontend implementation.  
**Depends on:** `spec/requirements.md`, `spec/api-contract.md`, `spec/rag-api-contract.md`, `spec/data-model.md`, `spec/state-machine.md`

**Source rule:** Screens and actions below follow **Specified** ticket lifecycle and RAG behavior. Auth, roles, styling, and frontend stack are **Open**.

---

## 1. Purpose

Describe how a user (role unspecified) interacts with the support ticket system and RAG query feature over the HTTP APIs defined in the specs.

**UI-A-1:** The frontend in `frontend/` is an HTTP client of `/api/v1/*`. No auth UI until `API-Q-1` is resolved.

---

## 2. Primary user goals

| Goal | API backing |
|------|-------------|
| View and manage tickets | `api-contract.md` §3–§5 |
| Move tickets through lifecycle | `PATCH .../status` |
| Ask questions about past tickets (RAG) | `POST /api/v1/rag/query` |

Requirements do not name user roles (agent vs customer). Flows assume a single generic user with full access (**Open `UI-Q-1`**).

---

## 3. Screen map

**UI-D-3:** Mobile-first **admin shell** (sidebar + header). **UI-D-1:** Default route is **Dashboard** (`GET /api/v1/dashboard/summary`).

```
┌──────────────────────────────────────────────────────────────┐
│ Admin shell: sidebar (drawer on mobile) + header             │
└──────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Dashboard      │────▶│  Ticket List     │────▶│  Ticket Detail  │
│  KPI + charts   │     │  /tickets        │     │  + comments     │
│  recent tickets │     └────────┬─────────┘     └────────┬────────┘
└─────────────────┘              │                        │
         │                       │              ┌─────────┴─────────┐
         │                       │              │ Change Status     │
         │                       ▼              └───────────────────┘
         │              ┌──────────────────┐
         └─────────────▶│  Create Ticket   │
                        └──────────────────┘
         ┌──────────────────┐
         │  Ask AI (RAG)    │
         └──────────────────┘
```

---

## 4. Flows

### 4.0 Dashboard (admin home)

**Entry:** `/` (default).

**Loads:** `GET /api/v1/dashboard/summary`.

**Displays:** Total tickets; counts by status (chart); counts by assignee (chart); up to 10 recent tickets.

**Actions:** Links to filtered ticket list (`?status=…`); navigate to ticket detail; refresh summary.

**Responsive:** KPI stack on mobile; charts full width; sidebar via hamburger menu (**UI-D-3**).

### 4.1 Ticket list

**Entry:** `/tickets` (sidebar or dashboard links).

**Displays:** Table or list of tickets (`GET /api/v1/tickets`).

| Column (suggested) | Source field |
|--------------------|--------------|
| ID | `ticketId` |
| Title | `title` |
| Status | `status` |
| Priority | `priority` |
| Assignee | `assignee` |
| Created | `createdAt` |

**Actions:**

* Filter by `status`, `priority`, `assignee`, `category` (API-D-6)  
* Open ticket detail  
* Navigate to create ticket  
* Navigate to RAG ask screen  

**Open (`UI-Q-2`):** Pagination controls.

---

### 4.2 Create ticket

**Form fields** (from data model): `title`, `description`, `priority`, `assignee`, `category`.

**Submit:** `POST /api/v1/tickets`.

**On success:** Navigate to ticket detail. Ticket shows `status: OPEN` (SM-D-3). Server assigns `ticketId` (`TKT-1001` format).

**On validation error:** Show API error message.

Client must **not** send `ticketId` or `status`.

---

### 4.3 Ticket detail

**Load:** `GET /api/v1/tickets/{ticketId}` + `GET .../comments`.

**Displays:**

* Ticket fields  
* Comment thread (chronological)  
* Current status  

**Actions:**

* Edit ticket fields → `PATCH /api/v1/tickets/{ticketId}`  
* Change status (see §4.4)  
* Add comment (see §4.5)  

---

### 4.4 Change status

**UI rule:** Only show transitions allowed from current status (`state-machine.md` §2.1). Hiding illegal buttons is UX only — backend still enforces (FR-6).

| Current status | Allowed actions (labels) |
|----------------|----------------------------|
| `OPEN` | Start progress → `IN_PROGRESS`; Cancel → `CANCELLED` |
| `IN_PROGRESS` | Resolve → `RESOLVED`; Cancel → `CANCELLED` |
| `RESOLVED` | Close → `CLOSED` |
| `CLOSED` | None (terminal) |
| `CANCELLED` | None (terminal) |

**Submit:** `PATCH /api/v1/tickets/{ticketId}/status` with `{ "status": "..." }`.

**On illegal transition (400):** Show error; refresh ticket to show unchanged status.

**Optional:** `resolutionNotes` field when resolving — **Open (`UI-Q-3`)** whether required.

---

### 4.5 Add comment

**Form:** `author`, `content`.

**Submit:** `POST /api/v1/tickets/{ticketId}/comments`.

**On success:** Append to comment list. Does not change ticket status.

---

### 4.6 Ask AI (RAG)

**Entry:** Dedicated “Ask” screen or panel.

**Input:** Free-text question (`query`).

**Submit:** `POST /api/v1/rag/query` with `{ "query": "..." }`.

**Outcomes** (`rag-api-contract.md`):

| `outcome` | UI behavior |
|-----------|-------------|
| `ANSWER` | Show `answer.text` and citation links from `citations[].ticketId` (+ `title` for display). Citations link to ticket detail. |
| `NO_MATCH` | Show explicit no-match message (not an error toast). `escalation.recommended` may show “escalate” hint if true. HTTP 200. |
| `ERROR` | Show error from `error` object. HTTP 400 or 503. |

**UI must not:**

* Fabricate an answer when `NO_MATCH`  
* Hide citations on `ANSWER`  
* Treat `NO_MATCH` as a generic server failure  

**Open (`UI-Q-4`):** Whether RAG is global or scoped to current ticket.

---

## 5. Error display (common)

| HTTP | User message |
|------|--------------|
| 400 validation | Field-level or `error.message` |
| 400 `INVALID_STATUS_TRANSITION` | Transition not allowed |
| 404 | Ticket not found |
| 503 RAG `ERROR` | AI temporarily unavailable |

---

## 6. Assumptions

| ID | Assumption |
|----|------------|
| UI-A-1 | SPA or MPA calling JSON APIs |
| UI-A-2 | Single user type with full access until roles are specified |
| UI-A-3 | RAG is a separate screen, not inline on every ticket field |

---

## 7. Open questions

| ID | Question |
|----|----------|
| UI-Q-1 | User roles and permissions |
| UI-Q-2 | Pagination on ticket list |
| UI-Q-3 | Required `resolutionNotes` on resolve |
| UI-Q-4 | Global vs ticket-scoped RAG |
| UI-Q-5 | Frontend framework (React, Vue, etc.) |
| UI-Q-6 | Mobile / responsive requirements |

---

## 8. Out of scope

* Visual design system  
* Authentication screens  
* Admin configuration UI for `top-k` / threshold  
* Real-time notifications  

---

## 9. Traceability

| Requirement | UI flow |
|-------------|---------|
| FR-2–FR-6 | §4.4 status actions |
| FR-7–FR-10 | §4.6 RAG outcomes |
| FR-9 | §4.6 citations |
| API-2 | §4.6 |
| SM-D-2, SM-D-3 | §4.2, §4.4 |
