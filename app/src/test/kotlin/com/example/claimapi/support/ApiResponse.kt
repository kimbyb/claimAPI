package com.example.claimapi.support

import com.fasterxml.jackson.databind.JsonNode
import java.net.URI

data class ApiResponse(
    val statusCode: Int,
    val body: String,
    val json: JsonNode?,
    val uri: URI,
    val elapsedMillis: Long,
) {
    fun field(path: String): JsonNode? =
        path.split('.').fold(json) { node, part ->
            when {
                node == null -> null
                part.toIntOrNull() != null -> node.get(part.toInt())
                else -> node.get(part)
            }
        }
}
