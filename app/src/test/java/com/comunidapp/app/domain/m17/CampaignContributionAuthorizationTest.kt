package com.comunidapp.app.domain.m17

import com.comunidapp.app.data.model.M17CampaignSearchFilter
import com.comunidapp.app.data.model.M17CampaignStatus
import com.comunidapp.app.data.model.M17Contribution
import com.comunidapp.app.data.model.M17ContributionStatus
import com.comunidapp.app.data.model.M17DonorVisibility
import com.comunidapp.app.data.remote.supabase.m17.M17DonationErrorMapper
import com.comunidapp.app.data.repository.M17MemoryStore
import com.comunidapp.app.data.repository.MockM17DonationRepository
import com.comunidapp.app.domain.user.SessionGeneration
import com.comunidapp.app.ui.UiRegressionGateTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer 17B.2 — campaign contribution authorization.
 * Staging M17 is mock, so A–J run against that store.
 * The canonical RPCs are locked by the 1105 migration text (no pgTAP runner).
 */
class CampaignContributionAuthorizationTest {

    @Test
    fun personCanDeclareCollaboration() = runBlocking {
        val h = harness()
        val campaign = h.published()
        val declared = h.repo.declareContribution(campaign.id, 2_500, "nota", "ARS").getOrThrow()
        assertEquals(M17ContributionStatus.PENDING, declared.status)
        assertEquals(PERSON, declared.contributorUserId)
    }

    @Test
    fun samePersonCannotConfirmOwnCollaboration() = runBlocking {
        val h = harness()
        val campaign = h.published()
        val declared = h.repo.declareContribution(campaign.id, 2_500, null, "ARS").getOrThrow()
        val before = h.row(declared.id)
        assertEquals("FORBIDDEN", code(h.repo.confirmContribution(declared.id)))
        assertEquals(before, h.row(declared.id))
    }

    @Test
    fun samePersonCannotRejectOwnCollaboration() = runBlocking {
        val h = harness()
        val campaign = h.published()
        val declared = h.repo.declareContribution(campaign.id, 2_500, null, "ARS").getOrThrow()
        val before = h.row(declared.id)
        assertEquals("FORBIDDEN", code(h.repo.rejectContribution(declared.id)))
        assertEquals(before, h.row(declared.id))
    }

    @Test
    fun personCannotConfirmForeignContributionWithoutMembership() = runBlocking {
        val h = harness(PERSON)
        val campaign = h.published()
        val declared = h.repo.declareContribution(campaign.id, 3_200, null, "ARS").getOrThrow()
        h.actor = OTHER
        val before = h.row(declared.id)
        val beforeTotal = h.total(campaign.id)
        assertEquals("FORBIDDEN", code(h.repo.confirmContribution(declared.id)))
        assertEquals(before, h.row(declared.id))
        assertEquals(beforeTotal, h.total(campaign.id))
        assertFalse(h.repo.canManageOrganization(campaign.organizationId!!))
    }

    @Test
    fun authorizedMemberConfirmsAnotherUsersContribution() = runBlocking {
        val h = harness()
        val campaign = h.published()
        val declared = h.repo.declareContribution(campaign.id, 4_100, null, "ARS").getOrThrow()
        val before = h.total(campaign.id)
        h.actor = MANAGER
        val confirmed = h.repo.confirmContribution(declared.id).getOrThrow()
        assertEquals(M17ContributionStatus.CONFIRMED, confirmed.status)
        assertEquals(MANAGER, confirmed.confirmedBy)
        assertEquals(before + 4_100, h.total(campaign.id))
    }

    @Test
    fun authorizedMemberRejectsAnotherUsersContribution() = runBlocking {
        val h = harness()
        val campaign = h.published()
        val declared = h.repo.declareContribution(campaign.id, 1_800, null, "ARS").getOrThrow()
        val before = h.total(campaign.id)
        h.actor = MANAGER
        val rejected = h.repo.rejectContribution(declared.id).getOrThrow()
        assertEquals(M17ContributionStatus.REJECTED, rejected.status)
        assertEquals(before, h.total(campaign.id))
    }

    @Test
    fun authorizedMemberCannotConfirmOwnContribution() = runBlocking {
        val h = harness(MANAGER)
        val campaign = h.published()
        assertTrue(h.repo.getPublicCampaignById(campaign.id).getOrThrow().canManageContributions)
        val declared = h.repo.declareContribution(campaign.id, 2_200, null, "ARS").getOrThrow()
        val before = h.row(declared.id)
        val beforeTotal = h.total(campaign.id)
        assertEquals("FORBIDDEN", code(h.repo.confirmContribution(declared.id)))
        assertEquals(before, h.row(declared.id))
        assertEquals(beforeTotal, h.total(campaign.id))
        h.grant(OTHER, campaign.organizationId!!)
        h.actor = OTHER
        val confirmed = h.repo.confirmContribution(declared.id).getOrThrow()
        assertEquals(M17ContributionStatus.CONFIRMED, confirmed.status)
        assertEquals(beforeTotal + 2_200, h.total(campaign.id))
    }

