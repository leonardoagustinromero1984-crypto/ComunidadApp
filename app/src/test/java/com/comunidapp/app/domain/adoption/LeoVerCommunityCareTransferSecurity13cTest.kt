package com.comunidapp.app.domain.adoption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Authorization contract for canon_accept_care_transfer.
 * Migration 1071 returns an already ACCEPTED transfer before the recipient check.
 * Migration 1101 authorizes the canonical target first. Live STAGING execution
 * stays outside this JVM file.
 */
class LeoVerCommunityCareTransferSecurity13cTest {

    @Test
    fun migration1071ReturnsAcceptedTransferBeforeAuthorization() {
        val body = functionBody(migration("20260905040000_1071_canonical_care_transfers.sql"), "canon_accept_care_transfer")
        val accepted = body.indexOf("if v_tr.status = 'ACCEPTED'")
        val authorize = body.indexOf("_acl_can_operate_care_actor")
        assertTrue(accepted >= 0)
        assertTrue(authorize > accepted)
        val earlyReturn = body.substring(accepted, authorize)
        assertTrue(earlyReturn.contains("return public._canon_care_transfer_json(v_tr.id)"))
        assertFalse(earlyReturn.contains("FORBIDDEN"))
        assertFalse(earlyReturn.contains("target_kind"))
    }

    @Test
    fun migration1101AuthorizesRecipientBeforeTerminalResults() {
        val sql = migration1101()
        val body = functionBody(sql, "canon_accept_care_transfer")
        val authenticated = body.indexOf("raise exception 'NOT_AUTHENTICATED'")
        val loaded = body.indexOf("from public.pet_care_transfers")
        val authorize = body.indexOf(
            "_acl_can_operate_care_actor(\n" +
                "    auth.uid(), v_tr.target_kind, v_tr.target_person_id, v_tr.target_organization_id, 'org.pets.transfer'"
        )
        val forbidden = body.indexOf("raise exception 'FORBIDDEN'")
        val accepted = body.indexOf("if v_tr.status = 'ACCEPTED'")
        val notPending = body.indexOf("raise exception 'PET_TRANSFER_NOT_PENDING'")
        assertTrue(authenticated >= 0)
        assertTrue(authenticated < loaded)
        assertTrue(loaded < authorize)
        assertTrue(authorize < forbidden)
        assertTrue(forbidden < accepted)
        assertTrue(accepted < notPending)
        assertTrue(body.contains("security definer"))
        assertFalse(body.contains("initiated_by_user_id = auth.uid()"))
        assertFalse(body.contains("target_person_id = auth.uid()"))
        assertTrue(sql.contains("REJECTED"))
        assertTrue(sql.contains("CANCELLED"))
        assertEquals(1, migrations().count { it.contains("_1101_") })
    }

    @Test
    fun authorizedAcceptedRetryDoesNotMutateOrRerunCompletion() {
        val body = functionBody(migration1101(), "canon_accept_care_transfer")
        val accepted = body.indexOf("if v_tr.status = 'ACCEPTED'")
        val notPending = body.indexOf("if v_tr.status <> 'PENDING'")
        val retry = body.substring(accepted, notPending)
        assertTrue(retry.contains("return public._canon_care_transfer_json(v_tr.id)"))
        assertFalse(retry.contains("update"))
        assertFalse(retry.contains("insert"))
        assertFalse(retry.contains("perform"))
        assertFalse(retry.contains("_canon_complete_adoptions_for_pet"))
        assertFalse(retry.contains("_canon_revoke_current_care_holders"))
        assertFalse(retry.contains("_canon_apply_current_custodian"))
        assertFalse(retry.contains("_canon_open_care_stage"))
        assertFalse(body.contains("_canon_complete_adoptions_for_pet"))
        assertFalse(migration1101().contains("create trigger"))
        assertFalse(migration1101().contains("drop trigger"))

        val pending = body.substring(notPending)
        assertTrue(pending.contains("and status = 'PENDING'"))
        assertTrue(pending.contains("_canon_revoke_current_care_holders"))
        assertTrue(pending.contains("_canon_apply_current_custodian"))
        assertTrue(pending.contains("_canon_grant_custodian_control"))
        assertTrue(pending.contains("_canon_open_care_stage"))
        assertTrue(pending.contains("'CARE_TRANSFERRED'"))
        assertTrue(pending.contains("v_tr.target_kind = 'PERSON'"))
        assertTrue(pending.contains("v_tr.target_kind = 'ORGANIZATION'"))

        val hook = functionBody(
            migration("20260921023000_1093_transit_adoption_invite.sql"),
            "_canon_on_care_transfer_accepted"
        )
        assertTrue(hook.contains("new.status = 'ACCEPTED'"))
        assertTrue(hook.contains("old.status is distinct from 'ACCEPTED'"))
        assertTrue(hook.contains("_canon_complete_adoptions_for_pet(new.pet_id)"))
    }

