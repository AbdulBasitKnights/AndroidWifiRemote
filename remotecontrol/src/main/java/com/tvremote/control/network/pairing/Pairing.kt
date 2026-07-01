package com.tvremote.control.network.pairing

import com.tvremote.control.coding.Encoder
import com.tvremote.control.network.RequestData

data class PairingRequest(
    val clientName: String,
    val serviceName: String,
) : RequestData {
    private val protocolVersion = PairingNetwork.ProtocolVersion2
    private val statusCode = PairingNetwork.Status.OK

    override val data: ByteArray
        get() {
            val result = mutableListOf<Byte>()
            statusCode.data.forEach { result.add(it) }
            protocolVersion.data.forEach { result.add(it) }
            result.add(0x52)

            if (serviceName.isEmpty() && clientName.isEmpty()) {
                result.add(0)
                return result.toByteArray()
            }

            val array = mutableListOf<Byte>()
            if (serviceName.isNotEmpty()) {
                array.add(0x0a)
                Encoder.encodeVarint(serviceName.toByteArray(Charsets.UTF_8).size).forEach { array.add(it) }
                serviceName.toByteArray(Charsets.UTF_8).forEach { array.add(it) }
            }
            if (clientName.isNotEmpty()) {
                array.add(0x12)
                Encoder.encodeVarint(clientName.toByteArray(Charsets.UTF_8).size).forEach { array.add(it) }
                clientName.toByteArray(Charsets.UTF_8).forEach { array.add(it) }
            }

            Encoder.encodeVarint(array.size).forEach { result.add(it) }
            array.forEach { result.add(it) }
            return result.toByteArray()
        }
}

class PairingResponse(private val payload: ByteArray) {
    val isSuccess: Boolean
        get() = hasPrefix(successPrefix())

    val statusCode: Int?
        get() {
            val index = payload.indexOf(0x10)
            if (index < 0 || index + 2 >= payload.size) return null
            val b1 = payload[index + 1].toInt() and 0xFF
            val b2 = payload[index + 2].toInt() and 0xFF
            return when {
                b1 == 0xC8.toInt() && b2 == 0x01 -> 200
                b1 == 0x90.toInt() && b2 == 0x02 -> 400
                b1 == 0x91.toInt() && b2 == 0x02 -> 401
                b1 == 0x92.toInt() && b2 == 0x02 -> 402
                else -> null
            }
        }

    private fun successPrefix(): ByteArray {
        val prefix = mutableListOf<Byte>()
        PairingNetwork.ProtocolVersion2.data.forEach { prefix.add(it) }
        PairingNetwork.Status.OK.data.forEach { prefix.add(it) }
        prefix.add(0x5a)
        prefix.add(0x00)
        return prefix.toByteArray()
    }

    private fun hasPrefix(prefix: ByteArray): Boolean {
        if (payload.size < prefix.size) return false
        return payload.copyOfRange(0, prefix.size).contentEquals(prefix)
    }
}
