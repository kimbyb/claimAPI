package com.example.claimapi.support

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

data class ApiConfig(val baseUrl: String, val token: String?) {
    companion object {
        fun load(): ApiConfig {
            val properties = Properties()
            val configPath = configPath()
            if (configPath != null && Files.isRegularFile(configPath)) {
                Files.newInputStream(configPath).use { properties.load(it) }
            }

            val baseUrl = System.getenv("CLAIM_API_BASE_URL")
                ?.takeIf(String::isNotBlank)
                ?: properties.getProperty("baseUrl")?.takeIf(String::isNotBlank)
                ?: ClaimsApiClient.DEFAULT_BASE_URL
            val token = System.getenv("CLAIM_API_TOKEN")
                ?.takeIf(String::isNotBlank)
                ?: properties.getProperty("token")?.takeIf(String::isNotBlank)

            return ApiConfig(baseUrl.trimEnd('/'), token)
        }

        private fun configPath(): Path? {
            System.getProperty("claim.api.config")?.let { return Path.of(it) }
            return generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .map { it.resolve("config.properties") }
                .firstOrNull(Files::isRegularFile)
        }
    }
}
