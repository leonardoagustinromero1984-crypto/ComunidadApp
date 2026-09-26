package com.comunidapp.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Round8UxGuardsTest {

    @Test
    fun reelDoesNotRenderInventedTitle() {
        val card = File("src/main/java/com/comunidapp/app/ui/components/leo/LeoSocialPostCard.kt").readText()
        val media = File("src/main/java/com/comunidapp/app/ui/media/ReelFeedMedia.kt").readText()
        val controller = File("src/main/java/com/comunidapp/app/domain/social/ReelPublishController.kt").readText()
        val publish = File("src/main/java/com/comunidapp/app/viewmodel/PublishViewModel.kt").readText()
        assertTrue(card.contains("inventedReelTitle"))
        assertFalse(media.contains("text = previewLabel"))
        assertFalse(controller.contains("ifBlank { \"Reel\" }"))
        assertFalse(controller.contains("title = \"Reel\""))
        assertTrue(controller.contains("title = \"\""))
        assertFalse(publish.contains("ifBlank { \"Reel\" }"))
    }

    @Test
    fun uploadIndicatorStaysTopAndCompact() {
        val banner = File("src/main/java/com/comunidapp/app/ui/components/leo/ReelPublishStatusBanner.kt").readText()
        assertTrue(banner.contains("statusBarsPadding()"))
        assertTrue(banner.contains("reel_compact_upload_indicator"))
        assertTrue(banner.contains("vertical = 2.dp"))
        assertTrue(banner.contains("Reintentar"))
        assertTrue(banner.contains("Cerrar"))
        assertFalse(banner.contains("CircularProgressIndicator"))
    }

    @Test
    fun transferRequestCardHasNoRedundantDetail() {
        val card = File("src/main/java/com/comunidapp/app/ui/screens/pets/IncomingCareTransferCard.kt").readText()
        val screen = File("src/main/java/com/comunidapp/app/ui/screens/pets/PetTransfersScreen.kt").readText()
        assertTrue(card.contains("mediaShareLine"))
        assertTrue(card.contains("acceptConfirmTitle"))
        assertTrue(card.contains("Aceptar"))
        assertTrue(card.contains("Rechazar"))
        assertFalse(card.contains("Ver detalle"))
        assertFalse(screen.contains("Ver detalle"))
        assertTrue(screen.contains("IncomingCareTransferCard"))
        assertTrue(screen.contains("CANCEL_REQUEST"))
    }

    @Test
    fun memoriesSurfaceIsProfileLibrary() {
        val profile = File("src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt").readText()
        val memories = File("src/main/java/com/comunidapp/app/ui/screens/profile/PersonalMemoriesScreen.kt").readText()
        val sql = File("../infra/supabase-canonical/supabase/migrations/20260906120000_1074_personal_memories.sql").readText()
        assertTrue(profile.contains("MEMORIES_TITLE"))
        assertTrue(memories.contains("MEMORY_ONLY_YOU"))
        assertTrue(memories.contains("MEMORY_SHARED"))
        assertTrue(memories.contains("canOpenPet"))
        assertTrue(sql.contains("canon_list_my_personal_memories"))
        assertTrue(sql.contains("owner_kind = 'PERSON'"))
        assertTrue(sql.contains("_canon_media_origin_readable"))
        assertFalse(sql.contains("canon_accept_care_transfer"))
    }
}