    @Test
    fun forbiddenOperationDoesNotChangeTotal() = runBlocking {
        val h = harness()
        val campaign = h.published()
        val declared = h.repo.declareContribution(campaign.id, 9_900, null, "ARS").getOrThrow()
        val before = h.total(campaign.id)
        val beforeRow = h.row(declared.id)
        assertEquals("FORBIDDEN", code(h.repo.confirmContribution(declared.id)))
        assertEquals("FORBIDDEN", code(h.repo.rejectContribution(declared.id)))
        assertEquals(beforeRow.status, h.row(declared.id).status)
        assertEquals(beforeRow.confirmedAt, h.row(declared.id).confirmedAt)
        assertEquals(beforeRow.confirmedBy, h.row(declared.id).confirmedBy)
        assertEquals(before, h.total(campaign.id))
    }

    @Test
    fun validConfirmIncrementsTotalOnce() = runBlocking {
        val h = harness()
        val campaign = h.published()
        val before = h.total(campaign.id)
        val declared = h.repo.declareContribution(campaign.id, 6_000, null, "ARS").getOrThrow()
        assertEquals(before, h.total(campaign.id))
        h.actor = MANAGER
        h.repo.confirmContribution(declared.id).getOrThrow()
        assertEquals(before + 6_000, h.total(campaign.id))
    }

    @Test
    fun secondConfirmDoesNotDuplicateTotal() = runBlocking {
        val h = harness()
        val campaign = h.published()
        val before = h.total(campaign.id)
        val declared = h.repo.declareContribution(campaign.id, 6_000, null, "ARS").getOrThrow()
        h.actor = MANAGER
        h.repo.confirmContribution(declared.id).getOrThrow()
        h.repo.confirmContribution(declared.id).getOrThrow()
        assertEquals(before + 6_000, h.total(campaign.id))
        assertEquals(1, h.store.contributions.value.count { it.id == declared.id && it.status == M17ContributionStatus.CONFIRMED })
    }

    @Test
    fun directUpdateFromAuthenticatedCannotBypassAuthorization() {
        val sql = migration()
        assertTrue(sql.contains("revoke all on table public.donation_contributions from public, anon, authenticated"))
        assertTrue(sql.contains("alter table public.donation_contributions enable row level security"))
        assertFalse(sql.contains("create policy"))
        assertFalse(Regex("grant\\s+(update|insert|delete|select)\\s+on\\s+table\\s+public\\.donation_contributions", RegexOption.IGNORE_CASE).containsMatchIn(sql))
        val confirm = functionBody(sql, "canon_confirm_campaign_contribution")
        val reject = functionBody(sql, "canon_reject_campaign_contribution")
        assertTrue(confirm.indexOf("raise exception 'FORBIDDEN'") < confirm.indexOf("update public.donation_contributions"))
        assertTrue(reject.indexOf("raise exception 'FORBIDDEN'") < reject.indexOf("update public.donation_contributions"))
        assertTrue(confirm.contains("contributor_user_id is distinct from auth.uid()"))
        assertTrue(reject.contains("contributor_user_id is distinct from auth.uid()"))
        val previous = source("infra/supabase-canonical/supabase/migrations/20260913223000_1085_campaign_declared_contributions.sql")
        assertTrue(previous.contains("d.created_by = auth.uid()"))
        assertFalse(functionBody(sql, "_canon_campaign_is_manager").contains("created_by"))
    }

    @Test
    fun personDoesNotSeeConfirmOrRejectOnForeignCampaign() {
        val own = pending(contributor = PERSON, viewer = true)
        assertFalse(M17ContributionModeration.canConfirmOrReject(false, own, PERSON))
        val foreign = pending(contributor = "someone-else", viewer = false)
        assertFalse(M17ContributionModeration.canConfirmOrReject(false, foreign, PERSON))
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/m17/M17DonationScreens.kt")
        val detail = screen.substringAfter("fun M17CampaignDetailScreen")
        assertTrue(detail.contains("M17ContributionModeration.canConfirmOrReject"))
        assertFalse(detail.contains("isCreator"))
        val viewModel = source("app/src/main/java/com/comunidapp/app/viewmodel/M17DonationViewModels.kt")
        assertFalse(viewModel.contains("createdBy == sessionUserId"))
        assertFalse(viewModel.contains("isCreator"))
    }

    @Test
    fun declaredContributionShowsPendingStatus() = runBlocking {
        val h = harness()
        val campaign = h.published()
        h.repo.declareContribution(campaign.id, 1_500, null, "ARS").getOrThrow()
        val listed = h.repo.listManagedContributions(campaign.id).getOrThrow()
        val mine = listed.single { it.contributorUserId == PERSON }
        assertEquals(M17ContributionStatus.PENDING, mine.status)
        assertTrue(mine.declaredByViewer)
        assertEquals("Pendiente de confirmación", M17ContributionModeration.statusLabel(mine.status))
        assertFalse(M17ContributionModeration.canConfirmOrReject(false, mine, PERSON))
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/m17/M17DonationScreens.kt")
        assertTrue(screen.contains("statusLabel"))
        assertTrue(screen.contains("Queda pendiente de confirmación."))
    }

