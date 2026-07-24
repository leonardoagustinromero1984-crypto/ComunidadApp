package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.VeterinaryAppointment
import com.comunidapp.app.data.model.VeterinaryAppointmentAuditEvents
import com.comunidapp.app.data.model.VeterinaryAppointmentM06Hooks
import com.comunidapp.app.data.model.VeterinaryAppointmentOperationalMetrics
import com.comunidapp.app.data.model.VeterinaryAppointmentSlot
import com.comunidapp.app.data.model.VeterinaryAppointmentStatus
import com.comunidapp.app.data.model.VeterinaryAppointmentStatusHistory
import com.comunidapp.app.data.model.VeterinaryAppointmentTimelineStep
import com.comunidapp.app.data.model.VeterinaryAvailabilityException
import com.comunidapp.app.data.model.VeterinaryAvailabilityExceptionType
import com.comunidapp.app.data.model.VeterinaryAvailabilityRule
import com.comunidapp.app.data.model.VeterinaryReminderDeliveryStatus
import com.comunidapp.app.data.model.VeterinaryReminderSchedule
import com.comunidapp.app.data.model.VeterinaryReminderState
import com.comunidapp.app.data.model.VeterinaryReminderType
import com.comunidapp.app.data.model.VeterinaryScheduleSettings
import com.comunidapp.app.data.remote.supabase.m12.M12VeterinaryErrorMapper
import com.comunidapp.app.data.remote.supabase.m12.M12VeterinaryException
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * LeoVer M12 Bloque 3 — agenda, disponibilidad y solicitudes de turno (fakes).
 *
 * Sin cobro, sin historia clínica. Reutiliza los patrones del Bloque 2
 * (mapas laterales sobre [M12VeterinaryMemoryStore], errores tipados
 * [M12VeterinaryException] y autoridad de organización [orgManagers]).
 */

private const val VETERINARY_APPOINTMENT_NOTE_TOO_LONG = "VETERINARY_APPOINTMENT_NOTE_TOO_LONG"
private const val MAX_REQUEST_NOTE_LENGTH = 1000
private const val DEFAULT_TIMEZONE = "America/Argentina/Buenos_Aires"

private fun failM12b3(code: String): Nothing =
    throw M12VeterinaryException(code, M12VeterinaryErrorMapper.userMessage(code))

// --- Store extensions (Bloque 3) — mapas laterales, mismo patrón que clinicProfessionalLinks ---

private val storeScheduleSettings =
    mutableMapOf<M12VeterinaryMemoryStore, MutableStateFlow<Map<String, VeterinaryScheduleSettings>>>()
private val storeAvailabilityRules =
    mutableMapOf<M12VeterinaryMemoryStore, MutableStateFlow<List<VeterinaryAvailabilityRule>>>()
private val storeAvailabilityExceptions =
    mutableMapOf<M12VeterinaryMemoryStore, MutableStateFlow<List<VeterinaryAvailabilityException>>>()
private val storeAppointments =
    mutableMapOf<M12VeterinaryMemoryStore, MutableStateFlow<List<VeterinaryAppointment>>>()
private val storeAppointmentHistory =
    mutableMapOf<M12VeterinaryMemoryStore, MutableStateFlow<List<VeterinaryAppointmentStatusHistory>>>()
private val storePetAuthorizedActors =
    mutableMapOf<M12VeterinaryMemoryStore, MutableStateFlow<Map<String, Set<String>>>>()
private val storePetOrgCustody =
    mutableMapOf<M12VeterinaryMemoryStore, MutableStateFlow<Map<String, String>>>()

// --- Store extensions (Bloque 4) — recordatorios (sin push real) e infraestructura M06 ---

private val storeReminderStates =
    mutableMapOf<M12VeterinaryMemoryStore, MutableStateFlow<Map<String, List<VeterinaryReminderState>>>>()
private val storeReminderIdempotency =
    mutableMapOf<M12VeterinaryMemoryStore, MutableStateFlow<Set<String>>>()
private val storeM06InfrastructureAvailable =
    mutableMapOf<M12VeterinaryMemoryStore, MutableStateFlow<Boolean>>()

/** clinicId → configuración de agenda persistida. */
val M12VeterinaryMemoryStore.scheduleSettings: MutableStateFlow<Map<String, VeterinaryScheduleSettings>>
    get() = storeScheduleSettings.getOrPut(this) { MutableStateFlow(emptyMap()) }

val M12VeterinaryMemoryStore.availabilityRules: MutableStateFlow<List<VeterinaryAvailabilityRule>>
    get() = storeAvailabilityRules.getOrPut(this) { MutableStateFlow(emptyList()) }

val M12VeterinaryMemoryStore.availabilityExceptions: MutableStateFlow<List<VeterinaryAvailabilityException>>
    get() = storeAvailabilityExceptions.getOrPut(this) { MutableStateFlow(emptyList()) }

val M12VeterinaryMemoryStore.appointments: MutableStateFlow<List<VeterinaryAppointment>>
    get() = storeAppointments.getOrPut(this) { MutableStateFlow(emptyList()) }

val M12VeterinaryMemoryStore.appointmentHistory: MutableStateFlow<List<VeterinaryAppointmentStatusHistory>>
    get() = storeAppointmentHistory.getOrPut(this) { MutableStateFlow(emptyList()) }

/** petId → userIds con autoridad personal (responsable/corresponsable). */
val M12VeterinaryMemoryStore.petAuthorizedActors: MutableStateFlow<Map<String, Set<String>>>
    get() = storePetAuthorizedActors.getOrPut(this) { MutableStateFlow(emptyMap()) }

/** petId → organizationId con custodia organizacional ACTIVA. */
val M12VeterinaryMemoryStore.petOrgCustody: MutableStateFlow<Map<String, String>>
    get() = storePetOrgCustody.getOrPut(this) { MutableStateFlow(emptyMap()) }

/** appointmentId → recordatorios preparados (PREPARED/FIRED_PREPARED/CANCELLED). */
val M12VeterinaryMemoryStore.reminderStates: MutableStateFlow<Map<String, List<VeterinaryReminderState>>>
    get() = storeReminderStates.getOrPut(this) { MutableStateFlow(emptyMap()) }

/** Claves de idempotencia "appointmentId|TYPE" ya programadas. */
val M12VeterinaryMemoryStore.reminderIdempotency: MutableStateFlow<Set<String>>
    get() = storeReminderIdempotency.getOrPut(this) { MutableStateFlow(emptySet()) }

/**
 * Infraestructura push M06 disponible para M12. Cuando es false, `prepareReminders`
 * sigue creando estados PREPARED pero con `infrastructureAvailable=false` (nunca hay
 * reclamo de push real en el Bloque 4).
 */
val M12VeterinaryMemoryStore.m06InfrastructureAvailable: MutableStateFlow<Boolean>
    get() = storeM06InfrastructureAvailable.getOrPut(this) { MutableStateFlow(true) }

fun M12VeterinaryMemoryStore.clearBlock3() {
    scheduleSettings.value = emptyMap()
    availabilityRules.value = emptyList()
    availabilityExceptions.value = emptyList()
    appointments.value = emptyList()
    appointmentHistory.value = emptyList()
    petAuthorizedActors.value = emptyMap()
    petOrgCustody.value = emptyMap()
    clearBlock4()
}

fun M12VeterinaryMemoryStore.clearBlock4() {
    reminderStates.value = emptyMap()
    reminderIdempotency.value = emptySet()
    m06InfrastructureAvailable.value = true
}

// --- Autoridad (misma fuente que Bloque 2: orgManagers / orgViewers) ---

private fun M12VeterinaryMemoryStore.canManageB3(actor: String, orgId: String): Boolean =
    orgManagers.value[orgId]?.contains(actor) == true

private fun M12VeterinaryMemoryStore.canViewB3(actor: String, orgId: String): Boolean =
    canManageB3(actor, orgId) || orgViewers.value[orgId]?.contains(actor) == true

// --- Inputs ---

