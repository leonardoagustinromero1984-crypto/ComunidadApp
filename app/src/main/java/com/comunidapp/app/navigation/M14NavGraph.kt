package com.comunidapp.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.comunidapp.app.ui.screens.m14.M14CredentialCreateScreen
import com.comunidapp.app.ui.screens.m14.M14CredentialDetailScreen
import com.comunidapp.app.ui.screens.m14.M14CredentialsScreen
import com.comunidapp.app.ui.screens.m14.M14IssueVerifiedCredentialScreen
import com.comunidapp.app.ui.screens.m14.M14ManagedVerificationsScreen
import com.comunidapp.app.ui.screens.m14.M14PassportEditScreen
import com.comunidapp.app.ui.screens.m14.M14PassportHistoryScreen
import com.comunidapp.app.ui.screens.m14.M14PassportListScreen
import com.comunidapp.app.ui.screens.m14.M14PassportShareScreen
import com.comunidapp.app.ui.screens.m14.M14PetPassportScreen
import com.comunidapp.app.ui.screens.m14.M14PublicPassportScreen
import com.comunidapp.app.ui.screens.m14.M14RevokeCredentialScreen
import com.comunidapp.app.ui.screens.m14.M14VerificationDetailScreen
import com.comunidapp.app.ui.screens.m14.M14VerificationPrepScreen
import java.nio.charset.StandardCharsets

