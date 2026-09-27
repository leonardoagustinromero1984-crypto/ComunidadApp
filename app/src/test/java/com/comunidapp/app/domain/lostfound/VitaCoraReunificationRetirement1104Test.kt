package com.comunidapp.app.domain.lostfound

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Migration 1104 contract. The migration is not applied here.
 * A retired provisional VitaCora keeps its historical number and redirects
 * reads to the survivor. Writes against it are rejected.
 */
class VitaCoraReunificationRetirement1104Test {

    private val migrationName = "20260927230000_1104_vitacora_reunification_retirement.sql"

    @Test
    fun addsRetirementColumnsAndPairConstraint() {
        val sql = migration1104()
        assertTrue(sql.contains("add column if not exists retired_at timestamptz null"))
        assertTrue(sql.contains("add column if not exists successor_pet_id uuid null"))
        assertTrue(sql.contains("vitacora_profiles_successor_pet_fk"))
        assertTrue(sql.contains("references public.pets(id)"))
        assertTrue(sql.contains("vitacora_profiles_retirement_pair_chk"))
        assertTrue(sql.contains("(retired_at is null and successor_pet_id is null)"))
        assertTrue(sql.contains("retired_at is not null"))
        assertTrue(sql.contains("successor_pet_id is not null"))
        assertTrue(sql.contains("successor_pet_id <> pet_id"))
        assertTrue(sql.contains("public_vitacora_number"))
        assertFalse(sql.contains("drop column if exists public_vitacora_number"))
        assertFalse(sql.contains("drop column public_vitacora_number"))
    }

    @Test
    fun confirmRetiresProvisionalProfileAfterArchive() {
        val confirm = functionBody(migration1104(), "canon_confirm_found_owner_match")
        val archive = confirm.indexOf("lifecycle_status = 'ARCHIVED'")
        val retire = confirm.indexOf("_canon_retire_reunified_vitacora(v_found_pet, v_lost_pet, auth.uid())")
        assertTrue(archive >= 0)
        assertTrue(retire > archive)
        val helper = functionBody(migration1104(), "_canon_retire_reunified_vitacora")
        assertTrue(helper.contains("set retired_at = coalesce(retired_at, timezone('utc', now()))"))
        assertTrue(helper.contains("successor_pet_id = p_survivor_pet_id"))
        assertTrue(helper.contains("where pet_id = p_archived_pet_id"))
        assertFalse(helper.contains("where pet_id = p_survivor_pet_id"))
    }

    @Test
    fun confirmEndsTemporaryAuthorizedResponsibility() {
        val helper = functionBody(migration1104(), "_canon_retire_reunified_vitacora")
        assertTrue(helper.contains("l.holder_kind = 'PERSON'"))
        assertTrue(helper.contains("l.role = 'AUTHORIZED'"))
        assertTrue(helper.contains("l.status = 'ACTIVE'"))
        assertTrue(helper.contains("ev.event_type = 'FOUND_CASE_CARE'"))
        assertTrue(helper.contains("set status = 'ENDED'"))
        assertTrue(helper.contains("valid_until = timezone('utc', now())"))
        assertTrue(helper.contains("'ENDED'"))
        assertFalse(helper.contains("insert into public.pet_responsibility_links"))
        assertFalse(helper.contains("'OWNER'"))
        assertFalse(helper.contains("delete from public.pet_responsibility_events"))
        assertFalse(helper.contains("delete from public.pet_lifecycle_events"))
    }

    @Test
    fun confirmRevokesProvisionalGrants() {
        val helper = functionBody(migration1104(), "_canon_retire_reunified_vitacora")
        assertTrue(helper.contains("update public.pet_permission_grants g"))
        assertTrue(helper.contains("set revoked_at = timezone('utc', now())"))
        assertTrue(helper.contains("g.revoked_at is null"))
        assertTrue(helper.contains("update public.vitacora_access_grants"))
        assertTrue(helper.contains("where pet_id = p_archived_pet_id"))
        assertFalse(helper.contains("delete from public.pet_permission_grants"))
        assertFalse(helper.contains("delete from public.vitacora_access_grants"))
        assertFalse(helper.contains("delete from public.security_audit_events"))
    }

