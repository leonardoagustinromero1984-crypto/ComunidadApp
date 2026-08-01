package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.M16IntegrationPetIds
import com.comunidapp.app.data.model.CreateM17CampaignInput
import com.comunidapp.app.data.model.M17CampaignFinancialSummary
import com.comunidapp.app.data.model.M17CampaignGoal
import com.comunidapp.app.data.model.M17CampaignReference
import com.comunidapp.app.data.model.M17CampaignSearchFilter
import com.comunidapp.app.data.model.M17CampaignStatus
import com.comunidapp.app.data.model.M17CampaignType
import com.comunidapp.app.data.model.M17CampaignUpdate
import com.comunidapp.app.data.model.M17Contribution
import com.comunidapp.app.data.model.M17ContributionStatus
import com.comunidapp.app.data.model.M17DonationCampaign
import com.comunidapp.app.data.model.M17DonorVisibility
import com.comunidapp.app.data.model.M17FinancialCalculator
import com.comunidapp.app.data.model.M17MockOrganizations
import com.comunidapp.app.data.model.M17PublicCampaign
import com.comunidapp.app.data.model.M17PublicContribution
import com.comunidapp.app.data.model.M17PrivacySanitizer
import com.comunidapp.app.data.model.M17_ELIGIBLE_ORGANIZATION_TYPES
import com.comunidapp.app.data.model.RegisterM17MockContributionInput
import com.comunidapp.app.data.model.UpdateM17CampaignDetailsInput
import com.comunidapp.app.data.model.UpdateM17CampaignGoalInput
import com.comunidapp.app.data.remote.supabase.m17.M17DonationErrorMapper
import com.comunidapp.app.data.remote.supabase.m17.M17Exception
import com.comunidapp.app.domain.organization.OrganizationType
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** LeoVer M17 — store + contratos + mock (Bloque 1, sin red ni pagos reales). */

class M17MemoryStore {
    private val idSeq = AtomicLong(0)
    private val mutex = Mutex()
    private val _campaigns = MutableStateFlow<List<M17DonationCampaign>>(emptyList())
    private val _contributions = MutableStateFlow<List<M17Contribution>>(emptyList())
    private val idempotentRetries = AtomicInteger(0)

    val organizationTypes = MutableStateFlow<Map<String, OrganizationType>>(emptyMap())
    val organizationManagers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val organizationDisplayNames = MutableStateFlow<Map<String, String>>(emptyMap())
    var m06InfrastructureAvailable: Boolean = false
    var seeded: Boolean = false

    val campaigns: StateFlow<List<M17DonationCampaign>> = _campaigns.asStateFlow()
    val contributions: StateFlow<List<M17Contribution>> = _contributions.asStateFlow()

    fun nextId(prefix: String): String = "${prefix}_${idSeq.incrementAndGet()}"

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    fun upsertCampaign(campaign: M17DonationCampaign) {
        _campaigns.update { list ->
            (list.filterNot { it.id == campaign.id } + campaign).sortedByDescending { it.updatedAt }
        }
    }

    fun upsertContribution(contribution: M17Contribution) {
        _contributions.update { list ->
            (list.filterNot { it.id == contribution.id } + contribution).sortedByDescending { it.createdAt }
        }
    }

    fun recordIdempotentRetry() {
        idempotentRetries.incrementAndGet()
    }

    fun contributionsFor(campaignId: String): List<M17Contribution> =
        _contributions.value.filter { it.campaignId == campaignId }

