package com.comunidapp.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.FeedPostCard
import com.comunidapp.app.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onAuthorClick: (String) -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val posts by viewModel.posts.collectAsState()

    Scaffold(
        topBar = { ComunidappTopBar(title = "Inicio") }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(posts, key = { it.id }) { post ->
                FeedPostCard(post = post, onAuthorClick = onAuthorClick)
            }
        }
    }
}
