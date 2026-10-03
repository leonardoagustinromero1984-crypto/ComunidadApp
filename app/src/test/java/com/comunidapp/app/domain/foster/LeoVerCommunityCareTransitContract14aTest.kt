package com.comunidapp.app.domain.foster

import com.comunidapp.app.data.repository.CanonicalFosterTransitDecoding
import com.comunidapp.app.domain.canonical.CanonicalBackend
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Migration 1102 read, idempotency, and eligibility contract.
 * Live STAGING execution stays outside this JVM file. 1102 is not applied here.
 */
class LeoVerCommunityCareTransitContract14aTest {

    @Test
    fun migration1102IsTheOnlyNewTransitContractAndFollows1101() {
        val names = migrations()
        assertEquals(1, names.count { it.contains("_1102_") })
        assertEquals(1, names.count { it.contains("_1101_") })
        val file = names.single { it.contains("_1102_") }
        assertEquals("20260927210000_1102_foster_transit_read_contract.sql", file)
        assertTrue(file > names.single { it.contains("_1101_") })
        assertFalse(migration1102().contains("create or replace function public.canon_select_foster_applicant"))
        assertFalse(migration1102().contains("create or replace function public.canon_list_foster_request_applications"))
        assertFalse(migration1102().contains("create or replace function public.canon_accept_care_transfer"))
        assertFalse(migration1102().contains("function public.canon_end_"))
    }

    @Test
    fun responsibleListsOnlyRequestsTheyCanManage() {
        val sql = migration1102()
        val list = functionBody(sql, "canon_list_my_foster_requests")
        val gate = functionBody(sql, "_canon_can_manage_foster_request")
        assertTrue(list.contains("public._canon_can_manage_foster_request(auth.uid(), r.id)"))
        assertTrue(list.startsWith("function public.canon_list_my_foster_requests()"))
        assertFalse(list.contains("p_user_id"))
        assertTrue(gate.contains("r.requested_by = p_user_id"))
        assertTrue(gate.contains("public._acl_pet_holder(p_user_id, r.pet_id)"))
        assertTrue(gate.contains("a.foster_user_id = p_user_id"))
        assertTrue(gate.contains("a.status in ('PENDING', 'SELECTED')"))
        assertTrue(list.contains("'status', r.status"))
        assertTrue(list.contains("'needs', r.needs"))
        assertTrue(list.contains("'notes', r.notes"))
        assertTrue(list.contains("'selected_application_id', r.selected_application_id"))
        assertTrue(list.contains("'pet_id', r.pet_id"))
        assertTrue(list.contains("'pet_name', p.name"))
        assertTrue(list.contains("'REQUESTED'") || sql.contains("REQUESTED, MATCHED, ACTIVE"))
        assertTrue(list.contains("security definer"))
        assertFalse(Regex("""\bwhere\s+true\b""").containsMatchIn(list))
    }

    @Test
    fun fosterReadsOnlyTheirOwnApplications() {
        val list = functionBody(migration1102(), "canon_list_my_foster_applications")
        assertTrue(list.startsWith("function public.canon_list_my_foster_applications()"))
        assertTrue(list.contains("where a.foster_user_id = auth.uid()"))
        assertFalse(list.contains("p_foster"))
        assertFalse(list.contains("p_user_id"))
        assertFalse(list.contains("p_application_id"))
        assertTrue(list.contains("'status', a.status"))
        assertTrue(list.contains("'request_status', r.status"))
        assertTrue(list.contains("'PENDING'") || migration1102().contains("'PENDING', 'SELECTED'"))
        assertTrue(migration1102().contains("NOT_SELECTED"))
        assertTrue(migration1102().contains("WITHDRAWN"))
        assertTrue(list.contains("security definer"))
    }

    @Test
    fun responsibleApplicationListStaysHolderGated() {
        val previous = functionBody(
            migration("20260921023000_1093_transit_adoption_invite.sql"),
            "canon_list_foster_request_applications"
        )
        assertTrue(previous.contains("raise exception 'FORBIDDEN'"))
        assertTrue(previous.contains("public._acl_pet_holder(auth.uid(), v_req.pet_id)"))
        assertTrue(previous.contains("v_req.requested_by is distinct from auth.uid()"))
        assertFalse(migration1102().contains("function public.canon_list_foster_request_applications"))
    }

