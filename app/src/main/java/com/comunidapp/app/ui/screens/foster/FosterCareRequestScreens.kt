package com.comunidapp.app.ui.screens.foster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.CanonicalActiveFosterTransit
import com.comunidapp.app.data.repository.CanonicalFosterApplicantRow
import com.comunidapp.app.data.repository.CanonicalFosterTransitApplication
import com.comunidapp.app.data.repository.CanonicalFosterTransitRecovery
import com.comunidapp.app.data.repository.CanonicalFosterTransitRepository
import com.comunidapp.app.data.repository.CanonicalFosterTransitRequest
import com.comunidapp.app.data.repository.CanonicalOpenFosterRequest
import com.comunidapp.app.data.repository.toFosterNeeds
import com.comunidapp.app.data.repository.toHomeCapabilities
import com.comunidapp.app.domain.foster.FOSTER_APPLICANTS_EMPTY
import com.comunidapp.app.domain.foster.FOSTER_APPLICANTS_EMPTY_HINT
import com.comunidapp.app.domain.foster.FOSTER_CHOOSE_ACTION
import com.comunidapp.app.domain.foster.FOSTER_CHOOSE_HOME
import com.comunidapp.app.domain.foster.FosterHomeCapabilities
import com.comunidapp.app.domain.foster.FosterLifeStage
import com.comunidapp.app.domain.foster.FosterMatchingPolicy
import com.comunidapp.app.domain.foster.FosterRequestPresentation
import com.comunidapp.app.domain.foster.FosterSizeBand
import com.comunidapp.app.domain.foster.FosterTransitSignals
import com.comunidapp.app.domain.foster.FosterTransitSnapshot
import com.comunidapp.app.domain.foster.FoundPetFosterNeeds
import com.comunidapp.app.domain.user.SessionGeneration
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
    var size by remember { mutableStateOf<FosterSizeBand?>(null) }
    var lifeStage by remember { mutableStateOf<FosterLifeStage?>(null) }
    var needsMedication by remember { mutableStateOf<Boolean?>(null) }
    var cohabitsDogs by remember { mutableStateOf<Boolean?>(null) }
    var cohabitsCats by remember { mutableStateOf<Boolean?>(null) }
    var cohabitsChildren by remember { mutableStateOf<Boolean?>(null) }
    var reducedMobility by remember { mutableStateOf<Boolean?>(null) }
    var needsIsolation by remember { mutableStateOf<Boolean?>(null) }
    var additionalInfo by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var request by remember { mutableStateOf<CanonicalFosterTransitRequest?>(null) }
    var transit by remember { mutableStateOf<CanonicalActiveFosterTransit?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repository = DataProvider.canonicalFosterTransitRepository

    suspend fun applyListed(
        token: Long,
        listed: List<CanonicalFosterTransitRequest>
    ) {
        val open = CanonicalFosterTransitRecovery.requestForPet(listed, petId)
        val shown = open ?: CanonicalFosterTransitRecovery.completedRequestForPet(listed, petId)
        val published = FosterTransitSignals.live.publish(
            token,
            FosterTransitSnapshot(petId, open?.id, open?.status)
        )
        if (!published) return
        request = shown
        transit = if (open != null && open.status in setOf("MATCHED", "ACTIVE")) {
            repository.getActiveFosterTransit(petId).getOrNull()
        } else {
            null
        }
    }

    suspend fun reload() {
        val token = SessionGeneration.current()
        val listed = repository.listMyFosterRequests().getOrThrow()
        applyListed(token, listed)
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
            Modifier
                .padding(padding)
                .padding(LeoDimens.SpaceMd)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            val current = request
            val finished = current?.status == "COMPLETED"
            if (current == null) {
                Text(
                    "Contá lo que sepas. Si no sabés, podés enviar igual.",
                    style = LeoCaption
                )
                FoundAnimalTraitForm(
                    size, { size = it },
                    lifeStage, { lifeStage = it },
                    needsMedication, { needsMedication = it },
                    cohabitsDogs, { cohabitsDogs = it },
                    cohabitsCats, { cohabitsCats = it },
                    cohabitsChildren, { cohabitsChildren = it },
                    reducedMobility, { reducedMobility = it },
                    needsIsolation, { needsIsolation = it }
                )
                OutlinedTextField(
                    additionalInfo,
                    { additionalInfo = it },
                    label = { Text("Información adicional") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    FosterRequestPresentation.requestStatus(current.status),
                    style = LeoCaption
                )
                Text(FOSTER_CHOOSE_HOME, style = LeoCaption)
            }
            transit?.let { active ->
                Text(activeTransitLabel(active), style = LeoCaption)
            }
            message?.let { Text(it, style = LeoCaption) }
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
            if (current != null && (current.status == "REQUESTED" || current.status == "MATCHED")) {
                LeoOutlinedButton(
                    text = "Cancelar solicitud",
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val token = SessionGeneration.current()
                            runCatching {
                                repository.cancelFosterRequest(current.id).getOrThrow()
                                val listed = repository.listMyFosterRequests().getOrThrow()
                                applyListed(token, listed)
                            }.onSuccess {
                                message = "Solicitud cancelada."
                            }.onFailure {
                                message = it.message ?: "No se pudo cancelar la solicitud."
                            }
                            busy = false
                        }
                    }
                )
            }
            if (finished) {
                Text("Tránsito finalizado.", style = LeoCaption)
                Text(
                    "El hogar temporal ya no figura como cuidador activo.",
                    style = LeoCaption
                )
            }
            if (current == null) {
                LeoPrimaryButton(
                    text = "Publicar solicitud",
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val needs = ""
                            val notes = additionalInfo
                            val traits = FoundPetFosterNeeds(
                                size = size,
                                lifeStage = lifeStage,
                                needsMedication = needsMedication,
                                cohabitsDogs = cohabitsDogs,
                                cohabitsCats = cohabitsCats,
                                cohabitsChildren = cohabitsChildren,
                                reducedMobility = reducedMobility,
                                needsIsolation = needsIsolation,
                                additionalInfo = additionalInfo.ifBlank { null }
                            )
                            val token = SessionGeneration.current()
                            runCatching {
                                val id = repository.requestFosterForPet(petId, needs, notes, traits).getOrThrow()
                                val listed = repository.listMyFosterRequests().getOrThrow()
                                applyListed(token, listed)
                                val recovered = request
                                if (recovered == null || recovered.id != id) {
                                    error("No pudimos confirmar la solicitud.")
                                }
                                recovered.status
                            }.onSuccess {
                                message = "Solicitud enviada."
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
                                    val token = SessionGeneration.current()
                                    runCatching {
                                        repository.completeFosterTransit(requestId).getOrThrow()
                                        val listed = repository.listMyFosterRequests().getOrThrow()
                                        applyListed(token, listed)
                                        val finishedRequest = request
                                        if (
                                            finishedRequest == null ||
                                            finishedRequest.id != requestId ||
                                            finishedRequest.status != "COMPLETED" ||
                                            finishedRequest.placementStatus != "CLOSED" ||
                                            transit != null
                                        ) {
                                            error("No pudimos confirmar el cierre.")
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
    var home by remember { mutableStateOf(FosterHomeCapabilities()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val repository = DataProvider.canonicalFosterTransitRepository

    suspend fun reload() {
        rows = repository.listOpenFosterRequests().getOrThrow()
        applications = repository.listMyFosterApplications().getOrThrow()
        val uid = AuthProvider.repository.getCurrentUser()?.id.orEmpty()
        val mine = if (uid.isBlank()) {
            null
        } else {
            DataProvider.fosterHomeRepository.getFosterHomeById(uid).getOrNull()
        }
        home = (mine?.capabilities ?: FosterHomeCapabilities()).copy(
            active = true,
            hasBaseLocation = true,
            capacity = (mine?.capabilities?.capacity ?: 1).coerceAtLeast(1)
        )
    }

    LaunchedEffect(Unit) {
        runCatching { reload() }
            .onFailure { message = it.message ?: "No se pudieron leer las solicitudes." }
    }

    val visible = FosterMatchingPolicy.rankCandidates(rows, { it.toFosterNeeds() }, home)

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
            if (visible.isEmpty() && recovered.isEmpty()) {
                item { Text("No hay solicitudes abiertas.", style = LeoCaption) }
            }
            items(visible, key = { it.id }) { row ->
                val mine = runCatching {
                    CanonicalFosterTransitRecovery.applicationForRequest(applications, row.id)
                }.getOrNull()
                val species = FosterRequestPresentation.species(row.species)
                val title = listOf(row.petName.orEmpty(), species).filter { it.isNotBlank() }.joinToString(" · ")
                Text(title.ifBlank { "Animal encontrado" })
                val extra = row.additionalInfo?.ifBlank { null } ?: row.notes?.ifBlank { null }
                if (extra != null) Text(extra, style = LeoCaption)
                if (mine != null && mine.status == "PENDING") {
                    Text("Postulación enviada", style = LeoCaption)
                }
                if (mine == null || mine.status != "PENDING") {
                    LeoPrimaryButton(
                        text = "Postularme",
                        enabled = busyId == null,
                        onClick = {
                            scope.launch {
                                busyId = row.id
                                runCatching {
                                    val id = repository.applyToFosterRequest(row.id).getOrThrow()
                                    reload()
                                    val recoveredApp = CanonicalFosterTransitRecovery.applicationForRequest(
                                        applications,
                                        row.id
                                    )
                                    if (recoveredApp == null || recoveredApp.id != id) {
                                        error("No pudimos confirmar la postulación.")
                                    }
                                    recoveredApp.status
                                }.onSuccess {
                                    message = "Postulación enviada. El responsable elige."
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
            item { Text(FOSTER_CHOOSE_HOME, style = LeoCaption) }
            item { message?.let { Text(it, style = LeoCaption) } }
            if (rows.isEmpty()) {
                item { Text(FOSTER_APPLICANTS_EMPTY, style = LeoCaption) }
                item { Text(FOSTER_APPLICANTS_EMPTY_HINT, style = LeoCaption) }
            }
            items(rows, key = { it.id }) { row ->
                ApplicantRow(
                    row = row,
                    busy = busy,
                    onChoose = {
                        scope.launch {
                            busy = true
                            val token = SessionGeneration.current()
                            runCatching {
                                repository.selectFosterApplicant(row.id).getOrThrow()
                                reload()
                                publishSelectedRequest(repository, requestId, token)
                            }.onSuccess {
                                message = "Hogar seleccionado."
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

@Composable
private fun ApplicantRow(
    row: CanonicalFosterApplicantRow,
    busy: Boolean,
    onChoose: () -> Unit
) {
    val view = presentApplicant(row)
    Text(view.title)
    Text(view.zone, style = LeoCaption)
    Text(view.availability, style = LeoCaption)
    if (view.capacity.isNotBlank()) Text(view.capacity, style = LeoCaption)
    Text(view.compatibility, style = LeoCaption)
    if (view.canChoose) {
        LeoOutlinedButton(
            text = FOSTER_CHOOSE_ACTION,
            enabled = !busy,
            onClick = onChoose
        )
    }
}

private fun presentApplicant(row: CanonicalFosterApplicantRow) =
    com.comunidapp.app.domain.foster.presentFosterApplicant(
        name = row.fosterName,
        localityId = row.localityId,
        capacity = row.capacity,
        profileActive = row.profileActive,
        hasBaseLocation = row.hasBaseLocation,
        applicationStatus = row.status,
        needs = row.toFosterNeeds(),
        home = row.toHomeCapabilities(),
        callerIsManager = true,
        applicationExists = row.id.isNotBlank()
    )

private suspend fun publishSelectedRequest(
    repository: CanonicalFosterTransitRepository,
    requestId: String,
    token: Long
) {
    val listed = repository.listMyFosterRequests().getOrThrow()
    val mine = listed.firstOrNull { it.id == requestId } ?: return
    FosterTransitSignals.live.publish(
        token,
        FosterTransitSnapshot(mine.petId, mine.id, mine.status)
    )
}

private fun applicationLine(application: CanonicalFosterTransitApplication): String {
    val name = application.petName.orEmpty().ifBlank { "Animal" }
    val species = FosterRequestPresentation.species(application.species)
    val who = listOf(name, species).filter { it.isNotBlank() }.joinToString(" · ")
    return if (CanonicalFosterTransitRecovery.isHistoricalSelection(application)) {
        "$who · tránsito finalizado"
    } else {
        "$who · ${FosterRequestPresentation.applicationStatus(application.status)}"
    }
}

private fun applicationCaption(application: CanonicalFosterTransitApplication): String {
    return when {
        CanonicalFosterTransitRecovery.isHistoricalSelection(application) ->
            "Tránsito finalizado."
        CanonicalFosterTransitRecovery.showsActiveTemporaryCare(application) ->
            "Tránsito activo."
        else -> "Postulación enviada."
    }
}

private fun activeTransitLabel(transit: CanonicalActiveFosterTransit): String {
    val role = transit.temporaryHolderRole.orEmpty()
    return if (role == "AUTHORIZED") {
        "En tránsito. El hogar tiene cuidado temporal autorizado. La organización responsable sigue a cargo."
    } else {
        "Hogar seleccionado."
    }
}
