package com.comunidapp.app.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.domain.authorization.AdminAccessPolicy
import com.comunidapp.app.ui.components.leo.LeoSettingsRow
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.MasterCatalogTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private data class CatalogsIndexUiState(
    val accessChecked: Boolean = false,
    val accessAllowed: Boolean = false
)

private class CatalogsIndexViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CatalogsIndexUiState())
    val uiState: StateFlow<CatalogsIndexUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = AuthProvider.repository.getCurrentUser()
            if (user == null) {
                _uiState.value = CatalogsIndexUiState(accessChecked = true)
                return@launch
            }
            val ctx = DataProvider.permissionRepository.refresh(user.id)
            _uiState.value = CatalogsIndexUiState(
                accessChecked = true,
                accessAllowed = AdminAccessPolicy.canSeeCatalogs(ctx)
            )
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CatalogsIndexViewModel() as T
        }
    }
}

@Composable
fun CatalogsIndexScreen(
    onNavigateBack: () -> Unit,
    onOpenCatalog: (String) -> Unit,
    viewModel: androidx.lifecycle.ViewModel = viewModel(factory = CatalogsIndexViewModel.factory())
) {
    val uiState by (viewModel as CatalogsIndexViewModel).uiState.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Catálogos",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when {
            !uiState.accessChecked -> LoadingState(contentModifier = Modifier.padding(padding))
            !uiState.accessAllowed -> {
                LaunchedEffect(Unit) { onNavigateBack() }
                Column(Modifier.padding(padding).padding(24.dp)) {
                    Text("No tenés permiso para ver catálogos.")
                }
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(LeoDimens.SpaceMd)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
            ) {
                Text(
                    "Datos maestros de LeoVer. Desactivar conserva el historial.",
                    style = LeoCaption,
                    color = BrandTextSecondary
                )
                catalogCards().forEach { (key, title, description, icon) ->
                    LeoSettingsRow(
                        title = title,
                        description = description,
                        icon = icon,
                        onClick = { onOpenCatalog(key) }
                    )
                }
            }
        }
    }
}

private fun catalogCards() = listOf(
    Quad("species", "Especies", "Especies, estado y clasificación secundaria", Icons.Default.Pets),
    Quad("vaccines", MasterCatalogTab.title(MasterCatalogTab.VACCINES), "Productos de vacunación", Icons.Default.Category),
    Quad("flea", "Antipulgas / antiparasitarios", "Antipulgas y garrapatas", Icons.Default.Category),
    Quad("dewormers", MasterCatalogTab.title(MasterCatalogTab.DEWORMERS), "Desparasitantes", Icons.Default.Category),
    Quad(
        "service_categories",
        MasterCatalogTab.title(MasterCatalogTab.SERVICE_CATEGORIES),
        "Categorías de servicios",
        Icons.Default.Category
    )
)

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
