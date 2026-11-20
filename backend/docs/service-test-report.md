# Service layer test report

**Last run:** 2026-11-20 (UTC+5:30)  
**Command:** `cd backend && mvn test`  
**Result:** **PASS** — 39 tests, 0 failures, 0 errors, 0 skipped  

Machine-readable Surefire output: [`backend/target/surefire-reports/`](../target/surefire-reports/) (`TEST-*.xml`, `*.txt`).

## Summary by service

| Service | Method | Test class | Test methods |
|---------|--------|------------|--------------|
| `TicketService` | `create` | `TicketServiceTest` | `createSavesOpenTicketAndTriggersIngest`, `createDefaultsNullPriorityToMedium` |
| `TicketService` | `getByTicketId` | `TicketServiceTest` | `getByTicketIdReturnsTicketWhenFound`, `getByTicketIdThrowsWhenMissing` |
| `TicketService` | `list` | `TicketServiceTest` | `listDelegatesToRepository` |
| `TicketService` | `updateFields` | `TicketServiceTest` | `updateFieldsAppliesPartialUpdatesAndTriggersIngest` |
| `TicketService` | `changeStatus` | `TicketServiceTest` | `changeStatusAllowsLegalTransitionAndSaves`, `changeStatusRejectsIllegalTransitionWithoutSave` |
| `CommentService` | `addComment` | `CommentServiceTest` | `addCommentSavesWhenTicketExists`, `addCommentThrowsWhenTicketMissing` |
| `CommentService` | `listComments` | `CommentServiceTest` | `listCommentsReturnsOrderedList`, `listCommentsThrowsWhenTicketMissing` |
| `DashboardService` | `getSummary` | `DashboardServiceTest` | `getSummaryAggregatesStatusAssigneeAndRecentTickets` |
| `TicketIdGenerator` | `nextTicketId` | `TicketIdGeneratorTest` | `nextTicketIdIncrementsSequenceAndFormatsId` |
| `RagRetrievalService` | `retrieve` | `RagRetrievalServiceTest` | `retrieveAppliesTopKThresholdAndSortsBySimilarity`, `retrieveReturnsEmptyWhenCorpusEmpty` |
| `RagQueryService` | `query` | `RagQueryServiceTest` | `queryBlankReturnsInvalidQueryError`, `queryEmptyRetrievalReturnsNoMatch`, `queryFailedReviewReturnsNoMatch`, `queryInvalidCitationsReturnNoMatch`, `queryHappyPathReturnsAnswer`, `queryEmbeddingFailureReturnsError`, `queryGenerationFailureReturnsError` |
| `TicketIngestionService` | `ingest` | `TicketIngestionServiceTest` | `ingestThrowsForUnknownTicket`, `ingestDeletesVectorsWhenNoUsableContent`, `ingestEmbedsChunksAndReplacesVectors` |
| `TicketIngestionService` | `afterTicketPersisted` | `TicketIngestionServiceTest` | `afterTicketPersistedSwallowsIngestFailures` |

## Requirement traceability (service tests)

| ID | Coverage |
|----|----------|
| T-1 | Legal status transition in `TicketServiceTest.changeStatusAllowsLegalTransitionAndSaves` |
| T-2 | Illegal transition in `TicketServiceTest.changeStatusRejectsIllegalTransitionWithoutSave` |
| T-4 | Empty retrieval in `RagQueryServiceTest.queryEmptyRetrievalReturnsNoMatch` |
| T-6 | Threshold/top-k behavior in `RagRetrievalServiceTest` (+ `RagRetrievalServicePolicyLiteralsTest`) |
| T-7 | Review failure → no match in `RagQueryServiceTest.queryFailedReviewReturnsNoMatch` |

## Full suite (context)

Service unit tests above are part of the full backend suite (API, domain, RAG review). Re-run `mvn test` after changes to refresh Surefire artifacts and update the counts in this file if needed.