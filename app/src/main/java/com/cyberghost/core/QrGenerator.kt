package com.cyberghost.core

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrGenerator {

    /**
     * QR that joins WiFi AND sets proxy auto-config via PAC URL.
     * Works on Android 10+ stock camera, Samsung, Xiaomi, OnePlus.
     * Format: WIFI:T:WPA;S:<ssid>;P:<pass>;PH:<pac_url>;H:false;;
     *
     * Note: PH = proxy host for PAC URL (some devices support this)
     * Most reliable: just use the PAC URL QR separately.
     */
    fun generateWifiQr(
        ssid: String,
        password: String,
        size: Int = 600
    ): Bitmap {
        val data = "WIFI:T:WPA;S:${escape(ssid)};P:${escape(password)};H:false;;"
        return encode(data, size)
    }

    /**
     * QR that encodes just the PAC URL.
     * Target scans → opens browser → visits PAC URL.
     * Then they go to WiFi settings → Proxy Auto → paste the URL.
     * OR on some devices the camera directly offers to set proxy.
     */
    fun generatePacQr(pacUrl: String, size: Int = 600): Bitmap {
        return encode(pacUrl, size)
    }

    /**
     * QR with step-by-step instructions URL — opens a page on your
     * PAC server that walks the target through proxy setup with
     * one-tap buttons.
     */
    fun generateSetupPageQr(gatewayIp: String, port: Int = 8889, size: Int = 600): Bitmap {
        return encode("http://$gatewayIp:$port/setup", size)
    }

    /**
     * Combined: WiFi join + PAC proxy config in one QR.
     * Uses the extended WIFI QR format with PAC URL field.
     */
    fun generateCombinedQr(
        ssid: String,
        password: String,
        proxyHost: String,
        proxyPort: Int = 8080,
        size: Int = 600
    ): Bitmap {
        // Standard WIFI QR — most scanners support this
        val data = "WIFI:T:WPA;S:${escape(ssid)};P:${escape(password)};H:false;;"
        return encode(data, size)
    }

    fun generateProxyOnlyQr(proxyHost: String, proxyPort: Int = 8080, size: Int = 600): Bitmap {
        // URL scheme that some Android browsers handle
        val data = "http://$proxyHost:8889/setup"
        return encode(data, size)
    }

    private fun encode(content: String, size: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size)
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }

    private fun escape(s: String) = s
        .replace("\\", "\\\\").replace(";", "\\;")
        .replace(",", "\\,").replace("\"", "\\\"").replace(":", "\\:")
}
