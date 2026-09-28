# Claim Service API scenarios

This catalog is based on the Swagger screenshots and the Postman notes in this repository. These are test cases to run against the service; documented examples and the earlier observations are not treated as verified runtime behavior. The documented routes are:

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/v1/claims` | List and filter claims |
| POST | `/v1/claims` | Create a claim |
| GET | `/v1/claims/{id}` | Read a claim |
| PATCH | `/v1/claims/{id}` | Update a claim |
| DELETE | `/v1/claims/{id}` | Delete a claim |
| GET | `/v1/claims/{claimId}/payouts` | List payouts for a claim |
| GET | `/v1/payouts/{id}` | Read a payout |

## Criticality

| Level | Meaning |
| --- | --- |
| **P0 – Critical** | An unauthenticated caller can read or change claim/payout data, or a core payout/claim invariant is broken. Block release until resolved. |
| **P1 – High** | A core operation, validation rule, or important response is incorrect, but it does not expose or mutate data without authorization. Fix before release where possible. |
| **P2 – Medium** | Boundary, pagination, or error handling is inconsistent without compromising data or core money movement. |

## Happy paths

| ID | Scenario | Expected result | Criticality |
| --- | --- | --- | --- |
| HP-01 | With a valid bearer token, create a claim with a positive amount and EUR currency. | Success response contains an ID and the submitted fields; save the ID for follow-up. Record the actual status because create has no documented status input. | P1 |
| HP-02 | Read the newly created claim by ID. | Success response returns the same claim ID and values. | P1 |
| HP-03 | List claims with no status filter and with each supported status filter. | Success response has a claims array; each result matches the requested filter. Record `nextPageToken` and `totalCount`. | P1 |
| HP-04 | Patch an existing claim's title, description, and status. | Success response reflects the requested update and preserves the claim ID. | P1 |
| HP-05 | Delete an existing claim, then read it by ID. | Delete succeeds with the documented empty object; later read reports not found. | P1 |
| HP-06 | Move a non-approved claim into `CLAIM_STATUS_APPROVED` with amount above 50,000 and at or below 1,000,000 cents. | PATCH returns 200 promptly; payout is not required in the PATCH response. A separate payout appears with amount `claim.amountCents - 50000` and reaches `PAID`, `FAILED`, or `CANCELLED` within 10 seconds. | P0 |
| HP-07 | Move a claim with amount above 1,000,000 cents into `CLAIM_STATUS_APPROVED`. | Payout reaches `PAYOUT_STATUS_FAILED` within 10 seconds and has `failureReason: "manual_review_required"`. | P0 |
| HP-08 | List payouts for a claim with no qualifying approval, then fetch a known payout by ID. | First response is a successful empty `payouts` array; second returns the payout and its claim ID, amount, currency, status, and timestamps. | P1 |

> **Known issue tracked in the automated suite:** HP-03 list responses have previously returned a non-empty `claims` array with `totalCount: 0`. The regression check is the `@api @list @known-issue` scenario **“A filtered list includes the created claim and reports a nonzero count”** in [`claims.feature`](app/src/test/resources/features/claims.feature). See [Current observations to verify](#current-observations-to-verify) below for the recorded evidence and reporting guidance.

## Negative and boundary paths

| ID | Scenario | Expected result | Criticality |
| --- | --- | --- | --- |
| NG-01 | Create a claim with `amountCents` omitted, zero, or negative. | Reject with a client error and a useful validation message; create no claim. Omission and non-positive values are evidenced by the notes/spec; confirm exact HTTP status. | P1 |
| NG-02 | Create a claim with a non-numeric `amountCents`, such as `"hello"`. | Reject as invalid int64; create no claim. | P1 |
| NG-03 | Move a claim with amount below, at, and above 50,000 cents into approved. | No payout is created at or below 50,000 cents. Above 50,000, exactly one payout is created for amount minus 50,000. | P0 |
| NG-04 | Read, patch, or delete a claim ID that does not exist. | Return a not-found response (Swagger/runtime HTTP mapping to be recorded); do not affect another claim. | P1 |
| NG-05 | Patch a claim while omitting each documented body field in turn. | For each case, accept either rejection with no mutation or success with omitted values preserved; report which PATCH semantics the service implements. | P1 |
| NG-06 | Submit an unsupported claim status or explore `REJECTED` → `APPROVED`. | Unsupported enum should be rejected; the transition result is recorded without asserting invalidity because no state machine is published. Verify the stored claim state agrees with the PATCH response. | P1 |
| NG-07 | Update a claim without changing its status, including an update while it is already approved. | No payout is created by an update that leaves status unchanged; payout count remains the same. | P0 |
| NG-08 | Change a claim from a non-approved status to a status other than approved. | No payout is created. Approval is the sole payout trigger. | P0 |
| NG-09 | Move an approved claim out of approved before its payout settles. | The in-flight payout reaches `PAYOUT_STATUS_CANCELLED` within the 10-second terminal-state window. | P0 |
| NG-10 | Delete an approved claim before its payout settles. | The in-flight payout reaches `PAYOUT_STATUS_CANCELLED` within the 10-second terminal-state window. | P0 |
| NG-11 | Traverse claim pages with page size 1 using returned page tokens. | Each page respects the requested size, tokens do not repeat, and no claim ID repeats across pages. Confirm missing-result behavior against a stable fixture before asserting full completeness. | P2 |
| NG-12 | Fetch a nonexistent payout ID. | Return not found with a clear error body; do not return an empty success object. | P1 |
| NG-13 | Create a claim with a currency other than EUR or with malformed currency text. | First establish the supported currency contract. Swagger notes EUR as the default but do not state the allowed currency set, so treat acceptance/rejection as exploratory until specified. | P2 |
| NG-14 | Send an extra `status` field in the create request. | Observe whether it is ignored or rejected. Since status is absent from the documented create model, do not expect it to set initial status. A payout is due only if the claim actually transitions into approved. | P1 |

The payout trigger must be verified by comparing claim status before and after the update. A claim created directly as `APPROVED` is not enough to prove a transition occurred; check whether the API specification treats initial creation as entering approved before asserting that it should produce a payout. Approval PATCH scenarios enforce HTTP 200 within a 2-second test budget, confirm the response contains no inline payout, and observe payout creation through the separate payouts endpoint.

### Inferred claim status transitions

The API publishes claim statuses but not a transition state machine. For the scenarios above, the inferred model is:

| Transition exercised | Why / expected result |
| --- | --- |
| `PENDING` → `APPROVED` | Valid approval transition and the defined payout trigger. Expect one payout if the amount is above 50,000 cents. |
| `PENDING` → `REJECTED` | Valid decision outcome; expect no payout. |
| `APPROVED` → `PENDING` before payout settlement | Used to exercise the explicit cancellation rule; the in-flight payout should become `CANCELLED`. |
| `REJECTED` → `APPROVED` | Explored, but neither accepted nor rejected behavior is treated as a contract failure. The test verifies state consistency with the update response. |
| Any status → same status | No status transition occurred, so no payout should be created. |

Other transitions among `PENDING`, `UNDER_REVIEW`, and `REJECTED` are left exploratory unless the service owner publishes allowed transitions. In particular, this suite does not claim that `UNDER_REVIEW` must precede approval.

### Payout status transitions exercised

Payout creation starts separately from the claim update. The claim PATCH should return HTTP 200 promptly; poll the claim's payout list to observe the new payout, then poll/read that payout until it reaches one of the documented terminal states.

| Payout transition/outcome | Expected behavior |
| --- | --- |
| No payout → `PENDING` after an eligible move into approved | A payout appears asynchronously only for a status transition into approved and only when the amount is above 50,000 cents. Amount is claim amount minus 50,000. |
| `PENDING` / `PROCESSING` → `PAID` | Successful settlement; payout is terminal. |
| `PENDING` / `PROCESSING` → `FAILED` | Failed settlement; for claims above 1,000,000 cents, expect `failureReason: "manual_review_required"`. |
| `PENDING` / `PROCESSING` → `CANCELLED` | Expected if the claim leaves approved or is deleted before the payout reaches any terminal state. |
| No status change on claim update → no new payout | Payout count does not increase. |

The precise ordering of `PENDING` and `PROCESSING`, and the causes of failures other than manual review, are not specified. The suite checks the terminal outcomes and the specified manual-review reason, but does not require every payout to pass through `PROCESSING` or assert undocumented failure causes.

### Coverage choices and execution note

Automated coverage includes claim create/read/update/delete, list responses with no filter and supported status filters, page-size/token traversal and duplicate detection, the `totalCount` regression check, missing-amount rejection, unknown claim lookup, partial PATCH omission probes, exploratory `REJECTED` → `APPROVED` behavior, payout behavior for all six published rules, payout lookup, and unauthenticated requests for all seven documented routes. Malformed bearer-token rejection is automated; an expired/revoked-token scenario is available when configured.

Not yet automated: zero/negative/non-numeric amount validation, completeness of pagination against a stable data snapshot, and expired/revoked-token verification unless `CLAIM_API_EXPIRED_TOKEN` is configured. The suite does not assert a complete claim state machine, exact HTTP error mappings beyond success/auth/not-found semantics, allowed currencies beyond the documented EUR default, every possible payout failure cause, or mandatory traversal through `PROCESSING`. These need API-owner rules or stable fixtures before hard assertions are added.

Payout completion is defined as `PAYOUT_STATUS_PAID`, `PAYOUT_STATUS_FAILED`, or `PAYOUT_STATUS_CANCELLED`. The approval PATCH must return HTTP 200 within a 2-second test budget, must not include payout data, and is timed before the response body is read. The suite polls the separate payout endpoint every 200 ms and requires terminal status to be observed before a monotonic 10-second deadline measured from before the PATCH. Amount checks require claim amount minus 50,000 cents; claims at/below the deductible are watched for 10 seconds to confirm no payout appears. Manual review is checked above and at the exact 1,000,000-cent boundary. Same-status and non-approval updates are watched for 10 seconds for unexpected payouts. Both cancellation tests first capture a non-terminal payout before moving the claim out of approved or deleting it. On timeout, fail with the last observed status and response so timing failures are diagnosable.

## Missing and invalid authentication

Run these requests with the `Authorization` header removed. Each route needs an explicit check because authorization can be applied inconsistently between handlers.

| ID | Unauthenticated request | Expected result | Criticality |
| --- | --- | --- | --- |
| AU-01 | `GET /v1/claims` | Reject with 401 or 403; return no claim data. | P0 |
| AU-02 | `POST /v1/claims` with a valid body | Reject with 401 or 403; create no claim and no payout. | P0 |
| AU-03 | `GET /v1/claims/{id}` using a known ID | Reject with 401 or 403; return no claim data. | P0 |
| AU-04 | `PATCH /v1/claims/{id}` with a valid update | Reject with 401 or 403; claim fields/status and payout count remain unchanged. | P0 |
| AU-05 | `DELETE /v1/claims/{id}` using a disposable known ID | Reject with 401 or 403; claim remains readable by an authorized request. | P0 |
| AU-06 | `GET /v1/claims/{claimId}/payouts` using a known claim ID | Reject with 401 or 403; return no payout details. | P0 |
| AU-07 | `GET /v1/payouts/{id}` using a known payout ID | Reject with 401 or 403; return no payout details. | P0 |
| AU-08 | Repeat AU-01 to AU-07 with a malformed, expired, or revoked bearer token. | Reject with 401 or 403 and do not read or mutate resources. | P0 |

For each auth case, first create the resource using a valid token, then make the unauthenticated/invalid-token request, then read the resource again using the valid token to prove it was not changed. Capture HTTP status and response body. Swagger's Try it out `401 unauthorized` examples show that authentication is expected, but they do not establish that every route enforces it at runtime. The automated suite covers AU-01 through AU-07 and a malformed bearer token. AU-08 has an expired/revoked-token scenario, which is skipped unless `CLAIM_API_EXPIRED_TOKEN` is configured with a known expired or revoked token.

## Current observations to verify

| Observation | Scenario IDs | How to report it |
| --- | --- | --- |
| Non-empty claim lists were returned with `totalCount: 0`. | HP-03 | Candidate P1 defect if reproduced; save the filter, returned IDs, count, HTTP status, and page token. |
| Create appeared to return `CLAIM_STATUS_APPROVED` without a status input. | HP-01, NG-14 | Confirm using documented `POST`; inspect the before/after status and payout list. The payout rule requires a move into approved, so clarify whether creation directly into approved counts as that transition. |
| Currency text outside EUR appeared to be accepted. | NG-13 | Keep exploratory until allowed currencies are specified. |
| Deleted/unknown claim produced error code 5. | NG-04 | Not-found is expected semantically; capture HTTP status and check whether the error envelope is usable and consistent. |
| Non-numeric int64 and missing amount appeared rejected. | NG-01, NG-02 | Confirm HTTP status and ensure no resource was created. |

## Suggested execution order

1. Run AU-01 through AU-08 first; any successful unauthenticated read or mutation is a P0 finding.
2. Run HP-01, HP-02, HP-03, NG-01, NG-02, and NG-04 as the core request/response checks.
3. Run HP-06, HP-07, and NG-03, NG-07 through NG-10 for payout and money movement invariants.
4. Finish pagination, PATCH semantics, currency behavior, and other exploratory boundaries.
