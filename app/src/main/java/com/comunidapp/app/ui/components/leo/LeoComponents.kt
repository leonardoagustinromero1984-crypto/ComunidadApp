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
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.BrandGreen
import com.comunidapp.app.ui.theme.BrandGreenContainer
import com.comunidapp.app.ui.theme.BrandGreenDark
import com.comunidapp.app.ui.theme.BrandOrangeContainer
import com.comunidapp.app.ui.theme.BrandOrangeSoft
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandWhite
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
            .background(BrandCream)
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
                containerColor = BrandCream,
                titleContentColor = BrandText,
                navigationIconContentColor = BrandText,
                actionIconContentColor = BrandText
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
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LeoDimens.ButtonPrimaryHeight),
        shape = RoundedCornerShape(LeoDimens.RadiusCard),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandOrangeSoft,
            contentColor = BrandText,
            disabledContainerColor = BrandOrangeContainer,
            disabledContentColor = MutedText
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(LeoDimens.SpaceSm))
        }
        Text(text)
    }
}

@Composable
fun LeoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LeoDimens.ButtonSecondaryHeight),
        shape = RoundedCornerShape(LeoDimens.RadiusCard),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandGreenContainer,
            contentColor = BrandGreenDark
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(text)
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
        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandText)
    ) {
        Text(text)
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
        colors = CardDefaults.cardColors(containerColor = BrandWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorder)
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
        border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorder),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(LeoDimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
            }
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
            selectedContainerColor = BrandOrangeContainer,
            selectedLabelColor = BrandText,
            containerColor = BrandWhite,
            labelColor = MutedText
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
            placeholder = { Text(placeholder, color = MutedText) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MutedText)
            },
            singleLine = true,
            shape = RoundedCornerShape(LeoDimens.RadiusField),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BrandWhite,
                unfocusedContainerColor = BrandWhite,
                focusedBorderColor = BrandOrangeSoft,
                unfocusedBorderColor = NeutralBorder,
                focusedTextColor = BrandText,
                unfocusedTextColor = BrandText
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
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(BrandOrangeContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = BrandOrangeSoft, modifier = Modifier.size(36.dp))
            }
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
        border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorder),
        shadowElevation = 1.dp
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
        color = BrandWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorder)
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

@Preview(showBackground = true, backgroundColor = 0xFFFFF6EA, widthDp = 390)
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
