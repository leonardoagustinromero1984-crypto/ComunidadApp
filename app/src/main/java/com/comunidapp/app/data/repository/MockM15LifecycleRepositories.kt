package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AddM15EvolutionInput
import com.comunidapp.app.data.model.AddM15ExpenseInput
import com.comunidapp.app.data.model.AddM15HelpRequestInput
import com.comunidapp.app.data.model.M15AuditEvents
import com.comunidapp.app.data.model.M15DischargeInput
import com.comunidapp.app.data.model.M15DischargeOutcome
import com.comunidapp.app.data.model.M15EvolutionEventType
import com.comunidapp.app.data.model.M15ExpenseStatus
import com.comunidapp.app.data.model.M15FosterPlacement
import com.comunidapp.app.data.model.M15FosterPlacementStatus
import com.comunidapp.app.data.model.M15HelpRequestStatus
import com.comunidapp.app.data.model.M15M06Hooks
import com.comunidapp.app.data.model.M15PlacementEvolution
import com.comunidapp.app.data.model.M15PlacementExpense
import com.comunidapp.app.data.model.M15PlacementHelpRequest
import com.comunidapp.app.data.remote.supabase.m15.M15ErrorMapper
import com.comunidapp.app.data.remote.supabase.m15.M15Exception
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** M15 Bloque 3 — mocks locales sobre M15MemoryStore. */

private fun failM15Life(code: String): Nothing =
    throw M15Exception(code, M15ErrorMapper.userMessage(code))

private fun M15MemoryStore.requireOpenPlacement(placementId: String): M15FosterPlacement {
    val p = placements.value.find { it.id == placementId } ?: failM15Life("M15_FOSTER_PLACEMENT_NOT_FOUND")
    if (p.status != M15FosterPlacementStatus.ACTIVE &&
        p.status != M15FosterPlacementStatus.RESERVED
    ) {
        failM15Life("M15_DISCHARGE_NOT_ALLOWED")
    }
    return p
}

private fun M15MemoryStore.canAccessPlacement(actor: String, placementId: String): Boolean {
    val p = placements.value.find { it.id == placementId } ?: return false
    val home = homes.value.find { it.id == p.fosterHomeId }
    val principal = petPrincipal.value[p.petId]
    return actor == p.fosterUserId ||
        actor == p.requesterUserId ||
        actor == principal ||
        actor == home?.ownerUserId
}

class MockM15PlacementEvolutionRepository(
    private val actorUserId: () -> String?,
    private val store: M15MemoryStore
) : M15PlacementEvolutionRepository {
    override fun observeEvolution(placementId: String): Flow<List<M15PlacementEvolution>> =
        store.evolution.map { list -> list.filter { it.placementId == placementId } }

    override suspend fun addEvolution(input: AddM15EvolutionInput): Result<M15PlacementEvolution> =
        store.withLock {
            runCatching {
                val actor = actorUserId() ?: failM15Life("NOT_AUTHENTICATED")
                store.requireOpenPlacement(input.placementId)
                if (!store.canAccessPlacement(actor, input.placementId)) failM15Life("M15_UNAUTHORIZED")
                if (input.summary.isBlank()) failM15Life("M15_INVALID_FOSTER_INPUT")
                input.mediaRefs.forEach { ref ->
                    if (FosterSecureRefValidator.isUnsafePublicReference(ref)) {
                        failM15Life("M15_MEDIA_REFERENCE_INVALID")
                    }
                }
                val now = System.currentTimeMillis()
                val row = M15PlacementEvolution(
                    id = store.nextId("m15_evo"),
                    placementId = input.placementId,
                    eventType = input.eventType,
                    summary = input.summary.trim(),
                    privateNote = input.privateNote?.trim()?.takeIf { it.isNotEmpty() },
                    healthAlert = input.healthAlert ||
                        input.eventType == M15EvolutionEventType.HEALTH_ALERT,
                    mediaRefs = input.mediaRefs,
                    createdBy = actor,
                    createdAt = now,
                    occurredAt = input.occurredAt
                )
                store.evolution.update { listOf(row) + it }
                store.audit(M15AuditEvents.EVOLUTION_ADDED, row.id)
                store.recordM06(M15M06Hooks.EVOLUTION_ADDED, row.id)
                row
            }.fold({ Result.success(it) }, { M15ErrorMapper.failure(it) })
        }
}

