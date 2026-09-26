package com.comunidapp.app.domain.onboarding.onb02

import com.comunidapp.app.navigation.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionSetupMappingTest {

    @Test
    fun catalogIsSeparateFromActiveContexts() {
        assertTrue(FunctionSetupMapping.CATALOG_IS_NOT_ACTIVE_CONTEXT_LIST)
        assertEquals(9, FunctionSetupMapping.catalogOptions().size)
        assertTrue(FunctionSetupMapping.catalogOptions().contains(LeoverFunction.RESCUER))
        assertTrue(FunctionSetupMapping.catalogOptions().contains(LeoverFunction.FOSTER))
        assertTrue(FunctionSetupMapping.catalogOptions().contains(LeoverFunction.ORGANIZATION))
        assertFalse(FunctionSetupMapping.catalogOptions().contains(LeoverFunction.PROFILE_PERSONAL))
    }

    @Test
    fun fosterIsPersonalAndNeverOrganization() {
        assertTrue(FunctionSetupMapping.FOSTER_IS_PERSONAL_FUNCTION)
        assertFalse(FunctionSetupMapping.FOSTER_CREATES_ORGANIZATION)
        assertTrue(FunctionSetupMapping.neverUsesOrganizationForm(LeoverFunction.FOSTER))
        assertTrue(FunctionSetupMapping.neverUsesOrganizationForm(LeoverFunction.RESCUER))
        assertEquals(NavRoutes.FOSTER_PLACEMENTS, FunctionSetupMapping.routeFor(LeoverFunction.FOSTER).route)
        assertEquals(FunctionSetupKind.PERSONAL_FOSTER, FunctionSetupMapping.routeFor(LeoverFunction.FOSTER).kind)
        assertEquals(
            NavRoutes.FOSTER_PLACEMENTS,
            FunctionSetupMapping.setupRouteAfterSelection(setOf(LeoverFunction.FOSTER), null)
        )
        assertEquals(
            NavRoutes.createOrganization(),
            FunctionSetupMapping.setupRouteAfterSelection(setOf(LeoverFunction.ORGANIZATION), null)
        )
    }

    @Test
    fun organizationCategoriesDoNotIncludeFosterOrRescuer() {
        val labels = ProductOrganizationCategory.visibleInitial.map { it.visibleLabel }
        assertTrue(labels.contains("Veterinaria"))
        assertFalse(labels.contains("Refugio / ONG"))
        assertTrue(labels.contains("Tienda"))
        assertTrue(labels.contains("Guardería"))
        assertFalse(labels.contains("Otro servicio"))
        assertFalse(labels.any { it.contains("Hogar de tránsito", ignoreCase = true) })
        assertFalse(labels.any { it.contains("Rescatista", ignoreCase = true) })
    }

    @Test
    fun countryUiHiddenFixedArgentina() {
        assertTrue(FunctionSetupMapping.COUNTRY_UI_V1_HIDDEN)
        assertEquals("AR", FunctionSetupMapping.COUNTRY_INTERNAL_DEFAULT)
    }

    @Test
    fun personalFunctionsNeverOpenOrganization() {
        val extras = setOf(
            LeoverFunction.RESCUER,
            LeoverFunction.FOSTER,
            LeoverFunction.WALKER,
            LeoverFunction.VETERINARY_PROFESSIONAL
        )
        val routes = FunctionSetupMapping.setupRoutesInOrder(extras, null)
        assertFalse(routes.contains(NavRoutes.createOrganization()))
        assertEquals(NavRoutes.HOME, routes.first())
        assertEquals(
            NavRoutes.HOME,
            FunctionSetupMapping.setupRouteAfterSelection(setOf(LeoverFunction.RESCUER), null)
        )
        assertEquals(
            NavRoutes.MY_BUSINESS,
            FunctionSetupMapping.setupRouteAfterSelection(setOf(LeoverFunction.WALKER), null)
        )
        assertEquals(
            NavRoutes.MY_VETERINARY_CLINICS,
            FunctionSetupMapping.setupRouteAfterSelection(
                setOf(LeoverFunction.VETERINARY_PROFESSIONAL),
                null
            )
        )
    }

    @Test
    fun organizationIsLastWhenCombinedWithPersonalSetups() {
        val routes = FunctionSetupMapping.setupRoutesInOrder(
            extras = setOf(LeoverFunction.ORGANIZATION, LeoverFunction.FOSTER, LeoverFunction.RESCUER),
            organizationAction = OrganizationSetupAction.CREATE
        )
        assertEquals(
            listOf(NavRoutes.HOME, NavRoutes.FOSTER_PLACEMENTS, NavRoutes.createOrganization()),
            routes
        )
        assertEquals(
            NavRoutes.FOSTER_PLACEMENTS,
            FunctionSetupMapping.setupRouteAfterSelection(
                setOf(LeoverFunction.FOSTER, LeoverFunction.ORGANIZATION),
                OrganizationSetupAction.CREATE
            )
        )
    }
}
