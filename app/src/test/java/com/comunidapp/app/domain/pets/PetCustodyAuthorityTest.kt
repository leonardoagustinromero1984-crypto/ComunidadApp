package com.comunidapp.app.domain.pets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetCustodyAuthorityTest {

    @Test
    fun currentPersonCustodian_canAdminister() {
        assertTrue(PetCustodyAuthority.isCurrentPersonCustodian("B", "PERSON", "B"))
        assertTrue(PetCustodyAuthority.canArchive("B", "PERSON", "B"))
        assertTrue(PetCustodyAuthority.canManageResponsibilities("B", "PERSON", "B"))
        assertTrue(PetCustodyAuthority.canMarkDeceased("B", "PERSON", "B"))
        assertTrue(PetCustodyAuthority.canInitiateTransfer("B", "PERSON", "B"))
    }

    @Test
    fun originalCreator_withoutCustody_cannotAdminister() {
        assertFalse(PetCustodyAuthority.isCurrentPersonCustodian("A", "PERSON", "B"))
        assertFalse(PetCustodyAuthority.canArchive("A", "PERSON", "B"))
        assertFalse(PetCustodyAuthority.canInitiateTransfer("A", "PERSON", "B"))
    }

    @Test
    fun sharedResponsible_cannotTransfer() {
        assertFalse(PetCustodyAuthority.canResponsibleTransfer())
        assertFalse(PetCustodyAuthority.canInitiateTransfer("R1", "PERSON", "A"))
    }

    @Test
    fun organizationCustodian_isNotPersonOperatorOnClient() {
        assertFalse(PetCustodyAuthority.isCurrentPersonCustodian("A", "ORGANIZATION", "A"))
        assertFalse(PetCustodyAuthority.canInitiateTransfer("A", "ORGANIZATION", "A"))
    }
}
