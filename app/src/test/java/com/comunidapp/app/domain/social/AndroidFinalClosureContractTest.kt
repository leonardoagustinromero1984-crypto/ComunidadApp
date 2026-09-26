package com.comunidapp.app.domain.social

import com.comunidapp.app.domain.pets.PetCareTransferCopy
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidFinalClosureContractTest {

    @Test
    fun memoriesGateRequiresCompletedTransfer() {
        val sql = UiRegressionGateTest.sourceFile(
            "infra/supabase-canonical/supabase/migrations/" +
                "20260913180000_1083_memories_transfer_gate_and_social_media_acl.sql"
        ).readText()
        assertTrue(sql.contains("_canon_viewer_completed_care_transfer"))
        assertTrue(sql.contains("t.status = 'ACCEPTED'"))
        assertTrue(sql.contains("t.source_person_id = p_viewer"))
        assertTrue(sql.contains("_acl_social_post_media_readable"))
        assertTrue(sql.contains("_canon_social_post_visible"))
        assertTrue(sql.contains("avatar_asset_id"))
        assertFalse(sql.contains("service_role"))
        assertTrue(PetCareTransferCopy.MEMORIES_EMPTY.contains("cuidado"))
        assertFalse(PetCareTransferCopy.MEMORIES_SUBTITLE.contains("guardados en LeoVer"))
    }

    @Test
    fun reelSuccessIsTerminalAndAutoLinksVitaCora() {
        val controller = source("app/src/main/java/com/comunidapp/app/domain/social/ReelPublishController.kt")
        val banner = source("app/src/main/java/com/comunidapp/app/ui/components/leo/ReelPublishStatusBanner.kt")
        assertTrue(controller.contains("VitaCoraSocialSave.saveApprovedReel"))
        assertTrue(controller.contains("vitaCoraResolved = true"))
        assertTrue(controller.contains("createCompleted = true"))
        assertFalse(controller.contains("REEL_MEDIA_ASSOCIATION_NOT_VISIBLE"))
        assertFalse(controller.contains("ensureVisiblePost"))
        assertFalse(banner.contains("AlertDialog"))
        assertTrue(banner.contains("PendingSocialPublishState.SUCCESS"))
        assertTrue(banner.contains("dismissTerminal()"))
    }

    @Test
    fun manadaDoesNotEmbedSocialHistory() {
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/UserPublicProfileScreen.kt")
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(screen.contains("hideSocialHistory"))
        assertTrue(graph.contains("PROFILE_FROM_MANADA"))
        assertTrue(graph.contains("hideSocialHistory = fromManada"))
    }

    @Test
    fun signedUrlDoesNotRequireClientSelect() {
        val downloads = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalMediaRepositories.kt")
        val resolver = source("app/src/main/java/com/comunidapp/app/data/files/FileDisplayResolver.kt")
        assertTrue(downloads.contains("invokeCanonicalMediaSignedUrl(request.assetId)"))
        assertFalse(
            downloads.contains("if (decision != FileAccessDecision.ALLOWED) {\n            return failureFromThrowable")
        )
        assertTrue(resolver.contains("asset?.id ?: assetId"))
    }

    private fun source(relativePath: String): String =
        UiRegressionGateTest.sourceFile(relativePath).readText()
}
