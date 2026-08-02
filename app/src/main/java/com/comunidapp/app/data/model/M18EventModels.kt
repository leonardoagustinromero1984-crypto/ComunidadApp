package com.comunidapp.app.data.model

import com.comunidapp.app.domain.organization.OrganizationType

/** LeoVer M18 — Eventos comunitarios (Bloque 1 local/mock). */

enum class M18EventStatus {
    DRAFT,
    PUBLISHED,
    PAUSED,
    COMPLETED,
    CANCELLED;

    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELLED
    val isPublic: Boolean get() = this == PUBLISHED || this == PAUSED || this == COMPLETED
}

enum class M18EventType {
    ADOPTION_FAIR,
    VOLUNTEER_DAY,
    TRAINING_WORKSHOP,
    COMMUNITY_GATHERING,
    FREE_FUNDRAISER,
    AWARENESS_WALK
}

enum class M18RegistrationStatus {
    REGISTERED,
    WAITLISTED,
    CANCELLED,
    CHECKED_IN,
    ATTENDED,
    NO_SHOW,
    REJECTED;

    val isTerminal: Boolean
        get() = this in setOf(CANCELLED, ATTENDED, NO_SHOW, REJECTED)
    val occupiesCapacity: Boolean
        get() = this in setOf(REGISTERED, CHECKED_IN, ATTENDED)
}

enum class M18ReminderStatus {
    SCHEDULED,
    SENT,
    SKIPPED
}

data class M18EventReference(
    val petId: String? = null,
    val petPublicName: String? = null,
    val shelterProfileId: String? = null,
    val shelterPublicName: String? = null,
    val publicLocationText: String? = null
)

data class M18EventCapacitySummary(
    val maxCapacity: Int,
    val registeredCount: Int,
    val waitlistCount: Int,
    val availableSpots: Int,
    val isFull: Boolean,
    val isWaitlistOpen: Boolean
)

data class M18CommunityEvent(
    val id: String,
    val organizationId: String,
    val organizationDisplayName: String,
    val title: String,
    val description: String,
    val eventType: M18EventType,
    val status: M18EventStatus,
    val venueName: String? = null,
    val reference: M18EventReference = M18EventReference(),
    val coverImageRef: String? = null,
    val maxCapacity: Int,
    val waitlistEnabled: Boolean = true,
    val startsAt: Long,
    val endsAt: Long,
    val checkInOpensAt: Long? = null,
    val checkInClosesAt: Long? = null,
    val internalNotes: String? = null,
    val moderationStatus: String? = null,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toPublicEvent(summary: M18EventCapacitySummary): M18PublicEvent =
        M18PrivacySanitizer.toPublicEvent(this, summary)
}

data class M18PublicEvent(
    val id: String,
    val title: String,
    val description: String,
    val organizationDisplayName: String,
    val eventType: M18EventType,
    val status: M18EventStatus,
    val venueName: String? = null,
    val reference: M18EventReference = M18EventReference(),
    val coverImageRef: String? = null,
    val maxCapacity: Int,
    val registeredCount: Int,
    val waitlistCount: Int,
    val availableSpots: Int,
    val isFull: Boolean,
    val isWaitlistOpen: Boolean,
    val isRegistrationOpen: Boolean,
    val startsAt: Long,
    val endsAt: Long
)

data class M18EventSummary(
    val id: String,
    val title: String,
    val organizationDisplayName: String,
    val eventType: M18EventType,
    val status: M18EventStatus,
    val startsAt: Long,
    val maxCapacity: Int,
    val registeredCount: Int,
    val availableSpots: Int,
    val venueName: String? = null,
    val publicLocationText: String? = null
)

data class M18EventRegistration(
    val id: String,
    val eventId: String,
    val userId: String,
    val status: M18RegistrationStatus,
    val attendeeDisplayName: String? = null,
    val registeredAt: Long,
    val checkedInAt: Long? = null,
    val reminderScheduled: Boolean = false
)

