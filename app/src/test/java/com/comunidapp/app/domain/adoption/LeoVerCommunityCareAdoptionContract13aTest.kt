package com.comunidapp.app.domain.adoption

import com.comunidapp.app.data.model.AdoptionApplicationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JVM contract for migration 1100 and the canonical Android adoption path.
 * Live STAGING execution stays in block 13B. This file does not apply SQL.
 */
class LeoVerCommunityCareAdoptionContract13aTest {

    @Test
    fun applicationReadContractIsAuthenticatedRpcOnly() {
        val sql = migration1100()
        val mine = functionBody(sql, "canon_list_my_adoption_applications")
        val managed = functionBody(sql, "canon_list_adoption_applications")
        val get = functionBody(sql, "canon_get_adoption_application")
        val projection = functionBody(sql, "_canon_adoption_application_json")

        assertTrue(mine.contains("if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'"))
        assertTrue(mine.contains("a.applicant_user_id = auth.uid()"))
        assertTrue(managed.contains("raise exception 'FORBIDDEN'"))
        assertTrue(managed.contains("_canon_can_manage_adoption_publication"))
        assertTrue(get.contains("v_app.applicant_user_id"))
        assertTrue(get.contains("raise exception 'FORBIDDEN'"))
        assertTrue(get.contains("_canon_can_manage_adoption_publication"))

        assertTrue(sql.contains("grant execute on function public.canon_list_my_adoption_applications() to authenticated"))
        assertTrue(sql.contains("grant execute on function public.canon_list_adoption_applications(uuid) to authenticated"))
        assertTrue(sql.contains("grant execute on function public.canon_get_adoption_application(uuid) to authenticated"))
        assertTrue(sql.contains("revoke all on function public._canon_adoption_application_json(uuid) from public, anon, authenticated"))
        assertTrue(sql.contains("revoke all on function public._canon_can_manage_adoption_publication(uuid, uuid) from public, anon, authenticated"))

        assertFalse(Regex("""grant\s+(select|all)\s+on\s+(table\s+)?public\.adoption_applications""", RegexOption.IGNORE_CASE).containsMatchIn(sql))
        assertTrue(sql.contains("revoke all on table public.adoption_applications from public, anon, authenticated"))
        assertFalse(projection.contains("email"))
        assertFalse(projection.contains("e164_phone"))
        assertFalse(projection.contains("base_address"))
        assertFalse(projection.contains("contact_phone"))
        assertFalse(sql.contains("service_role"))
        assertFalse(sql.contains("qa07"))
    }

    @Test
    fun applyIsIdempotentForOneActiveApplication() {
        val sql = migration1100()
        val apply = functionBody(sql, "canon_apply_adoption")
        val index = sql.substringAfter("create unique index if not exists adoption_applications_one_active_uidx")
            .substringBefore(";")
        assertTrue(apply.contains("for update"))
        assertTrue(apply.contains("return v_id"))
        assertTrue(apply.contains("when unique_violation then"))
        assertTrue(apply.contains("'PENDING', 'SUBMITTED', 'IN_REVIEW', 'ACCEPTED', 'PAUSED'"))
        assertTrue(index.contains("publication_id, applicant_user_id"))
        assertTrue(index.contains("'PENDING', 'SUBMITTED', 'IN_REVIEW', 'ACCEPTED', 'PAUSED'"))
        assertFalse(index.contains("REJECTED"))
        assertFalse(index.contains("WITHDRAWN"))
        assertFalse(index.contains("COMPLETED"))
        assertFalse(index.contains("CLOSED"))
        assertTrue(sql.contains("ADOPTION_ACTIVE_APPLICATION_DUPLICATES"))
        assertFalse(sql.contains("delete from public.adoption_applications"))
    }

    @Test
    fun acceptPausesOthersAndDoesNotChangeResponsibility() {
        val accept = functionBody(migration("20260921023000_1093_transit_adoption_invite.sql"), "canon_accept_adoption_application")
        assertTrue(accept.contains("set status = 'ACCEPTED'"))
        assertTrue(accept.contains("set status = 'PAUSED'"))
        assertTrue(accept.contains("'PENDING', 'SUBMITTED', 'IN_REVIEW'"))
        assertTrue(accept.contains("raise exception 'FORBIDDEN'"))
        assertFalse(accept.contains("REJECTED"))
        assertFalse(accept.contains("update public.adoption_publications"))
        assertFalse(accept.contains("update public.pets"))
        assertFalse(accept.contains("vitacora_profiles"))
        assertFalse(accept.contains("insert into public.pets"))
        assertFalse(migration1100().contains("function public.canon_accept_adoption_application"))
    }

