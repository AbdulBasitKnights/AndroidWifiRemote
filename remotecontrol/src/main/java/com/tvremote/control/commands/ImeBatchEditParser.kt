package com.tvremote.control.commands

object ImeBatchEditParser {
    data class ImeCounters(val imeCounter: Int, val fieldCounter: Int)

    fun parseCounters(data: ByteArray): ImeCounters? {
        var index = 0
        while (index < data.size) {
            val tag = data[index].toInt() and 0xFF
            index += 1
            if (tag == 0xAA) {
                val (length, next) = readVarint(data, index) ?: return null
                index = next
                if (index + length > data.size) return null
                return parseBatchEdit(data.copyOfRange(index, index + length))
            }
            val wireType = tag and 0x07
            when (wireType) {
                0 -> {
                    val (_, next) = readVarint(data, index) ?: return null
                    index = next
                }
                2 -> {
                    val (length, next) = readVarint(data, index) ?: return null
                    index = next + length
                }
                else -> return null
            }
        }
        return null
    }

    private fun parseBatchEdit(data: ByteArray): ImeCounters? {
        var imeCounter: Int? = null
        var fieldCounter: Int? = null
        var index = 0
        while (index < data.size) {
            val tag = data[index].toInt() and 0xFF
            index += 1
            when (tag) {
                0x08 -> {
                    val (value, next) = readVarint(data, index) ?: return null
                    imeCounter = value
                    index = next
                }
                0x10 -> {
                    val (value, next) = readVarint(data, index) ?: return null
                    fieldCounter = value
                    index = next
                }
                0x1a -> {
                    val (length, next) = readVarint(data, index) ?: return null
                    index = next + length
                }
                else -> return null
            }
        }
        if (imeCounter == null || fieldCounter == null) return null
        return ImeCounters(imeCounter, fieldCounter)
    }

    private fun readVarint(data: ByteArray, start: Int): Pair<Int, Int>? {
        var result = 0
        var shift = 0
        var index = start
        while (index < data.size) {
            val byte = data[index].toInt() and 0xFF
            index += 1
            result = result or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) {
                return result to index
            }
            shift += 7
            if (shift > 28) return null
        }
        return null
    }
}
