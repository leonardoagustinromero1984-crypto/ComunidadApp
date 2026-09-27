package com.comunidapp.app.data.repository



import com.comunidapp.app.data.remote.supabase.m14.M14Exception

import com.comunidapp.app.domain.publiclinks.LeoVerPublicUrls



/**

 * LeoVer M14 — QR / share payloads without PII.

 * QR and external share prefer resolvable HTTPS; in-app deep link kept for App Links.

 */

object M14PublicQrPayloadService {

    private const val SCHEME_PREFIX = "leover://passport/"

    private const val DISPLAY_PREFIX = "PUB-"

    private val CANONICAL_HEX = Regex("^[0-9A-Fa-f]{12}$")



    /** Canonical DB codes are 12-char hex; QR payload uses optional PUB- prefix for readability. */

    fun normalizeForPayload(publicCode: String): Result<String> {

        val raw = publicCode.trim()

        if (raw.isEmpty()) {

            return Result.failure(M14Exception("INVALID_QR_PAYLOAD", "Código público inválido para QR."))

        }

        val withoutPrefix = raw.removePrefix(DISPLAY_PREFIX)

        if (withoutPrefix.length > 80 || M14Validators.publicCodeLooksLikePii(withoutPrefix)) {

            return Result.failure(M14Exception("INVALID_QR_PAYLOAD", "El payload QR no puede incluir PII."))

        }

        if (withoutPrefix.contains('/') || withoutPrefix.contains('?') || withoutPrefix.contains('#')) {

            return Result.failure(M14Exception("INVALID_QR_PAYLOAD", "Código público con caracteres no permitidos."))

        }

        val payloadCode = when {

            raw.startsWith(DISPLAY_PREFIX) -> raw

            CANONICAL_HEX.matches(withoutPrefix) -> DISPLAY_PREFIX + withoutPrefix.uppercase()

            else -> raw

        }

        return Result.success(payloadCode)

    }



    /** HTTPS URL for QR / WhatsApp / browser (matches web /mascota/[publicCode]). */

    fun buildPublicHttpsUrl(publicCode: String): Result<String> =

        normalizeForPayload(publicCode).map { normalized ->

            val code = normalized.removePrefix(DISPLAY_PREFIX).ifBlank { normalized }

            LeoVerPublicUrls.pet(code)

        }



    /** In-app deep link (App Links / leover://passport/). */

    fun buildDeepLinkPayload(publicCode: String): Result<String> =

        normalizeForPayload(publicCode).map { SCHEME_PREFIX + it }



    /** QR encodes HTTPS so scanners without the app open the public web page. */

    fun buildPayload(publicCode: String): Result<String> = buildPublicHttpsUrl(publicCode)



    fun extractPublicCode(payload: String): Result<String> {

        val trimmed = payload.trim()

        when {

            trimmed.startsWith(LeoVerPublicUrls.BASE) -> {

                val segment = trimmed.removePrefix(LeoVerPublicUrls.BASE)

                    .removePrefix("/mascota/")

                    .substringBefore('?')

                    .substringBefore('#')

                return normalizeForPayload(segment).map { normalized ->

                    normalized.removePrefix(DISPLAY_PREFIX).ifBlank { normalized }

                }

            }

            trimmed.startsWith(SCHEME_PREFIX) -> {

                val code = trimmed.removePrefix(SCHEME_PREFIX)

                return normalizeForPayload(code).map { normalized ->

                    normalized.removePrefix(DISPLAY_PREFIX).ifBlank { normalized }

                }

            }

        }

        return Result.failure(M14Exception("INVALID_QR_PAYLOAD", "Esquema de enlace no aprobado."))

    }
}

