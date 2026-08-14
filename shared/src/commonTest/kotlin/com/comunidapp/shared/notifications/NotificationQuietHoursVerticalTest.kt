package com.comunidapp.shared.notifications

import com.comunidapp.shared.auth.FakeAuthSessionGateway
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class NotificationQuietHoursVerticalTest {

    private fun auth() =
        GatewayAuthRepository(
            FakeAuthSessionGateway(
                SessionState.Authenticated(SessionUser("u1", "a@leover.test", "Ana"))
            )
        )

    @Test
    fun load_with_quiet_hours() = runTest {
        val gw = FakeNotificationPreferencesGateway(
            rows = mutableListOf(
                RemoteNotificationPreferenceRow(
                    category = "PET",
                    quietHoursStart = "22:00",
                    quietHoursEnd = "07:00",
                    timezone = "America/Argentina/Buenos_Aires",
                    marketingConsent = true,
                    quietHoursDays = listOf(1, 2, 3, 4, 5)
                )
            )
        )
        val repo = RemoteNotificationPreferencesRepository(gw, auth())
        val prefs = repo.getPreferences().getOrThrow()
        assertEquals(1, prefs.size)
        assertEquals("22:00", prefs[0].quietHoursStart)
        assertEquals("07:00", prefs[0].quietHoursEnd)
        assertEquals("America/Argentina/Buenos_Aires", prefs[0].timezone)
        assertEquals(listOf(1, 2, 3, 4, 5), prefs[0].quietHoursDays)
        assertTrue(prefs[0].marketingConsent)
    }

    @Test
    fun save_valid_quiet_hours() = runTest {
        val gw = FakeNotificationPreferencesGateway()
        val repo = RemoteNotificationPreferencesRepository(gw, auth())
        val result = repo.updatePreference(
            SharedNotificationPreference(
                category = "PET",
                pushEnabled = true,
                quietHoursStart = "22:30",
                quietHoursEnd = "06:15:00",
                timezone = "UTC",
                marketingConsent = true,
                quietHoursDays = listOf(1, 2, 3, 4, 5, 6, 7)
            )
        )
        assertIs<NotificationPreferenceWriteResult.Success>(result)
        assertEquals("22:30", gw.lastQuietHoursStart)
        assertEquals("06:15:00", gw.lastQuietHoursEnd)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), gw.lastQuietHoursDays)
        assertEquals(true, gw.lastMarketingConsent)
        assertEquals("UTC", gw.lastTimezone)
    }

    @Test
    fun invalid_range_start_without_end() = runTest {
        val gw = FakeNotificationPreferencesGateway()
        val repo = RemoteNotificationPreferencesRepository(gw, auth())
        val result = repo.updatePreference(
            SharedNotificationPreference(
                category = "PET",
                quietHoursStart = "22:00",
                quietHoursEnd = null
            )
        )
        assertIs<NotificationPreferenceWriteResult.ValidationError>(result)
        assertEquals(0, gw.updateCalls)
        assertTrue(QuietHoursValidator.validate("22:00", null).isFailure)
    }

    @Test
    fun timezone_blank_maps_to_utc() = runTest {
        val gw = FakeNotificationPreferencesGateway()
        val repo = RemoteNotificationPreferencesRepository(gw, auth())
        val result = repo.updatePreference(
            SharedNotificationPreference(
                category = "PET",
                timezone = "  ",
                quietHoursStart = "22:00",
                quietHoursEnd = "07:00"
            )
        )
        assertIs<NotificationPreferenceWriteResult.Success>(result)
        assertEquals("UTC", gw.lastTimezone)
        assertEquals("UTC", result.preference.timezone)
    }

    @Test
    fun null_quiet_hours_days_sent_as_null_for_sql_default() = runTest {
        val gw = FakeNotificationPreferencesGateway()
        val repo = RemoteNotificationPreferencesRepository(gw, auth())
        val result = repo.updatePreference(
            SharedNotificationPreference(
                category = "PET",
                quietHoursStart = "22:00",
                quietHoursEnd = "07:00",
                quietHoursDays = null
            )
        )
        assertIs<NotificationPreferenceWriteResult.Success>(result)
        assertNull(gw.lastQuietHoursDays)
    }

    @Test
    fun apply_quiet_hours_to_all_preserves_push_flags() = runTest {
        val gw = FakeNotificationPreferencesGateway(
            rows = mutableListOf(
                RemoteNotificationPreferenceRow(category = "PET", pushEnabled = true),
                RemoteNotificationPreferenceRow(category = "LOST_FOUND", pushEnabled = false)
            )
        )
        val repo = RemoteNotificationPreferencesRepository(gw, auth())
        val loaded = repo.getPreferences().getOrThrow()
        val result = repo.applyQuietHoursToAll(
            loaded = loaded,
            quietHoursStart = "23:00",
            quietHoursEnd = "06:00",
            timezone = "UTC",
            marketingConsent = false,
            quietHoursDays = null
        )
        assertIs<NotificationPreferenceWriteResult.Success>(result)
        assertEquals(2, gw.updateCalls)
        val prefs = repo.getPreferences().getOrThrow()
        assertEquals(true, prefs.first { it.category == "PET" }.pushEnabled)
        assertEquals(false, prefs.first { it.category == "LOST_FOUND" }.pushEnabled)
        assertTrue(prefs.all { it.quietHoursStart == "23:00" })
    }

    @Test
    fun backend_unavailable() = runTest {
        val gw = FakeNotificationPreferencesGateway(
            updateError = IllegalStateException("network timeout")
        )
        val repo = RemoteNotificationPreferencesRepository(gw, auth())
        val result = repo.updatePreference(
            SharedNotificationPreference(
                category = "PET",
                quietHoursStart = "22:00",
                quietHoursEnd = "07:00"
            )
        )
        assertIs<NotificationPreferenceWriteResult.BackendError>(result)
    }

    @Test
    fun push_prefs_regression() = runTest {
        val gw = FakeNotificationPreferencesGateway()
        val repo = RemoteNotificationPreferencesRepository(gw, auth())
        val result = repo.updatePreference(
            SharedNotificationPreference(category = "PET", pushEnabled = false, emailEnabled = true)
        )
        assertIs<NotificationPreferenceWriteResult.Success>(result)
        assertFalse(result.preference.pushEnabled)
        assertFalse(result.preference.emailEnabled)
        assertEquals(false, gw.lastEmailEnabled)
    }

    @Test
    fun unconfigured_no_fake_success() = runTest {
        val result = UnconfiguredNotificationPreferencesRepository().updatePreference(
            SharedNotificationPreference(
                category = "PET",
                quietHoursStart = "22:00",
                quietHoursEnd = "07:00"
            )
        )
        assertIs<NotificationPreferenceWriteResult.BackendError>(result)
    }
}
