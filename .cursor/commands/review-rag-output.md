# RAG Output Review Command

## Objective

Review a generated RAG answer against the retrieved knowledge-base context and determine whether the answer is grounded, correct, and supported by the sources.

This command is used after retrieval and generation to catch hallucinations, factual errors, and source–answer conflicts before returning a response to the client.

## Review Inputs

For each RAG response under review, collect:

| Input | Description |
|-------|-------------|
| **User question** | The original query submitted by the user |
| **Retrieved context** | Full text of all kept chunks passed to the generator (including document titles if present) |
| **Generated answer** | Final answer text returned by the AI |
| **Citations** | `citations[].ticketId` and any cited document metadata |

Also consult:

* `spec/requirements.md` (FR-8, FR-9, FR-10, AI-R-2)
* `spec/rag-api-contract.md` (§8.1 generator grounding instructions)
* `commands/review-ai.md` (§5 Groundedness, §5.1 Answer–Context–Citation Conflict Review)

## Review Process

### 1. Source–Answer Alignment

Compare the generated answer to the retrieved context.

**Mandatory checks:**

1. **Contradiction:** Does the answer state facts that conflict with retrieved context?
2. **Unsupported claim:** Does the answer introduce facts not present in retrieved context?
3. **Grounding:** Can the answer be derived from retrieved context alone (FR-8)?
4. **Citation support:** Do cited tickets' retrieved chunks actually support the claims made?
5. **Hallucination risk:** Does the answer appear to use parametric model knowledge instead of retrieved sources?

### 2. Factual Claim Extraction

For policy, duration, status, and numeric questions, extract concrete claims from both:

* **Retrieved context** → expected factual basis
* **Generated answer** → stated factual claims

If claims conflict (e.g. context says **30 minutes**, answer says **24 hours**), the review **must fail**.

### 3. Programmatic Check

When available, run:

```text
RagOutputReviewer.review(userQuestion, generatedAnswer, retrievedContext)
```

Treat `passed == false` / `result == "FAIL"` as a review failure.

Underlying groundedness validation is performed by `RagAnswerGroundingReviewer.review(answerText, retrievedContext)`.

### 4. Regression Scenario (required)

| Field | Value |
|-------|-------|
| Document | Password Reset Policy |
| Retrieved context | Password reset links expire after 30 minutes. |
| User question | How long is a password reset link valid? |
| Incorrect answer | The password reset link is valid for 24 hours. |
| Correct answer | Password reset links expire after 30 minutes. |

**Incorrect answer review must produce:**

```text
Expected: 30 minutes
Generated: 24 hours
Result: FAIL
Reason: The generated answer conflicts with the retrieved knowledge-base context.
```

**Correct answer review must produce:**

```text
Expected: 30 minutes
Generated: 30 minutes
Result: PASS
Reason: The generated answer is grounded in the retrieved knowledge-base context.
```

## Review Output

Return the review in this format:

### Summary

* Overall status: PASS / FAIL
* Grounded: yes / no
* Hallucination risk: low / medium / high

### Comparison

```text
User question:
<question>

Retrieved context:
<context excerpt or full text>

Generated answer:
<answer>

Expected (from context):
<expected factual basis>

Generated claim:
<claim extracted from answer>

Result:
PASS | FAIL

Reason:
<why the review passed or failed>
```

### Findings

For each issue, list:

* Issue type: `CONTEXT_CONFLICT` | `UNSUPPORTED_CLAIM` | `POTENTIAL_HALLUCINATION`
* Description
* Answer excerpt
* Context excerpt

### Final Recommendation

* **PASS** — answer is grounded and supported by retrieved context
* **FAIL** — answer contradicts, is unsupported by, or is not grounded in retrieved context; do not return as a confident `ANSWER` without remediation

## Rules

* Do not modify code during the review.
* Do not invent requirements.
* The incorrect answer must exist only in test/mock fixtures, never in production behavior.
* Prefer explicit `NO_MATCH` or rejection over returning an ungrounded `ANSWER`.
