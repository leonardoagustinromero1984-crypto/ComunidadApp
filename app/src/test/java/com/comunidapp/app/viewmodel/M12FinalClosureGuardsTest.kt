package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer M12 — guardas estáticas del cierre técnico final (smoke diferido).
 *
 * Estado permitido:
 *   M12 CIERRE TÉCNICO LOCAL COMPLETADO / SMOKE PENDIENTE EXTERNO / CIERRE OFICIAL PENDIENTE
 *
 * Este test NO ejecuta SQL ni Supabase real. Verifica que los Bloques 1–4 quedan cerrados
 * localmente, que las migraciones 040–047 están intactas (sin 048), que no hay service_role ni
 * WorkManager ni pagos/historia clínica en las fuentes M12, y que la documentación de cierre
 * documenta el smoke como PENDIENTE EXTERNO sin declarar "M12 CERRADO".
 */
class M12FinalClosureGuardsTest {

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

    // Fuentes Android M12 (veterinary + paquete m12) que deben permanecer limpias.
    private val m12AndroidSources = listOf(
        "app/src/main/java/com/comunidapp/app/data/model/VeterinaryModels.kt",
        "app/src/main/java/com/comunidapp/app/data/model/VeterinaryAppointmentModels.kt",
        "app/src/main/java/com/comunidapp/app/data/repository/VeterinaryRepositories.kt",
        "app/src/main/java/com/comunidapp/app/data/repository/VeterinaryBlock2Repositories.kt",
        "app/src/main/java/com/comunidapp/app/data/repository/VeterinaryAppointmentRepositories.kt",
        "app/src/main/java/com/comunidapp/app/data/repository/SupabaseVeterinaryRepositories.kt",
        "app/src/main/java/com/comunidapp/app/viewmodel/VeterinaryViewModels.kt",
        "app/src/main/java/com/comunidapp/app/viewmodel/VeterinaryAppointmentViewModels.kt",
        "app/src/main/java/com/comunidapp/app/ui/screens/veterinary/VeterinaryAppointmentScreens.kt",
        "app/src/main/java/com/comunidapp/app/data/remote/supabase/m12/SupabaseVeterinaryM12RemoteDataSource.kt",
        "app/src/main/java/com/comunidapp/app/data/remote/supabase/m12/M12VeterinaryErrorMapper.kt"
    )

    private val appointmentSources = listOf(
        "app/src/main/java/com/comunidapp/app/data/repository/VeterinaryAppointmentRepositories.kt",
        "app/src/main/java/com/comunidapp/app/viewmodel/VeterinaryAppointmentViewModels.kt",
        "app/src/main/java/com/comunidapp/app/ui/screens/veterinary/VeterinaryAppointmentScreens.kt"
    )

    // Documentos de cierre final que DEBEN existir con el lenguaje correcto.
    private val cierreFinalDoc = "docs/03-modulos/M12-cierre-final.md"
    private val validacionFinalDoc = "docs/05-operacion/M12-validacion-final.md"
    private val matrizFinalDoc = "docs/02-arquitectura/M12-matriz-funcional-final.md"
    private val smokePendingDoc = "docs/05-operacion/M12-smoke-funcional-pendiente-cierre.md"

    private val newClosureDocs = listOf(cierreFinalDoc, validacionFinalDoc, matrizFinalDoc)

    // 1 — Fuentes B1–B4 presentes.
    @Test
    fun b1_to_b4_sources_present() {
        val models = read("app/src/main/java/com/comunidapp/app/data/model/VeterinaryModels.kt")
        assertTrue("B1: VeterinaryModels debe existir", models.contains("veterinary.profile"))
        assertTrue(findApp("app/src/main/java/com/comunidapp/app/data/repository/VeterinaryBlock2Repositories.kt").isFile)
        assertTrue(findApp("app/src/main/java/com/comunidapp/app/data/repository/VeterinaryAppointmentRepositories.kt").isFile)
        val apptModels = read("app/src/main/java/com/comunidapp/app/data/model/VeterinaryAppointmentModels.kt")
        assertTrue("B4: hooks de recordatorio", apptModels.contains("REMINDER_24H_DUE"))
        assertTrue("B4: hooks de recordatorio", apptModels.contains("REMINDER_CANCELLED"))
    }

