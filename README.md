# Claim Service API test task

## API reference captured from Swagger

### Endpoints

| Method | Path | Purpose / documented inputs |
| --- | --- | --- |
| `GET` | `/v1/claims` | List claims. Query parameters: `pageSize` (integer, shown default `1`), `pageToken`, `statusFilter` (claim status, shown default `CLAIM_STATUS_UNSPECIFIED`). Response model includes `claims`, `nextPageToken`, and `totalCount`. |
| `POST` | `/v1/claims` | Create a claim. Required request body object; example fields: `title`, `description`, `claimantId`, `amountCents` (int64), `currency`. Currency defaults to EUR when empty. Example response is a claim. |
| `GET` | `/v1/claims/{id}` | Fetch a claim by id. |
| `DELETE` | `/v1/claims/{id}` | Delete a claim. Documented success response is `{}`. |
| `PATCH` | `/v1/claims/{id}` | Update a claim. Required path `id` and body with `title`, `description`, `status`. |
| `GET` | `/v1/claims/{claimId}/payouts` | List payouts for a claim; response model contains `payouts`. Swagger description says payout creation is tied to entering `CLAIM_STATUS_APPROVED` and settlement is asynchronous. |
| `GET` | `/v1/payouts/{id}` | Fetch one payout by id. |

### Models and statuses

- Claim response (`v1Claim`) includes `id`, `title`, `description`, `status`, `claimantId`, `createdAt`, `updatedAt`, `amountCents`, and `currency`.
- Claim status values shown in Swagger: `CLAIM_STATUS_UNSPECIFIED`, `CLAIM_STATUS_PENDING`, `CLAIM_STATUS_UNDER_REVIEW`, `CLAIM_STATUS_APPROVED`, and `CLAIM_STATUS_REJECTED`.
- Payout response (`v1Payout`) includes `id`, `claimId`, `amountCents`, `currency`, `status`, `failureReason`, `createdAt`, and `updatedAt`.
- Payout statuses shown in Swagger: `PAYOUT_STATUS_UNSPECIFIED`, `PAYOUT_STATUS_PENDING`, `PAYOUT_STATUS_PROCESSING`, `PAYOUT_STATUS_PAID`, `PAYOUT_STATUS_FAILED`, and `PAYOUT_STATUS_CANCELLED`.
- `failureReason` is described as set when the payout status is `PAYOUT_STATUS_FAILED`.
- The create request model lists `title`, `description`, `claimantId`, `amountCents`, and `currency`. The screenshot marks the request body itself required, but does not visibly mark individual model properties as required. Verify field-level validation with requests before asserting it.

## Findings

The findings below come from the live Cucumber run on 2026-09-28 unless marked exploratory. Reproduce a scenario with `./gradlew :app:test --tests com.example.claimapi.RunCucumberTest -Dcucumber.filter.tags='@tag'`, replacing `@tag` with the scenario tag listed below. Severity uses P0 for broken money movement/invariants, P1 for incorrect core operations or response data, and P2 for smaller inconsistencies. Exact HTTP statuses and response bodies were not retained for every failure; uncertainty is noted instead of inferred.

_These are a mix of finding with automation and manual tests. The automation was basically prompts to cover needed things based on the requirements. Manual testing was made in Postman (initial versions of the findings could be found in notes file. Kept in on purpose). Those findings were given to Codex as reference of the bugs. Also you can find some commends in the cucumber feature files with exmplanations_

### F-01 — List reports zero total for non-empty results (P1)

- **Reproduce:** With a valid token, create a claim, then run the filtered-list scenario (`@list @known-issue`) and inspect `GET /v1/claims`.
- **Expected:** `totalCount` is consistent with the matching returned claims (at least one here).
- **Actual:** `claims` contained the created claim, but `totalCount` was `0`. Manual Postman runs also returned seeded claims with zero count for unspecified and pending filters.
- **Rule/convention:** Swagger includes `totalCount` in the list response. Zero contradicts a non-empty result and makes pagination/count displays unreliable.
- **Covering test:** “A filtered list includes the created claim and reports a nonzero count” (`claims.feature`).

