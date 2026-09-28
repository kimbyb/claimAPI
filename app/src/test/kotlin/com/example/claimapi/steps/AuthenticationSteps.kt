package com.example.claimapi.steps

import com.example.claimapi.support.ApiAssertions
import com.example.claimapi.support.ScenarioContext
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

class AuthenticationSteps(private val context: ScenarioContext) {
    private val assertions = ApiAssertions()

    @When("User lists claims without authentication")
    fun listClaimsWithoutAuthentication() {
        context.response = context.unauthenticatedApi.listClaims()
    }

    @When("User lists claims with a malformed token")
    fun listClaimsWithMalformedToken() {
        context.response = context.malformedTokenApi.listClaims()
    }

    @When("User lists claims with an expired token")
    fun listClaimsWithExpiredToken() {
        val token = System.getenv("CLAIM_API_EXPIRED_TOKEN")?.takeIf(String::isNotBlank)
            ?: throw org.opentest4j.TestAbortedException("Set CLAIM_API_EXPIRED_TOKEN to a known expired or revoked token")
        context.response = context.api.withToken(token).listClaims()
    }

    @When("User creates the claim without authentication")
    fun createClaimWithoutAuthentication() {
        context.response = context.unauthenticatedApi.createClaim(context.claimRequest)
    }

    @When("User gets the created claim without authentication")
    fun getClaimWithoutAuthentication() {
        context.response = context.unauthenticatedApi.getClaim(context.claimId())
    }

    @When("User saves the created claim status")
    fun saveCreatedClaimStatus() {
        val response = context.api.getClaim(context.claimId())
        assertions.successful(response)
        context.saveClaimStatus(
            response.field("status")?.asText()
                ?: error("Claim response did not contain status: ${response.body}"),
        )
    }

    @When("User updates the created claim without authentication")
    fun updateClaimWithoutAuthentication() {
        val body =
            mapOf(
                "title" to (context.claimRequest["title"] ?: "Cucumber claim"),
                "description" to (context.claimRequest["description"] ?: "API test claim"),
                "status" to "CLAIM_STATUS_REJECTED",
            )
        context.response = context.unauthenticatedApi.updateClaim(context.claimId(), body)
    }

    @When("User deletes the created claim without authentication")
    fun deleteClaimWithoutAuthentication() {
        context.response = context.unauthenticatedApi.deleteClaim(context.claimId())
    }

    @When("User gets payouts for the created claim without authentication")
    fun getClaimPayoutsWithoutAuthentication() {
        context.response = context.unauthenticatedApi.getClaimPayouts(context.claimId())
    }

    @When("User gets the saved payout without authentication")
    fun getPayoutWithoutAuthentication() {
        context.response = context.unauthenticatedApi.getPayout(context.payoutId())
    }

    @Then("the response is unauthorized")
    fun responseUnauthorized() = assertions.unauthorized(context.response())

    @Then("the created claim status is unchanged")
    fun createdClaimStatusUnchanged() =
        assertions.fieldEquals(
            context.response(),
            "status",
            context.claimStatusBeforeUnauthorizedUpdate(),
        )
}
