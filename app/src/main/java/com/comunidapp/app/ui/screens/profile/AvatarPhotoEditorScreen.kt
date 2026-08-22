package com.comunidapp.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.comunidapp.app.domain.media.AvatarPhotoEditorState
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.media.LeoVerCropShape
import com.comunidapp.app.ui.media.LeoVerMediaCropper
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens

@Composable
fun AvatarPhotoEditorScreen(
    sourceUri: String,
    onCancel: () -> Unit,
    onConfirm: (AvatarPhotoEditorState) -> Unit,
    processing: Boolean = false
) {
    var state by remember(sourceUri) { mutableStateOf(AvatarPhotoEditorState(sourceUri)) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B1B1B))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LeoDimens.SpaceL, vertical = LeoDimens.SpaceS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancelar", color = Color.White)
            }
            Spacer(modifier = Modifier.weight(1f))
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            LeoVerMediaCropper(
                state = state,
                onStateChange = { state = it },
                shape = LeoVerCropShape.CIRCLE
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LeoDimens.SpaceL, vertical = LeoDimens.SpaceL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Mové y ajustá la foto. Pellizcá para acercar. Lo que ves es lo que se guarda.",
                style = LeoCaption,
                color = Color.White.copy(alpha = 0.86f)
            )
            Spacer(modifier = Modifier.size(LeoDimens.SpaceS))
            TextButton(onClick = { state = state.recenter() }) {
                Text("Centrar", color = Color.White)
            }
            Spacer(modifier = Modifier.size(LeoDimens.SpaceM))
            LeoPrimaryButton(
                text = if (processing) "Preparando foto…" else "Usar foto",
                enabled = !processing,
                onClick = { onConfirm(state) }
            )
        }
    }
}
