package com.example.claimapi.steps

import com.example.claimapi.support.ScenarioContext
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Duration
import java.time.Instant

class PayoutSteps(private val context: ScenarioContext) {
    @When("I wait for the created claim payout to settle")
    fun waitForPayoutToSettle() {
        val deadline = Instant.now().plus(Duration.ofSeconds(10))
        var lastState = "no payout returned"
        while (Instant.now().isBefore(deadline)) {
            val response = context.api.get("/v1/claims/${context.claimId()}/payouts")
            context.response = response
            val payouts = response.field("payouts")
            if (response.statusCode in 200..299 && payouts != null && payouts.isArray && !payouts.isEmpty) {
                val payout = payouts.maxByOrNull { it.path("createdAt").asText() } ?: payouts.last()
                context.payoutId = payout.path("id").asText().takeIf(String::isNotBlank)
                lastState = payout.path("status").asText("missing status")
                if (lastState in TERMINAL_STATES) return
            } else {
                lastState = "HTTP ${response.statusCode}: ${response.body}"
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("Payout did not settle within 10 seconds; last observed: $lastState")
    }

    @Then("the payout amount is {long} cents")
    fun payoutAmount(expected: Long) {
        val payout = context.api.get("/v1/payouts/${context.payoutId()}")
        context.response = payout
        assertEquals(200, payout.statusCode, "${payout.uri}\n${payout.body}")
        assertEquals(expected, payout.field("amountCents")?.asLong())
    }

    @Then("the payout is in a terminal state")
    fun payoutIsTerminal() {
        val payout = context.api.get("/v1/payouts/${context.payoutId()}")
        context.response = payout
        assertEquals(200, payout.statusCode, "${payout.uri}\n${payout.body}")
        val status = payout.field("status")?.asText()
        assertTrue(status in TERMINAL_STATES, "Expected a terminal payout status, got $status")
    }

    @Then("the payout failed because manual review is required")
    fun manualReviewFailure() {
        val payout = context.api.get("/v1/payouts/${context.payoutId()}")
        context.response = payout
        assertEquals(200, payout.statusCode, "${payout.uri}\n${payout.body}")
        assertEquals("PAYOUT_STATUS_FAILED", payout.field("status")?.asText())
        assertEquals("manual_review_required", payout.field("failureReason")?.asText())
    }

    companion object {
        private val TERMINAL_STATES = setOf("PAYOUT_STATUS_PAID", "PAYOUT_STATUS_FAILED", "PAYOUT_STATUS_CANCELLED")
        private const val POLL_INTERVAL_MS = 200L
    }
}
