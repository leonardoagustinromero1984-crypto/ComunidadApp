package com.comunidapp.app.ui.screens.m20

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.M20ConversationStatus
import com.comunidapp.app.data.model.M20PublicConversation
import com.comunidapp.app.data.model.M20PublicMessage
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M20ConversationListUiState
import com.comunidapp.app.viewmodel.M20ConversationListViewModel
import com.comunidapp.app.viewmodel.M20ThreadUiState
import com.comunidapp.app.viewmodel.M20ThreadViewModel
import com.comunidapp.app.viewmodel.m20ConversationStatusLabel
import com.comunidapp.app.viewmodel.m20MessageStatusLabel

@Composable
fun M20ConversationListScreen(
    onNavigateBack: () -> Unit,
    onConversationClick: (String) -> Unit,
    viewModel: M20ConversationListViewModel = viewModel(factory = M20ConversationListViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Mensajería", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "M20 — conversaciones con contexto y sin PII en modelos públicos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            when (val s = state) {
                M20ConversationListUiState.Loading -> LoadingState()
                M20ConversationListUiState.Empty -> EmptyState(
                    title = "Sin conversaciones",
                    message = "Todavía no tenés mensajes."
                )
                is M20ConversationListUiState.Error -> ErrorState(message = s.message, onRetry = { viewModel.refresh() })
                is M20ConversationListUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.items, key = { it.id }) { item ->
                        M20ConversationCard(item, onClick = { onConversationClick(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun M20ConversationCard(conversation: M20PublicConversation, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    conversation.peerDisplayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    m20ConversationStatusLabel(conversation.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (conversation.status) {
                        M20ConversationStatus.BLOCKED -> MaterialTheme.colorScheme.error
                        M20ConversationStatus.ARCHIVED -> MaterialTheme.colorScheme.onSurfaceVariant
                        M20ConversationStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                    }
                )
            }
            conversation.contextHint?.let { hint ->
                Text("↗ ${hint.displayLabel}", style = MaterialTheme.typography.labelMedium)
            }
            conversation.lastMessagePreview?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            if (conversation.unreadCount > 0) {
                Text(
                    "${conversation.unreadCount} sin leer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun M20ThreadScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    viewModel: M20ThreadViewModel = viewModel(factory = M20ThreadViewModel.factory(conversationId))
) {
    val state by viewModel.uiState.collectAsState()
    val feedback by viewModel.message.collectAsState()
    val sending by viewModel.sending.collectAsState()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Conversación", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            M20ThreadUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            is M20ThreadUiState.Error -> ErrorState(
                message = s.message,
                contentModifier = Modifier.padding(padding)
            )
            is M20ThreadUiState.Content -> Column(
                Modifier.padding(padding).padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                s.conversation?.let { conv ->
                    Text(conv.peerDisplayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Estado: ${m20ConversationStatusLabel(conv.status)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    conv.contextHint?.let { hint ->
                        Text("Contexto: ${hint.displayLabel}", style = MaterialTheme.typography.labelMedium)
                    }
                }
                feedback?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.messages, key = { it.id }) { msg ->
                        M20MessageBubble(msg)
                    }
                }
                if (s.conversation?.status == M20ConversationStatus.ACTIVE) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Escribí un mensaje") },
                        enabled = !sending,
                        maxLines = 4
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.sendMessage(draft)
                                draft = ""
                            },
                            enabled = draft.isNotBlank() && !sending
                        ) { Text(if (sending) "Enviando…" else "Enviar") }
                        OutlinedButton(onClick = { viewModel.archiveConversation() }) {
                            Text("Archivar")
                        }
                        OutlinedButton(onClick = { viewModel.blockUser() }) {
                            Text("Bloquear")
                        }
                    }
                } else {
                    Text(
                        "No podés enviar mensajes en esta conversación.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun M20MessageBubble(message: M20PublicMessage) {
    val align = if (message.isOwnMessage) Alignment.End else Alignment.Start
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (message.isOwnMessage) 0.85f else 0.9f)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(message.senderDisplayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(message.content, style = MaterialTheme.typography.bodyMedium)
                message.attachmentRef?.let {
                    Text("📎 Adjunto (referencia)", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    m20MessageStatusLabel(message.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (message.isOwnMessage) TextAlign.End else TextAlign.Start
                )
            }
        }
    }
}
