package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.*
import com.comunidapp.app.domain.m27.*
import java.util.UUID

internal object M27IntegrationOperationsLogic {
    fun orgFor(actor: String): String? = when (actor) {
        M27MockUsers.DEVELOPER -> M27MockOrg.DEV_ORG
        M27MockUsers.OTHER -> M27MockOrg.OTHER_ORG
        M27MockUsers.ADMIN -> M27MockOrg.DEV_ORG
        else -> null
    }

    fun canManageOrg(actor: String, orgId: String): Boolean =
        actor == M27MockUsers.ADMIN || orgFor(actor) == orgId

    fun idempotencyKey(actor: String, clientRequestId: String?): String? =
        clientRequestId?.trim()?.takeIf { it.isNotEmpty() }?.let { "$actor:$it" }

    fun createApp(
        store: M27IntegrationMemoryStore,
        actor: String,
        input: CreateM27IntegrationAppInput
    ): Result<M27IntegrationApp> {
        if (orgFor(actor) == null && actor != M27MockUsers.ADMIN) {
            return M27IntegrationErrors.failure(M27IntegrationException("M27_PERMISSION_DENIED", "perm"))
        }
        if (!canManageOrg(actor, input.organizationId)) {
            return M27IntegrationErrors.failure(M27IntegrationException("M27_PERMISSION_DENIED", "perm"))
        }
        M27ScopePolicy.validateGrantList(input.requestedScopes)?.let {
            return M27IntegrationErrors.failure(M27IntegrationException(it, it))
        }
        if (input.environment == M27Environment.PRODUCTION) {
            return M27IntegrationErrors.failure(M27IntegrationException("M27_PRODUCTION_DISABLED", "prod"))
        }
        idempotencyKey(actor, input.clientRequestId)?.let { key ->
            store.clientRequests[key]?.let { existingId ->
                store.apps.value.find { it.id == existingId }?.let { return Result.success(it) }
            }
        }
        val stamp = System.currentTimeMillis()
        val app = M27IntegrationApp(
            id = store.nextId("m27_app"),
            ownerUserId = actor,
            organizationId = input.organizationId,
            name = input.name.trim(),
            contractVersion = input.contractVersion,
            grantedScopes = M27ScopePolicy.filterAllowed(input.requestedScopes),
            status = M27IntegrationAppStatus.DRAFT,
            environment = input.environment,
            createdAt = stamp,
            updatedAt = stamp
        )
        store.apps.value = store.apps.value + app
        input.clientRequestId?.let { store.clientRequests[idempotencyKey(actor, it)!!] = app.id }
        audit(store, actor, app.id, "CREATE_APP", "OK", app.environment, null)
        return Result.success(app)
    }

    fun activateApp(store: M27IntegrationMemoryStore, actor: String, appId: String): Result<M27IntegrationApp> =
        transitionApp(store, actor, appId, M27IntegrationAppStatus.ACTIVE)

    fun pauseApp(store: M27IntegrationMemoryStore, actor: String, appId: String): Result<M27IntegrationApp> =
        transitionApp(store, actor, appId, M27IntegrationAppStatus.PAUSED)

    fun revokeApp(store: M27IntegrationMemoryStore, actor: String, appId: String): Result<M27IntegrationApp> =
        transitionApp(store, actor, appId, M27IntegrationAppStatus.REVOKED)

    private fun transitionApp(
        store: M27IntegrationMemoryStore,
        actor: String,
        appId: String,
        target: M27IntegrationAppStatus
    ): Result<M27IntegrationApp> {
        val idx = store.apps.value.indexOfFirst { it.id == appId }
        if (idx < 0) return M27IntegrationErrors.failure(M27IntegrationException("M27_APP_NOT_FOUND", "nf"))
        val current = store.apps.value[idx]
        if (!canManageOrg(actor, current.organizationId)) {
            return M27IntegrationErrors.failure(M27IntegrationException("M27_PERMISSION_DENIED", "perm"))
        }
        M27AppLifecycle.validateTransition(current.status, target)?.let {
            return M27IntegrationErrors.failure(M27IntegrationException(it, it))
        }
        val updated = current.copy(status = target, updatedAt = System.currentTimeMillis())
        store.apps.value = store.apps.value.toMutableList().also { it[idx] = updated }
        audit(store, actor, appId, "APP_${target.name}", "OK", updated.environment, null)
        return Result.success(updated)
    }

