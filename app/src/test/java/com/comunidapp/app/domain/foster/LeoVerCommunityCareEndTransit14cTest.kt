package com.comunidapp.app.domain.foster

import com.comunidapp.app.data.repository.CanonicalFosterTransitApplication
import com.comunidapp.app.data.repository.CanonicalFosterTransitCompletion
import com.comunidapp.app.data.repository.CanonicalFosterTransitDecoding
import com.comunidapp.app.data.repository.CanonicalFosterTransitRecovery
import com.comunidapp.app.data.repository.CanonicalFosterTransitRequest
import com.comunidapp.app.domain.canonical.CanonicalBackend
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * End-transit contract for migration 1103.
 * The Bruno completion itself runs in scripts/qa/probe-community-care-live-transit.py.
 */
class LeoVerCommunityCareEndTransit14cTest {

    @Test
    fun migration1103IsTheOnlyCompletionContractAndFollows1102() {
        val names = migrations()
        assertEquals(1, names.count { it.contains("_1103_") })
        val file = names.single { it.contains("_1103_") }
        assertEquals("20260927220000_1103_foster_transit_completion.sql", file)
        val previous = names.last { it.contains("_1102_") }
        assertTrue(names.indexOf(file) == names.indexOf(previous) + 1)
        val sql = migration1103()
        assertTrue(sql.contains("function public.canon_complete_foster_transit"))
        assertFalse(sql.contains("create or replace function public.canon_select_foster_applicant"))
        assertFalse(sql.contains("create or replace function public.canon_get_active_foster_transit"))
        assertFalse(sql.contains("create or replace function public.canon_list_my_foster_applications"))
        assertFalse(sql.contains("create or replace function public.canon_accept_care_transfer"))
        assertFalse(sql.contains("create or replace function public._canon_can_manage_foster_request"))
        assertFalse(sql.contains("create or replace function public.canon_end_pet_responsibility"))
    }

    @Test
    fun onlyTheRequestManagerCanCompleteAndTheFosterIsNotAdded() {
        val body = functionBody(migration1103(), "canon_complete_foster_transit")
        val manage = functionBody(
            migration("20260927210000_1102_foster_transit_read_contract.sql"),
            "_canon_can_manage_foster_request"
        )
        val auth = body.indexOf("public._canon_can_manage_foster_request(auth.uid(), v_req.id)")
        val forbidden = body.indexOf("raise exception 'FORBIDDEN'")
        val completed = body.indexOf("if v_req.status = 'COMPLETED'")
        assertTrue(auth >= 0)
        assertTrue(forbidden > auth)
        assertTrue(completed > forbidden)
        assertTrue(body.contains("raise exception 'NOT_AUTHENTICATED'"))
        assertTrue(manage.contains("r.requested_by = p_user_id"))
        assertTrue(manage.contains("a.status in ('PENDING', 'SELECTED')"))
        assertTrue(manage.contains("a.foster_user_id = p_user_id"))
        assertFalse(body.contains("FOSTER_NOT_ELIGIBLE"))
        assertTrue(migration1103().contains("The selected foster is not given a new completion privilege"))
        assertTrue(migration1103().contains("Foster-initiated early termination stays a later flow"))
    }

    @Test
    fun activeRequestAndOpenPlacementBecomeTerminalTogether() {
        val body = functionBody(migration1103(), "canon_complete_foster_transit")
        assertTrue(body.contains("elsif v_req.status <> 'ACTIVE'"))
        assertTrue(body.contains("raise exception 'STATUS_INVALID'"))
        assertTrue(body.contains("and status = 'OPEN'"))
        assertTrue(body.contains("set status = 'COMPLETED'"))
        assertTrue(body.contains("set status = 'CLOSED'"))
        assertTrue(body.contains("update public.foster_care_requests"))
        assertTrue(body.contains("update public.foster_placements"))
        val active = branchAfter(body, "\n  else", "\n  end if;")
        assertTrue(active.contains("set status = 'COMPLETED'"))
        assertTrue(active.contains("set status = 'CLOSED'"))
        assertTrue(active.contains("set status = 'ENDED'"))
    }

