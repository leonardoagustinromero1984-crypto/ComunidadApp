package com.comunidapp.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.domain.capability.CapabilityFacts
import com.comunidapp.app.domain.capability.CapabilityGate
import com.comunidapp.app.domain.capability.CapabilityNavigationGuard
import com.comunidapp.app.domain.capability.StartupNavigationPolicy
import com.comunidapp.app.domain.capability.StartupSessionLatchStore
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.context.OperationalContextProvider
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.notifications.NotificationDeepLinkRouter
import com.comunidapp.app.notifications.NotificationDeepLinkSessionResolver
import com.comunidapp.app.notifications.NotificationPendingNavigationStore
import com.comunidapp.app.ui.components.ComunidappBottomBar
import com.comunidapp.app.ui.components.SessionLoadingScreen
import com.comunidapp.app.ui.components.bottomNavItemsFor
import com.comunidapp.app.ui.screens.admin.AdminHubScreen
import com.comunidapp.app.ui.screens.admin.AdminModerationScreen
import com.comunidapp.app.ui.screens.admin.AdminMfaChallengeScreen
import com.comunidapp.app.ui.screens.admin.AdminMfaEnrollmentScreen
import com.comunidapp.app.ui.screens.admin.AdminPasswordChangeScreen
import com.comunidapp.app.ui.screens.admin.AdminStaffCreateScreen
import com.comunidapp.app.ui.screens.admin.AdminStaffDetailScreen
import com.comunidapp.app.ui.screens.admin.AdminStaffListScreen
import com.comunidapp.app.ui.screens.admin.CatalogsIndexScreen
import com.comunidapp.app.ui.screens.admin.AdministrativeAuditScreen
import com.comunidapp.app.ui.screens.admin.ObservabilityHealthScreen
import com.comunidapp.app.ui.screens.admin.ObservabilityIncidentsScreen
import com.comunidapp.app.ui.screens.admin.ObservabilityMetricsScreen
import com.comunidapp.app.ui.screens.admin.ObservabilityOverviewScreen
import com.comunidapp.app.ui.screens.admin.ObservabilityPermissionsInfoScreen
import com.comunidapp.app.ui.screens.admin.ObservabilityRetentionScreen
import com.comunidapp.app.ui.screens.admin.ObservabilityAuditListScreen
import com.comunidapp.app.ui.screens.admin.ObservabilityErrorsListScreen
import com.comunidapp.app.ui.screens.admin.ObservabilityExportsScreen
import com.comunidapp.app.ui.screens.admin.LocationCatalogAdminScreen
import com.comunidapp.app.ui.screens.admin.MasterCatalogAdminScreen
import com.comunidapp.app.ui.screens.admin.PlatformAdminScreen
import com.comunidapp.app.ui.screens.admin.SpeciesCatalogCreateScreen
import com.comunidapp.app.ui.screens.admin.SpeciesCatalogDetailScreen
import com.comunidapp.app.ui.screens.admin.SpeciesCatalogListScreen
import com.comunidapp.app.ui.screens.vitacora.AdminVitacoraImportNewScreen
import com.comunidapp.app.ui.screens.vitacora.AdminVitacoraImportQueueScreen
import com.comunidapp.app.ui.screens.vitacora.VitacoraImportScreen
import com.comunidapp.app.ui.screens.moderation.ModerationAppealDetailScreen
import com.comunidapp.app.ui.screens.moderation.ModerationAppealQueueScreen
import com.comunidapp.app.ui.screens.moderation.ModerationCaseDetailScreen
import com.comunidapp.app.ui.screens.moderation.ModerationCaseQueueScreen
import com.comunidapp.app.ui.screens.moderation.ModerationReportDetailScreen
import com.comunidapp.app.ui.screens.moderation.MyModerationAppealsScreen
import com.comunidapp.app.ui.screens.support.CreateSupportTicketScreen
import com.comunidapp.app.ui.screens.support.MySupportTicketsScreen
import com.comunidapp.app.ui.screens.support.SupportQueueScreen
import com.comunidapp.app.ui.screens.support.SupportTicketAdminDetailScreen
import com.comunidapp.app.ui.screens.support.SupportTicketDetailScreen
import com.comunidapp.app.ui.screens.verification.M16ShelterVerificationReviewScreen
import com.comunidapp.app.ui.screens.verification.OrganizationVerificationQueueScreen
import com.comunidapp.app.ui.screens.verification.OrganizationVerificationReviewScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionAgreementScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionApplicationDetailScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionApplyScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionDetailScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionSearchScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionsScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionDocumentsScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionFinalizeScreen
import com.comunidapp.app.ui.screens.foster.FosterCompleteScreen
import com.comunidapp.app.ui.screens.foster.FosterEvolutionFormScreen
import com.comunidapp.app.ui.screens.foster.FosterEvolutionScreen
import com.comunidapp.app.ui.screens.foster.FosterExpenseFormScreen
import com.comunidapp.app.ui.screens.foster.FosterExpensesScreen
import com.comunidapp.app.ui.screens.foster.FosterHelpDetailScreen
import com.comunidapp.app.ui.screens.foster.FosterHelpFormScreen
import com.comunidapp.app.ui.screens.foster.FosterHelpScreen
import com.comunidapp.app.ui.screens.foster.FosterHistoryScreen
import com.comunidapp.app.ui.screens.foster.FosterHomeDetailScreen
import com.comunidapp.app.ui.screens.foster.FosterHomeFormScreen
import com.comunidapp.app.ui.screens.foster.FosterHomesScreen
import com.comunidapp.app.ui.screens.foster.FosterPlacementDetailScreen
import com.comunidapp.app.ui.screens.foster.FosterPlacementManagementScreen
import com.comunidapp.app.ui.screens.foster.FosterPlacementsScreen
import com.comunidapp.app.ui.screens.foster.FosterNewPlacementScreen
import com.comunidapp.app.ui.screens.foster.FosterRequestDetailScreen
import com.comunidapp.app.ui.screens.foster.FosterRequestFormScreen
import com.comunidapp.app.ui.screens.foster.FosterRequestsScreen
import com.comunidapp.app.ui.screens.foster.MyFosterHomeScreen
import com.comunidapp.app.viewmodel.FosterCompleteViewModel
import com.comunidapp.app.viewmodel.FosterEvolutionFormViewModel
import com.comunidapp.app.viewmodel.FosterEvolutionListViewModel
import com.comunidapp.app.viewmodel.FosterExpenseFormViewModel
import com.comunidapp.app.viewmodel.FosterExpensesViewModel
import com.comunidapp.app.viewmodel.FosterHelpDetailViewModel
import com.comunidapp.app.viewmodel.FosterHelpFormViewModel
import com.comunidapp.app.viewmodel.FosterHelpListViewModel
import com.comunidapp.app.viewmodel.FosterHomeDetailViewModel
import com.comunidapp.app.viewmodel.FosterPlacementDetailViewModel
import com.comunidapp.app.viewmodel.FosterPlacementManagementViewModel
import com.comunidapp.app.viewmodel.FosterRequestDetailViewModel
import com.comunidapp.app.viewmodel.FosterRequestFormViewModel
import com.comunidapp.app.ui.screens.adoptions.AdoptionFollowUpCheckDetailScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionFollowUpScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionFormScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionInterviewDetailScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionInterviewsScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionProcessScreen
import com.comunidapp.app.ui.screens.adoptions.MyAdoptionApplicationsScreen
import com.comunidapp.app.ui.screens.adoptions.MyAdoptionsScreen
import com.comunidapp.app.ui.screens.adoptions.ReceivedAdoptionApplicationsScreen
import com.comunidapp.app.ui.screens.search.SearchScreen
import com.comunidapp.app.ui.screens.chat.ChatListScreen
import com.comunidapp.app.ui.screens.chat.ChatStartScreen
import com.comunidapp.app.ui.screens.chat.ChatThreadScreen
import com.comunidapp.app.ui.screens.business.MiNegocioScreen
import com.comunidapp.app.ui.screens.comunidad.ComunidadScreen
import com.comunidapp.app.ui.screens.comunidad.ServiceDetailScreen
import com.comunidapp.app.ui.screens.daycare.DaycareGuestsScreen
import com.comunidapp.app.ui.screens.daycare.DaycareReservationsScreen
import com.comunidapp.app.ui.screens.home.HomeScreen
import com.comunidapp.app.ui.screens.login.EmailVerificationScreen
import com.comunidapp.app.ui.screens.login.ForgotPasswordScreen
import com.comunidapp.app.ui.screens.login.LoginScreen
import com.comunidapp.app.ui.screens.login.RegisterScreen
import com.comunidapp.app.ui.screens.legal.PrivacyDraftScreen
import com.comunidapp.app.ui.screens.legal.TermsDraftScreen
import com.comunidapp.app.ui.screens.lostfound.LostFoundDetailScreen
import com.comunidapp.app.ui.screens.lostfound.LostFoundMapScreen
import com.comunidapp.app.ui.screens.lostfound.LostFoundScreen
import com.comunidapp.app.ui.screens.m13.M13CaseMatchesScreen
import com.comunidapp.app.ui.screens.m13.M13MatchDetailScreen
import com.comunidapp.app.ui.screens.m13.M13MetricsScreen
import com.comunidapp.app.ui.screens.m13.M13SightingCreateScreen
import com.comunidapp.app.ui.screens.m13.M13SightingDetailScreen
import com.comunidapp.app.ui.screens.m13.M13SightingListScreen
import com.comunidapp.app.ui.screens.pets.AddPetScreen
import com.comunidapp.app.ui.screens.pets.EditPetScreen
import com.comunidapp.app.ui.screens.pets.MyPetsScreen
import com.comunidapp.app.ui.screens.pets.PetAuthorizationsScreen
import com.comunidapp.app.ui.screens.m28.M28ClinicCareScreen
import com.comunidapp.app.ui.screens.m28.M28PassportProposalsScreen
import com.comunidapp.app.ui.screens.m28.M28PetGrantsScreen
import com.comunidapp.app.ui.screens.pets.PetDetailScreen
import com.comunidapp.app.ui.screens.pets.PetResponsibilitiesScreen
import com.comunidapp.app.ui.screens.pets.PetStatusHistoryScreen
import com.comunidapp.app.ui.screens.pets.PetTransferDetailScreen
import com.comunidapp.app.ui.screens.pets.PetTransfersScreen
import com.comunidapp.app.ui.screens.profile.EditProfileScreen
import com.comunidapp.app.ui.screens.profile.ConnectedPetProfileScreen
import com.comunidapp.app.ui.screens.profile.FriendRequestsScreen
import com.comunidapp.app.ui.screens.profile.NotificationPreferencesScreen
import com.comunidapp.app.ui.screens.profile.NotificationsScreen
import com.comunidapp.app.ui.screens.profile.MyPublicationsScreen
import com.comunidapp.app.ui.screens.profile.PersonalMemoriesScreen
import com.comunidapp.app.ui.screens.profile.ProfileScreen
import com.comunidapp.app.ui.screens.profile.ProfilePrivacyScreen
import com.comunidapp.app.ui.screens.profile.SearchFriendsScreen
import com.comunidapp.app.ui.screens.profile.FriendsListScreen
import com.comunidapp.app.ui.screens.profile.MiManadaScreen
import com.comunidapp.app.ui.screens.profile.SavedPostsScreen
import com.comunidapp.app.ui.screens.profile.SettingsScreen
import com.comunidapp.app.ui.screens.profile.UserPublicProfileScreen
import com.comunidapp.app.ui.screens.organization.CreateOrganizationScreen
import com.comunidapp.app.ui.screens.organization.EditOrganizationScreen
import com.comunidapp.app.ui.screens.organization.MyOrganizationsScreen
import com.comunidapp.app.ui.screens.organization.OrganizationBranchesScreen
import com.comunidapp.app.ui.screens.organization.OrganizationManageScreen
import com.comunidapp.app.ui.screens.organization.OrganizationTeamScreen
import com.comunidapp.app.ui.screens.organization.PublicOrganizationScreen
import com.comunidapp.app.ui.screens.onboarding.FirstRunOnboardingScreen
import com.comunidapp.app.ui.screens.onboarding.ProfileOnboardingScreen
import com.comunidapp.app.ui.screens.onboarding.onb02.HelpTutorialsScreen
import com.comunidapp.app.ui.screens.onboarding.onb02.Onb02HostScreen
import com.comunidapp.app.ui.screens.onboarding.onb02.UseLeoverAsScreen
import com.comunidapp.app.data.local.Onb02StoreProvider
import com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind
import com.comunidapp.app.domain.onboarding.onb02.Onb02SessionFlags
import com.comunidapp.app.domain.onboarding.onb02.OrganizationKindOption
import com.comunidapp.app.domain.onboarding.onb02.ProfileActorTaxonomy
import com.comunidapp.app.domain.onboarding.onb02.TutorialId
import kotlinx.coroutines.launch
import com.comunidapp.app.ui.screens.security.AccountAccessBlockedScreen
import com.comunidapp.app.ui.screens.security.AccountSecurityScreen
import com.comunidapp.app.ui.screens.security.LegalConsentRequiredScreen
import com.comunidapp.app.ui.screens.security.PasswordResetActiveScreen
import com.comunidapp.app.viewmodel.UserPublicProfileViewModel
import com.comunidapp.app.viewmodel.EditOrganizationViewModel
import com.comunidapp.app.viewmodel.OrganizationBranchesViewModel
import com.comunidapp.app.viewmodel.OrganizationManageViewModel
import com.comunidapp.app.viewmodel.OrganizationTeamViewModel
import com.comunidapp.app.viewmodel.PublicOrganizationViewModel
import com.comunidapp.app.viewmodel.ChatStartViewModel
import com.comunidapp.app.viewmodel.ChatThreadViewModel
import com.comunidapp.app.ui.screens.social.SocialPostDetailScreen
import com.comunidapp.app.ui.screens.publish.PublishGeneralScreen
import com.comunidapp.app.ui.screens.publish.PublishLostFoundScreen
import com.comunidapp.app.ui.screens.publish.PublishPromoScreen
import com.comunidapp.app.ui.screens.publish.PublishQuestionScreen
import com.comunidapp.app.ui.screens.publish.PublishReelScreen
import com.comunidapp.app.ui.screens.publish.PublishStoryScreen
import com.comunidapp.app.ui.screens.publish.PublishUrgentScreen
import com.comunidapp.app.ui.screens.publish.PublishDonationScreen
import com.comunidapp.app.ui.screens.publish.PublishEventScreen
import com.comunidapp.app.ui.screens.publish.PublishFosterScreen
import com.comunidapp.app.ui.screens.publish.PublishShelterScreen
import com.comunidapp.app.ui.screens.publish.PublishScreen
import com.comunidapp.app.ui.screens.shelters.MySheltersScreen
import com.comunidapp.app.ui.screens.shelters.ShelterCampaignDetailScreen
import com.comunidapp.app.ui.screens.shelters.ShelterCampaignFormScreen
import com.comunidapp.app.ui.screens.shelters.ShelterCampaignUpdateScreen
import com.comunidapp.app.ui.screens.shelters.ShelterCampaignsScreen
import com.comunidapp.app.ui.screens.shelters.ShelterDashboardScreen
import com.comunidapp.app.ui.screens.shelters.ShelterDetailScreen
import com.comunidapp.app.ui.screens.shelters.ShelterEmergenciesScreen
import com.comunidapp.app.ui.screens.shelters.ShelterEmergencyDetailScreen
import com.comunidapp.app.ui.screens.shelters.ShelterEmergencyFormScreen
import com.comunidapp.app.ui.screens.shelters.ShelterEventDetailScreen
import com.comunidapp.app.ui.screens.shelters.ShelterEventFormScreen
import com.comunidapp.app.ui.screens.shelters.ShelterEventRegistrationsScreen
import com.comunidapp.app.ui.screens.shelters.ShelterEventsScreen
import com.comunidapp.app.ui.screens.shelters.ShelterReportsScreen
import com.comunidapp.app.ui.screens.shelters.ShelterIntakeScreen
import com.comunidapp.app.ui.screens.shelters.ShelterOpsDetailScreen
import com.comunidapp.app.ui.screens.shelters.ShelterOpsFormScreen
import com.comunidapp.app.ui.screens.shelters.ShelterOpsListScreen
import com.comunidapp.app.ui.screens.shelters.ShelterOpsPetDetailScreen
import com.comunidapp.app.ui.screens.shelters.ShelterOpsPetsScreen
import com.comunidapp.app.ui.screens.shelters.ShelterOpsVolunteersScreen
import com.comunidapp.app.ui.screens.shelters.ShelterPublicCampaignsScreen
import com.comunidapp.app.ui.screens.shelters.ShelterPublicEmergenciesScreen
import com.comunidapp.app.ui.screens.shelters.ShelterPublicEventsScreen
import com.comunidapp.app.ui.screens.shelters.ShelterPublicSupplyRequestsScreen
import com.comunidapp.app.ui.screens.shelters.ShelterSupplyContributeScreen
import com.comunidapp.app.ui.screens.shelters.ShelterSupplyContributionsScreen
import com.comunidapp.app.ui.screens.shelters.ShelterSupplyRequestDetailScreen
import com.comunidapp.app.ui.screens.shelters.ShelterSupplyRequestFormScreen
import com.comunidapp.app.ui.screens.shelters.ShelterSupplyRequestsScreen
import com.comunidapp.app.ui.screens.shelters.ShelterVolunteerInviteScreen
import com.comunidapp.app.ui.screens.veterinary.ManagedVeterinaryClinicsScreen
import com.comunidapp.app.ui.screens.veterinary.VeterinaryClinicDetailScreen
import com.comunidapp.app.ui.screens.veterinary.VeterinaryClinicDraftScreen
import com.comunidapp.app.ui.screens.veterinary.VeterinaryClinicHoursScreen
import com.comunidapp.app.ui.screens.veterinary.VeterinaryClinicManageHubScreen
import com.comunidapp.app.ui.screens.veterinary.VeterinaryClinicProfessionalsScreen
import com.comunidapp.app.ui.screens.veterinary.VeterinaryClinicServicesScreen
import com.comunidapp.app.ui.screens.veterinary.VeterinaryDirectoryScreen
import com.comunidapp.app.ui.screens.veterinary.VeterinaryBookAppointmentScreen
import com.comunidapp.app.ui.screens.veterinary.MyVeterinaryAppointmentsScreen
import com.comunidapp.app.ui.screens.veterinary.VeterinaryAppointmentDetailScreen
import com.comunidapp.app.ui.screens.veterinary.VeterinaryManagedAgendaScreen
import com.comunidapp.app.ui.screens.veterinary.VeterinaryScheduleSettingsScreen
import com.comunidapp.app.ui.screens.veterinary.VeterinaryAvailabilityRulesScreen
import com.comunidapp.app.ui.screens.veterinary.VeterinaryAppointmentManagementScreen
import com.comunidapp.app.viewmodel.ShelterCampaignDetailViewModel
import com.comunidapp.app.viewmodel.ShelterCampaignFormViewModel
import com.comunidapp.app.viewmodel.ShelterCampaignUpdateFormViewModel
import com.comunidapp.app.viewmodel.ShelterCampaignsViewModel
import com.comunidapp.app.viewmodel.ShelterDashboardViewModel
import com.comunidapp.app.viewmodel.ShelterEmergenciesViewModel
import com.comunidapp.app.viewmodel.ShelterEmergencyDetailViewModel
import com.comunidapp.app.viewmodel.ShelterEmergencyFormViewModel
import com.comunidapp.app.viewmodel.ShelterEventDetailViewModel
import com.comunidapp.app.viewmodel.ShelterEventFormViewModel
import com.comunidapp.app.viewmodel.ShelterEventRegistrationsViewModel
import com.comunidapp.app.viewmodel.ShelterEventsViewModel
import com.comunidapp.app.viewmodel.ShelterFormViewModel
import com.comunidapp.app.viewmodel.ShelterPublicEmergenciesViewModel
import com.comunidapp.app.viewmodel.ShelterPublicEventsViewModel
import com.comunidapp.app.viewmodel.ShelterReportsViewModel
import com.comunidapp.app.viewmodel.ShelterIntakeViewModel
import com.comunidapp.app.viewmodel.ShelterOpsDetailViewModel
import com.comunidapp.app.viewmodel.ShelterPetDetailViewModel
import com.comunidapp.app.viewmodel.ShelterPetsViewModel
import com.comunidapp.app.viewmodel.ShelterSupplyContributeViewModel
import com.comunidapp.app.viewmodel.ShelterSupplyContributionsViewModel
import com.comunidapp.app.viewmodel.ShelterSupplyRequestDetailViewModel
import com.comunidapp.app.viewmodel.ShelterSupplyRequestFormViewModel
import com.comunidapp.app.viewmodel.ShelterSupplyRequestsViewModel
import com.comunidapp.app.viewmodel.ShelterVolunteerInviteViewModel
import com.comunidapp.app.viewmodel.ShelterVolunteersViewModel
import com.comunidapp.app.ui.screens.sumate.SumateScreen
import com.comunidapp.app.viewmodel.PetAuthorizationsViewModel
import com.comunidapp.app.viewmodel.PetFormViewModel
import com.comunidapp.app.viewmodel.PetResponsibilitiesViewModel
import com.comunidapp.app.viewmodel.PetStatusHistoryViewModel
import com.comunidapp.app.viewmodel.PetTransfersViewModel
import com.comunidapp.app.viewmodel.SessionNavDisplay
import com.comunidapp.app.viewmodel.SessionState
import com.comunidapp.app.viewmodel.SessionViewModel