    fun createApiKey(
        store: M27IntegrationMemoryStore,
        actor: String,
        input: CreateM27ApiKeyInput
    ): Result<M27IssuedCredential> {
        val app = store.apps.value.find { it.id == input.appId }
            ?: return M27IntegrationErrors.failure(M27IntegrationException("M27_APP_NOT_FOUND", "nf"))
        if (!canManageOrg(actor, app.organizationId)) {
            return M27IntegrationErrors.failure(M27IntegrationException("M27_PERMISSION_DENIED", "perm"))
        }
        if (!M27AppLifecycle.canOperate(app.status)) {
            return M27IntegrationErrors.failure(M27IntegrationException("M27_APP_NOT_ACTIVE", "inactive"))
        }
        M27ScopePolicy.validateRequested(app.grantedScopes, input.scopes)?.let {
            return M27IntegrationErrors.failure(M27IntegrationException(it, it))
        }
        idempotencyKey(actor, input.clientRequestId)?.let { key ->
            store.clientRequests[key]?.let { existingId ->
                store.apiKeys.value.find { it.id == existingId }?.let {
                    return Result.success(M27IssuedCredential(it, plaintextKeyOnce = null))
                }
            }
        }
        val raw = "lvk_${UUID.randomUUID().toString().replace("-", "").take(24)}"
        val hash = M27CredentialHasher.hashSecret(raw)
        val prefix = M27CredentialHasher.prefixFor(raw, if (input.environment == M27Environment.SANDBOX) "lvk_sbx_" else "lvk_stg_")
        val stamp = System.currentTimeMillis()
        val cred = M27ApiCredential(
            id = store.nextId("m27_key"),
            ownerUserId = actor,
            appId = app.id,
            label = input.label.trim(),
            keyPrefix = prefix,
            keyHash = hash,
            scopes = input.scopes,
            status = M27ApiKeyStatus.ACTIVE,
            environment = input.environment,
            createdAt = stamp,
            expiresAt = null
        )
        store.apiKeys.value = store.apiKeys.value + cred
        input.clientRequestId?.let { store.clientRequests[idempotencyKey(actor, it)!!] = cred.id }
        audit(store, actor, app.id, "CREATE_KEY", "OK", cred.environment, "prefix=$prefix")
        return Result.success(M27IssuedCredential(cred, plaintextKeyOnce = raw))
    }

    fun rotateApiKey(store: M27IntegrationMemoryStore, actor: String, keyId: String): Result<M27IssuedCredential> {
        val idx = store.apiKeys.value.indexOfFirst { it.id == keyId }
        if (idx < 0) return M27IntegrationErrors.failure(M27IntegrationException("M27_KEY_NOT_FOUND", "nf"))
        val current = store.apiKeys.value[idx]
        val app = store.apps.value.find { it.id == current.appId }
        if (app != null && !canManageOrg(actor, app.organizationId)) {
            return M27IntegrationErrors.failure(M27IntegrationException("M27_PERMISSION_DENIED", "perm"))
        }
        store.apiKeys.value = store.apiKeys.value.toMutableList().also {
            it[idx] = current.copy(status = M27ApiKeyStatus.REVOKED)
        }
        return createApiKey(
            store, actor,
            CreateM27ApiKeyInput(current.appId ?: "", current.label, current.scopes, current.environment)
        )
    }

