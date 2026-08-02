package com.comunidapp.app.domain.m27

import com.comunidapp.app.data.model.M27ApiCredential
import com.comunidapp.app.data.model.M27ApiContract
import com.comunidapp.app.data.model.M27OAuthApplication
import com.comunidapp.app.data.model.M27PublicApiKey
import com.comunidapp.app.data.model.M27PublicContract
import com.comunidapp.app.data.model.M27PublicOAuthApp
import com.comunidapp.app.data.model.M27PublicWebhook
import com.comunidapp.app.data.model.M27WebhookEndpoint

object M27PrivacySanitizer {
    private val secretPattern = Regex("(?i)(secret|token|bearer|api[_-]?key|client[_-]?secret)\\s*[:=]\\s*\\S+")

    fun scrubPublicText(text: String): String = text
        .replace(secretPattern, "[redactado]")
        .trim()

    fun toPublicWebhook(webhook: M27WebhookEndpoint): M27PublicWebhook = M27PublicWebhook(
        label = scrubPublicText(webhook.label),
        targetUrl = scrubPublicText(webhook.targetUrl),
        secretPrefix = webhook.secretPrefix,
        status = webhook.status,
        environment = webhook.environment
    )

    fun toPublicOAuthApp(app: M27OAuthApplication): M27PublicOAuthApp = M27PublicOAuthApp(
        name = scrubPublicText(app.name),
        redirectUri = scrubPublicText(app.redirectUri),
        clientIdPrefix = app.clientIdPrefix,
        scopes = app.scopes,
        status = app.status,
        environment = app.environment
    )

    fun toPublicApiKey(key: M27ApiCredential): M27PublicApiKey = M27PublicApiKey(
        label = scrubPublicText(key.label),
        keyPrefix = key.keyPrefix,
        scopes = key.scopes,
        status = key.status,
        environment = key.environment
    )

    fun toPublicContract(contract: M27ApiContract): M27PublicContract = M27PublicContract(
        title = scrubPublicText(contract.title),
        version = contract.version,
        summary = scrubPublicText(contract.summary),
        publishedForDisplay = M27ContractEligibilityService.isEligibleForDisplay(contract)
    )
}
