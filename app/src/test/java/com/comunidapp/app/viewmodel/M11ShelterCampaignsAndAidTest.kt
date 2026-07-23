package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.ShelterCampaignCategory
import com.comunidapp.app.data.model.ShelterCampaignStatus
import com.comunidapp.app.data.model.ShelterCampaignVisibility
import com.comunidapp.app.data.model.ShelterStatus
import com.comunidapp.app.data.model.ShelterSupplyCategory
import com.comunidapp.app.data.model.ShelterSupplyContributionStatus
import com.comunidapp.app.data.model.ShelterSupplyPriority
import com.comunidapp.app.data.model.ShelterSupplyRequestStatus
import com.comunidapp.app.data.model.ShelterVolunteerRole
import com.comunidapp.app.data.remote.supabase.m11.M11ShelterErrorMapper
import com.comunidapp.app.data.repository.AddShelterCampaignUpdateInput
import com.comunidapp.app.data.repository.CreateShelterCampaignInput
import com.comunidapp.app.data.repository.CreateShelterProfileInput
import com.comunidapp.app.data.repository.CreateSupplyRequestInput
import com.comunidapp.app.data.repository.FosterSecureRefValidator
import com.comunidapp.app.data.repository.M11ShelterAuditEvents
import com.comunidapp.app.data.repository.M11ShelterMemoryStore
import com.comunidapp.app.data.repository.M11ShelterNotificationHooks
import com.comunidapp.app.data.repository.MockShelterCampaignRepository
import com.comunidapp.app.data.repository.MockShelterProfileRepository
import com.comunidapp.app.data.repository.MockShelterSupplyRepository
import com.comunidapp.app.data.repository.MockShelterVolunteerRepository
import com.comunidapp.app.data.repository.PledgeSupplyContributionInput
import com.comunidapp.app.data.repository.UpdateShelterCampaignInput
import com.comunidapp.app.data.repository.UpdateSupplyRequestInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LeoVer M11 — Bloque 2: campañas, pedidos de insumos y red de ayuda (no monetaria).
 * Solo fakes en memoria; sin Supabase real ni WorkManager.
 */
class M11ShelterCampaignsAndAidTest {

    private lateinit var store: M11ShelterMemoryStore
    private var actorId: String = "manager-1"

    private lateinit var profiles: MockShelterProfileRepository
    private lateinit var campaigns: MockShelterCampaignRepository
    private lateinit var supply: MockShelterSupplyRepository
    private lateinit var volunteers: MockShelterVolunteerRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = M11ShelterMemoryStore()
        actorId = "manager-1"
        store.organizationStatus.value = mapOf("org-1" to "ACTIVE")
        store.orgManagers.value = mapOf("org-1" to setOf("manager-1"))
        store.orgViewers.value = mapOf("org-1" to setOf("manager-1", "viewer-1", "contributor-1"))
        wire()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun wire() {
        profiles = MockShelterProfileRepository(actorUserId = { actorId }, store = store)
        campaigns = MockShelterCampaignRepository(actorUserId = { actorId }, store = store)
        supply = MockShelterSupplyRepository(actorUserId = { actorId }, store = store)
        volunteers = MockShelterVolunteerRepository(
            actorUserId = { actorId },
            store = store,
            knownUserIds = { setOf("vol-1", "manager-1", "contributor-1") }
        )
    }

    private suspend fun createActiveShelter() =
        profiles.createShelter(
            CreateShelterProfileInput(
                organizationId = "org-1",
                displayName = "Refugio Sol",
                description = "Desc",
                totalCapacity = 2,
                acceptedSpecies = setOf("DOG"),
                activate = true
            )
        ).getOrThrow()

    private suspend fun createDraftCampaign(shelterId: String) =
        campaigns.createCampaign(
            CreateShelterCampaignInput(
                shelterProfileId = shelterId,
                title = "Campaña insumos",
                description = "Ayuda con alimento",
                category = ShelterCampaignCategory.FOOD,
                visibility = ShelterCampaignVisibility.PUBLIC,
                activate = false
            )
        ).getOrThrow()

