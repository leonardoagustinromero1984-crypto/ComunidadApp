package com.comunidapp.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.notifications.NotificationDeepLinkRouter
import com.comunidapp.app.notifications.NotificationDeepLinkSessionResolver
import com.comunidapp.app.notifications.NotificationPendingNavigationStore
import com.comunidapp.app.ui.components.ComunidappBottomBar
import com.comunidapp.app.ui.components.SessionLoadingScreen
import com.comunidapp.app.ui.components.bottomNavItemsFor
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
import com.comunidapp.app.ui.screens.admin.PlatformAdminScreen
import com.comunidapp.app.ui.screens.moderation.ModerationAppealDetailScreen
import com.comunidapp.app.ui.screens.moderation.ModerationAppealQueueScreen
import com.comunidapp.app.ui.screens.moderation.ModerationCaseDetailScreen
import com.comunidapp.app.ui.screens.moderation.ModerationCaseQueueScreen
import com.comunidapp.app.ui.screens.moderation.ModerationQueueScreen
import com.comunidapp.app.ui.screens.moderation.ModerationReportDetailScreen
import com.comunidapp.app.ui.screens.moderation.MyModerationAppealsScreen
import com.comunidapp.app.ui.screens.support.CreateSupportTicketScreen
import com.comunidapp.app.ui.screens.support.MySupportTicketsScreen
import com.comunidapp.app.ui.screens.support.SupportQueueScreen
import com.comunidapp.app.ui.screens.support.SupportTicketAdminDetailScreen
import com.comunidapp.app.ui.screens.support.SupportTicketDetailScreen
import com.comunidapp.app.ui.screens.verification.OrganizationVerificationQueueScreen
import com.comunidapp.app.ui.screens.verification.OrganizationVerificationReviewScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionAgreementScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionApplicationDetailScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionApplyScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionDetailScreen
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
import com.comunidapp.app.ui.screens.home.HomeScreen
import com.comunidapp.app.ui.screens.login.EmailVerificationScreen
import com.comunidapp.app.ui.screens.login.ForgotPasswordScreen
import com.comunidapp.app.ui.screens.login.LoginScreen
import com.comunidapp.app.ui.screens.login.RegisterScreen
import com.comunidapp.app.ui.screens.legal.PrivacyDraftScreen
import com.comunidapp.app.ui.screens.legal.TermsDraftScreen
import com.comunidapp.app.ui.screens.lostfound.LostFoundMapScreen
import com.comunidapp.app.ui.screens.lostfound.LostFoundScreen
import com.comunidapp.app.ui.screens.pets.AddPetScreen
import com.comunidapp.app.ui.screens.pets.EditPetScreen
import com.comunidapp.app.ui.screens.pets.MyPetsScreen
import com.comunidapp.app.ui.screens.pets.PetAuthorizationsScreen
import com.comunidapp.app.ui.screens.pets.PetDetailScreen
import com.comunidapp.app.ui.screens.pets.PetResponsibilitiesScreen
import com.comunidapp.app.ui.screens.pets.PetStatusHistoryScreen
import com.comunidapp.app.ui.screens.pets.PetTransferDetailScreen
import com.comunidapp.app.ui.screens.pets.PetTransfersScreen
import com.comunidapp.app.ui.screens.profile.EditProfileScreen
import com.comunidapp.app.ui.screens.profile.FriendRequestsScreen
import com.comunidapp.app.ui.screens.profile.NotificationPreferencesScreen
import com.comunidapp.app.ui.screens.profile.NotificationsScreen
import com.comunidapp.app.ui.screens.profile.ProfileScreen
import com.comunidapp.app.ui.screens.profile.SearchFriendsScreen
import com.comunidapp.app.ui.screens.profile.UserPublicProfileScreen
import com.comunidapp.app.ui.screens.organization.CreateOrganizationScreen
import com.comunidapp.app.ui.screens.organization.EditOrganizationScreen
import com.comunidapp.app.ui.screens.organization.MyOrganizationsScreen
import com.comunidapp.app.ui.screens.organization.OrganizationBranchesScreen
import com.comunidapp.app.ui.screens.organization.OrganizationManageScreen
import com.comunidapp.app.ui.screens.organization.OrganizationTeamScreen
import com.comunidapp.app.ui.screens.organization.PublicOrganizationScreen
import com.comunidapp.app.ui.screens.onboarding.ProfileOnboardingScreen
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
import com.comunidapp.app.ui.screens.publish.PublishGeneralScreen
import com.comunidapp.app.ui.screens.publish.PublishLostFoundScreen
import com.comunidapp.app.ui.screens.publish.PublishPromoScreen
import com.comunidapp.app.ui.screens.publish.PublishQuestionScreen
import com.comunidapp.app.ui.screens.publish.PublishUrgentScreen
import com.comunidapp.app.ui.screens.publish.PublishDonationScreen
import com.comunidapp.app.ui.screens.publish.PublishEventScreen
import com.comunidapp.app.ui.screens.publish.PublishFosterScreen
import com.comunidapp.app.ui.screens.publish.PublishShelterScreen
import com.comunidapp.app.ui.screens.publish.PublishScreen
import com.comunidapp.app.data.model.AccountType
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
import com.comunidapp.app.viewmodel.SessionState
import com.comunidapp.app.viewmodel.SessionViewModel

