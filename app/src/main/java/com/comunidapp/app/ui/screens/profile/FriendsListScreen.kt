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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.User
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.ResolvedProfileAvatar
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoSectionTitle
import com.comunidapp.app.ui.theme.SurfaceMuted
import com.comunidapp.app.viewmodel.FriendListItem
import com.comunidapp.app.viewmodel.FriendsListViewModel

@Composable
fun FriendsListScreen(
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit,
    onMessageClick: (userId: String, name: String) -> Unit,
    showTopBar: Boolean = true,
    showIncomingRequests: Boolean = true,
    showOutgoingRequests: Boolean = true,
    connectionsOnly: Boolean = false,
    requestsOnly: Boolean = false,
    viewModel: FriendsListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showIncoming = showIncomingRequests && !connectionsOnly
    val showFriends = !requestsOnly
    val showOutgoing = showOutgoingRequests && !connectionsOnly

    val content: @Composable (PaddingValues) -> Unit = { padding ->
        when {
            uiState.isLoading -> LoadingState(Modifier.padding(padding))
            showFriends && uiState.friends.isEmpty() &&
                (!showIncoming || uiState.incoming.isEmpty()) &&
                (!showOutgoing || uiState.outgoing.isEmpty()) -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    LeoEmptyState(
                        title = if (connectionsOnly) "Todavía no tenés conexiones" else "Todavía no tenés conexiones",
                        message = if (connectionsOnly) {
                            "Encontrá personas y conectá con ellas para verlas acá."
                        } else {
                            "Encontrá personas y enviales una solicitud de conexión."
                        },
                        icon = Icons.Default.People
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + LeoDimens.SpaceS,
                        bottom = padding.calculateBottomPadding() + LeoDimens.SpaceS
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (showIncoming && uiState.incoming.isNotEmpty()) {
                        item {
                            Text(
                                text = "Solicitudes de conexión",
                                style = LeoSectionTitle,
                                color = BrandText
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
                    if (showFriends && uiState.friends.isNotEmpty()) {
                        if (connectionsOnly) {
                            item {
                                Text(
                                    text = "Conexiones",
                                    style = LeoSectionTitle,
                                    color = BrandText
                                )
                            }
                        }
                        items(uiState.friends, key = { it.connection.id }) { item ->
                            FriendRow(
                                item = item,
                                onUserClick = { onUserClick(item.user.id) },
                                onMessageClick = {
                                    onMessageClick(item.user.id, displayName(item.user))
                                },
                                onRemoveClick = if (connectionsOnly) {
                                    { viewModel.removeFromManada(item.connection.id) }
                                } else {
                                    null
                                },
                                removeBusy = uiState.actionInProgressId == item.connection.id
                            )
                        }
                    }
                    if (showOutgoing && uiState.outgoing.isNotEmpty()) {
                        item {
                            Text(
                                text = "Solicitudes enviadas",
                                style = LeoSectionTitle,
                                color = BrandText,
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
                                style = LeoCaption,
                                color = BrandTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTopBar) {
        Scaffold(
            containerColor = BrandBackground,
            topBar = {
                LeoTopAppBar(
                    title = "Mi manada",
                    showBackButton = true,
                    onBackClick = onNavigateBack
                )
            }
        ) { padding -> content(padding) }
    } else {
        content(PaddingValues())
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
    Column(Modifier.fillMaxWidth()) {
        FriendRow(
            item = item,
            onUserClick = onUserClick,
            onMessageClick = null
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceS),
            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
        ) {
            LeoPrimaryButton(
                text = "Aceptar",
                onClick = onAccept,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                fillMaxWidth = false
            )
            LeoOutlinedButton(
                text = "Rechazar",
                onClick = onReject,
                enabled = !busy,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FriendRow(
    item: FriendListItem,
    onUserClick: () -> Unit,
    onMessageClick: (() -> Unit)?,
    onRemoveClick: (() -> Unit)? = null,
    removeBusy: Boolean = false
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
                ResolvedProfileAvatar(
                    user = item.user,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 26.dp,
                    contentDescription = displayName(item.user)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = LeoDimens.SpaceCompact)
            ) {
                Text(
                    text = displayName(item.user),
                    style = LeoCardTitle,
                    color = BrandText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val username = item.user.username?.trim().orEmpty()
                if (username.isNotEmpty()) {
                    Text(
                        text = "@$username",
                        style = LeoCaption,
                        color = BrandTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.pending) {
                    Text(
                        text = "Pendiente",
                        style = LeoCaption,
                        color = BrandTextSecondary
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
        if (onRemoveClick != null) {
            var confirm by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(false)
            }
            TextButton(
                onClick = { confirm = true },
                enabled = !removeBusy,
                modifier = Modifier.padding(start = LeoDimens.SpaceMd)
            ) {
                Text("Eliminar de mi manada")
            }
            if (confirm) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text("Eliminar de mi manada") },
                    text = {
                        Text("Se termina la conexión. No se bloquea ni se borran mensajes.")
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirm = false
                            onRemoveClick()
                        }) { Text("Eliminar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirm = false }) { Text("Cancelar") }
                    }
                )
            }
        }
        LeoHairline(modifier = Modifier.padding(start = LeoDimens.SpaceMd))
    }
}

private fun displayName(user: User): String =
    user.displayName?.takeIf { it.isNotBlank() } ?: user.name
