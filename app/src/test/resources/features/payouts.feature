# Live API notes (2026-09-28): manual-review payout remained PROCESSING beyond 10s;
# at exactly 1,000,000 cents, payout amount was 1,000,000 instead of 950,000; an
# unchanged APPROVED update created a second payout; both cancellation paths remained
# PAYOUT_STATUS_PROCESSING instead of reaching CANCELLED within 10s. Approval response
# and deductible no-payout checks passed.
# Keep these rule assertions strict to expose service failures.
Feature: Asynchronous claim payouts
  Payout scenarios verify each approval transition against the payout count captured immediately beforehand.
  This accounts for any payout created when a claim is initially returned as APPROVED.

  @api @payout
  Scenario: A payout for a claim above the deductible settles with the deductible withheld
    Given a claim request with these fields
      | title       | Payout amount check             |
      | description | Claim amount exceeds deductible |
      | claimantId  | cucumber-user                   |
      | amountCents | 150000                          |
      | currency    | EUR                             |
    When User creates the claim
    Then the response status is 200
    When User changes the created claim status to "CLAIM_STATUS_PENDING"
    Then the response status is 200
    When User remembers the current payout count
    When User changes the created claim status to "CLAIM_STATUS_APPROVED"
    Then the response status is 200
    When User waits for the created claim payout to settle
    Then the payout is in a terminal state
    And the payout amount is 100000 cents
    And the payout amount matches the claim amount minus the deductible

    # This one is failing because as per documentation "Claims above 1000000 (10 000 EUR) need manual review. Their payout ends FAILED with failureReason “manual_review_required”.
    # And we are getting CLAIM_STATUS_APPROVED
  @api @payout @manual-review
  Scenario: A claim above the manual review limit fails with the specified reason
    Given a claim request with these fields
      | title       | Manual review threshold  |
      | description | Amount exceeds 10000 EUR |
      | claimantId  | cucumber-user            |
      | amountCents | 1000001                  |
      | currency    | EUR                      |
    When User creates the claim
    Then the response status is 200
    When User changes the created claim status to "CLAIM_STATUS_PENDING"
    Then the response status is 200
    When User remembers the current payout count
    When User changes the created claim status to "CLAIM_STATUS_APPROVED"
    Then the response status is 200
    When User waits for the created claim payout to settle
    Then the payout failed because manual review is required
    And the payout amount matches the claim amount minus the deductible

  @api @payout @deductible
  Scenario: A claim at the deductible does not create a payout
    Given a claim request with these fields
      | title       | Deductible boundary      |
      | description | Amount equals deductible |
      | claimantId  | cucumber-user            |
      | amountCents | 50000                    |
      | currency    | EUR                      |
    When User creates the claim
    Then the response status is 200
    When User changes the created claim status to "CLAIM_STATUS_PENDING"
    Then the response status is 200
    When User remembers the current payout count
    When User changes the created claim status to "CLAIM_STATUS_APPROVED"
    Then the response status is 200
    Then no payout is created during a 10-second observation window

  @api @payout @deductible
  Scenario: A claim below the deductible does not create a payout
    Given a claim request with these fields
      | title       | Below deductible          |
      | description | Amount is below deductible |
      | claimantId  | cucumber-user              |
      | amountCents | 49999                      |
      | currency    | EUR                        |
    When User creates the claim
    Then the response status is 200
    When User changes the created claim status to "CLAIM_STATUS_PENDING"
    Then the response status is 200
    When User remembers the current payout count
    When User changes the created claim status to "CLAIM_STATUS_APPROVED"
    Then the response status is 200
    Then no payout is created during a 10-second observation window

  @api @payout @trigger
  Scenario: A non-approval status transition does not create a payout
    Given a claim request with these fields
      | title       | Rejected claim has no payout |
      | description | Only approval releases money |
      | claimantId  | cucumber-user                |
      | amountCents | 150000                       |
      | currency    | EUR                          |
    When User creates the claim
    Then the response status is 200
    When User changes the created claim status to "CLAIM_STATUS_PENDING"
    Then the response status is 200
    When User remembers the current payout count
    When User changes the created claim status to "CLAIM_STATUS_REJECTED"
    Then the response status is 200
    Then no payout is created during a 10-second observation window

  @api @payout @manual-review @boundary
  Scenario: The exact manual review limit does not require manual review
    Given a claim request with these fields
      | title       | Exact manual review limit |
      | description | 1000000 cents is not above |
      | claimantId  | cucumber-user              |
      | amountCents | 1000000                    |
      | currency    | EUR                        |
    When User creates the claim
    Then the response status is 200
    When User changes the created claim status to "CLAIM_STATUS_PENDING"
    Then the response status is 200
    When User remembers the current payout count
    When User changes the created claim status to "CLAIM_STATUS_APPROVED"
    Then the response status is 200
    When User waits for the created claim payout to settle
    Then the payout is in a terminal state
    And the payout failure reason is not "manual_review_required"
    And the payout amount matches the claim amount minus the deductible

  @api @payout @happy
  Scenario: User sees no payout for a non-qualifying claim and can fetch a known payout
    Given a claim request with these fields
      | title       | Known payout source             |
      | description | Eligible approval for lookup    |
      | claimantId  | cucumber-user                   |
      | amountCents | 150000                          |
      | currency    | EUR                             |
    When User creates the claim
    Then the response status is 200
    When User changes the created claim status to "CLAIM_STATUS_PENDING"
    Then the response status is 200
    When User remembers the current payout count
    When User changes the created claim status to "CLAIM_STATUS_APPROVED"
    Then the response status is 200
    When User waits for the created claim payout to settle
    Then the payout is in a terminal state

    Given a claim request with these fields
      | title       | No payout source       |
      | description | At deductible          |
      | claimantId  | cucumber-user          |
      | amountCents | 50000                  |
      | currency    | EUR                    |
    When User creates the claim
    Then the response status is 200
    When User gets payouts for the created claim
    Then the response status is 200
    And the response payout list is empty
    When User gets the saved payout
    Then the response matches the payout schema
    And the response JSON field "claimId" equals the payout claim ID
    And the response JSON field "amountCents" equals "100000"
    And the response JSON field "currency" equals "EUR"

  @api @payout @idempotency
  Scenario: Updating a claim without changing its status does not create another payout
    Given a claim request with these fields
      | title       | Duplicate payout check |
      | description | Same status update     |
      | claimantId  | cucumber-user          |
      | amountCents | 150000                 |
      | currency    | EUR                    |
    When User creates the claim
    Then the response status is 200
    When User changes the created claim status to "CLAIM_STATUS_PENDING"
    Then the response status is 200
    When User remembers the current payout count
    When User changes the created claim status to "CLAIM_STATUS_APPROVED"
    Then the response status is 200
    When User waits for the created claim payout to settle
    Then the payout is in a terminal state
    When User remembers the current payout count
    And User updates the created claim with its current status
    Then the response status is 200
    When User gets payouts for the created claim
    Then the response status is 200
    Then payout count remains unchanged during a 10-second observation window

  @api @payout @cancellation @assumption
  Scenario: Leaving approved cancels an in-flight payout
    Given a claim request with these fields
      | title       | In-flight cancellation      |
      | description | Leave approved immediately  |
      | claimantId  | cucumber-user               |
      | amountCents | 1000001                     |
      | currency    | EUR                         |
    When User creates the claim
    Then the response status is 200
    When User changes the created claim status to "CLAIM_STATUS_PENDING"
    Then the response status is 200
    When User remembers the current payout count
    When User changes the created claim status to "CLAIM_STATUS_APPROVED"
    When User saves the new in-flight payout
    And User immediately moves the created claim out of approved
    Then the response status is 200
    When User waits for the saved payout to become cancelled
    Then the saved payout is cancelled

  @api @payout @cancellation @delete
  Scenario: Deleting an approved claim cancels its in-flight payout
    Given a claim request with these fields
      | title       | Delete during payout       |
      | description | Delete before settlement   |
      | claimantId  | cucumber-user              |
      | amountCents | 1000001                    |
      | currency    | EUR                        |
    When User creates the claim
    Then the response status is 200
    When User changes the created claim status to "CLAIM_STATUS_PENDING"
    Then the response status is 200
    When User remembers the current payout count
    When User changes the created claim status to "CLAIM_STATUS_APPROVED"
    When User saves the new in-flight payout
    And User deletes the created claim
    Then the response status is 200
    When User waits for the saved payout to become cancelled
    Then the saved payout is cancelled
