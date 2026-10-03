package com.comunidapp.app.domain.organization

import com.comunidapp.app.data.model.M16OpeningHours
import com.comunidapp.app.data.model.M16PublicContactChannel
import com.comunidapp.app.data.model.M16PublicContactChannelType
import com.comunidapp.app.data.model.M16ShelterAvailabilityStatus
import com.comunidapp.app.data.model.M16ShelterNeed
import com.comunidapp.app.data.model.M16ShelterOperationalStatus
import com.comunidapp.app.data.model.M16ShelterOperationsFilter
import com.comunidapp.app.data.model.M16ShelterPetOperationalStatus
import com.comunidapp.app.data.model.M16ShelterService
import com.comunidapp.app.data.model.M16ShelterVerificationStatus
import com.comunidapp.app.data.model.visibleLabel
import com.comunidapp.app.domain.capability.CapabilityFacts
import com.comunidapp.app.domain.capability.CapabilityGate

/**
 * Public language for a shelter or organization.
 * Raw enum names and IANA time zones stay out of the public card.
 * The time zone remains on [M16OpeningHours] for schedules. It is not profile content.
 */
object OrganizationPresentation {
    const val VERIFIED_BADGE = "Organización verificada"

    /**
     * ACTIVE is the only status that accepts intake.
     * PAUSED, TEMPORARILY_CLOSED and PERMANENTLY_CLOSED tell a visitor
     * the organization is not taking animals right now.
     */
    fun operationalStatus(status: M16ShelterOperationalStatus): String = when (status) {
        M16ShelterOperationalStatus.ACTIVE -> "Atiende actualmente"
        M16ShelterOperationalStatus.PAUSED -> "Pausado temporalmente"
        M16ShelterOperationalStatus.TEMPORARILY_CLOSED -> "Cerrado temporalmente"
        M16ShelterOperationalStatus.PERMANENTLY_CLOSED -> "Cerrado de forma permanente"
    }

    /**
     * LIMITED means the published, active shelter already houses animals and still has free slots.
     * It is not an administrative "limited admissions" flag.
     */
    fun availability(status: M16ShelterAvailabilityStatus): String = when (status) {
        M16ShelterAvailabilityStatus.AVAILABLE -> "Hay cupos"
        M16ShelterAvailabilityStatus.LIMITED -> "Hay cupos"
        M16ShelterAvailabilityStatus.FULL -> "Sin cupos"
        M16ShelterAvailabilityStatus.UNAVAILABLE -> "No está recibiendo"
    }

    fun showAvailability(operational: M16ShelterOperationalStatus): Boolean =
        operational == M16ShelterOperationalStatus.ACTIVE

    /** Only a real verified organization gets a badge. Everyone else gets none. */
    fun verifiedBadge(status: M16ShelterVerificationStatus): String? =
        if (status == M16ShelterVerificationStatus.VERIFIED) VERIFIED_BADGE else null

    fun organizationVerifiedBadge(status: OrganizationVerificationStatus): String? =
        if (status == OrganizationVerificationStatus.VERIFIED) VERIFIED_BADGE else null

    fun organizationType(type: OrganizationType): String = when (type) {
        OrganizationType.SHELTER -> "Refugio"
        OrganizationType.RESCUE_GROUP -> "Grupo de rescate"
        OrganizationType.NGO -> "ONG"
        OrganizationType.VETERINARY_CLINIC -> "Veterinaria"
        OrganizationType.PET_SHOP -> "Tienda de mascotas"
        OrganizationType.TRAINING_CENTER -> "Centro de adiestramiento"
        OrganizationType.WALKER_AGENCY -> "Paseadores"
        OrganizationType.OTHER -> "Organización"
    }

    fun service(service: M16ShelterService): String = service.visibleLabel()

    /** Species the organization works with. Unknown codes are omitted, not printed. */
    fun species(code: String): String? = when (code.trim().uppercase()) {
        "DOG" -> "Perros"
        "CAT" -> "Gatos"
        "HORSE" -> "Caballos"
        "BIRD" -> "Aves"
        "RABBIT" -> "Conejos"
        "OTHER" -> "Otras especies"
        else -> null
    }

    fun speciesList(codes: Set<String>): List<String> =
        codes.mapNotNull { species(it) }.distinct()

    fun needLine(need: M16ShelterNeed): String? {
        val headline = when (need.category.trim().uppercase()) {
            "FOOD" -> "Necesita alimento"
            "HYGIENE" -> "Necesita artículos de higiene"
            "MEDICATION" -> "Necesita medicación"
            "BEDDING" -> "Necesita abrigo"
            "TRANSPORT", "TRANSPORT_SUPPLIES" -> "Necesita transporte"
            else -> null
        }
        val detail = need.description.trim().takeIf { isHumanText(it) }
        return when {
            headline != null && detail != null -> "$headline. $detail"
            headline != null -> headline
            detail != null -> detail
            else -> null
        }
    }

