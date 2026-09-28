package com.example.claimapi.steps

import com.example.claimapi.support.ApiAssertions
import com.example.claimapi.support.ScenarioContext
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class ClaimSteps(private val context: ScenarioContext) {
    private val assertions = ApiAssertions()

    @Given("a claim request with these fields")
    fun claimRequest(fields: DataTable) {
        context.saveClaimRequest(fields.asMap(String::class.java, String::class.java))
    }

    @Given("a claim request with these fields and no amount")
    fun claimRequestWithoutAmount(fields: DataTable) {
        claimRequest(fields)
        context.saveClaimRequest(context.claimRequest - "amountCents")
    }

    @When("User creates the claim")
    fun createClaim() {
        context.response = context.api.createClaim(context.claimRequest)
        if (context.response?.statusCode?.let { it in 200..299 } == true) {
            context.saveClaimId(context.response?.field("id")?.asText())
        }
    }

    @When("User gets the created claim")
    fun getCreatedClaim() {
        context.response = context.api.getClaim(context.claimId())
    }

    @When("User saves the created claim for partial update checks")
    fun saveCreatedClaimForPartialUpdates() {
        val response = context.api.getClaim(context.claimId())
        assertions.successful(response)
        context.saveOriginalClaim(response.json ?: error("Expected claim JSON: ${response.body}"))
        context.response = response
    }

    @When("User patches the claim omitting {string}")
    fun patchOmitting(field: String) {
        val original = context.originalClaim ?: error("Original claim was not saved")
        val body = listOf("title", "description", "status").filter { it != field }
            .associateWith { original.path(it).asText() }
        context.response = context.api.updateClaim(context.claimId(), body)
    }

    @Then("the omitted update field is preserved or the patch is rejected without changing the claim")
    fun omittedFieldPreservedOrRejected() {
        val patchResponse = context.response()
        val original = context.originalClaim ?: error("Original claim was not saved")
        val updated = context.api.getClaim(context.claimId())
        assertions.successful(updated)
        listOf("title", "description", "status").forEach { field ->
            assertEquals(original.path(field).asText(), updated.field(field)?.asText(), "PATCH changed $field")
        }
        if (patchResponse.statusCode !in 200..299) {
            assertEquals(original.path("status").asText(), updated.field("status")?.asText())
        }
    }

    @Then("the rejected-to-approved transition is reported without assuming the state machine")
    fun observeRejectedToApprovedTransition() {
        val update = context.response()
        val actual = context.api.getClaim(context.claimId())
        assertions.successful(actual)
        val status = actual.field("status")?.asText()
        assertTrue(status in setOf("CLAIM_STATUS_REJECTED", "CLAIM_STATUS_APPROVED"), "Unexpected claim state: ${actual.body}")
        println("Exploratory REJECTED -> APPROVED result: PATCH ${update.statusCode}; stored status $status")
    }

    @When("User traverses claims with page size {int}")
    fun traversePages(pageSize: Int) {
        require(pageSize > 0)
        val ids = mutableListOf<String>()
        val seenTokens = mutableSetOf<String>()
        var token: String? = null
        var pages = 0
        do {
            val response = context.api.listClaims(pageSize = pageSize, pageToken = token)
            context.response = response
            assertions.claimListSchema(response)
            val claims = response.field("claims")!!
            assertTrue(claims.size() <= pageSize, "Page exceeded requested size: ${response.body}")
            claims.forEach { ids += it.path("id").asText() }
            token = response.field("nextPageToken")?.asText()?.takeIf(String::isNotBlank)
            if (token != null) assertTrue(seenTokens.add(token), "Pagination token repeated: $token")
            pages++
            assertTrue(pages <= 100, "Pagination exceeded 100 pages; last response: ${response.body}")
        } while (token != null)
        assertEquals(ids.size, ids.toSet().size, "Pagination returned duplicate claim IDs")
    }

    @When("User fetches all claims")
    fun fetchAllClaims() {
        val claims = mutableListOf<com.fasterxml.jackson.databind.JsonNode>()
        val seenTokens = mutableSetOf<String>()
        var token: String? = null
        var pages = 0
        do {
            val response = context.api.listClaims(pageSize = 1, pageToken = token)
            context.response = response
            assertions.claimListSchema(response)
            response.field("claims")!!.forEach { claims.add(it) }
            token = response.field("nextPageToken")?.asText()?.takeIf(String::isNotBlank)
            if (token != null) check(seenTokens.add(token)) { "Pagination token repeated: $token" }
            check(++pages <= 100) { "Claim listing exceeded 100 pages" }
        } while (token != null)
        context.saveListedClaims(claims)
    }

    @Then("the created claim in the list has field {string} equal to {string}")
    fun createdClaimInListHasField(path: String, expected: String) {
        val listedClaim = context.listedClaims.firstOrNull { it.path("id").asText() == context.claimId() }
        assertTrue(listedClaim != null, "The all-claims listing did not include ${context.claimId()}")
        assertEquals(expected, listedClaim!!.path(path).asText(), "Unexpected listed claim field '$path': $listedClaim")
    }

    @When("User lists claims with status filter {string}")
    fun listClaims(status: String) {
        context.response = context.api.listClaims(status)
    }

    @When("User lists claims")
    fun listClaims() {
        context.response = context.api.listClaims()
    }

    @When("User updates the created claim with these fields")
    fun updateClaim(fields: DataTable) {
        val updates = fields.asMap(String::class.java, String::class.java)
        context.response = context.api.updateClaim(context.claimId(), claimUpdate(updates))
    }

    @When("User deletes the created claim")
    fun deleteCreatedClaim() {
        context.response = context.api.deleteClaim(context.claimId())
    }

    @When("User gets payouts for the created claim")
    fun getClaimPayouts() {
        context.response = context.api.getClaimPayouts(context.claimId())
    }

    @When("User gets the saved payout")
    fun getSavedPayout() {
        context.response = context.api.getPayout(context.payoutId())
    }

    @When("User gets claim {string}")
    fun getClaim(id: String) {
        context.response = context.api.getClaim(id)
    }

    @When("User changes the created claim status to {string}")
    fun changeClaimStatus(status: String) {
        if (status == "CLAIM_STATUS_APPROVED") context.markApprovalRequested()
        context.response = context.api.updateClaim(context.claimId(), claimUpdate(mapOf("status" to status)))
        if (status == "CLAIM_STATUS_APPROVED") {
            val response = context.response()
            assertEquals(200, response.statusCode, "Approval PATCH should return HTTP 200 promptly: ${response.body}")
            assertTrue(
                response.elapsedMillis <= APPROVAL_RESPONSE_BUDGET_MS,
                "Approval PATCH took ${response.elapsedMillis} ms; budget is ${APPROVAL_RESPONSE_BUDGET_MS} ms",
            )
            assertTrue(
                response.field("payout") == null && response.field("payouts") == null,
                "Payout should be observed via its separate endpoint, not embedded in the approval response",
            )
        }
    }

    @When("User immediately moves the created claim out of approved")
    fun immediatelyLeaveApproved() {
        context.response = context.api.updateClaim(
            context.claimId(),
            claimUpdate(mapOf("status" to "CLAIM_STATUS_PENDING")),
        )
    }

    private fun claimUpdate(updates: Map<String, String>) =
        mapOf(
            "title" to (updates["title"] ?: context.claimRequest["title"] ?: "Cucumber claim"),
            "description" to (updates["description"] ?: context.claimRequest["description"] ?: "API test claim"),
            "status" to (updates["status"] ?: context.claimRequest["status"] ?: "CLAIM_STATUS_PENDING"),
        )

    @When("User remembers the current payout count")
    fun rememberPayoutCount() {
        context.response = context.api.getClaimPayouts(context.claimId())
        val payouts = context.response().field("payouts")
        check(context.response().statusCode in 200..299 && payouts != null && payouts.isArray) {
            "Could not read payouts: ${context.response().body}"
        }
        context.savePayoutCount(payouts.size())
    }

    @When("User updates the created claim with its current status")
    fun updateWithCurrentStatus() {
        val claimResponse = context.api.getClaim(context.claimId())
        check(claimResponse.statusCode in 200..299) { "Could not read claim: ${claimResponse.body}" }
        val status =
            claimResponse.field("status")?.asText()
                ?: error("Claim response did not contain status: ${claimResponse.body}")
        val body =
            mapOf(
                "title" to (claimResponse.field("title")?.asText() ?: "Cucumber claim"),
                "description" to (claimResponse.field("description")?.asText() ?: "API test claim"),
                "status" to status,
            )
        context.response = context.api.updateClaim(context.claimId(), body)
    }

    @Then("the response status is {int}")
    fun responseStatus(expected: Int) {
        val response = context.response()
        assertEquals(expected, response.statusCode, "${response.uri}\n${response.body}")
    }

    @Then("the response is successful")
    fun responseSuccessful() = assertions.successful(context.response())

    @Then("the response matches the claim schema")
    fun responseMatchesClaimSchema() = assertions.claimSchema(context.response())

    @Then("the response matches the claim list schema")
    fun responseMatchesClaimListSchema() = assertions.claimListSchema(context.response())

    @Then("every listed claim has status {string}")
    fun everyListedClaimHasStatus(status: String) {
        val filteredResponse = context.response()
        if (status != "CLAIM_STATUS_UNSPECIFIED") {
            assertions.claimListMatchesStatus(filteredResponse, status)
            return
        }

        val unfilteredResponse = context.api.listClaims()
        assertions.claimListSchema(unfilteredResponse)
        assertEquals(
            unfilteredResponse.field("claims"),
            filteredResponse.field("claims"),
            "CLAIM_STATUS_UNSPECIFIED should return the same claims as omitting statusFilter",
        )
        assertEquals(
            unfilteredResponse.field("nextPageToken"),
            filteredResponse.field("nextPageToken"),
            "CLAIM_STATUS_UNSPECIFIED should use the unfiltered pagination result",
        )
        assertEquals(
            unfilteredResponse.field("totalCount"),
            filteredResponse.field("totalCount"),
            "CLAIM_STATUS_UNSPECIFIED should report the unfiltered total count",
        )
    }

    @Then("the response is an empty object")
    fun responseIsEmptyObject() = assertions.emptyObject(context.response())

    @Then("the response matches the payout schema")
    fun responseMatchesPayoutSchema() = assertions.payoutSchema(context.response())

    @Then("the response JSON field {string} equals the created claim ID")
    fun responseFieldEqualsCreatedClaimId(path: String) =
        assertions.fieldEquals(context.response(), path, context.claimId())

    @Then("the response JSON field {string} equals the payout claim ID")
    fun responseFieldEqualsPayoutClaimId(path: String) =
        assertions.fieldEquals(context.response(), path, context.payoutClaimId())

    @Then("the response is not successful")
    fun responseNotSuccessful() {
        val response = context.response()
        assertions.unsuccessful(response)
    }

    @Then("the response JSON field {string} is present")
    fun responseFieldPresent(path: String) {
        assertions.fieldPresent(context.response(), path)
    }

    @Then("the response JSON field {string} equals {string}")
    fun responseFieldEquals(
        path: String,
        expected: String,
    ) {
        assertions.fieldEquals(context.response(), path, expected)
    }

    @Then("the response JSON field {string} is greater than or equal to {int}")
    fun responseFieldAtLeast(
        path: String,
        minimum: Int,
    ) {
        assertions.fieldAtLeast(context.response(), path, minimum.toLong())
    }

    @Then("the response contains the created claim")
    fun responseContainsCreatedClaim() {
        assertions.containsClaim(context.response(), context.claimId())
    }

    @Then("the response payout list is empty")
    fun payoutListEmpty() {
        assertions.payoutListEmpty(context.response())
    }

    @Then("the payout count is unchanged")
    fun payoutCountUnchanged() {
        val expected = context.payoutCount ?: error("Payout count was not recorded")
        assertions.payoutCount(context.response(), expected)
    }

    @Then("the payout list contains no payout for the claim")
    fun payoutListContainsNoPayout() {
        val payouts = context.response().field("payouts")
        check(payouts != null && payouts.isArray) { "Expected payouts array: ${context.response().body}" }
        assertEquals(0, payouts.count { it.path("claimId").asText() == context.claimId() })
    }

    companion object {
        // The challenge says "right away" without a numeric response-time SLA; use a 2s test budget.
        private const val APPROVAL_RESPONSE_BUDGET_MS = 2_000L
    }

}
