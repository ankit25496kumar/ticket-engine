# State Machine

**Status:** Backend transition design only. No application code, no JPA entities, no controllers.  
**Depends on:** `spec/requirements.md`, `spec/architecture.md`, `spec/data-model.md`  
**Does not decide:** HTTP paths, error JSON, who is allowed to transition (auth), RAG corpus rules for `CLOSED` / `CANCELLED`.

**Source rule:** Allowed edges are **Specified** (FR-3–FR-6). Placement of validation follows architecture. Initial status, same-state, and “trust persisted status only” are **Design decisions** labeled `SM-D-*`. Unspecified actors, reasons, and history remain **Open**.

---

## 1. Purpose

Every change to `Ticket.status` must go through a backend state-transition mechanism that:

| ID | Rule |
|----|------|
| FR-6 / SM-1 | Rejects invalid transitions; the UI is not sufficient |
| DM-D-3 | Uses enum `TicketStatus` values only |
| DM-I-4 | Does not persist an illegal new status |
| DM-D-5 | Is not triggered by creating comments |

The database enum/check constraint only blocks **unknown values**. It cannot encode `OPEN → CLOSED` as illegal. That graph lives in application code.

---

## 2. States and graph

States (DM-D-3): `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`.

### 2.1 Allowed transitions (specified)

| From | To | Notes |
|------|----|--------|
| `OPEN` | `IN_PROGRESS` | Happy path |
| `IN_PROGRESS` | `RESOLVED` | Happy path |
| `RESOLVED` | `CLOSED` | Happy path |
| `OPEN` | `CANCELLED` | Cancel |
| `IN_PROGRESS` | `CANCELLED` | Cancel |

```
                  ┌──────────────┐
                  │    OPEN      │
                  └───┬──────┬───┘
                      │      │
          IN_PROGRESS │      │ CANCELLED
                      ▼      ▼
              ┌───────────┐  ┌───────────┐
              │IN_PROGRESS│  │ CANCELLED │  (terminal)
              └───┬────┬──┘  └───────────┘
                  │    │
         RESOLVED │    │ CANCELLED
                  ▼    ▼
            ┌──────────┐  ┌───────────┐
            │ RESOLVED │  │ CANCELLED │
            └────┬─────┘  └───────────┘
                 │ CLOSED
                 ▼
            ┌──────────┐
            │  CLOSED  │  (terminal)
            └──────────┘
```

### 2.2 Design decisions that close requirement gaps

| ID | Decision |
|----|----------|
| **SM-D-1** | The mechanism evaluates `persistedStatus → requestedStatus`. The client must not supply a trusted “from” status. If it sends one, the backend ignores it or rejects mismatch — exact HTTP behavior is deferred to the API contract. |
| **SM-D-2** | Same-state requests (`OPEN → OPEN`, etc.) are **invalid**. They are not no-ops. |
| **SM-D-3** | On **create**, `status` is set to `OPEN` by the backend. Create is not a transition. Clients do not choose the initial status. |
| **SM-D-4** | Comment create/update does not call the state machine and must not write `Ticket.status`. |
| **SM-D-5** | Terminal states: `CLOSED` and `CANCELLED` have **no** outbound edges. |
| **SM-D-6** | The allowed set is **closed**. Any pair not in §2.1 is invalid (including skips and reverse moves). |

### 2.3 Full matrix (5 × 5)

`A` = allow and persist. `R` = reject; ticket unchanged.

| From \ To | OPEN | IN_PROGRESS | RESOLVED | CLOSED | CANCELLED |
|-----------|------|-------------|----------|--------|-----------|
| OPEN | R | A | R | R | A |
| IN_PROGRESS | R | R | A | R | A |
| RESOLVED | R | R | R | A | R |
| CLOSED | R | R | R | R | R |
| CANCELLED | R | R | R | R | R |

Skip examples (all `R`): `OPEN → RESOLVED`, `OPEN → CLOSED`, `IN_PROGRESS → CLOSED`.  
Reverse examples (all `R`): `IN_PROGRESS → OPEN`, `RESOLVED → IN_PROGRESS`, `CLOSED → *`, `CANCELLED → *`.  
Cancel too late (all `R`): `RESOLVED → CANCELLED`, `CLOSED → CANCELLED`.

