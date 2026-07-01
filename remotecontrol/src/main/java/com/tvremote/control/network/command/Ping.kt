package com.tvremote.control.network.command

import com.tvremote.control.coding.Encoder
import com.tvremote.control.network.RequestData

class Ping private constructor(
    val val1: ByteArray,
    val val2: ByteArray,
) {
    companion object {
        fun fromBytes(data: ByteArray): Ping? = fromBytes(data.toList())

        fun fromBytes(data: List<Byte>): Ping? {
            if (data.isEmpty()) return null
            if (data[0].toInt() and 0xFF != data.size - 1) return null
            if (data.size < 4) return null
            if (data[1] != 66.toByte()) return null
            if (data[2].toInt() and 0xFF != data[0].toInt() and 0xFF - 2) return null
            if (data[3] != 8.toByte()) return null

            val startIndex = 4
            if (data[2] == 0x02.toByte()) {
                return Ping(data.drop(startIndex).toByteArray(), byteArrayOf())
            }

            var endIndex = data.indexOf(16)
            if (endIndex <= 3) return null
            if (endIndex + 1 < data.size && data[endIndex + 1] == 16.toByte()) {
                endIndex += 1
            }

            val val1 = data.subList(startIndex, endIndex).toByteArray()
            val val2 = if (endIndex + 1 < data.size) {
                data.drop(endIndex).toByteArray()
            } else {
                byteArrayOf()
            }
            return Ping(val1, val2)
        }

        fun extract(data: ByteArray): Ping? = extract(data.toList())

        fun extract(bytes: List<Byte>): Ping? {
            val indexes = bytes.indices.filter { bytes[it] == 66.toByte() }
            for (i in indexes) {
                if (i == 0) continue
                val size = bytes[i - 1].toInt() and 0xFF
                if (i + size > bytes.size) continue
                fromBytes(bytes.subList(i - 1, i + size))?.let { return it }
            }
            return null
        }
    }
}

class Pong(value: ByteArray) : RequestData {
    override val data: ByteArray

    init {
        val payload = mutableListOf<Byte>()
        payload.add(74)
        payload.add((value.size + 1).toByte())
        payload.add(8)
        value.forEach { payload.add(it) }
        payload.add(0, payload.size.toByte())
        data = payload.toByteArray()
    }
}
