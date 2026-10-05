package com.comunidapp.app.domain.qa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QaActorBootstrapContractTest {

    @Test
    fun bootstrap_accepts_only_staging_and_reuses_existing_passwords() {
        val script = source("scripts/qa/bootstrap-staging-qa.ps1")
        val shelters = source("infra/supabase-canonical/qa/bootstrap_staging_qa_shelters.sql")
        assertTrue(script.contains("tobqbddfcyitwgbkthhy"))
        assertTrue(script.contains("QA_BOOTSTRAP_ABORT_UNKNOWN_PROJECT"))
        assertTrue(script.contains("QA_BOOTSTRAP_ABORT_LEGACY_PROJECT"))
        assertTrue(script.contains("wystsapjfpdtoprlmizz"))
        assertTrue(script.contains("AUTH_REUSED"))
        assertFalse(script.contains("AUTH_UPDATED"))
        assertFalse(script.contains("Method Put"))
        assertFalse(script.contains("seed_community_care_02.sql"))
        assertTrue(script.contains("seed_community_care_test_actors.sql"))
        assertTrue(script.contains("QA_BOOTSTRAP_ABORT_REAL_USER"))
        val reused = script.substringAfter("AUTH_REUSED").take(180)
        assertFalse(reused.contains("password"))
        assertTrue(shelters.contains("QA_BOOTSTRAP_ABORT_PROJECT_REF"))
        assertTrue(shelters.contains("tobqbddfcyitwgbkthhy"))
        assertFalse(shelters.contains("auth.users"))
        assertFalse(shelters.contains("encrypted_password"))
        assertFalse(shelters.contains("order by created_at"))
    }

    @Test
    fun bootstrap_is_idempotent_for_qa01_through_qa16_and_both_shelters() {
        val script = source("scripts/qa/bootstrap-staging-qa.ps1")
        val shelters = source("infra/supabase-canonical/qa/bootstrap_staging_qa_shelters.sql")
        val actors = source("infra/supabase-canonical/qa/seed_community_care_test_actors.sql")
        for (n in 1..16) {
            val id = "QA%02d".format(n)
            assertTrue(script.contains(id))
        }
        listOf(
            "qa01owner", "qa02finder", "qa03rescuer", "qa04rescuer2",
            "qa05unavailable", "qa06foster", "qa07shelter", "qa08pending",
            "qa09noreq", "qa10vet", "qa11proa", "qa12proind",
            "qa13shop", "qa14adopter", "qa15adopter2", "qa16vetnorte"
        ).forEach { username ->
            assertTrue(actors.contains(username))
            assertTrue(shelters.contains("'$username'") || actors.contains("'$username'"))
        }
        assertTrue(shelters.contains("qa-cc02-shelter-n"))
        assertTrue(shelters.contains("qa-cc02-shelter-u"))
        assertTrue(shelters.contains("qa07shelter"))
        assertTrue(shelters.contains("qa08pending"))
        assertTrue(shelters.contains("organization_memberships"))
        assertTrue(shelters.contains("permission_codes"))
        assertTrue(shelters.contains("scope = 'ORG'"))
        assertTrue(shelters.contains("where not exists"))
        assertTrue(shelters.contains("on conflict"))
        assertTrue(shelters.contains("QA_BOOTSTRAP_ABORT_ACTORS_MISSING"))
        assertTrue(shelters.contains("QA_BOOTSTRAP_ABORT_REAL_USER"))
        assertTrue(actors.contains("on conflict"))
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath"),
            File(System.getProperty("user.dir"), relativePath),
            File(System.getProperty("user.dir"), "../$relativePath")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("SOURCE_NOT_FOUND:$relativePath")
    }
}
