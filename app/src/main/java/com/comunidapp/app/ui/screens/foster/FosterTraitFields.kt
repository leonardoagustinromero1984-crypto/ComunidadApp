package com.comunidapp.app.ui.screens.foster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.foster.FosterLifeStage
import com.comunidapp.app.domain.foster.FosterSizeBand
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoTriStateSelector
import com.comunidapp.app.ui.theme.LeoCaption

@Composable
internal fun FosterTriChips(
    title: String,
    value: Boolean?,
    unknownLabel: String,
    onChange: (Boolean?) -> Unit
) {
    LeoTriStateSelector(
        title = title,
        value = value,
        unknownLabel = unknownLabel,
        onChange = onChange
    )
}

@Composable
internal fun FosterSizeChips(
    title: String,
    value: FosterSizeBand?,
    unknownLabel: String,
    onChange: (FosterSizeBand?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = LeoCaption)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LeoFilterChip("Pequeño", value == FosterSizeBand.SMALL, onClick = { onChange(FosterSizeBand.SMALL) })
            LeoFilterChip("Mediano", value == FosterSizeBand.MEDIUM, onClick = { onChange(FosterSizeBand.MEDIUM) })
            LeoFilterChip("Grande", value == FosterSizeBand.LARGE, onClick = { onChange(FosterSizeBand.LARGE) })
            LeoFilterChip(unknownLabel, value == null, onClick = { onChange(null) })
        }
    }
}

@Composable
internal fun FosterLifeStageChips(
    title: String,
    value: FosterLifeStage?,
    unknownLabel: String,
    onChange: (FosterLifeStage?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = LeoCaption)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LeoFilterChip("Cachorro", value == FosterLifeStage.YOUNG, onClick = { onChange(FosterLifeStage.YOUNG) })
            LeoFilterChip("Adulto", value == FosterLifeStage.ADULT, onClick = { onChange(FosterLifeStage.ADULT) })
            LeoFilterChip("Mayor", value == FosterLifeStage.SENIOR, onClick = { onChange(FosterLifeStage.SENIOR) })
            LeoFilterChip(unknownLabel, value == null, onClick = { onChange(null) })
        }
    }
}

@Composable
internal fun FoundAnimalTraitForm(
    size: FosterSizeBand?,
    onSize: (FosterSizeBand?) -> Unit,
    lifeStage: FosterLifeStage?,
    onLifeStage: (FosterLifeStage?) -> Unit,
    needsMedication: Boolean?,
    onMedication: (Boolean?) -> Unit,
    cohabitsDogs: Boolean?,
    onDogs: (Boolean?) -> Unit,
    cohabitsCats: Boolean?,
    onCats: (Boolean?) -> Unit,
    cohabitsChildren: Boolean?,
    onChildren: (Boolean?) -> Unit,
    reducedMobility: Boolean?,
    onMobility: (Boolean?) -> Unit,
    needsIsolation: Boolean?,
    onIsolation: (Boolean?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("¿Qué sabés del animal?", style = LeoCaption)
        FosterSizeChips("Tamaño aproximado", size, "No sé", onSize)
        FosterLifeStageChips("Etapa", lifeStage, "No sé", onLifeStage)
        FosterTriChips("¿Necesita medicación?", needsMedication, "No sé", onMedication)
        FosterTriChips("¿Puede convivir con perros?", cohabitsDogs, "No sé", onDogs)
        FosterTriChips("¿Puede convivir con gatos?", cohabitsCats, "No sé", onCats)
        FosterTriChips("¿Puede convivir con niños?", cohabitsChildren, "No sé", onChildren)
        FosterTriChips("¿Tiene movilidad reducida?", reducedMobility, "No sé", onMobility)
        FosterTriChips("¿Necesita estar separado de otros animales?", needsIsolation, "No sé", onIsolation)
    }
}

@Composable
internal fun FosterHomeCapabilityForm(
    acceptsDogs: Boolean?,
    onDogs: (Boolean?) -> Unit,
    acceptsCats: Boolean?,
    onCats: (Boolean?) -> Unit,
    acceptsSmall: Boolean?,
    onSmall: (Boolean?) -> Unit,
    acceptsMedium: Boolean?,
    onMedium: (Boolean?) -> Unit,
    acceptsLarge: Boolean?,
    onLarge: (Boolean?) -> Unit,
    acceptsYoung: Boolean?,
    onYoung: (Boolean?) -> Unit,
    acceptsAdult: Boolean?,
    onAdult: (Boolean?) -> Unit,
    acceptsSenior: Boolean?,
    onSenior: (Boolean?) -> Unit,
    acceptsMedication: Boolean?,
    onMedication: (Boolean?) -> Unit,
    livesWithDogs: Boolean?,
    onLivesDogs: (Boolean?) -> Unit,
    livesWithCats: Boolean?,
    onLivesCats: (Boolean?) -> Unit,
    livesWithChildren: Boolean?,
    onLivesChildren: (Boolean?) -> Unit,
    acceptsReducedMobility: Boolean?,
    onMobility: (Boolean?) -> Unit,
    canIsolate: Boolean?,
    onIsolate: (Boolean?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Puedo recibir…", style = LeoCaption)
        FosterTriChips("Perros", acceptsDogs, "Sin preferencia", onDogs)
        FosterTriChips("Gatos", acceptsCats, "Sin preferencia", onCats)
        FosterTriChips("Tamaño pequeño", acceptsSmall, "Sin preferencia", onSmall)
        FosterTriChips("Tamaño mediano", acceptsMedium, "Sin preferencia", onMedium)
        FosterTriChips("Tamaño grande", acceptsLarge, "Sin preferencia", onLarge)
        FosterTriChips("Cachorros", acceptsYoung, "Sin preferencia", onYoung)
        FosterTriChips("Adultos", acceptsAdult, "Sin preferencia", onAdult)
        FosterTriChips("Mayores", acceptsSenior, "Sin preferencia", onSenior)
        FosterTriChips("Animales con medicación", acceptsMedication, "Sin preferencia", onMedication)
        FosterTriChips("Convive con perros en casa", livesWithDogs, "Sin preferencia", onLivesDogs)
        FosterTriChips("Convive con gatos en casa", livesWithCats, "Sin preferencia", onLivesCats)
        FosterTriChips("Convive con niños", livesWithChildren, "Sin preferencia", onLivesChildren)
        FosterTriChips("Movilidad reducida", acceptsReducedMobility, "Sin preferencia", onMobility)
        FosterTriChips("Puedo separarlo de otros animales", canIsolate, "Sin preferencia", onIsolate)
    }
}