data class CreateVeterinaryAvailabilityRuleInput(
    val clinicId: String,
    val dayOfWeek: DayOfWeek,
    val startsAt: LocalTime,
    val endsAt: LocalTime,
    val slotDurationMinutes: Int,
    val capacityPerSlot: Int,
    val professionalId: String? = null,
    val serviceId: String? = null,
    val validFrom: LocalDate? = null,
    val validUntil: LocalDate? = null
)

data class UpdateVeterinaryAvailabilityRuleInput(
    val ruleId: String,
    val dayOfWeek: DayOfWeek,
    val startsAt: LocalTime,
    val endsAt: LocalTime,
    val slotDurationMinutes: Int,
    val capacityPerSlot: Int,
    val professionalId: String? = null,
    val serviceId: String? = null,
    val validFrom: LocalDate? = null,
    val validUntil: LocalDate? = null
)

data class CreateVeterinaryAvailabilityExceptionInput(
    val clinicId: String,
    val exceptionDate: LocalDate,
    val type: VeterinaryAvailabilityExceptionType,
    val ruleId: String? = null,
    val startsAt: LocalTime? = null,
    val endsAt: LocalTime? = null,
    val capacityPerSlot: Int? = null,
    val reason: String? = null
)

data class UpdateVeterinaryAvailabilityExceptionInput(
    val exceptionId: String,
    val exceptionDate: LocalDate,
    val type: VeterinaryAvailabilityExceptionType,
    val ruleId: String? = null,
    val startsAt: LocalTime? = null,
    val endsAt: LocalTime? = null,
    val capacityPerSlot: Int? = null,
    val reason: String? = null
)

data class RequestVeterinaryAppointmentInput(
    val clinicId: String,
    val serviceId: String,
    val petId: String,
    val startsAt: Instant,
    val professionalId: String? = null,
    val requestNote: String? = null
)

data class ManagedVeterinaryAvailability(
    val rules: List<VeterinaryAvailabilityRule>,
    val exceptions: List<VeterinaryAvailabilityException>
)

/** Acciones idempotentes de transición usadas por [VeterinaryAppointmentRepository.retrySafeTransition]. */
enum class VeterinaryRetryAction { CONFIRM, REJECT, CANCEL_MY, CANCEL_MANAGED, EXPIRE }

// --- Contratos ---

interface VeterinaryScheduleRepository {
    suspend fun getSettings(clinicId: String): Result<VeterinaryScheduleSettings>
    suspend fun saveSettings(settings: VeterinaryScheduleSettings): Result<VeterinaryScheduleSettings>
    fun observeManagedAvailability(clinicId: String): Flow<ManagedVeterinaryAvailability>
    suspend fun createRule(input: CreateVeterinaryAvailabilityRuleInput): Result<VeterinaryAvailabilityRule>
    suspend fun updateRule(input: UpdateVeterinaryAvailabilityRuleInput): Result<VeterinaryAvailabilityRule>
    suspend fun changeRuleStatus(ruleId: String, active: Boolean): Result<VeterinaryAvailabilityRule>
    suspend fun createException(
        input: CreateVeterinaryAvailabilityExceptionInput
    ): Result<VeterinaryAvailabilityException>
    suspend fun updateException(
        input: UpdateVeterinaryAvailabilityExceptionInput
    ): Result<VeterinaryAvailabilityException>
    suspend fun changeExceptionStatus(
        exceptionId: String,
        active: Boolean
    ): Result<VeterinaryAvailabilityException>
    fun observeAvailableSlots(
        clinicId: String,
        serviceId: String,
        date: LocalDate,
        professionalId: String?
    ): Flow<List<VeterinaryAppointmentSlot>>
}

interface VeterinaryAppointmentRepository {
    suspend fun requestAppointment(input: RequestVeterinaryAppointmentInput): Result<VeterinaryAppointment>
    suspend fun getAppointment(appointmentId: String): Result<VeterinaryAppointment>
    fun observeMyAppointments(): Flow<List<VeterinaryAppointment>>
    fun observeManagedAppointments(clinicId: String): Flow<List<VeterinaryAppointment>>
    suspend fun confirmAppointment(appointmentId: String): Result<VeterinaryAppointment>
    suspend fun rejectAppointment(appointmentId: String, reason: String): Result<VeterinaryAppointment>
    suspend fun cancelMyAppointment(appointmentId: String, reason: String?): Result<VeterinaryAppointment>
    suspend fun cancelManagedAppointment(
        appointmentId: String,
        reason: String?
    ): Result<VeterinaryAppointment>
    suspend fun completeAppointment(appointmentId: String): Result<VeterinaryAppointment>
    suspend fun markNoShow(appointmentId: String): Result<VeterinaryAppointment>
    suspend fun expireAppointment(appointmentId: String): Result<VeterinaryAppointment>
    fun observeAppointmentHistory(appointmentId: String): Flow<List<VeterinaryAppointmentStatusHistory>>

    // --- Bloque 4 — recordatorios (sin push real), métricas y timeline ---
    suspend fun prepareRemindersForConfirmed(appointmentId: String): Result<VeterinaryReminderSchedule>
    suspend fun cancelReminders(
        appointmentId: String,
        reason: String? = null
    ): Result<VeterinaryReminderSchedule>
    suspend fun fireDueReminder(
        appointmentId: String,
        type: VeterinaryReminderType,
        now: Instant = Instant.now()
    ): Result<VeterinaryReminderState>
    fun observeReminderSchedule(appointmentId: String): Flow<VeterinaryReminderSchedule?>
    suspend fun getOperationalMetrics(
        clinicId: String,
        from: Instant,
        to: Instant,
        serviceId: String? = null,
        professionalId: String? = null
    ): Result<VeterinaryAppointmentOperationalMetrics>
    fun buildTimeline(
        appointmentId: String,
        isManager: Boolean
    ): Result<List<VeterinaryAppointmentTimelineStep>>
    suspend fun retrySafeTransition(
        appointmentId: String,
        expectedFrom: VeterinaryAppointmentStatus,
        action: VeterinaryRetryAction
    ): Result<VeterinaryAppointment>
}

/**
 * Redacta un turno según el observador: quita la nota operativa de clínica a quien no
 * gestiona y la nota de solicitud a extraños (ni solicitante ni manager).
 */
fun redactAppointmentForViewer(
    appointment: VeterinaryAppointment,
    actor: String,
    isManager: Boolean
): VeterinaryAppointment {
    if (isManager) return appointment
    val isRequester = appointment.requesterUserId == actor
    return appointment.copy(
        clinicOperationalNote = null,
        requestNote = if (isRequester) appointment.requestNote else null
    )
}

// --- Helpers de dominio ---

private fun M12VeterinaryMemoryStore.settingsFor(clinicId: String): VeterinaryScheduleSettings =
    scheduleSettings.value[clinicId] ?: VeterinaryScheduleSettings(clinicId = clinicId)

private fun zoneOf(settings: VeterinaryScheduleSettings): ZoneId =
    runCatching { ZoneId.of(settings.timezoneName) }.getOrElse { ZoneId.of(DEFAULT_TIMEZONE) }

private fun professionalMatch(a: String?, b: String?): Boolean =
    a == null || b == null || a == b

private fun timesOverlap(
    aStart: LocalTime,
    aEnd: LocalTime,
    bStart: LocalTime,
    bEnd: LocalTime
): Boolean = aStart < bEnd && bStart < aEnd

private fun instantsOverlap(
    aStart: Instant,
    aEnd: Instant,
    bStart: Instant,
    bEnd: Instant
): Boolean = aStart.isBefore(bEnd) && bStart.isBefore(aEnd)

private fun reservedCount(
    appts: List<VeterinaryAppointment>,
    clinicId: String,
    serviceId: String,
    slotProfessionalId: String?,
    startsAt: Instant,
    endsAt: Instant
): Int = appts.count { a ->
    a.clinicId == clinicId &&
        a.serviceId == serviceId &&
        a.status.consumesCapacity &&
        professionalMatch(a.professionalId, slotProfessionalId) &&
        instantsOverlap(a.startsAt, a.endsAt, startsAt, endsAt)
}

