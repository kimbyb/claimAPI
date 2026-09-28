package com.example.claimapi.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

/** Shared response checks used by the Cucumber step definitions. */
class ApiAssertions {
    fun successful(response: ApiResponse) {
        assertTrue(
            response.statusCode in 200..299,
            "Expected a successful response, got ${response.statusCode}: ${response.uri}\n${response.body}",
        )
    }

    fun fieldEquals(
        response: ApiResponse,
        path: String,
        expected: String,
    ) {
        val actual = response.field(path)
        assertNotNull(actual, "Expected '$path' in ${response.body}")
        assertEquals(expected, actual!!.asText(), "Unexpected '$path' in ${response.body}")
    }

    fun claimSchema(response: ApiResponse) {
        val json = response.json
        assertNotNull(json, "Expected a JSON claim response: ${response.body}")
        listOf(
            "id",
            "title",
            "description",
            "status",
            "claimantId",
            "amountCents",
            "currency",
            "createdAt",
            "updatedAt",
        )
            .forEach { field ->
                val value = json!!.get(field)
                assertNotNull(value, "Expected claim schema field '$field' in ${response.body}")
                assertTrue(!value!!.isNull, "Expected non-null claim schema field '$field' in ${response.body}")
            }
    }

    fun claimListSchema(response: ApiResponse) {
        successful(response)
        val claims = response.field("claims")
        assertNotNull(claims, "Expected 'claims' in ${response.body}")
        assertTrue(claims!!.isArray, "Expected 'claims' to be an array in ${response.body}")

        val totalCount = response.field("totalCount")
        assertNotNull(totalCount, "Expected 'totalCount' in ${response.body}")
        assertTrue(
            totalCount!!.isIntegralNumber && totalCount.asLong() >= 0,
            "Expected non-negative integer totalCount in ${response.body}",
        )
        assertNotNull(response.field("nextPageToken"), "Expected 'nextPageToken' in ${response.body}")
    }

    fun claimListMatchesStatus(
        response: ApiResponse,
        status: String,
    ) {
        claimListSchema(response)
        val claims = response.field("claims")!!
        claims.forEachIndexed { index, claim ->
            assertEquals(
                status,
                claim.path("status").asText(),
                "Claim at index $index did not match status filter '$status': ${response.body}",
            )
        }
    }

    fun emptyObject(response: ApiResponse) {
        successful(response)
        assertTrue(
            response.json?.isObject == true && response.json.isEmpty,
            "Expected an empty object, got ${response.body}",
        )
    }

    fun payoutSchema(response: ApiResponse) {
        successful(response)
        val payout = response.json
        assertNotNull(payout, "Expected a JSON payout response: ${response.body}")
        listOf("id", "claimId", "amountCents", "currency", "status", "createdAt", "updatedAt")
            .forEach { field ->
                assertTrue(payout!!.has(field), "Expected payout schema field '$field' in ${response.body}")
            }
    }

    fun unauthorized(response: ApiResponse) {
        assertTrue(
            response.statusCode == 401 || response.statusCode == 403,
            "Expected HTTP 401 or 403, got ${response.statusCode}: ${response.uri}\n${response.body}",
        )
        listOf("id", "claim", "claims", "payout", "payouts").forEach { field ->
            assertTrue(response.field(field) == null, "Unauthenticated response exposed '$field': ${response.body}")
        }
    }

    fun unsuccessful(response: ApiResponse) {
        assertTrue(
            response.statusCode !in 200..299,
            "Expected an error response but got ${response.statusCode}: ${response.uri}\n${response.body}",
        )
    }

    fun fieldPresent(
        response: ApiResponse,
        path: String,
    ) {
        assertNotNull(response.field(path), "Expected '$path' in ${response.body}")
    }

    fun fieldAtLeast(
        response: ApiResponse,
        path: String,
        minimum: Long,
    ) {
        val actual = response.field(path)
        assertNotNull(actual, "Expected '$path' in ${response.body}")
        assertTrue(actual!!.asLong() >= minimum, "Expected $path >= $minimum, got $actual")
    }

    fun containsClaim(
        response: ApiResponse,
        claimId: String,
    ) {
        val claims = response.field("claims")
        assertNotNull(claims, "Expected claims array in ${response.body}")
        assertTrue(
            claims!!.any { it.path("id").asText() == claimId },
            "Expected claim $claimId in ${response.body}",
        )
    }

    fun payoutListEmpty(response: ApiResponse) {
        val payouts = response.field("payouts")
        assertNotNull(payouts, "Expected payouts array in ${response.body}")
        assertTrue(payouts!!.isArray && payouts.isEmpty, "Expected empty payouts array, got $payouts")
    }

    fun payoutCount(
        response: ApiResponse,
        expected: Int,
    ) {
        val payouts = response.field("payouts")
        assertNotNull(payouts, "Expected payouts array in ${response.body}")
        assertEquals(expected, payouts!!.size(), "Unexpected payout count: ${response.body}")
    }
}
