package com.comunidapp.app.data.provider

import com.comunidapp.app.LeoverApplication
import com.comunidapp.app.core.config.AppConfigProvider
import com.comunidapp.app.data.files.AndroidContentFileMetadataReader
import com.comunidapp.app.data.files.AndroidFileBytesReader
import com.comunidapp.app.data.files.FileBytesReader
import com.comunidapp.app.data.files.FileDisplayResolver
import com.comunidapp.app.data.files.FileLocalMetadataReader
import com.comunidapp.app.data.files.FileObjectUploader
import com.comunidapp.app.data.files.FileUploadCoordinator
import com.comunidapp.app.data.files.MockFileObjectUploader
import com.comunidapp.app.data.files.SupabaseFileObjectUploader
import com.comunidapp.app.data.remote.storage.ImageStorageService
import com.comunidapp.app.data.remote.storage.OrganizationMediaStorageService
import com.comunidapp.app.data.remote.storage.ProfileAvatarStorageService
import com.comunidapp.app.data.remote.storage.SupabaseStorageService
import com.comunidapp.app.data.repository.AdoptionApplicationRepository
import com.comunidapp.app.data.repository.AdoptionAgreementRepository
import com.comunidapp.app.data.repository.AdoptionCompletionRepository
import com.comunidapp.app.data.repository.AdoptionDocumentRepository
import com.comunidapp.app.data.repository.AdoptionFollowUpRepository
import com.comunidapp.app.data.repository.AdoptionInterviewRepository
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.AdoptionRequestRepository
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.ChatRepository
import com.comunidapp.app.data.repository.ClientDeniedNotificationDeliveryRepository
import com.comunidapp.app.data.repository.ClientDeniedNotificationOutboxRepository
import com.comunidapp.app.data.repository.CommunityRepository
import com.comunidapp.app.data.repository.FosterEvolutionRepository
import com.comunidapp.app.data.repository.FosterExpenseRepository
import com.comunidapp.app.data.repository.FosterHelpRepository
import com.comunidapp.app.data.repository.FosterHomeRepository
import com.comunidapp.app.data.repository.FosterPlacementRepository
import com.comunidapp.app.data.repository.FosterRequestRepository
import com.comunidapp.app.data.repository.M10FosterMemoryStore
import com.comunidapp.app.data.repository.MockFosterEvolutionRepository
import com.comunidapp.app.data.repository.MockFosterExpenseRepository
import com.comunidapp.app.data.repository.MockFosterHelpRepository
import com.comunidapp.app.data.repository.MockFosterHomeRepository
import com.comunidapp.app.data.repository.MockFosterPlacementRepository
import com.comunidapp.app.data.repository.MockFosterRequestRepository
import com.comunidapp.app.data.repository.SupabaseFosterEvolutionRepository
import com.comunidapp.app.data.repository.SupabaseFosterExpenseRepository
import com.comunidapp.app.data.repository.SupabaseFosterHelpRepository
import com.comunidapp.app.data.repository.SupabaseFosterHomeRepository
import com.comunidapp.app.data.repository.SupabaseFosterPlacementRepository
import com.comunidapp.app.data.repository.SupabaseFosterRequestRepository
import com.comunidapp.app.data.repository.m10ResolvePetFromStore
import com.comunidapp.app.data.repository.M09CompletionMemoryStore
import com.comunidapp.app.data.repository.MockAdoptionAgreementRepository
import com.comunidapp.app.data.repository.MockAdoptionApplicationRepository
import com.comunidapp.app.data.repository.MockAdoptionCompletionRepository
import com.comunidapp.app.data.repository.MockAdoptionDocumentRepository
import com.comunidapp.app.data.repository.MockAdoptionFollowUpRepository
import com.comunidapp.app.data.repository.MockAdoptionInterviewRepository
import com.comunidapp.app.data.repository.MockAdoptionRequestRepository
import com.comunidapp.app.data.repository.MockChatRepository
import com.comunidapp.app.data.repository.MockCommunityRepository
import com.comunidapp.app.data.repository.SupabaseAdoptionAgreementRepository
import com.comunidapp.app.data.repository.SupabaseAdoptionApplicationRepository
import com.comunidapp.app.data.repository.SupabaseAdoptionCompletionRepository
import com.comunidapp.app.data.repository.SupabaseAdoptionDocumentRepository
import com.comunidapp.app.data.repository.SupabaseAdoptionFollowUpRepository
import com.comunidapp.app.data.repository.SupabaseAdoptionInterviewRepository
import com.comunidapp.app.data.repository.SupabaseAdoptionRequestRepository
import com.comunidapp.app.data.repository.SupabaseChatRepository
import com.comunidapp.app.data.repository.FeedRepository
import com.comunidapp.app.data.repository.FriendRepository
import com.comunidapp.app.data.repository.MockFriendRepository
import com.comunidapp.app.data.repository.SupabaseFriendRepository
import com.comunidapp.app.data.repository.LostFoundRepository
import com.comunidapp.app.data.repository.MockAdoptionRepository
import com.comunidapp.app.data.repository.MockFeedRepository
import com.comunidapp.app.data.repository.MockLostFoundRepository
import com.comunidapp.app.data.repository.LegacyPetRepositoryAdapter
import com.comunidapp.app.data.repository.MockPetRepository
import com.comunidapp.app.data.repository.MockShelterRepository
import com.comunidapp.app.data.repository.MockUserRepository
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.SupabasePetAuthorizationRepository
import com.comunidapp.app.data.repository.SupabasePetDomainRepository
import com.comunidapp.app.data.repository.SupabasePetResponsibilityRepository
import com.comunidapp.app.data.repository.SupabasePetTransferRepository
import com.comunidapp.app.domain.pets.PetAuthorizationRepository
import com.comunidapp.app.domain.pets.PetDomainRepository
import com.comunidapp.app.domain.pets.PetResponsibilityRepository
import com.comunidapp.app.domain.pets.PetTransferRepository
import com.comunidapp.app.data.repository.AdministrativeAuditRepository
import com.comunidapp.app.data.repository.FileAccessRepository
import com.comunidapp.app.data.repository.FileAssetRepository
import com.comunidapp.app.data.repository.FileDownloadRepository
import com.comunidapp.app.data.repository.FileRetentionRepository
import com.comunidapp.app.data.repository.FileUploadRepository
import com.comunidapp.app.data.repository.MockFileAccessRepository
import com.comunidapp.app.data.repository.MockFileAssetRepository
import com.comunidapp.app.data.repository.MockFileDownloadRepository
import com.comunidapp.app.data.repository.MockFileRetentionRepository
import com.comunidapp.app.data.repository.MockFileUploadRepository
import com.comunidapp.app.data.repository.MockAdministrativeAuditRepository
import com.comunidapp.app.data.repository.MockModerationRepository
import com.comunidapp.app.data.repository.MockNotificationDeliveryRepository
import com.comunidapp.app.data.repository.MockNotificationInboxRepository
import com.comunidapp.app.data.repository.MockNotificationInstallationRepository
import com.comunidapp.app.data.repository.MockNotificationOutboxRepository
import com.comunidapp.app.data.repository.MockNotificationPreferenceRepository
import com.comunidapp.app.data.repository.MockNotificationRepositories
import com.comunidapp.app.data.repository.MockObservabilityRepositories
import com.comunidapp.app.data.repository.MockOrganizationInvitationRepository
import com.comunidapp.app.data.repository.AuditEventRepository
import com.comunidapp.app.data.repository.SecurityEventRepository
import com.comunidapp.app.data.repository.ApplicationErrorRepository
import com.comunidapp.app.data.repository.PerformanceMetricRepository
import com.comunidapp.app.data.repository.HealthCheckRepository
import com.comunidapp.app.data.repository.AnalyticsEventRepository
import com.comunidapp.app.data.repository.AlertRepository
import com.comunidapp.app.data.repository.ObservabilityExportRepository
import com.comunidapp.app.data.repository.EventCatalogRepository
import com.comunidapp.app.data.repository.CorrelationContextRepository
import com.comunidapp.app.data.repository.ClientDeniedAuditEventRepository
import com.comunidapp.app.data.repository.SupabaseAuditEventRepository
import com.comunidapp.app.data.repository.SupabaseSecurityEventRepository
import com.comunidapp.app.data.repository.SupabaseApplicationErrorRepository
import com.comunidapp.app.data.repository.SupabaseObservabilityExportRepository
import com.comunidapp.app.data.repository.OperationalObservabilityRepository
import com.comunidapp.app.data.repository.MockOperationalObservabilityRepository
import com.comunidapp.app.data.repository.SupabaseOperationalObservabilityRepository
import com.comunidapp.app.data.repository.RetentionRepository
import com.comunidapp.app.data.repository.MockRetentionRepository
import com.comunidapp.app.data.repository.SupabaseRetentionRepository
import com.comunidapp.app.data.repository.MockOrganizationMembershipRepository
import com.comunidapp.app.data.repository.MockOrganizationPermissionRepository
import com.comunidapp.app.data.repository.MockOrganizationRepository
import com.comunidapp.app.data.repository.MockOrganizationVerificationRepository
import com.comunidapp.app.data.repository.MockPermissionRepository
import com.comunidapp.app.data.repository.MockPlatformAdministrationRepository
import com.comunidapp.app.data.repository.MockPlatformRepository
import com.comunidapp.app.data.repository.MockServiceRepository
import com.comunidapp.app.data.repository.MockSupportRepository
import com.comunidapp.app.data.repository.ModerationRepository
import com.comunidapp.app.data.repository.NotificationDeliveryRepository
import com.comunidapp.app.data.repository.NotificationInboxRepository
import com.comunidapp.app.data.repository.NotificationInstallationRepository
import com.comunidapp.app.data.repository.NotificationOutboxRepository
import com.comunidapp.app.data.repository.NotificationPreferenceRepository
import com.comunidapp.app.data.repository.OrganizationInvitationRepository
import com.comunidapp.app.data.repository.OrganizationMembershipRepository
import com.comunidapp.app.data.repository.OrganizationPermissionRepository
import com.comunidapp.app.data.repository.OrganizationRepository
import com.comunidapp.app.data.repository.OrganizationVerificationRepository
import com.comunidapp.app.data.repository.PermissionRepository
import com.comunidapp.app.data.repository.PlatformAdministrationRepository
import com.comunidapp.app.data.repository.PlatformRepository
import com.comunidapp.app.data.repository.ServiceRepository
import com.comunidapp.app.data.repository.SupportRepository
import com.comunidapp.app.domain.notifications.NotificationRetryPolicy
import java.time.Instant
import com.comunidapp.app.data.repository.SupabaseAdministrativeAuditRepository
import com.comunidapp.app.data.repository.SupabaseModerationRepository
import com.comunidapp.app.data.repository.SupabaseOrganizationInvitationRepository
import com.comunidapp.app.data.repository.SupabaseOrganizationMembershipRepository
import com.comunidapp.app.data.repository.SupabaseOrganizationPermissionRepository
import com.comunidapp.app.data.repository.SupabaseOrganizationRepository
import com.comunidapp.app.data.repository.SupabaseOrganizationVerificationRepository
import com.comunidapp.app.data.repository.SupabasePermissionRepository
import com.comunidapp.app.data.repository.SupabasePlatformAdministrationRepository
import com.comunidapp.app.data.repository.SupabasePlatformRepository
import com.comunidapp.app.data.repository.SupabaseServiceRepository
import com.comunidapp.app.data.repository.SupabaseSupportRepository
import com.comunidapp.app.data.repository.SupabaseFileAccessRepository
import com.comunidapp.app.data.repository.SupabaseFileAssetRepository
import com.comunidapp.app.data.repository.SupabaseFileDownloadRepository
import com.comunidapp.app.data.repository.SupabaseFileRetentionRepository
import com.comunidapp.app.data.repository.SupabaseFileUploadRepository
import com.comunidapp.app.data.repository.ShelterRepository
import com.comunidapp.app.data.repository.SupabaseAdoptionRepository
import com.comunidapp.app.data.repository.SupabaseCommunityRepository
import com.comunidapp.app.data.repository.SupabaseFeedRepository
import com.comunidapp.app.data.repository.SupabaseLostFoundRepository
import com.comunidapp.app.data.repository.SupabaseNotificationInboxRepository
import com.comunidapp.app.data.repository.SupabaseNotificationInstallationRepository
import com.comunidapp.app.data.repository.SupabaseNotificationPreferenceRepository
import com.comunidapp.app.data.repository.SupabaseShelterRepository
import com.comunidapp.app.data.repository.SupabaseUserRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.viewmodel.files.FileSessionCleanup