---

## 3. Mechanism (logical)

A single **transition policy** is the source of truth for the graph. It is a pure function:

```
allow(from: TicketStatus, to: TicketStatus) -> boolean
```

or equivalently `assertAllowed(...)` that throws/fails a domain error when `R`.

The policy contains **only** the matrix in §2.3. It does not load tickets, send HTTP, or refresh embeddings.

### 3.1 Status-change flow

```
Client
  → API boundary          (parse body; reject unknown enum names — not the graph)
  → Ticket application service
       1. Load ticket by ticketId (TKT-1001). If missing → not-found (API contract).
       2. from := ticket.status          // persisted only (SM-D-1)
       3. to   := requested TicketStatus
       4. Transition policy.allow(from, to)
       5. If false → reject; do not save; do not ingest
       6. If true  → ticket.status := to; persist
       7. RAG metadata/embeddings: only if a later ingestion spec treats status as a refresh trigger
```

### 3.2 Create flow

```
Client (no status / status ignored)
  → API boundary
  → Ticket application service
       set status := OPEN (SM-D-3)
       persist
       (not a transition; policy not consulted)
```

If a create payload includes a status other than `OPEN` (or includes status at all), the API contract must reject or ignore it. The state machine spec requires the stored value to be `OPEN`.

---

## 4. Where validation belongs

Enforcement is **layered**. Only one layer owns the **graph**.

| Layer | What it validates | Owns the graph? |
|-------|-------------------|-----------------|
| **Transition policy** (domain, backend) | `from → to` against §2.3 | **Yes. This is the mechanism.** |
| **Ticket application service** | Loads persisted `from`, calls policy **before** `save`, maps failure to a domain/API error | No; it **must call** the policy on every status write |
| **API boundary** | JSON shape; value is a known `TicketStatus` enum; `ticketId` present | No. Bean Validation / `@Valid` only stops `"FOO"`, not `OPEN → CLOSED` |
| **Persistence** | Column is one of five enum values (DM-D-3) | No |
| **Frontend** (later) | May hide illegal buttons | **No.** FR-6: client-only checks are not sufficient |
| **Comments** | Must not change status | N/A (SM-D-4) |

### 4.1 Must not

- Put the matrix only in a `@Controller` `if`/`switch` with no reusable policy (other write paths would skip it).
- Persist first and “validate” after.
- Trust `from` on the request body as the current state (SM-D-1).
- Encode the graph as extra enum constants or as a SQL CHECK of pairs unless that CHECK is generated from the **same** policy (optional hardening; not a substitute).
- Allow JPA/`save` of a ticket entity that already has an illegal `status` mutation without going through the service+policy.

When entities exist: any code path that sets `Ticket.status` after create must go through the application service (or a domain `Ticket.transitionTo(to)` that calls the same policy). Direct field sets in controllers are out of bounds.

### 4.2 Failure behavior

| Outcome | Persist | Embeddings | Client |
|---------|---------|------------|--------|
| Allowed transition | New `status` (+ `updatedAt`) | Per RAG ingestion spec | Success (API contract) |
| Invalid transition | **No** | **No** | Backend rejection (status code/body: API contract) |
| Unknown status string | **No** | **No** | Request validation error (not a transition error) |
| Ticket not found | **No** | **No** | Not found (API contract) |

HTTP mapping is **not** defined here.

### 4.3 Concurrency (open, with a minimum rule)

Requirements did not choose optimistic locking. **Minimum for SM-1:** always re-read `status` from the store in the same use-case that validates.

**Open (SM-Q-1):** two concurrent allowed transitions from the same `from` (e.g. `OPEN → IN_PROGRESS` and `OPEN → CANCELLED`) — last-write-wins vs `version`/`updatedAt` conflict. This spec does not add a `version` column (not in the data model). If races matter, a later change may add optimistic locking; it is not required to implement the matrix.

### 4.4 Out of this mechanism

- Who may call the API (auth).
- Required `resolutionNotes` on `RESOLVED` (DM-Q-6).
- Transition history table.
- RAG index eligibility by status (DM-Q-10).

---

## 5. How it will be tested