    @Test
    fun managerSeesActionsOnForeignContribution() = runBlocking {
        val h = harness()
        val campaign = h.published()
        val declared = h.repo.declareContribution(campaign.id, 1_100, null, "ARS").getOrThrow()
        h.actor = MANAGER
        val loaded = h.repo.getPublicCampaignById(campaign.id).getOrThrow()
        assertTrue(loaded.canManageContributions)
        val row = h.repo.listManagedContributions(campaign.id).getOrThrow().first { it.id == declared.id }
        assertFalse(row.declaredByViewer)
        assertTrue(M17ContributionModeration.canConfirmOrReject(true, row, MANAGER))
    }

    @Test
    fun managerDoesNotSeeActionsOnOwnContribution() = runBlocking {
        val h = harness(MANAGER)
        val campaign = h.published()
        val declared = h.repo.declareContribution(campaign.id, 1_100, null, "ARS").getOrThrow()
        val row = h.repo.listManagedContributions(campaign.id).getOrThrow().first { it.id == declared.id }
        assertTrue(row.declaredByViewer)
        assertTrue(h.repo.getPublicCampaignById(campaign.id).getOrThrow().canManageContributions)
        assertFalse(M17ContributionModeration.canConfirmOrReject(true, row, MANAGER))
    }

    @Test
    fun mockInitDoesNotPromoteCurrentUserToManager() = runBlocking {
        val store = M17MemoryStore()
        val repo = MockM17DonationRepository(actorUserId = { PERSON }, store = store)
        assertFalse(store.organizationManagers.value.values.any { PERSON in it })
        assertTrue(store.organizationManagers.value.values.all { it.single() == MANAGER })
        assertTrue(store.campaigns.value.all { it.createdBy == MANAGER })
        assertFalse(repo.canManageOrganization(store.campaigns.value.first().organizationId))
        val campaign = repo.searchPublicCampaigns(M17CampaignSearchFilter()).getOrThrow()
            .first { it.title == "Cirugía para Bruno" }
        assertFalse(repo.getPublicCampaignById(campaign.id).getOrThrow().canManageContributions)
    }

    @Test
    fun canonicalManagerUsesActiveOrgEditNotCreatedBy() {
        val sql = migration()
        val manager = functionBody(sql, "_canon_campaign_is_manager")
        assertTrue(manager.contains("_acl_org_permission"))
        assertTrue(manager.contains("'org.edit'"))
        assertTrue(manager.contains("d.organization_id is not null"))
        assertFalse(manager.contains("created_by"))
        val get = functionBody(sql, "canon_get_donation_campaign")
        assertTrue(get.contains("c.status = 'CONFIRMED'"))
        assertTrue(get.contains("'can_manage'"))
        assertTrue(sql.contains("revoke all on function public._canon_campaign_is_manager(uuid) from public, anon, authenticated"))
        assertTrue(sql.contains("grant execute on function public.canon_confirm_campaign_contribution(uuid) to authenticated"))
        assertFalse(sql.contains("grant execute on function public._canon_campaign_is_manager"))
    }

    private fun pending(contributor: String, viewer: Boolean) = M17Contribution(
        id = "c",
        campaignId = "camp",
        amountMinor = 100,
        currency = "ARS",
        status = M17ContributionStatus.PENDING,
        visibility = M17DonorVisibility.PRIVATE,
        createdAt = 0,
        contributorUserId = contributor,
        declaredByViewer = viewer
    )

    private fun code(result: Result<*>): String =
        M17DonationErrorMapper.codeOf(result.exceptionOrNull()!!)

    private fun migration(): String =
        source("infra/supabase-canonical/supabase/migrations/20261002120000_1105_campaign_contribution_authorization.sql")

    private fun source(relative: String): String =
        UiRegressionGateTest.sourceFile(relative).readText()

    private fun functionBody(sql: String, name: String): String {
        val start = sql.indexOf("function public.$name")
        assertTrue(start >= 0)
        val next = sql.indexOf("create or replace function", start + 1)
        return if (next < 0) sql.substring(start) else sql.substring(start, next)
    }

    private fun harness(initial: String = PERSON) = Harness(initial)

    private class Harness(initial: String) {
        var actor: String = initial
        val store = M17MemoryStore()
        val repo = MockM17DonationRepository(actorUserId = { actor }, store = store)

        fun published() = runBlocking {
            repo.searchPublicCampaigns(M17CampaignSearchFilter()).getOrThrow()
                .first { it.status == M17CampaignStatus.PUBLISHED }
        }

        fun row(id: String): M17Contribution = store.contributions.value.first { it.id == id }

        suspend fun total(campaignId: String): Long =
            repo.getPublicCampaignById(campaignId).getOrThrow().confirmedAmountMinor

        fun grant(userId: String, organizationId: String) {
            val current = store.organizationManagers.value
            store.organizationManagers.value = current +
                (organizationId to ((current[organizationId] ?: emptySet()) + userId))
        }
    }

    private companion object {
        const val PERSON = "person-visitor"
        const val OTHER = "person-stranger"
        val MANAGER = SessionGeneration.NEUTRAL_MOCK_ACTOR
    }
}
