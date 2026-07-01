package com.tvremote.control.coding

/**
 * Reads length-prefixed messages: [varint length]
 */
class FramedMessageReader {
    private val buffer = ArrayList<Byte>(512)

    fun reset() {
        buffer.clear()
    }

    fun feed(data: ByteArray): List<ByteArray> {
        if (data.isEmpty()) return emptyList()
        buffer.addAll(data.toList())
        return drainMessages()
    }

    private fun drainMessages(): List<ByteArray> {
        val messages = ArrayList<ByteArray>()
        while (buffer.isNotEmpty()) {
            val varint = Decoder.decodeVarint(buffer) ?: break
            val messageLength = varint.value.toInt()
            if (messageLength < 0) break
            val frameSize = varint.bytesCount + messageLength
            if (buffer.size < frameSize) break

            val message = ByteArray(messageLength)
            for (i in 0 until messageLength) {
                message[i] = buffer[varint.bytesCount + i]
            }
            repeat(frameSize) { buffer.removeAt(0) }
            messages.add(message)
        }
        return messages
    }
}