    @Test
    fun publicationClosesOnlyAfterAcceptedCareTransfer() {
        val sql = migration1100()
        val complete = functionBody(sql, "_canon_complete_adoptions_for_pet")
        val hook = migration("20260921023000_1093_transit_adoption_invite.sql")
        val acceptTransfer = functionBody(
            migration("20260905040000_1071_canonical_care_transfers.sql"),
            "canon_accept_care_transfer"
        )
        val initiate = functionBody(
            migration("20260908120000_1079_content_grouping_privacy_transfer.sql"),
            "canon_initiate_care_transfer"
        )
        val finalize = functionBody(hook, "m09_finalize_adoption")

        assertTrue(complete.contains("a.status = 'ACCEPTED'"))
        assertTrue(complete.contains("set status = 'COMPLETED'"))
        assertTrue(complete.contains("set status = 'CLOSED'"))
        assertTrue(complete.contains("'PAUSED', 'PENDING', 'SUBMITTED', 'IN_REVIEW'"))
        assertTrue(complete.contains("and status = 'OPEN'"))
        assertFalse(complete.contains("REJECTED"))
        assertFalse(complete.contains("update public.pets"))
        assertFalse(complete.contains("vitacora_profiles"))
        assertFalse(sql.contains("m09_finalize_adoption"))

        assertTrue(hook.contains("new.status = 'ACCEPTED'"))
        assertTrue(hook.contains("_canon_complete_adoptions_for_pet(new.pet_id)"))
        assertTrue(finalize.contains("raise exception 'ADOPTION_USE_CANONICAL_TRANSFER'"))

        assertTrue(initiate.contains("_acl_is_current_custodian_operator(auth.uid(), p_pet_id, 'org.pets.transfer')"))
        assertTrue(initiate.contains("raise exception 'FORBIDDEN'"))
        assertTrue(acceptTransfer.contains("_acl_can_operate_care_actor"))
        assertTrue(acceptTransfer.contains("v_pet.id"))
        assertTrue(acceptTransfer.contains("set status = 'ACCEPTED'"))
        assertFalse(acceptTransfer.contains("insert into public.pets"))
        assertFalse(acceptTransfer.contains("vitacora_profiles"))
        assertFalse(acceptTransfer.contains("gen_random_uuid()"))
    }

    @Test
    fun qa07TransferCapabilityUsesNormalOrgRolePermissions() {
        val seed = source("infra/supabase-canonical/qa/seed_community_care_test_actors.sql")
        val validate = source("infra/supabase-canonical/qa/seed_community_care_test_actors_validate.sql")
        val product = migration("20260815172000_1020_rls_rpc.sql") +
            migration("20260905040000_1071_canonical_care_transfers.sql")
        assertTrue(seed.contains("org.pets.transfer"))
        assertTrue(seed.contains("scope = 'ORG'"))
        assertTrue(seed.contains("organization_role_permissions"))
        assertTrue(seed.contains("r07"))
        assertTrue(product.contains("org.pets.transfer"))
        assertTrue(product.contains("permission_codes where scope = 'ORG'"))
        assertTrue(validate.contains("QA07_ORG_PETS_TRANSFER"))
        assertTrue(validate.contains("rp.permission_code = 'org.pets.transfer'"))
        assertFalse(migration1100().contains("qa07shelter"))
        assertFalse(migration1100().contains("qa-cc-shelter-verified"))
    }

