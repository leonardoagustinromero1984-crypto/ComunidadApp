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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.AppNotification
import com.comunidapp.app.notifications.NotificationInboxRefreshCoordinator
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.util.formatRelativeTime
import com.comunidapp.app.viewmodel.NotificationsViewModel

@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPreferences: () -> Unit = {},
    viewModel: NotificationsViewModel = viewModel()
) {
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()

    LaunchedEffect(Unit) {
        NotificationInboxRefreshCoordinator.refreshSignals.collect {
            viewModel.refreshFromSignal()
        }
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
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
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No tenés notificaciones en LeoVer",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationCard(
                        notification = notification,
                        onClick = { viewModel.markRead(notification.id) },
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Notificación ${notification.title}" },
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isUnread) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (notification.isUnread) FontWeight.Bold else FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = notification.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatRelativeTime(notification.createdAt ?: 0L),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    TextButton(onClick = onArchive) { Text("Archivar") }
                    TextButton(onClick = onDelete) { Text("Eliminar") }
                }
            }
        }
    }
}