No implementation yet. Tests below are the contract for later `spec/test-strategy.md` and T-1 / T-2 in requirements.

### 5.1 Unit tests — transition policy (must exist)

Pure tests; **no Spring, no database**. Table-driven over the **entire** 5×5 matrix in §2.3 (25 cases).

| Test | Expect |
|------|--------|
| Each `A` cell | `allow(from, to) == true` |
| Each `R` cell | `allow(from, to) == false` (or named domain error) |
| Same-state for all five statuses | Reject (SM-D-2) |

This is the cheapest proof that skip, reverse, and terminal lockout are correct. If the matrix is encoded as a set of pairs, tests should still list pairs explicitly so a deleted edge fails a test.

### 5.2 Application-service tests (must exist)

Mock or in-memory ticket store. **Policy is real** (not mocked away — otherwise the graph is untested at the orchestration boundary). Alternatively mock the policy **and** keep §5.1; then add at least one service test that a rejected policy means **`save` is not called**.

| Test | Expect |
|------|--------|
| Ticket `OPEN`, request `IN_PROGRESS` | Saved with `IN_PROGRESS` |
| Ticket `OPEN`, request `CLOSED` | Rejected; store still `OPEN`; save not called |
| Ticket `CLOSED`, request `OPEN` | Rejected; unchanged |
| Ticket `IN_PROGRESS`, request `CANCELLED` | Saved `CANCELLED` |
| Ticket `RESOLVED`, request `CANCELLED` | Rejected |
| Missing ticket | Not-found; policy irrelevant |
| Create ticket | Persisted `OPEN` without calling `allow` (or `allow` never used for create) |

### 5.3 API / slice tests (when API contract exists)

HTTP tests against the status-change endpoint (path TBD):

| Test | Expect |
|------|--------|
| Allowed body | 2xx; GET shows new status |
| Illegal pair (e.g. `OPEN` → `CLOSED`) | 4xx from **backend**; GET still old status |
| Body `"status": "NOPE"` | 4xx validation; not a transition-matrix error |
| Illegal transition not recoverable by calling a “private” persist URL | No such URL, or it still uses the service |

Do not treat a UI test as fulfilling FR-6.

### 5.4 Not sufficient as the only tests

- Checking that the enum has five values.
- Checking JSON deserialization.
- Frontend tests that hide buttons.

### 5.5 Traceability

| Requirement test ID | Covered by |
|---------------------|------------|
| T-1 | §5.1 `A` cells + §5.2 happy paths |
| T-2 | §5.1 `R` cells + §5.2/`§5.3` reject-and-not-persist |

---

## 6. Implementation notes (for later; do not do now)

When code is written (Java / Spring):

- `TicketStatus` enum (DM-D-3).
- `TicketStatusTransitionPolicy` (or similar) with the allow-set; **no** `@Component` behavior beyond being injectable.
- `TicketService.changeStatus(ticketId, to)` loads entity, calls policy, saves.
- Map policy failure to a dedicated exception type so the API layer can translate it later.
- Do not generate controllers in this step.

Package placement is unspecified; keep the policy out of `web`.

---

## 7. Open questions

| ID | Question |
|----|----------|
| SM-Q-1 | Optimistic locking vs last-write-wins for concurrent valid transitions. |
| SM-Q-2 | AuthZ: who may transition. |
| SM-Q-3 | Required reason / `resolutionNotes` on `RESOLVED` / `CANCELLED`. |
| SM-Q-4 | Persist transition history. |
| SM-Q-5 | HTTP status and error body for `R` (API contract). |
| SM-Q-6 | Whether status-only updates refresh RAG metadata (data model §7.3 says metadata `status` should be updated; embedding recompute is ingestion). |

---

## 8. Traceability

| Requirement / decision | This spec |
|------------------------|-----------|
| FR-3, FR-4, FR-5 | §2.1 allowed edges |
| FR-6, SM-1, VAL-1 | §3–§4 reject before persist |
| T-1, T-2 | §5 |
| DM-D-3 | Enum values; graph not in the DB |
| DM-D-5 | Comments do not transition |
| ARCH §5.1 | Matches load → policy → persist/reject |
| A-3 | Unlisted edges invalid (§2.2 SM-D-6) |
