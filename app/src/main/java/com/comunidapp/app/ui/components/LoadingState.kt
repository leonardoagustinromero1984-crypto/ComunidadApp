package com.comunidapp.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.comunidapp.app.ui.components.state.LoadingState as FoundationLoadingState

/** Compatibilidad: pantallas existentes. Preferir `ui.components.state.LoadingState`. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    FoundationLoadingState(contentModifier = modifier)
}