    // 2 — Rutas de navegación.
    @Test
    fun nav_routes_present() {
        val routes = read("app/src/main/java/com/comunidapp/app/navigation/NavRoutes.kt")
        listOf(
            "veterinary_directory",
            "my_veterinary_clinics",
            "veterinary_book_appointment/{clinicId}",
            "my_veterinary_appointments",
            "veterinary_appointment_detail/{appointmentId}",
            "veterinary_appointment_management/{appointmentId}",
            "veterinary_managed_agenda/{clinicId}"
        ).forEach { assertTrue("falta ruta $it", routes.contains(it)) }
    }

    // 3 — DataProvider cablea clinic/schedule/appointment (Mock + Supabase).
    @Test
    fun data_provider_wires_veterinary_repos() {
        val provider = read("app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt")
        listOf(
            "veterinaryClinicRepository",
            "veterinaryScheduleRepository",
            "veterinaryAppointmentRepository",
            "MockVeterinaryClinicRepository",
            "SupabaseVeterinaryClinicRepository",
            "MockVeterinaryScheduleRepository",
            "SupabaseVeterinaryScheduleRepository",
            "MockVeterinaryAppointmentRepository",
            "SupabaseVeterinaryAppointmentRepository"
        ).forEach { assertTrue("DataProvider debe cablear $it", provider.contains(it)) }
        assertTrue("DataProvider debe ramificar por useSupabase", provider.contains("useSupabase"))
    }

    // 4 — Clases Mock y Supabase de appointment/schedule existen.
    @Test
    fun mock_and_supabase_appointment_schedule_classes_exist() {
        val apptRepos = read("app/src/main/java/com/comunidapp/app/data/repository/VeterinaryAppointmentRepositories.kt")
        assertTrue(apptRepos.contains("class MockVeterinaryScheduleRepository"))
        assertTrue(apptRepos.contains("class MockVeterinaryAppointmentRepository"))
        val supa = read("app/src/main/java/com/comunidapp/app/data/repository/SupabaseVeterinaryRepositories.kt")
        assertTrue(supa.contains("class SupabaseVeterinaryScheduleRepository"))
        assertTrue(supa.contains("class SupabaseVeterinaryAppointmentRepository"))
    }

    // 5 — Códigos de permisos en modelos.
    @Test
    fun permission_codes_present_in_models() {
        val b1 = read("app/src/main/java/com/comunidapp/app/data/model/VeterinaryModels.kt")
        listOf(
            "veterinary.profile.read",
            "veterinary.profile.manage"
        ).forEach { assertTrue("falta permiso $it", b1.contains(it)) }
        val b3 = read("app/src/main/java/com/comunidapp/app/data/model/VeterinaryAppointmentModels.kt")
        listOf(
            "veterinary.schedule.read",
            "veterinary.schedule.manage",
            "veterinary.appointment.read",
            "veterinary.appointment.request",
            "veterinary.appointment.manage"
        ).forEach { assertTrue("falta permiso $it", b3.contains(it)) }
    }

    // 6 — Migraciones 046 y 047 presentes.
    @Test
    fun migrations_046_and_047_present() {
        val names = migrationDir().listFiles()?.map { it.name }.orEmpty()
        assertTrue("falta 046 M12", names.any { it.startsWith("046_") && it.contains("m12") && it.contains("veterinary") })
        assertTrue("falta 047 M12", names.any { it.startsWith("047_") && it.contains("m12") && it.contains("veterinary") })
    }

    // 7 — 040–047 presentes; sin 048.
    @Test
    fun migrations_040_to_047_present_and_no_048() {
        val names = migrationDir().listFiles()?.map { it.name }.orEmpty()
        (40..47).forEach { n ->
            assertTrue("falta migración 0$n", names.any { it.startsWith("0${n}_") })
        }
        assertFalse("no debe existir migración 048", names.any { it.startsWith("048") })
    }

