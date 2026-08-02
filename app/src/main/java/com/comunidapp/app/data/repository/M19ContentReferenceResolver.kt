package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M19ContentReference
import com.comunidapp.app.data.model.M19ContentReferenceType
import com.comunidapp.app.data.model.M19MockReferenceIds

/** Resolución mock de referencias contextuales M08/M16/M17/M18. */
object M19ContentReferenceResolver {

    private val knownPublic = mapOf(
        M19MockReferenceIds.PET to "Mascota Luna",
        M19MockReferenceIds.SHELTER to "Refugio Comunitario Norte",
        M19MockReferenceIds.CAMPAIGN to "Campaña invierno",
        M19MockReferenceIds.EVENT to "Feria de adopciones"
    )

    fun resolve(ref: M19ContentReference): M19ContentReference {
        if (ref.targetId.startsWith("private_")) {
            return ref.copy(isPublic = false, displayLabel = "Contenido no disponible")
        }
        val label = knownPublic[ref.targetId] ?: ref.displayLabel.ifBlank { "Referencia" }
        return ref.copy(displayLabel = label, isPublic = ref.targetId in knownPublic || ref.isPublic)
    }

    fun resolveAll(refs: List<M19ContentReference>): List<M19ContentReference> =
        refs.map { resolve(it) }

    fun snapshot(
        type: M19ContentReferenceType,
        targetId: String,
        displayLabel: String,
        isPublic: Boolean = true
    ): M19ContentReference = resolve(
        M19ContentReference(type = type, targetId = targetId, displayLabel = displayLabel, isPublic = isPublic)
    )
}
