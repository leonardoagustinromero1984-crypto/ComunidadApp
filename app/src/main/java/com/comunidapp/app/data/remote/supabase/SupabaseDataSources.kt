package com.comunidapp.app.data.remote.supabase

import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.Pet
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

class PetSupabaseDataSource {

    suspend fun getPet(petId: String): Pet? {
        return try {
            supabase.from(SupabaseTables.PETS)
                .select {
                    filter { eq("id", petId) }
                }
                .decodeSingleOrNull<PetRow>()
                ?.let(::parsePet)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchPets(limit: Int = 100): List<Pet> {
        return try {
            supabase.from(SupabaseTables.PETS)
                .select {
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<PetRow>()
                .map(::parsePet)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observePets(): Flow<List<Pet>> = pollingFlow { fetchPets() }

    fun observePet(petId: String): Flow<Pet?> = pollingFlow { getPet(petId) }

    suspend fun fetchPetsByOwner(ownerId: String): List<Pet> {
        return try {
            supabase.from(SupabaseTables.PETS)
                .select {
                    filter { eq("owner_id", ownerId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<PetRow>()
                .map(::parsePet)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observePetsForOwner(ownerId: String): Flow<List<Pet>> =
        pollingFlow { fetchPetsByOwner(ownerId) }

    suspend fun createPet(pet: Pet): Result<String> {
        return try {
            val row = if (pet.id.isBlank()) {
                pet.toPetRow().copy(id = java.util.UUID.randomUUID().toString())
            } else {
                pet.toPetRow()
            }
            supabase.from(SupabaseTables.PETS).insert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePet(pet: Pet): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.PETS).update(pet.toPetRow()) {
                filter { eq("id", pet.id) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePet(petId: String): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.PETS).delete {
                filter { eq("id", petId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class PostSupabaseDataSource {

    suspend fun fetchPosts(): List<com.comunidapp.app.data.model.FeedPost> {
        return try {
            supabase.from(SupabaseTables.POSTS)
                .select {
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<PostRow>()
                .map(::parseFeedPost)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observePosts(): Flow<List<com.comunidapp.app.data.model.FeedPost>> =
        pollingFlow { fetchPosts() }

    suspend fun addPost(post: com.comunidapp.app.data.model.FeedPost): Result<String> {
        return try {
            val row = if (post.id.isBlank()) {
                post.toPostRow().copy(id = java.util.UUID.randomUUID().toString())
            } else {
                post.toPostRow()
            }
            supabase.from(SupabaseTables.POSTS).insert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePost(post: com.comunidapp.app.data.model.FeedPost): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.POSTS).update(post.toPostRow()) {
                filter { eq("id", post.id) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class AdoptionSupabaseDataSource {

    suspend fun fetchAdoptions(): List<AdoptionPost> {
        return try {
            supabase.from(SupabaseTables.ADOPTIONS)
                .select {
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<AdoptionRow>()
                .map(::parseAdoption)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observeAdoptions(): Flow<List<AdoptionPost>> = pollingFlow { fetchAdoptions() }

    suspend fun getAdoption(id: String): AdoptionPost? {
        return try {
            supabase.from(SupabaseTables.ADOPTIONS)
                .select {
                    filter { eq("id", id) }
                }
                .decodeSingleOrNull<AdoptionRow>()
                ?.let(::parseAdoption)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun addAdoption(post: AdoptionPost): Result<String> {
        return try {
            val row = if (post.id.isBlank()) {
                post.toAdoptionRow().copy(id = java.util.UUID.randomUUID().toString())
            } else {
                post.toAdoptionRow()
            }
            supabase.from(SupabaseTables.ADOPTIONS).insert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAdoption(post: AdoptionPost): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.ADOPTIONS).update(post.toAdoptionRow()) {
                filter { eq("id", post.id) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class LostFoundSupabaseDataSource {

    suspend fun fetchLostFound(): List<LostFoundPost> {
        return try {
            supabase.from(SupabaseTables.LOST_FOUND)
                .select {
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<LostFoundRow>()
                .map(::parseLostFound)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observeLostFound(): Flow<List<LostFoundPost>> = pollingFlow { fetchLostFound() }

    suspend fun addLostFound(post: LostFoundPost): Result<String> {
        return try {
            val row = if (post.id.isBlank()) {
                post.toLostFoundRow().copy(id = java.util.UUID.randomUUID().toString())
            } else {
                post.toLostFoundRow()
            }
            supabase.from(SupabaseTables.LOST_FOUND).insert(row)
            Result.success(row.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateLostFound(post: LostFoundPost): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.LOST_FOUND).update(post.toLostFoundRow()) {
                filter { eq("id", post.id) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateStatus(id: String, status: LostFoundStatus): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.LOST_FOUND).update(
                mapOf(
                    "status" to status.name,
                    "updated_at" to nowIso()
                )
            ) {
                filter { eq("id", id) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun <T> pollingFlow(fetch: suspend () -> T): Flow<T> = flow {
    while (coroutineContext.isActive) {
        try {
            emit(fetch())
        } catch (_: Exception) {
            // Ignorar errores transitorios de red/Supabase y seguir polling.
        }
        delay(4_000)
    }
}