    @Test
    fun activeTransitReadIsLimitedToHolderRequesterOrSelectedFoster() {
        val sql = migration1102()
        val read = functionBody(sql, "canon_get_active_foster_transit")
        val gate = functionBody(sql, "_canon_caller_may_read_foster_transit")
        val forbidden = read.indexOf("raise exception 'FORBIDDEN'")
        val payload = read.indexOf("jsonb_build_object")
        assertTrue(read.contains("public._canon_caller_may_read_foster_transit(auth.uid(), p_pet_id)"))
        assertTrue(forbidden >= 0)
        assertTrue(payload > forbidden)
        assertTrue(gate.contains("public._acl_pet_holder(p_user_id, p_pet_id)"))
        assertTrue(gate.contains("r.requested_by = p_user_id"))
        assertTrue(gate.contains("a.foster_user_id = p_user_id"))
        assertTrue(gate.contains("a.status = 'SELECTED'"))
        assertFalse(gate.contains("true"))
        assertTrue(read.contains("r.status in ('MATCHED', 'ACTIVE')"))
        assertTrue(read.contains("'request_id', v_req.id"))
        assertTrue(read.contains("'request_status', v_req.status"))
        assertTrue(read.contains("'selected_application_id', v_req.selected_application_id"))
        assertTrue(read.contains("'application_status', v_application_status"))
        assertTrue(read.contains("'placement_id', v_placement_id"))
        assertTrue(read.contains("'placement_status', v_placement_status"))
        assertTrue(read.contains("'pet_id', v_req.pet_id"))
        assertTrue(read.contains("'foster_user_id', v_foster_user_id"))
        assertTrue(read.contains("'temporary_holder_kind', 'PERSON'"))
        assertTrue(read.contains("'temporary_holder_role', 'AUTHORIZED'"))
        assertTrue(read.contains("'requested_by', v_req.requested_by"))
        assertTrue(read.contains("'responsible_organization_id'"))
        assertTrue(read.contains("'responsible_role', 'RESPONSIBLE'"))
        assertFalse(Regex("""\binsert\b""").containsMatchIn(read))
        assertFalse(Regex("""\bupdate\b""").containsMatchIn(read))
        assertFalse(Regex("""\bdelete\b""").containsMatchIn(read))
        assertFalse(read.contains("current_custodian"))
        assertFalse(read.contains("OWNER"))
        assertFalse(read.contains("vitacora_moments"))
    }

    @Test
    fun directTableSelectStaysRevokedAndGrantsStayAuthenticated() {
        val sql = migration1102()
        listOf(
            "public.foster_care_requests",
            "public.foster_care_applications",
            "public.foster_placements",
            "public.foster_profiles",
            "public.pet_responsibility_links",
            "public.vitacora_profiles"
        ).forEach { table ->
            assertTrue(sql.contains("revoke all on table $table from public, anon, authenticated"))
        }
        assertFalse(Regex("""grant\s+select""", RegexOption.IGNORE_CASE).containsMatchIn(sql))
        assertFalse(Regex("""grant\s+all\b""", RegexOption.IGNORE_CASE).containsMatchIn(sql))
        assertFalse(sql.contains("service_role"))
        assertFalse(Regex("""grant\s+execute\s+on\s+function\s+[^;]+\sto\s+(public|anon)\b""").containsMatchIn(sql))
        assertTrue(sql.contains("grant execute on function public.canon_list_my_foster_requests() to authenticated"))
        assertTrue(sql.contains("grant execute on function public.canon_list_my_foster_applications() to authenticated"))
        assertTrue(sql.contains("grant execute on function public.canon_get_active_foster_transit(uuid) to authenticated"))
        assertTrue(sql.contains("revoke all on function public._canon_assert_foster_eligible() from public, anon, authenticated"))
        assertTrue(sql.contains("revoke all on function public._canon_can_manage_foster_request(uuid, uuid) from public, anon, authenticated"))
        assertTrue(sql.contains("revoke all on function public._canon_caller_may_read_foster_transit(uuid, uuid) from public, anon, authenticated"))
        assertFalse(sql.contains("create policy"))
    }

