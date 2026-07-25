package com.dms.app.services.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager

/**
 * GpsLocationProvider fetches the real-time live GPS coordinates from Android LocationManager
 * and formats them automatically into a clickable Google Maps URL.
 */
class GpsLocationProvider(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun getCurrentOrLastKnownLocationUrl(): String? {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null

            val gpsLoc = try { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (e: Exception) { null }
            val netLoc = try { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { null }

            val bestLoc = when {
                gpsLoc != null && netLoc != null -> if (gpsLoc.time > netLoc.time) gpsLoc else netLoc
                gpsLoc != null -> gpsLoc
                netLoc != null -> netLoc
                else -> null
            }

            if (bestLoc != null) {
                formatGoogleMapsUrl(bestLoc.latitude, bestLoc.longitude)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun formatGoogleMapsUrl(lat: Double, lng: Double): String {
            return "https://maps.google.com/?q=$lat,$lng"
        }
    }
}
