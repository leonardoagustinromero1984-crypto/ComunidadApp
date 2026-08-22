package com.comunidapp.app.domain.context

import com.comunidapp.app.domain.onboarding.onb02.AddFunctionGroupKind
import com.comunidapp.app.domain.onboarding.onb02.IndependentProfessionalSpecialty
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import com.comunidapp.app.domain.onboarding.onb02.OrganizationKindOption
import com.comunidapp.app.domain.onboarding.onb02.ProfileActorKind
import com.comunidapp.app.domain.onboarding.onb02.ProfileActorTaxonomy

/**
 * Single source of truth for actors, add-function groups and commercial leaves.
 * Screens must filter this registry — they must not invent parallel lists.
 */
object ContextRegistry {

    const val PERSON_IN_ADD_FUNCTION = false
    const val FOSTER_IS_FIRST_LEVEL_ACTOR = true
    const val FOSTER_IN_ADD_FUNCTION = true
    const val ADD_FUNCTION_TOP_LEVEL_COUNT = 5

    data class AddFunctionTopLevel(
        val kind: AddFunctionGroupKind,
        val visibleLabel: String,
        val subtitle: String,
        val leafId: String?
    )

    fun firstLevelActors(): List<ProfileActorKind> = ProfileActorTaxonomy.firstLevel

    fun addFunctionTopLevel(): List<AddFunctionTopLevel> = listOf(
        AddFunctionTopLevel(
            kind = AddFunctionGroupKind.RESCUER,
            visibleLabel = LeoverFunction.RESCUER.visibleLabel,
            subtitle = LeoverFunction.RESCUER.subtitle,
            leafId = "RESCUER"
        ),
        AddFunctionTopLevel(
            kind = AddFunctionGroupKind.SHELTER,
            visibleLabel = "Refugio / Organización de rescate",
            subtitle = "Entidad dedicada al rescate y adopción.",
            leafId = "SHELTER"
        ),
        AddFunctionTopLevel(
            kind = AddFunctionGroupKind.PROFESSIONAL,
            visibleLabel = ProfileActorKind.INDEPENDENT_PROFESSIONAL.visibleLabel,
            subtitle = ProfileActorKind.INDEPENDENT_PROFESSIONAL.subtitle,
            leafId = null
        ),
        AddFunctionTopLevel(
            kind = AddFunctionGroupKind.BUSINESS,
            visibleLabel = ProfileActorKind.BUSINESS.visibleLabel,
            subtitle = ProfileActorKind.BUSINESS.subtitle,
            leafId = null
        ),
        AddFunctionTopLevel(
            kind = AddFunctionGroupKind.FOSTER,
            visibleLabel = LeoverFunction.FOSTER.visibleLabel,
            subtitle = LeoverFunction.FOSTER.subtitle,
            leafId = "FOSTER"
        )
    )

    fun professionalLeaves(): List<IndependentProfessionalSpecialty> =
        IndependentProfessionalSpecialty.entries.toList()

    fun businessLeaves(): List<OrganizationKindOption> = OrganizationKindOption.commercial

    fun addFunctionNeverContainsPerson(): Boolean = !PERSON_IN_ADD_FUNCTION

    fun fosterAlwaysOfferedWhenNotOccupied(): Boolean = FOSTER_IN_ADD_FUNCTION
}