    @Test
    fun fosterAuthorizedLinkEndsAndOrganizationResponsibleStays() {
        val body = functionBody(migration1103(), "canon_complete_foster_transit")
        assertTrue(body.contains("and role = 'AUTHORIZED'"))
        assertTrue(body.contains("and holder_kind = 'PERSON'"))
        assertTrue(body.contains("set status = 'ENDED'"))
        assertTrue(body.contains("l.holder_kind = 'ORGANIZATION'"))
        assertTrue(body.contains("l.role = 'RESPONSIBLE'"))
        assertTrue(body.contains("l.status = 'ACTIVE'"))
        assertFalse(body.contains("set role = 'RESPONSIBLE'"))
        val linkUpdate = branchAfter(body, "update public.pet_responsibility_links")
        assertTrue(linkUpdate.contains("holder_kind = 'PERSON'"))
        assertTrue(linkUpdate.contains("role = 'AUTHORIZED'"))
        assertFalse(linkUpdate.contains("ORGANIZATION"))
        assertTrue(body.contains("where id = v_link.id"))
        assertTrue(migration1103().contains("The organization RESPONSIBLE link stays ACTIVE"))
    }

    @Test
    fun fosterNeverBecomesOwnerAndPetIdentityStays() {
        val sql = migration1103()
        val body = functionBody(sql, "canon_complete_foster_transit")
        assertTrue(sql.contains("The foster never becomes OWNER"))
        assertFalse(body.contains("'OWNER'"))
        assertFalse(body.contains("insert into public.pet_responsibility_links"))
        assertFalse(body.contains("insert into public.pets"))
        assertFalse(body.contains("update public.pets"))
        assertFalse(body.contains("current_custodian"))
        assertTrue(body.contains("'pet_id', v_req.pet_id"))
        assertTrue(sql.contains("No custodian write"))
    }

    @Test
    fun sameVitaCoraIsReadAndNoTransitEndMomentIsInvented() {
        val sql = migration1103()
        val body = functionBody(sql, "canon_complete_foster_transit")
        assertTrue(sql.contains("TRANSIT_END_MOMENT: NOT_CREATED_BY_CURRENT_DOMAIN_DESIGN"))
        assertTrue(body.contains("from public.vitacora_profiles v"))
        assertTrue(body.contains("where v.pet_id = v_req.pet_id"))
        assertTrue(body.contains("'vitacora_pet_id', v_vita_pet"))
        assertFalse(body.contains("insert into public.vitacora_profiles"))
        assertFalse(body.contains("update public.vitacora_profiles"))
        assertFalse(body.contains("delete from public.vitacora_profiles"))
        assertFalse(body.contains("vitacora_moments"))
        assertFalse(sql.contains("insert into public.vitacora_moments"))
    }

    @Test
    fun completionDoesNotCreateACareTransferOrAdoption() {
        val sql = migration1103()
        val body = functionBody(sql, "canon_complete_foster_transit")
        assertTrue(sql.contains("No care transfer. No adoption."))
        assertFalse(sql.contains("pet_care_transfers"))
        assertFalse(sql.contains("canon_initiate_care_transfer"))
        assertFalse(sql.contains("canon_accept_care_transfer"))
        assertFalse(sql.contains("adoption_publications"))
        assertFalse(sql.contains("adoption_applications"))
        assertFalse(body.contains("insert into public.pets"))
        assertFalse(body.contains("insert into public.foster_care_requests"))
        assertFalse(body.contains("insert into public.foster_care_applications"))
        assertFalse(body.contains("insert into public.foster_placements"))
    }

    @Test
    fun selectedApplicationStaysHistorical() {
        val sql = migration1103()
        val body = functionBody(sql, "canon_complete_foster_transit")
        assertTrue(sql.contains("SELECTED remains the historical application status"))
        assertTrue(sql.contains("foster_care_applications.status has no COMPLETED value"))
        assertTrue(body.contains("v_app.status <> 'SELECTED'"))
        assertFalse(body.contains("update public.foster_care_applications"))
        assertTrue(body.contains("'application_status', v_app.status"))
    }

