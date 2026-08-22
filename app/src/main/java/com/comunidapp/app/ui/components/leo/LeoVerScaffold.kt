package com.comunidapp.app.ui.components.leo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.comunidapp.app.ui.theme.BrandBackground

/**
 * Canonical LeoVer screen chrome (UI-01).
 *
 * New production screens should start here instead of a raw Material Scaffold
 * with a one-off background color.
 */
@Composable
fun LeoVerScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier.imePadding(),
        containerColor = BrandBackground,
        topBar = {
            if (title != null) {
                LeoTopAppBar(
                    title = title,
                    showBackButton = showBackButton,
                    onBackClick = onBackClick,
                    subtitle = subtitle,
                    actions = actions
                )
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        content = content
    )
}
