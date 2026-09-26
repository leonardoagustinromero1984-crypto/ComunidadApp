package com.comunidapp.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CanonicalCareIncomingGuardTest {

    @Test
    fun repositoryListsIncomingPendingTransfers() {
        val repo = File(
            "src/main/java/com/comunidapp/app/data/repository/CanonicalCareTransferRepository.kt"
        ).readText()
        assertTrue(repo.contains("RPC_LIST_INCOMING_CARE_TRANSFERS"))
        assertTrue(repo.contains("override suspend fun listIncoming"))
        val inbox = File(
            "src/main/java/com/comunidapp/app/domain/pets/IncomingCareTransferInbox.kt"
        ).readText()
        assertTrue(inbox.contains("INCOMING_RPC_FAIL"))
        assertTrue(inbox.contains("keepPrevious"))
    }

    @Test
    fun myPetsAndNotificationsSurfaceIncoming() {
        val pets = File("src/main/java/com/comunidapp/app/ui/screens/pets/MyPetsScreen.kt").readText()
        val notes = File(
            "src/main/java/com/comunidapp/app/ui/screens/profile/NotificationsScreen.kt"
        ).readText()
        val profile = File(
            "src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt"
        ).readText()
        assertTrue(pets.contains("incomingTransfers"))
        assertTrue(pets.contains("onOpenIncomingTransfer"))
        assertTrue(notes.contains("incomingTransfers"))
        assertTrue(notes.contains("onOpenCareTransfer"))
        assertTrue(profile.contains("incomingCareTransferInbox"))
        assertTrue(profile.contains("IncomingCareTransfersSection"))
        assertTrue(profile.contains("IncomingCareTransferCard"))
        assertFalse(
            profile.substringAfter("IncomingCareTransfersSection")
                .substringBefore("PetResponsibleInvitesSection")
                .contains("observePets()")
        )
    }
}
