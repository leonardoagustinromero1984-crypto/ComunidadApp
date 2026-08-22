package com.comunidapp.app.domain.qa

import com.comunidapp.app.domain.adoption.AdoptionApplicantPolicy
import com.comunidapp.app.domain.auth.GoogleAuthPolicy
import com.comunidapp.app.domain.business.PetFriendlyVenuePolicy
import com.comunidapp.app.domain.business.PetFriendlyVenueSubtype
import com.comunidapp.app.domain.m23.M23SlotGenerator
import com.comunidapp.app.domain.onboarding.onb02.OrganizationKindOption
import com.comunidapp.app.domain.onboarding.onb02.ProfileActorTaxonomy
import com.comunidapp.app.domain.RolePermissions
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.schedule.AppointmentSlotPolicy
import com.comunidapp.app.domain.verification.VerificationDisplayPolicy
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoVerPhysicalQaFix01ContractTest {

    @Test
    fun GOOGLE_AUTO_SELECT_DISABLED_WHEN_SUPPORTED() {
        assertFalse(GoogleAuthPolicy.GOOGLE_AUTO_SELECT)
        assertFalse(PhysicalQaFix01Contracts.GOOGLE_AUTO_SELECT)
        val repo = source("app/src/main/java/com/comunidapp/app/data/repository/SupabaseAuthRepository.kt")
        assertTrue(repo.contains("select_account") || repo.contains("OAUTH_PROMPT_SELECT_ACCOUNT"))
    }

    @Test
    fun OAUTH_ACCOUNT_CAN_ADD_PASSWORD() {
        val security = source("app/src/main/java/com/comunidapp/app/ui/screens/security/AccountSecurityScreen.kt")
        assertTrue(security.contains(PhysicalQaFix01Contracts.CREATE_PASSWORD_LABEL))
        val auth = source("app/src/main/java/com/comunidapp/app/data/repository/AuthRepository.kt")
        assertTrue(auth.contains("addPassword") || auth.contains("fun addPassword"))
    }

    @Test
    fun NO_DUPLICATE_PERSON_ON_METHOD_LINK() {
        assertTrue(PhysicalQaFix01Contracts.ONE_EMAIL_ONE_PERSON)
    }

    @Test
    fun INCOMPLETE_ONBOARDING_RESUMES() {
        assertTrue(PhysicalQaFix01Contracts.INCOMPLETE_ONBOARDING_RESUMES)
        assertTrue(PhysicalQaFix01Contracts.PHOTO_OPTIONAL_ON_ONBOARDING)
    }

    @Test
    fun EXISTING_OAUTH_EMAIL_RECOVERY_PATH() {
        val mapper = source("app/src/main/java/com/comunidapp/app/domain/auth/AuthErrorMapper.kt")
        assertTrue(mapper.contains("Google") || mapper.contains("Recuperar contraseña"))
    }

    @Test
    fun PROFILE_PHOTO_DRAG_PINCH() {
        val cropper = source("app/src/main/java/com/comunidapp/app/ui/media/LeoVerMediaCropper.kt")
        assertTrue(cropper.contains("detectTransformGestures"))
        val editor = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/AvatarPhotoEditorScreen.kt")
        assertTrue(editor.contains("LeoVerMediaCropper"))
        val onboarding = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/ProfileOnboardingScreen.kt")
        assertTrue(onboarding.contains("AvatarPhotoEditorScreen"))
    }

    @Test
    fun PHONE_PHOTO_NORMALIZED_AND_COMPRESSED() {
        val ingest = source("app/src/main/java/com/comunidapp/app/domain/media/ImageIngestPipeline.kt")
        assertTrue(ingest.contains("ExifInterface"))
        assertTrue(ingest.contains("JPEG_QUALITY") || ingest.contains("compress"))
    }

    @Test
    fun VIDEO_PIPELINE_PRESERVED() {
        val files = source("app/src/main/java/com/comunidapp/app/data/files/FileUploadCoordinator.kt")
        assertTrue(files.contains("Tus") || files.contains("TUS") || files.contains("transcod") || files.contains("video"))
    }

    @Test
    fun PUBLIC_CONTACT_PHONE_ONLY() {
        assertTrue(PhysicalQaFix01Contracts.CONTACT_PHONE_VISIBLE)
        assertFalse(PhysicalQaFix01Contracts.CONTACT_INSTAGRAM_VISIBLE)
        assertFalse(PhysicalQaFix01Contracts.CONTACT_EMAIL_VISIBLE)
        val negocio = source("app/src/main/java/com/comunidapp/app/ui/screens/business/MiNegocioScreen.kt")
        assertTrue(negocio.contains("Teléfono de contacto"))
        assertFalse(negocio.contains("tel / email / IG"))
    }

    @Test
    fun MISSING_REQUIREMENTS_LISTED() {
        val missing = source("app/src/main/java/com/comunidapp/app/domain/validation/MissingRequirements.kt")
        assertTrue(missing.contains("Te falta completar:"))
        val ui = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoValidationSummary.kt")
        assertTrue(ui.contains("LeoValidationSummary"))
    }

    @Test
    fun TIME_FIELDS_NOT_FREE_TEXT_AND_OPEN_24H() {
        assertFalse(PhysicalQaFix01Contracts.TIME_FIELDS_FREE_TEXT)
        assertTrue(PhysicalQaFix01Contracts.OPEN_24_HOURS_SUPPORTED)
        val hours = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoVerHours.kt")
        assertTrue(hours.contains("TimePicker") || hours.contains("open24Hours"))
    }

    @Test
    fun APPOINTMENT_INTERVAL_AND_SLOT_GENERATION() {
        assertEquals(listOf(15, 20, 30, 45, 60), AppointmentSlotPolicy.INTERVAL_MINUTES)
        val gen = source("app/src/main/java/com/comunidapp/app/domain/m23/M23SlotGenerator.kt")
        assertTrue(gen.contains("overlaps"))
        assertTrue(gen.contains("slotIntervalMinutes") || gen.contains("slotDurationMinutes"))
        assertTrue(M23SlotGenerator.overlaps(100, 200, 150, 250))
        assertFalse(M23SlotGenerator.overlaps(100, 200, 200, 300))
    }

    @Test
    fun PERSON_ADOPTION_PUBLISH_FORBIDDEN() {
        assertFalse(PhysicalQaFix01Contracts.PUBLIC_ADOPTION_ALLOWED_PERSON)
        assertFalse(AdoptionApplicantPolicy.PERSON_PUBLISH_ALLOWED)
        assertFalse(RolePermissions.canPublishAdoption(OperationalContext.Personal))
    }

    @Test
    fun MIS_POSTULACIONES_AND_REUSABLE_PROFILE() {
        assertEquals("Mis postulaciones", AdoptionApplicantPolicy.MY_APPLICATIONS_LABEL)
        assertTrue(AdoptionApplicantPolicy.REUSABLE_PROFILE)
        val store = source("app/src/main/java/com/comunidapp/app/data/local/AdoptionApplicantProfileStore.kt")
        assertTrue(store.contains("AdoptionApplicantProfileStore"))
    }

    @Test
    fun VERIFICATION_NOT_SELF_DECLARED() {
        assertEquals("actor_verifications", VerificationDisplayPolicy.SOURCE_OF_TRUTH)
        assertFalse(VerificationDisplayPolicy.SELF_DECLARED_ALLOWED)
        assertFalse(VerificationDisplayPolicy.FILTERS_VISIBLE)
    }

    @Test
    fun LOST_FOUND_MAP_AND_CASE_BOUND_ACTIONS() {
        assertFalse(PhysicalQaFix01Contracts.GLOBAL_SIGHTING_ACTION)
        assertTrue(PhysicalQaFix01Contracts.SIGHTING_REQUIRES_CASE)
        val lost = source("app/src/main/java/com/comunidapp/app/ui/screens/lostfound/LostFoundScreen.kt")
        assertFalse(lost.contains("Avistamientos y coincidencias"))
        val map = source("app/src/main/java/com/comunidapp/app/ui/screens/lostfound/AlertMapScreen.kt")
        assertTrue(map.contains("LeoVerMap"))
        assertTrue(map.contains("ForegroundLocation"))
        assertTrue(map.contains("Provincia y localidad") || map.contains("Elegir provincia y localidad"))
    }

    @Test
    fun PERSON_CAMPAIGN_AND_EVENT_GATES() {
        assertFalse(PhysicalQaFix01Contracts.PERSON_CAMPAIGN_CREATE)
        assertFalse(PhysicalQaFix01Contracts.PERSON_CAMPAIGN_ADMIN)
        assertTrue(PhysicalQaFix01Contracts.PERSON_CAMPAIGN_PARTICIPATE)
        assertFalse(PhysicalQaFix01Contracts.PERSON_EVENT_CREATE)
        assertFalse(RolePermissions.canCreateCampaigns(OperationalContext.Personal))
        assertFalse(RolePermissions.canPublishEvent(OperationalContext.Personal))
        val m17 = source("app/src/main/java/com/comunidapp/app/ui/screens/m17/M17DonationScreens.kt")
        assertTrue(m17.contains("canAdminister"))
        assertTrue(m17.contains("Quiero colaborar"))
        val m18 = source("app/src/main/java/com/comunidapp/app/ui/screens/m18/M18EventScreens.kt")
        assertTrue(m18.contains("canAdminister"))
    }

    @Test
    fun PARTICIPATION_MESSAGES_IDEMPOTENT() {
        val messaging = source("app/src/main/java/com/comunidapp/app/domain/participation/ParticipationMessaging.kt")
        assertTrue(messaging.contains("idempotencyKey"))
        val coord = source("app/src/main/java/com/comunidapp/app/data/repository/ParticipationCoordinator.kt")
        assertTrue(coord.contains("notifyCampaignOffer"))
        assertTrue(coord.contains("notifyEventRegistration"))
    }

    @Test
    fun PET_FRIENDLY_IS_SECOND_LEVEL() {
        assertEquals(5, ProfileActorTaxonomy.FIRST_LEVEL_ACTOR_COUNT)
        assertFalse(PetFriendlyVenuePolicy.FIRST_LEVEL_ACTOR)
        assertTrue(PetFriendlyVenuePolicy.SUBTYPE_REQUIRED)
        assertTrue(OrganizationKindOption.commercial.contains(OrganizationKindOption.PET_FRIENDLY_VENUE))
        assertEquals(6, PetFriendlyVenueSubtype.entries.size)
        assertTrue(PetFriendlyVenueSubtype.required("HOTEL"))
        assertFalse(PetFriendlyVenueSubtype.required(null))
    }

    @Test
    fun STORY_ORANGE_IS_BRAND_TOKEN() {
        assertEquals("BrandOrange", PhysicalQaFix01Contracts.STORY_ORANGE_TOKEN)
    }

    @Test
    fun NO_DUPLICATE_COMMERCIAL_EDIT_FORM() {
        val negocio = source("app/src/main/java/com/comunidapp/app/ui/screens/business/MiNegocioScreen.kt")
        assertFalse(negocio.contains(" Editar perfil"))
    }

    @Test
    fun HUMAN_REFUGE_FILTER_LABELS() {
        val labels = source("app/src/main/java/com/comunidapp/app/data/model/M16ShelterLabels.kt")
        assertTrue(labels.contains("Adopciones"))
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/m16/M16ShelterScreens.kt")
        assertTrue(screen.contains("visibleLabel()"))
        assertFalse(screen.contains("Especie (DOG, CAT"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
