package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M28Migration080StaticGuardsTest {
    private val migration080 = "080_m28_veterinary_professional_health_management.sql"

    private val tables = listOf(
        "veterinary_care_type_catalog",
        "veterinary_patient_relationships",
        "veterinary_professional_access_grants",
        "veterinary_professional_cares",
        "veterinary_vaccination_records",
        "veterinary_professional_documents",
        "veterinary_follow_ups",
        "veterinary_passport_update_proposals",
        "veterinary_export_requests"
    )

    private val rpcs = listOf(
        "m28_grant_professional_access",
        "m28_revoke_professional_access",
        "m28_create_care_draft",
        "m28_finalize_care",
        "m28_supersede_care",
        "m28_decide_passport_update_proposal",
        "m28_request_export",
        "m28_get_export_snapshot"
    )

    private fun migrationDir(): File {
        val candidates = listOf(File("supabase/migrations"), File("../supabase/migrations"))
        return candidates.firstOrNull { it.isDirectory } ?: error("missing migrations")
    }

    private fun read080(): String = File(migrationDir(), migration080).readText()

    @Test fun migration_080_present() {
        assertTrue(File(migrationDir(), migration080).isFile)
    }

    @Test fun no_duplicate_m12_tables() {
        val sql = read080()
        assertFalse(sql.contains("create table if not exists public.veterinary_clinic_profiles"))
        assertFalse(sql.contains("create table if not exists public.veterinary_appointments"))
        assertFalse(sql.contains("create table if not exists public.pets"))
    }

    @Test fun uses_veterinary_care_permissions() {
        val sql = read080()
        assertTrue(sql.contains("veterinary.care.read"))
        assertFalse(sql.contains("'m28.care.read'"))
    }

    @Test fun tables_and_rpcs_declared() {
        val sql = read080()
        tables.forEach { assertTrue("missing $it", sql.contains(it)) }
        rpcs.forEach { assertTrue("missing $it", sql.contains(it)) }
    }

    @Test fun rls_enabled_and_no_service_role_client() {
        val sql = read080()
        assertTrue(sql.contains("enable row level security"))
        assertFalse(sql.contains("auth.role() = 'service_role'"))
        assertFalse(sql.contains("set role service_role"))
    }

    @Test fun passport_proposal_separate_table() {
        val sql = read080()
        assertTrue(sql.contains("veterinary_passport_update_proposals"))
        assertFalse(sql.contains("insert into public.pet_passports") && sql.contains("m28_create_passport_update_proposal"))
    }
}
