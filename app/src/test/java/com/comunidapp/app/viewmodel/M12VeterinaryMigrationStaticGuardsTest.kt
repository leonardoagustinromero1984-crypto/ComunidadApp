package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer M12 Bloque 2 — guardas estáticas de migración 046.
 */
class M12VeterinaryMigrationStaticGuardsTest {

    private fun migrationDir(): File {
        val candidates = listOf(
            File("supabase/migrations"),
            File("../supabase/migrations"),
            File("../../supabase/migrations")
        )
        return candidates.firstOrNull { it.isDirectory } ?: error("migrations missing")
    }

    private fun read046(): String =
        File(migrationDir(), "046_m12_veterinary_profiles_and_services.sql").readText()

    private fun findApp(vararg rel: String): File {
        rel.forEach { r ->
            listOf(File(r), File("../$r"), File("../../$r")).forEach { if (it.isFile) return it }
        }
        error("missing ${rel.first()}")
    }

    @Test
    fun migration_046_present() {
        assertTrue(File(migrationDir(), "046_m12_veterinary_profiles_and_services.sql").isFile)
    }

    @Test
    fun migrations_040_to_045_intact() {
        val names = migrationDir().listFiles()?.map { it.name }.orEmpty()
        assertTrue(names.any { it.startsWith("040_") })
        assertTrue(names.any { it.startsWith("041_") })
        listOf(
            "042_m11_shelter_operations_core.sql",
            "043_m11_shelter_campaigns_and_aid.sql",
            "044_m11_harden_campaign_aid_permissions.sql",
            "045_m11_shelter_emergencies_events_reports.sql"
        ).forEach { assertTrue(names.contains(it)) }
    }

    @Test
    fun six_tables_and_rls() {
        val sql = read046().lowercase()
        listOf(
            "veterinary_clinic_profiles",
            "veterinary_professionals",
            "veterinary_clinic_professionals",
            "veterinary_professional_specialties",
            "veterinary_services",
            "veterinary_opening_hours"
        ).forEach { assertTrue(sql.contains("create table if not exists public.$it")) }
        assertTrue(sql.contains("enable row level security"))
    }

    @Test
    fun policies_idempotent_and_no_client_dml() {
        val sql = read046().lowercase()
        assertTrue(sql.contains("drop policy if exists"))
        assertTrue(sql.contains("with check (false)") || sql.contains("with check(false)"))
        assertTrue(sql.contains("revoke all privileges on table"))
        assertFalse(Regex("grant\\s+(insert|update|delete)\\b").containsMatchIn(sql))
    }

    @Test
    fun rpc_grants_and_helpers() {
        val sql = read046()
        assertTrue(sql.contains("grant execute on function public.m12_"))
        assertTrue(sql.contains("revoke all on function public._m12_"))
        assertTrue(sql.contains("security definer"))
        assertTrue(sql.contains("search_path = public") || sql.contains("search_path=public"))
        assertTrue(sql.contains("auth.uid()"))
    }

    @Test
    fun no_drop_table_truncate_prices_appointments_clinical() {
        val sql = read046().lowercase()
        assertFalse(Regex("drop\\s+table").containsMatchIn(sql))
        assertFalse(sql.contains("truncate"))
        assertFalse(sql.contains("price") && sql.contains("amount"))
        assertFalse(sql.contains("health_record") || sql.contains("medical_record"))
        // requires_appointment is a boolean flag, not an appointments system
        assertTrue(sql.contains("requires_appointment"))
    }

    @Test
    fun permissions_seeded_and_legacy_intact() {
        val sql = read046()
        listOf(
            "veterinary.profile.read",
            "veterinary.profile.manage",
            "veterinary.professional.read",
            "veterinary.professional.manage",
            "veterinary.service.read",
            "veterinary.service.manage"
        ).forEach { assertTrue(sql.contains(it)) }
        assertTrue(sql.contains("service_profiles"))
        assertFalse(Regex("drop\\s+table\\s+.*service_profiles", RegexOption.IGNORE_CASE).containsMatchIn(sql))
    }

    @Test
    fun no_service_role_android_and_param_order_safe() {
        val android = findApp(
            "app/src/main/java/com/comunidapp/app/data/repository/SupabaseVeterinaryRepositories.kt"
        ).readText() + findApp(
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m12/SupabaseVeterinaryM12RemoteDataSource.kt"
        ).readText()
        assertFalse(android.contains("service_role"))
        assertFalse(android.contains("SERVICE_ROLE"))
        // Migration should not place required params after DEFAULT (spot-check create draft)
        val sql = read046()
        assertTrue(sql.contains("m12_create_veterinary_clinic_draft"))
        assertTrue(sql.contains("begin;"))
        assertTrue(sql.trim().endsWith("commit;") || sql.contains("\ncommit;"))
    }

    @Test
    fun client_rpcs_present() {
        val sql = read046()
        listOf(
            "m12_create_veterinary_clinic_draft",
            "m12_list_public_veterinary_clinics",
            "m12_review_veterinary_clinic_verification",
            "m12_link_veterinary_professional",
            "m12_replace_veterinary_opening_hours",
            "m12_create_veterinary_service"
        ).forEach { assertTrue(sql.contains(it)) }
    }
}
