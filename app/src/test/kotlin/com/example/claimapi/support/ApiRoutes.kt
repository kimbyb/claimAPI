package com.example.claimapi.support

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object ApiRoutes {
    private const val VERSION = "v1"

    const val CLAIMS = "claims"
    private const val PAYOUTS = "payouts"

    fun claim(id: String) = "$CLAIMS/${pathSegment(id)}"

    fun claimPayouts(claimId: String) = "${claim(claimId)}/payouts"

    fun payout(id: String) = "$PAYOUTS/${pathSegment(id)}"

    fun versioned(path: String) = "$VERSION/${path.trimStart('/')}"

    fun baseUri(baseUrl: String): URI {
        val configuredUri = URI.create(baseUrl.trimEnd('/') + "/")
        require(configuredUri.isAbsolute && configuredUri.host != null) {
            "API base URL must be an absolute URL: $baseUrl"
        }
        require(configuredUri.query == null && configuredUri.fragment == null) {
            "API base URL must not contain a query or fragment: $baseUrl"
        }

        val configuredPath = configuredUri.path.trimEnd('/')
        val rootPath =
            configuredPath.removeSuffix("/$VERSION")
                .ifEmpty { "/" }
                .let { path -> if (path.endsWith('/')) path else "$path/" }

        return URI(
            configuredUri.scheme,
            configuredUri.userInfo,
            configuredUri.host,
            configuredUri.port,
            rootPath,
            null,
            null,
        )
    }

    fun queryString(parameters: Map<String, String>): String =
        parameters.entries.joinToString("&") { (key, value) ->
            "${queryParameter(key)}=${queryParameter(value)}"
        }

    private fun pathSegment(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun queryParameter(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
