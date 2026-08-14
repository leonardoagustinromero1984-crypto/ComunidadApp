package com.comunidapp.shared.remote

import com.comunidapp.shared.adoption.AdoptionApplicationRepository
import com.comunidapp.shared.adoption.AdoptionRepository
import com.comunidapp.shared.adoption.RemoteAdoptionApplicationRepository
import com.comunidapp.shared.adoption.RemoteAdoptionRepository
import com.comunidapp.shared.adoption.UnconfiguredAdoptionApplicationRepository
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
import com.comunidapp.shared.media.CachingMediaResolver
import com.comunidapp.shared.media.MediaResolver
import com.comunidapp.shared.media.SupabaseM05MediaReadGateway
import com.comunidapp.shared.media.SupabaseM05MediaUploadGateway
import com.comunidapp.shared.media.UnavailableMediaResolver
import com.comunidapp.shared.media.createFileContentReader
import com.comunidapp.shared.media.createMediaHttpClient
import com.comunidapp.shared.pets.RemoteSharedPetsRepository
import com.comunidapp.shared.pets.SharedPetsRepository
import com.comunidapp.shared.pets.UnconfiguredSharedPetsRepository
import com.comunidapp.shared.platform.PlatformClock
import com.comunidapp.shared.profile.RemoteUserProfileRepository
import com.comunidapp.shared.profile.SupabaseProfileAvatarUploadGateway
import com.comunidapp.shared.profile.UnconfiguredUserProfileRepository
import com.comunidapp.shared.profile.UserProfileRepository
import com.comunidapp.shared.publiccontent.PublicContentRepository
import com.comunidapp.shared.publiccontent.RemotePublicContentRepository
import com.comunidapp.shared.publiccontent.UnconfiguredPublicContentRepository
import com.comunidapp.shared.push.PushInstallationRepository
import com.comunidapp.shared.push.RemotePushInstallationRepository
import com.comunidapp.shared.push.SupabasePushInstallationGateway
import com.comunidapp.shared.push.UnconfiguredPushInstallationRepository
import com.comunidapp.shared.session.SessionState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.PropertyConversionMethod
import io.github.jan.supabase.storage.Storage

/**
 * Único runtime Kotlin-only — un solo SupabaseClient.
 * KMP-11…16 + KMP-20 public content + KMP-21 pet edit + KMP-22 LF manage.
 */
internal class SharedRemoteRuntime private constructor(
    private val client: SupabaseClient?,
    val authRepository: AuthRepository,
    val profileRepository: UserProfileRepository,
    val petsRepository: SharedPetsRepository,
    val lostFoundRepository: LostFoundRepository,
    val adoptionRepository: AdoptionRepository,
    val adoptionApplicationRepository: AdoptionApplicationRepository,
    val publicContentRepository: PublicContentRepository,
    val mediaResolver: MediaResolver,
    val pushInstallationRepository: PushInstallationRepository
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
                    adoptionRepository = UnconfiguredAdoptionRepository(),
                    adoptionApplicationRepository = UnconfiguredAdoptionApplicationRepository(),
                    publicContentRepository = UnconfiguredPublicContentRepository(),
                    mediaResolver = UnavailableMediaResolver(),
                    pushInstallationRepository = UnconfiguredPushInstallationRepository()
                )
            }
            val client = createClient(usable, storage)
            val authGateway = SupabaseAuthSessionGateway(client)
            val authRepository = GatewayAuthRepository(authGateway)
            val mediaResolver: MediaResolver = CachingMediaResolver(
                gateway = SupabaseM05MediaReadGateway(
                    client = client,
                    httpClient = createMediaHttpClient()
                ),
                clock = { PlatformClock.SYSTEM.nowEpochMs() },
                checkAuthenticated = {
                    authRepository.currentSession() is SessionState.Authenticated
                }
            )
            val profileRepository = RemoteUserProfileRepository(
                gateway = SupabaseProfileRemoteGateway(client),
                writeGateway = SupabaseProfileWriteRemoteGateway(client),
                avatarUpload = SupabaseProfileAvatarUploadGateway(
                    client = client,
                    fileContentReader = createFileContentReader()
                ),
                sessionRepository = authRepository,
                mediaResolver = mediaResolver
            )
            val m05UploadGateway = SupabaseM05MediaUploadGateway(
                client = client,
                fileContentReader = createFileContentReader()
            )
            val petsRepository = RemoteSharedPetsRepository(
                gateway = SupabasePetsRemoteGateway(client),
                sessionRepository = authRepository,
                mediaUploadGateway = m05UploadGateway
            )
            val mediaGateway = M05BackedLostFoundMediaUploadGateway(
                m05 = m05UploadGateway
            )
            val lostFoundRepository = RemoteLostFoundRepository(
                gateway = SupabaseLostFoundRemoteGateway(client),
                writeGateway = SupabaseLostFoundWriteGateway(client),
                sessionRepository = authRepository,
                mediaUploadGateway = mediaGateway
            )
            val adoptionGateway = SupabaseAdoptionRemoteGateway(client)
            val adoptionRepository = RemoteAdoptionRepository(
                gateway = adoptionGateway,
                sessionRepository = authRepository
            )
            val adoptionApplicationRepository = RemoteAdoptionApplicationRepository(
                gateway = SupabaseAdoptionApplicationRemoteGateway(client),
                sessionRepository = authRepository
            )
            val publicContentRepository = RemotePublicContentRepository(
                gateway = SupabasePublicContentRemoteGateway(client)
            )
            val pushInstallationRepository = RemotePushInstallationRepository(
                gateway = SupabasePushInstallationGateway(client),
                sessionRepository = authRepository
            )
            return SharedRemoteRuntime(
                client = client,
                authRepository = authRepository,
                profileRepository = profileRepository,
                petsRepository = petsRepository,
                lostFoundRepository = lostFoundRepository,
                adoptionRepository = adoptionRepository,
                adoptionApplicationRepository = adoptionApplicationRepository,
                publicContentRepository = publicContentRepository,
                mediaResolver = mediaResolver,
                pushInstallationRepository = pushInstallationRepository
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
