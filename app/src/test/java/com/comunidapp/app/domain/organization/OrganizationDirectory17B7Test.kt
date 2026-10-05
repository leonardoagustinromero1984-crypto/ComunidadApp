package com.comunidapp.app.domain.organization

import com.comunidapp.app.data.model.M16OpeningHours
import com.comunidapp.app.data.model.M16OpeningPeriod
import com.comunidapp.app.data.model.M16PublicContactChannel
import com.comunidapp.app.data.model.M16PublicContactChannelType
import com.comunidapp.app.data.model.M16ShelterAvailabilityStatus
import com.comunidapp.app.data.model.M16ShelterNeed
import com.comunidapp.app.data.model.M16ShelterOperationalStatus
import com.comunidapp.app.data.model.M16ShelterSearchFilter
import com.comunidapp.app.data.model.M16ShelterService
import com.comunidapp.app.data.model.M16ShelterVerificationStatus
import com.comunidapp.app.data.repository.M16MemoryStore
import com.comunidapp.app.data.repository.MockM16ShelterRepository
import com.comunidapp.app.domain.capability.CapabilityFacts
import com.comunidapp.app.domain.capability.CapabilityGate
import com.comunidapp.app.domain.capability.CapabilityNavigationGuard
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.user.SessionGeneration
import com.comunidapp.app.navigation.NavRoutes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OrganizationDirectory17B7Test {

    @Test
    fun publicLabelsAreHumanAndTimezoneStaysOffTheCard() {
        assertEquals("Atiende actualmente", OrganizationPresentation.operationalStatus(M16ShelterOperationalStatus.ACTIVE))
        assertEquals("Pausado temporalmente", OrganizationPresentation.operationalStatus(M16ShelterOperationalStatus.PAUSED))
        assertEquals("Hay cupos", OrganizationPresentation.availability(M16ShelterAvailabilityStatus.LIMITED))
        assertEquals("Perros", OrganizationPresentation.species("DOG"))
        assertEquals("Gatos", OrganizationPresentation.species("CAT"))
        assertNull(OrganizationPresentation.species("NOT_A_SPECIES"))
        assertEquals("Adopciones", OrganizationPresentation.service(M16ShelterService.ADOPTIONS))
        assertEquals("Tránsito", OrganizationPresentation.service(M16ShelterService.TEMPORARY_SHELTER))
        assertEquals("Voluntariado", OrganizationPresentation.service(M16ShelterService.VOLUNTEERING))
        assertEquals(
            "Necesita alimento. Balanceado",
            OrganizationPresentation.needLine(M16ShelterNeed("FOOD", "Balanceado"))
        )
        assertEquals(
            "Necesita artículos de higiene",
            OrganizationPresentation.needLine(M16ShelterNeed("HYGIENE", "null"))
        )
        assertEquals(
            "Necesita alimento",
            OrganizationPresentation.needLine(M16ShelterNeed("FOOD", "N/A"))
        )
        val hours = OrganizationPresentation.openingLines(
            M16OpeningHours(
                zoneIdName = "America/Argentina/Buenos_Aires",
                periods = listOf(M16OpeningPeriod(1, openTime = "09:00", closeTime = "18:00"))
            )
        )
        assertEquals(listOf("Lunes: 09:00 – 18:00"), hours)
        assertFalse(hours.joinToString().contains("America/"))
        listOf(
            OrganizationPresentation.operationalStatus(M16ShelterOperationalStatus.ACTIVE),
            OrganizationPresentation.verifiedBadge(M16ShelterVerificationStatus.VERIFIED).orEmpty(),
            OrganizationPresentation.species("DOG").orEmpty(),
            OrganizationPresentation.service(M16ShelterService.ADOPTIONS)
        ).forEach { label ->
            assertFalse(label == label.uppercase() && label.contains("_"))
        }
    }

    @Test
    fun verifiedBadgeIsOnlyForVerifiedOrganizations() {
        assertEquals(
            OrganizationPresentation.VERIFIED_BADGE,
            OrganizationPresentation.verifiedBadge(M16ShelterVerificationStatus.VERIFIED)
        )
        M16ShelterVerificationStatus.entries
            .filter { it != M16ShelterVerificationStatus.VERIFIED }
            .forEach { assertNull(OrganizationPresentation.verifiedBadge(it)) }
        assertFalse(OrganizationPresentation.VERIFIED_BADGE.contains("VERIFIED"))
        assertEquals("Refugio", OrganizationPresentation.organizationType(OrganizationType.SHELTER))
        assertEquals("ONG", OrganizationPresentation.organizationType(OrganizationType.NGO))
        assertEquals(
            OrganizationPresentation.VERIFIED_BADGE,
            OrganizationPresentation.organizationVerifiedBadge(OrganizationVerificationStatus.VERIFIED)
        )
        assertNull(OrganizationPresentation.organizationVerifiedBadge(OrganizationVerificationStatus.NOT_REQUESTED))
        val publicOrg = source("app/src/main/java/com/comunidapp/app/ui/screens/organization/PublicOrganizationScreen.kt")
        assertFalse(publicOrg.contains(".name"))
        assertFalse(publicOrg.contains("timezone"))
    }

    @Test
    fun visitorDoesNotManageAndCreatedByIsNotMembership() {
        val person = CapabilityFacts(OperationalContext.Personal)
        val org = CapabilityFacts(
            context = OperationalContext.Organization("org-1", "Refugio"),
            organizationMembershipAuthorized = true
        )
        assertFalse(OrganizationManageAccess.allows(person, memberOfOrganization = true))
        assertFalse(OrganizationManageAccess.allows(org, memberOfOrganization = false))
        assertTrue(OrganizationManageAccess.allows(org, memberOfOrganization = true))
        assertFalse(CapabilityGate.canManageOrganization(person))
        assertFalse(CapabilityNavigationGuard.allows(NavRoutes.M16_SHELTERS_MANAGE, person))
        assertTrue(CapabilityNavigationGuard.allows(NavRoutes.M16_SHELTERS_MANAGE, org))
        val access = source("app/src/main/java/com/comunidapp/app/domain/organization/OrganizationPresentation.kt")
            .substringAfter("object OrganizationManageAccess")
        assertFalse(access.contains("createdBy"))
    }

    @Test
    fun publicCardOmitsPrivateDataAndRawEnums() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/m16/M16ShelterScreens.kt")
        val card = screen.substringAfter("fun M16PublicShelterCard").substringBefore("fun M16ShelterDetailScreen")
        val detail = screen.substringAfter("fun M16PublicShelterDetailContent").substringBefore("fun M16ShelterManageScreen")
        val filters = screen.substringAfter("fun M16ListFilterRow").substringBefore("fun M16PublicShelterCard")
        listOf(card, detail).forEach { surface ->
            assertFalse(surface.contains(".name"))
            assertFalse(surface.contains("zoneIdName"))
            assertFalse(surface.contains("internalNotes"))
            assertFalse(surface.contains("organizationId"))
            assertFalse(surface.contains("latitude"))
            assertFalse(surface.contains("N/A"))
        }
        assertTrue(screen.contains("OrganizationPublicSearch.PLACEHOLDER"))
        assertTrue(screen.contains("Buscá por nombre"))
        assertFalse(filters.contains("Actividad"))
        assertFalse(filters.contains("Adopciones"))
        assertTrue(filters.contains("Perros"))
        assertTrue(filters.contains("Solo organizaciones verificadas"))
        assertFalse(filters.contains("Estado operativo"))
        assertFalse(filters.contains("UNVERIFIED_OR_PENDING"))
        assertFalse(filters.contains("PERMANENTLY_CLOSED"))
        assertTrue(detail.contains("Qué hacemos"))
        assertTrue(detail.contains("Todavía no hay mascotas publicadas en adopción."))
        assertTrue(detail.contains("Administrar"))
        val contact = OrganizationPresentation.contactLine(
            M16PublicContactChannel(M16PublicContactChannelType.INSTITUTIONAL_EMAIL, "null")
        )
        assertNull(contact)
    }

    @Test
    fun searchMatchesPublicTextAndOpeningTheDirectoryDoesNotMakeAPersonManager() = runBlocking {
        val store = M16MemoryStore()
        val repo = MockM16ShelterRepository(actorUserId = { "person-1" }, store = store)
        assertFalse(store.organizationManagers.value.values.any { "person-1" in it })
        assertEquals(
            SessionGeneration.NEUTRAL_MOCK_ACTOR,
            store.organizationManagers.value.values.first().single()
        )
        val byEmail = repo.searchPublic(
            M16ShelterSearchFilter(query = "contacto@refugio-demo.local")
        ).getOrThrow()
        assertTrue(byEmail.isEmpty())
        val byName = repo.searchPublic(M16ShelterSearchFilter(query = "Norte")).getOrThrow()
        assertTrue(byName.any { it.displayName.contains("Norte") })
        val byZone = repo.searchPublic(M16ShelterSearchFilter(zoneQuery = "CABA")).getOrThrow()
        assertTrue(byZone.isNotEmpty())
        val descriptionOnly = repo.searchPublic(M16ShelterSearchFilter(query = "descripcion-interna-que-no-es-nombre")).getOrThrow()
        assertTrue(descriptionOnly.isEmpty() || descriptionOnly.all { it.displayName.contains("descripcion", true) })
        val search = source("app/src/main/java/com/comunidapp/app/data/repository/M16ShelterRepositories.kt")
            .substringAfter("private fun applyPublicSearchFilters")
            .substringBefore("class MockM16ShelterRepository")
        assertFalse(search.contains("publicContacts"))
        assertFalse(search.contains("internalNotes"))
        store.clearSessionResidue()
        assertFalse(store.organizationManagers.value.values.any { "person-1" in it })
    }

    private fun source(path: String): String {
        val file = listOf(
            File(path),
            File(System.getProperty("user.dir"), path),
            File(System.getProperty("user.dir"), "../$path")
        ).firstOrNull { it.isFile } ?: error("SOURCE_NOT_FOUND:$path")
        return file.readText()
    }
}