    @Test
    fun authorizedRetryReturnsBeforeAnyWrite() {
        val body = functionBody(migration1103(), "canon_complete_foster_transit")
        val completed = body.indexOf("if v_req.status = 'COMPLETED'")
        val other = body.indexOf("elsif v_req.status <> 'ACTIVE'")
        val branch = body.substring(completed, other)
        assertTrue(branch.contains("v_idempotent := true"))
        assertFalse(branch.contains("update "))
        assertFalse(branch.contains("insert into"))
        assertTrue(branch.contains("status = 'CLOSED'"))
        assertTrue(branch.contains("status = 'ENDED'"))
        val writes = body.indexOf("update public.foster_care_requests")
        assertTrue(writes > other)
        assertTrue(body.contains("'idempotent', v_idempotent"))
    }

    @Test
    fun unauthorizedTerminalRetryIsRejectedBeforeTheResult() {
        val sql = migration1103()
        val body = functionBody(sql, "canon_complete_foster_transit")
        val forbidden = body.indexOf("raise exception 'FORBIDDEN'")
        val completed = body.indexOf("if v_req.status = 'COMPLETED'")
        val returned = body.indexOf("return jsonb_build_object")
        assertTrue(forbidden >= 0)
        assertTrue(completed > forbidden)
        assertTrue(returned > completed)
        assertTrue(sql.contains("Authorization runs before any COMPLETED result"))
        assertTrue(sql.contains("canon_end_pet_responsibility is not the transit operation"))
        assertFalse(body.contains("canon_end_pet_responsibility"))
        assertTrue(sql.contains("returns a terminal link id before checking responsibility.manage"))
    }

    @Test
    fun activeReadStaysOn1102AndHidesAFinishedTransit() {
        val read = functionBody(
            migration("20260927210000_1102_foster_transit_read_contract.sql"),
            "canon_get_active_foster_transit"
        )
        val apps = functionBody(
            migration("20260927210000_1102_foster_transit_read_contract.sql"),
            "canon_list_my_foster_applications"
        )
        assertTrue(read.contains("r.status in ('MATCHED', 'ACTIVE')"))
        assertTrue(read.contains("raise exception 'NOT_FOUND'"))
        assertFalse(read.contains("'COMPLETED'"))
        assertTrue(apps.contains("'request_status', r.status"))
        assertTrue(apps.contains("'placement_status', pl.status"))
        assertFalse(migration1103().contains("create or replace function public.canon_get_active_foster_transit"))
        assertFalse(migration1103().contains("create or replace function public.canon_list_my_foster_requests"))
    }

    @Test
    fun androidRefreshesTheBackendInsteadOfFakingClosure() {
        val screens = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterCareRequestScreens.kt")
        val repository = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalFosterTransitRepository.kt")
        val backend = source("app/src/main/java/com/comunidapp/app/domain/canonical/CanonicalBackend.kt")
        assertTrue(screens.contains("Finalizar tránsito"))
        assertTrue(screens.contains("¿Finalizar tránsito?"))
        assertTrue(screens.contains("repository.completeFosterTransit(requestId)"))
        val after = screens.substringAfter("repository.completeFosterTransit(requestId)")
        assertTrue(after.contains("reload()"))
        assertTrue(after.contains("finishedRequest.status != \"COMPLETED\""))
        assertTrue(after.contains("finishedRequest.placementStatus != \"CLOSED\""))
        assertTrue(after.contains("transit != null"))
        assertFalse(after.contains("status = \"COMPLETED\""))
        assertFalse(screens.contains("copy(status"))
        assertFalse(screens.contains("MockFoster"))
        assertTrue(repository.contains("CanonicalBackend.RPC_COMPLETE_FOSTER_TRANSIT"))
        assertTrue(repository.contains("No se pudo finalizar el tránsito."))
        assertFalse(repository.contains("Result.success(CanonicalFosterTransitCompletion"))
        assertEquals("canon_complete_foster_transit", CanonicalBackend.RPC_COMPLETE_FOSTER_TRANSIT)
        assertTrue(backend.contains("const val RPC_COMPLETE_FOSTER_TRANSIT = \"canon_complete_foster_transit\""))
        assertTrue(screens.contains("El hogar temporal ya no figura como cuidador activo."))
    }