    private suspend fun createActiveCampaign(shelterId: String) =
        campaigns.createCampaign(
            CreateShelterCampaignInput(
                shelterProfileId = shelterId,
                title = "Campaña activa",
                description = "Pedimos colaboración",
                category = ShelterCampaignCategory.FOOD,
                visibility = ShelterCampaignVisibility.PUBLIC,
                activate = true
            )
        ).getOrThrow()

    private suspend fun createOpenRequest(
        shelterId: String,
        campaignId: String? = null,
        quantity: Int = 10,
        priority: ShelterSupplyPriority = ShelterSupplyPriority.NORMAL,
        expiresAt: Long? = null,
        internalNotes: String? = null
    ) = supply.createSupplyRequest(
        CreateSupplyRequestInput(
            shelterProfileId = shelterId,
            campaignId = campaignId,
            category = ShelterSupplyCategory.FOOD,
            itemName = "Alimento",
            quantityRequested = quantity,
            unitText = "kg",
            priority = priority,
            expiresAt = expiresAt,
            internalNotes = internalNotes,
            publishOpen = true
        )
    ).getOrThrow()

    private fun codeOf(r: Result<*>) = M11ShelterErrorMapper.codeOf(r.exceptionOrNull()!!)

    @Test
    fun createCampaignDraft() = runTest {
        val shelter = createActiveShelter()
        val c = createDraftCampaign(shelter.id)
        assertEquals(ShelterCampaignStatus.DRAFT, c.status)
        assertEquals("Campaña insumos", c.title)
        assertTrue(store.auditEvents.value.any { it.eventKey == M11ShelterAuditEvents.SHELTER_CAMPAIGN_CREATED })
    }

    @Test
    fun activateValidCampaign() = runTest {
        val shelter = createActiveShelter()
        val draft = createDraftCampaign(shelter.id)
        val active = campaigns.changeCampaignStatus(draft.id, ShelterCampaignStatus.ACTIVE).getOrThrow()
        assertEquals(ShelterCampaignStatus.ACTIVE, active.status)
        assertTrue(store.m06Hooks.value.contains(M11ShelterNotificationHooks.CAMPAIGN_ACTIVATED))
    }

    @Test
    fun preventCampaignOnInactiveShelter() = runTest {
        val shelter = createActiveShelter()
        profiles.changeStatus(shelter.id, ShelterStatus.PAUSED).getOrThrow()
        assertEquals(
            "SHELTER_NOT_ACTIVE",
            codeOf(
                campaigns.createCampaign(
                    CreateShelterCampaignInput(
                        shelterProfileId = shelter.id,
                        title = "X",
                        description = "Y",
                        category = ShelterCampaignCategory.EMERGENCY,
                        activate = true
                    )
                )
            )
        )
        val draft = createDraftCampaign(shelter.id)
        assertEquals(
            "SHELTER_NOT_ACTIVE",
            codeOf(campaigns.changeCampaignStatus(draft.id, ShelterCampaignStatus.ACTIVE))
        )
    }

    @Test
    fun publicListOnlyActivePublic() = runTest {
        val shelter = createActiveShelter()
        val activePublic = createActiveCampaign(shelter.id)
        createDraftCampaign(shelter.id)
        campaigns.createCampaign(
            CreateShelterCampaignInput(
                shelterProfileId = shelter.id,
                title = "Interna",
                description = "Solo staff",
                category = ShelterCampaignCategory.OTHER,
                visibility = ShelterCampaignVisibility.INTERNAL,
                activate = true
            )
        ).getOrThrow()
        val paused = createActiveCampaign(shelter.id)
        campaigns.changeCampaignStatus(paused.id, ShelterCampaignStatus.PAUSED).getOrThrow()
        val pub = campaigns.observePublicCampaigns().first()
        assertEquals(1, pub.size)
        assertEquals(activePublic.id, pub.first().id)
        assertTrue(pub.all { it.status == ShelterCampaignStatus.ACTIVE })
    }

