package com.comunidapp.app.domain.auth

/**
 * Eventos de auth sin PII (M07 pendiente: no hay provider externo).
 * No usa android.util.Log para permanecer testeable en JVM.
 */
object AuthAnalytics {
    @Volatile
    var lastEvent: String? = null
        private set

    private val events = mutableListOf<String>()

    fun track(event: String) {
        lastEvent = event
        synchronized(events) {
            events += event
        }
    }

    fun clearForTests() {
        lastEvent = null
        synchronized(events) { events.clear() }
    }

    fun snapshotForTests(): List<String> = synchronized(events) { events.toList() }
}
