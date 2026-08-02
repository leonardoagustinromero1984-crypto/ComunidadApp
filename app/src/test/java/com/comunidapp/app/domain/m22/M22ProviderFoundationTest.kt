package com.comunidapp.app.domain.m22

import com.comunidapp.app.data.model.CreateM22ProviderInput
import com.comunidapp.app.data.model.M22CoverageArea
import com.comunidapp.app.data.model.M22CoverageType
import com.comunidapp.app.data.model.M22MockProviderIds
import com.comunidapp.app.data.model.M22MockUsers
import com.comunidapp.app.data.model.M22PriceType
import com.comunidapp.app.data.model.M22ProviderCategory
import com.comunidapp.app.data.model.UpsertM22OfferingInput
import com.comunidapp.app.data.repository.M22ProviderMemoryStore
import com.comunidapp.app.data.repository.M22ProviderValidators
import com.comunidapp.app.data.repository.MockM22ProviderRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M22ProviderFoundationTest {
    private fun repository(actor: String = M22MockUsers.PROVIDER, store: M22ProviderMemoryStore = M22ProviderMemoryStore()) =
        MockM22ProviderRepository({ actor }, store)

    @Test fun validProviderAccepted() = assertNull(M22ProviderValidators.validateProvider("Patitas", "Atención cuidada para mascotas.", "CABA"))
    @Test fun blankProviderRejected() = assertEquals("M22_INVALID_PROVIDER", M22ProviderValidators.validateProvider("", "Atención cuidada para mascotas.", "CABA"))
    @Test fun shortDescriptionRejected() = assertEquals("M22_INVALID_PROVIDER", M22ProviderValidators.validateProvider("Patitas", "corta", "CABA"))
    @Test fun unsafeDescriptionRejected() = assertEquals("M22_INVALID_PROVIDER", M22ProviderValidators.validateProvider("Patitas", "<script>alert(1)</script>", "CABA"))
    @Test fun fixedPriceNeedsAmount() = assertEquals("M22_INVALID_PRICE", M22ProviderValidators.validatePrice(M22PriceType.FIXED, null))
    @Test fun fromPriceMustBePositive() = assertEquals("M22_INVALID_PRICE", M22ProviderValidators.validatePrice(M22PriceType.FROM, 0))
    @Test fun quoteCannotHaveAmount() = assertEquals("M22_INVALID_PRICE", M22ProviderValidators.validatePrice(M22PriceType.QUOTE, 10))
    @Test fun quoteWithoutAmountAccepted() = assertNull(M22ProviderValidators.validatePrice(M22PriceType.QUOTE, null))
    @Test fun cityCoverageIsMinimal() = assertNull(M22ProviderValidators.validateCoverage(M22CoverageArea(M22CoverageType.CITY, "CABA")))
    @Test fun radiusCoverageRequiresRadius() = assertEquals("M22_INVALID_BRANCH", M22ProviderValidators.validateCoverage(M22CoverageArea(M22CoverageType.RADIUS, "CABA")))
    @Test fun sanitizerRedactsEmail() = assertFalse(M22PrivacySanitizer.scrubPublicText("Escribí a hola@privado.com").contains("hola@privado.com"))
    @Test fun sanitizerRedactsPhone() = assertFalse(M22PrivacySanitizer.scrubPublicText("Llamá al +54 11 5555 1234").contains("5555"))
    @Test fun catalogOnlyContainsActiveProviders() = runBlocking {
        val items = repository().observeCatalog().first()
        assertTrue(items.none { it.displayName == "Paseos Norte" || it.displayName == "Traslados Mascota" })
    }
    @Test fun publicListingHasNoInternalIdentifiers() = runBlocking {
        val listing = repository().observeCatalog().first().first()
        assertFalse(listing.toString().contains("mock_user"))
        assertFalse(listing.toString().contains("m22_provider"))
    }
    @Test fun publicDetailHasMinimalCoverage() = runBlocking {
        val detail = repository().observeProviderDetail(M22MockProviderIds.ACTIVE_MULTI_BRANCH).first()!!
        assertFalse(detail.branches.joinToString().contains("providerId"))
        assertTrue(detail.branches.any { it.coverage.startsWith("Radio") })
    }
    @Test fun mockSeedsAreDeterministic() = runBlocking {
        assertEquals(repository().observeCatalog().first().map { it.displayName }, repository().observeCatalog().first().map { it.displayName })
    }
    @Test fun categoryFilterWorks() = runBlocking {
        val items = repository().observeCatalog(M22ProviderCategory.GROOMING).first()
        assertEquals(1, items.size)
        assertEquals(M22ProviderCategory.GROOMING, items.single().category)
    }
    @Test fun draftProviderIsNotPublic() = runBlocking {
        assertNull(repository().observeProviderDetail(M22MockProviderIds.DRAFT).first())
    }
    @Test fun archiveIsIdempotent() = runBlocking {
        val repo = repository()
        repo.archiveProvider(M22MockProviderIds.ACTIVE_MULTI_BRANCH).getOrThrow()
        repo.archiveProvider(M22MockProviderIds.ACTIVE_MULTI_BRANCH).getOrThrow()
        assertNull(repo.observeProviderDetail(M22MockProviderIds.ACTIVE_MULTI_BRANCH).first())
    }
    @Test fun unauthorizedArchiveIsRejected() = runBlocking {
        assertTrue(repository(M22MockUsers.UNAUTHORIZED).archiveProvider(M22MockProviderIds.ACTIVE_MULTI_BRANCH).isFailure)
    }
    @Test fun offeringValidationIsApplied() = runBlocking {
        val result = repository().upsertOffering(UpsertM22OfferingInput(M22MockProviderIds.ACTIVE_MULTI_BRANCH, name = "Baño", description = "Servicio de baño completo.", priceType = M22PriceType.FIXED))
        assertTrue(result.isFailure)
    }
    @Test fun createProviderStartsDraft() = runBlocking {
        val result = repository().createProvider(CreateM22ProviderInput("Nuevo servicio", M22ProviderCategory.OTHER, "Descripción válida para el catálogo.", "CABA")).getOrThrow()
        assertEquals("DRAFT", result.status.name)
    }
}