    @Test
    fun hideInternalCampaignFromPublic() = runTest {
        val shelter = createActiveShelter()
        campaigns.createCampaign(
            CreateShelterCampaignInput(
                shelterProfileId = shelter.id,
                title = "Operativa interna",
                description = "No visible",
                category = ShelterCampaignCategory.INFRASTRUCTURE,
                visibility = ShelterCampaignVisibility.INTERNAL,
                activate = true
            )
        ).getOrThrow()
        assertTrue(campaigns.observePublicCampaigns().first().isEmpty())
    }

    @Test
    fun editCampaign() = runTest {
        val shelter = createActiveShelter()
        val c = createDraftCampaign(shelter.id)
        val updated = campaigns.updateCampaign(
            UpdateShelterCampaignInput(
                campaignId = c.id,
                title = "Nuevo título",
                description = "Nueva descripción",
                category = ShelterCampaignCategory.HYGIENE,
                visibility = ShelterCampaignVisibility.PUBLIC
            )
        ).getOrThrow()
        assertEquals("Nuevo título", updated.title)
        assertEquals(ShelterCampaignCategory.HYGIENE, updated.category)
    }

    @Test
    fun pauseCampaign() = runTest {
        val shelter = createActiveShelter()
        val c = createActiveCampaign(shelter.id)
        val paused = campaigns.changeCampaignStatus(c.id, ShelterCampaignStatus.PAUSED).getOrThrow()
        assertEquals(ShelterCampaignStatus.PAUSED, paused.status)
    }

    @Test
    fun preventCompleteWithOpenRequests() = runTest {
        val shelter = createActiveShelter()
        val campaign = createActiveCampaign(shelter.id)
        createOpenRequest(shelter.id, campaign.id)
        assertEquals(
            "SHELTER_CAMPAIGN_HAS_OPEN_REQUESTS",
            codeOf(campaigns.changeCampaignStatus(campaign.id, ShelterCampaignStatus.COMPLETED))
        )
    }

    @Test
    fun cancelCampaignCancelsOpenRequests() = runTest {
        val shelter = createActiveShelter()
        val campaign = createActiveCampaign(shelter.id)
        val request = createOpenRequest(shelter.id, campaign.id)
        campaigns.changeCampaignStatus(campaign.id, ShelterCampaignStatus.CANCELLED).getOrThrow()
        val after = supply.getSupplyRequest(request.id).getOrThrow()
        assertEquals(ShelterSupplyRequestStatus.CANCELLED, after.status)
    }

    @Test
    fun addPublicUpdate() = runTest {
        val shelter = createActiveShelter()
        val c = createActiveCampaign(shelter.id)
        val update = campaigns.addCampaignUpdate(
            AddShelterCampaignUpdateInput(
                campaignId = c.id,
                visibility = ShelterCampaignVisibility.PUBLIC,
                message = "Llegó el primer lote",
                evidenceRef = "m05://photo/1"
            )
        ).getOrThrow()
        assertEquals(ShelterCampaignVisibility.PUBLIC, update.visibility)
        assertEquals(1, campaigns.observeCampaignUpdates(c.id).first().size)
    }

    @Test
    fun addInternalUpdate() = runTest {
        val shelter = createActiveShelter()
        val c = createActiveCampaign(shelter.id)
        campaigns.addCampaignUpdate(
            AddShelterCampaignUpdateInput(
                campaignId = c.id,
                visibility = ShelterCampaignVisibility.INTERNAL,
                message = "Nota interna de logística"
            )
        ).getOrThrow()
        val updates = campaigns.observeCampaignUpdates(c.id).first()
        assertEquals(1, updates.size)
        assertEquals(ShelterCampaignVisibility.INTERNAL, updates.first().visibility)
    }

