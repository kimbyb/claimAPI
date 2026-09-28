package com.example.claimapi.support

import com.fasterxml.jackson.databind.ObjectMapper
import io.restassured.RestAssured.given
import io.restassured.builder.RequestSpecBuilder
import io.restassured.http.ContentType
import io.restassured.config.HttpClientConfig
import io.restassured.config.RestAssuredConfig
import io.restassured.specification.RequestSpecification

internal class ApiHttpClient(
    private val config: ApiConfig,
    private val mapper: ObjectMapper,
) {
    private val baseUri = ApiRoutes.baseUri(config.baseUrl)
    private val requestSpec: RequestSpecification =
        RequestSpecBuilder()
            .setBaseUri(baseUri.toString())
            .setAccept(ContentType.JSON)
            .setConfig(
                RestAssuredConfig.config().httpClient(
                    HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", HTTP_TIMEOUT_MS)
                        .setParam("http.socket.timeout", HTTP_TIMEOUT_MS),
                ),
            )
            .apply {
                config.token?.takeIf(String::isNotBlank)?.let { token ->
                    addHeader("Authorization", "Bearer $token")
                }
            }
            .build()

    fun unauthenticated() = ApiHttpClient(config.copy(token = null), mapper)

    fun withToken(token: String?) = ApiHttpClient(config.copy(token = token), mapper)

    fun get(
        path: String,
        query: Map<String, String> = emptyMap(),
    ) = send("GET", path, query)

    fun post(
        path: String,
        body: Map<String, Any?>,
    ) = send("POST", path, body = body)

    fun patch(
        path: String,
        body: Map<String, Any?>,
    ) = send("PATCH", path, body = body)

    fun delete(path: String) = send("DELETE", path)

    private fun send(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: Map<String, Any?>? = null,
    ): ApiResponse {
        val uri =
            endpoint(path, query)
        val request =
            given()
                .spec(requestSpec)
                .basePath(ApiRoutes.versioned(path))
                .queryParams(query)
        body?.let { request.contentType(ContentType.JSON).body(it) }

        val requestStartedAt = System.nanoTime()
        val response = request.`when`().request(method)
        val elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStartedAt)
        val responseBody =
            response.asString()
        val parsed =
            responseBody.takeIf(String::isNotBlank)?.let {
                runCatching { mapper.readTree(it) }.getOrNull()
            }
        return ApiResponse(response.statusCode, responseBody, parsed, uri, elapsedMillis)
    }

    private fun endpoint(
        path: String,
        query: Map<String, String>,
    ) = baseUri.resolve(ApiRoutes.versioned(path)).let { versionedUri ->
        val queryString = ApiRoutes.queryString(query)
        if (queryString.isEmpty()) versionedUri else java.net.URI.create("$versionedUri?$queryString")
    }

    companion object {
        private const val HTTP_TIMEOUT_MS = 2_000
    }
}
