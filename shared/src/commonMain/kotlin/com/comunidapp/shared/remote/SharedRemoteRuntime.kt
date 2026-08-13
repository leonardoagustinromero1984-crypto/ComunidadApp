package com.comunidapp.shared.remote

import com.comunidapp.shared.adoption.AdoptionRepository
import com.comunidapp.shared.adoption.RemoteAdoptionRepository
import com.comunidapp.shared.adoption.UnconfiguredAdoptionRepository
import com.comunidapp.shared.auth.AuthRepository
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.auth.SecureSessionStorage
import com.comunidapp.shared.auth.SecureStorageSessionManager
import com.comunidapp.shared.auth.SharedSupabaseConfig
import com.comunidapp.shared.auth.SupabaseAuthSessionGateway
import com.comunidapp.shared.auth.UnconfiguredAuthSessionRepository
import com.comunidapp.shared.auth.usableOrNull
import com.comunidapp.shared.lostfound.LostFoundRepository
import com.comunidapp.shared.lostfound.RemoteLostFoundRepository
import com.comunidapp.shared.lostfound.UnconfiguredLostFoundRepository
import com.comunidapp.shared.media.SupabaseM05MediaUploadGateway
import com.comunidapp.shared.media.createFileContentReader
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
import io.github.jan.supabase.storage.Storage

/**
 * Único runtime Kotlin-only: Auth + Postgrest + Storage + Keychain SessionManager.
 * Produce Auth / Profile / Pets / LostFound / Adoption — un solo SupabaseClient.
 * internal — no exportar SupabaseClient a ObjC/Swift.
 */
internal class SharedRemoteRuntime private constructor(
    private val client: SupabaseClient?,
    val authRepository: AuthRepository,
    val profileRepository: UserProfileRepository,
    val petsRepository: SharedPetsRepository,
    val lostFoundRepository: LostFoundRepository,
    val adoptionRepository: AdoptionRepository
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
                    petsRepository = UnconfiguredSharedPetsRepository(),
                    lostFoundRepository = UnconfiguredLostFoundRepository(),
                    adoptionRepository = UnconfiguredAdoptionRepository()
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
            val mediaGateway = M05BackedLostFoundMediaUploadGateway(
                m05 = SupabaseM05MediaUploadGateway(
                    client = client,
                    fileContentReader = createFileContentReader()
                )
            )
            val lostFoundRepository = RemoteLostFoundRepository(
                gateway = SupabaseLostFoundRemoteGateway(client),
                writeGateway = SupabaseLostFoundWriteGateway(client),
                sessionRepository = authRepository,
                mediaUploadGateway = mediaGateway
            )
            val adoptionRepository = RemoteAdoptionRepository(
                gateway = SupabaseAdoptionRemoteGateway(client),
                sessionRepository = authRepository
            )
            return SharedRemoteRuntime(
                client = client,
                authRepository = authRepository,
                profileRepository = profileRepository,
                petsRepository = petsRepository,
                lostFoundRepository = lostFoundRepository,
                adoptionRepository = adoptionRepository
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
                install(Storage)
            }
        }
    }
}
