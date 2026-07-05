package br.pucpr.authserver.integration

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for the Aisly backend account-deletion webhook. A blank
 * [baseUrl] disables the webhook (local development without the backend running).
 */
@ConfigurationProperties("aisly")
data class BackendProperties(
    val baseUrl: String = "",
    val internalSecret: String = "",
)
