package com.comunidapp.shared.poc.m08.data

import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.poc.m08.model.PocMediaBackendMode
import com.comunidapp.shared.poc.m08.model.PocPet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface PetPocRepository {
    val backendMode: PocMediaBackendMode
    fun observePets(): StateFlow<List<PocPet>>
    fun getPet(petId: String): PocPet?
    /** FAKE_FOR_NATIVE_POC — keeps selection in memory; no remote write. */
    fun attachLocalMedia(petId: String, file: FileRef): Result<PocPet>
    fun clearLocalMedia(petId: String): Result<PocPet>
}

/**
 * In-memory pets inspired by M08 PetForm.
 * BACKEND: FAKE_FOR_NATIVE_POC (no Supabase writes).
 */
class FakePetPocRepository(
    seed: List<PocPet> = defaultSeed
) : PetPocRepository {
    override val backendMode: PocMediaBackendMode = PocMediaBackendMode.FAKE_FOR_NATIVE_POC

    private val pets = MutableStateFlow(seed)

    override fun observePets(): StateFlow<List<PocPet>> = pets.asStateFlow()

    override fun getPet(petId: String): PocPet? = pets.value.firstOrNull { it.id == petId }

    override fun attachLocalMedia(petId: String, file: FileRef): Result<PocPet> {
        val current = getPet(petId) ?: return Result.failure(NoSuchElementException("PET_NOT_FOUND"))
        val updated = current.copy(pendingMedia = file)
        pets.update { list -> list.map { if (it.id == petId) updated else it } }
        return Result.success(updated)
    }

    override fun clearLocalMedia(petId: String): Result<PocPet> {
        val current = getPet(petId) ?: return Result.failure(NoSuchElementException("PET_NOT_FOUND"))
        val updated = current.copy(pendingMedia = null)
        pets.update { list -> list.map { if (it.id == petId) updated else it } }
        return Result.success(updated)
    }

    companion object {
        val defaultSeed = listOf(
            PocPet(id = "pet-luna", name = "Luna", speciesLabel = "Perro", photoUrl = null),
            PocPet(id = "pet-michi", name = "Michi", speciesLabel = "Gato", photoUrl = null),
            PocPet(id = "pet-kiwi", name = "Kiwi", speciesLabel = "Ave", photoUrl = null)
        )
    }
}