object DataProvider {

    val useSupabase: Boolean get() = AppConfigProvider.featureFlags().useSupabase

    val userRepository: UserRepository by lazy {
        if (useSupabase) SupabaseUserRepository() else MockUserRepository()
    }

    val petRepository: PetRepository by lazy {
        if (useSupabase) LegacyPetRepositoryAdapter() else MockPetRepository()
    }

    /** LeoVer M08 domain repos (optional; wired when useSupabase). */
    val petDomainRepository: PetDomainRepository? by lazy {
        if (useSupabase) SupabasePetDomainRepository() else null
    }

    val petResponsibilityRepository: PetResponsibilityRepository? by lazy {
        if (useSupabase) SupabasePetResponsibilityRepository() else null
    }

    val petAuthorizationRepository: PetAuthorizationRepository? by lazy {
        if (useSupabase) SupabasePetAuthorizationRepository() else null
    }

    val petTransferRepository: PetTransferRepository? by lazy {
        if (useSupabase) SupabasePetTransferRepository() else null
    }

    val feedRepository: FeedRepository by lazy {
        if (useSupabase) SupabaseFeedRepository() else MockFeedRepository()
    }

    val adoptionRepository: AdoptionRepository by lazy {
        if (useSupabase) {
            SupabaseAdoptionRepository()
        } else {
            MockAdoptionRepository(
                actorUserId = { AuthProvider.repository.getCurrentUser()?.id }
            )
        }
    }

