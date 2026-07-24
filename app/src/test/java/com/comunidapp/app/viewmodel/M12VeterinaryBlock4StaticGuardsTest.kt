package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer M12 Bloque 4 — guardas estáticas (sin SQL nuevo, sin Supabase real).
 *
 * Verifica que el Bloque 4 se cierra localmente: sin migración 048, migraciones 040–047 intactas,
 * sin WorkManager ni service_role en las fuentes de turnos/recordatorios, sin pagos ni historia
 * clínica, hotfix de autenticación preservado, rutas de seguimiento presentes, hooks M06 y errores
 * tipados del Bloque 4 disponibles, y sin afirmar el smoke remoto del Bloque 3 en la documentación.
 */
class M12VeterinaryBlock4StaticGuardsTest {

    private fun migrationDir(): File {
        val candidates = listOf(
            File("supabase/migrations"),
            File("../supabase/migrations"),
            File("../../supabase/migrations")
        )
        return candidates.firstOrNull { it.isDirectory } ?: error("supabase/migrations not found")
    }

    private fun findApp(vararg rel: String): File {
        rel.forEach { r ->
            listOf(File(r), File("../$r"), File("../../$r")).forEach { if (it.isFile) return it }
        }
        error("missing ${rel.first()}")
    }

    private fun read(vararg rel: String): String = findApp(*rel).readText()

    private val appointmentSources = listOf(
        "app/src/main/java/com/comunidapp/app/data/model/VeterinaryAppointmentModels.kt",
        "app/src/main/java/com/comunidapp/app/data/repository/VeterinaryAppointmentRepositories.kt",
        "app/src/main/java/com/comunidapp/app/viewmodel/VeterinaryAppointmentViewModels.kt",
        "app/src/main/java/com/comunidapp/app/ui/screens/veterinary/VeterinaryAppointmentScreens.kt",
        "app/src/main/java/com/comunidapp/app/data/remote/supabase/m12/M12VeterinaryErrorMapper.kt"
    )

    private val createdDocs = listOf(
        "docs/02-arquitectura/M12-recordatorios-endurecimiento-seguimiento.md",
        "docs/05-operacion/M12-validacion-bloque-4.md"
    )

    // --- Migraciones ---

    @Test
    fun no_migration_048_file() {
        val names = migrationDir().listFiles()?.map { it.name }.orEmpty()
        assertFalse(
            "El Bloque 4 no debe crear la migración 048",
            names.any { it.startsWith("048") }
        )
    }

    @Test
    fun migrations_040_to_047_present() {
        val names = migrationDir().listFiles()?.map { it.name }.orEmpty()
        (40..47).forEach { n ->
            assertTrue("falta migración 0$n", names.any { it.startsWith("0${n}_") })
        }
        assertTrue(
            "047 debe ser M12 veterinary appointments",
            names.any { it.startsWith("047_") && it.contains("m12") && it.contains("veterinary") }
        )
    }

    // --- Sin WorkManager ni service_role ---

    @Test
    fun no_workmanager_in_appointment_sources() {
        appointmentSources.forEach { path ->
            val text = read(path)
            assertFalse("$path no debe usar WorkManager", text.contains("WorkManager"))
            assertFalse("$path no debe importar androidx.work", text.contains("androidx.work"))
        }
    }

    @Test
    fun no_service_role_in_appointment_and_reminder_sources() {
        appointmentSources.forEach { path ->
            val text = read(path)
            assertFalse("$path no debe usar service_role", text.contains("service_role"))
            assertFalse("$path no debe usar SERVICE_ROLE", text.contains("SERVICE_ROLE"))
        }
    }

    // --- Sin pagos ni historia clínica ---

    @Test
    fun no_payments_or_clinical_records_in_block4_sources() {
        appointmentSources.forEach { path ->
            val text = read(path).lowercase()
            assertFalse("$path no debe mencionar mercadopago", text.contains("mercadopago"))
            assertFalse("$path no debe mencionar health_record", text.contains("health_record"))
            assertFalse("$path no debe mencionar medical_record", text.contains("medical_record"))
            assertFalse("$path no debe mencionar healthrecord", text.contains("healthrecord"))
            assertFalse("$path no debe mencionar medicalrecord", text.contains("medicalrecord"))
        }
    }

