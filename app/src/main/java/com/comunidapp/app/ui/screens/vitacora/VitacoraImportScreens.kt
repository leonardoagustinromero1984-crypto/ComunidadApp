package com.comunidapp.app.ui.screens.vitacora

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.domain.onboarding.onb02.TutorialCatalog
import com.comunidapp.app.domain.onboarding.onb02.TutorialId
import com.comunidapp.app.domain.vitacora.import.VitacoraImportCopy
import com.comunidapp.app.domain.vitacora.import.VitacoraImportJob
import com.comunidapp.app.domain.vitacora.import.VitacoraImportJobStatus
import com.comunidapp.app.domain.vitacora.import.VitacoraImportMode
import com.comunidapp.app.domain.vitacora.import.VitacoraImportOrgHit
import com.comunidapp.app.domain.vitacora.import.VitacoraImportPolicy
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.leo.LeoVerTutorialPager
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.VitacoraImportUiState
import com.comunidapp.app.viewmodel.VitacoraImportViewModel
import com.comunidapp.app.viewmodel.labelEs
import java.io.File

@Composable
fun VitacoraImportScreen(
    orgId: String,
    orgName: String,
    isStaff: Boolean,
    onNavigateBack: () -> Unit,
    onViewImported: () -> Unit = {},
    viewModel: VitacoraImportViewModel = viewModel(
        factory = VitacoraImportViewModel.factory(orgId, orgName, VitacoraImportMode.SELF_SERVICE, isStaff)
    )
) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFile(uri, uri.lastPathSegment.orEmpty(), assisted = false)
    }
    val assistedPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFile(uri, uri.lastPathSegment.orEmpty(), assisted = true)
    }

    if (ui.showTutorial) {
        val def = TutorialCatalog.definition(TutorialId.T20_VITACORA_IMPORT)
        Scaffold(containerColor = BrandBackground, topBar = {
            LeoTopAppBar(title = VitacoraImportCopy.HOW_TO, showBackButton = true, onBackClick = { viewModel.showTutorial(false) })
        }) { padding ->
            Column(Modifier.padding(padding)) {
                LeoVerTutorialPager(
                    definition = def,
                    stepIndex = 0,
                    onPrimary = { viewModel.showTutorial(false) },
                    onSkip = { viewModel.showTutorial(false) }
                )
            }
        }
        return
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = { LeoTopAppBar(title = VitacoraImportCopy.TITLE, showBackButton = true, onBackClick = onNavigateBack) }
    ) { padding ->
        val job = ui.job
        if (job != null && job.status != VitacoraImportJobStatus.PENDING) {
            ImportPreview(
                job = job,
                busy = ui.busy,
                message = ui.message,
                onConfirm = viewModel::confirm,
                onAnalyze = viewModel::analyzeCurrent,
                onViewImported = onViewImported,
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Importá varias mascotas a la vez con la plantilla oficial de LeoVer. Se crean VitaCoras; no se publican en adopción ni se agregan fotos.")
            LeoPrimaryButton(
                text = VitacoraImportCopy.DOWNLOAD,
                onClick = {
                    val bytes = viewModel.templateBytes()
                    val file = File(context.cacheDir, VitacoraImportPolicy.PUBLISHED_FILENAME)
                    file.writeBytes(bytes)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, VitacoraImportCopy.DOWNLOAD))
                }
            )
            LeoOutlinedButton(
                text = VitacoraImportCopy.HOW_TO,
                onClick = { viewModel.showTutorial(true) }
            )
            LeoOutlinedButton(
                text = VitacoraImportCopy.SELECT_FILE,
                onClick = {
                    picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream"))
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(VitacoraImportCopy.ASSISTED_TITLE, fontWeight = FontWeight.SemiBold)
            Text(VitacoraImportCopy.ASSISTED_BODY)
            OutlinedTextField(
                value = ui.comment,
                onValueChange = viewModel::onComment,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Comentario (opcional)") }
            )
            LeoOutlinedButton(
                text = VitacoraImportCopy.ASSISTED_CTA,
                onClick = {
                    assistedPicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                }
            )
            ui.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (ui.busy) Text("Analizando…")
        }
    }
}

