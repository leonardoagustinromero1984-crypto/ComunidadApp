package com.comunidapp.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.domain.pets.PetPhotoResolver

@Composable
fun ResolvedPetImage(
    pet: Pet,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop
) {
    val url by produceState(
        initialValue = null as String?,
        pet.id,
        pet.avatarFileAssetId,
        pet.photoUrl
    ) {
        value = PetPhotoResolver.displayUrl(pet)
    }
    PetImage(
        imageUrl = url,
        modifier = modifier,
        cornerRadius = cornerRadius,
        contentDescription = contentDescription ?: pet.name,
        contentScale = contentScale
    )
}