class MockM15PlacementDischargeRepository(
    private val actorUserId: () -> String?,
    private val store: M15MemoryStore
) : M15PlacementDischargeRepository {
    override suspend fun discharge(input: M15DischargeInput): Result<M15FosterPlacement> =
        store.withLock {
            runCatching {
                val actor = actorUserId() ?: failM15Life("NOT_AUTHENTICATED")
                val placement = store.placements.value.find { it.id == input.placementId }
                    ?: failM15Life("M15_FOSTER_PLACEMENT_NOT_FOUND")
                if (!store.canAccessPlacement(actor, input.placementId)) failM15Life("M15_UNAUTHORIZED")
                if (placement.dischargeOutcome != null) failM15Life("M15_DISCHARGE_ALREADY_APPLIED")
                val home = store.homes.value.find { it.id == placement.fosterHomeId }
                    ?: failM15Life("M15_FOSTER_HOME_NOT_FOUND")
                if (store.forceRevokeFailure) failM15Life("M15_TEMPORARY_CUSTODY_NOT_ALLOWED")
                val now = System.currentTimeMillis()
                val updated = when (input.outcome) {
                    M15DischargeOutcome.CANCELLED -> {
                        if (placement.status != M15FosterPlacementStatus.RESERVED) {
                            failM15Life("M15_DISCHARGE_NOT_ALLOWED")
                        }
                        placement.copy(
                            status = M15FosterPlacementStatus.CANCELLED,
                            endedAt = now,
                            dischargeReason = input.reason,
                            dischargeOutcome = input.outcome,
                            endNotes = input.privateNote,
                            endedBy = actor
                        ).also {
                            store.upsertHome(
                                home.copy(
                                    reservedCount = (home.reservedCount - 1).coerceAtLeast(0),
                                    availabilityStatus = M15Validators.recomputeAvailability(
                                        home.status, home.totalCapacity, home.currentOccupancy,
                                        (home.reservedCount - 1).coerceAtLeast(0)
                                    ),
                                    updatedAt = now
                                )
                            )
                        }
                    }
                    M15DischargeOutcome.COMPLETED, M15DischargeOutcome.INTERRUPTED -> {
                        if (placement.status != M15FosterPlacementStatus.ACTIVE) {
                            failM15Life("M15_DISCHARGE_NOT_ALLOWED")
                        }
                        store.temporaryCustody.update { grants ->
                            grants.map { g ->
                                if (g.placementId == placement.id && g.active) g.copy(active = false) else g
                            }
                        }
                        placement.copy(
                            status = M15FosterPlacementStatus.COMPLETED,
                            endedAt = now,
                            dischargeReason = input.reason,
                            dischargeOutcome = input.outcome,
                            endNotes = input.privateNote,
                            endedBy = actor
                        ).also {
                            val newOcc = (home.currentOccupancy - 1).coerceAtLeast(0)
                            store.upsertHome(
                                home.copy(
                                    currentOccupancy = newOcc,
                                    availabilityStatus = M15Validators.recomputeAvailability(
                                        home.status, home.totalCapacity, newOcc, home.reservedCount
                                    ),
                                    updatedAt = now
                                )
                            )
                            store.helpRequests.update { list ->
                                list.map { hr ->
                                    if (hr.placementId == placement.id &&
                                        (hr.status == M15HelpRequestStatus.OPEN ||
                                            hr.status == M15HelpRequestStatus.IN_PROGRESS)
                                    ) {
                                        hr.copy(status = M15HelpRequestStatus.CANCELLED, resolvedAt = now)
                                    } else hr
                                }
                            }
                        }
                    }
                }
                store.upsertPlacement(updated)
                val audit = when (input.outcome) {
                    M15DischargeOutcome.INTERRUPTED -> M15AuditEvents.PLACEMENT_INTERRUPTED
                    else -> M15AuditEvents.PLACEMENT_COMPLETED
                }
                store.audit(audit, updated.id)
                store.recordM06(
                    if (input.outcome == M15DischargeOutcome.INTERRUPTED) {
                        M15M06Hooks.PLACEMENT_INTERRUPTED
                    } else {
                        M15M06Hooks.PLACEMENT_COMPLETED
                    },
                    updated.id
                )
                store.audit(M15AuditEvents.CUSTODY_REVOKED, updated.id)
                updated
            }.fold({ Result.success(it) }, { M15ErrorMapper.failure(it) })
        }
}

