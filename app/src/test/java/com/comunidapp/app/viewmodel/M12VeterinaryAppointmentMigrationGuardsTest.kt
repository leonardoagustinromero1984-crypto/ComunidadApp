package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer M12 Bloque 3 — guardas estáticas de la migración 047 y de las fuentes Android
 * (sin Supabase real). Verifica hardening, ausencia de pagos/historia clínica y RPCs.
 */
class M12VeterinaryAppointmentMigrationGuardsTest {

    private val migration047 = "047_m12_veterinary_appointments_and_availability.sql"

    private val b3Tables = listOf(
        "veterinary_schedule_settings",
        "veterinary_availability_rules",
        "veterinary_availability_exceptions",
        "veterinary_appointments",
        "veterinary_appointment_status_history"
    )

    private val b3Rpcs = listOf(
        "m12_upsert_veterinary_schedule_settings",
        "m12_get_veterinary_schedule_settings",
        "m12_create_veterinary_availability_rule",
        "m12_update_veterinary_availability_rule",
        "m12_change_veterinary_availability_rule_status",
        "m12_create_veterinary_availability_exception",
        "m12_update_veterinary_availability_exception",
        "m12_change_veterinary_availability_exception_status",
        "m12_list_managed_veterinary_availability",
        "m12_list_available_veterinary_appointment_slots",
        "m12_request_veterinary_appointment",
        "m12_get_veterinary_appointment",
        "m12_list_my_veterinary_appointments",
        "m12_list_managed_veterinary_appointments",
        "m12_confirm_veterinary_appointment",
        "m12_reject_veterinary_appointment",
        "m12_cancel_my_veterinary_appointment",
        "m12_cancel_managed_veterinary_appointment",
        "m12_complete_veterinary_appointment",
        "m12_mark_veterinary_appointment_no_show",
        "m12_expire_veterinary_appointment",
        "m12_list_veterinary_appointment_history"
    )

    private fun migrationDir(): File {
        val candidates = listOf(
            File("supabase/migrations"),
            File("../supabase/migrations"),
            File("../../supabase/migrations")
        )
        return candidates.firstOrNull { it.isDirectory } ?: error("migrations missing")
    }

    private fun read047(): String = File(migrationDir(), migration047).readText()

    private fun findApp(vararg rel: String): File {
        rel.forEach { r ->
            listOf(File(r), File("../$r"), File("../../$r")).forEach { if (it.isFile) return it }
        }
        error("missing ${rel.first()}")
    }

    @Test
    fun migration_047_present() {
        assertTrue(File(migrationDir(), migration047).isFile)
    }

    @Test
    fun migrations_040_to_046_intact() {
        val names = migrationDir().listFiles()?.map { it.name }.orEmpty()
        assertTrue(names.any { it.startsWith("040_") })
        assertTrue(names.any { it.startsWith("041_") })
        listOf(
            "042_m11_shelter_operations_core.sql",
            "043_m11_shelter_campaigns_and_aid.sql",
            "044_m11_harden_campaign_aid_permissions.sql",
            "045_m11_shelter_emergencies_events_reports.sql"
        ).forEach { assertTrue("missing $it", names.contains(it)) }
        val m046 = names.filter { it.startsWith("046_") }
        assertTrue("046 debe ser M12 veterinary", m046.any { it.contains("m12") && it.contains("veterinary") })
    }

    @Test
    fun five_tables_with_if_not_exists_and_rls() {
        val sql = read047().lowercase()
        b3Tables.forEach {
            assertTrue("falta create table $it", sql.contains("create table if not exists public.$it"))
            assertTrue("falta rls $it", sql.contains("alter table public.$it enable row level security"))
        }
    }

    @Test
    fun tables_revoke_all_privileges() {
        val sql = read047().lowercase()
        assertTrue(sql.contains("revoke all privileges on table"))
        b3Tables.forEach {
            assertTrue(
                "falta revoke all privileges $it",
                sql.contains("revoke all privileges on table public.$it from authenticated")
            )
        }
    }

    @Test
    fun rpc_grants_only_to_authenticated() {
        val sql = read047()
        assertTrue(sql.contains("grant execute on function public.m12_"))
        assertTrue(sql.contains("to authenticated;"))
        // Ninguna RPC m12_ concede EXECUTE a public/anon/service_role.
        val leaked = Regex(
            "grant\\s+execute\\s+on\\s+function\\s+public\\.m12_[a-z_]+\\([^)]*\\)\\s+to\\s+(public|anon|service_role)",
            RegexOption.IGNORE_CASE
        )
        assertFalse("RPC m12_ no debe conceder execute salvo authenticated", leaked.containsMatchIn(sql))
    }

