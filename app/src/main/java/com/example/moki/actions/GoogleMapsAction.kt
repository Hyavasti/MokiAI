package com.example.moki.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.moki.services.MokiAccessibilityService
import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult

class GoogleMapsAction(private val context: Context) : MokiSkill {
    override val name = "maps_navigation"
    override val description = "Navigate with Google Maps, avoid tolls, reroute, or check traffic."
    override val parameterSchema = """{"command": "navigate" | "avoid_tolls" | "traffic", "destination": "string"}"""
    override val triggerKeywords = listOf("maps", "navigate", "route", "reroute", "traffic", "toll", "tolls", "directions")

    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult {
        val command = params["command"]?.lowercase() ?: "traffic"
        val destination = params["destination"]?.trim().orEmpty()

        return when {
            command == "avoid_tolls" || destination.contains("toll", ignoreCase = true) -> {
                val dest = destination.ifEmpty { "current destination" }
                val uri = Uri.parse("google.navigation:q=${Uri.encode(dest)}&avoid=t")
                launchMapsIntent(uri, "Rerouting in Google Maps while avoiding tolls.")
            }
            destination.isNotEmpty() -> {
                val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
                launchMapsIntent(uri, "Starting navigation to $destination in Google Maps.")
            }
            else -> {
                val bannerText = MokiAccessibilityService.instance?.readMapsTrafficBanner()
                if (!bannerText.isNullOrBlank()) {
                    SkillExecutionResult(true, "Google Maps currently shows: $bannerText")
                } else {
                    SkillExecutionResult(
                        true,
                        "I don't see an active Google Maps route on screen right now. Tell me a destination if you'd like me to start navigation."
                    )
                }
            }
        }
    }

    private fun launchMapsIntent(uri: Uri, successSpeech: String): SkillExecutionResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            SkillExecutionResult(true, successSpeech)
        } catch (_: Exception) {
            SkillExecutionResult(false, "I couldn't open Google Maps on this device.")
        }
    }
}