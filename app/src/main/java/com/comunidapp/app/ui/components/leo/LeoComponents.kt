package com.comunidapp.app.ui.components.leo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pets
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.leoVisual
import com.comunidapp.app.ui.theme.BrandGreen
import com.comunidapp.app.ui.theme.BrandGreenContainer
import com.comunidapp.app.ui.theme.BrandGreenDark
import com.comunidapp.app.ui.theme.BrandOrangeContainer
import com.comunidapp.app.ui.theme.BrandOrangeSoft
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.LeoButton
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoPageTitle
import com.comunidapp.app.ui.theme.LeoSectionTitle
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.ui.theme.NeutralBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeoTopAppBar(
    title: String,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .background(leoVisual().background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = LeoSectionTitle,
                    color = BrandText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (showBackButton) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = BrandText
                        )
                    }
                }
            },
            actions = actions,
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = leoVisual().background,
                titleContentColor = leoVisual().textPrimary,
                navigationIconContentColor = leoVisual().textPrimary,
                actionIconContentColor = leoVisual().textPrimary
            )
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = LeoCaption,
                color = MutedText,
                modifier = Modifier.padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceMicro)
            )
        }
    }
}

@Composable
fun LeoSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = LeoSectionTitle, color = BrandText)
            if (!subtitle.isNullOrBlank()) {
                Text(text = subtitle, style = LeoCaption, color = MutedText)
            }
        }
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = LeoCaption,
                color = BrandOrangeSoft,
                modifier = Modifier
                    .clip(RoundedCornerShape(LeoDimens.RadiusChip))
                    .clickable(onClick = onAction)
                    .padding(LeoDimens.SpaceSm)
            )
        }
    }
}

@Composable
fun LeoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fillMaxWidth: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = LeoDimens.ButtonPrimaryHeight),
        shape = RoundedCornerShape(LeoDimens.RadiusCard),
        colors = ButtonDefaults.buttonColors(
            containerColor = leoVisual().primary,
            contentColor = leoVisual().onPrimary,
            disabledContainerColor = leoVisual().primarySoft,
            disabledContentColor = leoVisual().textSecondary
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(LeoDimens.SpaceSm))
        }
        Text(text, style = LeoButton)
    }
}

@Composable
fun LeoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LeoDimens.ButtonSecondaryHeight),
        shape = RoundedCornerShape(LeoDimens.RadiusCard),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = leoVisual().textPrimary),
        border = androidx.compose.foundation.BorderStroke(1.dp, leoVisual().borderSoft)
    ) {
        Text(text, style = LeoButton)
    }
}

@Composable
fun LeoOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LeoDimens.ButtonSecondaryHeight),
        shape = RoundedCornerShape(LeoDimens.RadiusCard),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = leoVisual().textPrimary)
    ) {
        Text(text, style = LeoButton)
    }
}

@Composable
fun ContinueWithGoogleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val visual = leoVisual()
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LeoDimens.ButtonPrimaryHeight),
        shape = RoundedCornerShape(LeoDimens.RadiusCard),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = visual.surface,
            contentColor = visual.textPrimary
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, visual.borderSoft)
    ) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.comunidapp.app.R.drawable.ic_google_g),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(LeoDimens.SpaceS))
            Text("Continuar con Google", style = LeoButton)
        }
    }
}

@Composable
fun AuthMethodDivider(
    modifier: Modifier = Modifier
) {
    val visual = leoVisual()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = visual.borderSoft
        )
        Text(
            text = "o",
            style = LeoCaption,
            color = visual.textSecondary,
            modifier = Modifier.padding(horizontal = LeoDimens.SpaceS)
        )
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = visual.borderSoft
        )
    }
}

@Composable
fun LeoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(LeoDimens.RadiusCard)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = leoVisual().surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(LeoDimens.SpaceMd), content = content)
    }
}

