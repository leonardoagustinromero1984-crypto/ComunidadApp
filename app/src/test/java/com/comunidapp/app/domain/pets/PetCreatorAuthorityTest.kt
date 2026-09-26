package com.comunidapp.app.domain.pets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetCreatorAuthorityTest {

    @Test
    fun isCreator_isHistoricalOnly() {
        assertTrue(PetCreatorAuthority.isCreator("A", "A"))
        assertFalse(PetCreatorAuthority.isCreator("B", "A"))
        assertFalse(PetCreatorAuthority.isCreator(null, "A"))
        assertFalse(PetCreatorAuthority.isCreator("A", null))
        assertFalse(PetCreatorAuthority.isCreator(" ", "A"))
    }
}
