package com.comunidapp.app.domain.m17

import com.comunidapp.app.data.model.M17CampaignSearchFilter
import com.comunidapp.app.data.model.M17CampaignStatus
import com.comunidapp.app.data.model.M17Contribution
import com.comunidapp.app.data.model.M17ContributionStatus
import com.comunidapp.app.data.model.M17DonorVisibility
import com.comunidapp.app.data.model.M17InKindCategory
import com.comunidapp.app.data.model.M17InKindNeedStatus
import com.comunidapp.app.data.model.M17InKindPledgeStatus
import com.comunidapp.app.data.model.M17InKindSearchFilter
import com.comunidapp.app.data.model.M17VolunteerApplicationStatus
import com.comunidapp.app.data.model.M17VolunteerOpportunityStatus
import com.comunidapp.app.data.model.M17VolunteerOpportunityType
import com.comunidapp.app.data.model.M17VolunteerSearchFilter
import com.comunidapp.app.data.repository.M17ExtendedMemoryStore
import com.comunidapp.app.data.repository.M17MemoryStore
import com.comunidapp.app.data.repository.MockM17DonationRepository
import com.comunidapp.app.data.repository.MockM17InKindRepository
import com.comunidapp.app.data.repository.MockM17VolunteerRepository
import com.comunidapp.app.domain.capability.PersonCapabilityCode
import com.comunidapp.app.domain.user.SessionGeneration
import com.comunidapp.app.ui.UiRegressionGateTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeoVer 17B.8 — ayudar a la comunidad vs actividad personal.
 */
class CommunityHelp17B8Test {

    @Test
    fun moneyUsesLocalThousandsAndKeepsTheRealAmountOverGoal() = runBlocking {
        assertEquals("$95.000", MoneyPresentation.formatMinor(95_000_00, "ARS"))
        assertEquals("$250.000", MoneyPresentation.formatMinor(250_000_00, "ARS"))
        assertEquals("$95.000 de $250.000", MoneyPresentation.raisedOfGoal(95_000_00, 250_000_00, "ARS"))
        assertEquals("$270.000 de $250.000", MoneyPresentation.raisedOfGoal(270_000_00, 250_000_00, "ARS"))
        assertEquals("USD 95.000", MoneyPresentation.formatMinor(95_000_00, "USD"))
        assertFalse(MoneyPresentation.formatMinor(95_000_00, "ARS").contains("ARS"))
        assertFalse(MoneyPresentation.formatMinor(95_000_00, "ARS").contains("95000.00"))
        assertEquals(1f, MoneyPresentation.barFraction(116))

        val repo = MockM17DonationRepository(actorUserId = { PERSON })
        val over = repo.searchPublicCampaigns(M17CampaignSearchFilter()).getOrThrow()
            .first { it.title == "Campaña meta superada" }
        assertTrue(over.confirmedAmountMinor > over.goalAmountMinor)
        assertTrue(over.progressPercent > 100)
        assertEquals(70_000_00, over.confirmedAmountMinor)
        val line = MoneyPresentation.raisedOfGoal(
            over.confirmedAmountMinor,
            over.goalAmountMinor,
            over.currency
        )
        assertEquals("$70.000 de $60.000", line)
        assertEquals(1f, MoneyPresentation.barFraction(over.progressPercent))
        assertTrue(line.contains(MoneyPresentation.formatMinor(over.confirmedAmountMinor, over.currency)))
    }

