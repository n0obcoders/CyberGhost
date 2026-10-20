package com.cyberghost.core

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrGenerator {

    /**
     * Generates a WiFi QR code that:
     * 1. Connects the device to the hotspot (SSID + password)
     * 2. On Android 10+ also triggers proxy configuration
     *
     * Standard WiFi QR format:
     * WIFI:T:WPA;S:<ssid>;P:<password>;H:false;;
     *
     * Android proxy auto-config extension (works on MIUI, Samsung, stock Android 10+):
     * WIFI:T:WPA;S:<ssid>;P:<password>;PH:<proxy_host>;PP:<proxy_port>;H:false;;
     */
    fun generateWifiProxyQr(
        ssid: String,
        password: String,
        proxyHost: String,
        proxyPort: Int = 8080,
        size: Int = 512
    ): Bitmap {
        // PH = proxy host, PP = proxy port — Android extension
        val wifiString = "WIFI:T:WPA;S:${escapeWifi(ssid)};P:${escapeWifi(password)};PH:$proxyHost;PP:$proxyPort;H:false;;"

        return encode(wifiString, size)
    }

    /**
     * Generates a proxy-only QR (for devices already on the network)
     * Uses PAC (Proxy Auto-Config) URL format that Android camera app understands
     */
    fun generateProxyOnlyQr(
        proxyHost: String,
        proxyPort: Int = 8080,
        size: Int = 512
    ): Bitmap {
        // This format is recognised by Android's built-in QR scanner
        // when scanning from WiFi settings
        val proxyString = "http://$proxyHost:$proxyPort/proxy.pac"
        return encode(proxyString, size)
    }

    /**
     * Combined QR — shows both WiFi join + proxy in one scan
     * Most reliable across Android versions
     */
    fun generateCombinedQr(
        ssid: String,
        password: String,
        proxyHost: String,
        proxyPort: Int = 8080,
        size: Int = 512
    ): Bitmap {
        val data = buildString {
            append("WIFI:T:WPA;")
            append("S:${escapeWifi(ssid)};")
            append("P:${escapeWifi(password)};")
            append("PH:$proxyHost;")
            append("PP:$proxyPort;")
            append("H:false;;")
        }
        return encode(data, size)
    }

    private fun encode(content: String, size: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val writer = QRCodeWriter()
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun escapeWifi(s: String): String {
        return s.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\"", "\\\"")
            .replace(":", "\\:")
    }
}
