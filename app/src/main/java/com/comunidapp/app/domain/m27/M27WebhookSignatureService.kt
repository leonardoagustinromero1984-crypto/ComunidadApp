package com.comunidapp.app.domain.m27

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object M27CredentialHasher {
    fun hashSecret(raw: String): String =
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }

    fun prefixFor(raw: String, envPrefix: String): String =
        "$envPrefix${raw.takeLast(4)}"
}

object M27WebhookSignatureService {
    const val VERSION = "v1"
    private const val SEP = "."

    fun canonicalPayload(eventId: String, deliveryId: String, payload: String): String =
        listOf(eventId, deliveryId, payload.trim()).joinToString(SEP)

    fun sign(secretHash: String, timestamp: Long, canonical: String): String {
        val material = "$timestamp$SEP$canonical"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretHash.toByteArray(), "HmacSHA256"))
        return mac.doFinal(material.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun digestForDisplay(signature: String): String = signature.take(12)
}
