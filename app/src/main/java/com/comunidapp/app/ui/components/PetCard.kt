package com.comunidapp.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(64.dp)
            ) {
                PetImage(
                    imageUrl = pet.photoUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = pet.name
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pet.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!pet.status.equals("ACTIVE", ignoreCase = true)) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (pet.status.uppercase()) {
                                "ARCHIVED" -> "Archivada"
                                "DECEASED" -> "Fallecida"
                                else -> pet.status
                            },
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Text(
                    text = "${pet.species.toDisplayName()} · ${pet.sex.toDisplayName()} · ${pet.ageDisplay()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = pet.size.toDisplayName(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun AdoptionCard(
    post: AdoptionPost,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(72.dp)) {
                    PetImage(
                        imageUrl = post.photoUrl,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = post.name
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = post.shelterName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${post.species.toDisplayName()} · ${post.sex.toDisplayName()} · ${post.ageDisplay()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AdoptionStatusBadge(status = post.status)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = post.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "📍 ${post.location}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun AdoptionStatusBadge(status: AdoptionStatus) {
    val (label, color) = when (status) {
        AdoptionStatus.DRAFT -> "Borrador" to MaterialTheme.colorScheme.surfaceVariant
        AdoptionStatus.PUBLISHED -> "Publicada" to MaterialTheme.colorScheme.primaryContainer
        AdoptionStatus.PAUSED -> "Pausada" to MaterialTheme.colorScheme.tertiaryContainer
        AdoptionStatus.ADOPTED -> "Adoptada" to MaterialTheme.colorScheme.secondaryContainer
        AdoptionStatus.CLOSED -> "Cerrada" to MaterialTheme.colorScheme.errorContainer
    }
    Text(
        text = label,
        modifier = Modifier
            .background(color, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall
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
    return if (ageYears > 0) {
        if (ageMonths > 0) "$ageYears años, $ageMonths meses" else "$ageYears años"
    } else {
        "$ageMonths meses"
    }
}

fun AdoptionPost.ageDisplay(): String {
    return if (ageYears > 0) {
        if (ageMonths > 0) "$ageYears años" else "$ageYears años"
    } else {
        "$ageMonths meses"
    }
}
