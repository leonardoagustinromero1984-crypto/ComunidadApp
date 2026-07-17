package com.comunidapp.app.viewmodel.moderation

/**
 * Limpieza de caches/estáticos administrativos al logout.
 * Extensible si se agregan caches sensibles en cliente.
 */
object AdministrativeSessionCleanup {
    @Volatile
    private var clearedGeneration: Long = 0L

    fun clear() {
        com.comunidapp.app.viewmodel.files.FileSessionCleanup.clear()
        clearedGeneration += 1L
    }

    /** Visible para tests: generación incrementa en cada clear. */
    fun clearedGenerationForTests(): Long = clearedGeneration

    fun resetForTests() {
        clearedGeneration = 0L
    }
}
