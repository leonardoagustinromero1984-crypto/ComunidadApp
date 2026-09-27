package com.comunidapp.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPolish06ContractTest {

    @Test
    fun postVideoUsesCropPreviewAndOpensOriginalRatioViewer() {
        val card = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoSocialPostCard.kt")
        assertTrue(card.contains("aspectRatio(if (isReel) 9f / 16f else 4f / 5f)"))
        assertTrue(card.contains("cropPreview = openFull != null || isReel"))
        assertTrue(card.contains("onOpenFull = openFull"))
        assertTrue(card.contains("heightIn(min = 220.dp, max = 520.dp)"))
        val player = source("app/src/main/java/com/comunidapp/app/ui/screens/social/StoryViewerScreen.kt")
        assertTrue(player.contains("cropToFill"))
        assertTrue(player.contains("RESIZE_MODE_ZOOM"))
        val media = source("app/src/main/java/com/comunidapp/app/ui/media/ReelFeedMedia.kt")
        assertTrue(media.contains("onOpenFull"))
        assertTrue(media.contains("VideoPreviewFrame("))
    }

    @Test
    fun vitaCoraOperationalViewHasNoEtymologyAndClearerDividers() {
        val vita = source("app/src/main/java/com/comunidapp/app/ui/screens/m14/M14PassportScreens.kt")
        assertFalse(vita.contains("VitaCora nace de vita (vida) y cora (corazón)."))
        assertTrue(vita.contains("Color(0xFFB4BAB2)"))
        assertTrue(vita.contains("title = \"Momentos\""))
        val tutorial = source("app/src/main/java/com/comunidapp/app/domain/onboarding/onb02/TutorialCatalog.kt")
        assertTrue(tutorial.contains("VitaCora nace de vita (vida) y cora (corazón)"))
    }

    @Test
    fun petProfileKeepsEditAndVitaCoraSideBySide() {
        val pet = source("app/src/main/java/com/comunidapp/app/ui/screens/pets/PetDetailV2Components.kt")
        val actions = pet.substringAfter("internal fun PetPrimaryActions(").substringBefore("internal fun PetV2Card(")
        assertTrue(actions.contains("LeoOutlinedButton(text = \"Editar\""))
        assertTrue(actions.contains("LeoPrimaryButton(text = \"VitaCora\""))
        assertTrue(actions.contains("Modifier.weight(1f)"))
        assertFalse(actions.contains("Column("))
    }

    @Test
    fun reelComposerCanRecordLiveAndPickGallery() {
        val composers = source("app/src/main/java/com/comunidapp/app/ui/screens/social/SocialComposerScreens.kt")
        assertTrue(composers.contains("Grabar Clip"))
        assertTrue(composers.contains("LeoVerCaptureCamera("))
        assertTrue(composers.contains("LeoVerCaptureMode.VIDEO"))
        val camera = source("app/src/main/java/com/comunidapp/app/ui/media/LeoVerCaptureCamera.kt")
        assertTrue(camera.contains("Girar cámara"))
        assertTrue(camera.contains("enabled = !recording"))
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/PublishViewModel.kt")
        assertTrue(vm.contains("feedRepository.addReel("))
        assertTrue(vm.contains("kind = \"REEL\"") || source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalConsumerRepositories.kt").contains("kind = \"REEL\""))
    }

    @Test
    fun storyQuotaIsIsolatedAndUsesHistoriasCopy() {
        val mapper = source("app/src/main/java/com/comunidapp/app/domain/files/FileUiErrorMapper.kt")
        assertTrue(mapper.contains("Alcanzaste el límite diario de historias. Podés volver a publicar mañana."))
        assertTrue(mapper.contains("fun storyLimitMessage"))
        val sql = source("infra/supabase-canonical/supabase/migrations/20260913220000_1084_story_quota_after_success.sql")
        assertTrue(sql.contains("social.story.create"))
        assertTrue(sql.contains("limit_count = 20"))
        assertTrue(sql.contains("window_seconds = 86400"))
        val insertBeforeConsume = sql.indexOf("insert into public.social_stories")
        val consume = sql.indexOf("_canon_consume_rate_limit('social.story.create'")
        assertTrue(insertBeforeConsume in 0 until consume)
        assertFalse(sql.contains("media.video.count.daily"))
        assertFalse(sql.contains("social.post.create"))
        assertFalse(sql.contains("social.reel.create"))
    }

    @Test
    fun campaignsDeclareConfirmWithoutCheckout() {
        val screens = source("app/src/main/java/com/comunidapp/app/ui/screens/m17/M17DonationScreens.kt")
        assertTrue(screens.contains("Las transferencias se realizan fuera de LeoVer."))
        assertTrue(screens.contains("Colaboré"))
        assertTrue(screens.contains("Declarar colaboración"))
        assertTrue(screens.contains("Copiar alias"))
        assertFalse(screens.contains("Contribución de prueba — pagos reales aún no habilitados"))
        val sql = source("infra/supabase-canonical/supabase/migrations/20260913223000_1085_campaign_declared_contributions.sql")
        assertTrue(sql.contains("canon_declare_campaign_contribution"))
        assertTrue(sql.contains("PENDING"))
        assertTrue(sql.contains("CONFIRMED"))
        assertTrue(sql.contains("REJECTED"))
        assertFalse(sql.contains("Mercado Pago", ignoreCase = true) || sql.contains("mercadopago", ignoreCase = true))
    }

    @Test
    fun mapKeepsInjectedKeyAndDoesNotHardcodeSecret() {
        val map = source("app/src/main/java/com/comunidapp/app/ui/map/LeoVerMap.kt")
        assertTrue(map.contains("MAPS_API_KEY"))
        assertFalse(map.contains("AIza"))
        val gradle = source("app/build.gradle.kts")
        assertTrue(gradle.contains("MAPS_API_KEY"))
        val diag = source("app/src/main/java/com/comunidapp/app/ui/map/MapsTileDiagnostics.kt")
        assertTrue(diag.contains("key_len"))
        assertFalse(diag.contains("AIza"))
    }

    @Test
    fun socialFeedCompositionFromPolish05IsPreserved() {
        val home = source("app/src/main/java/com/comunidapp/app/ui/screens/home/HomeScreen.kt")
        assertTrue(home.contains("StoriesRow("))
        assertTrue(home.contains("SocialFeedComposition.publications("))
        assertTrue(home.contains("SocialFeedComposition.clips("))
        assertTrue(home.contains("ClipsCarousel("))
    }

    private fun source(relative: String): String = UiRegressionGateTest.sourceFile(relative).readText()
}
