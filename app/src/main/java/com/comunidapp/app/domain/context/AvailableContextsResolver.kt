package com.comunidapp.app.domain.context

import com.comunidapp.app.data.model.FosterAvailabilityStatus
import com.comunidapp.app.data.model.FosterHomeStatus
import com.comunidapp.app.data.model.M22ProviderStatus
import com.comunidapp.app.data.model.M25ShopStatus
import com.comunidapp.app.data.model.VeterinaryClinicStatus
import com.comunidapp.app.domain.organization.OrganizationStatus
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.FosterHomeRepository
import com.comunidapp.app.data.repository.M22ProviderRepository
import com.comunidapp.app.data.repository.M25MarketplaceRepository
import com.comunidapp.app.data.repository.OrganizationRepository
import com.comunidapp.app.data.repository.PersonCapabilityRepository
import com.comunidapp.app.data.repository.VeterinaryClinicRepository
import com.comunidapp.app.data.local.Onb02StoreProvider
import com.comunidapp.app.domain.capability.PersonCapabilityCode
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import kotlinx.coroutines.flow.first

/**
 * Deriva contextos de entidades canónicas existentes.
 * No inventa permisos: solo incluye lo que el usuario ya puede operar.
 */
class AvailableContextsResolver(
    private val organizationRepository: OrganizationRepository = DataProvider.organizationRepository,
    private val fosterHomeRepository: FosterHomeRepository = DataProvider.fosterHomeRepository,
    private val providerRepository: M22ProviderRepository = DataProvider.m22ProviderRepository,
    private val clinicRepository: VeterinaryClinicRepository = DataProvider.veterinaryClinicRepository,
    private val shopRepository: M25MarketplaceRepository = DataProvider.m25MarketplaceRepository,
    private val personCapabilityRepository: PersonCapabilityRepository = DataProvider.personCapabilityRepository
) {
    suspend fun getMyAvailableContexts(userId: String): List<OperationalContext> =
        resolve(userId).contexts

    suspend fun resolve(userId: String): AvailableContextsSnapshot {
        if (userId.isBlank()) {
            return AvailableContextsSnapshot(listOf(OperationalContext.Personal), emptySet())
        }
        val result = mutableListOf<OperationalContext>(OperationalContext.Personal)

        runCatching { organizationRepository.getMyOrganizations() }.getOrDefault(emptyList())
            .filter { it.status != OrganizationStatus.CLOSED && it.status != OrganizationStatus.REJECTED }
            .forEach { org ->
                result += ContextIdentityMapping.contextForOrganization(
                    organizationId = org.id.value,
                    publicName = org.publicName,
                    typeOrCapability = org.typeDescription ?: org.type.name
                )
            }

        val foster = runCatching {
            fosterHomeRepository.observeMyFosterHome(userId).first()
        }.getOrNull()
        if (foster != null &&
            foster.ownerUserId == userId &&
            foster.status != FosterHomeStatus.CLOSED
        ) {
            result += OperationalContext.Foster(
                entityId = foster.id,
                displayName = foster.displayName.ifBlank { "Mi hogar de tránsito" }
            )
        }

        runCatching { providerRepository.observeMyProviders().first() }.getOrDefault(emptyList())
            .filter { it.ownerUserId == userId && it.status != M22ProviderStatus.ARCHIVED }
            .forEach { provider ->
                result += OperationalContext.Provider(
                    entityId = provider.id,
                    displayName = provider.displayName,
                    category = provider.category.name
                )
            }

        runCatching { clinicRepository.observeManagedClinics().first() }.getOrDefault(emptyList())
            .filter { it.status != VeterinaryClinicStatus.ARCHIVED }
            .forEach { clinic ->
                result += OperationalContext.Veterinary(
                    entityId = clinic.id,
                    displayName = if (clinic.displayName.isBlank()) {
                        "Profesional veterinario"
                    } else {
                        clinic.displayName
                    }
                )
            }

        runCatching { shopRepository.observeMyShops().first() }.getOrDefault(emptyList())
            .filter { it.ownerUserId == userId && it.status != M25ShopStatus.ARCHIVED }
            .forEach { shop ->
                result += OperationalContext.Shop(
                    entityId = shop.id,
                    displayName = shop.displayName
                )
            }

        val extras = runCatching { Onb02StoreProvider.instance.selection(userId).extras }
            .getOrDefault(emptySet())
        if (LeoverFunction.FOSTER in extras && result.none { it is OperationalContext.Foster }) {
            result += OperationalContext.Foster(
                entityId = userId,
                displayName = "Hogar de tránsito"
            )
        }
        com.comunidapp.app.domain.canonical.CanonicalProviderWrite.extraProviderContexts(userId, extras)
            .forEach { extra ->
                val already = result.any { ctx ->
                    ctx is OperationalContext.Provider &&
                        ctx.category.equals(extra.category, ignoreCase = true)
                }
                if (!already) result += extra
            }
        val personCaps = runCatching { personCapabilityRepository.listMine() }
            .getOrDefault(emptyList())
            .filter { it.active }
            .map { it.code }
            .toSet()
        val contexts = withPersonCapabilities(userId, result, personCaps)
            .distinctBy { it.kind to it.entityId }
        return AvailableContextsSnapshot(
            contexts = contexts,
            capabilities = deriveCapabilities(contexts, foster?.availabilityStatus)
        )
    }

    companion object {
        fun withPersonCapabilities(
            userId: String,
            contexts: List<OperationalContext>,
            active: Set<PersonCapabilityCode>
        ): List<OperationalContext> {
            val next = contexts.toMutableList()
            if (PersonCapabilityCode.RESCUER in active && next.none { it is OperationalContext.Rescuer }) {
                next += OperationalContext.Rescuer(entityId = userId)
            }
            if (PersonCapabilityCode.FOSTER in active && next.none { it is OperationalContext.Foster }) {
                next += OperationalContext.Foster(entityId = userId, displayName = "Hogar de tránsito")
            }
            return next
        }

        fun deriveCapabilities(
            contexts: List<OperationalContext>,
            fosterAvailability: FosterAvailabilityStatus? = null
        ): Set<PersonalCapability> {
            val caps = mutableSetOf<PersonalCapability>()
            if (fosterAvailability == FosterAvailabilityStatus.AVAILABLE ||
                fosterAvailability == FosterAvailabilityStatus.LIMITED
            ) {
                caps += PersonalCapability.FOSTER_AVAILABLE
            }
            if (contexts.any { it is OperationalContext.Rescuer }) {
                caps += PersonalCapability.INDEPENDENT_RESCUER
            }
            return caps
        }
    }
}
