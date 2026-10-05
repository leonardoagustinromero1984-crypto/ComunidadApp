package com.comunidapp.app.domain.user

/**
 * If the extended profile RPC is missing, privacy and phone are not saved and
 * the caller must not report success. The phone is never published by a fallback.
 */
object ProfilePrivacySave {
    const val UNAVAILABLE = "PRIVACY_FLAGS_UNAVAILABLE"

    fun whenExtendedRpcMissing(savingPhoneOrFlags: Boolean): Result<Unit> =
        if (savingPhoneOrFlags) {
            Result.failure(IllegalStateException(UNAVAILABLE))
        } else {
            Result.success(Unit)
        }

    /** A failed privacy write must not be presented as a saved profile. */
    fun saved(result: Result<Unit>): Boolean = result.isSuccess

    fun failureMessage(result: Result<Unit>): String? =
        result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
            ?: if (result.isFailure) "No se pudo guardar la privacidad" else null
}
