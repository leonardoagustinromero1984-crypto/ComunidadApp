package com.comunidapp.app.domain.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.comunidapp.app.domain.map.LeoVerGeoPoint
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object ForegroundLocation {
    val permissions = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    fun hasForegroundPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    fun hasFinePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isDeviceLocationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return manager?.isLocationEnabled == true
    }

    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): LeoVerGeoPoint? {
        if (!hasForegroundPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val priority = if (hasFinePermission(context)) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val fresh = withTimeoutOrNull(8_000) {
            val token = CancellationTokenSource()
            suspendCancellableCoroutine { cont ->
                client.getCurrentLocation(priority, token.token)
                    .addOnSuccessListener { loc ->
                        cont.resume(loc.toPoint())
                    }
                    .addOnFailureListener { cont.resume(null) }
                cont.invokeOnCancellation { token.cancel() }
            }
        }
        if (fresh != null) return fresh
        val last = withTimeoutOrNull(3_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                client.lastLocation
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        }
        return last.toPoint()
    }

    private fun Location?.toPoint(): LeoVerGeoPoint? =
        this?.let { LeoVerGeoPoint.parseOrNull(it.latitude, it.longitude) }
}
