package com.comunidapp.app.data.remote.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostsSchemaCompatTest {

    @Test
    fun detectsMissingExpiresAtInSchemaCache() {
        val error = RuntimeException(
            "Could not find the 'expires_at' column of 'posts' in the schema cache"
        )
        assertTrue(error.isMissingPostsSchemaColumn("expires_at"))
        assertFalse(error.isMissingPostsSchemaColumn("author_id"))
    }

    @Test
    fun legacyRow_keepsCoreFieldsWithoutSchema078() {
        val full = PostRow(
            id = "p1",
            authorId = "u1",
            authorName = "Ana",
            type = "STORY",
            title = "Historia",
            content = "Hola",
            petId = "pet-1",
            expiresAt = "2026-08-08T00:00:00Z"
        )
        val legacy = full.toLegacy()
        assertEquals("p1", legacy.id)
        assertEquals("STORY", legacy.type)
        assertEquals("Ana", legacy.authorName)
        assertEquals("Hola", legacy.content)
    }
}
