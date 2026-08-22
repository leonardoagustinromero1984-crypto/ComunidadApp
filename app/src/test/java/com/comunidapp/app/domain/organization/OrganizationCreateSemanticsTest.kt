package com.comunidapp.app.domain.organization

import com.comunidapp.app.domain.onboarding.onb02.ProductOrganizationCategory
import com.comunidapp.app.domain.ux.CanonicalUiErrorMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OrganizationCreateSemanticsTest {

    @Test
    fun createOrgMapsProductCategoriesToCanonicalCapabilities() {
        assertEquals("VETERINARY_CLINIC", ProductOrganizationCategory.VETERINARY.capability)
        assertEquals("SHELTER", ProductOrganizationCategory.SHELTER_NGO.capability)
        assertEquals("PROVIDER", ProductOrganizationCategory.SHOP.capability)
        assertEquals("DAYCARE", ProductOrganizationCategory.DAYCARE.capability)
        assertEquals("OTHER", ProductOrganizationCategory.OTHER_SERVICE.capability)
    }

    @Test
    fun createScreenHidesCountryIsoAndLegacyTypes() {
        val source = File("src/main/java/com/comunidapp/app/ui/screens/organization/CreateOrganizationScreen.kt").readText()
        assertFalse(source.contains("País ISO"))
        assertFalse(source.contains("Grupo de rescate"))
        assertFalse(source.contains("Agencia de paseadores"))
        assertTrue(source.contains("ProductOrganizationCategory"))
        assertFalse(source.contains("Razón social"))
        assertFalse(source.contains("Identificador público"))
    }

    @Test
    fun rawDatabaseErrorNotShown() {
        val shown = CanonicalUiErrorMapper.userMessage(
            "ERROR: duplicate key value violates unique constraint \"organizations_slug_uidx\" (SQLSTATE 23505)"
        )
        assertFalse(CanonicalUiErrorMapper.isUnsafeToShow(shown).not() && shown.contains("SQLSTATE"))
        assertFalse(shown.contains("duplicate key", ignoreCase = true))
        assertEquals(CanonicalUiErrorMapper.DUPLICATE_PUBLIC_IDENTIFIER, shown)
    }

    @Test
    fun retryAfterSuccessCopyDoesNotLookLikeFailure() {
        assertEquals(
            CanonicalUiErrorMapper.ALREADY_EXISTS_ORG,
            CanonicalUiErrorMapper.userMessage("ORGANIZATION_ALREADY_EXISTS_FOR_REQUEST")
        )
    }
}