### F-02 — PATCH response is stale and omitted fields are cleared (P1)

- **Reproduce:** Create a claim and PATCH new `title`, `description`, and `status` (`@update`). For omission behavior, PATCH only a subset of title/description fields as in the partial-PATCH scenario.
- **Expected:** The response reflects supplied updates. Omitted fields in a PATCH remain unchanged under the usual PATCH convention.
- **Actual:** The PATCH response returned the old title and description, while Get All contained the updated claim with its new values. The partial-PATCH case also found omitted title/description fields cleared. Exact HTTP status/body was not retained in the run summary.
- **Rule/convention:** Swagger defines PATCH as an update operation. Stale values conflict with the requested update; clearing omitted fields violates the usual partial-update convention. Swagger does not spell out field-mask semantics, so the omission part is convention-based.
- **Covering tests:** “User updates a claim and receives the updated fields” and “A partial claim patch omitting <field> preserves existing values or is rejected safely” (`claims.feature`).

### F-03 — Payout over manual-review limit stays PROCESSING (P0)

- **Reproduce:** Create a claim for `1,000,001` cents, move it through `PENDING` to `APPROVED`, and poll the payout (`@manual-review`).
- **Expected:** Within 10 seconds it becomes `PAYOUT_STATUS_FAILED` with `failureReason: "manual_review_required"`.
- **Actual:** It remained `PAYOUT_STATUS_PROCESSING` past 10 seconds.
- **Rule:** Claims above 1,000,000 cents fail for manual review and payouts reach a terminal state within 10 seconds.
- **Covering test:** “A claim above the manual review limit fails with the specified reason” (`payouts.feature`).

### F-04 — Payout at exactly 1,000,000 cents omits the deductible (P0)

- **Reproduce:** Create a claim for exactly `1,000,000` cents, transition it through `PENDING` to `APPROVED`, and inspect its payout (`@boundary`).
- **Expected:** The manual-review rule applies only above 1,000,000, so payout amount is `1,000,000 - 50,000 = 950,000` cents.
- **Actual:** The payout amount was `1,000,000` cents. A terminal payout was observed, but the amount assertion failed.
- **Rule:** Payout amount is claim amount minus 50,000 cents; manual review is for amounts above 1,000,000.
- **Covering test:** “The exact manual review limit does not require manual review” (`payouts.feature`).

### F-05 — Unchanged APPROVED update creates a duplicate payout (P0)

- **Reproduce:** Approve an eligible claim, wait for its payout to settle, record payout count, then update the claim while leaving status `APPROVED` (`@idempotency`).
- **Expected:** No new payout because status did not transition into approved.
- **Actual:** A second payout appeared after the unchanged-status update.
- **Rule:** One payout per approval; an update that leaves status unchanged creates none.
- **Covering test:** “Updating a claim without changing its status does not create another payout” (`payouts.feature`).

### F-06 — Cancellation does not reach CANCELLED within 10 seconds (P0)

- **Reproduce:** Start an approval payout and, while it is non-terminal, either move the claim out of approved (`@cancellation`) or delete the claim (`@cancellation @delete`); poll the saved payout.
- **Expected:** The in-flight payout becomes `PAYOUT_STATUS_CANCELLED` within the settlement window.
- **Actual:** Both cases timed out with the payout still `PAYOUT_STATUS_PROCESSING`, including reruns of each scenario individually.
- **Rule:** Leaving approved or deleting the claim before settlement cancels its payout.
- **Covering tests:** “Leaving approved cancels an in-flight payout” and “Deleting an approved claim cancels its in-flight payout” (`payouts.feature`).

### Exploratory observation — REJECTED to APPROVED

