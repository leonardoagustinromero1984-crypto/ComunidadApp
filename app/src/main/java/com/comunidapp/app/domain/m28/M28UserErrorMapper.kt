package com.comunidapp.app.domain.m28

import com.comunidapp.app.data.repository.M28AccessException

object M28UserErrorMapper {
    fun message(error: Throwable): String {
        val code = (error as? M28AccessException)?.code ?: error.message.orEmpty()
        val blob = "$code ${error.cause?.message.orEmpty()}"
        return when {
            blob.contains("M28_GRANT_REVOKED", ignoreCase = true) ->
                "No tenés permiso para esta acción."
            blob.contains("M28_PROFESSIONAL_SUSPENDED", ignoreCase = true) ->
                "El acceso profesional no está disponible."
            blob.contains("M28_ESTABLISHMENT_SUSPENDED", ignoreCase = true) ->
                "El establecimiento no está disponible."
            else -> "Algo salió mal. Intentá nuevamente."
        }
    }
}
