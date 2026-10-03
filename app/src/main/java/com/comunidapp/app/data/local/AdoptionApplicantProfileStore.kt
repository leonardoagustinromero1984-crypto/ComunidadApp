package com.comunidapp.app.data.local

import com.comunidapp.app.domain.adoption.AdopterProfile
import com.comunidapp.app.domain.adoption.AdoptionApplicantProfile
import java.util.concurrent.ConcurrentHashMap

object AdoptionApplicantProfileStore {
    private val profiles = ConcurrentHashMap<String, AdoptionApplicantProfile>()
    private val structured = ConcurrentHashMap<String, AdopterProfile>()

    fun get(personId: String): AdoptionApplicantProfile? =
        personId.takeIf { it.isNotBlank() }?.let { profiles[it] }

    fun save(profile: AdoptionApplicantProfile) {
        if (profile.personId.isBlank()) return
        profiles[profile.personId] = profile
    }

    fun structured(personId: String): AdopterProfile? =
        personId.takeIf { it.isNotBlank() }?.let { structured[it] }

    fun saveStructured(personId: String, profile: AdopterProfile) {
        if (personId.isBlank()) return
        structured[personId] = profile
    }

    fun clear() {
        profiles.clear()
        structured.clear()
    }
}
