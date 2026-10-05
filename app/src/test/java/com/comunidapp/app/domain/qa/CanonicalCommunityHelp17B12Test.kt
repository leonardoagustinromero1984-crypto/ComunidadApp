package com.comunidapp.app.domain.qa

import com.comunidapp.app.data.model.M17InKindPledgeStatus
import com.comunidapp.app.data.model.M17InKindSearchFilter
import com.comunidapp.app.data.model.M17MockOrganizations
import com.comunidapp.app.data.model.M17VolunteerApplicationStatus
import com.comunidapp.app.data.model.M17VolunteerOpportunityStatus
import com.comunidapp.app.data.model.M17VolunteerOpportunityType
import com.comunidapp.app.data.model.M17VolunteerSearchFilter
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.m13.CanonSightingRecord
import com.comunidapp.app.data.remote.supabase.m17.CanonicalVolunteerList
import com.comunidapp.app.data.remote.supabase.m17.M17Exception
import com.comunidapp.app.data.remote.supabase.m17.toM17InKindPledgeFromRpc
import com.comunidapp.app.data.remote.supabase.m17.toM17PublicInKindNeed
import com.comunidapp.app.data.remote.supabase.m17.toM17PublicVolunteerOpportunity
import com.comunidapp.app.data.remote.supabase.m17.toM17VolunteerApplicationFromRpc
import com.comunidapp.app.data.repository.CreateM13SightingInput
import com.comunidapp.app.data.repository.M13Validators
import com.comunidapp.app.data.repository.M17ExtendedMemoryStore
import com.comunidapp.app.data.repository.MockM17InKindRepository
import com.comunidapp.app.data.repository.MockM17VolunteerRepository
import com.comunidapp.app.domain.lostfound.IncidentMoment
import com.comunidapp.app.domain.m17.CommunityHelpPresentation
import com.comunidapp.app.domain.m17.M17HelpBackend
import com.comunidapp.app.domain.m17.M17HelpRouting
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalCommunityHelp17B12Test {

    @Test
    fun volunteeringUsesOneIdFromListThroughOrganizationManagement() = runBlocking {
        val store = M17ExtendedMemoryStore()
        val visitor = MockM17VolunteerRepository(actorUserId = { "qa14" }, store = store)
        val all = visitor.searchPublicOpportunities(M17VolunteerSearchFilter()).getOrThrow()
        val onlyA = visitor.searchPublicOpportunities(
            M17VolunteerSearchFilter(organizationId = M17MockOrganizations.ORG_NORTE)
        ).getOrThrow()
        val onlyB = visitor.searchPublicOpportunities(
            M17VolunteerSearchFilter(organizationId = M17MockOrganizations.ORG_SUR)
        ).getOrThrow()
        assertTrue(onlyB.all { it.title.contains("Sur") })
        assertTrue(onlyA.none { it.title.contains("Sur") })
        assertTrue(all.any { it.title.contains("Sur") })
        assertTrue(all.size > onlyB.size)

        val listed = onlyB.single { it.title == "QA17B12 Paseos Sur" }
        val detail = visitor.getPublicOpportunity(listed.id).getOrThrow()
        assertEquals(listed.id, detail.id)
        val application = visitor.submitApplication(detail.id, "Puedo pasear").getOrThrow()
        assertEquals(detail.id, application.opportunityId)
        val manager = MockM17VolunteerRepository(
            actorUserId = { "qa07" },
            store = store,
            canManage = { it == M17MockOrganizations.ORG_SUR }
        )
        val applicants = manager.listApplicants(detail.id).getOrThrow()
        assertEquals(detail.id, applicants.single().opportunityId)
        assertEquals("qa14", applicants.single().userId)
        assertTrue(visitor.listApplicants(detail.id).isFailure)
    }

    @Test
    fun aMaterialNeedIsNotAPersonOfferAndAPledgeKeepsTheNeedId() = runBlocking {
        val store = M17ExtendedMemoryStore()
        val visitor = MockM17InKindRepository(actorUserId = { "qa14" }, store = store)
        val all = visitor.searchPublicNeeds(M17InKindSearchFilter()).getOrThrow()
        val onlyA = visitor.searchPublicNeeds(
            M17InKindSearchFilter(organizationId = M17MockOrganizations.ORG_NORTE)
        ).getOrThrow()
        val onlyB = visitor.searchPublicNeeds(
            M17InKindSearchFilter(organizationId = M17MockOrganizations.ORG_SUR)
        ).getOrThrow()
        assertTrue(onlyB.all { it.title.contains("Sur") })
        assertTrue(onlyA.none { it.title.contains("Sur") })
        assertTrue(all.any { it.title.contains("Sur") })
        val listed = onlyB.single { it.title == "QA17B12 Alimento Sur" }
        val detail = visitor.getPublicNeed(listed.id).getOrThrow()
        assertEquals(listed.id, detail.id)
        val pledge = visitor.createPledge(detail.id, 2, "Llevo bolsas").getOrThrow()
        assertEquals(detail.id, pledge.needId)
        val manager = MockM17InKindRepository(
            actorUserId = { "qa07" },
            store = store,
            canManage = { it == M17MockOrganizations.ORG_SUR }
        )
        val managed = manager.listPledges(detail.id).getOrThrow()
        assertTrue(managed.any { it.id == pledge.id && it.needId == detail.id })
        assertTrue(visitor.listPledges(detail.id).isFailure)
    }

    @Test
    fun canonicalPayloadsShareTheOpportunityAndNeedId() {
        val opportunity = buildJsonObject {
            put("id", "opp-a")
            put("title", "QA17B12 Paseos Norte")
            put("description", "convocatoria")
            put("organization_id", "org-a")
            put("organization_display_name", "Refugio A")
            put("opportunity_type", "ANIMAL_CARE")
            put("status", "PUBLISHED")
            put("slots_needed", 4)
            put("slots_filled", 1)
        }.toM17PublicVolunteerOpportunity()
        val application = buildJsonObject {
            put("id", "app-1")
            put("opportunity_id", opportunity.id)
            put("applicant_user_id", "qa14")
            put("status", "SUBMITTED")
        }.toM17VolunteerApplicationFromRpc("", "")
        assertEquals("opp-a", opportunity.id)
        assertEquals(opportunity.id, application.opportunityId)
        assertEquals("qa14", application.userId)

        val need = buildJsonObject {
            put("id", "need-a")
            put("title", "QA17B12 Alimento Norte")
            put("description", "necesidad")
            put("organization_display_name", "Refugio A")
            put("category", "FOOD")
            put("status", "PUBLISHED")
            put("quantity_requested", 10)
            put("quantity_pledged", 2)
            put("quantity_delivered", 0)
            put("quantity_unit", "unidades")
            put("coverage_percent", 20)
        }.toM17PublicInKindNeed()
        val pledge = buildJsonObject {
            put("id", "pledge-1")
            put("need_id", need.id)
            put("pledged_by", "qa14")
            put("quantity", 2)
            put("status", "PLEDGED")
        }.toM17InKindPledgeFromRpc("", "")
        assertEquals(need.id, pledge.needId)
        assertEquals("qa14", pledge.userId)
    }

    @Test
    fun observedAtIsATimestampOnTheCaseAndDoesNotHideInsideTheComment() {
        val moment = IncidentMoment.combine("2026-04-11", "09:30", 0L)
        val input = CreateM13SightingInput(
            lostFoundCaseId = "case-1",
            species = PetSpecies.CAT,
            primaryColor = "negro",
            observedAt = moment,
            zoneText = "Belgrano",
            description = "Lo vi en la esquina",
            latitudeApprox = -34.56,
            longitudeApprox = -58.45,
            mediaRefs = listOf("m05://qa17b12-foto")
        )
        val params = CanonSightingRecord.createParams(input)
        val observed = params.string("p_observed_at")
        assertEquals(Instant.ofEpochMilli(moment).toString(), observed)
        assertEquals("case-1", params.string("p_alert_id"))
        assertEquals("Lo vi en la esquina", params.string("p_note"))
        assertEquals("Belgrano", params.string("p_zone_text"))
        assertEquals("m05://qa17b12-foto", params.string("p_media_ref"))
        assertFalse(params.string("p_note").contains("2026-04-11"))

        val stored = CanonSightingRecord.read(
            buildJsonObject {
                put("id", "sight-1")
                put("alert_id", "case-1")
                put("lost_found_case_id", "case-1")
                put("reporter_user_id", "qa02")
                put("observed_at", observed)
                put("note", "Lo vi en la esquina")
                put("description", "Lo vi en la esquina")
                put("zone_text", "Belgrano")
                put("species", "CAT")
                put("primary_color", "negro")
                put("media_ref", "m05://qa17b12-foto")
                put("latitude", -34.56)
                put("longitude", -58.45)
                put("created_at", "2026-04-11T12:00:00Z")
                put("status", "ACTIVE")
            }
        )
        assertEquals(moment, stored.observedAt)
        assertEquals("case-1", stored.lostFoundCaseId)
        assertEquals("Lo vi en la esquina", stored.description)
        assertEquals("Belgrano", stored.zoneText)
        assertEquals(listOf("m05://qa17b12-foto"), stored.mediaRefs)
        assertEquals(-34.56, stored.latitudeApprox ?: 0.0, 0.0001)
        assertEquals(-58.45, stored.longitudeApprox ?: 0.0, 0.0001)
        assertEquals(PetSpecies.CAT, stored.species)
    }

    @Test
    fun canonicalSchemaDoesNotReuseEventsOrPersonOffers() {
        val volunteer = text("infra/supabase-canonical/supabase/migrations/20261005120000_1112_volunteer_opportunities.sql")
        val needs = text("infra/supabase-canonical/supabase/migrations/20261005130000_1113_in_kind_needs.sql")
        val sighting = text("infra/supabase-canonical/supabase/migrations/20261005140000_1114_lost_found_sighting_observed_at.sql")
        val client = text("app/src/main/java/com/comunidapp/app/data/remote/supabase/m17/CanonicalM17RemoteDataSource.kt")
        val legacy = text("app/src/main/java/com/comunidapp/app/data/remote/supabase/m17/SupabaseM17ExtendedRemoteDataSource.kt")
        val sightingClient = text("app/src/main/java/com/comunidapp/app/data/repository/CanonicalM13SightingRepository.kt")
        val names = text("app/src/main/java/com/comunidapp/app/domain/canonical/CanonicalBackend.kt")
        val provider = text("app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt")
        val fixture = text("infra/supabase-canonical/qa/seed_17b12_staging.sql")
        val runner = text("scripts/qa/seed-17b12-staging.ps1")

        assertTrue(volunteer.contains("volunteer_opportunities"))
        assertTrue(volunteer.contains("volunteer_applications"))
        assertTrue(volunteer.contains("p_organization_id is null or o.organization_id = p_organization_id"))
        assertTrue(volunteer.contains("canon_apply_volunteer_opportunity"))
        assertTrue(volunteer.contains("canon_list_volunteer_applicants"))
        assertTrue(volunteer.contains("security definer"))
        assertTrue(volunteer.contains("set search_path = public"))
        assertFalse(volunteer.contains("from public.community_events"))
        assertFalse(volunteer.contains("event_registrations"))
        assertFalse(volunteer.contains("'EVENTS'"))

        assertTrue(needs.contains("in_kind_needs"))
        assertTrue(needs.contains("in_kind_pledges"))
        assertTrue(needs.contains("p_organization_id is null or n.organization_id = p_organization_id"))
        assertTrue(needs.contains("canon_pledge_in_kind_need"))
        assertTrue(needs.contains("'need_id', p_need_id"))
        assertFalse(needs.contains("from public.in_kind_offers"))
        assertFalse(needs.contains("m17_create_in_kind_pledge"))

        assertTrue(sighting.contains("observed_at timestamptz"))
        assertTrue(sighting.contains("p_observed_at timestamptz"))
        assertTrue(sighting.contains("canon_contribute_lost_found_info"))
        assertTrue(sighting.contains("alert_id"))
        assertTrue(sighting.contains("media_ref"))
        assertFalse(sighting.contains("m13_create_sighting"))

        assertTrue(client.contains("RPC_LIST_VOLUNTEER_OPPORTUNITIES"))
        assertTrue(client.contains("RPC_LIST_IN_KIND_NEEDS"))
        assertTrue(client.contains("RPC_APPLY_VOLUNTEER_OPPORTUNITY"))
        assertTrue(client.contains("RPC_PLEDGE_IN_KIND_NEED"))
        assertTrue(names.contains("canon_list_volunteer_opportunities"))
        assertTrue(names.contains("canon_list_in_kind_needs"))
        assertTrue(names.contains("canon_contribute_lost_found_info"))
        assertFalse(client.contains("RPC_LIST_EVENTS"))
        assertFalse(client.contains("in_kind_offers"))
        assertFalse(client.contains("community_events"))
        assertFalse(client.contains("\"EVENTS\""))
        assertFalse(client.contains("m17_"))
        assertTrue(legacy.contains("m17_list_public_volunteer_opportunities"))
        assertTrue(legacy.contains("m17_create_in_kind_pledge"))
        assertFalse(legacy.contains("canon_list_volunteer_opportunities"))
        assertFalse(legacy.contains("canon_pledge_in_kind_need"))

        assertTrue(sightingClient.contains("CanonSightingRecord"))
        assertFalse(sightingClient.contains("m13_create_sighting"))
        assertTrue(provider.contains("CanonicalM13SightingRepository"))

        assertTrue(fixture.contains("tobqbddfcyitwgbkthhy"))
        assertTrue(fixture.contains("QA17B12_ABORT_NOT_STAGING"))
        assertTrue(fixture.contains("insert into public.volunteer_opportunities"))
        assertTrue(fixture.contains("insert into public.in_kind_needs"))
        assertTrue(fixture.contains("timestamptz '2026-04-11 09:30:00+00'"))
        assertFalse(fixture.contains("insert into public.community_events"))
        assertFalse(fixture.contains("insert into public.in_kind_offers"))
        assertTrue(runner.contains("tobqbddfcyitwgbkthhy"))
        assertTrue(runner.contains("if (-not \$Apply)"))
        assertTrue(runner.contains("ExpectedRef = \"tobqbddfcyitwgbkthhy\""))
    }

    @Test
    fun oneSlotAcceptsTheFirstApplicantAndRejectsTheSecond() = runBlocking {
        val store = M17ExtendedMemoryStore()
        store.seedDefaults()
        val target = store.opportunities.value.first { it.title == "Sin postulantes" }
        store.updateOpportunity(target.copy(slotsNeeded = 1, slotsFilled = 0))
        val visitorA = MockM17VolunteerRepository(actorUserId = { "qa14" }, store = store)
        val visitorB = MockM17VolunteerRepository(actorUserId = { "qa02" }, store = store)
        val manager = MockM17VolunteerRepository(
            actorUserId = { "qa07" },
            store = store,
            canManage = { it == M17MockOrganizations.ORG_NORTE }
        )
        val first = visitorA.submitApplication(target.id, "Primera").getOrThrow()
        val second = visitorB.submitApplication(target.id, "Segunda").getOrThrow()
        val accepted = manager.acceptApplication(first.id).getOrThrow()
        assertEquals(M17VolunteerApplicationStatus.ACCEPTED, accepted.status)
        val rejected = manager.acceptApplication(second.id)
        assertEquals("SLOTS_FULL", (rejected.exceptionOrNull() as M17Exception).code)
        val stillSubmitted = manager.listApplicants(target.id).getOrThrow()
            .single { it.id == second.id }
        assertEquals(M17VolunteerApplicationStatus.SUBMITTED, stillSubmitted.status)
        assertEquals(1, manager.listApplicants(target.id).getOrThrow().count {
            it.status == M17VolunteerApplicationStatus.ACCEPTED
        })
        val refreshed = manager.getPublicOpportunity(target.id).getOrThrow()
        assertEquals(1, refreshed.slotsFilled)
        assertTrue(manager.canManageOpportunity(target.id))
        assertFalse(visitorA.canManageOpportunity(target.id))
    }

    @Test
    fun shelterADoesNotManageShelterBAndClosedRejectsAccept() = runBlocking {
        val store = M17ExtendedMemoryStore()
        store.seedDefaults()
        val south = store.opportunities.value.first { it.title == "QA17B12 Paseos Sur" }
        val north = store.opportunities.value.first { it.organizationId == M17MockOrganizations.ORG_NORTE }
        val managerA = MockM17VolunteerRepository(
            actorUserId = { "qa07" },
            store = store,
            canManage = { it == M17MockOrganizations.ORG_NORTE }
        )
        val visitor = MockM17VolunteerRepository(actorUserId = { "qa14" }, store = store)
        val application = visitor.submitApplication(south.id, "Quiero ayudar").getOrThrow()
        assertTrue(managerA.listApplicants(south.id).isFailure)
        assertTrue(managerA.acceptApplication(application.id).isFailure)
        assertTrue(managerA.canManageOpportunity(north.id))
        assertFalse(managerA.canManageOpportunity(south.id))
        store.updateOpportunity(north.copy(status = M17VolunteerOpportunityStatus.CLOSED))
        val pending = visitor.submitApplication(north.id, "Tarde")
        assertTrue(pending.isFailure)
        val closed = managerA.getPublicOpportunity(north.id).getOrThrow()
        assertEquals(M17VolunteerOpportunityStatus.CLOSED, closed.status)
        assertEquals("Cerrada", CommunityHelpPresentation.opportunityStatus(closed.status))
    }

    @Test
    fun closedMapsToClosedAndSearchKeepsOrganizationQueryAndType() = runBlocking {
        val mapped = buildJsonObject {
            put("id", "opp-closed")
            put("title", "Cerrada")
            put("description", "ya no recibe postulaciones")
            put("organization_id", "org-a")
            put("organization_display_name", "Refugio A")
            put("opportunity_type", "TRANSPORT")
            put("status", "CLOSED")
            put("slots_needed", 1)
            put("slots_filled", 1)
            put("can_manage", true)
        }.toM17PublicVolunteerOpportunity()
        assertEquals(M17VolunteerOpportunityStatus.CLOSED, mapped.status)
        assertTrue(mapped.canManage)
        assertEquals("Cerrada", CommunityHelpPresentation.opportunityStatus(mapped.status))

        val params = CanonicalVolunteerList.params("org-b", "paseos", "ANIMAL_CARE")
        assertEquals("org-b", params.string("p_organization_id"))
        assertEquals("paseos", params.string("p_query"))
        assertEquals("ANIMAL_CARE", params.string("p_type"))

        val store = M17ExtendedMemoryStore()
        val visitor = MockM17VolunteerRepository(actorUserId = { "qa14" }, store = store)
        val found = visitor.searchPublicOpportunities(
            M17VolunteerSearchFilter(
                query = "Paseos",
                type = M17VolunteerOpportunityType.ANIMAL_CARE,
                organizationId = M17MockOrganizations.ORG_SUR
            )
        ).getOrThrow()
        assertEquals(listOf("QA17B12 Paseos Sur"), found.map { it.title })
        val otherType = visitor.searchPublicOpportunities(
            M17VolunteerSearchFilter(
                query = "Paseos",
                type = M17VolunteerOpportunityType.TRANSPORT,
                organizationId = M17MockOrganizations.ORG_SUR
            )
        ).getOrThrow()
        assertTrue(otherType.isEmpty())
    }

    @Test
    fun canonicalStagingDoesNotSelectTheLegacyHelpRemote() {
        assertEquals(
            M17HelpBackend.CANONICAL,
            M17HelpRouting.select(useSupabase = true, legacyRemoteModules = false)
        )
        assertEquals(
            M17HelpBackend.LEGACY_M17,
            M17HelpRouting.select(useSupabase = true, legacyRemoteModules = true)
        )
        assertEquals(
            M17HelpBackend.LOCAL,
            M17HelpRouting.select(useSupabase = false, legacyRemoteModules = false)
        )
        val provider = text("app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt")
        val canon = text("app/src/main/java/com/comunidapp/app/data/remote/supabase/m17/CanonicalM17RemoteDataSource.kt")
        assertTrue(provider.contains("M17HelpBackend.CANONICAL ->"))
        assertTrue(provider.contains("CanonicalM17RemoteDataSource()"))
        assertFalse(canon.contains("m17_"))
        assertTrue(canon.contains("canon_list_volunteer_opportunities") || canon.contains("RPC_LIST_VOLUNTEER_OPPORTUNITIES"))
        assertTrue(canon.contains("RPC_ACCEPT_VOLUNTEER_APPLICATION"))
        assertTrue(canon.contains("RPC_LIST_IN_KIND_PLEDGES"))
        assertTrue(canon.contains("RPC_MARK_IN_KIND_PLEDGE_DELIVERED"))
    }

    @Test
    fun repeatedPledgeUpdatesQuantityAndOverOfferStaysVisibleAtOneHundred() = runBlocking {
        val store = M17ExtendedMemoryStore()
        store.seedDefaults()
        val need = store.needs.value.first { it.title == "QA17B12 Alimento Sur" }
        val visitor = MockM17InKindRepository(actorUserId = { "qa14" }, store = store)
        val manager = MockM17InKindRepository(
            actorUserId = { "qa07" },
            store = store,
            canManage = { it == M17MockOrganizations.ORG_SUR }
        )
        val otherOrg = MockM17InKindRepository(
            actorUserId = { "qa16" },
            store = store,
            canManage = { it == M17MockOrganizations.ORG_NORTE }
        )
        val first = visitor.createPledge(need.id, 2, "dos").getOrThrow()
        val second = visitor.createPledge(need.id, 12, "doce").getOrThrow()
        assertEquals(first.id, second.id)
        assertEquals(12, second.quantity)
        assertTrue(need.quantityRequested < 12)
        val listed = manager.listPledges(need.id).getOrThrow().single { it.id == first.id }
        assertEquals(12, listed.quantity)
        assertTrue(otherOrg.listPledges(need.id).isFailure)
        assertEquals("FORBIDDEN", (visitor.markDelivered(first.id).exceptionOrNull() as M17Exception).code)
        val delivered = manager.markDelivered(first.id).getOrThrow()
        assertEquals(M17InKindPledgeStatus.DELIVERED, delivered.status)
        val again = visitor.createPledge(need.id, 3, "otra vez")
        assertEquals("PLEDGE_ALREADY_DELIVERED", (again.exceptionOrNull() as M17Exception).code)
        val publicNeed = visitor.getPublicNeed(need.id).getOrThrow()
        assertTrue(publicNeed.coveragePercent <= 100)
        assertTrue(manager.canManageNeed(need.id))
        assertFalse(visitor.canManageNeed(need.id))
        val sql = text("infra/supabase-canonical/supabase/migrations/20261005130000_1113_in_kind_needs.sql")
        assertTrue(sql.contains("Over-offer is allowed"))
        assertTrue(sql.contains("least(100,"))
        assertFalse(sql.contains("raise exception 'NEED_FULFILLED'"))
    }

    @Test
    fun volunteerAcceptLocksTheOpportunityAndSearchIsCanonical() {
        val sql = text("infra/supabase-canonical/supabase/migrations/20261005120000_1112_volunteer_opportunities.sql")
        val lock = sql.indexOf("for update")
        val slotsFull = sql.indexOf("SLOTS_FULL")
        val acceptedCount = sql.indexOf("select count(*)::int into v_accepted")
        assertTrue(lock in 0 until slotsFull)
        assertTrue(acceptedCount in lock until slotsFull)
        assertTrue(sql.contains("canon_list_volunteer_opportunities(uuid, text, text)"))
        assertTrue(sql.contains("p_query"))
        assertTrue(sql.contains("p_type"))
        assertTrue(sql.contains("OPPORTUNITY_CLOSED"))
    }

    @Test
    fun sightingRoundTripRejectsAFutureDateAndKeepsALongMediaRef() {
        val now = 1_700_000_000_000L
        val historical = M13Validators.validateCreate(
            description = "Lo vi en la esquina",
            zoneText = "Belgrano",
            primaryColor = "negro",
            mediaRefs = listOf("m05://" + "a".repeat(300)),
            latitudeApprox = -34.56,
            longitudeApprox = -58.45,
            accuracyMeters = 20.0,
            observedAt = now - 86_400_000L,
            nowEpochMs = now
        )
        assertEquals(null, historical)
        val nearNow = M13Validators.validateCreate(
            description = "Lo vi en la esquina",
            zoneText = "Belgrano",
            primaryColor = "negro",
            mediaRefs = emptyList(),
            latitudeApprox = null,
            longitudeApprox = null,
            accuracyMeters = null,
            observedAt = now + 60_000L,
            nowEpochMs = now
        )
        assertEquals(null, nearNow)
        val future = M13Validators.validateCreate(
            description = "Lo vi en la esquina",
            zoneText = "Belgrano",
            primaryColor = "negro",
            mediaRefs = emptyList(),
            latitudeApprox = null,
            longitudeApprox = null,
            accuracyMeters = null,
            observedAt = now + M13Validators.FUTURE_TOLERANCE_MS + 60_000L,
            nowEpochMs = now
        )
        assertEquals("OBSERVED_AT_IN_FUTURE", future)
        val sql = text("infra/supabase-canonical/supabase/migrations/20261005140000_1114_lost_found_sighting_observed_at.sql")
        assertTrue(sql.contains("extensions.ST_Y"))
        assertTrue(sql.contains("extensions.ST_X"))
        assertTrue(sql.contains("OBSERVED_AT_IN_FUTURE"))
        assertTrue(sql.contains("interval '5 minutes'"))
        assertTrue(sql.contains("media_ref text"))
        assertTrue(sql.contains("drop constraint if exists lost_found_sightings_media_ref_len"))
        assertFalse(sql.contains("char_length(media_ref)"))
        val fixture = text("infra/supabase-canonical/qa/seed_17b12_staging.sql")
        assertTrue(fixture.contains("persons.show_location"))
        assertTrue(fixture.contains("persons.phone_public"))
        assertTrue(fixture.contains("QA17B12_ABORT_COLUMN_MISSING:persons.show_location"))
    }

    private fun JsonObject.string(key: String): String = this[key]!!.jsonPrimitive.content

    private fun text(relative: String): String {
        val file = listOf(File(relative), File("..", relative)).firstOrNull { it.isFile }
            ?: error("No se encontró $relative")
        return file.readText()
    }
}
