package com.comunidapp.app.ui.screens.m28

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
fun ProfessionalHubScreen(
    onAgenda: () -> Unit,
    onPatients: () -> Unit,
    onNewPatient: () -> Unit,
    onNotifications: () -> Unit,
    onPublicProfile: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    Scaffold(
        containerColor = BrandBackground,
        topBar = { LeoTopAppBar(title = "Profesional", showBackButton = true, onBackClick = onNavigateBack) }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            Text("Hoy · Agenda · Pacientes · Buscar / QR · Notificaciones", style = LeoCaption)
            LeoPrimaryButton(text = "Hoy / Agenda", onClick = onAgenda)
            LeoPrimaryButton(text = "Pacientes", onClick = onPatients)
            LeoPrimaryButton(text = "Nuevo paciente", onClick = onNewPatient)
            LeoOutlinedButton(text = "Notificaciones", onClick = onNotifications)
            LeoOutlinedButton(text = "Ver mi ficha en Comunidad", onClick = onPublicProfile)
        }
    }
}

@Composable
fun VetCreatePatientScreen(onNavigateBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var species by remember { mutableStateOf("DOG") }
    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Scaffold(
        containerColor = BrandBackground,
        topBar = { LeoTopAppBar(title = "Nuevo paciente", showBackButton = true, onBackClick = onNavigateBack) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            Text("El email del responsable es obligatorio. No se vuelve owner hasta verificar ese email.", style = LeoCaption)
            OutlinedTextField(name, { name = it }, label = { Text("Nombre de la mascota") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(species, { species = it }, label = { Text("Especie (DOG/CAT)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("Email del responsable *") }, modifier = Modifier.fillMaxWidth())
            message?.let { Text(it, style = LeoCaption) }
            LeoPrimaryButton(
                text = "Crear e invitar",
                onClick = {
                    if (!email.contains("@")) {
                        message = "El email es obligatorio."
                        return@LeoPrimaryButton
                    }
                    scope.launch {
                        runCatching {
                            supabase.postgrest.rpc(
                                CanonicalBackend.RPC_CREATE_VET_PATIENT,
                                buildJsonObject {
                                    put("p_name", name)
                                    put("p_species", species)
                                    put("p_owner_email", email)
                                }
                            )
                        }.onSuccess { message = "Paciente creado. Invitación en cola de email." }
                            .onFailure { message = "No se pudo crear el paciente." }
                    }
                }
            )
        }
    }
}
