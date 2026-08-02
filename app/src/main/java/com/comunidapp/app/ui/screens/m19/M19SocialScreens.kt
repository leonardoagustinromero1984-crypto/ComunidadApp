package com.comunidapp.app.ui.screens.m19

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.M19PostStatus
import com.comunidapp.app.data.model.M19PublicPost
import com.comunidapp.app.data.model.M19ReactionType
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M19PostDetailViewModel
import com.comunidapp.app.viewmodel.M19PostEditUiState
import com.comunidapp.app.viewmodel.M19PostEditViewModel
import com.comunidapp.app.viewmodel.M19PostsManageUiState
import com.comunidapp.app.viewmodel.M19PostsManageViewModel
import com.comunidapp.app.viewmodel.M19SocialFeedUiState
import com.comunidapp.app.viewmodel.M19SocialFeedViewModel
import com.comunidapp.app.viewmodel.m19PostStatusLabel
import com.comunidapp.app.viewmodel.m19ReactionTypeLabel

@Composable
fun M19SocialFeedScreen(
    onNavigateBack: () -> Unit,
    onPostClick: (String) -> Unit,
    onManage: () -> Unit,
    onCreate: () -> Unit,
    viewModel: M19SocialFeedViewModel = viewModel(factory = M19SocialFeedViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var query by remember(filter.query) { mutableStateOf(filter.query) }

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Feed comunitario", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Feed M19 — publicaciones, comentarios y reacciones sin PII.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.setQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar publicación") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.clearFilters() }) { Text("Limpiar filtros") }
                OutlinedButton(onClick = onManage) { Text("Administrar") }
                Button(onClick = onCreate) { Text("Nueva") }
            }
            when (val s = state) {
                M19SocialFeedUiState.Loading -> LoadingState()
                M19SocialFeedUiState.Empty -> EmptyState(
                    title = "Sin publicaciones",
                    message = "No hay contenido publicado con estos filtros."
                )
                is M19SocialFeedUiState.Error -> ErrorState(message = s.message, onRetry = { viewModel.load() })
                is M19SocialFeedUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.items, key = { it.id }) { item ->
                        M19PostCard(item, onClick = { onPostClick(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun M19PostCard(post: M19PublicPost, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(post.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(post.organizationDisplayName, style = MaterialTheme.typography.bodySmall)
            Text(post.authorDisplayName, style = MaterialTheme.typography.labelMedium)
            Text(post.content, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            Text(
                "👍 ${post.likeCount} · 🤝 ${post.supportCount} · 🎉 ${post.celebrateCount} · 💬 ${post.commentCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun M19PostDetailScreen(
    postId: String,
    onNavigateBack: () -> Unit,
    viewModel: M19PostDetailViewModel = viewModel(factory = M19PostDetailViewModel.factory(postId))
) {
    val post by viewModel.post.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val myReaction by viewModel.myReaction.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()
    var commentText by remember { mutableStateOf("") }

    LaunchedEffect(message) {
        if (message != null) viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Detalle publicación", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when {
            loading -> LoadingState(contentModifier = Modifier.padding(padding))
            post == null -> ErrorState(
                message = "Publicación no disponible",
                contentModifier = Modifier.padding(padding)
            )
            else -> {
                val p = post!!
                Column(
                    Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(p.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(p.organizationDisplayName, style = MaterialTheme.typography.bodySmall)
                    Text(p.content, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Reacciones: 👍 ${p.likeCount} · 🤝 ${p.supportCount} · 🎉 ${p.celebrateCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        M19ReactionType.entries.forEach { type ->
                            val selected = myReaction == type
                            if (selected) {
                                OutlinedButton(onClick = { viewModel.removeReaction() }) {
                                    Text("${m19ReactionTypeLabel(type)} ✓")
                                }
                            } else {
                                Button(onClick = { viewModel.react(type) }) {
                                    Text(m19ReactionTypeLabel(type))
                                }
                            }
                        }
                    }
                    Text("Comentarios (${comments.size})", style = MaterialTheme.typography.titleMedium)
                    comments.forEach { c ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(c.authorDisplayName, fontWeight = FontWeight.SemiBold)
                                Text(c.content)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tu comentario") }
                    )
                    Button(
                        onClick = {
                            viewModel.addComment(commentText)
                            commentText = ""
                        },
                        enabled = commentText.isNotBlank()
                    ) { Text("Comentar") }
                }
            }
        }
    }
}

@Composable
fun M19PostsManageScreen(
    onNavigateBack: () -> Unit,
    onEditPost: (String) -> Unit,
    onCreate: () -> Unit,
    viewModel: M19PostsManageViewModel = viewModel(factory = M19PostsManageViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(message) {
        if (message != null) viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Administrar publicaciones", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Button(onClick = onCreate, modifier = Modifier.padding(bottom = 12.dp)) { Text("Nueva publicación") }
            when (val s = state) {
                M19PostsManageUiState.Loading -> LoadingState()
                M19PostsManageUiState.Empty -> EmptyState(title = "Sin publicaciones", message = "Creá la primera.")
                is M19PostsManageUiState.Error -> ErrorState(message = s.message)
                is M19PostsManageUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.items, key = { it.id }) { item ->
                        Card(Modifier.fillMaxWidth().clickable { onEditPost(item.id) }) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(item.title, fontWeight = FontWeight.Bold)
                                Text(m19PostStatusLabel(item.status), style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (item.status == M19PostStatus.DRAFT || item.status == M19PostStatus.HIDDEN) {
                                        Button(onClick = { viewModel.publish(item.id) }) { Text("Publicar") }
                                    }
                                    if (item.status == M19PostStatus.PUBLISHED) {
                                        OutlinedButton(onClick = { viewModel.hide(item.id) }) { Text("Ocultar") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M19PostEditScreen(
    postId: String?,
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: M19PostEditViewModel = viewModel(factory = M19PostEditViewModel.factory(postId))
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is M19PostEditUiState.Form) {
            val form = state as M19PostEditUiState.Form
            title = form.title
            content = form.content
        }
    }
    LaunchedEffect(message) {
        if (message != null) viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = if (postId == null) "Nueva publicación" else "Editar publicación",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when (val s = state) {
            M19PostEditUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            is M19PostEditUiState.Error -> ErrorState(message = s.message, contentModifier = Modifier.padding(padding))
            is M19PostEditUiState.Form -> Column(
                Modifier.padding(padding).padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Título") }
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Contenido") },
                    minLines = 4
                )
                Button(onClick = { viewModel.save(title, content, onSaved) }) {
                    Text(if (s.isEdit) "Guardar" else "Crear borrador")
                }
            }
        }
    }
}
