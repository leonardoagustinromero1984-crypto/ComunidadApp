package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.FosterContributionStatus
import com.comunidapp.app.data.model.FosterEvolutionEntry
import com.comunidapp.app.data.model.FosterEvolutionVisibility
import com.comunidapp.app.data.model.FosterExpense
import com.comunidapp.app.data.model.FosterExpenseCategory
import com.comunidapp.app.data.model.FosterHealthStatus
import com.comunidapp.app.data.model.FosterHelpContribution
import com.comunidapp.app.data.model.FosterHelpRequest
import com.comunidapp.app.data.model.FosterHelpStatus
import com.comunidapp.app.data.model.FosterHelpType
import com.comunidapp.app.data.model.FosterPlacementStatus
import com.comunidapp.app.data.model.FosterUrgency
import com.comunidapp.app.data.remote.supabase.m10.M10FosterErrorMapper
import com.comunidapp.app.data.remote.supabase.m10.M10FosterException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Same policy as M09 docs: reject public leover URLs; allow m05:// and file_asset:. */
object FosterSecureRefValidator {
    private val allowedCurrencies = setOf("ARS", "USD", "EUR", "UYU", "BRL", "CLP")

    fun isUnsafePublicReference(ref: String?): Boolean {
        if (ref.isNullOrBlank()) return false
        val lower = ref.trim().lowercase()
        if (lower.startsWith("m05://") || lower.startsWith("file_asset:")) return false
        if (lower.contains("/object/public/leover")) return true
        if (lower.contains("bucket/leover") || lower.contains("bucket=leover")) return true
        if ((lower.startsWith("http://") || lower.startsWith("https://")) &&
            lower.contains("/leover/")
        ) {
            return true
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) return true
        return false
    }

    fun isValidCurrency(code: String): Boolean =
        code.trim().uppercase() in allowedCurrencies
}

interface FosterExpenseRepository {
    fun observeExpenses(placementId: String): Flow<List<FosterExpense>>
    suspend fun addExpense(
        placementId: String,
        category: FosterExpenseCategory,
        description: String,
        amountMinor: Long,
        currency: String,
        occurredAt: Long,
        receiptRef: String? = null
    ): Result<FosterExpense>
}

interface FosterEvolutionRepository {
    fun observeEvolution(placementId: String): Flow<List<FosterEvolutionEntry>>
    suspend fun addEvolution(
        placementId: String,
        title: String,
        description: String,
        healthStatus: FosterHealthStatus,
        weightGrams: Int? = null,
        occurredAt: Long,
        mediaRefs: List<String> = emptyList(),
        visibility: FosterEvolutionVisibility = FosterEvolutionVisibility.PARTICIPANTS
    ): Result<FosterEvolutionEntry>
}

interface FosterHelpRepository {
    fun observeHelpRequests(placementId: String): Flow<List<FosterHelpRequest>>
    fun observeContributions(helpRequestId: String): Flow<List<FosterHelpContribution>>
    suspend fun getHelpRequest(id: String): Result<FosterHelpRequest>
    suspend fun createHelpRequest(
        placementId: String,
        type: FosterHelpType,
        title: String,
        description: String,
        targetAmountMinor: Long? = null,
        currency: String? = null,
        quantityNeeded: Int? = null,
        urgency: FosterUrgency = FosterUrgency.NORMAL
    ): Result<FosterHelpRequest>
    suspend fun changeHelpRequestStatus(
        helpRequestId: String,
        status: FosterHelpStatus
    ): Result<FosterHelpRequest>
    suspend fun recordContribution(
        helpRequestId: String,
        description: String,
        amountMinor: Long? = null,
        quantity: Int? = null,
        status: FosterContributionStatus = FosterContributionStatus.RECEIVED
    ): Result<FosterHelpContribution>
}

private fun failCare(code: String): Nothing =
    throw M10FosterException(code, M10FosterErrorMapper.userMessage(code))

private fun M10FosterMemoryStore.canAccessPlacement(actor: String, placementId: String): Boolean {
    val p = placements.value.find { it.id == placementId } ?: return false
    val home = homes.value.find { it.id == p.fosterHomeId }
    val principal = petPrincipal.value[p.petId]
    return actor == p.fosterUserId ||
        actor == p.requesterUserId ||
        actor == principal ||
        actor == home?.ownerUserId
}

private fun M10FosterMemoryStore.requireActivePlacement(placementId: String) =
    placements.value.find { it.id == placementId }?.also {
        if (it.status != FosterPlacementStatus.ACTIVE) failCare("FOSTER_PLACEMENT_NOT_ACTIVE")
    } ?: failCare("FOSTER_PLACEMENT_NOT_FOUND")

