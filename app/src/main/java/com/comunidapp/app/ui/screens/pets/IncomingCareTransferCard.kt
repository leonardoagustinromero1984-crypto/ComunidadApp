package com.comunidapp.app.ui.screens.pets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.pets.PetCareTransferCopy
import com.comunidapp.app.domain.pets.PetTransfer
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.MutedText

@Composable
fun IncomingCareTransferCard(
    transfer: PetTransfer,
    enabled: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmAccept by remember(transfer.id.value) { mutableStateOf(false) }
    val pet = transfer.petDisplayName.orEmpty().ifBlank { "esta mascota" }
    if (confirmAccept) {
        AlertDialog(
            onDismissRequest = { confirmAccept = false },
            title = { Text(PetCareTransferCopy.acceptConfirmTitle(pet)) },
            text = { Text(PetCareTransferCopy.acceptConfirmBody(pet)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmAccept = false
                        onAccept()
                    }
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAccept = false }) { Text("Cancelar") }
            }
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceCompact),
        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
    ) {
            Text(
                text = PetCareTransferCopy.RECEIVER_TITLE,
                style = LeoCaption,
                color = MutedText,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = transfer.petDisplayName?.trim().orEmpty().ifBlank { "Mascota" },
                style = LeoCardTitle,
                color = BrandText,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = PetCareTransferCopy.incomingRequest(
                    sourceName = transfer.sourceDisplayName.orEmpty(),
                    petName = pet
                ),
                style = LeoCaption,
                color = BrandText
            )
            Text(
                text = PetCareTransferCopy.mediaShareLine(transfer.sharePersonalMedia),
                style = LeoCaption,
                color = MutedText
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LeoOutlinedButton(
                    text = "Rechazar",
                    onClick = onReject,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
                LeoPrimaryButton(
                    text = "Aceptar",
                    onClick = { confirmAccept = true },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    fillMaxWidth = false
                )
            }
        LeoHairline()
    }
}
