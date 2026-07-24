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
        "MATCH_NOT_FOUND" -> "No encontramos esa coincidencia."
        "MATCH_FORBIDDEN" -> "No tenés permiso para revisar esta coincidencia."
        "MATCH_INVALID_TRANSITION" -> "La coincidencia ya fue resuelta."
        "MATCH_AUTO_CONFIRM_FORBIDDEN" -> "Las coincidencias requieren confirmación humana."
        "MEDIA_REF_INVALID" -> "La referencia de media no es segura."
        "M13_REPOSITORY_FAILURE" -> "No pudimos completar la operación (M13)."
        else -> "Ocurrió un error en avistamientos (M13)."
    }

    fun codeOf(error: Throwable): String =
        (error as? M13Exception)?.code ?: "M13_UNKNOWN"
}
