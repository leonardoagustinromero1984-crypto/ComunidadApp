package com.comunidapp.app.domain.social

import com.comunidapp.app.data.model.FeedMediaAvailability
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelSavedContentStabilizationTest {

    @Test
    fun feedPostKeepsAssetIdentitySeparateFromDisplayAvailability() {
        val post = FeedPost(
            id = "post-1",
            authorId = "author-1",
            authorName = "Ana",
            type = PostType.REEL,
            title = "",
            content = "Paseo",
            mediaAssetId = "asset-1",
            mediaAvailability = FeedMediaAvailability.UNAVAILABLE
        )

        assertEquals("asset-1", post.mediaAssetId)
        assertEquals(FeedMediaAvailability.UNAVAILABLE, post.mediaAvailability)
        assertTrue(SocialPostMedia.displayUrls(post.imageUrl, post.imageUrls).isEmpty())
    }

    @Test
    fun canonicalSavedRowsUseTheSignedDisplayResolverAndKeepTheirAssetId() {
        val repository = source(
            "app/src/main/java/com/comunidapp/app/data/repository/CanonicalConsumerRepositories.kt"
        )

        assertTrue(repository.contains("RPC_LIST_SAVED_SOCIAL_POSTS"))
        assertTrue(repository.contains("DataProvider.fileDisplayResolver.resolve("))
        assertTrue(repository.contains("mediaAssetId = primaryAssetId"))
        assertTrue(repository.contains("FeedMediaAvailability.UNAVAILABLE"))
        assertFalse(repository.contains("createSignedUrl("))
    }

    @Test
    fun reelRecoveryConfirmsCanonicalCreateWithoutFeedPolling() {
        val controller = source(
            "app/src/main/java/com/comunidapp/app/domain/social/ReelPublishController.kt"
        )
        val recovery = controller.indexOf("recoverReelId(assetId)")
        val createDone = controller.indexOf("createCompleted = true")
        val success = controller.indexOf("state = PendingSocialPublishState.SUCCESS", createDone)

        assertTrue(recovery >= 0)
        assertTrue(createDone > recovery)
        assertTrue(success > createDone)
        assertTrue(controller.contains("noteCreatedReel(postId, assetId)"))
        assertFalse(controller.contains("REEL_MEDIA_ASSOCIATION_NOT_VISIBLE"))
        assertFalse(controller.contains("visibleMedia.isEmpty()"))
    }

    @Test
    fun savedContentOpensTheExistingPostDetailPlayerAndReloadsAfterUnsave() {
        val screen = source(
            "app/src/main/java/com/comunidapp/app/ui/screens/profile/SavedPostsScreen.kt"
        )
        val graph = source(
            "app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt"
        )
        val detail = source(
            "app/src/main/java/com/comunidapp/app/ui/screens/social/SocialPostDetailScreen.kt"
        )
        val viewModel = source(
            "app/src/main/java/com/comunidapp/app/viewmodel/SavedPostsViewModel.kt"
        )

        assertTrue(screen.contains("onPostClick(post.id)"))
        assertTrue(graph.contains("NavRoutes.postDetail(postId)"))
        assertTrue(detail.contains("LeoSocialPostCard("))
        assertTrue(viewModel.contains(".onSuccess { reload() }"))
    }

    private fun source(relativePath: String): String =
        UiRegressionGateTest.sourceFile(relativePath).readText()
}
