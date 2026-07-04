package com.comunidapp.app.data.remote.firestore

import com.comunidapp.app.data.model.Pet
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class PetFirestoreDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun getPet(petId: String): Pet? {
        return try {
            firestore.collection(FirestoreCollections.PETS)
                .document(petId)
                .get()
                .await()
                .toPet()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getPetsByOwner(ownerId: String): List<Pet> {
        return try {
            firestore.collection(FirestoreCollections.PETS)
                .whereEqualTo("ownerId", ownerId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
                .documents
                .mapNotNull { it.toPet() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observePets(): Flow<List<Pet>> = callbackFlow {
        val listener = firestore.collection(FirestoreCollections.PETS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val pets = snapshot?.documents?.mapNotNull { it.toPet() } ?: emptyList()
                trySend(pets)
            }
        awaitClose { listener.remove() }
    }

    fun observePetsByOwner(ownerId: String): Flow<List<Pet>> = callbackFlow {
        val listener = firestore.collection(FirestoreCollections.PETS)
            .whereEqualTo("ownerId", ownerId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val pets = snapshot?.documents?.mapNotNull { it.toPet() } ?: emptyList()
                trySend(pets)
            }
        awaitClose { listener.remove() }
    }

    suspend fun createPet(pet: Pet): Result<String> {
        return try {
            val now = Timestamp.now()
            val docRef = if (pet.id.isBlank()) {
                firestore.collection(FirestoreCollections.PETS).document()
            } else {
                firestore.collection(FirestoreCollections.PETS).document(pet.id)
            }
            val petToSave = pet.copy(
                id = docRef.id,
                createdAt = pet.createdAt ?: now.toDate().time,
                updatedAt = now.toDate().time
            )
            docRef.set(petToSave.toFirestoreMap()).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePet(pet: Pet): Result<Unit> {
        return try {
            val data = pet.copy(updatedAt = Timestamp.now().toDate().time).toFirestoreMap()
            firestore.collection(FirestoreCollections.PETS)
                .document(pet.id)
                .set(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePet(petId: String): Result<Unit> {
        return try {
            firestore.collection(FirestoreCollections.PETS)
                .document(petId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
