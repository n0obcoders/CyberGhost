package com.cyberghost.core

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SessionSave(private val context: Context) {
    private val saveDir = File(context.filesDir, "sessions").also { it.mkdirs() }

    fun save(
        packets: List<InterceptedPacket>,
        credentials: List<HarvestedCredential>,
        captiveEntries: List<CaptiveEntry>
    ): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "session_$ts"
        try {
            val root = JSONObject()
            root.put("savedAt", ts)
            root.put("version", "2.0")

            // packets
            val pArr = JSONArray()
            packets.takeLast(500).forEach { pkt ->
                val o = JSONObject()
                o.put("host", pkt.host); o.put("path", pkt.path)
                o.put("method", pkt.method ?: ""); o.put("protocol", pkt.protocol)
                o.put("sourceIp", pkt.sourceIp); o.put("statusCode", pkt.statusCode ?: 0)
                o.put("isRequest", pkt.isRequest)
                o.put("bodyPreview", String(pkt.body).take(256))
                pArr.put(o)
            }
            root.put("packets", pArr)

            // credentials
            val cArr = JSONArray()
            credentials.forEach { cred ->
                val o = JSONObject()
                o.put("host", cred.host); o.put("type", cred.type.name)
                o.put("username", cred.username ?: ""); o.put("password", cred.password ?: "")
                o.put("token", cred.token ?: ""); o.put("sourceIp", cred.sourceIp)
                o.put("protocol", cred.protocol); o.put("path", cred.path)
                cArr.put(o)
            }
            root.put("credentials", cArr)

            // captive entries
            val caArr = JSONArray()
            captiveEntries.forEach { e ->
                val o = JSONObject()
                o.put("sourceIp", e.sourceIp); o.put("userAgent", e.userAgent)
                o.put("username", e.username ?: ""); o.put("password", e.password ?: "")
                o.put("accepted", e.accepted)
                caArr.put(o)
            }
            root.put("captiveEntries", caArr)

            File(saveDir, "$name.json").writeText(root.toString(2))
            Log.i("CG2", "Session saved: $name")
        } catch (e: Exception) {
            Log.e("CG2", "Session save failed", e)
        }
        return name
    }

    fun list(): List<String> = saveDir.listFiles()
        ?.filter { it.extension == "json" }
        ?.sortedByDescending { it.lastModified() }
        ?.map { it.nameWithoutExtension } ?: emptyList()

    fun delete(name: String) { File(saveDir, "$name.json").delete() }

    fun getFile(name: String): File? {
        val f = File(saveDir, "$name.json")
        return if (f.exists()) f else null
    }
}