class MockM15PlacementExpenseRepository(
    private val actorUserId: () -> String?,
    private val store: M15MemoryStore
) : M15PlacementExpenseRepository {
    override fun observeExpenses(placementId: String): Flow<List<M15PlacementExpense>> =
        store.expenses.map { list -> list.filter { it.placementId == placementId } }

    override suspend fun addExpense(input: AddM15ExpenseInput): Result<M15PlacementExpense> =
        store.withLock {
            runCatching {
                val actor = actorUserId() ?: failM15Life("NOT_AUTHENTICATED")
                store.requireOpenPlacement(input.placementId)
                if (!store.canAccessPlacement(actor, input.placementId)) failM15Life("M15_UNAUTHORIZED")
                if (input.amountMinor <= 0) failM15Life("M15_EXPENSE_INVALID_AMOUNT")
                if (!FosterSecureRefValidator.isValidCurrency(input.currency)) {
                    failM15Life("M15_EXPENSE_INVALID_CURRENCY")
                }
                if (FosterSecureRefValidator.isUnsafePublicReference(input.receiptMediaRef)) {
                    failM15Life("M15_MEDIA_REFERENCE_INVALID")
                }
                val now = System.currentTimeMillis()
                val row = M15PlacementExpense(
                    id = store.nextId("m15_exp"),
                    placementId = input.placementId,
                    category = input.category,
                    amountMinor = input.amountMinor,
                    currency = input.currency.trim().uppercase(),
                    occurredAt = input.occurredAt,
                    description = input.description.trim(),
                    receiptMediaRef = input.receiptMediaRef,
                    status = M15ExpenseStatus.RECORDED,
                    createdBy = actor,
                    createdAt = now
                )
                store.expenses.update { listOf(row) + it }
                store.audit(M15AuditEvents.EXPENSE_RECORDED, row.id)
                store.recordM06(M15M06Hooks.EXPENSE_RECORDED, row.id)
                row
            }.fold({ Result.success(it) }, { M15ErrorMapper.failure(it) })
        }
}

class MockM15PlacementHelpRepository(
    private val actorUserId: () -> String?,
    private val store: M15MemoryStore
) : M15PlacementHelpRepository {
    override fun observeHelpRequests(placementId: String): Flow<List<M15PlacementHelpRequest>> =
        store.helpRequests.map { list -> list.filter { it.placementId == placementId } }

    override suspend fun createHelpRequest(input: AddM15HelpRequestInput): Result<M15PlacementHelpRequest> =
        store.withLock {
            runCatching {
                val actor = actorUserId() ?: failM15Life("NOT_AUTHENTICATED")
                store.requireOpenPlacement(input.placementId)
                if (!store.canAccessPlacement(actor, input.placementId)) failM15Life("M15_UNAUTHORIZED")
                if (input.title.isBlank() || input.description.isBlank()) {
                    failM15Life("M15_INVALID_FOSTER_INPUT")
                }
                val now = System.currentTimeMillis()
                val row = M15PlacementHelpRequest(
                    id = store.nextId("m15_help"),
                    placementId = input.placementId,
                    type = input.type,
                    title = input.title.trim(),
                    description = input.description.trim(),
                    priority = input.priority,
                    status = M15HelpRequestStatus.OPEN,
                    createdBy = actor,
                    createdAt = now
                )
                store.helpRequests.update { listOf(row) + it }
                store.audit(M15AuditEvents.HELP_OPENED, row.id)
                store.recordM06(M15M06Hooks.HELP_REQUEST_OPENED, row.id)
                row
            }.fold({ Result.success(it) }, { M15ErrorMapper.failure(it) })
        }

    override suspend fun resolveHelpRequest(helpRequestId: String): Result<M15PlacementHelpRequest> =
        store.withLock {
            runCatching {
                val actor = actorUserId() ?: failM15Life("NOT_AUTHENTICATED")
                val existing = store.helpRequests.value.find { it.id == helpRequestId }
                    ?: failM15Life("M15_HELP_REQUEST_NOT_FOUND")
                if (!store.canAccessPlacement(actor, existing.placementId)) failM15Life("M15_UNAUTHORIZED")
                if (existing.status == M15HelpRequestStatus.RESOLVED ||
                    existing.status == M15HelpRequestStatus.CANCELLED
                ) {
                    failM15Life("M15_HELP_REQUEST_ALREADY_FINAL")
                }
                val now = System.currentTimeMillis()
                val updated = existing.copy(status = M15HelpRequestStatus.RESOLVED, resolvedAt = now)
                store.helpRequests.update { list ->
                    list.map { if (it.id == helpRequestId) updated else it }
                }
                store.audit(M15AuditEvents.HELP_RESOLVED, updated.id)
                store.recordM06(M15M06Hooks.HELP_REQUEST_RESOLVED, updated.id)
                updated
            }.fold({ Result.success(it) }, { M15ErrorMapper.failure(it) })
        }
}
