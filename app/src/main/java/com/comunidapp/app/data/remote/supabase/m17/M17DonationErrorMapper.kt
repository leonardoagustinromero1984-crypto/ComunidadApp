package com.comunidapp.app.data.remote.supabase.m17

class M17Exception(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object M17DonationErrorMapper {

    private val knownCodes = listOf(
        "M17_CAMPAIGN_NOT_FOUND",
        "M17_ORGANIZATION_NOT_ELIGIBLE",
        "M17_PERMISSION_DENIED",
        "M17_INVALID_TITLE",
        "M17_INVALID_DESCRIPTION",
        "M17_INVALID_GOAL",
        "M17_INVALID_CURRENCY",
        "M17_INVALID_DATE_RANGE",
        "M17_INVALID_REFERENCE",
        "M17_INVALID_STATE_TRANSITION",
        "M17_STATE_ALREADY_FINAL",
        "M17_CAMPAIGN_NOT_PUBLIC",
        "M17_CONTRIBUTION_NOT_FOUND",
        "M17_INVALID_CONTRIBUTION_AMOUNT",
        "M17_PAYMENT_INFRASTRUCTURE_UNAVAILABLE",
        "M17_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE",
        "M17_REMOTE_VALIDATION_PENDING",
        "M17_NEED_NOT_FOUND",
        "M17_NEED_NOT_PUBLIC",
        "M17_NEED_TERMINAL",
        "M17_OPPORTUNITY_NOT_FOUND",
        "M17_OPPORTUNITY_NOT_PUBLIC",
        "M17_OPPORTUNITY_TERMINAL",
        "M17_DUPLICATE_APPLICATION",
        "M17_PLEDGE_NOT_FOUND",
        "M17_APPLICATION_NOT_FOUND",
        "M17_INVALID_QUANTITY",
        "M17_INVALID_AMOUNT",
        "NOT_AUTHENTICATED"
    )

    fun userMessage(code: String): String = when (code) {
        "M17_CAMPAIGN_NOT_FOUND" -> "No encontramos esa campaña."
        "M17_ORGANIZATION_NOT_ELIGIBLE" -> "Esta organización no puede crear campañas solidarias."
        "M17_PERMISSION_DENIED" -> "No tenés permiso para esta acción."
        "M17_INVALID_TITLE" -> "El título no es válido."
        "M17_INVALID_DESCRIPTION" -> "La descripción no es válida."
        "M17_INVALID_GOAL" -> "El objetivo debe ser mayor a cero."
        "M17_INVALID_CURRENCY" -> "La moneda no es válida o no puede cambiarse."
        "M17_INVALID_DATE_RANGE" -> "La fecha límite debe ser posterior al inicio."
        "M17_INVALID_REFERENCE" -> "Alguna referencia vinculada no es válida."
        "M17_INVALID_STATE_TRANSITION" -> "Ese cambio de estado no está permitido."
        "M17_STATE_ALREADY_FINAL" -> "Esta campaña ya está cerrada."
        "M17_CAMPAIGN_NOT_PUBLIC" -> "Esta campaña no está publicada."
        "M17_CONTRIBUTION_NOT_FOUND" -> "No encontramos esa contribución."
        "M17_INVALID_CONTRIBUTION_AMOUNT" -> "El monto de contribución no es válido."
        "M17_PAYMENT_INFRASTRUCTURE_UNAVAILABLE" ->
            "Los pagos reales todavía no están habilitados."
        "M17_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE" ->
            "Las notificaciones no están disponibles para campañas."
        "M17_REMOTE_VALIDATION_PENDING" -> "La validación remota está pendiente."
        "M17_NEED_NOT_FOUND" -> "No encontramos esa necesidad."
        "M17_NEED_NOT_PUBLIC" -> "Esta necesidad no está publicada."
        "M17_NEED_TERMINAL" -> "Esta necesidad ya está cerrada."
        "M17_OPPORTUNITY_NOT_FOUND" -> "No encontramos esa oportunidad."
        "M17_OPPORTUNITY_NOT_PUBLIC" -> "Esta oportunidad no está publicada."
        "M17_OPPORTUNITY_TERMINAL" -> "Esta oportunidad ya está cerrada."
        "M17_DUPLICATE_APPLICATION" -> "Ya te postulaste a esta oportunidad."
        "M17_PLEDGE_NOT_FOUND" -> "No encontramos ese compromiso."
        "M17_APPLICATION_NOT_FOUND" -> "No encontramos esa postulación."
        "M17_INVALID_QUANTITY" -> "La cantidad no es válida."
        "M17_INVALID_AMOUNT" -> "El monto no es válido."
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión."
        else -> "No se pudo completar la operación."
    }

    fun codeOf(throwable: Throwable): String {
        if (throwable is M17Exception) return throwable.code
        knownCodes.forEach { code ->
            if (throwable.message?.contains(code, ignoreCase = true) == true) return code
        }
        return "UNKNOWN"
    }

    fun <T> failure(throwable: Throwable): Result<T> {
        val code = codeOf(throwable)
        return Result.failure(M17Exception(code, userMessage(code), throwable))
    }

    fun <T> fail(code: String): Result<T> =
        Result.failure(M17Exception(code, userMessage(code)))
}