- **Reproduce:** Create a claim, PATCH it to `CLAIM_STATUS_REJECTED`, then PATCH it to `CLAIM_STATUS_APPROVED` (`@status-transition @exploratory`).
- **Expected:** The specification does not define a claim state machine, so no success/failure expectation can be asserted.
- **Actual:** PATCH returned HTTP 200, but a subsequent read still showed `CLAIM_STATUS_REJECTED`.
- **Rule/convention:** No published rule decides whether the transition is valid. A 200 response with the old state is surprising; this remains an open question, not a confirmed contract violation.

### Looked suspicious, but expected or not established as a defect

- A pending claim's payouts endpoint returned `{ "payouts": [] }`; this is expected because no approval occurred.
- Unknown/deleted claim lookup returned code `5` and a not-found message. Not-found is semantically expected; HTTP status and error-envelope requirements were not captured, so no discrepancy is claimed.
- Currency `EURoooooo` was accepted, but no published currency validation rule was found. This is undocumented behavior, not a confirmed defect.
- Manual Postman notes say create returned `APPROVED` although status was absent (and an extra requested `PENDING` was ignored). The method was recorded inconsistently as PUT while Swagger says POST, and whether creation counts as a transition is unclear. Treat as unresolved, not confirmed.
- Missing `amountCents` and non-numeric `"hello"` were rejected. Swagger's int64 example uses a JSON string, so a numeric string is valid representation and not itself a type issue.

## Additional Postman evidence (2026-09-25)

These are user-provided observations. They strengthen some items above; keep the HTTP method and status code in the test record because the pasted notes do not include all of them.

- A claim with id `6ab6de7744289b6b59417552` was created with title/description `test`, claimant `99`, amount `3400`, and currency `EUR`. The response showed `CLAIM_STATUS_APPROVED` even though status was not in the submitted body. The pasted request line says `PUT /v1/claims`, while Swagger documents `POST /v1/claims`; confirm the actual method from Postman history before reporting. The same claim appeared in the list response.
- Listing with `statusFilter=CLAIM_STATUS_UNSPECIFIED` returned seeded claims in `PENDING`, `UNDER_REVIEW`, `APPROVED`, and `REJECTED`, plus the new approved claim, with `nextPageToken: ""` and `totalCount: 0`. Listing with `statusFilter=CLAIM_STATUS_PENDING` returned the one pending seed claim and still `totalCount: 0`. This is a reproducible candidate defect: non-empty `claims` with zero count. Record HTTP status and repeat to check consistency.
- Omitting `amountCents` produced `validation error: amount_cents: value must be greater than 0 [int64.gt]`. Sending the string `"hello"` produced error `code: 3` with `invalid value for int64 type`. This indicates validation/type errors, but `code: 3` is commonly gRPC `INVALID_ARGUMENT`; capture HTTP status and exact payload.
- The challenge's Swagger example represents `amountCents` as a JSON string because it is an `int64` protobuf field. Therefore a numeric-looking string such as `"3400"` is not by itself evidence of a type defect; the non-numeric string rejection is expected. The pasted notes also say numeric input was accepted; verify both forms if relevant.
- A create request with `amountCents: "1"` and `currency: "EURoooooo"` was accepted and returned the same currency. This is a candidate validation gap, but no published rule specifies currency validation. Treat as an exploratory observation, not a confirmed spec violation.
- Repeating the same create request produced distinct claim IDs (`6ab6dff044289b6b59417553`, `6ab6e24f44289b6b59417554`, and `6ab6e25144289b6b59417555` among the examples). This is normal for a non-idempotent `POST` unless the API documents an idempotency key; it does not mean multiple payouts were created for one approval.
- A request containing an extra `status: "CLAIM_STATUS_PENDING"` field still returned a claim in `CLAIM_STATUS_APPROVED`. The create schema does not list `status`, so the server appears to ignore that unknown field and applies its default. This reinforces the create-status candidate; verify payouts for the created claim, especially because amount `1000000000000` is above the manual-review threshold.
- An amount of `1000000000000` cents was accepted. This is within signed int64 range, so acceptance alone is not an error. Verify that the resulting payout is `FAILED` with `failureReason: "manual_review_required"` as required by the challenge specification.
- The notes mention deleting a nonexistent id and receiving code `5`, but do not show the HTTP verb or status. Swagger documents `DELETE /v1/claims/{id}`; capture the verb and status to distinguish delete behavior from the previously observed GET-by-id not-found behavior.
- `GET /v1/claims/Aleksandra-seed-002/payouts` returned `{ "payouts": [] }` for a pending claim. That is expected under the challenge rule: a payout is created only on transition into approved. An empty list is a successful, informative response, not the earlier concern about an unsuccessful payout lookup.
- A nonexistent claim lookup returned `code: 5` and `claim "6ab6dff044289b6b5941755" not found`. The code maps to gRPC `NOT_FOUND`; record its HTTP status. The semantic outcome is expected for an unknown id, so report only if the transport/status/error shape conflicts with the documented API contract or makes client handling unreasonable.

