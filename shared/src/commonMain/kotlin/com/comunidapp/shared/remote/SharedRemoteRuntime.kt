package com.comunidapp.shared.remote

import com.comunidapp.shared.auth.AuthRepository
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.auth.SecureSessionStorage
import com.comunidapp.shared.auth.SecureStorageSessionManager
import com.comunidapp.shared.auth.SharedSupabaseConfig
import com.comunidapp.shared.auth.SupabaseAuthSessionGateway
import com.comunidapp.shared.auth.UnconfiguredAuthSessionRepository
import com.comunidapp.shared.auth.usableOrNull
import com.comunidapp.shared.pets.RemoteSharedPetsRepository
import com.comunidapp.shared.pets.SharedPetsRepository
import com.comunidapp.shared.pets.UnconfiguredSharedPetsRepository
import com.comunidapp.shared.profile.RemoteUserProfileRepository
import com.comunidapp.shared.profile.UnconfiguredUserProfileRepository
import com.comunidapp.shared.profile.UserProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.PropertyConversionMethod

/**
 * Único runtime Kotlin-only: Auth + Postgrest + Keychain SessionManager.
 * internal — no exportar SupabaseClient a ObjC/Swift.
 */
internal class SharedRemoteRuntime private constructor(
    private val client: SupabaseClient?,
    val authRepository: AuthRepository,
    val profileRepository: UserProfileRepository,
    val petsRepository: SharedPetsRepository
) {
    companion object {
        fun create(
            config: SharedSupabaseConfig?,
            storage: SecureSessionStorage
        ): SharedRemoteRuntime {
            val usable = config.usableOrNull()
            if (usable == null) {
                return SharedRemoteRuntime(
                    client = null,
                    authRepository = UnconfiguredAuthSessionRepository(),
                    profileRepository = UnconfiguredUserProfileRepository(),
                    petsRepository = UnconfiguredSharedPetsRepository()
                )
            }
            val client = createClient(usable, storage)
            val authGateway = SupabaseAuthSessionGateway(client)
            val authRepository = GatewayAuthRepository(authGateway)
            val profileRepository = RemoteUserProfileRepository(
                gateway = SupabaseProfileRemoteGateway(client),
                sessionRepository = authRepository
            )
            val petsRepository = RemoteSharedPetsRepository(
                gateway = SupabasePetsRemoteGateway(client),
                sessionRepository = authRepository
            )
            return SharedRemoteRuntime(
                client = client,
                authRepository = authRepository,
                profileRepository = profileRepository,
                petsRepository = petsRepository
            )
        }

        private fun createClient(
            config: SharedSupabaseConfig,
            storage: SecureSessionStorage
        ): SupabaseClient {
            return createSupabaseClient(
                supabaseUrl = config.url.trim(),
                supabaseKey = config.anonKey.trim()
            ) {
                install(Auth) {
                    sessionManager = SecureStorageSessionManager(storage)
                    autoLoadFromStorage = true
                    autoSaveToStorage = true
                    alwaysAutoRefresh = true
                }
                install(Postgrest) {
                    propertyConversionMethod = PropertyConversionMethod.SERIAL_NAME
                }
            }
        }
    }
}
