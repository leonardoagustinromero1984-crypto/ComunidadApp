package com.comunidapp.app.ui.screens.pets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.VaccinationRecord
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.BrandGreen
import com.comunidapp.app.ui.theme.BrandGreenContainer
import com.comunidapp.app.ui.theme.BrandGreenDark
import com.comunidapp.app.ui.theme.BrandOrange
import com.comunidapp.app.ui.theme.BrandOrangeDeep
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.NeutralBorder
import com.comunidapp.app.ui.theme.UrgentContainer
import com.comunidapp.app.ui.util.formatDisplayDate

private val V2CardShape = RoundedCornerShape(20.dp)
private val V2HeroShape = RoundedCornerShape(24.dp)

@Composable
internal fun PetDetailV2TopBar(
    onBack: () -> Unit,
    showMenu: Boolean,
    onMenuClick: () -> Unit,
    menuContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = "Volver" }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = BrandText
            )
        }
        if (showMenu) {
            Box {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Más opciones" }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = BrandText
                    )
                }
                menuContent()
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
internal fun PetHero(
    imageUrl: String?,
    petName: String,
    modifier: Modifier = Modifier
) {
    PetImage(
        imageUrl = imageUrl,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(V2HeroShape),
        cornerRadius = 24.dp,
        contentDescription = "Foto de $petName"
    )
}

@Composable
internal fun PetStatusChip(
    status: String,
    reasonCode: String? = null
) {
    val active = status.equals("ACTIVE", ignoreCase = true)
    val label = if (active) "Activa" else petStatusLabel(status, reasonCode)
    val background = when {
        active -> BrandGreenContainer
        status.equals("DECEASED", ignoreCase = true) -> UrgentContainer
        else -> NeutralBorder.copy(alpha = 0.55f)
    }
    val foreground = when {
        active -> BrandGreenDark
        status.equals("DECEASED", ignoreCase = true) -> BrandText
        else -> BrandTextSecondary
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = background,
        border = BorderStroke(1.dp, NeutralBorder.copy(alpha = 0.6f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = foreground
        )
    }
}

@Composable
internal fun PetIdentityBlock(
    name: String,
    subtitle: String,
    ageLabel: String?,
    status: String,
    reasonCode: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = BrandText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!ageLabel.isNullOrBlank()) {
                Text(
                    text = ageLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = BrandText
                )
            }
            PetStatusChip(status = status, reasonCode = reasonCode)
        }
    }
}

@Composable
internal fun PetPrimaryActions(
    canEdit: Boolean,
    onEdit: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (canEdit) {
            Button(
                onClick = onEdit,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandOrange,
                    contentColor = BrandWhite
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Editar", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        OutlinedButton(
            onClick = onShare,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NeutralBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandText),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Compartir", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun PetV2Card(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = V2CardShape,
        colors = CardDefaults.cardColors(containerColor = BrandWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, NeutralBorder.copy(alpha = 0.7f)),
        content = {
            Column(modifier = Modifier.padding(18.dp), content = content)
        }
    )
}

