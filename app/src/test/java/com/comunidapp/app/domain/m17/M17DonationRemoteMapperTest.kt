package com.comunidapp.app.domain.m17

import com.comunidapp.app.data.model.M17CampaignStatus
import com.comunidapp.app.data.model.M17ContributionStatus
import com.comunidapp.app.data.model.M17DonorVisibility
import com.comunidapp.app.data.model.M17FinancialCalculator
import com.comunidapp.app.data.model.M17CampaignGoal
import com.comunidapp.app.data.model.M17Contribution
import com.comunidapp.app.data.model.M17PrivacySanitizer
import com.comunidapp.app.data.remote.supabase.m17.toM17PublicCampaign
import com.comunidapp.app.data.remote.supabase.m17.toM17PublicContribution
import com.comunidapp.app.data.repository.M17DonationValidators
import com.comunidapp.app.data.repository.MockM17DonationRepository
import com.comunidapp.app.data.repository.SupabaseM17DonationRepository
import com.comunidapp.app.data.model.RegisterM17MockContributionInput
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M17DonationRemoteMapperTest {

    @Test
    fun publicCampaignMapperOmitsInternalFields() {
        val json = buildJsonObject {
            put("id", "camp-1")
            put("title", "Ayuda")
            put("description", "Descripción pública")
            put("organization_display_name", "Refugio Norte")
            put("campaign_type", "MEDICAL")
            put("status", "PUBLISHED")
            put("goal_amount_minor", 10000)
            put("currency", "ARS")
            put("confirmed_amount_minor", 5000)
            put("progress_percent", 50)
            put("confirmed_contribution_count", 2)
            put("starts_at", "2026-01-01T00:00:00Z")
        }
        val public = json.toM17PublicCampaign()
        assertEquals("camp-1", public.id)
        assertFalse(public.title.contains("@"))
    }

    @Test
    fun publicContributionMapperAnonymizes() {
        val json = buildJsonObject {
            put("id", "c1")
            put("amount_minor", 1000)
            put("currency", "ARS")
            put("donor_label", "Donante anónimo")
            put("created_at", "2026-01-01T00:00:00Z")
        }
        val contrib = json.toM17PublicContribution()
        assertTrue(contrib?.donorLabel?.contains("anónimo", ignoreCase = true) == true)
    }

    @Test
    fun privateContributionHiddenFromSanitizer() {
        val hidden = M17PrivacySanitizer.toPublicContribution(
            M17Contribution(
                id = "x",
                campaignId = "c",
                amountMinor = 100,
                currency = "ARS",
                status = M17ContributionStatus.CONFIRMED,
                visibility = M17DonorVisibility.PRIVATE,
                createdAt = 0
            )
        )
        assertNull(hidden)
    }

    @Test
    fun onlyConfirmedSumsInCalculator() {
        val goal = M17CampaignGoal(1000, "ARS")
        val summary = M17FinancialCalculator.summarize(
            goal,
            listOf(
                contrib(500, M17ContributionStatus.CONFIRMED),
                contrib(200, M17ContributionStatus.PENDING),
                contrib(100, M17ContributionStatus.REFUNDED)
            )
        )
        assertEquals(500, summary.confirmedAmountMinor)
    }

    @Test
    fun draftNotPublicStatus() {
        assertFalse(M17CampaignStatus.DRAFT.isPublic)
    }

    @Test
    fun terminalCannotReopen() {
        assertEquals(
            "M17_STATE_ALREADY_FINAL",
            M17DonationValidators.validateStateTransition(
                M17CampaignStatus.COMPLETED,
                M17CampaignStatus.PUBLISHED
            )
        )
    }

    @Test
    fun remoteMockContributionUnavailable() {
        val repo = SupabaseM17DonationRepository(actorUserId = { "u1" })
        val result = kotlinx.coroutines.runBlocking {
            repo.registerMockContribution(
                RegisterM17MockContributionInput(
                    campaignId = "x",
                    amountMinor = 100,
                    currency = "ARS"
                )
            )
        }
        assertTrue(result.isFailure)
        val code = result.exceptionOrNull()?.let {
            com.comunidapp.app.data.remote.supabase.m17.M17DonationErrorMapper.codeOf(it)
        }
        assertEquals("M17_PAYMENT_INFRASTRUCTURE_UNAVAILABLE", code)
    }

    @Test
    fun mockRepositoryStillOperative() {
        val repo = MockM17DonationRepository(actorUserId = { "mock_user_admin" })
        val result = kotlinx.coroutines.runBlocking { repo.searchPublicCampaigns(com.comunidapp.app.data.model.M17CampaignSearchFilter()) }
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull().orEmpty().isNotEmpty())
    }

    private fun contrib(amount: Long, status: M17ContributionStatus) = M17Contribution(
        id = "c",
        campaignId = "camp",
        amountMinor = amount,
        currency = "ARS",
        status = status,
        visibility = M17DonorVisibility.PUBLIC,
        createdAt = 0
    )
}
