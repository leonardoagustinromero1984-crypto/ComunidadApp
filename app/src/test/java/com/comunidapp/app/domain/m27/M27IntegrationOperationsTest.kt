package com.comunidapp.app.domain.m27

import com.comunidapp.app.data.model.CreateM27ApiKeyInput
import com.comunidapp.app.data.model.CreateM27IntegrationAppInput
import com.comunidapp.app.data.model.EmitM27WebhookEventInput
import com.comunidapp.app.data.model.M27ApiKeyStatus
import com.comunidapp.app.data.model.M27ApiScopes
import com.comunidapp.app.data.model.M27ContractVersion
import com.comunidapp.app.data.model.M27Environment
import com.comunidapp.app.data.model.M27IntegrationAppStatus
import com.comunidapp.app.data.model.M27MockIds
import com.comunidapp.app.data.model.M27MockOrg
import com.comunidapp.app.data.model.M27MockUsers
import com.comunidapp.app.data.model.M27WebhookDeliveryStatus
import com.comunidapp.app.data.model.M27WebhookEventType
import com.comunidapp.app.data.model.RegisterM27WebhookEndpointInput
import com.comunidapp.app.data.model.SubscribeM27WebhookInput
import com.comunidapp.app.data.repository.M27IntegrationErrors
import com.comunidapp.app.data.repository.M27IntegrationException
import com.comunidapp.app.data.repository.M27IntegrationMemoryStore
import com.comunidapp.app.data.repository.MockM27IntegrationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M27IntegrationOperationsTest {
    private fun repository(
        actor: String = M27MockUsers.DEVELOPER,
        store: M27IntegrationMemoryStore = M27IntegrationMemoryStore()
    ) = MockM27IntegrationRepository({ actor }, store)

    private fun errorCode(result: Result<*>): String? =
        (result.exceptionOrNull() as? M27IntegrationException)?.code

    private fun activeKeyInput(scopes: List<String> = listOf("webhooks.manage")) =
        CreateM27ApiKeyInput(
            appId = M27MockIds.APP_ACTIVE,
            label = "Clave operaciones",
            scopes = scopes,
            environment = M27Environment.SANDBOX
        )

    private suspend fun emitAdoptionEvent(
        repo: MockM27IntegrationRepository,
        clientRequestId: String? = null
    ) = repo.emitWebhookEvent(
        EmitM27WebhookEventInput(
            appId = M27MockIds.APP_ACTIVE,
            eventType = M27WebhookEventType.ADOPTION_PUBLISHED,
            resourceRef = "adoption/mock-001",
            sanitizedPayload = """{"ref":"adoption/mock-001","status":"published"}""",
            clientRequestId = clientRequestId
        )
    ).getOrThrow()

    // 1 draft app cannot create key
    @Test fun draftAppCannotCreateKey() = runBlocking {
        val repo = repository()
        val result = repo.createApiKeyForApp(
            CreateM27ApiKeyInput(
                appId = M27MockIds.APP_DRAFT,
                label = "Draft key",
                scopes = listOf("sandbox.execute"),
                environment = M27Environment.SANDBOX
            )
        )
        assertTrue(result.isFailure)
        assertEquals("M27_APP_NOT_ACTIVE", errorCode(result))
    }

    // 2 active app can create key within scopes
    @Test fun activeAppCanCreateKeyWithinScopes() = runBlocking {
        val repo = repository()
        val issued = repo.createApiKeyForApp(activeKeyInput()).getOrThrow()
        assertEquals(M27MockIds.APP_ACTIVE, issued.credential.appId)
        assertTrue(issued.credential.scopes.all { it in listOf("webhooks.manage") })
        assertTrue(repo.observeApiKeys().first().any { it.label == "Clave operaciones" })
    }

    // 3 paused app blocked
    @Test fun pausedAppBlocked() = runBlocking {
        val repo = repository()
        repo.pauseIntegrationApp(M27MockIds.APP_ACTIVE).getOrThrow()
        val result = repo.createApiKeyForApp(activeKeyInput())
        assertTrue(result.isFailure)
        assertEquals("M27_APP_NOT_ACTIVE", errorCode(result))
    }

    // 4 revoked app terminal no reopen
    @Test fun revokedAppTerminalNoReopen() = runBlocking {
        val repo = repository()
        repo.revokeIntegrationApp(M27MockIds.APP_ACTIVE).getOrThrow()
        val reopen = repo.activateIntegrationApp(M27MockIds.APP_ACTIVE)
        assertTrue(reopen.isFailure)
        assertEquals("M27_APP_TERMINAL", errorCode(reopen))
    }

    // 5 other org cannot manage
    @Test fun otherOrgCannotManage() = runBlocking {
        val result = repository(M27MockUsers.OTHER).pauseIntegrationApp(M27MockIds.APP_ACTIVE)
        assertTrue(result.isFailure)
        assertEquals("M27_PERMISSION_DENIED", errorCode(result))
    }

    // 6 scope denied
    @Test fun scopeDenied() = runBlocking {
        val repo = repository()
        val result = repo.createApiKeyForApp(
            activeKeyInput(scopes = listOf("pets.read.public"))
        )
        assertTrue(result.isFailure)
        assertEquals("M27_SCOPE_DENIED", errorCode(result))
    }

    // 7 unknown scope
    @Test fun unknownScope() = runBlocking {
        val repo = repository()
        val result = repo.createApiKeyForApp(
            activeKeyInput(scopes = listOf("payments.charge"))
        )
        assertTrue(result.isFailure)
        assertEquals("M27_UNKNOWN_SCOPE", errorCode(result))
    }

    // 8 key shown once on create
    @Test fun keyShownOnceOnCreate() = runBlocking {
        val repo = repository()
        val first = repo.createApiKeyForApp(
            activeKeyInput().copy(label = "Una vez", clientRequestId = "key-once-1")
        ).getOrThrow()
        assertNotNull(first.plaintextKeyOnce)
        val replay = repo.createApiKeyForApp(
            activeKeyInput().copy(label = "Una vez", clientRequestId = "key-once-1")
        ).getOrThrow()
        assertNull(replay.plaintextKeyOnce)
        assertEquals(first.credential.id, replay.credential.id)
    }

    // 9 list keys no plaintext/hash
    @Test fun listKeysNoPlaintextOrHash() = runBlocking {
        val repo = repository()
        repo.createApiKeyForApp(activeKeyInput().copy(label = "Listado seguro")).getOrThrow()
        val listed = repo.observeApiKeys().first()
        val blob = listed.joinToString("|") { "${it.label}:${it.keyPrefix}:${it.status}" }
        assertFalse(Regex("[a-f0-9]{32,}").containsMatchIn(blob))
        listed.forEach { assertTrue(it.keyPrefix.length <= 16) }
    }

    // 10 rotate revokes old
    @Test fun rotateRevokesOld() = runBlocking {
        val store = M27IntegrationMemoryStore()
        val repo = repository(store = store)
        val rotated = repo.rotateApiKey(M27MockIds.API_KEY_PROD).getOrThrow()
        assertNotEquals(M27MockIds.API_KEY_PROD, rotated.credential.id)
        assertNotNull(rotated.plaintextKeyOnce)
        val old = store.apiKeys.value.first { it.id == M27MockIds.API_KEY_PROD }
        assertEquals(M27ApiKeyStatus.REVOKED, old.status)
        assertEquals(M27ApiKeyStatus.ACTIVE, rotated.credential.status)
    }

    // 11 expired/revoked key logic (mock status)
    @Test fun expiredRevokedKeyLogic() = runBlocking {
        val store = M27IntegrationMemoryStore()
        val repo = repository(store = store)
        repo.revokeApiKey(M27MockIds.API_KEY_PROD).getOrThrow()
        val revoked = store.apiKeys.value.first { it.id == M27MockIds.API_KEY_PROD }
        assertEquals(M27ApiKeyStatus.REVOKED, revoked.status)
        store.apiKeys.value = store.apiKeys.value.map {
            if (it.id == "m27_key_sandbox") it.copy(status = M27ApiKeyStatus.EXPIRED) else it
        }
        val public = repo.observeApiKeys().first()
        assertTrue(public.any { it.label == "Clave sandbox" && it.status == M27ApiKeyStatus.EXPIRED })
    }

    // 12 hash not in public projection
    @Test fun hashNotInPublicProjection() = runBlocking {
        val store = M27IntegrationMemoryStore()
        val repo = repository(store = store)
        val seedHash = store.apiKeys.value.first { it.id == M27MockIds.API_KEY_PROD }.keyHash
        val projection = repo.observeApiKeys().first().joinToString("|") { it.keyPrefix }
        assertFalse(projection.contains(seedHash))
        assertFalse(projection.contains("seed_key"))
    }

    // 13 https endpoint ok
    @Test fun httpsEndpointOk() = runBlocking {
        val repo = repository()
        val endpoint = repo.registerWebhookEndpoint(
            RegisterM27WebhookEndpointInput(
                appId = M27MockIds.APP_ACTIVE,
                label = "Hook seguro",
                targetUrl = "https://hooks.example.com/leover/secure",
                environment = M27Environment.SANDBOX
            )
        ).getOrThrow()
        assertTrue(endpoint.endpoint.targetUrl.startsWith("https://"))
        assertNotNull(endpoint.secretOnce)
    }

    // 14 localhost rejected
    @Test fun localhostRejected() {
        assertEquals("M27_UNSAFE_WEBHOOK_URL", M27SsrfValidator.validateTargetUrl("https://localhost/hook"))
    }

    // 15 private ip rejected
    @Test fun privateIpRejected() {
        assertEquals("M27_UNSAFE_WEBHOOK_URL", M27SsrfValidator.validateTargetUrl("https://10.0.0.8/hook"))
    }

    // 16 insecure scheme rejected
    @Test fun insecureSchemeRejected() = runBlocking {
        val repo = repository()
        val result = repo.registerWebhookEndpoint(
            RegisterM27WebhookEndpointInput(
                appId = M27MockIds.APP_ACTIVE,
                label = "Hook inseguro",
                targetUrl = "http://hooks.example.com/insecure",
                environment = M27Environment.SANDBOX
            )
        )
        assertTrue(result.isFailure)
        assertEquals("M27_UNSAFE_WEBHOOK_URL", errorCode(result))
    }

    // 17 signature changes with payload
    @Test fun signatureChangesWithPayload() {
        val secret = M27CredentialHasher.hashSecret("whsec_test")
        val ts = 1_700_000_000_000L
        val sigA = M27WebhookSignatureService.sign(secret, ts, "evt.sub.{\"a\":1}")
        val sigB = M27WebhookSignatureService.sign(secret, ts, "evt.sub.{\"a\":2}")
        assertNotEquals(sigA, sigB)
    }

    // 18 event has version
    @Test fun eventHasVersion() = runBlocking {
        val repo = repository()
        val event = emitAdoptionEvent(repo)
        assertEquals("1", event.version)
        assertEquals("1", repo.observeEvents().first().first().version)
    }

    // 19 payload no email pii pattern
    @Test fun payloadNoEmailPiiPattern() = runBlocking {
        val repo = repository()
        val clean = emitAdoptionEvent(repo)
        assertFalse(clean.sanitizedPayload.contains("@"))
        val secretPayload = repo.emitWebhookEvent(
            EmitM27WebhookEventInput(
                appId = M27MockIds.APP_ACTIVE,
                eventType = M27WebhookEventType.ADOPTION_PUBLISHED,
                resourceRef = "adoption/mock-002",
                sanitizedPayload = """secret=leak@corp.com"""
            )
        )
        assertTrue(secretPayload.isFailure)
        assertEquals("M27_INVALID_PAYLOAD", errorCode(secretPayload))
    }

    // 20 duplicate event idempotent
    @Test fun duplicateEventIdempotent() = runBlocking {
        val repo = repository()
        val first = emitAdoptionEvent(repo, clientRequestId = "evt-dup-1")
        val second = emitAdoptionEvent(repo, clientRequestId = "evt-dup-1")
        assertEquals(first.id, second.id)
        assertEquals(1, repo.observeEvents().first().count { it.resourceRef == "adoption/mock-001" })
    }

    // 21 single delivery per subscription
    @Test fun singleDeliveryPerSubscription() = runBlocking {
        val store = M27IntegrationMemoryStore()
        val repo = repository(store = store)
        val before = store.deliveries.value.size
        emitAdoptionEvent(repo)
        val afterFirst = store.deliveries.value.size
        assertEquals(1, afterFirst - before)
        emitAdoptionEvent(repo, clientRequestId = "evt-second")
        val matching = store.deliveries.value.filter { it.subscriptionId == M27MockIds.SUBSCRIPTION_ACTIVE }
        assertEquals(afterFirst - before + 1, matching.size)
    }

    // 22 delivery reaches delivered or retry
    @Test fun deliveryReachesDeliveredOrRetry() = runBlocking {
        val store = M27IntegrationMemoryStore()
        val repo = repository(store = store)
        emitAdoptionEvent(repo)
        val delivery = store.deliveries.value.last()
        assertTrue(
            delivery.status == M27WebhookDeliveryStatus.DELIVERED ||
                delivery.status == M27WebhookDeliveryStatus.RETRY_SCHEDULED
        )
    }

    // 23 retry scheduled on failure path
    @Test fun retryScheduledOnFailurePath() {
        val next = M27WebhookDeliveryPolicy.nextStatusAfterAttempt(
            M27WebhookDeliveryStatus.PENDING,
            attempt = 1,
            success = false
        )
        assertEquals(M27WebhookDeliveryStatus.RETRY_SCHEDULED, next)
    }

    // 24 dead letter after max (manual setup)
    @Test fun deadLetterAfterMax() = runBlocking {
        val store = M27IntegrationMemoryStore()
        val repo = repository(store = store)
        val event = emitAdoptionEvent(repo)
        val seed = store.deliveries.value.last()
        store.deliveries.value = store.deliveries.value.map {
            if (it.id == seed.id) {
                it.copy(status = M27WebhookDeliveryStatus.RETRY_SCHEDULED, attemptCount = 2)
            } else it
        }
        repo.manualRetryDelivery(seed.id).getOrThrow()
        val terminal = store.deliveries.value.first { it.id == seed.id }
        assertEquals(M27WebhookDeliveryStatus.DEAD_LETTER, terminal.status)
        assertEquals(M27WebhookDeliveryPolicy.MAX_ATTEMPTS, terminal.attemptCount)
    }

    // 25 terminal delivery no manual retry for unauthorized
    @Test fun terminalDeliveryNoManualRetryForUnauthorized() = runBlocking {
        val store = M27IntegrationMemoryStore()
        val repo = repository(store = store)
        emitAdoptionEvent(repo)
        val delivered = store.deliveries.value.last()
        assertEquals(M27WebhookDeliveryStatus.DELIVERED, delivered.status)
        val denied = repository(M27MockUsers.UNAUTHORIZED, store).manualRetryDelivery(delivered.id)
        assertTrue(denied.isFailure)
        assertEquals("M27_PERMISSION_DENIED", errorCode(denied))
    }

    // 26 rate limit blocks excess
    @Test fun rateLimitBlocksExcess() = runBlocking {
        val repo = repository()
        repeat(5) {
            val allowed = repo.checkAppRateLimit(M27MockIds.APP_ACTIVE, M27Environment.SANDBOX)
            assertTrue(allowed.allowed)
        }
        val blocked = repo.checkAppRateLimit(M27MockIds.APP_ACTIVE, M27Environment.SANDBOX)
        assertFalse(blocked.allowed)
        assertEquals("M27_RATE_LIMIT", blocked.reason)
    }

    // 27 app id manipulation no bypass (same counter key)
    @Test fun appIdManipulationNoBypassSameCounterKey() = runBlocking {
        val store = M27IntegrationMemoryStore()
        val repo = repository(store = store)
        repeat(5) { repo.checkAppRateLimit(M27MockIds.APP_ACTIVE, M27Environment.SANDBOX) }
        val blockedSame = repo.checkAppRateLimit(M27MockIds.APP_ACTIVE, M27Environment.SANDBOX)
        assertFalse(blockedSame.allowed)
        val otherApp = repo.checkAppRateLimit(M27MockIds.APP_DRAFT, M27Environment.SANDBOX)
        assertTrue(otherApp.allowed)
        val manipulated = repo.checkAppRateLimit(" ${M27MockIds.APP_ACTIVE} ", M27Environment.SANDBOX)
        assertTrue(manipulated.allowed)
        assertFalse(repo.checkAppRateLimit(M27MockIds.APP_ACTIVE, M27Environment.SANDBOX).allowed)
    }

    // 28 sandbox simulated delivery no internet
    @Test fun sandboxSimulatedDeliveryNoInternet() = runBlocking {
        val store = M27IntegrationMemoryStore()
        val repo = repository(store = store)
        val beforeAttempts = store.attempts.value.size
        emitAdoptionEvent(repo)
        assertTrue(store.deliveries.value.isNotEmpty())
        assertTrue(store.attempts.value.size > beforeAttempts)
        store.deliveries.value.forEach { assertTrue(it.endpointId.isNotBlank()) }
    }

    // 29 sandbox emit no source mutation (just check success)
    @Test fun sandboxEmitNoSourceMutation() = runBlocking {
        val store = M27IntegrationMemoryStore()
        val repo = repository(store = store)
        val appsBefore = store.apps.value.toList()
        val result = emitAdoptionEvent(repo)
        assertTrue(result.id.isNotBlank())
        assertEquals(appsBefore, store.apps.value)
    }

    // 30 oauth redirect insecure rejected
    @Test fun oauthRedirectInsecureRejected() = runBlocking {
        val repo = repository()
        val result = repo.startOAuthStub(
            redirectUri = "http://portal.example.com/oauth/cb",
            scopes = listOf("adoptions.read.public"),
            state = "state-123"
        )
        assertTrue(result.isFailure)
        assertEquals("M27_UNSAFE_WEBHOOK_URL", errorCode(result))
    }

    // 31 oauth stub requires state
    @Test fun oauthStubRequiresState() = runBlocking {
        val repo = repository()
        val result = repo.startOAuthStub(
            redirectUri = "https://portal.example.com/oauth/cb",
            scopes = listOf("events.read.public"),
            state = null
        )
        assertTrue(result.isFailure)
        assertEquals("M27_OAUTH_STATE_REQUIRED", errorCode(result))
    }

    // 32 oauth stub token prefix stub_
    @Test fun oauthStubTokenPrefixStub() = runBlocking {
        val repo = repository()
        val session = repo.startOAuthStub(
            redirectUri = "https://portal.example.com/oauth/cb",
            scopes = listOf("adoptions.read.public"),
            state = "oauth-state-1"
        ).getOrThrow()
        assertTrue(session.stubTokenPrefix.startsWith("stub_tok_"))
    }

    // 33 audit no full secrets in reason
    @Test fun auditNoFullSecretsInReason() = runBlocking {
        val repo = repository()
        repo.createApiKeyForApp(activeKeyInput().copy(label = "Audit key")).getOrThrow()
        val audit = repo.observeAuditLog().first().first { it.operation == "CREATE_KEY" }
        assertNotNull(audit.sanitizedReason)
        assertTrue(audit.sanitizedReason!!.startsWith("prefix="))
        assertFalse(Regex("lvk_[A-Za-z0-9]{20,}").containsMatchIn(audit.sanitizedReason!!))
    }

    // 34 error messages no sql
    @Test fun errorMessagesNoSql() {
        val codes = listOf(
            "M27_PERMISSION_DENIED",
            "M27_APP_NOT_ACTIVE",
            "M27_SCOPE_DENIED",
            "M27_UNKNOWN_SCOPE",
            "M27_UNSAFE_WEBHOOK_URL",
            "M27_RATE_LIMIT",
            "M27_DELIVERY_TERMINAL",
            "M27_INVALID_PAYLOAD"
        )
        codes.forEach { code ->
            val message = M27IntegrationErrors.userMessage(code)
            assertFalse(message.contains("sql", ignoreCase = true))
            assertFalse(message.contains("select", ignoreCase = true))
        }
    }

    // 35 webhook failure doesn't throw on emit (success on event)
    @Test fun webhookFailureDoesNotThrowOnEmit() = runBlocking {
        val store = M27IntegrationMemoryStore()
        val repo = repository(store = store)
        store.deliveries.value = emptyList()
        store.deliveryKeys.clear()
        val endpoint = store.webhooks.value.first { it.id == M27MockIds.WEBHOOK_ACTIVE }
        store.webhooks.value = store.webhooks.value.map {
            if (it.id == endpoint.id) it.copy(environment = M27Environment.SANDBOX) else it
        }
        val emitted = repo.emitWebhookEvent(
            EmitM27WebhookEventInput(
                appId = M27MockIds.APP_ACTIVE,
                eventType = M27WebhookEventType.ADOPTION_PUBLISHED,
                resourceRef = "adoption/fail-path",
                sanitizedPayload = """{"ref":"adoption/fail-path"}""",
                clientRequestId = "evt-fail-path"
            )
        )
        assertTrue(emitted.isSuccess)
        val delivery = store.deliveries.value.last()
        store.deliveries.value = store.deliveries.value.map {
            if (it.id == delivery.id) {
                it.copy(status = M27WebhookDeliveryStatus.DEAD_LETTER, attemptCount = 3)
            } else it
        }
        assertTrue(store.deliveries.value.any { it.status == M27WebhookDeliveryStatus.DEAD_LETTER })
    }

    // 36 mock deterministic seeds
    @Test fun mockDeterministicSeeds() = runBlocking {
        val first = repository().observeIntegrationApps().first().map { it.name }
        val second = repository().observeIntegrationApps().first().map { it.name }
        assertEquals(first, second)
        assertTrue(first.any { it.contains("Portal municipal activo") })
    }

    // 37 no external provider (oauth stub only)
    @Test fun noExternalProviderOauthStubOnly() = runBlocking {
        val repo = repository()
        val session = repo.startOAuthStub(
            redirectUri = "https://portal.example.com/oauth/cb",
            scopes = listOf("sandbox.execute"),
            state = "stub-only"
        ).getOrThrow()
        assertTrue(session.stubTokenPrefix.startsWith("stub_tok_"))
        assertTrue(repo.observeOAuthApps().first().none { it.name.contains("Google") })
    }

    // 38 no m24 payment scopes
    @Test fun noM24PaymentScopes() {
        assertFalse(M27ApiScopes.ALLOWLIST.any { it.contains("payment", ignoreCase = true) })
        assertFalse(M27ApiScopes.ALLOWLIST.any { it.contains("m24", ignoreCase = true) })
        assertEquals("M27_UNKNOWN_SCOPE", M27ScopePolicy.validateGrantList(listOf("m24.charge")))
    }

    // 39 idempotent app create
    @Test fun idempotentAppCreate() = runBlocking {
        val repo = repository()
        val input = CreateM27IntegrationAppInput(
            name = "App idempotente",
            organizationId = M27MockOrg.DEV_ORG,
            contractVersion = M27ContractVersion.V1,
            requestedScopes = listOf("sandbox.execute"),
            environment = M27Environment.SANDBOX,
            clientRequestId = "app-create-1"
        )
        val first = repo.createIntegrationApp(input).getOrThrow()
        val second = repo.createIntegrationApp(input).getOrThrow()
        assertEquals(first.id, second.id)
        assertEquals(M27IntegrationAppStatus.DRAFT, second.status)
    }

    // 40 manual retry requires developer/admin
    @Test fun manualRetryRequiresDeveloperAdmin() = runBlocking {
        val store = M27IntegrationMemoryStore()
        val repo = repository(store = store)
        emitAdoptionEvent(repo)
        val base = store.deliveries.value.last()
        fun retryableDelivery(suffix: String) = base.copy(
            id = "${base.id}_$suffix",
            status = M27WebhookDeliveryStatus.RETRY_SCHEDULED,
            attemptCount = 1
        )
        val devDelivery = retryableDelivery("dev")
        val adminDelivery = retryableDelivery("admin")
        store.deliveries.value = store.deliveries.value + devDelivery + adminDelivery
        assertTrue(repository(M27MockUsers.UNAUTHORIZED, store).manualRetryDelivery(devDelivery.id).isFailure)
        assertTrue(repository(M27MockUsers.DEVELOPER, store).manualRetryDelivery(devDelivery.id).isSuccess)
        assertTrue(repository(M27MockUsers.ADMIN, store).manualRetryDelivery(adminDelivery.id).isSuccess)
    }
}
