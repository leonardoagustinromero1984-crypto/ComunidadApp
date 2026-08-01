package com.comunidapp.app.domain.m16

import com.comunidapp.app.data.model.M16MockOrganizations
import com.comunidapp.app.data.model.Shelter
import com.comunidapp.app.data.repository.M16ShelterRepository
import kotlinx.coroutines.flow.first

/**
 * Puente M11 legacy → M16 sin duplicar dominio.
 */
object M11M16ShelterCompatibilityAdapter {

    /** Legacy shelter listing id → organization M03/M16 (mock documentado). */
    private val legacyShelterToOrganization: Map<String, String> = mapOf(
        "shelter_1" to M16MockOrganizations.ORG_NORTE,
        "shelter_2" to M16MockOrganizations.ORG_SUR
    )

    fun resolveOrganizationId(legacyShelter: Shelter): String? =
        legacyShelterToOrganization[legacyShelter.id]
            ?: legacyShelter.ownerId.takeIf { it.startsWith("org_") }

    suspend fun resolveM16PublicShelterId(
        legacyShelter: Shelter,
        repository: M16ShelterRepository
    ): String? {
        val orgId = resolveOrganizationId(legacyShelter) ?: return null
        if (!repository.isOrganizationEligible(orgId)) return null
        val profile = repository.observeProfileByOrganization(orgId).first() ?: return null
        return if (profile.publicationStatus.name == "PUBLISHED" ||
            profile.publicationStatus.name == "DRAFT"
        ) {
            profile.id
        } else {
            null
        }
    }

    fun legacyFlowLabel(legacyShelter: Shelter): String =
        if (legacyShelterToOrganization.containsKey(legacyShelter.id)) {
            "Este refugio tiene perfil M16 vinculado por organización."
        } else {
            "Flujo legacy anterior a M16 — sin vínculo automático a organización."
        }
}
