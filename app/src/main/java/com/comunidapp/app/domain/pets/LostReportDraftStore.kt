package com.comunidapp.app.domain.pets

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Fields typed before "crear mascota" survive the trip to the pet form,
 * activity recreation, and a process death while the form entry is alive.
 * Publish and a definitive cancel clear it so a new alert starts empty.
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
    val estimatedAgeYears: String = "",
    val boundPetId: String? = null,
    val occurredAtEpochMs: Long? = null,
    val notes: String = ""
)

object LostReportUriGrant {
    fun shouldPersist(uri: String?): Boolean = uri?.startsWith("content:") == true
}

object LostReportDraftCodec {
    private val utf8 = StandardCharsets.UTF_8.name()

    fun encode(draft: LostReportDraft): String = listOf(
        draft.typeName,
        draft.petName,
        draft.speciesName,
        draft.location,
        draft.description,
        draft.contactInfo,
        draft.knownPetIds.sorted().joinToString(","),
        draft.imageUri.orEmpty(),
        draft.latitude?.toString().orEmpty(),
        draft.longitude?.toString().orEmpty(),
        draft.foundSexName.orEmpty(),
        draft.foundSizeName.orEmpty(),
        draft.estimatedAgeYears,
        draft.boundPetId.orEmpty(),
        draft.occurredAtEpochMs?.toString().orEmpty(),
        draft.notes
    ).joinToString("\n") { URLEncoder.encode(it, utf8) }

    fun decode(raw: String?): LostReportDraft? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split("\n").map { URLDecoder.decode(it, utf8) }
        if (parts.size < 13) return null
        return LostReportDraft(
            typeName = parts[0],
            petName = parts[1],
            speciesName = parts[2],
            location = parts[3],
            description = parts[4],
            contactInfo = parts[5],
            knownPetIds = parts[6].split(",").filter { it.isNotEmpty() }.toSet(),
            imageUri = parts[7].ifBlank { null },
            latitude = parts[8].toDoubleOrNull(),
            longitude = parts[9].toDoubleOrNull(),
            foundSexName = parts[10].ifBlank { null },
            foundSizeName = parts[11].ifBlank { null },
            estimatedAgeYears = parts[12],
            boundPetId = parts.getOrNull(13)?.ifBlank { null },
            occurredAtEpochMs = parts.getOrNull(14)?.toLongOrNull(),
            notes = parts.getOrNull(15).orEmpty()
        )
    }
}