    fun seedDefaults(actorUserId: String = "mock_user_admin") {
        if (seeded) return
        seeded = true
        val now = System.currentTimeMillis()
        organizationTypes.value = mapOf(
            M17MockOrganizations.ORG_NORTE to OrganizationType.SHELTER,
            M17MockOrganizations.ORG_SUR to OrganizationType.RESCUE_GROUP,
            M17MockOrganizations.ORG_OESTE to OrganizationType.NGO,
            "org_clinica_demo" to OrganizationType.VETERINARY_CLINIC
        )
        organizationManagers.value = mapOf(
            M17MockOrganizations.ORG_NORTE to setOf(actorUserId),
            M17MockOrganizations.ORG_SUR to setOf(actorUserId),
            M17MockOrganizations.ORG_OESTE to setOf(actorUserId)
        )
        organizationDisplayNames.value = mapOf(
            M17MockOrganizations.ORG_NORTE to "Refugio Comunitario Norte",
            M17MockOrganizations.ORG_SUR to "Rescate Sur",
            M17MockOrganizations.ORG_OESTE to "Red Solidaria Oeste"
        )

        val cMedical = campaign(
            id = nextId("m17_campaign"),
            org = M17MockOrganizations.ORG_NORTE,
            title = "Cirugía para Bruno",
            type = M17CampaignType.MEDICAL,
            status = M17CampaignStatus.PUBLISHED,
            goalMinor = 250_000_00,
            ref = M17CampaignReference(
                petId = M16IntegrationPetIds.PET_HOUSED,
                petPublicName = "Bruno",
                publicLocationText = "Zona norte · CABA"
            ),
            actor = actorUserId,
            now = now
        )
        val cFood = campaign(
            id = nextId("m17_campaign"),
            org = M17MockOrganizations.ORG_NORTE,
            title = "Alimento para el refugio",
            type = M17CampaignType.FOOD_AND_SUPPLIES,
            status = M17CampaignStatus.PUBLISHED,
            goalMinor = 180_000_00,
            ref = M17CampaignReference(
                shelterProfileId = "m16_shelter_1",
                shelterPublicName = "Refugio Comunitario Norte",
                publicLocationText = "Zona norte · CABA"
            ),
            actor = actorUserId,
            now = now - 86_400_000L
        )
        val cPaused = campaign(
            id = nextId("m17_campaign"),
            org = M17MockOrganizations.ORG_SUR,
            title = "Traslado solidario",
            type = M17CampaignType.TRANSPORT,
            status = M17CampaignStatus.PAUSED,
            goalMinor = 90_000_00,
            actor = actorUserId,
            now = now - 172_800_000L
        )
        val cCompleted = campaign(
            id = nextId("m17_campaign"),
            org = M17MockOrganizations.ORG_OESTE,
            title = "Emergencia veterinaria resuelta",
            type = M17CampaignType.EMERGENCY,
            status = M17CampaignStatus.COMPLETED,
            goalMinor = 120_000_00,
            actor = actorUserId,
            now = now - 604_800_000L
        )
        val cCancelled = campaign(
            id = nextId("m17_campaign"),
            org = M17MockOrganizations.ORG_SUR,
            title = "Infraestructura cancelada",
            type = M17CampaignType.SHELTER_INFRASTRUCTURE,
            status = M17CampaignStatus.CANCELLED,
            goalMinor = 500_000_00,
            actor = actorUserId,
            now = now - 900_000_000L
        )
        val cDraft = campaign(
            id = nextId("m17_campaign"),
            org = M17MockOrganizations.ORG_NORTE,
            title = "Borrador campaña apoyo general",
            type = M17CampaignType.GENERAL_SUPPORT,
            status = M17CampaignStatus.DRAFT,
            goalMinor = 50_000_00,
            actor = actorUserId,
            now = now
        )
        val cNoContrib = campaign(
            id = nextId("m17_campaign"),
            org = M17MockOrganizations.ORG_OESTE,
            title = "Rescate en ruta",
            type = M17CampaignType.RESCUE,
            status = M17CampaignStatus.PUBLISHED,
            goalMinor = 75_000_00,
            actor = actorUserId,
            now = now - 50_000_000L
        )
        val cNearGoal = campaign(
            id = nextId("m17_campaign"),
            org = M17MockOrganizations.ORG_NORTE,
            title = "Medicamentos mensuales",
            type = M17CampaignType.MEDICAL,
            status = M17CampaignStatus.PUBLISHED,
            goalMinor = 100_000_00,
            actor = actorUserId,
            now = now - 30_000_000L
        )
        val cOverGoal = campaign(
            id = nextId("m17_campaign"),
            org = M17MockOrganizations.ORG_SUR,
            title = "Campaña meta superada",
            type = M17CampaignType.GENERAL_SUPPORT,
            status = M17CampaignStatus.PUBLISHED,
            goalMinor = 60_000_00,
            actor = actorUserId,
            now = now - 20_000_000L
        )

        listOf(cMedical, cFood, cPaused, cCompleted, cCancelled, cDraft, cNoContrib, cNearGoal, cOverGoal)
            .forEach { upsertCampaign(it) }

        seedContributions(cMedical.id, cFood.id, cNearGoal.id, cOverGoal.id, now)
    }

