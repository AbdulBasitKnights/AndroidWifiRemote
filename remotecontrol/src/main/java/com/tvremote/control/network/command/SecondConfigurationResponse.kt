package com.tvremote.control.network.command

class VolumeLevel(data: ByteArray) {
    var unknown1: Byte = 0
    var unknown2: Byte = 0
    var modelName: String = ""
    var unknown3: Byte = 0
    var unknown4: Byte? = null
    var volumeMax: Byte? = null
    var volumeLevel: Byte? = null

    init {
        parse(data.toList())
    }

    private fun parse(data: List<Byte>) {
        var index = data.indexOf(146.toByte())
        if (index <= 0) throw IllegalArgumentException("Invalid volume level")
        val length = data[index - 1].toInt() and 0xFF
        if (length < 12 || data.size < index + length) throw IllegalArgumentException("Invalid volume level length")

        index += 1
        if (data[index] != 3.toByte() || data[index + 2] != 8.toByte()) {
            throw IllegalArgumentException("Invalid volume level format")
        }

        unknown1 = data[index + 3]
        index += 4
        if (index >= data.size || data[index] != 16.toByte()) {
            index += 1
        }
        if (index >= data.size || data[index] != 16.toByte()) {
            throw IllegalArgumentException("Invalid volume level format")
        }

        unknown2 = data[index + 1]
        val modelNameSizeIndex = index + 3
        if (modelNameSizeIndex >= data.size) throw IllegalArgumentException("Invalid volume level format")

        val modelNameSize = data[modelNameSizeIndex].toInt() and 0xFF
        if (modelNameSizeIndex + modelNameSize >= data.size) {
            throw IllegalArgumentException("Invalid volume level format")
        }

        modelName = String(
            data.subList(modelNameSizeIndex + 1, modelNameSizeIndex + 1 + modelNameSize).toByteArray(),
            Charsets.UTF_8,
        )

        index = modelNameSizeIndex + modelNameSize + 1
        if (index >= data.size || data[index] != 32.toByte()) return
        index += 1
        if (index >= data.size) return
        unknown3 = data[index]
        index += 1
        if (index >= data.size || data[index] != 40.toByte()) return
        index += 1
        if (index >= data.size) return
        unknown4 = data[index]
        index += 1
        if (index >= data.size || data[index] != 48.toByte()) return
        index += 1
        if (index >= data.size) return
        volumeMax = data[index]
        index += 1
        if (index >= data.size || data[index] != 56.toByte()) return
        index += 1
        if (index >= data.size) return
        volumeLevel = data[index]
    }
}

class SecondConfigurationResponse {
    var powerPart: Boolean = false
        private set
    var currentAppPart: Boolean = false
        private set
    var volumeLevelPart: Boolean = false
        private set
    var runAppName: String? = null
        private set
    var modelName: String = ""

    val readyFullResponse: Boolean
        get() = powerPart && currentAppPart && volumeLevelPart

    fun parse(data: ByteArray): Boolean = parse(data.toList())

    fun parse(data: List<Byte>): Boolean {
        var result = false
        if (!powerPart) {
            powerPart = parsePowerPart(data)
            result = powerPart
        }
        if (!currentAppPart) {
            runAppName = parseCurrentApp(data)
            currentAppPart = runAppName != null
            result = result || currentAppPart
        }
        if (!volumeLevelPart) {
            volumeLevelPart = try {
                VolumeLevel(data.toByteArray())
                true
            } catch (_: Exception) {
                false
            }
            result = result || volumeLevelPart
        }
        return result
    }

    private fun parsePowerPart(data: List<Byte>): Boolean {
        val pattern = listOf(194.toByte(), 2, 2, 8)
        if (data.size < pattern.size) return false
        val index = data.indexOf(194.toByte())
        if (index < 0) return false
        val end = index + pattern.size
        if (end > data.size) return false
        return data.subList(index, end) == pattern
    }

    private fun parseCurrentApp(data: List<Byte>): String? {
        val index = data.indexOf(162.toByte())
        if (index <= 0) return null
        val length = data[index - 1].toInt() and 0xFF
        if (length < 7 || data.size < index + length) return null
        if (data.getOrNull(index + 1) != 1.toByte()) return null
        if (data.getOrNull(index + 3) != 10.toByte()) return null

        var appIndex = data.indexOf(98)
        if (appIndex < 0 || appIndex + 1 >= data.size) return null
        appIndex += 1
        val appNameLength = data[appIndex].toInt() and 0xFF
        if (data.size <= appIndex + appNameLength) return null
        return String(
            data.subList(appIndex + 1, appIndex + 1 + appNameLength).toByteArray(),
            Charsets.UTF_8,
        )
    }
}
