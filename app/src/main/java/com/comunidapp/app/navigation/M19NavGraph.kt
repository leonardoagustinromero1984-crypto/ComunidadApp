package com.comunidapp.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.comunidapp.app.ui.screens.m19.M19PostDetailScreen
import com.comunidapp.app.ui.screens.m19.M19PostEditScreen
import com.comunidapp.app.ui.screens.m19.M19PostsManageScreen
import com.comunidapp.app.ui.screens.m19.M19SocialFeedScreen
import java.nio.charset.StandardCharsets

/** M19 red social — rutas Bloque 1 (fundación local/mock). */
fun NavGraphBuilder.m19SocialRoutes(navController: NavHostController) {
    composable(NavRoutes.M19_FEED) {
        M19SocialFeedScreen(
            onNavigateBack = { navController.popBackStack() },
            onPostClick = { id -> navController.navigate(NavRoutes.m19PostDetail(id)) },
            onManage = { navController.navigate(NavRoutes.M19_POSTS_MANAGE) },
            onCreate = { navController.navigate(NavRoutes.M19_POSTS_CREATE) }
        )
    }
    composable(
        route = NavRoutes.M19_POST_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_M19_POST_ID) { type = NavType.StringType })
    ) { entry ->
        val postId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M19_POST_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M19PostDetailScreen(
            postId = postId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.M19_POSTS_MANAGE) {
        M19PostsManageScreen(
            onNavigateBack = { navController.popBackStack() },
            onEditPost = { id -> navController.navigate(NavRoutes.m19PostEdit(id)) },
            onCreate = { navController.navigate(NavRoutes.M19_POSTS_CREATE) }
        )
    }
    composable(NavRoutes.M19_POSTS_CREATE) {
        M19PostEditScreen(
            postId = null,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { id ->
                navController.popBackStack()
                navController.navigate(NavRoutes.m19PostDetail(id))
            }
        )
    }
    composable(
        route = NavRoutes.M19_POST_EDIT,
        arguments = listOf(navArgument(NavRoutes.ARG_M19_POST_ID) { type = NavType.StringType })
    ) { entry ->
        val postId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M19_POST_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M19PostEditScreen(
            postId = postId,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() }
        )
    }
}