    val lostFoundRepository: LostFoundRepository by lazy {
        if (useSupabase) SupabaseLostFoundRepository() else MockLostFoundRepository()
    }

    val adoptionRequestRepository: AdoptionRequestRepository by lazy {
        if (useSupabase) SupabaseAdoptionRequestRepository() else MockAdoptionRequestRepository()
    }

    private val m09ApplicationStore by lazy {
        kotlinx.coroutines.flow.MutableStateFlow<List<com.comunidapp.app.data.model.AdoptionApplication>>(emptyList())
    }

    private val m09CompletionStore by lazy { M09CompletionMemoryStore() }

    val adoptionApplicationRepository: AdoptionApplicationRepository by lazy {
        if (useSupabase) {
            SupabaseAdoptionApplicationRepository()
        } else {
            MockAdoptionApplicationRepository(
                actorUserId = { AuthProvider.repository.getCurrentUser()?.id },
                actorName = { AuthProvider.repository.getCurrentUser()?.name ?: "Usuario" },
                store = m09ApplicationStore
            )
        }
    }

    private fun m09IsManager(adoptionId: String, userId: String): Boolean {
        val post = com.comunidapp.app.data.mock.InMemoryDataStore.getAdoptionPostById(adoptionId)
            ?: return false
        return post.publisherId == userId || post.shelterId == userId
    }

