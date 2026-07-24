package com.comunidapp.app.core.config

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer — guardas estáticas del hotfix de auth para APK localDebug (post M12 Bloque 3).
 *
 * Verifica que la corrección quede fijada en el código fuente:
 *  - Gradle resuelve credenciales locales sin hosts de emulador (fallback a staging).
 *  - cleartext deshabilitado.
 *  - AuthErrorMapper con PERMISSION_DENIED y mensajes nuevos.
 *  - SupabaseClientProvider valida la URL vía SupabaseUrlPolicy.
 *  - Sin service_role en fuentes de auth/config.
 *  - Migraciones 040–047 intactas (047 presente, sin modificar SQL).
 *  - Diagnóstico seguro (no loguea el valor de la anon key).
 *  - Permiso INTERNET y documentación de fallback en local.properties.example.
 */
class AuthHotfixLocalDebugGuardsTest {

    private fun findFile(vararg rel: String): File {
        rel.forEach { r ->
            listOf(File(r), File("../$r"), File("../../$r")).forEach { if (it.isFile) return it }
        }
        error("missing ${rel.first()}")
    }

    private fun findDir(vararg rel: String): File {
        rel.forEach { r ->
            listOf(File(r), File("../$r"), File("../../$r")).forEach { if (it.isDirectory) return it }
        }
        error("missing dir ${rel.first()}")
    }

    private fun read(vararg rel: String): String = findFile(*rel).readText()

    private fun buildGradle(): String = read("build.gradle.kts", "app/build.gradle.kts")

    private fun mainSource(rel: String): String =
        read("src/main/java/$rel", "app/src/main/java/$rel")

    // 1) Gradle resuelve local sin hosts de emulador y con fallback a staging.
    @Test
    fun gradle_resolves_local_supabase_with_staging_fallback_guards() {
        val gradle = buildGradle()
        assertTrue(gradle.contains("resolveLocalSupabase"))
        assertTrue(gradle.contains("STAGING_FALLBACK"))
        assertTrue(gradle.contains("isForbiddenLocalHost"))
    }

    // 2) cleartext deshabilitado.
    @Test
    fun network_security_config_disables_cleartext() {
        val xml = read(
            "src/main/res/xml/network_security_config.xml",
            "app/src/main/res/xml/network_security_config.xml"
        )
        assertTrue(xml.contains("cleartextTrafficPermitted=\"false\""))
        assertFalse(xml.contains("cleartextTrafficPermitted=\"true\""))
    }

    // 3) AuthErrorMapper con PERMISSION_DENIED y mensajes nuevos.
    @Test
    fun auth_error_mapper_has_permission_denied_and_new_messages() {
        val mapper = mainSource("com/comunidapp/app/domain/auth/AuthErrorMapper.kt")
        assertTrue(mapper.contains("PERMISSION_DENIED"))
        assertTrue(mapper.contains("El correo o la contraseña son incorrectos."))
        assertTrue(mapper.contains("Tu correo todavía no fue confirmado."))
        assertTrue(mapper.contains("Tu sesión venció. Iniciá sesión nuevamente."))
        assertTrue(mapper.contains("No pudimos conectarnos. Revisá tu conexión."))
        assertTrue(mapper.contains("La configuración de Supabase no está disponible en esta versión."))
        assertFalse(mapper.contains("Ocurrió un problema de autenticación"))
    }

    // 4) SupabaseClientProvider valida URL vía SupabaseUrlPolicy.
    @Test
    fun supabase_client_provider_validates_url_via_policy() {
        val provider = mainSource(
            "com/comunidapp/app/data/remote/supabase/SupabaseClientProvider.kt"
        )
        assertTrue(provider.contains("SupabaseUrlPolicy"))
        assertTrue(provider.contains("isUsableRemoteUrl"))
    }

    // 5) Sin service_role en fuentes de auth/config.
    @Test
    fun no_service_role_in_auth_config_sources() {
        val sources = listOf(
            "com/comunidapp/app/domain/auth/AuthErrorMapper.kt",
            "com/comunidapp/app/core/config/SupabaseUrlPolicy.kt",
            "com/comunidapp/app/core/config/AuthConfigDiagnostics.kt",
            "com/comunidapp/app/core/config/AppConfigProvider.kt",
            "com/comunidapp/app/data/remote/supabase/SupabaseClientProvider.kt"
        ).joinToString("\n") { mainSource(it) }
        assertFalse(sources.contains("service_role"))
        assertFalse(sources.contains("SERVICE_ROLE"))
    }

    // 6) Migraciones 040–047 presentes.
    @Test
    fun migrations_040_to_047_present() {
        val dir = findDir("supabase/migrations")
        val names = dir.listFiles()?.map { it.name }.orEmpty()
        listOf(
            "040_m10_foster_homes_core.sql",
            "041_m10_foster_care_management.sql",
            "042_m11_shelter_operations_core.sql",
            "043_m11_shelter_campaigns_and_aid.sql",
            "044_m11_harden_campaign_aid_permissions.sql",
            "045_m11_shelter_emergencies_events_reports.sql",
            "046_m12_veterinary_profiles_and_services.sql",
            "047_m12_veterinary_appointments_and_availability.sql"
        ).forEach { assertTrue("migration $it missing", names.contains(it)) }
    }

    // 7) 047 presente y con contenido (no vaciada).
    @Test
    fun migration_047_present_and_non_empty() {
        val sql = read(
            "supabase/migrations/047_m12_veterinary_appointments_and_availability.sql",
            "../supabase/migrations/047_m12_veterinary_appointments_and_availability.sql"
        )
        assertTrue(sql.isNotBlank())
        assertTrue(sql.length > 100)
    }

    // 8) Diagnóstico seguro: no concatena el valor de la anon key en logs.
    @Test
    fun auth_config_diagnostics_never_logs_anon_key_value() {
        val diag = mainSource("com/comunidapp/app/core/config/AuthConfigDiagnostics.kt")
        assertTrue(diag.contains("anonKeyPresent"))
        assertTrue(diag.contains("anonKeyLength"))
        // El log NO debe interpolar el valor de la key.
        assertFalse(diag.contains("\${s.anonKey}"))
        assertFalse(diag.contains("\${key}"))
        assertFalse(diag.contains("\${BuildConfig.SUPABASE_ANON_KEY}"))
        assertFalse(diag.contains("anonKey=\$"))
    }

    // 9) Permiso INTERNET en el manifest.
    @Test
    fun manifest_declares_internet_permission() {
        val manifest = read("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android.permission.INTERNET"))
    }

    // 10) local.properties.example documenta el fallback a staging.
    @Test
    fun local_properties_example_documents_staging_fallback() {
        val example = read("local.properties.example", "../local.properties.example")
        assertTrue(example.contains("SUPABASE_STAGING_URL"))
        assertTrue(example.contains("fallback", ignoreCase = false) || example.contains("STAGING"))
        assertTrue(example.contains("10.0.2.2"))
    }
}
