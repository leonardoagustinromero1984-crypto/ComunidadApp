package com.comunidapp.app.ui.components.leo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoChip
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoSectionTitle
import com.comunidapp.app.ui.theme.NeutralBorder

/** Filtro ya aplicado. El id lo usa la pantalla para quitarlo. La etiqueta es humana. */
data class LeoActiveFilter(
    val id: String,
    val label: String
)

/**
 * Barra de un listado: búsqueda opcional y el botón Filtros.
 * Debajo, solo los filtros que están activos.
 * Abrir el panel no cambia el resultado hasta Aplicar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeoFilterBar(
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
    activeFilters: List<LeoActiveFilter> = emptyList(),
    onRemoveFilter: (String) -> Unit = {},
    onClearFilters: (() -> Unit)? = null,
    search: (@Composable () -> Unit)? = null
) {
    val visible = activeFilters.filter { it.label.isNotBlank() }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
        ) {
            if (search != null) {
                Box(Modifier.weight(1f)) { search() }
            }
            TextButton(
                onClick = onOpenFilters,
                modifier = Modifier.semantics {
                    contentDescription = if (visible.isEmpty()) {
                        "Filtros"
                    } else {
                        "Filtros, ${visible.size} activos"
                    }
                }
            ) {
                Text("Filtros", style = LeoChip)
            }
        }
        if (visible.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS),
                verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
            ) {
                visible.forEach { filter ->
                    LeoFilterChip(
                        label = "${filter.label} ×",
                        selected = true,
                        onClick = { onRemoveFilter(filter.id) }
                    )
                }
                if (onClearFilters != null) {
                    TextButton(onClick = onClearFilters) {
                        Text("Limpiar", style = LeoChip)
                    }
                }
            }
        }
    }
}

/**
 * Panel de filtros. Los controles de [content] editan un borrador.
 * Limpiar vacía ese borrador. Aplicar filtros es lo que confirma.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeoFilterSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onClearDraft: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BrandBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(LeoDimens.SpaceL),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceM)
        ) {
            Text("Filtros", style = LeoSectionTitle, color = BrandText)
            content()
            Row(horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)) {
                LeoOutlinedButton(
                    text = "Limpiar",
                    onClick = onClearDraft,
                    modifier = Modifier.weight(1f)
                )
                LeoPrimaryButton(
                    text = "Aplicar filtros",
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Sí / No / un tercer valor que es null.
 * "No" y "No informado" son opciones distintas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeoTriStateSelector(
    title: String,
    value: Boolean?,
    onChange: (Boolean?) -> Unit,
    unknownLabel: String,
    modifier: Modifier = Modifier,
    yesLabel: String = "Sí",
    noLabel: String = "No"
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
    ) {
        Text(title, style = LeoCaption, color = BrandText)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
        ) {
            LeoFilterChip(label = yesLabel, selected = value == true, onClick = { onChange(true) })
            LeoFilterChip(label = noLabel, selected = value == false, onClick = { onChange(false) })
            LeoFilterChip(label = unknownLabel, selected = value == null, onClick = { onChange(null) })
        }
    }
}

/** Estado con palabras. El color acompaña; el texto es el significado. */
@Composable
fun LeoStatusBadge(
    label: String,
    modifier: Modifier = Modifier
) {
    if (label.isBlank()) return
    Text(
        text = label,
        style = LeoChip,
        color = BrandText,
        modifier = modifier
            .background(BrandCream, RoundedCornerShape(LeoDimens.RadiusChip))
            .border(1.dp, NeutralBorder, RoundedCornerShape(LeoDimens.RadiusChip))
            .padding(horizontal = LeoDimens.SpaceS, vertical = LeoDimens.SpaceXs)
    )
}

@Preview(showBackground = true)
@Composable
private fun LeoFilterBarPreview() {
    ComunidappTheme {
        LeoFilterBar(
            onOpenFilters = {},
            activeFilters = listOf(LeoActiveFilter("dogs", "Perros"), LeoActiveFilter("sex", "Hembra")),
            onClearFilters = {},
            modifier = Modifier.background(BrandWhite).padding(LeoDimens.SpaceL)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LeoTriStatePreview() {
    ComunidappTheme {
        LeoTriStateSelector(
            title = "¿Convive con otros animales?",
            value = null,
            unknownLabel = "No sé",
            onChange = {},
            modifier = Modifier.padding(LeoDimens.SpaceL)
        )
    }
}