    @Test
    fun personDeclaresAndSeesOwnHistoryWithoutConfirming() = runBlocking {
        var actor: String? = PERSON
        val store = M17MemoryStore()
        val repo = MockM17DonationRepository(actorUserId = { actor }, store = store)
        val campaign = repo.searchPublicCampaigns(M17CampaignSearchFilter()).getOrThrow()
            .first { it.status == M17CampaignStatus.PUBLISHED && it.title == "Cirugía para Bruno" }
        store.upsertContribution(
            own(campaign.id, 1_500_00, 1_000L)
        )
        store.upsertContribution(
            own(campaign.id, 2_500_00, 2_000L)
        )
        val declared = repo.declareContribution(campaign.id, 3_200_00, "nota", "ARS").getOrThrow()
        assertEquals(M17ContributionStatus.PENDING, declared.status)
        assertEquals(
            "Pendiente de confirmación",
            CommunityHelpPresentation.contributionStatus(declared.status)
        )
        assertTrue(repo.confirmContribution(declared.id).isFailure)
        assertTrue(repo.rejectContribution(declared.id).isFailure)

        val mine = repo.listMyContributions().getOrThrow()
        assertEquals(3_200_00, mine.first().amountMinor)
        assertTrue(mine.zipWithNext().all { (a, b) -> a.createdAt >= b.createdAt })
        assertTrue(mine.all { it.campaignTitle == "Cirugía para Bruno" })
        assertTrue(mine.all { it.organizationName.isNotBlank() })
        assertEquals("Pendiente de confirmación", CommunityHelpPresentation.contributionStatus(mine.first().status))

        actor = OTHER
        assertTrue(repo.listMyContributions().getOrThrow().isEmpty())
        actor = PERSON
        store.clearSessionResidue()
        assertTrue(repo.listMyContributions().getOrThrow().isEmpty())
    }

    @Test
    fun authorizedManagerCanModerateSomeoneElseAndNotThemselves() = runBlocking {
        var actor = PERSON
        val store = M17MemoryStore()
        val repo = MockM17DonationRepository(actorUserId = { actor }, store = store)
        val campaign = repo.searchPublicCampaigns(M17CampaignSearchFilter()).getOrThrow()
            .first { it.title == "Cirugía para Bruno" }
        val declared = repo.declareContribution(campaign.id, 4_000_00, null, "ARS").getOrThrow()
        actor = SessionGeneration.NEUTRAL_MOCK_ACTOR
        val confirmed = repo.confirmContribution(declared.id).getOrThrow()
        assertEquals(M17ContributionStatus.CONFIRMED, confirmed.status)
        val own = repo.declareContribution(campaign.id, 500_00, null, "ARS").getOrThrow()
        assertTrue(repo.confirmContribution(own.id).isFailure)
        assertEquals(M17ContributionStatus.PENDING, store.contributions.value.first { it.id == own.id }.status)
    }

    @Test
    fun goodsDetailIsUnderstandableAndHelpPersistsWithoutManagement() = runBlocking {
        var actor: String? = PERSON
        val store = M17ExtendedMemoryStore()
        val visitor = MockM17InKindRepository(
            actorUserId = { actor },
            store = store,
            canManage = { false }
        )
        val needs = visitor.searchPublicNeeds(M17InKindSearchFilter()).getOrThrow()
        assertTrue(needs.isNotEmpty())
        assertTrue(visitor.listMyPledges().getOrThrow().isEmpty())
        val food = needs.first { it.category == M17InKindCategory.FOOD }
        assertEquals("Alimento", CommunityHelpPresentation.goodsCategory(food.category))
        assertEquals("Refugio Comunitario Norte", food.organizationDisplayName)
        assertTrue(food.quantityRequested > 0)
        assertTrue(food.quantityUnit.isNotBlank())
        assertTrue(food.description.isNotBlank())
        assertEquals(M17InKindNeedStatus.PUBLISHED, food.status)
        assertTrue(food.publicLocationText.orEmpty().isNotBlank())
        M17InKindCategory.entries.forEach { category ->
            val label = CommunityHelpPresentation.goodsCategory(category)
            assertFalse(label == category.name)
            assertFalse(label.contains("_"))
        }
        assertTrue(visitor.createPledge(food.id, 0, null).isFailure)
        assertTrue(visitor.listMyPledges().getOrThrow().isEmpty())
        val pledge = visitor.createPledge(food.id, 3, "puedo llevar").getOrThrow()
        assertEquals(M17InKindPledgeStatus.PLEDGED, pledge.status)
        val mine = visitor.listMyPledges().getOrThrow()
        assertEquals(1, mine.size)
        assertEquals(food.title, mine.first().needTitle)
        assertEquals("Oferta registrada", CommunityHelpPresentation.pledgeStatus(mine.first().status))
        assertFalse(visitor.canManageNeed(food.id))
        assertTrue(visitor.markDelivered(pledge.id).isFailure)
        actor = OTHER
        assertTrue(visitor.listMyPledges().getOrThrow().isEmpty())

        val manager = MockM17InKindRepository(
            actorUserId = { SessionGeneration.NEUTRAL_MOCK_ACTOR },
            store = store,
            canManage = { true }
        )
        assertTrue(manager.canManageNeed(food.id))
        assertTrue(manager.markDelivered(pledge.id).isSuccess)
    }

