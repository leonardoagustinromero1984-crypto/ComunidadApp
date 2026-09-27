package com.comunidapp.app.ui.screens.foster

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
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Composable
fun RequestFosterForPetScreen(petId: String, onNavigateBack: () -> Unit) {
    var needs by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Necesito hogar de tránsito",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            Text("LeoVer avisará hogares verificados y disponibles. Vos elegís uno. No gana el primero.", style = LeoCaption)
            OutlinedTextField(needs, { needs = it }, label = { Text("Necesidades") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth())
            message?.let { Text(it, style = LeoCaption) }
            LeoPrimaryButton(
                text = "Publicar solicitud",
                onClick = {
                    scope.launch {
                        runCatching {
                            supabase.postgrest.rpc(
                                CanonicalBackend.RPC_REQUEST_FOSTER_FOR_PET,
                                buildJsonObject {
                                    put("p_pet_id", petId)
                                    put("p_needs", needs)
                                    put("p_notes", notes)
                                }
                            )
                        }.onSuccess { message = "Solicitud creada. Los hogares pueden postularse." }
                            .onFailure { message = "No se pudo crear la solicitud." }
                    }
                }
            )
        }
    }
}

@Composable
fun OpenFosterRequestsScreen(onNavigateBack: () -> Unit) {
    var rows by remember { mutableStateOf(listOf<JsonObject>()) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching {
            val el: JsonElement = supabase.postgrest.rpc(CanonicalBackend.RPC_LIST_OPEN_FOSTER_REQUESTS).decodeAs()
            rows = (el as? JsonArray)?.map { it.jsonObject }.orEmpty()
        }
    }
    Scaffold(
        containerColor = BrandBackground,
        topBar = { LeoTopAppBar(title = "Solicitudes de tránsito", showBackButton = true, onBackClick = onNavigateBack) }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(LeoDimens.SpaceMd), verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)) {
            item { message?.let { Text(it, style = LeoCaption) } }
            items(rows) { row ->
                val name = (row["pet_name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val id = (row["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                Text("$name · ${(row["species"] as? JsonPrimitive)?.contentOrNull}")
                LeoPrimaryButton(
                    text = "Postularme",
                    onClick = {
                        scope.launch {
                            runCatching {
                                supabase.postgrest.rpc(
                                    CanonicalBackend.RPC_APPLY_TO_FOSTER_REQUEST,
                                    buildJsonObject { put("p_request_id", id) }
                                )
                            }.onSuccess { message = "Te postulaste. El responsable elige." }
                                .onFailure { message = "No se pudo postular." }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ChooseFosterApplicantScreen(requestId: String, onNavigateBack: () -> Unit) {
    var rows by remember { mutableStateOf(listOf<JsonObject>()) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(requestId) {
        runCatching {
            val el: JsonElement = supabase.postgrest.rpc(
                CanonicalBackend.RPC_LIST_FOSTER_REQUEST_APPS,
                buildJsonObject { put("p_request_id", requestId) }
            ).decodeAs()
            rows = (el as? JsonArray)?.map { it.jsonObject }.orEmpty()
        }
    }
    Scaffold(
        containerColor = BrandBackground,
        topBar = { LeoTopAppBar(title = "Elegir hogar", showBackButton = true, onBackClick = onNavigateBack) }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(LeoDimens.SpaceMd), verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)) {
            item { Text("Elegí un hogar. No se adjudica solo.", style = LeoCaption) }
            item { message?.let { Text(it, style = LeoCaption) } }
            items(rows) { row ->
                val name = (row["foster_name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val id = (row["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                Text("$name · ${(row["status"] as? JsonPrimitive)?.contentOrNull}")
                LeoOutlinedButton(
                    text = "Elegir este hogar",
                    onClick = {
                        scope.launch {
                            runCatching {
                                supabase.postgrest.rpc(
                                    CanonicalBackend.RPC_SELECT_FOSTER_APPLICANT,
                                    buildJsonObject { put("p_application_id", id) }
                                )
                            }.onSuccess { message = "Tránsito activo. Misma mascota, misma VitaCora." }
                                .onFailure { message = "No se pudo elegir." }
                        }
                    }
                )
            }
        }
    }
}
