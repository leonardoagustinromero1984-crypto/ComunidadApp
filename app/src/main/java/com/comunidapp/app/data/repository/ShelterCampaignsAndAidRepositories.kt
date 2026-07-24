package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.ShelterCampaign
import com.comunidapp.app.data.model.ShelterCampaignCategory
import com.comunidapp.app.data.model.ShelterCampaignPublicListing
import com.comunidapp.app.data.model.ShelterCampaignStatus
import com.comunidapp.app.data.model.ShelterCampaignUpdate
import com.comunidapp.app.data.model.ShelterCampaignVisibility
import com.comunidapp.app.data.model.ShelterStatus
import com.comunidapp.app.data.model.ShelterSupplyCategory
import com.comunidapp.app.data.model.ShelterSupplyContribution
import com.comunidapp.app.data.model.ShelterSupplyContributionStatus
import com.comunidapp.app.data.model.ShelterSupplyPriority
import com.comunidapp.app.data.model.ShelterSupplyRequest
import com.comunidapp.app.data.model.ShelterSupplyRequestPublicListing
import com.comunidapp.app.data.model.ShelterSupplyRequestStatus
import com.comunidapp.app.data.model.recomputeSupplyRequestStatus
import com.comunidapp.app.data.model.toPublicListing
import com.comunidapp.app.data.remote.supabase.m11.M11ShelterErrorMapper
import com.comunidapp.app.data.remote.supabase.m11.M11ShelterException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Hooks M06 preparados (sin push en este bloque). */
object M11ShelterNotificationHooks {
    const val CAMPAIGN_ACTIVATED = "CAMPAIGN_ACTIVATED"
    const val SUPPLY_URGENT_CREATED = "SUPPLY_URGENT_CREATED"
    const val CONTRIBUTION_PLEDGED = "CONTRIBUTION_PLEDGED"
    const val CONTRIBUTION_RECEIVED = "CONTRIBUTION_RECEIVED"
    const val SUPPLY_FULFILLED = "SUPPLY_FULFILLED"
    const val EMERGENCY_CRITICAL_ACTIVATED = "EMERGENCY_CRITICAL_ACTIVATED"
    const val EVENT_PUBLISHED = "EVENT_PUBLISHED"
    const val EVENT_CANCELLED = "EVENT_CANCELLED"
    const val EVENT_WAITLISTED = "EVENT_WAITLISTED"
}

/** Eventos auditables M07. */
object M11ShelterAuditEvents {
    const val SHELTER_CAMPAIGN_CREATED = "SHELTER_CAMPAIGN_CREATED"
    const val SHELTER_CAMPAIGN_STATUS_CHANGED = "SHELTER_CAMPAIGN_STATUS_CHANGED"
    const val SHELTER_SUPPLY_REQUEST_CREATED = "SHELTER_SUPPLY_REQUEST_CREATED"
    const val SHELTER_SUPPLY_CONTRIBUTION_PLEDGED = "SHELTER_SUPPLY_CONTRIBUTION_PLEDGED"
    const val SHELTER_SUPPLY_CONTRIBUTION_RECEIVED = "SHELTER_SUPPLY_CONTRIBUTION_RECEIVED"
    const val SHELTER_SUPPLY_REQUEST_FULFILLED = "SHELTER_SUPPLY_REQUEST_FULFILLED"
    const val SHELTER_EMERGENCY_CREATED = "SHELTER_EMERGENCY_CREATED"
    const val SHELTER_EMERGENCY_STATUS_CHANGED = "SHELTER_EMERGENCY_STATUS_CHANGED"
    const val SHELTER_EMERGENCY_RESOLVED = "SHELTER_EMERGENCY_RESOLVED"
    const val SHELTER_EVENT_CREATED = "SHELTER_EVENT_CREATED"
    const val SHELTER_EVENT_STATUS_CHANGED = "SHELTER_EVENT_STATUS_CHANGED"
    const val SHELTER_EVENT_REGISTRATION = "SHELTER_EVENT_REGISTRATION"
    const val SHELTER_EVENT_REGISTRATION_CANCELLED = "SHELTER_EVENT_REGISTRATION_CANCELLED"
    const val SHELTER_EVENT_ATTENDANCE = "SHELTER_EVENT_ATTENDANCE"
    const val SHELTER_REPORT_EXPORTED = "SHELTER_REPORT_EXPORTED"
}

data class CreateShelterCampaignInput(
    val shelterProfileId: String,
    val title: String,
    val description: String,
    val category: ShelterCampaignCategory,
    val visibility: ShelterCampaignVisibility = ShelterCampaignVisibility.PUBLIC,
    val startsAt: Long? = null,
    val endsAt: Long? = null,
    val coverAssetRef: String? = null,
    val activate: Boolean = false
)

