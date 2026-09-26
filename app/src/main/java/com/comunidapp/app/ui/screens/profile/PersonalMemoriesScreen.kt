package com.comunidapp.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.comunidapp.app.data.repository.PersonalMemory
import com.comunidapp.app.data.repository.PersonalMemoryPetGroup
import com.comunidapp.app.domain.pets.PetCareTransferCopy
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.components.v2.V2SurfaceCard
import com.comunidapp.app.ui.media.ReelFeedMedia
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.BrandOrangeSoft
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.ui.util.formatMemoryDateTime
import com.comunidapp.app.ui.util.formatMemoryDay
import com.comunidapp.app.viewmodel.PersonalMemoryGroupsViewModel
import com.comunidapp.app.viewmodel.PersonalPetMemoriesViewModel

@Composable
fun PersonalMemoriesScreen(
    onNavigateBack: () -> Unit,
    onOpenPetMemories: (petId: String?, petName: String) -> Unit,
    viewModel: PersonalMemoryGroupsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    androidx.compose.material3.Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = PetCareTransferCopy.MEMORIES_TITLE,
                subtitle = PetCareTransferCopy.MEMORIES_SUBTITLE,
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when {
            state.isLoading && state.groups.isEmpty() ->
                LoadingState(contentModifier = Modifier.padding(padding))
            state.errorMessage != null && state.groups.isEmpty() -> ErrorState(
                message = state.errorMessage.orEmpty(),
                contentModifier = Modifier.padding(padding),
                onRetry = viewModel::refresh
            )
            state.groups.isEmpty() -> LeoEmptyState(
                title = PetCareTransferCopy.MEMORIES_TITLE,
                message = PetCareTransferCopy.MEMORIES_EMPTY,
                icon = Icons.Default.PhotoLibrary,
                modifier = Modifier.padding(padding)
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(LeoDimens.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceMd)
            ) {
                items(state.groups, key = { it.petId ?: "_unassigned" }) { group ->
                    MemoryPetGroupCard(
                        group = group,
                        onClick = { onOpenPetMemories(group.petId, group.petName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryPetGroupCard(
    group: PersonalMemoryPetGroup,
    onClick: () -> Unit
) {
    V2SurfaceCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemoryCoverThumb(
                url = group.coverPreviewUrl,
                isVideo = group.coverMimeType?.startsWith("video/", ignoreCase = true) == true,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.width(LeoDimens.SpaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.petName,
                    style = LeoCardTitle,
                    color = BrandText,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = memoryCountLabel(group),
                    style = LeoCaption,
                    color = MutedText,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (group.lastMemoryAtEpochMs > 0L) {
                    Text(
                        text = "Último · ${formatMemoryDateTime(group.lastMemoryAtEpochMs)}",
                        style = LeoCaption,
                        color = MutedText,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                // canOpenPet is historical capability only; this list is not a VitaCora bypass.
            }
        }
    }
}

private fun memoryCountLabel(group: PersonalMemoryPetGroup): String {
    val total = "${group.memoryCount} ${if (group.memoryCount == 1) "recuerdo" else "recuerdos"}"
    return if (group.videoCount > 0) {
        "$total · ${group.videoCount} ${if (group.videoCount == 1) "video" else "videos"}"
    } else {
        total
    }
}

@Composable
fun PersonalPetMemoriesScreen(
    petId: String?,
    petName: String?,
    onNavigateBack: () -> Unit,
    viewModel: PersonalPetMemoriesViewModel = viewModel(
        key = "memories_pet_${petId ?: "none"}",
        factory = PersonalPetMemoriesViewModel.factory(petId, petName)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.hasMore && !state.isLoadingMore && last >= (info.totalItemsCount - 3).coerceAtLeast(0)
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    androidx.compose.material3.Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = state.title,
                subtitle = PetCareTransferCopy.MEMORIES_TITLE,
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when {
            state.isLoading && state.items.isEmpty() ->
                LoadingState(contentModifier = Modifier.padding(padding))
            state.errorMessage != null && state.items.isEmpty() -> ErrorState(
                message = state.errorMessage.orEmpty(),
                contentModifier = Modifier.padding(padding),
                onRetry = viewModel::refresh
            )
            state.items.isEmpty() -> LeoEmptyState(
                title = state.title,
                message = "No hay recuerdos en este grupo.",
                icon = Icons.Default.PhotoLibrary,
                modifier = Modifier.padding(padding)
            )
            else -> {
                val playing = state.items.firstOrNull { it.memoryId == state.playingAssetId }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (playing != null && !playing.coverPreviewUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (playing.isVideo) Modifier.aspectRatio(9f / 16f)
                                    else Modifier.aspectRatio(1f)
                                )
                                .padding(LeoDimens.SpaceMd)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BrandCream)
                                .clickable { viewModel.stopPlayback() }
                        ) {
                            if (playing.isVideo) {
                                ReelFeedMedia(
                                    url = playing.coverPreviewUrl.orEmpty(),
                                    modifier = Modifier.fillMaxSize(),
                                    previewLabel = "Video",
                                    allowPlayback = true
                                )
                            } else {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(playing.coverPreviewUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = playing.caption,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f, fill = true),
                        contentPadding = PaddingValues(LeoDimens.SpaceMd),
                        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceMd)
                    ) {
                        items(state.items, key = { it.memoryId }) { memory ->
                            MemoryContentCard(
                                memory = memory,
                                selected = memory.memoryId == state.playingAssetId,
                                onClick = { viewModel.play(memory.memoryId) }
                            )
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(LeoDimens.SpaceSm),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryContentCard(
    memory: PersonalMemory,
    selected: Boolean,
    onClick: () -> Unit
) {
    V2SurfaceCard(onClick = onClick) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = formatMemoryDateTime(memory.displayAtEpochMs),
                style = LeoCaption,
                color = MutedText
            )
            memory.caption?.takeIf {
                it.isNotBlank() &&
                    !it.contains("VitaCora", ignoreCase = true) &&
                    !it.equals("Clip", ignoreCase = true)
            }?.let { caption ->
                Text(
                    text = caption,
                    style = LeoCardTitle,
                    color = BrandText,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val thumbs = memory.mediaAssetIds.take(4)
                thumbs.forEach { assetId ->
                    MemoryCoverThumb(
                        url = memory.previewUrls[assetId] ?: memory.previewUrl.takeIf { assetId == memory.assetId },
                        isVideo = memory.mediaMimeTypes
                            .getOrNull(memory.mediaAssetIds.indexOf(assetId))
                            ?.startsWith("video/", ignoreCase = true) == true ||
                            (thumbs.size == 1 && memory.isVideo),
                        modifier = Modifier.size(88.dp),
                        corner = 8.dp
                    )
                }
                if (memory.mediaAssetIds.size > 4) {
                    Text(
                        text = "+${memory.mediaAssetIds.size - 4}",
                        style = LeoCaption,
                        color = MutedText,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
            Text(
                text = if (memory.sharedWithVitaCora) {
                    PetCareTransferCopy.MEMORY_SHARED
                } else {
                    PetCareTransferCopy.MEMORY_ONLY_YOU
                },
                style = LeoCaption,
                color = MutedText,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(BrandOrangeSoft)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MemoryCoverThumb(
    url: String?,
    isVideo: Boolean,
    modifier: Modifier = Modifier,
    corner: androidx.compose.ui.unit.Dp = 12.dp
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(BrandCream),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = BrandOrangeSoft,
                modifier = Modifier.size(28.dp)
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .size(360)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isVideo) {
                // Grid/cover never starts ExoPlayer — play icon only.
            }
        }
    }
}
