package com.comunidapp.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.VitacoraImportRepository
import com.comunidapp.app.domain.vitacora.import.VitacoraImportAuth
import com.comunidapp.app.domain.vitacora.import.VitacoraImportJob
import com.comunidapp.app.domain.vitacora.import.VitacoraImportJobStatus
import com.comunidapp.app.domain.vitacora.import.VitacoraImportMode
import com.comunidapp.app.domain.vitacora.import.VitacoraImportOrgHit
import com.comunidapp.app.domain.vitacora.import.VitacoraImportPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VitacoraImportUiState(
    val job: VitacoraImportJob? = null,
    val jobs: List<VitacoraImportJob> = emptyList(),
    val orgs: List<VitacoraImportOrgHit> = emptyList(),
    val selectedOrg: VitacoraImportOrgHit? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val comment: String = "",
    val filter: String? = null,
    val showTutorial: Boolean = false,
    val confirming: Boolean = false
)

class VitacoraImportViewModel(
    application: Application,
    private val orgId: String,
    private val orgName: String,
    private val mode: VitacoraImportMode,
    private val isStaff: Boolean,
    private val repo: VitacoraImportRepository = DataProvider.vitacoraImportRepository
) : AndroidViewModel(application) {

    private val _ui = MutableStateFlow(VitacoraImportUiState())
    val ui: StateFlow<VitacoraImportUiState> = _ui.asStateFlow()

    fun onComment(value: String) {
        _ui.value = _ui.value.copy(comment = value)
    }

    fun showTutorial(show: Boolean) {
        _ui.value = _ui.value.copy(showTutorial = show)
    }

    fun templateBytes(): ByteArray = repo.templateBytes()

    fun importFile(uri: Uri, fileName: String, assisted: Boolean = false) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, message = null)
            val bytes = runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes == null) {
                _ui.value = _ui.value.copy(busy = false, message = "No pudimos leer el archivo.")
                return@launch
            }
            if (bytes.size > VitacoraImportPolicy.MAX_FILE_SIZE_BYTES) {
                _ui.value = _ui.value.copy(busy = false, message = "El archivo supera 5 MB.")
                return@launch
            }
            val result = repo.createAndAnalyze(
                orgId = orgId,
                orgName = orgName,
                mode = if (assisted) VitacoraImportMode.ASSISTED else mode,
                bytes = bytes,
                fileName = fileName,
                comment = _ui.value.comment.ifBlank { null },
                auth = currentAuth()
            )
            _ui.value = result.fold(
                onSuccess = {
                    _ui.value.copy(
                        busy = false,
                        job = it,
                        message = if (assisted) "Solicitud enviada al equipo de LeoVer." else null
                    )
                },
                onFailure = {
                    _ui.value.copy(busy = false, message = human(it))
                }
            )
        }
    }

    fun confirm() {
        val job = _ui.value.job ?: return
        if (_ui.value.confirming) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(confirming = true, busy = true, message = null)
            val result = repo.confirm(job.id, currentAuth())
            _ui.value = result.fold(
                onSuccess = { _ui.value.copy(busy = false, confirming = false, job = it) },
                onFailure = { _ui.value.copy(busy = false, confirming = false, message = human(it)) }
            )
        }
    }

    fun refreshJob() {
        val id = _ui.value.job?.id ?: return
        viewModelScope.launch {
            repo.get(id, currentAuth()).onSuccess { _ui.value = _ui.value.copy(job = it) }
        }
    }

    fun loadJobs(status: String? = _ui.value.filter) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, filter = status)
            repo.list(if (isStaff && orgId.isBlank()) null else orgId.ifBlank { null }, status, currentAuth())
                .onSuccess { _ui.value = _ui.value.copy(busy = false, jobs = it) }
                .onFailure { _ui.value = _ui.value.copy(busy = false, message = human(it)) }
        }
    }

    fun searchOrgs(query: String) {
        viewModelScope.launch {
            repo.searchOrganizations(query).onSuccess { _ui.value = _ui.value.copy(orgs = it) }
        }
    }

    fun selectOrg(hit: VitacoraImportOrgHit) {
        _ui.value = _ui.value.copy(selectedOrg = hit)
    }

    fun openJob(job: VitacoraImportJob) {
        _ui.value = _ui.value.copy(job = job)
    }

    fun analyzeCurrent() {
        val job = _ui.value.job ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, message = null)
            repo.analyze(job.id, currentAuth())
                .onSuccess { _ui.value = _ui.value.copy(busy = false, job = it) }
                .onFailure { _ui.value = _ui.value.copy(busy = false, message = human(it)) }
        }
    }

    private fun currentAuth(): VitacoraImportAuth {
        val userId = AuthProvider.repository.getCurrentUser()?.id.orEmpty()
        return VitacoraImportAuth(
            actorId = userId,
            isStaff = isStaff,
            membershipActive = true,
            permissions = setOf(VitacoraImportPolicy.ORG_IMPORT_PERMISSION, VitacoraImportPolicy.ORG_PETS_MANAGE_PERMISSION),
            organizationVerified = true,
            requestedOrganizationId = orgId.ifBlank { null }
        )
    }

    private fun human(error: Throwable): String {
        val msg = error.message.orEmpty()
        return when {
            msg.contains("FORBIDDEN") -> "No tenés permiso para esta importación."
            msg.contains("ORG_NOT_VERIFIED") -> "La organización tiene que estar verificada."
            msg.contains("MAX_FILE") -> "El archivo es demasiado grande."
            msg.contains("XLS") -> "Solo se admite un archivo .xlsx."
            else -> "No pudimos completar la importación. Probá de nuevo."
        }
    }

    companion object {
        fun factory(
            orgId: String,
            orgName: String,
            mode: VitacoraImportMode,
            isStaff: Boolean
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = com.comunidapp.app.LeoverApplication.instance
                return VitacoraImportViewModel(app, orgId, orgName, mode, isStaff) as T
            }
        }
    }
}

fun VitacoraImportJobStatus.labelEs(): String = when (this) {
    VitacoraImportJobStatus.UPLOADED -> "Subida"
    VitacoraImportJobStatus.ANALYZING, VitacoraImportJobStatus.EN_REVISION -> "En revisión"
    VitacoraImportJobStatus.PENDING -> "Pendiente"
    VitacoraImportJobStatus.READY -> "Lista para importar"
    VitacoraImportJobStatus.IMPORTING -> "Importando"
    VitacoraImportJobStatus.COMPLETED -> "Completada"
    VitacoraImportJobStatus.COMPLETED_WITH_ERRORS -> "Completada con observaciones"
    VitacoraImportJobStatus.REQUIRES_CORRECTION -> "Necesita corrección"
    VitacoraImportJobStatus.CANCELLED -> "Cancelada"
    VitacoraImportJobStatus.FAILED -> "Con errores"
}
