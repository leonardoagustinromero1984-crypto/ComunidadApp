package com.comunidapp.app.ui.screens.publish

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.DonationType
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.viewmodel.PublishViewModel

@Composable
fun PublishGeneralScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) = PublishFeedTypeScreen(
    screenTitle = "Publicación general",
    onNavigateBack = onNavigateBack,
    onPublishSuccess = onPublishSuccess,
    onSubmit = viewModel::publishGeneral,
    viewModel = viewModel
)

@Composable
fun PublishUrgentScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) = PublishFeedTypeScreen(
    screenTitle = "Aviso urgente",
    onNavigateBack = onNavigateBack,
    onPublishSuccess = onPublishSuccess,
    onSubmit = viewModel::publishUrgent,
    viewModel = viewModel
)

@Composable
fun PublishQuestionScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) = PublishFeedTypeScreen(
    screenTitle = "Pregunta a la comunidad",
    onNavigateBack = onNavigateBack,
    onPublishSuccess = onPublishSuccess,
    onSubmit = viewModel::publishQuestion,
    viewModel = viewModel
)

@Composable
fun PublishPromoScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) = PublishFeedTypeScreen(
    screenTitle = "Publicidad / promo",
    onNavigateBack = onNavigateBack,
    onPublishSuccess = onPublishSuccess,
    onSubmit = viewModel::publishPromo,
    viewModel = viewModel
)

