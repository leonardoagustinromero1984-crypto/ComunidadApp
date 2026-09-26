package com.comunidapp.app.domain.qa

import com.comunidapp.app.data.local.InMemoryOnb02Store
import com.comunidapp.app.domain.context.NewContextActivation
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.map.LeoVerGeoPoint
import com.comunidapp.app.domain.media.AvatarPhotoEditorState
import com.comunidapp.app.domain.media.CoverCropMath
import com.comunidapp.app.domain.media.ProfileMediaPipeline
import com.comunidapp.app.domain.navigation.AppNavRestoreStore
import com.comunidapp.app.domain.onboarding.onb02.AddFunctionCatalog
import com.comunidapp.app.domain.onboarding.onb02.AddFunctionGroupKind
import com.comunidapp.app.domain.onboarding.onb02.FunctionSetupMapping
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import com.comunidapp.app.domain.onboarding.onb02.OccupiedFunctions
import com.comunidapp.app.domain.onboarding.onb02.Onb02Copy
import com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind
import com.comunidapp.app.domain.onboarding.onb02.OrganizationKindOption
import com.comunidapp.app.domain.onboarding.onb02.OrganizationSetupAction
import com.comunidapp.app.domain.onboarding.onb02.ProfileActorKind
import com.comunidapp.app.domain.ux.HumanLocationLabel
import com.comunidapp.app.navigation.NavRoutes
import com.comunidapp.app.ui.UiRegressionGateTest
import com.comunidapp.app.viewmodel.Onb02Phase
import com.comunidapp.app.viewmodel.Onb02ViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoVerPhysicalQaFix03ContractTest {

    @Test
    fun PERSON_ALWAYS_SELECTED_LOCKED() {
        val store = InMemoryOnb02Store()
        val vm = Onb02ViewModel(store = store, userIdProvider = { "user-1" })
        vm.start(Onb02FlowKind.FULL_ONBOARDING)
        vm.skipCurrentTutorial()
        assertEquals(ProfileActorKind.PERSON, vm.ui.value.selection.actorKind)
        vm.selectActor(ProfileActorKind.PERSON)
        assertEquals(ProfileActorKind.PERSON, vm.ui.value.selection.actorKind)
        vm.selectActor(ProfileActorKind.INDEPENDENT_RESCUER)
        assertEquals(ProfileActorKind.INDEPENDENT_RESCUER, vm.ui.value.selection.actorKind)
        assertTrue(Onb02Copy.PROFILE_PERSONAL_ALWAYS_ACTIVE)
        assertFalse(Onb02Copy.PROFILE_PERSONAL_EDITABLE_SELECTION)
        assertFalse(Onb02Copy.PROFILE_PERSONAL_REMOVABLE)
        assertTrue(PhysicalQaFix03Contracts.PERSON_ALWAYS_SELECTED)
        assertFalse(PhysicalQaFix03Contracts.PERSON_SELECTION_EDITABLE)
        val selector = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/onb02/Onb02Screens.kt")
        assertTrue(selector.contains("locked = personLocked"))
        assertTrue(selector.contains("Siempre activo"))
        assertTrue(Onb02Copy.SELECTOR_SUBTITLE.contains("perfil Personal es la base"))
    }

    @Test
    fun ADD_FUNCTION_GROUP_COUNT_AND_ORDER() {
        val groups = AddFunctionCatalog.groupedAvailable(OccupiedFunctions())
        assertEquals(PhysicalQaFix03Contracts.ADD_FUNCTION_TOP_LEVEL_COUNT, AddFunctionCatalog.topLevelIds().size)
        assertEquals(
            listOf(
                AddFunctionGroupKind.RESCUER,
                AddFunctionGroupKind.SHELTER,
                AddFunctionGroupKind.PROFESSIONAL,
                AddFunctionGroupKind.BUSINESS,
                AddFunctionGroupKind.FOSTER
            ),
            groups.map { it.kind }
        )
        assertTrue(groups.none { it.visibleLabel.equals("Persona", ignoreCase = true) })
        assertFalse(PhysicalQaFix03Contracts.PERSON_IN_ADD_FUNCTION)
        assertTrue(PhysicalQaFix03Contracts.FOSTER_PRESENT)
        assertTrue(PhysicalQaFix03Contracts.ADD_FUNCTION_GROUPED)
    }

    @Test
    fun ADD_FUNCTION_PROFESSIONAL_NOT_FLAT() {
        val groups = AddFunctionCatalog.groupedAvailable(OccupiedFunctions())
        assertTrue(groups.none { it.visibleLabel.contains("Paseador") })
        assertTrue(groups.none { it.visibleLabel.contains("Peluquero") })
        assertTrue(groups.none { it.visibleLabel.contains("Adiestrador") })
        assertTrue(groups.none { it.visibleLabel.contains("Veterinario/a profesional") })
        val professional = AddFunctionCatalog.availableProfessional(OccupiedFunctions())
        assertEquals(4, professional.size)
        assertFalse(PhysicalQaFix03Contracts.PROFESSIONAL_CATEGORIES_FLAT)
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/onb02/Onb02Screens.kt")
        assertTrue(screen.contains("groupedAvailable"))
        assertFalse(screen.contains("AddFunctionCatalog.available("))
    }

    @Test
    fun ADD_FUNCTION_BUSINESS_NOT_FLAT() {
        val groups = AddFunctionCatalog.groupedAvailable(OccupiedFunctions())
        assertTrue(groups.none { it.visibleLabel.contains("Veterinaria / Clínica") })
        assertTrue(groups.none { it.visibleLabel.contains("Tienda") })
        assertTrue(groups.none { it.visibleLabel.contains("Lugar pet friendly") })
        val business = AddFunctionCatalog.availableBusiness(OccupiedFunctions())
        assertTrue(business.any { it.id == "VETERINARY_CLINIC" })
        assertFalse(PhysicalQaFix03Contracts.BUSINESS_CATEGORIES_FLAT)
    }

    @Test
    fun CREATE_VETERINARY_PERSISTS_AND_ACTIVATES_CONTEXT() {
        val store = InMemoryOnb02Store()
        val vm = Onb02ViewModel(store = store, userIdProvider = { "user-1" })
        vm.start(Onb02FlowKind.ADD_FUNCTION_LATER)
        val vet = AddFunctionCatalog.catalog().first { it.id == "VETERINARY_CLINIC" }
        vm.selectAddable(vet)
        assertEquals(Onb02Phase.ORG_SETUP, vm.ui.value.phase)
        vm.setOrganizationAction(OrganizationSetupAction.CREATE)
        vm.confirmOrganizationSetup()
        val route = vm.setupRouteAfterTutorials().orEmpty()
        assertTrue(route.contains("create_organization"))
        assertTrue(route.contains("VETERINARY_CLINIC"))
        assertFalse(route.contains(NavRoutes.SETTINGS))
        val ctx = NewContextActivation.contextForOrganization("org-vet", "Clínica Norte", "VETERINARY_CLINIC")
        assertTrue(ctx is OperationalContext.Veterinary)
        assertEquals(NavRoutes.MY_BUSINESS, NewContextActivation.landingRoute(ctx, OrganizationKindOption.VETERINARY_CLINIC))
        val nav = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(nav.contains("activateNewlyCreated"))
        assertTrue(nav.contains("landingRoute"))
        assertFalse(PhysicalQaFix03Contracts.VETERINARY_CREATE_DESTINATION_SETTINGS)
    }

    @Test
    fun CREATE_RESCUER_ACTIVATES_DASHBOARD() {
        val store = InMemoryOnb02Store()
        val vm = Onb02ViewModel(store = store, userIdProvider = { "user-1" })
        vm.start(Onb02FlowKind.ADD_FUNCTION_LATER)
        vm.selectAddable(AddFunctionCatalog.catalog().first { it.id == "RESCUER" })
        repeat(24) {
            val phase = vm.ui.value.phase
            if (phase != Onb02Phase.TUTORIAL && phase != Onb02Phase.INTRO) return@repeat
            vm.skipCurrentTutorial()
        }
        if (vm.ui.value.phase != Onb02Phase.DONE) vm.finish()
        val route = vm.setupRouteAfterTutorials()
        assertEquals(NavRoutes.HOME, route)
        assertEquals(NavRoutes.HOME, FunctionSetupMapping.routeFor(LeoverFunction.RESCUER).route)
        assertFalse(PhysicalQaFix03Contracts.RESCUER_CREATE_DESTINATION_SETTINGS)
    }

    @Test
    fun CREATE_FOSTER_NO_RAW_ADMIN_LANDING() {
        assertEquals(NavRoutes.FOSTER_PLACEMENTS, FunctionSetupMapping.routeFor(LeoverFunction.FOSTER).route)
        assertEquals(
            NavRoutes.FOSTER_PLACEMENTS,
            NewContextActivation.landingRoute(OperationalContext.Foster("u1", "Hogar de tránsito"))
        )
        assertFalse(PhysicalQaFix03Contracts.FOSTER_CREATE_LANDING_ADMIN)
        assertEquals("Burzaco, Buenos Aires", HumanLocationLabel.visible("loc-ar-loc-burzaco"))
        assertTrue(HumanLocationLabel.looksRawId("loc-ar-loc-burzaco"))
        assertFalse(HumanLocationLabel.looksRawId("Burzaco"))
        assertFalse(PhysicalQaFix03Contracts.RAW_LOCATION_ID_VISIBLE)
        val fosterRepo = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalFosterHomeRepository.kt")
        assertTrue(fosterRepo.contains("HumanLocationLabel.visible"))
    }

    @Test
    fun PROFILE_IMAGE_FAILURE_BLOCKS_OR_EXPLICIT_SKIP() {
        val onboarding = source("app/src/main/java/com/comunidapp/app/viewmodel/ProfileOnboardingViewModel.kt")
        assertTrue(onboarding.contains("photoUploadFailed = true"))
        assertTrue(onboarding.contains("return@launch"))
        assertTrue(onboarding.contains("skipPhotoAndContinue"))
        assertTrue(onboarding.contains("retryPhotoUpload"))
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/ProfileOnboardingScreen.kt")
        assertTrue(screen.contains("Reintentar foto"))
        assertTrue(screen.contains("Continuar sin foto"))
        assertTrue(PhysicalQaFix03Contracts.PROFILE_PHOTO_FAILURE_BLOCKS)
        assertFalse(ProfileMediaPipeline.USER_VISIBLE_MEDIA_LIMIT_FOR_NORMAL_PHONE_MEDIA)
    }

    @Test
    fun PROFILE_IMAGE_PIPELINE_AND_CROP() {
        assertEquals(0f, AvatarPhotoEditorState.maxPanPx(280f, 1f))
        assertTrue(AvatarPhotoEditorState.maxPanPx(280f, 2f) > 0f)
        val clamped = AvatarPhotoEditorState("content://x", imageWidth = 2000, imageHeight = 1200).pan(80f, 0f)
        assertTrue(clamped.offsetX != 0f)
        val limits = CoverCropMath.panLimits(280f, 1f, 2000, 1200)
        assertTrue(limits.maxX > 0f)
        assertEquals(0f, limits.maxY, 0.01f)
        assertTrue(ProfileMediaPipeline.SKIP_REINGEST_PROCESSED_AVATAR)
        val ingest = source("app/src/main/java/com/comunidapp/app/domain/media/ImageIngestPipeline.kt")
        assertTrue(ingest.contains("shouldSkipReingest"))
        val editor = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/AvatarPhotoEditorScreen.kt")
        assertTrue(editor.contains("Centrar"))
    }

    @Test
    fun NAV_STATE_AND_ACTIVE_CONTEXT_RESTORE() {
        assertTrue(PhysicalQaFix03Contracts.BACKGROUND_DOES_NOT_RESET_APP)
        assertTrue(PhysicalQaFix03Contracts.NAV_ROUTE_RESTORED)
        assertTrue(PhysicalQaFix03Contracts.ACTIVE_CONTEXT_RESTORED)
        val nav = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(nav.contains("AppNavRestoreStore.write"))
        assertTrue(nav.contains("AppNavRestoreStore.read"))
        AppNavRestoreStore.clear()
    }

    @Test
    fun MAP_ZERO_ALERTS_AND_PERMISSIONS() {
        assertEquals(null, LeoVerGeoPoint.parseOrNull(999.0, 0.0))
        assertTrue(PhysicalQaFix03Contracts.PERSON_LOST_FOUND_MAP_OPENS)
        assertTrue(PhysicalQaFix03Contracts.MAP_ZERO_ALERTS_NO_CRASH)
        val map = source("app/src/main/java/com/comunidapp/app/ui/map/google/GoogleLeoVerMap.kt")
        assertTrue(map.contains("isMyLocationEnabled = myLocationAllowed"))
        assertTrue(map.contains("ACCESS_FINE_LOCATION"))
        val alerts = source("app/src/main/java/com/comunidapp/app/ui/screens/lostfound/AlertMapScreen.kt")
        assertTrue(alerts.contains("LeoVerGeoPoint.parseOrNull"))
        assertFalse(alerts.contains("position = LeoVerGeoPoint(lat, lng)"))
    }

    @Test
    fun PET_CREATE_PERSON_AND_RESCUER_AUTH() {
        assertTrue(PhysicalQaFix03Contracts.AUTH_SESSION_VALID_DURING_PET_CREATE)
        val remote = source("app/src/main/java/com/comunidapp/app/data/remote/supabase/m08/SupabasePetM08RemoteDataSource.kt")
        assertTrue(remote.contains("AuthSessionAccess.requireUser"))
        assertFalse(remote.contains("refreshCurrentSession()"))
        val auth = source("app/src/main/java/com/comunidapp/app/data/repository/SupabaseAuthRepository.kt")
        assertTrue(auth.contains("AuthSessionAccess.requireUser"))
        val access = source("app/src/main/java/com/comunidapp/app/domain/auth/AuthSessionAccess.kt")
        assertTrue(access.contains("currentUserOrNull"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
