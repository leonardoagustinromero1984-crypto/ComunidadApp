package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M15DischargeOutcome
import com.comunidapp.app.data.model.M15EvolutionEventType
import com.comunidapp.app.data.model.M15ExpenseCategory
import com.comunidapp.app.data.model.M15ExpenseStatus
import com.comunidapp.app.data.model.M15FosterHomeStatus
import com.comunidapp.app.data.model.M15FosterPlacementStatus
import com.comunidapp.app.data.model.M15FosterRequestStatus
import com.comunidapp.app.data.model.M15HelpPriority
import com.comunidapp.app.data.model.M15HelpRequestStatus
import com.comunidapp.app.data.model.M15HelpRequestType
import com.comunidapp.app.data.model.M15MetricsPolicy
import com.comunidapp.app.data.model.M15OperationalMetrics
import com.comunidapp.app.data.model.M15OperationalMetricsQuery
import com.comunidapp.app.data.remote.supabase.m15.M15ErrorMapper
import com.comunidapp.app.data.remote.supabase.m15.M15Exception
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow

/**
 * LeoVer M15 Bloque 4 — operaciones: métricas agregadas sin PII, hooks M06, calidad operativa.
 * Sin SQL nuevo; remoto = M15_REMOTE_VALIDATION_PENDING.
 */
interface M15OperationsRepository {
    fun observePreparedM06Hooks(): Flow<List<Pair<String, String>>>
    suspend fun getOperationalMetrics(query: M15OperationalMetricsQuery): Result<M15OperationalMetrics>
    fun m06InfrastructureStatus(useSupabase: Boolean): String
}

private val conflictCountByStore = mutableMapOf<M15MemoryStore, AtomicInteger>()
private val idempotentCountByStore = mutableMapOf<M15MemoryStore, AtomicInteger>()
private val remoteFallbackCountByStore = mutableMapOf<M15MemoryStore, AtomicInteger>()
private val errorsByStore = mutableMapOf<M15MemoryStore, MutableMap<String, AtomicInteger>>()

fun M15MemoryStore.recordConflict() {
    conflictCountByStore.getOrPut(this) { AtomicInteger(0) }.incrementAndGet()
}

fun M15MemoryStore.recordIdempotentRetry() {
    idempotentCountByStore.getOrPut(this) { AtomicInteger(0) }.incrementAndGet()
}

fun M15MemoryStore.recordRemoteFallback() {
    remoteFallbackCountByStore.getOrPut(this) { AtomicInteger(0) }.incrementAndGet()
}

fun M15MemoryStore.recordOperationalError(code: String) {
    val bucket = errorsByStore.getOrPut(this) { mutableMapOf() }
    bucket.getOrPut(code) { AtomicInteger(0) }.incrementAndGet()
}

fun M15MemoryStore.conflictCount(): Int = conflictCountByStore[this]?.get() ?: 0

fun M15MemoryStore.idempotentRetryCount(): Int = idempotentCountByStore[this]?.get() ?: 0

fun M15MemoryStore.remoteFallbackCount(): Int = remoteFallbackCountByStore[this]?.get() ?: 0

fun M15MemoryStore.errorsByCodeSnapshot(): Map<String, Int> =
    errorsByStore[this]?.mapValues { it.value.get() }?.toSortedMap() ?: emptyMap()

internal fun <T> resultFailM15Block4(code: String): Result<T> =
    Result.failure(M15Exception(code, M15ErrorMapper.userMessage(code)))

