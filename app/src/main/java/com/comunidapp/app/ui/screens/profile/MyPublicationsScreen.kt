package com.comunidapp.app.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoSocialPostCard
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.ProfileViewModel

@Composable
fun MyPublicationsScreen(
    onNavigateBack: () -> Unit,
    onAuthorClick: (String) -> Unit = {},
    onPostClick: (String) -> Unit = {},
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BrandBackground,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            LeoTopAppBar(
                title = "Mis publicaciones",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        if (uiState.posts.isEmpty()) {
            LeoEmptyState(
                title = "Todavía no publicaste",
                message = "Lo que compartas en Inicio aparece acá.",
                icon = Icons.Default.PostAdd,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(
                    bottom = padding.calculateBottomPadding() + LeoDimens.SpaceMd
                )
            ) {
                items(uiState.posts, key = { it.id }) { post ->
                    LeoSocialPostCard(
                        post = post,
                        onAuthorClick = onAuthorClick,
                        onPostClick = { onPostClick(post.id) }
                    )
                }
            }
        }
    }
}