    private fun m09ApplicationsSnapshot(): List<com.comunidapp.app.data.model.AdoptionApplication> =
        (adoptionApplicationRepository as? MockAdoptionApplicationRepository)?.snapshot()
            ?: m09ApplicationStore.value

    val adoptionInterviewRepository: AdoptionInterviewRepository by lazy {
        if (useSupabase) {
            SupabaseAdoptionInterviewRepository()
        } else {
            MockAdoptionInterviewRepository(
                actorUserId = { AuthProvider.repository.getCurrentUser()?.id },
                applications = { m09ApplicationsSnapshot() },
                isManager = ::m09IsManager,
                store = m09CompletionStore
            )
        }
    }

    val adoptionDocumentRepository: AdoptionDocumentRepository by lazy {
        if (useSupabase) {
            SupabaseAdoptionDocumentRepository()
        } else {
            MockAdoptionDocumentRepository(
                actorUserId = { AuthProvider.repository.getCurrentUser()?.id },
                applications = { m09ApplicationsSnapshot() },
                isManager = ::m09IsManager,
                store = m09CompletionStore
            )
        }
    }

    val adoptionAgreementRepository: AdoptionAgreementRepository by lazy {
        if (useSupabase) {
            SupabaseAdoptionAgreementRepository()
        } else {
            MockAdoptionAgreementRepository(
                actorUserId = { AuthProvider.repository.getCurrentUser()?.id },
                applications = { m09ApplicationsSnapshot() },
                isManager = ::m09IsManager,
                store = m09CompletionStore
            )
        }
    }

