package com.comunidapp.app.domain.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSensitivityRulesTest {

    @Test
    fun sensitive_push_uses_generic_body() {
        val (title, body) = NotificationSensitivityRules.resolvePushCopy(
            NotificationSensitivity.SENSITIVE,
            "Caso de moderación",
            "Detalle privado del caso"
        )
        assertEquals(NotificationSensitivityRules.GENERIC_PUSH_TITLE, title)
        assertEquals(NotificationSensitivityRules.GENERIC_PUSH_BODY, body)
        assertFalse(body.contains("Detalle"))
    }

    @Test
    fun security_critical_lockscreen_generic() {
        val text = NotificationSensitivityRules.resolveLockScreenText(
            NotificationSensitivity.SECURITY_CRITICAL,
            "Tu contraseña cambió"
        )
        assertEquals(NotificationSensitivityRules.GENERIC_LOCKSCREEN_TEXT, text)
    }

    @Test
    fun private_allows_summary_on_push() {
        assertTrue(NotificationSensitivityRules.allowsFullBodyOnPush(NotificationSensitivity.PRIVATE))
        val (title, body) = NotificationSensitivityRules.resolvePushCopy(
            NotificationSensitivity.PRIVATE,
            "Amistad",
            "Nueva solicitud"
        )
        assertEquals("Amistad", title)
        assertEquals("Nueva solicitud", body)
    }

    @Test
    fun unknown_sensitivity_string_is_null() {
        assertEquals(null, NotificationSensitivity.fromString("NOPE"))
    }
}
