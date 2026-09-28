package com.comunidapp.app.ui.screens.foster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.CanonicalActiveFosterTransit
import com.comunidapp.app.data.repository.CanonicalFosterApplicantRow
import com.comunidapp.app.data.repository.CanonicalFosterTransitApplication
import com.comunidapp.app.data.repository.CanonicalFosterTransitRecovery
import com.comunidapp.app.data.repository.CanonicalFosterTransitRequest
import com.comunidapp.app.data.repository.CanonicalOpenFosterRequest
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import kotlinx.coroutines.launch

@Composable
fun RequestFosterForPetScreen(
    petId: String,
    onNavigateBack: () -> Unit,
    onChooseApplicant: (String) -> Unit = {}
) {
    var needs by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var request by remember { mutableStateOf<CanonicalFosterTransitRequest?>(null) }
    var transit by remember { mutableStateOf<CanonicalActiveFosterTransit?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repository = DataProvider.canonicalFosterTransitRepository

    suspend fun reload() {
        val listed = repository.listMyFosterRequests().getOrThrow()
        val open = CanonicalFosterTransitRecovery.requestForPet(listed, petId)
        val shown = open ?: CanonicalFosterTransitRecovery.completedRequestForPet(listed, petId)
        request = shown
        transit = if (open != null && open.status in setOf("MATCHED", "ACTIVE")) {
            repository.getActiveFosterTransit(petId).getOrNull()
        } else {
            null
        }
        if (shown != null) {
            if (needs.isBlank()) needs = shown.needs.orEmpty()
            if (notes.isBlank()) notes = shown.notes.orEmpty()
        }
    }

    LaunchedEffect(petId) {
        runCatching { reload() }
            .onFailure { message = it.message ?: "No se pudo leer la solicitud." }
    }

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
            Text(
                "LeoVer avisará hogares verificados y disponibles. Vos elegís uno. No gana el primero.",
                style = LeoCaption
            )
            request?.let { row ->
                Text("Solicitud ${row.status}", style = LeoCaption)
                Text("Identificador recuperado del servidor.", style = LeoCaption)
            }
            transit?.let { active ->
                Text(activeTransitLabel(active), style = LeoCaption)
            }
            OutlinedTextField(
                needs,
                { needs = it },
                label = { Text("Necesidades") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                notes,
                { notes = it },
                label = { Text("Notas") },
                modifier = Modifier.fillMaxWidth()
            )
            message?.let { Text(it, style = LeoCaption) }
            val current = request
            val finished = current?.status == "COMPLETED"
            if (current != null && !finished) {
                LeoOutlinedButton(
                    text = "Ver postulantes",
                    onClick = { onChooseApplicant(current.id) }
                )
            }
            if (current?.status == "ACTIVE" && transit?.placementStatus == "OPEN") {
                LeoPrimaryButton(
                    text = "Finalizar tránsito",
                    enabled = !busy,
                    onClick = { confirmEnd = true }
                )
            }
            if (finished) {
                Text("Tránsito finalizado.", style = LeoCaption)
                Text(
                    "El hogar temporal ya no figura como cuidador activo.",
                    style = LeoCaption
                )
            }
            if (!finished) {
                LeoPrimaryButton(
                    text = if (current == null) "Publicar solicitud" else "Reintentar solicitud",
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            runCatching {
                                val id = repository.requestFosterForPet(petId, needs, notes).getOrThrow()
                                reload()
                                val recovered = request
                                if (recovered == null || recovered.id != id) {
                                    error("La solicitud no volvió del servidor.")
                                }
                                recovered.status
                            }.onSuccess { status ->
                                message = "Solicitud $status. Mismo identificador del servidor."
                            }.onFailure {
                                message = it.message ?: "No se pudo crear la solicitud."
                            }
                            busy = false
                        }
                    }
                )
            }
            if (confirmEnd && current != null) {
                val requestId = current.id
                AlertDialog(
                    onDismissRequest = { confirmEnd = false },
                    title = { Text("¿Finalizar tránsito?") },
                    text = {
                        Text("El cuidado temporal termina. La organización responsable sigue a cargo.")
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                confirmEnd = false
                                scope.launch {
                                    busy = true
                                    runCatching {
                                        repository.completeFosterTransit(requestId).getOrThrow()
                                        reload()
                                        val finishedRequest = request
                                        if (
                                            finishedRequest == null ||
                                            finishedRequest.id != requestId ||
                                            finishedRequest.status != "COMPLETED" ||
                                            finishedRequest.placementStatus != "CLOSED" ||
                                            transit != null
                                        ) {
                                            error("El servidor no confirmó el cierre.")
                                        }
                                    }.onSuccess {
                                        message = "Tránsito finalizado."
                                    }.onFailure {
                                        message = it.message ?: "No se pudo finalizar el tránsito."
                                    }
                                    busy = false
                                }
                            }
                        ) { Text("Finalizar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmEnd = false }) { Text("Cancelar") }
                    }
                )
            }
        }
    }
}