@Composable
fun ComunidappNavGraph(
    sessionViewModel: SessionViewModel = viewModel()
) {
    val sessionState by sessionViewModel.sessionState.collectAsState()
    val sessionUserId = sessionViewModel.currentUser.collectAsState().value?.id
    val activeContext by OperationalContextProvider.active.collectAsState()
    var lastReadyWasLoggedIn by remember { mutableStateOf(false) }
    var rememberedUserId by remember { mutableStateOf<String?>(null) }
    lastReadyWasLoggedIn = SessionNavDisplay.rememberLoggedIn(
        sessionState,
        lastReadyWasLoggedIn,
        sessionUserId,
        rememberedUserId
    )
    if (sessionState == SessionState.LoggedIn) {
        rememberedUserId = sessionUserId
    } else if (sessionState == SessionState.LoggedOut) {
        rememberedUserId = null
    }
    val displaySession = SessionNavDisplay.resolve(sessionState, lastReadyWasLoggedIn)

    when (displaySession) {
        SessionState.Loading -> SessionLoadingScreen()
        SessionState.LegalConsentRequired -> {
            val consentNav = rememberNavController()
            NavHost(navController = consentNav, startDestination = NavRoutes.LEGAL_CONSENT_REQUIRED) {
                composable(NavRoutes.LEGAL_CONSENT_REQUIRED) {
                    LegalConsentRequiredScreen(
                        sessionViewModel = sessionViewModel,
                        onNavigateToTerms = { consentNav.navigate(NavRoutes.LEGAL_TERMS) },
                        onNavigateToPrivacy = { consentNav.navigate(NavRoutes.LEGAL_PRIVACY) }
                    )
                }
                composable(NavRoutes.LEGAL_TERMS) {
                    TermsDraftScreen(onNavigateBack = { consentNav.popBackStack() })
                }
                composable(NavRoutes.LEGAL_PRIVACY) {
                    PrivacyDraftScreen(onNavigateBack = { consentNav.popBackStack() })
                }
            }
        }
        SessionState.PasswordResetActive -> {
            PasswordResetActiveScreen(
                onSuccess = { /* session becomes LoggedOut → login */ },
                onInvalidLink = { sessionViewModel.clearPasswordResetActive() },
                sessionViewModel = sessionViewModel
            )
        }
        SessionState.ProfileSetupRequired -> {
            val setupUserId = sessionViewModel.currentUser.collectAsState().value?.id.orEmpty()
            key(setupUserId) {
                ProfileOnboardingScreen(
                    onComplete = { sessionViewModel.onProfileSetupCompleted() },
                    sessionUserId = setupUserId
                )
            }
        }
        SessionState.AccountAccessBlocked -> {
            val blockedStatus by sessionViewModel.blockedAccountStatus.collectAsState()
            AccountAccessBlockedScreen(
                accountStatus = blockedStatus,
                sessionViewModel = sessionViewModel
            )
        }
        SessionState.LoggedOut, SessionState.LoggedIn -> {
            key(displaySession to (sessionUserId ?: "anon")) {
                RootNavHost(
                    isLoggedIn = displaySession == SessionState.LoggedIn,
                    context = activeContext,
                    onLogout = { sessionViewModel.logout() }
                )
            }
        }
        SessionState.AdminPasswordChangeRequired -> {
            AdminPasswordChangeScreen(sessionViewModel = sessionViewModel)
        }
        SessionState.AdminMfaEnrollmentRequired -> {
            AdminMfaEnrollmentScreen(sessionViewModel = sessionViewModel)
        }
        SessionState.AdminMfaChallengeRequired -> {
            AdminMfaChallengeScreen(sessionViewModel = sessionViewModel)
        }
        SessionState.AdminSession -> {
            key(sessionUserId ?: "admin") {
                AdminSessionNavHost(onLogout = { sessionViewModel.logout() })
            }
        }
    }
}

@Composable
private fun RootNavHost(
    isLoggedIn: Boolean,
    context: OperationalContext,
    onLogout: () -> Unit
) {
    val rootNavController = rememberNavController()
    val startDestination = if (isLoggedIn) NavRoutes.MAIN else NavRoutes.LOGIN

    NavHost(
        navController = rootNavController,
        startDestination = startDestination,
        modifier = Modifier.imePadding()
    ) {
        composable(NavRoutes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    rootNavController.navigate(NavRoutes.MAIN) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    rootNavController.navigate(NavRoutes.REGISTER)
                },
                onNavigateToForgotPassword = {
                    rootNavController.navigate(NavRoutes.FORGOT_PASSWORD)
                },
                onNavigateToEmailVerification = { email ->
                    rootNavController.navigate(NavRoutes.emailVerification(email))
                }
            )
        }
        composable(NavRoutes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = { email ->
                    rootNavController.navigate(NavRoutes.emailVerification(email)) {
                        popUpTo(NavRoutes.REGISTER) { inclusive = true }
                    }
                },
                onNavigateBack = { rootNavController.popBackStack() },
                onNavigateToTerms = { rootNavController.navigate(NavRoutes.LEGAL_TERMS) },
                onNavigateToPrivacy = { rootNavController.navigate(NavRoutes.LEGAL_PRIVACY) }
            )
        }
        composable(NavRoutes.LEGAL_TERMS) {
            TermsDraftScreen(onNavigateBack = { rootNavController.popBackStack() })
        }
        composable(NavRoutes.LEGAL_PRIVACY) {
            PrivacyDraftScreen(onNavigateBack = { rootNavController.popBackStack() })
        }
        composable(NavRoutes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                onNavigateBack = { rootNavController.popBackStack() },
                onResetSuccess = {
                    rootNavController.popBackStack()
                }
            )
        }
        composable(
            route = NavRoutes.EMAIL_VERIFICATION,
            arguments = listOf(navArgument(NavRoutes.ARG_EMAIL) { type = NavType.StringType })
        ) { backStackEntry ->
            val email = backStackEntry.arguments?.getString(NavRoutes.ARG_EMAIL) ?: ""
            EmailVerificationScreen(
                email = email,
                onNavigateBack = { rootNavController.popBackStack() },
                onVerified = {
                    if (!com.comunidapp.app.data.repository.AuthProvider.isRemoteBackendEnabled) {
                        rootNavController.navigate(NavRoutes.LOGIN) {
                            popUpTo(NavRoutes.LOGIN) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable(NavRoutes.MAIN) {
            MainScreen(context = context, onLogout = onLogout)
        }
    }
}

@Composable
private fun AdminSessionNavHost(onLogout: () -> Unit) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = NavRoutes.ADMIN_HUB,
        modifier = Modifier.imePadding()
    ) {
        composable(NavRoutes.ADMIN_HUB) {
            AdminHubScreen(
                onNavigateBack = onLogout,
                onNavigateToUsers = { navController.navigate(NavRoutes.PLATFORM_ADMIN) },
                onNavigateToModeration = { navController.navigate(NavRoutes.ADMIN_MODERATION) },
                onNavigateToStaff = { navController.navigate(NavRoutes.ADMIN_STAFF) },
                onNavigateToCatalogs = { navController.navigate(NavRoutes.ADMIN_CATALOGS) },
                onNavigateToSupport = { navController.navigate(NavRoutes.SUPPORT_ADMIN_QUEUE) },
                exclusiveAdminSession = true,
                onLogout = onLogout
            )
        }
        composable(NavRoutes.PLATFORM_ADMIN) {
            PlatformAdminScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.ADMIN_MODERATION) {
            AdminModerationScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.SUPPORT_ADMIN_QUEUE) {
            SupportQueueScreen(
                onNavigateBack = { navController.popBackStack() },
                onTicketClick = { id -> navController.navigate(NavRoutes.supportAdminTicket(id)) }
            )
        }
        composable(
            route = NavRoutes.SUPPORT_ADMIN_TICKET,
            arguments = listOf(navArgument(NavRoutes.ARG_TICKET_ID) { type = NavType.StringType })
        ) { entry ->
            val ticketId = java.net.URLDecoder.decode(
                entry.arguments?.getString(NavRoutes.ARG_TICKET_ID).orEmpty(),
                Charsets.UTF_8.name()
            )
            SupportTicketAdminDetailScreen(
                ticketId = ticketId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        adminStaffAndCatalogRoutes(navController)
    }
}

private fun NavGraphBuilder.adminStaffAndCatalogRoutes(navController: NavHostController) {
    composable(NavRoutes.ADMIN_STAFF) {
        AdminStaffListScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToCreate = { navController.navigate(NavRoutes.ADMIN_STAFF_CREATE) },
            onNavigateToDetail = { navController.navigate(NavRoutes.adminStaffDetail(it)) }
        )
    }
    composable(NavRoutes.ADMIN_STAFF_CREATE) {
        AdminStaffCreateScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(
        route = NavRoutes.ADMIN_STAFF_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_STAFF_USER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_STAFF_USER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        AdminStaffDetailScreen(
            userId = id,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.ADMIN_CATALOGS) {
        CatalogsIndexScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenCatalog = { key ->
                if (key == "species") navController.navigate(NavRoutes.ADMIN_SPECIES)
                else navController.navigate(NavRoutes.adminCatalog(key))
            }
        )
    }
    composable(NavRoutes.ADMIN_SPECIES) {
        SpeciesCatalogListScreen(
            onNavigateBack = { navController.popBackStack() },
            onCreate = { navController.navigate(NavRoutes.ADMIN_SPECIES_CREATE) },
            onOpenSpecies = { navController.navigate(NavRoutes.adminSpeciesDetail(it)) }
        )
    }
    composable(NavRoutes.ADMIN_SPECIES_CREATE) {
        SpeciesCatalogCreateScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(
        route = NavRoutes.ADMIN_SPECIES_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_SPECIES_CODE) { type = NavType.StringType })
    ) { entry ->
        val code = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SPECIES_CODE).orEmpty(),
            Charsets.UTF_8.name()
        )
        SpeciesCatalogDetailScreen(
            speciesCode = code,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.ADMIN_CATALOG,
        arguments = listOf(navArgument(NavRoutes.ARG_CATALOG_KEY) { type = NavType.StringType })
    ) { entry ->
        val key = entry.arguments?.getString(NavRoutes.ARG_CATALOG_KEY).orEmpty()
        MasterCatalogAdminScreen(
            onNavigateBack = { navController.popBackStack() },
            allowGeography = false,
            initialTab = com.comunidapp.app.viewmodel.MasterCatalogTab.fromKey(key),
            lockTab = true
        )
    }
}

private suspend fun resolveOnboardingUserId(): String? {
    repeat(8) {
        val id = com.comunidapp.app.domain.onboarding.onb02.OnboardingEntryIdentity.resolve(
            com.comunidapp.app.domain.user.SessionResolvedPerson.current()?.id,
            AuthProvider.repository.getCurrentUser()?.id
        )
        if (!id.isNullOrBlank()) return id
        delay(150)
    }
    return null
}

private fun publishOnboardingPhase(userId: String?, entryKind: com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind?) {
    com.comunidapp.app.domain.onboarding.onb02.InitialOnboardingGate.apply(
        com.comunidapp.app.domain.onboarding.onb02.InitialOnboardingGate.phaseAfterEntry(userId, entryKind)
    )
}

/**
 * Home stays under any restored tab. The resolving route is removed so it
 * cannot remain as a blank root.
 */
private fun applyStartupBackStack(navController: NavHostController, backStack: List<String>) {
    val root = backStack.firstOrNull() ?: return
    navController.navigate(root) {
        popUpTo(NavRoutes.STARTUP_RESOLVING) { inclusive = true }
        launchSingleTop = true
    }
    backStack.drop(1).forEach { route ->
        navController.navigate(route) {
            popUpTo(root) { inclusive = false }
            launchSingleTop = true
        }
    }
}

@Composable
private fun MainScreen(context: OperationalContext, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val bottomNavRoutes = bottomNavItemsFor(context).map { it.route }
    val showBottomBar = currentRoute in bottomNavRoutes ||
        StartupNavigationPolicy.keepsTopLevelNavigation(currentRoute)

    LaunchedEffect(currentRoute) {
        if (currentRoute == NavRoutes.STARTUP_RESOLVING) return@LaunchedEffect
        com.comunidapp.app.domain.navigation.AppNavRestoreStore.write(currentRoute, loggedIn = true)
    }

    LaunchedEffect(currentRoute, context) {
        if (currentRoute == null || currentRoute == NavRoutes.STARTUP_RESOLVING) return@LaunchedEffect
        if (currentRoute.startsWith("onb02")) return@LaunchedEffect
        val facts = CapabilityFacts.forActiveContext(context)
        if (!CapabilityNavigationGuard.allows(currentRoute, facts)) {
            if (!navController.popBackStack()) {
                navController.navigate(NavRoutes.HOME) {
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        com.comunidapp.app.domain.onboarding.onb02.InitialOnboardingGate.markResolving()
        val userId = resolveOnboardingUserId()
        val latch = StartupSessionLatchStore.current
        if (!userId.isNullOrBlank()) {
            val latched = latch.peek(userId)
            if (latched != null) {
                applyStartupBackStack(navController, latched)
                val phase = if (latched.firstOrNull()?.startsWith("onb02") == true) {
                    com.comunidapp.app.domain.onboarding.onb02.InitialOnboardingPhase.TUTORIAL_REQUIRED
                } else {
                    com.comunidapp.app.domain.onboarding.onb02.InitialOnboardingPhase.READY
                }
                com.comunidapp.app.domain.onboarding.onb02.InitialOnboardingGate.apply(phase)
                return@LaunchedEffect
            }
        }
        var remoteTutorialFlowCompleted = false
        if (!userId.isNullOrBlank()) {
            remoteTutorialFlowCompleted = runCatching {
                com.comunidapp.app.data.repository.CanonicalTutorialProgressRepository.hydrate(
                    com.comunidapp.app.data.local.Onb02StoreProvider.instance,
                    userId
                ).getOrDefault(false)
            }.getOrDefault(false)
        }
        val onb02Kind = if (!userId.isNullOrBlank()) {
            val personComplete = runCatching {
                DataProvider.userRepository.getUser(userId)
            }.getOrNull()?.let { com.comunidapp.app.domain.user.OnboardingCompleteness.isComplete(it) }
                ?: false
            Onb02StoreProvider.decideEntry(
                userId = userId,
                justCompletedProfileSetup = Onb02SessionFlags.consumeJustCompletedProfileSetup(),
                personOnboardingComplete = personComplete,
                remoteTutorialFlowCompleted = remoteTutorialFlowCompleted
            )
        } else {
            null
        }
        publishOnboardingPhase(userId, onb02Kind)
        if (userId.isNullOrBlank()) return@LaunchedEffect
        val plan = StartupNavigationPolicy.resolveSession(
            userId = userId,
            onboardingKind = onb02Kind,
            facts = CapabilityFacts.forActiveContext(context),
            latch = latch,
            readRestore = { com.comunidapp.app.domain.navigation.AppNavRestoreStore.read() }
        )
        if (!plan.apply || plan.backStack.isEmpty()) return@LaunchedEffect
        if (!plan.usedLatchedDecision) {
            latch.latch(userId, plan.backStack)
        }
        applyStartupBackStack(navController, plan.backStack)
    }

    LaunchedEffect(Unit) {
        com.comunidapp.app.domain.onboarding.onb02.InitialOnboardingGate.awaitReady()
        LeoVerDeepLinkStore.consume()?.let { route ->
            navController.navigate(route) { launchSingleTop = true }
        }
    }

    LaunchedEffect(Unit) {
        com.comunidapp.app.domain.onboarding.onb02.InitialOnboardingGate.awaitReady()
        val pending = NotificationPendingNavigationStore.consume() ?: return@LaunchedEffect
        val userId = AuthProvider.repository.getCurrentUser()?.id
        var permissionLookupFailed = false
        val platformPermissions = if (userId != null) {
            runCatching {
                DataProvider.permissionRepository.getAuthorizationContext(userId).permissions
            }.getOrElse {
                permissionLookupFailed = true
                emptySet()
            }
        } else {
            emptySet()
        }

        // Nunca usar organizationId del payload como membresía probada.
        var provenOrgId: String? = null
        var orgPermissionCodes: Set<String> = emptySet()
        val claimedOrgId = pending.deepLink.organizationId
        if (userId != null && !claimedOrgId.isNullOrBlank()) {
            val orgId = OrganizationId(claimedOrgId)
            val membership = runCatching {
                DataProvider.organizationMembershipRepository.getActiveMembership(orgId, userId)
            }.getOrNull()
            if (membership != null) {
                provenOrgId = claimedOrgId
                orgPermissionCodes = runCatching {
                    DataProvider.organizationPermissionRepository
                        .getAuthorizationContext(
                            organizationId = orgId,
                            userId = userId,
                            accountStatus = com.comunidapp.app.domain.user.AccountStatus.ACTIVE
                        )
                        .permissions
                        .map { it.code }
                        .toSet()
                }.getOrDefault(emptySet())
            }
        }

        val context = NotificationDeepLinkSessionResolver.buildOpenContext(
            authenticatedUserId = userId,
            platformPermissions = platformPermissions,
            provenOrganizationId = provenOrgId,
            organizationPermissionCodes = orgPermissionCodes,
            link = pending.deepLink,
            permissionLookupFailed = permissionLookupFailed
        )
        val resolved = NotificationDeepLinkRouter.resolve(pending.deepLink, context)
        navController.navigate(resolved.navRoute) {
            launchSingleTop = true
        }
    }

    val overlayRoutes = setOf(
        NavRoutes.PUBLISH_FROM_PROFILE,
        NavRoutes.PUBLISH_GENERAL,
        NavRoutes.PUBLISH_REEL,
        NavRoutes.PUBLISH_STORY
    )
    val hideBottomBarRoutes = setOf(
        NavRoutes.STARTUP_RESOLVING,
        NavRoutes.ONB02,
        NavRoutes.ONB02_REOPEN,
        NavRoutes.USE_LEOVER_AS,
        NavRoutes.HELP_TUTORIALS,
        NavRoutes.FIRST_RUN_ONBOARDING,
        NavRoutes.SETTINGS,
        NavRoutes.ACCOUNT_SECURITY
    )
    val onb02Open = hideBottomBarRoutes.any { pattern ->
        currentRoute == pattern || currentRoute?.startsWith("onb02") == true ||
            currentRoute == NavRoutes.USE_LEOVER_AS ||
            currentRoute == NavRoutes.HELP_TUTORIALS ||
            currentRoute == NavRoutes.SETTINGS ||
            currentRoute == NavRoutes.ACCOUNT_SECURITY ||
            currentRoute?.startsWith("first_run_onboarding") == true
    }
    val showBar = (showBottomBar || currentRoute in overlayRoutes) && !onb02Open

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                ComunidappBottomBar(
                    navController = navController,
                    context = context
                )
            }
        }
    ) { innerPadding ->
        Column(Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
            com.comunidapp.app.ui.components.leo.ReelPublishStatusBanner()
            Box(Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = NavRoutes.STARTUP_RESOLVING,
                    modifier = Modifier
                ) {
                    mainAppRoutes(navController, context, onLogout)
                }
            }
        }
    }
}

