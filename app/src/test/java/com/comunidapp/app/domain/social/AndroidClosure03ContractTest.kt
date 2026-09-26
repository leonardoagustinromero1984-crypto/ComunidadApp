package com.comunidapp.app.domain.social

import com.comunidapp.app.domain.pets.PetCareTransferCopy
import com.comunidapp.app.ui.UiRegressionGateTest
import com.comunidapp.app.ui.components.leo.CompactReelUploadCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidClosure03ContractTest {

    @Test
    fun reelTransactionIsResumableByPhase() {
        val controller = source("app/src/main/java/com/comunidapp/app/domain/social/ReelPublishController.kt")
        val model = source("app/src/main/java/com/comunidapp/app/domain/social/PendingSocialPublish.kt")
        assertTrue(model.contains("enum class ReelPublishPhase"))
        assertTrue(model.contains("CREATE"))
        assertTrue(model.contains("ATTACH_PETS"))
        assertTrue(model.contains("VITACORA"))
        assertTrue(model.contains("createCompleted"))
        assertTrue(model.contains("petsAttached"))
        assertTrue(controller.contains("createCompleted = true"))
        assertTrue(controller.contains("VitaCoraSocialSave.saveApprovedReel"))
        assertTrue(controller.contains("LINK_FAILED"))
        assertFalse(controller.contains("REEL_MEDIA_ASSOCIATION_NOT_VISIBLE"))
        assertFalse(controller.contains("ensureVisiblePost"))
        assertTrue(controller.contains("shouldKeepForResume"))
    }

    @Test
    fun linkFailureCopyIsPrecise() {
        val failed = PendingSocialPublish(
            jobId = "job-1",
            actorUserId = "user-a",
            caption = "Clip",
            state = PendingSocialPublishState.FAILED,
            backendPostId = "post-1",
            createCompleted = true,
            petIds = listOf("pet-1"),
            errorCategory = PendingSocialPublishErrors.LINK_FAILED
        )
        assertEquals(
            "El Reel se publicó, pero no pudimos terminar de vincularlo con la mascota.",
            CompactReelUploadCopy.label(failed)
        )
    }

    @Test
    fun vitaCoraUsesTheSameSocialSavePath() {
        val save = source("app/src/main/java/com/comunidapp/app/domain/vitacora/VitaCoraSocialSave.kt")
        val repo = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalVitaCoraRepository.kt")
        assertTrue(save.contains("saveApprovedReel"))
        assertTrue(save.contains("saveApprovedPost"))
        assertTrue(save.contains("saveApprovedSocial"))
        assertFalse(save.contains("vitacora_for_reels_v2"))
        assertTrue(repo.contains("RPC_SAVE_SOCIAL_VITACORA_MOMENT"))
        assertTrue(repo.contains("does not exist"))
    }

    @Test
    fun memoriesStillRequireCompletedTransfer() {
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/" +
                "20260913180000_1083_memories_transfer_gate_and_social_media_acl.sql"
        )
        assertTrue(sql.contains("_canon_viewer_completed_care_transfer"))
        assertTrue(sql.contains("t.status = 'ACCEPTED'"))
        assertTrue(PetCareTransferCopy.MEMORIES_EMPTY.contains("cuidado"))
    }

    @Test
    fun mediaAclStillUsesSocialVisibility() {
        val sql = source(
            "infra/supabase-canonical/supabase/migrations/" +
                "20260913180000_1083_memories_transfer_gate_and_social_media_acl.sql"
        )
        assertTrue(sql.contains("_acl_social_post_media_readable"))
        assertTrue(sql.contains("_canon_social_post_visible"))
    }

    @Test
    fun feedMappingIsBoundedAndCachesSignedUrls() {
        val feed = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalConsumerRepositories.kt")
        val friends = source("app/src/main/java/com/comunidapp/app/viewmodel/FriendsListViewModel.kt")
        val profile = source("app/src/main/java/com/comunidapp/app/viewmodel/UserPublicProfileViewModel.kt")
        val resolver = source("app/src/main/java/com/comunidapp/app/data/files/FileDisplayResolver.kt")
        assertTrue(feed.contains("Semaphore(6)"))
        assertTrue(feed.contains("mapSocialPosts"))
        assertTrue(friends.contains("Semaphore(6)"))
        assertFalse(profile.contains("delay(4_000)"))
        assertTrue(resolver.contains("temporaryUrls[assetId]"))
        assertTrue(resolver.contains("temporaryUrls[resolvedId] = reference"))
    }

    @Test
    fun publishAndStoriesUseDs2Chips() {
        val publish = source("app/src/main/java/com/comunidapp/app/ui/screens/publish/PublishForms.kt")
        val stories = source("app/src/main/java/com/comunidapp/app/ui/screens/social/SocialEditorSheets.kt")
        val form = source("app/src/main/java/com/comunidapp/app/ui/screens/pets/PetFormScreen.kt")
        assertTrue(publish.contains("LeoFilterChip"))
        assertFalse(publish.contains("material3.FilterChip"))
        assertTrue(stories.contains("LeoFilterChip"))
        assertFalse(stories.contains("material3.FilterChip"))
        assertTrue(form.contains("LeoFilterChip"))
        assertFalse(form.contains("material3.FilterChip"))
    }

    @Test
    fun duplicatePreventionStaysOnCanonicalKeys() {
        val attach = source(
            "infra/supabase-canonical/supabase/migrations/20260905020000_1069_social_reel_pets_unfriend.sql"
        )
        val vita = source(
            "infra/supabase-canonical/supabase/migrations/20260908120000_1079_content_grouping_privacy_transfer.sql"
        )
        assertTrue(attach.contains("primary key (post_id, pet_id)"))
        assertTrue(attach.contains("on conflict do nothing"))
        assertTrue(vita.contains("_canon_moment_source_social_post_id"))
    }

    private fun source(relativePath: String): String =
        UiRegressionGateTest.sourceFile(relativePath).readText()
}
