package com.tvremote.control.commands

import com.tvremote.control.coding.Encoder
import com.tvremote.control.network.RequestData

/**
 * Android TV Remote v2 text input via [remote_ime_batch_edit].
 * Navigation keys still use [KeyPress]; printable text must use IME batch edits.
 */
data class ImeBatchEdit(
    val text: String,
    val imeCounter: Int,
    val fieldCounter: Int,
) : RequestData {
    override val data: ByteArray
        get() {
            require(text.isNotEmpty()) { "Text cannot be empty" }

            val textBytes = text.toByteArray(Charsets.UTF_8)
            val cursor = (text.length - 1).coerceAtLeast(0)

            val imeObject = mutableListOf<Byte>()
            imeObject.add(0x08)
            Encoder.encodeVarint(cursor).forEach { imeObject.add(it) }
            imeObject.add(0x10)
            Encoder.encodeVarint(cursor).forEach { imeObject.add(it) }
            imeObject.add(0x1a)
            Encoder.encodeVarint(textBytes.size).forEach { imeObject.add(it) }
            textBytes.forEach { imeObject.add(it) }

            val editInfo = mutableListOf<Byte>()
            editInfo.add(0x08)
            editInfo.add(0x01)
            editInfo.add(0x12)
            Encoder.encodeVarint(imeObject.size).forEach { editInfo.add(it) }
            editInfo.addAll(imeObject)

            val batchEdit = mutableListOf<Byte>()
            batchEdit.add(0x08)
            Encoder.encodeVarint(imeCounter).forEach { batchEdit.add(it) }
            batchEdit.add(0x10)
            Encoder.encodeVarint(fieldCounter).forEach { batchEdit.add(it) }
            batchEdit.add(0x1a)
            Encoder.encodeVarint(editInfo.size).forEach { batchEdit.add(it) }
            batchEdit.addAll(editInfo)

            val message = mutableListOf<Byte>()
            message.add(0xaa.toByte())
            Encoder.encodeVarint(batchEdit.size).forEach { message.add(it) }
            message.addAll(batchEdit)
            return message.toByteArray()
        }
}
