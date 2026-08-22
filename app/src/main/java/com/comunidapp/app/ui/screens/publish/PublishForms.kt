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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.DonationType
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.ui.components.v2.V2FormErrorBanner
import com.comunidapp.app.ui.components.v2.V2FormImagePreview
import com.comunidapp.app.ui.components.v2.V2FormScaffold
import com.comunidapp.app.ui.components.v2.V2FormTextField
import com.comunidapp.app.ui.components.v2.V2LocationStringPicker
import com.comunidapp.app.ui.components.v2.splitUserFacingFormError
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
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
fun PublishReelScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    viewModel: PublishViewModel = viewModel()
) {
    com.comunidapp.app.ui.screens.social.ReelComposerScreen(
        onNavigateBack = onNavigateBack,
        onPublishSuccess = onPublishSuccess,
        viewModel = viewModel
    )
}

/**
 * Flujo social directo de Historia.
 * Cámara real o galería (Photo Picker). Nunca abre galería al tocar Cámara.
 */
@Composable
fun PublishStoryScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    origin: String = "PUBLISH",
    autoOpenPicker: Boolean = false,
    viewModel: PublishViewModel = viewModel()
) {
    com.comunidapp.app.ui.screens.social.StoryComposerScreen(
        onNavigateBack = onNavigateBack,
        onPublishSuccess = onPublishSuccess,
        origin = origin,
        autoOpenPicker = autoOpenPicker,
        viewModel = viewModel
    )
}
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
        V2FormTextField(
            value = title,
            onValueChange = { title = it },
            label = "Título",
            imeAction = ImeAction.Next
        )
        V2FormTextField(
            value = content,
            onValueChange = { content = it },
            label = "Contenido",
            singleLine = false,
            minLines = 4,
            maxLines = 8,
            imeAction = ImeAction.Default
        )
        V2LocationStringPicker(
            value = location,
            onValueChange = { location = it }
        )
        imageUri?.let { uri ->
            V2FormImagePreview(
                imageUrl = uri.toString(),
                contentDescription = "Imagen de la publicación"
            )
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
            V2FormImagePreview(
                imageUrl = uri.toString(),
                contentDescription = "Foto del animal"
            )
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
        V2FormTextField(
            value = name,
            onValueChange = { name = it },
            label = "Nombre del animal",
            imeAction = ImeAction.Next
        )
        SpeciesChipRow(selected = species, onSelect = { species = it })
        SexChipRow(selected = sex, onSelect = { sex = it })
        SizeChipRow(selected = size, onSelect = { size = it })
        V2FormTextField(
            value = ageYears.toString(),
            onValueChange = { ageYears = it.toIntOrNull() ?: 0 },
            label = "Edad (años)",
            imeAction = ImeAction.Next,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        )
        V2LocationStringPicker(
            value = location,
            onValueChange = { location = it }
        )
        V2FormTextField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            imeAction = ImeAction.Default
        )
    }
}

