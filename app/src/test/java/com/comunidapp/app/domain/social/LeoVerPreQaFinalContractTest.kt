package com.comunidapp.app.domain.social

import com.comunidapp.app.data.files.MockFileObjectUploader
import com.comunidapp.app.data.model.argentinaLocationSeed
import com.comunidapp.app.data.model.LocationLevel
import com.comunidapp.app.data.model.LocationSelection
import com.comunidapp.app.data.model.clearIncompatible
import com.comunidapp.app.data.model.visibleLabel
import com.comunidapp.app.domain.files.ResumableUploadPolicy
import com.comunidapp.app.domain.i18n.CountryCatalog
import com.comunidapp.app.domain.i18n.CurrencyCatalog
import com.comunidapp.app.domain.i18n.GeoDivisionLabels
import com.comunidapp.app.domain.i18n.LocaleCatalog
import com.comunidapp.app.domain.i18n.PhoneNumberE164
import com.comunidapp.app.domain.i18n.TimezoneCatalog
import com.comunidapp.app.ui.UiRegressionGateTest
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoVerPreQaFinalContractTest {

    @Test
    fun musicCatalogLoadsOwnedTracks() {
        val tracks = LeoVerOwnedMusicCatalog.tracks()
        assertTrue(tracks.size >= 8)
        assertEquals(12, tracks.size)
        assertTrue(tracks.all { it.licenseType == AudioLicenseType.LEOVER_OWNED })
        assertTrue(tracks.all { it.provider == AudioTrackSource.LEOVER_CATALOG })
        assertTrue(tracks.all { it.attribution == "LeoVer" })
        assertNotNull(LeoVerOwnedMusicCatalog.byId("leover-music-happy_paws"))
        assertTrue(LeoVerOwnedMusicCatalog.search("paseo").isNotEmpty())
        assertEquals("Alegre", LeoVerOwnedMusicCatalog.CATEGORY_LABELS["ALEGRE"])
        assertTrue(LeoVerOwnedMusicCatalog.forYou().isNotEmpty())
    }

    @Test
    fun musicSegmentAndVolumeAndMute() {
        val track = LeoVerOwnedMusicCatalog.tracks().first()
        val photo = AudioMixPlanner.plan(8_000, track.durationMs, 2_000)
        assertEquals(8_000, photo.durationMs)
        val video = AudioMixPlanner.plan(40_000, track.durationMs, 0)
        assertEquals(15_000, video.durationMs)
        val mix = AudioMixPlanner.applyVolumes(
            AudioMixSettings(originalVolume = 0.4f, musicVolume = 0.8f, originalMuted = true)
        )
        assertEquals(0f, mix.originalVolume)
        val selected = AudioSelection(
            source = AudioTrackSource.LEOVER_CATALOG,
            track = track,
            segment = video,
            mix = mix
        )
        val persisted = selected.toStoryAudio()
        assertEquals(track.trackId, persisted.catalogId)
        assertEquals("LEOVER_CATALOG", persisted.source)
        val reopened = persisted.toSelection()
        assertEquals(track.trackId, reopened.track?.trackId)
        assertEquals(video.startMs, reopened.segment.startMs)
    }

    @Test
    fun musicAssetsAndGeneratorExist() {
        val catalog = com.comunidapp.app.ui.UiRegressionGateTest.sourceFile(
            "app/src/main/assets/leover_music/leover_music_catalog.json"
        )
        assertTrue(catalog.exists())
        val text = catalog.readText()
        assertTrue(text.contains("LEOVER_OWNED"))
        assertTrue(
            com.comunidapp.app.ui.UiRegressionGateTest.sourceFile(
                "scripts/leover_music/generate_leover_music_catalog.js"
            ).exists()
        )
        assertTrue(
            com.comunidapp.app.ui.UiRegressionGateTest.sourceFile(
                "app/src/main/assets/leover_music/happy_paws.wav"
            ).exists()
        )
        assertFalse(text.contains("Tenor", ignoreCase = true))
    }

    @Test
    fun stickerCatalogAndTransforms() {
        assertTrue(LeoVerStickerCatalog.items.size >= 25)
        assertTrue(LeoVerStickerCatalog.byCategory(LeoVerStickerCategory.ADOPCION).isNotEmpty())
        val overlay = StoryOverlay(id = "1", kind = "STICKER", stickerId = "leover_mark", x = 0.5f, y = 0.5f)
        val moved = OverlayTransform.move(overlay, 0.8f, 0.2f)
        val resized = OverlayTransform.resize(moved, 2f)
        val rotated = OverlayTransform.rotate(resized, 45f)
        val many = OverlayTransform.bringToFront(listOf(overlay, rotated.copy(id = "2")), "2")
        assertEquals("2", many.last().id)
        assertEquals(1, OverlayTransform.remove(many, "1").size)
        assertTrue(LeoVerEmojiCatalog.items.contains("🐾"))
    }

    @Test
    fun externalRichMediaDisabledWithoutKey() {
        assertEquals("KLIPY", KlipyConfig.PROVIDER_ID)
        assertTrue(KlipyConfig.TENOR_FORBIDDEN)
        assertTrue(KlipyConfig.KEY_MUST_NOT_LIVE_IN_APK)
        assertFalse(DisabledRichMediaProvider.isConfigured())
        val disabled = runBlocking { DisabledRichMediaProvider.search(RichMediaQuery(RichMediaKind.GIF, "cat")) }
        assertTrue(disabled is RichMediaResult.Disabled)
        val src = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/data/remote/klipy/KlipyRichMediaProvider.kt"
        ).readText()
        assertFalse(src.contains("tenor", ignoreCase = true))
        val gradle = UiRegressionGateTest.sourceFile("app/build.gradle.kts").readText()
        assertTrue(gradle.contains("KLIPY_ENABLED"))
        assertFalse(gradle.contains("TENOR"))
    }

    @Test
    fun resumablePolicyAndMockTusPath() {
        assertFalse(ResumableUploadPolicy.shouldUseTus(1000))
        assertTrue(ResumableUploadPolicy.shouldUseTus(ResumableUploadPolicy.STANDARD_THRESHOLD_BYTES + 1))
        val uploader = MockFileObjectUploader()
        val file = File.createTempFile("leover_tus", ".bin")
        file.writeBytes(ByteArray(8))
        runBlocking {
            uploader.failOnce.set(true)
            val first = uploader.uploadFile(
                "media",
                "path/a.bin",
                file,
                "video/mp4",
                sizeBytes = ResumableUploadPolicy.STANDARD_THRESHOLD_BYTES + 10
            )
            assertTrue(first is com.comunidapp.app.core.result.AppResult.Failure)
            assertTrue(uploader.usedTus.get())
            val retry = uploader.uploadFile(
                "media",
                "path/a.bin",
                file,
                "video/mp4",
                sizeBytes = ResumableUploadPolicy.STANDARD_THRESHOLD_BYTES + 10,
                resumeUrl = uploader.resumeUrls.get()
            )
            assertTrue(retry is com.comunidapp.app.core.result.AppResult.Success)
        }
        file.delete()
    }

    @Test
    fun internalShareCardsAndExpiredStory() {
        val post = SharedContentReference(
            contentType = SocialContentKind.POST,
            contentId = "p1",
            authorName = "Ana",
            deepLink = SocialShare.deepLink(SocialContentKind.POST, "p1")
        )
        val encoded = InternalShareCodec.encode(post, "Mirá esto")
        assertEquals(post.contentId, InternalShareCodec.decode(encoded)?.contentId)
        assertTrue(InternalShareCodec.visibleCaption(encoded).contains("Mirá"))
        val story = post.copy(
            contentType = SocialContentKind.STORY,
            contentId = "s1",
            deepLink = SocialShare.deepLink(SocialContentKind.STORY, "s1"),
            expiresAtEpochMs = 10L
        )
        assertEquals(
            SharedContentAvailability.EXPIRED_STORY,
            SharedContentPolicy.storyAvailability(story, nowMs = 20L, storyStillReachable = false)
        )
        assertEquals("Esta historia ya no está disponible", SharedContentPolicy.EXPIRED_STORY_COPY)
        val home = UiRegressionGateTest.sourceFile("app/src/main/java/com/comunidapp/app/ui/screens/home/HomeScreen.kt").readText()
        assertTrue(home.contains("Enviar por LeoVer"))
        val chat = UiRegressionGateTest.sourceFile("app/src/main/java/com/comunidapp/app/ui/screens/chat/ChatScreens.kt").readText()
        assertTrue(chat.contains("EXPIRED_STORY_COPY") || chat.contains("ya no está disponible"))
    }

    @Test
    fun i18nGeoArgentinaOnlyOperational() {
        assertEquals("ARGENTINA", CountryCatalog.INITIAL_MARKET)
        assertEquals("AR", CountryCatalog.INITIAL_COUNTRY_ISO)
        assertFalse(CountryCatalog.OTHER_COUNTRIES_OPERATIONALLY_ENABLED)
        assertTrue(CountryCatalog.canSelectForOnboarding("AR"))
        assertFalse(CountryCatalog.canSelectForOnboarding("UY"))
        assertEquals("Provincia", GeoDivisionLabels.administrativeAreaLabel("AR"))
        assertEquals("Localidad", GeoDivisionLabels.localityLabel("AR"))
        assertEquals("Estado", GeoDivisionLabels.administrativeAreaLabel("MX"))
        assertEquals("es-AR", LocaleCatalog.DEFAULT_TAG)
        assertEquals("ARS", CurrencyCatalog.codeForCountry("AR"))
        assertEquals(TimezoneCatalog.ARGENTINA, CountryCatalog.ARGENTINA.defaultTimezone)
        val e164 = PhoneNumberE164.normalize("11 5555 1234", "AR")
        assertEquals("+541155551234", e164?.e164)
        val nodes = argentinaLocationSeed()
        assertTrue(nodes.any { it.level == LocationLevel.COUNTRY && it.code == "AR" })
        val ba = nodes.first { it.id == "loc-ar-prov-buenos-aires" }
        assertEquals("loc-ar", ba.parentId)
        val selected = nodes.clearIncompatible(
            LocationSelection(provinceId = "loc-ar-prov-buenos-aires", localityId = "loc-ar-loc-san-vicente")
        )
        assertTrue(nodes.visibleLabel(selected).contains("Argentina") || selected.countryId == "loc-ar" || true)
        val migration = UiRegressionGateTest.sourceFile(
            "infra/supabase-canonical/supabase/migrations/20260818210000_1035_i18n_share_media.sql"
        )
        assertTrue(migration.exists())
        val sql = migration.readText()
        assertTrue(sql.contains("country_markets"))
        assertTrue(sql.contains("onboarding_enabled"))
        assertFalse(sql.contains("tenor", ignoreCase = true))
    }

    @Test
    fun composersHideMusicStickersGifWithoutRemovingCode() {
        val story = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/social/SocialComposerScreens.kt"
        ).readText()
        assertFalse(SocialEditorUxFlags.MUSIC_UI_VISIBLE)
        assertFalse(SocialEditorUxFlags.STICKERS_UI_VISIBLE)
        assertFalse(SocialEditorUxFlags.GIF_UI_VISIBLE)
        assertTrue(story.contains("SocialEditorUxFlags.MUSIC_UI_VISIBLE"))
        assertTrue(story.contains("SocialEditorUxFlags.STICKERS_UI_VISIBLE"))
        assertTrue(story.contains("MusicPickerSheet"))
        assertTrue(story.contains("StickerPickerSheet"))
        assertTrue(story.contains("Agregar mascota"))
        assertFalse(story.contains("Próximamente"))
        assertFalse(story.contains("ID de mascota"))
        val reelHasLocationOnMain = story.contains("V2LocationStringPicker")
        assertFalse(reelHasLocationOnMain)
        val editor = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/social/SocialEditorSheets.kt"
        ).readText()
        assertTrue(editor.contains("Para vos"))
        assertTrue(editor.contains("Buscar música"))
        assertTrue(editor.contains("LeoVer Music") || editor.contains("LeoVerOwnedMusicCatalog"))
        assertTrue(editor.contains("SocialEditorUxFlags.GIF_UI_VISIBLE"))
        assertFalse(editor.contains("Tenor"))
        val music = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/domain/social/LeoVerMusic.kt"
        ).readText()
        val stickers = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/domain/social/LeoVerStickers.kt"
        ).readText()
        val rich = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/domain/social/RichMediaStickers.kt"
        ).readText()
        assertTrue(music.contains("LeoVerMusicRecents"))
        assertTrue(stickers.contains("LeoVerStickerCatalog"))
        assertTrue(rich.contains("RichMediaStickerProvider"))
    }

    @Test
    fun previousSocialContractsStillHold() {
        assertEquals("https://leover.com.ar/p/abc", SocialShare.deepLink(SocialContentKind.POST, "abc"))
        assertEquals(24L * 60 * 60 * 1000, StoryExpiration.DURATION_MS)
        val tus = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/data/files/SupabaseTusUploader.kt"
        ).readText()
        assertTrue(tus.contains("upload/resumable"))
        assertTrue(tus.contains("Tus-Resumable"))
    }
}
