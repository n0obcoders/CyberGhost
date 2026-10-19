package com.cyberghost.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.cert.X509Certificate
import javax.net.ssl.*

data class CertInfo(
    val host: String,
    val subject: String,
    val issuer: String,
    val validFrom: String,
    val validTo: String,
    val serialNumber: String,
    val algorithm: String,
    val isExpired: Boolean,
    val isSelfSigned: Boolean,
    val chainLength: Int
)

class SslCertViewer {

    suspend fun inspect(host: String, port: Int = 443): CertInfo? = withContext(Dispatchers.IO) {
        try {
            val ctx = SSLContext.getInstance("TLS")
            val certs = mutableListOf<X509Certificate>()
            ctx.init(null, arrayOf(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    chain?.let { certs.addAll(it) }
                }
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }), null)

            val factory = ctx.socketFactory
            val socket = factory.createSocket(host, port) as SSLSocket
            socket.soTimeout = 5000
            socket.startHandshake()
            socket.close()

            if (certs.isEmpty()) return@withContext null

            val leaf = certs[0]
            val isExpired = try { leaf.checkValidity(); false } catch (_: Exception) { true }
            val isSelfSigned = leaf.subjectDN == leaf.issuerDN

            CertInfo(
                host = host,
                subject = leaf.subjectDN.name,
                issuer = leaf.issuerDN.name,
                validFrom = leaf.notBefore.toString(),
                validTo = leaf.notAfter.toString(),
                serialNumber = leaf.serialNumber.toString(16),
                algorithm = leaf.sigAlgName,
                isExpired = isExpired,
                isSelfSigned = isSelfSigned,
                chainLength = certs.size
            )
        } catch (e: Exception) {
            Log.e("CG2", "Cert inspection failed for $host", e)
            null
        }
    }
}