    @Test
    fun historicalSelectionIsNotShownAsActiveTemporaryCare() {
        val screens = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterCareRequestScreens.kt")
        assertTrue(screens.contains("CanonicalFosterTransitRecovery.isHistoricalSelection"))
        assertTrue(screens.contains("tránsito finalizado"))
        assertTrue(screens.contains("Antecedente SELECTED"))
        val historical = application(
            status = "SELECTED",
            requestStatus = "COMPLETED",
            placementStatus = "CLOSED"
        )
        val active = application(
            status = "SELECTED",
            requestStatus = "ACTIVE",
            placementStatus = "OPEN"
        )
        assertTrue(CanonicalFosterTransitRecovery.isHistoricalSelection(historical))
        assertFalse(CanonicalFosterTransitRecovery.showsActiveTemporaryCare(historical))
        assertTrue(CanonicalFosterTransitRecovery.showsActiveTemporaryCare(active))
        assertFalse(CanonicalFosterTransitRecovery.isHistoricalSelection(active))
        val completed = CanonicalFosterTransitRecovery.completedRequestForPet(
            listOf(
                request("done", "pet-bruno", "COMPLETED"),
                request("other", "pet-other", "COMPLETED")
            ),
            "pet-bruno"
        )
        assertEquals("done", completed?.id)
        assertNull(
            CanonicalFosterTransitRecovery.requestForPet(
                listOf(request("done", "pet-bruno", "COMPLETED")),
                "pet-bruno"
            )
        )
    }

    @Test
    fun completionPayloadKeepsEndedAuthorizedAndSelectedHistory() {
        val completion = CanonicalFosterTransitDecoding.completion(
            Json.parseToJsonElement(
                """
                {
                  "canon_complete_foster_transit": {
                    "request_id": "8c293651-83f6-43bc-b677-f34bef9e021f",
                    "request_status": "COMPLETED",
                    "application_id": "b0537915-f73b-45b8-addd-9246bd9fd542",
                    "application_status": "SELECTED",
                    "placement_id": "07d69d92-7d42-492d-a78d-48d020e3de59",
                    "placement_status": "CLOSED",
                    "pet_id": "9d9a6e5b-8cc8-4bc8-aa0c-740a1725c830",
                    "foster_user_id": "55555555-5555-5555-5555-555555555555",
                    "temporary_holder_kind": "PERSON",
                    "temporary_holder_role": "AUTHORIZED",
                    "temporary_link_status": "ENDED",
                    "responsible_organization_id": "8a34399d-bc11-408e-9b67-b15aa187fa5b",
                    "vitacora_pet_id": "9d9a6e5b-8cc8-4bc8-aa0c-740a1725c830",
                    "idempotent": true
                  }
                }
                """.trimIndent()
            )
        )
        assertEquals("COMPLETED", completion.requestStatus)
        assertEquals("SELECTED", completion.applicationStatus)
        assertEquals("CLOSED", completion.placementStatus)
        assertEquals("AUTHORIZED", completion.temporaryHolderRole)
        assertEquals("ENDED", completion.temporaryLinkStatus)
        assertEquals("9d9a6e5b-8cc8-4bc8-aa0c-740a1725c830", completion.petId)
        assertEquals(completion.petId, completion.vitacoraPetId)
        assertTrue(completion.idempotent)
        assertFalse(completion.temporaryHolderRole == "OWNER")
        assertEquals(CanonicalFosterTransitCompletion::class, completion::class)
    }

    @Test
    fun fosterStaysOutOfFoundFanout() {
        val fanout = functionBody(
            migration("20260927180000_1099_responder_fanout_postgis.sql"),
            "_canon_fanout_lost_found_recipients"
        )
        assertTrue(fanout.contains("c.capability = 'RESCUER'"))
        assertFalse(fanout.contains("'FOSTER'"))
        val current = migration1103()
        assertFalse(current.contains("_canon_fanout_lost_found_recipients"))
        assertFalse(current.contains("lost_found_alert_recipients"))
        assertFalse(current.contains("'FOSTER'"))
    }

