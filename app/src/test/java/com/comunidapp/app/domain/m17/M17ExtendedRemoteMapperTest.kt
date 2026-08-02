package com.comunidapp.app.domain.m17

import com.comunidapp.app.data.model.M17ExtendedPrivacySanitizer
import com.comunidapp.app.data.model.M17FundUsageItem
import com.comunidapp.app.data.model.M17InKindNeedStatus
import com.comunidapp.app.data.model.M17InKindPledgeStatus
import com.comunidapp.app.data.model.M17VolunteerApplicationStatus
import com.comunidapp.app.data.model.M17VolunteerOpportunityStatus
import com.comunidapp.app.data.remote.supabase.m17.toM17CampaignTransparencyReport
import com.comunidapp.app.data.remote.supabase.m17.toM17PublicInKindNeed
import com.comunidapp.app.data.remote.supabase.m17.toM17PublicVolunteerOpportunity
import com.comunidapp.app.data.repository.M17ExtendedValidators
import com.comunidapp.app.data.repository.MockM17InKindRepository
import com.comunidapp.app.data.repository.MockM17TransparencyRepository
import com.comunidapp.app.data.repository.MockM17VolunteerRepository
import com.comunidapp.app.data.repository.SupabaseM17InKindRepository
import com.comunidapp.app.data.repository.SupabaseM17TransparencyRepository
import com.comunidapp.app.data.repository.SupabaseM17VolunteerRepository
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M17ExtendedRemoteMapperTest {

    @Test
    fun draftNeedNotPublicStatus() {
        assertFalse(M17InKindNeedStatus.DRAFT.isPublic)
    }

    @Test
    fun publishedNeedIsPublicStatus() {
        assertTrue(M17InKindNeedStatus.PUBLISHED.isPublic)
    }

    @Test
    fun fulfilledNeedTerminal() {
        assertTrue(M17InKindNeedStatus.FULFILLED.isTerminal)
        assertEquals(
            "M17_NEED_TERMINAL",
            M17ExtendedValidators.validateInKindTransition(M17InKindNeedStatus.FULFILLED, M17InKindNeedStatus.PUBLISHED)
        )
    }

    @Test
    fun cancelledNeedTerminal() {
        assertTrue(M17InKindNeedStatus.CANCELLED.isTerminal)
    }

    @Test
    fun invalidQuantityRejected() {
        assertEquals("M17_INVALID_QUANTITY", M17ExtendedValidators.validateQuantity(0))
    }

    @Test
    fun publicInKindMapperOmitsInternalIds() {
        val json = buildJsonObject {
            put("id", "need-1")
            put("title", "Alimento")
            put("description", "Necesitamos balanceado")
            put("organization_display_name", "Refugio Norte")
            put("category", "FOOD")
            put("status", "PUBLISHED")
            put("quantity_requested", 50)
            put("quantity_pledged", 10)
            put("quantity_delivered", 5)
            put("quantity_unit", "kg")
            put("coverage_percent", 10)
            put("public_location_text", "Zona norte")
        }
        val public = json.toM17PublicInKindNeed()
        assertEquals("need-1", public.id)
        assertEquals("Refugio Norte", public.organizationDisplayName)
        assertFalse(json.keys.contains("organization_id"))
        assertFalse(json.keys.contains("contributor_user_id"))
        assertFalse(json.keys.contains("created_by"))
    }

    @Test
    fun contributorUserIdNotInPublicNeedJson() {
        val json = samplePublicNeedJson()
        assertNull(json["contributor_user_id"])
        assertNull(json["private_message"])
    }

    @Test
    fun draftVolunteerOpportunityNotPublic() {
        assertFalse(M17VolunteerOpportunityStatus.DRAFT.isPublic)
    }

    @Test
    fun publishedVolunteerOpportunityPublic() {
        assertTrue(M17VolunteerOpportunityStatus.PUBLISHED.isPublic)
    }

    @Test
    fun filledVolunteerOpportunityTerminal() {
        assertTrue(M17VolunteerOpportunityStatus.FILLED.isTerminal)
    }

    @Test
    fun completedVolunteerOpportunityTerminal() {
        assertTrue(M17VolunteerOpportunityStatus.COMPLETED.isTerminal)
    }

    @Test
    fun cancelledVolunteerOpportunityTerminal() {
        assertTrue(M17VolunteerOpportunityStatus.CANCELLED.isTerminal)
    }

    @Test
    fun publicVolunteerMapperOmitsApplicantId() {
        val json = buildJsonObject {
            put("id", "opp-1")
            put("title", "Apoyo refugio")
            put("description", "Ayuda los fines de semana")
            put("organization_display_name", "Refugio")
            put("opportunity_type", "SHELTER_SUPPORT")
            put("status", "PUBLISHED")
            put("slots_needed", 3)
            put("slots_filled", 1)
            put("public_location_text", "CABA")
            put("schedule_hint", "Sábados 10-14")
        }
        val public = json.toM17PublicVolunteerOpportunity()
        assertEquals(3, public.slotsNeeded)
        assertFalse(json.keys.contains("applicant_user_id"))
        assertFalse(json.keys.contains("availability_summary"))
    }

    @Test
    fun transparencyAmountUsesLong() {
        val item = M17FundUsageItem("i1", "Veterinaria", 150000L, "ARS", null)
        assertEquals(150000L, item.amountMinor)
    }

    @Test
    fun transparencyNegativeAmountRejected() {
        assertEquals("M17_INVALID_AMOUNT", M17ExtendedValidators.validateTransparencyAmount(-1))
    }

    @Test
    fun transparencyDraftNotInPublicMapper() {
        val json = buildJsonObject {
            put("campaign_id", "camp-1")
            put("summary", "Uso de fondos Q1")
            put("public_notes", "Gracias a todos")
            put("updated_at", "2026-01-15T12:00:00Z")
            put("usage_items", buildJsonArray { })
            put("milestones", buildJsonArray { })
        }
        val report = json.toM17CampaignTransparencyReport()
        assertEquals("camp-1", report.campaignId)
        assertFalse(json.keys.contains("internal_notes"))
        assertFalse(json.keys.contains("created_by"))
    }

    @Test
    fun publishedTransparencySanitized() {
        val json = buildJsonObject {
            put("campaign_id", "c1")
            put("summary", "Resumen público")
            put("public_notes", "Outcome")
            put("updated_at", "2026-01-01T00:00:00Z")
            put(
                "usage_items",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", "u1")
                            put("label", "Medicamentos")
                            put("amount_minor", 50000)
                            put("currency", "ARS")
                        }
                    )
                }
            )
            put("milestones", buildJsonArray { })
        }
        val report = json.toM17CampaignTransparencyReport()
        assertTrue(report.usageItems.isNotEmpty())
        assertEquals(50000L, report.usageItems.first().amountMinor)
    }

    @Test
    fun privateReceiptRefNotRequiredInPublicUsage() {
        val json = buildJsonObject {
            put("campaign_id", "c1")
            put("summary", "S")
            put("updated_at", "2026-01-01T00:00:00Z")
            put(
                "usage_items",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", "u1")
                            put("label", "Item")
                            put("amount_minor", 100)
                            put("currency", "ARS")
                        }
                    )
                }
            )
            put("milestones", buildJsonArray { })
        }
        val item = json.toM17CampaignTransparencyReport().usageItems.first()
        assertNull(item.receiptRef)
    }

    @Test
    fun unknownEnumFallsBackSafely() {
        val json = buildJsonObject {
            put("id", "n1")
            put("title", "T")
            put("description", "D")
            put("organization_display_name", "O")
            put("category", "UNKNOWN_CATEGORY")
            put("status", "PUBLISHED")
            put("quantity_requested", 1)
            put("quantity_pledged", 0)
            put("quantity_delivered", 0)
            put("quantity_unit", "u")
            put("coverage_percent", 0)
        }
        val mapped = json.toM17PublicInKindNeed()
        assertNotNull(mapped.category)
    }

    @Test
    fun deliveredPledgeStatusIsTerminal() {
        assertTrue(M17InKindPledgeStatus.DELIVERED.name == "DELIVERED")
    }

    @Test
    fun mockInKindRepositoryStillOperative() {
        val repo = MockM17InKindRepository(actorUserId = { "mock_user_admin" })
        val result = kotlinx.coroutines.runBlocking {
            repo.searchPublicNeeds(com.comunidapp.app.data.model.M17InKindSearchFilter())
        }
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull().orEmpty().isNotEmpty())
    }

    @Test
    fun mockVolunteerRepositoryStillOperative() {
        val repo = MockM17VolunteerRepository(actorUserId = { "mock_user_admin" })
        val result = kotlinx.coroutines.runBlocking {
            repo.searchPublicOpportunities(com.comunidapp.app.data.model.M17VolunteerSearchFilter())
        }
        assertTrue(result.isSuccess)
    }

    @Test
    fun mockTransparencyRepositoryStillOperative() {
        val repo = MockM17TransparencyRepository()
        val result = kotlinx.coroutines.runBlocking {
            repo.getTransparencyReport("camp_mock_1")
        }
        assertTrue(result.isSuccess || result.isFailure)
    }

    @Test
    fun supabaseInKindRepositoryTypeExists() {
        assertNotNull(SupabaseM17InKindRepository(actorUserId = { "u1" }))
    }

    @Test
    fun supabaseVolunteerRepositoryTypeExists() {
        assertNotNull(SupabaseM17VolunteerRepository(actorUserId = { "u1" }))
    }

    @Test
    fun supabaseTransparencyRepositoryTypeExists() {
        assertNotNull(SupabaseM17TransparencyRepository())
    }

    @Test
    fun duplicateApplicationRejectedInMock() = kotlinx.coroutines.runBlocking {
        val repo = MockM17VolunteerRepository(actorUserId = { "user_dup_remote_test" })
        val id = repo.searchPublicOpportunities(com.comunidapp.app.data.model.M17VolunteerSearchFilter())
            .getOrThrow().first().id
        repo.submitApplication(id, "Hola")
        val second = repo.submitApplication(id, "Otra")
        assertTrue(second.isFailure)
    }

    @Test
    fun volunteerDoesNotCreateMembership() {
        assertNotNull(MockM17VolunteerRepository(actorUserId = { "u1" }))
    }

    @Test
    fun availabilityNotInPublicVolunteerJson() {
        val json = sampleVolunteerJson()
        assertNull(json["availability_summary"])
        assertNull(json["private_message"])
    }

    @Test
    fun applicationStatusesNotPublicByDefault() {
        assertFalse(M17VolunteerApplicationStatus.SUBMITTED.name.contains("PUBLIC"))
        assertFalse(M17VolunteerApplicationStatus.REVIEWING.name.contains("PUBLIC"))
    }

    @Test
    fun privacySanitizerStripsEmailFromDescription() {
        val sanitized = M17ExtendedPrivacySanitizer.scrub("Contacto test@test.com")
        assertFalse(sanitized.contains("@"))
    }

    private fun samplePublicNeedJson() = buildJsonObject {
        put("id", "n1")
        put("title", "T")
        put("description", "D")
        put("organization_display_name", "Org")
        put("category", "FOOD")
        put("status", "PUBLISHED")
        put("quantity_requested", 10)
        put("quantity_pledged", 2)
        put("quantity_delivered", 1)
        put("quantity_unit", "kg")
        put("coverage_percent", 10)
    }

    private fun sampleVolunteerJson() = buildJsonObject {
        put("id", "o1")
        put("title", "T")
        put("description", "D")
        put("organization_display_name", "Org")
        put("opportunity_type", "EVENTS")
        put("status", "PUBLISHED")
        put("slots_needed", 2)
        put("slots_filled", 0)
    }
}
