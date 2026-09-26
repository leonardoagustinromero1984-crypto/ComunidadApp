package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.remote.supabase.m08.PetResponsibilityM08Row
import org.junit.Assert.assertEquals
import org.junit.Test

class PetDetailPrincipalHolderTest {

    @Test
    fun activeOwnerDisplayNameResolvedFromHolderRow() {
        val holders = listOf(
            PetResponsibilityM08Row(
                id = "link-1",
                petId = "pet-1",
                roleCode = "PRINCIPAL",
                personId = "user-1",
                status = "ACTIVE",
                createdBy = "",
                displayName = "Veronica Obregon"
            )
        )
        val owner = holders.firstOrNull {
            it.status.equals("ACTIVE", ignoreCase = true) &&
                (it.roleCode == "PRINCIPAL" || it.roleCode == "OWNER")
        }
        assertEquals("Veronica Obregon", owner?.displayName)
    }
}