    val adoptionCompletionRepository: AdoptionCompletionRepository by lazy {
        if (useSupabase) {
            SupabaseAdoptionCompletionRepository()
        } else {
            MockAdoptionCompletionRepository(
                actorUserId = { AuthProvider.repository.getCurrentUser()?.id },
                applications = { m09ApplicationsSnapshot() },
                isManager = ::m09IsManager,
                store = m09CompletionStore
            )
        }
    }

    val adoptionFollowUpRepository: AdoptionFollowUpRepository by lazy {
        if (useSupabase) {
            SupabaseAdoptionFollowUpRepository()
        } else {
            MockAdoptionFollowUpRepository(
                actorUserId = { AuthProvider.repository.getCurrentUser()?.id },
                isManager = ::m09IsManager,
                store = m09CompletionStore
            )
        }
    }

    private val m10FosterStore by lazy { M10FosterMemoryStore() }

    val fosterHomeRepository: FosterHomeRepository by lazy {
        if (useSupabase) {
            SupabaseFosterHomeRepository()
        } else {
            MockFosterHomeRepository(
                actorUserId = { AuthProvider.repository.getCurrentUser()?.id },
                store = m10FosterStore
            )
        }
    }

    val fosterRequestRepository: FosterRequestRepository by lazy {
        if (useSupabase) {
            SupabaseFosterRequestRepository()
        } else {
            MockFosterRequestRepository(
                actorUserId = { AuthProvider.repository.getCurrentUser()?.id },
                store = m10FosterStore,
                resolvePet = { id ->
                    petRepository.getPetById(id) ?: m10ResolvePetFromStore(id)
                }
            )
        }
    }

    val fosterPlacementRepository: FosterPlacementRepository by lazy {
        if (useSupabase) {
            SupabaseFosterPlacementRepository()
        } else {
            MockFosterPlacementRepository(
                actorUserId = { AuthProvider.repository.getCurrentUser()?.id },
                store = m10FosterStore
            )
        }
    }

    val fosterExpenseRepository: FosterExpenseRepository by lazy {
        if (useSupabase) {
            SupabaseFosterExpenseRepository()
        } else {
            MockFosterExpenseRepository(
                actorUserId = { AuthProvider.repository.getCurrentUser()?.id },
                store = m10FosterStore
            )
        }
    }

    val fosterEvolutionRepository: FosterEvolutionRepository by lazy {
        if (useSupabase) {
            SupabaseFosterEvolutionRepository()
        } else {
            MockFosterEvolutionRepository(
                actorUserId = { AuthProvider.repository.getCurrentUser()?.id },
                store = m10FosterStore
            )
        }
    }