/** Proyección de slots: reglas activas del día, aplicando excepciones y descontando cupo. */
private fun computeSlots(
    clinicId: String,
    serviceId: String,
    date: LocalDate,
    professionalId: String?,
    settings: VeterinaryScheduleSettings,
    rules: List<VeterinaryAvailabilityRule>,
    exceptions: List<VeterinaryAvailabilityException>,
    appts: List<VeterinaryAppointment>
): List<VeterinaryAppointmentSlot> {
    // La zona resuelve el instante de cada slot con reglas de DST correctas
    // (p. ej. America/Santiago cruza transiciones; America/Argentina/Buenos_Aires no).
    val zone = zoneOf(settings)
    val dayOfWeek = date.dayOfWeek
    val matchingRules = rules.filter { rule ->
        rule.clinicId == clinicId &&
            rule.active &&
            rule.dayOfWeek == dayOfWeek &&
            (rule.serviceId == null || rule.serviceId == serviceId) &&
            (professionalId == null || rule.professionalId == null || rule.professionalId == professionalId) &&
            (rule.validFrom == null || !date.isBefore(rule.validFrom)) &&
            (rule.validUntil == null || !date.isAfter(rule.validUntil))
    }

    val aggregated = LinkedHashMap<Pair<Instant, String?>, VeterinaryAppointmentSlot>()

    for (rule in matchingRules) {
        val dayExceptions = exceptions.filter {
            it.active &&
                it.clinicId == clinicId &&
                it.exceptionDate == date &&
                (it.ruleId == null || it.ruleId == rule.id)
        }
        if (dayExceptions.any { it.type == VeterinaryAvailabilityExceptionType.CLOSED }) continue

        var windowStart = rule.startsAt
        var windowEnd = rule.endsAt
        val custom = dayExceptions.firstOrNull { it.type == VeterinaryAvailabilityExceptionType.CUSTOM_HOURS }
        if (custom?.startsAt != null && custom.endsAt != null) {
            windowStart = custom.startsAt
            windowEnd = custom.endsAt
        }
        var capacity = rule.capacityPerSlot
        val capOverride = dayExceptions.firstOrNull {
            it.type == VeterinaryAvailabilityExceptionType.CAPACITY_OVERRIDE
        }
        if (capOverride?.capacityPerSlot != null) capacity = capOverride.capacityPerSlot

        val slotProfessionalId = professionalId ?: rule.professionalId
        val durationSeconds = rule.slotDurationMinutes * 60
        if (durationSeconds <= 0) continue
        val startSec = windowStart.toSecondOfDay()
        val endSec = windowEnd.toSecondOfDay()

        var cursor = startSec
        while (cursor + durationSeconds <= endSec) {
            val slotStartTime = LocalTime.ofSecondOfDay(cursor.toLong())
            val slotEndTime = LocalTime.ofSecondOfDay((cursor + durationSeconds).toLong())
            val startInstant = date.atTime(slotStartTime).atZone(zone).toInstant()
            val endInstant = date.atTime(slotEndTime).atZone(zone).toInstant()
            val reserved = reservedCount(
                appts, clinicId, serviceId, slotProfessionalId, startInstant, endInstant
            )
            val available = (capacity - reserved).coerceAtLeast(0)
            val key = startInstant to slotProfessionalId
            val existing = aggregated[key]
            if (existing == null || capacity > existing.capacity) {
                aggregated[key] = VeterinaryAppointmentSlot(
                    clinicId = clinicId,
                    professionalId = slotProfessionalId,
                    serviceId = serviceId,
                    startsAt = startInstant,
                    endsAt = endInstant,
                    capacity = capacity,
                    reserved = reserved,
                    available = available
                )
            }
            cursor += durationSeconds
        }
    }
    return aggregated.values.sortedBy { it.startsAt }
}

private fun allowedAppointmentTransition(
    from: VeterinaryAppointmentStatus,
    to: VeterinaryAppointmentStatus
): Boolean = when (from) {
    VeterinaryAppointmentStatus.REQUESTED -> to == VeterinaryAppointmentStatus.CONFIRMED ||
        to == VeterinaryAppointmentStatus.REJECTED ||
        to == VeterinaryAppointmentStatus.CANCELLED_BY_USER ||
        to == VeterinaryAppointmentStatus.CANCELLED_BY_CLINIC ||
        to == VeterinaryAppointmentStatus.EXPIRED
    VeterinaryAppointmentStatus.CONFIRMED -> to == VeterinaryAppointmentStatus.CANCELLED_BY_USER ||
        to == VeterinaryAppointmentStatus.CANCELLED_BY_CLINIC ||
        to == VeterinaryAppointmentStatus.COMPLETED ||
        to == VeterinaryAppointmentStatus.NO_SHOW
    else -> false
}

private fun validateScheduleSettings(settings: VeterinaryScheduleSettings) {
    runCatching { ZoneId.of(settings.timezoneName) }
        .getOrElse { failM12b3("VETERINARY_TIMEZONE_INVALID") }
    if (settings.bookingHorizonDays !in 1..365) failM12b3("VETERINARY_SCHEDULE_SETTINGS_INVALID")
    if (settings.minimumNoticeMinutes !in 0..10080) failM12b3("VETERINARY_SCHEDULE_SETTINGS_INVALID")
    if (settings.cancellationNoticeMinutes !in 0..43200) failM12b3("VETERINARY_SCHEDULE_SETTINGS_INVALID")
    if (settings.defaultSlotDurationMinutes !in 5..480) failM12b3("VETERINARY_SCHEDULE_SETTINGS_INVALID")
}

// --- Mock: agenda y disponibilidad ---