    fun contactLine(channel: M16PublicContactChannel): String? {
        val value = channel.value.trim()
        if (!isHumanText(value)) return null
        val kind = channel.label?.trim()?.takeIf { isHumanText(it) } ?: contactKind(channel.type)
        return "$kind: $value"
    }

    fun contactKind(type: M16PublicContactChannelType): String = when (type) {
        M16PublicContactChannelType.INSTITUTIONAL_EMAIL -> "Email institucional"
        M16PublicContactChannelType.INSTITUTIONAL_PHONE -> "Teléfono"
        M16PublicContactChannelType.WEBSITE -> "Sitio web"
        M16PublicContactChannelType.SOCIAL -> "Red social"
        M16PublicContactChannelType.MESSAGING -> "Mensajería"
    }

    /** Local opening hours. The IANA zone is not included. */
    fun openingLines(hours: M16OpeningHours): List<String> {
        if (hours.periods.isEmpty()) return emptyList()
        return hours.periods.groupBy { it.dayOfWeek }.toSortedMap().map { (day, periods) ->
            val span = periods.joinToString("; ") { period ->
                if (period.closed) "Cerrado"
                else listOfNotNull(period.openTime, period.closeTime)
                    .filter { it.isNotBlank() }
                    .joinToString(" – ")
                    .ifBlank { "Horario a confirmar" }
            }
            "${dayName(day)}: $span"
        }
    }

    fun publicImageUrl(ref: String?): String? {
        val value = ref?.trim().orEmpty()
        return value.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }

    fun operationsFilter(filter: M16ShelterOperationsFilter): String = when (filter) {
        M16ShelterOperationsFilter.ALL -> "Todas"
        M16ShelterOperationsFilter.HOUSED -> "En el refugio"
        M16ShelterOperationsFilter.RESERVED -> "Reservadas"
        M16ShelterOperationsFilter.IN_FOSTER -> "En tránsito"
        M16ShelterOperationsFilter.IN_ADOPTION -> "En adopción"
        M16ShelterOperationsFilter.ADOPTED -> "Adoptadas"
        M16ShelterOperationsFilter.INCONSISTENT -> "Para revisar"
    }

    fun petOperationalStatus(status: M16ShelterPetOperationalStatus): String = when (status) {
        M16ShelterPetOperationalStatus.PHYSICALLY_HOUSED -> "En el refugio"
        M16ShelterPetOperationalStatus.RESERVED_SLOT -> "Cupo reservado"
        M16ShelterPetOperationalStatus.IN_ACTIVE_FOSTER -> "En tránsito"
        M16ShelterPetOperationalStatus.ACTIVE_ADOPTION_PROCESS -> "En adopción"
        M16ShelterPetOperationalStatus.RECENTLY_ADOPTED -> "Adoptada recientemente"
        M16ShelterPetOperationalStatus.INACTIVE -> "Inactiva"
        M16ShelterPetOperationalStatus.INCONSISTENT -> "Para revisar"
    }

    private fun dayName(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "Lunes"
        2 -> "Martes"
        3 -> "Miércoles"
        4 -> "Jueves"
        5 -> "Viernes"
        6 -> "Sábado"
        7 -> "Domingo"
        else -> "Día"
    }

    private fun isHumanText(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.isNotEmpty() &&
            !trimmed.equals("null", ignoreCase = true) &&
            trimmed != "-" &&
            !trimmed.equals("N/A", ignoreCase = true)
    }
}

/**
 * Public directory search. Matches the repository filter:
 * display name, public zone text, and description.
 * It does not search email, member data, or ids.
 */
object OrganizationPublicSearch {
    const val PLACEHOLDER = "Nombre, localidad o descripción"
    val fields: List<String> = listOf("displayName", "publicZoneText", "description")
    val notSearched: List<String> = listOf("email", "publicContacts", "ids", "internalNotes", "members")

    /** Visitor filters that describe a real choice. Operational status stays off this list. */
    val visitorFilters: List<String> = listOf("localidad en la búsqueda", "especie", "actividad", "organizaciones verificadas")
}

/**
 * Management is membership in the active organization context.
 * Creating a record does not grant it.
 */
object OrganizationManageAccess {
    fun allows(facts: CapabilityFacts, memberOfOrganization: Boolean): Boolean =
        CapabilityGate.canManageOrganization(facts) && memberOfOrganization
}