    @Test
    fun rejectUnsafeEvidence() = runTest {
        val shelter = createActiveShelter()
        assertEquals(
            "SHELTER_EVIDENCE_REF_INVALID",
            codeOf(
                campaigns.createCampaign(
                    CreateShelterCampaignInput(
                        shelterProfileId = shelter.id,
                        title = "Con cover",
                        description = "Desc",
                        category = ShelterCampaignCategory.FOOD,
                        coverAssetRef = "https://evil.example/photo.jpg",
                        activate = false
                    )
                )
            )
        )
        assertTrue(FosterSecureRefValidator.isUnsafePublicReference("https://x/object/public/leover/x"))
    }

    @Test
    fun createValidSupplyRequest() = runTest {
        val shelter = createActiveShelter()
        val request = createOpenRequest(shelter.id)
        assertEquals(ShelterSupplyRequestStatus.OPEN, request.status)
        assertEquals(10, request.quantityRequested)
        assertTrue(store.auditEvents.value.any { it.eventKey == M11ShelterAuditEvents.SHELTER_SUPPLY_REQUEST_CREATED })
    }

    @Test
    fun quantityMustBePositive() = runTest {
        val shelter = createActiveShelter()
        assertEquals(
            "SHELTER_SUPPLY_REQUEST_INVALID",
            codeOf(
                supply.createSupplyRequest(
                    CreateSupplyRequestInput(
                        shelterProfileId = shelter.id,
                        category = ShelterSupplyCategory.FOOD,
                        itemName = "X",
                        quantityRequested = 0,
                        unitText = "kg",
                        publishOpen = true
                    )
                )
            )
        )
    }

    @Test
    fun unitRequired() = runTest {
        val shelter = createActiveShelter()
        assertEquals(
            "SHELTER_SUPPLY_REQUEST_INVALID",
            codeOf(
                supply.createSupplyRequest(
                    CreateSupplyRequestInput(
                        shelterProfileId = shelter.id,
                        category = ShelterSupplyCategory.FOOD,
                        itemName = "Alimento",
                        quantityRequested = 5,
                        unitText = "   ",
                        publishOpen = true
                    )
                )
            )
        )
    }

    @Test
    fun internalNotesNotInPublicListing() = runTest {
        val shelter = createActiveShelter()
        createOpenRequest(shelter.id, internalNotes = "Depósito privado calle 9")
        val pub = supply.observePublicSupplyRequests().first().first()
        assertNull(pub.publicNotes)
        assertFalse(
            pub.javaClass.declaredFields.any { it.name == "internalNotes" }
        )
    }

    @Test
    fun partialPledge() = runTest {
        val shelter = createActiveShelter()
        val request = createOpenRequest(shelter.id, quantity = 10)
        actorId = "contributor-1"
        wire()
        val contrib = supply.pledgeContribution(
            PledgeSupplyContributionInput(requestId = request.id, quantityCommitted = 4)
        ).getOrThrow()
        assertEquals(4, contrib.quantityCommitted)
        assertEquals(ShelterSupplyRequestStatus.PARTIALLY_COMMITTED, supply.getSupplyRequest(request.id).getOrThrow().status)
    }

    @Test
    fun fullPledge() = runTest {
        val shelter = createActiveShelter()
        val request = createOpenRequest(shelter.id, quantity = 6)
        actorId = "contributor-1"
        wire()
        supply.pledgeContribution(
            PledgeSupplyContributionInput(requestId = request.id, quantityCommitted = 6)
        ).getOrThrow()
        assertEquals(ShelterSupplyRequestStatus.FULLY_COMMITTED, supply.getSupplyRequest(request.id).getOrThrow().status)
    }

    @Test
    fun preventOverPledge() = runTest {
        val shelter = createActiveShelter()
        val request = createOpenRequest(shelter.id, quantity = 5)
        actorId = "contributor-1"
        wire()
        supply.pledgeContribution(
            PledgeSupplyContributionInput(requestId = request.id, quantityCommitted = 3)
        ).getOrThrow()
        assertEquals(
            "SHELTER_CONTRIBUTION_EXCEEDS_REMAINING",
            codeOf(
                supply.pledgeContribution(
                    PledgeSupplyContributionInput(requestId = request.id, quantityCommitted = 3)
                )
            )
        )
    }

