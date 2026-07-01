package com.tvremote.control

import com.tvremote.control.coding.Encoder
import com.tvremote.control.misc.DefaultLogger
import com.tvremote.control.misc.Logger
import com.tvremote.control.misc.TvRemoteError
import com.tvremote.control.misc.TvResult
import com.tvremote.control.network.RequestData
import com.tvremote.control.network.command.AndroidTVConfigurationMessage
import com.tvremote.control.network.command.FirstConfigurationRequest
import com.tvremote.control.network.command.FirstConfigurationResponse
import com.tvremote.control.network.command.Ping
import com.tvremote.control.network.command.Pong
import com.tvremote.control.network.command.SecondConfigurationRequest
import com.tvremote.control.network.command.SecondConfigurationResponse
import com.tvremote.control.network.command.DeviceInfo
import com.tvremote.control.network.command.VolumeLevel
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket

class RemoteManager(
    private val tlsManager: TlsManager,
    val deviceInfo: DeviceInfo,
    private val logger: Logger = DefaultLogger(),
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val receiving = AtomicBoolean(false)

    private var socket: SSLSocket? = null
    private val buffer = mutableListOf<Byte>()
    private var secondConfigurationResponse = SecondConfigurationResponse()

    var stateChanged: ((RemoteState) -> Unit)? = null
    var receiveData: ((ByteArray?, Throwable?) -> Unit)? = null

    @Volatile
    private var remoteState: RemoteState = RemoteState.Idle
        set(value) {
            field = value
            logState(value)
            stateChanged?.invoke(value)
        }

    fun connect(host: String, timeoutMs: Int = 60_000) {
        if (host.isBlank()) {
            logger.errorLog("$LOG_PREFIX host shouldn't be empty!")
        }

        executor.execute {
            logger.infoLog("$LOG_PREFIX connecting $host:$REMOTE_PORT")
            secondConfigurationResponse = SecondConfigurationResponse()
            when (val result = tlsManager.createSocket(host, REMOTE_PORT, timeoutMs)) {
                is TvResult.Success -> {
                    socket = result.value
                    remoteState = RemoteState.Connected
                    receiveLoop()
                }
                is TvResult.Failure -> {
                    if (result.error is TvRemoteError.ConnectionFailed) {
                        remoteState = RemoteState.Error(TvRemoteError.ConnectionWaitingError(result.error.throwable))
                    } else {
                        remoteState = RemoteState.Error(result.error)
                    }
                }
            }
        }
    }

    fun disconnect() {
        logger.infoLog("$LOG_PREFIX disconnect")
        receiving.set(false)
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
    }

    fun send(request: RequestData) {
        send(request.data)
    }

    fun send(data: ByteArray, nextData: ByteArray? = null) {
        val lengthPrefix = Encoder.encodeVarint(data.size)
        sendRaw(lengthPrefix + data, nextData)
    }

    private fun sendRaw(data: ByteArray, nextData: ByteArray? = null) {
        logger.debugLog("$LOG_PREFIX Sending data: ${data.toList()}")
        try {
            val output = socket?.outputStream ?: throw IOException("Socket not connected")
            output.write(data)
            output.flush()
            logger.debugLog("$LOG_PREFIX Success sent")
            nextData?.let { sendRaw(it) }
        } catch (e: Exception) {
            remoteState = RemoteState.Error(TvRemoteError.SendDataError(e))
            disconnect()
        }
    }

    private fun receiveLoop() {
        if (!receiving.compareAndSet(false, true)) return
        executor.execute {
            try {
                while (receiving.get()) {
                    val chunk = readChunk()
                    receiveData?.invoke(chunk, null)
                    if (chunk == null || chunk.isEmpty()) {
                        logger.infoLog("$LOG_PREFIX Empty or completion data received")
                        continue
                    }
                    buffer.addAll(chunk.toList())
                    handleData()
                }
            } catch (e: Exception) {
                remoteState = RemoteState.Error(TvRemoteError.ReceiveDataError(e))
            }
        }
    }

    private fun handleData() {
        val data = buffer.toByteArray()
        logger.debugLog("$LOG_PREFIX handle: ${data.toList()}")

        if (handlePing()) {
            receiveLoop()
            return
        }

        when (remoteState) {
            RemoteState.Connected -> {
                val configMessage = try {
                    AndroidTVConfigurationMessage(data)
                } catch (_: Exception) {
                    logger.debugLog("$LOG_PREFIX it's not configuration message")
                    receiveLoop()
                    return
                }

                buffer.clear()
                remoteState = RemoteState.FirstConfigMessageReceived(configMessage.deviceInfo)
                secondConfigurationResponse.modelName = configMessage.deviceInfo.model
                logger.debugLog("$LOG_PREFIX Sending first configuration request")
                send(FirstConfigurationRequest(deviceInfo))
                remoteState = RemoteState.FirstConfigSent
                receiveLoop()
            }

            RemoteState.FirstConfigSent -> {
                if (!FirstConfigurationResponse(data).isSuccess) {
                    logger.debugLog("$LOG_PREFIX it's not first configuration response")
                    receiveLoop()
                    return
                }
                logger.debugLog("$LOG_PREFIX first configuration response was received")
                buffer.clear()
                logger.debugLog("$LOG_PREFIX Sending second configuration request")
                send(SecondConfigurationRequest)
                remoteState = RemoteState.SecondConfigSent
                receiveLoop()
            }

            RemoteState.SecondConfigSent -> {
                if (!secondConfigurationResponse.parse(data)) {
                    logger.debugLog("$LOG_PREFIX it's not second configuration response")
                    receiveLoop()
                    return
                }
                if (secondConfigurationResponse.powerPart) {
                    logger.debugLog("$LOG_PREFIX second configuration response POWER - OK")
                }
                if (secondConfigurationResponse.currentAppPart) {
                    logger.debugLog("$LOG_PREFIX second configuration response CURRENT APP - OK")
                }
                if (secondConfigurationResponse.volumeLevelPart) {
                    logger.debugLog("$LOG_PREFIX second configuration response VOLUME LEVEL - OK")
                }
                buffer.clear()
                if (!secondConfigurationResponse.readyFullResponse) {
                    receiveLoop()
                    return
                }
                remoteState = RemoteState.Paired(secondConfigurationResponse.runAppName)
                receiveLoop()
            }

            else -> {
                logger.debugLog("$LOG_PREFIX unrecognized data")
                try {
                    VolumeLevel(data)
                    buffer.clear()
                } catch (_: Exception) {
                }
                receiveLoop()
            }
        }
    }

    private fun handlePing(): Boolean {
        val ping = Ping.extract(buffer.toByteArray()) ?: return false
        logger.debugLog("$LOG_PREFIX ping has been handled")
        buffer.clear()
        val pong = Pong(ping.val1)
        logger.debugLog("$LOG_PREFIX sending pong")
        sendRaw(pong.data)
        return true
    }

    private fun readChunk(): ByteArray? {
        val input = socket?.inputStream ?: return null
        val buffer = ByteArray(512)
        val read = input.read(buffer)
        if (read < 0) return byteArrayOf()
        if (read == 0) return byteArrayOf()
        return buffer.copyOf(read)
    }

    private fun logState(state: RemoteState) {
        when (state) {
            RemoteState.Idle -> logger.infoLog("$LOG_PREFIX idle")
            RemoteState.ConnectionSetUp -> logger.infoLog("$LOG_PREFIX connection set up")
            RemoteState.ConnectionPreparing -> logger.infoLog("$LOG_PREFIX connection preparing")
            RemoteState.Connected -> logger.infoLog("$LOG_PREFIX connected")
            is RemoteState.FirstConfigMessageReceived -> logger.infoLog(
                "$LOG_PREFIX first configuration message received: ${state.info.vendor} ${state.info.model}",
            )
            RemoteState.FirstConfigSent -> logger.infoLog("$LOG_PREFIX first configuration has been sent")
            RemoteState.SecondConfigSent -> logger.infoLog("$LOG_PREFIX second configuration has been sent")
            is RemoteState.Paired -> logger.infoLog(
                "$LOG_PREFIX paired, current running app: ${state.runningApp ?: "Unknown"}",
            )
            is RemoteState.Error -> logger.errorLog("$LOG_PREFIX ${state.error.toDisplayString()}")
        }
    }

    sealed class RemoteState {
        data object Idle : RemoteState()
        data object ConnectionSetUp : RemoteState()
        data object ConnectionPreparing : RemoteState()
        data object Connected : RemoteState()
        data class FirstConfigMessageReceived(val info: DeviceInfo) : RemoteState()
        data object FirstConfigSent : RemoteState()
        data object SecondConfigSent : RemoteState()
        data class Paired(val runningApp: String?) : RemoteState()
        data class Error(val error: TvRemoteError) : RemoteState()

        fun toDisplayString(): String = when (this) {
            Idle -> "idle"
            ConnectionSetUp -> "connection Set Up"
            ConnectionPreparing -> "connection Preparing"
            Connected -> "connected"
            is FirstConfigMessageReceived ->
                "first Config Message Received: vendor: ${info.vendor} model: ${info.model}"
            FirstConfigSent -> "first Config Sent"
            SecondConfigSent -> "second Config Sent"
            is Paired -> "Paired! Current running app ${runningApp.orEmpty()}"
            is Error -> "Error: ${error.toDisplayString()}"
        }
    }

    companion object {
        private const val LOG_PREFIX = "Remote: "
        const val REMOTE_PORT = 6466
    }
}