    private fun campaign(
        id: String,
        org: String,
        title: String,
        type: M17CampaignType,
        status: M17CampaignStatus,
        goalMinor: Long,
        ref: M17CampaignReference = M17CampaignReference(),
        actor: String,
        now: Long
    ) = M17DonationCampaign(
        id = id,
        organizationId = org,
        organizationDisplayName = organizationDisplayNames.value[org] ?: org,
        title = title,
        description = "Campaña solidaria mock para bienestar animal. Sin pagos reales en Bloque 1.",
        campaignType = type,
        status = status,
        goal = M17CampaignGoal(goalMinor, "ARS"),
        reference = ref,
        coverImageRef = "mock://m17/cover/$id",
        startsAt = now - 7 * 86_400_000L,
        endsAt = now + 30 * 86_400_000L,
        createdBy = actor,
        createdAt = now,
        updatedAt = now
    )

    private fun seedContributions(
        medicalId: String,
        foodId: String,
        nearGoalId: String,
        overGoalId: String,
        now: Long
    ) {
        val samples = listOf(
            contrib(medicalId, 50_000_00, M17ContributionStatus.CONFIRMED, M17DonorVisibility.PUBLIC, "María G.", now),
            contrib(medicalId, 30_000_00, M17ContributionStatus.CONFIRMED, M17DonorVisibility.ANONYMOUS, null, now),
            contrib(medicalId, 10_000_00, M17ContributionStatus.PENDING, M17DonorVisibility.PUBLIC, "Pending", now),
            contrib(medicalId, 5_000_00, M17ContributionStatus.FAILED, M17DonorVisibility.PUBLIC, "Fail", now),
            contrib(medicalId, 8_000_00, M17ContributionStatus.REFUNDED, M17DonorVisibility.PUBLIC, "Refund", now),
            contrib(medicalId, 15_000_00, M17ContributionStatus.CONFIRMED, M17DonorVisibility.PRIVATE, "Privado", now),
            contrib(foodId, 40_000_00, M17ContributionStatus.CONFIRMED, M17DonorVisibility.PUBLIC, "Carlos", now),
            contrib(nearGoalId, 92_000_00, M17ContributionStatus.CONFIRMED, M17DonorVisibility.PUBLIC, "Ana", now),
            contrib(overGoalId, 70_000_00, M17ContributionStatus.CONFIRMED, M17DonorVisibility.ANONYMOUS, null, now)
        )
        samples.forEach { upsertContribution(it) }
    }

    private fun contrib(
        campaignId: String,
        amount: Long,
        status: M17ContributionStatus,
        visibility: M17DonorVisibility,
        name: String?,
        now: Long
    ) = M17Contribution(
        id = nextId("m17_contrib"),
        campaignId = campaignId,
        amountMinor = amount,
        currency = "ARS",
        status = status,
        visibility = visibility,
        donorDisplayName = name,
        message = "Gracias por apoyar",
        providerReference = "mock_provider_ref_redacted",
        createdAt = now
    )
}

interface M17DonationRepository {
    fun observeCampaignById(campaignId: String): Flow<M17DonationCampaign?>
    fun observeCampaignsForOrganization(organizationId: String): Flow<List<M17DonationCampaign>>
    suspend fun searchPublicCampaigns(filter: M17CampaignSearchFilter): Result<List<M17PublicCampaign>>
    suspend fun getPublicCampaignById(campaignId: String): Result<M17PublicCampaign>
    suspend fun createCampaign(input: CreateM17CampaignInput): Result<M17DonationCampaign>
    suspend fun updateCampaignDetails(input: UpdateM17CampaignDetailsInput): Result<M17DonationCampaign>
    suspend fun updateCampaignGoal(input: UpdateM17CampaignGoalInput): Result<M17DonationCampaign>
    suspend fun updateCampaignReferences(
        campaignId: String,
        reference: M17CampaignReference
    ): Result<M17DonationCampaign>
    suspend fun updateCampaignImages(
        campaignId: String,
        coverImageRef: String?,
        galleryImageRefs: List<String>
    ): Result<M17DonationCampaign>
    suspend fun publishCampaign(campaignId: String): Result<M17DonationCampaign>
    suspend fun pauseCampaign(campaignId: String): Result<M17DonationCampaign>
    suspend fun completeCampaign(campaignId: String): Result<M17DonationCampaign>
    suspend fun cancelCampaign(campaignId: String): Result<M17DonationCampaign>
    suspend fun addPublicUpdate(campaignId: String, message: String): Result<M17DonationCampaign>
    suspend fun observeFinancialSummary(campaignId: String): Result<M17CampaignFinancialSummary>
    suspend fun observePublicContributions(campaignId: String): Result<List<M17PublicContribution>>
    suspend fun registerMockContribution(input: RegisterM17MockContributionInput): Result<M17Contribution>
    suspend fun refreshCampaign(campaignId: String): Result<M17DonationCampaign>
    suspend fun canManageOrganization(organizationId: String): Boolean
    suspend fun isOrganizationEligible(organizationId: String): Boolean
}

