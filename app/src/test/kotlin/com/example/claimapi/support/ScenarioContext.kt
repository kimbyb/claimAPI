package com.example.claimapi.support

import com.fasterxml.jackson.databind.ObjectMapper

class ScenarioContext {
    val mapper = ObjectMapper()
    val config = ApiConfig.load()
    val api = ClaimsApiClient(config = config, mapper = mapper)
    var response: ApiResponse? = null
    var claimId: String? = null
    var payoutId: String? = null
    var payoutCount: Int? = null
    var claimRequest: MutableMap<String, Any?> = mutableMapOf()

    fun response() = response ?: error("No API response is available yet")
    fun claimId() = claimId ?: error("No claim id has been saved in this scenario")
    fun payoutId() = payoutId ?: error("No payout id has been saved in this scenario")
}
