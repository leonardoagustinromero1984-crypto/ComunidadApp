package com.comunidapp.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPolish05ContractTest {

    @Test
    fun feedKeepsStoriesPostsAndClipsDistinct() {
        val home = source("app/src/main/java/com/comunidapp/app/ui/screens/home/HomeScreen.kt")
        assertTrue(home.contains("StoriesRow("))
        assertTrue(home.contains("SocialFeedComposition.publications("))
        assertTrue(home.contains("SocialFeedComposition.clips("))
        assertTrue(home.contains("ClipsCarousel("))
        assertTrue(home.contains("onOpenClipViewer"))
        assertTrue(home.contains("LeoSocialPostCard("))
        assertFalse(home.contains("post.type == PostType.REEL"))
        val carousel = source("app/src/main/java/com/comunidapp/app/ui/screens/home/SocialHomeComponents.kt")
        assertTrue(carousel.contains("fun ClipsCarousel("))
        assertTrue(carousel.contains("VideoPreviewFrame("))
        assertFalse(carousel.substringAfter("fun ClipsCarousel(").substringBefore("fun HomeReelsTab(")
            .contains("ReelFeedMedia("))
        assertFalse(carousel.substringAfter("fun ClipsCarousel(").substringBefore("fun HomeReelsTab(")
            .contains("StoryVideoPlayer("))
    }

    @Test
    fun clipOpensVerticalViewerNotAPostCard() {
        val nav = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(nav.contains("NavRoutes.CLIP_VIEWER"))
        assertTrue(nav.contains("ClipViewerScreen("))
        val viewer = source("app/src/main/java/com/comunidapp/app/ui/screens/social/ClipViewerScreen.kt")
        assertTrue(viewer.contains("HomeReelsTab("))
        assertTrue(viewer.contains("SocialFeedComposition.clips("))
        assertFalse(viewer.contains("LeoSocialPostCard("))
    }

    @Test
    fun postComposerHasFullCameraAndKeepsVideoAsPost() {
        val form = source("app/src/main/java/com/comunidapp/app/ui/screens/publish/PublishForms.kt")
        assertTrue(form.contains("LeoVerCaptureCamera("))
        assertTrue(form.contains("LeoVerMediaSourceSheet("))
        assertTrue(form.contains("LeoVerCaptureMode.PHOTO"))
        assertTrue(form.contains("LeoVerCaptureMode.VIDEO"))
        assertTrue(form.contains("PickVisualMedia.ImageOnly"))
        assertTrue(form.contains("PickVisualMedia.VideoOnly"))
        assertTrue(form.contains("Video en publicación — no es un Clip."))
        val camera = source("app/src/main/java/com/comunidapp/app/ui/media/LeoVerCaptureCamera.kt")
        assertTrue(camera.contains("Girar cámara"))
        assertTrue(camera.contains("RECORD_AUDIO"))
        assertTrue(camera.contains("enabled = !recording"))
        assertTrue(camera.contains("remember(lensFacing)"))
        val story = source("app/src/main/java/com/comunidapp/app/ui/screens/social/StoryCameraScreen.kt")
        assertTrue(story.contains("LeoVerCaptureCamera("))
        val repo = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalConsumerRepositories.kt")
        assertTrue(repo.contains("kind = \"POST\""))
        assertTrue(repo.contains("kind = \"REEL\""))
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/PublishViewModel.kt")
        assertTrue(vm.contains("PostType.GENERAL"))
        assertTrue(vm.contains("feedRepository.addReel("))
        assertTrue(vm.contains("Tu Clip se está publicando."))
    }

    @Test
    fun reelIdlePreviewIsNotGreenPlayOnBlack() {
        val media = source("app/src/main/java/com/comunidapp/app/ui/media/ReelFeedMedia.kt")
        assertTrue(media.contains("var playing by remember(url)"))
        assertTrue(media.contains("val showPlayer = playing && allowPlayback"))
        assertTrue(media.contains("VideoPreviewFrame("))
        assertTrue(media.contains("StoryVideoPlayer("))
        assertFalse(media.contains("BrandGreen"))
        assertFalse(media.contains("64.dp"))
    }

    @Test
    fun composersAndRelatedSurfacesUseDs2Primitives() {
        val composers = source("app/src/main/java/com/comunidapp/app/ui/screens/social/SocialComposerScreens.kt")
        assertTrue(composers.contains("LeoPrimaryButton("))
        assertTrue(composers.contains("LeoTextField("))
        assertTrue(composers.contains("LeoListRow("))
        assertFalse(composers.contains("OutlinedTextField("))
        val picker = source("app/src/main/java/com/comunidapp/app/ui/screens/social/SocialPickerSheets.kt")
        assertTrue(picker.contains("LeoListRow("))
        val pet = source("app/src/main/java/com/comunidapp/app/ui/screens/pets/PetDetailV2Components.kt")
        assertTrue(pet.contains("LeoPrimaryButton(text = \"VitaCora\""))
        val vita = source("app/src/main/java/com/comunidapp/app/ui/screens/m14/M14PassportScreens.kt")
        assertFalse(vita.contains("VitaCora nace de vita (vida) y cora (corazón)."))
        assertTrue(vita.contains("title = \"Momentos\""))
        assertTrue(vita.contains("title = \"Identidad\""))
        val campaigns = source("app/src/main/java/com/comunidapp/app/ui/screens/m17/M17DonationScreens.kt")
        assertTrue(campaigns.contains("LeoFilterChip("))
        assertTrue(campaigns.contains("LeoTextField("))
        assertFalse(campaigns.contains("OutlinedTextField("))
        val events = source("app/src/main/java/com/comunidapp/app/ui/screens/m18/M18EventScreens.kt")
        assertTrue(events.contains("LeoFilterChip("))
        assertTrue(events.contains("LeoTextField("))
        assertFalse(events.contains("OutlinedTextField("))
    }

    private fun source(relative: String): String = UiRegressionGateTest.sourceFile(relative).readText()
}