/** M14 passport / credential / verification routes (extracted to keep NavHost IR smaller). */
fun NavGraphBuilder.m14PassportRoutes(navController: NavHostController) {
    composable(NavRoutes.M14_PASSPORTS) {
        M14PassportListScreen(
            onNavigateBack = { navController.popBackStack() },
            onPassportClick = { petId ->
                navController.navigate(NavRoutes.m14PetPassport(petId))
            }
        )
    }
    composable(
        route = NavRoutes.M14_PET_PASSPORT,
        arguments = listOf(navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType })
    ) { entry ->
        val petId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_PET_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M14PetPassportScreen(
            petId = petId,
            onNavigateBack = { navController.popBackStack() },
            onEdit = { id -> navController.navigate(NavRoutes.m14PetPassportEdit(id)) },
            onCredentials = { id ->
                navController.navigate(NavRoutes.m14PassportCredentials(id))
            },
            onVerification = { id ->
                navController.navigate(NavRoutes.m14PassportVerification(id))
            },
            onShare = { id -> navController.navigate(NavRoutes.m14PassportShare(id)) },
            onHistory = { id -> navController.navigate(NavRoutes.m14PassportHistory(id)) },
            onManagedVerifications = {
                navController.navigate(NavRoutes.M14_VERIFICATIONS_MANAGED)
            },
            onPublic = { code -> navController.navigate(NavRoutes.m14Public(code)) }
        )
    }
    composable(
        route = NavRoutes.M14_PET_PASSPORT_EDIT,
        arguments = listOf(navArgument(NavRoutes.ARG_PET_ID) { type = NavType.StringType })
    ) { entry ->
        val petId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_PET_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M14PassportEditScreen(
            petId = petId,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.M14_PASSPORT_CREDENTIALS,
        arguments = listOf(
            navArgument(NavRoutes.ARG_PASSPORT_ID) { type = NavType.StringType }
        )
    ) { entry ->
        val passportId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_PASSPORT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M14CredentialsScreen(
            passportId = passportId,
            onNavigateBack = { navController.popBackStack() },
            onCredentialClick = { id ->
                navController.navigate(NavRoutes.m14CredentialDetail(id))
            },
            onCreate = {
                navController.navigate(NavRoutes.m14PassportCredentialNew(passportId))
            },
            onIssueVerified = {
                navController.navigate(NavRoutes.m14PassportCredentialIssue(passportId))
            }
        )
    }
    composable(
        route = NavRoutes.M14_PASSPORT_CREDENTIAL_NEW,
        arguments = listOf(
            navArgument(NavRoutes.ARG_PASSPORT_ID) { type = NavType.StringType }
        )
    ) { entry ->
        val passportId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_PASSPORT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M14CredentialCreateScreen(
            passportId = passportId,
            onNavigateBack = { navController.popBackStack() },
            onCreated = { id ->
                navController.navigate(NavRoutes.m14CredentialDetail(id)) {
                    popUpTo(NavRoutes.m14PassportCredentials(passportId)) {
                        inclusive = false
                    }
                }
            }
        )
    }
    composable(
        route = NavRoutes.M14_CREDENTIAL_DETAIL,
        arguments = listOf(
            navArgument(NavRoutes.ARG_CREDENTIAL_ID) { type = NavType.StringType }
        )
    ) { entry ->
        val credentialId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CREDENTIAL_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M14CredentialDetailScreen(
            credentialId = credentialId,
            onNavigateBack = { navController.popBackStack() },
            onRevoke = { id -> navController.navigate(NavRoutes.m14CredentialRevoke(id)) }
        )
    }
    composable(
        route = NavRoutes.M14_PASSPORT_VERIFICATION,
        arguments = listOf(
            navArgument(NavRoutes.ARG_PASSPORT_ID) { type = NavType.StringType }
        )
    ) { entry ->
        val passportId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_PASSPORT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M14VerificationPrepScreen(
            passportId = passportId,
            onNavigateBack = { navController.popBackStack() },
            onRequestClick = { id ->
                navController.navigate(NavRoutes.m14VerificationDetail(id))
            }
        )
    }
    composable(
        route = NavRoutes.M14_PUBLIC,
        arguments = listOf(
            navArgument(NavRoutes.ARG_PUBLIC_CODE) { type = NavType.StringType }
        )
    ) { entry ->
        val publicCode = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_PUBLIC_CODE).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M14PublicPassportScreen(
            publicCode = publicCode,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.M14_VERIFICATIONS_MANAGED) {
        M14ManagedVerificationsScreen(
            onNavigateBack = { navController.popBackStack() },
            onRequestClick = { id -> navController.navigate(NavRoutes.m14VerificationDetail(id)) }
        )
    }
    composable(
        route = NavRoutes.M14_VERIFICATION_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_M14_REQUEST_ID) { type = NavType.StringType })
    ) { entry ->
        val requestId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M14_REQUEST_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M14VerificationDetailScreen(
            requestId = requestId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.M14_PASSPORT_CREDENTIAL_ISSUE,
        arguments = listOf(navArgument(NavRoutes.ARG_PASSPORT_ID) { type = NavType.StringType })
    ) { entry ->
        val passportId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_PASSPORT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M14IssueVerifiedCredentialScreen(
            passportId = passportId,
            onNavigateBack = { navController.popBackStack() },
            onCreated = { id ->
                navController.navigate(NavRoutes.m14CredentialDetail(id)) {
                    popUpTo(NavRoutes.m14PassportCredentials(passportId)) {
                        inclusive = false
                    }
                }
            }
        )
    }
    composable(
        route = NavRoutes.M14_CREDENTIAL_REVOKE,
        arguments = listOf(navArgument(NavRoutes.ARG_CREDENTIAL_ID) { type = NavType.StringType })
    ) { entry ->
        val credentialId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_CREDENTIAL_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M14RevokeCredentialScreen(
            credentialId = credentialId,
            onNavigateBack = { navController.popBackStack() },
            onDone = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.M14_PASSPORT_SHARE,
        arguments = listOf(navArgument(NavRoutes.ARG_PASSPORT_ID) { type = NavType.StringType })
    ) { entry ->
        val passportId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_PASSPORT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M14PassportShareScreen(
            passportId = passportId,
            onNavigateBack = { navController.popBackStack() },
            onPublic = { code -> navController.navigate(NavRoutes.m14Public(code)) }
        )
    }
    composable(
        route = NavRoutes.M14_PASSPORT_HISTORY,
        arguments = listOf(navArgument(NavRoutes.ARG_PASSPORT_ID) { type = NavType.StringType })
    ) { entry ->
        val passportId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_PASSPORT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M14PassportHistoryScreen(
            passportId = passportId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
}
