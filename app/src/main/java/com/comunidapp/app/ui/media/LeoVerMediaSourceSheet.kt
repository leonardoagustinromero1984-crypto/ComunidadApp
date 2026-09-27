package com.comunidapp.app.ui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.MutedText

enum class LeoVerMediaSource {
    TAKE_PHOTO,
    RECORD_VIDEO,
    PICK_PHOTOS,
    PICK_VIDEO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeoVerMediaSourceSheet(
    visible: Boolean,
    title: String = "Agregar media",
    onDismiss: () -> Unit,
    onSelect: (LeoVerMediaSource) -> Unit
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceSm)) {
            Text(title, style = LeoCardTitle, color = BrandText)
            Text(
                "Foto o video. Un video en Publicación sigue siendo una publicación, no un Clip.",
                style = LeoCaption,
                color = MutedText,
                modifier = Modifier.padding(top = LeoDimens.SpaceXs, bottom = LeoDimens.SpaceSm)
            )
            LeoVerMediaSource.entries.forEach { source ->
                Text(
                    text = when (source) {
                        LeoVerMediaSource.TAKE_PHOTO -> "Tomar foto"
                        LeoVerMediaSource.RECORD_VIDEO -> "Grabar video"
                        LeoVerMediaSource.PICK_PHOTOS -> "Elegir foto"
                        LeoVerMediaSource.PICK_VIDEO -> "Elegir video"
                    },
                    style = LeoCardTitle,
                    color = BrandText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onSelect(source)
                        }
                        .padding(vertical = LeoDimens.SpaceCompact)
                )
                LeoHairline()
            }
            LeoOutlinedButton(text = "Cancelar", onClick = onDismiss)
        }
    }
}