class MockVeterinaryScheduleRepository(
    private val actorUserId: () -> String?,
    private val store: M12VeterinaryMemoryStore
) : VeterinaryScheduleRepository {

    override suspend fun getSettings(clinicId: String): Result<VeterinaryScheduleSettings> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val clinic = store.clinics.value.find { it.id == clinicId }
            ?: failM12b3("VETERINARY_CLINIC_NOT_FOUND")
        if (!store.canViewB3(actor, clinic.organizationId)) failM12b3("VETERINARY_CLINIC_FORBIDDEN")
        store.settingsFor(clinicId)
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun saveSettings(
        settings: VeterinaryScheduleSettings
    ): Result<VeterinaryScheduleSettings> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val clinic = store.clinics.value.find { it.id == settings.clinicId }
            ?: failM12b3("VETERINARY_CLINIC_NOT_FOUND")
        if (!store.canManageB3(actor, clinic.organizationId)) failM12b3("VETERINARY_CLINIC_FORBIDDEN")
        validateScheduleSettings(settings)
        val previous = store.scheduleSettings.value[settings.clinicId]
        store.scheduleSettings.value = store.scheduleSettings.value + (settings.clinicId to settings)
        store.recordAudit("VETERINARY_SCHEDULE_SETTINGS_UPDATED", settings.clinicId)
        // Los turnos guardan Instant absoluto: al cambiar la zona no se recalculan, pero los
        // slots proyectados usan la nueva zona (soporta DST). Solo dejamos rastro auditable.
        if (previous != null && previous.timezoneName != settings.timezoneName) {
            noteTimezoneChanged(settings.clinicId)
        }
        settings
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    /** Registra auditoría si hay turnos abiertos al cambiar la zona horaria de la clínica. */
    fun noteTimezoneChanged(clinicId: String) {
        val hasOpen = store.appointments.value.any {
            it.clinicId == clinicId &&
                (it.status == VeterinaryAppointmentStatus.REQUESTED ||
                    it.status == VeterinaryAppointmentStatus.CONFIRMED)
        }
        if (hasOpen) store.recordAudit("VETERINARY_APPOINTMENT_TIMEZONE_CHANGED", clinicId)
    }

    override fun observeManagedAvailability(clinicId: String): Flow<ManagedVeterinaryAvailability> =
        combine(store.availabilityRules, store.availabilityExceptions) { rules, exceptions ->
            ManagedVeterinaryAvailability(
                rules = rules.filter { it.clinicId == clinicId },
                exceptions = exceptions.filter { it.clinicId == clinicId }
            )
        }

    private fun validateRuleShape(
        clinicId: String,
        professionalId: String?,
        serviceId: String?,
        startsAt: LocalTime,
        endsAt: LocalTime,
        slotDurationMinutes: Int,
        capacityPerSlot: Int
    ) {
        if (!endsAt.isAfter(startsAt)) failM12b3("VETERINARY_AVAILABILITY_RULE_INVALID")
        if (capacityPerSlot !in 1..50) failM12b3("VETERINARY_AVAILABILITY_RULE_INVALID")
        if (slotDurationMinutes !in 5..480) failM12b3("VETERINARY_AVAILABILITY_RULE_INVALID")
        val windowMinutes = (endsAt.toSecondOfDay() - startsAt.toSecondOfDay()) / 60
        if (slotDurationMinutes > windowMinutes) failM12b3("VETERINARY_AVAILABILITY_RULE_INVALID")
        store.clinics.value.find { it.id == clinicId }
            ?: failM12b3("VETERINARY_CLINIC_NOT_FOUND")
        if (professionalId != null) {
            val linked = store.clinicProfessionalLinks.value.any {
                it.clinicId == clinicId && it.professionalId == professionalId && it.active
            }
            if (!linked) failM12b3("VETERINARY_AVAILABILITY_RULE_INVALID")
        }
        if (serviceId != null) {
            val service = store.services.value.find { it.id == serviceId }
            if (service == null || service.clinicId != clinicId || !service.active) {
                failM12b3("VETERINARY_AVAILABILITY_RULE_INVALID")
            }
        }
    }

    private fun assertNoOverlap(candidate: VeterinaryAvailabilityRule) {
        val conflict = store.availabilityRules.value.any { other ->
            other.id != candidate.id &&
                other.active &&
                other.clinicId == candidate.clinicId &&
                other.professionalId == candidate.professionalId &&
                other.serviceId == candidate.serviceId &&
                other.dayOfWeek == candidate.dayOfWeek &&
                timesOverlap(candidate.startsAt, candidate.endsAt, other.startsAt, other.endsAt)
        }
        if (conflict) failM12b3("VETERINARY_AVAILABILITY_RULE_OVERLAP")
    }

    override suspend fun createRule(
        input: CreateVeterinaryAvailabilityRuleInput
    ): Result<VeterinaryAvailabilityRule> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val clinic = store.clinics.value.find { it.id == input.clinicId }
            ?: failM12b3("VETERINARY_CLINIC_NOT_FOUND")
        if (!store.canManageB3(actor, clinic.organizationId)) failM12b3("VETERINARY_CLINIC_FORBIDDEN")
        validateRuleShape(
            input.clinicId, input.professionalId, input.serviceId,
            input.startsAt, input.endsAt, input.slotDurationMinutes, input.capacityPerSlot
        )
        val rule = VeterinaryAvailabilityRule(
            id = UUID.randomUUID().toString(),
            clinicId = input.clinicId,
            professionalId = input.professionalId,
            serviceId = input.serviceId,
            dayOfWeek = input.dayOfWeek,
            startsAt = input.startsAt,
            endsAt = input.endsAt,
            slotDurationMinutes = input.slotDurationMinutes,
            capacityPerSlot = input.capacityPerSlot,
            validFrom = input.validFrom,
            validUntil = input.validUntil,
            active = true
        )
        assertNoOverlap(rule)
        store.availabilityRules.value = listOf(rule) + store.availabilityRules.value
        store.recordAudit("VETERINARY_AVAILABILITY_RULE_CHANGED", rule.id)
        rule
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun updateRule(
        input: UpdateVeterinaryAvailabilityRuleInput
    ): Result<VeterinaryAvailabilityRule> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val existing = store.availabilityRules.value.find { it.id == input.ruleId }
            ?: failM12b3("VETERINARY_AVAILABILITY_RULE_INVALID")
        val clinic = store.clinics.value.find { it.id == existing.clinicId }
            ?: failM12b3("VETERINARY_CLINIC_NOT_FOUND")
        if (!store.canManageB3(actor, clinic.organizationId)) failM12b3("VETERINARY_CLINIC_FORBIDDEN")
        validateRuleShape(
            existing.clinicId, input.professionalId, input.serviceId,
            input.startsAt, input.endsAt, input.slotDurationMinutes, input.capacityPerSlot
        )
        val updated = existing.copy(
            professionalId = input.professionalId,
            serviceId = input.serviceId,
            dayOfWeek = input.dayOfWeek,
            startsAt = input.startsAt,
            endsAt = input.endsAt,
            slotDurationMinutes = input.slotDurationMinutes,
            capacityPerSlot = input.capacityPerSlot,
            validFrom = input.validFrom,
            validUntil = input.validUntil
        )
        assertNoOverlap(updated)
        store.availabilityRules.value = store.availabilityRules.value.map {
            if (it.id == updated.id) updated else it
        }
        store.recordAudit("VETERINARY_AVAILABILITY_RULE_CHANGED", updated.id)
        updated
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun changeRuleStatus(
        ruleId: String,
        active: Boolean
    ): Result<VeterinaryAvailabilityRule> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val existing = store.availabilityRules.value.find { it.id == ruleId }
            ?: failM12b3("VETERINARY_AVAILABILITY_RULE_INVALID")
        val clinic = store.clinics.value.find { it.id == existing.clinicId }
            ?: failM12b3("VETERINARY_CLINIC_NOT_FOUND")
        if (!store.canManageB3(actor, clinic.organizationId)) failM12b3("VETERINARY_CLINIC_FORBIDDEN")
        val updated = existing.copy(active = active)
        if (active) assertNoOverlap(updated)
        store.availabilityRules.value = store.availabilityRules.value.map {
            if (it.id == ruleId) updated else it
        }
        store.recordAudit("VETERINARY_AVAILABILITY_RULE_CHANGED", ruleId)
        updated
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    private fun validateExceptionShape(
        type: VeterinaryAvailabilityExceptionType,
        startsAt: LocalTime?,
        endsAt: LocalTime?,
        capacityPerSlot: Int?
    ) {
        when (type) {
            VeterinaryAvailabilityExceptionType.CLOSED -> {
                if (startsAt != null || endsAt != null) {
                    failM12b3("VETERINARY_AVAILABILITY_EXCEPTION_INVALID")
                }
            }
            VeterinaryAvailabilityExceptionType.CUSTOM_HOURS -> {
                if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
                    failM12b3("VETERINARY_AVAILABILITY_EXCEPTION_INVALID")
                }
            }
            VeterinaryAvailabilityExceptionType.CAPACITY_OVERRIDE -> {
                if (capacityPerSlot == null || capacityPerSlot !in 1..50) {
                    failM12b3("VETERINARY_AVAILABILITY_EXCEPTION_INVALID")
                }
            }
            VeterinaryAvailabilityExceptionType.UNKNOWN ->
                failM12b3("VETERINARY_AVAILABILITY_EXCEPTION_INVALID")
        }
    }

    override suspend fun createException(
        input: CreateVeterinaryAvailabilityExceptionInput
    ): Result<VeterinaryAvailabilityException> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val clinic = store.clinics.value.find { it.id == input.clinicId }
            ?: failM12b3("VETERINARY_CLINIC_NOT_FOUND")
        if (!store.canManageB3(actor, clinic.organizationId)) failM12b3("VETERINARY_CLINIC_FORBIDDEN")
        validateExceptionShape(input.type, input.startsAt, input.endsAt, input.capacityPerSlot)
        if (input.ruleId != null &&
            store.availabilityRules.value.none { it.id == input.ruleId && it.clinicId == input.clinicId }
        ) {
            failM12b3("VETERINARY_AVAILABILITY_EXCEPTION_INVALID")
        }
        val exception = VeterinaryAvailabilityException(
            id = UUID.randomUUID().toString(),
            clinicId = input.clinicId,
            ruleId = input.ruleId,
            exceptionDate = input.exceptionDate,
            type = input.type,
            startsAt = input.startsAt,
            endsAt = input.endsAt,
            capacityPerSlot = input.capacityPerSlot,
            reason = input.reason?.trim()?.takeIf { it.isNotEmpty() },
            active = true
        )
        store.availabilityExceptions.value = listOf(exception) + store.availabilityExceptions.value
        store.recordAudit("VETERINARY_AVAILABILITY_EXCEPTION_CHANGED", exception.id)
        exception
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun updateException(
        input: UpdateVeterinaryAvailabilityExceptionInput
    ): Result<VeterinaryAvailabilityException> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val existing = store.availabilityExceptions.value.find { it.id == input.exceptionId }
            ?: failM12b3("VETERINARY_AVAILABILITY_EXCEPTION_INVALID")
        val clinic = store.clinics.value.find { it.id == existing.clinicId }
            ?: failM12b3("VETERINARY_CLINIC_NOT_FOUND")
        if (!store.canManageB3(actor, clinic.organizationId)) failM12b3("VETERINARY_CLINIC_FORBIDDEN")
        validateExceptionShape(input.type, input.startsAt, input.endsAt, input.capacityPerSlot)
        if (input.ruleId != null &&
            store.availabilityRules.value.none { it.id == input.ruleId && it.clinicId == existing.clinicId }
        ) {
            failM12b3("VETERINARY_AVAILABILITY_EXCEPTION_INVALID")
        }
        val updated = existing.copy(
            ruleId = input.ruleId,
            exceptionDate = input.exceptionDate,
            type = input.type,
            startsAt = input.startsAt,
            endsAt = input.endsAt,
            capacityPerSlot = input.capacityPerSlot,
            reason = input.reason?.trim()?.takeIf { it.isNotEmpty() }
        )
        store.availabilityExceptions.value = store.availabilityExceptions.value.map {
            if (it.id == updated.id) updated else it
        }
        store.recordAudit("VETERINARY_AVAILABILITY_EXCEPTION_CHANGED", updated.id)
        updated
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun changeExceptionStatus(
        exceptionId: String,
        active: Boolean
    ): Result<VeterinaryAvailabilityException> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val existing = store.availabilityExceptions.value.find { it.id == exceptionId }
            ?: failM12b3("VETERINARY_AVAILABILITY_EXCEPTION_INVALID")
        val clinic = store.clinics.value.find { it.id == existing.clinicId }
            ?: failM12b3("VETERINARY_CLINIC_NOT_FOUND")
        if (!store.canManageB3(actor, clinic.organizationId)) failM12b3("VETERINARY_CLINIC_FORBIDDEN")
        val updated = existing.copy(active = active)
        store.availabilityExceptions.value = store.availabilityExceptions.value.map {
            if (it.id == exceptionId) updated else it
        }
        store.recordAudit("VETERINARY_AVAILABILITY_EXCEPTION_CHANGED", exceptionId)
        updated
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override fun observeAvailableSlots(
        clinicId: String,
        serviceId: String,
        date: LocalDate,
        professionalId: String?
    ): Flow<List<VeterinaryAppointmentSlot>> = combine(
        store.scheduleSettings,
        store.availabilityRules,
        store.availabilityExceptions,
        store.appointments
    ) { settingsMap, rules, exceptions, appts ->
        val settings = settingsMap[clinicId] ?: VeterinaryScheduleSettings(clinicId = clinicId)
        computeSlots(clinicId, serviceId, date, professionalId, settings, rules, exceptions, appts)
    }
}

