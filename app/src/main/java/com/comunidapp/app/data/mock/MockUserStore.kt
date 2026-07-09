package com.comunidapp.app.data.mock

import com.comunidapp.app.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Almacén en memoria para perfiles en modo mock.
 * Permite observar y actualizar usuarios de forma reactiva (mismo patrón que Firestore).
 */
object MockUserStore {

    private val users = MutableStateFlow(MockData.users.associateBy { it.id })

    fun observe(userId: String): Flow<User?> = users.map { it[userId] }

    fun observeAll(): Flow<List<User>> = users.map { it.values.toList() }

    fun get(userId: String): User? = users.value[userId]

    fun upsert(user: User) {
        users.update { current -> current + (user.id to user) }
    }
}
