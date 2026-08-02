package com.comunidapp.app.domain.m27

import com.comunidapp.app.data.model.IssueM27ApiKeyInput
import com.comunidapp.app.data.model.M27Environment
import com.comunidapp.app.data.model.M27MockIds
import com.comunidapp.app.data.model.M27MockUsers
import com.comunidapp.app.data.model.RegisterM27OAuthAppInput
import com.comunidapp.app.data.model.RegisterM27WebhookInput
import com.comunidapp.app.data.repository.M27IntegrationMemoryStore
import com.comunidapp.app.data.repository.M27IntegrationValidators
import com.comunidapp.app.data.repository.MockM27IntegrationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M27IntegrationFoundationTest {
    private fun repository(actor: String = M27MockUsers.DEVELOPER, store: M27IntegrationMemoryStore = M27IntegrationMemoryStore()) =
        MockM27IntegrationRepository({ actor }, store)

    @Test fun validWebhookAccepted() =
        assertEquals(null, M27IntegrationValidators.validateWebhook("Eventos municipales", "https://hooks.example.com/leover"))

    @Test fun invalidWebhookUrlRejected() =
        assertEquals("M27_INVALID_WEBHOOK", M27IntegrationValidators.validateWebhook("Hook", "http://insecure.example.com/hook"))

    @Test fun validOAuthAccepted() =
        assertEquals(null, M27IntegrationValidators.validateOAuthApp("Portal", "https://portal.example.com/cb", listOf("adoptions_read")))

    @Test fun emptyScopesRejected() =
        assertEquals("M27_INVALID_OAUTH", M27IntegrationValidators.validateOAuthApp("Portal", "https://portal.example.com/cb", emptyList()))

    @Test fun sanitizerRedactsSecretPattern() =
        assertFalse(M27PrivacySanitizer.scrubPublicText("secret=abc123").contains("abc123"))

    @Test fun publishedContractsOnlyEligible() = runBlocking {
        val contracts = repository().observePublishedContracts().first()
        assertTrue(contracts.all { it.publishedForDisplay })
        assertTrue(contracts.any { it.title.contains("v1", ignoreCase = true) })
        assertTrue(contracts.none { it.title.contains("borrador", ignoreCase = true) })
    }

    @Test fun publicWebhookHasNoOwnerId() = runBlocking {
        val webhook = repository().observeWebhooks().first().first()
        assertFalse(webhook.toString().contains("mock_user"))
    }

    @Test fun developerCanRegisterWebhook() = runBlocking {
        val repo = repository()
        val before = repo.observeWebhooks().first().size
        repo.registerWebhook(
            RegisterM27WebhookInput("Nuevo hook", "https://new.example.com/hook", M27Environment.SANDBOX)
        ).getOrThrow()
        assertTrue(repo.observeWebhooks().first().size > before)
    }

    @Test fun unauthorizedCannotRegisterWebhook() = runBlocking {
        assertTrue(
            repository(M27MockUsers.UNAUTHORIZED).registerWebhook(
                RegisterM27WebhookInput("Hook", "https://x.example.com/h", M27Environment.SANDBOX)
            ).isFailure
        )
    }

    @Test fun developerCanIssueApiKey() = runBlocking {
        val repo = repository()
        repo.issueApiKey(
            IssueM27ApiKeyInput("Clave test", listOf("sandbox_all"), M27Environment.SANDBOX)
        ).getOrThrow()
        val keys = repo.observeApiKeys().first()
        assertTrue(keys.any { it.label == "Clave test" })
    }

    @Test fun oauthRegistrationRequiresDeveloper() = runBlocking {
        assertTrue(
            repository(M27MockUsers.OTHER).registerOAuthApp(
                RegisterM27OAuthAppInput("App", "https://app.example.com/cb", listOf("events_read"), M27Environment.SANDBOX)
            ).isFailure
        )
    }

    @Test fun rateLimitsIncludeSandboxAndProduction() = runBlocking {
        val limits = repository().observeRateLimits().first()
        assertEquals(2, limits.size)
        assertTrue(limits.any { it.environment == M27Environment.SANDBOX })
    }

    @Test fun disableWebhookNotFoundForOtherUser() = runBlocking {
        assertTrue(repository(M27MockUsers.OTHER).disableWebhook(M27MockIds.WEBHOOK_ACTIVE).isFailure)
    }

    @Test fun mockSeedsAreDeterministic() = runBlocking {
        assertEquals(
            repository().observeWebhooks().first().map { it.label },
            repository().observeWebhooks().first().map { it.label }
        )
    }
}