data class UpdateShelterCampaignInput(
    val campaignId: String,
    val title: String,
    val description: String,
    val category: ShelterCampaignCategory,
    val visibility: ShelterCampaignVisibility,
    val startsAt: Long? = null,
    val endsAt: Long? = null,
    val coverAssetRef: String? = null
)

data class AddShelterCampaignUpdateInput(
    val campaignId: String,
    val visibility: ShelterCampaignVisibility,
    val message: String,
    val evidenceRef: String? = null
)

data class CreateSupplyRequestInput(
    val shelterProfileId: String,
    val campaignId: String? = null,
    val category: ShelterSupplyCategory,
    val itemName: String,
    val description: String? = null,
    val quantityRequested: Int,
    val unitText: String,
    val priority: ShelterSupplyPriority = ShelterSupplyPriority.NORMAL,
    val expiresAt: Long? = null,
    val publicNotes: String? = null,
    val internalNotes: String? = null,
    val publishOpen: Boolean = false
)

data class UpdateSupplyRequestInput(
    val requestId: String,
    val category: ShelterSupplyCategory,
    val itemName: String,
    val description: String? = null,
    val quantityRequested: Int,
    val unitText: String,
    val priority: ShelterSupplyPriority,
    val expiresAt: Long? = null,
    val publicNotes: String? = null,
    val internalNotes: String? = null
)

data class PledgeSupplyContributionInput(
    val requestId: String,
    val quantityCommitted: Int,
    val contributorNotes: String? = null,
    val evidenceRef: String? = null
)

interface ShelterCampaignRepository {
    fun observePublicCampaigns(): Flow<List<ShelterCampaignPublicListing>>
    fun observeShelterCampaigns(shelterId: String): Flow<List<ShelterCampaign>>
    suspend fun getCampaign(id: String): Result<ShelterCampaign>
    suspend fun createCampaign(input: CreateShelterCampaignInput): Result<ShelterCampaign>
    suspend fun updateCampaign(input: UpdateShelterCampaignInput): Result<ShelterCampaign>
    suspend fun changeCampaignStatus(
        campaignId: String,
        status: ShelterCampaignStatus
    ): Result<ShelterCampaign>
    suspend fun addCampaignUpdate(input: AddShelterCampaignUpdateInput): Result<ShelterCampaignUpdate>
    fun observeCampaignUpdates(campaignId: String): Flow<List<ShelterCampaignUpdate>>
}

interface ShelterSupplyRepository {
    fun observePublicSupplyRequests(): Flow<List<ShelterSupplyRequestPublicListing>>
    fun observeShelterSupplyRequests(shelterId: String): Flow<List<ShelterSupplyRequest>>
    suspend fun getSupplyRequest(id: String): Result<ShelterSupplyRequest>
    suspend fun createSupplyRequest(input: CreateSupplyRequestInput): Result<ShelterSupplyRequest>
    suspend fun updateSupplyRequest(input: UpdateSupplyRequestInput): Result<ShelterSupplyRequest>
    suspend fun cancelSupplyRequest(requestId: String): Result<ShelterSupplyRequest>
    suspend fun pledgeContribution(input: PledgeSupplyContributionInput): Result<ShelterSupplyContribution>
    suspend fun cancelContribution(contributionId: String): Result<ShelterSupplyContribution>
    suspend fun confirmContribution(contributionId: String): Result<ShelterSupplyContribution>
    suspend fun recordReceipt(
        contributionId: String,
        quantityReceived: Int,
        evidenceRef: String? = null,
        internalReceiptNotes: String? = null
    ): Result<ShelterSupplyContribution>
    fun observeContributions(requestId: String): Flow<List<ShelterSupplyContribution>>
}

private fun failM11(code: String): Nothing =
    throw M11ShelterException(code, M11ShelterErrorMapper.userMessage(code))

private fun M11ShelterMemoryStore.canManageCampaign(actor: String, orgId: String): Boolean =
    canManage(actor, orgId)

private fun M11ShelterMemoryStore.canReadCampaign(actor: String, orgId: String): Boolean =
    canView(actor, orgId)

private fun M11ShelterMemoryStore.recordAudit(eventKey: String, resourceId: String) {
    auditEvents.value = auditEvents.value + M11AuditEvent(eventKey, resourceId)
}

private fun M11ShelterMemoryStore.recordM06Hook(hookName: String) {
    m06Hooks.value = m06Hooks.value + hookName
}

private fun validateEvidenceRef(ref: String?) {
    if (ref.isNullOrBlank()) return
    if (FosterSecureRefValidator.isUnsafePublicReference(ref)) failM11("SHELTER_EVIDENCE_REF_INVALID")
    val lower = ref.trim().lowercase()
    if (!lower.startsWith("m05://") && !lower.startsWith("file_asset:")) {
        failM11("SHELTER_EVIDENCE_REF_INVALID")
    }
}

