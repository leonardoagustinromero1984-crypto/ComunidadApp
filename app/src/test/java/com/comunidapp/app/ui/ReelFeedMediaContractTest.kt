package com.comunidapp.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelFeedMediaContractTest {

    @Test
    fun feedCardDoesNotCreatePlayerUntilPlay() {
        val card = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoSocialPostCard.kt")
        assertTrue(card.contains("ReelFeedMedia("))
        assertFalse(card.contains("StoryVideoPlayer("))
        val media = source("app/src/main/java/com/comunidapp/app/ui/media/ReelFeedMedia.kt")
        assertTrue(media.contains("var playing by remember(url)"))
        assertTrue(media.contains("val showPlayer = playing && allowPlayback"))
        assertTrue(media.contains("StoryVideoPlayer("))
        assertTrue(media.contains("playWhenReady = true"))
    }

    @Test
    fun composerReleasesPreviewWhilePublishing() {
        val composer = source("app/src/main/java/com/comunidapp/app/ui/screens/social/SocialComposerScreens.kt")
        assertTrue(composer.contains("ReelFeedMedia("))
        assertTrue(composer.contains("allowPlayback = !formState.isLoading"))
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/PublishViewModel.kt")
        assertTrue(vm.contains("ReelPublishTrace.begin()"))
        assertTrue(vm.contains("finally"))
        assertTrue(vm.contains("withTimeout(120_000L)"))
        assertTrue(vm.contains("withTimeout(90_000L)"))
        assertTrue(vm.contains("withTimeout(20_000L)"))
        assertTrue(vm.contains("Preparando video…"))
        assertTrue(vm.contains("ReelPublishController.get()"))
        assertTrue(vm.contains("acceptedBackground"))
        assertTrue(vm.contains("Tu Clip se está publicando."))
    }

    @Test
    fun reelPrepareUsesExportPolicyInsteadOfDoubleCopy() {
        val pipeline = source("app/src/main/java/com/comunidapp/app/domain/social/SocialMediaPipeline.kt")
        assertTrue(pipeline.contains("prepareVideo("))
        assertTrue(pipeline.contains("VideoExportPolicy.shouldPassthrough("))
        assertTrue(pipeline.contains("DefaultEncoderFactory"))
        assertTrue(pipeline.contains("targetBitrateBps("))
        assertFalse(pipeline.contains("VerifiedVideoPipeline.verify("))
        val scheduler = source("app/src/main/java/com/comunidapp/app/domain/social/ReelPublishScheduler.kt")
        assertTrue(scheduler.contains("WorkManager"))
        assertTrue(scheduler.contains("leover-reel-publish"))
        val controller = source("app/src/main/java/com/comunidapp/app/domain/social/ReelPublishController.kt")
        assertTrue(controller.contains("filesDir"))
        assertTrue(controller.contains("findOwnReelIdByMediaAsset"))
        assertTrue(controller.contains("registerCompleted"))
        assertTrue(controller.contains("VitaCoraSocialSave.saveApprovedReel"))
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/PublishViewModel.kt")
        assertTrue(vm.contains("deleteExportOutput("))
        assertTrue(vm.contains("sizeBytes = prepared.sizeBytes"))
        val probe = source("app/src/main/java/com/comunidapp/app/domain/media/VideoContentProbe.kt")
        assertFalse(probe.contains("VerifiedVideoPipeline.verify("))
        val history = source("app/src/main/java/com/comunidapp/app/ui/screens/m14/M14Block3Screens.kt")
        assertTrue(history.contains("ReelFeedMedia("))
        assertFalse(history.contains("media_asset_id"))
        val card = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoSocialPostCard.kt")
        assertTrue(card.contains("ReelFeedMedia("))
        assertFalse(card.contains("StoryVideoPlayer("))
    }

    private fun source(relative: String): String = UiRegressionGateTest.sourceFile(relative).readText()
}
