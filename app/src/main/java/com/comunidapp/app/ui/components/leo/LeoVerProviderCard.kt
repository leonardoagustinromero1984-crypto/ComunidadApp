package com.comunidapp.app.ui.components.leo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.data.model.ServiceProfile
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.v2.V2CompactCta
import com.comunidapp.app.ui.components.v2.V2SurfaceCard
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.ui.theme.ProfileGreen

/**
 * Canonical Community provider card (UI-01).
 * Protects thumbnail + category + optional rating + locality + "Ver perfil".
 */
@Composable
fun LeoVerProviderCard(
    service: ServiceProfile,
    onClick: () -> Unit,
    localityLabel: String? = null
) {
    val place = localityLabel?.takeIf { it.isNotBlank() } ?: service.location.takeIf { it.isNotBlank() }
    val distance = service.distanceKm?.let { km ->
        if (km < 10) String.format("%.1f km", km) else "${km.toInt()} km"
    }
    val placeLine = listOfNotNull(distance, place).joinToString(" · ")
    V2SurfaceCard(onClick = onClick, radius = LeoDimens.RadiusCard) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceM)
        ) {
            PetImage(
                imageUrl = service.photoUrl,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(LeoDimens.RadiusCard)),
                cornerRadius = LeoDimens.RadiusCard,
                contentDescription = service.name,
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = service.name,
                    style = LeoCardTitle,
                    color = BrandText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = providerCategoryLabel(service.category),
                    style = LeoCaption,
                    color = BrandTextSecondary
                )
                val rating = service.rating
                val reviews = service.reviewCount
                if (rating != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = ProfileGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (reviews != null) {
                                " ${"%.1f".format(rating)} ($reviews)"
                            } else {
                                " ${"%.1f".format(rating)}"
                            },
                            style = LeoCaption,
                            color = BrandText
                        )
                    }
                }
                if (placeLine.isNotBlank()) {
                    Text(
                        text = placeLine,
                        style = LeoCaption,
                        color = MutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            V2CompactCta(text = "Ver perfil", onClick = onClick)
        }
    }
}

fun providerCategoryLabel(category: ServiceCategory): String = when (category) {
    ServiceCategory.VET -> "Veterinaria"
    ServiceCategory.WALKER -> "Paseador de perros"
    ServiceCategory.TRAINER -> "Adiestrador canino"
    ServiceCategory.SHOP -> "Tienda de mascotas"
    ServiceCategory.DAYCARE -> "Guardería canina"
    ServiceCategory.GROOMING -> "Peluquería"
    ServiceCategory.CAREGIVER -> "Cuidador"
    ServiceCategory.PET_FRIENDLY -> "Lugar pet friendly"
}
