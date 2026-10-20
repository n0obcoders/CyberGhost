package com.cyberghost.core

object OsFingerprinter {

    data class OsFingerprint(
        val os: String,
        val confidence: String,
        val details: String
    )

    fun fingerprint(userAgent: String?, ttl: Int? = null): OsFingerprint {
        // UA-based fingerprinting
        if (userAgent != null) {
            val ua = userAgent.lowercase()
            return when {
                ua.contains("iphone") || ua.contains("ipad") -> OsFingerprint("iOS", "High", extractIosVersion(ua))
                ua.contains("android") -> OsFingerprint("Android", "High", extractAndroidVersion(ua))
                ua.contains("windows phone") -> OsFingerprint("Windows Phone", "High", "")
                ua.contains("windows nt 10") -> OsFingerprint("Windows 10/11", "High", "")
                ua.contains("windows nt 6.3") -> OsFingerprint("Windows 8.1", "High", "")
                ua.contains("windows nt 6.1") -> OsFingerprint("Windows 7", "High", "")
                ua.contains("macintosh") || ua.contains("mac os x") -> OsFingerprint("macOS", "High", extractMacVersion(ua))
                ua.contains("linux") && ua.contains("x11") -> OsFingerprint("Linux", "Medium", "")
                ua.contains("cros") -> OsFingerprint("ChromeOS", "High", "")
                ua.contains("ubuntu") -> OsFingerprint("Ubuntu Linux", "High", "")
                ua.contains("firefox") -> OsFingerprint("Unknown (Firefox)", "Low", "")
                else -> OsFingerprint("Unknown", "Low", ua.take(60))
            }
        }

        // TTL-based fallback
        return when (ttl) {
            in 60..70 -> OsFingerprint("Linux/Android", "Low", "TTL ~64")
            in 120..130 -> OsFingerprint("Windows", "Low", "TTL ~128")
            in 250..260 -> OsFingerprint("Network device", "Low", "TTL ~255")
            else -> OsFingerprint("Unknown", "Low", "")
        }
    }

    private fun extractIosVersion(ua: String): String {
        val match = Regex("os (\\d+)[_ ](\\d+)").find(ua)
        return if (match != null) "iOS ${match.groupValues[1]}.${match.groupValues[2]}" else "iOS"
    }

    private fun extractAndroidVersion(ua: String): String {
        val match = Regex("android (\\d+\\.?\\d*)").find(ua)
        return if (match != null) "Android ${match.groupValues[1]}" else "Android"
    }

    private fun extractMacVersion(ua: String): String {
        val match = Regex("mac os x (\\d+)[_ ](\\d+)").find(ua)
        return if (match != null) "macOS ${match.groupValues[1]}.${match.groupValues[2]}" else "macOS"
    }

    fun getBrowserInfo(userAgent: String?): String {
        if (userAgent == null) return "Unknown"
        val ua = userAgent.lowercase()
        return when {
            ua.contains("edg/") -> "Microsoft Edge"
            ua.contains("opr/") || ua.contains("opera") -> "Opera"
            ua.contains("samsung") -> "Samsung Browser"
            ua.contains("crios/") -> "Chrome iOS"
            ua.contains("fxios/") -> "Firefox iOS"
            ua.contains("chrome/") && !ua.contains("chromium") -> "Google Chrome"
            ua.contains("firefox/") -> "Mozilla Firefox"
            ua.contains("safari/") && !ua.contains("chrome") -> "Safari"
            ua.contains("ucbrowser") -> "UC Browser"
            else -> "Unknown Browser"
        }
    }
}
