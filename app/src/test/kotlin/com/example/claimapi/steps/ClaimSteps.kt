package com.example.claimapi.steps

import com.example.claimapi.support.ScenarioContext
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

class ClaimSteps(private val context: ScenarioContext) {
    @Given("a claim request with these fields")
    fun claimRequest(fields: DataTable) {
        context.claimRequest = fields.asMap(String::class.java, String::class.java)
            .mapValuesTo(mutableMapOf()) { (_, value) -> value }
    }

    @Given("a claim request with these fields and no amount")
    fun claimRequestWithoutAmount(fields: DataTable) {
        claimRequest(fields)
        context.claimRequest.remove("amountCents")
    }

    @When("I create the claim")
    fun createClaim() {
        context.response = context.api.post("/v1/claims", context.claimRequest)
        if (context.response?.statusCode?.let { it in 200..299 } == true) {
            context.claimId = context.response?.field("id")?.asText()
        }
    }

    @When("I get the created claim")
    fun getCreatedClaim() {
        context.response = context.api.get("/v1/claims/${context.claimId()}")
    }

    @When("I list claims with status filter {string}")
    fun listClaims(status: String) {
        context.response = context.api.get("/v1/claims", mapOf("statusFilter" to status))
    }

    @When("I get payouts for the created claim")
    fun getClaimPayouts() {
        context.response = context.api.get("/v1/claims/${context.claimId()}/payouts")
    }

    @When("I get claim {string}")
    fun getClaim(id: String) {
        context.response = context.api.get("/v1/claims/$id")
    }

    @When("I change the created claim status to {string}")
    fun changeClaimStatus(status: String) {
        val body = mapOf(
            "title" to (context.claimRequest["title"] ?: "Cucumber claim"),
            "description" to (context.claimRequest["description"] ?: "API test claim"),
            "status" to status,
        )
        context.response = context.api.patch("/v1/claims/${context.claimId()}", body)
    }

    @When("I remember the current payout count")
    fun rememberPayoutCount() {
        context.response = context.api.get("/v1/claims/${context.claimId()}/payouts")
        val payouts = context.response().field("payouts")
        check(context.response().statusCode in 200..299 && payouts != null && payouts.isArray) {
            "Could not read payouts: ${context.response().body}"
        }
        context.payoutCount = payouts!!.size()
    }

    @When("I update the created claim with its current status")
    fun updateWithCurrentStatus() {
        val claimResponse = context.api.get("/v1/claims/${context.claimId()}")
        check(claimResponse.statusCode in 200..299) { "Could not read claim: ${claimResponse.body}" }
        val status = claimResponse.field("status")?.asText()
            ?: error("Claim response did not contain status: ${claimResponse.body}")
        val body = mapOf(
            "title" to (claimResponse.field("title")?.asText() ?: "Cucumber claim"),
            "description" to (claimResponse.field("description")?.asText() ?: "API test claim"),
            "status" to status,
        )
        context.response = context.api.patch("/v1/claims/${context.claimId()}", body)
    }

    @Then("the response status is {int}")
    fun responseStatus(expected: Int) {
        val response = context.response()
        assertEquals(expected, response.statusCode, "${response.uri}\n${response.body}")
    }

    @Then("the response is not successful")
    fun responseNotSuccessful() {
        val response = context.response()
        assertFalse(response.statusCode in 200..299, "Expected an error response but got ${response.statusCode}: ${response.body}")
    }

    @Then("the response JSON field {string} is present")
    fun responseFieldPresent(path: String) {
        assertNotNull(context.response().field(path), "Expected '$path' in ${context.response().body}")
    }

    @Then("the response JSON field {string} equals {string}")
    fun responseFieldEquals(path: String, expected: String) {
        val actual = context.response().field(path)
        assertNotNull(actual, "Expected '$path' in ${context.response().body}")
        assertEquals(expected, actual!!.asText())
    }

    @Then("the response JSON field {string} is greater than or equal to {int}")
    fun responseFieldAtLeast(path: String, minimum: Int) {
        val actual = context.response().field(path)
        assertNotNull(actual, "Expected '$path' in ${context.response().body}")
        assertTrue(actual!!.asLong() >= minimum, "Expected $path >= $minimum, got $actual")
    }

    @Then("the response contains the created claim")
    fun responseContainsCreatedClaim() {
        val claims = context.response().field("claims")
        assertNotNull(claims, "Expected claims array in ${context.response().body}")
        assertTrue(
            claims!!.any { it.path("id").asText() == context.claimId() },
            "Expected claim ${context.claimId()} in ${context.response().body}",
        )
    }

    @Then("the response payout list is empty")
    fun payoutListEmpty() {
        val payouts = context.response().field("payouts")
        assertNotNull(payouts, "Expected payouts array in ${context.response().body}")
        assertTrue(payouts!!.isArray && payouts.isEmpty, "Expected empty payouts array, got $payouts")
    }

    @Then("the payout count is unchanged")
    fun payoutCountUnchanged() {
        val expected = context.payoutCount ?: error("Payout count was not recorded")
        val payouts = context.response().field("payouts")
        assertNotNull(payouts, "Expected payouts array in ${context.response().body}")
        assertEquals(expected, payouts!!.size(), "Payout count changed: ${context.response().body}")
    }
}