    @Test
    fun retiredProfileDoesNotSynthesizeCareCreated() {
        val list = functionBody(migration1104(), "canon_list_vitacora_moments")
        val guard = list.indexOf("perform public._canon_assert_vitacora_not_retired(p_pet_id)")
        val care = list.indexOf("'CARE_CREATED'")
        val forbidden = list.indexOf("raise exception 'FORBIDDEN'")
        assertTrue(forbidden >= 0)
        assertTrue(guard > forbidden)
        assertTrue(care > guard)
        assertTrue(list.contains("public._acl_is_admin(auth.uid())"))
        val assertFn = functionBody(migration1104(), "_canon_assert_vitacora_not_retired")
        assertTrue(assertFn.contains("v.retired_at is not null"))
        assertTrue(assertFn.contains("raise exception 'VITACORA_RETIRED'"))
        assertFalse(assertFn.contains("lifecycle_status"))
    }

    @Test
    fun retiredProfileRejectsNewMoment() {
        assertRejectsBeforeWrite(
            "canon_create_moment",
            "insert into public.vitacora_moments"
        )
    }

    @Test
    fun retiredProfileRejectsSocialMoment() {
        val body = functionBody(migration1104(), "canon_save_social_vitacora_moment")
        val guard = body.indexOf("perform public._canon_assert_vitacora_not_retired(p_pet_id)")
        val write = body.indexOf("update public.vitacora_moments")
        assertTrue(guard >= 0)
        assertTrue(write > guard)
        assertFalse(body.contains("successor_pet_id"))
    }

    @Test
    fun retiredProfileRejectsGrantCreation() {
        assertRejectsBeforeWrite(
            "canon_grant_vitacora",
            "insert into public.vitacora_access_grants"
        )
    }

    @Test
    fun retiredProfileRejectsProposalCreation() {
        assertRejectsBeforeWrite(
            "canon_create_proposal",
            "insert into public.vitacora_update_proposals"
        )
        assertRejectsBeforeWrite(
            "canon_decide_proposal",
            "update public.vitacora_update_proposals"
        )
        assertRejectsBeforeWrite(
            "canon_revoke_vitacora",
            "update public.vitacora_access_grants"
        )
        assertRejectsBeforeWrite(
            "canon_hide_integration",
            "update public.vitacora_integration_links"
        )
    }

    @Test
    fun oldVitacoraNumberResolvesToSuccessor() {
        val search = functionBody(migration1104(), "canon_search_vitacora_number")
        assertTrue(search.contains("if v_profile.retired_at is null then"))
        assertTrue(search.contains("'pet_id', p.id"))
        assertTrue(search.contains("'name', p.name"))
        assertTrue(search.contains("'public_vitacora_number', v.public_vitacora_number"))
        assertTrue(search.contains("'needs_photo', p.avatar_asset_id is null"))
        assertTrue(search.contains("public._acl_pet_holder(auth.uid(), p.id) or public._acl_is_staff(auth.uid())"))
        assertTrue(search.contains("where p.id = v_profile.successor_pet_id"))
        assertTrue(search.contains("and v.retired_at is null"))
        assertFalse(search.contains("raise exception 'VITACORA_RETIRED'"))
        assertFalse(search.contains("insert into public.vitacora_profiles"))
        val retiredBranch = search.substring(search.indexOf("if v_profile.retired_at is null then"))
        assertTrue(retiredBranch.contains("v_profile.successor_pet_id"))
        assertFalse(retiredBranch.contains("where v.pet_id = v_profile.pet_id\n        and v.retired_at is not null"))
    }