    @Test
    fun volunteerInterestOpensFromRealDataAndDoesNotGrantCapability() = runBlocking {
        var actor: String? = PERSON
        val store = M17ExtendedMemoryStore()
        val visitor = MockM17VolunteerRepository(
            actorUserId = { actor },
            store = store,
            canManage = { false }
        )
        val opportunities = visitor.searchPublicOpportunities(M17VolunteerSearchFilter()).getOrThrow()
        assertTrue(opportunities.isNotEmpty())
        assertTrue(visitor.listMyApplications().getOrThrow().isEmpty())
        opportunities.forEach { item ->
            val detail = visitor.getPublicOpportunity(item.id).getOrThrow()
            assertEquals(item.title, detail.title)
            assertEquals(item.organizationDisplayName, detail.organizationDisplayName)
            assertEquals(item.description, detail.description)
            assertEquals(M17VolunteerOpportunityStatus.PUBLISHED, detail.status)
            val label = CommunityHelpPresentation.volunteerType(detail.type)
            assertFalse(label == detail.type.name)
        }
        val first = opportunities.first()
        val saved = visitor.submitApplication(first.id, "puedo el sábado").getOrThrow()
        assertEquals(M17VolunteerApplicationStatus.SUBMITTED, saved.status)
        val mine = visitor.listMyApplications().getOrThrow()
        assertEquals(first.title, mine.first().opportunityTitle)
        assertEquals("Interés enviado", CommunityHelpPresentation.applicationStatus(mine.first().status))
        assertFalse(PersonCapabilityCode.entries.any { it.storageValue == "VOLUNTEER" })
        assertFalse(visitor.canManageOpportunity(first.id))
        assertTrue(visitor.acceptApplication(saved.id).isFailure)
        actor = OTHER
        assertTrue(visitor.listMyApplications().getOrThrow().isEmpty())
        val submit = functionBody(
            source("app/src/main/java/com/comunidapp/app/data/repository/M17ExtendedRepositories.kt"),
            "override suspend fun submitApplication"
        )
        assertFalse(submit.contains("PersonCapability"))
        assertFalse(submit.contains("activeCapabilities"))
    }

