package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.mock.MockUserStore
import com.comunidapp.app.data.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getUser(userId: String): User?
    suspend fun createUser(user: User): Result<Unit>
    suspend fun updateUser(user: User): Result<Unit>
    suspend fun searchUsers(query: String, excludeUserId: String): List<User>
    fun observeUser(userId: String): Flow<User?>
    fun observeUsers(): Flow<List<User>>
}

class MockUserRepository : UserRepository {

    override suspend fun getUser(userId: String): User? =
        MockUserStore.get(userId) ?: MockData.users.find { it.id == userId }

    override suspend fun createUser(user: User): Result<Unit> {
        MockUserStore.upsert(user)
        return Result.success(Unit)
    }

    override suspend fun updateUser(user: User): Result<Unit> {
        MockUserStore.upsert(user)
        return Result.success(Unit)
    }

    override suspend fun searchUsers(query: String, excludeUserId: String): List<User> =
        MockUserStore.search(query, excludeUserId)

    override fun observeUser(userId: String): Flow<User?> = MockUserStore.observe(userId)

    override fun observeUsers(): Flow<List<User>> = MockUserStore.observeAll()
}
