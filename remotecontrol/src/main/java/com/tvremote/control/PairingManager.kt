package com.tvremote.control

import com.tvremote.control.coding.Encoder
import com.tvremote.control.coding.FramedMessageReader
import com.tvremote.control.misc.DefaultLogger
import com.tvremote.control.misc.Logger
import com.tvremote.control.misc.TvRemoteError
import com.tvremote.control.misc.TvResult
import com.tvremote.control.network.RequestData
import com.tvremote.control.network.pairing.ConfigurationRequest
import com.tvremote.control.network.pairing.ConfigurationResponse
import com.tvremote.control.network.pairing.OptionRequest
import com.tvremote.control.network.pairing.OptionResponse
import com.tvremote.control.network.pairing.PairingRequest
import com.tvremote.control.network.pairing.PairingResponse
import com.tvremote.control.network.pairing.SecretRequest
import com.tvremote.control.network.pairing.SecretResponse
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket

class PairingManager(
    private val tlsManager: TlsManager,
    private val cryptoManager: CryptoManager,
    private val logger: Logger = DefaultLogger(),
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val messageReader = FramedMessageReader()

    private var socket: SSLSocket? = null
    private var code: String = ""

    var stateChanged: ((PairingState) -> Unit)? = null

    @Volatile
    private var pairingState: PairingState = PairingState.Idle
        set(value) {
            field = value
            logState(value)
            stateChanged?.invoke(value)
        }

    fun connect(host: String, clientName: String, serviceName: String, timeoutMs: Int = 60_000) {
        if (host.isBlank()) {
            logger.errorLog("$LOG_PREFIX host shouldn't be empty!")
        }
        messageReader.reset()
        pairingState = PairingState.ExtractTlsParams

        executor.execute {
            when (val result = tlsManager.createSocket(host, PAIRING_PORT, timeoutMs)) {
                is TvResult.Success -> {
                    socket = result.value
                    pairingState = PairingState.Connected
                    logger.debugLog("$LOG_PREFIX Sending pairing request")
                    send(PairingRequest(clientName, serviceName))
                    pairingState = PairingState.PairingRequestSent
                    startReceiveLoop()
                }
                is TvResult.Failure -> {
                    pairingState = PairingState.Error(result.error)
                }
            }
        }
    }

    fun disconnect() {
        logger.infoLog("$LOG_PREFIX disconnect")
        running.set(false)
        messageReader.reset()
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
    }

    fun sendSecret(code: String) {
        logger.debugLog("code: $code")
        this.code = code
        when (val secret = cryptoManager.getEncodedCert(code)) {
            is TvResult.Success -> {
                send(SecretRequest(secret.value))
                pairingState = PairingState.SecretSent
            }
            is TvResult.Failure -> {
                pairingState = PairingState.Error(secret.error)
                disconnect()
            }
        }
    }

    private fun startReceiveLoop() {
        if (!running.compareAndSet(false, true)) return
        executor.execute {
            try {
                while (running.get()) {
                    val chunk = readChunk() ?: continue
                    if (chunk.isEmpty()) continue
                    logger.debugLog("$LOG_PREFIX raw chunk: ${chunk.toList()}")
                    val messages = messageReader.feed(chunk)
                    for (message in messages) {
                        logger.debugLog("$LOG_PREFIX framed message: ${message.toList()}")
                        handleMessage(message)
                        if (!running.get()) break
                    }
                }
            } catch (e: Exception) {
                pairingState = PairingState.Error(TvRemoteError.ReceiveDataError(e))
            } finally {
                running.set(false)
            }
        }
    }

    private fun handleMessage(message: ByteArray) {
        when (pairingState) {
            PairingState.PairingRequestSent -> {
                val response = PairingResponse(message)
                if (!response.isSuccess) {
                    val status = response.statusCode
                    val detail = bytesToHex(message)
                    pairingState = PairingState.Error(
                        TvRemoteError.PairingNotSuccess(
                            message,
                            status,
                            "TV rejected pairing (status=$status). Open TV pairing screen first. bytes=$detail",
                        ),
                    )
                    disconnect()
                    return
                }
                pairingState = PairingState.PairingResponseSuccess
                send(OptionRequest())
                pairingState = PairingState.OptionRequestSent
            }

            PairingState.OptionRequestSent -> {
                val response = OptionResponse(message)
                if (!response.isSuccess) {
                    pairingState = PairingState.Error(
                        TvRemoteError.OptionNotSuccess(message, bytesToHex(message)),
                    )
                    disconnect()
                    return
                }
                pairingState = PairingState.OptionResponseSuccess
                send(ConfigurationRequest())
                pairingState = PairingState.ConfirmationRequestSent
            }

            PairingState.ConfirmationRequestSent -> {
                val response = ConfigurationResponse(message)
                if (!response.isSuccess) {
                    pairingState = PairingState.Error(
                        TvRemoteError.ConfigurationNotSuccess(message, bytesToHex(message)),
                    )
                    disconnect()
                    return
                }
                pairingState = PairingState.ConfirmationResponseSuccess
                pairingState = PairingState.WaitingCode
            }

            PairingState.SecretSent -> {
                val secretResponse = SecretResponse(message, code)
                pairingState = if (secretResponse.isSuccess) {
                    PairingState.SuccessPaired
                } else {
                    PairingState.Error(
                        TvRemoteError.SecretNotSuccess(message, bytesToHex(message)),
                    )
                }
                disconnect()
            }

            else -> Unit
        }
    }

    private fun send(request: RequestData) {
        send(request.data)
    }

    private fun send(data: ByteArray, nextData: ByteArray? = null) {
        val lengthPrefix = Encoder.encodeVarint(data.size)
        val payload = lengthPrefix + data
        logger.debugLog("$LOG_PREFIX Sending data: ${payload.toList()}")
        try {
            val output = socket?.outputStream ?: throw IOException("Socket not connected")
            output.write(payload)
            output.flush()
            logger.debugLog("$LOG_PREFIX Success sent")
            nextData?.let { send(it) }
        } catch (e: Exception) {
            pairingState = PairingState.Error(TvRemoteError.SendDataError(e))
            disconnect()
        }
    }

    private fun readChunk(): ByteArray? {
        val input = socket?.inputStream ?: return null
        val buffer = ByteArray(1024)
        val read = input.read(buffer)
        if (read <= 0) return byteArrayOf()
        return buffer.copyOf(read)
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.take(24).joinToString("") { "%02x".format(it) } + if (bytes.size > 24) "…" else ""

    private fun logState(state: PairingState) {
        when (state) {
            PairingState.Idle -> logger.infoLog("$LOG_PREFIX idle")
            PairingState.ExtractTlsParams -> logger.infoLog("$LOG_PREFIX extract TLS parameters")
            PairingState.Connected -> logger.infoLog("$LOG_PREFIX connected")
            PairingState.PairingRequestSent -> logger.infoLog("$LOG_PREFIX pairing request has been sent")
            PairingState.PairingResponseSuccess -> logger.infoLog("$LOG_PREFIX pairing response success")
            PairingState.OptionRequestSent -> logger.infoLog("$LOG_PREFIX option request sent")
            PairingState.OptionResponseSuccess -> logger.infoLog("$LOG_PREFIX option response success")
            PairingState.ConfirmationRequestSent -> logger.infoLog("$LOG_PREFIX confirmation request has been sent")
            PairingState.ConfirmationResponseSuccess -> logger.infoLog("$LOG_PREFIX confirmation response success")
            PairingState.WaitingCode -> logger.infoLog("$LOG_PREFIX waiting code")
            PairingState.SecretSent -> logger.infoLog("$LOG_PREFIX secret has been sent")
            PairingState.SuccessPaired -> logger.infoLog("$LOG_PREFIX success paired")
            is PairingState.Error -> logger.errorLog("$LOG_PREFIX ${state.error.toDisplayString()}")
            else -> Unit
        }
    }

    sealed class PairingState {
        data object Idle : PairingState()
        data object ExtractTlsParams : PairingState()
        data object ConnectionSetUp : PairingState()
        data object ConnectionPreparing : PairingState()
        data object Connected : PairingState()
        data object PairingRequestSent : PairingState()
        data object PairingResponseSuccess : PairingState()
        data object OptionRequestSent : PairingState()
        data object OptionResponseSuccess : PairingState()
        data object ConfirmationRequestSent : PairingState()
        data object ConfirmationResponseSuccess : PairingState()
        data object WaitingCode : PairingState()
        data object SecretSent : PairingState()
        data object SuccessPaired : PairingState()
        data class Error(val error: TvRemoteError) : PairingState()

        fun toDisplayString(): String = when (this) {
            Idle -> "idle"
            ExtractTlsParams -> "Extract TLS params"
            ConnectionSetUp -> "Connection Set Up"
            ConnectionPreparing -> "Connection Preparing"
            Connected -> "Connected"
            PairingRequestSent -> "Pairing Request Sent"
            PairingResponseSuccess -> "Pairing Response Success"
            OptionRequestSent -> "Option Request Sent"
            OptionResponseSuccess -> "Option Response Success"
            ConfirmationRequestSent -> "Confirmation Request Sent"
            ConfirmationResponseSuccess -> "Confirmation Response Success"
            WaitingCode -> "Waiting Code — enter 6-digit code from TV"
            SecretSent -> "Secret Sent"
            SuccessPaired -> "Success Paired"
            is Error -> "Error: ${error.toDisplayString()}"
        }
    }

    companion object {
        private const val LOG_PREFIX = "Pairing: "
        const val PAIRING_PORT = 6467
    }
}
