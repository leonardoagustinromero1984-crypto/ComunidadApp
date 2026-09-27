package com.comunidapp.app.domain.location

import com.comunidapp.app.domain.onboarding.onb02.OrganizationKindOption
import com.comunidapp.app.domain.onboarding.onb02.TutorialId
import com.comunidapp.app.domain.onboarding.onb03.CanonicalTutorialEvent
import com.comunidapp.app.domain.onboarding.onb03.TutorialQueueResolver
import com.comunidapp.app.domain.publish.LostFoundPublishError
import com.comunidapp.app.domain.verification.VerificationDisplayPolicy
import com.comunidapp.app.ui.UiRegressionGateTest
import com.comunidapp.app.ui.components.leo.LeoRequiredField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LeoVerCommunityCare02PhysicalQa01ContractTest {

    @Test
    fun LOCATION_BUTTON_REQUESTS_PERMISSION_AND_KEEPS_MANUAL_PIN() {
        val picker = source("app/src/main/java/com/comunidapp/app/ui/screens/location/LocationPinPicker.kt")
        assertTrue(picker.contains("Usar mi ubicación"))
        assertTrue(picker.contains("RequestMultiplePermissions"))
        assertTrue(picker.contains("ACCESS_COARSE_LOCATION"))
        assertTrue(picker.contains("ACCESS_FINE_LOCATION"))
        assertTrue(picker.contains("onMapClick"))
        assertFalse(picker.contains("ACCESS_BACKGROUND_LOCATION"))
        val loc = source("app/src/main/java/com/comunidapp/app/domain/location/ForegroundLocation.kt")
        assertTrue(loc.contains("PRIORITY_BALANCED_POWER_ACCURACY"))
        assertTrue(loc.contains("PRIORITY_HIGH_ACCURACY"))
    }

    @Test
    fun REQUIRED_FIELDS_SHOW_ASTERISK_OPTIONAL_DO_NOT() {
        assertEquals("Especie *", LeoRequiredField.label("Especie"))
        assertEquals("Foto *", LeoRequiredField.label("Foto", required = true))
        assertEquals("Contacto", LeoRequiredField.label("Contacto", required = false))
        val lost = source("app/src/main/java/com/comunidapp/app/ui/screens/publish/PublishForms.kt")
        assertTrue(lost.contains("LeoRequiredField.label(\"Foto\")"))
        assertTrue(lost.contains("label = \"Especie\""))
        assertTrue(lost.contains("required = true"))
        assertEquals(1, Regex("""label = \"Especie\"""").findAll(lost).count())
        assertFalse(lost.contains("Text(text = \"Especie\""))
        val adoption = source("app/src/main/java/com/comunidapp/app/ui/screens/adoptions/AdoptionGeneralProfileScreen.kt")
        assertTrue(adoption.contains("opcional"))
        assertFalse(adoption.contains("Vivienda *"))
    }

    @Test
    fun PHOTO_INPUT_OFFERS_CAMERA_AND_GALLERY() {
        val picker = source("app/src/main/java/com/comunidapp/app/ui/media/LeoVerPhotoSourcePicker.kt")
        assertTrue(picker.contains("Tomar foto"))
        assertTrue(picker.contains("Elegir de galería"))
        val lost = source("app/src/main/java/com/comunidapp/app/ui/screens/publish/PublishForms.kt")
        assertTrue(lost.contains("rememberLeoVerPhotoSourcePicker"))
    }

    @Test
    fun FOUND_PUBLISH_HAS_STAGE_CODES_AND_ISOLATED_MATCH() {
        val sql = migration("20260921100000_1094_lost_found_create_stages.sql")
        assertTrue(sql.contains("LF-CREATE-IDENTITY"))
        assertTrue(sql.contains("LF-CREATE-ALERT"))
        assertTrue(sql.contains("perform public._canon_match_found_to_lost"))
        assertTrue(sql.contains("exception when others then"))
        assertEquals(
            LostFoundPublishError.IDENTITY,
            LostFoundPublishError.codeOf(IllegalStateException("LF-CREATE-IDENTITY"))
        )
        assertTrue(LostFoundPublishError.userMessage(IllegalStateException("LF-CREATE-ALERT")).contains("LF-CREATE-ALERT"))
    }

    @Test
    fun SHELTER_OPENS_SHELTER_TUTORIAL_NOT_VETERINARY() {
        val refuge = TutorialQueueResolver.queue(
            CanonicalTutorialEvent.ORGANIZATION_CREATED,
            consumed = { false },
            organizationKind = OrganizationKindOption.SHELTER,
            commercialOrg = false
        )
        assertEquals(listOf(TutorialId.T10B_SHELTER), refuge.tutorials)
        assertFalse(refuge.tutorials.contains(TutorialId.T10A_VETERINARY_CLINIC))
        val vet = TutorialQueueResolver.queue(
            CanonicalTutorialEvent.ORGANIZATION_CREATED,
            consumed = { false },
            organizationKind = OrganizationKindOption.VETERINARY_CLINIC,
            commercialOrg = true
        )
        assertTrue(vet.tutorials.contains(TutorialId.T10A_VETERINARY_CLINIC))
        assertFalse(vet.tutorials.contains(TutorialId.T10B_SHELTER))
    }

    @Test
    fun VERIFICATION_CALLS_REAL_RPC_AND_HUMAN_COPY() {
        val repo = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalOrganizationRepository.kt")
        assertTrue(repo.contains("override suspend fun requestVerification"))
        assertTrue(repo.contains("CanonicalVerificationRepository().request"))
        assertFalse(
            repo.substringAfter("override suspend fun requestVerification")
                .substringBefore("override suspend fun linkResource")
                .contains("NOT_IMPLEMENTED_PRODUCT")
        )
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/verification/LeoverVerificationRequestScreen.kt")
        assertTrue(screen.contains("p_organization_id") || screen.contains("organizationId"))
        assertFalse(screen.contains("NOT_IMPLEMENTED_PRODUCT"))
        assertEquals("Verificación pendiente", VerificationDisplayPolicy.statusLabel("PENDING"))
        assertEquals("Aún no solicitaste la verificación", VerificationDisplayPolicy.statusLabel("NOT_REQUESTED"))
        assertEquals("Verificado por LeoVer", VerificationDisplayPolicy.statusLabel("VERIFIED"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()

    private fun migration(name: String): String {
        val file = File(UiRegressionGateTest.repoRoot(), "infra/supabase-canonical/supabase/migrations/$name")
        assertTrue(file.exists())
        return file.readText()
    }
}
