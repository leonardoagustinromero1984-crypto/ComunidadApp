package com.comunidapp.app.data.remote.supabase.m27

import com.comunidapp.app.data.model.M27Environment
import com.comunidapp.app.data.model.M27WebhookStatus
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M27IntegrationRemoteMapperTest {
    @Test fun mapsPublicWebhookWithoutOwnerId() {
        val json = buildJsonObject {
            put("label", "Eventos")
            put("target_url", "https://hooks.example.com/leover")
            put("secret_prefix", "whsec_1234")
            put("status", "ACTIVE")
            put("environment", "SANDBOX")
            put("owner_user_id", "should-not-appear-in-public")
        }
        val public = json.toM27PublicWebhook()
        assertEquals("Eventos", public.label)
        assertEquals(M27Environment.SANDBOX, public.environment)
        assertTrue(public.toString().contains("whsec_1234"))
    }

    @Test fun mapsWebhookEndpointWithTimestamps() {
        val json = buildJsonObject {
            put("id", "uuid-1")
            put("owner_user_id", "user-1")
            put("label", "Hook")
            put("target_url", "https://x.example.com/h")
            put("secret_prefix", "whsec_ab")
            put("status", "DISABLED")
            put("environment", "PRODUCTION")
            put("created_at", "2026-01-01T00:00:00Z")
            put("updated_at", "2026-01-02T00:00:00Z")
        }
        val endpoint = json.toM27WebhookEndpoint()
        assertEquals("uuid-1", endpoint.id)
        assertEquals(M27WebhookStatus.DISABLED, endpoint.status)
    }

    @Test fun mapsPublishedContractFlag() {
        val json = buildJsonObject {
            put("title", "LeoVer Public API v1")
            put("version", "V1")
            put("summary", "Contrato estable.")
            put("published_for_display", true)
        }
        assertTrue(json.toM27PublicContract().publishedForDisplay)
    }
}
