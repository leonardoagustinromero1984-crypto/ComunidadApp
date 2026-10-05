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
import com.comunidapp.app.data.local.AdoptionApplicantProfileStore
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.adoption.AdopterLifeStagePref
import com.comunidapp.app.domain.adoption.AdopterProfile
import com.comunidapp.app.domain.adoption.AdopterSizePref
import com.comunidapp.app.domain.adoption.AdopterSpeciesPref
import com.comunidapp.app.domain.adoption.AdoptionGeneralProfileCodec
import com.comunidapp.app.domain.adoption.AdopterProfileCompleteness
import com.comunidapp.app.domain.adoption.EscapeProtectionCopy
import com.comunidapp.app.domain.adoption.ExperienceBand
import com.comunidapp.app.domain.adoption.HoursAloneEstimate
import com.comunidapp.app.domain.adoption.HousingKind
import com.comunidapp.app.domain.adoption.HousingTenure
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.user.SessionGeneration
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject

@Composable
fun AdoptionGeneralProfileScreen(onNavigateBack: () -> Unit) {
    var housingKind by remember { mutableStateOf<HousingKind?>(null) }
    var housingTenure by remember { mutableStateOf<HousingTenure?>(null) }
    var landlordAllowsPets by remember { mutableStateOf<Boolean?>(null) }
    var hasOutdoorSpace by remember { mutableStateOf<Boolean?>(null) }
    var hasSecureEnclosure by remember { mutableStateOf<Boolean?>(null) }
    var escapeProtection by remember { mutableStateOf<Boolean?>(null) }
    var adults by remember { mutableStateOf("") }
    var children by remember { mutableStateOf("") }
    var householdAgrees by remember { mutableStateOf<Boolean?>(null) }
    var hasDogs by remember { mutableStateOf<Boolean?>(null) }
    var hasCats by remember { mutableStateOf<Boolean?>(null) }
    var hasOtherAnimals by remember { mutableStateOf<Boolean?>(null) }
    var experience by remember { mutableStateOf<ExperienceBand?>(null) }
    var hoursAlone by remember { mutableStateOf<HoursAloneEstimate?>(null) }
    var canVet by remember { mutableStateOf<Boolean?>(null) }
    var canMedicate by remember { mutableStateOf<Boolean?>(null) }
    var acceptsSpecialNeeds by remember { mutableStateOf<Boolean?>(null) }
    var species by remember { mutableStateOf<AdopterSpeciesPref?>(null) }
    var size by remember { mutableStateOf<AdopterSizePref?>(null) }
    var lifeStage by remember { mutableStateOf<AdopterLifeStagePref?>(null) }
    var motivation by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var housingNotes by remember { mutableStateOf<String?>(null) }
    var legacyExperience by remember { mutableStateOf<String?>(null) }
    var legacyHours by remember { mutableStateOf<String?>(null) }
    var legacyOtherPets by remember { mutableStateOf<String?>(null) }
    var existingRaw by remember { mutableStateOf("{}") }
    var loaded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun currentProfile() = AdopterProfile(
        housingKind = housingKind,
        housingTenure = housingTenure,
        landlordAllowsPets = landlordAllowsPets,
        hasOutdoorSpace = hasOutdoorSpace,
        hasSecureEnclosure = hasSecureEnclosure,
        escapeProtection = escapeProtection,
        adultsCount = adults.trim().toIntOrNull(),
        childrenCount = children.trim().toIntOrNull(),
        householdAgrees = householdAgrees,
        hasDogs = hasDogs,
        hasCats = hasCats,
        hasOtherAnimals = hasOtherAnimals,
        experienceBand = experience,
        hoursAlone = hoursAlone,
        canVetFollowup = canVet,
        canMedicate = canMedicate,
        acceptsSpecialNeeds = acceptsSpecialNeeds,
        speciesPref = species,
        sizePref = size,
        lifeStagePref = lifeStage,
        motivation = motivation,
        notes = notes,
        housingNotes = housingNotes,
        legacyExperience = legacyExperience,
        legacyHoursAlone = legacyHours,
        legacyOtherPets = legacyOtherPets
    )

    LaunchedEffect(Unit) {
        val raw = runCatching {
            supabase.postgrest.rpc(
                CanonicalBackend.RPC_LIST_ADOPTION_GENERAL_PROFILE,
                buildJsonObject { }
            ).data
        }.getOrNull() ?: return@LaunchedEffect
        existingRaw = raw
        val profile = AdoptionGeneralProfileCodec.decodeProfile(raw)
        if (!loaded) {
            housingKind = profile.housingKind
            housingTenure = profile.housingTenure
            landlordAllowsPets = profile.landlordAllowsPets
            hasOutdoorSpace = profile.hasOutdoorSpace
            hasSecureEnclosure = profile.hasSecureEnclosure
            escapeProtection = profile.escapeProtection
            adults = profile.adultsCount?.toString().orEmpty()
            children = profile.childrenCount?.toString().orEmpty()
            householdAgrees = profile.householdAgrees
            hasDogs = profile.hasDogs
            hasCats = profile.hasCats
            hasOtherAnimals = profile.hasOtherAnimals
            experience = profile.experienceBand
            hoursAlone = profile.hoursAlone
            canVet = profile.canVetFollowup
            canMedicate = profile.canMedicate
            acceptsSpecialNeeds = profile.acceptsSpecialNeeds
            species = profile.speciesPref
            size = profile.sizePref
            lifeStage = profile.lifeStagePref
            motivation = profile.motivation.orEmpty()
            notes = profile.notes.orEmpty()
            housingNotes = profile.housingNotes
            legacyExperience = profile.legacyExperience
            legacyHours = profile.legacyHoursAlone
            legacyOtherPets = profile.legacyOtherPets
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
            Text(
                "Contanos un poco sobre tu hogar y tus preferencias para ayudarte a encontrar mascotas que puedan adaptarse a vos. Podés modificarlo cuando quieras.",
                style = LeoCaption
            )
            Text("Vivienda", style = LeoCaption)
            HousingKindChips(housingKind) { housingKind = it }
            HousingTenureChips(housingTenure) { housingTenure = it }
            if (housingTenure == HousingTenure.RENT) {
                AdoptionTriChips("¿Permiten mascotas en el alquiler?", landlordAllowsPets, "No sé") {
                    landlordAllowsPets = it
                }
            }
            AdoptionTriChips("¿Hay patio o espacio exterior?", hasOutdoorSpace, "No sé") { hasOutdoorSpace = it }
            AdoptionTriChips("¿El espacio está cerrado de forma segura?", hasSecureEnclosure, "No sé") {
                hasSecureEnclosure = it
            }
            AdoptionTriChips(EscapeProtectionCopy.PROFILE_QUESTION, escapeProtection, "No sé") {
                escapeProtection = it
            }
            Text(EscapeProtectionCopy.PROFILE_HINT, style = LeoCaption)
            housingNotes?.takeIf { it.isNotBlank() }?.let {
                Text("Información anterior sobre la vivienda: $it", style = LeoCaption)
            }
            Text("Hogar", style = LeoCaption)
            OutlinedTextField(
                adults,
                { adults = it.filter { ch -> ch.isDigit() }.take(2) },
                label = { Text("Adultos (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                children,
                { children = it.filter { ch -> ch.isDigit() }.take(2) },
                label = { Text("Niños (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            AdoptionTriChips("¿Las personas del hogar están de acuerdo?", householdAgrees, "No sé") {
                householdAgrees = it
            }
            Text("Otros animales", style = LeoCaption)
            AdoptionTriChips("¿Hay perros?", hasDogs, "No sé") { hasDogs = it }
            AdoptionTriChips("¿Hay gatos?", hasCats, "No sé") { hasCats = it }
            AdoptionTriChips("¿Hay otros animales?", hasOtherAnimals, "No sé") { hasOtherAnimals = it }
            legacyOtherPets?.takeIf { it.isNotBlank() }?.let {
                Text("Información anterior: $it", style = LeoCaption)
            }
            Text("Experiencia", style = LeoCaption)
            ExperienceBandChips(experience) { experience = it }
            legacyExperience?.takeIf { it.isNotBlank() }?.let {
                Text("Información anterior: $it", style = LeoCaption)
            }
            Text("Rutina", style = LeoCaption)
            HoursAloneChips("Horas aproximadas que el animal quedaría solo", hoursAlone, "No sé") {
                hoursAlone = it
            }
            legacyHours?.takeIf { it.isNotBlank() }?.let {
                Text("Información anterior: $it", style = LeoCaption)
            }
            Text("Cuidados", style = LeoCaption)
            AdoptionTriChips("¿Podés hacer controles veterinarios?", canVet, "No sé") { canVet = it }
            AdoptionTriChips("¿Podés medicar o hacer cuidados especiales?", canMedicate, "No sé") {
                canMedicate = it
            }
            AdoptionTriChips("¿Aceptarías necesidades especiales?", acceptsSpecialNeeds, "No sé") {
                acceptsSpecialNeeds = it
            }
            Text("Preferencias", style = LeoCaption)
            SpeciesPrefChips(species) { species = it }
            SizePrefChips(size) { size = it }
            LifeStagePrefChips(lifeStage) { lifeStage = it }
            Text("Información adicional", style = LeoCaption)
            OutlinedTextField(
                motivation,
                { motivation = it },
                label = { Text("Motivación (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                notes,
                { notes = it },
                label = { Text("Información adicional") },
                modifier = Modifier.fillMaxWidth()
            )
            message?.let { Text(it, style = LeoCaption) }
            LeoPrimaryButton(
                text = "Guardar perfil",
                onClick = {
                    if (!loaded) return@LeoPrimaryButton
                    val profile = currentProfile()
                    val token = SessionGeneration.current()
                    scope.launch {
                        val saved = runCatching {
                            supabase.postgrest.rpc(
                                CanonicalBackend.RPC_UPSERT_ADOPTION_GENERAL_PROFILE,
                                AdoptionGeneralProfileCodec.upsertProfile(existingRaw, profile)
                            )
                        }
                        SessionGeneration.publishIfCurrent(token) {
                            saved.onSuccess {
                                val userId = runCatching { supabase.auth.currentUserOrNull()?.id }.getOrNull()
                                if (!userId.isNullOrBlank()) {
                                    AdoptionApplicantProfileStore.saveStructured(userId, profile)
                                }
                                message = AdopterProfileCompleteness.saveFeedback(profile)
                            }.onFailure { message = "No se pudo guardar." }
                        }
                    }
                }
            )
        }
    }
}