    @Test
    fun requestCreationReusesOneNonTerminalRowAndKeepsTerminalHistory() {
        val sql = migration1102()
        val request = functionBody(sql, "canon_request_foster_for_pet")
        val index = sql.substring(
            sql.indexOf("create unique index if not exists foster_care_requests_one_nonterminal_pet_uidx"),
            sql.indexOf("create or replace function public._canon_assert_foster_eligible")
        )
        assertTrue(index.contains("where status in ('REQUESTED', 'MATCHED', 'ACTIVE')"))
        assertFalse(index.contains("COMPLETED"))
        assertFalse(index.contains("CANCELLED"))
        assertTrue(request.contains("public._acl_pet_holder(auth.uid(), p_pet_id)"))
        assertTrue(request.contains("raise exception 'FORBIDDEN'"))
        val reuse = request.indexOf("and r.status in ('REQUESTED', 'MATCHED', 'ACTIVE')")
        val insert = request.indexOf("insert into public.foster_care_requests")
        assertTrue(reuse >= 0)
        assertTrue(insert > reuse)
        assertTrue(request.substring(reuse, insert).contains("return v_id"))
        assertTrue(request.contains("when unique_violation then"))
        assertTrue(request.contains("pg_advisory_xact_lock"))
        assertFalse(Regex("""\bdelete\b""").containsMatchIn(request))
        assertFalse(sql.contains("delete from public.foster_care_requests"))
        assertTrue(sql.contains("FOSTER_REQUEST_DUPLICATES_PRESENT"))
        assertTrue(sql.contains("COMPLETED and CANCELLED history is preserved"))
    }

    @Test
    fun selectionCustodyAndIdentityStayOnThe1093Contract() {
        val select = functionBody(
            migration("20260921023000_1093_transit_adoption_invite.sql"),
            "canon_select_foster_applicant"
        )
        assertTrue(select.contains("set status = 'SELECTED'"))
        assertTrue(select.contains("set status = 'NOT_SELECTED'"))
        assertTrue(select.contains("set status = 'ACTIVE'"))
        assertTrue(select.contains("insert into public.foster_placements"))
        assertTrue(select.contains("'AUTHORIZED'"))
        assertTrue(select.contains("'PERSON'"))
        assertFalse(select.contains("'OWNER'"))
        assertFalse(select.contains("current_custodian"))
        assertFalse(select.contains("vitacora_moments"))
        assertFalse(select.contains("pet_responsibility_events"))
        assertTrue(select.contains("v_req.pet_id"))
        val current = migration1102()
        listOf(
            "canon_request_foster_for_pet",
            "canon_apply_to_foster_request",
            "canon_list_open_foster_requests",
            "canon_list_my_foster_requests",
            "canon_list_my_foster_applications",
            "canon_get_active_foster_transit"
        ).forEach { name ->
            val body = functionBody(current, name)
            assertFalse(name, body.contains("current_custodian"))
            assertFalse(name, body.contains("vitacora_moments"))
            assertFalse(name, body.contains("insert into public.pets"))
            assertFalse(name, body.contains("insert into public.pet_responsibility_links"))
        }
        assertFalse(current.contains("insert into public.pet_responsibility_links"))
        assertFalse(current.contains("update public.pet_responsibility_links"))
        assertFalse(current.contains("update public.pets"))
        assertFalse(current.contains("insert into public.pets"))
        assertFalse(current.contains("insert into public.vitacora_moments"))
        assertFalse(current.contains("canon_initiate_care_transfer"))
        assertFalse(current.contains("canon_accept_care_transfer"))
        assertTrue(current.contains("TRANSIT_START_VITACORA_EVENT: MISSING"))
        assertTrue(current.contains("END TRANSIT"))
        assertTrue(current.contains("The foster never becomes OWNER"))
        assertTrue(current.contains("The organization RESPONSIBLE link stays ACTIVE"))
    }

