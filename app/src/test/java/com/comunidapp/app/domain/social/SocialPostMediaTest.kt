package com.comunidapp.app.domain.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialPostMediaTest {

    @Test
    fun encode_persistsLocationAndExtraAssets() {
        val json = SocialPostMedia.encode(
            locationLabel = "San Vicente, Buenos Aires",
            extraMediaAssetIds = listOf("asset-2", "asset-3"),
            postType = "LOST_FOUND"
        )
        assertEquals("San Vicente, Buenos Aires", SocialPostMedia.locationLabel(json))
        assertEquals("LOST_FOUND", SocialPostMedia.postType(json))
        assertEquals(listOf("asset-2", "asset-3"), SocialPostMedia.extraMediaAssetIds(json))
    }

    @Test
    fun isVideoMedia_detectsReelAndMime() {
        assertTrue(
            SocialPostMedia.isVideoMedia(
                com.comunidapp.app.data.model.PostType.REEL,
                "video/mp4",
                "https://x/reels/a.mp4"
            )
        )
        assertTrue(
            SocialPostMedia.isVideoMedia(
                com.comunidapp.app.data.model.PostType.GENERAL,
                "video/mp4",
                "https://x/file"
            )
        )
        org.junit.Assert.assertFalse(
            SocialPostMedia.isVideoMedia(
                com.comunidapp.app.data.model.PostType.GENERAL,
                "image/jpeg",
                "https://x/photo.jpg"
            )
        )
    }

    @Test
    fun displayUrls_keepsPrimaryThenExtras() {
        assertEquals(
            listOf("https://a", "https://b", "https://c"),
            SocialPostMedia.displayUrls("https://a", listOf("https://b", "https://c"))
        )
        assertEquals(
            listOf("https://a", "https://b", "https://c"),
            SocialPostMedia.displayUrls("https://a", listOf("https://a", "https://b", "https://c"))
        )
        assertEquals(listOf("https://only"), SocialPostMedia.displayUrls("https://only", emptyList()))
    }

    @Test
    fun repositoryRefresh_mapsPrimaryPlusExtraMediaToThreeUrls() {
        val repo = com.comunidapp.app.ui.UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/data/repository/CanonicalConsumerRepositories.kt"
        ).readText()
        assertTrue(repo.contains("extraMediaUrls(row.extraMedia)"))
        assertTrue(repo.contains("SocialPostMedia.displayUrls("))
        assertTrue(repo.contains("@SerialName(\"extra_media\")"))
    }

    @Test
    fun encode_andWithPetIds_roundTripMultiplePets() {
        val json = SocialPostMedia.encode(
            locationLabel = "San Vicente",
            petIds = listOf("pet-a", "pet-b", "pet-a")
        )
        assertEquals(listOf("pet-a", "pet-b"), SocialPostMedia.petIds(json))
        val updated = SocialPostMedia.withPetIds(json, listOf("pet-c"))
        assertEquals(listOf("pet-c"), SocialPostMedia.petIds(updated))
        assertEquals("San Vicente", SocialPostMedia.locationLabel(updated))
        assertTrue(SocialPostMedia.petIds(SocialPostMedia.withPetIds(updated, emptyList())).isEmpty())
    }

    @Test
    fun maxImages_isDocumentedCeiling() {
        assertTrue(SocialPostMedia.MAX_IMAGES >= 3)
        assertTrue(SocialPostMedia.MAX_IMAGES <= 10)
    }
}
