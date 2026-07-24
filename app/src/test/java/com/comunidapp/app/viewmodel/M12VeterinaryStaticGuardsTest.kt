package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer M12 — guardas estáticas Bloque 1 (sin SQL nuevo, sin Supabase real).
 */
class M12VeterinaryStaticGuardsTest {

    private val immutableMigrations = listOf(
        "040_",
        "041_",
        "042_m11_shelter_operations_core.sql",
        "043_m11_shelter_campaigns_and_aid.sql",
        "044_m11_harden_campaign_aid_permissions.sql",
        "045_m11_shelter_emergencies_events_reports.sql"
    )

    private fun migrationDir(): File {
        val candidates = listOf(
            File("supabase/migrations"),
            File("../supabase/migrations"),
            File("../../supabase/migrations")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("supabase/migrations not found")
    }

    private fun findAppFile(vararg relativeCandidates: String): File {
        relativeCandidates.forEach { rel ->
            listOf(File(rel), File("../$rel"), File("../../$rel")).forEach { f ->
                if (f.isFile) return f
            }
        }
        error("missing ${relativeCandidates.first()}")
    }

    private fun readSource(vararg candidates: String): String =
        findAppFile(*candidates).readText()

    @Test
    fun migration_046_is_m12_when_present() {
        val names = migrationDir().listFiles()?.map { it.name }.orEmpty()
        val m046 = names.filter { it.startsWith("046_") }
        assertTrue("Bloque 2 requiere 046 M12", m046.any { it.contains("m12") && it.contains("veterinary") })
    }

    @Test
    fun migrations_040_to_045_intact_present() {
        val names = migrationDir().listFiles()?.map { it.name }?.sorted().orEmpty()
        assertTrue(names.any { it.startsWith("040_") })
        assertTrue(names.any { it.startsWith("041_") })
        listOf(
            "042_m11_shelter_operations_core.sql",
            "043_m11_shelter_campaigns_and_aid.sql",
            "044_m11_harden_campaign_aid_permissions.sql",
            "045_m11_shelter_emergencies_events_reports.sql"
        ).forEach { assertTrue("missing $it", names.contains(it)) }
    }

    @Test
    fun m12_sql_is_046_and_047() {
        val names = migrationDir().listFiles()?.map { it.name }.orEmpty()
        val m12Files = names.filter { it.contains("m12", ignoreCase = true) }
        assertTrue("debe haber al menos una migración M12", m12Files.isNotEmpty())
        assertTrue(
            "las migraciones M12 deben ser 046_/047_ veterinary",
            m12Files.all {
                (it.startsWith("046_") || it.startsWith("047_")) &&
                    it.contains("veterinary", ignoreCase = true)
            }
        )
        assertTrue("falta 046 M12", m12Files.any { it.startsWith("046_") })
        assertTrue("falta 047 M12", m12Files.any { it.startsWith("047_") })
    }

    @Test
    fun mock_and_supabase_wired_without_service_role() {
        val repo = readSource(
            "app/src/main/java/com/comunidapp/app/data/repository/VeterinaryRepositories.kt"
        )
        assertFalse(repo.contains("service_role"))
        val provider = readSource(
            "app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt"
        )
        assertTrue(provider.contains("MockVeterinaryDirectoryRepository"))
        assertTrue(provider.contains("SupabaseVeterinaryDirectoryRepository"))
        assertTrue(provider.contains("if (useSupabase)"))
        assertFalse(provider.contains("service_role"))
        val remote = readSource(
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m12/SupabaseVeterinaryM12RemoteDataSource.kt"
        )
        assertTrue(remote.contains("m12_"))
        assertFalse(remote.contains("service_role"))
    }

    @Test
    fun no_payments_or_clinical_history_in_m12_sources() {
        val files = listOf(
            "app/src/main/java/com/comunidapp/app/data/model/VeterinaryModels.kt",
            "app/src/main/java/com/comunidapp/app/data/repository/VeterinaryRepositories.kt",
            "app/src/main/java/com/comunidapp/app/viewmodel/VeterinaryViewModels.kt",
            "app/src/main/java/com/comunidapp/app/ui/screens/veterinary/VeterinaryScreens.kt"
        )
        files.forEach { path ->
            val text = readSource(path).lowercase()
            assertFalse("$path payments", text.contains("mercadopago") || text.contains("paymentintent"))
            assertFalse("$path clinical", text.contains("healthrecord") || text.contains("medicalrecord"))
            assertFalse(
                "$path historia clinica",
                text.contains("historia clínica") || text.contains("historia clinica")
            )
        }
    }

    @Test
    fun no_service_role_android_in_m12() {
        val a = findAppFile(
            "app/src/main/java/com/comunidapp/app/data/repository/VeterinaryRepositories.kt"
        ).readText()
        val b = findAppFile(
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m12/M12VeterinaryErrorMapper.kt"
        ).readText()
        assertFalse((a + b).contains("service_role"))
        assertFalse((a + b).contains("SERVICE_ROLE"))
    }

    @Test
    fun double_submit_guard_present_in_draft_vm() {
        val src = readSource(
            "app/src/main/java/com/comunidapp/app/viewmodel/VeterinaryViewModels.kt"
        )
        assertTrue(src.contains("if (_submitting.value) return"))
    }

    @Test
    fun routes_registered() {
        val routes = readSource(
            "app/src/main/java/com/comunidapp/app/navigation/NavRoutes.kt"
        )
        assertTrue(routes.contains("veterinary_directory"))
        assertTrue(routes.contains("veterinary_clinic_detail/{clinicId}"))
        assertTrue(routes.contains("my_veterinary_clinics"))
        assertTrue(routes.contains("veterinary_clinic_draft"))
        val graph = readSource(
            "app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt"
        )
        assertTrue(graph.contains("VETERINARY_DIRECTORY"))
        assertTrue(graph.contains("VeterinaryDirectoryScreen"))
        assertTrue(graph.contains("VeterinaryClinicDetailScreen"))
        assertTrue(graph.contains("ManagedVeterinaryClinicsScreen"))
        assertTrue(graph.contains("VeterinaryClinicDraftScreen"))
    }

    @Test
    fun permission_identifiers_present() {
        val models = readSource(
            "app/src/main/java/com/comunidapp/app/data/model/VeterinaryModels.kt"
        )
        listOf(
            "veterinary.profile.read",
            "veterinary.profile.manage",
            "veterinary.professional.read",
            "veterinary.professional.manage",
            "veterinary.service.read",
            "veterinary.service.manage"
        ).forEach { assertTrue(models.contains(it)) }
    }

    @Test
    fun m05_secure_refs_policy_present() {
        val validators = readSource(
            "app/src/main/java/com/comunidapp/app/data/repository/VeterinaryValidators.kt"
        )
        assertTrue(validators.contains("m05://"))
        assertTrue(validators.contains("file_asset:"))
        assertTrue(validators.contains("FosterSecureRefValidator"))
    }

    @Test
    fun docs_created() {
        assertTrue(
            findAppFile("docs/03-modulos/M12-veterinarias.md").isFile
        )
        assertTrue(
            findAppFile("docs/02-arquitectura/M12-auditoria-y-contratos-iniciales.md").isFile
        )
    }

    @Test
    fun legacy_service_profiles_not_destructively_migrated() {
        val models = readSource(
            "app/src/main/java/com/comunidapp/app/data/model/VeterinaryModels.kt"
        )
        assertTrue(models.contains("service_profiles") || models.contains("ServiceProfile"))
        val sql046 = File(migrationDir(), "046_m12_veterinary_profiles_and_services.sql").readText()
        assertFalse(Regex("drop\\s+table\\s+.*service_profiles", RegexOption.IGNORE_CASE).containsMatchIn(sql046))
        assertTrue(immutableMigrations.isNotEmpty())
    }
}
