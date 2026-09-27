package com.comunidapp.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoSearchBar
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandOrange
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.SurfaceMuted
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.viewmodel.FriendActionState
import com.comunidapp.app.viewmodel.SearchFriendsViewModel
import com.comunidapp.app.viewmodel.UserSearchItem

@Composable
fun SearchFriendsScreen(
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit = {},
    showTopBar: Boolean = true,
    viewModel: SearchFriendsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            if (showTopBar) {
                LeoTopAppBar(
                    title = "Encontrar personas",
                    showBackButton = true,
                    onBackClick = onNavigateBack
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LeoSearchBar(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceS),
                placeholder = "Nombre, email o ciudad..."
            )

            if (uiState.query.trim().length < 2) {
                SearchHintCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else if (uiState.isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = BrandOrange,
                        strokeWidth = 2.dp
                    )
                }
            } else if (uiState.results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LeoEmptyState(
                        title = "Nadie coincide",
                        message = "No encontramos usuarios con ese criterio.",
                        icon = Icons.Default.Search
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.results, key = { it.user.id }) { item ->
                        UserSearchCard(
                            item = item,
                            onUserClick = { onUserClick(item.user.id) },
                            onAddFriend = { viewModel.sendFriendRequest(item.user.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHintCard(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(LeoDimens.SpaceMd)) {
            Text(
                text = "Encontrá personas de la comunidad",
                style = LeoCardTitle,
                color = BrandText
            )
            Text(
                text = "Escribí al menos 2 letras para buscar por nombre, email o ubicación.",
                style = LeoCaption,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = LeoDimens.SpaceS)
            )
    }
}

@Composable
private fun UserSearchCard(
    item: UserSearchItem,
    onUserClick: () -> Unit,
    onAddFriend: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onUserClick)
                .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceCompact),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(SurfaceMuted)
            ) {
                PetImage(
                    imageUrl = item.user.profileImageUrl,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 26.dp,
                    contentDescription = item.user.name
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = item.user.name,
                    style = LeoCardTitle,
                    color = BrandText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Persona",
                    style = LeoCaption,
                    color = BrandTextSecondary
                )
                item.user.locationText?.let { location ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = BrandTextSecondary
                        )
                        Text(
                            text = location,
                            style = LeoCaption,
                            color = BrandTextSecondary,
                            modifier = Modifier.padding(start = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            when (item.actionState) {
                FriendActionState.FRIEND -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = BrandOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Conexión",
                            style = LeoCaption,
                            color = BrandOrange,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                FriendActionState.PENDING -> {
                    Text(
                        text = "Enviada",
                        style = LeoCaption,
                        color = BrandTextSecondary
                    )
                }
                FriendActionState.LOADING -> {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                FriendActionState.NONE -> {
                    OutlinedButton(
                        onClick = onAddFriend,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Conectar")
                    }
                }
            }
        }
        LeoHairline(modifier = Modifier.padding(start = LeoDimens.SpaceMd))
    }
}
