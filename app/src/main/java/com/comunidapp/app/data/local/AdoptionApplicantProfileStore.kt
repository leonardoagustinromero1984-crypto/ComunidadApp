package com.comunidapp.app.data.local

import com.comunidapp.app.domain.adoption.AdoptionApplicantProfile
import java.util.concurrent.ConcurrentHashMap

object AdoptionApplicantProfileStore {
    private val profiles = ConcurrentHashMap<String, AdoptionApplicantProfile>()

    fun get(personId: String): AdoptionApplicantProfile? =
        personId.takeIf { it.isNotBlank() }?.let { profiles[it] }

    fun save(profile: AdoptionApplicantProfile) {
        if (profile.personId.isBlank()) return
        profiles[profile.personId] = profile
    }
}
