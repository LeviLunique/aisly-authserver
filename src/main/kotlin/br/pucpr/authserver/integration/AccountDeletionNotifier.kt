package br.pucpr.authserver.integration

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException

/**
 * Notifies the Aisly backend to remove a user's data when the account is
 * deleted here — the account-deletion side of the Tema 3 integration.
 *
 * With a blank `aisly.base-url` the call is skipped (local development without
 * the Aisly backend); any configured URL makes the webhook mandatory — a
 * failure aborts the deletion so it can be retried without orphaning data.
 */
@Component
class AccountDeletionNotifier(
    private val properties: BackendProperties,
    private val restClient: RestClient = RestClient.create(),
) {
    private val log = LoggerFactory.getLogger(AccountDeletionNotifier::class.java)

    fun notifyUserDeleted(subject: String) {
        val baseUrl = properties.baseUrl.trim().trimEnd('/')
        if (baseUrl.isEmpty()) {
            log.warn("aisly.base-url is blank; skipping delete-data webhook for subject {}", subject)
            return
        }

        try {
            restClient.post()
                .uri("$baseUrl/internal/v1/users/{subject}/delete-data", subject)
                .header("X-Aisly-Internal-Secret", properties.internalSecret)
                .retrieve()
                .toBodilessEntity()
        } catch (e: RestClientException) {
            log.error("Aisly delete-data webhook failed for subject {}", subject, e)
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Failed to remove user data from Aisly; account was not deleted",
            )
        }
    }
}
