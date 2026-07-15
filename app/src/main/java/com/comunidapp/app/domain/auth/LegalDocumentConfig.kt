package com.comunidapp.app.domain.auth

import com.comunidapp.app.BuildConfig
import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind

/**
 * Configuración tipada de documentos legales.
 * No inventa URLs públicas ni textos jurídicos definitivos.
 */
data class LegalDocumentRef(
    val version: String,
    /** Ruta interna de navegación Compose; no URL externa inventada. */
    val internalRoute: String,
    val publishable: Boolean,
    val draftLabel: String? = null
)

object LegalDocumentConfig {

    /** Versiones de borrador — revisión legal requerida antes de release publicable. */
    const val TERMS_VERSION_DRAFT = "draft-terms-2026-07"
    const val PRIVACY_VERSION_DRAFT = "draft-privacy-2026-07"

    val terms: LegalDocumentRef = LegalDocumentRef(
        version = TERMS_VERSION_DRAFT,
        internalRoute = "legal_terms",
        publishable = false,
        draftLabel = "BORRADOR — NO PUBLICABLE"
    )

    val privacy: LegalDocumentRef = LegalDocumentRef(
        version = PRIVACY_VERSION_DRAFT,
        internalRoute = "legal_privacy",
        publishable = false,
        draftLabel = "BORRADOR — NO PUBLICABLE"
    )

    val isDebug: Boolean get() = BuildConfig.DEBUG

    /**
     * En debug permite borradores.
     * En release exige documentos publicables; si faltan → ConfigurationError.
     */
    fun requireUsableForAuth(): Result<Unit> {
        if (isDebug) return Result.success(Unit)
        if (!terms.publishable || !privacy.publishable) {
            return Result.failure(
                AuthException(
                    AppError(
                        kind = AppErrorKind.CONFIGURATION,
                        userMessage = "La aplicación no está configurada para el registro.",
                        technicalMessage = "Legal documents not publishable in release",
                        code = AuthErrorCode.CONFIGURATION_ERROR.name
                    )
                )
            )
        }
        if (terms.version.isBlank() || privacy.version.isBlank()) {
            return Result.failure(
                AuthException(
                    AppError(
                        kind = AppErrorKind.CONFIGURATION,
                        userMessage = "La aplicación no está configurada para el registro.",
                        technicalMessage = "Empty legal versions in release",
                        code = AuthErrorCode.CONFIGURATION_ERROR.name
                    )
                )
            )
        }
        return Result.success(Unit)
    }
}

data class ConsentMetadata(
    val termsVersion: String,
    val privacyVersion: String,
    val locale: String? = null,
    val source: String = SOURCE_REGISTRATION
) {
    companion object {
        const val SOURCE_REGISTRATION = "registration"

        fun forRegistration(locale: String? = null): ConsentMetadata =
            ConsentMetadata(
                termsVersion = LegalDocumentConfig.terms.version,
                privacyVersion = LegalDocumentConfig.privacy.version,
                locale = locale,
                source = SOURCE_REGISTRATION
            )
    }
}

object EmailMasking {
    fun mask(email: String): String {
        val trimmed = email.trim()
        val at = trimmed.indexOf('@')
        if (at <= 0 || at == trimmed.lastIndex) return "***"
        val local = trimmed.substring(0, at)
        val domain = trimmed.substring(at + 1)
        val maskedLocal = when {
            local.length <= 1 -> "*"
            local.length == 2 -> "${local.first()}*"
            else -> "${local.first()}***${local.last()}"
        }
        return "$maskedLocal@$domain"
    }
}