    fun registerEndpoint(
        store: M27IntegrationMemoryStore,
        actor: String,
        input: RegisterM27WebhookEndpointInput
    ): Result<M27WebhookEndpointWithSecret> {
        val app = store.apps.value.find { it.id == input.appId }
            ?: return M27IntegrationErrors.failure(M27IntegrationException("M27_APP_NOT_FOUND", "nf"))
        if (!canManageOrg(actor, app.organizationId)) {
            return M27IntegrationErrors.failure(M27IntegrationException("M27_PERMISSION_DENIED", "perm"))
        }
        if (!M27AppLifecycle.canOperate(app.status)) {
            return M27IntegrationErrors.failure(M27IntegrationException("M27_APP_NOT_ACTIVE", "inactive"))
        }
        M27SsrfValidator.validateTargetUrl(input.targetUrl)?.let {
            return M27IntegrationErrors.failure(M27IntegrationException(it, it))
        }
        idempotencyKey(actor, input.clientRequestId)?.let { key ->
            store.clientRequests[key]?.let { existingId ->
                store.webhooks.value.find { it.id == existingId }?.let {
                    return Result.success(M27WebhookEndpointWithSecret(it, secretOnce = null))
                }
            }
        }
        val rawSecret = "whsec_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val stamp = System.currentTimeMillis()
        val endpoint = M27WebhookEndpoint(
            id = store.nextId("m27_endpoint"),
            ownerUserId = actor,
            appId = app.id,
            label = input.label.trim(),
            targetUrl = input.targetUrl.trim(),
            secretPrefix = rawSecret.take(10),
            secretHash = M27CredentialHasher.hashSecret(rawSecret),
            status = M27WebhookStatus.PENDING_VERIFICATION,
            environment = input.environment,
            createdAt = stamp,
            updatedAt = stamp
        )
        store.webhooks.value = store.webhooks.value + endpoint
        input.clientRequestId?.let { store.clientRequests[idempotencyKey(actor, it)!!] = endpoint.id }
        audit(store, actor, app.id, "REGISTER_ENDPOINT", "OK", endpoint.environment, null)
        return Result.success(M27WebhookEndpointWithSecret(endpoint, secretOnce = rawSecret))
    }

    fun verifyEndpoint(store: M27IntegrationMemoryStore, actor: String, endpointId: String): Result<M27WebhookEndpoint> {
        val idx = store.webhooks.value.indexOfFirst { it.id == endpointId && it.ownerUserId == actor }
        if (idx < 0) return M27IntegrationErrors.failure(M27IntegrationException("M27_WEBHOOK_NOT_FOUND", "nf"))
        val current = store.webhooks.value[idx]
        val updated = current.copy(status = M27WebhookStatus.ACTIVE, updatedAt = System.currentTimeMillis())
        store.webhooks.value = store.webhooks.value.toMutableList().also { it[idx] = updated }
        return Result.success(updated)
    }

    fun subscribe(
        store: M27IntegrationMemoryStore,
        actor: String,
        input: SubscribeM27WebhookInput
    ): Result<M27WebhookSubscription> {
        val endpoint = store.webhooks.value.find { it.id == input.endpointId && it.ownerUserId == actor }
            ?: return M27IntegrationErrors.failure(M27IntegrationException("M27_WEBHOOK_NOT_FOUND", "nf"))
        if (endpoint.status != M27WebhookStatus.ACTIVE) {
            return M27IntegrationErrors.failure(M27IntegrationException("M27_ENDPOINT_NOT_ACTIVE", "inactive"))
        }
        val dupKey = "${input.endpointId}:${input.eventType.name}"
        store.subscriptions.value.find { "${it.endpointId}:${it.eventType.name}" == dupKey && it.active }?.let {
            return Result.success(it)
        }
        val sub = M27WebhookSubscription(
            id = store.nextId("m27_sub"),
            endpointId = input.endpointId,
            eventType = input.eventType,
            active = true,
            createdAt = System.currentTimeMillis()
        )
        store.subscriptions.value = store.subscriptions.value + sub
        return Result.success(sub)
    }

