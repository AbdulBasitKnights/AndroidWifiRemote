package com.tvremote.control.network.pairing

import com.tvremote.control.network.RequestData

data class OptionRequest(
    private val option: PairingOption = PairingOption(
        inputEncodings = listOf(PairingNetwork.PairingEncoding(6, PairingNetwork.EncodingType.HEXADECIMAL)),
        outputEncodings = emptyList(),
        preferredRole = PairingNetwork.RoleType.INPUT,
    ),
    private val status: PairingNetwork.Status = PairingNetwork.Status.OK,
    private val protocolVersion: PairingNetwork.ProtocolVersion2 = PairingNetwork.ProtocolVersion2,
) : RequestData {
    override val data: ByteArray
        get() {
            val result = mutableListOf<Byte>()
            protocolVersion.data.forEach { result.add(it) }
            status.data.forEach { result.add(it) }
            result.add(0xa2.toByte())
            result.add(0x01)
            result.add(option.length)
            option.data.forEach { result.add(it) }
            return result.toByteArray()
        }

    data class PairingOption(
        val inputEncodings: List<PairingNetwork.PairingEncoding> = emptyList(),
        val outputEncodings: List<PairingNetwork.PairingEncoding> = emptyList(),
        val preferredRole: PairingNetwork.RoleType = PairingNetwork.RoleType.INPUT,
    ) {
        val data: ByteArray
            get() {
                val result = mutableListOf<Byte>()
                inputEncodings.forEach { encoding ->
                    result.add(0x0a)
                    result.add(encoding.length)
                    encoding.data.forEach { result.add(it) }
                }
                outputEncodings.forEach { encoding ->
                    result.add(0x12)
                    result.add(encoding.length)
                    encoding.data.forEach { result.add(it) }
                }
                if (preferredRole.rawValue != 0.toByte()) {
                    result.add(0x18)
                    result.add(preferredRole.rawValue)
                }
                return result.toByteArray()
            }

        val length: Byte
            get() {
                var len = 0
                inputEncodings.forEach { len += 2 + it.length }
                outputEncodings.forEach { len += 2 + it.length }
                if (preferredRole.rawValue != 0.toByte()) len += 2
                return len.toByte()
            }
    }
}

class OptionResponse(private val payload: ByteArray) {
    val isSuccess: Boolean
        get() {
            val prefix = mutableListOf<Byte>()
            PairingNetwork.ProtocolVersion2.data.forEach { prefix.add(it) }
            PairingNetwork.Status.OK.data.forEach { prefix.add(it) }
            prefix.add(0xa2.toByte())
            prefix.add(0x01)
            if (payload.size < prefix.size) return false
            return payload.copyOfRange(0, prefix.size).contentEquals(prefix.toByteArray())
        }
}
