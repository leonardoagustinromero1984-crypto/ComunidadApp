package com.comunidapp.app.domain.foster

import com.comunidapp.app.data.repository.CanonicalFosterTransitDecoding
import com.comunidapp.app.data.repository.CanonicalFosterTransitRecovery
import com.comunidapp.app.data.repository.CanonicalFosterTransitRequest
import com.comunidapp.app.ui.UiRegressionGateTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live transit wiring for migration 1102.
 * The Bruno lifecycle itself runs in scripts/qa/probe-community-care-live-transit.py.
 */
class LeoVerCommunityCareTransitLive14bTest {

    @Test
    fun canonicalDataProviderUsesTheRpcTransitRepository() {
        val provider = source("app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt")
        val transit = provider.substring(
            provider.indexOf("val canonicalFosterTransitRepository"),
            provider.indexOf("val fosterRequestRepository")
        )
        assertTrue(transit.contains("useSupabase && !useLegacyRemoteModules"))
        assertTrue(transit.contains("RpcCanonicalFosterTransitRepository()"))
        assertTrue(transit.contains("UnavailableCanonicalFosterTransitRepository()"))
        val requests = provider.substring(
            provider.indexOf("val fosterRequestRepository"),
            provider.indexOf("val fosterPlacementRepository")
        )
        assertTrue(requests.contains("useSupabase -> CanonicalFosterRequestRejectedRepository()"))
        assertTrue(requests.contains("else -> MockFosterRequestRepository("))
        assertFalse(requests.contains("useSupabase -> MockFosterRequestRepository"))
        val repository = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalFosterTransitRepository.kt")
        assertTrue(repository.contains("class RpcCanonicalFosterTransitRepository"))
        assertTrue(repository.contains("class CanonicalFosterRequestRejectedRepository"))
        assertTrue(repository.contains("CANONICAL_TRANSIT_USES_RPC"))
        assertFalse(repository.contains("Result.success(emptyList())"))
    }

    @Test
    fun responsibleTransitRouteIsReachableFromPetCare() {
        val detail = source("app/src/main/java/com/comunidapp/app/ui/screens/pets/PetDetailScreen.kt")
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(detail.contains("Buscar hogar de tránsito"))
        assertTrue(detail.contains("onNavigateToFosterTransit"))
        assertTrue(graph.contains("onNavigateToFosterTransit = { id ->"))
        assertTrue(graph.contains("navController.navigate(NavRoutes.fosterCareRequest(id))"))
        assertTrue(graph.contains("RequestFosterForPetScreen"))
    }

    @Test
    fun fosterOpenRequestRouteIsReachableFromSumate() {
        val sumate = source("app/src/main/java/com/comunidapp/app/ui/screens/sumate/SumateScreen.kt")
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        val home = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterScreens.kt")
        assertTrue(sumate.contains("Solicitudes de tránsito"))
        assertTrue(sumate.contains("onOpenFosterRequests"))
        assertTrue(sumate.contains("Ofrecer hogar de tránsito"))
        assertTrue(graph.contains("onOpenFosterRequests = { navController.navigate(NavRoutes.FOSTER_OPEN_REQUESTS) }"))
        assertTrue(graph.contains("onOpenRequests = { navController.navigate(NavRoutes.FOSTER_OPEN_REQUESTS) }"))
        assertTrue(home.contains("LeoPrimaryButton(text = \"Ver solicitudes abiertas\", onClick = onOpenRequests)"))
        assertFalse(home.contains("LeoPrimaryButton(text = \"Ver solicitudes abiertas\", onClick = { })"))
        assertTrue(graph.contains("OpenFosterRequestsScreen"))
    }

    @Test
    fun requestCreateReloadsTheSameBackendId() {
        val screens = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterCareRequestScreens.kt")
        val repository = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalFosterTransitRepository.kt")
        assertTrue(screens.contains("repository.requestFosterForPet(petId, needs, notes)"))
        assertTrue(screens.contains("CanonicalFosterTransitRecovery.requestForPet"))
        assertTrue(screens.contains("recovered.id != id"))
        assertTrue(repository.contains("CanonicalBackend.RPC_REQUEST_FOSTER_FOR_PET"))
        assertFalse(screens.contains("UUID.randomUUID"))
        assertFalse(screens.contains("MockFosterRequestRepository"))
        val recovered = CanonicalFosterTransitRecovery.requestForPet(
            listOf(
                request("same-id", "pet-bruno", "REQUESTED"),
                request("old-id", "pet-bruno", "CANCELLED")
            ),
            "pet-bruno"
        )
        assertEquals("same-id", recovered?.id)
        assertEquals("REQUESTED", recovered?.status)
    }