    // 8 — Sin service_role en fuentes Android M12.
    @Test
    fun no_service_role_in_m12_android_sources() {
        m12AndroidSources.forEach { path ->
            val text = read(path)
            assertFalse("$path no debe usar service_role", text.contains("service_role"))
            assertFalse("$path no debe usar SERVICE_ROLE", text.contains("SERVICE_ROLE"))
        }
    }

    // 9 — Sin pagos ni historia clínica en fuentes veterinary M12.
    @Test
    fun no_payments_or_clinical_records_in_m12_sources() {
        m12AndroidSources.forEach { path ->
            val text = read(path).lowercase()
            assertFalse("$path no debe mencionar mercadopago", text.contains("mercadopago"))
            assertFalse("$path no debe mencionar paymentintent", text.contains("paymentintent"))
            assertFalse("$path no debe mencionar health_record", text.contains("health_record"))
            assertFalse("$path no debe mencionar medical_record", text.contains("medical_record"))
        }
    }

    // 10 — Hotfix de autenticación preservado.
    @Test
    fun auth_hotfix_files_present() {
        assertTrue(findApp("app/src/main/java/com/comunidapp/app/core/config/SupabaseUrlPolicy.kt").isFile)
        assertTrue(findApp("app/src/main/java/com/comunidapp/app/core/config/AuthConfigDiagnostics.kt").isFile)
    }

    // 11 — SMOKE: docs documentan el smoke como PENDIENTE EXTERNO sin declarar M12 CERRADO.
    @Test
    fun closure_docs_exist() {
        newClosureDocs.forEach { assertTrue("falta doc de cierre $it", findApp(it).isFile) }
        assertTrue("falta doc de smoke pendiente $smokePendingDoc", findApp(smokePendingDoc).isFile)
    }

    @Test
    fun closure_docs_document_smoke_as_pending_external() {
        (newClosureDocs + smokePendingDoc).forEach { path ->
            val text = read(path)
            assertTrue(
                "$path debe documentar el smoke como PENDIENTE EXTERNO",
                text.contains("PENDIENTE EXTERNO")
            )
        }
    }

    @Test
    fun new_closure_docs_do_not_claim_smoke_pass() {
        newClosureDocs.forEach { path ->
            val text = read(path)
            assertFalse(
                "$path no debe afirmar 'M12 BLOQUE 3 SMOKE FUNCIONAL PASS' como estado actual",
                text.contains("M12 BLOQUE 3 SMOKE FUNCIONAL PASS")
            )
        }
    }

    @Test
    fun cierre_final_does_not_declare_m12_cerrado() {
        val text = read(cierreFinalDoc)
        // Permitido: "No se declara M12 CERRADO". Prohibido: declararlo como estado final (inicio de línea).
        assertFalse(
            "cierre-final no debe declarar M12 CERRADO como estado final",
            Regex("(^|\\n)\\s*M12 CERRADO").containsMatchIn(text)
        )
        assertTrue(
            "cierre-final debe indicar el cierre técnico local completado",
            text.contains("M12 CIERRE TÉCNICO LOCAL COMPLETADO")
        )
        assertTrue(
            "cierre-final debe aclarar que no se declara M12 CERRADO",
            text.contains("No se declara M12 CERRADO")
        )
    }

    // 12 — Push M06 externo documentado.
    @Test
    fun push_m06_external_documented() {
        val b4 = read("docs/05-operacion/M12-validacion-bloque-4.md")
        val cierre = read(cierreFinalDoc)
        assertTrue(
            "push M06 externo debe estar documentado",
            (b4 + cierre).contains("push", ignoreCase = true) &&
                (b4 + cierre).contains("M06")
        )
    }

    // 13 — Cron/scheduler externo documentado.
    @Test
    fun cron_scheduler_external_documented() {
        val b4 = read("docs/05-operacion/M12-validacion-bloque-4.md")
        val cierre = read(cierreFinalDoc)
        val combined = (b4 + cierre).lowercase()
        assertTrue(
            "cron/scheduler externo debe estar documentado",
            combined.contains("cron") || combined.contains("scheduler")
        )
    }

