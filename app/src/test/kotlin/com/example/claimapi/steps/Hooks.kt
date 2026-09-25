package com.example.claimapi.steps

import com.example.claimapi.support.ScenarioContext
import io.cucumber.java.Before
import io.cucumber.java.Scenario

class Hooks(private val context: ScenarioContext) {
    @Before
    fun requireToken(scenario: Scenario) {
        if (context.config.token.isNullOrBlank() || context.config.token == "PASTE_BEARER_TOKEN_HERE") {
            throw IllegalStateException(
                "Add the challenge token to config.properties or set CLAIM_API_TOKEN (scenario: ${scenario.name})",
            )
        }
    }
}