    @Test
    fun unauthorizedTerminalCallerIsDeniedAndTableSelectStaysRevoked() {
        val sql = migration1101()
        val body = functionBody(sql, "canon_accept_care_transfer")
        val forbidden = body.indexOf("raise exception 'FORBIDDEN'")
        val acceptedReturn = body.indexOf("return public._canon_care_transfer_json(v_tr.id)")
        assertTrue(forbidden >= 0)
        assertTrue(acceptedReturn > forbidden)
        assertFalse(
            Regex(
                """grant\s+(select|all)\s+on\s+(table\s+)?public\.pet_care_transfers""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(sql)
        )
        assertFalse(Regex("""grant\s+select""", RegexOption.IGNORE_CASE).containsMatchIn(sql))
        assertTrue(sql.contains("revoke all on function public.canon_accept_care_transfer(uuid) from public, anon"))
        assertTrue(sql.contains("grant execute on function public.canon_accept_care_transfer(uuid) to authenticated"))
        assertFalse(sql.contains("service_role"))
        assertFalse(sql.contains("grant execute on function public.canon_accept_care_transfer(uuid) to anon"))
    }

    @Test
    fun adoption1100CompletionRemainsTheOnlyPublicationClose() {
        val previous = migration("20260927190000_1100_adoption_canonical_contract.sql")
        val complete = functionBody(previous, "_canon_complete_adoptions_for_pet")
        val current = migration1101()
        assertTrue(complete.contains("set status = 'COMPLETED'"))
        assertTrue(complete.contains("set status = 'CLOSED'"))
        assertTrue(complete.contains("and status = 'OPEN'"))
        assertTrue(complete.contains("a.status = 'ACCEPTED'"))
        assertFalse(current.contains("function public._canon_complete_adoptions_for_pet"))
        assertFalse(current.contains("adoption_publications"))
        assertFalse(current.contains("adoption_applications"))
        assertFalse(current.contains("vitacora_profiles"))
        assertFalse(current.contains("function public.canon_apply_adoption"))
        assertFalse(current.contains("function public.canon_accept_adoption_application"))
    }

    @Test
    fun liveProbeRetriesTheExistingTransferWithoutCreatingFixtures() {
        val script = source("scripts/qa/probe-community-care-care-transfer-security.py")
        assertTrue(script.contains("canon_begin_username_login"))
        assertTrue(script.contains("canon_accept_care_transfer"))
        assertTrue(script.contains("7dcae39a-b8a2-4d10-9fc0-ea16d287a830"))
        assertTrue(script.contains("73e9997e-27fa-4265-946f-36001f27f815"))
        assertTrue(script.contains("qa14adopter"))
        assertTrue(script.contains("qa15adopter2"))
        assertTrue(script.contains("qa01owner"))
        assertTrue(script.contains("qa07shelter"))
        assertTrue(script.contains("tobqbddfcyitwgbkthhy"))
        assertTrue(script.contains("wystsapjfpdtoprlmizz"))
        assertTrue(script.contains("REFUSING PROD"))
        assertTrue(script.contains("REFUSING service_role key"))
        assertTrue(script.contains("pet_care_transfers"))
        assertFalse(script.contains("SUPABASE_ACCESS_TOKEN"))
        assertFalse(script.contains("SUPABASE_SERVICE_ROLE_KEY"))
        assertFalse(script.contains("canon_apply_adoption"))
        assertFalse(script.contains("canon_initiate_care_transfer"))
        assertFalse(script.contains("canon_create_adoption"))
        assertFalse(script.contains("canon_create_lost_found"))
        assertFalse(script.contains("insert into"))
    }

    private fun migration1101(): String =
        migration("20260927200000_1101_care_transfer_accept_authorization.sql")

    private fun migrations(): List<String> {
        val dir = sourceFile("infra/supabase-canonical/supabase/migrations")
        return dir.listFiles()?.map { it.name }?.sorted().orEmpty()
    }

    private fun migration(name: String): String =
        source("infra/supabase-canonical/supabase/migrations/$name")

    private fun functionBody(sql: String, name: String): String {
        val marker = "function public.$name"
        val start = sql.indexOf(marker)
        check(start >= 0) { "MISSING:$name" }
        val end = sql.indexOf("\$\$;", start)
        check(end > start) { "UNCLOSED:$name" }
        return sql.substring(start, end)
    }

    private fun source(relativePath: String): String = sourceFile(relativePath).readText()

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath"),
            File(System.getProperty("user.dir"), relativePath),
            File(System.getProperty("user.dir"), "../$relativePath")
        )
        return candidates.firstOrNull { it.exists() } ?: error("SOURCE_NOT_FOUND:$relativePath")
    }
}
