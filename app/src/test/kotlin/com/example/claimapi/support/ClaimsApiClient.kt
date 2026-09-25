package com.example.claimapi.support

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class ClaimsApiClient(
    private val config: ApiConfig = ApiConfig.load(),
    private val mapper: ObjectMapper = ObjectMapper(),
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) {
    fun get(path: String, query: Map<String, String> = emptyMap()) = send("GET", path, query)
    fun post(path: String, body: Map<String, Any?>) = send("POST", path, body = body)
    fun patch(path: String, body: Map<String, Any?>) = send("PATCH", path, body = body)

    private fun send(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: Map<String, Any?>? = null,
    ): ApiResponse {
        val queryString = query.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val requestPath = path.trimStart('/')
        val relativePath = if (config.baseUrl.endsWith("/v1") && requestPath.startsWith("v1/")) {
            requestPath.removePrefix("v1/")
        } else {
            requestPath
        }
        val url = config.baseUrl + "/" + relativePath +
            if (queryString.isEmpty()) "" else "?$queryString"
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
        config.token?.let { builder.header("Authorization", "Bearer $it") }
        if (body != null) builder.header("Content-Type", "application/json")
        val publisher = body?.let { HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(it)) }
            ?: HttpRequest.BodyPublishers.noBody()
        val request = builder.method(method, publisher).build()
        return ApiResponse.from(client.send(request, HttpResponse.BodyHandlers.ofString()), mapper)
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        const val DEFAULT_BASE_URL = "https://claimservice-api.emil.de"
    }
}
