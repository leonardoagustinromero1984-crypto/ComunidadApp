package com.comunidapp.app.domain.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.comunidapp.app.domain.map.LeoVerGeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class AddressSuggestion(
    val label: String,
    val locality: String?,
    val province: String?,
    val point: LeoVerGeoPoint
)

/**
 * Platform Geocoder only. No extra API key. Places SDK is not configured.
 */
object AddressGeocoder {

    fun available(context: Context): Boolean {
        return Geocoder.isPresent()
    }

    suspend fun suggest(context: Context, query: String, limit: Int = 5): List<AddressSuggestion> {
        val trimmed = query.trim()
        if (trimmed.length < 3 || !Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context, Locale("es", "AR"))
        val results = lookupByName(geocoder, trimmed, limit)
        return results.mapNotNull { it.toSuggestion() }
    }

    suspend fun reverse(context: Context, point: LeoVerGeoPoint): AddressSuggestion? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale("es", "AR"))
        val results = lookupByPoint(geocoder, point)
        return results.firstOrNull()?.toSuggestion(point)
    }

    private suspend fun lookupByName(geocoder: Geocoder, query: String, limit: Int): List<Address> {
        if (Build.VERSION.SDK_INT >= 33) {
            return suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(query, limit) { list ->
                    if (cont.isActive) cont.resume(list)
                }
            }
        }
        return withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(query, limit).orEmpty()
        }
    }

    private suspend fun lookupByPoint(geocoder: Geocoder, point: LeoVerGeoPoint): List<Address> {
        if (Build.VERSION.SDK_INT >= 33) {
            return suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(point.latitude, point.longitude, 1) { list ->
                    if (cont.isActive) cont.resume(list)
                }
            }
        }
        return withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(point.latitude, point.longitude, 1).orEmpty()
        }
    }

    private fun Address.toSuggestion(fallback: LeoVerGeoPoint? = null): AddressSuggestion? {
        val lat = if (hasLatitude()) latitude else fallback?.latitude
        val lng = if (hasLongitude()) longitude else fallback?.longitude
        if (lat == null || lng == null) return null
        val line = getAddressLine(0)?.trim().orEmpty().ifBlank {
            listOfNotNull(thoroughfare, subLocality, locality, adminArea).joinToString(", ")
        }
        if (line.isBlank()) return null
        return AddressSuggestion(
            label = line,
            locality = locality,
            province = adminArea,
            point = LeoVerGeoPoint(lat, lng)
        )
    }
}