class MockFosterExpenseRepository(
    private val actorUserId: () -> String?,
    private val store: M10FosterMemoryStore
) : FosterExpenseRepository {

    override fun observeExpenses(placementId: String): Flow<List<FosterExpense>> =
        store.expenses.map { list -> list.filter { it.placementId == placementId } }

    override suspend fun addExpense(
        placementId: String,
        category: FosterExpenseCategory,
        description: String,
        amountMinor: Long,
        currency: String,
        occurredAt: Long,
        receiptRef: String?
    ): Result<FosterExpense> = runCatching {
        val actor = actorUserId() ?: failCare("NOT_AUTHENTICATED")
        if (placementId.isBlank()) failCare("FOSTER_PLACEMENT_NOT_FOUND")
        if (amountMinor <= 0) failCare("FOSTER_EXPENSE_INVALID_AMOUNT")
        if (!FosterSecureRefValidator.isValidCurrency(currency)) failCare("FOSTER_EXPENSE_INVALID_AMOUNT")
        if (FosterSecureRefValidator.isUnsafePublicReference(receiptRef)) {
            failCare("FOSTER_EVOLUTION_INVALID_MEDIA_REF")
        }
        val placement = store.requireActivePlacement(placementId)
        if (actor != placement.fosterUserId) failCare("FOSTER_EXPENSE_FORBIDDEN")
        val now = System.currentTimeMillis()
        val row = FosterExpense(
            id = UUID.randomUUID().toString(),
            placementId = placementId,
            category = category,
            description = description.trim(),
            amountMinor = amountMinor,
            currency = currency.trim().uppercase(),
            occurredAt = occurredAt,
            receiptRef = receiptRef?.trim()?.takeIf { it.isNotEmpty() },
            createdBy = actor,
            createdAt = now
        )
        store.expenses.value = listOf(row) + store.expenses.value
        row
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })
}

class MockFosterEvolutionRepository(
    private val actorUserId: () -> String?,
    private val store: M10FosterMemoryStore
) : FosterEvolutionRepository {

    override fun observeEvolution(placementId: String): Flow<List<FosterEvolutionEntry>> =
        store.evolution.map { list ->
            list.filter { it.placementId == placementId }.sortedByDescending { it.occurredAt }
        }

    override suspend fun addEvolution(
        placementId: String,
        title: String,
        description: String,
        healthStatus: FosterHealthStatus,
        weightGrams: Int?,
        occurredAt: Long,
        mediaRefs: List<String>,
        visibility: FosterEvolutionVisibility
    ): Result<FosterEvolutionEntry> = runCatching {
        val actor = actorUserId() ?: failCare("NOT_AUTHENTICATED")
        if (placementId.isBlank()) failCare("FOSTER_PLACEMENT_NOT_FOUND")
        val placement = store.requireActivePlacement(placementId)
        if (actor != placement.fosterUserId) failCare("FOSTER_EVOLUTION_FORBIDDEN")
        if (mediaRefs.any { FosterSecureRefValidator.isUnsafePublicReference(it) }) {
            failCare("FOSTER_EVOLUTION_INVALID_MEDIA_REF")
        }
        val publicSafeDescription = if (visibility == FosterEvolutionVisibility.PUBLIC) {
            description.trim().take(280)
        } else {
            description.trim()
        }
        val row = FosterEvolutionEntry(
            id = UUID.randomUUID().toString(),
            placementId = placementId,
            title = title.trim(),
            description = publicSafeDescription,
            healthStatus = healthStatus,
            weightGrams = weightGrams,
            occurredAt = occurredAt,
            mediaRefs = mediaRefs,
            visibility = visibility,
            createdBy = actor,
            createdAt = System.currentTimeMillis()
        )
        store.evolution.value = listOf(row) + store.evolution.value
        row
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })
}