@Composable
fun ComunidappNavGraph(
    sessionViewModel: SessionViewModel = viewModel()
) {
    val sessionState by sessionViewModel.sessionState.collectAsState()
    val currentUser by sessionViewModel.currentUser.collectAsState()

    when (sessionState) {
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
            ProfileOnboardingScreen(
                onComplete = { sessionViewModel.onProfileSetupCompleted() }
            )
        }
        SessionState.AccountAccessBlocked -> {
            val blockedStatus by sessionViewModel.blockedAccountStatus.collectAsState()
            AccountAccessBlockedScreen(
                accountStatus = blockedStatus,
                sessionViewModel = sessionViewModel
            )
        }
        SessionState.LoggedOut, SessionState.LoggedIn -> {
            key(sessionState) {
                RootNavHost(
                    isLoggedIn = sessionState == SessionState.LoggedIn,
                    accountType = currentUser?.accountType ?: AccountType.PERSON
                )
            }
        }
    }
}

@Composable
private fun RootNavHost(
    isLoggedIn: Boolean,
    accountType: AccountType
) {
    val rootNavController = rememberNavController()
    val startDestination = if (isLoggedIn) NavRoutes.MAIN else NavRoutes.LOGIN

    NavHost(
        navController = rootNavController,
        startDestination = startDestination
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
                    rootNavController.navigate(NavRoutes.LOGIN) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(NavRoutes.MAIN) {
            MainScreen(accountType = accountType)
        }
    }
}

