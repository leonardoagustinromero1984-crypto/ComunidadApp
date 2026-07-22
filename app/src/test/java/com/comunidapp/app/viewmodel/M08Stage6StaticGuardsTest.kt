package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer M08 Etapa 6 — guardas estáticas: rutas, sin RPC en UI, sin ownerId auth,
 * sin GlobalScope, sin photo_url writes, sin migración 037, galería diferida OK.
 */
class M08Stage6StaticGuardsTest {

    private val stage6Screens = listOf(
        "app/src/main/java/com/comunidapp/app/ui/screens/pets/PetDetailScreen.kt",
        "app/src/main/java/com/comunidapp/app/ui/screens/pets/PetStatusHistoryScreen.kt",
        "app/src/main/java/com/comunidapp/app/ui/screens/pets/PetFormScreen.kt"
    )

    private val stage6ViewModels = listOf(
        "app/src/main/java/com/comunidapp/app/viewmodel/PetDetailViewModel.kt",
        "app/src/main/java/com/comunidapp/app/viewmodel/PetStatusHistoryViewModel.kt",
        "app/src/main/java/com/comunidapp/app/viewmodel/PetFormViewModel.kt"
    )

    @Test
    fun navRoutes_define_stage6_status_history_with_url_encode() {
        val routes = sourceFile("app/src/main/java/com/comunidapp/app/navigation/NavRoutes.kt")
            .readText()
        assertTrue(routes.contains("pet_status_history/{petId}"))
        assertTrue(routes.contains("fun petStatusHistory(petId: String)"))
        assertTrue(routes.contains("URLEncoder.encode(petId"))
        // Stage 5 routes remain intact
        assertTrue(routes.contains("pet_responsibilities/{petId}"))
        assertTrue(routes.contains("pet_transfers/{petId}"))
    }

    @Test
    fun navGraph_wires_status_history_factory() {
        val graph = sourceFile(
            "app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt"
        ).readText()
        assertTrue(graph.contains("PetStatusHistoryScreen("))
        assertTrue(graph.contains("PetStatusHistoryViewModel.factory(petId)"))
        assertTrue(graph.contains("onNavigateToStatusHistory"))
    }

    @Test
    fun petRepository_exposes_stage6_wrappers() {
        val repo = sourceFile(
            "app/src/main/java/com/comunidapp/app/data/repository/PetRepository.kt"
        ).readText()
        assertTrue(repo.contains("fun markPetDeceased"))
        assertTrue(repo.contains("fun restorePet"))
        assertTrue(repo.contains("fun listStatusHistory"))
        assertTrue(repo.contains("fun detectDuplicateCandidates"))
        val adapter = sourceFile(
            "app/src/main/java/com/comunidapp/app/data/repository/LegacyPetRepositoryAdapter.kt"
        ).readText()
        assertTrue(adapter.contains("remote.markPetDeceased"))
        assertTrue(adapter.contains("remote.restorePet"))
        assertTrue(adapter.contains("remote.listStatusHistory"))
        assertTrue(adapter.contains("remote.detectDuplicates"))
    }

    @Test
    fun errorMapper_covers_stage6_sql_codes() {
        val mapper = sourceFile(
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m08/M08PetErrorMapper.kt"
        ).readText()
        listOf(
            "PET_ALREADY_DECEASED",
            "PET_ALREADY_ARCHIVED",
            "PET_DECEASED_CANNOT_ARCHIVE",
            "PET_DECEASED_CANNOT_RESTORE",
            "PET_NOT_ARCHIVED",
            "PET_AVATAR_ASSET_NOT_FOUND",
            "PET_AVATAR_PURPOSE_INVALID"
        ).forEach { code ->
            assertTrue("missing $code", mapper.contains(code))
        }
        assertTrue(
            mapper.contains(
                "Ya existe una mascota activa registrada con ese microchip."
            )
        )
    }

    @Test
    fun screens_never_call_rpc_or_write_photo_url() {
        stage6Screens.forEach { path ->
            val content = sourceFile(path).readText()
            // Package imports of DTOs (…data.remote.supabase.m08.*) are allowed;
            // forbid real client/RPC usage from Composables.
            assertFalse("$path must not call PostgREST", content.contains(".postgrest"))
            assertFalse("$path must not call RPC", content.contains(".rpc("))
            assertFalse(
                "$path must not construct Supabase client",
                content.contains("createSupabaseClient") || content.contains("SupabaseClient")
            )
            assertFalse(
                "$path must not write photo_url column",
                content.contains("photo_url")
            )
        }
    }

    @Test
    fun viewModels_no_globalScope_no_ownerId_authorization() {
        (stage6ViewModels + stage6Screens).forEach { path ->
            val content = sourceFile(path).readText()
            assertFalse("$path must not use GlobalScope", content.contains("GlobalScope"))
            assertFalse(
                "$path must not authorize by ownerId equality",
                content.contains("ownerId ==") || content.contains("== ownerId")
            )
        }
    }

    @Test
    fun avatar_flow_uses_m05_and_setPetAvatarAsset() {
        val formVm = sourceFile(
            "app/src/main/java/com/comunidapp/app/viewmodel/PetFormViewModel.kt"
        ).readText()
        assertTrue(formVm.contains("FileAssetPurpose.PET_AVATAR"))
        assertTrue(formVm.contains("setPetAvatarAsset"))
        assertTrue(formVm.contains("canManageMedia"))
        assertTrue(formVm.contains("detectDuplicateCandidates"))
        assertFalse(formVm.contains("photo_url"))
    }

    @Test
    fun gallery_screen_absent_is_documented_backlog() {
        val galleryScreen = File(
            "app/src/main/java/com/comunidapp/app/ui/screens/pets/PetPhotoGalleryScreen.kt"
        )
        val audit = sourceFile(
            "docs/02-arquitectura/M08-etapa-6-auditoria-contratos.md"
        ).readText()
        if (!galleryScreen.isFile) {
            assertTrue(audit.contains("GALLERY GAP"))
            assertTrue(audit.contains("BACKLOG"))
        }
    }

    @Test
    fun no_migration_037_and_quality_script_present() {
        val migrations = File("supabase/migrations")
        val has037 = migrations.listFiles()?.any { it.name.startsWith("037_") } == true
        assertFalse(has037)
        val script = sourceFile("scripts/ci/m08_stage6_quality_checks.sh").readText()
        assertTrue(script.contains("PetStatusHistoryViewModel"))
        assertTrue(script.contains("037"))
        val backlog = sourceFile(
            "docs/04-calidad/M08-backlog-defectos-smoke-staging.md"
        ).readText()
        assertTrue(backlog.contains("M08-SMOKE-001"))
        assertTrue(backlog.contains("BACKLOG"))
        val declaredPass = Regex(
            """(?m)^\s*(SMOKE APK STAGING|SMOKE MANUAL|SMOKE INTEGRAL M08)\s*—\s*PASS\s*$"""
        )
        assertFalse(declaredPass.containsMatchIn(backlog))
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath"),
            File(System.getProperty("user.dir"), relativePath),
            File(System.getProperty("user.dir"), "../$relativePath")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("$relativePath not found. cwd=${System.getProperty("user.dir")}")
    }
}
