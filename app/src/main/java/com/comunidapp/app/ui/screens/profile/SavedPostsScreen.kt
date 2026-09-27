package com.comunidapp.app.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoSocialPostCard
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.viewmodel.SavedPostsViewModel

@Composable
fun SavedPostsScreen(
    onNavigateBack: () -> Unit,
    onPostClick: (String) -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    viewModel: SavedPostsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Guardados",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Text(
                "Publicaciones y clips",
                style = LeoCaption,
                color = MutedText,
                modifier = Modifier.padding(
                    horizontal = LeoDimens.SpaceMd,
                    vertical = LeoDimens.SpaceSm
                )
            )
            if (uiState.posts.isEmpty()) {
                LeoEmptyState(
                    title = "Todavía no guardaste nada",
                    message = "Cuando guardes una publicación o reel, lo vas a ver acá."
                )
            } else {
                LazyColumn {
                    items(uiState.posts, key = { it.id }) { post ->
                        LeoSocialPostCard(
                            post = post,
                            isLiked = post.id in uiState.likedPostIds,
                            isSaved = true,
                            onAuthorClick = onAuthorClick,
                            onLikeClick = { viewModel.toggleLike(post.id) },
                            onSaveClick = { viewModel.toggleSave(post.id) },
                            onPostClick = { onPostClick(post.id) },
                            onCommentClick = { onPostClick(post.id) }
                        )
                    }
                }
            }
        }
    }
}
