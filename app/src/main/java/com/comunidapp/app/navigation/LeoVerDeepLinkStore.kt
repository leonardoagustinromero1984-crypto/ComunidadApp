package com.comunidapp.app.navigation

import java.util.concurrent.atomic.AtomicReference

/** One-shot deep link route consumed by [ComunidappNavGraph]. */
object LeoVerDeepLinkStore {
    private val pendingRoute = AtomicReference<String?>(null)

    fun offer(route: String) {
        val trimmed = route.trim()
        if (trimmed.isNotEmpty()) pendingRoute.set(trimmed)
    }

    fun consume(): String? = pendingRoute.getAndSet(null)
}
