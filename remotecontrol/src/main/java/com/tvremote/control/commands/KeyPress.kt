package com.tvremote.control.commands

import com.tvremote.control.coding.Encoder
import com.tvremote.control.network.RequestData

data class KeyPress(
    val key: Key,
    val direction: Direction = Direction.SHORT,
) : RequestData {
    override val data: ByteArray
        get() {
            val encodedKey = Encoder.encodeVarint(key.rawValue)
            val payload = ByteArray(3 + encodedKey.size)
            payload[0] = 0x52
            payload[1] = (3 + encodedKey.size).toByte()
            payload[2] = 0x08
            System.arraycopy(encodedKey, 0, payload, 3, encodedKey.size)
            val result = payload.toMutableList()
            result.add(0x10)
            result.add(direction.rawValue)
            return result.toByteArray()
        }
}
