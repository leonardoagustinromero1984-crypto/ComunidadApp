package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M20ContextReferenceType
import com.comunidapp.app.data.model.M20ContextSnapshot
import com.comunidapp.app.data.model.M20MockReferenceIds

/** Resolución mock de context hints M08/M03/M18 para conversaciones M20. */
object M20ContextHintResolver {

    private val knownPublic = mapOf(
        M20MockReferenceIds.PET to "Consulta adopción — Luna",
        M20MockReferenceIds.ORGANIZATION to "Refugio Comunitario Norte",
        M20MockReferenceIds.EVENT to "Feria de adopciones — domingo"
    )

    fun resolve(snapshot: M20ContextSnapshot): M20ContextSnapshot {
        if (snapshot.targetId.startsWith("private_")) {
            return snapshot.copy(isPublic = false, displayLabel = "Contexto no disponible")
        }
        val label = knownPublic[snapshot.targetId] ?: snapshot.displayLabel.ifBlank { "Contexto" }
        return snapshot.copy(
            displayLabel = label,
            isPublic = snapshot.targetId in knownPublic || snapshot.isPublic
        )
    }

    fun snapshot(
        type: M20ContextReferenceType,
        targetId: String,
        displayLabel: String,
        isPublic: Boolean = true
    ): M20ContextSnapshot = resolve(
        M20ContextSnapshot(type = type, targetId = targetId, displayLabel = displayLabel, isPublic = isPublic)
    )
}
