package com.example.claimapi.support

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpResponse

data class ApiResponse(
    val statusCode: Int,
    val body: String,
    val json: JsonNode?,
    val uri: URI,
) {
    fun field(path: String): JsonNode? = path.split('.').fold(json) { node, part ->
        when {
            node == null -> null
            part.toIntOrNull() != null -> node.get(part.toInt())
            else -> node.get(part)
        }
    }

    companion object {
        fun from(response: HttpResponse<String>, mapper: ObjectMapper): ApiResponse {
            val parsed = response.body().takeIf(String::isNotBlank)?.let {
                runCatching { mapper.readTree(it) }.getOrNull()
            }
            return ApiResponse(response.statusCode(), response.body(), parsed, response.uri())
        }
    }
}
