package com.comunidapp.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.theme.BrandGreenContainer
import com.comunidapp.app.ui.theme.BrandOrangeContainer
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.SurfaceMuted
import com.comunidapp.app.ui.theme.UrgentContainer
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies

@Composable
fun PetCard(
    pet: Pet,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceCompact),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SurfaceMuted)
            ) {
                ResolvedPetImage(
                    pet = pet,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = pet.name
                )
            }
            Spacer(modifier = Modifier.width(LeoDimens.SpaceCompact))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = com.comunidapp.app.domain.vitacora.import.VitacoraNumberQuery.petTitle(
                            com.comunidapp.app.domain.pets.PetDisplayName.of(pet.originKind, pet.name),
                            pet.publicVitacoraNumber
                        ),
                        style = LeoCardTitle,
                        color = BrandText,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!pet.status.equals("ACTIVE", ignoreCase = true)) {
                        Spacer(modifier = Modifier.width(LeoDimens.SpaceS))
                        Text(
                            text = when (pet.status.uppercase()) {
                                "ARCHIVED" -> "Archivada"
                                "DECEASED" -> "Fallecida"
                                else -> pet.status
                            },
                            modifier = Modifier
                                .background(
                                    SurfaceMuted,
                                    RoundedCornerShape(LeoDimens.RadiusSmall)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            style = LeoCaption,
                            color = BrandTextSecondary
                        )
                    }
                }
                Text(
                    text = "${pet.species.toDisplayName()} · ${pet.sex.toDisplayName()} · ${pet.ageDisplay()}",
                    style = LeoCaption,
                    color = BrandTextSecondary
                )
                Text(
                    text = pet.size.toDisplayName(),
                    style = LeoCaption,
                    color = BrandTextSecondary
                )
                pet.organizationExternalPetId?.takeIf { it.isNotBlank() }?.let { ref ->
                    Text(
                        text = "${com.comunidapp.app.domain.vitacora.import.VitacoraImportCopy.ORG_REF_LABEL}: $ref",
                        style = LeoCaption,
                        color = BrandTextSecondary
                    )
                }
            }
        }
        LeoHairline(modifier = Modifier.padding(start = LeoDimens.SpaceMd))
    }
}

@Composable
fun AdoptionCard(
    post: AdoptionPost,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceCompact)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SurfaceMuted)
            ) {
                PetImage(
                    imageUrl = post.photoUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = post.name
                )
            }
            Spacer(modifier = Modifier.width(LeoDimens.SpaceCompact))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.name,
                    style = LeoCardTitle,
                    color = BrandText
                )
                if (post.shelterName.isNotBlank() || !post.publisherOrganizationId.isNullOrBlank()) {
                    Text(
                        text = post.shelterName.ifBlank { "Organización" } +
                            if (!post.publisherOrganizationId.isNullOrBlank()) " ✓" else "",
                        style = LeoCaption,
                        color = BrandTextSecondary
                    )
                }
                Text(
                    text = "${post.species.toDisplayName()} · ${post.sex.toDisplayName()} · ${post.ageDisplay()}",
                    style = LeoCaption,
                    color = BrandTextSecondary
                )
            }
            AdoptionStatusBadge(status = post.status)
        }
        Spacer(modifier = Modifier.height(LeoDimens.SpaceS))
        Text(
            text = post.description,
            style = LeoCaption,
            color = BrandText,
            maxLines = 2
        )
        Text(
            text = post.location,
            style = LeoCaption,
            color = BrandTextSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )
        LeoHairline(modifier = Modifier.padding(top = LeoDimens.SpaceCompact))
    }
}

@Composable
fun AdoptionStatusBadge(status: AdoptionStatus) {
    val (label, color) = when (status) {
        AdoptionStatus.DRAFT -> "Borrador" to SurfaceMuted
        AdoptionStatus.PUBLISHED -> "Publicada" to BrandOrangeContainer
        AdoptionStatus.PAUSED -> "Pausada" to SurfaceMuted
        AdoptionStatus.ADOPTED -> "Adoptada" to BrandGreenContainer
        AdoptionStatus.CLOSED -> "Cerrada" to UrgentContainer
    }
    Text(
        text = label,
        modifier = Modifier
            .background(color, RoundedCornerShape(LeoDimens.RadiusSmall))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = LeoCaption,
        color = BrandText
    )
}

fun PetSpecies.toDisplayName(): String = when (this) {
    PetSpecies.DOG -> "Perro"
    PetSpecies.CAT -> "Gato"
    PetSpecies.HORSE -> "Caballo"
    PetSpecies.COW -> "Vaca"
    PetSpecies.SHEEP -> "Oveja"
    PetSpecies.GOAT -> "Cabra"
    PetSpecies.PIG -> "Cerdo"
    PetSpecies.RABBIT -> "Conejo"
    PetSpecies.HAMSTER -> "Hámster"
    PetSpecies.GUINEA_PIG -> "Cobayo / conejillo de indias"
    PetSpecies.BIRD -> "Ave"
    PetSpecies.FISH -> "Pez"
    PetSpecies.REPTILE -> "Reptil"
    PetSpecies.CHICKEN -> "Gallina / pollo"
    PetSpecies.DUCK -> "Pato"
    PetSpecies.DONKEY -> "Burro / asno"
    PetSpecies.OTHER -> "Otro"
}

fun PetSex.toDisplayName(): String = when (this) {
    PetSex.MALE -> "Macho"
    PetSex.FEMALE -> "Hembra"
    PetSex.UNKNOWN -> "Desconocido"
}

fun PetSize.toDisplayName(): String = when (this) {
    PetSize.SMALL -> "Pequeño"
    PetSize.MEDIUM -> "Mediano"
    PetSize.LARGE -> "Grande"
}

fun Pet.ageDisplay(): String {
    val precision = runCatching {
        com.comunidapp.app.domain.pets.PetBirthPrecision.valueOf(birthPrecision)
    }.getOrDefault(com.comunidapp.app.domain.pets.PetBirthPrecision.UNKNOWN)
    val canonicalBirth = com.comunidapp.app.domain.pets.PetBirth(
        precision = precision,
        birthDate = birthDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
        birthYear = birthYear,
        birthMonth = birthMonth,
        estimatedAgeMonths = estimatedAgeMonths,
        estimatedAsOf = estimatedAsOf?.let {
            runCatching { java.time.LocalDate.parse(it) }.getOrNull()
        }
    )
    if (precision != com.comunidapp.app.domain.pets.PetBirthPrecision.UNKNOWN &&
        canonicalBirth.isValid()
    ) {
        return canonicalBirth.display()
    }
    return when {
        ageYears == 1 -> "1 año"
        ageYears > 1 -> "$ageYears años"
        ageMonths == 1 -> "1 mes"
        ageMonths > 1 -> "$ageMonths meses"
        else -> "Edad desconocida"
    }
}

fun AdoptionPost.ageDisplay(): String {
    return if (ageYears > 0) {
        if (ageMonths > 0) "$ageYears años" else "$ageYears años"
    } else {
        "$ageMonths meses"
    }
}
