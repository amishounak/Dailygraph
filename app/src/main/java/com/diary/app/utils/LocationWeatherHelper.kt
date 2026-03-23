package com.diary.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.resume

data class LocationWeatherInfo(
    val location: String,
    val temperature: String
)

object LocationWeatherHelper {

    private val httpClient = OkHttpClient()

    @SuppressLint("MissingPermission")
    suspend fun getLocationAndWeather(context: Context): LocationWeatherInfo {
        return withContext(Dispatchers.IO) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val cts = CancellationTokenSource()

                val location = suspendCancellableCoroutine { cont ->
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cts.token
                    ).addOnSuccessListener { loc ->
                        cont.resume(loc)
                    }.addOnFailureListener {
                        cont.resume(null)
                    }
                    cont.invokeOnCancellation { cts.cancel() }
                }

                if (location == null) {
                    return@withContext LocationWeatherInfo("Location N/A", "Temp N/A")
                }

                val lat = location.latitude
                val lon = location.longitude

                // Reverse geocode for city name
                val locationName = getLocationName(context, lat, lon)

                // Fetch temperature from Open-Meteo (free, no key)
                val temperature = fetchTemperature(lat, lon)

                LocationWeatherInfo(locationName, temperature)
            } catch (e: Exception) {
                LocationWeatherInfo("Location N/A", "Temp N/A")
            }
        }
    }

    private suspend fun getLocationName(context: Context, lat: Double, lon: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lon, 1) { addresses ->
                            if (addresses.isNotEmpty()) {
                                val addr = addresses[0]
                                val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: ""
                                val country = addr.countryCode ?: ""
                                cont.resume(if (city.isNotEmpty()) "$city, $country" else country.ifEmpty { "Location N/A" })
                            } else {
                                cont.resume("Location N/A")
                            }
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: ""
                        val country = addr.countryCode ?: ""
                        if (city.isNotEmpty()) "$city, $country" else country.ifEmpty { "Location N/A" }
                    } else {
                        "Location N/A"
                    }
                }
            } catch (e: Exception) {
                "Location N/A"
            }
        }
    }

    private suspend fun fetchTemperature(lat: Double, lon: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&temperature_unit=celsius"
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext "Temp N/A"
                    val json = JSONObject(body)
                    val currentWeather = json.getJSONObject("current_weather")
                    val temp = currentWeather.getDouble("temperature")
                    "${temp.toInt()}°C"
                } else {
                    "Temp N/A"
                }
            } catch (e: Exception) {
                "Temp N/A"
            }
        }
    }
}
