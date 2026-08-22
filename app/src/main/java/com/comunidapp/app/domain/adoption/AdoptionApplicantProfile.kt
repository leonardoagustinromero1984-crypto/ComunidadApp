package com.comunidapp.app.domain.adoption

data class AdoptionApplicantProfile(
    val personId: String,
    val householdSummary: String = "",
    val hasYard: Boolean? = null,
    val otherPetsSummary: String = "",
    val experienceSummary: String = "",
    val availabilitySummary: String = ""
) {
    val isReusable: Boolean get() = personId.isNotBlank()
}

object AdoptionApplicantPolicy {
    const val REUSABLE_PROFILE = true
    const val PERSON_PUBLISH_ALLOWED = false
    const val MY_APPLICATIONS_LABEL = "Mis postulaciones"
    const val PREFILL_ON_APPLY = true
}
