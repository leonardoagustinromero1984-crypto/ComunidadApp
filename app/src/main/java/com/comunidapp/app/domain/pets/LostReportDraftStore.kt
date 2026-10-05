package com.comunidapp.app.domain.pets

/**
 * Fields typed before "crear mascota" survive the trip to the pet form
 * and the return to the lost report.
 */
data class LostReportDraft(
    val typeName: String,
    val petName: String,
    val speciesName: String,
    val location: String,
    val description: String,
    val contactInfo: String,
    val knownPetIds: Set<String>,
    val imageUri: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val foundSexName: String? = null,
    val foundSizeName: String? = null,
    val estimatedAgeYears: String = ""
)

object LostReportDraftStore {
    var current: LostReportDraft? = null

    fun capture(draft: LostReportDraft) {
        current = draft
    }

    fun peek(): LostReportDraft? = current

    fun clear() {
        current = null
    }
}
