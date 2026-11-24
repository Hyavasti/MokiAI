package com.example.moki.actions

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.example.moki.skills.MokiSkill
import com.example.moki.skills.SkillExecutionResult

class FlashlightAction(private val context: Context) : MokiSkill {
    override val name = "flashlight"
    override val description = "Turn the phone flashlight or torch on or off."
    override val parameterSchema = """{"state": "on" | "off"}"""
    override val triggerKeywords = listOf("flashlight", "torch", "light", "dark")

    private var isTorchOn = false

    override suspend fun execute(
        params: Map<String, String>,
        confirmed: Boolean
    ): SkillExecutionResult {
        val desired = params["state"]?.lowercase() ?: if (isTorchOn) "off" else "on"
        val turnOn = desired == "on" || desired == "true"

        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return SkillExecutionResult(
                success = false,
                spokenResponse = "It looks like this device doesn't have a camera flash I can control."
            )

            cameraManager.setTorchMode(cameraId, turnOn)
            isTorchOn = turnOn
            SkillExecutionResult(
                success = true,
                spokenResponse = if (turnOn) "Flashlight is on." else "Flashlight is off."
            )
        } catch (_: Exception) {
            SkillExecutionResult(
                success = false,
                spokenResponse = "I couldn't toggle the flashlight right now—another app might be using the camera."
            )
        }
    }
}