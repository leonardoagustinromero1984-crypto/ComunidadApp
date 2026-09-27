package com.comunidapp.app.ui.components.v2

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.UrgentContainer
import com.comunidapp.app.ui.theme.UrgentRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pedí que el campo enfocado quede visible cuando aparece el teclado.
 * El padding IME global vive en el NavHost raíz (edge-to-edge + adjustResize).
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.v2KeepVisibleOnFocus(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    bringIntoViewRequester(requester).onFocusEvent { state ->
        if (state.isFocused) {
            scope.launch {
                delay(280)
                requester.bringIntoView()
            }
        }
    }
}

/**
 * Preview de foto en formularios.
 * Compact = tile acotado con Coil sized (no decode full-res).
 * El upload sigue enviando el archivo original.
 */
@Composable
fun V2FormImagePreview(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Vista previa",
    compact: Boolean = false,
    compactSize: Dp = 112.dp
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val boxModifier = if (compact) {
        modifier.size(compactSize)
    } else {
        modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
    }
    Box(
        modifier = boxModifier
            .clip(RoundedCornerShape(LeoDimens.RadiusCardFeature))
            .background(BrandWhite),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNullOrBlank()) {
            Text(
                text = "Sin foto",
                style = LeoCaption,
                color = BrandTextSecondary
            )
        } else {
            val decodePx = if (compact) {
                with(density) { compactSize.roundToPx().coerceAtLeast(1) * 2 }
            } else {
                0
            }
            val request = remember(imageUrl, compact, decodePx) {
                val builder = ImageRequest.Builder(context).data(imageUrl).crossfade(true)
                if (compact && decodePx > 0) {
                    builder.size(Size(decodePx, decodePx))
                }
                builder.build()
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (compact) 0.dp else LeoDimens.SpaceSm),
                contentScale = if (compact) ContentScale.Crop else ContentScale.Fit
            )
        }
    }
}

@Composable
fun V2FormErrorBanner(
    title: String,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onCopyDiagnostic: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LeoDimens.RadiusCard))
            .background(UrgentContainer)
            .padding(LeoDimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = UrgentRed,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = LeoCardTitle, color = BrandText)
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(text = message, style = LeoCaption, color = BrandTextSecondary)
            }
            if (onRetry != null || onDismiss != null || onCopyDiagnostic != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onRetry != null) {
                        TextButton(onClick = onRetry) { Text("Reintentar") }
                    }
                    if (onDismiss != null) {
                        TextButton(onClick = onDismiss) { Text("Cerrar") }
                    }
                    if (onCopyDiagnostic != null) {
                        TextButton(onClick = onCopyDiagnostic) { Text("Copiar diagnóstico") }
                    }
                }
            }
        }
    }
}

@Composable
fun V2FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    required: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    imeAction: ImeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    onImeAction: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val visibleLabel = com.comunidapp.app.ui.components.leo.LeoRequiredField.label(label, required)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(visibleLabel) },
        isError = !errorMessage.isNullOrBlank(),
        supportingText = errorMessage?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        modifier = modifier
            .fillMaxWidth()
            .v2KeepVisibleOnFocus(),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = {
                onImeAction?.invoke() ?: focusManager.moveFocus(FocusDirection.Down)
            },
            onDone = {
                onImeAction?.invoke() ?: run {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }
            }
        )
    )
}

@Composable
fun V2FormScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    isLoading: Boolean,
    onSubmit: () -> Unit,
    submitLabel: String = "Publicar",
    errorTitle: String? = null,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    onDismissError: (() -> Unit)? = null,
    onCopyDiagnostic: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(title = title, showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(horizontal = LeoDimens.SpaceMd)
                .padding(bottom = LeoDimens.SpaceLg),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            content()
            if (!errorTitle.isNullOrBlank() || !errorMessage.isNullOrBlank()) {
                V2FormErrorBanner(
                    title = errorTitle ?: "No pudimos completar la acción",
                    message = errorMessage,
                    onRetry = onRetry,
                    onDismiss = onDismissError,
                    onCopyDiagnostic = onCopyDiagnostic
                )
            }
            Spacer(Modifier.height(LeoDimens.SpaceSm))
            LeoPrimaryButton(
                text = if (isLoading) "Publicando…" else submitLabel,
                onClick = onSubmit,
                enabled = !isLoading
            )
        }
    }
}

fun splitUserFacingFormError(raw: String?): Pair<String, String?>? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    return when {
        lines.size >= 2 -> lines.first() to lines.drop(1).joinToString(" ")
        else -> text to null
    }
}
