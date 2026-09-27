package com.comunidapp.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.model.User
import com.comunidapp.app.domain.user.ProfileAvatarResolver

@Composable
fun ResolvedProfileAvatar(
    user: User,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentDescription: String? = null
) {
    val url by produceState(
        initialValue = ProfileAvatarResolver.httpOrLocalUrl(user),
        user.id,
        user.profileImageUrl,
        user.avatarPath
    ) {
        value = ProfileAvatarResolver.displayUrl(user)
    }
    PetImage(
        imageUrl = url,
        modifier = modifier,
        cornerRadius = cornerRadius,
        contentDescription = contentDescription ?: user.name,
        contentScale = ContentScale.Crop,
        decodeSize = 52.dp
    )
}
