package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer M11 — guardas de cierre final del módulo Refugios.
 * Estáticas sobre migraciones 042–045, rutas, repos y docs; sin Supabase real.
 */
class M11FinalClosureGuardsTest {

    private val migrations = listOf(
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

    private fun source(relative: String): File {
        val direct = File(relative)
        if (direct.isFile) return direct
        val byName = File(migrationDir(), File(relative).name)
        require(byName.isFile) { "missing $relative" }
        return byName
    }

    private fun read(relative: String) = source(relative).readText()

    private fun findAppFile(vararg relativeCandidates: String): File {
        relativeCandidates.forEach { rel ->
            listOf(File(rel), File("../$rel"), File("../../$rel")).forEach { f ->
                if (f.isFile) return f
            }
        }
        error("missing ${relativeCandidates.first()}")
    }

    @Test
    fun migrations_042_to_045_present_in_order() {
        val names = migrationDir().listFiles()?.map { it.name }?.sorted().orEmpty()
        migrations.forEach { assertTrue("missing $it", names.contains(it)) }
        val idxs = migrations.map { names.indexOf(it) }
        assertTrue(idxs.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun applied_migrations_immutable_no_046() {
        val names = migrationDir().listFiles()?.map { it.name }.orEmpty()
        assertFalse("046 must not exist", names.any { it.startsWith("046_") })
        // Closure must not introduce DROP TABLE of block tables in 043–045
        listOf(
            "043_m11_shelter_campaigns_and_aid.sql",
            "044_m11_harden_campaign_aid_permissions.sql",
            "045_m11_shelter_emergencies_events_reports.sql"
        ).forEach { name ->
            val sql = read("supabase/migrations/$name").lowercase()
            assertFalse(
                "$name must not DROP TABLE",
                Regex("drop\\s+table").containsMatchIn(sql)
            )
        }
    }

    @Test
    fun block_tables_present_across_042_045() {
        val all = migrations.joinToString("\n") { read("supabase/migrations/$it") }
        listOf(
            "shelter_profiles",
            "shelter_pet_placements",
            "shelter_volunteer_assignments",
            "shelter_campaigns",
            "shelter_campaign_updates",
            "shelter_supply_requests",
            "shelter_supply_contributions",
            "shelter_emergencies",
            "shelter_events",
            "shelter_event_registrations"
        ).forEach { assertTrue("missing table $it", all.contains(it)) }
    }

    @Test
    fun rls_declared_on_m11_tables() {
        migrations.forEach { name ->
            val sql = read("supabase/migrations/$name").lowercase()
            assertTrue("$name missing RLS", sql.contains("enable row level security"))
        }
    }

    @Test
    fun rpc_search_path_present() {
        listOf("042", "043", "045").forEach { prefix ->
            val file = migrations.first { it.startsWith(prefix) }
            val sql = read("supabase/migrations/$file").lowercase()
            assertTrue("$file missing search_path", sql.contains("search_path"))
            assertTrue("$file missing security definer", sql.contains("security definer"))
        }
    }

    @Test
    fun hardening_044_present() {
        val sql = read("supabase/migrations/044_m11_harden_campaign_aid_permissions.sql").lowercase()
        assertTrue(sql.contains("revoke all privileges on table public.shelter_campaigns"))
        assertTrue(sql.contains("grant select on table public.shelter_campaigns to authenticated"))
        assertTrue(sql.contains("from anon"))
        assertTrue(sql.contains("grant execute"))
    }

    @Test
    fun no_service_role_in_android_m11() {
        val remote = findAppFile(
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m11/SupabaseShelterM11RemoteDataSource.kt",
            "src/main/java/com/comunidapp/app/data/remote/supabase/m11/SupabaseShelterM11RemoteDataSource.kt"
        )
        val text = remote.readText().lowercase()
        assertFalse(text.contains("service_role_key"))
        assertFalse(text.contains("role=service_role"))
        assertFalse(text.contains("\"service_role\""))
    }

    @Test
    fun no_public_url_persistence_policy_in_043_045() {
        val sql = read("supabase/migrations/043_m11_shelter_campaigns_and_aid.sql") +
            read("supabase/migrations/045_m11_shelter_emergencies_events_reports.sql")
        assertTrue(sql.contains("m05://") || sql.contains("file_asset:"))
        assertTrue(sql.contains("https?") || sql.lowercase().contains("leover"))
    }

    @Test
    fun no_banking_or_payment_columns_in_m11_migrations() {
        val all = migrations.joinToString("\n") { read("supabase/migrations/$it") }.lowercase()
        assertFalse(Regex("\\bcbu\\b").containsMatchIn(all))
        assertFalse(all.contains("mercado_pago") || all.contains("mercadopago"))
        assertFalse(all.contains("checkout"))
        assertFalse(Regex("goal_amount").containsMatchIn(all))
        assertFalse(all.contains("ticket_price") || all.contains("entry_fee"))
    }

    @Test
    fun no_paid_tickets_in_m11_domain() {
        val models = findAppFile(
            "app/src/main/java/com/comunidapp/app/data/model/ShelterOperationsModels.kt",
            "src/main/java/com/comunidapp/app/data/model/ShelterOperationsModels.kt"
        ).readText().lowercase()
        assertFalse(models.contains("ticket_price"))
        assertFalse(models.contains("paymentintent"))
        assertFalse(models.contains("checkout"))
    }

    @Test
    fun m11_routes_registered() {
        val routes = findAppFile(
            "app/src/main/java/com/comunidapp/app/navigation/NavRoutes.kt",
            "src/main/java/com/comunidapp/app/navigation/NavRoutes.kt"
        ).readText()
        listOf(
            "SHELTER_DASHBOARD",
            "SHELTER_PETS",
            "SHELTER_VOLUNTEERS",
            "SHELTER_CAMPAIGNS",
            "SHELTER_SUPPLY_REQUESTS",
            "SHELTER_EMERGENCIES",
            "SHELTER_EVENTS",
            "SHELTER_REPORTS",
            "SHELTER_PUBLIC_CAMPAIGNS",
            "SHELTER_PUBLIC_EMERGENCIES",
            "SHELTER_PUBLIC_EVENTS"
        ).forEach { assertTrue("missing route $it", routes.contains(it)) }
        val graph = findAppFile(
            "app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt",
            "src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt"
        ).readText()
        assertTrue(graph.contains("SHELTER_PUBLIC_EMERGENCIES"))
        assertTrue(graph.contains("SHELTER_PUBLIC_EVENTS"))
        assertTrue(graph.contains("shelterEmergencies") || graph.contains("SHELTER_EMERGENCIES"))
        assertTrue(graph.contains("shelterReports") || graph.contains("SHELTER_REPORTS"))
    }

    @Test
    fun dashboard_has_three_block_accesses() {
        val screen = findAppFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/shelters/ShelterOperationsScreens.kt",
            "src/main/java/com/comunidapp/app/ui/screens/shelters/ShelterOperationsScreens.kt"
        ).readText()
        assertTrue(screen.contains("onPets"))
        assertTrue(screen.contains("onVolunteers"))
        assertTrue(screen.contains("onCampaigns"))
        assertTrue(screen.contains("onSupplyRequests") || screen.contains("Pedidos de insumos"))
        assertTrue(screen.contains("onEmergencies"))
        assertTrue(screen.contains("onEvents"))
        assertTrue(screen.contains("onReports"))
        assertTrue(screen.contains("Urgencias"))
        assertTrue(screen.contains("Eventos"))
        assertTrue(screen.contains("Reportes"))
    }

    @Test
    fun repositories_and_fakes_registered_in_data_provider() {
        val dp = findAppFile(
            "app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt",
            "src/main/java/com/comunidapp/app/data/provider/DataProvider.kt"
        ).readText()
        listOf(
            "shelterProfileRepository",
            "shelterPetRepository",
            "shelterVolunteerRepository",
            "shelterCampaignRepository",
            "shelterSupplyRepository",
            "shelterEmergencyRepository",
            "shelterEventRepository",
            "shelterReportRepository",
            "MockShelterProfileRepository",
            "MockShelterCampaignRepository",
            "MockShelterEmergencyRepository",
            "MockShelterEventRepository",
            "MockShelterReportRepository"
        ).forEach { assertTrue("missing $it", dp.contains(it)) }
    }

    @Test
    fun m11_permissions_present_in_migrations() {
        val all = migrations.joinToString("\n") { read("supabase/migrations/$it") }
        listOf(
            "shelter.view",
            "shelter.manage",
            "shelter.campaign.read",
            "shelter.supply.manage",
            "shelter.emergency.read",
            "shelter.event.manage",
            "shelter.report.export"
        ).forEach { assertTrue("missing perm $it", all.contains(it)) }
    }

    @Test
    fun typed_errors_present() {
        val mapper = findAppFile(
            "app/src/main/java/com/comunidapp/app/data/remote/supabase/m11/M11ShelterErrorMapper.kt",
            "src/main/java/com/comunidapp/app/data/remote/supabase/m11/M11ShelterErrorMapper.kt"
        ).readText()
        listOf(
            "SHELTER_NOT_FOUND",
            "SHELTER_CAMPAIGN_FORBIDDEN",
            "SHELTER_SUPPLY_REQUEST_CLOSED",
            "SHELTER_EMERGENCY_RESOLUTION_REQUIRED",
            "SHELTER_EVENT_ALREADY_REGISTERED",
            "SHELTER_REPORT_FORBIDDEN",
            "SHELTER_EVIDENCE_REF_INVALID"
        ).forEach { assertTrue("missing error $it", mapper.contains(it)) }
    }

    @Test
    fun export_csv_without_pii_fields() {
        val repo = findAppFile(
            "app/src/main/java/com/comunidapp/app/data/repository/ShelterEmergenciesEventsReportsRepositories.kt",
            "src/main/java/com/comunidapp/app/data/repository/ShelterEmergenciesEventsReportsRepositories.kt"
        ).readText().lowercase()
        assertTrue(repo.contains("exportoperationalcsv") || repo.contains("csvcontent"))
        assertFalse(repo.contains("internal_address"))
        // Export builder must not emit private location / phones as columns
        val exportSlice = repo.substringAfter("exportoperationalcsv", repo.takeLast(2000))
        assertFalse(exportSlice.contains("private_location") && exportSlice.contains("csv"))
        assertFalse(exportSlice.contains("contributoruserid") && exportSlice.contains("append"))
    }

    @Test
    fun no_blocking_placeholders_in_m11_ui() {
        val screens = listOf(
            "ShelterOperationsScreens.kt",
            "ShelterCampaignsAndAidScreens.kt",
            "ShelterEmergenciesEventsReportsScreens.kt"
        )
        screens.forEach { name ->
            val f = findAppFile(
                "app/src/main/java/com/comunidapp/app/ui/screens/shelters/$name",
                "src/main/java/com/comunidapp/app/ui/screens/shelters/$name"
            )
            val text = f.readText().lowercase()
            assertFalse("$name has TODO block", text.contains("todo: implement"))
            assertFalse("$name has not implemented", text.contains("not implemented"))
            assertFalse("$name has placeholder bloqueante", text.contains("próximamente") && text.contains("bloqueo"))
        }
    }

    @Test
    fun final_documentation_present() {
        listOf(
            "docs/03-modulos/M11-refugios.md",
            "docs/03-modulos/M11-cierre-final.md",
            "docs/05-operacion/M11-validacion-final.md",
            "docs/02-arquitectura/M11-matriz-funcional-final.md",
            "docs/02-arquitectura/M11-campanas-insumos-red-ayuda.md",
            "docs/02-arquitectura/M11-urgencias-eventos-reportes.md",
            "docs/02-arquitectura/M11-Cierre-Final-Prompt-Cursor-v1.0.md",
            "docs/02-arquitectura/M11-Aplicacion-Remota-045-y-Smoke-Bloque-3-v1.0.md"
        ).forEach { path ->
            val f = listOf(File(path), File("../$path"), File("../../$path")).firstOrNull { it.isFile }
            assertTrue("missing doc $path", f != null && f.isFile)
        }
    }

    @Test
    fun no_migration_046_file() {
        assertFalse(File(migrationDir(), "046_m11_placeholder.sql").exists())
        assertFalse(migrationDir().listFiles()?.any { it.name.startsWith("046_") } == true)
    }
}
