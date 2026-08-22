package com.comunidapp.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.User
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.viewmodel.FriendListItem
import com.comunidapp.app.viewmodel.FriendsListViewModel

@Composable
fun FriendsListScreen(
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit,
    onMessageClick: (userId: String, name: String) -> Unit,
    viewModel: FriendsListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Mis amigos",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(Modifier.padding(padding))
            uiState.friends.isEmpty() && uiState.incoming.isEmpty() && uiState.outgoing.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    LeoEmptyState(
                        title = "Todavía no tenés amigos",
                        message = "Buscá personas y enviales una solicitud para verlas acá.",
                        icon = Icons.Default.People
                    )
                }
            }
            else -> {
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
                    if (uiState.incoming.isNotEmpty()) {
                        item {
                            Text(
                                text = "Solicitudes de amistad",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        items(uiState.incoming, key = { "in-${it.connection.id}" }) { item ->
                            IncomingFriendRequestRow(
                                item = item,
                                busy = uiState.actionInProgressId == item.connection.id,
                                onAccept = { viewModel.acceptRequest(item.connection.id) },
                                onReject = { viewModel.rejectRequest(item.connection.id) },
                                onUserClick = { onUserClick(item.user.id) }
                            )
                        }
                    }
                    if (uiState.friends.isNotEmpty()) {
                        items(uiState.friends, key = { it.connection.id }) { item ->
                            FriendRow(
                                item = item,
                                onUserClick = { onUserClick(item.user.id) },
                                onMessageClick = {
                                    onMessageClick(item.user.id, displayName(item.user))
                                }
                            )
                        }
                    }
                    if (uiState.outgoing.isNotEmpty()) {
                        item {
                            Text(
                                text = "Solicitudes enviadas",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(uiState.outgoing, key = { "out-${it.connection.id}" }) { item ->
                            FriendRow(
                                item = item,
                                onUserClick = { onUserClick(item.user.id) },
                                onMessageClick = null
                            )
                        }
                    }
                    uiState.actionMessage?.let { message ->
                        item {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomingFriendRequestRow(
    item: FriendListItem,
    busy: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onUserClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            FriendRow(
                item = item,
                onUserClick = onUserClick,
                onMessageClick = null
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAccept,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Aceptar") }
                OutlinedButton(
                    onClick = onReject,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Rechazar") }
            }
        }
    }
}

@Composable
private fun FriendRow(
    item: FriendListItem,
    onUserClick: () -> Unit,
    onMessageClick: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUserClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                PetImage(
                    imageUrl = item.user.profileImageUrl ?: item.user.avatarPath,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 26.dp,
                    contentDescription = displayName(item.user)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = displayName(item.user),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val username = item.user.username?.trim().orEmpty()
                if (username.isNotEmpty()) {
                    Text(
                        text = "@$username",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.pending) {
                    Text(
                        text = "Pendiente",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onMessageClick != null) {
                IconButton(onClick = onMessageClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "Mensaje"
                    )
                }
            }
        }
    }
}

private fun displayName(user: User): String =
    user.displayName?.takeIf { it.isNotBlank() } ?: user.name
