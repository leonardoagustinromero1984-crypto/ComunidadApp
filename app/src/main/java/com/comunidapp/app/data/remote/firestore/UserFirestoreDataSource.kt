package com.comunidapp.app.data.remote.firestore

import com.comunidapp.app.data.model.User
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UserFirestoreDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun getUser(userId: String): User? {
        return try {
            firestore.collection(FirestoreCollections.USERS)
                .document(userId)
                .get()
                .await()
                .toUser()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun createUser(user: User): Result<Unit> {
        return try {
            val now = Timestamp.now()
            val data = user.copy(
                createdAt = user.createdAt ?: now.toDate().time,
                updatedAt = now.toDate().time
            ).toFirestoreMap()
            firestore.collection(FirestoreCollections.USERS)
                .document(user.id)
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUser(user: User): Result<Unit> {
        return try {
            val data = user.copy(updatedAt = Timestamp.now().toDate().time).toFirestoreMap()
            firestore.collection(FirestoreCollections.USERS)
                .document(user.id)
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeUser(userId: String): Flow<User?> = callbackFlow {
        val listener = firestore.collection(FirestoreCollections.USERS)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toUser())
            }
        awaitClose { listener.remove() }
    }
}