    @Test
    fun openDiscoveryRequiresTheExistingFosterEligibilityGate() {
        val sql = migration1102()
        val open = functionBody(sql, "canon_list_open_foster_requests")
        val gate = functionBody(sql, "_canon_assert_foster_eligible")
        val apply = functionBody(sql, "canon_apply_to_foster_request")
        assertTrue(open.contains("perform public._canon_assert_foster_eligible()"))
        assertTrue(apply.contains("perform public._canon_assert_foster_eligible()"))
        assertTrue(apply.contains("on conflict (request_id, foster_user_id) do update set status = 'PENDING'"))
        assertTrue(gate.contains("raise exception 'FOSTER_NOT_ELIGIBLE'"))
        assertTrue(gate.contains("raise exception 'BASE_LOCATION_REQUIRED'"))
        assertTrue(gate.contains("c.capability = 'FOSTER'"))
        assertTrue(gate.contains("c.verification_status = 'VERIFIED'"))
        assertTrue(gate.contains("fp.active"))
        assertTrue(gate.contains("c.active"))
        assertTrue(gate.contains("public._canon_person_has_base_location(auth.uid())"))
        assertFalse(gate.contains("species_pref"))
        assertFalse(gate.contains("age_pref"))
        assertFalse(gate.contains("accepts_treatment"))
        assertFalse(gate.contains("other_animals_ok"))
        assertFalse(gate.contains("capacity"))
        assertFalse(open.contains("species_pref"))
        assertFalse(open.contains("age_pref"))
        assertFalse(open.contains("capacity"))
        assertTrue(open.contains("where r.status = 'REQUESTED'"))
        assertTrue(sql.contains("species_pref, age_pref, accepts_treatment, other_animals_ok, and capacity are advisory and are not filters."))
    }

    @Test
    fun fosterStaysOutOfFoundFanoutAndCareTransfer1101StaysTheAcceptContract() {
        val fanout = functionBody(
            migration("20260927180000_1099_responder_fanout_postgis.sql"),
            "_canon_fanout_lost_found_recipients"
        )
        assertTrue(fanout.contains("c.capability = 'RESCUER'"))
        assertFalse(fanout.contains("'FOSTER'"))
        assertFalse(migration1102().contains("_canon_fanout_lost_found_recipients"))
        assertFalse(migration1102().contains("lost_found_alert_recipients"))

        val accept = functionBody(
            migration("20260927200000_1101_care_transfer_accept_authorization.sql"),
            "canon_accept_care_transfer"
        )
        val authorize = accept.indexOf("_acl_can_operate_care_actor")
        val accepted = accept.indexOf("if v_tr.status = 'ACCEPTED'")
        assertTrue(authorize >= 0)
        assertTrue(accepted > authorize)
        assertFalse(migration1102().contains("pet_care_transfers"))
        assertFalse(migration1102().contains("_canon_complete_adoptions_for_pet"))
    }

    @Test
    fun androidRepositoryConstantsStayOnThe1102ReadContract() {
        val backend = source("app/src/main/java/com/comunidapp/app/domain/canonical/CanonicalBackend.kt")
        val repository = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalFosterTransitRepository.kt")
        assertTrue(backend.contains("const val RPC_LIST_MY_FOSTER_REQUESTS = \"canon_list_my_foster_requests\""))
        assertTrue(backend.contains("const val RPC_LIST_MY_FOSTER_APPLICATIONS = \"canon_list_my_foster_applications\""))
        assertTrue(backend.contains("const val RPC_GET_ACTIVE_FOSTER_TRANSIT = \"canon_get_active_foster_transit\""))
        assertEquals("canon_list_my_foster_requests", CanonicalBackend.RPC_LIST_MY_FOSTER_REQUESTS)
        assertEquals("canon_list_my_foster_applications", CanonicalBackend.RPC_LIST_MY_FOSTER_APPLICATIONS)
        assertEquals("canon_get_active_foster_transit", CanonicalBackend.RPC_GET_ACTIVE_FOSTER_TRANSIT)
        assertTrue(repository.contains("CanonicalBackend.RPC_LIST_MY_FOSTER_REQUESTS"))
        assertTrue(repository.contains("CanonicalBackend.RPC_LIST_MY_FOSTER_APPLICATIONS"))
        assertTrue(repository.contains("CanonicalBackend.RPC_GET_ACTIVE_FOSTER_TRANSIT"))
        assertTrue(repository.contains("interface CanonicalFosterTransitRepository"))
        assertFalse(repository.contains("Mock"))
        assertFalse(repository.contains("Result.success(emptyList())"))
    }

