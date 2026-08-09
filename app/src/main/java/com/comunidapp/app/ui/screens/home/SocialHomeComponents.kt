package com.comunidapp.app.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.comunidapp.app.R
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoSearchBar
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.BrandOrange
import com.comunidapp.app.ui.theme.BrandOrangeContainer
import com.comunidapp.app.ui.theme.BrandOrangeSoft
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.ui.theme.NeutralBorder

enum class HomeSocialTab { Feed, Reels, Explore }
enum class FeedAudience { ForYou, Following }

@Composable
fun SocialHomeTopBar(
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onMessages: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
        color = BrandWhite,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = LeoDimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Image(
                painter = painterResource(R.drawable.leover_logo_horizontal),
                contentDescription = stringResource(R.string.brand_name),
                modifier = Modifier
                    .height(28.dp)
                    .width(120.dp),
                contentScale = ContentScale.Fit
            )
            Row {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Buscar", tint = BrandText)
                }
                IconButton(onClick = onNotifications) {
                    Icon(Icons.Default.Notifications, contentDescription = "Notificaciones", tint = BrandText)
                }
                IconButton(onClick = onMessages) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Mensajes", tint = BrandText)
                }
            }
        }
    }
}

@Composable
fun HomeSocialTabRow(
    selected: HomeSocialTab,
    onSelect: (HomeSocialTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BrandWhite)
            .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
    ) {
        listOf(
            HomeSocialTab.Feed to "Feed",
            HomeSocialTab.Reels to "Reels",
            HomeSocialTab.Explore to "Explorar"
        ).forEach { (tab, label) ->
            LeoFilterChip(
                label = label,
                selected = selected == tab,
                onClick = { onSelect(tab) }
            )
        }
    }
}

@Composable
fun FeedAudienceSelector(
    selected: FeedAudience,
    onSelect: (FeedAudience) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
    ) {
        LeoFilterChip(
            label = "Para vos",
            selected = selected == FeedAudience.ForYou,
            onClick = { onSelect(FeedAudience.ForYou) }
        )
        LeoFilterChip(
            label = "Siguiendo",
            selected = selected == FeedAudience.Following,
            onClick = { onSelect(FeedAudience.Following) }
        )
    }
}

@Composable
fun StoriesRow(
    onAddStory: () -> Unit,
    modifier: Modifier = Modifier,
    stories: List<StoryUiItem> = emptyList()
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = LeoDimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
    ) {
        item {
            StoryBubble(
                label = "Tu historia",
                imageUrl = null,
                isAdd = true,
                hasNew = false,
                onClick = onAddStory
            )
        }
        items(stories, key = { it.id }) { story ->
            StoryBubble(
                label = story.name,
                imageUrl = story.imageUrl,
                isAdd = false,
                hasNew = story.hasNew,
                onClick = story.onClick
            )
        }
    }
}

data class StoryUiItem(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val hasNew: Boolean,
    val onClick: () -> Unit = {}
)

@Composable
private fun StoryBubble(
    label: String,
    imageUrl: String?,
    isAdd: Boolean,
    hasNew: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .border(
                        width = 2.dp,
                        brush = if (hasNew || isAdd) {
                            Brush.linearGradient(listOf(BrandOrangeSoft, BrandOrange))
                        } else {
                            Brush.linearGradient(listOf(NeutralBorder, NeutralBorder))
                        },
                        shape = CircleShape
                    )
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(BrandCream),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl != null) {
                    PetImage(
                        imageUrl = imageUrl,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 32.dp,
                        contentDescription = label
                    )
                } else {
                    Icon(Icons.Default.Pets, null, tint = BrandOrangeSoft)
                }
            }
            if (isAdd) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(BrandOrangeSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar historia", tint = BrandText, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(
            text = label,
            style = LeoCaption,
            color = BrandText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun HomeReelsTab(
    posts: List<FeedPost>,
    modifier: Modifier = Modifier,
    onAuthorClick: (String) -> Unit = {}
) {
    if (posts.isEmpty()) {
        LeoEmptyState(
            title = "Todavía no hay Reels",
            message = "Cuando haya videos cortos, van a aparecer aquí. La reproducción nativa queda pendiente.",
            icon = Icons.Default.PlayArrow,
            modifier = modifier.fillMaxSize()
        )
        return
    }
    val pagerState = rememberPagerState(pageCount = { posts.size })
    VerticalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { page ->
        val post = posts[page]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BrandCream),
                contentAlignment = Alignment.Center
            ) {
                if (post.imageUrl != null) {
                    PetImage(
                        imageUrl = post.imageUrl,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = post.title
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, null, tint = BrandOrangeSoft, modifier = Modifier.size(72.dp))
                }
                Text(
                    text = "Reproducción pendiente",
                    style = LeoCaption,
                    color = BrandText,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(BrandWhite.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(LeoDimens.SpaceMd)
                    .fillMaxWidth(0.7f)
            ) {
                Text(
                    text = post.authorName,
                    style = LeoCardTitle,
                    color = BrandWhite,
                    modifier = Modifier.clickable { onAuthorClick(post.authorId) }
                )
                Text(
                    text = post.content.ifBlank { post.title },
                    style = LeoCaption,
                    color = BrandWhite,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = LeoDimens.SpaceSm)
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = BrandWhite, modifier = Modifier.size(16.dp))
                    Text(" Audio original", style = LeoCaption, color = BrandWhite)
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(LeoDimens.SpaceMd),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceMd)
            ) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = "Me gusta", tint = BrandWhite)
                Icon(Icons.Default.Share, contentDescription = "Compartir", tint = BrandWhite)
            }
        }
    }
}

