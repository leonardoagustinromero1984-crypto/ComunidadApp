package com.comunidapp.app.ui.screens.admin

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AdminMfaInvalidCodeException
import com.comunidapp.app.data.repository.AdminTotpEnrollment
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoSecondaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.util.QrCodeBitmapGenerator
import com.comunidapp.app.ui.util.SecureWindow
import com.comunidapp.app.viewmodel.SessionViewModel
import kotlinx.coroutines.launch

@Composable
fun AdminMfaEnrollmentScreen(
    sessionViewModel: SessionViewModel = viewModel()
) {
    SecureWindow()
    var enrollment by remember { mutableStateOf<AdminTotpEnrollment?>(null) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val mfa = DataProvider.adminMfaRepository

    LaunchedEffect(Unit) {
        mfa.enrollTotp()
            .onSuccess {
                enrollment = it
                loading = false
            }
            .onFailure {
                error = "No se pudo iniciar la verificación en dos pasos."
                loading = false
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            scope.launch {
                if (sessionViewModel.sessionState.value !=
                    com.comunidapp.app.viewmodel.SessionState.AdminSession
                ) {
                    mfa.discardUnverifiedEnrollment()
                }
            }
        }
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = { LeoTopAppBar(title = "Protegé tu cuenta") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(LeoDimens.SpaceLg)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Para acceder a la administración de LeoVer necesitás verificación en dos pasos.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(LeoDimens.SpaceLg))
            if (loading) {
                Text("Preparando la verificación…", style = MaterialTheme.typography.bodyMedium)
            }
            enrollment?.let { enrolled ->
                val qr = remember(enrolled.otpauthUri) {
                    QrCodeBitmapGenerator.encode(enrolled.otpauthUri, sizePx = 512)
                }
                if (qr != null) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = "Código QR de verificación en dos pasos",
                        modifier = Modifier.size(220.dp)
                    )
                }
                Spacer(Modifier.height(LeoDimens.SpaceMd))
                Text(
                    "Si no podés escanear el código, ingresá esta clave en tu app de autenticación:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(LeoDimens.SpaceSm))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(LeoDimens.RadiusCard),
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp
                ) {
                    Text(
                        enrolled.secret,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(LeoDimens.SpaceMd),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(LeoDimens.SpaceSm))
                LeoSecondaryButton(
                    text = "Copiar clave",
                    onClick = {
                        copyTotpSecretToClipboard(context, enrolled.secret)
                        copied = true
                    }
                )
                if (copied) {
                    Spacer(Modifier.height(LeoDimens.SpaceSm))
                    Text(
                        "Clave copiada",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(LeoDimens.SpaceLg))
                OutlinedTextField(
                    value = code,
                    onValueChange = { incoming ->
                        code = incoming.filter { it.isDigit() }.take(6)
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Código de 6 dígitos") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
            }
            error?.let {
                Spacer(Modifier.height(LeoDimens.SpaceSm))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(LeoDimens.SpaceSection))
            LeoPrimaryButton(
                text = if (busy) "Verificando…" else "Continuar",
                onClick = {
                    if (busy || enrollment == null) return@LeoPrimaryButton
                    scope.launch {
                        busy = true
                        sessionViewModel.verifyAdminMfaCode(code)
                            .onFailure { failure ->
                                error = if (failure is AdminMfaInvalidCodeException) {
                                    "Código incorrecto. Intentá nuevamente."
                                } else {
                                    "No se pudo verificar el código. Intentá nuevamente."
                                }
                                busy = false
                            }
                    }
                },
                enabled = !busy && !loading && code.length == 6
            )
        }
    }
}

private fun copyTotpSecretToClipboard(context: Context, secret: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return
    val clip = ClipData.newPlainText("totp", secret)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}