    @Test
    fun helpers_revoked_and_security_definer() {
        val sql = read047()
        assertTrue(sql.contains("revoke all on function public._m12_"))
        assertTrue(sql.contains("security definer"))
        assertTrue(sql.contains("search_path = public") || sql.contains("search_path=public"))
        assertTrue(sql.contains("auth.uid()"))
    }

    @Test
    fun no_required_param_after_default_in_rpc_signatures() {
        val sql = read047()
        val headers = Regex(
            "create or replace function (public\\.m12_[a-z_]+)\\s*\\(([\\s\\S]*?)\\)\\s*returns",
            RegexOption.IGNORE_CASE
        ).findAll(sql)
        headers.forEach { match ->
            val fn = match.groupValues[1]
            val params = match.groupValues[2].trim()
            if (params.isEmpty()) return@forEach
            val parts = params.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            var seenDefault = false
            parts.forEach { p ->
                val hasDefault = p.contains(" default ", ignoreCase = true)
                if (hasDefault) {
                    seenDefault = true
                } else {
                    assertFalse("$fn: parámetro requerido después de DEFAULT ($p)", seenDefault)
                }
            }
        }
    }

    @Test
    fun no_drop_table_or_truncate() {
        val sql = read047().lowercase()
        assertFalse(Regex("drop\\s+table").containsMatchIn(sql))
        assertFalse(sql.contains("truncate"))
    }

    @Test
    fun no_pregenerated_slots_table() {
        val sql = read047().lowercase()
        // El nombre aparece como RPC (…_appointment_slots) pero NUNCA como tabla.
        assertFalse(
            Regex("create\\s+table[^;]*veterinary_appointment_slots").containsMatchIn(sql)
        )
    }

    @Test
    fun no_payments_or_clinical_tables() {
        val sql = read047().lowercase()
        assertFalse("no mercadopago", sql.contains("mercadopago"))
        // Sin columnas de cobro.
        assertFalse(
            "no columnas de pago",
            Regex("(price|amount|payment)\\s+(numeric|integer|money|bigint|decimal)").containsMatchIn(sql)
        )
        // Sin tablas clínicas (diagnóstico/receta/historia).
        assertFalse(
            "no tablas clínicas",
            Regex("create\\s+table[^;]*(diagnosis|prescription|health_record|medical_record|clinical_history)")
                .containsMatchIn(sql)
        )
    }

    @Test
    fun legacy_bookings_and_service_profiles_not_dropped() {
        val sql = read047().lowercase()
        assertFalse(
            Regex("drop\\s+table\\s+[^;]*service_profiles").containsMatchIn(sql)
        )
        assertFalse(
            Regex("drop\\s+table\\s+[^;]*bookings").containsMatchIn(sql)
        )
    }

    @Test
    fun permissions_schedule_and_appointment_seeded() {
        val sql = read047()
        listOf(
            "veterinary.schedule.read",
            "veterinary.schedule.manage",
            "veterinary.appointment.read",
            "veterinary.appointment.request",
            "veterinary.appointment.manage"
        ).forEach { assertTrue("falta permiso $it", sql.contains(it)) }
        assertTrue(sql.contains("veterinary.schedule.%"))
        assertTrue(sql.contains("veterinary.appointment.%"))
    }

    @Test
    fun all_22_rpc_names_present() {
        val sql = read047()
        b3Rpcs.forEach { assertTrue("falta RPC $it", sql.contains("public.$it")) }
        assertTrue(b3Rpcs.size == 22)
    }

    @Test
    fun for_update_and_m08_responsibility_used() {
        val sql = read047().lowercase()
        assertTrue("concurrencia FOR UPDATE", sql.contains("for update"))
        assertTrue("autoridad M08", sql.contains("m08_actor_has_active_responsibility"))
    }

    @Test
    fun transaction_wrapped() {
        val sql = read047()
        assertTrue(sql.contains("begin;"))
        assertTrue(sql.trim().endsWith("commit;") || sql.contains("\ncommit;"))
    }

    @Test
    fun no_service_role_in_android_b3_sources() {
        val combined = buildString {
            append(
                findApp(
                    "app/src/main/java/com/comunidapp/app/data/remote/supabase/m12/SupabaseVeterinaryM12RemoteDataSource.kt"
                ).readText()
            )
            append(
                findApp(
                    "app/src/main/java/com/comunidapp/app/data/repository/VeterinaryAppointmentRepositories.kt"
                ).readText()
            )
            append(
                findApp(
                    "app/src/main/java/com/comunidapp/app/data/model/VeterinaryAppointmentModels.kt"
                ).readText()
            )
            append(
                findApp(
                    "app/src/main/java/com/comunidapp/app/data/remote/supabase/m12/M12VeterinaryErrorMapper.kt"
                ).readText()
            )
        }
        assertFalse(combined.contains("service_role"))
        assertFalse(combined.contains("SERVICE_ROLE"))
    }
}
