package com.comunidapp.app.domain.m22

import com.comunidapp.app.data.model.CreateM22ProviderInput
import com.comunidapp.app.data.model.M22CatalogFilter
import com.comunidapp.app.data.model.M22CoverageArea
import com.comunidapp.app.data.model.M22CoverageType
import com.comunidapp.app.data.model.M22MockProviderIds
import com.comunidapp.app.data.model.M22MockUsers
import com.comunidapp.app.data.model.M22PriceType
import com.comunidapp.app.data.model.M22ProviderCategory
import com.comunidapp.app.data.model.M22ProviderProfile
import com.comunidapp.app.data.model.M22ProviderStatus
import com.comunidapp.app.data.model.M22ServiceOffering
import com.comunidapp.app.data.model.UpsertM22BranchInput
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

class M22ProviderOperationsTest {
    private fun repository(
        actor: String = M22MockUsers.PROVIDER,
        store: M22ProviderMemoryStore = M22ProviderMemoryStore()
    ) = MockM22ProviderRepository({ actor }, store)

    @Test fun publishRequiresActiveOffering() = runBlocking {
        val repo = repository()
        val provider = repo.createProvider(validInput()).getOrThrow()
        repo.upsertBranch(validBranch(provider.id)).getOrThrow()
        assertTrue(repo.publishProvider(provider.id).isFailure)
    }

    @Test fun publishRequiresActiveBranch() = runBlocking {
        val repo = repository()
        val provider = repo.createProvider(validInput()).getOrThrow()
        repo.upsertOffering(validOffering(provider.id)).getOrThrow()
        assertTrue(repo.publishProvider(provider.id).isFailure)
    }

    @Test fun draftIsNotPublicBeforePublish() = runBlocking {
        assertNull(repository().observeProviderDetail(M22MockProviderIds.DRAFT).first())
    }

    @Test fun publishMakesDraftPublic() = runBlocking {
        val repo = repository()
        repo.publishProvider(M22MockProviderIds.DRAFT).getOrThrow()
        assertTrue(repo.observeCatalog().first().any { it.displayName == "Paseos Norte" })
    }

    @Test fun suspendHidesCatalogEntry() = runBlocking {
        val repo = repository()
        repo.suspendProvider(M22MockProviderIds.ACTIVE_MULTI_BRANCH).getOrThrow()
        assertFalse(repo.observeCatalog().first().any { it.displayName == "Patitas Centro" })
    }

    @Test fun suspendedProviderHasNoPublicDetail() = runBlocking {
        val repo = repository()
        repo.suspendProvider(M22MockProviderIds.ACTIVE_MULTI_BRANCH).getOrThrow()
        assertNull(repo.observeProviderDetail(M22MockProviderIds.ACTIVE_MULTI_BRANCH).first())
    }

    @Test fun reactivateShowsCatalogEntry() = runBlocking {
        val repo = repository()
        repo.suspendProvider(M22MockProviderIds.ACTIVE_MULTI_BRANCH).getOrThrow()
        repo.reactivateProvider(M22MockProviderIds.ACTIVE_MULTI_BRANCH).getOrThrow()
        assertTrue(repo.observeCatalog().first().any { it.displayName == "Patitas Centro" })
    }

    @Test fun archiveIsTerminal() = runBlocking {
        val repo = repository()
        repo.archiveProvider(M22MockProviderIds.DRAFT).getOrThrow()
        assertTrue(repo.publishProvider(M22MockProviderIds.DRAFT).isFailure)
    }

    @Test fun archiveIsIdempotentForDraft() = runBlocking {
        val repo = repository()
        assertTrue(repo.archiveProvider(M22MockProviderIds.DRAFT).isSuccess)
        assertTrue(repo.archiveProvider(M22MockProviderIds.DRAFT).isSuccess)
    }

    @Test fun publishIsIdempotentForActiveProvider() = runBlocking {
        val repo = repository()
        assertEquals(
            M22ProviderStatus.ACTIVE,
            repo.publishProvider(M22MockProviderIds.ACTIVE_MULTI_BRANCH).getOrThrow().status
        )
    }

    @Test fun suspendIsIdempotentForSuspendedProvider() = runBlocking {
        val repo = repository(M22MockUsers.OTHER_PROVIDER)
        assertEquals(
            M22ProviderStatus.SUSPENDED,
            repo.suspendProvider(M22MockProviderIds.SUSPENDED).getOrThrow().status
        )
    }

    @Test fun unauthorizedPublishFails() = runBlocking {
        assertTrue(repository(M22MockUsers.UNAUTHORIZED).publishProvider(M22MockProviderIds.DRAFT).isFailure)
    }

