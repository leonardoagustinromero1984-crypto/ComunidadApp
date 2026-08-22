package com.comunidapp.app.domain.vitacora



import com.comunidapp.app.data.model.M14PassportHistory

import com.comunidapp.app.data.model.M14PassportStatus

import java.time.Instant

import java.time.ZoneId

import java.time.format.DateTimeFormatter

import java.util.Locale



object VitaCoraHistoryPresentation {

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "AR"))



    fun titleFor(item: M14PassportHistory): String {

        item.metadataEvent?.let { kind ->

            momentTitle(kind)?.let { return it }

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

            item.reason?.takeIf { it.isNotBlank() && !looksTechnical(it) }?.let { return it }

            momentTitle(kind)?.let { return item.reason?.takeIf { r -> r.isNotBlank() } }

        }

        return null

    }



    fun formatDate(epochMs: Long): String {

        if (epochMs <= 0L) return ""

        return runCatching {

            Instant.ofEpochMilli(epochMs)

                .atZone(ZoneId.systemDefault())

                .format(dateFormatter)

        }.getOrDefault("")

    }



    private fun momentTitle(kind: String): String? = when (kind.uppercase()) {

        "ARRIVAL" -> "Llegada a la familia"

        "BIRTHDAY" -> "Cumpleaños"

        "MEMORY", "PHOTO" -> "Se guardó un recuerdo"

        "MILESTONE" -> "Nuevo hito"

        "NOTE" -> "Se agregó una nota"

        "TRIP" -> "Viaje registrado"

        "SOCIAL" -> "Se guardó una historia"

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