    // 14 — Métricas sin PII.
    @Test
    fun operational_metrics_have_no_pii() {
        val text = read("app/src/main/java/com/comunidapp/app/data/model/VeterinaryAppointmentModels.kt")
        val block = Regex(
            "data class VeterinaryAppointmentOperationalMetrics\\s*\\(([\\s\\S]*?)\\)",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.get(1)
        assertTrue("no se encontró VeterinaryAppointmentOperationalMetrics", block != null)
        val fields = block!!.lowercase()
        listOf("email", "phone", "telefono", "teléfono", "name", "nombre").forEach {
            assertFalse("VeterinaryAppointmentOperationalMetrics no debe exponer PII ($it)", fields.contains(it))
        }
    }

    // 15 — Legacy service_profiles/bookings no eliminados en 046/047.
    @Test
    fun legacy_tables_not_dropped_in_046_047() {
        val sql046 = File(migrationDir(), "046_m12_veterinary_profiles_and_services.sql").readText().lowercase()
        val sql047 = File(migrationDir(), "047_m12_veterinary_appointments_and_availability.sql").readText().lowercase()
        listOf(sql046, sql047).forEach { sql ->
            assertFalse(Regex("drop\\s+table\\s+[^;]*service_profiles").containsMatchIn(sql))
            assertFalse(Regex("drop\\s+table\\s+[^;]*bookings").containsMatchIn(sql))
        }
    }

    // 16 — auth.uid() en 046 y 047.
    @Test
    fun auth_uid_present_in_046_and_047() {
        val sql046 = File(migrationDir(), "046_m12_veterinary_profiles_and_services.sql").readText()
        val sql047 = File(migrationDir(), "047_m12_veterinary_appointments_and_availability.sql").readText()
        assertTrue("046 debe usar auth.uid()", sql046.contains("auth.uid()"))
        assertTrue("047 debe usar auth.uid()", sql047.contains("auth.uid()"))
    }

    // 17 — Patrón revoke de helpers _m12_ en 046/047.
    @Test
    fun helper_revoke_pattern_present_in_046_047() {
        val sql046 = File(migrationDir(), "046_m12_veterinary_profiles_and_services.sql").readText()
        val sql047 = File(migrationDir(), "047_m12_veterinary_appointments_and_availability.sql").readText()
        assertTrue("046 debe revocar helpers _m12_", sql046.contains("revoke all on function public._m12_"))
        assertTrue("047 debe revocar helpers _m12_", sql047.contains("revoke all on function public._m12_"))
    }

    // 18 — Opt-in de contacto público en modelos.
    @Test
    fun contact_opt_in_present_in_models() {
        val models = read("app/src/main/java/com/comunidapp/app/data/model/VeterinaryModels.kt")
        assertTrue("falta publicContactEnabled", models.contains("publicContactEnabled"))
    }

    // 19 — Privacidad de requestNote mencionada en docs o código.
    @Test
    fun request_note_privacy_documented() {
        val matriz = read(matrizFinalDoc)
        val cierre = read(cierreFinalDoc)
        val apptRepo = read("app/src/main/java/com/comunidapp/app/data/repository/VeterinaryAppointmentRepositories.kt")
        val mentionsInDocs = (matriz + cierre).contains("requestNote")
        val enforcedInCode = apptRepo.contains("if (isRequester) appointment.requestNote else null")
        assertTrue(
            "la privacidad de requestNote debe estar documentada o aplicada en código",
            mentionsInDocs || enforcedInCode
        )
    }

    // 20 — Sin WorkManager en fuentes de turnos.
    @Test
    fun no_workmanager_in_appointment_sources() {
        appointmentSources.forEach { path ->
            val text = read(path)
            assertFalse("$path no debe usar WorkManager", text.contains("WorkManager"))
            assertFalse("$path no debe importar androidx.work", text.contains("androidx.work"))
        }
    }
}
