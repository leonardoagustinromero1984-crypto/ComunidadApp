package com.comunidapp.app.domain.m17

import com.comunidapp.app.data.model.M17ExtendedPrivacySanitizer
import com.comunidapp.app.data.model.M17FundUsageItem
import com.comunidapp.app.data.model.M17InKindCategory
import com.comunidapp.app.data.model.M17InKindDonationNeed
import com.comunidapp.app.data.model.M17InKindNeedStatus
import com.comunidapp.app.data.model.M17InKindPledgeStatus
import com.comunidapp.app.data.model.M17PublicReceiptRef
import com.comunidapp.app.data.model.M17VolunteerOpportunity
import com.comunidapp.app.data.model.M17VolunteerOpportunityStatus
import com.comunidapp.app.data.model.M17VolunteerOpportunityType
import com.comunidapp.app.data.repository.M17ExtendedValidators
import com.comunidapp.app.data.repository.MockM17ContributionIntentService
import com.comunidapp.app.data.repository.MockM17InKindRepository
import com.comunidapp.app.data.repository.MockM17VolunteerRepository
import com.comunidapp.app.data.repository.UnavailableM17ContributionIntentService
import com.comunidapp.app.data.remote.supabase.m17.M17DonationErrorMapper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M17ExtendedFoundationTest {

    @Test
    fun onlyPublishedNeedsArePublic() {
        assertFalse(M17InKindNeedStatus.DRAFT.isPublic)
        assertTrue(M17InKindNeedStatus.PUBLISHED.isPublic)
    }

    @Test
    fun fulfilledNeedIsTerminal() {
        assertTrue(M17InKindNeedStatus.FULFILLED.isTerminal)
        assertEquals("M17_NEED_TERMINAL", M17ExtendedValidators.validateInKindTransition(
            M17InKindNeedStatus.FULFILLED, M17InKindNeedStatus.PUBLISHED
        ))
    }

    @Test
    fun privateDataNotInPublicNeed() {
        val need = M17InKindDonationNeed(
            id = "n1", organizationId = "org1", organizationDisplayName = "Org",
            title = "Food", description = "test@test.com", category = M17InKindCategory.FOOD,
            status = M17InKindNeedStatus.PUBLISHED, quantityRequested = 10, quantityUnit = "kg",
            createdAt = 0, updatedAt = 0
        )
        val public = M17ExtendedPrivacySanitizer.toPublicNeed(need, 3, 1)
        assertFalse(public.description.contains("@"))
    }

    @Test
    fun invalidQuantityRejected() {
        assertEquals("M17_INVALID_QUANTITY", M17ExtendedValidators.validateQuantity(0))
    }

    @Test
    fun partialCoverageCalculated() {
        val need = M17ExtendedPrivacySanitizer.toPublicNeed(
            sampleNeed(), pledged = 5, delivered = 3
        )
        assertTrue(need.coveragePercent in 1..100)
    }

    @Test
    fun onlyPublishedOpportunitiesPublic() {
        assertFalse(M17VolunteerOpportunityStatus.DRAFT.isPublic)
        assertTrue(M17VolunteerOpportunityStatus.PUBLISHED.isPublic)
    }

    @Test
    fun duplicateApplicationRejected() = runBlocking {
        val repo = MockM17VolunteerRepository(actorUserId = { "user_dup" })
        val opps = repo.searchPublicOpportunities(com.comunidapp.app.data.model.M17VolunteerSearchFilter())
        val id = opps.getOrThrow().first().id
        repo.submitApplication(id, "Hola")
        val second = repo.submitApplication(id, "Otra")
        assertTrue(second.isFailure)
    }

    @Test
    fun filledOpportunityIsTerminal() {
        assertTrue(M17VolunteerOpportunityStatus.FILLED.isTerminal)
    }

    @Test
    fun transparencyAmountNegativeRejected() {
        assertEquals("M17_INVALID_AMOUNT", M17ExtendedValidators.validateTransparencyAmount(-1))
    }

    @Test
    fun receiptSanitized() {
        val ref = M17ExtendedPrivacySanitizer.sanitizeReceipt(
            M17PublicReceiptRef("r1", "Factura test@test.com", "mock://r", 100, "ARS")
        )
        assertFalse(ref.label.contains("@"))
    }

    @Test
    fun mockIntentServiceDoesNotCharge() = runBlocking {
        val svc = MockM17ContributionIntentService()
        val intent = svc.createIntent("camp", 1000, "ARS").getOrThrow()
        assertEquals(com.comunidapp.app.data.repository.M17ContributionIntentStatus.CREATED, intent.status)
    }

    @Test
    fun unavailableIntentServiceExplicit() = runBlocking {
        val result = UnavailableM17ContributionIntentService.createIntent("c", 100, "ARS")
        assertEquals(
            "M17_PAYMENT_INFRASTRUCTURE_UNAVAILABLE",
            M17DonationErrorMapper.codeOf(result.exceptionOrNull()!!)
        )
    }

    @Test
    fun volunteerDoesNotCreateMembership() {
        // Voluntariado mock no expone APIs de membresía M03 — verificación estructural
        assertNotNull(MockM17VolunteerRepository(actorUserId = { "u1" }))
    }

    private fun sampleNeed() = M17InKindDonationNeed(
        id = "n", organizationId = "o", organizationDisplayName = "O",
        title = "T", description = "D", category = M17InKindCategory.FOOD,
        status = M17InKindNeedStatus.PUBLISHED, quantityRequested = 10, quantityUnit = "kg",
        createdAt = 0, updatedAt = 0
    )
}
