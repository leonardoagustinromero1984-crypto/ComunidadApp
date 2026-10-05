package com.comunidapp.app.domain.adoption

import com.comunidapp.app.data.local.AdoptionApplicantProfileStore
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.model.AdoptionApplicationStatus
import com.comunidapp.app.data.model.AdoptionRequestStatus
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.domain.capability.CapabilityFacts
import com.comunidapp.app.domain.capability.CapabilityGate
import com.comunidapp.app.domain.capability.PersonCapabilityCode
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdoptionMatching17B6Test {

    @Test
    fun personCannotPublishAndAuthorizedContextsCan() {
        val person = CapabilityFacts(OperationalContext.Personal)
        assertFalse(CapabilityGate.canPublishAdoption(person))
        assertFalse(CapabilityGate.adoptionSurface(person).showPublish)
        val rescuer = CapabilityFacts(
            context = OperationalContext.Rescuer("rescuer-1"),
            activeCapabilities = setOf(PersonCapabilityCode.RESCUER),
            selectedFunctions = setOf(LeoverFunction.RESCUER)
        )
        assertTrue(CapabilityGate.canPublishAdoption(rescuer))
        val org = CapabilityFacts(
            context = OperationalContext.Organization("org-1", "Refugio"),
            organizationMembershipAuthorized = true
        )
        assertTrue(CapabilityGate.canPublishAdoption(org))
        val rescuerLensPersonal = CapabilityFacts(
            context = OperationalContext.Personal,
            activeCapabilities = setOf(PersonCapabilityCode.RESCUER)
        )
        assertFalse(CapabilityGate.canPublishAdoption(rescuerLensPersonal))
    }

    @Test
    fun visibilityIsNotPublishRightAndFoundCaseStaysIneligible() {
        val owner = pet(ownerId = "person-1")
        assertFalse(
            AdoptionPublishEligibility.evaluate(owner, capabilityAllowsPublish = false, viewerUserId = "person-1").eligible
        )
        val shared = pet(accessSubjectUserId = "viewer")
        assertFalse(
            AdoptionPublishEligibility.evaluate(shared, capabilityAllowsPublish = true, viewerUserId = "viewer").eligible
        )
        assertEquals(
            AdoptionPublishBlock.NOT_RESPONSIBLE,
            AdoptionPublishEligibility.evaluate(shared, true, "viewer").block
        )
        val found = pet(ownerId = "person-1", originKind = "FOUND_CASE")
        val foundDecision = AdoptionPublishEligibility.evaluate(found, true, "person-1")
        assertFalse(foundDecision.eligible)
        assertEquals(AdoptionPublishBlock.FOUND_CASE, foundDecision.block)
        assertFalse(AdoptionPublishEligibility.FOUND_NOT_ELIGIBLE.isBlank())
        val responsible = AdoptionPublishEligibility.evaluate(owner, true, "person-1")
        assertTrue(responsible.eligible)
        val orgPet = pet(organizationResponsibleId = "org-1", managementContextKind = "ORGANIZATION", managementContextId = "org-1")
        assertTrue(AdoptionPublishEligibility.evaluate(orgPet, true, "staff", "org-1").eligible)
        assertFalse(AdoptionPublishEligibility.evaluate(orgPet, true, "staff", "other-org").eligible)
    }

    @Test
    fun partialProfileSavesAsIncompleteAndNullIsNotFalse() {
        val empty = AdopterProfile()
        assertFalse(empty.isComplete)
        assertTrue(empty.isStructurallyEmpty)
        assertEquals("Perfil de adopción guardado.", AdopterProfileCompleteness.saveFeedback(empty))
        assertFalse(AdopterProfileCompleteness.saveFeedback(empty).contains("Perfil completo guardado"))
        val partial = AdopterProfile(householdAgrees = true, hasDogs = null, landlordAllowsPets = null)
        assertFalse(partial.isComplete)
        assertNull(partial.hasDogs)
        assertNull(partial.landlordAllowsPets)
        val decoded = AdoptionGeneralProfileCodec.decodeProfile(
            """{"animals_allowed":null,"children_count":null,"housing_type":"casa con patio","experience":"Tuve perros"}"""
        )
        assertNull(decoded.landlordAllowsPets)
        assertNull(decoded.childrenCount)
        assertNull(decoded.housingKind)
        assertEquals("casa con patio", decoded.housingNotes)
        assertEquals("Tuve perros", decoded.legacyExperience)
        val body = AdoptionGeneralProfileCodec.upsertProfile("{}", partial)
        assertTrue(body["p_has_dogs"] is JsonNull)
        assertTrue(body["p_animals_allowed"] is JsonNull)
        assertEquals("true", body["p_household_agrees"]?.toString())
        val complete = filledProfile()
        assertTrue(complete.isComplete)
        assertEquals("Perfil de adopción guardado.", AdopterProfileCompleteness.saveFeedback(complete))
        val notesOnly = AdopterProfile(notes = "Nada más")
        assertFalse(notesOnly.isComplete)
    }

    @Test
    fun privacyModelDoesNotRequireSensitiveData() {
        val names = AdopterProfile::class.java.declaredFields.map { it.name.lowercase() }
        listOf("dni", "salary", "salario", "marital", "medical", "allerg").forEach { token ->
            assertFalse(names.any { it.contains(token) })
        }
        assertFalse(AdoptionPrivacy.ALLERGIES_ASKED)
        assertFalse(AdoptionPrivacy.SHOWN_ON_PUBLIC_LISTING)
        assertTrue(AdoptionPrivacy.notCollectedByDefault.contains("DNI"))
        val card = source("app/src/main/java/com/comunidapp/app/ui/components/PetCard.kt")
        val adoptionCard = card.substringAfter("fun AdoptionCard")
        assertFalse(adoptionCard.contains("allergies"))
        assertFalse(adoptionCard.contains("dni", ignoreCase = true))
        val preserved = AdoptionGeneralProfileCodec.upsertProfile(
            """{"allergies":"polen","primary_caretaker":"Ana"}""",
            AdopterProfile()
        )
        assertEquals("polen", preserved["p_allergies"]?.toString()?.trim('"'))
    }

    @Test
    fun requirementsKeepAdditionalNotesAndHistoricalText() {
        val requirements = AdoptionRequirements(
            needsOutdoorSpace = true,
            acceptsChildren = false,
            additionalNotes = "Visita previa"
        )
        assertTrue(requirements.hasStructuredRequirement)
        assertEquals("Visita previa", requirements.additionalNotes)
        val historical = AdoptionRequirements(additionalNotes = "Requisitos de adopción: patio y tiempo.")
        assertFalse(historical.hasStructuredRequirement)
        assertTrue(historical.additionalNotes.orEmpty().isNotBlank())
        val sql = source("infra/supabase-canonical/supabase/migrations/20261003180000_1108_adoption_match_traits.sql")
        assertTrue(sql.contains("needs_outdoor_space"))
        assertTrue(sql.contains("note"))
        assertFalse(sql.contains("function public.canon_apply_adoption"))
    }

    @Test
    fun explicitConflictIsIncompatibleAndMissingDataIsUnknown() {
        val needsYard = AdoptionRequirements(needsOutdoorSpace = true)
        val conflict = AdoptionMatchingPolicy.evaluate(AdopterProfile(hasOutdoorSpace = false), needsYard)
        assertEquals(AdoptionMatchOutcome.INCOMPATIBLE, conflict.outcome)
        assertFalse(conflict.autoAssigned)
        val unknown = AdoptionMatchingPolicy.evaluate(AdopterProfile(hasOutdoorSpace = null), needsYard)
        assertEquals(AdoptionMatchOutcome.COMPATIBLE, unknown.outcome)
        assertTrue(unknown.unknowns.isNotEmpty())
        assertTrue(unknown.incompatibilities.isEmpty())
        val match = AdoptionMatchingPolicy.evaluate(AdopterProfile(hasOutdoorSpace = true), needsYard)
        assertEquals(AdoptionMatchOutcome.COMPATIBLE, match.outcome)
        assertEquals(1, match.rank)
        assertTrue(match.matches.isNotEmpty())
        val children = AdoptionRequirements(acceptsChildren = false)
        assertEquals(
            AdoptionMatchOutcome.INCOMPATIBLE,
            AdoptionMatchingPolicy.evaluate(AdopterProfile(childrenCount = 2), children).outcome
        )
        assertEquals(
            AdoptionMatchOutcome.COMPATIBLE,
            AdoptionMatchingPolicy.evaluate(AdopterProfile(childrenCount = null), children).outcome
        )
        assertFalse(AdoptionMatchingPolicy.evaluate(AdopterProfile(), AdoptionRequirements()).autoAssigned)
        val summary = AdoptionMatchPresentation.summary(conflict)
        assertFalse(summary.contains("INCOMPATIBLE"))
        assertTrue(summary.contains("no coincide"))
    }

    @Test
    fun applyBlocksOnlyRealConflictsAndSendsMissingAgreementToTheProfile() {
        val open = baseApply(profile = minimumProfile())
        assertTrue(open.allowed)
        assertFalse(baseApply(authenticated = false).allowed)
        assertEquals(AdoptionApplyBlock.OWN_PUBLICATION, baseApply(own = true).block)
        assertEquals(AdoptionApplyBlock.NOT_ACCEPTING, baseApply(accepting = false).block)
        assertEquals(AdoptionApplyBlock.DUPLICATE_ACTIVE, baseApply(duplicate = true).block)
        val missing = baseApply(profile = AdopterProfile())
        assertFalse(missing.allowed)
        assertTrue(missing.openProfile)
        assertFalse(missing.message.orEmpty().contains("EXCEPTION"))
        assertTrue(missing.message.orEmpty().contains("acuerdo"))
        val agreementOnly = baseApply(profile = AdopterProfile(householdAgrees = true))
        assertFalse(agreementOnly.allowed)
        assertTrue(agreementOnly.openProfile)
        val optionalGaps = baseApply(
            profile = minimumProfile().copy(notes = null, experienceBand = null, canVetFollowup = null)
        )
        assertTrue(optionalGaps.allowed)
        val refused = baseApply(profile = AdopterProfile(householdAgrees = false))
        assertEquals(AdoptionApplyBlock.HOUSEHOLD_DOES_NOT_AGREE, refused.block)
        assertFalse(refused.openProfile)
    }

    @Test
    fun applicationStatusesAreHumanAndCoverDoesNotHostFilters() {
        AdoptionApplicationStatus.entries.forEach { status ->
            assertNotEquals(status.name, status.displayNameEs)
            assertFalse(status.displayNameEs.contains("_"))
        }
        assertEquals("Enviada", AdoptionApplicationStatus.SUBMITTED.displayNameEs)
        assertEquals("En revisión", AdoptionApplicationStatus.UNDER_REVIEW.displayNameEs)
        assertEquals("Aceptada", AdoptionApplicationStatus.ACCEPTED.displayNameEs)
        assertEquals("Rechazada", AdoptionApplicationStatus.REJECTED.displayNameEs)
        assertEquals("Retirada", AdoptionApplicationStatus.WITHDRAWN.displayNameEs)
        AdoptionRequestStatus.entries.forEach { status ->
            assertNotEquals(status.name, status.displayNameEs)
        }
        val mine = source("app/src/main/java/com/comunidapp/app/ui/screens/adoptions/AdoptionApplicationScreens.kt")
        assertTrue(mine.contains("displayNameEs"))
        assertTrue(mine.contains("Todavía no enviaste postulaciones."))
        assertFalse(mine.contains("status.name"))
        val cover = source("app/src/main/java/com/comunidapp/app/ui/screens/adoptions/AdoptionsScreen.kt")
        val search = cover.substringAfter("fun AdoptionSearchScreen")
        val home = cover.substringBefore("fun AdoptionSearchScreen")
        assertTrue(home.contains("Buscar mascota para adoptar"))
        assertTrue(home.contains("Mi perfil de adopción"))
        assertTrue(home.contains("Mis postulaciones"))
        assertTrue(home.contains("showPublishAdoption"))
        assertFalse(home.contains("V2LocationStringPicker("))
        assertTrue(search.contains("V2LocationStringPicker"))
        val nav = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(nav.contains("showPublishAdoption = CapabilityGate.adoptionSurface"))
    }

    @Test
    fun sessionClearDropsProfileAndMockPublishersStayNeutral() {
        AdoptionApplicantProfileStore.saveStructured("person-a", AdopterProfile(householdAgrees = true))
        assertEquals(true, AdoptionApplicantProfileStore.structured("person-a")?.householdAgrees)
        AdoptionApplicantProfileStore.clear()
        assertNull(AdoptionApplicantProfileStore.structured("person-a"))
        val cleanup = source("app/src/main/java/com/comunidapp/app/domain/user/AccountIdentityCleanup.kt")
        val invalidate = cleanup.indexOf("SessionGeneration.invalidate()")
        val cleared = cleanup.indexOf("AdoptionApplicantProfileStore.clear()")
        assertTrue(invalidate >= 0 && cleared > invalidate)
        assertTrue(MockData.adoptionPosts.isNotEmpty())
        MockData.adoptionPosts.forEach { post ->
            assertTrue(post.publisherId.isNullOrBlank())
            assertTrue(post.shelterId.orEmpty().startsWith("shelter_"))
        }
    }

    @Test
    fun migrationKeepsFoundCaseOutAndDoesNotCollectSensitiveColumns() {
        val sql = source("infra/supabase-canonical/supabase/migrations/20261003180000_1108_adoption_match_traits.sql")
        assertTrue(sql.contains("FOUND_CASE_NOT_ADOPTABLE"))
        assertTrue(sql.contains("household_agrees"))
        assertTrue(sql.contains("ADOPTION_PUBLISH_FORBIDDEN"))
        assertTrue(sql.contains("DNI, salary and medical data are not added"))
        assertFalse(sql.contains("add column if not exists dni"))
        assertFalse(sql.contains("drop table public.adoption_general_profiles"))
        assertTrue(sql.contains("grant execute on function public.canon_list_adoptions()"))
    }

    private fun baseApply(
        authenticated: Boolean = true,
        own: Boolean = false,
        accepting: Boolean = true,
        duplicate: Boolean = false,
        profile: AdopterProfile = minimumProfile()
    ) = AdoptionApplyPolicy.evaluate(authenticated, own, accepting, duplicate, profile)

    private fun minimumProfile() = AdopterProfile(
        householdAgrees = true,
        housingKind = HousingKind.APARTMENT,
        hasDogs = false,
        hasCats = false,
        hasOtherAnimals = false,
        hoursAlone = HoursAloneEstimate.UNDER_4
    )

    private fun filledProfile() = AdopterProfile(
        housingKind = HousingKind.HOUSE,
        housingTenure = HousingTenure.RENT,
        landlordAllowsPets = true,
        hasOutdoorSpace = true,
        hasSecureEnclosure = true,
        escapeProtection = true,
        adultsCount = 2,
        childrenCount = 0,
        householdAgrees = true,
        hasDogs = false,
        hasCats = false,
        hasOtherAnimals = false,
        experienceBand = ExperienceBand.SOME,
        hoursAlone = HoursAloneEstimate.UNDER_4,
        canVetFollowup = true,
        canMedicate = true,
        acceptsSpecialNeeds = false,
        speciesPref = AdopterSpeciesPref.DOG,
        sizePref = AdopterSizePref.MEDIUM,
        lifeStagePref = AdopterLifeStagePref.ADULT
    )

    private fun pet(
        ownerId: String? = null,
        originKind: String = "STANDARD",
        accessSubjectUserId: String? = null,
        organizationResponsibleId: String? = null,
        managementContextKind: String? = null,
        managementContextId: String? = null
    ) = Pet(
        id = "pet-1",
        ownerId = ownerId,
        name = "Luna",
        species = PetSpecies.DOG,
        sex = PetSex.FEMALE,
        ageYears = 2,
        size = PetSize.MEDIUM,
        description = "",
        status = "ACTIVE",
        originKind = originKind,
        accessSubjectUserId = accessSubjectUserId,
        organizationResponsibleId = organizationResponsibleId,
        managementContextKind = managementContextKind,
        managementContextId = managementContextId
    )

    private fun source(path: String): String {
        val candidates = listOf(
            File(path),
            File("../$path"),
            File("../../$path"),
            File(System.getProperty("user.dir"), path),
            File(System.getProperty("user.dir"), "../$path")
        )
        return (candidates.firstOrNull { it.isFile } ?: error("SOURCE_NOT_FOUND:$path")).readText()
    }
}
