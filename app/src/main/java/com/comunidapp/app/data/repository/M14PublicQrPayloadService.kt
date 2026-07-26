package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.m14.M14Exception

/**
 * LeoVer M14 — deep link / QR payload without PII.
 * Visual QR rendering may wait for Bloque 4 if no compatible library is present.
 */
object M14PublicQrPayloadService {
    private const val SCHEME_PREFIX = "leover://passport/"

    fun buildPayload(publicCode: String): Result<String> {
        val code = publicCode.trim()
        if (code.isEmpty() || !code.startsWith("PUB-")) {
            return Result.failure(M14Exception("INVALID_QR_PAYLOAD", "Código público inválido para QR."))
        }
        if (code.length > 80 || M14Validators.publicCodeLooksLikePii(code)) {
            return Result.failure(M14Exception("INVALID_QR_PAYLOAD", "El payload QR no puede incluir PII."))
        }
        if (code.contains('/') || code.contains('?') || code.contains('#')) {
            return Result.failure(M14Exception("INVALID_QR_PAYLOAD", "Código público con caracteres no permitidos."))
        }
        return Result.success(SCHEME_PREFIX + code)
    }

    fun extractPublicCode(payload: String): Result<String> {
        val trimmed = payload.trim()
        if (!trimmed.startsWith(SCHEME_PREFIX)) {
            return Result.failure(M14Exception("INVALID_QR_PAYLOAD", "Esquema de deep link no aprobado."))
        }
        return buildPayload(trimmed.removePrefix(SCHEME_PREFIX)).map { it.removePrefix(SCHEME_PREFIX) }
    }
}
