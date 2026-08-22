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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.comunidapp.app.data.model.BookingStatus
import com.comunidapp.app.data.model.PaymentIntent
import com.comunidapp.app.data.model.PaymentIntentStatus
import com.comunidapp.app.data.model.ServiceBooking
import com.comunidapp.app.data.model.ShopProduct
import androidx.compose.foundation.layout.height
import com.comunidapp.app.domain.map.LeoVerGeoPoint
import com.comunidapp.app.domain.map.LeoVerMapCameraState
import com.comunidapp.app.domain.map.LeoVerMapPolicy
import com.comunidapp.app.domain.schedule.ProviderWeeklySchedule
import com.comunidapp.app.ui.components.leo.LeoVerWeeklyHoursEditor
import com.comunidapp.app.ui.map.LeoVerMap
import com.comunidapp.app.domain.RolePermissions
import com.comunidapp.app.domain.canonical.CanonicalProviderWrite
import com.comunidapp.app.domain.schedule.AppointmentSlotPolicy
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.leo.LeoValidationSummary
import com.comunidapp.app.ui.components.v2.V2SurfaceCard
import com.comunidapp.app.ui.components.v2.V2LocationStringPicker
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoSectionTitle
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
    val snackbarHostState = remember { SnackbarHostState() }
    val activeContext by com.comunidapp.app.domain.context.OperationalContextProvider.active.collectAsState()
    val title = RolePermissions.businessPanelTitle(activeContext)

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = title,
                subtitle = "Tu ficha y agenda en Comunidad"
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (user == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(LeoDimens.SpaceSection),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Iniciá sesión para ver tu negocio", color = BrandText)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = LeoDimens.SpaceMd,
                end = LeoDimens.SpaceMd,
                top = padding.calculateTopPadding() + LeoDimens.SpaceSm,
                bottom = padding.calculateBottomPadding() + LeoDimens.SpaceSm
            ),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            item {
                V2SurfaceCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PetImage(
                            imageUrl = uiState.profile?.photoUrl ?: user?.profileImageUrl,
                            modifier = Modifier.size(88.dp),
                            contentDescription = uiState.name.ifBlank { title }
                        )
                        Text(
                            text = uiState.name.ifBlank { title },
                            style = LeoSectionTitle,
                            color = BrandText,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = LeoDimens.SpaceCompact)
                        )
                        Text(
                            text = com.comunidapp.app.domain.context.ContextHumanLabels.shortLabel(activeContext),
                            style = LeoCaption,
                            color = BrandTextSecondary,
                            modifier = Modifier.padding(top = LeoDimens.SpaceMicro)
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Ficha en Comunidad",
                    style = LeoSectionTitle,
                    color = BrandText,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Publicá tu negocio para aparecer en el directorio. Si habilitás turnos online, también podrán reservarte desde LeoVer.",
                    style = LeoCaption,
                    color = BrandTextSecondary
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
                V2LocationStringPicker(
                    value = uiState.location,
                    onValueChange = viewModel::updateLocation
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
                    label = { Text("Teléfono de contacto") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                LeoVerWeeklyHoursEditor(
                    schedule = ProviderWeeklySchedule(uiState.weeklyHours),
                    onChange = { viewModel.updateWeeklyHours(it.days) }
                )
            }
            item {
                val storage = uiState.profile?.category?.let { CanonicalProviderWrite.storageCategory(it) }.orEmpty()
                val fixed = LeoVerMapPolicy.isFixedPublicPremisesCategory(storage)
                if (fixed) {
                    Text("Ubicación en el mapa", fontWeight = FontWeight.SemiBold)
                    Text("Mové el mapa y tocá para confirmar el pin. Esa coordenada es la autoridad geográfica.")
                    LeoVerMap(
                        markers = emptyList(),
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        camera = LeoVerMapCameraState.cameraOrFallback(uiState.pinLat, uiState.pinLng),
                        pinMode = true,
                        onMapClick = { point ->
                            viewModel.updateMapPin(point.latitude, point.longitude, publicPremises = true)
                        }
                    )
                    if (uiState.pinLat != null) {
                        Text("Ubicación confirmada")
                    }
                } else if (storage.isNotBlank()) {
                    Text("Los servicios móviles no publican la coordenada del domicilio.")
                }
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
                    Text("¿Aceptás turnos online?")
                }
            }
            if (uiState.acceptsBookings) {
                item {
                    Text("Intervalo de turnos", fontWeight = FontWeight.SemiBold)
                    com.comunidapp.app.ui.components.leo.LeoVerIntervalChipRow(
                        selectedMinutes = uiState.slotIntervalMinutes,
                        onSelect = viewModel::updateSlotInterval
                    )
                }
            }
            item {
                LeoValidationSummary(summary = uiState.missingRequirements)
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

            if (activeContext is com.comunidapp.app.domain.context.OperationalContext.Shop) {
                item {
                    Text(
                        text = "Catálogo de productos",
                        style = LeoSectionTitle,
                        color = BrandText,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = LeoDimens.SpaceSm)
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
                            style = LeoCaption,
                            color = BrandTextSecondary
                        )
                    }
                } else {
                    items(products, key = { it.id }) { product ->
                        ProductCard(product = product)
                    }
                }
            }

            // V1: no customer checkout / generic "Pagos" in daycare operations.

            item {
                Text(
                    text = "Agenda de turnos",
                    style = LeoSectionTitle,
                    color = BrandText,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = LeoDimens.SpaceSm)
                )
            }
            if (bookings.isEmpty()) {
                item {
                    Text(
                        text = "Todavía no tenés turnos solicitados.",
                        style = LeoCaption,
                        color = BrandTextSecondary
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
    V2SurfaceCard {
        Text(
            text = product.name,
            style = LeoCardTitle,
            color = BrandText,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "$${product.price.toInt()} · Stock: ${product.stock}",
            style = LeoCaption,
            color = BrandTextSecondary
        )
    }
}

@Composable
private fun PaymentCard(
    payment: PaymentIntent,
    onMarkPaid: () -> Unit
) {
    V2SurfaceCard {
        Text(
            text = "$${payment.amount.toInt()} ${payment.currency}",
            style = LeoCardTitle,
            color = BrandText,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Estado: ${payment.status.name} · ${payment.provider}",
            style = LeoCaption,
            color = BrandTextSecondary,
            modifier = Modifier.padding(top = LeoDimens.SpaceMicro)
        )
        if (payment.status != PaymentIntentStatus.PAID) {
            TextButton(onClick = onMarkPaid) { Text("Marcar pagado") }
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
    V2SurfaceCard {
        Text(
            text = booking.clientName,
            style = LeoCardTitle,
            color = BrandText,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = dateLabel,
            style = LeoCaption,
            color = BrandText
        )
        Text(
            text = "Estado: ${booking.status.name} · Pago: ${booking.paymentStatus.name}",
            style = LeoCaption,
            color = BrandTextSecondary,
            modifier = Modifier.padding(top = LeoDimens.SpaceMicro)
        )
        if (booking.notes.isNotBlank()) {
            Text(
                text = booking.notes,
                style = LeoCaption,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = LeoDimens.SpaceMicro)
            )
        }
        Row(
            modifier = Modifier.padding(top = LeoDimens.SpaceSm),
            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceMicro)
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
