package com.comunidapp.app.ui.screens.m28

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.v2.V2SurfaceCard
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

data class ProfessionalPatientRow(
    val id: String,
    val name: String,
    val species: String?,
    val publicCode: String?,
    val responsibleName: String?
)

@Composable
fun ProfessionalPatientsScreen(
    onOpenPatient: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<ProfessionalPatientRow>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun search(term: String) {
        scope.launch {
            runCatching {
                val element = supabase.postgrest.rpc(
                    CanonicalBackend.RPC_SEARCH_PROFESSIONAL_PATIENTS,
                    buildJsonObject { put("p_query", term) }
                ).decodeAs<kotlinx.serialization.json.JsonElement>()
                val array = element as? JsonArray ?: JsonArray(emptyList())
                array.mapNotNull { item ->
                    val obj = item as? JsonObject ?: return@mapNotNull null
                    ProfessionalPatientRow(
                        id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null,
                        name = (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                        species = (obj["species"] as? JsonPrimitive)?.contentOrNull,
                        publicCode = (obj["public_code"] as? JsonPrimitive)?.contentOrNull,
                        responsibleName = (obj["responsible_name"] as? JsonPrimitive)?.contentOrNull
                    )
                }
            }.onSuccess {
                rows = it
                message = if (it.isEmpty()) "No hay pacientes permitidos para esta búsqueda." else null
            }.onFailure {
                message = "No se pudo buscar pacientes."
            }
        }
    }

    LaunchedEffect(Unit) { search("") }

    Scaffold(
        containerColor = BrandBackground,
        topBar = { LeoTopAppBar(title = "Pacientes", showBackButton = true, onBackClick = onNavigateBack) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    search(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar paciente") },
                singleLine = true
            )
            message?.let { Text(it, style = LeoCaption) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)) {
                items(rows, key = { it.id }) { row ->
                    V2SurfaceCard(onClick = { onOpenPatient(row.id) }) {
                        Column(Modifier.padding(LeoDimens.SpaceSm)) {
                            Text(row.name.ifBlank { "Paciente" })
                            Text(
                                listOfNotNull(
                                    row.species,
                                    row.responsibleName?.let { "Responsable: $it" },
                                    row.publicCode
                                ).joinToString(" · "),
                                style = LeoCaption
                            )
                        }
                    }
                }
            }
        }
    }
}
