package com.comunidapp.app.domain.canonical

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Qa01StagingGuardsTest {

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath"),
            File(System.getProperty("user.dir"), relativePath),
            File(System.getProperty("user.dir"), "../$relativePath")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("SOURCE_NOT_FOUND:$relativePath")
    }

    @Test
    fun qaSeedIsNotAMigrationAndGuardsStagingRef() {
        val seed = sourceFile("infra/supabase-canonical/qa/qa01_seed.sql").readText()
        val ps1 = sourceFile("infra/supabase-canonical/qa/qa01.ps1").readText()
        val migration = sourceFile(
            "infra/supabase-canonical/supabase/migrations/20260816220000_1027_list_providers_coverage.sql"
        ).readText()
        assertTrue(seed.contains("qa_public"))
        assertTrue(seed.contains("QA •"))
        assertFalse(migration.contains("QA •"))
        assertTrue(ps1.contains("tobqbddfcyitwgbkthhy"))
        assertTrue(ps1.contains("wystsapjfpdtoprlmizz"))
        assertTrue(ps1.contains("QA_PASSWORD_REQUIRED"))
        assertFalse(ps1.contains("service_role_key ="))
    }

    @Test
    fun comunidadUsesCanonicalServiceRepositoryOnStaging() {
        val provider = sourceFile("app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt").readText()
        val block = provider.substringAfter("val serviceRepository").substringBefore("val friendRepository")
        assertTrue(block.contains("CanonicalServiceRepository"))
        assertTrue(block.contains("MockServiceRepository()"))
    }
}