    fun emitEvent(
        store: M27IntegrationMemoryStore,
        actor: String,
        input: EmitM27WebhookEventInput
    ): Result<M27WebhookEvent> {
        val app = store.apps.value.find { it.id == input.appId }
            ?: return M27IntegrationErrors.failure(M27IntegrationException("M27_APP_NOT_FOUND", "nf"))
        if (!canManageOrg(actor, app.organizationId)) {
            return M27IntegrationErrors.failure(M27IntegrationException("M27_PERMISSION_DENIED", "perm"))
        }
        if (!M27AppLifecycle.canOperate(app.status)) {
            return M27IntegrationErrors.failure(M27IntegrationException("M27_APP_NOT_ACTIVE", "inactive"))
        }
        if (M27PrivacySanitizer.scrubPublicText(input.sanitizedPayload) != input.sanitizedPayload.trim()) {
            return M27IntegrationErrors.failure(M27IntegrationException("M27_INVALID_PAYLOAD", "payload"))
        }
        idempotencyKey(actor, input.clientRequestId)?.let { key ->
            store.eventIdempotency[key]?.let { existingId ->
                store.events.value.find { it.id == existingId }?.let { return Result.success(it) }
            }
        }
        val event = M27WebhookEvent(
            id = store.nextId("m27_event"),
            appId = app.id,
            eventType = input.eventType,
            version = "1",
            resourceRef = input.resourceRef.trim(),
            sanitizedPayload = input.sanitizedPayload.trim(),
            occurredAt = System.currentTimeMillis()
        )
        store.events.value = store.events.value + event
        input.clientRequestId?.let { store.eventIdempotency[idempotencyKey(actor, it)!!] = event.id }
        createDeliveriesForEvent(store, event)
        return Result.success(event)
    }

    private fun createDeliveriesForEvent(store: M27IntegrationMemoryStore, event: M27WebhookEvent) {
        val endpoints = store.webhooks.value.filter { it.appId == event.appId && it.status == M27WebhookStatus.ACTIVE }
        val subs = store.subscriptions.value.filter { it.active && it.eventType == event.eventType }
        subs.forEach { sub ->
            if (endpoints.none { it.id == sub.endpointId }) return@forEach
            val dup = "${event.id}:${sub.id}"
            if (store.deliveryKeys.contains(dup)) return@forEach
            store.deliveryKeys.add(dup)
            val endpoint = endpoints.first { it.id == sub.endpointId }
            val canonical = M27WebhookSignatureService.canonicalPayload(event.id, sub.id, event.sanitizedPayload)
            val sig = M27WebhookSignatureService.sign(endpoint.secretHash, event.occurredAt, canonical)
            val delivery = M27WebhookDelivery(
                id = store.nextId("m27_delivery"),
                eventId = event.id,
                subscriptionId = sub.id,
                endpointId = sub.endpointId,
                status = M27WebhookDeliveryStatus.PENDING,
                attemptCount = 0,
                maxAttempts = M27WebhookDeliveryPolicy.MAX_ATTEMPTS,
                signatureVersion = M27WebhookSignatureService.VERSION,
                signatureDigest = M27WebhookSignatureService.digestForDisplay(sig),
                lastAttemptAt = null,
                createdAt = System.currentTimeMillis()
            )
            store.deliveries.value = store.deliveries.value + delivery
            simulateDelivery(store, delivery, endpoint, event, sig)
        }
    }

    private fun simulateDelivery(
        store: M27IntegrationMemoryStore,
        delivery: M27WebhookDelivery,
        endpoint: M27WebhookEndpoint,
        event: M27WebhookEvent,
        signature: String
    ) {
        val attemptNum = delivery.attemptCount + 1
        val success = endpoint.environment != M27Environment.SANDBOX || attemptNum <= 2
        val outcome = if (success) "HTTP_200" else "HTTP_503"
        val attempt = M27WebhookAttempt(
            id = store.nextId("m27_attempt"),
            deliveryId = delivery.id,
            attemptNumber = attemptNum,
            outcome = outcome,
            sanitizedError = if (success) null else "Servicio no disponible (simulado)",
            createdAt = System.currentTimeMillis()
        )
        store.attempts.value = store.attempts.value + attempt
        val nextStatus = M27WebhookDeliveryPolicy.nextStatusAfterAttempt(delivery.status, attemptNum, success)
        val idx = store.deliveries.value.indexOfFirst { it.id == delivery.id }
        if (idx >= 0) {
            store.deliveries.value = store.deliveries.value.toMutableList().also {
                it[idx] = delivery.copy(
                    status = nextStatus,
                    attemptCount = attemptNum,
                    lastAttemptAt = attempt.createdAt,
                    signatureDigest = M27WebhookSignatureService.digestForDisplay(signature)
                )
            }
        }
    }

