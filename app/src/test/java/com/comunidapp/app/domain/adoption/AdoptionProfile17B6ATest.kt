package com.comunidapp.app.domain.adoption

import com.comunidapp.app.data.model.PetSpecies
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdoptionProfile17B6ATest {

    @Test
    fun partialProfileCanBeSavedAndEmptyStaysIncomplete() {
        val empty = AdopterProfile()
        assertTrue(empty.isStructurallyEmpty)
        assertFalse(empty.isComplete)
        assertTrue(AdopterProfileCompleteness.saveFeedback(empty).contains("incompleto"))
        val partial = AdopterProfile(housingKind = HousingKind.HOUSE, notes = null)
        assertFalse(partial.isComplete)
        assertFalse(partial.isStructurallyEmpty)
        assertFalse(AdopterProfileCompleteness.saveFeedback(partial).contains("Perfil completo guardado"))
        assertFalse(AdoptionApplyMinimum.isReady(partial))
    }

    @Test
    fun householdAgreementAloneDoesNotAllowApply() {
        val onlyAgreement = AdopterProfile(householdAgrees = true)
        assertFalse(AdoptionApplyMinimum.isReady(onlyAgreement))
        val decision = apply(onlyAgreement)
        assertFalse(decision.allowed)
        assertTrue(decision.openProfile)
        assertEquals(AdoptionApplyBlock.NEEDS_PROFILE, decision.block)
        assertFalse(decision.message.orEmpty().contains("EXCEPTION"))
    }

    @Test
    fun missingHousingOpensTheProfile() {
        val decision = apply(ready().copy(housingKind = null))
        assertFalse(decision.allowed)
        assertTrue(decision.openProfile)
        assertTrue(decision.message.orEmpty().contains("vivienda"))
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/adoptions/AdoptionApplicationScreens.kt")
        assertTrue(screen.contains("Completar perfil"))
    }

    @Test
    fun rentPermissionIsDistinctFromUnknownAndFromFalse() {
        val unknown = apply(ready().copy(housingTenure = HousingTenure.RENT, landlordAllowsPets = null))
        assertFalse(unknown.allowed)
        assertTrue(unknown.openProfile)
        assertTrue(unknown.message.orEmpty().contains("alquiler"))
        assertEquals(AdoptionApplyBlock.NEEDS_PROFILE, unknown.block)

        val refused = apply(ready().copy(housingTenure = HousingTenure.RENT, landlordAllowsPets = false))
        assertFalse(refused.allowed)
        assertFalse(refused.openProfile)
        assertEquals(AdoptionApplyBlock.LANDLORD_DOES_NOT_ALLOW, refused.block)
        assertTrue(refused.message.orEmpty().contains("no permiten"))

        val allowed = apply(ready().copy(housingTenure = HousingTenure.RENT, landlordAllowsPets = true))
        assertTrue(allowed.allowed)
        val owned = apply(ready().copy(housingTenure = HousingTenure.OWN, landlordAllowsPets = null))
        assertTrue(owned.allowed)
    }

    @Test
    fun animalsAndHoursAreRequiredToApplyAndOptionalFieldsAreNot() {
        val animals = ready().copy(hasCats = null)
        assertFalse(AdoptionApplyMinimum.isReady(animals))
        assertTrue(apply(animals).message.orEmpty().contains("animales"))
        val hours = ready().copy(hoursAlone = null)
        assertFalse(AdoptionApplyMinimum.isReady(hours))
        assertTrue(apply(hours).openProfile)
        assertTrue(apply(hours).message.orEmpty().contains("solo"))
        val optional = ready().copy(
            experienceBand = null,
            canVetFollowup = null,
            canMedicate = null,
            escapeProtection = null,
            notes = null,
            motivation = null
        )
        assertTrue(AdoptionApplyMinimum.isReady(optional))
        assertTrue(apply(optional).allowed)
    }

    @Test
    fun escapeProtectionMissingIsNotIncompatibleAndAKnownGapIs() {
        val required = AdoptionRequirements(requiresEscapeProtection = true)
        val missing = AdoptionMatchingPolicy.evaluate(AdopterProfile(escapeProtection = null), required)
        assertEquals(AdoptionMatchOutcome.COMPATIBLE, missing.outcome)
        assertEquals(AdoptionMatchClassification.MISSING_REQUIRED_PROFILE_DATA, missing.classification)
        assertTrue(missing.incompatibilities.isEmpty())
        assertFalse(missing.autoAssigned)
        val insufficient = AdoptionMatchingPolicy.evaluate(AdopterProfile(escapeProtection = false), required)
        assertEquals(AdoptionMatchOutcome.INCOMPATIBLE, insufficient.outcome)
        assertEquals(
            AdoptionMatchClassification.INCOMPATIBLE_WITH_LISTING_REQUIREMENT,
            insufficient.classification
        )
        val protectedHome = AdoptionMatchingPolicy.evaluate(AdopterProfile(escapeProtection = true), required)
        assertEquals(AdoptionMatchClassification.COMPATIBLE, protectedHome.classification)
        assertNotEquals(
            EscapeProtectionCopy.requirementHint(PetSpecies.CAT),
            EscapeProtectionCopy.requirementHint(PetSpecies.DOG)
        )
        assertTrue(EscapeProtectionCopy.PROFILE_HINT.contains("dirección"))
    }

    @Test
    fun hoursUseANumericRangeAndPresetsStayShortcuts() {
        val legacy = AdoptionGeneralProfileCodec.decodeProfile("""{"hours_alone_band":"H4_TO_8"}""")
        assertEquals(HoursAloneEstimate.FROM_4_TO_8, legacy.hoursAlone)
        val exact = AdoptionGeneralProfileCodec.decodeProfile(
            """{"hours_alone_from":6,"hours_alone_to":6,"hours_alone_band":"UNDER_4"}"""
        )
        assertEquals(HoursAloneEstimate(6, 6), exact.hoursAlone)
        assertNull(HoursAloneEstimate(6, 6).legacyBandToken())
        val cap = AdoptionRequirements(maxHoursAlone = HoursAloneEstimate.FROM_4_TO_8)
        val inside = AdoptionMatchingPolicy.evaluate(AdopterProfile(hoursAlone = HoursAloneEstimate(6, 6)), cap)
        assertEquals(AdoptionMatchFact.MATCH, inside.findings.single().fact)
        val across = AdoptionMatchingPolicy.evaluate(AdopterProfile(hoursAlone = HoursAloneEstimate(6, 10)), cap)
        assertEquals(AdoptionMatchFact.UNKNOWN, across.findings.single().fact)
        assertEquals(AdoptionMatchClassification.MISSING_REQUIRED_PROFILE_DATA, across.classification)
        val over = AdoptionMatchingPolicy.evaluate(AdopterProfile(hoursAlone = HoursAloneEstimate.OVER_8), cap)
        assertEquals(AdoptionMatchFact.INCOMPATIBLE, over.findings.single().fact)
        val stored = AdoptionGeneralProfileCodec.upsertProfile("{}", AdopterProfile(hoursAlone = HoursAloneEstimate(6, 6)))
        assertEquals("6", stored["p_hours_alone_from"]?.toString())
        assertTrue(stored["p_hours_alone_band"] is JsonNull)
    }

    @Test
    fun allergiesDoNotMatchAndSensitiveDataIsNotARequirement() {
        val policy = source("app/src/main/java/com/comunidapp/app/domain/adoption/AdoptionMatching.kt")
            .substringAfter("object AdoptionMatchingPolicy")
            .substringBefore("object AdoptionMatchPresentation")
        assertFalse(policy.contains("allerg"))
        val names = AdoptionRequirements::class.java.declaredFields.map { it.name.lowercase() }
        listOf("dni", "salario", "salary", "marital", "medic", "profesion", "alerg", "domicilio").forEach { token ->
            assertFalse(names.any { it.contains(token) })
        }
        assertFalse(AdoptionPrivacy.ALLERGIES_ASKED)
        assertFalse(AdoptionPrivacy.SHOWN_ON_PUBLIC_LISTING)
        assertTrue(AdoptionPrivacy.notCollectedByDefault.contains("DNI"))
        assertTrue(AdoptionPrivacy.notCollectedByDefault.contains("estado civil"))
        val echoed = AdoptionGeneralProfileCodec.upsertProfile("""{"allergies":"polen"}""", AdopterProfile())
        assertEquals("polen", echoed["p_allergies"]?.toString()?.trim('"'))
        val profile = source("app/src/main/java/com/comunidapp/app/ui/screens/adoptions/AdoptionGeneralProfileScreen.kt")
        assertFalse(profile.contains("allergies"))
        assertFalse(profile.contains("DNI"))
        val sql = source("infra/supabase-canonical/supabase/migrations/20261003200000_1109_adoption_profile_minimum.sql")
        assertTrue(sql.contains("escape_protection"))
        assertTrue(sql.contains("hours_alone_from"))
        assertTrue(sql.contains("requires_escape_protection"))
        assertFalse(sql.contains("function public.canon_apply_adoption"))
        assertFalse(sql.contains("add column if not exists dni"))
        assertFalse(sql.contains("add column if not exists allergies"))
    }

    private fun ready() = AdopterProfile(
        householdAgrees = true,
        housingKind = HousingKind.HOUSE,
        hasDogs = false,
        hasCats = false,
        hasOtherAnimals = false,
        hoursAlone = HoursAloneEstimate.UNDER_4
    )

    private fun apply(profile: AdopterProfile) =
        AdoptionApplyPolicy.evaluate(
            authenticated = true,
            isOwnPublication = false,
            publicationAccepting = true,
            hasActiveApplication = false,
            profile = profile
        )

    private fun source(path: String): String {
        val candidates = listOf(
            File(path),
            File(System.getProperty("user.dir"), path),
            File(System.getProperty("user.dir"), "../$path")
        )
        return (candidates.firstOrNull { it.isFile } ?: error("SOURCE_NOT_FOUND:$path")).readText()
    }
}
