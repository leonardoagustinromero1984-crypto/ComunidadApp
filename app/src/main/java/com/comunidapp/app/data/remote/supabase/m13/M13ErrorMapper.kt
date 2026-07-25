package com.comunidapp.app.data.remote.supabase.m13

/**
 * LeoVer M13 — errores tipificados (Bloque 1 local; sin cliente remoto).
 */
class M13Exception(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object M13ErrorMapper {
    fun userMessage(code: String): String = when (code) {
        "NOT_AUTHENTICATED" -> "Debés iniciar sesión."
        "SIGHTING_NOT_FOUND" -> "No encontramos ese avistamiento."
        "SIGHTING_INVALID" -> "Revisá los datos del avistamiento."
        "SIGHTING_FORBIDDEN" -> "No tenés permiso sobre este avistamiento."
        "SIGHTING_NOT_ACTIVE" -> "El avistamiento ya no está activo."
        "SIGHTING_INVALID_TRANSITION" -> "Esa transición de estado no está permitida."
        "CASE_NOT_FOUND" -> "No encontramos el caso Lost/Found."
        "CASE_NOT_ACTIVE" -> "El caso no está activo para coincidencias."
        "CASE_REQUIRED" -> "El avistamiento persistido requiere un caso Lost/Found."
        "MATCH_NOT_FOUND" -> "No encontramos esa coincidencia."
        "MATCH_FORBIDDEN" -> "No tenés permiso para revisar esta coincidencia."
        "MATCH_INVALID_TRANSITION" -> "La coincidencia ya fue resuelta."
        "MATCH_AUTO_CONFIRM_FORBIDDEN" -> "Las coincidencias requieren confirmación humana."
        "MATCH_GENERATION_NOT_ALLOWED" -> "No tenés permiso para generar coincidencias."
        "MATCH_DATA_INSUFFICIENT" -> "Faltan datos para calcular la coincidencia."
        "MATCH_TERMINAL" -> "La coincidencia ya está en un estado final."
        "MATCH_ALREADY_FINAL" -> "La coincidencia ya está en un estado final."
        "MATCH_REVIEW_NOT_ALLOWED" -> "No tenés permiso para revisar esta coincidencia."
        "MATCH_REVIEW_ALREADY_OPEN" -> "La revisión ya está abierta."
        "INVALID_TRANSITION" -> "Esa transición de estado no está permitida."
        "UNAUTHORIZED" -> "No tenés autorización para esta decisión."
        "DECISION_ALREADY_EXISTS" -> "Ya existe una decisión final para esta coincidencia."
        "MATCH_REVIEW_RPC_UNAVAILABLE" ->
            "La revisión remota de coincidencias requiere la migración 049 (RPC)."
        "M13_EXPIRATION_INFRASTRUCTURE_UNAVAILABLE" ->
            "La expiración programada remota requiere infraestructura externa (cron)."
        "M13_METRICS_INFRASTRUCTURE_UNAVAILABLE" ->
            "Las métricas agregadas remotas aún no están disponibles."
        "M13_METRICS_INVALID_RANGE" -> "El rango temporal de métricas no es válido."
        "CONFLICT" -> "Conflicto al guardar el avistamiento o la coincidencia."
        "MEDIA_REF_INVALID" -> "La referencia de media no es segura."
        "M13_REPOSITORY_FAILURE" -> "No pudimos completar la operación (M13)."
        else -> "Ocurrió un error en avistamientos (M13)."
    }

    private val knownCodes = listOf(
        "NOT_AUTHENTICATED",
        "SIGHTING_NOT_FOUND",
        "SIGHTING_INVALID",
        "SIGHTING_FORBIDDEN",
        "SIGHTING_NOT_ACTIVE",
        "SIGHTING_INVALID_TRANSITION",
        "CASE_NOT_FOUND",
        "CASE_NOT_ACTIVE",
        "CASE_REQUIRED",
        "MATCH_NOT_FOUND",
        "MATCH_FORBIDDEN",
        "MATCH_INVALID_TRANSITION",
        "MATCH_AUTO_CONFIRM_FORBIDDEN",
        "MATCH_GENERATION_NOT_ALLOWED",
        "MATCH_DATA_INSUFFICIENT",
        "MATCH_TERMINAL",
        "MATCH_ALREADY_FINAL",
        "MATCH_REVIEW_NOT_ALLOWED",
        "MATCH_REVIEW_ALREADY_OPEN",
        "INVALID_TRANSITION",
        "UNAUTHORIZED",
        "DECISION_ALREADY_EXISTS",
        "MATCH_REVIEW_RPC_UNAVAILABLE",
        "M13_EXPIRATION_INFRASTRUCTURE_UNAVAILABLE",
        "M13_METRICS_INFRASTRUCTURE_UNAVAILABLE",
        "M13_METRICS_INVALID_RANGE",
        "CONFLICT",
        "MEDIA_REF_INVALID",
        "M13_REPOSITORY_FAILURE"
    )

    fun codeOf(error: Throwable): String {
        val blob = buildString {
            append(error.message.orEmpty())
            generateSequence(error.cause) { it.cause }.forEach { append(' ').append(it.message.orEmpty()) }
        }
        knownCodes.forEach { code ->
            if (blob.contains(code, ignoreCase = false)) return code
        }
        return (error as? M13Exception)?.code ?: "M13_UNKNOWN"
    }

    fun failure(t: Throwable): Result<Nothing> {
        val code = codeOf(t)
        return Result.failure(M13Exception(code, userMessage(code), t))
    }
}
