package com.comunidapp.app.data.files

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyFileReferenceAdapterTest {

    @Test
    fun `legacy URL is read only and grants no ownership`() {
        val reference = LegacyFileReferenceAdapter
            .fromPublicUrl("https://legacy.example/media/photo.jpg")
            .getOrThrow()

        assertFalse(LegacyFileReferenceAdapter.grantsOwnership(reference))
        assertFalse(LegacyFileReferenceAdapter.allowsNewUpload(reference))
    }

    @Test
    fun `content URI is rejected`() {
        assertTrue(LegacyFileReferenceAdapter.fromPublicUrl("content://media/photo").isFailure)
    }

    @Test
    fun `base64 data URI is rejected`() {
        assertTrue(
            LegacyFileReferenceAdapter
                .fromPublicUrl("data:image/png;base64,AAAA")
                .isFailure
        )
    }
}