@Composable
fun OpenFosterRequestsScreen(onNavigateBack: () -> Unit) {
    var rows by remember { mutableStateOf(listOf<CanonicalOpenFosterRequest>()) }
    var applications by remember { mutableStateOf(listOf<CanonicalFosterTransitApplication>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val repository = DataProvider.canonicalFosterTransitRepository

    suspend fun reload() {
        rows = repository.listOpenFosterRequests().getOrThrow()
        applications = repository.listMyFosterApplications().getOrThrow()
    }

    LaunchedEffect(Unit) {
        runCatching { reload() }
            .onFailure { message = it.message ?: "No se pudieron leer las solicitudes." }
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Solicitudes de tránsito",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(LeoDimens.SpaceMd).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            item { message?.let { Text(it, style = LeoCaption) } }
            val openIds = rows.map { it.id }.toSet()
            val recovered = applications.filter { it.requestId !in openIds }
            if (recovered.isNotEmpty()) {
                item { Text("Tus postulaciones", style = LeoCaption) }
                items(recovered, key = { "app-${it.id}" }) { mine ->
                    Text(applicationLine(mine))
                    Text(applicationCaption(mine), style = LeoCaption)
                }
            }
            if (rows.isEmpty() && recovered.isEmpty()) {
                item { Text("No hay solicitudes abiertas.", style = LeoCaption) }
            }
            items(rows, key = { it.id }) { row ->
                val mine = runCatching {
                    CanonicalFosterTransitRecovery.applicationForRequest(applications, row.id)
                }.getOrNull()
                Text("${row.petName.orEmpty()} · ${row.species.orEmpty()}")
                Text(row.needs.orEmpty(), style = LeoCaption)
                if (mine != null) {
                    Text("Postulación ${mine.status}", style = LeoCaption)
                }
                LeoPrimaryButton(
                    text = if (mine == null) "Postularme" else "Reintentar postulación",
                    enabled = busyId == null,
                    onClick = {
                        scope.launch {
                            busyId = row.id
                            runCatching {
                                val id = repository.applyToFosterRequest(row.id).getOrThrow()
                                reload()
                                val recovered = CanonicalFosterTransitRecovery.applicationForRequest(
                                    applications,
                                    row.id
                                )
                                if (recovered == null || recovered.id != id) {
                                    error("La postulación no volvió del servidor.")
                                }
                                recovered.status
                            }.onSuccess { status ->
                                message = "Postulación $status. El responsable elige."
                            }.onFailure {
                                message = it.message ?: "No se pudo postular."
                            }
                            busyId = null
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ChooseFosterApplicantScreen(requestId: String, onNavigateBack: () -> Unit) {
    var rows by remember { mutableStateOf(listOf<CanonicalFosterApplicantRow>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repository = DataProvider.canonicalFosterTransitRepository

    suspend fun reload() {
        rows = repository.listFosterRequestApplications(requestId).getOrThrow()
    }

    LaunchedEffect(requestId) {
        runCatching { reload() }
            .onFailure { message = it.message ?: "No se pudieron leer las postulaciones." }
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = "Elegir hogar", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            item { Text("Elegí un hogar. No se adjudica solo.", style = LeoCaption) }
            item { message?.let { Text(it, style = LeoCaption) } }
            items(rows, key = { it.id }) { row ->
                Text("${row.fosterName.orEmpty()} · ${row.status}")
                if (row.status == "PENDING") {
                    LeoOutlinedButton(
                        text = "Elegir este hogar",
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                runCatching {
                                    repository.selectFosterApplicant(row.id).getOrThrow()
                                    reload()
                                }.onSuccess {
                                    message = "Tránsito activo. Misma mascota, misma VitaCora."
                                }.onFailure {
                                    message = it.message ?: "No se pudo elegir."
                                }
                                busy = false
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun applicationLine(application: CanonicalFosterTransitApplication): String {
    val name = application.petName.orEmpty()
    return if (CanonicalFosterTransitRecovery.isHistoricalSelection(application)) {
        "$name · tránsito finalizado"
    } else {
        "$name · ${application.status}"
    }
}

private fun applicationCaption(application: CanonicalFosterTransitApplication): String {
    return when {
        CanonicalFosterTransitRecovery.isHistoricalSelection(application) ->
            "Antecedente SELECTED. Solicitud ${application.requestStatus}. Alojamiento ${application.placementStatus}."
        CanonicalFosterTransitRecovery.showsActiveTemporaryCare(application) ->
            "Tránsito activo. Solicitud ${application.requestStatus}."
        else -> "Estado recuperado del servidor."
    }
}

private fun activeTransitLabel(transit: CanonicalActiveFosterTransit): String {
    val role = transit.temporaryHolderRole.orEmpty()
    return if (role == "AUTHORIZED") {
        "En tránsito. El hogar tiene cuidado temporal autorizado. La organización responsable sigue a cargo."
    } else {
        "Tránsito ${transit.requestStatus}."
    }
}
