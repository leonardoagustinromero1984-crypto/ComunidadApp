package com.comunidapp.app.data.remote.supabase.m09

/**
 * LeoVer M09 — typed adoption failure with stable domain code.
 */
class M09AdoptionException(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object M09AdoptionErrorMapper {

    private val knownCodes = listOf(
        "ADOPTION_NOT_FOUND",
        "ADOPTION_ALREADY_EXISTS",
        "PET_NOT_ADOPTABLE",
        "ADOPTION_NOT_EDITABLE",
        "ADOPTION_ALREADY_PAUSED",
        "ADOPTION_ALREADY_CLOSED",
        "ADOPTION_ALREADY_ADOPTED",
        "ADOPTION_TITLE_REQUIRED",
        "ADOPTION_DESCRIPTION_REQUIRED",
        "ADOPTION_STATUS_INVALID",
        "ADOPTION_USE_MARK_ADOPTED",
        "ADOPTION_USE_FINALIZE",
        "PET_NOT_FOUND",
        "APPLICATION_NOT_FOUND",
        "APPLICATION_ALREADY_EXISTS",
        "APPLICATION_NOT_ACTIVE",
        "APPLICATION_ALREADY_ACCEPTED",
        "APPLICATION_ALREADY_REJECTED",
        "APPLICATION_ALREADY_WITHDRAWN",
        "ADOPTION_NOT_ACCEPTING_APPLICATIONS",
        "CANNOT_APPLY_TO_OWN_ADOPTION",
        "APPLICATION_FORBIDDEN",
        "APPLICATION_INVALID_TRANSITION",
        "APPLICATION_MESSAGE_REQUIRED",
        "INTERVIEW_NOT_FOUND",
        "INTERVIEW_NOT_ALLOWED",
        "INTERVIEW_ALREADY_COMPLETED",
        "INTERVIEW_INVALID_TRANSITION",
        "DOCUMENT_REQUIREMENT_NOT_FOUND",
        "DOCUMENT_NOT_APPROVED",
        "DOCUMENT_FORBIDDEN",
        "DOCUMENT_UNSAFE_REFERENCE",
        "AGREEMENT_NOT_FOUND",
        "AGREEMENT_NOT_ACCEPTED",
        "AGREEMENT_ALREADY_ACCEPTED",
        "AGREEMENT_ALREADY_EXISTS",
        "AGREEMENT_FORBIDDEN",
        "ADOPTION_NOT_READY_TO_FINALIZE",
        "ADOPTION_ALREADY_FINALIZED",
        "ADOPTION_TRANSFER_FAILED",
        "FOLLOWUP_NOT_FOUND",
        "FOLLOWUP_ALREADY_COMPLETED",
        "FOLLOWUP_FORBIDDEN",
        "NOT_AUTHENTICATED",
        "FORBIDDEN",
        "NETWORK",
        "TIMEOUT",
        "SERIALIZATION"
    )

    fun codeOf(throwable: Throwable): String {
        if (throwable is M09AdoptionException) return throwable.code
        val signal = listOfNotNull(throwable.message, throwable.cause?.message)
            .joinToString(" ")
            .uppercase()
        knownCodes.forEach { code ->
            if (signal.contains(code)) return code
        }
        return when {
            "APPLICATIONS_ONE_ACTIVE" in signal || "ADOPTION_APPLICATIONS_ONE_ACTIVE" in signal ->
                "APPLICATION_ALREADY_EXISTS"
            "UNIQUE" in signal || "ADOPTIONS_ONE_OPEN" in signal -> "ADOPTION_ALREADY_EXISTS"
            "NETWORK" in signal || "UNABLE TO RESOLVE" in signal -> "NETWORK"
            "TIMEOUT" in signal -> "TIMEOUT"
            "SERIALIZ" in signal || "JSON" in signal -> "SERIALIZATION"
            else -> "FORBIDDEN"
        }
    }

    fun userMessage(code: String): String = when (code) {
        "ADOPTION_NOT_FOUND" -> "No encontramos esa publicación de adopción."
        "ADOPTION_ALREADY_EXISTS" -> "Ya hay una publicación abierta para esa mascota."
        "PET_NOT_ADOPTABLE" -> "Esa mascota no se puede publicar en adopción (fallecida, archivada o no activa)."
        "ADOPTION_NOT_EDITABLE" -> "Esta publicación no se puede editar en su estado actual."
        "ADOPTION_ALREADY_PAUSED" -> "La publicación ya está pausada."
        "ADOPTION_ALREADY_CLOSED" -> "La publicación ya está cerrada."
        "ADOPTION_ALREADY_ADOPTED" -> "La mascota ya figura como adoptada."
        "ADOPTION_TITLE_REQUIRED" -> "Indicá un título para la publicación."
        "ADOPTION_DESCRIPTION_REQUIRED" -> "Indicá una descripción."
        "ADOPTION_STATUS_INVALID" -> "Estado de publicación no válido."
        "PET_NOT_FOUND" -> "No encontramos la mascota."
        "APPLICATION_NOT_FOUND" -> "No encontramos esa postulación."
        "APPLICATION_ALREADY_EXISTS" -> "Ya tenés una postulación activa para esta publicación."
        "APPLICATION_NOT_ACTIVE" -> "Esta postulación ya no está activa."
        "APPLICATION_ALREADY_ACCEPTED" -> "Esta postulación ya fue aceptada."
        "APPLICATION_ALREADY_REJECTED" -> "Esta postulación ya fue rechazada."
        "APPLICATION_ALREADY_WITHDRAWN" -> "Esta postulación ya fue retirada."
        "ADOPTION_NOT_ACCEPTING_APPLICATIONS" -> "Esta publicación no está recibiendo postulaciones."
        "CANNOT_APPLY_TO_OWN_ADOPTION" -> "No podés postularte a tu propia publicación."
        "APPLICATION_FORBIDDEN" -> "No tenés permiso para esta postulación."
        "APPLICATION_INVALID_TRANSITION" -> "Ese cambio de estado no está permitido."
        "APPLICATION_MESSAGE_REQUIRED" -> "Escribí un mensaje para el responsable."
        "INTERVIEW_NOT_FOUND" -> "No encontramos esa entrevista."
        "INTERVIEW_NOT_ALLOWED" -> "No se puede agendar una entrevista en este estado."
        "INTERVIEW_ALREADY_COMPLETED" -> "La entrevista ya está completada."
        "INTERVIEW_INVALID_TRANSITION" -> "Ese cambio de estado de entrevista no está permitido."
        "DOCUMENT_REQUIREMENT_NOT_FOUND" -> "No encontramos ese requisito documental."
        "DOCUMENT_NOT_APPROVED" -> "Falta documentación obligatoria aprobada."
        "DOCUMENT_FORBIDDEN" -> "No tenés permiso para esta documentación."
        "DOCUMENT_UNSAFE_REFERENCE" -> "No se permite una URL pública para documentos personales."
        "AGREEMENT_NOT_FOUND" -> "No encontramos el acuerdo de adopción."
        "AGREEMENT_NOT_ACCEPTED" -> "El acuerdo todavía no está aceptado por ambas partes."
        "AGREEMENT_ALREADY_ACCEPTED" -> "El acuerdo ya está aceptado."
        "AGREEMENT_ALREADY_EXISTS" -> "Ya hay un acuerdo activo para esta adopción."
        "AGREEMENT_FORBIDDEN" -> "No tenés permiso sobre este acuerdo."
        "ADOPTION_NOT_READY_TO_FINALIZE" -> "La adopción todavía no está lista para finalizar."
        "ADOPTION_ALREADY_FINALIZED" -> "Esta adopción ya fue finalizada."
        "ADOPTION_USE_FINALIZE" ->
            "Para completar la adopción usá el proceso post-aceptación (entrevista, docs, acuerdo y finalización)."
        "ADOPTION_TRANSFER_FAILED" -> "No se pudo transferir la responsabilidad de la mascota."
        "FOLLOWUP_NOT_FOUND" -> "No encontramos ese control de seguimiento."
        "FOLLOWUP_ALREADY_COMPLETED" -> "Ese control ya fue completado."
        "FOLLOWUP_FORBIDDEN" -> "No tenés permiso para el seguimiento."
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión."
        "NETWORK" -> "Problema de conexión. Intentá de nuevo."
        "TIMEOUT" -> "La operación tardó demasiado. Intentá de nuevo."
        "SERIALIZATION" -> "No pudimos interpretar la respuesta del servidor."
        "FORBIDDEN" -> "No tenés permiso para esta acción."
        else -> "No se pudo completar la operación."
    }

    fun <T> failure(throwable: Throwable): Result<T> {
        val code = codeOf(throwable)
        return Result.failure(M09AdoptionException(code, userMessage(code), throwable))
    }
}