// --- Mock: turnos ---

class MockVeterinaryAppointmentRepository(
    private val actorUserId: () -> String?,
    private val store: M12VeterinaryMemoryStore
) : VeterinaryAppointmentRepository {

    private fun appendHistory(
        appointmentId: String,
        from: VeterinaryAppointmentStatus?,
        to: VeterinaryAppointmentStatus,
        changedBy: String,
        reason: String?,
        at: Instant
    ) {
        val entry = VeterinaryAppointmentStatusHistory(
            id = UUID.randomUUID().toString(),
            appointmentId = appointmentId,
            fromStatus = from,
            toStatus = to,
            changedBy = changedBy,
            reason = reason,
            changedAt = at
        )
        store.appointmentHistory.value = store.appointmentHistory.value + entry
    }

    private fun petAuthorized(actor: String, petId: String, clinicOrganizationId: String): Boolean {
        val personal = store.petAuthorizedActors.value[petId]?.contains(actor) == true
        if (personal) return true
        val custodyOrg = store.petOrgCustody.value[petId]
        return custodyOrg != null &&
            custodyOrg == clinicOrganizationId &&
            store.canManageB3(actor, clinicOrganizationId)
    }

    override suspend fun requestAppointment(
        input: RequestVeterinaryAppointmentInput
    ): Result<VeterinaryAppointment> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val clinic = store.clinics.value.find { it.id == input.clinicId }
            ?: failM12b3("VETERINARY_CLINIC_NOT_FOUND")
        val service = store.services.value.find { it.id == input.serviceId && it.clinicId == input.clinicId }
            ?: failM12b3("VETERINARY_SERVICE_NOT_FOUND")
        if (!service.active) failM12b3("VETERINARY_SERVICE_NOT_FOUND")
        if (input.professionalId != null) {
            val linked = store.clinicProfessionalLinks.value.any {
                it.clinicId == input.clinicId && it.professionalId == input.professionalId && it.active
            }
            if (!linked) failM12b3("VETERINARY_PROFESSIONAL_NOT_LINKED")
        }
        if (!petAuthorized(actor, input.petId, clinic.organizationId)) {
            failM12b3("VETERINARY_APPOINTMENT_PET_FORBIDDEN")
        }
        val note = input.requestNote?.trim()?.takeIf { it.isNotEmpty() }
        if (note != null && note.length > MAX_REQUEST_NOTE_LENGTH) {
            failM12b3(VETERINARY_APPOINTMENT_NOTE_TOO_LONG)
        }

        val now = Instant.now()
        if (!input.startsAt.isAfter(now)) failM12b3("VETERINARY_APPOINTMENT_PAST_SLOT")
        val settings = store.settingsFor(input.clinicId)
        if (input.startsAt.isBefore(now.plusSeconds(settings.minimumNoticeMinutes * 60L))) {
            failM12b3("VETERINARY_SLOT_NOT_AVAILABLE")
        }
        if (input.startsAt.isAfter(now.plusSeconds(settings.bookingHorizonDays * 86_400L))) {
            failM12b3("VETERINARY_SLOT_NOT_AVAILABLE")
        }

        val date = input.startsAt.atZone(zoneOf(settings)).toLocalDate()
        val slots = computeSlots(
            input.clinicId, input.serviceId, date, input.professionalId,
            settings, store.availabilityRules.value, store.availabilityExceptions.value,
            store.appointments.value
        )
        val slot = slots.firstOrNull {
            it.startsAt == input.startsAt && professionalMatch(it.professionalId, input.professionalId)
        } ?: failM12b3("VETERINARY_SLOT_NOT_AVAILABLE")
        if (slot.available < 1) failM12b3("VETERINARY_SLOT_CAPACITY_EXHAUSTED")

        val slotProfessionalId = slot.professionalId
        synchronized(store) {
            val duplicate = store.appointments.value.any {
                it.requesterUserId == actor &&
                    it.clinicId == input.clinicId &&
                    it.serviceId == input.serviceId &&
                    it.startsAt == input.startsAt &&
                    it.status.consumesCapacity
            }
            if (duplicate) failM12b3("VETERINARY_SLOT_NOT_AVAILABLE")
            val reserved = reservedCount(
                store.appointments.value, input.clinicId, input.serviceId,
                slotProfessionalId, slot.startsAt, slot.endsAt
            )
            if (reserved >= slot.capacity) failM12b3("VETERINARY_SLOT_CAPACITY_EXHAUSTED")
            val appointment = VeterinaryAppointment(
                id = UUID.randomUUID().toString(),
                clinicId = input.clinicId,
                professionalId = slotProfessionalId,
                serviceId = input.serviceId,
                petId = input.petId,
                requesterUserId = actor,
                startsAt = slot.startsAt,
                endsAt = slot.endsAt,
                status = VeterinaryAppointmentStatus.REQUESTED,
                requestNote = note,
                createdAt = now,
                updatedAt = now
            )
            store.appointments.value = store.appointments.value + appointment
            appendHistory(
                appointment.id, null, VeterinaryAppointmentStatus.REQUESTED, actor, null, now
            )
            store.recordAudit(VeterinaryAppointmentAuditEvents.REQUESTED, appointment.id)
            store.recordM06Hook(VeterinaryAppointmentM06Hooks.REQUESTED)
            appointment
        }
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun getAppointment(appointmentId: String): Result<VeterinaryAppointment> =
        runCatching {
            if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
            val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
            val appointment = store.appointments.value.find { it.id == appointmentId }
                ?: failM12b3("VETERINARY_APPOINTMENT_NOT_FOUND")
            val clinicOrg = store.clinics.value.find { it.id == appointment.clinicId }?.organizationId
            val allowed = appointment.requesterUserId == actor ||
                (clinicOrg != null && store.canManageB3(actor, clinicOrg))
            if (!allowed) failM12b3("VETERINARY_APPOINTMENT_FORBIDDEN")
            appointment
        }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override fun observeMyAppointments(): Flow<List<VeterinaryAppointment>> =
        store.appointments.map { list ->
            val actor = actorUserId().orEmpty()
            if (actor.isBlank()) emptyList()
            else list.filter { it.requesterUserId == actor }.sortedBy { it.startsAt }
        }

    override fun observeManagedAppointments(clinicId: String): Flow<List<VeterinaryAppointment>> =
        store.appointments.map { list ->
            val actor = actorUserId().orEmpty()
            val clinicOrg = store.clinics.value.find { it.id == clinicId }?.organizationId
            if (actor.isBlank() || clinicOrg == null || !store.canManageB3(actor, clinicOrg)) {
                emptyList()
            } else {
                list.filter { it.clinicId == clinicId }.sortedBy { it.startsAt }
            }
        }

    private fun requireManaged(appointment: VeterinaryAppointment, actor: String) {
        val clinicOrg = store.clinics.value.find { it.id == appointment.clinicId }?.organizationId
        if (clinicOrg == null || !store.canManageB3(actor, clinicOrg)) {
            failM12b3("VETERINARY_APPOINTMENT_FORBIDDEN")
        }
    }

    private fun assertTransitionAllowed(
        from: VeterinaryAppointmentStatus,
        to: VeterinaryAppointmentStatus
    ) {
        if (from.isFinal) failM12b3("VETERINARY_APPOINTMENT_ALREADY_FINAL")
        if (!allowedAppointmentTransition(from, to)) {
            failM12b3("VETERINARY_APPOINTMENT_INVALID_TRANSITION")
        }
    }

    private fun applyTransition(
        appointment: VeterinaryAppointment,
        to: VeterinaryAppointmentStatus,
        changedBy: String,
        reason: String?,
        auditEvent: String,
        m06Hook: String?,
        rejectionReason: String? = null,
        cancellationReason: String? = null
    ): VeterinaryAppointment {
        val now = Instant.now()
        val updated = appointment.copy(
            status = to,
            rejectionReason = rejectionReason ?: appointment.rejectionReason,
            cancellationReason = cancellationReason ?: appointment.cancellationReason,
            updatedAt = now
        )
        store.appointments.value = store.appointments.value.map {
            if (it.id == updated.id) updated else it
        }
        appendHistory(appointment.id, appointment.status, to, changedBy, reason, now)
        store.recordAudit(auditEvent, appointment.id)
        if (m06Hook != null) store.recordM06Hook(m06Hook)
        // Al salir de CONFIRMED por cancelación/rechazo/expiración, cancelamos recordatorios.
        val cancelsReminders = to == VeterinaryAppointmentStatus.CANCELLED_BY_USER ||
            to == VeterinaryAppointmentStatus.CANCELLED_BY_CLINIC ||
            to == VeterinaryAppointmentStatus.EXPIRED ||
            to == VeterinaryAppointmentStatus.REJECTED
        if (appointment.status == VeterinaryAppointmentStatus.CONFIRMED && cancelsReminders) {
            cancelRemindersInternal(appointment.id)
        }
        return updated
    }

    override suspend fun confirmAppointment(appointmentId: String): Result<VeterinaryAppointment> =
        runCatching {
            if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
            val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
            val appointment = store.appointments.value.find { it.id == appointmentId }
                ?: failM12b3("VETERINARY_APPOINTMENT_NOT_FOUND")
            requireManaged(appointment, actor)
            assertTransitionAllowed(appointment.status, VeterinaryAppointmentStatus.CONFIRMED)
            if (!appointment.startsAt.isAfter(Instant.now())) {
                failM12b3("VETERINARY_APPOINTMENT_PAST_SLOT")
            }
            val service = store.services.value.find { it.id == appointment.serviceId }
            if (service == null || !service.active) {
                failM12b3("VETERINARY_APPOINTMENT_SERVICE_INACTIVE")
            }
            if (appointment.professionalId != null) {
                val linked = store.clinicProfessionalLinks.value.any {
                    it.clinicId == appointment.clinicId &&
                        it.professionalId == appointment.professionalId &&
                        it.active
                }
                if (!linked) failM12b3("VETERINARY_APPOINTMENT_PROFESSIONAL_INACTIVE")
            }
            val updated = applyTransition(
                appointment, VeterinaryAppointmentStatus.CONFIRMED, actor, null,
                VeterinaryAppointmentAuditEvents.CONFIRMED, VeterinaryAppointmentM06Hooks.CONFIRMED
            )
            // Hardening: al confirmar, preparamos los recordatorios (idempotente, sin push real).
            prepareRemindersInternal(updated)
            updated
        }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun rejectAppointment(
        appointmentId: String,
        reason: String
    ): Result<VeterinaryAppointment> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val appointment = store.appointments.value.find { it.id == appointmentId }
            ?: failM12b3("VETERINARY_APPOINTMENT_NOT_FOUND")
        requireManaged(appointment, actor)
        assertTransitionAllowed(appointment.status, VeterinaryAppointmentStatus.REJECTED)
        val cleanReason = reason.trim().takeIf { it.isNotEmpty() }
        applyTransition(
            appointment, VeterinaryAppointmentStatus.REJECTED, actor, cleanReason,
            VeterinaryAppointmentAuditEvents.REJECTED, VeterinaryAppointmentM06Hooks.REJECTED,
            rejectionReason = cleanReason
        )
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun cancelMyAppointment(
        appointmentId: String,
        reason: String?
    ): Result<VeterinaryAppointment> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val appointment = store.appointments.value.find { it.id == appointmentId }
            ?: failM12b3("VETERINARY_APPOINTMENT_NOT_FOUND")
        if (appointment.requesterUserId != actor) failM12b3("VETERINARY_APPOINTMENT_FORBIDDEN")
        assertTransitionAllowed(appointment.status, VeterinaryAppointmentStatus.CANCELLED_BY_USER)
        if (appointment.status == VeterinaryAppointmentStatus.CONFIRMED) {
            val settings = store.settingsFor(appointment.clinicId)
            val cutoff = appointment.startsAt.minusSeconds(settings.cancellationNoticeMinutes * 60L)
            if (Instant.now().isAfter(cutoff)) {
                failM12b3("VETERINARY_APPOINTMENT_CANCELLATION_WINDOW")
            }
        }
        val cleanReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        applyTransition(
            appointment, VeterinaryAppointmentStatus.CANCELLED_BY_USER, actor, cleanReason,
            VeterinaryAppointmentAuditEvents.CANCELLED, VeterinaryAppointmentM06Hooks.CANCELLED,
            cancellationReason = cleanReason
        )
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun cancelManagedAppointment(
        appointmentId: String,
        reason: String?
    ): Result<VeterinaryAppointment> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val appointment = store.appointments.value.find { it.id == appointmentId }
            ?: failM12b3("VETERINARY_APPOINTMENT_NOT_FOUND")
        requireManaged(appointment, actor)
        assertTransitionAllowed(appointment.status, VeterinaryAppointmentStatus.CANCELLED_BY_CLINIC)
        val cleanReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        applyTransition(
            appointment, VeterinaryAppointmentStatus.CANCELLED_BY_CLINIC, actor, cleanReason,
            VeterinaryAppointmentAuditEvents.CANCELLED, VeterinaryAppointmentM06Hooks.CANCELLED,
            cancellationReason = cleanReason
        )
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun completeAppointment(appointmentId: String): Result<VeterinaryAppointment> =
        runCatching {
            if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
            val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
            val appointment = store.appointments.value.find { it.id == appointmentId }
                ?: failM12b3("VETERINARY_APPOINTMENT_NOT_FOUND")
            requireManaged(appointment, actor)
            assertTransitionAllowed(appointment.status, VeterinaryAppointmentStatus.COMPLETED)
            if (appointment.startsAt.isAfter(Instant.now())) {
                failM12b3("VETERINARY_APPOINTMENT_INVALID_TRANSITION")
            }
            applyTransition(
                appointment, VeterinaryAppointmentStatus.COMPLETED, actor, null,
                VeterinaryAppointmentAuditEvents.COMPLETED, VeterinaryAppointmentM06Hooks.COMPLETED
            )
        }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun markNoShow(appointmentId: String): Result<VeterinaryAppointment> =
        runCatching {
            if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
            val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
            val appointment = store.appointments.value.find { it.id == appointmentId }
                ?: failM12b3("VETERINARY_APPOINTMENT_NOT_FOUND")
            requireManaged(appointment, actor)
            assertTransitionAllowed(appointment.status, VeterinaryAppointmentStatus.NO_SHOW)
            if (appointment.startsAt.isAfter(Instant.now())) {
                failM12b3("VETERINARY_APPOINTMENT_INVALID_TRANSITION")
            }
            applyTransition(
                appointment, VeterinaryAppointmentStatus.NO_SHOW, actor, null,
                VeterinaryAppointmentAuditEvents.NO_SHOW, null
            )
        }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun expireAppointment(appointmentId: String): Result<VeterinaryAppointment> =
        runCatching {
            if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
            val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
            val appointment = store.appointments.value.find { it.id == appointmentId }
                ?: failM12b3("VETERINARY_APPOINTMENT_NOT_FOUND")
            requireManaged(appointment, actor)
            assertTransitionAllowed(appointment.status, VeterinaryAppointmentStatus.EXPIRED)
            applyTransition(
                appointment, VeterinaryAppointmentStatus.EXPIRED, actor, null,
                VeterinaryAppointmentAuditEvents.EXPIRED, null
            )
        }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override fun observeAppointmentHistory(
        appointmentId: String
    ): Flow<List<VeterinaryAppointmentStatusHistory>> =
        store.appointmentHistory.map { list ->
            list.filter { it.appointmentId == appointmentId }.sortedBy { it.changedAt }
        }

    // --- Bloque 4 — recordatorios (sin push real) ---

    private fun idempoKey(appointmentId: String, type: VeterinaryReminderType): String =
        "$appointmentId|${type.name}"

    private fun dueAtFor(startsAt: Instant, type: VeterinaryReminderType): Instant = when (type) {
        VeterinaryReminderType.REMINDER_24H -> startsAt.minusSeconds(24L * 3600L)
        VeterinaryReminderType.REMINDER_2H -> startsAt.minusSeconds(2L * 3600L)
    }

    private fun hookFor(type: VeterinaryReminderType): String = when (type) {
        VeterinaryReminderType.REMINDER_24H -> VeterinaryAppointmentM06Hooks.REMINDER_24H_DUE
        VeterinaryReminderType.REMINDER_2H -> VeterinaryAppointmentM06Hooks.REMINDER_2H_DUE
    }

    /**
     * Prepara (idempotente) los recordatorios 24h/2h de un turno CONFIRMED.
     * Payload de hooks: solo appointmentId + type + dueAt (sin nota, email ni teléfono).
     * Nunca reclama push real (pushClaimed=false).
     */
    private fun prepareRemindersInternal(appointment: VeterinaryAppointment): VeterinaryReminderSchedule {
        val settings = store.settingsFor(appointment.clinicId)
        val tz = settings.timezoneName
        val infra = store.m06InfrastructureAvailable.value
        val current = store.reminderStates.value[appointment.id].orEmpty().toMutableList()
        var idempo = store.reminderIdempotency.value

        for (type in VeterinaryReminderType.entries) {
            val key = idempoKey(appointment.id, type)
            val existing = current.firstOrNull { it.type == type }
            if (existing != null && key in idempo) continue // ya programado: no duplicar
            current.removeAll { it.type == type }
            current += VeterinaryReminderState(
                appointmentId = appointment.id,
                type = type,
                dueAt = dueAtFor(appointment.startsAt, type),
                status = VeterinaryReminderDeliveryStatus.PREPARED,
                timezoneName = tz,
                pushClaimed = false
            )
            idempo = idempo + key
            store.recordM06Hook(hookFor(type))
            store.recordAudit(hookFor(type), appointment.id)
            if (!infra) store.recordM06Hook(VeterinaryAppointmentM06Hooks.REMINDER_INFRASTRUCTURE)
        }
        store.reminderStates.value = store.reminderStates.value + (appointment.id to current)
        store.reminderIdempotency.value = idempo
        return VeterinaryReminderSchedule(
            appointmentId = appointment.id,
            reminders = current.sortedBy { it.dueAt },
            infrastructureAvailable = infra
        )
    }

    /** Cancela (idempotente) los recordatorios de un turno; registra REMINDER_CANCELLED una vez. */
    private fun cancelRemindersInternal(appointmentId: String): VeterinaryReminderSchedule {
        val infra = store.m06InfrastructureAvailable.value
        val current = store.reminderStates.value[appointmentId].orEmpty()
        val alreadyAllCancelled = current.isNotEmpty() &&
            current.all { it.status == VeterinaryReminderDeliveryStatus.CANCELLED }
        if (current.isEmpty() || alreadyAllCancelled) {
            return VeterinaryReminderSchedule(appointmentId, current.sortedBy { it.dueAt }, infra)
        }
        val cancelled = current.map {
            it.copy(status = VeterinaryReminderDeliveryStatus.CANCELLED)
        }
        store.reminderStates.value = store.reminderStates.value + (appointmentId to cancelled)
        store.recordM06Hook(VeterinaryAppointmentM06Hooks.REMINDER_CANCELLED)
        store.recordAudit(VeterinaryAppointmentM06Hooks.REMINDER_CANCELLED, appointmentId)
        return VeterinaryReminderSchedule(appointmentId, cancelled.sortedBy { it.dueAt }, infra)
    }

    private fun requireViewer(appointment: VeterinaryAppointment, actor: String): Boolean {
        val clinicOrg = store.clinics.value.find { it.id == appointment.clinicId }?.organizationId
        val manager = clinicOrg != null && store.canManageB3(actor, clinicOrg)
        val requester = appointment.requesterUserId == actor
        if (!manager && !requester) failM12b3("VETERINARY_APPOINTMENT_FORBIDDEN")
        return manager
    }

    override suspend fun prepareRemindersForConfirmed(
        appointmentId: String
    ): Result<VeterinaryReminderSchedule> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val appointment = store.appointments.value.find { it.id == appointmentId }
            ?: failM12b3("VETERINARY_APPOINTMENT_NOT_FOUND")
        requireViewer(appointment, actor)
        if (appointment.status != VeterinaryAppointmentStatus.CONFIRMED) {
            failM12b3("VETERINARY_REMINDER_NOT_ELIGIBLE")
        }
        prepareRemindersInternal(appointment)
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun cancelReminders(
        appointmentId: String,
        reason: String?
    ): Result<VeterinaryReminderSchedule> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val appointment = store.appointments.value.find { it.id == appointmentId }
            ?: failM12b3("VETERINARY_APPOINTMENT_NOT_FOUND")
        requireViewer(appointment, actor)
        cancelRemindersInternal(appointmentId)
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun fireDueReminder(
        appointmentId: String,
        type: VeterinaryReminderType,
        now: Instant
    ): Result<VeterinaryReminderState> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val appointment = store.appointments.value.find { it.id == appointmentId }
            ?: failM12b3("VETERINARY_APPOINTMENT_NOT_FOUND")
        requireViewer(appointment, actor)
        if (appointment.status != VeterinaryAppointmentStatus.CONFIRMED) {
            failM12b3("VETERINARY_REMINDER_NOT_ELIGIBLE")
        }
        val states = store.reminderStates.value[appointmentId].orEmpty()
        val state = states.firstOrNull { it.type == type }
            ?: failM12b3("VETERINARY_REMINDER_NOT_ELIGIBLE")
        when (state.status) {
            VeterinaryReminderDeliveryStatus.CANCELLED,
            VeterinaryReminderDeliveryStatus.NOT_ELIGIBLE ->
                failM12b3("VETERINARY_REMINDER_NOT_ELIGIBLE")
            VeterinaryReminderDeliveryStatus.FIRED_PREPARED ->
                failM12b3("VETERINARY_REMINDER_ALREADY_SCHEDULED")
            VeterinaryReminderDeliveryStatus.PREPARED -> Unit
        }
        if (state.dueAt.isAfter(now)) failM12b3("VETERINARY_REMINDER_NOT_ELIGIBLE")
        val fired = state.copy(
            status = VeterinaryReminderDeliveryStatus.FIRED_PREPARED,
            pushClaimed = false
        )
        store.reminderStates.value = store.reminderStates.value +
            (appointmentId to states.map { if (it.type == type) fired else it })
        store.recordM06Hook(hookFor(type))
        store.recordAudit(hookFor(type), appointmentId)
        fired
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override fun observeReminderSchedule(appointmentId: String): Flow<VeterinaryReminderSchedule?> =
        combine(store.reminderStates, store.m06InfrastructureAvailable) { statesMap, infra ->
            val states = statesMap[appointmentId] ?: return@combine null
            VeterinaryReminderSchedule(appointmentId, states.sortedBy { it.dueAt }, infra)
        }

    // --- Bloque 4 — métricas operativas ---

    override suspend fun getOperationalMetrics(
        clinicId: String,
        from: Instant,
        to: Instant,
        serviceId: String?,
        professionalId: String?
    ): Result<VeterinaryAppointmentOperationalMetrics> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val clinicOrg = store.clinics.value.find { it.id == clinicId }?.organizationId
            ?: failM12b3("VETERINARY_CLINIC_NOT_FOUND")
        if (!store.canManageB3(actor, clinicOrg)) failM12b3("VETERINARY_APPOINTMENT_FORBIDDEN")
        if (!from.isBefore(to)) failM12b3("VETERINARY_APPOINTMENT_METRICS_INVALID_RANGE")

        val windowAppts = store.appointments.value.filter { a ->
            a.clinicId == clinicId &&
                !a.startsAt.isBefore(from) && a.startsAt.isBefore(to) &&
                (serviceId == null || a.serviceId == serviceId) &&
                (professionalId == null || a.professionalId == professionalId)
        }
        fun count(status: VeterinaryAppointmentStatus) = windowAppts.count { it.status == status }
        val requested = count(VeterinaryAppointmentStatus.REQUESTED)
        val confirmed = count(VeterinaryAppointmentStatus.CONFIRMED)
        val rejected = count(VeterinaryAppointmentStatus.REJECTED)
        val cancelledByUser = count(VeterinaryAppointmentStatus.CANCELLED_BY_USER)
        val cancelledByClinic = count(VeterinaryAppointmentStatus.CANCELLED_BY_CLINIC)
        val completed = count(VeterinaryAppointmentStatus.COMPLETED)
        val noShow = count(VeterinaryAppointmentStatus.NO_SHOW)
        val expired = count(VeterinaryAppointmentStatus.EXPIRED)

        val denominator = requested + confirmed + rejected + cancelledByUser +
            cancelledByClinic + completed + noShow + expired
        val occupancyRate = (confirmed + completed).toDouble() / maxOf(1, denominator)

        val history = store.appointmentHistory.value
        val confirmationMinutes = windowAppts.mapNotNull { appt ->
            val entries = history.filter { it.appointmentId == appt.id }
            val requestedAt = entries
                .filter { it.toStatus == VeterinaryAppointmentStatus.REQUESTED }
                .minByOrNull { it.changedAt }?.changedAt
            val confirmedAt = entries
                .filter { it.toStatus == VeterinaryAppointmentStatus.CONFIRMED }
                .minByOrNull { it.changedAt }?.changedAt
            if (requestedAt != null && confirmedAt != null && !confirmedAt.isBefore(requestedAt)) {
                Duration.between(requestedAt, confirmedAt).toMillis() / 60000.0
            } else {
                null
            }
        }
        val averageConfirmationMinutes =
            if (confirmationMinutes.isEmpty()) null else confirmationMinutes.average()

        VeterinaryAppointmentOperationalMetrics(
            clinicId = clinicId,
            from = from,
            to = to,
            requested = requested,
            confirmed = confirmed,
            rejected = rejected,
            cancelledByUser = cancelledByUser,
            cancelledByClinic = cancelledByClinic,
            completed = completed,
            noShow = noShow,
            expired = expired,
            occupancyRate = occupancyRate,
            averageConfirmationMinutes = averageConfirmationMinutes
        )
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    // --- Bloque 4 — timeline ---

    private fun timelineLabel(status: VeterinaryAppointmentStatus): String = when (status) {
        VeterinaryAppointmentStatus.REQUESTED -> "Turno solicitado"
        VeterinaryAppointmentStatus.CONFIRMED -> "Turno confirmado"
        VeterinaryAppointmentStatus.REJECTED -> "Turno rechazado"
        VeterinaryAppointmentStatus.CANCELLED_BY_USER -> "Cancelado por el usuario"
        VeterinaryAppointmentStatus.CANCELLED_BY_CLINIC -> "Cancelado por la clínica"
        VeterinaryAppointmentStatus.COMPLETED -> "Turno realizado"
        VeterinaryAppointmentStatus.NO_SHOW -> "No asistió"
        VeterinaryAppointmentStatus.EXPIRED -> "Turno vencido"
        VeterinaryAppointmentStatus.UNKNOWN -> "Estado desconocido"
    }

    override fun buildTimeline(
        appointmentId: String,
        isManager: Boolean
    ): Result<List<VeterinaryAppointmentTimelineStep>> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b3("NOT_AUTHENTICATED")
        val appointment = store.appointments.value.find { it.id == appointmentId }
            ?: failM12b3("VETERINARY_APPOINTMENT_NOT_FOUND")
        val manager = requireViewer(appointment, actor)
        // Solo un manager real ve motivos de rechazo/cancelación de clínica en el timeline.
        val effectiveManager = isManager && manager
        store.appointmentHistory.value
            .filter { it.appointmentId == appointmentId }
            .sortedBy { it.changedAt }
            .map { entry ->
                val reason = when {
                    effectiveManager -> entry.reason
                    entry.toStatus == VeterinaryAppointmentStatus.CANCELLED_BY_USER -> entry.reason
                    else -> null // el solicitante no ve notas operativas de la clínica
                }
                VeterinaryAppointmentTimelineStep(
                    status = entry.toStatus,
                    at = entry.changedAt,
                    label = timelineLabel(entry.toStatus),
                    reason = reason
                )
            }
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    // --- Bloque 4 — transición idempotente con guardia de conflicto ---

    override suspend fun retrySafeTransition(
        appointmentId: String,
        expectedFrom: VeterinaryAppointmentStatus,
        action: VeterinaryRetryAction
    ): Result<VeterinaryAppointment> = runCatching {
        if (store.forceFailure) failM12b3("VETERINARY_REPOSITORY_FAILURE")
        val appointment = store.appointments.value.find { it.id == appointmentId }
            ?: failM12b3("VETERINARY_APPOINTMENT_NOT_FOUND")
        if (appointment.status != expectedFrom) failM12b3("VETERINARY_APPOINTMENT_RETRY_CONFLICT")
        val result = when (action) {
            VeterinaryRetryAction.CONFIRM -> confirmAppointment(appointmentId)
            VeterinaryRetryAction.REJECT -> rejectAppointment(appointmentId, "")
            VeterinaryRetryAction.CANCEL_MY -> cancelMyAppointment(appointmentId, null)
            VeterinaryRetryAction.CANCEL_MANAGED -> cancelManagedAppointment(appointmentId, null)
            VeterinaryRetryAction.EXPIRE -> expireAppointment(appointmentId)
        }
        result.getOrThrow()
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })
}
