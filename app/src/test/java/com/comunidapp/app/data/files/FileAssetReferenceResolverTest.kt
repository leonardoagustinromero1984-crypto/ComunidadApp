package com.comunidapp.app.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileAssetReferenceResolverTest {

    private val resolver = FileAssetReferenceResolver()
    private val uuid = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun `UUID is accepted as logical asset ID`() {
        assertTrue(resolver.isUuid(uuid))
        assertTrue(resolver.isLogicalAssetId(uuid))
        assertEquals(uuid, resolver.toM04LogicalRef(uuid))
    }

    @Test
    fun `data URI is rejected`() {
        assertTrue(resolver.rejectIfBase64OrDataUri("data:image/png;base64,AAAA").isFailure)
    }

    @Test
    fun `content URI is rejected`() {
        assertTrue(resolver.rejectIfBase64OrDataUri("content://media/photo").isFailure)
        assertFalse(resolver.isLogicalAssetId("content://media/photo"))
    }
}
