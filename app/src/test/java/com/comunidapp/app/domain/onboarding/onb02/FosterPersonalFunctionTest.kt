package com.comunidapp.app.domain.onboarding.onb02

import com.comunidapp.app.data.local.InMemoryOnb02Store
import com.comunidapp.app.viewmodel.FosterHomeFormState
import com.comunidapp.app.viewmodel.Onb02Phase
import com.comunidapp.app.viewmodel.Onb02ViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FosterPersonalFunctionTest {

    @Test
    fun fosterIsPersonalFunction() {
        assertTrue(FunctionSetupMapping.FOSTER_IS_PERSONAL_FUNCTION)
        assertEquals(FunctionSetupKind.PERSONAL_FOSTER, FunctionSetupMapping.routeFor(LeoverFunction.FOSTER).kind)
    }

    @Test
    fun fosterDoesNotCreateOrganization() {
        assertFalse(FunctionSetupMapping.FOSTER_CREATES_ORGANIZATION)
        assertTrue(FunctionSetupMapping.neverUsesOrganizationForm(LeoverFunction.FOSTER))
        val route = FunctionSetupMapping.setupRouteAfterSelection(setOf(LeoverFunction.FOSTER), null)
        assertFalse(route.orEmpty().contains("organization", ignoreCase = true))
    }

    @Test
    fun fosterFormHasNoLegalOrganizationFields() {
        val source = File("src/main/java/com/comunidapp/app/ui/screens/foster/FosterScreens.kt").readText()
        assertFalse(source.contains("Razón social"))
        assertFalse(source.contains("Identificador público"))
        assertFalse(source.contains("Tipo de organización"))
        assertFalse(source.contains("País ISO"))
        assertTrue(source.contains("Hogar de tránsito"))
        assertTrue(source.contains("Capacidad"))
    }

    @Test
    fun fosterFormDefaultsArePersonal() {
        val form = FosterHomeFormState()
        assertEquals("Hogar de tránsito", form.displayName)
        assertTrue(form.activate)
    }

    @Test
    fun fosterSelectionPersistsInOnboardingStore() {
        val store = InMemoryOnb02Store()
        val vm = Onb02ViewModel(store = store, userIdProvider = { "user-foster" })
        vm.start(Onb02FlowKind.FULL_ONBOARDING)
        vm.skipCurrentTutorial()
        assertEquals(Onb02Phase.SELECT, vm.ui.value.phase)
        vm.toggleExtra(LeoverFunction.FOSTER)
        vm.confirmSelection()
        assertTrue(store.selection("user-foster").extras.contains(LeoverFunction.FOSTER))
    }
}
