package com.comunidapp.app.domain.onboarding.onb02

import com.comunidapp.app.data.local.InMemoryOnb02Store
import com.comunidapp.app.ui.UiRegressionGateTest
import com.comunidapp.app.viewmodel.Onb02Phase
import com.comunidapp.app.viewmodel.Onb02ViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoVerProfileActorTaxonomyContractTest {

    @Test
    fun PROFILE_ACTOR_FIRST_LEVEL_IS_EXACTLY_FIVE() {
        assertEquals(5, ProfileActorTaxonomy.FIRST_LEVEL_ACTOR_COUNT)
        assertEquals(5, ProfileActorTaxonomy.firstLevel.size)
        assertEquals(
            listOf(
                ProfileActorKind.PERSON,
                ProfileActorKind.INDEPENDENT_RESCUER,
                ProfileActorKind.REFUGE,
                ProfileActorKind.INDEPENDENT_PROFESSIONAL,
                ProfileActorKind.BUSINESS
            ),
            ProfileActorTaxonomy.firstLevel
        )
        val labels = ProfileActorTaxonomy.firstLevelLabels()
        assertTrue(labels.contains("Persona"))
        assertTrue(labels.contains("Rescatista independiente"))
        assertTrue(labels.contains("Refugio / Organización de rescate"))
        assertTrue(labels.contains("Profesional independiente"))
        assertTrue(labels.contains("Organización / Negocio"))
    }

    @Test
    fun SERVICE_CATEGORIES_ARE_NOT_IN_FIRST_LEVEL() {
        assertFalse(ProfileActorTaxonomy.SERVICE_CATEGORIES_IN_FIRST_LEVEL)
        val labels = ProfileActorTaxonomy.firstLevelLabels()
        ProfileActorTaxonomy.forbiddenFirstLevelLabels().forEach { banned ->
            assertFalse("First level must not include $banned", labels.contains(banned))
        }
        val selector = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/onb02/Onb02Screens.kt")
        assertTrue(selector.contains("ProfileActorTaxonomy.firstLevel"))
        assertFalse(selector.contains("Onb02Planner.extraOptions()"))
        assertTrue(selector.contains("locked = personLocked"))
        assertTrue(selector.contains("ProfileActorKind.PERSON"))
    }

    @Test
    fun REFUGE_IS_GENERAL_NOT_COMMERCIAL() {
        assertTrue(ProfileActorTaxonomy.REFUGE_GENERAL_SELECTOR)
        assertFalse(ProfileActorTaxonomy.REFUGE_COMMERCIAL_SELECTOR)
        val refuge = ProfileActorTaxonomy.applyActor(ProfileActorKind.REFUGE)
        assertEquals(setOf(LeoverFunction.ORGANIZATION), refuge.extras)
        assertEquals(OrganizationKindOption.SHELTER, refuge.organizationKind)
        assertFalse(ProfileActorTaxonomy.showCommercialKindPicker(refuge))
        assertFalse(OrganizationKindOption.commercial.contains(OrganizationKindOption.SHELTER))
    }

    @Test
    fun PROFESSIONAL_AND_BUSINESS_CATEGORIES() {
        val professional = IndependentProfessionalSpecialty.entries.map { it.visibleLabel }
        assertTrue(professional.contains("Paseador / Cuidador"))
        assertTrue(professional.contains("Peluquero/a de mascotas"))
        assertTrue(professional.contains("Adiestrador/a"))
        assertTrue(professional.contains("Veterinario/a profesional"))
        assertEquals(4, professional.size)

        val business = ProfileActorTaxonomy.businessKinds.map {
            ProfileActorTaxonomy.businessVisibleLabel(it)
        }
        assertEquals(
            listOf(
                "Veterinaria / Clínica veterinaria",
                "Tienda de mascotas",
                "Guardería / Hospedaje",
                "Peluquería",
                "Paseos / Cuidado",
                "Adiestramiento",
                "Marca",
                "Lugar pet friendly"
            ),
            business
        )
        assertFalse(ProfileActorTaxonomy.INDEPENDENT_BOARDING_DUPLICATE)
        assertFalse(business.contains("Guardería independiente"))
        assertEquals(
            setOf(LeoverFunction.WALKER),
            IndependentProfessionalSpecialty.WALKER_CAREGIVER.extras()
        )
        assertEquals(
            setOf(LeoverFunction.GROOMING),
            IndependentProfessionalSpecialty.GROOMER.extras()
        )
        assertTrue(FunctionSetupMapping.neverUsesOrganizationForm(LeoverFunction.GROOMING))
        assertTrue(FunctionSetupMapping.neverUsesOrganizationForm(LeoverFunction.VETERINARY_PROFESSIONAL))
        val vmSource = source("app/src/main/java/com/comunidapp/app/viewmodel/Onb02ViewModel.kt")
        assertTrue(vmSource.contains("PersonCapabilityCode.RESCUER"))
    }

    @Test
    fun PROFILE_EXPLANATION_SLIDE_IS_LAST_T00_BEFORE_SELECTOR() {
        val steps = TutorialCatalog.definition(TutorialId.T00_MULTI_FUNCTION_INTRO).steps
        val last = steps.last()
        assertEquals(Onb02Copy.PROFILE_EXPLANATION_SLIDE_TITLE, last.title)
        assertTrue(Onb02Planner.shouldShowT00BeforeSelector(Onb02FlowKind.FULL_ONBOARDING))
        assertTrue(last.body.contains("Persona"))
        assertTrue(last.body.contains("Rescatista independiente"))
        assertTrue(last.body.contains("Refugio / Organización de rescate"))
        assertTrue(last.body.contains("Profesional independiente"))
        assertTrue(last.body.contains("Organización / Negocio"))
        assertTrue(last.highlight.orEmpty().contains("agregar otros perfiles"))
        assertEquals("Empezar", last.primaryCta)
        assertEquals("Siguiente", steps[steps.lastIndex - 1].primaryCta)
    }

    @Test
    fun FLOW_PROFESSIONAL_WALKER_CAREGIVER() {
        val store = InMemoryOnb02Store()
        val vm = Onb02ViewModel(store = store, userIdProvider = { "u" })
        vm.start(Onb02FlowKind.FULL_ONBOARDING)
        assertEquals(Onb02Phase.INTRO, vm.ui.value.phase)
        assertEquals(TutorialId.T00_MULTI_FUNCTION_INTRO, vm.ui.value.currentTutorial?.id)
        vm.skipCurrentTutorial()
        assertEquals(Onb02Phase.SELECT, vm.ui.value.phase)
        vm.selectActor(ProfileActorKind.INDEPENDENT_PROFESSIONAL)
        vm.confirmActor()
        assertEquals(Onb02Phase.PROFESSIONAL_SETUP, vm.ui.value.phase)
        vm.selectProfessionalSpecialty(IndependentProfessionalSpecialty.WALKER_CAREGIVER)
        vm.confirmProfessionalSpecialty()
        assertTrue(vm.ui.value.queue.contains(TutorialId.T05_WALKER))
        assertFalse(vm.ui.value.queue.contains(TutorialId.T10_ORGANIZATION))
        assertEquals(setOf(LeoverFunction.WALKER), vm.ui.value.selection.extras)
        assertTrue(FunctionSetupMapping.neverUsesOrganizationForm(LeoverFunction.WALKER))
    }

    @Test
    fun FLOW_BUSINESS_GROOMING() {
        val store = InMemoryOnb02Store()
        val vm = Onb02ViewModel(store = store, userIdProvider = { "u" })
        vm.start(Onb02FlowKind.FULL_ONBOARDING)
        vm.skipCurrentTutorial()
        vm.selectActor(ProfileActorKind.BUSINESS)
        vm.confirmActor()
        assertEquals(Onb02Phase.BUSINESS_SETUP, vm.ui.value.phase)
        vm.selectBusinessKind(OrganizationKindOption.GROOMING)
        vm.confirmBusinessKind()
        assertEquals(Onb02Phase.ORG_SETUP, vm.ui.value.phase)
        assertEquals(OrganizationKindOption.GROOMING, vm.ui.value.selection.organizationKind)
        assertFalse(ProfileActorTaxonomy.showCommercialKindPicker(vm.ui.value.selection))
        assertEquals(setOf(LeoverFunction.ORGANIZATION), vm.ui.value.selection.extras)
    }

    @Test
    fun FLOW_REFUGE_SKIPS_COMMERCIAL_SELECTOR() {
        val store = InMemoryOnb02Store()
        val vm = Onb02ViewModel(store = store, userIdProvider = { "u" })
        vm.start(Onb02FlowKind.FULL_ONBOARDING)
        vm.skipCurrentTutorial()
        vm.selectActor(ProfileActorKind.REFUGE)
        vm.confirmActor()
        assertEquals(Onb02Phase.ORG_SETUP, vm.ui.value.phase)
        assertEquals(OrganizationKindOption.SHELTER, vm.ui.value.selection.organizationKind)
        assertFalse(ProfileActorTaxonomy.showCommercialKindPicker(vm.ui.value.selection))
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/onboarding/onb02/Onb02Screens.kt")
        assertTrue(screen.contains("showCommercialKinds"))
    }

    @Test
    fun TUTORIAL_PAGER_AND_GOOGLE_IMPORT_MUSIC_UNTOUCHED() {
        val pager = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoVerTutorialPager.kt")
        assertTrue(pager.contains("fun LeoVerTutorialPager("))
        assertTrue(pager.contains("HorizontalPager("))
        val login = source("app/src/main/java/com/comunidapp/app/ui/screens/login/LoginScreen.kt")
        assertTrue(login.contains("ContinueWithGoogleButton"))
        val importEngine = source(
            "app/src/main/java/com/comunidapp/app/domain/vitacora/import/VitacoraImportEngine.kt"
        )
        assertTrue(importEngine.contains("class InMemoryVitacoraImportEngine"))
        val music = source("app/src/main/java/com/comunidapp/app/domain/social/LeoVerMusic.kt")
        val stickers = source("app/src/main/java/com/comunidapp/app/domain/social/LeoVerStickers.kt")
        assertTrue(music.contains("LeoVerMusicRecents"))
        assertTrue(stickers.contains("LeoVerStickerCatalog"))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
