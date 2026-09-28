package com.example.claimapi.steps

import com.example.claimapi.support.ScenarioContext
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.awaitility.Awaitility.await
import org.awaitility.core.ConditionTimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Duration

class PayoutSteps(private val context: ScenarioContext) {
    @When("User waits for the created claim payout to settle")
    fun waitForPayoutToSettle() {
        context.markApprovalRequestedIfMissing()
        val expectedCount = (context.payoutCount ?: 0) + 1
        var lastState = "no payout returned"

        awaitUntilApprovalDeadline("payout settlement", { lastState }) {
            val response = context.api.getClaimPayouts(context.claimId())
            context.response = response
            val payouts = response.field("payouts")
            if (response.statusCode !in 200..299 || payouts == null || !payouts.isArray) {
                lastState = "HTTP ${response.statusCode}: ${response.body}"
                return@awaitUntilApprovalDeadline false
            }
            if (payouts.size() < expectedCount) {
                lastState = "waiting for payout $expectedCount; observed ${payouts.size()}"
                return@awaitUntilApprovalDeadline false
            }

            val payout = payouts.maxByOrNull { it.path("createdAt").asText() } ?: payouts.last()
            savePayout(payout.path("id").asText(), context.claimId())
            lastState = payout.path("status").asText("missing status")
            if (context.payoutDeadlineReached()) {
                lastState = "10-second deadline elapsed; last observed $lastState"
                return@awaitUntilApprovalDeadline false
            }
            if (lastState !in TERMINAL_STATES) return@awaitUntilApprovalDeadline false

            assertEquals(expectedCount, payouts.size(), "Expected one payout for this approval: ${response.body}")
            assertEquals(context.claimId(), payout.path("claimId").asText(), "Payout belongs to a different claim")
            true
        }
    }

    @When("User saves the new in-flight payout")
    fun saveInFlightPayout() {
        var lastState = "payout not returned"
        awaitUntilApprovalDeadline("payout creation", { lastState }) {
            val response = context.api.getClaimPayouts(context.claimId())
            context.response = response
            val payouts = response.field("payouts")
            val expectedCount = (context.payoutCount ?: 0) + 1
            if (response.statusCode !in 200..299 || payouts == null || !payouts.isArray || payouts.size() < expectedCount) {
                lastState = "HTTP ${response.statusCode}: ${response.body}"
                return@awaitUntilApprovalDeadline false
            }

            val payout = payouts.maxByOrNull { it.path("createdAt").asText() } ?: payouts.last()
            lastState = payout.path("status").asText("missing status")
            if (context.payoutDeadlineReached()) {
                lastState = "10-second deadline elapsed; last observed $lastState"
                return@awaitUntilApprovalDeadline false
            }
            if (lastState in TERMINAL_STATES) {
                throw AssertionError("Payout reached $lastState before cancellation could be tested: ${response.body}")
            }
            savePayout(payout.path("id").asText(), context.claimId())
            true
        }
    }

    @Then("no payout is created during a 10-second observation window")
    fun noPayoutCreatedDuringSettlementWindow() {
        val expectedCount = context.payoutCount ?: error("Payout count was not recorded before approval")
        var lastObservation = "not checked"
        val observationStartedAt = System.nanoTime()
        val observationWindow = Duration.ofSeconds(10)
        val atMost = observationWindow.plus(POLL_INTERVAL).plus(Duration.ofSeconds(2))
        try {
            await("no payout for claim ${context.claimId()}")
                .pollDelay(Duration.ZERO)
                .pollInterval(POLL_INTERVAL)
                .atMost(atMost)
                .until {
                    val response = context.api.getClaimPayouts(context.claimId())
                    context.response = response
                    val payouts = response.field("payouts")
                    lastObservation = "HTTP ${response.statusCode}: ${response.body}"
                    if (response.statusCode !in 200..299 || payouts == null || !payouts.isArray) return@until false
                    assertEquals(expectedCount, payouts.size(), "Unexpected payout during observation window: ${response.body}")
                    System.nanoTime() - observationStartedAt >= observationWindow.toNanos()
                }
        } catch (timeout: ConditionTimeoutException) {
            throw AssertionError("Could not confirm no payout throughout 10 seconds; last response: $lastObservation", timeout)
        }
    }

    @Then("payout count remains unchanged during a 10-second observation window")
    fun payoutCountRemainsUnchanged() = noPayoutCreatedDuringSettlementWindow()

    @Then("the payout amount is {long} cents")
    fun payoutAmount(expected: Long) {
        val payout = context.api.getPayout(context.payoutId())
        context.response = payout
        assertEquals(200, payout.statusCode, "${payout.uri}\n${payout.body}")
        assertEquals(expected, payout.field("amountCents")?.asLong())
    }

