package com.comunidapp.app.viewmodel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static guards for M14 hotfix migration 051 (residual table privilege revoke). */
class M14Migration051StaticGuardsTest {
    private fun repoRoot(): File = listOf(File("."), File(".."), File("../.."))
        .first { File(it, "supabase/migrations").isDirectory }

    private fun sql051(): String =
        File(repoRoot(), "supabase/migrations/051_m14_revoke_residual_table_privileges.sql").readText()

    private val tables = listOf(
        "pet_passports",
        "pet_passport_credentials",
        "pet_passport_verification_requests",
        "pet_passport_verification_decisions",
        "pet_passport_status_history"
    )

    @Test
    fun migration_051_exists_without_052() {
        val names = File(repoRoot(), "supabase/migrations").listFiles()!!.map { it.name }
        assertTrue(names.contains("050_m14_pet_passports_and_credentials.sql"))
        assertTrue(names.contains("051_m14_revoke_residual_table_privileges.sql"))
        assertFalse("052 must not exist for this hotfix", names.any { it.startsWith("052_") })
        val nums = names.filter { it.matches(Regex("^\\d{3}_.*\\.sql$")) }
            .map { it.substring(0, 3).toInt() }
        assertEquals(51, nums.maxOrNull())
    }

    @Test
    fun revokes_all_then_grants_select_only_to_authenticated() {
        val sql = sql051().lowercase()
        tables.forEach { table ->
            assertTrue(
                "missing revoke all for $table",
                sql.contains("revoke all privileges on table public.$table")
            )
            assertTrue(
                "missing grant select for $table",
                sql.contains("grant select on table public.$table to authenticated")
            )
        }
        assertTrue(sql.contains("from authenticated, anon"))
        // No client DML / DDL-adjacent privileges re-granted
        listOf("insert", "update", "delete", "truncate", "references", "trigger").forEach { priv ->
            assertFalse(
                "must not grant $priv to clients",
                Regex("grant\\s+$priv\\b").containsMatchIn(sql)
            )
        }
        assertFalse("anon must not receive table grants", Regex("grant\\s+.+\\s+to\\s+anon").containsMatchIn(sql))
    }

    @Test
    fun does_not_alter_schema_rpcs_or_policies() {
        val sql = sql051().lowercase()
        assertFalse(Regex("""\bcreate\s+table\b""").containsMatchIn(sql))
        assertFalse(Regex("""\balter\s+table\b""").containsMatchIn(sql))
        assertFalse(Regex("""\bcreate\s+policy\b""").containsMatchIn(sql))
        assertFalse(Regex("""\bcreate\s+or\s+replace\s+function\b""").containsMatchIn(sql))
        assertFalse(Regex("""\bdrop\s+function\b""").containsMatchIn(sql))
        assertFalse(Regex("""\binsert\s+into\b""").containsMatchIn(sql))
        assertFalse(Regex("""\bupdate\s+\w+\s+set\b""").containsMatchIn(sql))
        assertFalse(Regex("""\bdelete\s+from\b""").containsMatchIn(sql))
    }
}
