package com.comunidapp.app.domain.foster

import com.comunidapp.app.domain.capability.CapabilityFacts
import com.comunidapp.app.domain.capability.CapabilityGate
import com.comunidapp.app.domain.capability.PersonCapabilityCode
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.user.ManualSessionEpoch
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FosterTransit17B5Test {

    @Test
    fun foundPetCanSearchTransitAndOwnedPetCannot() {
        assertTrue(FosterTransitVisibility.forOrigin("FOUND_CASE"))
        assertTrue(FosterTransitVisibility.forOrigin("found_case"))
        assertFalse(FosterTransitVisibility.forOrigin("STANDARD"))
        assertFalse(FosterTransitVisibility.forOrigin(null))
        assertFalse(FosterTransitVisibility.forOrigin("Encontrado"))
        val detail = source("app/src/main/java/com/comunidapp/app/ui/screens/pets/PetDetailScreen.kt")
        assertTrue(detail.contains("FosterTransitVisibility.forOrigin(data.originKind)"))
        assertTrue(detail.contains("Buscar hogar de tránsito"))
        assertTrue(detail.contains("refreshFosterTransit()"))
    }

    @Test
    fun requestingTransitDoesNotGrantFoster() {
        val person = CapabilityFacts(context = OperationalContext.Personal)
        assertTrue(CapabilityGate.canRequestFosterForFoundPet(person))
        assertFalse(CapabilityGate.canOfferFosterHome(person))
        assertFalse(CapabilityGate.canBrowseFosterRequests(person))
        val before = emptySet<PersonCapabilityCode>()
        assertEquals(before, CapabilityGate.afterRequestingFosterForFoundPet(before))
        assertFalse(PersonCapabilityCode.FOSTER in CapabilityGate.afterRequestingFosterForFoundPet(before))
    }

    @Test
    fun activeRequestSwitchesThePrimaryAction() {
        assertEquals(
            "Buscar hogar de tránsito",
            FosterTransitVisibility.primaryLabel(hasActiveRequest = false)
        )
        assertEquals(
            "Ver solicitud de tránsito",
            FosterTransitVisibility.primaryLabel(hasActiveRequest = true)
        )
        assertTrue(FosterTransitVisibility.isActiveRequest("REQUESTED"))
        assertTrue(FosterTransitVisibility.isActiveRequest("MATCHED"))
        assertTrue(FosterTransitVisibility.isActiveRequest("ACTIVE"))
        assertFalse(FosterTransitVisibility.isActiveRequest("CANCELLED"))
        assertFalse(FosterTransitVisibility.isActiveRequest("COMPLETED"))
        assertFalse(FosterTransitVisibility.isActiveRequest(null))
    }

    @Test
    fun humanStatusDoesNotLeakCanonicalCodes() {
        listOf(
            FosterRequestPresentation.requestStatus("REQUESTED") to "Solicitud enviada",
            FosterRequestPresentation.requestStatus("REQUESTED", interestedHomes = 2) to "Hay hogares interesados",
            FosterRequestPresentation.requestStatus("MATCHED") to "Buscando hogares",
            FosterRequestPresentation.requestStatus("ACTIVE") to "Hogar seleccionado",
            FosterRequestPresentation.requestStatus("COMPLETED") to "Tránsito finalizado",
            FosterRequestPresentation.requestStatus("CANCELLED") to "Solicitud cancelada",
            FosterRequestPresentation.applicationStatus("PENDING") to "En espera",
            FosterRequestPresentation.applicationStatus("SELECTED") to "Hogar seleccionado",
            FosterRequestPresentation.applicationStatus("WITHDRAWN") to "Postulación retirada",
            FosterRequestPresentation.applicationStatus("NOT_SELECTED") to "No elegido"
        ).forEach { (label, expected) ->
            assertEquals(expected, label)
            assertFalse(FosterRequestPresentation.leaksTechnical(label))
        }
        val screens = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterCareRequestScreens.kt")
        assertFalse(screens.contains("No gana el primero"))
        assertFalse(screens.contains("No se adjudica solo"))
        assertFalse(screens.contains("Reintentar solicitud"))
        assertFalse(screens.contains("Identificador recuperado"))
        assertFalse(screens.contains("Necesidades"))
        assertFalse(screens.contains("label = { Text(\"Notas\") }"))
        assertTrue(screens.contains("Información adicional"))
        assertTrue(screens.contains("Solicitud enviada."))
        assertEquals("Todavía no hay postulantes", FOSTER_APPLICANTS_EMPTY)
        assertEquals("Elegí un hogar de tránsito", FOSTER_CHOOSE_HOME)
        assertEquals("Elegir hogar de tránsito", FOSTER_CHOOSE_ACTION)
        assertTrue(screens.contains("FOSTER_APPLICANTS_EMPTY"))
        assertTrue(screens.contains("FOSTER_CHOOSE_HOME"))
        assertTrue(screens.contains("FOSTER_CHOOSE_ACTION"))
    }

    @Test
    fun extraTraitsAreOptionalAndUnknownIsNotFalse() {
        val blank = FoundPetFosterNeeds()
        assertTrue(blank.isCompleteEnoughToSend())
        assertNull(blank.size)
        assertNull(blank.needsMedication)
        assertNull(blank.cohabitsDogs)
        assertTrue(blank.needsMedication != false)
        assertTrue(blank.cohabitsDogs != false)
        val screens = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterCareRequestScreens.kt")
        assertTrue(screens.contains("additionalInfo.ifBlank { null }"))
        assertTrue(screens.contains("FoundPetFosterNeeds("))
        val form = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterTraitFields.kt")
        assertTrue(form.contains("No sé"))
        assertTrue(form.contains("Sin preferencia"))
        assertTrue(form.contains("Puedo recibir"))
        val home = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterScreens.kt")
        assertTrue(home.contains("FosterHomeCapabilityForm"))
    }

    @Test
    fun explicitIncompatibilityExcludesAndUnknownDoesNot() {
        val dog = FoundPetFosterNeeds(species = FosterSpeciesKind.DOG)
        val refusesDogs = FosterHomeCapabilities(acceptsDogs = false)
        assertFalse(FosterMatchingPolicy.evaluate(dog, refusesDogs).eligible)

        val unknownSpecies = FoundPetFosterNeeds()
        assertTrue(FosterMatchingPolicy.evaluate(unknownSpecies, refusesDogs).eligible)

        val medicated = FoundPetFosterNeeds(needsMedication = true)
        assertFalse(
            FosterMatchingPolicy.evaluate(
                medicated,
                FosterHomeCapabilities(acceptsMedication = false)
            ).eligible
        )
        assertTrue(
            FosterMatchingPolicy.evaluate(
                FoundPetFosterNeeds(needsMedication = null),
                FosterHomeCapabilities(acceptsMedication = false)
            ).eligible
        )
        assertTrue(
            FosterMatchingPolicy.evaluate(
                FoundPetFosterNeeds(needsMedication = false),
                FosterHomeCapabilities(acceptsMedication = false)
            ).eligible
        )

        val cannotLiveWithDogs = FoundPetFosterNeeds(cohabitsDogs = false)
        assertFalse(
            FosterMatchingPolicy.evaluate(
                cannotLiveWithDogs,
                FosterHomeCapabilities(livesWithDogs = true)
            ).eligible
        )
        assertTrue(
            FosterMatchingPolicy.evaluate(
                FoundPetFosterNeeds(cohabitsDogs = null),
                FosterHomeCapabilities(livesWithDogs = true)
            ).eligible
        )
    }

    @Test
    fun knownMatchRanksAboveANeutralHomeAndUnavailableHomesStayOut() {
        val puppy = FoundPetFosterNeeds(
            species = FosterSpeciesKind.DOG,
            lifeStage = FosterLifeStage.YOUNG
        )
        val specialist = FosterHomeCapabilities(acceptsDogs = true, acceptsYoung = true)
        val neutral = FosterHomeCapabilities()
        val paused = FosterHomeCapabilities(active = false, acceptsDogs = true, acceptsYoung = true)
        val noBase = FosterHomeCapabilities(hasBaseLocation = false, acceptsDogs = true)
        assertTrue(FosterMatchingPolicy.evaluate(puppy, specialist).rank > FosterMatchingPolicy.evaluate(puppy, neutral).rank)
        assertFalse(FosterMatchingPolicy.evaluate(puppy, paused).eligible)
        assertFalse(FosterMatchingPolicy.evaluate(puppy, noBase).eligible)
        assertFalse(FosterMatchingPolicy.evaluate(puppy, FosterHomeCapabilities(capacity = 0)).eligible)

        val ranked = FosterMatchingPolicy.rankCandidates(
            listOf("neutral" to neutral, "specialist" to specialist, "paused" to paused),
            { puppy },
            home = FosterHomeCapabilities()
        )
        // rankCandidates evaluates each item's needs against one home.
        // Here every item shares the same needs, so order follows the single home.
        assertEquals(listOf("neutral", "specialist", "paused"), ranked.map { it.first })

        val homes = listOf(neutral, specialist, paused)
        val ordered = homes
            .mapNotNull { home ->
                val result = FosterMatchingPolicy.evaluate(puppy, home)
                if (!result.eligible) null else home to result.rank
            }
            .sortedByDescending { it.second }
            .map { it.first }
        assertEquals(specialist, ordered.first())
        assertFalse(ordered.contains(paused))
    }

    @Test
    fun legacyTextAddsPreferenceWithoutBecomingANo() {
        val fromText = FosterMatchingPolicy.resolveLegacy(
            FosterHomeCapabilities(speciesPref = "perros", agePref = "cachorros")
        )
        assertEquals(true, fromText.acceptsDogs)
        assertNull(fromText.acceptsCats)
        assertEquals(true, fromText.acceptsYoung)
        val explicitNoWins = FosterMatchingPolicy.resolveLegacy(
            FosterHomeCapabilities(acceptsDogs = false, speciesPref = "perros")
        )
        assertEquals(false, explicitNoWins.acceptsDogs)
        val blank = parseSpeciesPref("   ")
        assertNull(blank.dogs)
        assertNull(blank.cats)
    }

    @Test
    fun zeroApplicantsHaveAnEmptyStateAndCardsHideTechnicalFields() {
        val screens = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterCareRequestScreens.kt")
        assertTrue(screens.contains("FOSTER_APPLICANTS_EMPTY"))
        assertTrue(screens.contains("if (rows.isEmpty())"))
        val view = presentFosterApplicant(
            name = "Casa Norte",
            localityId = "Palermo",
            capacity = 2,
            profileActive = true,
            hasBaseLocation = true,
            applicationStatus = "PENDING",
            needs = FoundPetFosterNeeds(species = FosterSpeciesKind.DOG),
            home = FosterHomeCapabilities(acceptsDogs = true, capacity = 2),
            callerIsManager = true,
            applicationExists = true
        )
        assertEquals("Casa Norte", view.title)
        assertEquals("Palermo", view.zone)
        assertEquals("Disponible", view.availability)
        assertEquals("Puede recibir 2", view.capacity)
        assertEquals("Coincide con lo que sabemos", view.compatibility)
        assertTrue(view.canChoose)
        assertFalse(FosterRequestPresentation.leaksTechnical(view.title))
        assertFalse(FosterRequestPresentation.leaksTechnical(view.compatibility))
        assertFalse(view.title.contains("id"))
    }

    @Test
    fun onlyTheManagerCanChooseAPendingAvailableHome() {
        val needs = FoundPetFosterNeeds()
        val home = FosterHomeCapabilities()
        assertFalse(
            presentFosterApplicant(
                "Ana", null, 1, true, true, "PENDING", needs, home,
                callerIsManager = false, applicationExists = true
            ).canChoose
        )
        assertFalse(
            presentFosterApplicant(
                "Ana", null, 1, true, true, "WITHDRAWN", needs, home,
                callerIsManager = true, applicationExists = true
            ).canChoose
        )
        assertFalse(
            presentFosterApplicant(
                "Ana", null, 1, false, true, "PENDING", needs, home,
                callerIsManager = true, applicationExists = true
            ).canChoose
        )
        assertFalse(
            presentFosterApplicant(
                "Ana", null, 1, true, false, "PENDING", needs, home,
                callerIsManager = true, applicationExists = true
            ).canChoose
        )
        assertFalse(
            presentFosterApplicant(
                "Ana", null, 1, true, true, "PENDING", needs, home,
                callerIsManager = true, applicationExists = false
            ).canChoose
        )
        assertFalse(
            FosterApplicantSelection.canChoose(
                callerIsManager = true,
                applicationExists = true,
                applicationStatus = "NOT_SELECTED",
                homeActive = true,
                homeHasBaseLocation = true
            )
        )
        val sql = source("infra/supabase-canonical/supabase/migrations/20261003120000_1107_foster_match_traits.sql")
        assertTrue(sql.contains("if v_app.status is distinct from 'PENDING'"))
        assertTrue(sql.contains("raise exception 'FORBIDDEN'"))
        assertTrue(sql.contains("raise exception 'NOT_FOUND'"))
        assertTrue(sql.contains("FOSTER_NOT_ELIGIBLE"))
        assertTrue(sql.contains("canon_cancel_foster_request"))
        assertFalse(sql.contains("'OWNER'"))
    }

    @Test
    fun publishUpdatesTheCtaAndALateSessionCannotEnter() {
        val epoch = ManualSessionEpoch()
        val board = FosterTransitBoard(epoch)
        val token = epoch.current()
        assertTrue(
            board.publish(token, FosterTransitSnapshot("pet-1", null, null))
        )
        assertEquals(
            "Buscar hogar de tránsito",
            FosterTransitVisibility.primaryLabel(
                FosterTransitVisibility.isActiveRequest(board.current("pet-1")?.status)
            )
        )
        assertTrue(
            board.publish(token, FosterTransitSnapshot("pet-1", "req-1", "REQUESTED"))
        )
        assertEquals(
            "Ver solicitud de tránsito",
            FosterTransitVisibility.primaryLabel(
                FosterTransitVisibility.isActiveRequest(board.current("pet-1")?.status)
            )
        )
        assertTrue(
            board.publish(token, FosterTransitSnapshot("pet-1", "req-1", "ACTIVE"))
        )
        assertEquals("Hogar seleccionado", FosterRequestPresentation.requestStatus(board.current("pet-1")?.status))
        assertTrue(
            board.publish(token, FosterTransitSnapshot("pet-1", "req-1", "CANCELLED"))
        )
        assertEquals(
            "Buscar hogar de tránsito",
            FosterTransitVisibility.primaryLabel(
                FosterTransitVisibility.isActiveRequest(board.current("pet-1")?.status)
            )
        )
        val stale = epoch.current()
        epoch.invalidate()
        assertFalse(board.publish(stale, FosterTransitSnapshot("pet-1", "other", "REQUESTED")))
        assertEquals("CANCELLED", board.current("pet-1")?.status)
        val screens = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterCareRequestScreens.kt")
        assertTrue(screens.contains("FosterTransitSignals.live.publish"))
        assertTrue(screens.contains("SessionGeneration.current()"))
        val detail = source("app/src/main/java/com/comunidapp/app/viewmodel/PetDetailViewModel.kt")
        assertTrue(detail.contains("FosterTransitSignals.live.publish"))
    }

    @Test
    fun migrationKeepsHistoricalNullsAndDoesNotEditEarlierFiles() {
        val sql = source("infra/supabase-canonical/supabase/migrations/20261003120000_1107_foster_match_traits.sql")
        assertTrue(sql.contains("size_band is null or size_band in ('SMALL', 'MEDIUM', 'LARGE')"))
        assertTrue(sql.contains("needs_medication boolean null"))
        assertTrue(sql.contains("accepts_dogs boolean null"))
        assertTrue(sql.contains("Null means unknown"))
        assertTrue(sql.contains("there is still no foster push fan-out"))
        assertFalse(sql.contains("update public.foster_care_requests set needs_medication = false"))
        val previous = source("infra/supabase-canonical/supabase/migrations/20261002150000_1106_vitacora_moment_media_and_case.sql")
        assertFalse(previous.contains("accepts_dogs"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