@Composable
fun LeoFeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = BrandOrangeContainer,
    iconTint: Color = BrandOrangeSoft
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LeoDimens.TouchMin)
            .semantics { contentDescription = title },
        shape = RoundedCornerShape(LeoDimens.RadiusCardFeature),
        color = BrandWhite,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(LeoDimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(LeoDimens.SpaceCompact))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = LeoCardTitle, color = BrandText)
                Text(
                    text = description,
                    style = LeoCaption,
                    color = MutedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MutedText
            )
        }
    }
}

/**
 * Legacy Community tile. Do not use on Comunidad — use [com.comunidapp.app.ui.components.leo.LeoVerProviderCard].
 */
@Deprecated(
    message = "Use canonical LeoVer UI component",
    level = DeprecationLevel.ERROR
)
@Composable
fun LeoServiceTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = BrandWhite,
    iconTint: Color = BrandText,
    iconContainerColor: Color? = null,
    borderColor: Color = NeutralBorder,
    badge: String? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 96.dp)
            .semantics { contentDescription = title },
        shape = RoundedCornerShape(LeoDimens.RadiusCard),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        shadowElevation = 1.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LeoDimens.SpaceCompact),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            val tone = iconContainerColor
            if (tone != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tone),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
            } else {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
            }
            Text(text = title, style = LeoCardTitle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize), color = BrandText, maxLines = 2)
            if (badge != null) {
                Text(text = badge, style = LeoCaption, color = MutedText)
            }
        }
    }
}

@Composable
fun LeoFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier.heightIn(min = LeoDimens.ChipHeight),
        shape = RoundedCornerShape(LeoDimens.RadiusChip),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = leoVisual().primarySoft,
            selectedLabelColor = leoVisual().primaryDark,
            containerColor = leoVisual().surface,
            labelColor = leoVisual().textSecondary
        )
    )
}

@Composable
fun LeoSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Buscar",
    onFilterClick: (() -> Unit)? = null,
    activeFiltersCount: Int = 0
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = LeoDimens.SearchBarHeight),
            placeholder = { Text(placeholder, color = leoVisual().textSecondary) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = leoVisual().textSecondary)
            },
            singleLine = true,
            shape = RoundedCornerShape(LeoDimens.RadiusField),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = leoVisual().surface,
                unfocusedContainerColor = leoVisual().surface,
                focusedBorderColor = leoVisual().primary,
                unfocusedBorderColor = leoVisual().borderSoft,
                focusedTextColor = leoVisual().textPrimary,
                unfocusedTextColor = leoVisual().textPrimary
            )
        )
        if (onFilterClick != null) {
            Surface(
                onClick = onFilterClick,
                modifier = Modifier
                    .heightIn(min = LeoDimens.TouchMin)
                    .semantics {
                        contentDescription = if (activeFiltersCount > 0) {
                            "Filtros, $activeFiltersCount activos"
                        } else {
                            "Filtros"
                        }
                    },
                shape = RoundedCornerShape(LeoDimens.RadiusField),
                color = if (activeFiltersCount > 0) BrandOrangeContainer else BrandWhite,
                border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = LeoDimens.SpaceCompact, vertical = LeoDimens.SpaceSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = BrandOrangeSoft)
                    if (activeFiltersCount > 0) {
                        Spacer(modifier = Modifier.width(LeoDimens.SpaceMicro))
                        Text(text = "$activeFiltersCount", style = LeoCaption, color = BrandText)
                    }
                }
            }
        }
    }
}

@Composable
fun LeoEmptyState(
    title: String,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(LeoDimens.SpaceSection)
            .semantics { contentDescription = title },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MutedText,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(LeoDimens.SpaceSm))
        }
        Text(text = title, style = LeoSectionTitle, color = BrandText, textAlign = TextAlign.Center)
        if (!message.isNullOrBlank()) {
            Text(text = message, style = LeoCaption, color = MutedText, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(LeoDimens.SpaceSm))
            LeoPrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth(0.75f))
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            LeoSecondaryButton(
                text = secondaryActionLabel,
                onClick = onSecondaryAction,
                modifier = Modifier.fillMaxWidth(0.75f)
            )
        }
    }
}

