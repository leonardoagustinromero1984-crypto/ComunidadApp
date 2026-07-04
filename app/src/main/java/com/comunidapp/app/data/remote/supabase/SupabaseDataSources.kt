package com.comunidapp.app.data.remote.supabase

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.User
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

class UserSupabaseDataSource {

    suspend fun getUser(userId: String): User? {
        return try {
            supabase.from(SupabaseTables.USERS)
                .select {
                    filter { eq("id", userId) }
                }
                .decodeSingleOrNull<UserRow>()
                ?.let(::parseUser)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun createUser(user: User): Result<Unit> {
        return try {
            val row = user.toUserRow()
            supabase.from(SupabaseTables.USERS).insert(row)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUser(user: User): Result<Unit> {
        return try {
            val row = user.toUserRow()
            supabase.from(SupabaseTables.USERS).update(row) {
                filter { eq("id", user.id) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateEmailVerified(userId: String, verified: Boolean): Result<Unit> {
        return try {
            supabase.from(SupabaseTables.USERS).update(
                mapOf(
                    "email_verified" to verified,
                    "updated_at" to nowIso()
                )
            ) {
                filter { eq("id", userId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeUser(userId: String): Flow<User?> = pollingFlow {
        getUser(userId)
    }
}

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

private fun <T> pollingFlow(fetch: suspend () -> T): Flow<T> = flow {
    while (coroutineContext.isActive) {
        emit(fetch())
        delay(4_000)
    }
}
