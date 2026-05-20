package com.myra.assistant.features

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import java.util.*

class LocationHelper(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: ((String) -> Unit)? = null

    fun getCurrentLocation(callback: (String) -> Unit) {
        locationCallback = callback

        if (!hasLocationPermission()) {
            callback("Location permission not granted. Please enable location permission.")
            return
        }

        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(5000)
                .setMaxUpdates(1)
                .build()

            val locationListener = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        val address = getAddressFromLocation(location)
                        callback("You are at ${location.latitude}, ${location.longitude}. $address")
                    } ?: callback("Location not available. Please enable GPS.")
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationListener,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            callback("Location permission not granted.")
        }
    }

    fun searchNearby(query: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$query"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getDirectionsTo(placeName: String) {
        try {
            val encodedName = Uri.encode(placeName)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getAddressFromLocation(location: Location): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            addresses?.firstOrNull()?.getAddressLine(0) ?: "Unknown location"
        } catch (e: Exception) {
            "Unknown location"
        }
    }
}