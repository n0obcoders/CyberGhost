package com.cyberghost.core

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.KeyManager

class CertManager(private val storageDir: File) {

    private val caKeyPair: KeyPair
    private val caCert: X509Certificate
    private val hostCertCache = ConcurrentHashMap<String, Pair<PrivateKey, X509Certificate>>()

    init {
        Security.addProvider(BouncyCastleProvider())
        val caFile = File(storageDir, "cyberghost_ca.p12")
        if (caFile.exists()) {
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(caFile.inputStream(), CA_PASS)
            caKeyPair = KeyPair(
                ks.getCertificate("ca").publicKey,
                ks.getKey("ca", CA_PASS) as PrivateKey
            )
            caCert = ks.getCertificate("ca") as X509Certificate
        } else {
            caKeyPair = generateKeyPair(2048)
            caCert = generateCaCert(caKeyPair)
            saveCa(caFile)
        }
    }

    fun getCaCertPem(): String {
        val encoder = Base64.getMimeEncoder(64, "\n".toByteArray())
        val encoded = encoder.encodeToString(caCert.encoded)
        return "-----BEGIN CERTIFICATE-----\n$encoded\n-----END CERTIFICATE-----\n"
    }

    fun getKeyManagerForHost(host: String): Array<KeyManager> {
        val (key, cert) = hostCertCache.getOrPut(host) { generateHostCert(host) }
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("host", key, CA_PASS, arrayOf(cert, caCert))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, CA_PASS)
        return kmf.keyManagers
    }

    private fun generateHostCert(host: String): Pair<PrivateKey, X509Certificate> {
        val hostKeyPair = generateKeyPair(2048)
        val now = Date()
        val until = Date(now.time + 365L * 24 * 60 * 60 * 1000)
        val issuer = X500Name("CN=CyberGhost CA,O=CyberGhost")
        val subject = X500Name("CN=$host")
        val builder = JcaX509v3CertificateBuilder(
            issuer, BigInteger(128, SecureRandom()),
            now, until, subject, hostKeyPair.public
        )
        val san = GeneralNames(GeneralName(GeneralName.dNSName, host))
        builder.addExtension(Extension.subjectAlternativeName, false, san)
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(caKeyPair.private)
        val holder: X509CertificateHolder = builder.build(signer)
        val cert = JcaX509CertificateConverter().setProvider("BC").getCertificate(holder)
        return hostKeyPair.private to cert
    }

    private fun generateCaCert(keyPair: KeyPair): X509Certificate {
        val now = Date()
        val until = Date(now.time + 10L * 365 * 24 * 60 * 60 * 1000)
        val name = X500Name("CN=CyberGhost CA,O=CyberGhost")
        val builder = JcaX509v3CertificateBuilder(
            name, BigInteger(128, SecureRandom()),
            now, until, name, keyPair.public
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private)
        return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
    }

    private fun generateKeyPair(bits: Int): KeyPair {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(bits, SecureRandom())
        return gen.generateKeyPair()
    }

    private fun saveCa(file: File) {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("ca", caKeyPair.private, CA_PASS, arrayOf(caCert))
        ks.store(file.outputStream(), CA_PASS)
    }

    companion object {
        private val CA_PASS = "cyberghost".toCharArray()
    }
}