data class M18PublicRegistrationStats(
    val registeredCount: Int,
    val waitlistCount: Int,
    val checkedInCount: Int
)

/** Resumen operativo para panel organizador (sin PII). */
data class M18EventOperationsSummary(
    val eventId: String,
    val maxCapacity: Int,
    val registeredCount: Int,
    val waitlistCount: Int,
    val cancelledCount: Int,
    val checkedInCount: Int,
    val attendedCount: Int,
    val noShowCount: Int,
    val rejectedCount: Int = 0,
    val availableSpots: Int,
    val occupancyPercent: Int,
    val registrationToAttendancePercent: Int,
    val hasCapacityInconsistency: Boolean
)

/** Ítem administrativo mínimo — alias permitido, sin userId expuesto en UI. */
data class M18EventParticipantItem(
    val registrationId: String,
    val displayAlias: String,
    val status: M18RegistrationStatus,
    val registeredAt: Long,
    val checkedInAt: Long? = null,
    val canCheckIn: Boolean = false,
    val canMarkAttendance: Boolean = false,
    val canMarkNoShow: Boolean = false
)

enum class M18ParticipantVisibility {
    HIDDEN,
    ALIAS_ONLY,
    ORGANIZER_ONLY
}

data class M18EventRegistrationFilter(
    val status: M18RegistrationStatus? = null,
    val includeCancelled: Boolean = false
)

data class M18EventReminder(
    val id: String,
    val eventId: String,
    val userId: String,
    val scheduledFor: Long,
    val status: M18ReminderStatus,
    val sentAt: Long? = null
)

data class M18EventSearchFilter(
    val query: String = "",
    val type: M18EventType? = null,
    val organizationId: String? = null,
    val activeOnly: Boolean = true,
    val completedOnly: Boolean = false,
    val withOpenSpotsOnly: Boolean = false,
    val upcomingOnly: Boolean = true,
    val locationQuery: String = "",
    val freeOnly: Boolean = false
)

object M18PermissionCodes {
    const val EVENT_VIEW = "event.view"
    const val EVENT_MANAGE = "event.manage"
}

object M18M06Hooks {
    const val EVENT_CREATED = "M18_EVENT_CREATED"
    const val EVENT_PUBLISHED = "M18_EVENT_PUBLISHED"
    const val REGISTRATION_CONFIRMED = "M18_REGISTRATION_CONFIRMED"
    const val WAITLIST_JOINED = "M18_WAITLIST_JOINED"
    const val WAITLIST_PROMOTED = "M18_WAITLIST_PROMOTED"
    const val REGISTRATION_CANCELLED = "M18_REGISTRATION_CANCELLED"
    const val REMINDER_SCHEDULED = "M18_REMINDER_SCHEDULED"
    const val CHECK_IN_RECORDED = "M18_CHECK_IN_RECORDED"
    const val EVENT_SCHEDULE_CHANGED = "M18_EVENT_SCHEDULE_CHANGED"
    const val EVENT_CANCELLED = "M18_EVENT_CANCELLED"
    const val INFRASTRUCTURE = "M18_NOTIFICATION_INFRASTRUCTURE"
}

object M18MockOrganizations {
    const val ORG_NORTE = M16MockOrganizations.ORG_NORTE
    const val ORG_SUR = M16MockOrganizations.ORG_SUR
    const val ORG_OESTE = M16MockOrganizations.ORG_OESTE

    val MANAGE_ORGANIZATION_IDS = M16MockOrganizations.MANAGE_ORGANIZATION_IDS
}

val M18_ELIGIBLE_ORGANIZATION_TYPES: Set<OrganizationType> = setOf(
    OrganizationType.SHELTER,
    OrganizationType.RESCUE_GROUP,
    OrganizationType.NGO,
    OrganizationType.TRAINING_CENTER
)

data class CreateM18EventInput(
    val organizationId: String,
    val title: String,
    val description: String,
    val eventType: M18EventType,
    val maxCapacity: Int,
    val waitlistEnabled: Boolean = true,
    val venueName: String? = null,
    val reference: M18EventReference = M18EventReference(),
    val coverImageRef: String? = null,
    val startsAt: Long,
    val endsAt: Long,
    val checkInOpensAt: Long? = null,
    val checkInClosesAt: Long? = null
)

