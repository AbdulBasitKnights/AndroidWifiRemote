package com.tvremote.control

import com.tvremote.control.misc.TvRemoteError
import com.tvremote.control.misc.TvResult
import java.security.MessageDigest
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey

class CryptoManager {
    var clientPublicKeyProvider: (() -> TvResult<PublicKey>)? = null
    var serverPublicKeyProvider: (() -> TvResult<PublicKey>)? = null

    fun getEncodedCert(code: String): TvResult<ByteArray> {
        if (code.length != 6) {
            return TvResult.Failure(TvRemoteError.InvalidCode("The code should contain 6 characters"))
        }

        val firstNumber = code.take(2).toIntOrNull(16)?.toByte()
            ?: return TvResult.Failure(TvRemoteError.InvalidCode("The code should contain only hex characters"))

        code.drop(2).toIntOrNull(16)
            ?: return TvResult.Failure(TvRemoteError.InvalidCode("The code should contain only hex characters"))

        val clientComponents = when (val result = clientPublicKeyProvider?.invoke()) {
            is TvResult.Success -> getCertComponents(result.value)
            is TvResult.Failure -> return result
            null -> return TvResult.Failure(TvRemoteError.NoClientPublicCertificate)
        }
        if (clientComponents is TvResult.Failure) return clientComponents

        val serverComponents = when (val result = serverPublicKeyProvider?.invoke()) {
            is TvResult.Success -> getCertComponents(result.value)
            is TvResult.Failure -> return result
            null -> return TvResult.Failure(TvRemoteError.NoServerPublicCertificate)
        }
        if (serverComponents is TvResult.Failure) return serverComponents

        val client = (clientComponents as TvResult.Success).value
        val server = (serverComponents as TvResult.Success).value

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(client.first)
        digest.update(client.second)
        digest.update(server.first)
        digest.update(server.second)
        digest.update(hexToBytes(code.substring(2)))
        val hashData = digest.digest()

        if (hashData.first() != firstNumber) {
            return TvResult.Failure(TvRemoteError.WrongCode)
        }

        return TvResult.Success(hashData)
    }

    private fun getCertComponents(publicKey: PublicKey): TvResult<Pair<ByteArray, ByteArray>> {
        if (publicKey !is RSAPublicKey) {
            return TvResult.Failure(TvRemoteError.NotRsaKey)
        }

        val modulus = hexToBytes(publicKey.modulus.toString(16))
        val exponent = hexToBytes("0${publicKey.publicExponent.toString(16)}")
        return TvResult.Success(modulus to exponent)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val padded = if (hex.length % 2 == 1) "0$hex" else hex
        return ByteArray(padded.length / 2) { index ->
            padded.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
