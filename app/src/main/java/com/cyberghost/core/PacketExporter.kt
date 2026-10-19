package com.cyberghost.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PacketExporter(private val context: Context) {

    fun exportHar(packets: List<InterceptedPacket>): File? {
        try {
            val har = JSONObject()
            val log = JSONObject()
            log.put("version", "1.2")
            log.put("creator", JSONObject().put("name", "CyberGhost2").put("version", "2.0"))

            val entries = JSONArray()
            packets.forEach { pkt ->
                val entry = JSONObject()
                entry.put("startedDateTime", pkt.timestamp.toString())

                val request = JSONObject()
                request.put("method", pkt.method ?: "GET")
                request.put("url", "${pkt.protocol.lowercase()}://${pkt.host}${pkt.path}")
                request.put("httpVersion", "HTTP/1.1")
                val reqHeaders = JSONArray()
                pkt.headers.forEach { (k, v) -> reqHeaders.put(JSONObject().put("name", k).put("value", v)) }
                request.put("headers", reqHeaders)
                request.put("bodySize", pkt.body.size)

                if (pkt.isRequest) {
                    entry.put("request", request)
                    entry.put("response", JSONObject().put("status", 0))
                } else {
                    entry.put("request", JSONObject().put("method", pkt.method).put("url", "${pkt.protocol.lowercase()}://${pkt.host}${pkt.path}"))
                    val response = JSONObject()
                    response.put("status", pkt.statusCode ?: 0)
                    response.put("headers", reqHeaders)
                    val content = JSONObject()
                    content.put("size", pkt.body.size)
                    content.put("mimeType", pkt.headers["Content-Type"] ?: "")
                    content.put("text", String(pkt.body).take(2000))
                    response.put("content", content)
                    entry.put("response", response)
                }

                entries.put(entry)
            }
            log.put("entries", entries)
            har.put("log", log)

            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(context.cacheDir, "cyberghost_$ts.har")
            file.writeText(har.toString(2))
            Log.i("CG2", "Exported ${packets.size} packets to ${file.name}")
            return file
        } catch (e: Exception) {
            Log.e("CG2", "HAR export failed", e)
            return null
        }
    }

    fun shareFile(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Export Capture").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.e("CG2", "Share failed", e)
        }
    }
}