    @Test
    fun fosterApplicationIsRecoveredFromTheBackendList() {
        val screens = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterCareRequestScreens.kt")
        val repository = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalFosterTransitRepository.kt")
        assertTrue(screens.contains("repository.listMyFosterApplications()"))
        assertTrue(screens.contains("Tus postulaciones"))
        assertTrue(screens.contains("it.requestId !in openIds"))
        assertTrue(screens.contains("CanonicalFosterTransitRecovery.applicationForRequest"))
        assertTrue(screens.contains("repository.applyToFosterRequest(row.id)"))
        assertTrue(repository.contains("CanonicalBackend.RPC_LIST_MY_FOSTER_APPLICATIONS"))
        assertTrue(repository.contains("CanonicalBackend.RPC_APPLY_TO_FOSTER_REQUEST"))
        val json = Json { ignoreUnknownKeys = true }
        val rows = CanonicalFosterTransitDecoding.applications(
            json.parseToJsonElement(
                """[{"id":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","request_id":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","status":"PENDING","pet_id":"9d9a6e5b-8cc8-4bc8-aa0c-740a1725c830"}]"""
            )
        )
        val recovered = CanonicalFosterTransitRecovery.applicationForRequest(
            rows,
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        )
        assertEquals("PENDING", recovered?.status)
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", recovered?.id)
    }

    @Test
    fun responsibleRecoversTheRequestAfterNavigationLoss() {
        val screens = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterCareRequestScreens.kt")
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(screens.contains("repository.listMyFosterRequests()"))
        assertTrue(screens.contains("LaunchedEffect(petId)"))
        assertTrue(screens.contains("onChooseApplicant(current.id)"))
        assertTrue(graph.contains("onChooseApplicant = { requestId ->"))
        assertTrue(graph.contains("navController.navigate(NavRoutes.fosterChooseApplicant(requestId))"))
        assertNull(
            CanonicalFosterTransitRecovery.requestForPet(
                listOf(request("gone", "other-pet", "REQUESTED")),
                "pet-bruno"
            )
        )
    }

    @Test
    fun activeTransitIsRecoveredFromTheBackend() {
        val screens = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterCareRequestScreens.kt")
        val repository = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalFosterTransitRepository.kt")
        assertTrue(screens.contains("repository.getActiveFosterTransit(petId)"))
        assertTrue(screens.contains("activeTransitLabel"))
        assertTrue(screens.contains("cuidado temporal autorizado"))
        assertFalse(screens.contains("current_custodian"))
        assertTrue(repository.contains("CanonicalBackend.RPC_GET_ACTIVE_FOSTER_TRANSIT"))
        val transit = CanonicalFosterTransitDecoding.transit(
            Json.parseToJsonElement(
                """
                {
                  "request_id": "11111111-1111-1111-1111-111111111111",
                  "request_status": "ACTIVE",
                  "selected_application_id": "33333333-3333-3333-3333-333333333333",
                  "application_status": "SELECTED",
                  "placement_id": "44444444-4444-4444-4444-444444444444",
                  "placement_status": "OPEN",
                  "pet_id": "9d9a6e5b-8cc8-4bc8-aa0c-740a1725c830",
                  "foster_user_id": "55555555-5555-5555-5555-555555555555",
                  "temporary_holder_kind": "PERSON",
                  "temporary_holder_role": "AUTHORIZED",
                  "temporary_link_status": "ACTIVE",
                  "responsible_role": "RESPONSIBLE"
                }
                """.trimIndent()
            )
        )
        assertEquals("AUTHORIZED", transit.temporaryHolderRole)
        assertEquals("ACTIVE", transit.requestStatus)
        assertEquals("OPEN", transit.placementStatus)
        assertFalse(transit.temporaryHolderRole == "OWNER")
        val id = CanonicalFosterTransitDecoding.id(
            JsonPrimitive("44444444-4444-4444-4444-444444444444")
        )
        assertEquals("44444444-4444-4444-4444-444444444444", id)
    }

    @Test
    fun selectionPreservesOrganizationResponsibleAndDoesNotMakeFosterOwner() {
        val select = functionBody(
            migration("20260921023000_1093_transit_adoption_invite.sql"),
            "canon_select_foster_applicant"
        )
        assertTrue(select.contains("set status = 'ACTIVE'"))
        assertTrue(select.contains("'PERSON'"))
        assertTrue(select.contains("'AUTHORIZED'"))
        assertFalse(select.contains("'OWNER'"))
        assertFalse(select.contains("current_custodian"))
        assertFalse(select.contains("pet_care_transfers"))
        val read = functionBody(
            migration("20260927210000_1102_foster_transit_read_contract.sql"),
            "canon_get_active_foster_transit"
        )
        assertTrue(read.contains("'temporary_holder_role', 'AUTHORIZED'"))
        assertTrue(read.contains("'responsible_role', 'RESPONSIBLE'"))
        assertTrue(read.contains("l.holder_kind = 'ORGANIZATION'"))
        assertTrue(read.contains("l.role = 'RESPONSIBLE'"))
        assertTrue(read.contains("l.status = 'ACTIVE'"))
        assertFalse(read.contains("insert into public.pet_responsibility_links"))
        assertTrue(migration("20260927210000_1102_foster_transit_read_contract.sql").contains("The foster never becomes OWNER"))
        assertTrue(migration("20260927210000_1102_foster_transit_read_contract.sql").contains("The organization RESPONSIBLE link stays ACTIVE"))
    }

