package com.tvremote.control.coding

object Decoder {
    data class Varint(val value: Long, val bytesCount: Int)

    fun decodeVarint(data: ByteArray): Varint? = decodeVarint(data.toList())

    fun decodeVarint(data: List<Byte>): Varint? {
        if (data.isEmpty()) return null

        var shift = 0
        var value = 0L
        var count = 0

        for (byte in data) {
            count++
            value = value or ((byte.toInt() and 0x7F).toLong() shl shift)
            if (byte.toInt() and 0x80 == 0) {
                return Varint(value, count)
            }
            shift += 7
            if (shift > 63) return null
        }

        return Varint(value, count)
    }
}