    @Test
    fun transitRoutesStayRegisteredForTheLiveScreens() {
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(graph.contains("composable(NavRoutes.FOSTER_CARE_REQUEST)"))
        assertTrue(graph.contains("composable(NavRoutes.FOSTER_OPEN_REQUESTS)"))
        assertTrue(graph.contains("composable(NavRoutes.FOSTER_CHOOSE_APPLICANT)"))
        assertTrue(graph.contains("RequestFosterForPetScreen"))
        assertTrue(graph.contains("OpenFosterRequestsScreen"))
        assertTrue(graph.contains("ChooseFosterApplicantScreen"))
    }

    @Test
    fun decoderKeepsTextNeedsAndTransitRoles() {
        val json = Json { ignoreUnknownKeys = true }
        val requests = CanonicalFosterTransitDecoding.requests(
            json.parseToJsonElement(
                """
                [{
                  "id": "11111111-1111-1111-1111-111111111111",
                  "pet_id": "22222222-2222-2222-2222-222222222222",
                  "pet_name": "Luna",
                  "species": "DOG",
                  "status": "ACTIVE",
                  "needs": "medication",
                  "notes": "quiet street",
                  "selected_application_id": "33333333-3333-3333-3333-333333333333",
                  "placement_status": "OPEN"
                }]
                """.trimIndent()
            )
        )
        assertEquals("ACTIVE", requests.single().status)
        assertEquals("medication", requests.single().needs)
        assertEquals("Luna", requests.single().petName)
        assertEquals("OPEN", requests.single().placementStatus)

        val applications = CanonicalFosterTransitDecoding.applications(
            JsonPrimitive(
                """[{"id":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","request_id":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","status":"NOT_SELECTED","pet_id":"cccccccc-cccc-cccc-cccc-cccccccccccc","request_status":"ACTIVE"}]"""
            )
        )
        assertEquals("NOT_SELECTED", applications.single().status)
        assertEquals("ACTIVE", applications.single().requestStatus)
        assertTrue(CanonicalFosterTransitDecoding.requests(JsonNull).isEmpty())
        assertTrue(CanonicalFosterTransitDecoding.applications(json.parseToJsonElement("[]")).isEmpty())

        val transit = CanonicalFosterTransitDecoding.transit(
            json.parseToJsonElement(
                """
                {
                  "canon_get_active_foster_transit": {
                    "request_id": "11111111-1111-1111-1111-111111111111",
                    "request_status": "ACTIVE",
                    "selected_application_id": "33333333-3333-3333-3333-333333333333",
                    "application_status": "SELECTED",
                    "placement_id": "44444444-4444-4444-4444-444444444444",
                    "placement_status": "OPEN",
                    "pet_id": "22222222-2222-2222-2222-222222222222",
                    "foster_user_id": "55555555-5555-5555-5555-555555555555",
                    "temporary_holder_kind": "PERSON",
                    "temporary_holder_role": "AUTHORIZED",
                    "requested_by": "66666666-6666-6666-6666-666666666666",
                    "responsible_organization_id": "77777777-7777-7777-7777-777777777777",
                    "responsible_role": "RESPONSIBLE",
                    "needs": "rest"
                  }
                }
                """.trimIndent()
            )
        )
        assertEquals("AUTHORIZED", transit.temporaryHolderRole)
        assertEquals("PERSON", transit.temporaryHolderKind)
        assertEquals("RESPONSIBLE", transit.responsibleRole)
        assertEquals("SELECTED", transit.applicationStatus)
        assertEquals("rest", transit.needs)
        assertNull(transit.notes)
        assertFalse(transit.temporaryHolderRole == "OWNER")
    }

    private fun migration1102(): String =
        migration("20260927210000_1102_foster_transit_read_contract.sql")

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
