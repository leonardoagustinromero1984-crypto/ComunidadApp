package com.comunidapp.app.data.remote.firestore

import com.comunidapp.app.data.model.FeedPost
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class PostFirestoreDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun observePosts(): Flow<List<FeedPost>> = callbackFlow {
        val listener = firestore.collection(FirestoreCollections.POSTS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val posts = snapshot?.documents?.mapNotNull { it.toFeedPost() } ?: emptyList()
                trySend(posts)
            }
        awaitClose { listener.remove() }
    }

    fun observePostsByAuthor(authorId: String): Flow<List<FeedPost>> = callbackFlow {
        val listener = firestore.collection(FirestoreCollections.POSTS)
            .whereEqualTo("authorId", authorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val posts = snapshot?.documents?.mapNotNull { it.toFeedPost() } ?: emptyList()
                trySend(posts)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addPost(post: FeedPost): Result<Unit> {
        return try {
            val docRef = if (post.id.isBlank()) {
                firestore.collection(FirestoreCollections.POSTS).document()
            } else {
                firestore.collection(FirestoreCollections.POSTS).document(post.id)
            }
            val postToSave = post.copy(id = docRef.id)
            docRef.set(postToSave.toFirestoreMap()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
