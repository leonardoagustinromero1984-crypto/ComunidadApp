package com.comunidapp.app.ui.screens.social

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.leo.LeoSocialPostCard
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.PostDetailViewModel

@Composable
fun SocialPostDetailScreen(
    postId: String,
    onNavigateBack: () -> Unit,
    onAuthorClick: (String) -> Unit = {},
    viewModel: PostDetailViewModel = viewModel(factory = PostDetailViewModel.factory(postId))
) {
    val post by viewModel.post.collectAsState()
    val comments by viewModel.comments.collectAsState()
    var draft by remember { mutableStateOf("") }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Publicación",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(LeoDimens.SpaceMd)
        ) {
            val current = post
            if (current == null) {
                Text("Cargando publicación…", style = LeoCaption)
            } else {
                LeoSocialPostCard(
                    post = current,
                    onAuthorClick = onAuthorClick
                )
                Spacer(modifier = Modifier.height(LeoDimens.SpaceMd))
                Text("Comentarios", style = LeoCardTitle)
                if (comments.isEmpty()) {
                    Text(
                        "Todavía no hay comentarios.",
                        style = LeoCaption,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    comments.forEach { comment ->
                        Text(
                            text = comment.authorName.ifBlank { "Persona" },
                            style = LeoCardTitle,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Text(text = comment.content, style = LeoCaption)
                    }
                }
                Spacer(modifier = Modifier.height(LeoDimens.SpaceMd))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Escribí un comentario") }
                )
                Button(
                    onClick = {
                        viewModel.addComment(draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Publicar comentario")
                }
            }
        }
    }
}