@Composable
private fun PublishFeedTypeScreen(
    screenTitle: String,
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    onSubmit: (String, String, String, android.net.Uri?) -> Unit,
    viewModel: PublishViewModel
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val formState by viewModel.formState.collectAsState()

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> imageUri = uri }

    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetFormState()
            onPublishSuccess()
        }
    }

    PublishFormScaffold(
        title = screenTitle,
        onNavigateBack = onNavigateBack,
        isLoading = formState.isLoading,
        errorMessage = formState.errorMessage,
        onSubmit = { onSubmit(title, content, location, imageUri) }
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Título") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Contenido") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Ubicación (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        imageUri?.let { uri ->
            PetImage(
                imageUrl = uri.toString(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                cornerRadius = 8.dp,
                contentDescription = "Imagen de la publicación"
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = {
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (imageUri == null) "Agregar imagen (opcional)" else "Cambiar imagen")
        }
    }
}

@Composable
fun PublishAdoptionScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) {
    var name by remember { mutableStateOf("") }
    var species by remember { mutableStateOf(PetSpecies.DOG) }
    var sex by remember { mutableStateOf(PetSex.MALE) }
    var ageYears by remember { mutableIntStateOf(1) }
    var size by remember { mutableStateOf(PetSize.MEDIUM) }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val formState by viewModel.formState.collectAsState()
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> imageUri = uri }

    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetFormState()
            onPublishSuccess()
        }
    }

    PublishFormScaffold(
        title = "Publicar adopción",
        onNavigateBack = onNavigateBack,
        isLoading = formState.isLoading,
        errorMessage = formState.errorMessage,
        onSubmit = {
            viewModel.publishAdoption(name, species, sex, ageYears, size, location, description, imageUri)
        }
    ) {
        imageUri?.let { uri ->
            PetImage(
                imageUrl = uri.toString(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                cornerRadius = 8.dp,
                contentDescription = "Foto del animal"
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = {
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (imageUri == null) "Agregar foto (obligatoria)" else "Cambiar foto")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre del animal") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        SpeciesChipRow(selected = species, onSelect = { species = it })
        Spacer(modifier = Modifier.height(8.dp))
        SexChipRow(selected = sex, onSelect = { sex = it })
        Spacer(modifier = Modifier.height(8.dp))
        SizeChipRow(selected = size, onSelect = { size = it })
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = ageYears.toString(),
            onValueChange = { ageYears = it.toIntOrNull() ?: 0 },
            label = { Text("Edad (años)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Zona") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descripción") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
    }
}

@Composable
fun PublishLostFoundScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) {
    var type by remember { mutableStateOf(LostFoundType.LOST) }
    var petName by remember { mutableStateOf("") }
    var species by remember { mutableStateOf(PetSpecies.DOG) }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var contactInfo by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val formState by viewModel.formState.collectAsState()
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> imageUri = uri }

    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetFormState()
            onPublishSuccess()
        }
    }

    PublishFormScaffold(
        title = "Perdido / Encontrado",
        onNavigateBack = onNavigateBack,
        isLoading = formState.isLoading,
        errorMessage = formState.errorMessage,
        onSubmit = {
            viewModel.publishLostFound(type, petName, species, location, description, contactInfo, imageUri)
        }
    ) {
        imageUri?.let { uri ->
            PetImage(
                imageUrl = uri.toString(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                cornerRadius = 8.dp,
                contentDescription = "Foto de la mascota"
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = {
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (imageUri == null) "Agregar foto (obligatoria)" else "Cambiar foto")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = type == LostFoundType.LOST,
                onClick = { type = LostFoundType.LOST },
                label = { Text("Perdido") }
            )
            FilterChip(
                selected = type == LostFoundType.FOUND,
                onClick = { type = LostFoundType.FOUND },
                label = { Text("Encontrado") }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = petName,
            onValueChange = { petName = it },
            label = { Text("Nombre (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        SpeciesChipRow(selected = species, onSelect = { species = it })
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Zona") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descripción") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = contactInfo,
            onValueChange = { contactInfo = it },
            label = { Text("Contacto") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
private fun PublishFormScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onSubmit: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            ComunidappTopBar(title = title, showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            content()
            errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Publicar")
                }
            }
        }
    }
}

@Composable
private fun SpeciesChipRow(selected: PetSpecies, onSelect: (PetSpecies) -> Unit) {
    Text(text = "Especie", style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PetSpecies.entries.forEach { entry ->
            FilterChip(
                selected = selected == entry,
                onClick = { onSelect(entry) },
                label = { Text(entry.toDisplayName()) }
            )
        }
    }
}

@Composable
private fun SexChipRow(selected: PetSex, onSelect: (PetSex) -> Unit) {
    Text(text = "Sexo", style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PetSex.entries.forEach { entry ->
            FilterChip(
                selected = selected == entry,
                onClick = { onSelect(entry) },
                label = { Text(entry.toDisplayName()) }
            )
        }
    }
}

@Composable
private fun SizeChipRow(selected: PetSize, onSelect: (PetSize) -> Unit) {
    Text(text = "Tamaño", style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PetSize.entries.forEach { entry ->
            FilterChip(
                selected = selected == entry,
                onClick = { onSelect(entry) },
                label = { Text(entry.toDisplayName()) }
            )
        }
    }
}

@Composable
fun PublishFosterScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) {
    var location by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf(1) }
    var notes by remember { mutableStateOf("") }
    var contactInfo by remember { mutableStateOf("") }
    var species by remember { mutableStateOf(setOf(PetSpecies.DOG, PetSpecies.CAT)) }
    val formState by viewModel.formState.collectAsState()

    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetFormState()
            onPublishSuccess()
        }
    }

    PublishFormScaffold(
        title = "Hogar de tránsito",
        onNavigateBack = onNavigateBack,
        isLoading = formState.isLoading,
        errorMessage = formState.errorMessage,
        onSubmit = {
            viewModel.publishFosterHome(location, capacity, species.toList(), notes, contactInfo)
        }
    ) {
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Zona") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = capacity.toString(),
            onValueChange = { capacity = it.toIntOrNull() ?: 1 },
            label = { Text("Capacidad") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        SpeciesChipRow(
            selected = species.firstOrNull() ?: PetSpecies.DOG,
            onSelect = { selected ->
                species = if (species.contains(selected)) species - selected else species + selected
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notas") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = contactInfo,
            onValueChange = { contactInfo = it },
            label = { Text("Contacto") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun PublishEventScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var contactInfo by remember { mutableStateOf("") }
    val formState by viewModel.formState.collectAsState()

    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetFormState()
            onPublishSuccess()
        }
    }

    PublishFormScaffold(
        title = "Evento de adopción",
        onNavigateBack = onNavigateBack,
        isLoading = formState.isLoading,
        errorMessage = formState.errorMessage,
        onSubmit = {
            viewModel.publishEvent(title, date, location, description, contactInfo)
        }
    ) {
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Fecha") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Zona") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = contactInfo, onValueChange = { contactInfo = it }, label = { Text("Contacto") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun PublishDonationScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var donationType by remember { mutableStateOf(DonationType.MONEY) }
    val formState by viewModel.formState.collectAsState()

    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetFormState()
            onPublishSuccess()
        }
    }

    PublishFormScaffold(
        title = "Campaña solidaria",
        onNavigateBack = onNavigateBack,
        isLoading = formState.isLoading,
        errorMessage = formState.errorMessage,
        onSubmit = {
            viewModel.publishDonationCampaign(
                title,
                description,
                location,
                goal.toDoubleOrNull(),
                donationType
            )
        }
    ) {
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Zona") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = goal, onValueChange = { goal = it }, label = { Text("Meta ($, opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
}

@Composable
fun PublishShelterScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var needs by remember { mutableStateOf("") }
    val formState by viewModel.formState.collectAsState()

    LaunchedEffect(formState.isSuccess) {
        if (formState.isSuccess) {
            viewModel.resetFormState()
            onPublishSuccess()
        }
    }

    PublishFormScaffold(
        title = "Perfil de refugio",
        onNavigateBack = onNavigateBack,
        isLoading = formState.isLoading,
        errorMessage = formState.errorMessage,
        onSubmit = {
            viewModel.publishShelter(name, location, description, phone, email, needs)
        }
    ) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre del refugio") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Zona") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Teléfono") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = needs,
            onValueChange = { needs = it },
            label = { Text("Necesidades (una por línea: ítem|cantidad)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
    }
}
