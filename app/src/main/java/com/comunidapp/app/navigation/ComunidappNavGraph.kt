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
import com.comunidapp.app.ui.components.bottomNavItems
import com.comunidapp.app.ui.screens.adoptions.AdoptionDetailScreen
import com.comunidapp.app.ui.screens.adoptions.AdoptionsScreen
import com.comunidapp.app.ui.screens.home.HomeScreen
import com.comunidapp.app.ui.screens.login.EmailVerificationScreen
import com.comunidapp.app.ui.screens.login.ForgotPasswordScreen
import com.comunidapp.app.ui.screens.login.LoginScreen
import com.comunidapp.app.ui.screens.login.RegisterScreen
import com.comunidapp.app.ui.screens.lostfound.LostFoundScreen
import com.comunidapp.app.ui.screens.pets.AddPetScreen
import com.comunidapp.app.ui.screens.pets.EditPetScreen
import com.comunidapp.app.ui.screens.pets.MyPetsScreen
import com.comunidapp.app.ui.screens.pets.PetDetailScreen
import com.comunidapp.app.ui.screens.profile.EditProfileScreen
import com.comunidapp.app.ui.screens.profile.ProfileScreen
import com.comunidapp.app.ui.screens.profile.UserPublicProfileScreen
import com.comunidapp.app.ui.screens.publish.PublishAdoptionScreen
import com.comunidapp.app.ui.screens.publish.PublishGeneralScreen
import com.comunidapp.app.ui.screens.publish.PublishLostFoundScreen
import com.comunidapp.app.ui.screens.publish.PublishScreen
import com.comunidapp.app.ui.screens.shelters.ShelterDetailScreen
import com.comunidapp.app.ui.screens.shelters.SheltersScreen
import com.comunidapp.app.viewmodel.SessionState
import com.comunidapp.app.viewmodel.SessionViewModel

@Composable
fun ComunidappNavGraph(
    sessionViewModel: SessionViewModel = viewModel()
) {
    val sessionState by sessionViewModel.sessionState.collectAsState()

    when (sessionState) {
        SessionState.Loading -> SessionLoadingScreen()
        SessionState.LoggedOut, SessionState.LoggedIn -> {
            key(sessionState) {
                RootNavHost(
                    isLoggedIn = sessionState == SessionState.LoggedIn
                )
            }
        }
    }
}

@Composable
private fun RootNavHost(isLoggedIn: Boolean) {
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
                onNavigateBack = { rootNavController.popBackStack() }
            )
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
            MainScreen()
        }
    }
}

@Composable
private fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val bottomNavRoutes = bottomNavItems.map { it.route }
    val showBottomBar = currentRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                ComunidappBottomBar(navController = navController)
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
                    }
                )
            }
            composable(NavRoutes.ADOPTIONS) {
                AdoptionsScreen(
                    onAdoptionClick = { id ->
                        navController.navigate(NavRoutes.adoptionDetail(id))
                    }
                )
            }
            composable(NavRoutes.PUBLISH) {
                PublishScreen(
                    onNavigateToGeneral = { navController.navigate(NavRoutes.PUBLISH_GENERAL) },
                    onNavigateToAdoption = { navController.navigate(NavRoutes.PUBLISH_ADOPTION) },
                    onNavigateToLostFound = { navController.navigate(NavRoutes.PUBLISH_LOST_FOUND) }
                )
            }
            composable(NavRoutes.SHELTERS) {
                SheltersScreen(
                    onShelterClick = { id ->
                        navController.navigate(NavRoutes.shelterDetail(id))
                    }
                )
            }
            composable(NavRoutes.PROFILE) {
                ProfileScreen(
                    onNavigateToEditProfile = { navController.navigate(NavRoutes.EDIT_PROFILE) },
                    onNavigateToMyPets = { navController.navigate(NavRoutes.MY_PETS) },
                    onNavigateToLostFound = { navController.navigate(NavRoutes.LOST_FOUND) },
                    onPetClick = { id -> navController.navigate(NavRoutes.petDetail(id)) }
                )
            }
            composable(NavRoutes.EDIT_PROFILE) {
                EditProfileScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSaveSuccess = { navController.popBackStack() }
                )
            }
            composable(
                route = NavRoutes.USER_PROFILE,
                arguments = listOf(navArgument(NavRoutes.ARG_USER_ID) { type = NavType.StringType })
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString(NavRoutes.ARG_USER_ID) ?: ""
                UserPublicProfileScreen(
                    userId = userId,
                    onNavigateBack = { navController.popBackStack() },
                    onPetClick = { id -> navController.navigate(NavRoutes.petDetail(id)) }
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
                AddPetScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSaveSuccess = {
                        navController.popBackStack()
                    }
                )
            }
            composable(
                route = NavRoutes.EDIT_PET,
                arguments = listOf(navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType })
            ) {
                EditPetScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSaveSuccess = { navController.popBackStack() },
                    onDeleteSuccess = {
                        navController.popBackStack()
                        navController.popBackStack()
                    }
                )
            }
            composable(NavRoutes.LOST_FOUND) {
                LostFoundScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(
                route = NavRoutes.ADOPTION_DETAIL,
                arguments = listOf(navArgument(NavRoutes.ARG_ADOPTION_ID) { type = NavType.StringType })
            ) {
                AdoptionDetailScreen(onNavigateBack = { navController.popBackStack() })
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
            composable(NavRoutes.PUBLISH_ADOPTION) {
                PublishAdoptionScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPublishSuccess = {
                        navController.popBackStack()
                        navController.navigate(NavRoutes.ADOPTIONS)
                    }
                )
            }
            composable(NavRoutes.PUBLISH_LOST_FOUND) {
                PublishLostFoundScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPublishSuccess = {
                        navController.popBackStack()
                        navController.navigate(NavRoutes.LOST_FOUND)
                    }
                )
            }
        }
    }
}
