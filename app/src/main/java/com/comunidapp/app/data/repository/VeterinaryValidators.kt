package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.VeterinaryClinicProfile
import com.comunidapp.app.data.model.VeterinaryOpeningHours
import com.comunidapp.app.data.model.VeterinaryService
import com.comunidapp.app.data.model.VeterinaryServiceCategory
import java.time.LocalTime

/**
 * LeoVer M12 — validadores puros de dominio (Bloque 1).
 */
object VeterinaryValidators {

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val urlRegex = Regex("^https?://[\\w.-]+(?:\\.[\\w.-]+)+(?:[/#?].*)?$", RegexOption.IGNORE_CASE)

    fun requireDisplayName(name: String?): String {
        val trimmed = name?.trim().orEmpty()
        require(trimmed.isNotEmpty()) { "VETERINARY_CLINIC_INVALID" }
        return trimmed
    }

    fun requireOrganizationId(organizationId: String?): String {
        val id = organizationId?.trim().orEmpty()
        require(id.isNotEmpty()) { "ORGANIZATION_NOT_ELIGIBLE" }
        return id
    }

    fun normalizeBranchId(branchId: String?): String? =
        branchId?.trim()?.takeIf { it.isNotEmpty() }

    fun requirePublicZone(zone: String?): String {
        val trimmed = zone?.trim().orEmpty()
        require(trimmed.isNotEmpty()) { "VETERINARY_CLINIC_INVALID" }
        return trimmed
    }

    fun normalizeLicense(license: String?): String? =
        license?.trim()?.takeIf { it.isNotEmpty() }

    fun validateEmail(email: String?) {
        if (email.isNullOrBlank()) return
        require(emailRegex.matches(email.trim())) { "VETERINARY_CLINIC_INVALID" }
    }

    fun validateWebsiteUrl(url: String?) {
        if (url.isNullOrBlank()) return
        require(urlRegex.matches(url.trim())) { "VETERINARY_CLINIC_INVALID" }
    }

    fun validateOpeningHours(row: VeterinaryOpeningHours) {
        if (row.closed) {
            require(row.opensAt == null && row.closesAt == null) {
                "VETERINARY_OPENING_HOURS_INVALID"
            }
            return
        }
        val opens = row.opensAt
        val closes = row.closesAt
        require(opens != null && closes != null) { "VETERINARY_OPENING_HOURS_INVALID" }
        require(closes.isAfter(opens)) { "VETERINARY_OPENING_HOURS_INVALID" }
    }

    fun isOpen24HoursSlot(opens: LocalTime?, closes: LocalTime?): Boolean {
        if (opens == null || closes == null) return false
        return opens == LocalTime.MIDNIGHT &&
            (closes == LocalTime.of(23, 59) || closes == LocalTime.MAX)
    }

    fun isOpen24HoursCoherent(
        isOpen24Hours: Boolean,
        hours: List<VeterinaryOpeningHours>
    ): Boolean {
        hours.forEach { row ->
            if (runCatching { validateOpeningHours(row) }.isFailure) return false
        }
        if (!isOpen24Hours) return true
        if (hours.isEmpty()) return true
        val openDays = hours.filter { !it.closed }
        if (openDays.isEmpty()) return false
        return openDays.all { isOpen24HoursSlot(it.opensAt, it.closesAt) }
    }

    fun isEmergencyCoherent(
        offersEmergencyCare: Boolean,
        services: List<VeterinaryService>
    ): Boolean {
        if (!offersEmergencyCare) return true
        if (services.isEmpty()) return true
        return services.any {
            it.active && (
                it.emergencyAvailable ||
                    it.category == VeterinaryServiceCategory.EMERGENCY_GUARD
                )
        }
    }

    fun validateMediaRef(ref: String?) {
        if (ref.isNullOrBlank()) return
        if (FosterSecureRefValidator.isUnsafePublicReference(ref)) {
            throw IllegalArgumentException("VETERINARY_MEDIA_REF_INVALID")
        }
        val lower = ref.trim().lowercase()
        if (!lower.startsWith("m05://") && !lower.startsWith("file_asset:")) {
            throw IllegalArgumentException("VETERINARY_MEDIA_REF_INVALID")
        }
    }

    fun isValidMediaRef(ref: String?): Boolean =
        runCatching { validateMediaRef(ref) }.isSuccess

    fun publicContactOrNull(
        enabled: Boolean,
        phone: String?,
        email: String?
    ): Pair<String?, String?> {
        if (!enabled) return null to null
        return phone?.trim()?.takeIf { it.isNotEmpty() } to
            email?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun validateDraftBasics(profile: VeterinaryClinicProfile) {
        requireDisplayName(profile.displayName)
        requireOrganizationId(profile.organizationId)
        requirePublicZone(profile.publicZoneText)
        validateEmail(profile.publicEmail)
        validateWebsiteUrl(profile.websiteUrl)
        validateMediaRef(profile.logoAssetRef)
        validateMediaRef(profile.coverAssetRef)
    }
}
