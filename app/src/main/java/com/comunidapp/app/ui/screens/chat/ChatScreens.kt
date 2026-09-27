package com.comunidapp.app.ui.screens.chat

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.ChatMessage
import com.comunidapp.app.data.model.Conversation
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.leo.LeoTextField
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandOrange
import com.comunidapp.app.ui.theme.BrandOrangeContainer
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.LeoBody
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.SurfaceMuted
import com.comunidapp.app.ui.theme.UrgentRed
import com.comunidapp.app.viewmodel.ChatListViewModel
import com.comunidapp.app.viewmodel.ChatStartState
import com.comunidapp.app.viewmodel.ChatStartViewModel
import com.comunidapp.app.viewmodel.ChatThreadViewModel
import com.comunidapp.app.viewmodel.SendMessageState
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun ChatListScreen(
    onNavigateBack: () -> Unit,
    onConversationClick: (String, String) -> Unit,
    viewModel: ChatListViewModel = viewModel()
) {
    val conversations by viewModel.conversations.collectAsState()

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Mensajes",
                subtitle = "Tus conversaciones",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                LeoEmptyState(
                    title = "Todavía no tenés conversaciones",
                    message = "Cuando alguien te escriba, van a aparecer acá."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = LeoDimens.SpaceS)
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationCard(
                        conversation = conversation,
                        onClick = { onConversationClick(conversation.id, conversation.peerName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationCard(
    conversation: Conversation,
    onClick: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val time = conversation.lastMessageAt?.let {
        SimpleDateFormat("HH:mm", locale).format(Date(it))
    }.orEmpty()
    val initial = conversation.peerName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "·"
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceCompact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(SurfaceMuted, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = initial, style = LeoCardTitle, color = BrandText)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.peerName,
                    style = LeoCardTitle,
                    color = BrandText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                conversation.lastMessageText?.let { preview ->
                    Text(
                        text = preview,
                        style = LeoCaption,
                        color = BrandTextSecondary,
                        modifier = Modifier.padding(top = LeoDimens.SpaceMicro),
                        maxLines = 1
                    )
                }
            }
            if (time.isNotBlank()) {
                Text(text = time, style = LeoCaption, color = BrandTextSecondary)
            }
        }
        LeoHairline(modifier = Modifier.padding(start = LeoDimens.SpaceMd))
    }
}

@Composable
fun ChatStartScreen(
    onNavigateBack: () -> Unit,
    onConversationReady: (String) -> Unit,
    viewModel: ChatStartViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    val readyConversationId = (state as? ChatStartState.Ready)?.conversationId
    LaunchedEffect(readyConversationId) {
        if (!readyConversationId.isNullOrBlank()) {
            onConversationReady(readyConversationId)
        }
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Abriendo chat…",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (val current = state) {
                ChatStartState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = BrandOrange,
                    strokeWidth = 2.dp
                )
                is ChatStartState.Error -> Text(
                    text = current.message,
                    color = UrgentRed,
                    style = LeoCaption
                )
                is ChatStartState.Ready -> CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = BrandOrange,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
fun ChatThreadScreen(
    peerName: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatThreadViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val sendState by viewModel.sendState.collectAsState()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val currentUserId = AuthProvider.repository.getCurrentUser()?.id
    var didInitialScroll by remember { mutableStateOf(false) }

    LaunchedEffect(sendState) {
        if (sendState is SendMessageState.Sent) {
            draft = ""
            viewModel.clearSendState()
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !didInitialScroll) {
            listState.scrollToItem(messages.lastIndex)
            didInitialScroll = true
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, messages.firstOrNull()?.id) {
        if (didInitialScroll && listState.firstVisibleItemIndex == 0 && messages.isNotEmpty()) {
            viewModel.loadOlder()
        }
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = peerName,
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isMine = message.senderId == currentUserId
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LeoTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "Escribí un mensaje…",
                    singleLine = false,
                    minLines = 1
                )
                IconButton(
                    onClick = { viewModel.sendMessage(draft) },
                    enabled = draft.isNotBlank() && sendState !is SendMessageState.Sending
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                }
            }
            if (sendState is SendMessageState.Error) {
                Text(
                    text = (sendState as SendMessageState.Error).message,
                    color = UrgentRed,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, isMine: Boolean) {
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (isMine) BrandOrangeContainer else BrandWhite
    val locale = LocalConfiguration.current.locales[0]
    val time = message.createdAt?.let {
        SimpleDateFormat("HH:mm", locale).format(Date(it))
    }.orEmpty()

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(color, RoundedCornerShape(18.dp))
                .padding(horizontal = LeoDimens.SpaceCompact, vertical = LeoDimens.SpaceS)
        ) {
            if (!isMine) {
                Text(
                    text = message.senderName,
                    style = LeoCaption,
                    color = BrandText
                )
            }
            val share = com.comunidapp.app.domain.social.InternalShareCodec.decode(message.content)
            if (share != null) {
                val availability = com.comunidapp.app.domain.social.SharedContentPolicy.storyAvailability(
                    share,
                    System.currentTimeMillis(),
                    storyStillReachable = share.expiresAtEpochMs == null ||
                        (share.expiresAtEpochMs ?: 0L) > System.currentTimeMillis()
                )
                Text(
                    text = when (availability) {
                        com.comunidapp.app.domain.social.SharedContentAvailability.EXPIRED_STORY ->
                            com.comunidapp.app.domain.social.SharedContentPolicy.EXPIRED_STORY_COPY
                        com.comunidapp.app.domain.social.SharedContentAvailability.PERMISSION_DENIED ->
                            "No tenés acceso a este contenido"
                        com.comunidapp.app.domain.social.SharedContentAvailability.AVAILABLE ->
                            com.comunidapp.app.domain.social.InternalShareCodec.visibleCaption(message.content)
                    },
                    style = LeoBody,
                    color = BrandText
                )
                if (availability == com.comunidapp.app.domain.social.SharedContentAvailability.AVAILABLE) {
                    com.comunidapp.app.ui.screens.social.SharedContentCard(share = share)
                }
            } else {
                Text(text = message.content, style = LeoBody, color = BrandText)
            }
            if (time.isNotBlank()) {
                Text(
                    text = time,
                    style = LeoCaption,
                    color = BrandTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
