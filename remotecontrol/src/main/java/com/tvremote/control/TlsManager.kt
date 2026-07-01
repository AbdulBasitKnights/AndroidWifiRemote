package com.tvremote.control

import android.os.Build
import com.tvremote.control.misc.TvRemoteError
import com.tvremote.control.misc.TvResult
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager

class TlsManager(
    private val keyStoreProvider: () -> TvResult<KeyStore>,
    private val keyStorePassword: String = "",
    private val keyAlias: String = "client",
) {
    var onServerCertificate: ((X509Certificate) -> Unit)? = null

    fun createSocket(host: String, port: Int, timeoutMs: Int = 60_000): TvResult<SSLSocket> {
        return try {
            val keyStore = when (val result = keyStoreProvider()) {
                is TvResult.Success -> result.value
                is TvResult.Failure -> return result
            }

            val password = keyStorePassword.toCharArray()
            val keyManager = ClientKeyManager(keyStore, password, keyAlias)

            val sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(arrayOf<KeyManager>(keyManager), arrayOf(TrustAllManager()), null)

            val factory = sslContext.socketFactory
            val sslSocket = factory.createSocket() as SSLSocket
            sslSocket.soTimeout = timeoutMs
            sslSocket.connect(InetSocketAddress(host, port), timeoutMs)
            sslSocket.useClientMode = true
            sslSocket.enabledProtocols = arrayOf("TLSv1.2")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val params = sslSocket.sslParameters
                params.endpointIdentificationAlgorithm = null
                sslSocket.sslParameters = params
            }

            sslSocket.startHandshake()

            val serverCerts = sslSocket.session.peerCertificates
            if (serverCerts.isNotEmpty()) {
                onServerCertificate?.invoke(serverCerts[0] as X509Certificate)
            }

            TvResult.Success(sslSocket)
        } catch (e: Exception) {
            TvResult.Failure(TvRemoteError.ConnectionFailed(e))
        }
    }

    private class ClientKeyManager(
        private val keyStore: KeyStore,
        private val password: CharArray,
        private val alias: String,
    ) : X509ExtendedKeyManager() {
        override fun chooseClientAlias(
            keyType: Array<out String>?,
            issuers: Array<out Principal>?,
            socket: java.net.Socket?,
        ): String? = alias

        override fun chooseServerAlias(
            keyType: String?,
            issuers: Array<out Principal>?,
            socket: java.net.Socket?,
        ): String? = null

        override fun getCertificateChain(alias: String?): Array<X509Certificate>? {
            if (alias != this.alias) return null
            val chain = keyStore.getCertificateChain(alias) ?: return null
            return Array(chain.size) { chain[it] as X509Certificate }
        }

        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
            arrayOf(alias)

        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null

        override fun getPrivateKey(alias: String?): PrivateKey? {
            if (alias != this.alias) return null
            return keyStore.getKey(alias, password) as PrivateKey
        }
    }
}
