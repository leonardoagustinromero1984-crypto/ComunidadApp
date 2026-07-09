package com.comunidapp.app.ui.screens.profile



import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.LocationOn

import androidx.compose.material.icons.filled.Lock

import androidx.compose.material3.Button

import androidx.compose.material3.Icon

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.OutlinedButton

import androidx.compose.material3.Scaffold

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.runtime.collectAsState

import androidx.compose.runtime.getValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp

import androidx.lifecycle.viewmodel.compose.viewModel

import com.comunidapp.app.data.model.ProfileRelation

import com.comunidapp.app.ui.components.ComunidappTopBar

import com.comunidapp.app.ui.components.FeedPostCard

import com.comunidapp.app.ui.components.LoadingState

import com.comunidapp.app.ui.components.PetCard

import com.comunidapp.app.ui.components.PetImage

import com.comunidapp.app.ui.components.toDisplayName

import com.comunidapp.app.viewmodel.UserPublicProfileViewModel



@Composable

fun UserPublicProfileScreen(

    userId: String,

    onNavigateBack: () -> Unit,

    onPetClick: (String) -> Unit = {},

    onMessageClick: (String, String) -> Unit = { _, _ -> },
    viewModel: UserPublicProfileViewModel = viewModel()
) {

    val uiState by viewModel.uiState.collectAsState()



    Scaffold(

        topBar = {

            ComunidappTopBar(

                title = uiState.user?.name ?: "Perfil",

                showBackButton = true,

                onBackClick = onNavigateBack

            )

        }

    ) { padding ->

        when {

            uiState.isLoading -> LoadingState(Modifier.padding(padding))

            uiState.errorMessage != null -> {

                Box(

                    modifier = Modifier

                        .fillMaxSize()

                        .padding(padding)

                        .padding(24.dp),

                    contentAlignment = Alignment.Center

                ) {

                    Text(

                        text = uiState.errorMessage!!,

                        style = MaterialTheme.typography.bodyLarge,

                        color = MaterialTheme.colorScheme.error,

                        textAlign = TextAlign.Center

                    )

                }

            }

            uiState.user != null -> {
                val user = uiState.user!!
                LazyColumn(

                    modifier = Modifier.fillMaxSize(),

                    contentPadding = PaddingValues(

                        start = 16.dp,

                        end = 16.dp,

                        top = padding.calculateTopPadding() + 8.dp,

                        bottom = padding.calculateBottomPadding() + 8.dp

                    ),

                    verticalArrangement = Arrangement.spacedBy(12.dp)

                ) {

                    item {

                        Column(

                            modifier = Modifier.fillMaxWidth(),

                            horizontalAlignment = Alignment.CenterHorizontally

                        ) {

                            Box(

                                modifier = Modifier

                                    .size(96.dp)

                                    .clip(CircleShape)

                            ) {

                                PetImage(

                                    imageUrl = user.profileImageUrl,

                                    modifier = Modifier.fillMaxSize(),

                                    cornerRadius = 48.dp,

                                    contentDescription = user.name

                                )

                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(

                                text = user.name,

                                style = MaterialTheme.typography.headlineMedium,

                                fontWeight = FontWeight.Bold

                            )

                            Text(

                                text = user.accountType.toDisplayName(),

                                style = MaterialTheme.typography.labelLarge,

                                color = MaterialTheme.colorScheme.primary,

                                modifier = Modifier

                                    .padding(top = 4.dp)

                                    .background(

                                        MaterialTheme.colorScheme.primaryContainer,

                                        RoundedCornerShape(8.dp)

                                    )

                                    .padding(horizontal = 10.dp, vertical = 4.dp)

                            )

                            if (!uiState.canViewFullProfile) {

                                Row(

                                    verticalAlignment = Alignment.CenterVertically,

                                    modifier = Modifier.padding(top = 12.dp)

                                ) {

                                    Icon(

                                        Icons.Default.Lock,

                                        contentDescription = null,

                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,

                                        modifier = Modifier.size(18.dp)

                                    )

                                    Text(

                                        text = "Perfil privado",

                                        style = MaterialTheme.typography.bodyMedium,

                                        color = MaterialTheme.colorScheme.onSurfaceVariant,

                                        modifier = Modifier.padding(start = 6.dp)

                                    )

                                }

                                Text(

                                    text = privateProfileHint(uiState.relation),

                                    style = MaterialTheme.typography.bodySmall,

                                    color = MaterialTheme.colorScheme.onSurfaceVariant,

                                    modifier = Modifier.padding(top = 8.dp),

                                    textAlign = TextAlign.Center

                                )

                            } else {

                                user.bio?.let {

                                    Text(

                                        text = it,

                                        style = MaterialTheme.typography.bodyMedium,

                                        color = MaterialTheme.colorScheme.onSurfaceVariant,

                                        modifier = Modifier.padding(top = 8.dp),

                                        textAlign = TextAlign.Center

                                    )

                                }

                                user.locationText?.let { location ->

                                    Row(

                                        verticalAlignment = Alignment.CenterVertically,

                                        modifier = Modifier.padding(top = 4.dp)

                                    ) {

                                        Icon(

                                            Icons.Default.LocationOn,

                                            contentDescription = null,

                                            tint = MaterialTheme.colorScheme.secondary

                                        )

                                        Text(

                                            text = location,

                                            style = MaterialTheme.typography.bodySmall,

                                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                                            modifier = Modifier.padding(start = 4.dp)

                                        )

                                    }

                                }

                            }

                        }

                    }



                    item {

                        FriendActionSection(

                            relation = uiState.relation,

                            actionInProgress = uiState.actionInProgress,

                            actionMessage = uiState.actionMessage,

                            onSendRequest = viewModel::sendFriendRequest,

                            onAccept = viewModel::acceptFriendRequest,

                            onReject = viewModel::rejectFriendRequest,

                            onCancel = viewModel::cancelFriendRequest,

                            onMessage = { onMessageClick(user.id, user.name) }

                        )

                    }



                    if (uiState.canViewFullProfile && uiState.pets.isNotEmpty()) {

                        item {

                            Text(

                                text = "Mascotas (${uiState.pets.size})",

                                style = MaterialTheme.typography.titleMedium,

                                fontWeight = FontWeight.SemiBold,

                                modifier = Modifier.padding(top = 8.dp)

                            )

                        }

                        items(uiState.pets, key = { it.id }) { pet ->

                            PetCard(pet = pet, onClick = { onPetClick(pet.id) })

                        }

                    }



                    if (uiState.canViewFullProfile && uiState.posts.isNotEmpty()) {

                        item {

                            Text(

                                text = "Publicaciones",

                                style = MaterialTheme.typography.titleMedium,

                                fontWeight = FontWeight.SemiBold,

                                modifier = Modifier.padding(top = 8.dp)

                            )

                        }

                        items(uiState.posts, key = { it.id }) { post ->

                            FeedPostCard(post = post)

                        }

                    }



                    if (uiState.canViewFullProfile &&

                        uiState.pets.isEmpty() &&

                        uiState.posts.isEmpty() &&

                        uiState.relation != ProfileRelation.SELF

                    ) {

                        item {

                            Text(

                                text = "Este usuario aún no tiene mascotas ni publicaciones.",

                                style = MaterialTheme.typography.bodyMedium,

                                color = MaterialTheme.colorScheme.onSurfaceVariant,

                                modifier = Modifier.padding(top = 16.dp),

                                textAlign = TextAlign.Center

                            )

                        }

                    }

                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No se pudo cargar el perfil",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}



@Composable

private fun FriendActionSection(

    relation: ProfileRelation,

    actionInProgress: Boolean,

    actionMessage: String?,

    onSendRequest: () -> Unit,

    onAccept: () -> Unit,

    onReject: () -> Unit,

    onCancel: () -> Unit,

    onMessage: () -> Unit

) {

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        when (relation) {

            ProfileRelation.LOCKED -> {

                Button(

                    onClick = onSendRequest,

                    enabled = !actionInProgress,

                    modifier = Modifier.fillMaxWidth()

                ) {

                    Text("Enviar solicitud de amistad")

                }

            }

            ProfileRelation.PENDING_OUTGOING -> {

                OutlinedButton(

                    onClick = onCancel,

                    enabled = !actionInProgress,

                    modifier = Modifier.fillMaxWidth()

                ) {

                    Text("Solicitud enviada — Cancelar")

                }

            }

            ProfileRelation.PENDING_INCOMING -> {

                Button(

                    onClick = onAccept,

                    enabled = !actionInProgress,

                    modifier = Modifier.fillMaxWidth()

                ) {

                    Text("Aceptar solicitud")

                }

                OutlinedButton(

                    onClick = onReject,

                    enabled = !actionInProgress,

                    modifier = Modifier.fillMaxWidth()

                ) {

                    Text("Rechazar")

                }

            }

            ProfileRelation.FRIENDS,

            ProfileRelation.PUBLIC_PROFILE -> {

                Button(

                    onClick = onMessage,

                    modifier = Modifier.fillMaxWidth()

                ) {

                    Text("Enviar mensaje")

                }

            }

            ProfileRelation.SELF -> Unit

        }

        actionMessage?.let {

            Text(

                text = it,

                style = MaterialTheme.typography.bodySmall,

                color = MaterialTheme.colorScheme.primary,

                modifier = Modifier.fillMaxWidth(),

                textAlign = TextAlign.Center

            )

        }

    }

}



private fun privateProfileHint(relation: ProfileRelation): String = when (relation) {

    ProfileRelation.LOCKED ->

        "Enviá una solicitud de amistad para ver sus mascotas, publicaciones e historias."

    ProfileRelation.PENDING_OUTGOING ->

        "Tu solicitud está pendiente. Cuando la acepten podrás ver su contenido."

    ProfileRelation.PENDING_INCOMING ->

        "Te enviaron una solicitud de amistad."

    else -> ""

}


