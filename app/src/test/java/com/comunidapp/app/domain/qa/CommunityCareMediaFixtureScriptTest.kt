package com.comunidapp.app.domain.qa

import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The community-care media fixture helper must stay on the canonical
 * user-session upload path and must refuse anything outside STAGING.
 */
class CommunityCareMediaFixtureScriptTest {

    @Test
    fun helperUsesCanonicalRegisterThenStorageUpload() {
        val script = source("scripts/qa/ensure-community-care-media-fixtures.py")
        assertTrue(script.contains("canon_begin_username_login"))
        assertTrue(script.contains("canon_register_media"))
        assertTrue(script.contains("/storage/v1/object/"))
        assertTrue(script.contains("public-media"))
        assertTrue(script.contains("qa01owner"))
        assertTrue(script.contains("qa02finder"))
        assertTrue(script.contains("owner_person_id"))
        assertTrue(script.contains("EXISTING ASSET REUSED"))
        assertEqualsRegisterContract()
        assertFalse(script.contains("canon_create_lost_found"))
        assertFalse(script.contains("canon_attach_lost_found_photo"))
        assertFalse(script.contains("insert into public.media_assets"))
        assertFalse(script.contains("create_file_upload_session"))
    }

    @Test
    fun helperRefusesProdAndServiceRole() {
        val script = source("scripts/qa/ensure-community-care-media-fixtures.py")
        assertTrue(script.contains("tobqbddfcyitwgbkthhy"))
        assertTrue(script.contains("wystsapjfpdtoprlmizz"))
        assertTrue(script.contains("REFUSING PROD"))
        assertTrue(script.contains("def assert_publishable_key"))
        assertTrue(script.contains("REFUSING service_role key"))
        assertTrue(script.contains("SUPABASE_STAGING_PUBLISHABLE_KEY"))
        assertTrue(script.contains("LEOVER_QA_PASSWORD"))
        assertFalse(script.contains("SUPABASE_SERVICE_ROLE_KEY"))
        assertFalse(script.contains("postgres://"))
        assertFalse(script.contains("insert into"))
        assertTrue(script.contains("os.environ.get(\"SUPABASE_STAGING_PUBLISHABLE_KEY\""))
        assertTrue(script.contains("os.environ.get(\"LEOVER_QA_PASSWORD\""))
    }

    private fun assertEqualsRegisterContract() {
        assertTrue(CanonicalBackend.RPC_REGISTER_MEDIA == "canon_register_media")
        assertTrue(CanonicalBackend.STAGING_PROJECT_REF == "tobqbddfcyitwgbkthhy")
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