private fun rejectMonetaryContent(vararg texts: String?) {
    val forbidden = listOf("cbu", "alias bancario", "cuenta bancaria", "mercado pago", "tarjeta")
    texts.filterNotNull().forEach { text ->
        val lower = text.lowercase()
        if (forbidden.any { lower.contains(it) }) failM11("SHELTER_SUPPLY_REQUEST_INVALID")
    }
}

private fun requireActiveShelter(store: M11ShelterMemoryStore, shelterId: String) {
    val shelter = store.profiles.value.find { it.id == shelterId } ?: failM11("SHELTER_NOT_FOUND")
    if (!shelter.status.isOperative) failM11("SHELTER_NOT_ACTIVE")
}

private fun shelterDisplayName(store: M11ShelterMemoryStore, shelterId: String): String? =
    store.profiles.value.find { it.id == shelterId }?.displayName

private fun activeContributions(
    store: M11ShelterMemoryStore,
    requestId: String
): List<ShelterSupplyContribution> =
    store.supplyContributions.value.filter {
        it.requestId == requestId &&
            it.status !in setOf(
                ShelterSupplyContributionStatus.CANCELLED,
                ShelterSupplyContributionStatus.REJECTED
            )
    }

private fun syncSupplyRequest(store: M11ShelterMemoryStore, requestId: String): ShelterSupplyRequest {
    val request = store.supplyRequests.value.find { it.id == requestId }
        ?: failM11("SHELTER_SUPPLY_REQUEST_NOT_FOUND")
    val contribs = activeContributions(store, requestId)
    val committed = contribs.sumOf { it.quantityCommitted }
    val received = contribs.sumOf { it.quantityReceived }
    val now = System.currentTimeMillis()
    val previousStatus = request.status
    val newStatus = recomputeSupplyRequestStatus(
        request.quantityRequested,
        committed,
        received,
        request.expiresAt,
        now,
        request.status
    )
    val updated = request.copy(
        quantityCommitted = committed,
        quantityReceived = received,
        status = newStatus,
        updatedAt = now
    )
    store.supplyRequests.value = store.supplyRequests.value.map { if (it.id == requestId) updated else it }
    if (newStatus == ShelterSupplyRequestStatus.FULFILLED &&
        previousStatus != ShelterSupplyRequestStatus.FULFILLED
    ) {
        store.recordAudit(M11ShelterAuditEvents.SHELTER_SUPPLY_REQUEST_FULFILLED, requestId)
        store.recordM06Hook(M11ShelterNotificationHooks.SUPPLY_FULFILLED)
    }
    return updated
}

private fun cancelOpenSupplyRequestsForCampaign(store: M11ShelterMemoryStore, campaignId: String) {
    val openIds = store.supplyRequests.value.filter {
        it.campaignId == campaignId && it.status.isOpen
    }.map { it.id }
    openIds.forEach { id -> cancelSupplyRequestInternal(store, id) }
}

private fun cancelSupplyRequestInternal(store: M11ShelterMemoryStore, requestId: String) {
    val now = System.currentTimeMillis()
    store.supplyContributions.value = store.supplyContributions.value.map { c ->
        if (c.requestId == requestId &&
            c.quantityReceived == 0 &&
            c.status !in setOf(
                ShelterSupplyContributionStatus.CANCELLED,
                ShelterSupplyContributionStatus.REJECTED,
                ShelterSupplyContributionStatus.RECEIVED
            )
        ) {
            c.copy(
                status = ShelterSupplyContributionStatus.CANCELLED,
                cancelledAt = now
            )
        } else c
    }
    store.supplyRequests.value = store.supplyRequests.value.map { r ->
        if (r.id == requestId && r.status.isOpen) {
            r.copy(
                status = ShelterSupplyRequestStatus.CANCELLED,
                updatedAt = now
            )
        } else r
    }
}