    @Test
    fun profileOpensPersonalActivityAndPublicCopyDropsInternalLanguage() {
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(graph.contains("onNavigateToDonations = { navController.navigate(NavRoutes.M17_MY_HELP) }"))
        assertFalse(graph.contains("onNavigateToDonations = { navController.navigate(NavRoutes.M17_HUB) }"))
        assertTrue(graph.contains("navController.navigate(NavRoutes.M17_HUB)"))
        assertTrue(graph.contains("OrganizationRoute.append"))
        assertFalse(graph.contains("OrganizationListContext.clear()"))
        val profile = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/ProfileScreen.kt")
        assertTrue(profile.contains("Mi ayuda"))
        assertFalse(profile.contains("title = \"Donaciones\""))
        val nav = source("app/src/main/java/com/comunidapp/app/navigation/M17NavGraph.kt")
        assertTrue(nav.contains("M17MyHelpScreen"))
        assertTrue(nav.contains("M17GoodsDetailScreen"))
        assertTrue(nav.contains("M17VolunteerDetailScreen"))
        assertTrue(nav.contains("NavRoutes.m17GoodDetail"))
        assertTrue(nav.contains("NavRoutes.m17VolunteerDetail"))
        val campaigns = source("app/src/main/java/com/comunidapp/app/ui/screens/m17/M17DonationScreens.kt")
        assertTrue(campaigns.contains("MoneyPresentation.raisedOfGoal"))
        assertTrue(campaigns.contains("MoneyPresentation.barFraction"))
        assertTrue(campaigns.contains("Quiero colaborar"))
        assertTrue(campaigns.contains("Declarar colaboración"))
        assertTrue(campaigns.contains("Colaboré"))
        assertFalse(campaigns.contains("sin datos financieros sensibles"))
        assertFalse(campaigns.contains("% del objetivo"))
        assertFalse(campaigns.contains("createdBy"))
        val help = source("app/src/main/java/com/comunidapp/app/ui/screens/m17/M17ExtendedScreens.kt")
        assertTrue(help.contains("CommunityHelpPresentation.DISCOVER_QUESTION"))
        assertTrue(help.contains("CommunityHelpPresentation.MONEY_ACTION"))
        assertTrue(help.contains("CommunityHelpPresentation.GOODS_ACTION"))
        assertTrue(help.contains("CommunityHelpPresentation.TIME_ACTION"))
        assertTrue(help.contains("CommunityHelpPresentation.WANT_TO_HELP"))
        assertTrue(help.contains("onNeedClick(need.id)"))
        assertTrue(help.contains("CommunityHelpPresentation.goodsCategory"))
        assertTrue(help.contains("CommunityHelpPresentation.volunteerType"))
        assertTrue(help.contains("CommunityHelpPresentation.EMPTY_MONEY"))
        assertTrue(help.contains("CommunityHelpPresentation.ACTIVITY_TITLE"))
        assertTrue(help.contains("Lifecycle.Event.ON_RESUME"))
        assertFalse(help.contains("transparencia mock"))
        assertFalse(help.contains("markDelivered"))
        assertFalse(help.contains("acceptApplication"))
        assertFalse(help.contains("PersonCapability"))
        assertFalse(help.contains("onInKindDetail"))
        val statuses = M17ContributionStatus.entries.joinToString(" ") {
            CommunityHelpPresentation.contributionStatus(it)
        }
        assertTrue(statuses.contains("Pendiente de confirmación"))
        assertTrue(statuses.contains("Confirmado"))
        assertTrue(statuses.contains("No confirmado"))
        assertTrue(statuses.contains("Cancelado"))
        assertFalse(statuses.contains("PENDING"))
        M17VolunteerOpportunityType.entries.forEach {
            assertFalse(CommunityHelpPresentation.volunteerType(it) == it.name)
        }
        val moneySql = source(
            "infra/supabase-canonical/supabase/migrations/20261004120000_1110_community_help_activity.sql"
        )
        assertTrue(moneySql.contains("canon_list_my_campaign_contributions"))
        assertTrue(moneySql.contains("c.contributor_user_id = auth.uid()"))
        assertTrue(moneySql.contains("org.name"))
        assertFalse(moneySql.contains("'id'"))
        assertFalse(moneySql.contains("create table"))
        val goodsSql = source("supabase/migrations/085_m17_my_help_activity.sql")
        assertTrue(goodsSql.contains("m17_list_my_in_kind_pledges"))
        assertTrue(goodsSql.contains("m17_list_my_volunteer_applications"))
        assertTrue(goodsSql.contains("o.display_name"))
        assertTrue(goodsSql.contains("p.contributor_user_id = v_actor"))
        assertTrue(goodsSql.contains("a.applicant_user_id = v_actor"))
        assertFalse(goodsSql.contains("'id'"))
        assertFalse(goodsSql.contains("create table"))
    }

    private fun own(campaignId: String, amount: Long, at: Long) = M17Contribution(
        id = "own-$at",
        campaignId = campaignId,
        amountMinor = amount,
        currency = "ARS",
        status = M17ContributionStatus.PENDING,
        visibility = M17DonorVisibility.PRIVATE,
        createdAt = at,
        contributorUserId = PERSON
    )

    private fun source(relative: String): String =
        UiRegressionGateTest.sourceFile(relative).readText()

    private fun functionBody(sql: String, name: String): String {
        val start = sql.indexOf(name)
        assertTrue(start >= 0)
        val next = sql.indexOf("override suspend fun", start + name.length)
        return if (next < 0) sql.substring(start) else sql.substring(start, next)
    }

    private companion object {
        const val PERSON = "person-visitor"
        const val OTHER = "person-stranger"
    }
}
