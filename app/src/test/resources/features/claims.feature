# Live API notes (2026-09-28): filtered results had totalCount=0; PATCH returned old
# title/description values and cleared omitted title/description; the updated claim was
# absent from the get-all listing. REJECTED -> APPROVED returned 200 but stored REJECTED.
# Assertions remain strict for the published behaviors so these defects stay visible.
Feature: Claims API
  As a User of the claims API
  User wants to create, retrieve, and list claims
  So that claims can be managed and reviewed

  @api @smoke @happy
  Scenario: Create a claim and retrieve it
    Given a claim request with these fields
      | title       | Cucumber claim |
      | description | API test claim |
      | claimantId  | cucumber-user  |
      | amountCents | 150000         |
      | currency    | EUR            |
    When User creates the claim
    Then the response is successful
    And the response JSON field "id" is present
    And the response JSON field "status" is present
    And the response matches the claim schema
    When User gets the created claim
    Then the response is successful
    And the response JSON field "id" equals the created claim ID
    And the response JSON field "title" equals "Cucumber claim"
    And the response JSON field "claimantId" equals "cucumber-user"

    # When we create a request, it always gets the CLAIM_STATUS_APPROVED, even though it should be panging in my opinion
    # In documentation the status is CLAIM_STATUS_UNSPECIFIED since its default. So in this test the status that is returned is used
    # Also even though the request is created and displayed in total list, we always get a total count of 0, so this is a known issue
  @api @list @known-issue
  Scenario: A filtered list includes the created claim and reports a nonzero count
    Given a claim request with these fields
      | title       | Count regression check |
      | description | Claim for list count   |
      | claimantId  | cucumber-user          |
      | amountCents | 150000                 |
      | currency    | EUR                    |
    When User creates the claim
    Then the response status is 200
    When User lists claims with status filter "CLAIM_STATUS_APPROVED"
    Then the response status is 200
    And the response contains the created claim
    And the response JSON field "totalCount" is greater than or equal to 1

  @api @list
  Scenario: User lists claims without a status filter
    When User lists claims
    Then the response matches the claim list schema


    # Added here CLAIM_STATUS_UNSPECIFIED since its also a status, even if its default. Should return it all
  @api @list
  Scenario Outline: User filters claims by status <status>
    When User lists claims with status filter "<status>"
    Then the response matches the claim list schema
    And every listed claim has status "<status>"

    Examples:
      | status                    |
      | CLAIM_STATUS_PENDING      |
      | CLAIM_STATUS_UNDER_REVIEW |
      | CLAIM_STATUS_APPROVED     |
      | CLAIM_STATUS_REJECTED     |
      | CLAIM_STATUS_UNSPECIFIED  |

  @api @list @pagination
  Scenario: Claim pages respect the requested size and do not repeat claims
    When User traverses claims with page size 1
    Then the response matches the claim list schema

  @api @update @patch-semantics
  Scenario Outline: A partial claim patch omitting <field> preserves existing values or is rejected safely
    Given a claim request with these fields
      | title       | Partial patch probe |
      | description | Preserve omissions  |
      | claimantId  | cucumber-user       |
      | amountCents | 50000               |
      | currency    | EUR                 |
    When User creates the claim
    Then the response is successful
    When User saves the created claim for partial update checks
    And User patches the claim omitting "<field>"
    Then the omitted update field is preserved or the patch is rejected without changing the claim

    Examples:
      | field       |
      | title       |
      | description |
      | status      |

    # The update is not working. We always get the before status. Also it saves with CLAIM_STATUS_APPROVED status (should be CLAIM_STATUS_UNSPECIFIED)
    # Even though we see the updated record in claim all records
  @api @update @known-issue
  Scenario: User updates a claim and receives the updated fields
    Given a claim request with these fields
      | title       | Before update        |
      | description | Original description |
      | claimantId  | cucumber-user        |
      | amountCents | 50000                |
      | currency    | EUR                  |
    When User creates the claim
    Then the response is successful
    When User updates the created claim with these fields
      | title       | After update         |
      | description | Updated description  |
      | status      | CLAIM_STATUS_PENDING |
    Then the response is successful
    And the response JSON field "id" equals the created claim ID
    And the response JSON field "title" equals "After update"
    And the response JSON field "description" equals "Updated description"
    And the response JSON field "status" equals "CLAIM_STATUS_PENDING"

  @api @update @known-issue
  Scenario: Updated claim values appear in the get-all claims response
    Given a claim request with these fields
      | title       | Before list update   |
      | description | Original list record |
      | claimantId  | cucumber-user        |
      | amountCents | 50000                |
      | currency    | EUR                  |
    When User creates the claim
    Then the response is successful
    When User updates the created claim with these fields
      | title       | Updated in list      |
      | description | Updated list record  |
      | status      | CLAIM_STATUS_PENDING |
    Then the response is successful
    When User fetches all claims
    Then the created claim in the list has field "title" equal to "Updated in list"
    And the created claim in the list has field "description" equal to "Updated list record"
    And the created claim in the list has field "status" equal to "CLAIM_STATUS_PENDING"

  @api @delete
  Scenario: User deletes a claim and it can no longer be retrieved
    Given a claim request with these fields
      | title       | Disposable claim  |
      | description | Delete happy path |
      | claimantId  | cucumber-user     |
      | amountCents | 50000             |
      | currency    | EUR               |
    When User creates the claim
    Then the response is successful
    When User deletes the created claim
    Then the response is an empty object
    When User gets the created claim
    Then the response is not successful

  @api @validation
  Scenario: Creating a claim without its positive amount is rejected
    Given a claim request with these fields and no amount
      | title       | Missing amount     |
      | description | Invalid test claim |
      | claimantId  | cucumber-user      |
      | currency    | EUR                |
    When User creates the claim
    Then the response is not successful

  @api @not-found
  Scenario: An unknown claim cannot be retrieved
    When User gets claim "cucumber-claim-that-does-not-exist"
    Then the response is not successful

  @api @status-transition @exploratory
  Scenario: Observe whether a rejected claim can move directly to approved
    Given a claim request with these fields
      | title       | Invalid transition check    |
      | description | Rejected should be terminal |
      | claimantId  | cucumber-user               |
      | amountCents | 50000                       |
      | currency    | EUR                         |
    When User creates the claim
    Then the response status is 200
    When User changes the created claim status to "CLAIM_STATUS_REJECTED"
    Then the response status is 200
    When User changes the created claim status to "CLAIM_STATUS_APPROVED"
    Then the rejected-to-approved transition is reported without assuming the state machine
