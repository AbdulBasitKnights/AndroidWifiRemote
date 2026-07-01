package com.tvremote.control.coding

object Encoder {
    fun encodeVarint(value: Int): ByteArray {
        return encodeVarint(value.toLong())
    }

    fun encodeVarint(value: Long): ByteArray {
        if (value <= 127) {
            return byteArrayOf(value.toByte())
        }

        val encoded = ArrayList<Byte>()
        var v = value
        while (v != 0L) {
            var byte = (v and 0x7F).toByte()
            v = v ushr 7
            if (v != 0L) {
                byte = (byte.toInt() or 0x80).toByte()
            }
            encoded.add(byte)
        }
        return encoded.toByteArray()
    }
}
