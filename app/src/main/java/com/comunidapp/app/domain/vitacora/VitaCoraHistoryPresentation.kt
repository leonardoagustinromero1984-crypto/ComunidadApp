package com.comunidapp.app.domain.vitacora



import com.comunidapp.app.data.model.M14PassportHistory

import com.comunidapp.app.data.model.M14PassportStatus

import java.time.Instant

import java.time.ZoneId

import java.time.format.DateTimeFormatter

import java.util.Locale



object VitaCoraHistoryPresentation {

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "AR"))
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale("es", "AR"))

    fun titleFor(item: M14PassportHistory): String {

        item.metadataEvent?.let { kind ->

            momentTitle(kind, item)?.let { return it }

        }

        val reason = item.reason?.trim().orEmpty()

        if (reason.isNotBlank() && !looksTechnical(reason)) {

            return humanizeReason(reason)

        }

        return when (item.toStatus) {

            M14PassportStatus.ARCHIVED -> "Se archivó la VitaCora"
            M14PassportStatus.SUSPENDED -> "VitaCora suspendida"

            M14PassportStatus.REVOKED -> "VitaCora revocada"

            M14PassportStatus.ACTIVE -> when (item.fromStatus) {

                null, M14PassportStatus.DRAFT -> "Se activó la VitaCora"

                else -> "Se actualizó la VitaCora"

            }

            M14PassportStatus.DRAFT -> "Se creó la VitaCora"

        }

    }



    fun detailFor(item: M14PassportHistory): String? {

        item.metadataEvent?.let { kind ->
            if (kind.equals("CARE_CREATED", ignoreCase = true) ||
                kind.equals("CARE_TRANSFER", ignoreCase = true)
            ) {
                return null
            }

            val reason = item.reason?.trim().orEmpty()
            if (reason in GENERIC_SOCIAL_TITLES) return null
            reason.takeIf { it.isNotBlank() && !looksTechnical(it) }?.let { return it }
            momentTitle(kind, item)
            return null

        }

        return null

    }



    fun formatDate(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        return runCatching {
            val zoned = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
            // Real timestamptz events always carry clock time; show date + hour.
            zoned.format(dateTimeFormatter)
        }.getOrDefault("")
    }

    /** Date-only when no reliable clock time exists (midnight local from date-only sources). */
    fun formatDatePreferringTime(epochMs: Long, hasRealClockTime: Boolean = true): String {
        if (epochMs <= 0L) return ""
        return runCatching {
            val zoned = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
            if (!hasRealClockTime) zoned.format(dateFormatter) else zoned.format(dateTimeFormatter)
        }.getOrDefault("")
    }



    fun isPlayableVideo(item: M14PassportHistory): Boolean =
        com.comunidapp.app.domain.vitacora.VitaCoraSocialMedia.isVideo(
            item.mediaMime,
            item.sourceContentKind,
            item.mediaDisplayUrl
        )

    fun isPlayableImage(item: M14PassportHistory): Boolean =
        !item.mediaDisplayUrl.isNullOrBlank() && !isPlayableVideo(item)

    private val GENERIC_SOCIAL_TITLES = setOf(
        "Clip en VitaCora",
        "Reel en VitaCora",
        "Historia en VitaCora",
        "Publicación en VitaCora"
    )

    private fun momentTitle(kind: String, item: M14PassportHistory): String? = when (kind.uppercase()) {

        "ARRIVAL" -> "Llegada a la familia"

        "BIRTHDAY" -> "Cumpleaños"

        "MEMORY", "PHOTO" -> "Se guardó un recuerdo"

        "MILESTONE" -> "Nuevo hito"

        "NOTE" -> "Se agregó una nota"

        "TRIP" -> "Viaje registrado"

        "SOCIAL" -> when {
            item.sourceContentKind.equals("REEL", ignoreCase = true) ||
                isPlayableVideo(item) -> "Se guardó un Clip"
            item.sourceContentKind.equals("POST", ignoreCase = true) -> "Se guardó una publicación"
            else -> "Se guardó una historia"
        }

        "CARE_CREATED" -> item.reason?.trim()?.takeIf { it.isNotBlank() }
            ?: "Se creó la VitaCora"
        "CARE_TRANSFER" -> item.reason?.trim()?.takeIf { it.isNotBlank() }
            ?: "La mascota pasó a estar bajo un nuevo cuidado"

        "WEIGHT" -> "Se actualizó el peso"

        "VACCINATION", "VACCINE" -> "Se agregó una vacuna"

        "HEALTH" -> "Se modificó información de salud"

        else -> null

    }



    private fun humanizeReason(reason: String): String = when (reason.uppercase()) {

        "DECEASED" -> "Registro de fallecimiento"

        "ARCHIVED" -> "Se archivó la VitaCora"

        "LOST" -> "Se reportó pérdida"

        else -> reason.replace('_', ' ').replaceFirstChar { it.titlecase(Locale("es", "AR")) }

    }



    private fun looksTechnical(value: String): Boolean {

        val upper = value.uppercase()

        return VitaCoraUserHistoryFilter.internalReasonMarkers.any { upper.contains(it) } ||

            upper.contains("→") ||

            upper.startsWith("M14.") ||

            upper.startsWith("CANON_")

    }

}


