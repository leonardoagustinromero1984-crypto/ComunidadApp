package com.comunidapp.app.data.remote.supabase.m08

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * LeoVer M08 Etapa 4C — guards de integración local (sin secrets, sin remoto).
 */
class M08Stage4CIntegrationGuardsTest {

    private fun repoRoot(): File = listOf(
        File("."),
        File(".."),
        File("../..")
    ).first { File(it, "supabase/migrations").isDirectory }

    @Test
    fun highestMigrationIs036_and037Absent() {
        val mig = File(repoRoot(), "supabase/migrations")
        val nums = mig.listFiles()!!
            .map { it.name }
            .filter { it.matches(Regex("^\\d{3}_.*\\.sql$")) }
            .map { it.substring(0, 3).toInt() }
        assertTrue(nums.contains(36))
        assertFalse(nums.contains(37))
        assertTrue(nums.maxOrNull() == 36)
    }

    @Test
    fun productionPathUsesLegacyAdapterAndRpcNames() {
        val adapter = File(
            repoRoot(),
            "app/src/main/java/com/comunidapp/app/data/repository/LegacyPetRepositoryAdapter.kt"
        ).readText()
        val ds = File(
            repoRoot(),
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m08/SupabasePetM08RemoteDataSource.kt"
        ).readText()
        val provider = File(
            repoRoot(),
            "app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt"
        ).readText()

        assertTrue(provider.contains("LegacyPetRepositoryAdapter"))
        assertFalse(adapter.contains("PetSupabaseDataSource"))
        for (rpc in listOf(
            "m08_list_accessible_pets",
            "m08_create_pet_with_principal",
            "m08_update_pet_profile",
            "m08_update_pet_health",
            "m08_archive_pet",
            "m08_set_pet_avatar_asset",
            "m08_get_pet_access_context"
        )) {
            assertTrue("$rpc missing", ds.contains(rpc) || adapter.contains(rpc))
        }
        assertFalse(ds.contains("service_role"))
        assertFalse(adapter.contains("service_role"))
    }

    @Test
    fun legacyPetDataSourceWritesAreDisabled() {
        val src = File(
            repoRoot(),
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/SupabaseDataSources.kt"
        ).readText()
        assertFalse(src.contains("from(SupabaseTables.PETS).insert"))
        assertFalse(src.contains("from(SupabaseTables.PETS).update"))
        assertFalse(src.contains("from(SupabaseTables.PETS).delete"))
        assertTrue(
            src.contains("PET_DIRECT_INSERT_FORBIDDEN") ||
                src.contains("PET_DIRECT_UPDATE_FORBIDDEN") ||
                src.contains("UnsupportedOperationException")
        )
    }

    @Test
    fun debugCleartextOnlyInDebugSourceSet() {
        val main = File(repoRoot(), "app/src/main/res/xml/network_security_config.xml").readText()
        val debug = File(repoRoot(), "app/src/debug/res/xml/network_security_config.xml").readText()
        assertTrue(main.contains("cleartextTrafficPermitted=\"false\""))
        assertFalse(main.contains("10.0.2.2"))
        assertTrue(debug.contains("10.0.2.2"))
        assertTrue(debug.contains("cleartextTrafficPermitted=\"true\""))
    }

    @Test
    fun publicProfilePetsRpcAbsentAndSmokeFixturesLocalOnly() {
        val mig036 = File(
            repoRoot(),
            "supabase/migrations/036_m08_pet_repository_compatibility_rpcs.sql"
        ).readText()
        assertFalse(
            Regex(
                "create\\s+(or\\s+replace\\s+)?function\\s+public\\.m08_list_public_profile_pets",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(mig036)
        )
        val fixtures = File(repoRoot(), "scripts/sql/m08_prepare_local_smoke_fixtures.sql").readText()
        assertTrue(fixtures.contains("LOCAL TEST DATA ONLY"))
        assertFalse(Regex("(?i)\\bservice_role\\s*[:=]").containsMatchIn(fixtures))
    }
}