### Current candidate finding priorities

1. **`totalCount` inconsistent with returned claims** — strongest candidate; list responses provide concrete non-empty examples with count zero.
2. **Create assigns `APPROVED` without a requested status** — observable and important because approval triggers payouts. First confirm the request used documented `POST`, then inspect the claim's payouts and compare to the rule that payout creation happens on moving into approved.
3. **Currency accepts an arbitrary value** — investigate, but no published currency constraint exists, so likely an undocumented behavior/assumption rather than a rule violation.
4. **Error codes and mandatory fields** — capture HTTP status and compare payload semantics with the published contract. `NOT_FOUND` for an unknown claim is reasonable; missing `amountCents` and invalid int64 already appear to be rejected.

Repeated `POST` creating a new resource each time is not currently a candidate defect. The most valuable remaining manual check is to inspect payouts for one newly created approved claim and one claim above 1000000 cents, then exercise a PATCH that changes a claim into approved and one that leaves it approved.

## Cucumber test framework

The API suite uses Cucumber-JVM with Kotlin, JUnit Platform, Rest Assured, Awaitility, and Jackson. Feature files are in `app/src/test/resources/features`; reusable step definitions, scenario context, the shared API client, and response/schema assertions are under `app/src/test/kotlin/com/example/claimapi`. The happy-path scenarios automate HP-01 through HP-05: create/read, list/filter, update, and delete/read-after-delete.

### Configure and run

Use Java 17 or newer. The Gradle wrapper downloads the build dependencies on first run. For a one-command setup and suite run, supply the bearer token as an environment variable:

```sh
CLAIM_API_TOKEN='your-challenge-token' ./gradlew :app:test
```

Alternatively, copy `config.properties.example` to the ignored local `config.properties` and add the token there. `CLAIM_API_BASE_URL` may include `/v1` or stop at the host; the client normalizes either form. Environment variables override file values. To test expired/revoked-token behavior, set `CLAIM_API_EXPIRED_TOKEN` to a known expired or revoked token; that scenario is skipped if unset.

The Cucumber HTML report is written to `app/build/reports/cucumber/cucumber.html`. Kotlin formatting rules are in `.editorconfig`; run tests and linter with:

```sh
./gradlew :app:test :app:check
```

### Decisions and trade-offs