    val fosterHelpRepository: FosterHelpRepository by lazy {
        if (useSupabase) {
            SupabaseFosterHelpRepository()
        } else {
            MockFosterHelpRepository(
                actorUserId = { AuthProvider.repository.getCurrentUser()?.id },
                store = m10FosterStore
            )
        }
    }

    val chatRepository: ChatRepository by lazy {
        if (useSupabase) SupabaseChatRepository() else MockChatRepository()
    }

    val communityRepository: CommunityRepository by lazy {
        if (useSupabase) SupabaseCommunityRepository() else MockCommunityRepository()
    }

    val shelterRepository: ShelterRepository by lazy {
        if (useSupabase) SupabaseShelterRepository() else MockShelterRepository()
    }

    val serviceRepository: ServiceRepository by lazy {
        if (useSupabase) SupabaseServiceRepository() else MockServiceRepository()
    }

    val friendRepository: FriendRepository by lazy {
        if (useSupabase) SupabaseFriendRepository() else MockFriendRepository()
    }

    val platformRepository: PlatformRepository by lazy {
        if (useSupabase) SupabasePlatformRepository() else MockPlatformRepository()
    }

    /**
     * M06 — mocks deterministas para modo local y contratos server-side no expuestos al cliente.
     * Etapa 3 usa Supabase real para inbox/preferencias/instalaciones cuando corresponde.
     */
    private val m06Stage2ContractMocks: MockNotificationRepositories by lazy {
        MockNotificationRepositories.create(
            clock = { Instant.now() },
            retryPolicy = NotificationRetryPolicy()
        )
    }

    val notificationInboxRepository: NotificationInboxRepository by lazy {
        if (useSupabase) SupabaseNotificationInboxRepository() else m06Stage2ContractMocks.inbox
    }

    val notificationPreferenceRepository: NotificationPreferenceRepository by lazy {
        if (useSupabase) SupabaseNotificationPreferenceRepository() else m06Stage2ContractMocks.preference
    }

    val notificationInstallationRepository: NotificationInstallationRepository by lazy {
        if (useSupabase) SupabaseNotificationInstallationRepository() else m06Stage2ContractMocks.installation
    }

    val notificationDeliveryRepository: NotificationDeliveryRepository by lazy {
        if (useSupabase) ClientDeniedNotificationDeliveryRepository() else m06Stage2ContractMocks.delivery
    }

    val notificationOutboxRepository: NotificationOutboxRepository by lazy {
        if (useSupabase) ClientDeniedNotificationOutboxRepository() else m06Stage2ContractMocks.outbox
    }

    /** Acceso tipado a los mocks de etapa 2 (tests / inyección). */
    val m06Stage2NotificationMocks: MockNotificationRepositories
        get() = m06Stage2ContractMocks

    val mockNotificationInboxRepository: MockNotificationInboxRepository
        get() = m06Stage2ContractMocks.inbox

    val mockNotificationPreferenceRepository: MockNotificationPreferenceRepository
        get() = m06Stage2ContractMocks.preference

    val mockNotificationInstallationRepository: MockNotificationInstallationRepository
        get() = m06Stage2ContractMocks.installation

    val mockNotificationDeliveryRepository: MockNotificationDeliveryRepository
        get() = m06Stage2ContractMocks.delivery

    val mockNotificationOutboxRepository: MockNotificationOutboxRepository
        get() = m06Stage2ContractMocks.outbox

    /**
     * M07 Etapa 3 — mocks locales + RPCs allowlisted cuando useSupabase.
     * Writers internos siguen server-side; Android no inserta auditoría arbitraria.
     */
    private val m07Stage2ContractMocks: MockObservabilityRepositories by lazy {
        MockObservabilityRepositories.create(clock = { Instant.now() })
    }

    val m07Stage2ObservabilityMocks: MockObservabilityRepositories
        get() = m07Stage2ContractMocks

