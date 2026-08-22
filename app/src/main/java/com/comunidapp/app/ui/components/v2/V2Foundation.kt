package com.comunidapp.app.ui.components.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.ResolvedPetImage
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandGreen
import com.comunidapp.app.ui.theme.BrandGreenContainer
import com.comunidapp.app.ui.theme.BrandGreenDark
import com.comunidapp.app.ui.theme.BrandOrangeContainer
import com.comunidapp.app.ui.theme.BrandOrangeDeep
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoSectionTitle
import com.comunidapp.app.ui.theme.NeutralBorder
import com.comunidapp.app.ui.theme.UrgentContainer
import com.comunidapp.app.ui.theme.UrgentRed
import com.comunidapp.app.ui.theme.leoVisual

/** Fondo de pantalla V2 — canonical general background, not cream. */
val V2ScreenBackground = BrandBackground

@Composable
fun V2SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    radius: androidx.compose.ui.unit.Dp = LeoDimens.RadiusCardFeature,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = BrandWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, NeutralBorder.copy(alpha = 0.7f)),
        content = {
            Column(
                modifier = Modifier.padding(LeoDimens.SpaceCompact),
                content = content
            )
        }
    )
}

@Composable
fun V2SectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = LeoSectionTitle,
                color = BrandText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = LeoCaption,
                    color = BrandTextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = LeoCaption,
                fontWeight = FontWeight.SemiBold,
                color = BrandGreen,
                modifier = Modifier
                    .heightIn(min = LeoDimens.TouchMin)
                    .padding(start = LeoDimens.SpaceSm)
                    .clickable(onClick = onAction)
                    .padding(vertical = 14.dp)
            )
        }
    }
}

@Composable
fun V2NavRow(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    iconTint: Color = BrandGreenDark,
    iconContainer: Color = BrandGreenContainer,
    modifier: Modifier = Modifier
) {
    V2SurfaceCard(modifier = modifier, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.padding(11.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = LeoCardTitle,
                    color = BrandText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    style = LeoCaption,
                    color = BrandTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = BrandTextSecondary
            )
        }
    }
}

enum class V2StatusTone { Neutral, Positive, Action, Urgent }

@Composable
fun V2StatusChip(
    label: String,
    tone: V2StatusTone,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (tone) {
        V2StatusTone.Positive -> BrandGreenContainer to BrandGreenDark
        V2StatusTone.Action -> BrandOrangeContainer to BrandOrangeDeep
        V2StatusTone.Urgent -> UrgentContainer to UrgentRed
        V2StatusTone.Neutral -> NeutralBorder.copy(alpha = 0.45f) to BrandTextSecondary
    }
    Surface(
        modifier = modifier.semantics { contentDescription = label },
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(1.dp, NeutralBorder.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = LeoCaption,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
    }
}

@Composable
fun V2UrgentActionRow(
    lostLabel: String,
    foundLabel: String,
    onLost: () -> Unit,
    onFound: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
    ) {
        V2UrgentActionCard(
            label = lostLabel,
            onClick = onLost,
            container = leoVisual().error,
            modifier = Modifier.weight(1f)
        )
        V2UrgentActionCard(
            label = foundLabel,
            onClick = onFound,
            container = leoVisual().secondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun V2UrgentActionCard(
    label: String,
    onClick: () -> Unit,
    container: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 72.dp)
            .semantics { contentDescription = label },
        shape = RoundedCornerShape(LeoDimens.RadiusCard),
        color = container,
        contentColor = BrandWhite
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Pets,
                contentDescription = null,
                tint = BrandWhite,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = LeoCaption,
                fontWeight = FontWeight.SemiBold,
                color = BrandWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun V2CompactCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = text },
        shape = RoundedCornerShape(LeoDimens.RadiusChip),
        color = BrandGreen,
        contentColor = BrandWhite
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = LeoCaption,
            fontWeight = FontWeight.SemiBold,
            color = BrandWhite,
            maxLines = 1
        )
    }
}

fun petAgeSummary(pet: Pet): String? = when {
    pet.ageYears > 0 -> if (pet.ageYears == 1) "1 año" else "${pet.ageYears} años"
    pet.ageMonths > 0 -> if (pet.ageMonths == 1) "1 mes" else "${pet.ageMonths} meses"
    else -> null
}

@Composable
fun V2PetsStrip(
    pets: List<Pet>,
    onPetClick: (String) -> Unit,
    onAddPet: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm),
        contentPadding = PaddingValues(horizontal = LeoDimens.SpaceMd)
    ) {
        items(pets, key = { it.id }) { pet ->
            Column(
                modifier = Modifier
                    .width(88.dp)
                    .clickable { onPetClick(pet.id) }
            ) {
                ResolvedPetImage(
                    pet = pet,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(LeoDimens.RadiusCard)),
                    cornerRadius = LeoDimens.RadiusCard,
                    contentDescription = pet.name
                )
                Text(
                    text = pet.name,
                    style = LeoCaption,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
                petAgeSummary(pet)?.let { age ->
                    Text(
                        text = age,
                        style = LeoCaption,
                        color = BrandTextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
        item(key = "add_pet") {
            Column(
                modifier = Modifier
                    .width(88.dp)
                    .clickable(onClick = onAddPet),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(LeoDimens.RadiusCard)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(LeoDimens.RadiusCard),
                        color = BrandWhite,
                        border = BorderStroke(1.dp, BrandGreen.copy(alpha = 0.45f))
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Agregar mascota",
                                tint = BrandGreen,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "Agregar mascota",
                    style = LeoCaption,
                    color = BrandText,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
