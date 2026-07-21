package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer M08 Etapa 5 — guardas estáticas sobre el código fuente de la etapa:
 * rutas presentes, sin RPC desde Composables, sin autorización por ownerId,
 * sin GlobalScope y DTO de historial alineado al esquema 035.
 */
class M08Stage5StaticGuardsTest {

    private val screenFiles = listOf(
        "app/src/main/java/com/comunidapp/app/ui/screens/pets/PetResponsibilitiesScreen.kt",
        "app/src/main/java/com/comunidapp/app/ui/screens/pets/PetAuthorizationsScreen.kt",
        "app/src/main/java/com/comunidapp/app/ui/screens/pets/PetTransfersScreen.kt",
        "app/src/main/java/com/comunidapp/app/ui/screens/pets/PetTransferDetailScreen.kt"
    )

    private val viewModelFiles = listOf(
        "app/src/main/java/com/comunidapp/app/viewmodel/PetResponsibilitiesViewModel.kt",
        "app/src/main/java/com/comunidapp/app/viewmodel/PetAuthorizationsViewModel.kt",
        "app/src/main/java/com/comunidapp/app/viewmodel/PetTransfersViewModel.kt"
    )

    @Test
    fun navRoutes_define_stage5_routes() {
        val routes = sourceFile("app/src/main/java/com/comunidapp/app/navigation/NavRoutes.kt")
            .readText()
        assertTrue(routes.contains("pet_responsibilities/{petId}"))
        assertTrue(routes.contains("pet_authorizations/{petId}"))
        assertTrue(routes.contains("pet_transfers/{petId}"))
        assertTrue(routes.contains("pet_transfer_detail/{petId}/{transferId}"))
        assertTrue(routes.contains("fun petTransferDetail(petId: String, transferId: String)"))
        assertTrue(routes.contains("URLEncoder.encode(petId"))
    }

    @Test
    fun navGraph_wires_stage5_screens_with_factories() {
        val graph = sourceFile(
            "app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt"
        ).readText()
        assertTrue(graph.contains("PetResponsibilitiesScreen("))
        assertTrue(graph.contains("PetAuthorizationsScreen("))
        assertTrue(graph.contains("PetTransfersScreen("))
        assertTrue(graph.contains("PetTransferDetailScreen("))
        assertTrue(graph.contains("PetResponsibilitiesViewModel.factory(petId)"))
        assertTrue(graph.contains("PetAuthorizationsViewModel.factory(petId)"))
        assertTrue(graph.contains("PetTransfersViewModel.factory(petId)"))
    }

    @Test
    fun petDetail_entry_gated_by_access_context_not_ownerId() {
        val screen = sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/pets/PetDetailScreen.kt"
        ).readText()
        assertTrue(screen.contains("canViewGovernance"))
        assertTrue(screen.contains("Responsables y permisos"))
        assertFalse(screen.contains("ownerId =="))
        val viewModel = sourceFile(
            "app/src/main/java/com/comunidapp/app/viewmodel/PetDetailViewModel.kt"
        ).readText()
        assertTrue(viewModel.contains("ctx.canRead"))
        assertFalse(viewModel.contains("ownerId =="))
    }

    @Test
    fun screens_never_call_rpc_or_postgrest_directly() {
        screenFiles.forEach { path ->
            val content = sourceFile(path).readText()
            assertFalse("$path must not call RPC", content.contains("postgrest"))
            assertFalse("$path must not call RPC", content.contains(".rpc("))
            assertFalse("$path must not use Supabase client", content.contains("supabase."))
        }
    }

    @Test
    fun viewModels_no_globalScope_and_no_ownerId_authorization() {
        (viewModelFiles + screenFiles).forEach { path ->
            val content = sourceFile(path).readText()
            assertFalse("$path must not use GlobalScope", content.contains("GlobalScope"))
            assertFalse(
                "$path must not authorize by ownerId equality",
                content.contains("ownerId ==") || content.contains("== ownerId")
            )
        }
    }

    @Test
    fun viewModels_gate_by_backend_capabilities() {
        val responsibilities = sourceFile(viewModelFiles[0]).readText()
        assertTrue(responsibilities.contains("canManageResponsibilities"))
        val authorizations = sourceFile(viewModelFiles[1]).readText()
        assertTrue(authorizations.contains("canManageAuthorizations"))
        val transfers = sourceFile(viewModelFiles[2]).readText()
        assertTrue(transfers.contains("canInitiateTransfer"))
        assertTrue(transfers.contains("canAcceptTransfer"))
        assertTrue(transfers.contains("canCancelTransfer"))
    }

    @Test
    fun screens_have_confirmation_dialogs_and_state_handling() {
        screenFiles.forEach { path ->
            val content = sourceFile(path).readText()
            assertTrue("$path must confirm mutations", content.contains("AlertDialog"))
            assertTrue("$path must handle loading", content.contains("LoadingState"))
            assertTrue("$path must handle errors with retry", content.contains("ErrorState"))
        }
    }

    @Test
    fun statusHistory_dto_matches_035_schema_columns() {
        val dtos = sourceFile(
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m08/PetM08Dtos.kt"
        ).readText()
        assertTrue(dtos.contains("@SerialName(\"changed_by\") val actorUserId"))
        assertTrue(dtos.contains("@SerialName(\"changed_at\") val createdAt"))
        assertTrue(dtos.contains("@SerialName(\"reason\") val reasonCode"))
        val dataSource = sourceFile(
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m08/SupabasePetM08RemoteDataSource.kt"
        ).readText()
        assertTrue(dataSource.contains("order(\"changed_at\""))
    }

    @Test
    fun authorization_allowlist_never_contains_forbidden_capabilities() {
        val forbidden = setOf(
            com.comunidapp.app.domain.pets.PetCapability.INITIATE_TRANSFER,
            com.comunidapp.app.domain.pets.PetCapability.MARK_DECEASED,
            com.comunidapp.app.domain.pets.PetCapability.ARCHIVE,
            com.comunidapp.app.domain.pets.PetCapability.MANAGE_RESPONSIBILITIES
        )
        assertTrue(
            PetAuthorizationsViewModel.GRANTABLE_CAPABILITIES.intersect(forbidden).isEmpty()
        )
        assertTrue(PetAuthorizationsViewModel.GRANTABLE_CAPABILITIES.isNotEmpty())
    }

    @Test
    fun stage5_quality_script_and_backlog_doc_present() {
        val script = sourceFile("scripts/ci/m08_stage5_quality_checks.sh").readText()
        assertTrue(script.contains("PetResponsibilitiesViewModel"))
        assertTrue(script.contains("037"))
        val backlog = sourceFile(
            "docs/04-calidad/M08-backlog-defectos-smoke-staging.md"
        ).readText()
        assertTrue(backlog.contains("M08-SMOKE-001"))
        assertTrue(backlog.contains("BACKLOG"))
        // Declared PASS status lines only; prose that forbids PASS must not trip this.
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