@Composable
private fun MainScreen(accountType: AccountType) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val bottomNavRoutes = bottomNavItemsFor(accountType).map { it.route }
    val showBottomBar = currentRoute in bottomNavRoutes

    LaunchedEffect(Unit) {
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

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                ComunidappBottomBar(
                    navController = navController,
                    accountType = accountType
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.HOME,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(NavRoutes.HOME) {
                HomeScreen(
                    onAuthorClick = { userId ->
                        navController.navigate(NavRoutes.userProfile(userId))
                    },
                    onNavigateToSearch = { navController.navigate(NavRoutes.SEARCH) }
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
                    onVeterinaryDirectory = { navController.navigate(NavRoutes.VETERINARY_DIRECTORY) }
                )
            }
            composable(NavRoutes.PUBLISH) {
                PublishScreen(
                    accountType = accountType,
                    onNavigateToGeneral = { navController.navigate(NavRoutes.PUBLISH_GENERAL) },
                    onNavigateToQuestion = { navController.navigate(NavRoutes.PUBLISH_QUESTION) },
                    onNavigateToPromo = { navController.navigate(NavRoutes.PUBLISH_PROMO) },
                    onNavigateToAdoption = { navController.navigate(NavRoutes.ADOPTION_FORM) },
                    onNavigateToLostFound = { navController.navigate(NavRoutes.PUBLISH_LOST_FOUND) },
                    onNavigateToUrgent = { navController.navigate(NavRoutes.PUBLISH_URGENT) },
                    onNavigateToFoster = { navController.navigate(NavRoutes.PUBLISH_FOSTER) },
                    onNavigateToEvent = { navController.navigate(NavRoutes.PUBLISH_EVENT) },
                    onNavigateToDonation = { navController.navigate(NavRoutes.PUBLISH_DONATION) },
                    onNavigateToShelter = { navController.navigate(NavRoutes.PUBLISH_SHELTER) }
                )
            }
            composable(NavRoutes.COMUNIDAD) {
                ComunidadScreen(
                    onServiceClick = { id -> navController.navigate(NavRoutes.serviceDetail(id)) }
                )
            }
            composable(NavRoutes.MY_BUSINESS) {
                MiNegocioScreen(
                    onNavigateToEditProfile = { navController.navigate(NavRoutes.EDIT_PROFILE) }
                )
            }
            composable(NavRoutes.PROFILE) {
                ProfileScreen(
                    onNavigateToEditProfile = { navController.navigate(NavRoutes.EDIT_PROFILE) },
                    onNavigateToMyPets = { navController.navigate(NavRoutes.MY_PETS) },
                    onNavigateToMyAdoptions = { navController.navigate(NavRoutes.MY_ADOPTIONS) },
                    onNavigateToMyApplications = {
                        navController.navigate(NavRoutes.MY_ADOPTION_APPLICATIONS)
                    },
                    onNavigateToChat = { navController.navigate(NavRoutes.CHAT) },
                    onNavigateToFriendRequests = { navController.navigate(NavRoutes.FRIEND_REQUESTS) },
                    onNavigateToNotifications = { navController.navigate(NavRoutes.NOTIFICATIONS) },
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
                    onNavigateToAccountSecurity = { navController.navigate(NavRoutes.ACCOUNT_SECURITY) },
                    onNavigateToMyOrganizations = { navController.navigate(NavRoutes.MY_ORGANIZATIONS) },
                    onFriendClick = { userId -> navController.navigate(NavRoutes.userProfile(userId)) },
                    onPetClick = { id -> navController.navigate(NavRoutes.petDetail(id)) }
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
            composable(NavRoutes.SEARCH_FRIENDS) {
                SearchFriendsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onUserClick = { userId -> navController.navigate(NavRoutes.userProfile(userId)) }
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
                    onCreateOrganization = { navController.navigate(NavRoutes.CREATE_ORGANIZATION) },
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
            composable(NavRoutes.CREATE_ORGANIZATION) {
                CreateOrganizationScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onCreated = { organizationId ->
                        navController.navigate(NavRoutes.editOrganization(organizationId)) {
                            popUpTo(NavRoutes.MY_ORGANIZATIONS)
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
                arguments = listOf(navArgument(NavRoutes.ARG_USER_ID) { type = NavType.StringType })
            ) { backStackEntry ->
                val userId = java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString(NavRoutes.ARG_USER_ID).orEmpty(),
                    Charsets.UTF_8.name()
                )
                UserPublicProfileScreen(
                    userId = userId,
                    onNavigateBack = { navController.popBackStack() },
                    onPetClick = { id -> navController.navigate(NavRoutes.petDetail(id)) },
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
            composable(NavRoutes.MY_PETS) {
                MyPetsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPetClick = { id -> navController.navigate(NavRoutes.petDetail(id)) },
                    onAddPet = { navController.navigate(NavRoutes.ADD_PET) }
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
                arguments = listOf(navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType })
            ) { backStackEntry ->
                val petId = backStackEntry.arguments?.getString(NavRoutes.ARG_PET_ID)
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
            composable(NavRoutes.MY_ADOPTIONS) {
                MyAdoptionsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onAdoptionClick = { id -> navController.navigate(NavRoutes.adoptionDetail(id)) },
                    onCreateAdoption = { navController.navigate(NavRoutes.ADOPTION_FORM) },
                    onEditAdoption = { id -> navController.navigate(NavRoutes.adoptionFormEdit(id)) },
                    onReceivedApplications = {
                        navController.navigate(NavRoutes.RECEIVED_ADOPTION_APPLICATIONS)
                    },
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
                LostFoundMapScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(NavRoutes.LOST_FOUND) {
                LostFoundScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMap = { navController.navigate(NavRoutes.LOST_FOUND_MAP) }
                )
            }
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
                    onSubmitted = {
                        navController.navigate(NavRoutes.MY_ADOPTION_APPLICATIONS) {
                            popUpTo(NavRoutes.ADOPTIONS) { inclusive = false }
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
                    onEdit = { id -> navController.navigate(NavRoutes.fosterHomeFormEdit(id)) }
                )
            }
            composable(NavRoutes.FOSTER_HOME_FORM) {
                FosterHomeFormScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSaved = {
                        navController.navigate(NavRoutes.MY_FOSTER_HOME) {
                            popUpTo(NavRoutes.FOSTER_HOMES) { inclusive = false }
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
                    onPlacementClick = { id -> navController.navigate(NavRoutes.fosterPlacementManagement(id)) }
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
                    onShelterClick = { id -> navController.navigate(NavRoutes.shelterOpsDetail(id)) },
                    onMyShelters = { navController.navigate(NavRoutes.MY_SHELTERS) },
                    onPublicCampaigns = { navController.navigate(NavRoutes.SHELTER_PUBLIC_CAMPAIGNS) },
                    onPublicSupplyRequests = { navController.navigate(NavRoutes.SHELTER_PUBLIC_SUPPLY_REQUESTS) },
                    onPublicEmergencies = { navController.navigate(NavRoutes.SHELTER_PUBLIC_EMERGENCIES) },
                    onPublicEvents = { navController.navigate(NavRoutes.SHELTER_PUBLIC_EVENTS) }
                )
            }
            composable(NavRoutes.MY_SHELTERS) {
                MySheltersScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onShelterClick = { id -> navController.navigate(NavRoutes.shelterDashboard(id)) },
                    onCreate = { navController.navigate(NavRoutes.SHELTER_FORM) }
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
                    onMyClinics = { navController.navigate(NavRoutes.MY_VETERINARY_CLINICS) }
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
                    onNavigateBack = { navController.popBackStack() }
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
                route = NavRoutes.SHELTER_DETAIL,
                arguments = listOf(navArgument(NavRoutes.ARG_SHELTER_ID) { type = NavType.StringType })
            ) {
                ShelterDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onAdoptionClick = { id -> navController.navigate(NavRoutes.adoptionDetail(id)) }
                )
            }
            composable(
                route = NavRoutes.PET_DETAIL,
                arguments = listOf(navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType })
            ) {
                PetDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id -> navController.navigate(NavRoutes.editPet(id)) },
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
                    }
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
                    onPublishSuccess = {
                        navController.popBackStack()
                        navController.navigate(NavRoutes.HOME)
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
                    onSaved = { id ->
                        navController.popBackStack()
                        navController.navigate(NavRoutes.SUMATE)
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
                    onPublishSuccess = {
                        navController.popBackStack()
                        navController.navigate(NavRoutes.SUMATE)
                    }
                )
            }
            composable(NavRoutes.PUBLISH_FOSTER) {
                PublishFosterScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPublishSuccess = {
                        navController.popBackStack()
                        navController.navigate(NavRoutes.SUMATE)
                    }
                )
            }
            composable(NavRoutes.PUBLISH_EVENT) {
                PublishEventScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPublishSuccess = {
                        navController.popBackStack()
                        navController.navigate(NavRoutes.SUMATE)
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
                    }
                )
            }
            composable(NavRoutes.NOTIFICATION_PREFERENCES) {
                NotificationPreferencesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(NavRoutes.ADMIN_MODERATION) {
                ModerationQueueScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onReportClick = { id -> navController.navigate(NavRoutes.moderationReportDetail(id)) }
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
                    onReviewClick = { id -> navController.navigate(NavRoutes.orgVerificationReview(id)) }
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
            // M07 Etapa 6: dedicated screens — no AdministrativeAuditScreen / audit.view proxy
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
                PlatformAdminScreen(onNavigateBack = { navController.popBackStack() })
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
    }
}