@Composable
fun HomeExploreTab(
    posts: List<FeedPost>,
    suggestedUsers: List<User>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onPostClick: (FeedPost) -> Unit,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        LeoSearchBar(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = "Buscar personas, mascotas o #hashtags",
            modifier = Modifier.padding(LeoDimens.SpaceMd)
        )
        Row(
            modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            LeoFilterChip(label = "Personas", selected = false, onClick = onOpenSearch)
            LeoFilterChip(label = "Mascotas", selected = false, onClick = onOpenSearch)
            LeoFilterChip(label = "Tendencias", selected = false, onClick = onOpenSearch)
        }
        if (suggestedUsers.isNotEmpty()) {
            Text(
                text = "Cuentas sugeridas",
                style = LeoCardTitle,
                color = BrandText,
                modifier = Modifier.padding(LeoDimens.SpaceMd)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = LeoDimens.SpaceMd),
                horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
            ) {
                items(suggestedUsers, key = { it.id }) { user ->
                    Surface(
                        onClick = { onUserClick(user.id) },
                        shape = RoundedCornerShape(LeoDimens.RadiusCard),
                        color = BrandWhite,
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(LeoDimens.SpaceCompact).width(100.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PetImage(
                                imageUrl = user.profileImageUrl,
                                modifier = Modifier.size(56.dp),
                                cornerRadius = 28.dp,
                                contentDescription = user.name
                            )
                            Text(
                                text = user.name,
                                style = LeoCaption,
                                color = BrandText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = "Descubrir",
            style = LeoCardTitle,
            color = BrandText,
            modifier = Modifier.padding(LeoDimens.SpaceMd)
        )
        if (posts.isEmpty()) {
            LeoEmptyState(
                title = "Nada para explorar todavía",
                message = "Cuando haya publicaciones, vas a ver una cuadrícula visual aquí.",
                icon = Icons.Default.Search
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(1.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(posts, key = { it.id }) { post ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(BrandCream)
                            .clickable { onPostClick(post) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (post.imageUrl != null) {
                            PetImage(
                                imageUrl = post.imageUrl,
                                modifier = Modifier.fillMaxSize(),
                                contentDescription = post.title
                            )
                        } else {
                            Icon(Icons.Default.Pets, null, tint = BrandOrangeSoft)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, name = "StoriesRowPreview")
@Composable
private fun StoriesRowPreview() {
    ComunidappTheme {
        StoriesRow(onAddStory = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 700, name = "HomeReelsPreview")
@Composable
private fun HomeReelsPreview() {
    ComunidappTheme {
        HomeReelsTab(posts = emptyList())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF6EA, widthDp = 390, heightDp = 700, name = "HomeExplorePreview")
@Composable
private fun HomeExplorePreview() {
    ComunidappTheme {
        HomeExploreTab(
            posts = listOf(
                FeedPost("1", "u", "Ana", type = PostType.GENERAL, title = "A", content = "", imageUrl = null)
            ),
            suggestedUsers = emptyList(),
            searchQuery = "",
            onSearchChange = {},
            onOpenSearch = {},
            onPostClick = {},
            onUserClick = {}
        )
    }
}
