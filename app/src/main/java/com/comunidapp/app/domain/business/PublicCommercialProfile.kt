package com.comunidapp.app.domain.business

import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.data.model.ServiceProfile
import com.comunidapp.app.domain.context.ContextHumanLabels
import com.comunidapp.app.domain.schedule.WeeklyHoursDay

/**
 * User-facing commercial ficha. No IDs, enums, codes, or DB field names.
 */
data class PublicCommercialProfile(
    val name: String,
    val categoryLabel: String,
    val description: String?,
    val phone: String?,
    val location: String?,
    val hours: List<WeeklyHoursDay>,
    val photoUrl: String?,
    val bookingsEnabled: Boolean,
    val servicesSummary: List<String> = emptyList()
)

object PublicCommercialProfileMapper {

    fun fromService(profile: ServiceProfile): PublicCommercialProfile = PublicCommercialProfile(
        name = profile.name.trim().ifBlank { "Servicio" },
        categoryLabel = ContextHumanLabels.serviceCategoryLabel(profile.category),
        description = profile.description.trim().takeIf { it.isNotBlank() },
        phone = profile.contactInfo?.trim()?.takeIf { it.isNotBlank() },
        location = profile.location.trim().takeIf { it.isNotBlank() },
        hours = profile.weeklyHours,
        photoUrl = profile.photoUrl,
        bookingsEnabled = profile.acceptsBookings,
        servicesSummary = profile.tags.map { it.trim() }.filter { it.isNotBlank() }
    )

    fun isTechnicalText(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        if (ContextHumanLabels.looksRawEnum(trimmed)) return true
        if (trimmed.startsWith("id", ignoreCase = true) && trimmed.contains('=')) return true
        val uuid =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        if (uuid.matches(trimmed)) return true
        return trimmed.contains("organization_id", ignoreCase = true) ||
            trimmed.contains("holder_kind", ignoreCase = true) ||
            trimmed.contains("service_provider", ignoreCase = true)
    }

    fun serviceCategoryOrNull(raw: String?): ServiceCategory? =
        com.comunidapp.app.domain.canonical.CanonicalProviderWrite.fromStorageCategory(raw.orEmpty())
}
