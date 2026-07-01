package com.tvremote.control.network.command

import com.tvremote.control.coding.Encoder
import com.tvremote.control.network.RequestData

object CommandNetwork

data class DeviceInfo(
    val model: String,
    val vendor: String,
    val version: String,
    val appName: String,
    val appBuild: String,
) {
    private val length: Int
        get() = model.length + vendor.length + version.length + appBuild.length + appName.length

    companion object {
        fun fromBytes(data: ByteArray): DeviceInfo? {
            if (data.isEmpty()) return null
            var index = 0

            val model = extractString(data, index) ?: return null
            index += 1 + model.toByteArray(Charsets.UTF_8).size

            if (index >= data.size || data[index] != 0x12.toByte()) return null
            index += 1
            val vendor = extractString(data, index) ?: return null
            index += 1 + vendor.toByteArray(Charsets.UTF_8).size

            if (index + 2 >= data.size) return null
            if (data[index] != 0x18.toByte() || data[index + 1] != 0x01.toByte() || data[index + 2] != 0x22.toByte()) {
                return null
            }
            index += 3
            val version = extractString(data, index) ?: return null
            index += 1 + version.toByteArray(Charsets.UTF_8).size

            if (index >= data.size || data[index] != 0x2a.toByte()) return null
            index += 1
            val appName = extractString(data, index) ?: return null
            index += appName.toByteArray(Charsets.UTF_8).size + 1

            if (index >= data.size || data[index] != 0x32.toByte()) return null
            index += 1
            val appBuild = extractString(data, index) ?: "-1"

            return DeviceInfo(model, vendor, version, appName, appBuild)
        }

        private fun extractString(data: ByteArray, index: Int): String? {
            if (data.size <= index) return null
            val size = data[index].toInt() and 0xFF
            if (data.size <= index + size) return null
            return String(data, index + 1, size, Charsets.UTF_8)
        }
    }
}

class AndroidTVConfigurationMessage(data: ByteArray) {
    val deviceInfo: DeviceInfo

    init {
        require(data.size > 10) { "Invalid configuration message" }
        require(data[8] == 0x0a.toByte()) { "Invalid configuration message format" }
        deviceInfo = DeviceInfo.fromBytes(data.copyOfRange(9, data.size))
            ?: throw IllegalArgumentException("Unable to parse device info")
    }
}

data class FirstConfigurationRequest(
    val deviceInfo: DeviceInfo,
) : RequestData {
    override val data: ByteArray
        get() {
            val modelLength = Encoder.encodeVarint(deviceInfo.model.toByteArray(Charsets.UTF_8).size)
            val vendorLength = Encoder.encodeVarint(deviceInfo.vendor.toByteArray(Charsets.UTF_8).size)
            val buildLength = Encoder.encodeVarint(deviceInfo.appBuild.toByteArray(Charsets.UTF_8).size)
            val appNameLength = Encoder.encodeVarint(deviceInfo.appName.toByteArray(Charsets.UTF_8).size)
            val versionLength = Encoder.encodeVarint(deviceInfo.version.toByteArray(Charsets.UTF_8).size)

            val subLength = 7 + deviceInfo.model.length + deviceInfo.vendor.length +
                deviceInfo.appBuild.length + deviceInfo.appName.length + deviceInfo.version.length +
                modelLength.size + vendorLength.size + buildLength.size + appNameLength.size + versionLength.size
            val lengthEncoded = Encoder.encodeVarint(subLength)
            val length = subLength + 4 + lengthEncoded.size

            val result = mutableListOf<Byte>()
            result.add(0x0a)
            Encoder.encodeVarint(length).forEach { result.add(it) }
            result.add(0x08)
            result.add(0xEE.toByte())
            result.add(0x04)
            result.add(0x12)
            Encoder.encodeVarint(subLength).forEach { result.add(it) }
            result.add(0x0a)
            modelLength.forEach { result.add(it) }
            deviceInfo.model.toByteArray(Charsets.UTF_8).forEach { result.add(it) }
            result.add(0x12)
            vendorLength.forEach { result.add(it) }
            deviceInfo.vendor.toByteArray(Charsets.UTF_8).forEach { result.add(it) }
            result.add(0x18)
            result.add(0x01)
            result.add(0x22)
            buildLength.forEach { result.add(it) }
            deviceInfo.appBuild.toByteArray(Charsets.UTF_8).forEach { result.add(it) }
            result.add(0x2a)
            appNameLength.forEach { result.add(it) }
            deviceInfo.appName.toByteArray(Charsets.UTF_8).forEach { result.add(it) }
            result.add(0x32)
            versionLength.forEach { result.add(it) }
            deviceInfo.version.toByteArray(Charsets.UTF_8).forEach { result.add(it) }
            return result.toByteArray()
        }
}

class FirstConfigurationResponse(val data: ByteArray) {
    val isSuccess: Boolean
        get() = data.size >= 3 && data.takeLast(3) == listOf(0x02.toByte(), 0x12.toByte(), 0x00.toByte())
}

object SecondConfigurationRequest : RequestData {
    override val data: ByteArray = byteArrayOf(0x12, 0x03, 0x08, 0xEE.toByte(), 0x04)
}
