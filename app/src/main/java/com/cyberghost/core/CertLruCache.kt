package com.cyberghost.core

import java.security.PrivateKey
import java.security.cert.X509Certificate

class CertLruCache(private val maxSize: Int = 200) {
    private val cache = object : LinkedHashMap<String, Pair<PrivateKey, X509Certificate>>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<PrivateKey, X509Certificate>>): Boolean = size > maxSize
    }
    @Synchronized fun get(host: String): Pair<PrivateKey, X509Certificate>? = cache[host]
    @Synchronized fun put(host: String, entry: Pair<PrivateKey, X509Certificate>) { cache[host] = entry }
}
