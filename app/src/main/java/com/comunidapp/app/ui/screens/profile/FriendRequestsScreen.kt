package com.comunidapp.app.ui.screens.profile

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandOrange
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoSectionTitle
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.viewmodel.FriendRequestItem
import com.comunidapp.app.viewmodel.FriendRequestsViewModel

@Composable
fun FriendRequestsScreen(
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit = {},
    viewModel: FriendRequestsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Solicitudes para seguirte",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(Modifier.padding(padding))
            uiState.incoming.isEmpty() && uiState.outgoing.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    LeoEmptyState(
                        title = "No tenés solicitudes pendientes",
                        message = "Cuando alguien te envíe una solicitud, va a aparecer acá."
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
                    uiState.actionMessage?.let { message ->
                        item {
                            Text(
                                text = message,
                                style = LeoCaption,
                                color = BrandTextSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = LeoDimens.SpaceMd)
                            )
                        }
                    }

                    if (uiState.incoming.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Recibidas",
                                count = uiState.incoming.size
                            )
                        }
                        items(uiState.incoming, key = { it.connection.id }) { item ->
                            IncomingRequestCard(
                                item = item,
                                isLoading = uiState.actionInProgressId == item.connection.id,
                                onUserClick = { onUserClick(item.user.id) },
                                onAccept = { viewModel.acceptRequest(item.connection.id) },
                                onReject = { viewModel.rejectRequest(item.connection.id) }
                            )
                        }
                    }

                    if (uiState.outgoing.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Enviadas",
                                count = uiState.outgoing.size,
                                modifier = Modifier.padding(top = if (uiState.incoming.isNotEmpty()) 8.dp else 0.dp)
                            )
                        }
                        items(uiState.outgoing, key = { it.connection.id }) { item ->
                            OutgoingRequestCard(
                                item = item,
                                isLoading = uiState.actionInProgressId == item.connection.id,
                                onUserClick = { onUserClick(item.user.id) },
                                onCancel = { viewModel.cancelRequest(item.connection.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = "$title ($count)",
        style = LeoSectionTitle,
        color = BrandText,
        modifier = modifier.padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceS)
    )
}

@Composable
private fun IncomingRequestCard(
    item: FriendRequestItem,
    isLoading: Boolean,
    onUserClick: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        RequestUserRow(user = item.user, onClick = onUserClick)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceS),
            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = BrandOrange,
                    strokeWidth = 2.dp
                )
            } else {
                LeoPrimaryButton(
                    text = "Aceptar",
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    fillMaxWidth = false
                )
                LeoOutlinedButton(
                    text = "Rechazar",
                    onClick = onReject,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        LeoHairline(modifier = Modifier.padding(start = LeoDimens.SpaceMd))
    }
}

@Composable
private fun OutgoingRequestCard(
    item: FriendRequestItem,
    isLoading: Boolean,
    onUserClick: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        RequestUserRow(user = item.user, onClick = onUserClick)
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = LeoDimens.SpaceMd)
                    .size(22.dp),
                color = BrandOrange,
                strokeWidth = 2.dp
            )
        } else {
            LeoOutlinedButton(
                text = "Cancelar solicitud",
                onClick = onCancel,
                modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd)
            )
        }
        LeoHairline(modifier = Modifier.padding(start = LeoDimens.SpaceMd, top = LeoDimens.SpaceS))
    }
}

@Composable
private fun RequestUserRow(
    user: com.comunidapp.app.data.model.User,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceCompact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
    ) {
        PetImage(
            imageUrl = user.profileImageUrl,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape),
            cornerRadius = 26.dp,
            contentDescription = user.name
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = LeoCardTitle,
                color = BrandText
            )
            Text(
                text = "Persona",
                style = LeoCaption,
                color = BrandTextSecondary
            )
            user.locationText?.let { location ->
                Text(
                    text = location,
                    style = LeoCaption,
                    color = BrandTextSecondary
                )
            }
        }
    }
}
