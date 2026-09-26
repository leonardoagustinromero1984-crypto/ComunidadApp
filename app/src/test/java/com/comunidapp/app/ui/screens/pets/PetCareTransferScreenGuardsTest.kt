package com.comunidapp.app.ui.screens.pets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PetCareTransferScreenGuardsTest {

    @Test
    fun transferScreen_usesCanonicalCareCopy() {
        val screen = File("src/main/java/com/comunidapp/app/ui/screens/pets/PetTransfersScreen.kt").readText()
        val detail = File("src/main/java/com/comunidapp/app/ui/screens/pets/PetDetailScreen.kt").readText()
        val responsibilities =
            File("src/main/java/com/comunidapp/app/ui/screens/pets/PetResponsibilitiesScreen.kt")
                .readText()
        val provider = File("src/main/java/com/comunidapp/app/data/provider/DataProvider.kt").readText()
        assertTrue(screen.contains("PetCareTransferCopy.SCREEN_TITLE"))
        assertTrue(screen.contains("SHARE_PERSONAL_MEDIA_LABEL"))
        assertTrue(screen.contains("setSharePersonalMedia"))
        assertFalse(screen.contains("El creador original no se transfiere"))
        assertFalse(screen.contains("Responsabilidad compartida"))
        assertTrue(detail.contains("PetCareTransferCopy.UNDER_THE_CARE_OF"))
        assertFalse(detail.contains("Responsable:"))
        assertTrue(detail.contains("showTransfer = access?.canInitiateTransfer == true"))
        assertTrue(detail.contains("if (showTransfer)"))
        assertTrue(detail.contains("access?.canAcceptTransfer == true"))
        assertTrue(detail.contains("PetCareTransferCopy.RECEIVER_TITLE"))
        assertTrue(screen.contains("shouldLeaveUnauthorized()"))
        assertTrue(screen.contains("onNavigateBack()"))
        assertTrue(screen.contains("onAccepted"))
        assertTrue(screen.contains("acceptedNavigateToMyPets"))
        assertTrue(screen.contains("wrapContentWidth()"))
        assertTrue(screen.contains("Quitar"))
        assertTrue(screen.contains("title = \"No pudimos cargar las transferencias\""))
        assertTrue(provider.contains("useSupabase -> CanonicalCareTransferRepository()"))
        assertTrue(responsibilities.contains("maxLines = 2"))
        assertTrue(responsibilities.contains("modifier = Modifier.wrapContentWidth()"))
        assertFalse(screen.contains("text = \"Historial\""))
        assertFalse(screen.contains("state.history.forEach"))
        assertFalse(screen.contains("Sin transferencias anteriores."))
    }
}