    @Test
    fun canonicalAndroidDoesNotUseMemoryApplicationsOrDirectFinalize() {
        val provider = source("app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt")
        val applications = provider.substringAfter("val adoptionApplicationRepository")
            .substringBefore("private fun m09IsManager")
        val completion = provider.substringAfter("val adoptionCompletionRepository")
            .substringBefore("val adoptionFollowUpRepository")
        val repo = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalAdoptionApplicationRepository.kt")
        val transfer = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalCareTransferRepository.kt")
        val viewModel = source("app/src/main/java/com/comunidapp/app/viewmodel/AdoptionCompletionViewModels.kt")

        assertTrue(applications.contains("useLegacyRemoteModules -> SupabaseAdoptionApplicationRepository()"))
        assertTrue(applications.contains("useSupabase -> CanonicalAdoptionApplicationRepository()"))
        assertTrue(applications.contains("else -> MockAdoptionApplicationRepository"))
        assertFalse(applications.contains("useSupabase -> MockAdoptionApplicationRepository"))
        assertTrue(completion.contains("useSupabase -> CanonicalAdoptionCompletionRepository"))
        assertTrue(provider.contains("not canonical completion gates"))

        assertTrue(repo.contains("CanonicalBackend.RPC_APPLY_ADOPTION"))
        assertTrue(repo.contains("CanonicalBackend.RPC_LIST_MY_ADOPTION_APPLICATIONS"))
        assertTrue(repo.contains("CanonicalBackend.RPC_LIST_ADOPTION_APPLICATIONS"))
        assertTrue(repo.contains("CanonicalBackend.RPC_GET_ADOPTION_APPLICATION"))
        assertTrue(repo.contains("CanonicalBackend.RPC_ACCEPT_ADOPTION_APPLICATION"))
        assertTrue(repo.contains("CanonicalBackend.RPC_INITIATE_CARE_TRANSFER"))
        assertTrue(repo.contains("p_target_kind\", \"PERSON\""))
        assertFalse(repo.contains("InMemoryDataStore"))
        assertFalse(repo.contains("m09_finalize"))
        assertFalse(repo.contains("RPC_SET_ADOPTION_STATUS"))

        assertTrue(transfer.contains("CanonicalBackend.RPC_ACCEPT_CARE_TRANSFER"))
        assertTrue(transfer.contains("adoptions.refresh()"))
        assertTrue(viewModel.contains("Transferencia de cuidado iniciada"))
        assertTrue(viewModel.contains("DataProvider.useSupabase && !DataProvider.useLegacyRemoteModules"))
    }

    @Test
    fun canonicalApplicationStatusesMapWithoutNewNames() {
        assertEquals(AdoptionApplicationStatus.SUBMITTED, AdoptionApplicationStatus.fromString("PENDING"))
        assertEquals(AdoptionApplicationStatus.UNDER_REVIEW, AdoptionApplicationStatus.fromString("IN_REVIEW"))
        assertEquals(AdoptionApplicationStatus.ACCEPTED, AdoptionApplicationStatus.fromString("ACCEPTED"))
        assertEquals(AdoptionApplicationStatus.PAUSED, AdoptionApplicationStatus.fromString("PAUSED"))
        assertEquals(AdoptionApplicationStatus.COMPLETED, AdoptionApplicationStatus.fromString("COMPLETED"))
        assertEquals(AdoptionApplicationStatus.CLOSED, AdoptionApplicationStatus.fromString("CLOSED"))
        assertTrue(AdoptionApplicationStatus.isActive(AdoptionApplicationStatus.PAUSED))
        assertTrue(AdoptionApplicationStatus.isActive(AdoptionApplicationStatus.ACCEPTED))
        assertFalse(AdoptionApplicationStatus.isActive(AdoptionApplicationStatus.COMPLETED))
        assertFalse(AdoptionApplicationStatus.isActive(AdoptionApplicationStatus.CLOSED))
    }

    private fun migration1100(): String =
        migration("20260927190000_1100_adoption_canonical_contract.sql")

    private fun migration(name: String): String =
        source("infra/supabase-canonical/supabase/migrations/$name")

    private fun functionBody(sql: String, name: String): String {
        val marker = "function public.$name"
        val start = sql.indexOf(marker)
        check(start >= 0) { "MISSING:$name" }
        val next = sql.indexOf("create or replace function public.", start + marker.length)
        return sql.substring(start, if (next < 0) sql.length else next)
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath"),
            File(System.getProperty("user.dir"), relativePath),
            File(System.getProperty("user.dir"), "../$relativePath")
        )
        return (candidates.firstOrNull { it.isFile } ?: error("SOURCE_NOT_FOUND:$relativePath")).readText()
    }
}
