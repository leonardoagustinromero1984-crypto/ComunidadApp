package com.comunidapp.app.domain.ux

import com.comunidapp.app.data.model.LocationSelection
import com.comunidapp.app.data.model.argentinaLocationSeed
import com.comunidapp.app.data.model.visibleLabel
import com.comunidapp.app.domain.i18n.CountryCatalog
import com.comunidapp.app.domain.i18n.MarketUxPolicy
import com.comunidapp.app.domain.media.PhotoCanvasFitMode
import com.comunidapp.app.domain.media.PhotoCanvasMath
import com.comunidapp.app.domain.media.PhotoCanvasTransform
import com.comunidapp.app.domain.onboarding.onb02.ProfileActorTaxonomy
import com.comunidapp.app.domain.social.SocialEditorUxFlags
import com.comunidapp.app.domain.social.StoryComposition
import com.comunidapp.app.domain.vitacora.VitaCoraSocialMomentCodec
import com.comunidapp.app.domain.vitacora.import.VitacoraImportAnalyzer
import com.comunidapp.app.domain.vitacora.import.VitacoraImportColumns
import com.comunidapp.app.domain.vitacora.import.VitacoraImportMode
import com.comunidapp.app.domain.vitacora.import.VitacoraImportXlsx
import com.comunidapp.app.ui.UiRegressionGateTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoVerRecoveredUx01ContractTest {

    @Test
    fun PROFILE_TAXONOMY_AND_TUTORIAL_UNTOUCHED() {
        assertEquals(6, ProfileActorTaxonomy.FIRST_LEVEL_ACTOR_COUNT)
        assertFalse(ProfileActorTaxonomy.SERVICE_CATEGORIES_IN_FIRST_LEVEL)
        assertTrue(ProfileActorTaxonomy.REFUGE_GENERAL_SELECTOR)
        assertFalse(ProfileActorTaxonomy.REFUGE_COMMERCIAL_SELECTOR)
        val pager = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoVerTutorialPager.kt")
        assertTrue(pager.contains("fun LeoVerTutorialPager("))
        assertTrue(pager.contains("HorizontalPager("))
        val login = source("app/src/main/java/com/comunidapp/app/ui/screens/login/LoginScreen.kt")
        assertTrue(login.contains("ContinueWithGoogleButton"))
    }

    @Test
    fun MUSIC_STICKER_GIF_HIDDEN_CODE_PRESERVED() {
        assertTrue(SocialEditorUxFlags.MUSIC_CODE_PRESERVED)
        assertTrue(SocialEditorUxFlags.STICKER_CODE_PRESERVED)
        assertTrue(SocialEditorUxFlags.RICH_MEDIA_CODE_PRESERVED)
        assertFalse(SocialEditorUxFlags.MUSIC_UI_VISIBLE)
        assertFalse(SocialEditorUxFlags.STICKERS_UI_VISIBLE)
        assertFalse(SocialEditorUxFlags.GIF_UI_VISIBLE)
        val composer = source("app/src/main/java/com/comunidapp/app/ui/screens/social/SocialComposerScreens.kt")
        assertTrue(composer.contains("if (SocialEditorUxFlags.MUSIC_UI_VISIBLE)"))
        assertTrue(composer.contains("if (SocialEditorUxFlags.STICKERS_UI_VISIBLE)"))
        assertFalse(composer.contains("Próximamente"))
        assertTrue(source("app/src/main/java/com/comunidapp/app/domain/social/LeoVerMusic.kt").contains("LeoVerMusicRecents"))
        assertTrue(source("app/src/main/java/com/comunidapp/app/domain/social/LeoVerStickers.kt").contains("LeoVerStickerCatalog"))
        assertTrue(source("app/src/main/java/com/comunidapp/app/domain/social/RichMediaStickers.kt").contains("enum class RichMediaKind"))
        assertTrue(source("app/src/main/java/com/comunidapp/app/data/remote/klipy/KlipyRichMediaProvider.kt").contains("class KlipyRichMediaProvider"))
    }

    @Test
    fun COUNTRY_HIDDEN_ARGENTINA_DEFAULT() {
        assertTrue(MarketUxPolicy.INTERNATIONAL_ARCHITECTURE_PRESERVED)
        assertEquals("AR", MarketUxPolicy.initialCountryIso())
        assertFalse(MarketUxPolicy.COUNTRY_UI_VISIBLE)
        assertFalse(MarketUxPolicy.COUNTRY_ONBOARDING_VISIBLE)
        assertFalse(MarketUxPolicy.COUNTRY_PROFILE_VISIBLE)
        assertFalse(MarketUxPolicy.COUNTRY_ORGANIZATION_VISIBLE)
        assertFalse(MarketUxPolicy.COUNTRY_SOCIAL_LOCATION_VISIBLE)
        assertFalse(MarketUxPolicy.COUNTRY_VITACORA_IMPORT_USER_INPUT)
        assertTrue(MarketUxPolicy.PROVINCE_VISIBLE)
        assertTrue(MarketUxPolicy.LOCALITY_VISIBLE)
        assertEquals("AR", CountryCatalog.INITIAL_COUNTRY_ISO)
        val picker = source("app/src/main/java/com/comunidapp/app/ui/components/v2/V2LocationPicker.kt")
        assertTrue(picker.contains("MarketUxPolicy.COUNTRY_UI_VISIBLE"))
        assertTrue(picker.contains("administrativeAreaLabel"))
        assertTrue(picker.contains("localityLabel"))
        val nodes = argentinaLocationSeed()
        val selected = LocationSelection(
            countryId = "loc-ar",
            provinceId = "loc-ar-prov-buenos-aires",
            localityId = "loc-ar-loc-la-plata"
        )
        val label = nodes.visibleLabel(selected)
        assertTrue(label.contains("La Plata") || label.contains("Buenos Aires"))
        assertFalse(label.contains("Argentina"))
    }

    @Test
    fun VITACORA_IMPORT_OMITS_COUNTRY_BUT_ACCEPTS_V1() {
        assertFalse(VitacoraImportColumns.DOWNLOAD_HEADERS.containsKey(VitacoraImportColumns.COUNTRY))
        assertTrue(VitacoraImportColumns.HEADERS.containsKey(VitacoraImportColumns.COUNTRY))
        val bytes = VitacoraImportXlsx.writeTemplate()
        val inspected = VitacoraImportXlsx.inspect(bytes, "LeoVer-Plantilla-Importacion-VitaCora-v1.xlsx")
        assertEquals("LEOVER_VITACORA_IMPORT", inspected.templateType)
        assertEquals(1, inspected.templateVersion)
        val example = inspected.rows.first()
        assertFalse(example.values.containsKey(VitacoraImportColumns.COUNTRY) && example.values[VitacoraImportColumns.COUNTRY] == "must-not")
        assertTrue(example.values[VitacoraImportColumns.COUNTRY].isNullOrBlank())
        val withCountry = VitacoraImportXlsx.writePetsSheet(
            listOf(
                VitacoraImportColumns.HEADERS.keys.map { key ->
                    when (key) {
                        VitacoraImportColumns.EXTERNAL_ID -> "OLD-1"
                        VitacoraImportColumns.NAME -> "Luna"
                        VitacoraImportColumns.SPECIES -> "Perro"
                        VitacoraImportColumns.SEX -> "Hembra"
                        VitacoraImportColumns.STATUS -> "Activo"
                        VitacoraImportColumns.COUNTRY -> "Argentina"
                        VitacoraImportColumns.ADMIN_AREA -> "Buenos Aires"
                        VitacoraImportColumns.LOCALITY -> "La Plata"
                        else -> ""
                    }
                }
            )
        )
        val old = VitacoraImportXlsx.inspect(withCountry, "old-v1.xlsx")
        assertEquals("Argentina", old.rows.first().values[VitacoraImportColumns.COUNTRY])
        val ctx = com.comunidapp.app.domain.vitacora.import.VitacoraImportAnalyzeContext(
            organizationId = "org-a",
            existingNormalizedExternalIds = emptySet(),
            locationNodes = argentinaLocationSeed(),
            verifiedOrganization = true,
            actorCanImport = true,
            mode = VitacoraImportMode.SELF_SERVICE
        )
        val fresh = VitacoraImportAnalyzer.analyze(inspected, ctx)
        assertTrue(fresh.second.first().issues.none { it.field == VitacoraImportColumns.COUNTRY && it.code == "COUNTRY_INVALID" })
        val legacy = VitacoraImportAnalyzer.analyze(old, ctx)
        assertTrue(legacy.second.first().issues.none { it.code == "COUNTRY_INVALID" })
    }

    @Test
    fun PHOTO_TRANSFORM_IS_SINGLE_SOURCE_OF_TRUTH() {
        assertTrue(PhotoCanvasMath.PREVIEW_MATCHES_PUBLISHED)
        val photo = PhotoCanvasTransform(offsetX = 0.12f, offsetY = -0.08f, scale = 1.4f, fitMode = PhotoCanvasFitMode.FIT.name)
        val composition = StoryComposition(photo = photo)
        val encoded = Json.encodeToString(StoryComposition.serializer(), composition)
        val decoded = Json { ignoreUnknownKeys = true }
            .decodeFromString(StoryComposition.serializer(), encoded)
        assertEquals(photo, decoded.photo)
        val legacy = Json { ignoreUnknownKeys = true }
            .decodeFromString(StoryComposition.serializer(), """{"overlays":[],"audio":{}}""")
        assertEquals(PhotoCanvasFitMode.FILL, legacy.photo.mode)
        val composer = source("app/src/main/java/com/comunidapp/app/ui/screens/social/SocialComposerScreens.kt")
        val viewer = source("app/src/main/java/com/comunidapp/app/ui/screens/social/StoryViewerScreen.kt")
        assertTrue(composer.contains("StoryPhotoCanvas("))
        assertTrue(viewer.contains("StoryPhotoCanvas("))
        assertTrue(composer.contains("detectTransformGestures") || source("app/src/main/java/com/comunidapp/app/ui/screens/social/StoryPhotoCanvas.kt").contains("detectTransformGestures"))
        val canvas = source("app/src/main/java/com/comunidapp/app/ui/screens/social/StoryPhotoCanvas.kt")
        assertTrue(canvas.contains("detectTransformGestures"))
        assertTrue(canvas.contains("onDoubleTap"))
        assertTrue(canvas.contains("Rellenar"))
        assertTrue(canvas.contains("Ajustar"))
        assertTrue(canvas.contains("Restablecer"))
        val payload = VitaCoraSocialMomentCodec.encode("story-1", encoded, "media://1")
        val decodedPayload = VitaCoraSocialMomentCodec.decode(payload)
        assertEquals("story-1", decodedPayload?.contentId)
        assertEquals(encoded, decodedPayload?.compositionJson)
        val oldBody = VitaCoraSocialMomentCodec.decode("plain-content-id")
        assertEquals("plain-content-id", oldBody?.contentId)
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
