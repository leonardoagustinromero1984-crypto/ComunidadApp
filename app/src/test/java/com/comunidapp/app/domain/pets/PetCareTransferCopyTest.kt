package com.comunidapp.app.domain.pets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetCareTransferCopyTest {

    @Test
    fun titlesAndNeverOwnerLanguage() {
        assertEquals("Transferir VitaCora", PetCareTransferCopy.SCREEN_TITLE)
        assertEquals("Solicitud de transferencia", PetCareTransferCopy.RECEIVER_TITLE)
        assertEquals("Bajo el cuidado de", PetCareTransferCopy.UNDER_THE_CARE_OF)
        val blob = listOf(
            PetCareTransferCopy.DESCRIPTION,
            PetCareTransferCopy.SHARED_ACCESS_NOTE,
            PetCareTransferCopy.SHARE_PERSONAL_MEDIA_LABEL,
            PetCareTransferCopy.HISTORY_ALWAYS_TRAVELS
        ).joinToString(" ")
        assertFalse(blob.contains("propietario", ignoreCase = true))
        assertFalse(blob.contains("dueño", ignoreCase = true))
        assertFalse(blob.contains("owner", ignoreCase = true))
        assertFalse(blob.contains("El creador original no se transfiere"))
    }

    @Test
    fun confirmAndIncomingUseRealNames() {
        val person = PetCareTransferCopy.confirmInitiate("Carolina Gómez", "Luna", false)
        assertTrue(person.contains("Carolina Gómez"))
        assertTrue(person.contains("Luna"))
        assertTrue(person.contains("perderán el acceso"))
        val org = PetCareTransferCopy.confirmInitiate("Refugio Patitas", "Luna", true)
        assertTrue(org.contains("Refugio Patitas"))
        assertFalse(org.contains("perderán el acceso"))
        val incoming = PetCareTransferCopy.incomingRequest("Carolina Gómez", "Luna")
        assertEquals("Carolina Gómez quiere transferirte el cuidado de Luna.", incoming)
        assertTrue(PetCareTransferCopy.incomingAcceptPerson("Luna").contains("bajo tu cuidado"))
        assertTrue(
            PetCareTransferCopy.incomingAcceptOrganization("Refugio Patitas", "Luna")
                .contains("Refugio Patitas")
        )
    }

    @Test
    fun profileAndHistoryLabels() {
        assertEquals(
            "Bajo el cuidado de\nCarolina Gómez",
            PetCareTransferCopy.underTheCareOf("Carolina Gómez")
        )
        assertEquals("Se creó la VitaCora de Luna.", PetCareTransferCopy.createdHistory("Luna"))
        assertEquals(
            "Luna pasó a estar bajo el cuidado de Refugio Patitas.",
            PetCareTransferCopy.transferredHistory("Luna", "Refugio Patitas")
        )
        assertEquals(
            "Fotos y videos personales anteriores:\nNo incluidos",
            PetCareTransferCopy.mediaShareLine(false)
        )
        assertEquals("Luna ahora está bajo tu cuidado.", PetCareTransferCopy.nowUnderYourCare("Luna"))
        assertEquals("Solo vos", PetCareTransferCopy.MEMORY_ONLY_YOU)
        assertEquals("Compartido con VitaCora", PetCareTransferCopy.MEMORY_SHARED)
    }
}
