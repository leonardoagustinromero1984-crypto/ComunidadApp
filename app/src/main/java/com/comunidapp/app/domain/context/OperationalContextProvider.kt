package com.comunidapp.app.domain.context

import com.comunidapp.app.domain.organization.OrganizationContextProvider
import com.comunidapp.app.domain.organization.OrganizationId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Autoridad de sesión para contextos operativos.
 * PERSONAL es siempre fallback. No concede permisos de backend.
 */
object OperationalContextProvider {

    private val store = ActiveContextStore()
    private val resolver = AvailableContextsResolver()

    private val _available = MutableStateFlow<List<OperationalContext>>(listOf(OperationalContext.Personal))
    val available: StateFlow<List<OperationalContext>> = _available.asStateFlow()

    private val _active = MutableStateFlow<OperationalContext>(OperationalContext.Personal)
    val active: StateFlow<OperationalContext> = _active.asStateFlow()

    private val _capabilities = MutableStateFlow<Set<PersonalCapability>>(emptySet())
    val capabilities: StateFlow<Set<PersonalCapability>> = _capabilities.asStateFlow()

    suspend fun refresh(userId: String?) {
        if (userId.isNullOrBlank()) {
            clear()
            return
        }
        val snapshot = resolver.resolve(userId)
        val coalesced = coalesceAvailable(snapshot.contexts)
        _available.value = coalesced
        _capabilities.value = snapshot.capabilities
        val saved = store.read()
        val next = resolveActiveContext(coalesced, saved, _active.value)
        val savedStillValid = saved != null &&
            next.kind == saved.kind &&
            next.entityId == saved.entityId
        applyActive(next, persist = saved != null && !savedStillValid)
    }

    suspend fun select(context: OperationalContext) {
        val allowed = _available.value.any { it.kind == context.kind && it.entityId == context.entityId }
        val next = if (allowed) {
            context
        } else {
            activateNewlyCreated(context)
            context
        }
        applyActive(next, persist = true)
    }

    /**
     * Persist and expose a just-created context even if the resolver has not
     * seen it yet. Never falls back to Persona.
     */
    suspend fun activateNewlyCreated(context: OperationalContext) {
        val merged = coalesceAvailable(_available.value + context)
        _available.value = merged
        applyActive(context, persist = true)
    }

    fun clear() {
        store.clear()
        _available.value = listOf(OperationalContext.Personal)
        _active.value = OperationalContext.Personal
        _capabilities.value = emptySet()
        OrganizationContextProvider.clear()
    }

    private suspend fun applyActive(next: OperationalContext, persist: Boolean) {
        _active.value = next
        if (persist) {
            store.write(ActiveContextSelection(next.kind, next.entityId))
        }
        when (next) {
            is OperationalContext.Organization -> {
                runCatching {
                    OrganizationContextProvider.selectOrganization(OrganizationId(next.entityId))
                }
            }
            is OperationalContext.Veterinary,
            is OperationalContext.Shop -> {
                if (next.entityId.matches(CANONICAL_UUID)) {
                    runCatching {
                        OrganizationContextProvider.selectOrganization(OrganizationId(next.entityId))
                    }
                }
            }
            else -> {
                // Independent professional / person capabilities are not an org.
                // Do not switch OrganizationContext to personal as a side effect of Back.
            }
        }
    }

    private fun coalesceAvailable(contexts: List<OperationalContext>): List<OperationalContext> {
        val personal = contexts.filterIsInstance<OperationalContext.Personal>()
            .ifEmpty { listOf(OperationalContext.Personal) }
        val rest = contexts.filter { it !is OperationalContext.Personal }
        val providers = rest.filterIsInstance<OperationalContext.Provider>()
        val realCategories = providers
            .filter { !it.entityId.startsWith("onb02:") }
            .map { it.category?.uppercase().orEmpty() }
            .toSet()
        val withoutSyntheticDupes = rest.filterNot { ctx ->
            ctx is OperationalContext.Provider &&
                ctx.entityId.startsWith("onb02:") &&
                ctx.category?.uppercase() in realCategories
        }
        return (personal + withoutSyntheticDupes).distinctBy { it.kind to it.entityId }
    }

    private val CANONICAL_UUID =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
}
