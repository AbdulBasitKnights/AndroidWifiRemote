package com.tvremote.control.network.pairing

object PairingNetwork {
    object ProtocolVersion2 {
        val data = byteArrayOf(0x08, 0x02)
        const val size = 2
    }

    enum class Status {
        UNKNOWN,
        OK,
        ERROR,
        BAD_CONFIGURATION,
        BAD_SECRET;

        val data: ByteArray
            get() = when (this) {
                UNKNOWN -> byteArrayOf()
                OK -> byteArrayOf(0x10, 0xc8.toByte(), 0x01)
                ERROR -> byteArrayOf(0x10, 0x90.toByte(), 0x02)
                BAD_CONFIGURATION -> byteArrayOf(0x10, 0x91.toByte(), 0x02)
                BAD_SECRET -> byteArrayOf(0x10, 0x92.toByte(), 0x02)
            }

        val encodedSize: Int
            get() = if (this == UNKNOWN) 0 else 3
    }

    enum class EncodingType(val rawValue: Byte) {
        UNKNOWN(0),
        ALPHANUMERIC(1),
        NUMERIC(2),
        HEXADECIMAL(3),
        QRCODE(4),
    }

    data class PairingEncoding(
        val symbolLength: Int,
        val type: EncodingType,
    ) {
        val data: ByteArray
            get() {
                val array = mutableListOf<Byte>()
                if (type.rawValue != 0.toByte()) {
                    array.add(0x08)
                    array.add(type.rawValue)
                }
                if (symbolLength > 0) {
                    array.add(0x10)
                    if (symbolLength < 128) {
                        array.add(symbolLength.toByte())
                    } else {
                        val part2 = symbolLength / 128
                        val part1 = symbolLength - (part2 - 1) * 128
                        array.add(part1.toByte())
                        array.add(part2.toByte())
                    }
                }
                return array.toByteArray()
            }

        val length: Byte
            get() = data.size.toByte()
    }

    enum class RoleType(val rawValue: Byte) {
        UNKNOWN(0),
        INPUT(1),
        OUTPUT(2),
    }
}