    @Test
    fun publicArchivedPetRemainsNonPublic() {
        val sql = migration1104()
        assertFalse(sql.contains("function public.canon_public_pet"))
        assertFalse(sql.contains("function public.canon_public_lost_found"))
        val pet = functionBody(
            migration("20260815172000_1020_rls_rpc.sql"),
            "canon_public_pet"
        )
        val alert = functionBody(
            migration("20260815172000_1020_rls_rpc.sql"),
            "canon_public_lost_found"
        )
        assertTrue(pet.contains("p.lifecycle_status = 'ACTIVE'"))
        assertTrue(alert.contains("a.status = 'OPEN'"))
    }

    @Test
    fun hallazgoStoredMomentsRemainOnSurvivor() {
        val confirm = functionBody(migration1104(), "canon_confirm_found_owner_match")
        assertTrue(
            confirm.contains(
                "update public.vitacora_moments set pet_id = v_lost_pet where pet_id = v_found_pet"
            )
        )
        assertFalse(confirm.contains("delete from public.vitacora_moments"))
        val helper = functionBody(migration1104(), "_canon_retire_reunified_vitacora")
        assertFalse(helper.contains("vitacora_moments"))
    }

    @Test
    fun survivorVitaCoraRemainsUsable() {
        val moment = functionBody(migration1104(), "canon_create_moment")
        assertTrue(moment.contains("insert into public.vitacora_moments"))
        assertTrue(moment.contains("'vitacora.manage'"))
        val assertFn = functionBody(migration1104(), "_canon_assert_vitacora_not_retired")
        assertTrue(assertFn.contains("v.retired_at is not null"))
        assertFalse(assertFn.contains("lifecycle_status"))
        val helper = functionBody(migration1104(), "_canon_retire_reunified_vitacora")
        assertFalse(helper.contains("update public.pets"))
        assertTrue(helper.contains("p.origin_kind = 'FOUND_CASE'"))
        assertTrue(helper.contains("p.lifecycle_status = 'ARCHIVED'"))
    }

    @Test
    fun backfillIsIdempotentAndSkipsMalformedRows() {
        val sql = migration1104()
        val helper = functionBody(sql, "_canon_retire_reunified_vitacora")
        assertTrue(sql.contains("e.action = 'lost_found.reunify'"))
        assertTrue(sql.contains("archived_pet_id"))
        assertTrue(sql.contains("survivor_pet_id"))
        assertTrue(sql.contains("order by e.occurred_at asc, e.id asc"))
        assertTrue(sql.contains("when invalid_text_representation then"))
        assertTrue(sql.contains("continue"))
        assertTrue(helper.contains("or successor_pet_id = p_survivor_pet_id"))
        assertTrue(helper.contains("v.successor_pet_id is distinct from p_survivor_pet_id"))
        assertTrue(helper.contains("coalesce(retired_at, timezone('utc', now()))"))
        assertTrue(helper.contains("and status = 'ACTIVE'"))
        assertFalse(sql.contains("delete from public.security_audit_events"))
        assertFalse(sql.contains("where pet_id = '5f487c6f-f7b5-4b68-9419-d018b89195f8'"))
        assertFalse(sql.contains("where pet_id = 'f58305a1-0b83-40ed-bbc3-fdcdc0120fb5'"))
        assertTrue(sql.contains("f58305a1-0b83-40ed-bbc3-fdcdc0120fb5"))
        assertTrue(sql.contains("5f487c6f-f7b5-4b68-9419-d018b89195f8"))
    }

    @Test
    fun regLf006IsOneCanonicalUsableVitaCora() {
        val sql = migration1104()
        assertTrue(sql.contains("MUST_REJECT_RETIRED"))
        assertTrue(sql.contains("MUST_REDIRECT_TO_SUCCESSOR"))
        assertTrue(sql.contains("ADMIN_HISTORICAL_ONLY"))
        assertTrue(sql.contains("SAFE_UNCHANGED"))
        assertTrue(sql.contains("historical identity"))
        val confirm = functionBody(sql, "canon_confirm_found_owner_match")
        assertTrue(confirm.contains("'pet_id', v_lost_pet"))
        assertFalse(confirm.contains("insert into public.vitacora_profiles"))
    }