@Composable
fun PublishLostFoundScreen(
    onNavigateBack: () -> Unit,
    onPublishSuccess: () -> Unit,
    initialType: LostFoundType = LostFoundType.LOST,
    prefillPetId: String? = null,
    viewModel: PublishViewModel = viewModel()
) {
    var type by remember { mutableStateOf(initialType) }
    var petName by remember { mutableStateOf("") }
    var species by remember { mutableStateOf(PetSpecies.DOG) }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var contactInfo by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var boundPetId by remember { mutableStateOf(prefillPetId) }
    var existingPhotoUrl by remember { mutableStateOf<String?>(null) }
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
    LaunchedEffect(prefillPetId) {
        val id = prefillPetId?.trim().orEmpty()
        if (id.isBlank()) return@LaunchedEffect
        val pet = DataProvider.petRepository.fetchPetById(id) ?: DataProvider.petRepository.getPetById(id)
        if (pet != null) {
            boundPetId = pet.id
            petName = pet.name
            species = pet.species
            existingPhotoUrl = pet.photoUrl
            val home = pet.locationText?.trim().orEmpty()
            if (home.isNotBlank() && location.isBlank()) {
                location = home
            }
            if (description.isBlank()) {
                description = buildString {
                    append("Se perdió ${pet.name}.")
                    append(" Sexo: ${pet.sex.toDisplayName()}.")
                    pet.breed?.takeIf { it.isNotBlank() }?.let { append(" Raza: $it.") }
                    pet.color?.takeIf { it.isNotBlank() }?.let { append(" Color: $it.") }
                    pet.description?.takeIf { it.isNotBlank() }?.let { append(" $it") }
                }.trim()
            }
        }
    }

    PublishFormScaffold(
        title = if (type == LostFoundType.LOST) "Animal perdido" else "Animal encontrado",
        onNavigateBack = onNavigateBack,
        isLoading = formState.isLoading,
        errorMessage = formState.errorMessage,
        diagnosticText = formState.diagnosticText,
        onSubmit = {
            viewModel.publishLostFound(
                type,
                petName,
                species,
                location,
                description,
                contactInfo,
                imageUri,
                petId = boundPetId,
                hasExistingPhoto = !existingPhotoUrl.isNullOrBlank()
            )
        }
    ) {
        com.comunidapp.app.ui.components.ContextualFirstVisitHelp(
            helpId = com.comunidapp.app.domain.onboarding.ContextualHelpId.ALERTS,
            message = com.comunidapp.app.ui.components.ContextualHelpMessages.ALERTS
        )
        Text(
            text = "La foto se publica completa. Revisala antes de enviar.",
            style = LeoCaption
        )
        V2FormImagePreview(
            imageUrl = imageUri?.toString() ?: existingPhotoUrl,
            contentDescription = "Vista previa de la foto"
        )
        LeoOutlinedButton(
            text = if (imageUri == null) "Agregar foto" else "Cambiar foto",
            onClick = {
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
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
        SpeciesChipRow(selected = species, onSelect = { species = it })
        V2FormTextField(
            value = petName,
            onValueChange = { petName = it },
            label = "Nombre (opcional)",
            imeAction = ImeAction.Next
        )
        V2LocationStringPicker(
            value = location,
            onValueChange = { location = it }
        )
        V2FormTextField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            imeAction = ImeAction.Default
        )
        V2FormTextField(
            value = contactInfo,
            onValueChange = { contactInfo = it },
            label = "Contacto",
            imeAction = ImeAction.Done,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
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
    diagnosticText: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val split = splitUserFacingFormError(errorMessage)
    val retryable = errorMessage?.contains("No pudimos publicar", ignoreCase = true) == true
    val clipboard = LocalClipboardManager.current
    val copyDiagnostic = diagnosticText?.takeIf { it.isNotBlank() }?.let { text ->
        {
            clipboard.setText(AnnotatedString(text))
        }
    }
    V2FormScaffold(
        title = title,
        onNavigateBack = onNavigateBack,
        isLoading = isLoading,
        onSubmit = onSubmit,
        errorTitle = split?.first,
        errorMessage = split?.second,
        onRetry = if (retryable) onSubmit else null,
        onCopyDiagnostic = copyDiagnostic,
        content = content
    )
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
        V2LocationStringPicker(
            value = location,
            onValueChange = { location = it }
        )
        V2FormTextField(
            value = capacity.toString(),
            onValueChange = { capacity = it.toIntOrNull() ?: 1 },
            label = "Capacidad",
            imeAction = ImeAction.Next,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        )
        SpeciesChipRow(
            selected = species.firstOrNull() ?: PetSpecies.DOG,
            onSelect = { selected ->
                species = if (species.contains(selected)) species - selected else species + selected
            }
        )
        V2FormTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "Notas",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            imeAction = ImeAction.Default
        )
        V2FormTextField(
            value = contactInfo,
            onValueChange = { contactInfo = it },
            label = "Contacto",
            imeAction = ImeAction.Done
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
        V2FormTextField(value = title, onValueChange = { title = it }, label = "Título")
        V2FormTextField(value = date, onValueChange = { date = it }, label = "Fecha")
        V2LocationStringPicker(value = location, onValueChange = { location = it })
        V2FormTextField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            imeAction = ImeAction.Default
        )
        V2FormTextField(
            value = contactInfo,
            onValueChange = { contactInfo = it },
            label = "Contacto",
            imeAction = ImeAction.Done
        )
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
        V2FormTextField(value = title, onValueChange = { title = it }, label = "Título")
        V2FormTextField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            imeAction = ImeAction.Default
        )
        V2LocationStringPicker(value = location, onValueChange = { location = it })
        V2FormTextField(
            value = goal,
            onValueChange = { goal = it },
            label = "Meta ($, opcional)",
            imeAction = ImeAction.Done,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        )
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
        V2FormTextField(value = name, onValueChange = { name = it }, label = "Nombre del refugio")
        V2LocationStringPicker(value = location, onValueChange = { location = it })
        V2FormTextField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            imeAction = ImeAction.Default
        )
        V2FormTextField(
            value = phone,
            onValueChange = { phone = it },
            label = "Teléfono",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
        )
        V2FormTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
        )
        V2FormTextField(
            value = needs,
            onValueChange = { needs = it },
            label = "Necesidades (una por línea: ítem|cantidad)",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            imeAction = ImeAction.Default
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "StoryMediaPickerEntryPreview")
@Composable
private fun StoryMediaPickerEntryPreview() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tu historia", style = MaterialTheme.typography.titleMedium)
        Text("Imagen o video Â· se publica 24 horas.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Galería") }
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Cámara") }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "StoryImagePreview")
@Composable
private fun StoryImagePreview() {
    Column(Modifier.padding(16.dp)) {
        Text("Vista previa imagen")
        Spacer(Modifier.height(8.dp))
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Tu historia") }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "StoryVideoPreview")
@Composable
private fun StoryVideoPreview() {
    Column(Modifier.padding(16.dp)) {
        Text("Video seleccionado", color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Tu historia") }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "StoryUploadErrorPreview")
@Composable
private fun StoryUploadErrorPreview() {
    Column(Modifier.padding(16.dp)) {
        Text("No pudimos publicar la historia", color = MaterialTheme.colorScheme.error)
        Text("Podés cambiar el medio o reintentar. No quedás atrapado.")
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {}) { Text("Cancelar") }
    }
}
