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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.AppNotification
import com.comunidapp.app.notifications.NotificationInboxRefreshCoordinator
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.util.formatRelativeTime
import com.comunidapp.app.viewmodel.NotificationsViewModel

@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPreferences: () -> Unit = {},
    onOpenInvitation: (String) -> Unit = {},
    onOpenCareTransfer: (String) -> Unit = {},
    viewModel: NotificationsViewModel = viewModel()
) {
    val notifications by viewModel.notifications.collectAsState()
    val incomingTransfers by viewModel.incomingTransfers.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()

    LaunchedEffect(Unit) {
        NotificationInboxRefreshCoordinator.refreshSignals.collect {
            viewModel.refreshFromSignal()
        }
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Notificaciones",
                showBackButton = true,
                onBackClick = onNavigateBack,
                actions = {
                    TextButton(
                        onClick = onNavigateToPreferences,
                        modifier = Modifier.semantics {
                            contentDescription = "Abrir preferencias de notificaciones"
                        }
                    ) {
                        Text("Preferencias")
                    }
                    if (unreadCount > 0) {
                        TextButton(onClick = viewModel::markAllRead) {
                            Text("Marcar todas")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (notifications.isEmpty() && incomingTransfers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                LeoEmptyState(
                    title = "Sin notificaciones",
                    message = "Cuando haya novedades, van a aparecer acá."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + LeoDimens.SpaceS,
                    bottom = padding.calculateBottomPadding() + LeoDimens.SpaceS
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(incomingTransfers, key = { "care-${it.id.value}" }) { transfer ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCareTransfer(transfer.petId.value) }
                            .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceCompact)
                    ) {
                            Text(
                                text = com.comunidapp.app.domain.pets.PetCareTransferCopy.RECEIVER_TITLE,
                                style = LeoCardTitle,
                                color = BrandText
                            )
                            Text(
                                text = com.comunidapp.app.domain.pets.PetCareTransferCopy.incomingRequest(
                                    sourceName = transfer.sourceDisplayName.orEmpty(),
                                    petName = transfer.petDisplayName.orEmpty()
                                ),
                                style = LeoCaption,
                                color = BrandTextSecondary
                            )
                    }
                    LeoHairline(modifier = Modifier.padding(start = LeoDimens.SpaceMd))
                }
                items(notifications, key = { it.id }) { notification ->
                    NotificationCard(
                        notification = notification,
                        onClick = {
                            viewModel.markRead(notification.id)
                            if (notification.type == com.comunidapp.app.data.model.NotificationType.ORG_INVITE) {
                                notification.relatedId?.let(onOpenInvitation)
                            }
                        },
                        onArchive = { viewModel.archive(notification.id) },
                        onDelete = { viewModel.deleteLogical(notification.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: AppNotification,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Notificación ${notification.title}" }
                .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceCompact)
        ) {
            Text(
                text = notification.title,
                style = LeoCardTitle,
                color = BrandText
            )
            Text(
                text = notification.body,
                style = LeoCaption,
                color = BrandTextSecondary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = LeoDimens.SpaceS),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatRelativeTime(notification.createdAt ?: 0L),
                    style = LeoCaption,
                    color = BrandTextSecondary
                )
                Row {
                    TextButton(onClick = onArchive) { Text("Archivar") }
                    TextButton(onClick = onDelete) { Text("Eliminar") }
                }
            }
        }
        LeoHairline(modifier = Modifier.padding(start = LeoDimens.SpaceMd))
    }
}