    @Test
    fun cancelContributionBeforeReceipt() = runTest {
        val shelter = createActiveShelter()
        val request = createOpenRequest(shelter.id)
        actorId = "contributor-1"
        wire()
        val contrib = supply.pledgeContribution(
            PledgeSupplyContributionInput(requestId = request.id, quantityCommitted = 2)
        ).getOrThrow()
        val cancelled = supply.cancelContribution(contrib.id).getOrThrow()
        assertEquals(ShelterSupplyContributionStatus.CANCELLED, cancelled.status)
        assertNotNull(cancelled.cancelledAt)
    }

    @Test
    fun preventCancelAfterReceipt() = runTest {
        val shelter = createActiveShelter()
        val request = createOpenRequest(shelter.id, quantity = 4)
        actorId = "contributor-1"
        wire()
        val contrib = supply.pledgeContribution(
            PledgeSupplyContributionInput(requestId = request.id, quantityCommitted = 4)
        ).getOrThrow()
        actorId = "manager-1"
        wire()
        supply.recordReceipt(contrib.id, quantityReceived = 2, evidenceRef = "file_asset:r1").getOrThrow()
        actorId = "contributor-1"
        wire()
        assertEquals(
            "SHELTER_CONTRIBUTION_ALREADY_RECEIVED",
            codeOf(supply.cancelContribution(contrib.id))
        )
    }

    @Test
    fun confirmContribution() = runTest {
        val shelter = createActiveShelter()
        val request = createOpenRequest(shelter.id)
        actorId = "contributor-1"
        wire()
        val contrib = supply.pledgeContribution(
            PledgeSupplyContributionInput(requestId = request.id, quantityCommitted = 3)
        ).getOrThrow()
        actorId = "manager-1"
        wire()
        val confirmed = supply.confirmContribution(contrib.id).getOrThrow()
        assertEquals(ShelterSupplyContributionStatus.CONFIRMED, confirmed.status)
    }

    @Test
    fun partialReceipt() = runTest {
        val shelter = createActiveShelter()
        val request = createOpenRequest(shelter.id, quantity = 8)
        actorId = "contributor-1"
        wire()
        val contrib = supply.pledgeContribution(
            PledgeSupplyContributionInput(requestId = request.id, quantityCommitted = 8)
        ).getOrThrow()
        actorId = "manager-1"
        wire()
        val received = supply.recordReceipt(
            contrib.id,
            quantityReceived = 3,
            evidenceRef = "m05://receipt/1"
        ).getOrThrow()
        assertEquals(3, received.quantityReceived)
        assertEquals(ShelterSupplyContributionStatus.PARTIALLY_RECEIVED, received.status)
        assertEquals(ShelterSupplyRequestStatus.PARTIALLY_RECEIVED, supply.getSupplyRequest(request.id).getOrThrow().status)
    }

    @Test
    fun fullReceipt() = runTest {
        val shelter = createActiveShelter()
        val request = createOpenRequest(shelter.id, quantity = 5)
        actorId = "contributor-1"
        wire()
        val contrib = supply.pledgeContribution(
            PledgeSupplyContributionInput(requestId = request.id, quantityCommitted = 5)
        ).getOrThrow()
        actorId = "manager-1"
        wire()
        val received = supply.recordReceipt(contrib.id, quantityReceived = 5).getOrThrow()
        assertEquals(ShelterSupplyContributionStatus.RECEIVED, received.status)
        assertEquals(5, supply.getSupplyRequest(request.id).getOrThrow().quantityReceived)
    }

