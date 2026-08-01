package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M15M06Hooks
import com.comunidapp.app.data.model.M15M06PublishResult
import com.comunidapp.app.domain.notifications.NotificationOriginModule

/**
 * LeoVer M15 Bloque 4 — integración M06 best-effort con fallback honesto.
 *
 * M06 Etapa 2 allowlist actual: M01–M05 únicamente ([NotificationOriginModule]).
 * M15 no puede publicar en outbox real sin ampliación de infraestructura.
 * Los hooks quedan preparados en [M15MemoryStore.recordM06]; push real = PENDIENTE_EXTERNO.
 */
object M15M06NotificationBridge {

    const val INFRASTRUCTURE_UNAVAILABLE = "M15_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE"
    const val REMOTE_VALIDATION_PENDING = "M15_REMOTE_VALIDATION_PENDING"

    val preparedEventKeys: Set<String> = M15M06Hooks.all

    /**
     * Best-effort: registra hook preparado localmente; no bloquea operación principal.
     * Publicación M06 real solo si infraestructura explícita y módulo allowlisted (hoy: no).
     */
    fun publishBestEffort(
        store: M15MemoryStore,
        eventKey: String,
        entityId: String,
        useSupabase: Boolean
    ): M15M06PublishResult {
        store.recordM06(eventKey, entityId)
        return when {
            useSupabase -> M15M06PublishResult(
                eventKey = eventKey,
                published = false,
                code = REMOTE_VALIDATION_PENDING
            )
            store.m06InfrastructureAvailable -> M15M06PublishResult(
                eventKey = eventKey,
                published = false,
                code = INFRASTRUCTURE_UNAVAILABLE
            )
            else -> M15M06PublishResult(
                eventKey = eventKey,
                published = false,
                code = INFRASTRUCTURE_UNAVAILABLE
            )
        }
    }

    fun infrastructureStatus(useSupabase: Boolean, store: M15MemoryStore): String = when {
        useSupabase -> REMOTE_VALIDATION_PENDING
        store.m06InfrastructureAvailable -> INFRASTRUCTURE_UNAVAILABLE
        else -> INFRASTRUCTURE_UNAVAILABLE
    }
}