- Covers claim create/read/update/delete, filtered list shape and status matching, page-size/token traversal, filtered list content/count, partial PATCH omission behavior, unauthenticated and malformed-token requests, missing amount validation, unknown claim lookup, exploratory `REJECTED` to `APPROVED` behavior, payout trigger/no-trigger conditions, exact deductible and manual-review boundaries, payout amounts, settlement timing, both cancellation paths, and duplicate-payout detection.
- Uses generated server IDs to isolate claim operations. An `@After` hook deletes every claim created in the scenario after its assertions finish; cleanup failures are logged to that scenario without masking the test result. The HP-05 scenario still verifies the delete endpoint explicitly.
- Approval PATCH calls must return HTTP 200 within the suite's explicit 2-second response budget and must not embed payout data. The suite records response time before reading the body, captures payout count before the transition, then uses Awaitility to poll the separate payout endpoint every 200 ms until exactly one additional payout reaches a terminal state. Its monotonic 10-second deadline starts before the PATCH; polling avoids a long fixed sleep, and timeout failures include the last observed state.
- Claim status transitions beyond the published payout rule are not asserted as valid or invalid because Swagger does not publish a state machine. The rejected-to-approved scenario records either outcome and verifies that the resulting claim state agrees with the PATCH result.
- Both cancellation cases are automated. They first capture a non-terminal payout and then leave approved or delete the claim, so the assertion only applies when the payout is demonstrably in flight.
- Currency-format validation is not asserted because no published rule defines supported currencies. `totalCount` is asserted against returned filtered data and currently captures the observed zero-count defect.
- The malformed-token case is automated. The expired/revoked-token scenario runs when `CLAIM_API_EXPIRED_TOKEN` contains a known expired or revoked token; otherwise Cucumber reports it as skipped.
- Tests create claims in the shared pre-release service. Run against the provided challenge environment and token; avoid parallel executions with a shared test account if data isolation matters.

### Not tested and why

- Expired/revoked-token behavior was not exercised in the recorded run because no known expired or revoked token was supplied; the scenario is skipped when `CLAIM_API_EXPIRED_TOKEN` is unset. Malformed-token and unauthenticated cases were exercised.
- Currency-format acceptance was not asserted because Swagger and the challenge rules do not define a currency allowlist or format constraint. Postman accepted `EURoooooo`, which is recorded above as an open question.
- The exact HTTP status and full error envelope for several failed/unknown-resource requests were not captured in the retained run output. No claim about those transport details is made.
- Cross-client concurrency, load, long-running payout recovery, and repeated runs against a clean service were out of scope for this contract-focused run; the service is shared and tests create real remote data.

### Current known discrepancy

Manual responses show non-empty filtered `claims` arrays with `totalCount: 0`. The list scenario asserts that the count is at least one and is tagged `@known-issue`; a failure should be recorded in the findings report, not weakened to match the current service. New claims also appear as `APPROVED` on creation, which remains an open specification interpretation until their payout behavior is checked.

### Latest live suite run (2026-09-28)

`./gradlew :app:test --no-daemon` compiled successfully and ran 37 scenarios: 10 failed and 1 was skipped. The run reported these service discrepancies: `totalCount` was zero for a non-empty result; PATCH returned old title/description and cleared omitted title/description; manual-review payout remained `PROCESSING` beyond 10 seconds; at exactly 1,000,000 cents, payout amount was 1,000,000 instead of 950,000; an unchanged `APPROVED` update created a second payout; and neither cancellation path reached `CANCELLED` within 10 seconds. The earlier report that an updated claim was absent from Get All was incorrect: Get All contains the updated claim with its new values, so it is not a finding. The expired-token scenario was skipped because `CLAIM_API_EXPIRED_TOKEN` was unset. The rejected-to-approved case logged HTTP 200 with the stored status still rejected; it is exploratory, not a contract assertion. Approval response-time, separate payout visibility, below/equal deductible no-payout windows, and the non-approval no-payout window passed. “Right away” is operationalized as a 2-second test budget because the challenge does not give a numeric response SLA. Gherkin comments at the top of `claims.feature` and `payouts.feature` summarize the API observations. This is the recorded run total; the suite has not been rerun since correcting the Get All observation.

The two cancellation scenarios were then rerun by tag: both timed out with the payout still `PAYOUT_STATUS_PROCESSING`, so the service did not reach `CANCELLED` within the 10-second approval window in those runs.

### Submission notes

- Rough time spent: approximately 6 hours, including API exploration, test framework work, live runs, and documenting findings; this is a retrospective estimate.
- AI/code-assistant help was used substantially to organize API notes and scaffold/refine the test framework and documentation. Manual exploratory runs guided that work; outcomes were checked against the live service, and ambiguous behavior is labeled exploratory above.
