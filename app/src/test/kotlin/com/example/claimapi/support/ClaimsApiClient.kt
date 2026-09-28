package com.example.claimapi.support

import com.fasterxml.jackson.databind.ObjectMapper

class ClaimsApiClient private constructor(private val http: ApiHttpClient) {
    constructor(
        config: ApiConfig = ApiConfig.load(),
        mapper: ObjectMapper = ObjectMapper(),
    ) : this(ApiHttpClient(config, mapper))

    fun unauthenticated() = ClaimsApiClient(http.unauthenticated())

    fun withToken(token: String?) = ClaimsApiClient(http.withToken(token))

    fun createClaim(body: Map<String, Any?>) = http.post(ApiRoutes.CLAIMS, body)

    fun getClaim(id: String) = http.get(ApiRoutes.claim(id))

    fun listClaims(status: String? = null, pageSize: Int? = null, pageToken: String? = null) =
        http.get(
            ApiRoutes.CLAIMS,
            buildMap {
                status?.let { put("statusFilter", it) }
                pageSize?.let { put("pageSize", it.toString()) }
                pageToken?.let { put("pageToken", it) }
            },
        )

    fun updateClaim(
        id: String,
        body: Map<String, Any?>,
    ) = http.patch(ApiRoutes.claim(id), body)

    fun deleteClaim(id: String) = http.delete(ApiRoutes.claim(id))

    fun getClaimPayouts(claimId: String) = http.get(ApiRoutes.claimPayouts(claimId))

    fun getPayout(id: String) = http.get(ApiRoutes.payout(id))

    companion object {
        const val DEFAULT_BASE_URL = "https://claimservice-api.emil.de"
    }
}
