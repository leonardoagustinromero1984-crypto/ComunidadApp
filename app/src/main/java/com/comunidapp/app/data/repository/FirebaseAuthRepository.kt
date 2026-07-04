package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.remote.firestore.FirestoreCollections
import com.comunidapp.app.data.remote.firestore.toFirestoreMap
import com.comunidapp.app.data.remote.firestore.toUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository : AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    override suspend fun login(email: String, password: String): Result<User> {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Email y contraseña son requeridos"))
        }
        return try {
            auth.signInWithEmailAndPassword(normalizedEmail, password).await()
            val firebaseUser = auth.currentUser
                ?: return Result.failure(IllegalArgumentException("Error al iniciar sesión"))

            firebaseUser.reload().await()
            if (!firebaseUser.isEmailVerified) {
                return Result.failure(EmailNotVerifiedException(normalizedEmail))
            }

            val profile = fetchUserProfile(
                firebaseUser.uid,
                normalizedEmail,
                firebaseUser.displayName
            )
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(mapFirebaseException(e))
        }
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        accountType: AccountType
    ): Result<User> {
        val normalizedEmail = email.trim().lowercase()
        if (name.isBlank() || normalizedEmail.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Todos los campos son requeridos"))
        }
        return try {
            auth.createUserWithEmailAndPassword(normalizedEmail, password).await()
            val firebaseUser = auth.currentUser
                ?: return Result.failure(IllegalArgumentException("Error al crear la cuenta"))

            firebaseUser.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
            ).await()

            val user = User(
                id = firebaseUser.uid,
                name = name.trim(),
                email = normalizedEmail,
                accountType = accountType
            )
            saveUserToFirestore(user)
            firebaseUser.sendEmailVerification().await()
            // Mantener sesión activa para poder reenviar / verificar email
            Result.success(user)
        } catch (e: Exception) {
            auth.signOut()
            Result.failure(mapFirebaseException(e))
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Ingresá tu email"))
        }
        return try {
            auth.sendPasswordResetEmail(normalizedEmail).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapFirebaseException(e))
        }
    }

    override suspend fun resetPassword(
        email: String,
        token: String,
        newPassword: String
    ): Result<Unit> {
        return Result.failure(
            IllegalArgumentException("Abrí el link que te enviamos por email para restablecer tu contraseña")
        )
    }

    override suspend fun sendEmailVerification(email: String): Result<Unit> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(IllegalArgumentException("No hay sesión activa para reenviar el email"))
            if (!user.email.equals(email.trim(), ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("El email no coincide con la sesión activa"))
            }
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapFirebaseException(e))
        }
    }

    override suspend fun confirmEmailVerification(email: String): Result<Unit> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(
                    IllegalArgumentException("Iniciá sesión con tu email y contraseña para verificar.")
                )
            if (!user.email.equals(email.trim(), ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("El email no coincide con la sesión activa"))
            }
            user.reload().await()
            if (user.isEmailVerified) {
                firestore.collection(FirestoreCollections.USERS).document(user.uid)
                    .update("emailVerified", true)
                    .await()
                auth.signOut()
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalArgumentException("Tu email aún no está confirmado. Revisá tu bandeja de entrada y spam.")
                )
            }
        } catch (e: Exception) {
            Result.failure(mapFirebaseException(e))
        }
    }

    override suspend fun isEmailVerified(email: String): Boolean {
        val user = auth.currentUser ?: return false
        if (!user.email.equals(email.trim(), ignoreCase = true)) return false
        user.reload().await()
        return user.isEmailVerified
    }

    override fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        return User(
            id = firebaseUser.uid,
            name = firebaseUser.displayName ?: "",
            email = firebaseUser.email ?: ""
        )
    }

    override fun logout() {
        auth.signOut()
    }

    override fun observeAuthState(): Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            val user = if (firebaseUser != null && firebaseUser.isEmailVerified) {
                firebaseUser.toUser()
            } else {
                null
            }
            trySend(user)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    private fun com.google.firebase.auth.FirebaseUser.toUser(): User = User(
        id = uid,
        name = displayName ?: "",
        email = email ?: ""
    )

    private suspend fun saveUserToFirestore(user: User) {
        val now = com.google.firebase.Timestamp.now()
        val userDoc = user.copy(
            accountType = user.accountType,
            emailVerified = false,
            createdAt = user.createdAt ?: now.toDate().time,
            updatedAt = now.toDate().time
        )
        firestore.collection(FirestoreCollections.USERS).document(user.id).set(
            userDoc.toFirestoreMap(),
            SetOptions.merge()
        ).await()
    }

    private suspend fun fetchUserProfile(uid: String, email: String, displayName: String?): User {
        return try {
            val doc = firestore.collection(FirestoreCollections.USERS).document(uid).get().await()
            doc.toUser() ?: User(
                id = uid,
                name = displayName ?: "",
                email = email,
                accountType = AccountType.PERSON
            )
        } catch (_: Exception) {
            User(
                id = uid,
                name = displayName ?: "",
                email = email,
                accountType = AccountType.PERSON
            )
        }
    }

    private fun mapFirebaseException(e: Exception): Exception {
        val message = when (e) {
            is FirebaseAuthInvalidUserException -> "No encontramos una cuenta con ese email"
            is FirebaseAuthInvalidCredentialsException -> "Email o contraseña incorrectos"
            is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese email"
            is FirebaseAuthWeakPasswordException -> "La contraseña debe tener al menos 6 caracteres"
            else -> e.localizedMessage ?: "Ocurrió un error. Intentá de nuevo."
        }
        return IllegalArgumentException(message)
    }
}