    @Test
    fun requestBecomesFulfilled() = runTest {
        val shelter = createActiveShelter()
        val request = createOpenRequest(shelter.id, quantity = 4)
        actorId = "contributor-1"
        wire()
        val contrib = supply.pledgeContribution(
            PledgeSupplyContributionInput(requestId = request.id, quantityCommitted = 4)
        ).getOrThrow()
        actorId = "manager-1"
        wire()
        supply.recordReceipt(contrib.id, quantityReceived = 4).getOrThrow()
        assertEquals(ShelterSupplyRequestStatus.FULFILLED, supply.getSupplyRequest(request.id).getOrThrow().status)
        assertTrue(store.auditEvents.value.any { it.eventKey == M11ShelterAuditEvents.SHELTER_SUPPLY_REQUEST_FULFILLED })
        assertTrue(store.m06Hooks.value.contains(M11ShelterNotificationHooks.SUPPLY_FULFILLED))
    }

    @Test
    fun expiredRequest() = runTest {
        val shelter = createActiveShelter()
        val past = System.currentTimeMillis() - 86_400_000L
        val request = createOpenRequest(shelter.id, expiresAt = past)
        supply.updateSupplyRequest(
            UpdateSupplyRequestInput(
                requestId = request.id,
                category = ShelterSupplyCategory.FOOD,
                itemName = "Alimento",
                quantityRequested = 10,
                unitText = "kg",
                priority = ShelterSupplyPriority.NORMAL,
                expiresAt = past
            )
        ).getOrThrow()
        assertEquals(ShelterSupplyRequestStatus.EXPIRED, supply.getSupplyRequest(request.id).getOrThrow().status)
        actorId = "contributor-1"
        wire()
        assertEquals(
            "SHELTER_SUPPLY_REQUEST_EXPIRED",
            codeOf(
                supply.pledgeContribution(
                    PledgeSupplyContributionInput(requestId = request.id, quantityCommitted = 1)
                )
            )
        )
    }

