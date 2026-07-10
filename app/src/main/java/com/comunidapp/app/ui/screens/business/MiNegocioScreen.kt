package com.comunidapp.app.ui.screens.business

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.BookingStatus
import com.comunidapp.app.data.model.PaymentIntent
import com.comunidapp.app.data.model.PaymentIntentStatus
import com.comunidapp.app.data.model.ServiceBooking
import com.comunidapp.app.data.model.ShopProduct
import com.comunidapp.app.domain.RolePermissions
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.viewmodel.MiNegocioViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MiNegocioScreen(
    onNavigateToEditProfile: () -> Unit,
    viewModel: MiNegocioViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val user by viewModel.currentUser.collectAsState()
    val bookings by viewModel.bookings.collectAsState()
    val products by viewModel.products.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val accountType = user?.accountType
    val title = accountType?.let { RolePermissions.businessPanelTitle(it) } ?: "Mi negocio"

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { ComunidappTopBar(title = title) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (user == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Iniciá sesión para ver tu negocio")
            }
            return@Scaffold
        }

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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PetImage(
                            imageUrl = user!!.profileImageUrl ?: uiState.profile?.photoUrl,
                            modifier = Modifier.size(88.dp),
                            contentDescription = user!!.name
                        )
                        Text(
                            text = user!!.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Text(
                            text = user!!.accountType.toDisplayName(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        OutlinedButton(
                            onClick = onNavigateToEditProfile,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Editar perfil", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Ficha en Comunidad",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Publicá tu negocio para aparecer en el directorio y recibir turnos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::updateName,
                    label = { Text("Nombre comercial") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.location,
                    onValueChange = viewModel::updateLocation,
                    label = { Text("Ubicación") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::updateDescription,
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.contactInfo,
                    onValueChange = viewModel::updateContact,
                    label = { Text("Contacto (tel / email / IG)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.scheduleText,
                    onValueChange = viewModel::updateSchedule,
                    label = { Text("Horarios") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.priceFrom,
                    onValueChange = viewModel::updatePrice,
                    label = { Text("Precio desde (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = uiState.acceptsBookings,
                        onCheckedChange = viewModel::updateAcceptsBookings
                    )
                    Text("Aceptar turnos online")
                }
            }
            item {
                Button(
                    onClick = viewModel::saveProfile,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.isSaving) "Guardando…" else "Publicar / actualizar ficha")
                }
            }

            if (accountType == AccountType.SHOP) {
                item {
                    Text(
                        text = "Catálogo de productos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                item {
                    OutlinedTextField(
                        value = uiState.productName,
                        onValueChange = viewModel::updateProductName,
                        label = { Text("Nombre del producto") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.productPrice,
                            onValueChange = viewModel::updateProductPrice,
                            label = { Text("Precio") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = uiState.productStock,
                            onValueChange = viewModel::updateProductStock,
                            label = { Text("Stock") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Button(
                        onClick = viewModel::addProduct,
                        enabled = !uiState.isSavingProduct,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isSavingProduct) "Guardando…" else "Agregar producto")
                    }
                }
                if (products.isEmpty()) {
                    item {
                        Text(
                            text = "Todavía no cargaste productos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(products, key = { it.id }) { product ->
                        ProductCard(product = product)
                    }
                }
            }

            item {
                Text(
                    text = "Pagos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (payments.isEmpty()) {
                item {
                    Text(
                        text = "No hay intenciones de pago todavía.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(payments, key = { it.id }) { payment ->
                    PaymentCard(
                        payment = payment,
                        onMarkPaid = { viewModel.markPaymentPaid(payment.id) }
                    )
                }
            }

            item {
                Text(
                    text = "Agenda de turnos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (bookings.isEmpty()) {
                item {
                    Text(
                        text = "Todavía no tenés turnos solicitados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(bookings, key = { it.id }) { booking ->
                    BookingCard(
                        booking = booking,
                        onConfirm = { viewModel.confirmBooking(booking.id) },
                        onComplete = { viewModel.completeBooking(booking.id) },
                        onCancel = { viewModel.cancelBooking(booking.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: ShopProduct) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$${product.price.toInt()} · Stock: ${product.stock}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PaymentCard(
    payment: PaymentIntent,
    onMarkPaid: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "$${payment.amount.toInt()} ${payment.currency}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Estado: ${payment.status.name} · ${payment.provider}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (payment.status != PaymentIntentStatus.PAID) {
                TextButton(onClick = onMarkPaid) { Text("Marcar pagado") }
            }
        }
    }
}

@Composable
private fun BookingCard(
    booking: ServiceBooking,
    onConfirm: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val dateLabel = remember(booking.scheduledAt) {
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(booking.scheduledAt))
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = booking.clientName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Estado: ${booking.status.name} · Pago: ${booking.paymentStatus.name}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (booking.notes.isNotBlank()) {
                Text(
                    text = booking.notes,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (booking.status == BookingStatus.PENDING) {
                    TextButton(onClick = onConfirm) { Text("Confirmar") }
                }
                if (booking.status == BookingStatus.CONFIRMED) {
                    TextButton(onClick = onComplete) { Text("Completar") }
                }
                if (booking.status == BookingStatus.PENDING || booking.status == BookingStatus.CONFIRMED) {
                    TextButton(onClick = onCancel) { Text("Cancelar") }
                }
            }
        }
    }
}