/** Route table extracted from MainScreen to shrink Compose IR of the host composable. */
private fun NavGraphBuilder.mainAppRoutes(
    navController: NavHostController,
    context: OperationalContext,
    onLogout: () -> Unit
) {
    composable(
        route = NavRoutes.FIRST_RUN_ONBOARDING,
        arguments = listOf(
            navArgument(NavRoutes.ARG_ONBOARDING_RESTART) {
                type = NavType.BoolType
                defaultValue = false
            }
        )
    ) { entry ->
        val restart = entry.arguments?.getBoolean(NavRoutes.ARG_ONBOARDING_RESTART) == true
        FirstRunOnboardingScreen(
            forceVisualRestart = restart,
            onExit = { navController.popBackStack() },
            onNavigateToRoute = { route ->
                navController.navigate(route) {
                    popUpTo(NavRoutes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onOpenPrivacy = { navController.navigate(NavRoutes.LEGAL_PRIVACY) }
        )
    }
    composable(
        route = NavRoutes.ONB02,
        arguments = listOf(
            navArgument(NavRoutes.ARG_ONB02_KIND) {
                type = NavType.StringType
            }
        )
    ) { entry ->
        val kind = runCatching {
            Onb02FlowKind.valueOf(entry.arguments?.getString(NavRoutes.ARG_ONB02_KIND).orEmpty())
        }.getOrDefault(Onb02FlowKind.FULL_ONBOARDING)
        Onb02HostScreen(
            kind = kind,
            onFinished = { setupRoute ->
                com.comunidapp.app.domain.onboarding.onb02.InitialOnboardingGate.markReady()
                val target = setupRoute?.takeIf { it.isNotBlank() } ?: NavRoutes.HOME
                if (target == NavRoutes.HOME || target == NavRoutes.USE_LEOVER_AS) {
                    navController.navigate(target) {
                        popUpTo(NavRoutes.ONB02) { inclusive = true }
                        launchSingleTop = true
                    }
                    StartupSessionLatchStore.current.replace(listOf(target))
                } else {
                    navController.navigate(NavRoutes.HOME) {
                        popUpTo(NavRoutes.ONB02) { inclusive = true }
                        launchSingleTop = true
                    }
                    navController.navigate(target) { launchSingleTop = true }
                    StartupSessionLatchStore.current.replace(listOf(NavRoutes.HOME, target))
                }
            }
        )
    }
    composable(
        route = NavRoutes.ONB02_REOPEN,
        arguments = listOf(
            navArgument(NavRoutes.ARG_TUTORIAL_ID) {
                type = NavType.StringType
            }
        )
    ) { entry ->
        val tutorialId = TutorialId.fromKey(
            entry.arguments?.getString(NavRoutes.ARG_TUTORIAL_ID).orEmpty()
        )
        Onb02HostScreen(
            kind = Onb02FlowKind.REOPEN_FROM_HELP,
            reopenId = tutorialId,
            onFinished = { setupRoute ->
                if (!setupRoute.isNullOrBlank()) {
                    navController.navigate(setupRoute) {
                        popUpTo(NavRoutes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                } else {
                    navController.popBackStack()
                }
            }
        )
    }
    composable(NavRoutes.USE_LEOVER_AS) {
        UseLeoverAsScreen(
            onAddFunction = {
                navController.navigate(NavRoutes.onb02(Onb02FlowKind.ADD_FUNCTION_LATER.name)) {
                    launchSingleTop = true
                }
            },
            onNavigateBack = {
                if (!navController.popBackStack()) {
                    navController.navigate(NavRoutes.HOME) { launchSingleTop = true }
                }
            },
            onSelected = {
                navController.navigate(NavRoutes.HOME) {
                    popUpTo(NavRoutes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
                if (navController.currentDestination?.route != NavRoutes.HOME) {
                    navController.navigate(NavRoutes.HOME) {
                        popUpTo(NavRoutes.USE_LEOVER_AS) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        )
    }
    composable(NavRoutes.HELP_TUTORIALS) {
        HelpTutorialsScreen(
            onOpenTutorial = { id ->
                navController.navigate(NavRoutes.onb02Reopen(id.key)) {
                    launchSingleTop = true
                }
            },
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.STARTUP_RESOLVING) {
        com.comunidapp.app.ui.screens.startup.StartupResolvingScreen()
    }
    composable(NavRoutes.HOME) {
        HomeScreen(
            onAuthorClick = { userId ->
                navController.navigate(NavRoutes.userProfile(userId))
            },
            onNavigateToSearch = { navController.navigate(NavRoutes.SEARCH) },
            onNavigateToNotifications = { navController.navigate(NavRoutes.NOTIFICATIONS) },
            onNavigateToMessages = { navController.navigate(NavRoutes.CHAT) },
            onNavigateToPublish = { navController.navigate(NavRoutes.PUBLISH) },
            onNavigateToCreateStory = {
                navController.navigate(NavRoutes.PUBLISH_STORY) {
                    launchSingleTop = true
                }
            },
            onNavigateToSumate = { navController.navigate(NavRoutes.SUMATE) },
            onNavigateToLostFound = { navController.navigate(NavRoutes.PUBLISH_LOST_FOUND) },
            onNavigateToFound = { navController.navigate(NavRoutes.PUBLISH_FOUND_PET) },
            onNavigateToComunidad = { navController.navigate(NavRoutes.COMUNIDAD) },
            onNavigateToMyPets = { navController.navigate(NavRoutes.MY_PETS) },
            onNavigateToPetDetail = { id -> navController.navigate(NavRoutes.petDetail(id)) },
            onNavigateToAddPet = { navController.navigate(NavRoutes.ADD_PET) },
            onOpenStoryViewer = { authorId ->
                navController.navigate(NavRoutes.storyViewer(authorId))
            },
            onPostClick = { postId ->
                navController.navigate(NavRoutes.postDetail(postId)) {
                    launchSingleTop = true
                }
            },
            onOpenClipViewer = { postId ->
                navController.navigate(NavRoutes.clipViewer(postId)) {
                    launchSingleTop = true
                }
            }
        )
    }
    composable(
        route = NavRoutes.POST_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_POST_ID) { type = NavType.StringType })
    ) { entry ->
        val postId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_POST_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        SocialPostDetailScreen(
            postId = postId,
            onNavigateBack = { navController.popBackStack() },
            onAuthorClick = { userId -> navController.navigate(NavRoutes.userProfile(userId)) }
        )
    }
    composable(NavRoutes.SUMATE) {
        SumateScreen(
            onAdoptionClick = { id ->
                navController.navigate(NavRoutes.adoptionDetail(id))
            },
            onShelterClick = { id ->
                navController.navigate(NavRoutes.shelterDetail(id))
            },
            onNavigateToMap = { navController.navigate(NavRoutes.LOST_FOUND_MAP) },
            onMyApplications = {
                navController.navigate(NavRoutes.MY_ADOPTION_APPLICATIONS)
            },
            onReceivedApplications = {
                navController.navigate(NavRoutes.RECEIVED_ADOPTION_APPLICATIONS)
            },
            onFosterHomes = { navController.navigate(NavRoutes.FOSTER_HOMES) },
            onShelterOps = { navController.navigate(NavRoutes.SHELTERS) },
            onVeterinaryDirectory = { navController.navigate(NavRoutes.VETERINARY_DIRECTORY) },
            onM16Shelters = { navController.navigate(NavRoutes.M16_SHELTERS) },
            onM17Campaigns = { navController.navigate(NavRoutes.M17_HUB) },
            onM18Events = { navController.navigate(NavRoutes.M18_EVENTS) },
            onOpenAdoptions = { navController.navigate(NavRoutes.ADOPTIONS) },
            onOpenLostFound = { navController.navigate(NavRoutes.LOST_FOUND) },
            onNavigateToPublish = { navController.navigate(NavRoutes.PUBLISH) },
            onCreateAdoption = { navController.navigate(NavRoutes.ADOPTION_FORM) },
            onCreateLost = { navController.navigate(NavRoutes.PUBLISH_LOST_FOUND) },
            onCreateFound = { navController.navigate(NavRoutes.PUBLISH_FOUND_PET) },
            onCreateFoster = {
                val facts = CapabilityFacts.forActiveContext(context)
                if (CapabilityGate.canOfferFosterHome(facts)) {
                    navController.navigate(NavRoutes.FOSTER_HOME_FORM)
                }
            },
            onCreateEvent = { navController.navigate(NavRoutes.PUBLISH_EVENT) },
            onOpenFosterRequests = {
                val facts = CapabilityFacts.forActiveContext(context)
                if (CapabilityGate.canBrowseFosterRequests(facts)) {
                    navController.navigate(NavRoutes.FOSTER_OPEN_REQUESTS)
                }
            },
            context = context
        )
    }
    composable(NavRoutes.PUBLISH) {
        PublishScreen(
            context = context,
            showBackButton = false,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToGeneral = { navController.navigate(NavRoutes.PUBLISH_GENERAL) },
            onNavigateToReel = { navController.navigate(NavRoutes.PUBLISH_REEL) },
            onNavigateToStory = { navController.navigate(NavRoutes.PUBLISH_STORY) },
            onNavigateToQuestion = { navController.navigate(NavRoutes.PUBLISH_QUESTION) },
            onNavigateToPromo = { navController.navigate(NavRoutes.PUBLISH_PROMO) },
            onNavigateToAdoption = { navController.navigate(NavRoutes.ADOPTION_FORM) },
            onNavigateToLostFound = { navController.navigate(NavRoutes.PUBLISH_LOST_FOUND) },
            onNavigateToFound = { navController.navigate(NavRoutes.PUBLISH_FOUND_PET) },
            onNavigateToUrgent = { navController.navigate(NavRoutes.PUBLISH_URGENT) },
            onNavigateToFoster = { navController.navigate(NavRoutes.PUBLISH_FOSTER) },
            onNavigateToEvent = { navController.navigate(NavRoutes.PUBLISH_EVENT) },
            onNavigateToDonation = { navController.navigate(NavRoutes.PUBLISH_DONATION) },
            onNavigateToShelter = { navController.navigate(NavRoutes.PUBLISH_SHELTER) },
            onNavigateToProviderFicha = { navController.navigate(NavRoutes.MY_BUSINESS) },
            onNavigateToCampaign = { navController.navigate(NavRoutes.M17_CAMPAIGNS_CREATE) }
        )
    }
    composable(NavRoutes.PUBLISH_FROM_PROFILE) {
        PublishScreen(
            context = context,
            showBackButton = true,
            onNavigateBack = {
                if (!navController.popBackStack(NavRoutes.PROFILE, inclusive = false)) {
                    navController.popBackStack()
                }
            },
            onNavigateToGeneral = { navController.navigate(NavRoutes.PUBLISH_GENERAL) },
            onNavigateToReel = { navController.navigate(NavRoutes.PUBLISH_REEL) },
            onNavigateToStory = { navController.navigate(NavRoutes.PUBLISH_STORY) },
            onNavigateToQuestion = { navController.navigate(NavRoutes.PUBLISH_QUESTION) },
            onNavigateToPromo = { navController.navigate(NavRoutes.PUBLISH_PROMO) },
            onNavigateToAdoption = { navController.navigate(NavRoutes.ADOPTION_FORM) },
            onNavigateToLostFound = { navController.navigate(NavRoutes.PUBLISH_LOST_FOUND) },
            onNavigateToFound = { navController.navigate(NavRoutes.PUBLISH_FOUND_PET) },
            onNavigateToUrgent = { navController.navigate(NavRoutes.PUBLISH_URGENT) },
            onNavigateToFoster = { navController.navigate(NavRoutes.PUBLISH_FOSTER) },
            onNavigateToEvent = { navController.navigate(NavRoutes.PUBLISH_EVENT) },
            onNavigateToDonation = { navController.navigate(NavRoutes.PUBLISH_DONATION) },
            onNavigateToShelter = { navController.navigate(NavRoutes.PUBLISH_SHELTER) },
            onNavigateToProviderFicha = { navController.navigate(NavRoutes.MY_BUSINESS) },
            onNavigateToCampaign = { navController.navigate(NavRoutes.M17_CAMPAIGNS_CREATE) }
        )
    }
    composable(NavRoutes.COMUNIDAD) {
        ComunidadScreen(
            onServiceClick = { id -> navController.navigate(NavRoutes.serviceDetail(id)) },
            onOpenSocialFeed = { navController.navigate(NavRoutes.M19_FEED) },
            onOpenMessaging = { navController.navigate(NavRoutes.M20_INBOX) },
            onOpenReputation = { navController.navigate(NavRoutes.M21_HUB) },
            onOpenProviders = { navController.navigate(NavRoutes.M22_HUB) },
            onOpenBookings = { navController.navigate(NavRoutes.M23_HOME) },
            onOpenMarketplace = { navController.navigate(NavRoutes.M25_HUB) },
            onOpenAiAssistance = { navController.navigate(NavRoutes.M26_HUB) },
            onOpenIntegrations = { navController.navigate(NavRoutes.M27_HUB) }
        )
    }
    composable(NavRoutes.MY_BUSINESS) {
        MiNegocioScreen(
            onNavigateToEditProfile = { navController.navigate(NavRoutes.EDIT_PROFILE) },
            onPublished = {
                navController.navigate(NavRoutes.COMUNIDAD) {
                    popUpTo(NavRoutes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
            }
        )
    }
    composable(NavRoutes.DAYCARE_RESERVATIONS) {
        DaycareReservationsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.DAYCARE_GUESTS) {
        DaycareGuestsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.PROFILE) {
        ProfileScreen(
            onNavigateToEditProfile = { navController.navigate(NavRoutes.EDIT_PROFILE) },
            onNavigateToMyPets = { navController.navigate(NavRoutes.MY_PETS) },
            onNavigateToMyAdoptions = { navController.navigate(NavRoutes.MY_ADOPTIONS) },
            onNavigateToMyApplications = {
                navController.navigate(NavRoutes.MY_ADOPTION_APPLICATIONS)
            },
            onNavigateToReceivedApplications = {
                navController.navigate(NavRoutes.RECEIVED_ADOPTION_APPLICATIONS)
            },
            onNavigateToChat = { navController.navigate(NavRoutes.CHAT) },
            onNavigateToFriendRequests = { navController.navigate(NavRoutes.FRIEND_REQUESTS) },
            onNavigateToNotifications = { navController.navigate(NavRoutes.NOTIFICATIONS) },
            onNavigateToAdministration = { navController.navigate(NavRoutes.ADMIN_HUB) },
            onNavigateToModeration = { navController.navigate(NavRoutes.ADMIN_MODERATION) },
            onNavigateToPlatformAdmin = { navController.navigate(NavRoutes.PLATFORM_ADMIN) },
            onNavigateToCases = { navController.navigate(NavRoutes.MODERATION_CASES) },
            onNavigateToAppealsStaff = { navController.navigate(NavRoutes.MODERATION_APPEALS) },
            onNavigateToMyAppeals = { navController.navigate(NavRoutes.MY_MODERATION_APPEALS) },
            onNavigateToVerification = { navController.navigate(NavRoutes.ORG_VERIFICATION_QUEUE) },
            onNavigateToMySupport = { navController.navigate(NavRoutes.MY_SUPPORT_TICKETS) },
            onNavigateToSupportStaff = { navController.navigate(NavRoutes.SUPPORT_ADMIN_QUEUE) },
            onNavigateToAudit = { navController.navigate(NavRoutes.ADMINISTRATIVE_AUDIT) },
            onNavigateToObservability = { navController.navigate(NavRoutes.OBSERVABILITY_OVERVIEW) },
            onNavigateToSearchFriends = { navController.navigate(NavRoutes.SEARCH_FRIENDS) },
            onNavigateToMyFriends = { navController.navigate(NavRoutes.MI_MANADA) },
            onNavigateToMiManada = { navController.navigate(NavRoutes.MI_MANADA) },
            onNavigateToSavedPosts = { navController.navigate(NavRoutes.SAVED_POSTS) },
            onNavigateToMyMemories = { navController.navigate(NavRoutes.MY_MEMORIES) },
            onNavigateToAccountSecurity = { navController.navigate(NavRoutes.ACCOUNT_SECURITY) },
            onNavigateToFirstRunTutorial = {
                navController.navigate(NavRoutes.HELP_TUTORIALS) {
                    launchSingleTop = true
                }
            },
            onNavigateToUseLeoverAs = {
                navController.navigate(NavRoutes.USE_LEOVER_AS) { launchSingleTop = true }
            },
            onNavigateToHelpTutorials = {
                navController.navigate(NavRoutes.HELP_TUTORIALS) {
                    launchSingleTop = true
                }
            },
            onNavigateToMyOrganizations = { navController.navigate(NavRoutes.MY_ORGANIZATIONS) },
            onNavigateToPublish = {
                navController.navigate(NavRoutes.PUBLISH_FROM_PROFILE) {
                    launchSingleTop = true
                }
            },
            onNavigateToMyPublications = {
                navController.navigate(NavRoutes.MY_PUBLICATIONS) {
                    launchSingleTop = true
                }
            },
            onNavigateToAddPet = { navController.navigate(NavRoutes.ADD_PET) },
            onNavigateToDonations = { navController.navigate(NavRoutes.M17_MY_HELP) },
            onNavigateToMyEvents = { navController.navigate(NavRoutes.M18_MY_EVENTS) },
            onNavigateToPrivacy = { navController.navigate(NavRoutes.PROFILE_PRIVACY) },
            onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
            onFriendClick = { userId -> navController.navigate(NavRoutes.userProfile(userId)) },
            onPetClick = { id ->
                val petId = id.trim()
                if (petId.isNotEmpty()) {
                    navController.navigate(NavRoutes.petDetail(petId)) {
                        launchSingleTop = true
                    }
                }
            },
            onOpenIncomingTransfer = { petId ->
                navController.navigate(NavRoutes.petTransfers(petId))
            }
        )
    }
    composable(NavRoutes.MY_PUBLICATIONS) {
        MyPublicationsScreen(
            onNavigateBack = { navController.popBackStack() },
            onAuthorClick = { userId -> navController.navigate(NavRoutes.userProfile(userId)) },
            onPostClick = { postId ->
                navController.navigate(NavRoutes.postDetail(postId)) {
                    launchSingleTop = true
                }
            }
        )
    }
    composable(NavRoutes.SETTINGS) {
        SettingsScreen(
            onNavigateBack = { navController.popBackStack() },
            onEditProfile = { navController.navigate(NavRoutes.EDIT_PROFILE) },
            onPrivacy = { navController.navigate(NavRoutes.PROFILE_PRIVACY) },
            onLegalPrivacy = { navController.navigate(NavRoutes.LEGAL_PRIVACY) },
            onAccountSecurity = { navController.navigate(NavRoutes.ACCOUNT_SECURITY) },
            onAddFunction = {
                navController.navigate(NavRoutes.onb02(Onb02FlowKind.ADD_FUNCTION_LATER.name)) {
                    launchSingleTop = true
                }
            },
            onNotificationPreferences = { navController.navigate(NavRoutes.NOTIFICATION_PREFERENCES) },
            onHelpTutorials = {
                navController.navigate(NavRoutes.HELP_TUTORIALS) { launchSingleTop = true }
            },
            onSupport = { navController.navigate(NavRoutes.MY_SUPPORT_TICKETS) },
            onTerms = { navController.navigate(NavRoutes.LEGAL_TERMS) },
            onLogout = onLogout,
            onAdministration = { navController.navigate(NavRoutes.ADMIN_HUB) }
        )
    }
    composable(NavRoutes.ACCOUNT_SECURITY) {
        AccountSecurityScreen(
            onNavigateBack = { navController.popBackStack() },
            onAccountDeleted = {
                navController.popBackStack(NavRoutes.PROFILE, inclusive = false)
            },
            onNavigateToTerms = { navController.navigate(NavRoutes.LEGAL_TERMS) },
            onNavigateToPrivacy = { navController.navigate(NavRoutes.LEGAL_PRIVACY) }
        )
    }
    composable(NavRoutes.LEGAL_TERMS) {
        TermsDraftScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.LEGAL_PRIVACY) {
        PrivacyDraftScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.PROFILE_PRIVACY) {
        ProfilePrivacyScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.SEARCH_FRIENDS) {
        SearchFriendsScreen(
            onNavigateBack = { navController.popBackStack() },
            onUserClick = { userId -> navController.navigate(NavRoutes.userProfile(userId)) }
        )
    }
    composable(NavRoutes.MI_MANADA) {
        MiManadaScreen(
            onNavigateBack = { navController.popBackStack() },
            onUserClick = { userId ->
                navController.navigate(NavRoutes.userProfile(userId, NavRoutes.PROFILE_FROM_MANADA))
            },
            onMessageClick = { userId, name ->
                navController.navigate(NavRoutes.chatStart(userId, name))
            }
        )
    }
    composable(NavRoutes.SAVED_POSTS) {
        SavedPostsScreen(
            onNavigateBack = { navController.popBackStack() },
            onPostClick = { postId ->
                navController.navigate(NavRoutes.postDetail(postId)) {
                    launchSingleTop = true
                }
            },
            onAuthorClick = { userId -> navController.navigate(NavRoutes.userProfile(userId)) }
        )
    }
    composable(NavRoutes.MY_FRIENDS) {
        MiManadaScreen(
            onNavigateBack = { navController.popBackStack() },
            onUserClick = { userId ->
                navController.navigate(NavRoutes.userProfile(userId, NavRoutes.PROFILE_FROM_MANADA))
            },
            onMessageClick = { userId, name ->
                navController.navigate(NavRoutes.chatStart(userId, name))
            }
        )
    }
    composable(NavRoutes.EDIT_PROFILE) {
        EditProfileScreen(
            onNavigateBack = { navController.popBackStack() },
            onSaveSuccess = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.MY_ORGANIZATIONS) {
        MyOrganizationsScreen(
            onNavigateBack = { navController.popBackStack() },
            onCreateOrganization = { navController.navigate(NavRoutes.createOrganization()) },
            onManageOrganization = { id ->
                navController.navigate(NavRoutes.manageOrganization(id))
            },
            onEditOrganization = { id ->
                navController.navigate(NavRoutes.editOrganization(id))
            },
            onOpenPublic = { slug ->
                navController.navigate(NavRoutes.publicOrganization(slug))
            }
        )
    }
    composable(
        route = NavRoutes.CREATE_ORGANIZATION,
        arguments = listOf(
            navArgument(NavRoutes.ARG_ORG_PRESELECT) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(NavRoutes.ARG_ORG_WELFARE) {
                type = NavType.BoolType
                defaultValue = false
            }
        )
    ) { entry ->
        val preselect = entry.arguments?.getString(NavRoutes.ARG_ORG_PRESELECT).orEmpty()
        val welfare = entry.arguments?.getBoolean(NavRoutes.ARG_ORG_WELFARE) == true
        val createOrgScope = rememberCoroutineScope()
        CreateOrganizationScreen(
            onNavigateBack = { navController.popBackStack() },
            preselect = preselect.takeIf { it.isNotBlank() },
            welfare = welfare,
            onCreated = { organizationId ->
                val kind = runCatching { OrganizationKindOption.valueOf(preselect) }.getOrNull()
                val createdContext = com.comunidapp.app.domain.context.NewContextActivation.contextForOrganization(
                    organizationId = organizationId,
                    publicName = kind?.let { ProfileActorTaxonomy.businessVisibleLabel(it) }
                        ?: "Organización",
                    typeOrCapability = kind?.name ?: preselect.ifBlank { "ORGANIZATION" }
                )
                createOrgScope.launch {
                    com.comunidapp.app.domain.context.OperationalContextProvider.activateNewlyCreated(createdContext)
                }
                val pending = com.comunidapp.app.domain.onboarding.onb03.PendingTutorialQueue.peek()
                val verificationRoute = if (kind == OrganizationKindOption.SHELTER) {
                    NavRoutes.leoverVerification("SHELTER", organizationId)
                } else {
                    null
                }
                if (pending != null && pending.tutorials.isNotEmpty()) {
                    if (verificationRoute != null) {
                        com.comunidapp.app.domain.onboarding.onb03.PendingTutorialQueue.set(
                            pending.copy(landingRoute = verificationRoute)
                        )
                    }
                    navController.navigate(
                        NavRoutes.onb02(
                            com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind.EVENT_QUEUE.name
                        )
                    ) {
                        popUpTo(NavRoutes.CREATE_ORGANIZATION) { inclusive = true }
                        launchSingleTop = true
                    }
                } else {
                    val landing = verificationRoute
                        ?: com.comunidapp.app.domain.context.NewContextActivation.landingRoute(
                            createdContext,
                            kind
                        )
                    navController.navigate(landing) {
                        popUpTo(NavRoutes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        )
    }
    composable(
        route = NavRoutes.EDIT_ORGANIZATION,
        arguments = listOf(
            navArgument(NavRoutes.ARG_ORGANIZATION_ID) { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val organizationId = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_ORGANIZATION_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        EditOrganizationScreen(
            onNavigateBack = { navController.popBackStack() },
            onSaveSuccess = { navController.popBackStack() },
            onRequestVerification = { functionCode ->
                navController.navigate(
                    NavRoutes.leoverVerification(functionCode, organizationId)
                )
            },
            viewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "edit_org_$organizationId",
                factory = EditOrganizationViewModel.factory(organizationId)
            )
        )
    }
    composable(
        route = NavRoutes.MANAGE_ORGANIZATION,
        arguments = listOf(
            navArgument(NavRoutes.ARG_ORGANIZATION_ID) { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val organizationId = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_ORGANIZATION_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        OrganizationManageScreen(
            onNavigateBack = { navController.popBackStack() },
            onEditProfile = {
                navController.navigate(NavRoutes.editOrganization(organizationId))
            },
            onManageTeam = {
                navController.navigate(NavRoutes.organizationTeam(organizationId))
            },
            onManageBranches = {
                navController.navigate(NavRoutes.organizationBranches(organizationId))
            },
            viewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "manage_org_$organizationId",
                factory = OrganizationManageViewModel.factory(organizationId)
            )
        )
    }
    composable(
        route = NavRoutes.ORGANIZATION_TEAM,
        arguments = listOf(
            navArgument(NavRoutes.ARG_ORGANIZATION_ID) { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val organizationId = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_ORGANIZATION_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        OrganizationTeamScreen(
            onNavigateBack = { navController.popBackStack() },
            onLeftOrganization = {
                navController.popBackStack(NavRoutes.MY_ORGANIZATIONS, false)
            },
            onClosedOrganization = {
                navController.popBackStack(NavRoutes.MY_ORGANIZATIONS, false)
            },
            viewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "team_org_$organizationId",
                factory = OrganizationTeamViewModel.factory(organizationId)
            )
        )
    }
    composable(
        route = NavRoutes.ORGANIZATION_BRANCHES,
        arguments = listOf(
            navArgument(NavRoutes.ARG_ORGANIZATION_ID) { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val organizationId = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_ORGANIZATION_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        OrganizationBranchesScreen(
            onNavigateBack = { navController.popBackStack() },
            viewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "branches_org_$organizationId",
                factory = OrganizationBranchesViewModel.factory(organizationId)
            )
        )
    }
    composable(
        route = NavRoutes.PUBLIC_ORGANIZATION,
        arguments = listOf(
            navArgument(NavRoutes.ARG_SLUG) { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val slug = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_SLUG).orEmpty(),
            Charsets.UTF_8.name()
        )
        PublicOrganizationScreen(
            onNavigateBack = { navController.popBackStack() },
            viewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "public_org_$slug",
                factory = PublicOrganizationViewModel.factory(slug)
            )
        )
    }
    composable(
        route = NavRoutes.USER_PROFILE,
        arguments = listOf(
            navArgument(NavRoutes.ARG_USER_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_PROFILE_FROM) {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) { backStackEntry ->
        val userId = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_USER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        val fromManada = backStackEntry.arguments?.getString(NavRoutes.ARG_PROFILE_FROM) ==
            NavRoutes.PROFILE_FROM_MANADA
        UserPublicProfileScreen(
            userId = userId,
            hideSocialHistory = fromManada,
            onNavigateBack = { navController.popBackStack() },
            onPetClick = { id ->
                navController.navigate(NavRoutes.connectedPetProfile(userId, id))
            },
            onMessageClick = { id, name ->
                navController.navigate(NavRoutes.chatStart(id, name))
            },
            viewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "user_profile_$userId",
                factory = UserPublicProfileViewModel.factory(userId)
            )
        )
    }
    composable(
        route = NavRoutes.CONNECTED_PET_PROFILE,
        arguments = listOf(
            navArgument(NavRoutes.ARG_OWNER_USER_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val ownerUserId = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_OWNER_USER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        val petId = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_PET_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ConnectedPetProfileScreen(
            ownerUserId = ownerUserId,
            petId = petId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.MY_MEMORIES) {
        PersonalMemoriesScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenPetMemories = { petId, petName ->
                navController.navigate(NavRoutes.myMemoriesPet(petId, petName)) {
                    launchSingleTop = true
                }
            }
        )
    }
    composable(
        route = NavRoutes.MY_MEMORIES_PET,
        arguments = listOf(
            navArgument(NavRoutes.ARG_MEMORY_PET_KEY) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_MEMORY_PET_NAME) { type = NavType.StringType }
        )
    ) { entry ->
        val petKey = entry.arguments?.getString(NavRoutes.ARG_MEMORY_PET_KEY)
        val petName = entry.arguments?.getString(NavRoutes.ARG_MEMORY_PET_NAME)
            ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }
        val petId = petKey?.takeIf { it != NavRoutes.MEMORY_PET_UNASSIGNED }
        com.comunidapp.app.ui.screens.profile.PersonalPetMemoriesScreen(
            petId = petId,
            petName = petName,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.MY_PETS) {
        MyPetsScreen(
            onNavigateBack = { navController.popBackStack() },
            onPetClick = { id -> navController.navigate(NavRoutes.petDetail(id)) },
            onAddPet = { navController.navigate(NavRoutes.ADD_PET) },
            onImportRescuer = { navController.navigate(NavRoutes.VITACORA_IMPORT_RESCUER) },
            onOpenIncomingTransfer = { petId ->
                navController.navigate(NavRoutes.petTransfers(petId))
            }
        )
    }
    composable(NavRoutes.ADD_PET) {
        val addPetViewModel: PetFormViewModel = viewModel(
            key = NavRoutes.ADD_PET,
            factory = PetFormViewModel.factory(editPetId = null)
        )
        AddPetScreen(
            onNavigateBack = { navController.popBackStack() },
            onSaveSuccess = { navController.popBackStack() },
            viewModel = addPetViewModel
        )
    }
    composable(
        route = NavRoutes.EDIT_PET,
        arguments = listOf(
            navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_PET_SECTION) {
                type = NavType.StringType
                defaultValue = "profile"
            }
        )
    ) { backStackEntry ->
        val rawPetId = backStackEntry.arguments?.getString(NavRoutes.ARG_PET_ID).orEmpty()
        val decodedPetId = runCatching {
            java.net.URLDecoder.decode(rawPetId, Charsets.UTF_8.name())
        }.getOrDefault(rawPetId)
        val petId = com.comunidapp.app.domain.pets.PetInternalId.parseUuid(decodedPetId) ?: decodedPetId
        val section = backStackEntry.arguments?.getString(NavRoutes.ARG_PET_SECTION).orEmpty()
        val editPetViewModel: PetFormViewModel = viewModel(
            key = "edit_pet_$petId",
            factory = PetFormViewModel.factory(editPetId = petId)
        )
        EditPetScreen(
            onNavigateBack = { navController.popBackStack() },
            onSaveSuccess = { navController.popBackStack() },
            onDeleteSuccess = {
                navController.popBackStack()
                navController.popBackStack()
            },
            focusSection = section,
            viewModel = editPetViewModel
        )
    }
    composable(NavRoutes.SEARCH) {
        SearchScreen(
            onNavigateBack = { navController.popBackStack() },
            onAuthorClick = { userId -> navController.navigate(NavRoutes.userProfile(userId)) },
            onPetClick = { id -> navController.navigate(NavRoutes.petDetail(id)) },
            onAdoptionClick = { id -> navController.navigate(NavRoutes.adoptionDetail(id)) }
        )
    }
    composable(NavRoutes.ADOPTIONS) {
        AdoptionsScreen(
            onAdoptionClick = { id -> navController.navigate(NavRoutes.adoptionDetail(id)) },
            onSearchAdoptions = { navController.navigate(NavRoutes.ADOPTION_SEARCH) },
            onMyApplications = { navController.navigate(NavRoutes.MY_ADOPTION_APPLICATIONS) },
            onReceivedApplications = {
                navController.navigate(NavRoutes.RECEIVED_ADOPTION_APPLICATIONS)
            },
            onAdoptionProfile = { navController.navigate(NavRoutes.ADOPTION_GENERAL_PROFILE) },
            onCreateAdoption = {
                if (CapabilityGate.canPublishAdoption(CapabilityFacts.forActiveContext(context))) {
                    navController.navigate(NavRoutes.ADOPTION_FORM)
                }
            },
            showReceivedApplications = CapabilityGate.adoptionSurface(
                CapabilityFacts.forActiveContext(context)
            ).showReceivedApplications,
            showPublishAdoption = CapabilityGate.adoptionSurface(
                CapabilityFacts.forActiveContext(context)
            ).showPublish,
            showBackButton = true,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.ADOPTION_SEARCH) {
        AdoptionSearchScreen(
            onAdoptionClick = { id -> navController.navigate(NavRoutes.adoptionDetail(id)) },
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.MY_ADOPTIONS) {
        MyAdoptionsScreen(
            onNavigateBack = { navController.popBackStack() },
            onAdoptionClick = { id -> navController.navigate(NavRoutes.adoptionDetail(id)) },
            onCreateAdoption = {
                if (CapabilityGate.canPublishAdoption(CapabilityFacts.forActiveContext(context))) {
                    navController.navigate(NavRoutes.ADOPTION_FORM)
                }
            },
            onEditAdoption = { id -> navController.navigate(NavRoutes.adoptionFormEdit(id)) },
            onReceivedApplications = {
                navController.navigate(NavRoutes.RECEIVED_ADOPTION_APPLICATIONS)
            },
            showReceivedApplications = CapabilityGate.adoptionSurface(
                CapabilityFacts.forActiveContext(context)
            ).showReceivedApplications,
            showPublishAdoption = CapabilityGate.adoptionSurface(
                CapabilityFacts.forActiveContext(context)
            ).showPublish,
            onProcess = { id -> navController.navigate(NavRoutes.adoptionProcess(id)) }
        )
    }
    composable(NavRoutes.MY_ADOPTION_APPLICATIONS) {
        MyAdoptionApplicationsScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenDetail = { id ->
                navController.navigate(NavRoutes.adoptionApplicationDetail(id))
            }
        )
    }
    composable(NavRoutes.RECEIVED_ADOPTION_APPLICATIONS) {
        ReceivedAdoptionApplicationsScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenDetail = { id ->
                navController.navigate(NavRoutes.adoptionApplicationDetail(id))
            }
        )
    }
    composable(NavRoutes.LOST_FOUND_MAP) {
        LostFoundMapScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenAlert = { id -> navController.navigate(NavRoutes.lostFoundDetail(id)) },
            onReportLost = { navController.navigate(NavRoutes.PUBLISH_LOST_FOUND) },
            onReportFound = { navController.navigate(NavRoutes.PUBLISH_FOUND_PET) }
        )
    }
    composable(
        route = NavRoutes.LOST_FOUND_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_LOST_FOUND_POST_ID) { type = NavType.StringType })
    ) { entry ->
        val raw = entry.arguments?.getString(NavRoutes.ARG_LOST_FOUND_POST_ID).orEmpty()
        val postId = runCatching { java.net.URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrDefault(raw)
        LostFoundDetailScreen(
            postId = postId,
            onNavigateBack = { navController.popBackStack() },
            onCompleteAnimal = { petId -> navController.navigate(NavRoutes.editPet(petId)) }
        )
    }
    composable(NavRoutes.RESPONDER_BASE_LOCATION) {
        com.comunidapp.app.ui.screens.location.ResponderBaseLocationScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.LEOVER_VERIFICATION,
        arguments = listOf(
            navArgument("functionCode") { type = NavType.StringType },
            navArgument(NavRoutes.ARG_VERIFICATION_ORG_ID) {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) { entry ->
        val code = java.net.URLDecoder.decode(
            entry.arguments?.getString("functionCode").orEmpty(),
            Charsets.UTF_8.name()
        ).ifBlank { "RESCUER" }
        val orgId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_VERIFICATION_ORG_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        com.comunidapp.app.ui.screens.verification.LeoverVerificationRequestScreen(
            functionCode = code,
            organizationId = orgId.takeIf { it.isNotBlank() },
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.ADOPTION_GENERAL_PROFILE) {
        com.comunidapp.app.ui.screens.adoptions.AdoptionGeneralProfileScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.FOSTER_CARE_REQUEST) { entry ->
        val petId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_PET_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        com.comunidapp.app.ui.screens.foster.RequestFosterForPetScreen(
            petId = petId,
            onNavigateBack = { navController.popBackStack() },
            onChooseApplicant = { requestId ->
                navController.navigate(NavRoutes.fosterChooseApplicant(requestId))
            }
        )
    }
    composable(NavRoutes.FOSTER_OPEN_REQUESTS) {
        com.comunidapp.app.ui.screens.foster.OpenFosterRequestsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.FOSTER_CHOOSE_APPLICANT) { entry ->
        val requestId = java.net.URLDecoder.decode(
            entry.arguments?.getString("requestId").orEmpty(),
            Charsets.UTF_8.name()
        )
        com.comunidapp.app.ui.screens.foster.ChooseFosterApplicantScreen(
            requestId = requestId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.PROFESSIONAL_HUB) {
        com.comunidapp.app.ui.screens.m28.ProfessionalHubScreen(
            onAgenda = { navController.navigate(NavRoutes.MY_VETERINARY_APPOINTMENTS) },
            onPatients = { navController.navigate(NavRoutes.PROFESSIONAL_PATIENTS) },
            onNewPatient = { navController.navigate(NavRoutes.VET_CREATE_PATIENT) },
            onNotifications = { navController.navigate(NavRoutes.NOTIFICATIONS) },
            onPublicProfile = { navController.navigate(NavRoutes.MY_BUSINESS) },
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.PROFESSIONAL_PATIENTS) {
        com.comunidapp.app.ui.screens.m28.ProfessionalPatientsScreen(
            onOpenPatient = { petId -> navController.navigate(NavRoutes.petDetail(petId)) },
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.VET_CREATE_PATIENT) {
        com.comunidapp.app.ui.screens.m28.VetCreatePatientScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.LOST_FOUND) {
        LostFoundScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToMap = { navController.navigate(NavRoutes.LOST_FOUND_MAP) },
            onCreateLost = { navController.navigate(NavRoutes.PUBLISH_LOST_FOUND) },
            onCreateFound = { navController.navigate(NavRoutes.PUBLISH_FOUND_PET) },
            onResponderBase = { navController.navigate(NavRoutes.RESPONDER_BASE_LOCATION) },
            onNavigateToM13Sightings = { navController.navigate(NavRoutes.M13_SIGHTINGS) },
            onNavigateToCaseMatches = { caseId ->
                navController.navigate(NavRoutes.m13CaseMatches(caseId))
            },
            onNavigateToM13NewSighting = { caseId ->
                if (caseId.isNullOrBlank()) {
                    navController.navigate(NavRoutes.M13_SIGHTING_NEW)
                } else {
                    navController.navigate(NavRoutes.m13SightingNewForCase(caseId))
                }
            }
        )
    }
    composable(NavRoutes.M13_SIGHTINGS) {
        M13SightingListScreen(
            onNavigateBack = { navController.popBackStack() },
            onSightingClick = { id -> navController.navigate(NavRoutes.m13SightingDetail(id)) },
            onCreate = { navController.navigate(NavRoutes.M13_SIGHTING_NEW) },
            onOpenMetrics = if (!context.isPersonal) {
                { navController.navigate(NavRoutes.M13_METRICS) }
            } else {
                null
            }
        )
    }
    composable(NavRoutes.M13_SIGHTING_NEW) {
        M13SightingCreateScreen(
            onNavigateBack = { navController.popBackStack() },
            onCreated = { id ->
                navController.navigate(NavRoutes.m13SightingDetail(id)) {
                    popUpTo(NavRoutes.M13_SIGHTINGS) { inclusive = false }
                }
            }
        )
    }
    composable(
        route = NavRoutes.M13_SIGHTING_NEW_FOR_CASE,
        arguments = listOf(navArgument(NavRoutes.ARG_CASE_ID) { type = NavType.StringType })
    ) { entry ->
        val caseId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CASE_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        M13SightingCreateScreen(
            caseId = caseId,
            onNavigateBack = { navController.popBackStack() },
            onCreated = { id ->
                navController.navigate(NavRoutes.m13SightingDetail(id)) {
                    popUpTo(NavRoutes.LOST_FOUND) { inclusive = false }
                }
            }
        )
    }
    composable(
        route = NavRoutes.M13_SIGHTING_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_SIGHTING_ID) { type = NavType.StringType })
    ) { entry ->
        val sightingId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SIGHTING_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        M13SightingDetailScreen(
            sightingId = sightingId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.M13_CASE_MATCHES,
        arguments = listOf(navArgument(NavRoutes.ARG_CASE_ID) { type = NavType.StringType })
    ) { entry ->
        val caseId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CASE_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        M13CaseMatchesScreen(
            caseId = caseId,
            onNavigateBack = { navController.popBackStack() },
            onMatchClick = { id -> navController.navigate(NavRoutes.m13MatchDetail(id)) }
        )
    }
    composable(NavRoutes.M13_MATCH_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_CANDIDATE_ID) { type = NavType.StringType })
    ) { entry ->
        val candidateId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CANDIDATE_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        M13MatchDetailScreen(
            candidateId = candidateId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.M13_METRICS) {
        M13MetricsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    m14PassportRoutes(navController)
    m15FosterRoutes(navController)
    m16ShelterRoutes(navController)
    m17DonationRoutes(navController)
    m18EventRoutes(navController)
    m19SocialRoutes(navController)
    m20MessagingRoutes(navController)
    m21ReputationRoutes(navController)
    m22ProviderRoutes(navController)
    m23BookingRoutes(navController)
    m25MarketplaceRoutes(navController)
    m26AiRoutes(navController)
    m27IntegrationRoutes(navController)
    composable(
        route = NavRoutes.ADOPTION_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_ADOPTION_ID) { type = NavType.StringType })
    ) {
        AdoptionDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            onEdit = { id -> navController.navigate(NavRoutes.adoptionFormEdit(id)) },
            onApply = { id -> navController.navigate(NavRoutes.adoptionApply(id)) },
            onMessagePublisher = { publisherId, publisherName ->
                navController.navigate(NavRoutes.chatStart(publisherId, publisherName))
            },
            onProcess = { id -> navController.navigate(NavRoutes.adoptionProcess(id)) }
        )
    }
    composable(
        route = NavRoutes.ADOPTION_APPLY,
        arguments = listOf(navArgument(NavRoutes.ARG_ADOPTION_ID) { type = NavType.StringType })
    ) {
        AdoptionApplyScreen(
            onNavigateBack = { navController.popBackStack() },
            onCompleteProfile = { navController.navigate(NavRoutes.ADOPTION_GENERAL_PROFILE) },
            onSubmitted = {
                navController.navigate(NavRoutes.MY_ADOPTION_APPLICATIONS) {
                    popUpTo(NavRoutes.SUMATE) { inclusive = false }
                }
            }
        )
    }
    composable(
        route = NavRoutes.ADOPTION_APPLICATION_DETAIL,
        arguments = listOf(
            navArgument(NavRoutes.ARG_APPLICATION_ID) { type = NavType.StringType }
        )
    ) {
        AdoptionApplicationDetailScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.ADOPTION_PROCESS,
        arguments = listOf(navArgument(NavRoutes.ARG_ADOPTION_ID) { type = NavType.StringType })
    ) {
        AdoptionProcessScreen(
            onNavigateBack = { navController.popBackStack() },
            onInterviews = { id -> navController.navigate(NavRoutes.adoptionInterviews(id)) },
            onDocuments = { id -> navController.navigate(NavRoutes.adoptionDocuments(id)) },
            onAgreement = { id -> navController.navigate(NavRoutes.adoptionAgreement(id)) },
            onFinalize = { id -> navController.navigate(NavRoutes.adoptionFinalize(id)) },
            onFollowUp = { id -> navController.navigate(NavRoutes.adoptionFollowUp(id)) }
        )
    }
    composable(
        route = NavRoutes.ADOPTION_INTERVIEWS,
        arguments = listOf(navArgument(NavRoutes.ARG_ADOPTION_ID) { type = NavType.StringType })
    ) {
        AdoptionInterviewsScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenDetail = { id -> navController.navigate(NavRoutes.adoptionInterviewDetail(id)) }
        )
    }
    composable(
        route = NavRoutes.ADOPTION_INTERVIEW_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_INTERVIEW_ID) { type = NavType.StringType })
    ) {
        AdoptionInterviewDetailScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(
        route = NavRoutes.ADOPTION_DOCUMENTS,
        arguments = listOf(navArgument(NavRoutes.ARG_ADOPTION_ID) { type = NavType.StringType })
    ) {
        AdoptionDocumentsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(
        route = NavRoutes.ADOPTION_AGREEMENT,
        arguments = listOf(navArgument(NavRoutes.ARG_ADOPTION_ID) { type = NavType.StringType })
    ) {
        AdoptionAgreementScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(
        route = NavRoutes.ADOPTION_FINALIZE,
        arguments = listOf(navArgument(NavRoutes.ARG_ADOPTION_ID) { type = NavType.StringType })
    ) { entry ->
        val adoptionId = entry.arguments?.getString(NavRoutes.ARG_ADOPTION_ID).orEmpty()
        AdoptionFinalizeScreen(
            onNavigateBack = { navController.popBackStack() },
            onFinalized = {
                if (adoptionId.isNotBlank()) {
                    navController.navigate(NavRoutes.adoptionFollowUp(adoptionId)) {
                        popUpTo(NavRoutes.adoptionProcess(adoptionId)) { inclusive = false }
                    }
                }
            }
        )
    }
    composable(
        route = NavRoutes.ADOPTION_FOLLOWUP,
        arguments = listOf(navArgument(NavRoutes.ARG_ADOPTION_ID) { type = NavType.StringType })
    ) {
        AdoptionFollowUpScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenCheck = { id -> navController.navigate(NavRoutes.adoptionFollowUpCheck(id)) }
        )
    }
    composable(
        route = NavRoutes.ADOPTION_FOLLOWUP_CHECK,
        arguments = listOf(navArgument(NavRoutes.ARG_CHECK_ID) { type = NavType.StringType })
    ) {
        AdoptionFollowUpCheckDetailScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.FOSTER_HOMES) {
        FosterHomesScreen(
            onNavigateBack = { navController.popBackStack() },
            onHomeClick = { id -> navController.navigate(NavRoutes.fosterHomeDetail(id)) },
            onMyHome = { navController.navigate(NavRoutes.MY_FOSTER_HOME) },
            onReceived = { navController.navigate(NavRoutes.FOSTER_REQUESTS_RECEIVED) },
            onSent = { navController.navigate(NavRoutes.FOSTER_REQUESTS_SENT) },
            onPlacements = { navController.navigate(NavRoutes.FOSTER_PLACEMENTS) },
            onHistory = { navController.navigate(NavRoutes.FOSTER_HISTORY) }
        )
    }
    composable(NavRoutes.MY_FOSTER_HOME) {
        MyFosterHomeScreen(
            onNavigateBack = { navController.popBackStack() },
            onCreate = { navController.navigate(NavRoutes.FOSTER_HOME_FORM) },
            onEdit = { id -> navController.navigate(NavRoutes.fosterHomeFormEdit(id)) },
            onPlacements = { navController.navigate(NavRoutes.FOSTER_PLACEMENTS) },
            onRequests = { navController.navigate(NavRoutes.FOSTER_REQUESTS_RECEIVED) },
            onOpenRequests = { navController.navigate(NavRoutes.FOSTER_OPEN_REQUESTS) },
            onNewPlacement = { navController.navigate(NavRoutes.FOSTER_NEW_PLACEMENT) }
        )
    }
    composable(NavRoutes.FOSTER_HOME_FORM) {
        FosterHomeFormScreen(
            onNavigateBack = { navController.popBackStack() },
            onSaved = {
                navController.navigate(NavRoutes.MY_FOSTER_HOME) {
                    popUpTo(NavRoutes.FOSTER_HOME_FORM) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )
    }
    composable(
        route = NavRoutes.FOSTER_HOME_FORM_EDIT,
        arguments = listOf(navArgument(NavRoutes.ARG_FOSTER_HOME_ID) { type = NavType.StringType })
    ) { entry ->
        val id = entry.arguments?.getString(NavRoutes.ARG_FOSTER_HOME_ID).orEmpty()
        FosterHomeFormScreen(
            editHomeId = java.net.URLDecoder.decode(id, Charsets.UTF_8.name()),
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.FOSTER_HOME_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_FOSTER_HOME_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_FOSTER_HOME_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        FosterHomeDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            onRequest = { homeId -> navController.navigate(NavRoutes.fosterRequestForm(homeId)) },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = FosterHomeDetailViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.FOSTER_REQUEST_FORM,
        arguments = listOf(navArgument(NavRoutes.ARG_FOSTER_HOME_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_FOSTER_HOME_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        FosterRequestFormScreen(
            onNavigateBack = { navController.popBackStack() },
            onSubmitted = {
                navController.navigate(NavRoutes.FOSTER_REQUESTS_SENT) {
                    popUpTo(NavRoutes.FOSTER_HOMES) { inclusive = false }
                }
            },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = FosterRequestFormViewModel.factory(id)
            )
        )
    }
    composable(NavRoutes.FOSTER_REQUESTS_SENT) {
        FosterRequestsScreen(
            title = "Solicitudes enviadas",
            received = false,
            onNavigateBack = { navController.popBackStack() },
            onRequestClick = { id -> navController.navigate(NavRoutes.fosterRequestDetail(id)) }
        )
    }
    composable(NavRoutes.FOSTER_REQUESTS_RECEIVED) {
        FosterRequestsScreen(
            title = "Solicitudes recibidas",
            received = true,
            onNavigateBack = { navController.popBackStack() },
            showBackButton = context !is OperationalContext.Foster,
            onRequestClick = { id -> navController.navigate(NavRoutes.fosterRequestDetail(id)) }
        )
    }
    composable(
        route = NavRoutes.FOSTER_REQUEST_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_FOSTER_REQUEST_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_FOSTER_REQUEST_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        FosterRequestDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            onPlacementStarted = { placementId ->
                navController.navigate(NavRoutes.fosterPlacementManagement(placementId))
            },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = FosterRequestDetailViewModel.factory(id)
            )
        )
    }
    composable(NavRoutes.FOSTER_PLACEMENTS) {
        FosterPlacementsScreen(
            onNavigateBack = { navController.popBackStack() },
            showBackButton = context !is OperationalContext.Foster,
            onPlacementClick = { id -> navController.navigate(NavRoutes.fosterPlacementManagement(id)) },
            onNewPlacement = { navController.navigate(NavRoutes.FOSTER_NEW_PLACEMENT) }
        )
    }
    composable(NavRoutes.FOSTER_NEW_PLACEMENT) {
        FosterNewPlacementScreen(
            onNavigateBack = { navController.popBackStack() },
            onCreated = {
                navController.navigate(NavRoutes.FOSTER_PLACEMENTS) {
                    popUpTo(NavRoutes.FOSTER_NEW_PLACEMENT) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )
    }
    composable(
        route = NavRoutes.FOSTER_PLACEMENT_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_FOSTER_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_FOSTER_PLACEMENT_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        FosterPlacementDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = FosterPlacementDetailViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.FOSTER_PLACEMENT_MANAGEMENT,
        arguments = listOf(navArgument(NavRoutes.ARG_FOSTER_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_FOSTER_PLACEMENT_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        FosterPlacementManagementScreen(
            onNavigateBack = { navController.popBackStack() },
            onExpenses = { pid -> navController.navigate(NavRoutes.fosterExpenses(pid)) },
            onEvolution = { pid -> navController.navigate(NavRoutes.fosterEvolution(pid)) },
            onHelp = { pid -> navController.navigate(NavRoutes.fosterHelp(pid)) },
            onComplete = { pid -> navController.navigate(NavRoutes.fosterComplete(pid)) },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = FosterPlacementManagementViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.FOSTER_EXPENSES,
        arguments = listOf(navArgument(NavRoutes.ARG_FOSTER_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_FOSTER_PLACEMENT_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        FosterExpensesScreen(
            onNavigateBack = { navController.popBackStack() },
            onAdd = { navController.navigate(NavRoutes.fosterExpenseForm(id)) },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = FosterExpensesViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.FOSTER_EXPENSE_FORM,
        arguments = listOf(navArgument(NavRoutes.ARG_FOSTER_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_FOSTER_PLACEMENT_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        FosterExpenseFormScreen(
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = FosterExpenseFormViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.FOSTER_EVOLUTION,
        arguments = listOf(navArgument(NavRoutes.ARG_FOSTER_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_FOSTER_PLACEMENT_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        FosterEvolutionScreen(
            onNavigateBack = { navController.popBackStack() },
            onAdd = { navController.navigate(NavRoutes.fosterEvolutionForm(id)) },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = FosterEvolutionListViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.FOSTER_EVOLUTION_FORM,
        arguments = listOf(navArgument(NavRoutes.ARG_FOSTER_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_FOSTER_PLACEMENT_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        FosterEvolutionFormScreen(
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = FosterEvolutionFormViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.FOSTER_HELP,
        arguments = listOf(navArgument(NavRoutes.ARG_FOSTER_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_FOSTER_PLACEMENT_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        FosterHelpScreen(
            onNavigateBack = { navController.popBackStack() },
            onAdd = { navController.navigate(NavRoutes.fosterHelpForm(id)) },
            onDetail = { hid -> navController.navigate(NavRoutes.fosterHelpDetail(hid)) },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = FosterHelpListViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.FOSTER_HELP_FORM,
        arguments = listOf(navArgument(NavRoutes.ARG_FOSTER_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_FOSTER_PLACEMENT_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        FosterHelpFormScreen(
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = FosterHelpFormViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.FOSTER_HELP_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_FOSTER_HELP_REQUEST_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_FOSTER_HELP_REQUEST_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        FosterHelpDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = FosterHelpDetailViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.FOSTER_COMPLETE,
        arguments = listOf(navArgument(NavRoutes.ARG_FOSTER_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_FOSTER_PLACEMENT_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        FosterCompleteScreen(
            onNavigateBack = { navController.popBackStack() },
            onCompleted = {
                navController.navigate(NavRoutes.FOSTER_HISTORY) {
                    popUpTo(NavRoutes.FOSTER_HOMES) { inclusive = false }
                }
            },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = FosterCompleteViewModel.factory(id)
            )
        )
    }
    composable(NavRoutes.FOSTER_HISTORY) {
        FosterHistoryScreen(
            onNavigateBack = { navController.popBackStack() },
            onPlacementClick = { id -> navController.navigate(NavRoutes.fosterPlacementManagement(id)) }
        )
    }
    composable(NavRoutes.SHELTERS) {
        ShelterOpsListScreen(
            onNavigateBack = { navController.popBackStack() },
            showBackButton = context !is OperationalContext.Organization,
            onShelterClick = { id -> navController.navigate(NavRoutes.shelterOpsDetail(id)) },
            onMyShelters = { navController.navigate(NavRoutes.MY_SHELTERS) },
            onPublicCampaigns = { navController.navigate(NavRoutes.SHELTER_PUBLIC_CAMPAIGNS) },
            onPublicSupplyRequests = { navController.navigate(NavRoutes.SHELTER_PUBLIC_SUPPLY_REQUESTS) },
            onPublicEmergencies = { navController.navigate(NavRoutes.SHELTER_PUBLIC_EMERGENCIES) },
            onPublicEvents = { navController.navigate(NavRoutes.SHELTER_PUBLIC_EVENTS) },
            onImportPets = { orgId, orgName -> navController.navigate(NavRoutes.vitacoraImport(orgId, orgName)) },
            onImportRescuer = { navController.navigate(NavRoutes.VITACORA_IMPORT_RESCUER) },
            onAddPet = { navController.navigate(NavRoutes.ADD_PET) },
            operationalHub = com.comunidapp.app.ui.screens.context.OperationalHubActions(
                onProfile = {
                    val org = context as? com.comunidapp.app.domain.context.OperationalContext.Organization
                    if (org != null) navController.navigate(NavRoutes.editOrganization(org.entityId))
                    else navController.navigate(NavRoutes.EDIT_PROFILE)
                },
                onAnimals = {
                    if (context is com.comunidapp.app.domain.context.OperationalContext.Rescuer) {
                        navController.navigate(NavRoutes.MY_PETS)
                    } else {
                        navController.navigate(NavRoutes.MY_SHELTERS)
                    }
                },
                onAddPet = { navController.navigate(NavRoutes.ADD_PET) },
                onImportPets = {
                    val org = context as? com.comunidapp.app.domain.context.OperationalContext.Organization
                    if (org != null) {
                        navController.navigate(NavRoutes.vitacoraImport(org.entityId, org.displayName))
                    } else {
                        navController.navigate(NavRoutes.VITACORA_IMPORT_RESCUER)
                    }
                },
                onAdoptions = { navController.navigate(NavRoutes.ADOPTIONS) },
                onCampaigns = { navController.navigate(NavRoutes.M17_HUB) },
                onEvents = { navController.navigate(NavRoutes.M18_EVENTS) },
                onManagement = { navController.navigate(NavRoutes.SHELTERS) },
                onTeam = {
                    val org = context as? com.comunidapp.app.domain.context.OperationalContext.Organization
                    if (org != null) navController.navigate(NavRoutes.organizationTeam(org.entityId))
                },
                onBranches = {
                    val org = context as? com.comunidapp.app.domain.context.OperationalContext.Organization
                    if (org != null) navController.navigate(NavRoutes.organizationBranches(org.entityId))
                },
                onFoster = { navController.navigate(NavRoutes.FOSTER_HOMES) },
                onLostFound = { navController.navigate(NavRoutes.LOST_FOUND) },
                onRescuerProfile = { navController.navigate(NavRoutes.EDIT_PROFILE) },
                onVolunteer = { navController.navigate(NavRoutes.M17_HUB) }
            )
        )
    }
    composable(NavRoutes.MY_SHELTERS) {
        MySheltersScreen(
            onNavigateBack = { navController.popBackStack() },
            showBackButton = context !is OperationalContext.Organization,
            onShelterClick = { id -> navController.navigate(NavRoutes.shelterDashboard(id)) },
            onCreate = { navController.navigate(NavRoutes.SHELTER_FORM) },
            onImportPets = { orgId, orgName -> navController.navigate(NavRoutes.vitacoraImport(orgId, orgName)) },
            onAddPet = { navController.navigate(NavRoutes.ADD_PET) }
        )
    }
    composable(NavRoutes.SHELTER_FORM) {
        ShelterOpsFormScreen(
            onNavigateBack = { navController.popBackStack() },
            onSaved = { id ->
                navController.navigate(NavRoutes.shelterDashboard(id)) {
                    popUpTo(NavRoutes.MY_SHELTERS) { inclusive = false }
                }
            }
        )
    }
    composable(
        route = NavRoutes.SHELTER_FORM_EDIT,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterOpsFormScreen(
            editShelterId = id,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ShelterFormViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.SHELTER_OPS_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterOpsDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            onDashboard = { sid -> navController.navigate(NavRoutes.shelterDashboard(sid)) },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ShelterOpsDetailViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.SHELTER_DASHBOARD,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterDashboardScreen(
            onNavigateBack = { navController.popBackStack() },
            onPets = { sid -> navController.navigate(NavRoutes.shelterPets(sid)) },
            onVolunteers = { sid -> navController.navigate(NavRoutes.shelterVolunteers(sid)) },
            onEdit = { sid -> navController.navigate(NavRoutes.shelterFormEdit(sid)) },
            onCampaigns = { sid -> navController.navigate(NavRoutes.shelterCampaigns(sid)) },
            onSupplyRequests = { sid -> navController.navigate(NavRoutes.shelterSupplyRequests(sid)) },
            onEmergencies = { sid -> navController.navigate(NavRoutes.shelterEmergencies(sid)) },
            onEvents = { sid -> navController.navigate(NavRoutes.shelterEvents(sid)) },
            onReports = { sid -> navController.navigate(NavRoutes.shelterReports(sid)) },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ShelterDashboardViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.SHELTER_PETS,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterOpsPetsScreen(
            onNavigateBack = { navController.popBackStack() },
            onIntake = { navController.navigate(NavRoutes.shelterPetIntake(id)) },
            onDetail = { pid -> navController.navigate(NavRoutes.shelterPetDetail(pid)) },
            onImportPets = {
                val org = context as? OperationalContext.Organization
                navController.navigate(
                    NavRoutes.vitacoraImport(org?.entityId ?: id, org?.displayName ?: "Organización")
                )
            },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ShelterPetsViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.SHELTER_PET_INTAKE,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterIntakeScreen(
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ShelterIntakeViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.SHELTER_PET_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_PLACEMENT_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterOpsPetDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ShelterPetDetailViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.SHELTER_VOLUNTEERS,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterOpsVolunteersScreen(
            onNavigateBack = { navController.popBackStack() },
            onInvite = { navController.navigate(NavRoutes.shelterVolunteerInvite(id)) },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ShelterVolunteersViewModel.factory(id)
            )
        )
    }
    composable(
        route = NavRoutes.SHELTER_VOLUNTEER_INVITE,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterVolunteerInviteScreen(
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ShelterVolunteerInviteViewModel.factory(id)
            )
        )
    }
    composable(NavRoutes.SHELTER_PUBLIC_CAMPAIGNS) {
        ShelterPublicCampaignsScreen(
            onNavigateBack = { navController.popBackStack() },
            onCampaignClick = { cid -> navController.navigate(NavRoutes.shelterCampaignDetail(cid)) }
        )
    }
    composable(NavRoutes.SHELTER_PUBLIC_SUPPLY_REQUESTS) {
        ShelterPublicSupplyRequestsScreen(
            onNavigateBack = { navController.popBackStack() },
            onRequestClick = { rid -> navController.navigate(NavRoutes.shelterSupplyRequestDetail(rid)) },
            onContribute = { rid -> navController.navigate(NavRoutes.shelterSupplyContribute(rid)) }
        )
    }
    composable(NavRoutes.SHELTER_PUBLIC_EMERGENCIES) {
        ShelterPublicEmergenciesScreen(
            onNavigateBack = { navController.popBackStack() },
            onEmergencyClick = { eid -> navController.navigate(NavRoutes.shelterEmergencyDetail(eid)) },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ShelterPublicEmergenciesViewModel.factory()
            )
        )
    }
    composable(NavRoutes.SHELTER_PUBLIC_EVENTS) {
        ShelterPublicEventsScreen(
            onNavigateBack = { navController.popBackStack() },
            onEventClick = { eid -> navController.navigate(NavRoutes.shelterEventDetail(eid)) },
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ShelterPublicEventsViewModel.factory()
            )
        )
    }
    composable(
        route = NavRoutes.SHELTER_CAMPAIGNS,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterCampaignsScreen(
            onNavigateBack = { navController.popBackStack() },
            onCreate = { navController.navigate(NavRoutes.shelterCampaignForm(id)) },
            onDetail = { cid -> navController.navigate(NavRoutes.shelterCampaignDetail(cid)) },
            viewModel = viewModel(factory = ShelterCampaignsViewModel.factory(id))
        )
    }
    composable(
        route = NavRoutes.SHELTER_CAMPAIGN_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_CAMPAIGN_ID) { type = NavType.StringType })
    ) { entry ->
        val cid = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CAMPAIGN_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterCampaignDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            onAddUpdate = { id -> navController.navigate(NavRoutes.shelterCampaignUpdate(id)) },
            onEdit = { sid, campId -> navController.navigate(NavRoutes.shelterCampaignFormEdit(sid, campId)) },
            viewModel = viewModel(factory = ShelterCampaignDetailViewModel.factory(cid))
        )
    }
    composable(
        route = NavRoutes.SHELTER_CAMPAIGN_FORM,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterCampaignFormScreen(
            shelterId = id,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { campId ->
                navController.navigate(NavRoutes.shelterCampaignDetail(campId)) {
                    popUpTo(NavRoutes.shelterCampaigns(id)) { inclusive = false }
                }
            },
            viewModel = viewModel(factory = ShelterCampaignFormViewModel.factory(id))
        )
    }
    composable(
        route = NavRoutes.SHELTER_CAMPAIGN_FORM_EDIT,
        arguments = listOf(
            navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_CAMPAIGN_ID) { type = NavType.StringType }
        )
    ) { entry ->
        val sid = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        val cid = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CAMPAIGN_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterCampaignFormScreen(
            shelterId = sid,
            editCampaignId = cid,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            viewModel = viewModel(factory = ShelterCampaignFormViewModel.factory(sid, cid))
        )
    }
    composable(
        route = NavRoutes.SHELTER_CAMPAIGN_UPDATE,
        arguments = listOf(navArgument(NavRoutes.ARG_CAMPAIGN_ID) { type = NavType.StringType })
    ) { entry ->
        val cid = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CAMPAIGN_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterCampaignUpdateScreen(
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            viewModel = viewModel(factory = ShelterCampaignUpdateFormViewModel.factory(cid))
        )
    }
    composable(
        route = NavRoutes.SHELTER_SUPPLY_REQUESTS,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterSupplyRequestsScreen(
            onNavigateBack = { navController.popBackStack() },
            onCreate = { navController.navigate(NavRoutes.shelterSupplyRequestForm(id)) },
            onDetail = { rid -> navController.navigate(NavRoutes.shelterSupplyRequestDetail(rid)) },
            viewModel = viewModel(factory = ShelterSupplyRequestsViewModel.factory(id))
        )
    }
    composable(
        route = NavRoutes.SHELTER_SUPPLY_REQUEST_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_SUPPLY_REQUEST_ID) { type = NavType.StringType })
    ) { entry ->
        val rid = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SUPPLY_REQUEST_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterSupplyRequestDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            onEdit = { sid, reqId -> navController.navigate(NavRoutes.shelterSupplyRequestFormEdit(sid, reqId)) },
            onContributions = { reqId -> navController.navigate(NavRoutes.shelterSupplyContributions(reqId)) },
            onContribute = { reqId -> navController.navigate(NavRoutes.shelterSupplyContribute(reqId)) },
            viewModel = viewModel(factory = ShelterSupplyRequestDetailViewModel.factory(rid))
        )
    }
    composable(
        route = NavRoutes.SHELTER_SUPPLY_REQUEST_FORM,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterSupplyRequestFormScreen(
            shelterId = id,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { reqId ->
                navController.navigate(NavRoutes.shelterSupplyRequestDetail(reqId)) {
                    popUpTo(NavRoutes.shelterSupplyRequests(id)) { inclusive = false }
                }
            },
            viewModel = viewModel(factory = ShelterSupplyRequestFormViewModel.factory(id))
        )
    }
    composable(
        route = NavRoutes.SHELTER_SUPPLY_REQUEST_FORM_EDIT,
        arguments = listOf(
            navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_SUPPLY_REQUEST_ID) { type = NavType.StringType }
        )
    ) { entry ->
        val sid = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        val rid = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SUPPLY_REQUEST_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterSupplyRequestFormScreen(
            shelterId = sid,
            editRequestId = rid,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            viewModel = viewModel(factory = ShelterSupplyRequestFormViewModel.factory(sid, rid))
        )
    }
    composable(
        route = NavRoutes.SHELTER_SUPPLY_CONTRIBUTE,
        arguments = listOf(navArgument(NavRoutes.ARG_SUPPLY_REQUEST_ID) { type = NavType.StringType })
    ) { entry ->
        val rid = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SUPPLY_REQUEST_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterSupplyContributeScreen(
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            viewModel = viewModel(factory = ShelterSupplyContributeViewModel.factory(rid))
        )
    }
    composable(
        route = NavRoutes.SHELTER_SUPPLY_CONTRIBUTIONS,
        arguments = listOf(navArgument(NavRoutes.ARG_SUPPLY_REQUEST_ID) { type = NavType.StringType })
    ) { entry ->
        val rid = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SUPPLY_REQUEST_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterSupplyContributionsScreen(
            onNavigateBack = { navController.popBackStack() },
            viewModel = viewModel(factory = ShelterSupplyContributionsViewModel.factory(rid))
        )
    }
    composable(
        route = NavRoutes.SHELTER_EMERGENCIES,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterEmergenciesScreen(
            onNavigateBack = { navController.popBackStack() },
            onCreate = { navController.navigate(NavRoutes.shelterEmergencyForm(id)) },
            onDetail = { eid -> navController.navigate(NavRoutes.shelterEmergencyDetail(eid)) },
            viewModel = viewModel(factory = ShelterEmergenciesViewModel.factory(id))
        )
    }
    composable(
        route = NavRoutes.SHELTER_EMERGENCY_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_EMERGENCY_ID) { type = NavType.StringType })
    ) { entry ->
        val eid = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_EMERGENCY_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterEmergencyDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            onEdit = { sid, emergId ->
                navController.navigate(NavRoutes.shelterEmergencyFormEdit(sid, emergId))
            },
            viewModel = viewModel(factory = ShelterEmergencyDetailViewModel.factory(eid))
        )
    }
    composable(
        route = NavRoutes.SHELTER_EMERGENCY_FORM,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterEmergencyFormScreen(
            shelterId = id,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { emergId ->
                navController.navigate(NavRoutes.shelterEmergencyDetail(emergId)) {
                    popUpTo(NavRoutes.shelterEmergencies(id)) { inclusive = false }
                }
            },
            viewModel = viewModel(factory = ShelterEmergencyFormViewModel.factory(id))
        )
    }
    composable(
        route = NavRoutes.SHELTER_EMERGENCY_FORM_EDIT,
        arguments = listOf(
            navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_EMERGENCY_ID) { type = NavType.StringType }
        )
    ) { entry ->
        val sid = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        val eid = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_EMERGENCY_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterEmergencyFormScreen(
            shelterId = sid,
            editEmergencyId = eid,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            viewModel = viewModel(factory = ShelterEmergencyFormViewModel.factory(sid, eid))
        )
    }
    composable(
        route = NavRoutes.SHELTER_EVENTS,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterEventsScreen(
            onNavigateBack = { navController.popBackStack() },
            onCreate = { navController.navigate(NavRoutes.shelterEventForm(id)) },
            onDetail = { evId -> navController.navigate(NavRoutes.shelterEventDetail(evId)) },
            viewModel = viewModel(factory = ShelterEventsViewModel.factory(id))
        )
    }
    composable(
        route = NavRoutes.SHELTER_EVENT_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_EVENT_ID) { type = NavType.StringType })
    ) { entry ->
        val evId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_EVENT_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterEventDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            onEdit = { sid, eventId ->
                navController.navigate(NavRoutes.shelterEventFormEdit(sid, eventId))
            },
            onRegistrations = { eventId ->
                navController.navigate(NavRoutes.shelterEventRegistrations(eventId))
            },
            viewModel = viewModel(factory = ShelterEventDetailViewModel.factory(evId))
        )
    }
    composable(
        route = NavRoutes.SHELTER_EVENT_FORM,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterEventFormScreen(
            shelterId = id,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { evId ->
                navController.navigate(NavRoutes.shelterEventDetail(evId)) {
                    popUpTo(NavRoutes.shelterEvents(id)) { inclusive = false }
                }
            },
            viewModel = viewModel(factory = ShelterEventFormViewModel.factory(id))
        )
    }
    composable(
        route = NavRoutes.SHELTER_EVENT_FORM_EDIT,
        arguments = listOf(
            navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_EVENT_ID) { type = NavType.StringType }
        )
    ) { entry ->
        val sid = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        val evId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_EVENT_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterEventFormScreen(
            shelterId = sid,
            editEventId = evId,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            viewModel = viewModel(factory = ShelterEventFormViewModel.factory(sid, evId))
        )
    }
    composable(
        route = NavRoutes.SHELTER_EVENT_REGISTRATIONS,
        arguments = listOf(navArgument(NavRoutes.ARG_EVENT_ID) { type = NavType.StringType })
    ) { entry ->
        val evId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_EVENT_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterEventRegistrationsScreen(
            onNavigateBack = { navController.popBackStack() },
            viewModel = viewModel(factory = ShelterEventRegistrationsViewModel.factory(evId))
        )
    }
    composable(
        route = NavRoutes.SHELTER_REPORTS,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_SHELTER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ShelterReportsScreen(
            onNavigateBack = { navController.popBackStack() },
            viewModel = viewModel(factory = ShelterReportsViewModel.factory(id))
        )
    }
    composable(NavRoutes.VETERINARY_DIRECTORY) {
        VeterinaryDirectoryScreen(
            onNavigateBack = { navController.popBackStack() },
            onClinicClick = { id -> navController.navigate(NavRoutes.veterinaryClinicDetail(id)) },
            onMyClinics = { navController.navigate(NavRoutes.MY_VETERINARY_CLINICS) },
            onMyAppointments = { navController.navigate(NavRoutes.MY_VETERINARY_APPOINTMENTS) }
        )
    }
    composable(
        route = NavRoutes.VETERINARY_CLINIC_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_CLINIC_ID) { type = NavType.StringType })
    ) { entry ->
        val clinicId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CLINIC_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        VeterinaryClinicDetailScreen(
            clinicId = clinicId,
            onNavigateBack = { navController.popBackStack() },
            onBookAppointment = { id ->
                navController.navigate(NavRoutes.veterinaryBookAppointment(id))
            }
        )
    }
    composable(NavRoutes.MY_VETERINARY_CLINICS) {
        ManagedVeterinaryClinicsScreen(
            onNavigateBack = { navController.popBackStack() },
            onClinicClick = { id -> navController.navigate(NavRoutes.veterinaryClinicDraftEdit(id)) },
            onCreate = { navController.navigate(NavRoutes.VETERINARY_CLINIC_DRAFT) }
        )
    }
    composable(NavRoutes.VETERINARY_CLINIC_DRAFT) {
        VeterinaryClinicDraftScreen(
            clinicId = null,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { id ->
                navController.navigate(NavRoutes.veterinaryClinicDraftEdit(id)) {
                    popUpTo(NavRoutes.MY_VETERINARY_CLINICS) { inclusive = false }
                }
            }
        )
    }
    composable(
        route = NavRoutes.VETERINARY_CLINIC_DRAFT_EDIT,
        arguments = listOf(navArgument(NavRoutes.ARG_CLINIC_ID) { type = NavType.StringType })
    ) { entry ->
        val clinicId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CLINIC_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        VeterinaryClinicManageHubScreen(
            clinicId = clinicId,
            onNavigateBack = { navController.popBackStack() },
            onProfessionals = {
                navController.navigate(NavRoutes.veterinaryClinicProfessionals(clinicId))
            },
            onServices = {
                navController.navigate(NavRoutes.veterinaryClinicServices(clinicId))
            },
            onHours = {
                navController.navigate(NavRoutes.veterinaryClinicHours(clinicId))
            },
            onAgenda = {
                navController.navigate(NavRoutes.veterinaryManagedAgenda(clinicId))
            }
        )
    }
    composable(
        route = NavRoutes.VETERINARY_CLINIC_PROFESSIONALS,
        arguments = listOf(navArgument(NavRoutes.ARG_CLINIC_ID) { type = NavType.StringType })
    ) { entry ->
        val clinicId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CLINIC_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        VeterinaryClinicProfessionalsScreen(
            clinicId = clinicId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.VETERINARY_CLINIC_SERVICES,
        arguments = listOf(navArgument(NavRoutes.ARG_CLINIC_ID) { type = NavType.StringType })
    ) { entry ->
        val clinicId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CLINIC_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        VeterinaryClinicServicesScreen(
            clinicId = clinicId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.VETERINARY_CLINIC_HOURS,
        arguments = listOf(navArgument(NavRoutes.ARG_CLINIC_ID) { type = NavType.StringType })
    ) { entry ->
        val clinicId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CLINIC_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        VeterinaryClinicHoursScreen(
            clinicId = clinicId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.VETERINARY_BOOK_APPOINTMENT,
        arguments = listOf(navArgument(NavRoutes.ARG_CLINIC_ID) { type = NavType.StringType })
    ) { entry ->
        val clinicId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CLINIC_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        VeterinaryBookAppointmentScreen(
            clinicId = clinicId,
            onNavigateBack = { navController.popBackStack() },
            onBooked = { appointmentId ->
                navController.navigate(NavRoutes.veterinaryAppointmentDetail(appointmentId)) {
                    popUpTo(NavRoutes.VETERINARY_BOOK_APPOINTMENT) { inclusive = true }
                }
            }
        )
    }
    composable(NavRoutes.MY_VETERINARY_APPOINTMENTS) {
        MyVeterinaryAppointmentsScreen(
            onNavigateBack = { navController.popBackStack() },
            onAppointmentClick = { id ->
                navController.navigate(NavRoutes.veterinaryAppointmentDetail(id))
            }
        )
    }
    composable(
        route = NavRoutes.VETERINARY_APPOINTMENT_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_APPOINTMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val appointmentId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_APPOINTMENT_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        VeterinaryAppointmentDetailScreen(
            appointmentId = appointmentId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.VETERINARY_MANAGED_AGENDA,
        arguments = listOf(navArgument(NavRoutes.ARG_CLINIC_ID) { type = NavType.StringType })
    ) { entry ->
        val clinicId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CLINIC_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        VeterinaryManagedAgendaScreen(
            clinicId = clinicId,
            onNavigateBack = { navController.popBackStack() },
            onManageAppointment = { id ->
                navController.navigate(NavRoutes.veterinaryAppointmentManagement(id))
            },
            onSettings = {
                navController.navigate(NavRoutes.veterinaryScheduleSettings(clinicId))
            },
            onRules = {
                navController.navigate(NavRoutes.veterinaryAvailabilityRules(clinicId))
            }
        )
    }
    composable(
        route = NavRoutes.VETERINARY_SCHEDULE_SETTINGS,
        arguments = listOf(navArgument(NavRoutes.ARG_CLINIC_ID) { type = NavType.StringType })
    ) { entry ->
        val clinicId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CLINIC_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        VeterinaryScheduleSettingsScreen(
            clinicId = clinicId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.VETERINARY_AVAILABILITY_RULES,
        arguments = listOf(navArgument(NavRoutes.ARG_CLINIC_ID) { type = NavType.StringType })
    ) { entry ->
        val clinicId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CLINIC_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        VeterinaryAvailabilityRulesScreen(
            clinicId = clinicId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.VETERINARY_APPOINTMENT_MANAGEMENT,
        arguments = listOf(navArgument(NavRoutes.ARG_APPOINTMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val appointmentId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_APPOINTMENT_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        VeterinaryAppointmentManagementScreen(
            appointmentId = appointmentId,
            onNavigateBack = { navController.popBackStack() },
            onRegisterProfessionalCare = { clinicId, petId, apptId ->
                navController.navigate(NavRoutes.m28ClinicCare(clinicId, petId, apptId))
            }
        )
    }
    composable(
        route = NavRoutes.M28_PET_GRANTS,
        arguments = listOf(navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType })
    ) { entry ->
        val petId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_PET_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        M28PetGrantsScreen(petId = petId, clinicIdForGrant = null, onNavigateBack = { navController.popBackStack() })
    }
    composable(
        route = NavRoutes.M28_PET_PROPOSALS,
        arguments = listOf(navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType })
    ) { entry ->
        val petId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_PET_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        M28PassportProposalsScreen(petId = petId, onNavigateBack = { navController.popBackStack() })
    }
    composable(
        route = NavRoutes.M28_CLINIC_CARE,
        arguments = listOf(
            navArgument(NavRoutes.ARG_CLINIC_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_M28_APPOINTMENT_QUERY) {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) { entry ->
        val clinicId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CLINIC_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        val petId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_PET_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        val appointmentId = entry.arguments?.getString(NavRoutes.ARG_M28_APPOINTMENT_QUERY).orEmpty().ifBlank { null }
        M28ClinicCareScreen(
            clinicId = clinicId,
            petId = petId,
            appointmentId = appointmentId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.SHELTER_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
    ) {
        ShelterDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            onAdoptionClick = { id -> navController.navigate(NavRoutes.adoptionDetail(id)) },
            onM16ShelterClick = { m16Id -> navController.navigate(NavRoutes.m16ShelterDetail(m16Id)) }
        )
    }
    composable(
        route = NavRoutes.PET_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType })
    ) { backStackEntry ->
        val rawPetId = backStackEntry.arguments?.getString(NavRoutes.ARG_PET_ID).orEmpty()
        val petId = runCatching {
            java.net.URLDecoder.decode(rawPetId, Charsets.UTF_8.name())
        }.getOrDefault(rawPetId)
        PetDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToEdit = { id -> navController.navigate(NavRoutes.editPet(id)) },
            onNavigateToEditHealth = { id -> navController.navigate(NavRoutes.editPet(id, "health")) },
            onDeleteSuccess = { navController.popBackStack() },
            onNavigateToResponsibilities = { id ->
                navController.navigate(NavRoutes.petResponsibilities(id))
            },
            onNavigateToAuthorizations = { id ->
                navController.navigate(NavRoutes.petAuthorizations(id))
            },
            onNavigateToTransfers = { id ->
                navController.navigate(NavRoutes.petTransfers(id))
            },
            onNavigateToStatusHistory = { id ->
                navController.navigate(NavRoutes.petStatusHistory(id))
            },
            onNavigateToPassport = { id ->
                navController.navigate(NavRoutes.m14PetPassport(id))
            },
            onNavigateToShareQr = { id ->
                navController.navigate(NavRoutes.m14PetShare(id))
            },
            onNavigateToM28Grants = { id -> navController.navigate(NavRoutes.m28PetGrants(id)) },
            onNavigateToM28Proposals = { id -> navController.navigate(NavRoutes.m28PetProposals(id)) },
            onNavigateToReportLost = { navController.navigate(NavRoutes.publishLostFound(petId)) },
            onNavigateToFosterTransit = { id ->
                navController.navigate(NavRoutes.fosterCareRequest(id))
            },
            viewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "pet_detail_$petId",
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return com.comunidapp.app.viewmodel.PetDetailViewModel(
                            androidx.lifecycle.SavedStateHandle(mapOf("petId" to petId))
                        ) as T
                    }
                }
            )
        )
    }
    composable(
        route = NavRoutes.PET_STATUS_HISTORY,
        arguments = listOf(navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType })
    ) { backStackEntry ->
        val petId = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_PET_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        PetStatusHistoryScreen(
            onNavigateBack = { navController.popBackStack() },
            viewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "pet_status_history_$petId",
                factory = PetStatusHistoryViewModel.factory(petId)
            )
        )
    }
    composable(
        route = NavRoutes.PET_RESPONSIBILITIES,
        arguments = listOf(navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType })
    ) { backStackEntry ->
        val petId = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_PET_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        PetResponsibilitiesScreen(
            onNavigateBack = { navController.popBackStack() },
            viewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "pet_responsibilities_$petId",
                factory = PetResponsibilitiesViewModel.factory(petId)
            )
        )
    }
    composable(
        route = NavRoutes.PET_AUTHORIZATIONS,
        arguments = listOf(navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType })
    ) { backStackEntry ->
        val petId = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_PET_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        PetAuthorizationsScreen(
            onNavigateBack = { navController.popBackStack() },
            viewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "pet_authorizations_$petId",
                factory = PetAuthorizationsViewModel.factory(petId)
            )
        )
    }
    composable(
        route = NavRoutes.PET_TRANSFERS,
        arguments = listOf(navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType })
    ) { backStackEntry ->
        val petId = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_PET_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        PetTransfersScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenTransferDetail = { transferId ->
                navController.navigate(NavRoutes.petTransferDetail(petId, transferId))
            },
            onAccepted = { navigateToProfileMyPets(navController) },
            viewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "pet_transfers_$petId",
                factory = PetTransfersViewModel.factory(petId)
            )
        )
    }
    composable(
        route = NavRoutes.PET_TRANSFER_DETAIL,
        arguments = listOf(
            navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_TRANSFER_ID) { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val petId = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_PET_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        val transferId = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_TRANSFER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        PetTransferDetailScreen(
            transferId = transferId,
            onNavigateBack = { navController.popBackStack() },
            onAccepted = { navigateToProfileMyPets(navController) },
            viewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "pet_transfer_detail_$petId",
                factory = PetTransfersViewModel.factory(petId)
            )
        )
    }
    composable(NavRoutes.PUBLISH_GENERAL) {
        PublishGeneralScreen(
            onNavigateBack = { navController.popBackStack() },
            onPublishSuccess = { popCreatorSuccess(navController) }
        )
    }
    composable(NavRoutes.PUBLISH_REEL) {
        PublishReelScreen(
            onNavigateBack = { navController.popBackStack() },
            onPublishSuccess = { popCreatorSuccess(navController) }
        )
    }
    composable(NavRoutes.PUBLISH_STORY) {
        PublishStoryScreen(
            origin = "HOME_STORY_PLUS",
            autoOpenPicker = false,
            onNavigateBack = {
                if (!navController.popBackStack(NavRoutes.HOME, inclusive = false)) {
                    navController.popBackStack()
                }
            },
            onPublishSuccess = {
                navController.popBackStack(NavRoutes.HOME, inclusive = false)
            }
        )
    }
    composable(NavRoutes.PUBLISH_QUESTION) {
        PublishQuestionScreen(
            onNavigateBack = { navController.popBackStack() },
            onPublishSuccess = {
                navController.popBackStack()
                navController.navigate(NavRoutes.HOME)
            }
        )
    }
    composable(NavRoutes.PUBLISH_PROMO) {
        PublishPromoScreen(
            onNavigateBack = { navController.popBackStack() },
            onPublishSuccess = {
                navController.popBackStack()
                navController.navigate(NavRoutes.HOME)
            }
        )
    }
    composable(NavRoutes.ADOPTION_FORM) {
        AdoptionFormScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToCreatePet = { navController.navigate(NavRoutes.ADD_PET) },
            onSaved = { id ->
                navController.popBackStack()
                navController.navigate(NavRoutes.adoptionDetail(id))
            }
        )
    }
    composable(
        route = NavRoutes.ADOPTION_FORM_EDIT,
        arguments = listOf(navArgument(NavRoutes.ARG_ADOPTION_ID) { type = NavType.StringType })
    ) {
        AdoptionFormScreen(
            onNavigateBack = { navController.popBackStack() },
            onSaved = { id ->
                navController.popBackStack()
                navController.navigate(NavRoutes.adoptionDetail(id)) {
                    launchSingleTop = true
                }
            }
        )
    }
    composable(NavRoutes.PUBLISH_ADOPTION) {
        AdoptionFormScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToCreatePet = { navController.navigate(NavRoutes.ADD_PET) },
            onSaved = { id ->
                navController.popBackStack(NavRoutes.SUMATE, inclusive = false)
                navController.navigate(NavRoutes.adoptionDetail(id))
            }
        )
    }
    composable(NavRoutes.PUBLISH_URGENT) {
        PublishUrgentScreen(
            onNavigateBack = { navController.popBackStack() },
            onPublishSuccess = {
                navController.popBackStack()
                navController.navigate(NavRoutes.HOME)
            }
        )
    }
    composable(NavRoutes.PUBLISH_LOST_FOUND) {
        PublishLostFoundScreen(
            onNavigateBack = { navController.popBackStack() },
            onPublishSuccess = { navigateToHomeFeed(navController) },
            initialType = com.comunidapp.app.data.model.LostFoundType.LOST,
            onCreateMinimalPet = { navController.navigate(NavRoutes.ADD_PET) }
        )
    }
    composable(
        route = NavRoutes.PUBLISH_LOST_FOUND_FOR_PET,
        arguments = listOf(navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType })
    ) { entry ->
        val petId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_PET_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        PublishLostFoundScreen(
            onNavigateBack = { navController.popBackStack() },
            onPublishSuccess = { navigateToHomeFeed(navController) },
            initialType = com.comunidapp.app.data.model.LostFoundType.LOST,
            prefillPetId = petId,
            onCreateMinimalPet = { navController.navigate(NavRoutes.ADD_PET) }
        )
    }
    composable(NavRoutes.PUBLISH_FOUND_PET) {
        PublishLostFoundScreen(
            onNavigateBack = { navController.popBackStack() },
            onPublishSuccess = { navigateToHomeFeed(navController) },
            initialType = com.comunidapp.app.data.model.LostFoundType.FOUND
        )
    }
    composable(NavRoutes.PUBLISH_FOSTER) {
        PublishFosterScreen(
            onNavigateBack = { navController.popBackStack() },
            onPublishSuccess = {
                navController.popBackStack(NavRoutes.SUMATE, inclusive = false)
            }
        )
    }
    composable(NavRoutes.PUBLISH_EVENT) {
        PublishEventScreen(
            onNavigateBack = { navController.popBackStack() },
            onPublishSuccess = {
                navController.popBackStack(NavRoutes.SUMATE, inclusive = false)
            }
        )
    }
    composable(NavRoutes.PUBLISH_DONATION) {
        PublishDonationScreen(
            onNavigateBack = { navController.popBackStack() },
            onPublishSuccess = {
                navController.popBackStack()
                navController.navigate(NavRoutes.SUMATE)
            }
        )
    }
    composable(NavRoutes.PUBLISH_SHELTER) {
        PublishShelterScreen(
            onNavigateBack = { navController.popBackStack() },
            onPublishSuccess = {
                navController.popBackStack()
                navController.navigate(NavRoutes.SUMATE)
            }
        )
    }
    composable(
        route = NavRoutes.SERVICE_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_SERVICE_ID) { type = NavType.StringType })
    ) { entry ->
        val rawId = entry.arguments?.getString(NavRoutes.ARG_SERVICE_ID).orEmpty()
        val serviceId = java.net.URLDecoder.decode(rawId, Charsets.UTF_8.name())
        ServiceDetailScreen(
            serviceId = serviceId,
            onNavigateBack = { navController.popBackStack() },
            onChatClick = { ownerId, name ->
                navController.navigate(NavRoutes.chatStart(ownerId, name))
            }
        )
    }
    composable(NavRoutes.CHAT) {
        ChatListScreen(
            onNavigateBack = { navController.popBackStack() },
            onConversationClick = { conversationId, peerName ->
                navController.navigate(NavRoutes.chatThread(conversationId, peerName))
            }
        )
    }
    composable(NavRoutes.FRIEND_REQUESTS) {
        FriendRequestsScreen(
            onNavigateBack = { navController.popBackStack() },
            onUserClick = { userId -> navController.navigate(NavRoutes.userProfile(userId)) }
        )
    }
    composable(NavRoutes.NOTIFICATIONS) {
        NotificationsScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToPreferences = {
                navController.navigate(NavRoutes.NOTIFICATION_PREFERENCES)
            },
            onOpenInvitation = { id ->
                navController.navigate(NavRoutes.orgInvitation(id))
            },
            onOpenCareTransfer = { petId ->
                navController.navigate(NavRoutes.petTransfers(petId))
            },
            onOpenLostFound = { alertId ->
                navController.navigate(NavRoutes.lostFoundDetail(alertId))
            }
        )
    }
    composable(
        route = NavRoutes.ORG_INVITATION,
        arguments = listOf(navArgument(NavRoutes.ARG_INVITATION_ID) { type = NavType.StringType })
    ) { entry ->
        val invitationId = RouteArgDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_INVITATION_ID)
        )
        com.comunidapp.app.ui.screens.organization.OrgInvitationAcceptScreen(
            invitationId = invitationId,
            onNavigateBack = { navController.popBackStack() },
            onAccepted = { orgId ->
                val pending = com.comunidapp.app.domain.onboarding.onb03.PendingTutorialQueue.peek()
                if (pending != null && pending.tutorials.isNotEmpty()) {
                    navController.navigate(
                        NavRoutes.onb02(
                            com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind.EVENT_QUEUE.name
                        )
                    ) {
                        popUpTo(NavRoutes.ORG_INVITATION) { inclusive = true }
                    }
                } else {
                    navController.navigate(NavRoutes.manageOrganization(orgId)) {
                        popUpTo(NavRoutes.ORG_INVITATION) { inclusive = true }
                    }
                }
            }
        )
    }
    composable(
        route = NavRoutes.STORY_VIEWER,
        arguments = listOf(navArgument(NavRoutes.ARG_STORY_AUTHOR_ID) { type = NavType.StringType })
    ) { entry ->
        val authorId = RouteArgDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_STORY_AUTHOR_ID)
        )
        val stories by DataProvider.feedRepository.observeActiveStories().collectAsState()
        val ordered = com.comunidapp.app.domain.social.StoryTrayGrouping.segmentsOldestFirst(
            stories.filter { it.authorId == authorId && it.isActiveStory() }
        )
        com.comunidapp.app.ui.screens.social.StoryViewerScreen(
            stories = ordered,
            initialIndex = 0,
            onClose = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.CLIP_VIEWER,
        arguments = listOf(navArgument(NavRoutes.ARG_CLIP_POST_ID) { type = NavType.StringType })
    ) { entry ->
        val postId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CLIP_POST_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        com.comunidapp.app.ui.screens.social.ClipViewerScreen(
            startPostId = postId,
            onNavigateBack = { navController.popBackStack() },
            onAuthorClick = { userId -> navController.navigate(NavRoutes.userProfile(userId)) },
            onComment = { id ->
                navController.navigate(NavRoutes.postDetail(id)) { launchSingleTop = true }
            }
        )
    }
    composable(NavRoutes.NOTIFICATION_PREFERENCES) {
        NotificationPreferencesScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.ADMIN_HUB) {
        AdminHubScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToUsers = { navController.navigate(NavRoutes.PLATFORM_ADMIN) },
            onNavigateToModeration = { navController.navigate(NavRoutes.ADMIN_MODERATION) },
            onNavigateToStaff = { navController.navigate(NavRoutes.ADMIN_STAFF) },
            onNavigateToCatalogs = { navController.navigate(NavRoutes.ADMIN_CATALOGS) },
            onNavigateToSupport = { navController.navigate(NavRoutes.SUPPORT_ADMIN_QUEUE) }
        )
    }
    adminStaffAndCatalogRoutes(navController)
    composable(NavRoutes.ADMIN_MODERATION) {
        AdminModerationScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.MODERATION_REPORT_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_REPORT_ID) { type = NavType.StringType })
    ) { entry ->
        val reportId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_REPORT_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ModerationReportDetailScreen(
            reportId = reportId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.MODERATION_CASES) {
        ModerationCaseQueueScreen(
            onNavigateBack = { navController.popBackStack() },
            onCaseClick = { id -> navController.navigate(NavRoutes.moderationCaseDetail(id)) }
        )
    }
    composable(
        route = NavRoutes.MODERATION_CASE_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_CASE_ID) { type = NavType.StringType })
    ) { entry ->
        val caseId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CASE_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ModerationCaseDetailScreen(
            caseId = caseId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.MODERATION_APPEALS) {
        ModerationAppealQueueScreen(
            onNavigateBack = { navController.popBackStack() },
            onAppealClick = { id -> navController.navigate(NavRoutes.moderationAppealDetail(id)) }
        )
    }
    composable(
        route = NavRoutes.MODERATION_APPEAL_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_APPEAL_ID) { type = NavType.StringType })
    ) { entry ->
        val appealId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_APPEAL_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        ModerationAppealDetailScreen(
            appealId = appealId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.MY_MODERATION_APPEALS) {
        MyModerationAppealsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.ORG_VERIFICATION_QUEUE) {
        OrganizationVerificationQueueScreen(
            onNavigateBack = { navController.popBackStack() },
            onReviewClick = { id -> navController.navigate(NavRoutes.orgVerificationReview(id)) },
            onShelterVerificationClick = { id ->
                navController.navigate(NavRoutes.m16ShelterVerificationReview(id))
            }
        )
    }
    composable(
        route = NavRoutes.M16_SHELTER_VERIFICATION_REVIEW,
        arguments = listOf(navArgument(NavRoutes.ARG_M16_VERIFICATION_REQUEST_ID) { type = NavType.StringType })
    ) { entry ->
        val requestId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M16_VERIFICATION_REQUEST_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        M16ShelterVerificationReviewScreen(
            requestId = requestId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.ORG_VERIFICATION_REVIEW,
        arguments = listOf(navArgument(NavRoutes.ARG_REVIEW_ID) { type = NavType.StringType })
    ) { entry ->
        val reviewId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_REVIEW_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        OrganizationVerificationReviewScreen(
            reviewId = reviewId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.MY_SUPPORT_TICKETS) {
        MySupportTicketsScreen(
            onNavigateBack = { navController.popBackStack() },
            onTicketClick = { id -> navController.navigate(NavRoutes.supportTicketDetail(id)) },
            onCreateClick = { navController.navigate(NavRoutes.CREATE_SUPPORT_TICKET) }
        )
    }
    composable(NavRoutes.CREATE_SUPPORT_TICKET) {
        CreateSupportTicketScreen(
            onNavigateBack = { navController.popBackStack() },
            onCreated = { id ->
                navController.popBackStack()
                navController.navigate(NavRoutes.supportTicketDetail(id))
            }
        )
    }
    composable(
        route = NavRoutes.SUPPORT_TICKET_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_TICKET_ID) { type = NavType.StringType })
    ) { entry ->
        val ticketId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_TICKET_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        SupportTicketDetailScreen(
            ticketId = ticketId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.SUPPORT_ADMIN_QUEUE) {
        SupportQueueScreen(
            onNavigateBack = { navController.popBackStack() },
            onTicketClick = { id -> navController.navigate(NavRoutes.supportAdminTicket(id)) }
        )
    }
    composable(
        route = NavRoutes.SUPPORT_ADMIN_TICKET,
        arguments = listOf(navArgument(NavRoutes.ARG_TICKET_ID) { type = NavType.StringType })
    ) { entry ->
        val ticketId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_TICKET_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        SupportTicketAdminDetailScreen(
            ticketId = ticketId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.ADMINISTRATIVE_AUDIT) {
        AdministrativeAuditScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.OBSERVABILITY_OVERVIEW) {
        ObservabilityOverviewScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToMetrics = { navController.navigate(NavRoutes.OBSERVABILITY_METRICS) },
            onNavigateToHealth = { navController.navigate(NavRoutes.OBSERVABILITY_HEALTH) },
            onNavigateToIncidents = { navController.navigate(NavRoutes.OBSERVABILITY_INCIDENTS) },
            onNavigateToAudit = { navController.navigate(NavRoutes.OBSERVABILITY_AUDIT) },
            onNavigateToErrors = { navController.navigate(NavRoutes.OBSERVABILITY_ERRORS) },
            onNavigateToExports = { navController.navigate(NavRoutes.OBSERVABILITY_EXPORTS) },
            onNavigateToRetention = { navController.navigate(NavRoutes.OBSERVABILITY_RETENTION) },
            onNavigateToPermissionsInfo = {
                navController.navigate(NavRoutes.OBSERVABILITY_PERMISSIONS_INFO)
            }
        )
    }
    composable(NavRoutes.OBSERVABILITY_METRICS) {
        ObservabilityMetricsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.OBSERVABILITY_HEALTH) {
        ObservabilityHealthScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.OBSERVABILITY_INCIDENTS) {
        ObservabilityIncidentsScreen(onNavigateBack = { navController.popBackStack() })
    }
    // M07 Etapa 6: dedicated screens â€” no AdministrativeAuditScreen / audit.view proxy
    composable(NavRoutes.OBSERVABILITY_AUDIT) {
        ObservabilityAuditListScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.OBSERVABILITY_ERRORS) {
        ObservabilityErrorsListScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.OBSERVABILITY_EXPORTS) {
        ObservabilityExportsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.OBSERVABILITY_RETENTION) {
        ObservabilityRetentionScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.OBSERVABILITY_PERMISSIONS_INFO) {
        ObservabilityPermissionsInfoScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.PLATFORM_ADMIN) {
        PlatformAdminScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.ADMIN_VITACORA_IMPORTS) {
        AdminVitacoraImportQueueScreen(
            onNavigateBack = { navController.popBackStack() },
            onNew = { navController.navigate(NavRoutes.ADMIN_VITACORA_IMPORT_NEW) }
        )
    }
    composable(NavRoutes.ADMIN_VITACORA_IMPORT_NEW) {
        AdminVitacoraImportNewScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(
        route = NavRoutes.VITACORA_IMPORT,
        arguments = listOf(
            navArgument(NavRoutes.ARG_ORGANIZATION_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_ORG_NAME) { type = NavType.StringType }
        )
    ) { entry ->
        val orgId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_ORGANIZATION_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        val orgName = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_ORG_NAME).orEmpty(),
            Charsets.UTF_8.name()
        )
        VitacoraImportScreen(
            orgId = orgId,
            orgName = orgName,
            isStaff = false,
            onNavigateBack = { navController.popBackStack() },
            onViewImported = { navController.navigate(NavRoutes.MY_PETS) }
        )
    }
    composable(NavRoutes.VITACORA_IMPORT_RESCUER) {
        VitacoraImportScreen(
            orgId = "",
            orgName = "Rescatista",
            isStaff = false,
            onNavigateBack = { navController.popBackStack() },
            onViewImported = { navController.navigate(NavRoutes.MY_PETS) }
        )
    }
    composable(NavRoutes.LOCATION_CATALOG_ADMIN) {
        LocationCatalogAdminScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.MASTER_CATALOG_ADMIN) {
        MasterCatalogAdminScreen(
            onNavigateBack = { navController.popBackStack() },
            allowGeography = false
        )
    }
    composable(
        route = NavRoutes.CHAT_START,
        arguments = listOf(
            navArgument(NavRoutes.ARG_USER_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_PEER_NAME) { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val peerUserId = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_USER_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        val peerName = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_PEER_NAME).orEmpty(),
            Charsets.UTF_8.name()
        ).ifBlank { "Usuario" }
        ChatStartScreen(
            onNavigateBack = { navController.popBackStack() },
            onConversationReady = { conversationId ->
                navController.navigate(NavRoutes.chatThread(conversationId, peerName)) {
                    popUpTo(NavRoutes.CHAT_START) { inclusive = true }
                }
            },
            viewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "chat_start_$peerUserId",
                factory = ChatStartViewModel.factory(peerUserId, peerName)
            )
        )
    }
    composable(
        route = NavRoutes.CHAT_THREAD,
        arguments = listOf(
            navArgument(NavRoutes.ARG_CONVERSATION_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_PEER_NAME) { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val conversationId = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_CONVERSATION_ID).orEmpty(),
            Charsets.UTF_8.name()
        )
        val peerName = java.net.URLDecoder.decode(
            backStackEntry.arguments?.getString(NavRoutes.ARG_PEER_NAME).orEmpty(),
            Charsets.UTF_8.name()
        ).ifBlank { "Usuario" }
        ChatThreadScreen(
            peerName = peerName,
            onNavigateBack = { navController.popBackStack() },
            viewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                key = "chat_thread_$conversationId",
                factory = ChatThreadViewModel.factory(conversationId)
            )
        )
    }
}

/** Tras publicar alerta de mascota perdida/encontrada: Inicio/feed, no el formulario. */
private fun navigateToHomeFeed(navController: NavHostController) {
    navController.navigate(NavRoutes.HOME) {
        popUpTo(NavRoutes.HOME) { inclusive = false }
        launchSingleTop = true
    }
}

/** After ACCEPT: close the operational transfer screens and open Perfil → Mis mascotas. */
private fun navigateToProfileMyPets(navController: NavHostController) {
    if (!navController.popBackStack(NavRoutes.PET_TRANSFERS, inclusive = true)) {
        navController.popBackStack()
    }
    navController.navigate(NavRoutes.PROFILE) {
        launchSingleTop = true
    }
    navController.navigate(NavRoutes.MY_PETS) {
        launchSingleTop = true
    }
}

/** Tras publicar desde creador social: vuelve a Perfil si el origen fue Perfil; si no, a Inicio. */
private fun popCreatorSuccess(navController: NavHostController) {
    when {
        navController.popBackStack(NavRoutes.PROFILE, inclusive = false) -> Unit
        navController.popBackStack(NavRoutes.HOME, inclusive = false) -> Unit
        else -> {
            navController.navigate(NavRoutes.HOME) {
                launchSingleTop = true
            }
        }
    }
}