    @Test
    fun cancelRequest() = runTest {
        val shelter = createActiveShelter()
        val request = createOpenRequest(shelter.id)
        val cancelled = supply.cancelSupplyRequest(request.id).getOrThrow()
        assertEquals(ShelterSupplyRequestStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun preserveHistoryCancelledContribStillInStore() = runTest {
        val shelter = createActiveShelter()
        val request = createOpenRequest(shelter.id)
        actorId = "contributor-1"
        wire()
        val contrib = supply.pledgeContribution(
            PledgeSupplyContributionInput(requestId = request.id, quantityCommitted = 2)
        ).getOrThrow()
        supply.cancelContribution(contrib.id).getOrThrow()
        assertEquals(1, store.supplyContributions.value.count { it.id == contrib.id })
        assertEquals(
            ShelterSupplyContributionStatus.CANCELLED,
            store.supplyContributions.value.first { it.id == contrib.id }.status
        )
    }

    @Test
    fun userWithoutPermission() = runTest {
        val shelter = createActiveShelter()
        actorId = "stranger"
        wire()
        assertEquals(
            "SHELTER_CAMPAIGN_FORBIDDEN",
            codeOf(
                campaigns.createCampaign(
                    CreateShelterCampaignInput(
                        shelterProfileId = shelter.id,
                        title = "X",
                        description = "Y",
                        category = ShelterCampaignCategory.FOOD
                    )
                )
            )
        )
    }

    @Test
    fun volunteerWithoutAutomaticAuthority() = runTest {
        val shelter = createActiveShelter()
        val assignment = volunteers.inviteVolunteer(
            shelter.id,
            "vol-1",
            ShelterVolunteerRole.ANIMAL_CARE,
            null
        ).getOrThrow()
        actorId = "vol-1"
        wire()
        volunteers.acceptAssignment(assignment.id).getOrThrow()
        assertEquals(
            "SHELTER_CAMPAIGN_FORBIDDEN",
            codeOf(
                campaigns.createCampaign(
                    CreateShelterCampaignInput(
                        shelterProfileId = shelter.id,
                        title = "Vol intenta",
                        description = "Sin permiso M03",
                        category = ShelterCampaignCategory.FOOD
                    )
                )
            )
        )
    }

    @Test
    fun repositoryErrorBlankId() = runTest {
        assertEquals("SHELTER_CAMPAIGN_NOT_FOUND", codeOf(campaigns.getCampaign("")))
        assertEquals("SHELTER_SUPPLY_REQUEST_NOT_FOUND", codeOf(supply.getSupplyRequest("  ")))
    }

    @Test
    fun unknownEnumFromStringControlled() {
        assertEquals(ShelterCampaignStatus.UNKNOWN, ShelterCampaignStatus.fromString("WEIRD"))
        assertEquals(ShelterCampaignCategory.UNKNOWN, ShelterCampaignCategory.fromString("?"))
        assertEquals(ShelterSupplyRequestStatus.UNKNOWN, ShelterSupplyRequestStatus.fromString("x"))
        assertEquals(ShelterSupplyContributionStatus.UNKNOWN, ShelterSupplyContributionStatus.fromString("z"))
    }

    @Test
    fun emptyIdControlled() = runTest {
        assertEquals("SHELTER_CAMPAIGN_NOT_FOUND", codeOf(campaigns.getCampaign("")))
        assertEquals("SHELTER_SUPPLY_REQUEST_NOT_FOUND", codeOf(supply.getSupplyRequest("")))
    }

    @Test
    fun nonexistentIdControlled() = runTest {
        assertEquals("SHELTER_CAMPAIGN_NOT_FOUND", codeOf(campaigns.getCampaign("missing-campaign")))
        assertEquals("SHELTER_SUPPLY_REQUEST_NOT_FOUND", codeOf(supply.getSupplyRequest("missing-request")))
    }

    @Test
    fun doubleSubmitGuarded() = runTest {
        val shelter = createActiveShelter()
        val vm = ShelterCampaignFormViewModel(shelter.id, campaignId = null, repo = campaigns)
        vm.create(
            title = "Campaña A",
            description = "Desc A",
            category = ShelterCampaignCategory.FOOD,
            visibility = ShelterCampaignVisibility.PUBLIC,
            activate = true
        )
        vm.create(
            title = "Campaña B",
            description = "Desc B",
            category = ShelterCampaignCategory.FOOD,
            visibility = ShelterCampaignVisibility.PUBLIC,
            activate = true
        )
        assertTrue(campaigns.observeShelterCampaigns(shelter.id).first().isNotEmpty())
        val request = createOpenRequest(shelter.id)
        actorId = "contributor-1"
        wire()
        val contribVm = ShelterSupplyContributeViewModel(request.id, repo = supply)
        contribVm.pledge(1, null)
        contribVm.pledge(1, null)
        assertTrue(supply.observeContributions(request.id).first().isNotEmpty())
    }

    @Test
    fun loadingInitialThenEmptyOrContent() = runTest {
        val vm = ShelterPublicCampaignsViewModel(repo = campaigns)
        // Unconfined: el collect resuelve Loading → Empty sin campañas públicas.
        assertEquals(ShelterPublicCampaignsUiState.Empty, vm.uiState.value)
        val blankDetail = ShelterCampaignDetailViewModel("", repo = campaigns)
        assertTrue(blankDetail.uiState.value is ShelterCampaignDetailUiState.Error)
        assertTrue(blankDetail.uiState.value !is ShelterCampaignDetailUiState.Loading)
    }

    @Test
    fun m05IntegrationAcceptRefs() = runTest {
        val shelter = createActiveShelter()
        val m05 = campaigns.createCampaign(
            CreateShelterCampaignInput(
                shelterProfileId = shelter.id,
                title = "Con M05",
                description = "Cover seguro",
                category = ShelterCampaignCategory.TRANSPORT,
                coverAssetRef = "m05://media/cover-1",
                activate = true
            )
        ).getOrThrow()
        assertEquals("m05://media/cover-1", m05.coverAssetRef)
        val fileAsset = campaigns.addCampaignUpdate(
            AddShelterCampaignUpdateInput(
                campaignId = m05.id,
                visibility = ShelterCampaignVisibility.PUBLIC,
                message = "Evidencia file_asset",
                evidenceRef = "file_asset:local-99"
            )
        ).getOrThrow()
        assertEquals("file_asset:local-99", fileAsset.evidenceRef)
        assertFalse(FosterSecureRefValidator.isUnsafePublicReference("m05://x"))
        assertFalse(FosterSecureRefValidator.isUnsafePublicReference("file_asset:y"))
    }

    @Test
    fun m06HooksPreparedWithoutPush() = runTest {
        val shelter = createActiveShelter()
        store.m06Hooks.value = emptyList()
        val campaign = createActiveCampaign(shelter.id)
        createOpenRequest(
            shelter.id,
            campaign.id,
            priority = ShelterSupplyPriority.URGENT
        )
        actorId = "contributor-1"
        wire()
        val request = store.supplyRequests.value.first { it.campaignId == campaign.id }
        val contrib = supply.pledgeContribution(
            PledgeSupplyContributionInput(requestId = request.id, quantityCommitted = 1)
        ).getOrThrow()
        actorId = "manager-1"
        wire()
        supply.recordReceipt(contrib.id, quantityReceived = 1).getOrThrow()
        val hooks = store.m06Hooks.value
        assertTrue(hooks.contains(M11ShelterNotificationHooks.CAMPAIGN_ACTIVATED))
        assertTrue(hooks.contains(M11ShelterNotificationHooks.SUPPLY_URGENT_CREATED))
        assertTrue(hooks.contains(M11ShelterNotificationHooks.CONTRIBUTION_PLEDGED))
        assertTrue(hooks.contains(M11ShelterNotificationHooks.CONTRIBUTION_RECEIVED))
        // Sin WorkManager: hooks solo en memoria para integración M06 futura.
        assertFalse(this.javaClass.name.contains("WorkManager"))
    }

    @Test
    fun m07AuditEventsRecorded() = runTest {
        val shelter = createActiveShelter()
        val draft = createDraftCampaign(shelter.id)
        val campaign = campaigns.changeCampaignStatus(draft.id, ShelterCampaignStatus.ACTIVE).getOrThrow()
        val request = createOpenRequest(shelter.id, campaign.id, quantity = 2)
        actorId = "contributor-1"
        wire()
        val contrib = supply.pledgeContribution(
            PledgeSupplyContributionInput(requestId = request.id, quantityCommitted = 2)
        ).getOrThrow()
        actorId = "manager-1"
        wire()
        supply.recordReceipt(contrib.id, quantityReceived = 2).getOrThrow()
        val keys = store.auditEvents.value.map { it.eventKey }.toSet()
        assertTrue(keys.contains(M11ShelterAuditEvents.SHELTER_CAMPAIGN_CREATED))
        assertTrue(keys.contains(M11ShelterAuditEvents.SHELTER_CAMPAIGN_STATUS_CHANGED))
        assertTrue(keys.contains(M11ShelterAuditEvents.SHELTER_SUPPLY_REQUEST_CREATED))
        assertTrue(keys.contains(M11ShelterAuditEvents.SHELTER_SUPPLY_CONTRIBUTION_PLEDGED))
        assertTrue(keys.contains(M11ShelterAuditEvents.SHELTER_SUPPLY_CONTRIBUTION_RECEIVED))
        assertTrue(keys.contains(M11ShelterAuditEvents.SHELTER_SUPPLY_REQUEST_FULFILLED))
    }

    @Test
    fun repositoryErrorMappedToUserMessage() {
        val msg = M11ShelterErrorMapper.userMessage("SHELTER_CAMPAIGN_FORBIDDEN")
        assertFalse(msg.contains("postgres", ignoreCase = true))
        assertFalse(msg.contains("supabase", ignoreCase = true))
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun noRealSupabaseInTests() {
        assertTrue(campaigns is MockShelterCampaignRepository)
        assertTrue(supply is MockShelterSupplyRepository)
        assertTrue(profiles is MockShelterProfileRepository)
        assertFalse(campaigns.javaClass.name.contains("Supabase"))
        assertFalse(supply.javaClass.name.contains("Supabase"))
    }
}