class MockShelterCampaignRepository(
    private val actorUserId: () -> String?,
    private val store: M11ShelterMemoryStore
) : ShelterCampaignRepository {

    override fun observePublicCampaigns(): Flow<List<ShelterCampaignPublicListing>> =
        store.campaigns.map { list ->
            list.filter {
                it.status == ShelterCampaignStatus.ACTIVE &&
                    it.visibility == ShelterCampaignVisibility.PUBLIC
            }.map { campaign ->
                campaign.toPublicListing(shelterDisplayName(store, campaign.shelterProfileId))
            }
        }

    override fun observeShelterCampaigns(shelterId: String): Flow<List<ShelterCampaign>> =
        store.campaigns.map { list ->
            val actor = actorUserId()
            list.filter { campaign ->
                campaign.shelterProfileId == shelterId &&
                    actor != null &&
                    canReadCampaignFor(actor, campaign)
            }
        }

    override suspend fun getCampaign(id: String): Result<ShelterCampaign> = runCatching {
        if (id.isBlank()) failM11("SHELTER_CAMPAIGN_NOT_FOUND")
        val campaign = store.campaigns.value.find { it.id == id }
            ?: failM11("SHELTER_CAMPAIGN_NOT_FOUND")
        val actor = actorUserId()
        if (campaign.visibility == ShelterCampaignVisibility.INTERNAL) {
            if (actor == null || !canReadCampaignFor(actor, campaign)) {
                failM11("SHELTER_CAMPAIGN_FORBIDDEN")
            }
        }
        campaign
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun createCampaign(input: CreateShelterCampaignInput): Result<ShelterCampaign> =
        runCatching {
            val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
            if (input.shelterProfileId.isBlank()) failM11("SHELTER_NOT_FOUND")
            val shelter = store.profiles.value.find { it.id == input.shelterProfileId }
                ?: failM11("SHELTER_NOT_FOUND")
            if (!store.canManageCampaign(actor, shelter.organizationId)) {
                failM11("SHELTER_CAMPAIGN_FORBIDDEN")
            }
            val title = input.title.trim()
            val description = input.description.trim()
            if (title.isEmpty() || description.isEmpty()) failM11("SHELTER_CAMPAIGN_INVALID_TRANSITION")
            rejectMonetaryContent(title, description)
            validateEvidenceRef(input.coverAssetRef)
            if (input.activate) requireActiveShelter(store, shelter.id)
            val now = System.currentTimeMillis()
            val status = if (input.activate) {
                ShelterCampaignStatus.ACTIVE
            } else {
                ShelterCampaignStatus.DRAFT
            }
            val row = ShelterCampaign(
                id = UUID.randomUUID().toString(),
                shelterProfileId = input.shelterProfileId,
                title = title,
                description = description,
                category = input.category,
                visibility = input.visibility,
                status = status,
                startsAt = input.startsAt,
                endsAt = input.endsAt,
                coverAssetRef = input.coverAssetRef?.trim()?.takeIf { it.isNotEmpty() },
                createdBy = actor,
                createdAt = now,
                updatedAt = now
            )
            store.campaigns.value = listOf(row) + store.campaigns.value
            store.recordAudit(M11ShelterAuditEvents.SHELTER_CAMPAIGN_CREATED, row.id)
            if (status == ShelterCampaignStatus.ACTIVE) {
                store.recordM06Hook(M11ShelterNotificationHooks.CAMPAIGN_ACTIVATED)
            }
            row
        }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun updateCampaign(input: UpdateShelterCampaignInput): Result<ShelterCampaign> =
        runCatching {
            val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
            if (input.campaignId.isBlank()) failM11("SHELTER_CAMPAIGN_NOT_FOUND")
            val current = store.campaigns.value.find { it.id == input.campaignId }
                ?: failM11("SHELTER_CAMPAIGN_NOT_FOUND")
            val shelter = store.profiles.value.find { it.id == current.shelterProfileId }
                ?: failM11("SHELTER_NOT_FOUND")
            if (!store.canManageCampaign(actor, shelter.organizationId)) {
                failM11("SHELTER_CAMPAIGN_FORBIDDEN")
            }
            val title = input.title.trim()
            val description = input.description.trim()
            if (title.isEmpty() || description.isEmpty()) failM11("SHELTER_CAMPAIGN_INVALID_TRANSITION")
            rejectMonetaryContent(title, description)
            validateEvidenceRef(input.coverAssetRef)
            val updated = current.copy(
                title = title,
                description = description,
                category = input.category,
                visibility = input.visibility,
                startsAt = input.startsAt,
                endsAt = input.endsAt,
                coverAssetRef = input.coverAssetRef?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = System.currentTimeMillis()
            )
            store.campaigns.value = store.campaigns.value.map {
                if (it.id == updated.id) updated else it
            }
            updated
        }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun changeCampaignStatus(
        campaignId: String,
        status: ShelterCampaignStatus
    ): Result<ShelterCampaign> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        val current = store.campaigns.value.find { it.id == campaignId }
            ?: failM11("SHELTER_CAMPAIGN_NOT_FOUND")
        val shelter = store.profiles.value.find { it.id == current.shelterProfileId }
            ?: failM11("SHELTER_NOT_FOUND")
        if (!store.canManageCampaign(actor, shelter.organizationId)) {
            failM11("SHELTER_CAMPAIGN_FORBIDDEN")
        }
        if (current.status == status) return@runCatching current
        when (status) {
            ShelterCampaignStatus.ACTIVE -> {
                requireActiveShelter(store, shelter.id)
                if (current.status !in setOf(
                        ShelterCampaignStatus.DRAFT,
                        ShelterCampaignStatus.PAUSED
                    )
                ) {
                    failM11("SHELTER_CAMPAIGN_INVALID_TRANSITION")
                }
            }
            ShelterCampaignStatus.PAUSED -> {
                if (current.status != ShelterCampaignStatus.ACTIVE) {
                    failM11("SHELTER_CAMPAIGN_INVALID_TRANSITION")
                }
            }
            ShelterCampaignStatus.COMPLETED -> {
                if (current.status !in setOf(
                        ShelterCampaignStatus.ACTIVE,
                        ShelterCampaignStatus.PAUSED
                    )
                ) {
                    failM11("SHELTER_CAMPAIGN_INVALID_TRANSITION")
                }
                val hasOpen = store.supplyRequests.value.any {
                    it.campaignId == campaignId && it.status.isOpen
                }
                if (hasOpen) failM11("SHELTER_CAMPAIGN_HAS_OPEN_REQUESTS")
            }
            ShelterCampaignStatus.CANCELLED -> {
                if (current.status == ShelterCampaignStatus.COMPLETED) {
                    failM11("SHELTER_CAMPAIGN_INVALID_TRANSITION")
                }
                cancelOpenSupplyRequestsForCampaign(store, campaignId)
            }
            else -> failM11("SHELTER_CAMPAIGN_INVALID_TRANSITION")
        }
        val updated = current.copy(status = status, updatedAt = System.currentTimeMillis())
        store.campaigns.value = store.campaigns.value.map { if (it.id == updated.id) updated else it }
        store.recordAudit(M11ShelterAuditEvents.SHELTER_CAMPAIGN_STATUS_CHANGED, campaignId)
        if (status == ShelterCampaignStatus.ACTIVE) {
            store.recordM06Hook(M11ShelterNotificationHooks.CAMPAIGN_ACTIVATED)
        }
        updated
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun addCampaignUpdate(
        input: AddShelterCampaignUpdateInput
    ): Result<ShelterCampaignUpdate> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        val campaign = store.campaigns.value.find { it.id == input.campaignId }
            ?: failM11("SHELTER_CAMPAIGN_NOT_FOUND")
        val shelter = store.profiles.value.find { it.id == campaign.shelterProfileId }
            ?: failM11("SHELTER_NOT_FOUND")
        if (input.visibility == ShelterCampaignVisibility.INTERNAL) {
            if (!store.canManageCampaign(actor, shelter.organizationId)) {
                failM11("SHELTER_CAMPAIGN_FORBIDDEN")
            }
        } else if (!store.canManageCampaign(actor, shelter.organizationId)) {
            failM11("SHELTER_CAMPAIGN_FORBIDDEN")
        }
        val message = input.message.trim()
        if (message.isEmpty()) failM11("SHELTER_CAMPAIGN_INVALID_TRANSITION")
        validateEvidenceRef(input.evidenceRef)
        val row = ShelterCampaignUpdate(
            id = UUID.randomUUID().toString(),
            campaignId = input.campaignId,
            authorUserId = actor,
            visibility = input.visibility,
            message = message,
            evidenceRef = input.evidenceRef?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = System.currentTimeMillis()
        )
        store.campaignUpdates.value = listOf(row) + store.campaignUpdates.value
        row
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override fun observeCampaignUpdates(campaignId: String): Flow<List<ShelterCampaignUpdate>> =
        store.campaignUpdates.map { list ->
            val actor = actorUserId()
            val campaign = store.campaigns.value.find { it.id == campaignId }
            list.filter { it.campaignId == campaignId }
                .filter { update ->
                    if (update.visibility == ShelterCampaignVisibility.PUBLIC) return@filter true
                    actor != null && campaign != null && canReadCampaignFor(actor, campaign)
                }
                .sortedByDescending { it.createdAt }
        }

    private fun canReadCampaignFor(actor: String, campaign: ShelterCampaign): Boolean {
        val shelter = store.profiles.value.find { it.id == campaign.shelterProfileId }
            ?: return false
        return when (campaign.visibility) {
            ShelterCampaignVisibility.PUBLIC ->
                campaign.status == ShelterCampaignStatus.ACTIVE ||
                    store.canReadCampaign(actor, shelter.organizationId)
            ShelterCampaignVisibility.INTERNAL ->
                store.canReadCampaign(actor, shelter.organizationId)
            else -> store.canReadCampaign(actor, shelter.organizationId)
        }
    }
}

class MockShelterSupplyRepository(
    private val actorUserId: () -> String?,
    private val store: M11ShelterMemoryStore
) : ShelterSupplyRepository {

    override fun observePublicSupplyRequests(): Flow<List<ShelterSupplyRequestPublicListing>> =
        store.supplyRequests.map { list ->
            list.filter {
                it.status != ShelterSupplyRequestStatus.DRAFT &&
                    it.status != ShelterSupplyRequestStatus.CANCELLED
            }.map { request ->
                request.toPublicListing(shelterDisplayName(store, request.shelterProfileId))
            }
        }

    override fun observeShelterSupplyRequests(shelterId: String): Flow<List<ShelterSupplyRequest>> =
        store.supplyRequests.map { list ->
            val actor = actorUserId()
            list.filter { request ->
                request.shelterProfileId == shelterId &&
                    actor != null &&
                    canReadSupplyFor(actor, request)
            }
        }

    override suspend fun getSupplyRequest(id: String): Result<ShelterSupplyRequest> = runCatching {
        if (id.isBlank()) failM11("SHELTER_SUPPLY_REQUEST_NOT_FOUND")
        val request = store.supplyRequests.value.find { it.id == id }
            ?: failM11("SHELTER_SUPPLY_REQUEST_NOT_FOUND")
        val actor = actorUserId()
        if (request.status == ShelterSupplyRequestStatus.DRAFT) {
            if (actor == null || !canManageSupplyFor(actor, request)) {
                failM11("SHELTER_SUPPLY_REQUEST_FORBIDDEN")
            }
        }
        request
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun createSupplyRequest(input: CreateSupplyRequestInput): Result<ShelterSupplyRequest> =
        runCatching {
            val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
            if (input.shelterProfileId.isBlank()) failM11("SHELTER_NOT_FOUND")
            val shelter = store.profiles.value.find { it.id == input.shelterProfileId }
                ?: failM11("SHELTER_NOT_FOUND")
            if (!store.canManageCampaign(actor, shelter.organizationId)) {
                failM11("SHELTER_SUPPLY_REQUEST_FORBIDDEN")
            }
            if (input.publishOpen) requireActiveShelter(store, shelter.id)
            validateSupplyFields(input.itemName, input.unitText, input.quantityRequested)
            rejectMonetaryContent(
                input.itemName,
                input.description,
                input.publicNotes,
                input.internalNotes
            )
            input.campaignId?.let { cid ->
                val campaign = store.campaigns.value.find { it.id == cid }
                    ?: failM11("SHELTER_CAMPAIGN_NOT_FOUND")
                if (campaign.shelterProfileId != shelter.id) {
                    failM11("SHELTER_CAMPAIGN_NOT_FOUND")
                }
            }
            val now = System.currentTimeMillis()
            val status = if (input.publishOpen) {
                ShelterSupplyRequestStatus.OPEN
            } else {
                ShelterSupplyRequestStatus.DRAFT
            }
            val row = ShelterSupplyRequest(
                id = UUID.randomUUID().toString(),
                shelterProfileId = input.shelterProfileId,
                campaignId = input.campaignId,
                category = input.category,
                itemName = input.itemName.trim(),
                description = input.description?.trim()?.takeIf { it.isNotEmpty() },
                quantityRequested = input.quantityRequested,
                unitText = input.unitText.trim(),
                priority = input.priority,
                status = status,
                expiresAt = input.expiresAt,
                publicNotes = input.publicNotes?.trim()?.takeIf { it.isNotEmpty() },
                internalNotes = input.internalNotes?.trim()?.takeIf { it.isNotEmpty() },
                createdBy = actor,
                createdAt = now,
                updatedAt = now
            )
            store.supplyRequests.value = listOf(row) + store.supplyRequests.value
            store.recordAudit(M11ShelterAuditEvents.SHELTER_SUPPLY_REQUEST_CREATED, row.id)
            if (input.priority == ShelterSupplyPriority.URGENT && input.publishOpen) {
                store.recordM06Hook(M11ShelterNotificationHooks.SUPPLY_URGENT_CREATED)
            }
            row
        }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun updateSupplyRequest(input: UpdateSupplyRequestInput): Result<ShelterSupplyRequest> =
        runCatching {
            val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
            val current = store.supplyRequests.value.find { it.id == input.requestId }
                ?: failM11("SHELTER_SUPPLY_REQUEST_NOT_FOUND")
            if (!canManageSupplyFor(actor, current)) failM11("SHELTER_SUPPLY_REQUEST_FORBIDDEN")
            if (!current.status.isOpen && current.status != ShelterSupplyRequestStatus.DRAFT) {
                failM11("SHELTER_SUPPLY_REQUEST_CLOSED")
            }
            validateSupplyFields(input.itemName, input.unitText, input.quantityRequested)
            rejectMonetaryContent(
                input.itemName,
                input.description,
                input.publicNotes,
                input.internalNotes
            )
            val committed = current.quantityCommitted
            if (input.quantityRequested < committed) failM11("SHELTER_SUPPLY_REQUEST_INVALID")
            val updated = current.copy(
                category = input.category,
                itemName = input.itemName.trim(),
                description = input.description?.trim()?.takeIf { it.isNotEmpty() },
                quantityRequested = input.quantityRequested,
                unitText = input.unitText.trim(),
                priority = input.priority,
                expiresAt = input.expiresAt,
                publicNotes = input.publicNotes?.trim()?.takeIf { it.isNotEmpty() },
                internalNotes = input.internalNotes?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = System.currentTimeMillis()
            )
            store.supplyRequests.value = store.supplyRequests.value.map {
                if (it.id == updated.id) updated else it
            }
            syncSupplyRequest(store, updated.id)
        }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun cancelSupplyRequest(requestId: String): Result<ShelterSupplyRequest> =
        runCatching {
            val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
            val current = store.supplyRequests.value.find { it.id == requestId }
                ?: failM11("SHELTER_SUPPLY_REQUEST_NOT_FOUND")
            if (!canManageSupplyFor(actor, current)) failM11("SHELTER_SUPPLY_REQUEST_FORBIDDEN")
            if (!current.status.isOpen) failM11("SHELTER_SUPPLY_REQUEST_CLOSED")
            cancelSupplyRequestInternal(store, requestId)
            store.supplyRequests.value.find { it.id == requestId }
                ?: failM11("SHELTER_SUPPLY_REQUEST_NOT_FOUND")
        }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun pledgeContribution(
        input: PledgeSupplyContributionInput
    ): Result<ShelterSupplyContribution> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        val request = store.supplyRequests.value.find { it.id == input.requestId }
            ?: failM11("SHELTER_SUPPLY_REQUEST_NOT_FOUND")
        if (request.status == ShelterSupplyRequestStatus.EXPIRED) {
            failM11("SHELTER_SUPPLY_REQUEST_EXPIRED")
        }
        if (!request.status.isOpen || request.status == ShelterSupplyRequestStatus.DRAFT) {
            failM11("SHELTER_SUPPLY_REQUEST_CLOSED")
        }
        if (input.quantityCommitted <= 0) failM11("SHELTER_CONTRIBUTION_INVALID")
        validateEvidenceRef(input.evidenceRef)
        rejectMonetaryContent(input.contributorNotes)
        val remaining = request.quantityRequested - request.quantityCommitted
        if (input.quantityCommitted > remaining) failM11("SHELTER_CONTRIBUTION_EXCEEDS_REMAINING")
        val now = System.currentTimeMillis()
        val row = ShelterSupplyContribution(
            id = UUID.randomUUID().toString(),
            requestId = input.requestId,
            contributorUserId = actor,
            quantityCommitted = input.quantityCommitted,
            status = ShelterSupplyContributionStatus.PLEDGED,
            contributorNotes = input.contributorNotes?.trim()?.takeIf { it.isNotEmpty() },
            evidenceRef = input.evidenceRef?.trim()?.takeIf { it.isNotEmpty() },
            committedAt = now
        )
        store.supplyContributions.value = listOf(row) + store.supplyContributions.value
        syncSupplyRequest(store, input.requestId)
        store.recordAudit(M11ShelterAuditEvents.SHELTER_SUPPLY_CONTRIBUTION_PLEDGED, row.id)
        store.recordM06Hook(M11ShelterNotificationHooks.CONTRIBUTION_PLEDGED)
        row
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun cancelContribution(contributionId: String): Result<ShelterSupplyContribution> =
        runCatching {
            val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
            val current = store.supplyContributions.value.find { it.id == contributionId }
                ?: failM11("SHELTER_CONTRIBUTION_NOT_FOUND")
            if (current.contributorUserId != actor) failM11("SHELTER_CONTRIBUTION_FORBIDDEN")
            if (current.quantityReceived > 0) failM11("SHELTER_CONTRIBUTION_ALREADY_RECEIVED")
            if (current.status == ShelterSupplyContributionStatus.CANCELLED) return@runCatching current
            if (current.status !in setOf(
                    ShelterSupplyContributionStatus.PLEDGED,
                    ShelterSupplyContributionStatus.CONFIRMED
                )
            ) {
                failM11("SHELTER_CONTRIBUTION_INVALID")
            }
            val now = System.currentTimeMillis()
            val updated = current.copy(
                status = ShelterSupplyContributionStatus.CANCELLED,
                cancelledAt = now
            )
            store.supplyContributions.value = store.supplyContributions.value.map {
                if (it.id == updated.id) updated else it
            }
            syncSupplyRequest(store, current.requestId)
            updated
        }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun confirmContribution(contributionId: String): Result<ShelterSupplyContribution> =
        runCatching {
            val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
            val current = store.supplyContributions.value.find { it.id == contributionId }
                ?: failM11("SHELTER_CONTRIBUTION_NOT_FOUND")
            val request = store.supplyRequests.value.find { it.id == current.requestId }
                ?: failM11("SHELTER_SUPPLY_REQUEST_NOT_FOUND")
            if (!canManageSupplyFor(actor, request)) failM11("SHELTER_CONTRIBUTION_FORBIDDEN")
            if (current.status == ShelterSupplyContributionStatus.CONFIRMED) return@runCatching current
            if (current.status != ShelterSupplyContributionStatus.PLEDGED) {
                failM11("SHELTER_CONTRIBUTION_INVALID")
            }
            val updated = current.copy(status = ShelterSupplyContributionStatus.CONFIRMED)
            store.supplyContributions.value = store.supplyContributions.value.map {
                if (it.id == updated.id) updated else it
            }
            updated
        }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun recordReceipt(
        contributionId: String,
        quantityReceived: Int,
        evidenceRef: String?,
        internalReceiptNotes: String?
    ): Result<ShelterSupplyContribution> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        val current = store.supplyContributions.value.find { it.id == contributionId }
            ?: failM11("SHELTER_CONTRIBUTION_NOT_FOUND")
        val request = store.supplyRequests.value.find { it.id == current.requestId }
            ?: failM11("SHELTER_SUPPLY_REQUEST_NOT_FOUND")
        if (!canManageSupplyFor(actor, request)) failM11("SHELTER_CONTRIBUTION_FORBIDDEN")
        if (current.status in setOf(
                ShelterSupplyContributionStatus.CANCELLED,
                ShelterSupplyContributionStatus.REJECTED
            )
        ) {
            failM11("SHELTER_CONTRIBUTION_INVALID")
        }
        if (quantityReceived <= 0) failM11("SHELTER_CONTRIBUTION_INVALID")
        val newReceived = current.quantityReceived + quantityReceived
        if (newReceived > current.quantityCommitted) failM11("SHELTER_CONTRIBUTION_INVALID")
        validateEvidenceRef(evidenceRef)
        val now = System.currentTimeMillis()
        val newStatus = when {
            newReceived >= current.quantityCommitted -> ShelterSupplyContributionStatus.RECEIVED
            newReceived > 0 -> ShelterSupplyContributionStatus.PARTIALLY_RECEIVED
            else -> current.status
        }
        val updated = current.copy(
            quantityReceived = newReceived,
            status = newStatus,
            evidenceRef = evidenceRef?.trim()?.takeIf { it.isNotEmpty() } ?: current.evidenceRef,
            internalReceiptNotes = internalReceiptNotes?.trim()?.takeIf { it.isNotEmpty() }
                ?: current.internalReceiptNotes,
            receivedAt = now
        )
        store.supplyContributions.value = store.supplyContributions.value.map {
            if (it.id == updated.id) updated else it
        }
        syncSupplyRequest(store, current.requestId)
        store.recordAudit(M11ShelterAuditEvents.SHELTER_SUPPLY_CONTRIBUTION_RECEIVED, contributionId)
        store.recordM06Hook(M11ShelterNotificationHooks.CONTRIBUTION_RECEIVED)
        updated
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override fun observeContributions(requestId: String): Flow<List<ShelterSupplyContribution>> =
        store.supplyContributions.map { list ->
            val actor = actorUserId() ?: return@map emptyList()
            val request = store.supplyRequests.value.find { it.id == requestId } ?: return@map emptyList()
            val isManager = canManageSupplyFor(actor, request)
            list.filter { it.requestId == requestId }
                .filter { isManager || it.contributorUserId == actor }
                .sortedByDescending { it.committedAt }
        }

    private fun validateSupplyFields(itemName: String, unitText: String, quantityRequested: Int) {
        if (itemName.trim().isEmpty() || unitText.trim().isEmpty()) {
            failM11("SHELTER_SUPPLY_REQUEST_INVALID")
        }
        if (quantityRequested <= 0) failM11("SHELTER_SUPPLY_REQUEST_INVALID")
    }

    private fun canManageSupplyFor(actor: String, request: ShelterSupplyRequest): Boolean {
        val shelter = store.profiles.value.find { it.id == request.shelterProfileId }
            ?: return false
        return store.canManageCampaign(actor, shelter.organizationId)
    }

    private fun canReadSupplyFor(actor: String, request: ShelterSupplyRequest): Boolean {
        val shelter = store.profiles.value.find { it.id == request.shelterProfileId }
            ?: return false
        return store.canReadCampaign(actor, shelter.organizationId)
    }
}

private fun M11ShelterMemoryStore.canManage(actor: String, orgId: String): Boolean =
    orgManagers.value[orgId]?.contains(actor) == true

private fun M11ShelterMemoryStore.canView(actor: String, orgId: String): Boolean =
    canManage(actor, orgId) || orgViewers.value[orgId]?.contains(actor) == true
