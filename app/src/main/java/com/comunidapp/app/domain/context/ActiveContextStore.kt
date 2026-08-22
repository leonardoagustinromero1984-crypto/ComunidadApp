package com.comunidapp.app.domain.context

import android.content.Context
import com.comunidapp.app.LeoverApplication

/**
 * Selección de contexto activo: local/sesión.
 * Si el contexto guardado deja de ser válido, el resolver vuelve a PERSONAL.
 */
class ActiveContextStore(
    private val contextProvider: () -> Context? = {
        runCatching { LeoverApplication.instance }.getOrNull()
    }
) {
    private val prefs
        get() = contextProvider()?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): ActiveContextSelection? {
        val stored = prefs ?: return null
        val kindRaw = stored.getString(KEY_KIND, null) ?: return null
        val kind = runCatching { OperationalContextKind.valueOf(kindRaw) }.getOrNull()
            ?: return null
        val entityId = stored.getString(KEY_ENTITY, null)?.takeIf { it.isNotBlank() }
            ?: return null
        return ActiveContextSelection(kind, entityId)
    }

    fun write(selection: ActiveContextSelection) {
        prefs?.edit()
            ?.putString(KEY_KIND, selection.kind.name)
            ?.putString(KEY_ENTITY, selection.entityId)
            ?.apply()
    }

    fun clear() {
        prefs?.edit()?.remove(KEY_KIND)?.remove(KEY_ENTITY)?.apply()
    }

    companion object {
        private const val PREFS = "leover_active_context"
        private const val KEY_KIND = "kind"
        private const val KEY_ENTITY = "entity_id"
    }
}
