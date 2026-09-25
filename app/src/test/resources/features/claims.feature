Feature: Claims API
  As a claims API client
  I want to create, retrieve, and list claims
  So that claims can be managed and reviewed

  @api @smoke
  Scenario: Create a claim and retrieve it
    Given a claim request with these fields
      | title       | Cucumber claim |
      | description | API test claim |
      | claimantId  | cucumber-user  |
      | amountCents | 150000         |
      | currency    | EUR            |
    When I create the claim
    Then the response status is 200
    And the response JSON field "id" is present
    And the response JSON field "status" is present
    When I get the created claim
    Then the response status is 200
    And the response JSON field "title" equals "Cucumber claim"
    And the response JSON field "claimantId" equals "cucumber-user"

  @api @list @known-issue
  Scenario: A filtered list includes the created claim and reports a nonzero count
    Given a claim request with these fields
      | title       | Count regression check |
      | description | Claim for list count   |
      | claimantId  | cucumber-user          |
      | amountCents | 150000                 |
      | currency    | EUR                    |
    When I create the claim
    Then the response status is 200
    When I list claims with status filter "CLAIM_STATUS_APPROVED"
    Then the response status is 200
    And the response contains the created claim
    And the response JSON field "totalCount" is greater than or equal to 1

  @api @validation
  Scenario: Creating a claim without its positive amount is rejected
    Given a claim request with these fields and no amount
      | title       | Missing amount     |
      | description | Invalid test claim |
      | claimantId  | cucumber-user      |
      | currency    | EUR                |
    When I create the claim
    Then the response is not successful

  @api @not-found
  Scenario: An unknown claim cannot be retrieved
    When I get claim "cucumber-claim-that-does-not-exist"
    Then the response is not successful

  @api @status-transition @assumption
  Scenario: A rejected claim cannot move directly to approved
    Given a claim request with these fields
      | title       | Invalid transition check    |
      | description | Rejected should be terminal |
      | claimantId  | cucumber-user               |
      | amountCents | 50000                       |
      | currency    | EUR                         |
    When I create the claim
    Then the response status is 200
    When I change the created claim status to "CLAIM_STATUS_REJECTED"
    Then the response status is 200
    When I change the created claim status to "CLAIM_STATUS_APPROVED"
    Then the response is not successful
