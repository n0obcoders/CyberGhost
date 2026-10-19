package com.cyberghost.core

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

data class InjectionProfile(
    val name: String,
    val config: InjectionConfig
)

class ProfileManager(private val context: Context) {

    private val profileDir = File(context.filesDir, "profiles").also { it.mkdirs() }

    fun save(name: String, config: InjectionConfig) {
        try {
            val json = JSONObject().apply {
                put("hookEnabled", config.hookEnabled)
                put("audioUrl", config.audioUrl ?: "")
                put("videoUrl", config.videoUrl ?: "")
                put("documentUrl", config.documentUrl ?: "")
                put("redirectUrl", config.redirectUrl ?: "")
                put("customJs", config.customJs ?: "")
                put("customHtml", config.customHtml ?: "")
            }
            File(profileDir, "$name.json").writeText(json.toString(2))
            Log.i("CG2", "Profile saved: $name")
        } catch (e: Exception) {
            Log.e("CG2", "Profile save failed", e)
        }
    }

    fun load(name: String): InjectionConfig? {
        return try {
            val json = JSONObject(File(profileDir, "$name.json").readText())
            InjectionConfig(
                hookEnabled = json.optBoolean("hookEnabled", true),
                audioUrl = json.optString("audioUrl").ifBlank { null },
                videoUrl = json.optString("videoUrl").ifBlank { null },
                documentUrl = json.optString("documentUrl").ifBlank { null },
                redirectUrl = json.optString("redirectUrl").ifBlank { null },
                customJs = json.optString("customJs").ifBlank { null },
                customHtml = json.optString("customHtml").ifBlank { null }
            )
        } catch (e: Exception) {
            Log.e("CG2", "Profile load failed: $name", e)
            null
        }
    }

    fun list(): List<String> {
        return profileDir.listFiles()?.filter { it.extension == "json" }?.map { it.nameWithoutExtension } ?: emptyList()
    }

    fun delete(name: String) { File(profileDir, "$name.json").delete() }
}
