package com.comunidapp.app.domain

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.domain.context.OperationalContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RolePermissionsAccountTypeAuthorityTest {

    @Test
    fun accountTypeOverloadsMatchPersonalContextAndIgnoreType() {
        val personal = OperationalContext.Personal
        AccountType.entries.forEach { type ->
            assertEquals(RolePermissions.canAccessSumate(personal), RolePermissions.canAccessSumate(type))
            assertEquals(RolePermissions.canAccessComunidad(personal), RolePermissions.canAccessComunidad(type))
            assertEquals(RolePermissions.canManagePets(personal), RolePermissions.canManagePets(type))
            assertEquals(RolePermissions.canPublishAdoption(personal), RolePermissions.canPublishAdoption(type))
            assertEquals(RolePermissions.canPublishLostFound(personal), RolePermissions.canPublishLostFound(type))
            assertEquals(RolePermissions.canPublishFosterHome(personal), RolePermissions.canPublishFosterHome(type))
            assertEquals(RolePermissions.canPublishShelterNeeds(personal), RolePermissions.canPublishShelterNeeds(type))
            assertEquals(RolePermissions.canPublishEvent(personal), RolePermissions.canPublishEvent(type))
            assertEquals(RolePermissions.canPublishDonation(personal), RolePermissions.canPublishDonation(type))
            assertEquals(RolePermissions.canPublishPromo(personal), RolePermissions.canPublishPromo(type))
            assertEquals(RolePermissions.canPublishQuestion(personal), RolePermissions.canPublishQuestion(type))
            assertEquals(RolePermissions.businessPanelTitle(personal), RolePermissions.businessPanelTitle(type))
            assertEquals(
                ModulePermissions.canPublishPromo(AccountType.PERSON),
                ModulePermissions.canPublishPromo(type)
            )
            assertEquals(
                ModulePermissions.canPublishShelterNeeds(AccountType.PERSON),
                ModulePermissions.canPublishShelterNeeds(type)
            )
            assertEquals(AccountType.PERSON.defaultModules(), type.defaultModules())
            assertEquals(UserCategory.USUARIO, type.toUserCategory())
            assertNull(ServiceCategory.fromAccountType(type))
        }
        assertTrue(RolePermissions.canAccessSumate(AccountType.VET))
        assertFalse(RolePermissions.canPublishPromo(AccountType.VET))
        assertFalse(RolePermissions.canPublishShelterNeeds(AccountType.SHELTER))
        assertEquals(
            resolveActiveModules(AccountType.PERSON, null),
            resolveActiveModules(AccountType.VET, null)
        )
    }
}