class MockM15OperationsRepository(
    private val store: M15MemoryStore,
    private val actorUserId: () -> String?,
    private val canViewMetrics: () -> Boolean = { true }
) : M15OperationsRepository {

    override fun observePreparedM06Hooks(): Flow<List<Pair<String, String>>> =
        store.m06PreparedHooks

    override fun m06InfrastructureStatus(useSupabase: Boolean): String =
        M15M06NotificationBridge.infrastructureStatus(useSupabase, store)

    override suspend fun getOperationalMetrics(
        query: M15OperationalMetricsQuery
    ): Result<M15OperationalMetrics> {
        actorUserId() ?: return resultFailM15Block4("NOT_AUTHENTICATED")
        if (!canViewMetrics()) return resultFailM15Block4("M15_UNAUTHORIZED")
        query.validate()?.let { return resultFailM15Block4(it) }
        ZoneId.of(query.zoneIdName)

        val from = query.fromInclusive
        val to = query.toExclusive

        val homes = store.homes.value.filter { it.updatedAt in from until to }
        val requests = store.requests.value.filter { it.createdAt in from until to }
        val placements = store.placements.value.filter { it.startedAt in from until to }
        val evolutions = store.evolution.value.filter { it.createdAt in from until to }
        val expenses = store.expenses.value.filter { it.createdAt in from until to }
        val help = store.helpRequests.value.filter { it.createdAt in from until to }

        val homesByStatus = M15FosterHomeStatus.entries.associate { st ->
            st.name to homes.count { it.status == st }
        }
        val homesByAvailability = com.comunidapp.app.data.model.M15FosterAvailabilityStatus.entries
            .associate { st -> st.name to homes.count { it.availabilityStatus == st } }

        val totalCapacity = homes.sumOf { it.totalCapacity.coerceAtLeast(0) }
        val occupied = homes.sumOf { it.currentOccupancy.coerceAtLeast(0) }
        val reserved = homes.sumOf { it.reservedCount.coerceAtLeast(0) }
        val available = (totalCapacity - occupied - reserved).coerceAtLeast(0)

        val requestsByStatus = M15FosterRequestStatus.entries.associate { st ->
            st.name to requests.count { it.status == st }
        }

        val resolutionMinutes = requests.mapNotNull { r ->
            val reviewed = r.reviewedAt ?: return@mapNotNull null
            if (reviewed < from || reviewed >= to) return@mapNotNull null
            (reviewed - r.createdAt).coerceAtLeast(0L).toDouble() / 60000.0
        }

        val placementsByStatus = M15FosterPlacementStatus.entries.associate { st ->
            st.name to placements.count { it.status == st }
        }
        val interrupted = placements.count {
            it.dischargeOutcome == M15DischargeOutcome.INTERRUPTED
        }
        val completed = placements.count {
            it.status == M15FosterPlacementStatus.COMPLETED &&
                it.dischargeOutcome != M15DischargeOutcome.INTERRUPTED
        }

        val dischargesByReason = placements.mapNotNull { it.dischargeReason?.name }
            .groupingBy { it }.eachCount().toSortedMap()
        val dischargesByOutcome = placements.mapNotNull { it.dischargeOutcome?.name }
            .groupingBy { it }.eachCount().toSortedMap()

        val evolutionByType = M15EvolutionEventType.entries.associate { t ->
            t.name to evolutions.count { it.eventType == t }
        }
        val healthAlerts = evolutions.count { it.healthAlert }
        val incidents = evolutions.count { it.eventType == M15EvolutionEventType.INCIDENT }

        val expensesByStatus = M15ExpenseStatus.entries.associate { st ->
            st.name to expenses.count { it.status == st }
        }
        val expensesByCategory = M15ExpenseCategory.entries.associate { c ->
            c.name to expenses.count { it.category == c }
        }
        val sumByCurrency = expenses.groupBy { it.currency.uppercase() }
            .mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }
            .toSortedMap()

        val helpByType = M15HelpRequestType.entries.associate { t ->
            t.name to help.count { it.type == t }
        }
        val helpByStatus = M15HelpRequestStatus.entries.associate { st ->
            st.name to help.count { it.status == st }
        }
        val helpByPriority = M15HelpPriority.entries.associate { p ->
            p.name to help.count { it.priority == p }
        }

        return Result.success(
            M15OperationalMetrics(
                fromInclusive = from,
                toExclusive = to,
                zoneIdName = query.zoneIdName,
                homesByStatus = homesByStatus,
                homesByAvailability = homesByAvailability,
                totalCapacity = totalCapacity,
                occupiedSlots = occupied,
                reservedSlots = reserved,
                availableSlots = available,
                requestsByStatus = requestsByStatus,
                requestsSubmitted = requestsByStatus[M15FosterRequestStatus.SUBMITTED.name] ?: 0,
                requestsAccepted = requestsByStatus[M15FosterRequestStatus.ACCEPTED.name] ?: 0,
                requestsRejected = requestsByStatus[M15FosterRequestStatus.REJECTED.name] ?: 0,
                requestsCancelled = requestsByStatus[M15FosterRequestStatus.CANCELLED.name] ?: 0,
                requestsExpired = requestsByStatus[M15FosterRequestStatus.EXPIRED.name] ?: 0,
                avgMinutesToResolution = resolutionMinutes.takeIf { it.isNotEmpty() }?.average(),
                placementsByStatus = placementsByStatus,
                placementsReserved = placementsByStatus[M15FosterPlacementStatus.RESERVED.name] ?: 0,
                placementsActive = placementsByStatus[M15FosterPlacementStatus.ACTIVE.name] ?: 0,
                placementsCompleted = completed,
                placementsInterrupted = interrupted,
                placementsCancelled = placementsByStatus[M15FosterPlacementStatus.CANCELLED.name] ?: 0,
                dischargesByReason = dischargesByReason,
                dischargesByOutcome = dischargesByOutcome,
                evolutionByType = evolutionByType,
                evolutionHealthAlerts = healthAlerts,
                evolutionIncidents = incidents,
                expensesByStatus = expensesByStatus,
                expensesByCategory = expensesByCategory,
                expenseSumByCurrency = sumByCurrency,
                helpByType = helpByType,
                helpByStatus = helpByStatus,
                helpByPriority = helpByPriority,
                helpOpen = helpByStatus[M15HelpRequestStatus.OPEN.name] ?: 0,
                helpInProgress = helpByStatus[M15HelpRequestStatus.IN_PROGRESS.name] ?: 0,
                helpResolved = helpByStatus[M15HelpRequestStatus.RESOLVED.name] ?: 0,
                helpCancelled = helpByStatus[M15HelpRequestStatus.CANCELLED.name] ?: 0,
                helpExpired = helpByStatus[M15HelpRequestStatus.EXPIRED.name] ?: 0,
                conflicts = store.conflictCount(),
                idempotentRetries = store.idempotentRetryCount(),
                remoteFallbacks = store.remoteFallbackCount(),
                errorsByCode = store.errorsByCodeSnapshot()
            )
        )
    }
}

class SupabaseM15OperationsRepository : M15OperationsRepository {
    override fun observePreparedM06Hooks(): Flow<List<Pair<String, String>>> =
        kotlinx.coroutines.flow.MutableStateFlow(emptyList())

    override fun m06InfrastructureStatus(useSupabase: Boolean): String =
        M15M06NotificationBridge.REMOTE_VALIDATION_PENDING

    override suspend fun getOperationalMetrics(
        query: M15OperationalMetricsQuery
    ): Result<M15OperationalMetrics> {
        query.validate()?.let { return resultFailM15Block4(it) }
        return resultFailM15Block4("M15_REMOTE_VALIDATION_PENDING")
    }
}