data class UpdateM18EventDetailsInput(
    val eventId: String,
    val title: String,
    val description: String,
    val eventType: M18EventType,
    val venueName: String? = null,
    val reference: M18EventReference = M18EventReference(),
    val startsAt: Long,
    val endsAt: Long
)

data class UpdateM18EventCapacityInput(
    val eventId: String,
    val maxCapacity: Int,
    val waitlistEnabled: Boolean
)

object M18CapacityCalculator {
    fun summarize(
        maxCapacity: Int,
        waitlistEnabled: Boolean,
        registrations: List<M18EventRegistration>
    ): M18EventCapacitySummary {
        val active = registrations.filter { it.status.occupiesCapacity }
        val waitlisted = registrations.count { it.status == M18RegistrationStatus.WAITLISTED }
        val registeredCount = active.size
        val available = (maxCapacity - registeredCount).coerceAtLeast(0)
        return M18EventCapacitySummary(
            maxCapacity = maxCapacity,
            registeredCount = registeredCount,
            waitlistCount = waitlisted,
            availableSpots = available,
            isFull = available == 0,
            isWaitlistOpen = waitlistEnabled && available == 0
        )
    }

    fun registrationStats(registrations: List<M18EventRegistration>): M18PublicRegistrationStats =
        M18PublicRegistrationStats(
            registeredCount = registrations.count { it.status.occupiesCapacity },
            waitlistCount = registrations.count { it.status == M18RegistrationStatus.WAITLISTED },
            checkedInCount = registrations.count {
                it.status == M18RegistrationStatus.CHECKED_IN ||
                    it.status == M18RegistrationStatus.ATTENDED
            }
        )
}

object M18PrivacySanitizer {
    private val emailPattern = Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
    private val phonePattern = Regex("(?i)(\\+?\\d[\\d\\s().-]{6,}\\d)")

    fun scrubPublicText(text: String): String =
        text.replace(emailPattern, "[redactado]").replace(phonePattern, "[redactado]")

    fun toPublicEvent(
        event: M18CommunityEvent,
        summary: M18EventCapacitySummary
    ): M18PublicEvent {
        val registrationOpen = event.status == M18EventStatus.PUBLISHED &&
            (summary.availableSpots > 0 || summary.isWaitlistOpen)
        return M18PublicEvent(
            id = event.id,
            title = scrubPublicText(event.title),
            description = scrubPublicText(event.description),
            organizationDisplayName = scrubPublicText(event.organizationDisplayName),
            eventType = event.eventType,
            status = event.status,
            venueName = event.venueName?.let { scrubPublicText(it) },
            reference = event.reference.copy(
                publicLocationText = event.reference.publicLocationText?.let { scrubPublicText(it) }
            ),
            coverImageRef = event.coverImageRef,
            maxCapacity = summary.maxCapacity,
            registeredCount = summary.registeredCount,
            waitlistCount = summary.waitlistCount,
            availableSpots = summary.availableSpots,
            isFull = summary.isFull,
            isWaitlistOpen = summary.isWaitlistOpen,
            isRegistrationOpen = registrationOpen,
            startsAt = event.startsAt,
            endsAt = event.endsAt
        )
    }

    fun toSummary(
        event: M18CommunityEvent,
        summary: M18EventCapacitySummary
    ): M18EventSummary = M18EventSummary(
        id = event.id,
        title = scrubPublicText(event.title),
        organizationDisplayName = scrubPublicText(event.organizationDisplayName),
        eventType = event.eventType,
        status = event.status,
        startsAt = event.startsAt,
        maxCapacity = summary.maxCapacity,
        registeredCount = summary.registeredCount,
        availableSpots = summary.availableSpots,
        venueName = event.venueName?.let { scrubPublicText(it) },
        publicLocationText = event.reference.publicLocationText
    )
}
