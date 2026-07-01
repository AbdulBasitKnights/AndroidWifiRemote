package com.tvremote.control.network.pairing

import com.tvremote.control.network.RequestData

data class ConfigurationRequest(
    private val config: PairingConfiguration = PairingConfiguration(
        clientRole = PairingNetwork.RoleType.INPUT,
        encoding = PairingNetwork.PairingEncoding(6, PairingNetwork.EncodingType.HEXADECIMAL),
    ),
    private val status: PairingNetwork.Status = PairingNetwork.Status.OK,
    private val protocolVersion: PairingNetwork.ProtocolVersion2 = PairingNetwork.ProtocolVersion2,
) : RequestData {
    override val data: ByteArray
        get() {
            val result = mutableListOf<Byte>()
            protocolVersion.data.forEach { result.add(it) }
            status.data.forEach { result.add(it) }
            result.add(0xf2.toByte())
            result.add(0x01)
            result.add(config.length)
            config.data.forEach { result.add(it) }
            return result.toByteArray()
        }

    data class PairingConfiguration(
        val clientRole: PairingNetwork.RoleType,
        val encoding: PairingNetwork.PairingEncoding,
    ) {
        val data: ByteArray
            get() {
                val result = mutableListOf<Byte>()
                result.add(0x0a)
                result.add(encoding.length)
                encoding.data.forEach { result.add(it) }
                result.add(0x10)
                result.add(0x01)
                return result.toByteArray()
            }

        val length: Byte
            get() = data.size.toByte()
    }
}

class ConfigurationResponse(private val payload: ByteArray) {
    val isSuccess: Boolean
        get() {
            val prefix = mutableListOf<Byte>()
            PairingNetwork.ProtocolVersion2.data.forEach { prefix.add(it) }
            PairingNetwork.Status.OK.data.forEach { prefix.add(it) }
            prefix.add(0xfa.toByte())
            prefix.add(0x01)
            if (payload.size < prefix.size) return false
            return payload.copyOfRange(0, prefix.size).contentEquals(prefix.toByteArray())
        }
}
