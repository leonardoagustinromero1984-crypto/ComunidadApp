package com.comunidapp.app.domain.context

/**
 * Contexto operativo de sesión. No es identidad humana.
 *
 * IDENTIDAD = public.users (siempre persona).
 * Este tipo describe *dónde* opera la UI ahora.
 */
enum class OperationalContextKind {
    PERSONAL,
    ORGANIZATION,
    FOSTER,
    PROVIDER,
    VETERINARY,
    SHOP,
    RESCUER
}

sealed class OperationalContext {
    abstract val kind: OperationalContextKind
    abstract val entityId: String
    abstract val displayName: String

    data object Personal : OperationalContext() {
        override val kind = OperationalContextKind.PERSONAL
        override val entityId = ID
        override val displayName = "Perfil personal"
        const val ID = "personal"
    }

    data class Organization(
        override val entityId: String,
        override val displayName: String,
        val organizationType: String? = null
    ) : OperationalContext() {
        override val kind = OperationalContextKind.ORGANIZATION
    }

    data class Foster(
        override val entityId: String,
        override val displayName: String
    ) : OperationalContext() {
        override val kind = OperationalContextKind.FOSTER
    }

    data class Provider(
        override val entityId: String,
        override val displayName: String,
        val category: String? = null
    ) : OperationalContext() {
        override val kind = OperationalContextKind.PROVIDER
    }

    data class Veterinary(
        override val entityId: String,
        override val displayName: String
    ) : OperationalContext() {
        override val kind = OperationalContextKind.VETERINARY
    }

    data class Shop(
        override val entityId: String,
        override val displayName: String
    ) : OperationalContext() {
        override val kind = OperationalContextKind.SHOP
    }

    data class Rescuer(
        override val entityId: String,
        override val displayName: String = "Rescatista"
    ) : OperationalContext() {
        override val kind = OperationalContextKind.RESCUER
    }

    val isPersonal: Boolean get() = this is Personal
}

data class ActiveContextSelection(
    val kind: OperationalContextKind,
    val entityId: String
)

enum class PersonalCapability {
    ADOPTION_INTEREST,
    FOSTER_AVAILABLE,
    VOLUNTEER,
    TRANSPORT,
    DIFFUSION,
    INDEPENDENT_RESCUER,
    EVENT_COLLABORATOR
}

data class AvailableContextsSnapshot(
    val contexts: List<OperationalContext>,
    val capabilities: Set<PersonalCapability> = emptySet()
)

fun resolveActiveContext(
    available: List<OperationalContext>,
    saved: ActiveContextSelection?,
    lastActive: OperationalContext? = null
): OperationalContext {
    val pool = available.ifEmpty { listOf(OperationalContext.Personal) }
    val personal = pool.firstOrNull { it is OperationalContext.Personal } ?: OperationalContext.Personal
    if (saved == null) return personal
    pool.firstOrNull { it.kind == saved.kind && it.entityId == saved.entityId }
        ?.let { return it }
    val sameKind = pool.filter { it.kind == saved.kind }
    if (sameKind.size == 1) return sameKind.first()
    sameKind.firstOrNull { !it.entityId.startsWith("onb02:") }?.let { return it }
    @Suppress("UNUSED_PARAMETER")
    val ignoredLast = lastActive
    return personal
}
