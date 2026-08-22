package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LostFoundMigration082StaticGuardsTest {
    private fun repoRoot(): File = listOf(File("."), File(".."), File("../.."))
        .first { File(it, "supabase/migrations").isDirectory }

    private fun sql082(): String =
        File(repoRoot(), "supabase/migrations/082_lost_found_public_code_pgcrypto_schema.sql").readText()

    @Test
    fun hotfixQualifiesPgcryptoAndDoesNotRewrite081() {
        val sql = sql082()
        assertTrue(sql.contains("extensions.gen_random_bytes"))
        assertTrue(sql.contains("create extension if not exists pgcrypto with schema extensions"))
        assertTrue(sql.contains("_web_generate_public_code"))
        assertTrue(sql.contains("lost_found_posts"))
        assertFalse(Regex("""encode\(\s*gen_random_bytes\(""").containsMatchIn(sql))
        val historic = File(repoRoot(), "supabase/migrations/081_web_public_shareable_pages.sql").readText()
        assertTrue(historic.contains("encode(gen_random_bytes(16)"))
    }
}
