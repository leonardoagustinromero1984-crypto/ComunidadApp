package com.comunidapp.app.ui.screens.adoptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.adoption.AdoptionRequirements
import com.comunidapp.app.domain.adoption.AdopterLifeStagePref
import com.comunidapp.app.domain.adoption.AdopterSizePref
import com.comunidapp.app.domain.adoption.AdopterSpeciesPref
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.domain.adoption.EscapeProtectionCopy
import com.comunidapp.app.domain.adoption.ExperienceBand
import com.comunidapp.app.domain.adoption.HoursAloneEstimate
import com.comunidapp.app.domain.adoption.HousingKind
import com.comunidapp.app.domain.adoption.HousingTenure
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoTriStateSelector
import com.comunidapp.app.ui.theme.LeoCaption

@Composable
internal fun AdoptionTriChips(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HousingKindChips(value: HousingKind?, onChange: (HousingKind?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Tipo de vivienda", style = LeoCaption)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LeoFilterChip("Casa", value == HousingKind.HOUSE, onClick = { onChange(HousingKind.HOUSE) })
            LeoFilterChip("Departamento", value == HousingKind.APARTMENT, onClick = { onChange(HousingKind.APARTMENT) })
            LeoFilterChip("Otra", value == HousingKind.OTHER, onClick = { onChange(HousingKind.OTHER) })
            LeoFilterChip("No sé", value == null, onClick = { onChange(null) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HousingTenureChips(value: HousingTenure?, onChange: (HousingTenure?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Tenencia", style = LeoCaption)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LeoFilterChip("Propia", value == HousingTenure.OWN, onClick = { onChange(HousingTenure.OWN) })
            LeoFilterChip("Alquilada", value == HousingTenure.RENT, onClick = { onChange(HousingTenure.RENT) })
            LeoFilterChip("No sé", value == null, onClick = { onChange(null) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExperienceBandChips(
    value: ExperienceBand?,
    title: String = "Experiencia con animales",
    onChange: (ExperienceBand?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = LeoCaption)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LeoFilterChip("Sin experiencia", value == ExperienceBand.NONE, onClick = { onChange(ExperienceBand.NONE) })
            LeoFilterChip("Con experiencia", value == ExperienceBand.SOME, onClick = { onChange(ExperienceBand.SOME) })
            LeoFilterChip("Cuidados especiales", value == ExperienceBand.SPECIAL_CARE, onClick = { onChange(ExperienceBand.SPECIAL_CARE) })
            LeoFilterChip("No sé", value == null, onClick = { onChange(null) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HoursAloneChips(
    title: String,
    value: HoursAloneEstimate?,
    unknownLabel: String,
    onChange: (HoursAloneEstimate?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = LeoCaption)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LeoFilterChip("Menos de 4 h", value == HoursAloneEstimate.UNDER_4, onClick = { onChange(HoursAloneEstimate.UNDER_4) })
            LeoFilterChip("4 a 8 h", value == HoursAloneEstimate.FROM_4_TO_8, onClick = { onChange(HoursAloneEstimate.FROM_4_TO_8) })
            LeoFilterChip("Más de 8 h", value == HoursAloneEstimate.OVER_8, onClick = { onChange(HoursAloneEstimate.OVER_8) })
            LeoFilterChip(unknownLabel, value == null, onClick = { onChange(null) })
        }
        if (value != null && value.legacyBandToken() == null) {
            val end = value.toHours?.let { " a $it h" }.orEmpty()
            Text("Horas informadas: ${value.fromHours}$end", style = LeoCaption)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpeciesPrefChips(value: AdopterSpeciesPref?, onChange: (AdopterSpeciesPref?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Especie", style = LeoCaption)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LeoFilterChip("Perro", value == AdopterSpeciesPref.DOG, onClick = { onChange(AdopterSpeciesPref.DOG) })
            LeoFilterChip("Gato", value == AdopterSpeciesPref.CAT, onClick = { onChange(AdopterSpeciesPref.CAT) })
            LeoFilterChip(
                "Sin preferencia",
                value == null || value == AdopterSpeciesPref.ANY,
                onClick = { onChange(null) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SizePrefChips(value: AdopterSizePref?, onChange: (AdopterSizePref?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Tamaño", style = LeoCaption)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LeoFilterChip("Pequeño", value == AdopterSizePref.SMALL, onClick = { onChange(AdopterSizePref.SMALL) })
            LeoFilterChip("Mediano", value == AdopterSizePref.MEDIUM, onClick = { onChange(AdopterSizePref.MEDIUM) })
            LeoFilterChip("Grande", value == AdopterSizePref.LARGE, onClick = { onChange(AdopterSizePref.LARGE) })
            LeoFilterChip("Sin preferencia", value == null, onClick = { onChange(null) })
        }
    }
}

@Composable
internal fun AdoptionRequirementFields(
    value: AdoptionRequirements,
    species: PetSpecies? = null,
    onChange: (AdoptionRequirements) -> Unit
) {
    Text("Requisitos", style = LeoCaption)
    AdoptionTriChips(
        "Acepta hogar con niños",
        value.acceptsChildren,
        "Sin requisito",
        { onChange(value.copy(acceptsChildren = it)) }
    )
    AdoptionTriChips(
        "Acepta otros perros",
        value.acceptsOtherDogs,
        "Sin requisito",
        { onChange(value.copy(acceptsOtherDogs = it)) }
    )
    AdoptionTriChips(
        "Acepta gatos",
        value.acceptsCats,
        "Sin requisito",
        { onChange(value.copy(acceptsCats = it)) }
    )
    AdoptionTriChips(
        "Acepta otros animales",
        value.acceptsOtherAnimals,
        "Sin requisito",
        { onChange(value.copy(acceptsOtherAnimals = it)) }
    )
    AdoptionTriChips(
        "Necesita patio o espacio exterior",
        value.needsOutdoorSpace,
        "Sin requisito",
        { onChange(value.copy(needsOutdoorSpace = it)) }
    )
    AdoptionTriChips(
        "Necesita cerramiento seguro",
        value.needsSecureEnclosure,
        "Sin requisito",
        { onChange(value.copy(needsSecureEnclosure = it)) }
    )
    AdoptionTriChips(
        EscapeProtectionCopy.REQUIREMENT_TITLE,
        value.requiresEscapeProtection,
        "Sin requisito",
        { onChange(value.copy(requiresEscapeProtection = it)) }
    )
    Text(EscapeProtectionCopy.requirementHint(species), style = LeoCaption)
    AdoptionTriChips(
        "Requiere permiso de mascotas si alquila",
        value.requiresLandlordPetPermission,
        "Sin requisito",
        { onChange(value.copy(requiresLandlordPetPermission = it)) }
    )
    HoursAloneChips(
        "Máximo de horas solo",
        value.maxHoursAlone,
        "Sin requisito",
        { onChange(value.copy(maxHoursAlone = it)) }
    )
    ExperienceBandChips(value.experienceRequired, "Experiencia requerida") {
        onChange(value.copy(experienceRequired = it))
    }
    AdoptionTriChips(
        "Acepta adoptante sin experiencia",
        value.acceptsNoExperience,
        "Sin requisito",
        { onChange(value.copy(acceptsNoExperience = it)) }
    )
    AdoptionTriChips(
        "Requiere experiencia en cuidados especiales",
        value.requiresSpecialCareExperience,
        "Sin requisito",
        { onChange(value.copy(requiresSpecialCareExperience = it)) }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LifeStagePrefChips(value: AdopterLifeStagePref?, onChange: (AdopterLifeStagePref?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Etapa de vida", style = LeoCaption)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LeoFilterChip("Cachorro", value == AdopterLifeStagePref.YOUNG, onClick = { onChange(AdopterLifeStagePref.YOUNG) })
            LeoFilterChip("Adulto", value == AdopterLifeStagePref.ADULT, onClick = { onChange(AdopterLifeStagePref.ADULT) })
            LeoFilterChip("Mayor", value == AdopterLifeStagePref.SENIOR, onClick = { onChange(AdopterLifeStagePref.SENIOR) })
            LeoFilterChip("Sin preferencia", value == null, onClick = { onChange(null) })
        }
    }
}