    @Then("the payout amount matches the claim amount minus the deductible")
    fun payoutAmountMatchesDeductible() {
        val payout = context.api.getPayout(context.payoutId())
        context.response = payout
        assertEquals(200, payout.statusCode, "${payout.uri}\n${payout.body}")
        assertEquals(expectedPayoutAmount(), payout.field("amountCents")?.asLong(), "Wrong payout amount")
    }

    @Then("the payout is in a terminal state")
    fun payoutIsTerminal() {
        val payout = context.api.getPayout(context.payoutId())
        context.response = payout
        assertEquals(200, payout.statusCode, "${payout.uri}\n${payout.body}")
        val status = payout.field("status")?.asText()
        assertTrue(status in TERMINAL_STATES, "Expected a terminal payout status, got $status")
    }

    @Then("the payout failed because manual review is required")
    fun manualReviewFailure() {
        val payout = context.api.getPayout(context.payoutId())
        context.response = payout
        assertEquals(200, payout.statusCode, "${payout.uri}\n${payout.body}")
        assertEquals("PAYOUT_STATUS_FAILED", payout.field("status")?.asText())
        assertEquals("manual_review_required", payout.field("failureReason")?.asText())
    }

    @Then("the payout failure reason is not {string}")
    fun payoutFailureReasonIsNot(reason: String) {
        val payout = context.api.getPayout(context.payoutId())
        context.response = payout
        assertEquals(200, payout.statusCode, "${payout.uri}\n${payout.body}")
        assertTrue(
            payout.field("failureReason")?.asText() != reason,
            "Payout at the exact manual-review limit must not use failure reason '$reason': ${payout.body}",
        )
    }

    @Then("the saved payout is cancelled")
    fun payoutIsCancelled() {
        val payout = context.api.getPayout(context.payoutId())
        context.response = payout
        assertEquals(200, payout.statusCode, "${payout.uri}\n${payout.body}")
        assertEquals("PAYOUT_STATUS_CANCELLED", payout.field("status")?.asText(), payout.body)
    }

    @When("User waits for the saved payout to become cancelled")
    fun waitForCancellation() {
        var lastState = "payout not observed"
        awaitUntilApprovalDeadline("payout cancellation", { lastState }) {
            if (context.payoutId == null) {
                val list = context.api.getClaimPayouts(context.claimId())
                context.response = list
                val payouts = list.field("payouts")
                if (list.statusCode !in 200..299 || payouts == null || payouts.size() <= (context.payoutCount ?: 0)) {
                    lastState = "HTTP ${list.statusCode}: ${list.body}"
                    return@awaitUntilApprovalDeadline false
                }
                val payout = payouts.maxByOrNull { it.path("createdAt").asText() } ?: payouts.last()
                savePayout(payout.path("id").asText(), context.claimId())
            }

            val payoutResponse = context.api.getPayout(context.payoutId())
            context.response = payoutResponse
            if (payoutResponse.statusCode !in 200..299) {
                lastState = "HTTP ${payoutResponse.statusCode}: ${payoutResponse.body}"
                return@awaitUntilApprovalDeadline false
            }
            lastState = payoutResponse.field("status")?.asText("missing status") ?: payoutResponse.body
            if (context.payoutDeadlineReached()) {
                lastState = "10-second deadline elapsed; last observed $lastState"
                return@awaitUntilApprovalDeadline false
            }
            when (lastState) {
                "PAYOUT_STATUS_CANCELLED" -> true
                in TERMINAL_STATES -> throw AssertionError("Payout reached $lastState before cancellation: ${payoutResponse.body}")
                else -> false
            }
        }
    }

    private fun awaitUntilApprovalDeadline(
        alias: String,
        lastObserved: () -> String,
        condition: () -> Boolean,
    ) {
        val remaining = Duration.ofNanos(context.payoutTimeRemainingNanos())
        if (remaining.isZero) {
            throw AssertionError("$alias exceeded the 10-second approval deadline; last observed: ${lastObserved()}")
        }
        try {
            await(alias)
                .pollDelay(Duration.ZERO)
                .pollInterval(POLL_INTERVAL)
                .atMost(remaining)
                .until {
                    !context.payoutDeadlineReached() && condition()
                }
        } catch (timeout: ConditionTimeoutException) {
            throw AssertionError("$alias did not complete within 10 seconds of approval; last observed: ${lastObserved()}", timeout)
        }
    }

    private fun savePayout(id: String, claimId: String) {
        context.savePayout(id.takeIf(String::isNotBlank), claimId)
    }

    private fun expectedPayoutAmount(): Long {
        val claimAmount = context.claimRequest["amountCents"]?.toString()?.toLongOrNull()
            ?: error("Claim amountCents is not available for payout assertion")
        return claimAmount - DEDUCTIBLE_CENTS
    }

    companion object {
        private const val DEDUCTIBLE_CENTS = 50_000L
        private val TERMINAL_STATES = setOf("PAYOUT_STATUS_PAID", "PAYOUT_STATUS_FAILED", "PAYOUT_STATUS_CANCELLED")
        private val POLL_INTERVAL = Duration.ofMillis(200)
    }
}
