package com.comunidapp.app.ui.screens.pets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TransferUxFollowUpGuardsTest {

    @Test
    fun profileReadsIncomingInboxNotPetsFeed() {
        val profile = File("src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt").readText()
        assertTrue(profile.contains("incomingCareTransferInbox"))
        assertTrue(profile.contains("IncomingCareTransfersSection"))
        assertTrue(profile.contains("IncomingCareTransferCard"))
        assertTrue(profile.contains("incomingInbox.refresh(\"profile_resume\")"))
        assertTrue(profile.contains("incomingTransfers.isEmpty()"))
        assertFalse(profile.contains("petRepository.observePets()"))
    }

    @Test
    fun acceptNavigatesToProfileMyPets() {
        val graph = File("src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt").readText()
        val vm = File("src/main/java/com/comunidapp/app/viewmodel/PetTransfersViewModel.kt").readText()
        assertTrue(graph.contains("navigateToProfileMyPets"))
        assertTrue(graph.contains("onAccepted = { navigateToProfileMyPets(navController) }"))
        assertTrue(graph.contains("NavRoutes.MY_PETS"))
        assertTrue(vm.contains("acceptedNavigateToMyPets = true"))
        assertTrue(vm.contains("navigateToMyPetsOnSuccess"))
        assertTrue(vm.contains("incomingInbox?.remove(transferId)"))
    }

    @Test
    fun personMyPetsDoesNotShowNeedsPhotoSection() {
        val pets = File("src/main/java/com/comunidapp/app/ui/screens/pets/MyPetsScreen.kt").readText()
        val importBlock = pets.substringAfter("if (showImportTools) {")
        assertTrue(importBlock.contains("Necesitan foto"))
        val beforeImport = pets.substringBefore("if (showImportTools) {")
        assertFalse(beforeImport.contains("Necesitan foto"))
    }

    @Test
    fun transferScreenHidesCompletedHistoryRows() {
        val screen = File("src/main/java/com/comunidapp/app/ui/screens/pets/PetTransfersScreen.kt").readText()
        assertTrue(screen.contains("PENDING only"))
        assertFalse(screen.contains("text = \"Historial\""))
        assertFalse(screen.contains("state.history.forEach"))
        assertFalse(screen.contains("Sin transferencias anteriores."))
    }
}