@Composable
fun LeoSettingsRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LeoDimens.TouchMin)
            .clickable(onClick = onClick)
            .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceCompact),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = BrandOrangeSoft, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(LeoDimens.SpaceCompact))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = LeoCardTitle, color = BrandText)
            if (!description.isNullOrBlank()) {
                Text(text = description, style = LeoCaption, color = MutedText)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MutedText)
    }
}

@Composable
fun LeoQuickActionTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = BrandOrangeContainer,
    iconTint: Color = BrandOrangeSoft
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 104.dp)
            .semantics { contentDescription = title },
        shape = RoundedCornerShape(LeoDimens.RadiusCard),
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(28.dp))
            Text(text = title, style = LeoCardTitle, color = BrandText, maxLines = 2)
        }
    }
}

@Composable
fun LeoGreetingHeader(
    name: String?,
    onSearch: () -> Unit,
    onNotifications: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val greeting = if (name.isNullOrBlank()) "¡Hola!" else "¡Hola, ${name.trim().substringBefore(' ')}!"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceSm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = greeting, style = LeoPageTitle, color = BrandText)
            Text(text = "¿Qué hacemos hoy?", style = LeoCaption, color = MutedText)
        }
        Row {
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = "Buscar", tint = BrandText)
            }
        }
    }
}

@Composable
fun LeoPetCard(
    name: String,
    speciesLabel: String,
    ageLabel: String,
    statusLabel: String?,
    photoUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageContent: @Composable (Modifier: Modifier) -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(168.dp)
            .semantics { contentDescription = name },
        shape = RoundedCornerShape(LeoDimens.RadiusCard),
        color = BrandWhite,
        shadowElevation = 0.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(BrandOrangeContainer)
            ) {
                imageContent(Modifier.fillMaxWidth().height(110.dp))
            }
            Column(modifier = Modifier.padding(LeoDimens.SpaceCompact)) {
                Text(text = name, style = LeoCardTitle, color = BrandText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "$speciesLabel · $ageLabel", style = LeoCaption, color = MutedText, maxLines = 1)
                if (!statusLabel.isNullOrBlank()) {
                    Text(text = statusLabel, style = LeoCaption, color = BrandGreenDark)
                }
            }
        }
    }
}

@Composable
fun LeoPersonCard(
    name: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(120.dp)
            .semantics { contentDescription = name },
        shape = RoundedCornerShape(LeoDimens.RadiusCard),
        color = BrandWhite
    ) {
        Column(
            modifier = Modifier.padding(LeoDimens.SpaceCompact),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            avatar()
            Text(text = name, style = LeoCardTitle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize), color = BrandText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(text = subtitle, style = LeoCaption, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun LeoHairline(
    modifier: Modifier = Modifier,
    color: Color = leoVisual().borderSoft,
    thickness: androidx.compose.ui.unit.Dp = 0.5.dp
) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = thickness,
        color = color
    )
}

@Composable
fun LeoListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LeoDimens.TouchMin)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceCompact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = LeoCardTitle,
                    color = BrandText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = LeoCaption,
                        color = MutedText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailing?.invoke()
        }
        if (showDivider) {
            LeoHairline(modifier = Modifier.padding(start = LeoDimens.SpaceMd))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFDF8, widthDp = 390)
@Composable
private fun LeoComponentsPreview() {
    ComunidappTheme {
        Column(
            modifier = Modifier.padding(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            LeoGreetingHeader(name = "Leonardo", onSearch = {})
            LeoPrimaryButton(text = "Acción principal", onClick = {}, icon = Icons.Default.Favorite)
            LeoSecondaryButton(text = "Acción secundaria", onClick = {})
            LeoFeatureCard(
                title = "Refugios",
                description = "Organizaciones cercanas",
                icon = Icons.Default.Home,
                onClick = {}
            )
            LeoSettingsRow(title = "Configuración", description = "Privacidad y cuenta", icon = Icons.Default.Pets, onClick = {})
            LeoEmptyState(
                title = "Tu comunidad todavía está tranquila",
                message = "Sé la primera persona en compartir algo.",
                actionLabel = "Crear publicación",
                onAction = {},
                icon = Icons.Default.Pets
            )
        }
    }
}
