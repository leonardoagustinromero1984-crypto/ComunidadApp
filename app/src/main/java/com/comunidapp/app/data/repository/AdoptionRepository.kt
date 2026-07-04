package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.mock.InMemoryDataStore
import kotlinx.coroutines.flow.StateFlow

interface AdoptionRepository {
    fun observeAdoptionPosts(): StateFlow<List<AdoptionPost>>
    fun getAdoptionPostById(id: String): AdoptionPost?
    fun getFilteredAdoptions(
        location: String? = null,
        sex: PetSex? = null,
        minAge: Int? = null,
        maxAge: Int? = null,
        size: PetSize? = null,
        status: AdoptionStatus? = null
    ): List<AdoptionPost>
    fun getAdoptionsByShelter(shelterId: String): List<AdoptionPost>
    fun addAdoptionPost(post: AdoptionPost)
}

class MockAdoptionRepository : AdoptionRepository {

    override fun observeAdoptionPosts(): StateFlow<List<AdoptionPost>> =
        InMemoryDataStore.adoptionPosts

    override fun getAdoptionPostById(id: String): AdoptionPost? =
        InMemoryDataStore.getAdoptionPostById(id)

    override fun getFilteredAdoptions(
        location: String?,
        sex: PetSex?,
        minAge: Int?,
        maxAge: Int?,
        size: PetSize?,
        status: AdoptionStatus?
    ): List<AdoptionPost> = InMemoryDataStore.adoptionPosts.value.filter { post ->
        (location.isNullOrBlank() || post.location.contains(location, ignoreCase = true)) &&
            (sex == null || post.sex == sex) &&
            (minAge == null || post.ageYears >= minAge) &&
            (maxAge == null || post.ageYears <= maxAge) &&
            (size == null || post.size == size) &&
            (status == null || post.status == status)
    }

    override fun getAdoptionsByShelter(shelterId: String): List<AdoptionPost> =
        InMemoryDataStore.getAdoptionsByShelter(shelterId)

    override fun addAdoptionPost(post: AdoptionPost) {
        InMemoryDataStore.addAdoptionPost(post)
    }
}
