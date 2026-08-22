package com.comunidapp.app.domain.context

/**
 * Refugio/ONG destinations reachable from organization context.
 * Unimplemented modules must not crash or reuse provider/org-create forms.
 */
object RefugeDestinations {
    const val HOME = "HOME"
    const val ANIMALS = "ANIMALS"
    const val PUBLISH = "PUBLISH"
    const val MANAGEMENT = "MANAGEMENT"
    const val PROFILE = "PROFILE"

    val availableNow = setOf(HOME, PUBLISH, PROFILE, ANIMALS, MANAGEMENT)

    val notYetAvailable = listOf(
        "Campañas públicas de refugio",
        "Pedidos de insumos",
        "Urgencias de refugio",
        "Eventos de refugio",
        "Alta secundaria de perfil M11 (Crear refugio) cuando ya existe la organización"
    )

    fun notYetAvailableMessage(): String =
        "Esta sección todavía no está disponible en LeoVer."
}