    val auditEventRepository: AuditEventRepository by lazy {
        if (useSupabase) SupabaseAuditEventRepository() else m07Stage2ContractMocks.audit
    }

    val securityEventRepository: SecurityEventRepository by lazy {
        if (useSupabase) SupabaseSecurityEventRepository() else m07Stage2ContractMocks.security
    }

    val applicationErrorRepository: ApplicationErrorRepository by lazy {
        if (useSupabase) SupabaseApplicationErrorRepository() else m07Stage2ContractMocks.errors
    }

    val performanceMetricRepository: PerformanceMetricRepository by lazy {
        m07Stage2ContractMocks.metrics
    }

    val healthCheckRepository: HealthCheckRepository by lazy {
        m07Stage2ContractMocks.health
    }

    val analyticsEventRepository: AnalyticsEventRepository by lazy {
        m07Stage2ContractMocks.analytics
    }

    val alertRepository: AlertRepository by lazy {
        m07Stage2ContractMocks.alerts
    }

    val observabilityExportRepository: ObservabilityExportRepository by lazy {
        if (useSupabase) SupabaseObservabilityExportRepository() else m07Stage2ContractMocks.exports
    }

    val eventCatalogRepository: EventCatalogRepository by lazy {
        m07Stage2ContractMocks.catalog
    }

    val correlationContextRepository: CorrelationContextRepository by lazy {
        m07Stage2ContractMocks.correlation
    }

    /**
     * M07 Etapa 4 — métricas/health/alertas/overview.
     * useSupabase=false: mock completo; useSupabase=true: RPC-only (sin write arbitrario de métricas).
     */
    val operationalObservabilityRepository: OperationalObservabilityRepository by lazy {
        if (useSupabase) SupabaseOperationalObservabilityRepository()
        else MockOperationalObservabilityRepository()
    }

    /**
     * M07 Etapa 5 — retención (RPC-only / mock). Preview ≠ execute.
     */
    val retentionRepository: RetentionRepository by lazy {
        if (useSupabase) SupabaseRetentionRepository()
        else MockRetentionRepository()
    }

    /** Kept for tests expecting explicit client-denied audit writer. */
    val clientDeniedAuditEventRepository: ClientDeniedAuditEventRepository by lazy {
        ClientDeniedAuditEventRepository()
    }

    val permissionRepository: PermissionRepository by lazy {
        if (useSupabase) SupabasePermissionRepository() else MockPermissionRepository()
    }

    /**
     * M03 Etapa 4: invitaciones Supabase cuando useSupabase; mock local en caso contrario.
     */
    private val mockOrganizationMembershipRepository by lazy {
        MockOrganizationMembershipRepository()
    }

    private val mockOrganizationInvitationRepository by lazy {
        MockOrganizationInvitationRepository(mockOrganizationMembershipRepository)
    }

    private val mockOrganizationRepository by lazy {
        MockOrganizationRepository(mockOrganizationMembershipRepository)
    }

    val organizationRepository: OrganizationRepository by lazy {
        if (useSupabase) SupabaseOrganizationRepository() else mockOrganizationRepository
    }

    val organizationMembershipRepository: OrganizationMembershipRepository by lazy {
        if (useSupabase) {
            SupabaseOrganizationMembershipRepository()
        } else {
            mockOrganizationMembershipRepository
        }
    }

    val organizationInvitationRepository: OrganizationInvitationRepository by lazy {
        if (useSupabase) {
            SupabaseOrganizationInvitationRepository(organizationMembershipRepository)
        } else {
            mockOrganizationInvitationRepository
        }
    }

    val organizationPermissionRepository: OrganizationPermissionRepository by lazy {
        if (useSupabase) {
            SupabaseOrganizationPermissionRepository(
                organizationRepository,
                organizationMembershipRepository
            )
        } else {
            MockOrganizationPermissionRepository(
                organizationRepository,
                organizationMembershipRepository
            )
        }
    }

