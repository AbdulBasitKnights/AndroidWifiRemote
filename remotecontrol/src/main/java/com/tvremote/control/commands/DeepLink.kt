package com.tvremote.control.commands

import com.tvremote.control.coding.Encoder
import com.tvremote.control.network.RequestData

data class DeepLink(val url: String) : RequestData {
    override val data: ByteArray
        get() {
            val urlBytes = url.toByteArray(Charsets.UTF_8)
            val urlLengthEncoded = Encoder.encodeVarint(urlBytes.size)
            val innerLength = 1 + urlLengthEncoded.size + urlBytes.size
            val outerLengthEncoded = Encoder.encodeVarint(innerLength)

            val result = mutableListOf<Byte>()
            result.add(0xd2.toByte())
            result.add(0x05)
            outerLengthEncoded.forEach { result.add(it) }
            result.add(0x0a)
            urlLengthEncoded.forEach { result.add(it) }
            urlBytes.forEach { result.add(it) }
            return result.toByteArray()
        }
}