    @Test
    fun samePetIdentityAndNoCareTransferInTheTransitContract() {
        val current = migration("20260927210000_1102_foster_transit_read_contract.sql")
        val select = functionBody(
            migration("20260921023000_1093_transit_adoption_invite.sql"),
            "canon_select_foster_applicant"
        )
        assertTrue(select.contains("v_req.pet_id"))
        assertTrue(select.contains("insert into public.foster_placements"))
        assertFalse(select.contains("insert into public.pets"))
        assertFalse(current.contains("insert into public.pets"))
        assertFalse(current.contains("canon_initiate_care_transfer"))
        assertFalse(current.contains("canon_accept_care_transfer"))
        assertFalse(current.contains("pet_care_transfers"))
        assertFalse(current.contains("_canon_complete_adoptions_for_pet"))
    }

    @Test
    fun fosterStaysOutOfFoundResponderFanout() {
        val fanout = functionBody(
            migration("20260927180000_1099_responder_fanout_postgis.sql"),
            "_canon_fanout_lost_found_recipients"
        )
        assertTrue(fanout.contains("c.capability = 'RESCUER'"))
        assertFalse(fanout.contains("'FOSTER'"))
        val current = migration("20260927210000_1102_foster_transit_read_contract.sql")
        assertFalse(current.contains("_canon_fanout_lost_found_recipients"))
        assertFalse(current.contains("lost_found_alert_recipients"))
    }

    @Test
    fun lostFoundReunificationVitaCoraInvariantStaysUntouched() {
        val confirm = functionBody(
            migration("20260920210000_1087_found_creates_pet_waves_match.sql"),
            "canon_confirm_found_owner_match"
        )
        val transit = migration("20260927210000_1102_foster_transit_read_contract.sql")
        assertTrue(confirm.contains("update public.vitacora_moments set pet_id = v_lost_pet where pet_id = v_found_pet"))
        assertTrue(confirm.contains("lifecycle_status = 'ARCHIVED'"))
        assertTrue(confirm.contains("where id = v_found_pet"))
        assertTrue(confirm.contains("'pet_id', v_lost_pet"))
        assertTrue(confirm.contains("'archived_pet_id', v_found_pet"))
        assertTrue(confirm.contains("'survivor_pet_id', v_lost_pet"))
        assertFalse(transit.contains("canon_confirm_found_owner_match"))
        assertFalse(transit.contains("lost_found_alerts"))
        assertFalse(transit.contains("insert into public.vitacora_moments"))
        assertTrue(transit.contains("TRANSIT_START_VITACORA_EVENT: MISSING"))
    }

    @Test
    fun liveProbeRefusesProdAndDoesNotEndTransit() {
        val script = source("scripts/qa/probe-community-care-live-transit.py")
        assertTrue(script.contains("tobqbddfcyitwgbkthhy"))
        assertTrue(script.contains("wystsapjfpdtoprlmizz"))
        assertTrue(script.contains("REFUSING PROD"))
        assertTrue(script.contains("9d9a6e5b-8cc8-4bc8-aa0c-740a1725c830"))
        assertTrue(script.contains("8a34399d-bc11-408e-9b67-b15aa187fa5b"))
        assertTrue(script.contains("qa07shelter"))
        assertTrue(script.contains("qa06foster"))
        assertTrue(script.contains("qa01owner"))
        assertTrue(script.contains("canon_request_foster_for_pet"))
        assertTrue(script.contains("canon_apply_to_foster_request"))
        assertTrue(script.contains("canon_select_foster_applicant"))
        assertTrue(script.contains("canon_list_my_foster_requests"))
        assertTrue(script.contains("canon_list_my_foster_applications"))
        assertTrue(script.contains("canon_get_active_foster_transit"))
        assertTrue(script.contains("READ_ONLY"))
        assertTrue(script.contains("\"END_TRANSIT\", \"NOT_RUN\""))
        assertTrue(script.contains("NOT_CREATED_BY_DESIGN_CURRENTLY"))
        assertTrue(script.contains("REFUSING service_role key"))
        assertFalse(script.contains("canon_end_"))
        assertFalse(script.contains("insert into"))
        assertFalse(script.contains("SUPABASE_SERVICE_ROLE_KEY"))
    }

    private fun request(id: String, petId: String, status: String) = CanonicalFosterTransitRequest(
        id = id,
        petId = petId,
        status = status
    )

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

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
