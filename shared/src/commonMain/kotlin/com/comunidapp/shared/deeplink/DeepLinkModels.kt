package com.comunidapp.shared.deeplink

/**
 * Destinos tipados de deep link — sin URI cruda en UI.
 */
sealed interface DeepLinkTarget {
    data class PetPublic(val publicCode: String) : DeepLinkTarget
    data class AdoptionPublic(val publicCode: String) : DeepLinkTarget
    data class LostCase(val publicCode: String) : DeepLinkTarget
    data class FoundCase(val publicCode: String) : DeepLinkTarget
    /** Custom scheme: leover://passport/CODE */
    data class Passport(val publicCode: String) : DeepLinkTarget
    data object SafeHome : DeepLinkTarget
    /** [reason] es etiqueta segura (no URL cruda). */
    data class Unsupported(val reason: String) : DeepLinkTarget
}

object DeepLinkHosts {
    val HTTPS_HOSTS = setOf("leover.com.ar", "www.leover.com.ar")
    const val CUSTOM_SCHEME = "leover"
}

/**
 * Controlador de navegación deep link — pending post-login + consume.
 */
class DeepLinkNavigationController {
    private val _pending = kotlinx.coroutines.flow.MutableStateFlow<DeepLinkTarget?>(null)
    val pending: kotlinx.coroutines.flow.StateFlow<DeepLinkTarget?> = _pending

    fun offer(target: DeepLinkTarget) {
        when (target) {
            DeepLinkTarget.SafeHome,
            is DeepLinkTarget.Unsupported -> Unit
            else -> _pending.value = target
        }
    }

    fun offerRawUrl(rawUrl: String) {
        offer(DeepLinkParser.parse(rawUrl))
    }

    fun peek(): DeepLinkTarget? = _pending.value

    fun consume(): DeepLinkTarget? {
        val current = _pending.value
        _pending.value = null
        return current
    }

    fun clear() {
        _pending.value = null
    }
}

/**
 * Store in-memory simple (alternativa al controller) para pending post-login.
 */
object DeepLinkPendingStore {
    private val pending = kotlinx.coroutines.flow.MutableStateFlow<DeepLinkTarget?>(null)

    fun set(target: DeepLinkTarget) {
        pending.value = when (target) {
            DeepLinkTarget.SafeHome,
            is DeepLinkTarget.Unsupported -> null
            else -> target
        }
    }

    fun peek(): DeepLinkTarget? = pending.value

    fun consume(): DeepLinkTarget? {
        val current = pending.value
        pending.value = null
        return current
    }

    fun clear() {
        pending.value = null
    }
}
