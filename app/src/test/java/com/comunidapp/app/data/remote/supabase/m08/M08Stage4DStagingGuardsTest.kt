package com.comunidapp.app.data.remote.supabase.m08

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * LeoVer M08 Etapa 4D — guards estáticos de staging, sin acceso remoto.
 */
class M08Stage4DStagingGuardsTest {

    private fun repoRoot(): File = listOf(
        File("."),
        File(".."),
        File("../..")
    ).first { File(it, "app/build.gradle.kts").isFile }

    @Test
    fun gradleDefinesAllEnvironmentFlavorsAndStagingIdentity() {
        val gradle = File(repoRoot(), "app/build.gradle.kts").readText()

        assertTrue(gradle.contains("productFlavors"))
        for (flavor in listOf("local", "staging", "production")) {
            assertTrue("$flavor flavor missing", gradle.contains("create(\"$flavor\")"))
        }
        assertTrue(gradle.contains("applicationIdSuffix = \".staging\""))
        assertTrue(gradle.contains("LeoVer Staging"))
    }

    @Test
    fun leoverEnvironmentIsGeneratedThroughBuildConfig() {
        val gradle = File(repoRoot(), "app/build.gradle.kts").readText()

        assertTrue(gradle.contains("buildConfig = true"))
        assertTrue(gradle.contains("buildConfigField(\"String\", \"LEOVER_ENV\""))
        for (environment in listOf("local", "staging", "production")) {
            assertTrue(
                "$environment LEOVER_ENV missing",
                gradle.contains("\"LEOVER_ENV\", \"\\\"$environment\\\"\"")
            )
        }
    }

    @Test
    fun androidMainContainsNoServiceRoleCredentialUsage() {
        val main = File(repoRoot(), "app/src/main")
        val sourceFiles = main.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "kts", "xml", "properties") }
            .toList()

        val credentialPattern = Regex(
            "(?i)(service_role_key|supabase_service_role|service_role\\s*[:=])"
        )
        val offenders = sourceFiles.filter { credentialPattern.containsMatchIn(it.readText()) }
        assertTrue(
            "service role credential usage in main: ${offenders.joinToString { it.relativeTo(repoRoot()).path }}",
            offenders.isEmpty()
        )
    }

    @Test
    fun migration052IsM14Verification_and053Absent() {
        val migrations = File(repoRoot(), "supabase/migrations")
        val names = migrations.listFiles().orEmpty().map { it.name }
        assertTrue(names.any { it == "049_m13_match_review_workflow.sql" })
        assertTrue(names.any { it == "050_m14_pet_passports_and_credentials.sql" })
        assertTrue(names.any { it == "051_m14_revoke_residual_table_privileges.sql" })
        assertTrue(names.any { it == "052_m14_credential_verification_and_public_access.sql" })
        assertFalse(names.any { it.matches(Regex("^053_.*\\.sql$")) })
    }
}