    @Test
    fun lostFoundReunificationVitaCoraInvariantStaysUntouched() {
        val confirm = functionBody(
            migration("20260920210000_1087_found_creates_pet_waves_match.sql"),
            "canon_confirm_found_owner_match"
        )
        val transit = migration1103()
        assertTrue(confirm.contains("update public.vitacora_moments set pet_id = v_lost_pet where pet_id = v_found_pet"))
        assertTrue(confirm.contains("lifecycle_status = 'ARCHIVED'"))
        assertTrue(confirm.contains("where id = v_found_pet"))
        assertTrue(confirm.contains("'pet_id', v_lost_pet"))
        assertTrue(confirm.contains("'archived_pet_id', v_found_pet"))
        assertTrue(confirm.contains("'survivor_pet_id', v_lost_pet"))
        assertFalse(transit.contains("canon_confirm_found_owner_match"))
        assertFalse(transit.contains("lost_found_alerts"))
        assertFalse(transit.contains("insert into public.vitacora_moments"))
        assertFalse(transit.contains("update public.vitacora_moments"))
    }

    @Test
    fun liveProbeEndsOnlyTheActiveFixtureAndStaysReadOnlyAfterCompletion() {
        val script = source("scripts/qa/probe-community-care-live-transit.py")
        assertTrue(script.contains("tobqbddfcyitwgbkthhy"))
        assertTrue(script.contains("wystsapjfpdtoprlmizz"))
        assertTrue(script.contains("REFUSING PROD"))
        assertTrue(script.contains("REFUSING service_role key"))
        assertTrue(script.contains("LEOVER_END_TRANSIT"))
        assertTrue(script.contains("canon_complete_foster_transit"))
        assertTrue(script.contains("ALREADY_COMPLETED"))
        assertTrue(script.contains("8c293651-83f6-43bc-b677-f34bef9e021f"))
        assertTrue(script.contains("b0537915-f73b-45b8-addd-9246bd9fd542"))
        assertTrue(script.contains("07d69d92-7d42-492d-a78d-48d020e3de59"))
        assertTrue(script.contains("READ_ONLY"))
        assertTrue(script.contains("\"END_TRANSIT\", \"NOT_RUN\""))
        assertFalse(script.contains("SUPABASE_SERVICE_ROLE_KEY"))
        assertFalse(script.contains("insert into"))
        assertFalse(script.contains("canon_end_"))
        assertFalse(script.contains("p_needs"))
        assertTrue(script.contains("REFUSING REQUEST CREATION"))
        assertTrue(script.contains("REFUSING APPLICATION CREATION"))
        assertTrue(script.contains("REFUSING PLACEMENT CREATION"))
    }

    private fun request(id: String, petId: String, status: String) = CanonicalFosterTransitRequest(
        id = id,
        petId = petId,
        status = status
    )

    private fun application(
        status: String,
        requestStatus: String,
        placementStatus: String
    ) = CanonicalFosterTransitApplication(
        id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        requestId = "8c293651-83f6-43bc-b677-f34bef9e021f",
        status = status,
        requestStatus = requestStatus,
        petId = "9d9a6e5b-8cc8-4bc8-aa0c-740a1725c830",
        placementStatus = placementStatus
    )

    private fun migration1103(): String =
        migration("20260927220000_1103_foster_transit_completion.sql")

    private fun migrations(): List<String> {
        val dir = sourceFile("infra/supabase-canonical/supabase/migrations")
        return dir.listFiles()?.map { it.name }?.sorted().orEmpty()
    }

    private fun migration(name: String): String =
        source("infra/supabase-canonical/supabase/migrations/$name")

    private fun branchAfter(body: String, marker: String, until: String = ";"): String {
        val start = body.indexOf(marker)
        check(start >= 0) { "MISSING:$marker" }
        val end = body.indexOf(until, start + marker.length)
        check(end > start) { "UNCLOSED:$marker" }
        return body.substring(start, end)
    }

    private fun functionBody(sql: String, name: String): String {
        val marker = "function public.$name"
        val start = sql.indexOf(marker)
        check(start >= 0) { "MISSING:$name" }
        val end = sql.indexOf("\$\$;", start)
        check(end > start) { "UNCLOSED:$name" }
        return sql.substring(start, end)
    }

    private fun source(relativePath: String): String =
        sourceFile(relativePath).readText().replace("\r\n", "\n")

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
