package com.comunidapp.app.domain.canonical

import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CanonicalProviderWriteTest {

    @Test
    fun directoryOnlyWriteDoesNotRequireMarketplace() {
        assertTrue(CanonicalProviderWrite.DIRECTORY_ONLY_V1)
        assertFalse(CanonicalProviderWrite.MARKETPLACE_FIELDS_REQUIRED)
        assertEquals(
            "Completá los datos de tu servicio para publicarlo.",
            CanonicalProviderWrite.INCOMPLETE_SETUP_MESSAGE
        )
    }

    @Test
    fun personHolderIsDefaultForDaycareExtra() {
        val holder = CanonicalProviderWrite.resolveHolder(
            userId = "user-1",
            context = OperationalContext.Provider("onb02:DAYCARE:user-1", "Guardería", "DAYCARE"),
            myOrganizationIds = setOf("org-9")
        )
        assertEquals(CanonicalProviderHolder.PERSON, holder.kind)
        assertEquals("user-1", holder.personId)
        assertEquals(null, holder.organizationId)
    }

    @Test
    fun organizationHolderRequiresMembership() {
        val holder = CanonicalProviderWrite.resolveHolder(
            userId = "user-1",
            context = OperationalContext.Organization("org-9", "Mi guardería", "DAYCARE"),
            myOrganizationIds = setOf("org-9")
        )
        assertEquals(CanonicalProviderHolder.ORGANIZATION, holder.kind)
        assertEquals("org-9", holder.organizationId)
        val outsider = CanonicalProviderWrite.resolveHolder(
            userId = "user-1",
            context = OperationalContext.Organization("org-9", "Mi guardería", "DAYCARE"),
            myOrganizationIds = emptySet()
        )
        assertEquals(CanonicalProviderHolder.PERSON, outsider.kind)
    }

    @Test
    fun daycareAndSharedCategoriesMapToCanonicalCodes() {
        assertEquals("BOARDING", CanonicalProviderWrite.storageCategory(ServiceCategory.DAYCARE))
        assertEquals("WALKING", CanonicalProviderWrite.storageCategory(ServiceCategory.WALKER))
        assertEquals("CARE", CanonicalProviderWrite.storageCategory(ServiceCategory.CAREGIVER))
        assertEquals("TRAINING", CanonicalProviderWrite.storageCategory(ServiceCategory.TRAINER))
        assertEquals("GROOMING", CanonicalProviderWrite.storageCategory(ServiceCategory.GROOMING))
        assertEquals("VETERINARY", CanonicalProviderWrite.storageCategory(ServiceCategory.VET))
        assertEquals(ServiceCategory.DAYCARE, CanonicalProviderWrite.fromStorageCategory("BOARDING"))
        assertEquals(ServiceCategory.DAYCARE, CanonicalProviderWrite.categoryFromContext(
            OperationalContext.Provider("p1", "Guardería", "DAYCARE")
        ))
    }

    @Test
    fun extrasPersistProviderContextsIncludingDaycare() {
        val extras = CanonicalProviderWrite.extraProviderContexts(
            "user-1",
            setOf(LeoverFunction.DAYCARE, LeoverFunction.WALKER, LeoverFunction.GROOMING)
        )
        assertTrue(extras.any { it.category == "DAYCARE" && it.displayName == "Guardería" })
        assertTrue(extras.any { it.category == "WALKING" })
        assertTrue(extras.any { it.category == "GROOMING" })
    }

    @Test
    fun repositoryCallsDirectoryUpsertNotHardFail() {
        val repo = File("src/main/java/com/comunidapp/app/data/repository/CanonicalServiceRepository.kt").readText()
        assertTrue(repo.contains("RPC_UPSERT_PROVIDER"))
        assertFalse(repo.contains("CANONICAL_PROVIDER_WRITE_NOT_FROM_DIRECTORY"))
        val vm = File("src/main/java/com/comunidapp/app/viewmodel/ServiceViewModel.kt").readText()
        assertFalse(vm.contains("Este contexto no es un negocio"))
    }
}
