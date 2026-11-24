package com.example.moki.actions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WeatherAction(private val context: Context) : MokiSkill {
    override val name = "weather"
    override val description = "Check current temperature and weather conditions locally or for a specific city."
    override val parameterSchema = """{"location": "city name or current"}"""
    override val triggerKeywords = listOf("weather", "temperature", "rain", "forecast", "hot", "cold")

    @SuppressLint("MissingPermission")
    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult = withContext(Dispatchers.IO) {
        val rawPlace = params["location"]?.trim().orEmpty()
        val isLocalQuery = rawPlace.isEmpty() ||
                rawPlace.equals("current", ignoreCase = true) ||
                rawPlace.contains("my location", ignoreCase = true) ||
                rawPlace.contains("here", ignoreCase = true) ||
                rawPlace.equals("now", ignoreCase = true)

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        try {
            var lat: Double? = null
            var lon: Double? = null
            var resolvedLabel = ""

            if (!isLocalQuery) {
                val geoUrl =
                    "https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(rawPlace)}&count=1&language=en&format=json"
                val geoReq = Request.Builder()
                    .url(geoUrl)
                    .header("User-Agent", "MokiAndroid/1.0")
                    .build()

                client.newCall(geoReq).execute().use { geoResp ->
                    if (geoResp.isSuccessful) {
                        val geoJson = JSONObject(geoResp.body?.string().orEmpty())
                        val results = geoJson.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val first = results.getJSONObject(0)
                            lat = first.getDouble("latitude")
                            lon = first.getDouble("longitude")
                            resolvedLabel = " in ${first.getString("name")}"
                        }
                    }
                }
            }

            if (lat == null || lon == null) {
                val hasLocation = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasLocation) {
                    return@withContext SkillExecutionResult(
                        false,
                        "I need location permission to check your local weather."
                    )
                }

                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val location = fusedClient.lastLocation.await()
                    ?: fusedClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        null
                    ).await()
                    ?: return@withContext SkillExecutionResult(
                        false,
                        "Your phone's GPS hasn't locked a location yet—make sure Location is turned on in quick settings."
                    )

                lat = location.latitude
                lon = location.longitude
            }

            val url =
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
            val weatherReq = Request.Builder()
                .url(url)
                .header("User-Agent", "MokiAndroid/1.0")
                .build()

            client.newCall(weatherReq).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext SkillExecutionResult(
                        false,
                        "The weather service returned an unexpected response (${response.code})."
                    )
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val current = json.getJSONObject("current")
                val temp = current.getDouble("temperature_2m")
                val code = current.getInt("weather_code")
                val condition = describeWeatherCode(code)

                SkillExecutionResult(
                    true,
                    "Right now$resolvedLabel it's around ${temp.toInt()} degrees Celsius and $condition."
                )
            }
        } catch (e: Exception) {
            Log.e("WeatherAction", "Weather lookup failed", e)
            SkillExecutionResult(
                false,
                "I couldn't reach the weather service right now—make sure Moki has mobile data or Wi-Fi access enabled."
            )
        }
    }

    private fun describeWeatherCode(code: Int): String = when (code) {
        0 -> "clear and sunny"
        1, 2, 3 -> "partly cloudy"
        45, 48 -> "foggy"
        51, 53, 55, 61, 63, 65 -> "rainy"
        80, 81, 82 -> "experiencing rain showers"
        95, 96, 99 -> "stormy"
        else -> "mild"
    }
}