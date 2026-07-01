package com.tvremote.control.network.pairing

import com.tvremote.control.network.RequestData

data class SecretRequest(
    val encodedCert: ByteArray,
    private val status: PairingNetwork.Status = PairingNetwork.Status.OK,
    private val protocolVersion: PairingNetwork.ProtocolVersion2 = PairingNetwork.ProtocolVersion2,
) : RequestData {
    private val unknownFields = byteArrayOf(0xc2.toByte(), 0x02)

    override val data: ByteArray
        get() {
            val result = mutableListOf<Byte>()
            protocolVersion.data.forEach { result.add(it) }
            status.data.forEach { result.add(it) }
            unknownFields.forEach { result.add(it) }
            result.add((encodedCert.size + 2).toByte())
            result.add(0x0a)
            result.add(encodedCert.size.toByte())
            encodedCert.forEach { result.add(it) }
            return result.toByteArray()
        }
}

class SecretResponse(
    private val payload: ByteArray,
    private val code: String,
) {
    val isSuccess: Boolean
        get() {
            if (code.length < 2) return false
            val firstNumber = code.take(2).toIntOrNull(16)?.toByte() ?: return false

            val header = mutableListOf<Byte>()
            PairingNetwork.ProtocolVersion2.data.forEach { header.add(it) }
            PairingNetwork.Status.OK.data.forEach { header.add(it) }
            header.add(0xca.toByte())
            header.add(0x02)

            if (payload.size < header.size + 2) return false
            if (!payload.copyOfRange(0, header.size).contentEquals(header.toByteArray())) {
                return false
            }

            // secret_ack payload should start with hash byte matching first 2 hex chars of code
            val secretStart = header.size
            if (payload.size <= secretStart + 1) return false
            return payload[secretStart + 1] == firstNumber ||
                payload.drop(secretStart).any { it == firstNumber }
        }
}
