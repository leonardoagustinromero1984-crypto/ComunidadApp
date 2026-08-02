package com.comunidapp.app.ui.screens.m20

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.comunidapp.app.data.model.M20DeletedContent
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
                "LeoVer M20 — bandeja con contexto y sin PII en modelos públicos.",
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
            Text(
                conversation.conversationType.name.lowercase().replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            conversation.contextHint?.let { hint ->
                Text("↗ ${hint.displayLabel}", style = MaterialTheme.typography.labelMedium)
            }
            conversation.lastMessagePreview?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            if (conversation.unreadCount > 0) {
                AssistChip(
                    onClick = onClick,
                    label = { Text("${conversation.unreadCount} sin leer") }
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
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(feedback) {
        if (feedback != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(state) {
        val content = state as? M20ThreadUiState.Content ?: return@LaunchedEffect
        if (content.hasMore && !content.loadingMore &&
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == content.messages.lastIndex
        ) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Conversación", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            M20ThreadUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            M20ThreadUiState.Empty -> EmptyState(
                title = "Sin mensajes",
                message = "Todavía no hay mensajes en esta conversación.",
                contentModifier = Modifier.padding(padding)
            )
            M20ThreadUiState.Sending -> LoadingState(
                contentDescription = "Enviando mensaje…",
                contentModifier = Modifier.padding(padding)
            )
            is M20ThreadUiState.SendFailed -> ErrorState(
                message = s.message,
                contentModifier = Modifier.padding(padding),
                onRetry = { viewModel.sendMessage(draft) }
            )
            M20ThreadUiState.Blocked -> Column(
                Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Conversación bloqueada.", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { viewModel.unblockUser() }) { Text("Desbloquear") }
            }
            M20ThreadUiState.Archived -> Column(
                Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Conversación archivada.", style = MaterialTheme.typography.titleMedium)
                Text("No podés enviar mensajes mientras esté archivada.", style = MaterialTheme.typography.bodyMedium)
            }
            M20ThreadUiState.PermissionDenied -> ErrorState(
                message = "No tenés permiso para ver esta conversación.",
                contentModifier = Modifier.padding(padding)
            )
            M20ThreadUiState.AttachmentUnavailable -> ErrorState(
                message = "El adjunto no está disponible.",
                contentModifier = Modifier.padding(padding)
            )
            is M20ThreadUiState.Error -> ErrorState(
                message = s.message,
                contentModifier = Modifier.padding(padding)
            )
            is M20ThreadUiState.PartialData -> Column(
                Modifier.padding(padding).padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                M20ThreadBody(s.conversation, s.messages, s.hasMore, false, viewModel, draft, { draft = it })
            }
            is M20ThreadUiState.Content -> Column(
                Modifier.padding(padding).padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                feedback?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                M20ThreadBody(
                    conversation = s.conversation,
                    messages = s.messages,
                    hasMore = s.hasMore,
                    loadingMore = s.loadingMore,
                    viewModel = viewModel,
                    draft = draft,
                    onDraftChange = { draft = it },
                    replyTo = s.replyTo,
                    editing = s.editing,
                    listState = listState
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.M20ThreadBody(
    conversation: M20PublicConversation?,
    messages: List<M20PublicMessage>,
    hasMore: Boolean,
    loadingMore: Boolean,
    viewModel: M20ThreadViewModel,
    draft: String,
    onDraftChange: (String) -> Unit,
    replyTo: M20PublicMessage? = null,
    editing: M20PublicMessage? = null,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState()
) {
    conversation?.let { conv ->
        Text(conv.peerDisplayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Estado: ${m20ConversationStatusLabel(conv.status)}", style = MaterialTheme.typography.bodySmall)
        conv.contextHint?.let { hint ->
            Text("Contexto: ${hint.displayLabel}", style = MaterialTheme.typography.labelMedium)
        }
    }
    if (hasMore || loadingMore) {
        Text(
            if (loadingMore) "Cargando más…" else "Deslizá para cargar mensajes anteriores",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
    LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        state = listState
    ) {
        items(messages, key = { it.id }) { msg ->
            M20MessageBubble(
                message = msg,
                onReply = { viewModel.setReplyTo(msg) },
                onEdit = { if (msg.isOwnMessage && !msg.isDeleted) viewModel.setEditing(msg) },
                onDelete = { if (msg.isOwnMessage && !msg.isDeleted) viewModel.deleteMessage(msg.id) },
                onReport = { viewModel.reportMessage(msg.id) }
            )
        }
    }
    replyTo?.let {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("↩ Respondiendo: ${it.content.take(40)}", style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = { viewModel.cancelComposerModes() }) { Text("Cancelar") }
        }
    }
    editing?.let {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("✎ Editando mensaje", style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = { viewModel.cancelComposerModes() }) { Text("Cancelar") }
        }
    }
    if (conversation?.status == M20ConversationStatus.ACTIVE) {
        OutlinedTextField(
            value = draft.ifBlank { editing?.content ?: "" }.let { if (draft.isNotBlank()) draft else it },
            onValueChange = onDraftChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (editing != null) "Editar mensaje" else "Escribí un mensaje") },
            maxLines = 4
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val text = draft.ifBlank { editing?.content.orEmpty() }
                    viewModel.sendMessage(text)
                    onDraftChange("")
                },
                enabled = draft.isNotBlank() || editing != null
            ) { Text(if (editing != null) "Guardar" else "Enviar") }
            OutlinedButton(onClick = { viewModel.archiveConversation() }) { Text("Archivar") }
            OutlinedButton(onClick = { viewModel.blockUser() }) { Text("Bloquear") }
            OutlinedButton(onClick = { viewModel.reportConversation() }) { Text("Reportar") }
        }
    } else if (conversation?.status == M20ConversationStatus.BLOCKED) {
        OutlinedButton(onClick = { viewModel.unblockUser() }) { Text("Desbloquear") }
    } else {
        Text(
            "No podés enviar mensajes en esta conversación.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun M20MessageBubble(
    message: M20PublicMessage,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit
) {
    val align = if (message.isOwnMessage) Alignment.End else Alignment.Start
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Card(modifier = Modifier.fillMaxWidth(if (message.isOwnMessage) 0.85f else 0.9f)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    message.senderDisplayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                message.replyReference?.let { ref ->
                    Text(
                        "↩ ${ref.senderDisplayName}: ${ref.preview}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (message.isDeleted) M20DeletedContent.PLACEHOLDER else message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isDeleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                message.attachmentRef?.let {
                    Text("📎 Adjunto (referencia)", style = MaterialTheme.typography.labelSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onReply) { Text("Responder") }
                    if (message.isOwnMessage && !message.isDeleted) {
                        TextButton(onClick = onEdit) { Text("Editar") }
                        TextButton(onClick = onDelete) { Text("Eliminar") }
                    }
                    if (!message.isOwnMessage) {
                        TextButton(onClick = onReport) { Text("Reportar") }
                    }
                }
                Text(
                    buildString {
                        append(m20MessageStatusLabel(message.status))
                        message.editedAt?.let { append(" · editado") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (message.isOwnMessage) TextAlign.End else TextAlign.Start
                )
            }
        }
    }
}
