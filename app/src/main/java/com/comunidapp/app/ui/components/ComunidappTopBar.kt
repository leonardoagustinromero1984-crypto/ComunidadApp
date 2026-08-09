package com.comunidapp.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import com.comunidapp.app.ui.components.leo.LeoTopAppBar

/**
 * App bar LeoVer — fondo crema, texto BrandText (sin barra naranja completa).
 * Delega en [LeoTopAppBar] para mantener compatibilidad de call-sites.
 */
@Composable
fun ComunidappTopBar(
    title: String,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    LeoTopAppBar(
        title = title,
        showBackButton = showBackButton,
        onBackClick = onBackClick,
        actions = actions
    )
}
