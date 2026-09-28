Feature: Claims API authorization
  Requests without a bearer token must not read or mutate claim or payout data.

  @api @authorization @p0 @unauthenticated-only
  Scenario: User cannot list claims without authentication
    When User lists claims without authentication
    Then the response is unauthorized

  @api @authorization @p0 @invalid-token
  Scenario: User cannot list claims with a malformed bearer token
    When User lists claims with a malformed token
    Then the response is unauthorized

  @api @authorization @p0 @expired-token
  Scenario: User cannot list claims with an expired or revoked bearer token
    When User lists claims with an expired token
    Then the response is unauthorized

  @api @authorization @p0 @unauthenticated-only
  Scenario: User cannot create a claim without authentication
    Given a claim request with these fields
      | title       | Unauthorized create |
      | description | Must not be created |
      | claimantId  | cucumber-user       |
      | amountCents | 50000               |
      | currency    | EUR                 |
    When User creates the claim without authentication
    Then the response is unauthorized

  @api @authorization @p0
  Scenario: User cannot read a claim without authentication
    Given a claim request with these fields
      | title       | Protected claim      |
      | description | Authorization read  |
      | claimantId  | cucumber-user        |
      | amountCents | 50000                |
      | currency    | EUR                  |
    When User creates the claim
    Then the response is successful
    When User gets the created claim without authentication
    Then the response is unauthorized

  @api @authorization @p0
  Scenario: User cannot update a claim without authentication
    Given a claim request with these fields
      | title       | Protected claim        |
      | description | Authorization update  |
      | claimantId  | cucumber-user          |
      | amountCents | 50000                  |
      | currency    | EUR                    |
    When User creates the claim
    Then the response is successful
    When User saves the created claim status
    And User updates the created claim without authentication
    Then the response is unauthorized
    When User gets the created claim
    Then the response is successful
    And the created claim status is unchanged

  @api @authorization @p0
  Scenario: User cannot delete a claim without authentication
    Given a claim request with these fields
      | title       | Protected claim       |
      | description | Authorization delete |
      | claimantId  | cucumber-user         |
      | amountCents | 50000                 |
      | currency    | EUR                   |
    When User creates the claim
    Then the response is successful
    When User deletes the created claim without authentication
    Then the response is unauthorized
    When User gets the created claim
    Then the response is successful
    And the response JSON field "id" equals the created claim ID

  @api @authorization @p0
  Scenario: User cannot list claim payouts without authentication
    Given a claim request with these fields
      | title       | Protected payout list |
      | description | Authorization payouts|
      | claimantId  | cucumber-user         |
      | amountCents | 50000                 |
      | currency    | EUR                   |
    When User creates the claim
    Then the response is successful
    When User gets payouts for the created claim without authentication
    Then the response is unauthorized

  @api @authorization @p0
  Scenario: User cannot read a payout without authentication
    Given a claim request with these fields
      | title       | Protected payout        |
      | description | Authorization payout get|
      | claimantId  | cucumber-user           |
      | amountCents | 150000                  |
      | currency    | EUR                     |
    When User creates the claim
    Then the response is successful
    When User changes the created claim status to "CLAIM_STATUS_PENDING"
    Then the response is successful
    When User remembers the current payout count
    When User changes the created claim status to "CLAIM_STATUS_APPROVED"
    Then the response is successful
    When User waits for the created claim payout to settle
    Then the payout is in a terminal state
    When User gets the saved payout without authentication
    Then the response is unauthorized