    @Test fun unauthorizedSuspendFails() = runBlocking {
        assertTrue(repository(M22MockUsers.UNAUTHORIZED).suspendProvider(M22MockProviderIds.ACTIVE_MULTI_BRANCH).isFailure)
    }

    @Test fun cityFilterMatchesCaseInsensitively() = runBlocking {
        val items = repository().observeCatalog(M22CatalogFilter(city = "caba")).first()
        assertTrue(items.isNotEmpty())
        assertTrue(items.all { it.city == "CABA" })
    }

    @Test fun categoryAndCityFilterIntersect() = runBlocking {
        val items = repository().observeCatalog(M22CatalogFilter(M22ProviderCategory.GROOMING, "CABA")).first()
        assertEquals(listOf(M22ProviderCategory.GROOMING), items.map { it.category })
    }

    @Test fun inactiveOfferingIsExcludedFromPriceSummary() {
        val listing = M22PrivacySanitizer.toPublicListing(
            sampleProvider(),
            emptyList(),
            listOf(
                M22ServiceOffering("inactive", "provider", name = "Oculto", description = "Oferta inactiva.", priceType = M22PriceType.FIXED, priceAmount = 1, active = false),
                M22ServiceOffering("active", "provider", name = "Visible", description = "Oferta vigente.", priceType = M22PriceType.FIXED, priceAmount = 500, active = true)
            )
        )
        assertEquals("ARS 500", listing.priceSummary)
    }

    @Test fun m06HookStubDoesNotCrash() = runBlocking {
        val state = repository().observeNotificationsHook().first()
        assertFalse(state.available)
        assertFalse(state.providerPublished)
    }

    @Test fun publicOperationsKeepOwnerPrivate() = runBlocking {
        val listing = repository().observeCatalog().first().first()
        assertFalse(listing.toString().contains("mock_user"))
        assertFalse(listing.toString().contains("ownerUserId"))
    }

    @Test fun publicOperationsScrubContactData() {
        val listing = M22PrivacySanitizer.toPublicListing(
            sampleProvider(description = "Escribí a contacto@privado.com para reservar."),
            emptyList(),
            emptyList()
        )
        assertFalse(listing.description.contains("contacto@privado.com"))
    }

    @Test fun suspendedCannotTransitionToDraft() {
        assertEquals(
            "M22_INVALID_STATUS_TRANSITION",
            M22ProviderValidators.validateStatusTransition(M22ProviderStatus.SUSPENDED, M22ProviderStatus.DRAFT)
        )
    }

    @Test fun archivedCannotRepublish() {
        assertEquals(
            "M22_ARCHIVED_PROVIDER",
            M22ProviderValidators.validateStatusTransition(M22ProviderStatus.ARCHIVED, M22ProviderStatus.ACTIVE, true, true)
        )
    }

    @Test fun publishTransitionAcceptsReadyProvider() {
        assertNull(M22ProviderValidators.validateStatusTransition(M22ProviderStatus.DRAFT, M22ProviderStatus.ACTIVE, true, true))
    }

    @Test fun activeCanSuspend() {
        assertNull(M22ProviderValidators.validateStatusTransition(M22ProviderStatus.ACTIVE, M22ProviderStatus.SUSPENDED))
    }

    @Test fun archivedProviderNeverReturnsToCatalog() = runBlocking {
        val repo = repository()
        repo.archiveProvider(M22MockProviderIds.ACTIVE_MULTI_BRANCH).getOrThrow()
        assertFalse(repo.observeCatalog().first().any { it.displayName == "Patitas Centro" })
    }

    private fun validInput() = CreateM22ProviderInput(
        "Nuevo prestador",
        M22ProviderCategory.OTHER,
        "Descripción válida para operaciones del catálogo.",
        "CABA"
    )

    private fun validOffering(providerId: String) = UpsertM22OfferingInput(
        providerId,
        name = "Servicio válido",
        description = "Descripción válida del servicio ofrecido.",
        priceType = M22PriceType.FIXED,
        priceAmount = 1_000
    )

    private fun validBranch(providerId: String) = UpsertM22BranchInput(
        providerId,
        name = "Sede válida",
        city = "CABA",
        coverage = M22CoverageArea(M22CoverageType.CITY, "CABA")
    )

    private fun sampleProvider(description: String = "Prestador de prueba sin datos privados.") =
        M22ProviderProfile(
            "provider", "owner-secret", null, "Prestador", M22ProviderCategory.OTHER,
            description, "CABA", M22ProviderStatus.ACTIVE, 1L, 1L
        )
}
