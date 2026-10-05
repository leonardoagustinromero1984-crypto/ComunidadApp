package com.comunidapp.app.domain.m18

import com.comunidapp.app.data.model.M18EventStatus
import com.comunidapp.app.data.model.M18EventType
import com.comunidapp.app.data.model.M18RegistrationStatus
import java.time.Instant
import java.time.ZoneId

data class MyEventRegistration(
    val eventId: String,
    val title: String,
    val organizationName: String,
    val startsAt: Long,
    val endsAt: Long,
    val eventStatus: M18EventStatus,
    val registrationStatus: M18RegistrationStatus,
    val venueName: String? = null,
    val locationText: String? = null
)

enum class EventActivityBucket {
    UPCOMING,
    WAITLIST,
    PAST
}

/**
 * Public event copy. The Argentina zone is used only to format the clock.
 * The zone id is never part of the visible text. Events have no stored zone.
 */
object EventPresentation {
    const val DISCOVER_TITLE = "Eventos"
    const val ACTIVITY_TITLE = "Mis eventos"
    const val EMPTY_ACTIVITY = "Todavía no te anotaste a ningún evento."
    const val REGISTER_ACTION = "Inscribirme"
    const val WAITLIST_ACTION = "Anotarme en lista de espera"
    const val CANCEL_ACTION = "Cancelar inscripción"
    const val LEAVE_WAITLIST_ACTION = "Salir de la lista de espera"
    const val WAITLIST_BEFORE = "No quedan lugares. Si te anotás, vas a quedar en lista de espera."
    const val WAITLIST_NOW = "Estás en lista de espera."
    const val WAITLIST_HOW = "Si se libera un lugar, pasa la primera persona de la lista."
    const val REGISTERED_NOW = "Estás inscripto."
    const val FULL_NO_WAITLIST = "Completo. Este evento no tiene lista de espera."
    const val CLOSED = "Este evento no acepta inscripciones."
    const val CANCELLED_EVENT = "Este evento fue cancelado."
    const val PAST_EVENT = "Este evento ya pasó."
    const val SECTION_UPCOMING = "Próximos"
    const val SECTION_WAITLIST = "Lista de espera"
    const val SECTION_PAST = "Anteriores"

    private val zone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    private val weekdays = listOf(
        "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"
    )
    private val months = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    )

    fun eventType(type: M18EventType): String = when (type) {
        M18EventType.ADOPTION_FAIR -> "Feria de adopciones"
        M18EventType.VOLUNTEER_DAY -> "Jornada de voluntariado"
        M18EventType.TRAINING_WORKSHOP -> "Taller"
        M18EventType.COMMUNITY_GATHERING -> "Encuentro"
        M18EventType.FREE_FUNDRAISER -> "Recaudación"
        M18EventType.AWARENESS_WALK -> "Caminata"
    }

    fun eventStatus(status: M18EventStatus): String = when (status) {
        M18EventStatus.DRAFT -> "Borrador"
        M18EventStatus.PUBLISHED -> "Publicado"
        M18EventStatus.PAUSED -> "Pausado"
        M18EventStatus.COMPLETED -> "Finalizado"
        M18EventStatus.CANCELLED -> "Cancelado"
    }

    fun registrationStatus(status: M18RegistrationStatus): String = when (status) {
        M18RegistrationStatus.REGISTERED -> "Inscripto"
        M18RegistrationStatus.WAITLISTED -> "En lista de espera"
        M18RegistrationStatus.CANCELLED -> "Inscripción cancelada"
        M18RegistrationStatus.CHECKED_IN -> "Ingreso registrado"
        M18RegistrationStatus.ATTENDED -> "Asistió"
        M18RegistrationStatus.NO_SHOW -> "No registró asistencia"
        M18RegistrationStatus.REJECTED -> "No aceptada"
    }

    fun ownRegistration(status: M18RegistrationStatus): String = when (status) {
        M18RegistrationStatus.REGISTERED -> REGISTERED_NOW
        M18RegistrationStatus.WAITLISTED -> WAITLIST_NOW
        M18RegistrationStatus.CANCELLED -> "Inscripción cancelada."
        M18RegistrationStatus.CHECKED_IN -> "Ya registramos tu ingreso."
        M18RegistrationStatus.ATTENDED -> "Asististe."
        M18RegistrationStatus.NO_SHOW -> "No registró asistencia."
        M18RegistrationStatus.REJECTED -> "Tu inscripción no fue aceptada."
    }

    fun availability(maxCapacity: Int, availableSpots: Int, waitlistOpen: Boolean): String? {
        if (maxCapacity <= 0) return null
        return when {
            availableSpots > 1 -> "Quedan $availableSpots lugares"
            availableSpots == 1 -> "Queda 1 lugar"
            waitlistOpen -> "Completo. Hay lista de espera."
            else -> "Completo"
        }
    }

    fun whenLine(startsAt: Long, endsAt: Long): String {
        val start = Instant.ofEpochMilli(startsAt).atZone(zone)
        val end = Instant.ofEpochMilli(endsAt).atZone(zone)
        val date = "${weekdays[start.dayOfWeek.value - 1]} ${start.dayOfMonth} de ${months[start.monthValue - 1]}"
        val startTime = "%02d:%02d".format(start.hour, start.minute)
        val endTime = "%02d:%02d".format(end.hour, end.minute)
        return if (start.toLocalDate() == end.toLocalDate()) {
            "$date · $startTime a $endTime"
        } else {
            "$date · $startTime"
        }
    }

    fun placeLine(venueName: String?, locationText: String?): String? {
        val parts = listOfNotNull(
            venueName?.trim()?.takeIf { it.isNotEmpty() && !isBlankToken(it) },
            locationText?.trim()?.takeIf { it.isNotEmpty() && !isBlankToken(it) }
        ).distinct()
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun bucket(item: MyEventRegistration, now: Long = System.currentTimeMillis()): EventActivityBucket {
        if (item.registrationStatus == M18RegistrationStatus.WAITLISTED &&
            item.eventStatus == M18EventStatus.PUBLISHED &&
            item.endsAt >= now
        ) {
            return EventActivityBucket.WAITLIST
        }
        val active = item.registrationStatus == M18RegistrationStatus.REGISTERED ||
            item.registrationStatus == M18RegistrationStatus.CHECKED_IN
        val upcoming = item.eventStatus == M18EventStatus.PUBLISHED && item.endsAt >= now
        return if (active && upcoming) EventActivityBucket.UPCOMING else EventActivityBucket.PAST
    }

    private fun isBlankToken(value: String): Boolean {
        val token = value.trim()
        return token.equals("N/A", ignoreCase = true) || token == "-" || token.equals("null", ignoreCase = true)
    }
}
