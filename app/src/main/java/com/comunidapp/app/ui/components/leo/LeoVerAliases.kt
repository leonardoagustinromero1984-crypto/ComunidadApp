package com.comunidapp.app.ui.components.leo

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.ui.components.ComunidappBottomBar
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.components.v2.V2LocationStringPicker

/**
 * Canonical names (UI-01) mapped onto existing LeoVer components.
 * Do not create a second visual library — these are aliases only.
 */

@Composable
fun LeoVerTopBar(
    title: String,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) = LeoTopAppBar(title, showBackButton, onBackClick, subtitle, actions)

@Composable
fun LeoVerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) = LeoCard(modifier, onClick, content)

@Composable
fun LeoVerPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) = LeoPrimaryButton(text, onClick, modifier, enabled, icon)

@Composable
fun LeoVerSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) = LeoSecondaryButton(text, onClick, modifier, enabled)

@Composable
fun LeoVerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = LeoFilterChip(label, selected, onClick, modifier)

@Composable
fun LeoVerFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = LeoFilterChip(label, selected, onClick, modifier)

@Composable
fun LeoVerSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) = LeoSectionHeader(title, subtitle, modifier, actionLabel, onAction)

@Composable
fun LeoVerProfileHeader(
    name: String?,
    onSearch: () -> Unit,
    onNotifications: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) = LeoGreetingHeader(name, onSearch, onNotifications, modifier)

@Composable
fun LeoVerListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true
) = LeoListRow(title, modifier, subtitle, leading, trailing, onClick, showDivider)

@Composable
fun LeoVerEmptyState(
    title: String,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) = LeoEmptyState(
    title, message, actionLabel, onAction, secondaryActionLabel, onSecondaryAction, icon, modifier
)

@Composable
fun LeoVerLoadingState(
    contentModifier: Modifier = Modifier,
    contentDescription: String = "Cargando"
) = LoadingState(contentModifier, contentDescription)

@Composable
fun LeoVerErrorState(
    message: String,
    contentModifier: Modifier = Modifier,
    title: String = "Algo salió mal",
    retryLabel: String = "Reintentar",
    onRetry: (() -> Unit)? = null
) = ErrorState(message, contentModifier, title, retryLabel, onRetry)

@Composable
fun LeoVerLocationSelector(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    includeZone: Boolean = false,
    enabled: Boolean = true
) = V2LocationStringPicker(value, onValueChange, modifier, includeZone, enabled)

@Composable
fun LeoVerBottomNavigation(
    navController: NavController,
    context: OperationalContext
) = ComunidappBottomBar(navController = navController, context = context)
