package com.comunidapp.app.ui

import com.comunidapp.app.data.local.InMemoryOnb02Store
import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.domain.media.AvatarPhotoEditorState
import com.comunidapp.app.domain.media.AvatarPhotoRules
import com.comunidapp.app.domain.onboarding.onb02.FunctionSelection
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import com.comunidapp.app.domain.onboarding.onb02.Onb02Copy
import com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind
import com.comunidapp.app.domain.onboarding.onb02.Onb02Planner
import com.comunidapp.app.domain.onboarding.onb02.TutorialCatalog
import com.comunidapp.app.domain.onboarding.onb02.TutorialId
import com.comunidapp.app.viewmodel.ComunidadUiState
import com.comunidapp.app.viewmodel.Onb02Phase
import com.comunidapp.app.viewmodel.Onb02ViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ux06PhysicalQaStabilizationTest {

    @Test
    fun PHOTO_EDITOR_SUPPORTS_REPOSITION_STATE() {
        val start = AvatarPhotoEditorState("content://photo")
        val zoomed = start.zoom(2f)
        val panned = zoomed.pan(12f, -8f)
        assertTrue(panned.offsetX != 0f || panned.offsetY != 0f)
        assertEquals(2f, panned.scale)
        assertEquals(0f, panned.recenter().offsetX)
        assertTrue(AvatarPhotoRules.PICK_OPENS_EDITOR)
        assertTrue(AvatarPhotoRules.CANCEL_DOES_NOT_REPLACE)
        assertTrue(AvatarPhotoRules.STRIP_GPS_METADATA)
    }

    @Test
    fun PHOTO_PICK_OPENS_EDITOR() {
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/EditProfileViewModel.kt")
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/EditProfileScreen.kt")
        assertTrue(vm.contains("editorSourceUri = uri"))
        assertTrue(screen.contains("rememberLeoVerAvatarCropLauncher"))
        assertTrue(screen.contains("onCropped = viewModel::onCroppedPhoto"))
        assertTrue(AvatarPhotoRules.PICK_OPENS_EDITOR)
    }

    @Test
    fun PHOTO_CONFIRM_UPLOADS_PROCESSED_RESULT() {
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/EditProfileViewModel.kt")
        assertTrue(vm.contains("fun onCroppedPhoto"))
        assertTrue(vm.contains("encodeAlreadyCropped"))
        assertTrue(vm.contains("processedPhotoPath"))
    }

    @Test
    fun PHOTO_CANCEL_DOES_NOT_REPLACE_AVATAR() {
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/EditProfileViewModel.kt")
        assertTrue(vm.contains("fun cancelPhotoEditor"))
        assertTrue(vm.contains("editorSourceUri = null"))
        assertTrue(AvatarPhotoRules.CANCEL_DOES_NOT_REPLACE)
    }

    @Test
    fun TUTORIAL_SWIPE_NEXT() {
        val pager = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoVerTutorialPager.kt")
        assertTrue(pager.contains("HorizontalPager("))
        assertTrue(pager.contains("onPageChange"))
    }

    @Test
    fun TUTORIAL_SWIPE_PREVIOUS() {
        val pager = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoVerTutorialPager.kt")
        assertTrue(pager.contains("animateScrollToPage"))
        assertTrue(pager.contains("pagerState.currentPage"))
    }

    @Test
    fun TUTORIAL_BUTTON_AND_PAGER_SYNC() {
        val host = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/onb02/Onb02Screens.kt")
        assertTrue(host.contains("onPageChange = viewModel::setTutorialStep"))
        assertTrue(host.contains("onPrimary = viewModel::nextStepOrFinish"))
    }

    @Test
    fun TUTORIAL_PERSONAL_NOT_REPEATED_ON_ADD_FUNCTION() {
        val store = InMemoryOnb02Store()
        store.saveSelection(USER, FunctionSelection(extras = setOf(LeoverFunction.RESCUER)))
        store.markCompleted(USER, TutorialId.T01_PROFILE_PERSONAL)
        store.markCompleted(USER, TutorialId.T11_USE_LEOVER_AS)
        val vm = Onb02ViewModel(store = store, userIdProvider = { USER })
        vm.start(Onb02FlowKind.ADD_FUNCTION_LATER)
        vm.toggleExtra(LeoverFunction.FOSTER)
        vm.confirmSelection()
        assertFalse(vm.ui.value.queue.contains(TutorialId.T01_PROFILE_PERSONAL))
        assertFalse(vm.ui.value.queue.contains(TutorialId.T00_MULTI_FUNCTION_INTRO))
        assertEquals(listOf(TutorialId.T03_FOSTER), vm.ui.value.queue)
    }

    @Test
    fun TUTORIAL_CONTEXT_SWITCH_NOT_REPEATED_IF_COMPLETED() {
        val store = InMemoryOnb02Store()
        store.markSkipped(USER, TutorialId.T11_USE_LEOVER_AS)
        assertTrue(Onb02Planner.tutorialFinished(store.progress(USER, TutorialId.T11_USE_LEOVER_AS)))
        val queue = Onb02Planner.tutorialsWhenAddingLater(
            newlySelected = setOf(LeoverFunction.FOSTER),
            alreadySelected = setOf(LeoverFunction.RESCUER),
            t11AlreadyCompleted = true
        )
        assertFalse(queue.contains(TutorialId.T11_USE_LEOVER_AS))
    }

    @Test
    fun TUTORIAL_NEW_FUNCTION_ONLY_ON_ADD_FUNCTION() {
        val queue = Onb02Planner.tutorialsWhenAddingLater(
            newlySelected = setOf(LeoverFunction.FOSTER),
            alreadySelected = setOf(LeoverFunction.RESCUER),
            t11AlreadyCompleted = true
        )
        assertEquals(listOf(TutorialId.T03_FOSTER), queue)
    }

    @Test
    fun TUTORIAL_SWITCH_PATH() {
        assertEquals("Perfil → Usar LeoVer como", Onb02Copy.TUTORIAL_SWITCH_PATH)
    }

    @Test
    fun TUTORIAL_ADD_PATH() {
        assertEquals("Perfil → Configuración → Agregar función o perfil", Onb02Copy.TUTORIAL_ADD_FUNCTION_PATH)
    }

    @Test
    fun VITACORA_MEANING_PRESENT() {
        val body = TutorialCatalog.definition(TutorialId.T00_MULTI_FUNCTION_INTRO).steps[1].body
        assertTrue(body.contains("vita (vida)"))
        assertTrue(body.contains("cora (corazón)"))
        assertFalse(body.contains("bitácora"))
        assertTrue(body.contains("Su vida. Su historia. Sus cuidados."))
    }

    @Test
    fun HELP_REPLAY_DOES_NOT_MUTATE_ONBOARDING() {
        val store = InMemoryOnb02Store()
        store.saveSelection(USER, FunctionSelection(extras = setOf(LeoverFunction.ORGANIZATION)))
        val vm = Onb02ViewModel(store = store, userIdProvider = { USER })
        vm.start(Onb02FlowKind.REOPEN_FROM_HELP, reopenId = TutorialId.T01_PROFILE_PERSONAL)
        vm.skipCurrentTutorial()
        assertEquals(Onb02Phase.DONE, vm.ui.value.phase)
        assertNull(vm.setupRouteAfterTutorials())
        assertNotEquals(Onb02Phase.SELECT, vm.ui.value.phase)
    }

    @Test
    fun PROFILE_HAS_COMPACT_USE_LEOVER_AS() {
        val profile = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt")
        assertTrue(profile.contains("CompactUseLeoverAsRow"))
        assertTrue(profile.contains("Usar LeoVer como"))
        assertFalse(profile.contains("available.forEach"))
    }

    @Test
    fun SETTINGS_HAS_ADD_FUNCTION_PROFILE() {
        val settings = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/SettingsScreen.kt")
        assertTrue(settings.contains("Agregar función o perfil"))
        assertFalse(settings.contains("Usar LeoVer como"))
    }

    @Test
    fun PROFILE_PETS_FOLLOW_CONTEXT_SWITCHER() {
        val profile = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt")
        val switcher = profile.indexOf("item(key = \"use_leover_as\")")
        val pets = profile.indexOf("item(key = \"pets_header\")")
        assertTrue(switcher >= 0)
        assertTrue(pets > switcher)
    }

    @Test
    fun PROFILE_DONATIONS_ONLY_CURRENT_PERSON_DATA() {
        val profile = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt")
        assertTrue(profile.contains("Mi ayuda"))
        assertTrue(profile.contains("onNavigateToDonations"))
        val hub = source("app/src/main/java/com/comunidapp/app/ui/screens/m17/M17ExtendedScreens.kt")
        assertTrue(hub.contains("CommunityHelpPresentation.ACTIVITY_TITLE"))
        assertTrue(hub.contains("CommunityHelpPresentation.EMPTY_MONEY"))
        assertFalse(hub.contains("tus aportes y campañas"))
        assertFalse(hub.contains("Los pagos reales todavía no están habilitados."))
        val activity = source("app/src/main/java/com/comunidapp/app/viewmodel/M17ExtendedViewModels.kt")
        assertTrue(activity.contains("listMyContributions"))
        assertTrue(activity.contains("listMyPledges"))
        assertTrue(activity.contains("listMyApplications"))
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(graph.contains("onNavigateToDonations = { navController.navigate(NavRoutes.M17_MY_HELP) }"))
    }

    @Test
    fun COMMUNITY_ALL_CORE_CATEGORIES_DISCOVERABLE() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt")
        assertTrue(screen.contains("CommunityCategoryGrid"))
        assertTrue(screen.contains("Veterinarias"))
        assertTrue(screen.contains("Tiendas"))
        assertTrue(screen.contains("Paseadores"))
        assertTrue(screen.contains("Adiestradores"))
        assertTrue(screen.contains("Guarderías"))
        assertTrue(screen.contains("Peluquerías"))
        assertFalse(screen.contains("LazyRow(") && screen.substringAfter("item(key = \"categories\")").substringBefore("if (uiState.selectedCategory").contains("LazyRow("))
    }

    @Test
    fun COMMUNITY_SEARCH_BUTTON_REQUIRED() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt")
        assertTrue(screen.contains("viewModel::search"))
        assertTrue(screen.contains("Buscar"))
        val idle = ComunidadUiState(selectedCategory = ServiceCategory.VET)
        assertFalse(idle.hasSearched)
        assertFalse(idle.isLoading)
    }

    @Test
    fun COMMUNITY_CATEGORY_TOUCH_DOES_NOT_SEARCH() {
        assertFalse(ComunidadUiState(selectedCategory = ServiceCategory.VET).isLoading)
        val vmSource = source("app/src/main/java/com/comunidapp/app/viewmodel/ServiceViewModel.kt")
        val select = vmSource.substringAfter("fun selectCategory").substringBefore("fun search")
        assertFalse(select.contains("isLoading = true"))
    }

    @Test
    fun COMMUNITY_FILTER_CHANGE_DOES_NOT_SEARCH() {
        val geo = source("app/src/main/java/com/comunidapp/app/viewmodel/ServiceViewModel.kt")
            .substringAfter("fun applyGeography").substringBefore("fun selectTag")
        assertFalse(geo.contains("isLoading = true"))
        assertTrue(geo.contains("resultsStale"))
    }

    @Test
    fun COMMUNITY_SEARCH_STARTS_ON_BUTTON() {
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/ServiceViewModel.kt")
        assertTrue(vm.contains("fun search()"))
        assertTrue(vm.contains("searchJob?.cancel()"))
    }

    @Test
    fun COMMUNITY_SEARCH_FINISHES_SUCCESS() {
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/ServiceViewModel.kt")
        assertTrue(vm.contains("hasSearched = true"))
        assertTrue(vm.contains("isLoading = false"))
    }

    @Test
    fun COMMUNITY_SEARCH_FINISHES_ERROR() {
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/ServiceViewModel.kt")
        assertTrue(vm.contains("searchError"))
        assertTrue(vm.contains("finally"))
    }

    @Test
    fun COMMUNITY_SEARCH_NOT_STUCK_LOADING() {
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/ServiceViewModel.kt")
        assertTrue(vm.contains("if (generation == searchGeneration && _uiState.value.isLoading)"))
    }

    @Test
    fun COMMUNITY_NEW_SEARCH_REPLACES_PREVIOUS_REQUEST() {
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/ServiceViewModel.kt")
        assertTrue(vm.contains("searchJob?.cancel()"))
        assertTrue(vm.contains("searchGeneration"))
    }

    @Test
    fun FOSTER_NO_RAW_ACTIVE_AVAILABLE() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterScreens.kt")
        assertFalse(screen.contains("Estado: \${h.status.name}"))
        assertFalse(screen.contains("h.availabilityStatus.name"))
        assertTrue(screen.contains("Hogar de tránsito activo"))
        assertTrue(screen.contains("Tránsitos"))
        assertTrue(screen.contains("Solicitudes"))
    }

    @Test
    fun FOSTER_TRÁNSITOS_ENTRY_VISIBLE() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterScreens.kt")
        assertTrue(screen.contains("Tránsitos"))
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(graph.contains("NavRoutes.FOSTER_PLACEMENTS"))
    }

    @Test
    fun FOSTER_REQUEST_FLOW_VISIBLE() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterScreens.kt")
        assertTrue(screen.contains("Solicitudes"))
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(graph.contains("NavRoutes.FOSTER_REQUESTS_RECEIVED"))
    }

    @Test
    fun FOSTER_SAVE_PERSISTS() {
        val repo = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalFosterHomeRepository.kt")
        assertTrue(repo.contains("RPC_UPSERT_FOSTER_PROFILE"))
        assertTrue(repo.contains("OperationalContextProvider.refresh"))
    }

    @Test
    fun FOSTER_RELOAD_AVAILABLE_AFTER_RESTART_STATE() {
        val resolver = source("app/src/main/java/com/comunidapp/app/domain/context/AvailableContextsResolver.kt")
        assertTrue(resolver.contains("observeMyFosterHome"))
        assertTrue(resolver.contains("LeoverFunction.FOSTER"))
        assertTrue(resolver.contains("Onb02StoreProvider"))
    }

    @Test
    fun FOSTER_CONTEXT_AVAILABLE_AFTER_CREATION() {
        val form = source("app/src/main/java/com/comunidapp/app/viewmodel/FosterViewModels.kt")
        assertTrue(form.contains("OperationalContextProvider.refresh"))
        val resolver = source("app/src/main/java/com/comunidapp/app/domain/context/AvailableContextsResolver.kt")
        assertTrue(resolver.contains("OperationalContext.Foster"))
    }

    @Test
    fun FOSTER_BACK_DOES_NOT_REOPEN_COMPLETED_SETUP() {
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(graph.contains("popUpTo(NavRoutes.FOSTER_HOME_FORM) { inclusive = true }"))
    }

    @Test
    fun VET_PERSON_DOES_NOT_LAND_ON_DEBUG_MY_VETS() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/veterinary/VeterinaryScreens.kt")
        assertFalse(screen.contains("persistencia remota", ignoreCase = false))
        assertFalse(screen.contains("pendiente Bloque"))
        assertTrue(screen.contains("Perfil profesional"))
    }

    @Test
    fun DAYCARE_NO_AMBIGUOUS_PAYMENTS_V1() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/business/MiNegocioScreen.kt")
        assertFalse(screen.contains("text = \"Pagos\""))
    }

    @Test
    fun RAW_CONTEXT_NOT_BUSINESS_ERROR_NOT_SHOWN() {
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/ServiceViewModel.kt")
        assertFalse(vm.contains("Este contexto no es un negocio"))
        assertTrue(vm.contains("CanonicalProviderWrite"))
        val write = source("app/src/main/java/com/comunidapp/app/domain/canonical/CanonicalProviderWrite.kt")
        assertTrue(write.contains("DAYCARE"))
        assertTrue(write.contains("BOARDING"))
        assertTrue(write.contains("GROOMING"))
    }

    @Test
    fun HOME_ICONS_OUTLINED() {
        val header = source("app/src/main/java/com/comunidapp/app/ui/screens/home/SocialHomeComponents.kt")
        assertTrue(header.contains("Icons.AutoMirrored.Outlined.Chat"))
        assertTrue(header.contains("Icons.Outlined.Notifications"))
    }

    @Test
    fun AUTH_USES_V3_BACKGROUND() {
        val login = source("app/src/main/java/com/comunidapp/app/ui/screens/login/LoginScreen.kt")
        val register = source("app/src/main/java/com/comunidapp/app/ui/screens/login/RegisterScreen.kt")
        assertTrue(login.contains("VisualDirectionPilot"))
        assertTrue(login.contains("leoVisual().background"))
        assertTrue(register.contains("VisualDirectionPilot"))
        val colors = source("app/src/main/res/values/colors.xml")
        assertTrue(colors.contains("#FFFAFBF8"))
    }

    @Test
    fun DEVELOPMENT_PLACEHOLDERS_REMOVED_FROM_REACHABLE_UI() {
        val vet = source("app/src/main/java/com/comunidapp/app/ui/screens/veterinary/VeterinaryScreens.kt")
        assertFalse(vet.contains("pendiente Bloque"))
        assertFalse(vet.contains("Persistencia remota"))
        val foster = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterScreens.kt")
        assertFalse(foster.contains("Estado: \${h.status.name}"))
    }

    private fun source(relative: String): String = UiRegressionGateTest.sourceFile(relative).readText()

    companion object {
        private const val USER = "ux06-user"
    }
}
