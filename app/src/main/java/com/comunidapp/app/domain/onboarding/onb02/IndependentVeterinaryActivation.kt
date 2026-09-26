package com.comunidapp.app.domain.onboarding.onb02

import com.comunidapp.app.domain.context.OperationalContext

/**
 * Independent veterinarian is active only after backend persist + readback.
 * Local extras must not occupy the catalog by themselves.
 */
object IndependentVeterinaryActivation {

    const val STORAGE_CATEGORY = "VETERINARY"
    const val DISPLAY_NAME = "Veterinario/a profesional"

    fun isPersistedActive(contexts: List<OperationalContext>): Boolean =
        contexts.any { ctx ->
            when (ctx) {
                is OperationalContext.Veterinary -> isRealEntity(ctx.entityId)
                is OperationalContext.Provider ->
                    ctx.category.equals(STORAGE_CATEGORY, ignoreCase = true) &&
                        isRealEntity(ctx.entityId)
                else -> false
            }
        }

    fun occupyCatalog(persistedActive: Boolean): Boolean = persistedActive

    private fun isRealEntity(entityId: String): Boolean =
        entityId.isNotBlank() && !entityId.startsWith("onb02:")
}
