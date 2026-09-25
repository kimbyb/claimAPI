Feature: Asynchronous claim payouts
  The payout scenarios below follow the challenge specification.
  Current Postman runs show newly created claims as APPROVED although create has no status field.
  Payout scenarios explicitly move the claim through PENDING into APPROVED to exercise the documented trigger.

  @api @payout
  Scenario: A payout for a claim above the deductible settles with the deductible withheld
    Given a claim request with these fields
      | title       | Payout amount check             |
      | description | Claim amount exceeds deductible |
      | claimantId  | cucumber-user                   |
      | amountCents | 150000                          |
      | currency    | EUR                             |
    When I create the claim
    Then the response status is 200
    When I change the created claim status to "CLAIM_STATUS_PENDING"
    Then the response status is 200
    When I change the created claim status to "CLAIM_STATUS_APPROVED"
    Then the response status is 200
    When I wait for the created claim payout to settle
    Then the payout is in a terminal state
    And the payout amount is 100000 cents

  @api @payout @manual-review
  Scenario: A claim above the manual review limit fails with the specified reason
    Given a claim request with these fields
      | title       | Manual review threshold  |
      | description | Amount exceeds 10000 EUR |
      | claimantId  | cucumber-user            |
      | amountCents | 1000001                  |
      | currency    | EUR                      |
    When I create the claim
    Then the response status is 200
    When I change the created claim status to "CLAIM_STATUS_PENDING"
    Then the response status is 200
    When I change the created claim status to "CLAIM_STATUS_APPROVED"
    Then the response status is 200
    When I wait for the created claim payout to settle
    Then the payout failed because manual review is required

  @api @payout @deductible
  Scenario: A claim at the deductible does not create a payout
    Given a claim request with these fields
      | title       | Deductible boundary      |
      | description | Amount equals deductible |
      | claimantId  | cucumber-user            |
      | amountCents | 50000                    |
      | currency    | EUR                      |
    When I create the claim
    Then the response status is 200
    When I get payouts for the created claim
    Then the response status is 200
    And the response payout list is empty

  @api @payout @idempotency
  Scenario: Updating a claim without changing its status does not create another payout
    Given a claim request with these fields
      | title       | Duplicate payout check |
      | description | Same status update     |
      | claimantId  | cucumber-user          |
      | amountCents | 150000                 |
      | currency    | EUR                    |
    When I create the claim
    Then the response status is 200
    When I wait for the created claim payout to settle
    Then the payout is in a terminal state
    When I remember the current payout count
    And I update the created claim with its current status
    Then the response status is 200
    When I get payouts for the created claim
    Then the response status is 200
    And the payout count is unchanged
