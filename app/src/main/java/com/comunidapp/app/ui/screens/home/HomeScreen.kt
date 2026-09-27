package com.comunidapp.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.domain.social.StoryTrayGrouping
import com.comunidapp.app.ui.components.CommentsBottomSheet
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoSocialPostCard
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.ui.theme.VisualDirectionPilot
import com.comunidapp.app.ui.theme.leoVisual
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
    onOpenStoryViewer: (String) -> Unit = {},
    onNavigateToSumate: () -> Unit = {},
    onNavigateToLostFound: () -> Unit = {},
    onNavigateToFound: () -> Unit = {},
    onNavigateToComunidad: () -> Unit = {},
    onNavigateToMyPets: () -> Unit = {},
    onNavigateToPetDetail: (String) -> Unit = {},
    onNavigateToAddPet: () -> Unit = {},
    onPostClick: (String) -> Unit = {},
    onOpenClipViewer: (String) -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val posts by viewModel.posts.collectAsState()
    val stories by viewModel.stories.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val greetingName by viewModel.greetingName.collectAsState()
    val avatarDisplayUrl by viewModel.avatarDisplayUrl.collectAsState()
    val likedIds by viewModel.likedPostIds.collectAsState()
    val savedIds by viewModel.savedPostIds.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val commentsPostId by viewModel.commentsPostId.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    val storiesTray = stories.filter { it.isActiveStory() }
    val feedPosts = com.comunidapp.app.domain.social.SocialFeedComposition.visibleSocialItems(posts)
    val publications = com.comunidapp.app.domain.social.SocialFeedComposition.publications(feedPosts)
    val clips = com.comunidapp.app.domain.social.SocialFeedComposition.clips(feedPosts)
    val ownStories = storiesTray.filter { it.authorId == currentUser?.id }
    val otherStories = storiesTray.filter { it.authorId != currentUser?.id }
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeContext by com.comunidapp.app.domain.context.OperationalContextProvider.active.collectAsState()
    val homeContextLabel = com.comunidapp.app.domain.context.ContextHumanLabels.homeBrandLine(activeContext)
    var sharePost by remember { mutableStateOf<FeedPost?>(null) }
    var shareInternal by remember { mutableStateOf<FeedPost?>(null) }
    val location = currentUser?.locationText?.takeIf { it.isNotBlank() }
        ?: currentUser?.city?.takeIf { it.isNotBlank() }

    VisualDirectionPilot {
    Scaffold(
        containerColor = leoVisual().background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HomePersonaHeader(
                greetingName = greetingName,
                avatarUrl = null,
                locationText = location,
                onNotifications = onNavigateToNotifications,
                onMessages = onNavigateToMessages,
                contextLabel = homeContextLabel
            )
        }
    ) { padding ->
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
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item(key = "stories") {
                    StoriesRow(
                        onAddStory = onNavigateToCreateStory,
                        onOwnStoryClick = {
                            if (ownStories.isNotEmpty()) {
                                onOpenStoryViewer(currentUser?.id.orEmpty())
                            } else {
                                onNavigateToCreateStory()
                            }
                        },
                        ownHasActive = ownStories.isNotEmpty(),
                        ownAvatarUrl = avatarDisplayUrl,
                        stories = StoryTrayGrouping.groupByAuthor(otherStories).map { tray ->
                            StoryUiItem(
                                id = tray.authorId,
                                name = tray.authorName,
                                imageUrl = tray.imageUrl,
                                hasNew = true,
                                onClick = { onOpenStoryViewer(tray.authorId) }
                            )
                        }
                    )
                }
                when {
                    publications.isEmpty() && clips.isEmpty() -> {
                        item(key = "empty_feed") {
                            LeoEmptyState(
                                title = "Tu comunidad empieza acá",
                                message = "Cuando vos o personas que seguís compartan, las publicaciones aparecen aquí.",
                                actionLabel = "Crear publicación",
                                onAction = onNavigateToPublish,
                                icon = Icons.Default.PostAdd
                            )
                        }
                    }
                    else -> {
                        if (clips.isNotEmpty() && publications.isEmpty()) {
                            item(key = "clips_only") {
                                ClipsCarousel(clips = clips, onClipClick = onOpenClipViewer)
                            }
                        }
                        itemsIndexed(publications, key = { _, p -> p.id }) { index, post ->
                            LeoSocialPostCard(
                                post = post,
                                isLiked = likedIds.contains(post.id),
                                isSaved = savedIds.contains(post.id),
                                onAuthorClick = onAuthorClick,
                                onLikeClick = { viewModel.toggleLike(post.id) },
                                onCommentClick = { onPostClick(post.id) },
                                onShareClick = { sharePost = post },
                                onSaveClick = { viewModel.toggleSave(post.id) },
                                onReportClick = { viewModel.reportPost(post.id) },
                                onBlockClick = { viewModel.blockAuthor(post.authorId) },
                                onPostClick = { onPostClick(post.id) },
                                onSpecialCta = when (post.type) {
                                    PostType.ADOPTION -> onNavigateToSumate
                                    PostType.LOST_FOUND, PostType.URGENT -> ({ onPostClick(post.id) })
                                    else -> null
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (index == 0 && clips.isNotEmpty()) {
                                ClipsCarousel(
                                    clips = clips,
                                    onClipClick = onOpenClipViewer,
                                    modifier = Modifier.padding(top = LeoDimens.SpaceSm)
                                )
                            }
                            if (index == publications.lastIndex && hasMore) {
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

    if (commentsPostId != null) {
        CommentsBottomSheet(
            comments = comments,
            onDismiss = viewModel::closeComments,
            onSendComment = viewModel::sendComment,
            currentUserId = currentUser?.id,
            onDeleteOwn = viewModel::deleteOwnComment
        )
    }
    sharePost?.let { post ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { sharePost = null },
            title = { Text("Compartir") },
            text = { Text("Elegí cómo compartir este contenido.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    shareInternal = post
                    sharePost = null
                }) { Text("Enviar por LeoVer") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val kind = when (post.type) {
                        PostType.REEL -> com.comunidapp.app.domain.social.SocialContentKind.REEL
                        PostType.STORY -> com.comunidapp.app.domain.social.SocialContentKind.STORY
                        else -> com.comunidapp.app.domain.social.SocialContentKind.POST
                    }
                    val text = com.comunidapp.app.domain.social.SocialShare.shareText(kind, post.authorName, post.id)
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(android.content.Intent.createChooser(send, "Compartir en LeoVer"))
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                            "LeoVer",
                            com.comunidapp.app.domain.social.SocialShare.deepLink(kind, post.id)
                        )
                    )
                    sharePost = null
                }) { Text("Otras apps") }
            }
        )
    }
    shareInternal?.let { post ->
        com.comunidapp.app.ui.screens.social.InternalShareSheet(
            post = post,
            onDismiss = { shareInternal = null },
            onShared = { shareInternal = null }
        )
    }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBF8, widthDp = 390, name = "SocialHomeEmptyPreview")
@Composable
private fun SocialHomeEmptyPreview() {
    ComunidappTheme {
        Column {
            HomePersonaHeader(
                greetingName = "Leonardo",
                avatarUrl = null,
                locationText = "Buenos Aires",
                onNotifications = {},
                onMessages = {}
            )
            LeoEmptyState(
                title = "Tu comunidad empieza acá",
                message = "Cuando vos o personas que seguís compartan, las publicaciones aparecen aquí.",
                actionLabel = "Crear publicación",
                onAction = {},
                icon = Icons.Default.PostAdd
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBF8, widthDp = 390, name = "SocialHomeFeedPreview")
@Composable
private fun SocialHomeFeedPreview() {
    ComunidappTheme {
        Column {
            HomePersonaHeader(
                greetingName = "Leo",
                avatarUrl = null,
                locationText = null,
                onNotifications = {},
                onMessages = {}
            )
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
