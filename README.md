# Claim Service API test task

## API reference captured from Swagger

These notes are based on the Swagger screenshots shared for this task. Swagger's example responses describe the contract shape; the `401 unauthorized` responses shown by “Try it out” are not successful runtime observations and do not verify endpoint behavior.

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

## Runtime observations to reproduce

The following are observations/questions from manual Postman runs, not yet confirmed findings. Capture the exact request, response status/body, and test data for each before reporting it as a defect.

1. `GET /v1/claims`: `totalCount` appeared to remain `0`. Recheck with a known non-empty result and record returned claims and pagination fields.
2. Claim update: initial note asked about mandatory fields in a PUT. Swagger shows `PATCH /v1/claims/{id}`, not PUT. Probe PATCH by omitting one body field at a time and record whether it rejects, preserves, or clears that field.
3. Claim creation: a newly created claim appeared as `CLAIM_STATUS_APPROVED`. Swagger's create model does not show a status property, while its example response shows a status. Reproduce with the exact POST body and inspect claim status and payouts. The challenge specification says a payout is created when a claim moves into approved; whether initial creation as approved counts as a move needs to be tested and documented as an interpretation.
4. Payout lookup: `GET /v1/payouts/{id}` reportedly gave no useful information for an unsuccessful payout. Reproduce after a payout reaches `FAILED` or `CANCELLED`, and separately test an unknown payout id.
5. Deleted claim lookup: a deleted id returned an error body with `code: 5`, message `claim "<id>" not found`, and empty `details`. This code is commonly gRPC `NOT_FOUND`; record the HTTP status and compare the envelope with the API's documented error convention. The semantic not-found result itself is expected for a deleted resource.

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

## Challenge specification reminders

- A payout is created only when a claim moves into `CLAIM_STATUS_APPROVED`.
- Payout amount is `amountCents - 50000`; no payout is created when the amount is at or below 50000 cents.
- Payout settles asynchronously and reaches `PAID`, `FAILED`, or `CANCELLED` within 10 seconds.
- One payout per approval; an update that leaves status unchanged creates none.
- Claims above 1000000 cents fail with `failureReason: "manual_review_required"`.
- If a claim leaves approved or is deleted before settlement, its payout is cancelled.

## Cucumber test framework

The API suite uses Cucumber-JVM with Kotlin, JUnit Platform, Java's built-in HTTP client, and Jackson. Feature files are in `app/src/test/resources/features`; reusable step definitions, scenario context, and the API client are under `app/src/test/kotlin/com/example/claimapi`.

### Configure and run

Use Java 17 or newer. Put the challenge bearer token in the ignored, local `config.properties` file. The checked-in `config.properties.example` shows the required keys; the base URL defaults to the service URL. Environment variables `CLAIM_API_TOKEN` and `CLAIM_API_BASE_URL` override file values.

```sh
./gradlew :app:test
```

The Cucumber HTML report is written to `app/build/reports/cucumber/cucumber.html`. Kotlin formatting/lint checks use ktlint:

```sh
./gradlew :app:ktlintCheck
```

### Coverage choices and trade-offs

- Covers claim create/read, filtered list content/count, missing amount validation, unknown claim lookup, an assumed invalid `REJECTED` to `APPROVED` transition, payout amount after deductible, the deductible boundary, manual-review failure, and no extra payout for an unchanged status update.
- Uses a unique-enough title per scenario and generated server IDs; test data is not deleted because delete-before-settlement is timing-sensitive and must be covered separately.
- Payout polling checks the claim payout list every 200 ms for at most the specification's 10-second settlement window, then reads the payout by ID. This avoids fixed long sleeps and reports the last state on timeout.
- For payout scenarios, the test explicitly PATCHes the created claim through `PENDING` and into `APPROVED`, because manual runs show POST creates claims as approved even though the create model does not include status. The initial create may itself trigger a payout; the test identifies the latest payout by `createdAt` when polling.
- Claim status transitions beyond the exercised PATCH paths are assumptions because Swagger does not publish a state machine. The one invalid-transition example treats `REJECTED` as terminal; adjust it if further API exploration supports a different model.
- Cancellation when an approved claim is deleted or leaves approved before settlement is not in the default automated suite yet. It depends on winning a race before the payout reaches a terminal state and needs careful treatment to avoid a flaky test.
- Currency-format validation is not asserted because no published rule defines supported currencies. `totalCount` is asserted against returned filtered data and currently captures the observed zero-count defect.
- Tests create claims in the shared pre-release service. Run against the provided challenge environment and token; avoid parallel executions with a shared test account if data isolation matters.

### Current known discrepancy

Manual responses show non-empty filtered `claims` arrays with `totalCount: 0`. The list scenario asserts that the count is at least one and is tagged `@known-issue`; a failure should be recorded in the findings report, not weakened to match the current service. New claims also appear as `APPROVED` on creation, which remains an open specification interpretation until their payout behavior is checked.

AI assistance was used to organize the API notes and scaffold the test framework; all runtime findings should be confirmed against the challenge service before submission.