    val platformAdministrationRepository: PlatformAdministrationRepository by lazy {
        if (useSupabase) {
            SupabasePlatformAdministrationRepository()
        } else {
            MockPlatformAdministrationRepository(permissionRepository)
        }
    }

    /**
     * M04 Etapa 3: repositorios Supabase cuando useSupabase; mocks locales en caso contrario.
     */
    val moderationRepository: ModerationRepository by lazy {
        if (useSupabase) SupabaseModerationRepository() else MockModerationRepository()
    }

    val organizationVerificationRepository: OrganizationVerificationRepository by lazy {
        if (useSupabase) {
            SupabaseOrganizationVerificationRepository()
        } else {
            MockOrganizationVerificationRepository()
        }
    }

    val supportRepository: SupportRepository by lazy {
        if (useSupabase) SupabaseSupportRepository() else MockSupportRepository()
    }

    val administrativeAuditRepository: AdministrativeAuditRepository by lazy {
        if (useSupabase) {
            SupabaseAdministrativeAuditRepository()
        } else {
            MockAdministrativeAuditRepository()
        }
    }

    /** M05 Etapa 3: RPC/Storage Supabase o mocks deterministas locales. */
    private val mockFileAssetRepository: MockFileAssetRepository by lazy { MockFileAssetRepository() }

    val fileAssetRepository: FileAssetRepository by lazy {
        if (useSupabase) SupabaseFileAssetRepository() else mockFileAssetRepository
    }

    val fileUploadRepository: FileUploadRepository by lazy {
        if (useSupabase) {
            SupabaseFileUploadRepository()
        } else {
            MockFileUploadRepository(mockFileAssetRepository)
        }
    }

    val fileDownloadRepository: FileDownloadRepository by lazy {
        if (useSupabase) {
            SupabaseFileDownloadRepository(fileAssetRepository)
        } else {
            MockFileDownloadRepository(mockFileAssetRepository)
        }
    }

    val fileAccessRepository: FileAccessRepository by lazy {
        if (useSupabase) {
            SupabaseFileAccessRepository(fileAssetRepository)
        } else {
            MockFileAccessRepository(mockFileAssetRepository)
        }
    }

    val fileRetentionRepository: FileRetentionRepository by lazy {
        if (useSupabase) {
            SupabaseFileRetentionRepository()
        } else {
            MockFileRetentionRepository(mockFileAssetRepository)
        }
    }

    val fileObjectUploader: FileObjectUploader by lazy {
        if (useSupabase) SupabaseFileObjectUploader() else MockFileObjectUploader()
    }

    val fileLocalMetadataReader: FileLocalMetadataReader by lazy {
        AndroidContentFileMetadataReader(LeoverApplication.instance.contentResolver)
    }

    val fileBytesReader: FileBytesReader by lazy {
        AndroidFileBytesReader(LeoverApplication.instance.contentResolver)
    }

    val fileUploadCoordinator: FileUploadCoordinator by lazy {
        FileUploadCoordinator(
            uploadRepository = fileUploadRepository,
            assetRepository = fileAssetRepository,
            objectUploader = fileObjectUploader,
            metadataReader = fileLocalMetadataReader,
            bytesReader = fileBytesReader
        ).also { FileSessionCleanup.register(coordinator = it) }
    }

    val fileDisplayResolver: FileDisplayResolver by lazy {
        FileDisplayResolver(
            assetRepository = fileAssetRepository,
            downloadRepository = fileDownloadRepository
        ).also { FileSessionCleanup.register(displayResolver = it) }
    }

    val storageService: ImageStorageService? by lazy {
        if (useSupabase) SupabaseStorageService() else null
    }

    val profileAvatarStorage: ProfileAvatarStorageService? by lazy {
        if (useSupabase) ProfileAvatarStorageService() else null
    }

    val organizationMediaStorage: OrganizationMediaStorageService? by lazy {
        if (useSupabase) OrganizationMediaStorageService() else null
    }
}
