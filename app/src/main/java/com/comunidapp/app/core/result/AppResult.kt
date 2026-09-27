package com.comunidapp.app.core.result

/**
 * Resultado común para infraestructura nueva. No sustituye todos los Result de repositorios existentes.
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Failure(val error: AppError) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    fun getOrNull(): T? = (this as? Success)?.data
}

enum class AppErrorKind {
    NETWORK,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    VALIDATION,
    CONFLICT,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    FEATURE_TEMPORARILY_DISABLED,
    SERVER,
    CONFIGURATION,
    UNKNOWN
}

/**
 * @param technicalMessage detalle interno (logs/diagnóstico); no mostrar tal cual en UI
 * @param userMessage mensaje seguro para personas usuarias
 */
data class AppError(
    val kind: AppErrorKind,
    val userMessage: String,
    val technicalMessage: String,
    val cause: Throwable? = null,
    val code: String? = null
)

object AppErrorMapper {

    fun fromThrowable(
        throwable: Throwable,
        fallbackUserMessage: String = "Ocurrió un problema. Intentá de nuevo."
    ): AppError {
        val technical = throwable.message?.take(300) ?: throwable::class.java.simpleName
        return when (throwable) {
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.SocketTimeoutException,
            is java.io.IOException -> AppError(
                kind = AppErrorKind.NETWORK,
                userMessage = "Sin conexión o el servicio no responde.",
                technicalMessage = technical,
                cause = throwable,
                code = "NETWORK"
            )
            is SecurityException -> AppError(
                kind = AppErrorKind.FORBIDDEN,
                userMessage = "No tenés permiso para esta acción.",
                technicalMessage = technical,
                cause = throwable,
                code = "FORBIDDEN"
            )
            is IllegalArgumentException,
            is IllegalStateException -> AppError(
                kind = AppErrorKind.VALIDATION,
                userMessage = "Los datos no son válidos.",
                technicalMessage = technical,
                cause = throwable,
                code = "VALIDATION"
            )
            else -> {
                val upper = technical.uppercase()
                when {
                    upper.contains("FEATURE_TEMPORARILY_DISABLED") ->
                        AppErrorMapper.featureTemporarilyDisabled(technical, throwable)
                    upper.contains("FILE_TOO_LARGE") ->
                        AppError(
                            kind = AppErrorKind.VALIDATION,
                            userMessage = "El video supera el tamaño máximo permitido.",
                            technicalMessage = technical,
                            cause = throwable,
                            code = "FILE_TOO_LARGE"
                        )
                    upper.contains("QUOTA_EXCEEDED") ->
                        AppErrorMapper.quotaExceeded(technical, throwable)
                    upper.contains("RATE_LIMITED") || upper.contains("RATE_LIMIT_EXCEEDED") ->
                        AppErrorMapper.rateLimited(technical).copy(cause = throwable)
                    else -> AppError(
                        kind = AppErrorKind.UNKNOWN,
                        userMessage = fallbackUserMessage,
                        technicalMessage = technical,
                        cause = throwable,
                        code = "UNKNOWN"
                    )
                }
            }
        }
    }

    fun configuration(message: String): AppError = AppError(
        kind = AppErrorKind.CONFIGURATION,
        userMessage = "La aplicación no está configurada correctamente.",
        technicalMessage = message,
        code = "CONFIGURATION"
    )

    fun unauthorized(technical: String = "unauthorized"): AppError = AppError(
        kind = AppErrorKind.UNAUTHORIZED,
        userMessage = "Tenés que iniciar sesión.",
        technicalMessage = technical,
        code = "UNAUTHORIZED"
    )

    fun notFound(technical: String = "not_found"): AppError = AppError(
        kind = AppErrorKind.NOT_FOUND,
        userMessage = "No encontramos lo que buscás.",
        technicalMessage = technical,
        code = "NOT_FOUND"
    )

    fun server(technical: String): AppError = AppError(
        kind = AppErrorKind.SERVER,
        userMessage = "El servicio tiene un problema temporal.",
        technicalMessage = technical,
        code = "SERVER"
    )

    fun rateLimited(technical: String = "rate_limited"): AppError = AppError(
        kind = AppErrorKind.RATE_LIMITED,
        userMessage = "Estás publicando demasiado rápido. Intentá nuevamente más tarde.",
        technicalMessage = technical,
        code = "RATE_LIMITED"
    )

    fun quotaExceeded(
        technical: String = "quota_exceeded",
        cause: Throwable? = null
    ): AppError = AppError(
        kind = AppErrorKind.QUOTA_EXCEEDED,
        userMessage = "Alcanzaste el límite de uso. Intentá más tarde.",
        technicalMessage = technical,
        cause = cause,
        code = "QUOTA_EXCEEDED"
    )

    fun featureTemporarilyDisabled(
        technical: String = "feature_temporarily_disabled",
        cause: Throwable? = null
    ): AppError = AppError(
        kind = AppErrorKind.FEATURE_TEMPORARILY_DISABLED,
        userMessage = "Esta función no está disponible por ahora.",
        technicalMessage = technical,
        cause = cause,
        code = "FEATURE_TEMPORARILY_DISABLED"
    )

    fun conflict(technical: String = "conflict"): AppError = AppError(
        kind = AppErrorKind.CONFLICT,
        userMessage = "Hay un conflicto con el estado actual.",
        technicalMessage = technical,
        code = "CONFLICT"
    )
}

fun <T> Result<T>.toAppResult(
    fallbackUserMessage: String = "Ocurrió un problema. Intentá de nuevo."
): AppResult<T> =
    fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Failure(AppErrorMapper.fromThrowable(it, fallbackUserMessage)) }
    )
