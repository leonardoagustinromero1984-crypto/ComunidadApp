package com.comunidapp.app.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.social.SocialFeedComposition
import com.comunidapp.app.ui.screens.home.HomeReelsTab
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.LeoDimens

@Composable
fun ClipViewerScreen(
    startPostId: String,
    onNavigateBack: () -> Unit,
    onAuthorClick: (String) -> Unit = {},
    onComment: (String) -> Unit = {}
) {
    val posts by DataProvider.feedRepository.observeFeedPosts().collectAsState()
    val clips = remember(posts, startPostId) {
        val partitioned = SocialFeedComposition.clips(posts)
        if (partitioned.any { it.id == startPostId }) partitioned
        else posts.filter { it.id == startPostId }.ifEmpty { partitioned }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HomeReelsTab(
            posts = clips,
            startPostId = startPostId,
            onAuthorClick = onAuthorClick,
            onComment = onComment,
            modifier = Modifier.fillMaxSize()
        )
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(LeoDimens.SpaceXs)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = BrandWhite
            )
        }
    }
}
