package com.comunidapp.app.ui.screens.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.model.Conversation
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.ChatPersonHit
import com.comunidapp.app.domain.social.InternalShareCodec
import com.comunidapp.app.domain.social.SharedContentReference
import com.comunidapp.app.domain.social.SocialContentKind
import com.comunidapp.app.domain.social.SocialShare
import com.comunidapp.app.ui.theme.LeoDimens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalShareSheet(
    post: FeedPost,
    onDismiss: () -> Unit,
    onShared: () -> Unit
) {
    val conversations by DataProvider.chatRepository
        .observeConversations(AuthProvider.repository.getCurrentUser()?.id.orEmpty())
        .collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var people by remember { mutableStateOf<List<ChatPersonHit>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            people = emptyList()
            return@LaunchedEffect
        }
        people = runCatching { DataProvider.chatRepository.searchPeople(query.trim()) }.getOrDefault(emptyList())
    }
    val kind = when (post.type.name) {
        "REEL" -> SocialContentKind.REEL
        "STORY" -> SocialContentKind.STORY
        else -> SocialContentKind.POST
    }
    val reference = SharedContentReference(
        contentType = kind,
        contentId = post.id,
        authorName = post.authorName,
        authorId = post.authorId,
        thumbnailUrl = post.imageUrl,
        captionPreview = post.content.take(140),
        deepLink = SocialShare.deepLink(kind, post.id),
        expiresAtEpochMs = post.expiresAt
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(LeoDimens.SpaceMd)) {
            Text("Enviar por LeoVer")
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar persona") }
            )
            status?.let { Text(it, modifier = Modifier.padding(vertical = 8.dp)) }
            Text("Conversaciones recientes", modifier = Modifier.padding(top = 8.dp))
            LazyColumn {
                items(conversations, key = { it.id }) { conversation ->
                    ShareRow(conversation.peerName, conversation.peerUserId.takeIf { it.isNotBlank() }?.let { "@$it" }) {
                        scope.launch {
                            sendShare(conversation, reference)
                            onShared()
                            onDismiss()
                        }
                    }
                }
                if (people.isNotEmpty()) {
                    item { Text("Personas", modifier = Modifier.padding(top = 8.dp)) }
                    items(people, key = { it.userId }) { person ->
                        ShareRow(person.displayName, "@${person.username}") {
                            scope.launch {
                                val user = AuthProvider.repository.getCurrentUser() ?: return@launch
                                DataProvider.chatRepository.getOrCreateConversation(
                                    user, person.userId, person.displayName
                                ).onSuccess { id ->
                                    DataProvider.chatRepository.sendMessage(
                                        id, user, InternalShareCodec.encode(reference)
                                    )
                                    onShared()
                                    onDismiss()
                                }.onFailure {
                                    status = "No pudimos enviar el mensaje."
                                }
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    }
}

@Composable
private fun ShareRow(name: String, username: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Column {
            Text(name)
            if (!username.isNullOrBlank() && username != "@") Text(username)
        }
    }
}

private suspend fun sendShare(conversation: Conversation, reference: SharedContentReference) {
    val user = AuthProvider.repository.getCurrentUser() ?: return
    DataProvider.chatRepository.sendMessage(
        conversation.id,
        user,
        InternalShareCodec.encode(reference)
    )
}
