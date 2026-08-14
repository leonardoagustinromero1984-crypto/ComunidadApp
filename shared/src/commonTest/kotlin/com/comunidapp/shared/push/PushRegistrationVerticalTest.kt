package com.comunidapp.shared.push

import com.comunidapp.shared.crypto.sha256HexOfUtf8
import com.comunidapp.shared.session.FakeSessionRepository
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PushRegistrationVerticalTest {

    @Test
    fun fingerprint_is_sha256_hex_never_raw() {
        val raw = "apns-raw-secret-token-xyz"
        val fp = PushTokenFingerprintRules.ofRawUtf8Token(raw)
        assertEquals(64, fp.hexSha256.length)
        assertTrue(fp.hexSha256.matches(Regex("^[a-f0-9]{64}$")))
        assertFalse(fp.toString().contains(raw))
        assertFalse(fp.hexSha256.contains(raw))
        assertEquals(sha256HexOfUtf8(raw), fp.hexSha256)
    }

    @Test
    fun fake_register_success_stores_fingerprint_only() = runTest {
        val repo = FakePushInstallationRepository()
        val result = repo.registerIosInstallation(
            installationId = "inst-1",
            tokenFingerprint = PushTokenFingerprintRules.ofRawUtf8Token("tok").hexSha256,
            tokenReference = null,
            appVersion = "1.0"
        )
        assertIs<PushRegistrationResult.Success>(result)
        assertEquals(1, repo.registerCalls)
        assertEquals("inst-1", repo.lastInstallationId)
        assertFalse(repo.lastFingerprint.orEmpty().contains("tok"))
        assertEquals(64, repo.lastFingerprint.orEmpty().length)
    }

    @Test
    fun revoke_current() = runTest {
        val repo = FakePushInstallationRepository()
        assertIs<PushRegistrationResult.Success>(repo.revokeCurrent("inst-1"))
        assertEquals(1, repo.revokeCalls)
    }

    @Test
    fun unconfigured_unavailable() = runTest {
        val repo = UnconfiguredPushInstallationRepository()
        assertIs<PushRegistrationResult.Unavailable>(
            repo.registerIosInstallation("i", "abcd1234", null, null)
        )
    }

    @Test
    fun registration_messages_never_include_raw_token() = runTest {
        val raw = "super-secret-device-token"
        val fp = PushTokenFingerprintRules.ofRawUtf8Token(raw).hexSha256
        val repo = FakePushInstallationRepository(
            registerResult = PushRegistrationResult.Failed("No pudimos registrar el dispositivo.")
        )
        val result = repo.registerIosInstallation("inst", fp, null, null)
        val failed = assertIs<PushRegistrationResult.Failed>(result)
        assertFalse(failed.message.contains(raw))
        assertFalse(failed.toString().contains(raw))
    }

    @Test
    fun authenticated_session_helper_for_remote_path() = runTest {
        val session = FakeSessionRepository(
            SessionState.Authenticated(SessionUser("u1", "a@test.com", "A"))
        )
        assertIs<SessionState.Authenticated>(session.currentSession())
    }
}
