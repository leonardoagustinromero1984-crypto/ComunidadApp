package com.comunidapp.app.domain.canonical

import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalMediaTest {

    @Test
    fun petAvatarUsesPublicMediaBucket() {
        assertEquals(
            CanonicalMedia.BUCKET_PUBLIC,
            CanonicalMedia.physicalBucketForPurpose(FileAssetPurpose.PET_AVATAR)
        )
    }

    @Test
    fun publicUrlIsRuntimeOnlyAndNeverLooksLikeSignedToken() {
        val url = CanonicalMedia.publicObjectUrl(
            supabaseUrl = "https://tobqbddfcyitwgbkthhy.supabase.co",
            bucket = "public-media",
            objectPath = "users/u1/pets/p1/asset/pet.jpg"
        )
        assertTrue(url.startsWith("https://tobqbddfcyitwgbkthhy.supabase.co/storage/v1/object/public/public-media/"))
        assertFalse(url.contains("token="))
        assertFalse(url.contains("signed"))
    }

    @Test
    fun privateBucketIsNotExposedAsPublicUrl() {
        val denied = runCatching {
            CanonicalMedia.publicObjectUrl(
                "https://example.supabase.co",
                CanonicalMedia.BUCKET_PRIVATE,
                "users/u1/secret.jpg"
            )
        }
        assertTrue(denied.isFailure)
    }

    @Test
    fun publicMediaMapsToPublicDisplayVisibility() {
        assertEquals(
            FileAssetVisibility.PUBLIC,
            CanonicalMedia.displayVisibility("public-media", "PRIVATE")
        )
        assertEquals(
            FileAssetVisibility.OWNER_ONLY,
            CanonicalMedia.displayVisibility("private-media", "PRIVATE")
        )
    }
}
