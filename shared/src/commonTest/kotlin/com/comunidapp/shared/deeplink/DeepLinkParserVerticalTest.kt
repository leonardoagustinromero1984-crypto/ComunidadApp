package com.comunidapp.shared.deeplink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeepLinkParserVerticalTest {

    @Test
    fun https_pet_public() {
        val t = DeepLinkParser.parse("https://leover.com.ar/mascota/PUB-ABC123")
        val pet = assertIs<DeepLinkTarget.PetPublic>(t)
        assertEquals("PUB-ABC123", pet.publicCode)
    }

    @Test
    fun https_www_adoption() {
        val t = DeepLinkParser.parse("https://www.leover.com.ar/adopciones/PUB-ADOPT1")
        assertEquals("PUB-ADOPT1", assertIs<DeepLinkTarget.AdoptionPublic>(t).publicCode)
    }

    @Test
    fun https_lost_and_found() {
        assertIs<DeepLinkTarget.LostCase>(
            DeepLinkParser.parse("https://leover.com.ar/perdidos/PUB-L1")
        )
        assertIs<DeepLinkTarget.FoundCase>(
            DeepLinkParser.parse("https://leover.com.ar/encontrados/PUB-F1")
        )
    }

    @Test
    fun custom_passport() {
        val t = DeepLinkParser.parse("leover://passport/PUB-PASS1")
        assertEquals("PUB-PASS1", assertIs<DeepLinkTarget.Passport>(t).publicCode)
    }

    @Test
    fun url_decode_code() {
        val t = DeepLinkParser.parse("https://leover.com.ar/mascota/PUB-%2DCODE")
        assertEquals("PUB--CODE", assertIs<DeepLinkTarget.PetPublic>(t).publicCode)
    }

    @Test
    fun reject_wrong_host() {
        val t = DeepLinkParser.parse("https://evil.example/mascota/PUB-X")
        assertEquals("wrong_host", assertIs<DeepLinkTarget.Unsupported>(t).reason)
    }

    @Test
    fun reject_blank_and_forbidden_schemes() {
        assertEquals("blank", assertIs<DeepLinkTarget.Unsupported>(DeepLinkParser.parse("   ")).reason)
        assertEquals(
            "forbidden_scheme",
            assertIs<DeepLinkTarget.Unsupported>(DeepLinkParser.parse("javascript:alert(1)")).reason
        )
        assertEquals(
            "forbidden_scheme",
            assertIs<DeepLinkTarget.Unsupported>(DeepLinkParser.parse("file:///tmp/x")).reason
        )
        assertEquals(
            "forbidden_scheme",
            assertIs<DeepLinkTarget.Unsupported>(DeepLinkParser.parse("data:text/plain,hi")).reason
        )
        assertEquals(
            "forbidden_scheme",
            assertIs<DeepLinkTarget.Unsupported>(DeepLinkParser.parse("content://media/1")).reason
        )
    }

    @Test
    fun reject_empty_code_and_bad_chars() {
        assertIs<DeepLinkTarget.Unsupported>(
            DeepLinkParser.parse("https://leover.com.ar/mascota/")
        )
        assertIs<DeepLinkTarget.Unsupported>(
            DeepLinkParser.parse("https://leover.com.ar/mascota/bad code")
        )
        assertIs<DeepLinkTarget.Unsupported>(
            DeepLinkParser.parse("https://leover.com.ar/mascota/../../etc")
        )
    }

    @Test
    fun reject_unsupported_scheme() {
        assertEquals(
            "unsupported_scheme",
            assertIs<DeepLinkTarget.Unsupported>(DeepLinkParser.parse("ftp://leover.com.ar/mascota/PUB-X")).reason
        )
    }

    @Test
    fun unsupported_reason_is_safe_label_not_raw_url() {
        val raw = "https://evil.example/steal?token=secret"
        val t = assertIs<DeepLinkTarget.Unsupported>(DeepLinkParser.parse(raw))
        assertTrue(raw !in t.reason)
        assertTrue("secret" !in t.reason)
    }

    @Test
    fun notification_pet_pub_code() {
        val t = NotificationIntentParser.fromPushExtras("PET", "PUB-PET1")
        assertEquals("PUB-PET1", assertIs<DeepLinkTarget.PetPublic>(t).publicCode)
    }

    @Test
    fun notification_uuid_falls_to_safe_home() {
        assertIs<DeepLinkTarget.SafeHome>(
            NotificationIntentParser.fromPushExtras(
                "PET",
                "550e8400-e29b-41d4-a716-446655440000"
            )
        )
        assertIs<DeepLinkTarget.SafeHome>(
            NotificationIntentParser.fromPushExtras(
                "ADOPTION",
                "550e8400-e29b-41d4-a716-446655440000"
            )
        )
        assertIs<DeepLinkTarget.SafeHome>(
            NotificationIntentParser.fromPushExtras(
                "LOST_FOUND_CASE",
                "550e8400-e29b-41d4-a716-446655440000"
            )
        )
    }

    @Test
    fun notification_adoption_and_lost_pub() {
        assertIs<DeepLinkTarget.AdoptionPublic>(
            NotificationIntentParser.fromPushExtras("ADOPTION", "PUB-A1")
        )
        assertIs<DeepLinkTarget.LostCase>(
            NotificationIntentParser.fromPushExtras("LOST_FOUND_CASE", "PUB-LF1")
        )
    }

    @Test
    fun notification_unknown_or_blank_safe_home() {
        assertIs<DeepLinkTarget.SafeHome>(
            NotificationIntentParser.fromPushExtras(null, null)
        )
        assertIs<DeepLinkTarget.SafeHome>(
            NotificationIntentParser.fromPushExtras("CHAT", "PUB-X")
        )
        assertIs<DeepLinkTarget.SafeHome>(
            NotificationIntentParser.fromPushExtras("SAFE_HOME", null)
        )
    }

    @Test
    fun pending_store_consumes_once() {
        DeepLinkPendingStore.clear()
        DeepLinkPendingStore.set(DeepLinkTarget.PetPublic("PUB-1"))
        assertEquals("PUB-1", assertIs<DeepLinkTarget.PetPublic>(DeepLinkPendingStore.peek()!!).publicCode)
        assertEquals("PUB-1", assertIs<DeepLinkTarget.PetPublic>(DeepLinkPendingStore.consume()!!).publicCode)
        assertEquals(null, DeepLinkPendingStore.peek())
    }
}