@Composable
private fun ImportPreview(
    job: VitacoraImportJob,
    busy: Boolean,
    message: String?,
    onConfirm: () -> Unit,
    onAnalyze: () -> Unit = {},
    onViewImported: () -> Unit = {},
    modifier: Modifier
) {
    val s = job.summary
    val importable = s.ready + s.warnings
    Column(
        modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("IMPORTACIÓN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Total: ${s.total}")
        Text("Listas para importar: ${s.ready}")
        Text("Con observaciones: ${s.warnings}")
        Text("Con errores: ${s.errors}")
        Text("Posibles duplicadas: ${s.possibleDuplicates}")
        Text("Ya existentes: ${s.alreadyExists}")
        if (job.status == VitacoraImportJobStatus.COMPLETED || job.status == VitacoraImportJobStatus.COMPLETED_WITH_ERRORS) {
            Text(VitacoraImportCopy.COMPLETED, fontWeight = FontWeight.Bold)
            Text("${s.created} VitaCoras creadas")
            Text("${job.rows.count { it.createdPetId != null }} ${VitacoraImportCopy.NEEDS_PHOTO.lowercase()}")
            LeoOutlinedButton(
                text = "Ver mascotas importadas",
                onClick = onViewImported
            )
        } else if (job.status == VitacoraImportJobStatus.PENDING || job.status == VitacoraImportJobStatus.UPLOADED) {
            LeoOutlinedButton(
                text = "Analizar",
                onClick = onAnalyze,
                enabled = !busy
            )
        } else if (importable > 0 && job.status == VitacoraImportJobStatus.READY) {
            Text("$importable mascotas están listas para crear.")
            LeoPrimaryButton(
                text = VitacoraImportCopy.CREATE_N.format(importable),
                onClick = onConfirm,
                enabled = !busy
            )
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        job.rows.filter { it.issues.isNotEmpty() }.take(40).forEach { row ->
            val issue = row.issues.first()
            Text("Fila ${row.rowNumber}", fontWeight = FontWeight.SemiBold)
            Text("Mascota: ${row.name ?: "—"}")
            Text("Campo: ${issue.field}")
            Text("Problema: ${issue.message}")
            Text("Sugerencia: ${issue.suggestion}")
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun AdminVitacoraImportQueueScreen(
    onNavigateBack: () -> Unit,
    onNew: () -> Unit,
    viewModel: VitacoraImportViewModel = viewModel(
        factory = VitacoraImportViewModel.factory("", "", VitacoraImportMode.ADMIN, true)
    )
) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadJobs(null) }
    Scaffold(
        containerColor = BrandBackground,
        topBar = { LeoTopAppBar(title = "Importaciones", showBackButton = true, onBackClick = onNavigateBack) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LeoPrimaryButton(
                text = "Nueva importación",
                onClick = onNew
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                listOf(
                    null to "Todas",
                    "PENDING" to "Pendientes",
                    "EN_REVISION" to "En revisión",
                    "READY" to "Listas para importar",
                    "COMPLETED" to "Completadas",
                    "FAILED" to "Con errores"
                ).forEach { (code, label) ->
                    LeoFilterChip(
                        label = label,
                        selected = ui.filter == code,
                        onClick = { viewModel.loadJobs(code) }
                    )
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS), modifier = Modifier.weight(1f, fill = true)) {
                items(ui.jobs, key = { it.id }) { job ->
                    LeoListRow(
                        title = job.organizationName ?: "Organización",
                        subtitle = "${job.status.labelEs()} · ${job.mode.name}",
                        onClick = { viewModel.openJob(job) }
                    )
                }
            }
            ui.job?.let { job ->
                ImportPreview(job, ui.busy, ui.message, viewModel::confirm, viewModel::analyzeCurrent, {}, Modifier)
            }
        }
    }
}

@Composable
fun AdminVitacoraImportNewScreen(onNavigateBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val searchVm: VitacoraImportViewModel = viewModel(
        factory = VitacoraImportViewModel.factory("", "", VitacoraImportMode.ADMIN, true)
    )
    val searchUi by searchVm.ui.collectAsState()
    val selected = searchUi.selectedOrg
    AdminImportSelected(
        query = query,
        onQuery = {
            query = it
            searchVm.searchOrgs(it)
        },
        ui = searchUi,
        onSelect = searchVm::selectOrg,
        selected = selected,
        onBack = onNavigateBack
    )
}

@Composable
private fun AdminImportSelected(
    query: String,
    onQuery: (String) -> Unit,
    ui: VitacoraImportUiState,
    onSelect: (VitacoraImportOrgHit) -> Unit,
    selected: VitacoraImportOrgHit?,
    onBack: () -> Unit
) {
    val importVm: VitacoraImportViewModel = viewModel(
        key = selected?.id ?: "none",
        factory = VitacoraImportViewModel.factory(
            selected?.id.orEmpty(),
            selected?.name.orEmpty(),
            VitacoraImportMode.ADMIN,
            true
        )
    )
    val importUi by importVm.ui.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && selected != null) importVm.importFile(uri, uri.lastPathSegment.orEmpty())
    }
    Scaffold(
        containerColor = BrandBackground,
        topBar = { LeoTopAppBar(title = "Nueva importación", showBackButton = true, onBackClick = onBack) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar organización") },
                singleLine = true
            )
            ui.orgs.forEach { org ->
                Text(
                    "${org.name} · @${org.slug}",
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(org) }.padding(8.dp),
                    fontWeight = if (org.id == selected?.id) FontWeight.Bold else FontWeight.Normal
                )
            }
            selected?.let { Text("Seleccionada: ${it.name}", fontWeight = FontWeight.SemiBold) }
            LeoOutlinedButton(
                text = VitacoraImportCopy.SELECT_FILE,
                onClick = { picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
                enabled = selected != null
            )
            importUi.job?.let { job ->
                ImportPreview(job, importUi.busy, importUi.message, importVm::confirm, importVm::analyzeCurrent, {}, Modifier)
            }
            importUi.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
