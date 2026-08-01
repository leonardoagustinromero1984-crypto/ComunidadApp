package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AddM15EvolutionInput
import com.comunidapp.app.data.model.AddM15ExpenseInput
import com.comunidapp.app.data.model.AddM15HelpRequestInput
import com.comunidapp.app.data.model.M15AuditEvents
import com.comunidapp.app.data.model.M15DischargeInput
import com.comunidapp.app.data.model.M15DischargeOutcome
import com.comunidapp.app.data.model.M15FosterPlacement
import com.comunidapp.app.data.model.M15FosterPlacementStatus
import com.comunidapp.app.data.model.M15M06Hooks
import com.comunidapp.app.data.model.M15PlacementEvolution
import com.comunidapp.app.data.model.M15PlacementExpense
import com.comunidapp.app.data.model.M15PlacementHelpRequest
import com.comunidapp.app.data.remote.supabase.m15.M15ErrorMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface M15PlacementEvolutionRepository {
    fun observeEvolution(placementId: String): Flow<List<M15PlacementEvolution>>
    suspend fun addEvolution(input: AddM15EvolutionInput): Result<M15PlacementEvolution>
}

interface M15PlacementDischargeRepository {
    suspend fun discharge(input: M15DischargeInput): Result<M15FosterPlacement>
}

interface M15PlacementExpenseRepository {
    fun observeExpenses(placementId: String): Flow<List<M15PlacementExpense>>
    suspend fun addExpense(input: AddM15ExpenseInput): Result<M15PlacementExpense>
}

interface M15PlacementHelpRepository {
    fun observeHelpRequests(placementId: String): Flow<List<M15PlacementHelpRequest>>
    suspend fun createHelpRequest(input: AddM15HelpRequestInput): Result<M15PlacementHelpRequest>
    suspend fun resolveHelpRequest(helpRequestId: String): Result<M15PlacementHelpRequest>
}

class SupabaseM15PlacementEvolutionRepository(
    private val delegate: FosterEvolutionRepository
) : M15PlacementEvolutionRepository {
    override fun observeEvolution(placementId: String): Flow<List<M15PlacementEvolution>> =
        delegate.observeEvolution(placementId).map { list -> list.map { it.toM15Evolution() } }

    override suspend fun addEvolution(input: AddM15EvolutionInput): Result<M15PlacementEvolution> {
        val foster = input.toFosterEvolution()
        return delegate.addEvolution(
            placementId = input.placementId,
            title = foster.title,
            description = foster.description,
            healthStatus = foster.healthStatus,
            weightGrams = null,
            occurredAt = foster.occurredAt,
            mediaRefs = foster.mediaRefs,
            visibility = foster.visibility
        ).mapM15Lifecycle { it.toM15Evolution() }
    }
}

class SupabaseM15PlacementDischargeRepository(
    private val delegate: FosterPlacementRepository
) : M15PlacementDischargeRepository {
    override suspend fun discharge(input: M15DischargeInput): Result<M15FosterPlacement> =
        when (input.outcome) {
            M15DischargeOutcome.CANCELLED ->
                delegate.cancelReservedPlacement(input.placementId, input.privateNote)
            M15DischargeOutcome.COMPLETED, M15DischargeOutcome.INTERRUPTED ->
                delegate.completePlacement(
                    input.placementId,
                    input.reason.toFosterEndReason(),
                    input.privateNote
                )
        }.mapM15Lifecycle { it.toM15() }
}

class SupabaseM15PlacementExpenseRepository(
    private val delegate: FosterExpenseRepository
) : M15PlacementExpenseRepository {
    override fun observeExpenses(placementId: String): Flow<List<M15PlacementExpense>> =
        delegate.observeExpenses(placementId).map { list -> list.map { it.toM15Expense() } }

    override suspend fun addExpense(input: AddM15ExpenseInput): Result<M15PlacementExpense> =
        delegate.addExpense(
            placementId = input.placementId,
            category = input.toFosterExpenseCategory(),
            description = input.description,
            amountMinor = input.amountMinor,
            currency = input.currency,
            occurredAt = input.occurredAt,
            receiptRef = input.receiptMediaRef
        ).mapM15Lifecycle { it.toM15Expense() }
}

class SupabaseM15PlacementHelpRepository(
    private val delegate: FosterHelpRepository
) : M15PlacementHelpRepository {
    override fun observeHelpRequests(placementId: String): Flow<List<M15PlacementHelpRequest>> =
        delegate.observeHelpRequests(placementId).map { list -> list.map { it.toM15HelpRequest() } }

    override suspend fun createHelpRequest(input: AddM15HelpRequestInput): Result<M15PlacementHelpRequest> =
        delegate.createHelpRequest(
            placementId = input.placementId,
            type = input.toFosterHelpType(),
            title = input.title,
            description = input.description,
            urgency = input.priority.toFosterUrgency()
        ).mapM15Lifecycle { it.toM15HelpRequest() }

    override suspend fun resolveHelpRequest(helpRequestId: String): Result<M15PlacementHelpRequest> =
        delegate.changeHelpRequestStatus(
            helpRequestId,
            com.comunidapp.app.data.model.FosterHelpStatus.FULFILLED
        ).mapM15Lifecycle { it.toM15HelpRequest() }
}

private inline fun <T, R> Result<T>.mapM15Lifecycle(transform: (T) -> R): Result<R> =
    fold(
        onSuccess = { Result.success(transform(it)) },
        onFailure = { M15ErrorMapper.failure(it) }
    )