class MockFosterHelpRepository(
    private val actorUserId: () -> String?,
    private val store: M10FosterMemoryStore
) : FosterHelpRepository {

    override fun observeHelpRequests(placementId: String): Flow<List<FosterHelpRequest>> =
        store.helpRequests.map { list -> list.filter { it.placementId == placementId } }

    override fun observeContributions(helpRequestId: String): Flow<List<FosterHelpContribution>> =
        store.contributions.map { list -> list.filter { it.helpRequestId == helpRequestId } }

    override suspend fun getHelpRequest(id: String): Result<FosterHelpRequest> = runCatching {
        if (id.isBlank()) failCare("FOSTER_HELP_REQUEST_NOT_FOUND")
        store.helpRequests.value.find { it.id == id } ?: failCare("FOSTER_HELP_REQUEST_NOT_FOUND")
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })

    override suspend fun createHelpRequest(
        placementId: String,
        type: FosterHelpType,
        title: String,
        description: String,
        targetAmountMinor: Long?,
        currency: String?,
        quantityNeeded: Int?,
        urgency: FosterUrgency
    ): Result<FosterHelpRequest> = runCatching {
        val actor = actorUserId() ?: failCare("NOT_AUTHENTICATED")
        val placement = store.requireActivePlacement(placementId)
        if (!store.canAccessPlacement(actor, placementId)) failCare("FOSTER_HELP_REQUEST_FORBIDDEN")
        // MONEY is a need signal only — no bank data accepted in description
        val desc = description.trim()
        if (desc.contains("CBU", ignoreCase = true) ||
            desc.contains("alias bancario", ignoreCase = true) ||
            desc.contains("tarjeta", ignoreCase = true)
        ) {
            failCare("FOSTER_CONTRIBUTION_INVALID")
        }
        if (type == FosterHelpType.MONEY) {
            if (targetAmountMinor != null && targetAmountMinor <= 0) failCare("FOSTER_CONTRIBUTION_INVALID")
            if (currency != null && !FosterSecureRefValidator.isValidCurrency(currency)) {
                failCare("FOSTER_CONTRIBUTION_INVALID")
            }
        }
        val row = FosterHelpRequest(
            id = UUID.randomUUID().toString(),
            placementId = placementId,
            type = type,
            title = title.trim(),
            description = desc,
            targetAmountMinor = targetAmountMinor,
            currency = currency?.trim()?.uppercase(),
            quantityNeeded = quantityNeeded,
            status = FosterHelpStatus.OPEN,
            urgency = urgency,
            createdBy = actor,
            createdAt = System.currentTimeMillis()
        )
        store.helpRequests.value = listOf(row) + store.helpRequests.value
        row
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })

    override suspend fun changeHelpRequestStatus(
        helpRequestId: String,
        status: FosterHelpStatus
    ): Result<FosterHelpRequest> = runCatching {
        val actor = actorUserId() ?: failCare("NOT_AUTHENTICATED")
        val hr = store.helpRequests.value.find { it.id == helpRequestId }
            ?: failCare("FOSTER_HELP_REQUEST_NOT_FOUND")
        if (hr.status == status) return@runCatching hr
        if (!hr.status.isEditable) failCare("FOSTER_HELP_REQUEST_NOT_EDITABLE")
        if (!store.canAccessPlacement(actor, hr.placementId)) failCare("FOSTER_HELP_REQUEST_FORBIDDEN")
        val updated = hr.copy(
            status = status,
            closedAt = if (status == FosterHelpStatus.FULFILLED || status == FosterHelpStatus.CANCELLED) {
                System.currentTimeMillis()
            } else null
        )
        store.helpRequests.value = store.helpRequests.value.map {
            if (it.id == hr.id) updated else it
        }
        updated
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })

    override suspend fun recordContribution(
        helpRequestId: String,
        description: String,
        amountMinor: Long?,
        quantity: Int?,
        status: FosterContributionStatus
    ): Result<FosterHelpContribution> = runCatching {
        val actor = actorUserId() ?: failCare("NOT_AUTHENTICATED")
        val hr = store.helpRequests.value.find { it.id == helpRequestId }
            ?: failCare("FOSTER_HELP_REQUEST_NOT_FOUND")
        if (!hr.status.isEditable) failCare("FOSTER_HELP_REQUEST_NOT_EDITABLE")
        if (!store.canAccessPlacement(actor, hr.placementId)) failCare("FOSTER_HELP_REQUEST_FORBIDDEN")
        if (amountMinor != null && amountMinor <= 0) failCare("FOSTER_CONTRIBUTION_INVALID")
        if (quantity != null && quantity <= 0) failCare("FOSTER_CONTRIBUTION_INVALID")
        val contrib = FosterHelpContribution(
            id = UUID.randomUUID().toString(),
            helpRequestId = helpRequestId,
            contributorUserId = actor,
            description = description.trim(),
            amountMinor = amountMinor,
            quantity = quantity,
            status = status,
            recordedAt = System.currentTimeMillis()
        )
        store.contributions.value = listOf(contrib) + store.contributions.value
        if (status == FosterContributionStatus.RECEIVED) {
            var updated = hr.copy(
                receivedAmountMinor = hr.receivedAmountMinor + (amountMinor ?: 0),
                receivedQuantity = hr.receivedQuantity + (quantity ?: 0)
            )
            val moneyDone = hr.type == FosterHelpType.MONEY &&
                hr.targetAmountMinor != null &&
                updated.receivedAmountMinor >= hr.targetAmountMinor
            val qtyDone = hr.quantityNeeded != null &&
                updated.receivedQuantity >= hr.quantityNeeded
            if (moneyDone || qtyDone) {
                updated = updated.copy(
                    status = FosterHelpStatus.FULFILLED,
                    closedAt = System.currentTimeMillis()
                )
            }
            store.helpRequests.value = store.helpRequests.value.map {
                if (it.id == hr.id) updated else it
            }
        }
        contrib
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })
}