@Composable
internal fun PetInfoCard(rows: List<Pair<String, String>>) {
    if (rows.isEmpty()) return
    PetV2Card {
        Text(
            text = "Información",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = BrandText
        )
        Spacer(modifier = Modifier.height(12.dp))
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandTextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = BrandText,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
internal fun PetHealthSummary(
    pet: Pet,
    canOpenHealth: Boolean,
    onOpenHealth: () -> Unit
) {
    val nextVaccine = pet.vaccinations
        .mapNotNull { it.nextDueDate?.takeIf(String::isNotBlank)?.let { due -> it to due } }
        .minByOrNull { it.second }
    val pendingReminders = pet.reminders.count { it.title.isNotBlank() || it.date.isNotBlank() }
    val pendingVaccines = pet.vaccinations.count { !it.nextDueDate.isNullOrBlank() }
    val pendingCount = pendingReminders + pendingVaccines

    PetV2Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BrandGreenContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = BrandGreenDark,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Salud",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandText
                )
                Text(
                    text = "Vacunas, controles y recordatorios",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandTextSecondary
                )
            }
        }
        nextVaccine?.let { (vac: VaccinationRecord, due: String) ->
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Próxima vacuna",
                style = MaterialTheme.typography.labelMedium,
                color = BrandTextSecondary
            )
            Text(
                text = formatDisplayDate(due),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = BrandGreenDark,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (vac.name.isNotBlank()) {
                Text(
                    text = vac.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandTextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (pendingCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (pendingCount == 1) "1 pendiente" else "$pendingCount pendientes",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandText
            )
        }
        if (nextVaccine == null && pendingCount == 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Todavía no agregaste información de salud",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = BrandText
            )
            Text(
                text = "Registrá vacunas, cuidados y recordatorios de ${pet.name}.",
                style = MaterialTheme.typography.bodySmall,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (canOpenHealth) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onOpenHealth,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    text = if (nextVaccine == null && pendingCount == 0) "Agregar información" else "Ver salud",
                    color = BrandGreenDark,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = BrandGreenDark
                )
            }
        }
    }
}

@Composable
internal fun PetPassportSummary(
    petName: String,
    onOpenPassport: () -> Unit,
    secondaryActions: (@Composable ColumnScope.() -> Unit)? = null
) {
    PetV2Card {
        Text(
            text = "Pasaporte LeoVer",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = BrandText
        )
        Text(
            text = "La identidad compartible de $petName.",
            style = MaterialTheme.typography.bodyMedium,
            color = BrandTextSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = "Abrí el pasaporte para ver o compartir su estado.",
            style = MaterialTheme.typography.bodySmall,
            color = BrandTextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
        TextButton(
            onClick = onOpenPassport,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("Ver pasaporte", color = BrandOrange, fontWeight = FontWeight.SemiBold)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = BrandOrange
            )
        }
        secondaryActions?.invoke(this)
    }
}

@Composable
internal fun PetEmergencyAction(
    petName: String,
    onReportLost: () -> Unit
) {
    Button(
        onClick = onReportLost,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .semantics { contentDescription = "Perdí a $petName" },
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandOrangeDeep,
            contentColor = BrandWhite
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(Icons.Default.Pets, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Perdí a $petName",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun PetArchivedBanner(
    canRestore: Boolean,
    onRestore: () -> Unit
) {
    PetV2Card {
        Text(
            text = "Esta mascota está archivada",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = BrandText
        )
        Text(
            text = "No aparece entre tus mascotas activas, pero su información se conserva.",
            style = MaterialTheme.typography.bodyMedium,
            color = BrandTextSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (canRestore) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRestore,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandGreen,
                    contentColor = BrandWhite
                )
            ) {
                Text("Reactivar mascota")
            }
        }
    }
}

@Composable
internal fun PetDeceasedBanner(petName: String) {
    PetV2Card {
        Text(
            text = "$petName trascendió 🌈",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = BrandText
        )
        Text(
            text = "Siempre será parte de tu historia.",
            style = MaterialTheme.typography.bodyMedium,
            color = BrandTextSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

internal fun petIdentitySubtitle(pet: Pet): String = buildString {
    pet.breed?.takeIf { it.isNotBlank() }?.let { append(it) }
        ?: append(pet.species.toDisplayName())
    append(" · ")
    append(pet.sex.toDisplayName())
}

internal fun petInfoRows(pet: Pet): List<Pair<String, String>> = buildList {
    add("Especie" to pet.species.toDisplayName())
    pet.breed?.takeIf { it.isNotBlank() }?.let { add("Raza" to it) }
    add("Sexo" to pet.sex.toDisplayName())
    add("Tamaño" to pet.size.toDisplayName())
    pet.color?.takeIf { it.isNotBlank() }?.let { add("Color" to it) }
    // No fecha de nacimiento en el modelo Pet actual — no inventar.
}

internal val PetDetailV2BackgroundColor = BrandCream

internal fun PetDetailV2Background(): Color = PetDetailV2BackgroundColor