interface M17DonationAuthorityPolicy {
    fun canManageCampaign(actorUserId: String, organizationId: String, store: M17MemoryStore): Boolean
    fun isOrganizationEligible(organizationId: String, store: M17MemoryStore): Boolean
}

class MockM17DonationAuthorityPolicy : M17DonationAuthorityPolicy {
    override fun canManageCampaign(
        actorUserId: String,
        organizationId: String,
        store: M17MemoryStore
    ): Boolean = store.organizationManagers.value[organizationId]?.contains(actorUserId) == true

    override fun isOrganizationEligible(organizationId: String, store: M17MemoryStore): Boolean {
        val type = store.organizationTypes.value[organizationId] ?: return false
        return type in M17_ELIGIBLE_ORGANIZATION_TYPES
    }
}

private fun failM17(code: String): Nothing =
    throw M17Exception(code, M17DonationErrorMapper.userMessage(code))

class MockM17DonationRepository(
    private val actorUserId: () -> String?,
    private val store: M17MemoryStore = M17MemoryStore(),
    private val authority: M17DonationAuthorityPolicy = MockM17DonationAuthorityPolicy()
) : M17DonationRepository {

    init {
        store.seedDefaults(actorUserId() ?: "mock_user_admin")
    }

    private fun requireActor(): String =
        actorUserId() ?: failM17("NOT_AUTHENTICATED")

    private fun requireManage(orgId: String, actor: String) {
        if (!authority.isOrganizationEligible(orgId, store)) failM17("M17_ORGANIZATION_NOT_ELIGIBLE")
        if (!authority.canManageCampaign(actor, orgId, store)) failM17("M17_PERMISSION_DENIED")
    }

    private fun getCampaignOrFail(id: String): M17DonationCampaign =
        store.campaigns.value.firstOrNull { it.id == id } ?: failM17("M17_CAMPAIGN_NOT_FOUND")

    private fun summaryFor(campaign: M17DonationCampaign): M17CampaignFinancialSummary =
        M17FinancialCalculator.summarize(campaign.goal, store.contributionsFor(campaign.id))

    override fun observeCampaignById(campaignId: String): Flow<M17DonationCampaign?> =
        store.campaigns.map { list -> list.firstOrNull { it.id == campaignId } }

    override fun observeCampaignsForOrganization(organizationId: String): Flow<List<M17DonationCampaign>> =
        store.campaigns.map { list -> list.filter { it.organizationId == organizationId } }

    override suspend fun searchPublicCampaigns(filter: M17CampaignSearchFilter): Result<List<M17PublicCampaign>> =
        runCatching {
            store.campaigns.value
                .filter { c ->
                    when {
                        filter.completedOnly -> c.status == M17CampaignStatus.COMPLETED
                        filter.activeOnly -> c.status == M17CampaignStatus.PUBLISHED
                        else -> c.status.isPublic
                    }
                }
                .filter { c ->
                    filter.query.isBlank() ||
                        c.title.contains(filter.query, ignoreCase = true) ||
                        c.description.contains(filter.query, ignoreCase = true)
                }
                .filter { c -> filter.type == null || c.campaignType == filter.type }
                .filter { c -> filter.organizationId == null || c.organizationId == filter.organizationId }
                .filter { c ->
                    filter.shelterProfileId == null ||
                        c.reference.shelterProfileId == filter.shelterProfileId
                }
                .filter { c -> !filter.withPetOnly || c.reference.petId != null }
                .filter { c ->
                    !filter.nearGoalOnly || summaryFor(c).progressPercent >= 85
                }
                .map { c -> c.toPublicCampaign(summaryFor(c)) }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M17DonationErrorMapper.failure(it) }
        )

    override suspend fun getPublicCampaignById(campaignId: String): Result<M17PublicCampaign> =
        runCatching {
            val c = getCampaignOrFail(campaignId)
            if (c.status != M17CampaignStatus.PUBLISHED &&
                c.status != M17CampaignStatus.PAUSED &&
                c.status != M17CampaignStatus.COMPLETED
            ) {
                failM17("M17_CAMPAIGN_NOT_PUBLIC")
            }
            c.toPublicCampaign(summaryFor(c))
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M17DonationErrorMapper.failure(it) }
        )

    override suspend fun createCampaign(input: CreateM17CampaignInput): Result<M17DonationCampaign> =
        store.withLock {
            runCatching {
                val actor = requireActor()
                requireManage(input.organizationId, actor)
                M17DonationValidators.validateTitle(input.title)?.let { failM17(it) }
                M17DonationValidators.validateDescription(input.description)?.let { failM17(it) }
                M17DonationValidators.validateGoal(input.goalAmountMinor, input.currency)?.let { failM17(it) }
                M17DonationValidators.validateDateRange(input.startsAt, input.endsAt)?.let { failM17(it) }
                val now = System.currentTimeMillis()
                val campaign = M17DonationCampaign(
                    id = store.nextId("m17_campaign"),
                    organizationId = input.organizationId,
                    organizationDisplayName = store.organizationDisplayNames.value[input.organizationId]
                        ?: input.organizationId,
                    title = input.title.trim(),
                    description = input.description.trim(),
                    campaignType = input.campaignType,
                    status = M17CampaignStatus.DRAFT,
                    goal = M17CampaignGoal(input.goalAmountMinor, input.currency.uppercase()),
                    reference = input.reference,
                    coverImageRef = input.coverImageRef,
                    startsAt = input.startsAt,
                    endsAt = input.endsAt,
                    createdBy = actor,
                    createdAt = now,
                    updatedAt = now
                )
                store.upsertCampaign(campaign)
                campaign
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { M17DonationErrorMapper.failure(it) }
            )
        }

    override suspend fun updateCampaignDetails(
        input: UpdateM17CampaignDetailsInput
    ): Result<M17DonationCampaign> = mutate(input.campaignId) { c, actor ->
        if (c.status != M17CampaignStatus.DRAFT && c.status != M17CampaignStatus.PUBLISHED &&
            c.status != M17CampaignStatus.PAUSED
        ) {
            failM17("M17_INVALID_STATE_TRANSITION")
        }
        M17DonationValidators.validateTitle(input.title)?.let { failM17(it) }
        M17DonationValidators.validateDescription(input.description)?.let { failM17(it) }
        c.copy(
            title = input.title.trim(),
            description = input.description.trim(),
            campaignType = input.campaignType,
            reference = input.reference,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun updateCampaignGoal(input: UpdateM17CampaignGoalInput): Result<M17DonationCampaign> =
        mutate(input.campaignId) { c, _ ->
            val hasConfirmed = store.contributionsFor(c.id).any {
                it.status == M17ContributionStatus.CONFIRMED
            }
            M17DonationValidators.validateCurrencyChange(c, input.currency, hasConfirmed)?.let { failM17(it) }
            M17DonationValidators.validateGoal(input.goalAmountMinor, input.currency)?.let { failM17(it) }
            c.copy(
                goal = M17CampaignGoal(input.goalAmountMinor, input.currency.uppercase()),
                updatedAt = System.currentTimeMillis()
            )
        }

    override suspend fun updateCampaignReferences(
        campaignId: String,
        reference: M17CampaignReference
    ): Result<M17DonationCampaign> = mutate(campaignId) { c, _ ->
        c.copy(reference = reference, updatedAt = System.currentTimeMillis())
    }

    override suspend fun updateCampaignImages(
        campaignId: String,
        coverImageRef: String?,
        galleryImageRefs: List<String>
    ): Result<M17DonationCampaign> = mutate(campaignId) { c, _ ->
        c.copy(
            coverImageRef = coverImageRef,
            galleryImageRefs = galleryImageRefs,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun publishCampaign(campaignId: String): Result<M17DonationCampaign> =
        transition(campaignId, M17CampaignStatus.PUBLISHED)

    override suspend fun pauseCampaign(campaignId: String): Result<M17DonationCampaign> =
        transition(campaignId, M17CampaignStatus.PAUSED)

    override suspend fun completeCampaign(campaignId: String): Result<M17DonationCampaign> =
        transition(campaignId, M17CampaignStatus.COMPLETED)

    override suspend fun cancelCampaign(campaignId: String): Result<M17DonationCampaign> =
        transition(campaignId, M17CampaignStatus.CANCELLED)

    override suspend fun addPublicUpdate(campaignId: String, message: String): Result<M17DonationCampaign> =
        mutate(campaignId) { c, actor ->
            requireManage(c.organizationId, actor)
            val trimmed = message.trim()
            if (trimmed.length < 3) failM17("M17_INVALID_DESCRIPTION")
            val update = M17CampaignUpdate(
                id = store.nextId("m17_update"),
                message = trimmed,
                createdAt = System.currentTimeMillis()
            )
            c.copy(
                publicUpdates = c.publicUpdates + update,
                updatedAt = System.currentTimeMillis()
            )
        }

    override suspend fun observeFinancialSummary(campaignId: String): Result<M17CampaignFinancialSummary> =
        runCatching {
            summaryFor(getCampaignOrFail(campaignId))
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M17DonationErrorMapper.failure(it) }
        )

    override suspend fun observePublicContributions(campaignId: String): Result<List<M17PublicContribution>> =
        runCatching {
            getCampaignOrFail(campaignId)
            store.contributionsFor(campaignId)
                .mapNotNull { M17PrivacySanitizer.toPublicContribution(it) }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M17DonationErrorMapper.failure(it) }
        )

    override suspend fun registerMockContribution(
        input: RegisterM17MockContributionInput
    ): Result<M17Contribution> = store.withLock {
        runCatching {
            val campaign = getCampaignOrFail(input.campaignId)
            if (campaign.status != M17CampaignStatus.PUBLISHED) failM17("M17_CAMPAIGN_NOT_PUBLIC")
            M17DonationValidators.validateMockContribution(input)?.let { failM17(it) }
            if (input.currency.uppercase() != campaign.goal.currency.uppercase()) {
                failM17("M17_INVALID_CURRENCY")
            }
            val contribution = M17Contribution(
                id = store.nextId("m17_contrib"),
                campaignId = input.campaignId,
                amountMinor = input.amountMinor,
                currency = input.currency.uppercase(),
                status = input.status,
                visibility = input.visibility,
                donorDisplayName = input.donorDisplayName,
                message = input.message,
                providerReference = "mock_only",
                createdAt = System.currentTimeMillis()
            )
            store.upsertContribution(contribution)
            contribution
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M17DonationErrorMapper.failure(it) }
        )
    }

    override suspend fun refreshCampaign(campaignId: String): Result<M17DonationCampaign> =
        runCatching { getCampaignOrFail(campaignId) }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M17DonationErrorMapper.failure(it) }
        )

    override suspend fun canManageOrganization(organizationId: String): Boolean {
        val actor = actorUserId() ?: return false
        return authority.canManageCampaign(actor, organizationId, store)
    }

    override suspend fun isOrganizationEligible(organizationId: String): Boolean =
        authority.isOrganizationEligible(organizationId, store)

    private suspend fun transition(
        campaignId: String,
        target: M17CampaignStatus
    ): Result<M17DonationCampaign> = mutate(campaignId) { c, _ ->
        M17DonationValidators.validateStateTransition(c.status, target)?.let { failM17(it) }
        if (c.status == target) {
            store.recordIdempotentRetry()
            return@mutate c
        }
        c.copy(status = target, updatedAt = System.currentTimeMillis())
    }

    private suspend fun mutate(
        campaignId: String,
        block: (M17DonationCampaign, String) -> M17DonationCampaign
    ): Result<M17DonationCampaign> = store.withLock {
        runCatching {
            val actor = requireActor()
            val current = getCampaignOrFail(campaignId)
            requireManage(current.organizationId, actor)
            if (current.status.isTerminal) failM17("M17_STATE_ALREADY_FINAL")
            val updated = block(current, actor)
            store.upsertCampaign(updated)
            updated
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { M17DonationErrorMapper.failure(it) }
        )
    }
}
