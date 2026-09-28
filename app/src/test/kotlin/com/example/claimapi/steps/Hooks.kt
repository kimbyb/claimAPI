package com.example.claimapi.steps

import com.example.claimapi.support.ScenarioContext
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.Scenario

class Hooks(private val context: ScenarioContext) {
    @Before
    fun requireToken(scenario: Scenario) {
        if (!scenario.sourceTagNames.contains("@unauthenticated-only") &&
            (context.config.token.isNullOrBlank() || context.config.token == "PASTE_BEARER_TOKEN_HERE")
        ) {
            throw IllegalStateException(
                "Add the challenge token to config.properties or set CLAIM_API_TOKEN (scenario: ${scenario.name})",
            )
        }
    }

    @After
    fun cleanupClaimsCreatedByScenario(scenario: Scenario) {
        if (context.config.token.isNullOrBlank() || context.config.token == "PASTE_BEARER_TOKEN_HERE") return

        context.claimsCreatedInScenario().asReversed().forEach { claimId ->
            try {
                val response = context.api.deleteClaim(claimId)
                if (response.statusCode in 200..299 || response.statusCode == 404) {
                    scenario.log("Cleaned up claim $claimId (HTTP ${response.statusCode})")
                } else {
                    scenario.log("Could not clean up claim $claimId: HTTP ${response.statusCode}: ${response.body}")
                }
            } catch (error: Exception) {
                scenario.log("Could not clean up claim $claimId: ${error.message}")
            }
        }
    }
}
