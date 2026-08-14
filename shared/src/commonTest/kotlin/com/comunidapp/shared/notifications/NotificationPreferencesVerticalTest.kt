package com.comunidapp.shared.notifications

import com.comunidapp.shared.auth.FakeAuthSessionGateway
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class NotificationPreferencesVerticalTest {

    private fun auth() =
        GatewayAuthRepository(
            FakeAuthSessionGateway(
                SessionState.Authenticated(SessionUser("u1", "a@leover.test", "Ana"))
            )
        )

    @Test
    fun get_preferences_success() = runTest {
        val gw = FakeNotificationPreferencesGateway()
        val repo = RemoteNotificationPreferencesRepository(gw, auth())
        val prefs = repo.getPreferences().getOrThrow()
        assertTrue(prefs.isNotEmpty())
        assertTrue(prefs.any { it.category == "PET" })
        assertTrue(prefs.none { it.emailEnabled })
    }

    @Test
    fun update_forces_email_false() = runTest {
        val gw = FakeNotificationPreferencesGateway()
        val repo = RemoteNotificationPreferencesRepository(gw, auth())
        val result = repo.updatePreference(
            SharedNotificationPreference(
                category = "PET",
                pushEnabled = false,
                emailEnabled = true
            )
        )
        assertIs<NotificationPreferenceWriteResult.Success>(result)
        assertEquals(false, gw.lastEmailEnabled)
        assertFalse(result.preference.emailEnabled)
    }

    @Test
    fun in_app_mandatory_sanitized() = runTest {
        val gw = FakeNotificationPreferencesGateway()
        val repo = RemoteNotificationPreferencesRepository(gw, auth())
        val first = repo.updatePreference(
            SharedNotificationPreference(category = "SECURITY", inAppEnabled = false)
        )
        assertIs<NotificationPreferenceWriteResult.ValidationError>(first)
        val sanitized = repo.updatePreferenceSanitized(
            SharedNotificationPreference(category = "SECURITY", inAppEnabled = false, pushEnabled = true)
        )
        assertIs<NotificationPreferenceWriteResult.Success>(sanitized)
        assertTrue(sanitized.preference.inAppEnabled)
    }

    @Test
    fun unauthenticated() = runTest {
        val authRepo = GatewayAuthRepository(FakeAuthSessionGateway(SessionState.Unauthenticated))
        val repo = RemoteNotificationPreferencesRepository(
            FakeNotificationPreferencesGateway(),
            authRepo
        )
        assertTrue(repo.getPreferences().isFailure)
        assertIs<NotificationPreferenceWriteResult.Unauthenticated>(
            repo.updatePreference(SharedNotificationPreference(category = "PET"))
        )
    }

    @Test
    fun unconfigured_no_fake_success() = runTest {
        val result = UnconfiguredNotificationPreferencesRepository().updatePreference(
            SharedNotificationPreference(category = "PET")
        )
        assertIs<NotificationPreferenceWriteResult.BackendError>(result)
    }

    @Test
    fun fake_repo() = runTest {
        val fake = FakeNotificationPreferencesRepository()
        val prefs = fake.getPreferences().getOrThrow()
        assertTrue(prefs.isNotEmpty())
        val updated = fake.updatePreference(
            SharedNotificationPreference(category = "PET", pushEnabled = false)
        )
        assertIs<NotificationPreferenceWriteResult.Success>(updated)
        assertFalse(updated.preference.pushEnabled)
    }
}
