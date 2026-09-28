package com.example.claimapi.support

import com.fasterxml.jackson.databind.ObjectMapper

class ScenarioContext {
    private val createdClaimIds = linkedSetOf<String>()
    val mapper = ObjectMapper()
    val config = ApiConfig.load()
    val api = ClaimsApiClient(config = config, mapper = mapper)
    val unauthenticatedApi = api.unauthenticated()
    val malformedTokenApi = api.withToken("not-a-valid-bearer-token")
    var response: ApiResponse? = null
    var claimId: String? = null
        private set
    var payoutId: String? = null
        private set
    var payoutClaimId: String? = null
        private set
    var claimStatusBeforeUnauthorizedUpdate: String? = null
        private set
    var payoutCount: Int? = null
        private set
    var approvalRequestedAtNanos: Long? = null
        private set
    var claimRequest: Map<String, Any?> = emptyMap()
        private set
    var originalClaim: com.fasterxml.jackson.databind.JsonNode? = null
        private set
    var listedClaims: List<com.fasterxml.jackson.databind.JsonNode> = emptyList()
        private set

    fun saveClaimId(value: String?) {
        claimId = value
        value?.takeIf(String::isNotBlank)?.let(createdClaimIds::add)
    }

    fun claimsCreatedInScenario(): List<String> = createdClaimIds.toList()

    fun savePayout(
        id: String?,
        claim: String?,
    ) {
        payoutId = id
        payoutClaimId = claim
    }

    fun saveClaimStatus(value: String) {
        claimStatusBeforeUnauthorizedUpdate = value
    }

    fun savePayoutCount(value: Int) {
        payoutCount = value
    }

    fun markApprovalRequested() {
        approvalRequestedAtNanos = System.nanoTime()
    }

    fun markApprovalRequestedIfMissing() {
        if (approvalRequestedAtNanos == null) markApprovalRequested()
    }

    fun payoutDeadlineReached(): Boolean {
        val startedAt = approvalRequestedAtNanos ?: error("Approval request time was not recorded")
        return System.nanoTime() - startedAt >= PAYOUT_SETTLEMENT_TIMEOUT_NANOS
    }

    fun payoutTimeRemainingNanos(): Long {
        val startedAt = approvalRequestedAtNanos ?: error("Approval request time was not recorded")
        return (PAYOUT_SETTLEMENT_TIMEOUT_NANOS - (System.nanoTime() - startedAt)).coerceAtLeast(0)
    }

    fun saveClaimRequest(value: Map<String, Any?>) {
        claimRequest = value
    }

    fun saveOriginalClaim(value: com.fasterxml.jackson.databind.JsonNode) {
        originalClaim = value.deepCopy()
    }

    fun saveListedClaims(value: List<com.fasterxml.jackson.databind.JsonNode>) {
        listedClaims = value.toList()
    }

    fun response() = response ?: error("No API response is available yet")

    fun claimId() = claimId ?: error("No claim id has been saved in this scenario")

    fun payoutId() = payoutId ?: error("No payout id has been saved in this scenario")

    fun payoutClaimId() = payoutClaimId ?: error("No claim id has been saved for the payout in this scenario")

    fun claimStatusBeforeUnauthorizedUpdate() =
        claimStatusBeforeUnauthorizedUpdate ?: error("Claim status was not saved before the unauthorized update")

    companion object {
        // the delay specified in docs
        private const val PAYOUT_SETTLEMENT_TIMEOUT_NANOS = 10_000_000_000L
    }
}
