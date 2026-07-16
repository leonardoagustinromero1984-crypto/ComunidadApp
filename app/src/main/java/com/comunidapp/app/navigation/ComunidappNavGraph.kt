package com.comunidapp.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
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
import com.comunidapp.app.ui.components.ComunidappBottomBar
import com.comunidapp.app.ui.components.SessionLoadingScreen
import com.comunidapp.app.ui.components.bottomNavItemsFor
import com.comunidapp.app.ui.screens.admin.AdministrativeAuditScreen
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
import com.comunidapp.app.ui.screens.adoptions.AdoptionDetailScreen
import com.comunidapp.app.ui.screens.adoptions.MyAdoptionsScreen
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
import com.comunidapp.app.ui.screens.pets.PetDetailScreen
import com.comunidapp.app.ui.screens.profile.EditProfileScreen
import com.comunidapp.app.ui.screens.profile.FriendRequestsScreen
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
import com.comunidapp.app.ui.screens.publish.PublishAdoptionScreen
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
import com.comunidapp.app.ui.screens.shelters.ShelterDetailScreen
import com.comunidapp.app.ui.screens.sumate.SumateScreen
import com.comunidapp.app.viewmodel.PetFormViewModel
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
                    onNavigateToMap = { navController.navigate(NavRoutes.LOST_FOUND_MAP) }
                )
            }
            composable(NavRoutes.PUBLISH) {
                PublishScreen(
                    accountType = accountType,
                    onNavigateToGeneral = { navController.navigate(NavRoutes.PUBLISH_GENERAL) },
                    onNavigateToQuestion = { navController.navigate(NavRoutes.PUBLISH_QUESTION) },
                    onNavigateToPromo = { navController.navigate(NavRoutes.PUBLISH_PROMO) },
                    onNavigateToAdoption = { navController.navigate(NavRoutes.PUBLISH_ADOPTION) },
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
                    onAdoptionClick = { id -> navController.navigate(NavRoutes.adoptionDetail(id)) }
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
                    onMessagePublisher = { publisherId, publisherName ->
                        navController.navigate(NavRoutes.chatStart(publisherId, publisherName))
                    }
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
                    onDeleteSuccess = { navController.popBackStack() }
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
            composable(NavRoutes.PUBLISH_ADOPTION) {
                PublishAdoptionScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPublishSuccess = {
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
                NotificationsScreen(onNavigateBack = { navController.popBackStack() })
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
