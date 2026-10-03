package com.comunidapp.app.domain.qa

import com.comunidapp.app.data.local.InMemoryOnb02Store
import com.comunidapp.app.domain.RolePermissions
import com.comunidapp.app.domain.context.ContextRegistry
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.files.ResumableUploadPolicy
import com.comunidapp.app.domain.media.AvatarPhotoEditorState
import com.comunidapp.app.domain.media.CoverCropMath
import com.comunidapp.app.domain.media.MediaIngestionPolicy
import com.comunidapp.app.domain.onboarding.onb02.AddFunctionCatalog
import com.comunidapp.app.domain.onboarding.onb02.AddFunctionGroupKind
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import com.comunidapp.app.domain.onboarding.onb02.OccupiedFunctions
import com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind
import com.comunidapp.app.domain.onboarding.onb02.TutorialId
import com.comunidapp.app.domain.onboarding.onb02.TutorialTapPolicy
import com.comunidapp.app.domain.onboarding.onb02.TutorialTapZone
import com.comunidapp.app.ui.UiRegressionGateTest
import com.comunidapp.app.viewmodel.Onb02Phase
import com.comunidapp.app.viewmodel.Onb02ViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoVerPhysicalQaFix04ContractTest {

    @Test
    fun ADD_FUNCTION_FOSTER_PRESENT() {
        val groups = AddFunctionCatalog.groupedAvailable(OccupiedFunctions())
        assertEquals(5, groups.size)
        assertEquals(AddFunctionGroupKind.FOSTER, groups.last().kind)
        assertEquals("Hogar de tránsito", groups.last().visibleLabel)
        assertTrue(groups.any { it.visibleLabel == "Hogar de tránsito" })
        assertFalse(groups.any { it.visibleLabel.equals("Persona", ignoreCase = true) })
        val withRescuer = AddFunctionCatalog.groupedAvailable(
            OccupiedFunctions(extras = setOf(LeoverFunction.RESCUER))
        )
        assertTrue(withRescuer.any { it.kind == AddFunctionGroupKind.FOSTER })
        assertEquals(
            ContextRegistry.addFunctionTopLevel().map { it.kind },
            listOf(
                AddFunctionGroupKind.RESCUER,
                AddFunctionGroupKind.SHELTER,
                AddFunctionGroupKind.PROFESSIONAL,
                AddFunctionGroupKind.BUSINESS,
                AddFunctionGroupKind.FOSTER
            )
        )
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/onb02/Onb02Screens.kt")
        assertTrue(screen.contains("groupedAvailable(viewModel.occupiedForAddFunction())"))
        assertTrue(PhysicalQaFix04Contracts.ADD_FUNCTION_FOSTER_PRESENT)
    }

    @Test
    fun PRO_AND_BUSINESS_NOT_FLAT() {
        val groups = AddFunctionCatalog.groupedAvailable(OccupiedFunctions())
        assertTrue(groups.none { it.visibleLabel.contains("Paseador") })
        assertTrue(groups.none { it.visibleLabel.contains("Veterinaria / Clínica") })
        val business = AddFunctionCatalog.availableBusiness(OccupiedFunctions())
        assertTrue(business.any { it.id == "WALKING_CARE" })
        assertTrue(business.any { it.id == "TRAINING_ORG" })
        assertTrue(business.any { it.id == "BRAND" })
        assertTrue(business.any { it.id == "PET_FRIENDLY" })
    }

    @Test
    fun OMIT_SKIPS_ALL_REMAINING() {
        val store = InMemoryOnb02Store()
        val vm = Onb02ViewModel(store = store, userIdProvider = { "user-1" })
        vm.start(Onb02FlowKind.FULL_ONBOARDING)
        vm.skipCurrentTutorial()
        vm.toggleExtra(LeoverFunction.RESCUER)
        vm.confirmSelection()
        assertEquals(TutorialId.T02_RESCUER, vm.ui.value.currentTutorial?.id)
        vm.skipCurrentTutorial()
        assertEquals(Onb02Phase.DONE, vm.ui.value.phase)
        assertTrue(PhysicalQaFix04Contracts.OMIT_SKIPS_ALL_REMAINING)
    }

    @Test
    fun BACKGROUND_TAP_DOES_NOT_CLOSE_TUTORIAL() {
        assertEquals(TutorialTapZone.PREVIOUS, TutorialTapPolicy.zone(10f, 100f))
        assertEquals(TutorialTapZone.IGNORE, TutorialTapPolicy.zone(50f, 100f))
        assertEquals(TutorialTapZone.NEXT, TutorialTapPolicy.zone(90f, 100f))
        assertFalse(TutorialTapPolicy.BACKGROUND_TAP_CLOSES)
        assertFalse(TutorialTapPolicy.LAST_PAGE_RIGHT_FINISHES)
        val pager = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoVerTutorialPager.kt")
        assertFalse(pager.contains("if (lastPage) onPrimary() else onPageChange"))
        assertTrue(pager.contains("TutorialTapPolicy.zone"))
    }

    @Test
    fun UNIVERSAL_HOME_IS_FEED() {
        val home = source("app/src/main/java/com/comunidapp/app/ui/screens/home/HomeScreen.kt")
        assertFalse(home.contains("RescuerOperationalHub("))
        assertFalse(home.contains("RefugeOperationalHub("))
        assertTrue(home.contains("LeoSocialPostCard"))
        val gestion = source("app/src/main/java/com/comunidapp/app/ui/screens/shelters/ShelterOperationsScreens.kt")
        assertTrue(gestion.contains("RescuerOperationalHub"))
        assertTrue(PhysicalQaFix04Contracts.ALL_CONTEXT_HOME_IS_FEED)
    }

    @Test
    fun RESCUER_ANIMALS_MANUAL_AND_IMPORT() {
        val pets = source("app/src/main/java/com/comunidapp/app/ui/screens/pets/MyPetsScreen.kt")
        assertTrue(pets.contains("+ Agregar mascota"))
        assertTrue(pets.contains("Importar mascotas"))
        assertTrue(pets.contains("Cómo funciona la importación"))
        assertTrue(pets.contains("Necesita foto") || pets.contains("Necesitan foto"))
        assertTrue(PhysicalQaFix04Contracts.RESCUER_MANUAL_PET_ADD_VISIBLE)
        assertTrue(PhysicalQaFix04Contracts.RESCUER_IMPORT_VISIBLE)
    }

    @Test
    fun RESCUER_PERMISSIONS() {
        val ctx = OperationalContext.Rescuer("u1", "Rescatista")
        assertTrue(RolePermissions.canPublishAdoption(ctx))
        assertTrue(RolePermissions.canPublishLostFound(ctx))
        assertFalse(RolePermissions.canPublishFosterHome(ctx))
        assertTrue(RolePermissions.canCreateCampaigns(ctx))
        assertTrue(RolePermissions.canPublishEvent(ctx))
        assertTrue(RolePermissions.canPublishShelterNeeds(ctx))
    }

    @Test
    fun CROP_PAN_AT_SCALE_ONE_IS_REAL() {
        val state = AvatarPhotoEditorState("x", imageWidth = 4000, imageHeight = 2250)
        val panned = state.pan(40f, 0f)
        assertTrue(panned.offsetX > 0f)
        val limits = CoverCropMath.panLimits(280f, 1f, 4000, 2250)
        assertTrue(limits.maxX > 0f)
        assertTrue(PhysicalQaFix04Contracts.PROFILE_CROP_PAN_AT_SCALE_ONE)
        val cropper = source("app/src/main/java/com/comunidapp/app/ui/media/LeoVerMediaCropper.kt")
        assertTrue(cropper.contains("LeoVerMediaCropper"))
        val editor = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/AvatarPhotoEditorScreen.kt")
        assertTrue(editor.contains("LeoVerMediaCropper"))
    }

    @Test
    fun TUS_AND_VIDEO_POLICY() {
        assertTrue(MediaIngestionPolicy.shouldUseTus(10L * 1024 * 1024))
        assertFalse(MediaIngestionPolicy.shouldUseTus(ResumableUploadPolicy.STANDARD_THRESHOLD_BYTES))
        val social = source("app/src/main/java/com/comunidapp/app/domain/social/SocialMediaPipeline.kt")
        assertTrue(social.contains("Transformer"))
        val tus = source("app/src/main/java/com/comunidapp/app/data/files/SupabaseTusUploader.kt")
        assertTrue(tus.contains("upload/resumable"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