    @Test
    fun migrations1099Through1103StayUnaffected() {
        val names = listOf(
            "20260927180000_1099_responder_fanout_postgis.sql",
            "20260927190000_1100_adoption_canonical_contract.sql",
            "20260927200000_1101_care_transfer_accept_authorization.sql",
            "20260927210000_1102_foster_transit_read_contract.sql",
            "20260927220000_1103_foster_transit_completion.sql"
        )
        val sql = migration1104()
        assertFalse(sql.contains("function public.canon_complete_foster_transit"))
        assertFalse(sql.contains("function public.canon_accept_care_transfer"))
        assertFalse(sql.contains("function public.canon_apply_adoption"))
        for (name in names) {
            val earlier = migration(name)
            assertFalse(name, earlier.contains("vitacora_profiles_retirement_pair_chk"))
            assertFalse(name, earlier.contains("_canon_retire_reunified_vitacora"))
            assertFalse(name, earlier.contains("canon_confirm_found_owner_match"))
        }
    }

    @Test
    fun directTableSelectStaysRevoked() {
        val sql = migration1104()
        assertTrue(
            sql.contains(
                "revoke all on table public.vitacora_profiles from public, anon, authenticated"
            )
        )
        assertFalse(sql.contains("grant select"))
        assertFalse(sql.contains("grant all on table public.vitacora_profiles"))
        assertFalse(sql.contains("service_role"))
    }

    @Test
    fun formerFinderCannotUseRetiredVitaCoraAndUnrelatedUserStaysForbidden() {
        val list = functionBody(migration1104(), "canon_list_vitacora_moments")
        assertTrue(list.contains("raise exception 'FORBIDDEN'"))
        assertTrue(list.contains("perform public._canon_assert_vitacora_not_retired(p_pet_id)"))
        assertTrue(list.indexOf("FORBIDDEN") < list.indexOf("_canon_assert_vitacora_not_retired"))
        val grants = functionBody(migration1104(), "canon_list_vitacora_grants")
        val proposals = functionBody(migration1104(), "canon_list_vitacora_proposals")
        assertTrue(grants.contains("raise exception 'FORBIDDEN'"))
        assertTrue(grants.contains("_canon_assert_vitacora_not_retired"))
        assertTrue(proposals.contains("_canon_assert_vitacora_not_retired"))
        val attach = functionBody(migration1104(), "canon_attach_lost_found_photo")
        val guard = attach.indexOf("_canon_assert_vitacora_not_retired(v_row.pet_id)")
        val insert = attach.indexOf("insert into public.vitacora_moments")
        assertTrue(guard >= 0)
        assertTrue(insert > guard)
        assertTrue(migration1104().contains("vitacora_moments_reject_retired_write"))
        assertTrue(migration1104().contains("before insert or update on public.vitacora_moments"))
        assertTrue(migration1104().contains("before insert on public.vitacora_access_grants"))
        assertTrue(migration1104().contains("before insert on public.pet_permission_grants"))
        assertFalse(migration1104().contains("before update on public.vitacora_access_grants"))
    }

    private fun assertRejectsBeforeWrite(functionName: String, writeMarker: String) {
        val body = functionBody(migration1104(), functionName)
        val guard = body.indexOf("_canon_assert_vitacora_not_retired")
        val write = body.indexOf(writeMarker)
        assertTrue("$functionName guard", guard >= 0)
        assertTrue("$functionName write", write > guard)
        assertFalse(body.contains("successor_pet_id"))
    }

    private fun migration1104(): String = migration(migrationName)

    private fun migration(name: String): String =
        sourceFile("infra/supabase-canonical/supabase/migrations/$name").readText()

    private fun functionBody(sql: String, name: String): String {
        val marker = "function public.$name"
        val start = sql.indexOf(marker)
        check(start >= 0) { "MISSING:$name" }
        val end = sql.indexOf("\$\$;", start)
        check(end > start) { "UNCLOSED:$name" }
        return sql.substring(start, end)
    }

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