    // --- Hotfix de autenticación preservado ---

    @Test
    fun auth_hotfix_files_present() {
        assertTrue(
            findApp("app/src/main/java/com/comunidapp/app/core/config/SupabaseUrlPolicy.kt").isFile
        )
        assertTrue(
            findApp("app/src/main/java/com/comunidapp/app/core/config/AuthConfigDiagnostics.kt").isFile
        )
    }

    // --- Rutas de seguimiento ---

    @Test
    fun follow_up_routes_present() {
        val routes = read("app/src/main/java/com/comunidapp/app/navigation/NavRoutes.kt")
        assertTrue(routes.contains("my_veterinary_appointments"))
        assertTrue(routes.contains("veterinary_appointment_detail/{appointmentId}"))
        assertTrue(routes.contains("veterinary_appointment_management/{appointmentId}"))
    }

    // --- Hooks M06 del Bloque 4 ---

    @Test
    fun m06_reminder_hooks_present() {
        val models = read("app/src/main/java/com/comunidapp/app/data/model/VeterinaryAppointmentModels.kt")
        assertTrue(models.contains("REMINDER_24H_DUE"))
        assertTrue(models.contains("REMINDER_2H_DUE"))
        assertTrue(models.contains("REMINDER_CANCELLED"))
        assertTrue(models.contains("VETERINARY_APPOINTMENT_REMINDER_24H_DUE"))
        assertTrue(models.contains("VETERINARY_APPOINTMENT_REMINDER_2H_DUE"))
        assertTrue(models.contains("VETERINARY_APPOINTMENT_REMINDER_CANCELLED"))
    }

    // --- Errores tipados del Bloque 4 ---

    @Test
    fun block4_error_codes_present_in_mapper() {
        val mapper = read("app/src/main/java/com/comunidapp/app/data/remote/supabase/m12/M12VeterinaryErrorMapper.kt")
        listOf(
            "VETERINARY_REMINDER_NOT_ELIGIBLE",
            "VETERINARY_REMINDER_ALREADY_SCHEDULED",
            "VETERINARY_REMINDER_INFRASTRUCTURE_UNAVAILABLE",
            "VETERINARY_APPOINTMENT_RETRY_CONFLICT",
            "VETERINARY_APPOINTMENT_TIMEZONE_CHANGED",
            "VETERINARY_APPOINTMENT_SERVICE_INACTIVE",
            "VETERINARY_APPOINTMENT_PROFESSIONAL_INACTIVE",
            "VETERINARY_APPOINTMENT_METRICS_INVALID_RANGE"
        ).forEach { assertTrue("falta error $it en el mapper", mapper.contains(it)) }
    }

    // --- Documentación creada ---

    @Test
    fun created_docs_present() {
        createdDocs.forEach { assertTrue("falta doc $it", findApp(it).isFile) }
    }

    @Test
    fun created_docs_do_not_claim_block3_remote_pass() {
        val forbidden = Regex("bloque\\s*3\\s+remoto\\s+pass", RegexOption.IGNORE_CASE)
        createdDocs.forEach { path ->
            val text = read(path)
            assertFalse(
                "$path no debe afirmar 'BLOQUE 3 REMOTO PASS'",
                forbidden.containsMatchIn(text)
            )
        }
    }

    @Test
    fun created_docs_state_local_close_not_full_m12_close() {
        createdDocs.forEach { path ->
            val text = read(path)
            assertTrue(
                "$path debe indicar cierre local del Bloque 4",
                text.contains("M12 BLOQUE 4 CERRADO LOCALMENTE")
            )
            assertFalse(
                "$path no debe declarar M12 CERRADO",
                Regex("(^|\\n)\\s*M12 CERRADO").containsMatchIn(text)
            )
        }
    }
}
