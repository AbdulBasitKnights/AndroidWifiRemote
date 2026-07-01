package com.tvremote.control

import android.content.Context
import com.tvremote.control.misc.TvRemoteError
import com.tvremote.control.misc.TvResult
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class CertManager(private val context: Context) {
    private val certDir: File
        get() = File(context.filesDir, "tvremote_certs").apply { mkdirs() }

    val p12File: File
        get() = File(certDir, "cert.p12")

    val derFile: File
        get() = File(certDir, "cert.der")

    fun ensureCertificates(clientName: String = "Android TV Remote", password: String = ""): TvResult<Unit> {
        return try {
            if (!p12File.exists() || !derFile.exists()) {
                generateSelfSignedCertificate(clientName, password)
            }
            TvResult.Success(Unit)
        } catch (e: Exception) {
            TvResult.Failure(TvRemoteError.LoadCertError(e))
        }
    }

    fun loadKeyStore(password: String = ""): TvResult<KeyStore> {
        return try {
            ensureCertificates(password = password)
            val keyStore = KeyStore.getInstance("PKCS12")
            p12File.inputStream().use { stream ->
                keyStore.load(stream, password.toCharArray())
            }
            TvResult.Success(keyStore)
        } catch (e: Exception) {
            TvResult.Failure(TvRemoteError.LoadCertError(e))
        }
    }

    fun getClientPublicKey(): TvResult<PublicKey> {
        return try {
            ensureCertificates()
            val factory = CertificateFactory.getInstance("X.509")
            val certificate = factory.generateCertificate(derFile.inputStream()) as X509Certificate
            TvResult.Success(certificate.publicKey)
        } catch (e: Exception) {
            TvResult.Failure(TvRemoteError.CreateCertFromDataError)
        }
    }

    fun getServerPublicKey(certificate: X509Certificate): TvResult<PublicKey> {
        return try {
            TvResult.Success(certificate.publicKey)
        } catch (_: Exception) {
            TvResult.Failure(TvRemoteError.SecTrustCopyKeyError)
        }
    }

    private fun generateSelfSignedCertificate(clientName: String, password: String) {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048, SecureRandom())
        val keyPair: KeyPair = keyPairGenerator.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now)
        val notAfter = Date(now + 3650L * 24 * 60 * 60 * 1000)

        val subject = X500Name("CN=$clientName")
        val serial = BigInteger.valueOf(now)
        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public,
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val holder = certBuilder.build(signer)
        val certificate: X509Certificate = JcaX509CertificateConverter().getCertificate(holder)

        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)
        keyStore.setKeyEntry(
            "client",
            keyPair.private,
            password.toCharArray(),
            arrayOf<Certificate>(certificate),
        )
        p12File.outputStream().use { output ->
            keyStore.store(output, password.toCharArray())
        }

        derFile.outputStream().use { output ->
            output.write(certificate.encoded)
        }
    }
}

class TrustAllManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

fun X509Certificate.toPublicKeyBytes(): ByteArray = encoded

fun loadCertificate(bytes: ByteArray): X509Certificate {
    val factory = CertificateFactory.getInstance("X.509")
    return factory.generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
}