    fun manualRetry(store: M27IntegrationMemoryStore, actor: String, deliveryId: String): Result<M27WebhookDelivery> {
        if (actor != M27MockUsers.ADMIN && actor != M27MockUsers.DEVELOPER) {
            return M27IntegrationErrors.failure(M27IntegrationException("M27_PERMISSION_DENIED", "perm"))
        }
        val idx = store.deliveries.value.indexOfFirst { it.id == deliveryId }
        if (idx < 0) return M27IntegrationErrors.failure(M27IntegrationException("M27_DELIVERY_NOT_FOUND", "nf"))
        val delivery = store.deliveries.value[idx]
        if (M27WebhookDeliveryPolicy.isTerminal(delivery.status)) {
            return M27IntegrationErrors.failure(M27IntegrationException("M27_DELIVERY_TERMINAL", "terminal"))
        }
        val event = store.events.value.find { it.id == delivery.eventId } ?: return M27IntegrationErrors.failure(
            M27IntegrationException("M27_EVENT_NOT_FOUND", "nf")
        )
        val endpoint = store.webhooks.value.find { it.id == delivery.endpointId } ?: return M27IntegrationErrors.failure(
            M27IntegrationException("M27_WEBHOOK_NOT_FOUND", "nf")
        )
        val canonical = M27WebhookSignatureService.canonicalPayload(event.id, delivery.subscriptionId, event.sanitizedPayload)
        val sig = M27WebhookSignatureService.sign(endpoint.secretHash, System.currentTimeMillis(), canonical)
        simulateDelivery(store, delivery, endpoint, event, sig)
        return Result.success(store.deliveries.value.first { it.id == deliveryId })
    }

    fun checkRateLimit(store: M27IntegrationMemoryStore, appId: String, environment: M27Environment): M27RateLimitResult {
        val key = "$appId:${environment.name}"
        val count = store.rateCounters[key] ?: 0
        val limit = if (environment == M27Environment.SANDBOX) 5 else 30
        val result = M27RateLimitEnforcer.evaluate(environment, count, limit)
        if (result.allowed) store.rateCounters[key] = count + 1
        return result
    }

    fun startOAuthStub(store: M27IntegrationMemoryStore, redirectUri: String, scopes: List<String>, state: String?): Result<M27OAuthStubSession> {
        if (state.isNullOrBlank()) return M27IntegrationErrors.failure(M27IntegrationException("M27_OAUTH_STATE_REQUIRED", "state"))
        M27SsrfValidator.validateTargetUrl(redirectUri)?.let {
            return M27IntegrationErrors.failure(M27IntegrationException(it, it))
        }
        M27ScopePolicy.validateGrantList(scopes)?.let {
            return M27IntegrationErrors.failure(M27IntegrationException(it, it))
        }
        val session = M27OAuthStubSession(
            state = state,
            redirectUri = redirectUri,
            scopes = scopes,
            stubTokenPrefix = "stub_tok_${(100..999).random()}",
            expiresAt = System.currentTimeMillis() + 3600_000
        )
        store.oauthSessions[state] = session
        return Result.success(session)
    }

    private fun audit(
        store: M27IntegrationMemoryStore,
        actor: String,
        appId: String?,
        operation: String,
        outcome: String,
        environment: M27Environment,
        reason: String?
    ) {
        val entry = M27AuditEntry(
            id = store.nextId("m27_audit"),
            actorUserId = actor,
            appId = appId,
            operation = operation,
            outcome = outcome,
            environment = environment,
            sanitizedReason = reason?.let { M27PrivacySanitizer.scrubPublicText(it) },
            createdAt = System.currentTimeMillis()
        )
        store.auditLog.value = store.auditLog.value + entry
    }
}

data class M27WebhookEndpointWithSecret(val endpoint: M27WebhookEndpoint, val secretOnce: String?)
