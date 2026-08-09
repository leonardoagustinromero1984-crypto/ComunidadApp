package com.comunidapp.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.ui.components.CommentsBottomSheet
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoSocialPostCard
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAuthorClick: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onNavigateToPublish: () -> Unit = {},
    onNavigateToCreateStory: () -> Unit = {},
    onNavigateToSumate: () -> Unit = {},
    onNavigateToLostFound: () -> Unit = {},
    onNavigateToComunidad: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val posts by viewModel.posts.collectAsState()
    val nearbyUsers by viewModel.nearbyUsers.collectAsState()
    val likedIds by viewModel.likedPostIds.collectAsState()
    val savedIds by viewModel.savedPostIds.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val commentsPostId by viewModel.commentsPostId.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var socialTab by remember { mutableStateOf(HomeSocialTab.Feed) }
    var audience by remember { mutableStateOf(FeedAudience.ForYou) }
    var exploreQuery by remember { mutableStateOf("") }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        containerColor = BrandCream,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                SocialHomeTopBar(
                    onSearch = onNavigateToSearch,
                    onNotifications = onNavigateToNotifications,
                    onMessages = onNavigateToMessages
                )
                HomeSocialTabRow(selected = socialTab, onSelect = { socialTab = it })
            }
        }
    ) { padding ->
        when (socialTab) {
            HomeSocialTab.Feed -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LeoDimens.SpaceMd),
                        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
                    ) {
                        item {
                            StoriesRow(
                                onAddStory = onNavigateToCreateStory,
                                stories = posts
                                    .filter { it.type == PostType.STORY && it.isActiveStory() }
                                    .map { post ->
                                        StoryUiItem(
                                            id = post.id,
                                            name = post.authorName,
                                            imageUrl = post.imageUrl,
                                            hasNew = true
                                        )
                                    },
                                modifier = Modifier.padding(top = LeoDimens.SpaceSm)
                            )
                        }
                        item {
                            FeedAudienceSelector(selected = audience, onSelect = { audience = it })
                        }
                        when {
                            audience == FeedAudience.Following -> {
                                item {
                                    LeoEmptyState(
                                        title = "Tu feed de siguiendo está vacío",
                                        message = "Cuando sigas cuentas, sus publicaciones aparecerán aquí.",
                                        actionLabel = "Explorar",
                                        onAction = { socialTab = HomeSocialTab.Explore },
                                        icon = Icons.Default.PostAdd
                                    )
                                }
                            }
                            posts.isEmpty() -> {
                                item {
                                    LeoEmptyState(
                                        title = "Tu comunidad todavía está tranquila",
                                        message = "Sé la primera persona en compartir una foto o novedad.",
                                        actionLabel = "Crear publicación",
                                        onAction = onNavigateToPublish,
                                        icon = Icons.Default.PostAdd
                                    )
                                }
                            }
                            else -> {
                                val feedPosts = posts.filter {
                                    it.type != PostType.STORY && !it.isExpired()
                                }
                                itemsIndexed(feedPosts, key = { _, p -> p.id }) { index, post ->
                                    LeoSocialPostCard(
                                        post = post,
                                        isLiked = likedIds.contains(post.id),
                                        isSaved = savedIds.contains(post.id),
                                        onAuthorClick = onAuthorClick,
                                        onLikeClick = { viewModel.toggleLike(post.id) },
                                        onCommentClick = { viewModel.openComments(post.id) },
                                        onShareClick = {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Compartir próximamente")
                                            }
                                        },
                                        onSaveClick = { viewModel.toggleSave(post.id) },
                                        onReportClick = { viewModel.reportPost(post.id) },
                                        onBlockClick = { viewModel.blockAuthor(post.authorId) },
                                        onSpecialCta = when (post.type) {
                                            PostType.ADOPTION -> onNavigateToSumate
                                            PostType.LOST_FOUND, PostType.URGENT -> onNavigateToLostFound
                                            else -> null
                                        },
                                        modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd)
                                    )
                                    if (index == feedPosts.lastIndex && hasMore) {
                                        LaunchedEffect(post.id) { viewModel.loadMore() }
                                    }
                                }
                                if (!hasMore) {
                                    item {
                                        Text(
                                            text = "Llegaste al final del feed",
                                            style = LeoCaption,
                                            color = MutedText,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(LeoDimens.SpaceMd)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HomeSocialTab.Reels -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                    HomeReelsTab(
                        posts = posts.filter { it.type == PostType.REEL && !it.isExpired() },
                        onAuthorClick = onAuthorClick
                    )
                }
            }
            HomeSocialTab.Explore -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                    val filtered = if (exploreQuery.isBlank()) {
                        posts
                    } else {
                        posts.filter {
                            it.title.contains(exploreQuery, true) ||
                                it.content.contains(exploreQuery, true) ||
                                it.authorName.contains(exploreQuery, true)
                        }
                    }
                    HomeExploreTab(
                        posts = filtered,
                        suggestedUsers = nearbyUsers,
                        searchQuery = exploreQuery,
                        onSearchChange = { exploreQuery = it },
                        onOpenSearch = onNavigateToSearch,
                        onPostClick = { viewModel.openComments(it.id) },
                        onUserClick = onAuthorClick
                    )
                }
            }
        }
    }

    if (commentsPostId != null) {
        CommentsBottomSheet(
            comments = comments,
            onDismiss = viewModel::closeComments,
            onSendComment = viewModel::sendComment
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF6EA, widthDp = 390, name = "SocialHomeEmptyPreview")
@Composable
private fun SocialHomeEmptyPreview() {
    ComunidappTheme {
        Column {
            SocialHomeTopBar(onSearch = {}, onNotifications = {}, onMessages = {})
            HomeSocialTabRow(selected = HomeSocialTab.Feed, onSelect = {})
            LeoEmptyState(
                title = "Tu comunidad todavía está tranquila",
                message = "Sé la primera persona en compartir una foto o novedad.",
                actionLabel = "Crear publicación",
                onAction = {},
                icon = Icons.Default.PostAdd
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF6EA, widthDp = 390, name = "SocialHomeFeedPreview")
@Composable
private fun SocialHomeFeedPreview() {
    ComunidappTheme {
        Column {
            SocialHomeTopBar(onSearch = {}, onNotifications = {}, onMessages = {})
            HomeSocialTabRow(selected = HomeSocialTab.Feed, onSelect = {})
            StoriesRow(onAddStory = {})
            FeedAudienceSelector(selected = FeedAudience.ForYou, onSelect = {})
            LeoSocialPostCard(
                post = FeedPost(
                    id = "1",
                    authorId = "u",
                    authorName = "Leo",
                    type = PostType.GENERAL,
                    title = "Primer paseo",
                    content = "Con Toby en la plaza #perros",
                    likeCount = 10,
                    commentCount = 2,
                    date = "Hoy"
                ),
                modifier = Modifier.padding(LeoDimens.SpaceMd)
            )
        }
    }
}
