package com.comunidapp.app.domain.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NotificationInstallationRulesTest {

    private val now = Instant.parse("2026-07-15T12:00:00Z")

    @Test
    fun register_and_no_raw_token_in_tostring() {
        val raw = "fcm-raw-secret-token-xyz"
        val fp = NotificationInstallationRules.fingerprintOf(raw)
        val inst = NotificationInstallationRules.register(
            installationId = "inst-1",
            userId = "u1",
            platform = NotificationInstallationPlatform.ANDROID,
            tokenFingerprint = fp,
            now = now
        ).getOrThrow()
        assertFalse(inst.toString().contains(raw))
        assertTrue(inst.toString().contains("tokenFingerprint"))
        assertFalse(NotificationInstallationRules.containsRawTokenLeak(inst.toString(), raw))
    }

    @Test
    fun revoke_current_only() {
        val inst = NotificationInstallationRules.register(
            "inst-1", "u1", NotificationInstallationPlatform.ANDROID, "abcd1234", now
        ).getOrThrow()
        val wrong = NotificationInstallationRules.revokeCurrent(inst, "inst-other", now)
        assertTrue(wrong.isFailure)
        val ok = NotificationInstallationRules.revokeCurrent(inst, "inst-1", now).getOrThrow()
        assertTrue(ok.revokedAt != null)
        assertFalse(ok.isActive)
    }

    @Test
    fun rotate_fingerprint() {
        val inst = NotificationInstallationRules.register(
            "inst-1", "u1", NotificationInstallationPlatform.ANDROID, "abcd1234", now
        ).getOrThrow()
        val rotated = NotificationInstallationRules.rotateFingerprint(inst, "deadbeef", now).getOrThrow()
        assertTrue(rotated.tokenFingerprint == "deadbeef")
    }

    @Test
    fun account_switch_unbinds_previous() {
        val inst = NotificationInstallationRules.register(
            "inst-1", "u1", NotificationInstallationPlatform.ANDROID, "abcd1234", now
        ).getOrThrow()
        val (old, neu) = NotificationInstallationRules.switchAccount(
            inst, "u2", "beefcafe", now
        ).getOrThrow()
        assertTrue(old.revokedAt != null)
        assertTrue(neu.userId == "u2")
        assertTrue(neu.installationId == "inst-1")
    }
}
