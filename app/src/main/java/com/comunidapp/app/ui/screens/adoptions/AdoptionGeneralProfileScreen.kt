package com.comunidapp.app.ui.screens.adoptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject

@Composable
fun AdoptionGeneralProfileScreen(onNavigateBack: () -> Unit) {
    var housing by remember { mutableStateOf("") }
    var motivation by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var existingRaw by remember { mutableStateOf("{}") }
    var loaded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val raw = runCatching {
            supabase.postgrest.rpc(
                CanonicalBackend.RPC_LIST_ADOPTION_GENERAL_PROFILE,
                buildJsonObject { }
            ).data
        }.getOrNull() ?: return@LaunchedEffect
        existingRaw = raw
        val fields = com.comunidapp.app.domain.adoption.AdoptionGeneralProfileCodec.decode(raw)
        if (housing.isBlank() && motivation.isBlank() && notes.isBlank()) {
            housing = fields.housing
            motivation = fields.motivation
            notes = fields.notes
        }
        loaded = true
    }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Mi perfil de adopción",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(LeoDimens.SpaceMd)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            Text("Se completa una vez y se reutiliza. Los opcionales pueden quedar vacíos.", style = LeoCaption)
            OutlinedTextField(housing, { housing = it }, label = { Text("Vivienda (opcional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(motivation, { motivation = it }, label = { Text("Motivación (opcional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Información adicional") }, modifier = Modifier.fillMaxWidth())
            message?.let { Text(it, style = LeoCaption) }
            LeoPrimaryButton(
                text = "Guardar perfil",
                onClick = {
                    if (!loaded) return@LeoPrimaryButton
                    scope.launch {
                        runCatching {
                            supabase.postgrest.rpc(
                                CanonicalBackend.RPC_UPSERT_ADOPTION_GENERAL_PROFILE,
                                com.comunidapp.app.domain.adoption.AdoptionGeneralProfileCodec.upsertPreserving(
                                    existingRaw,
                                    housing,
                                    motivation,
                                    notes
                                )
                            )
                        }.onSuccess { message = "Perfil guardado." }
                            .onFailure { message = "No se pudo guardar." }
                    }
                }
            )
        }
    }
}
