package com.tvremote.control.misc

sealed class TvRemoteError : Exception() {
    data object UnexpectedCertData : TvRemoteError()
    data object ExtractIdentityError : TvRemoteError()
    data object SecIdentityCreateError : TvRemoteError()
    data class ToLongNames(val description: String) : TvRemoteError()
    data object ConnectionCanceled : TvRemoteError()
    data class PairingNotSuccess(val data: ByteArray, val status: Int? = null, val detail: String? = null) : TvRemoteError()
    data class OptionNotSuccess(val data: ByteArray, val detail: String? = null) : TvRemoteError()
    data class ConfigurationNotSuccess(val data: ByteArray, val detail: String? = null) : TvRemoteError()
    data class SecretNotSuccess(val data: ByteArray, val detail: String? = null) : TvRemoteError()
    data class ConnectionWaitingError(val throwable: Throwable) : TvRemoteError()
    data class ConnectionFailed(val throwable: Throwable) : TvRemoteError()
    data class ReceiveDataError(val throwable: Throwable) : TvRemoteError()
    data class SendDataError(val throwable: Throwable) : TvRemoteError()
    data class InvalidCode(val description: String) : TvRemoteError()
    data object WrongCode : TvRemoteError()
    data object NoSecAttributes : TvRemoteError()
    data object NotRsaKey : TvRemoteError()
    data object NotPublicKey : TvRemoteError()
    data object NoKeySizeAttribute : TvRemoteError()
    data object NoValueData : TvRemoteError()
    data object InvalidCertData : TvRemoteError()
    data object CreateCertFromDataError : TvRemoteError()
    data object NoClientPublicCertificate : TvRemoteError()
    data object NoServerPublicCertificate : TvRemoteError()
    data object SecTrustCopyKeyError : TvRemoteError()
    data class LoadCertError(val throwable: Throwable) : TvRemoteError()
    data object SecPkcs12ImportNotSuccess : TvRemoteError()
    data object CreateTrustObjectError : TvRemoteError()
    data class SecTrustCreateWithCertificatesNotSuccess(val status: Int) : TvRemoteError()

    fun toDisplayString(): String = when (this) {
        UnexpectedCertData -> "unexpected cert data"
        ExtractIdentityError -> "extract identity error"
        SecIdentityCreateError -> "sec identity create error"
        is ToLongNames -> "too long names: $description"
        ConnectionCanceled -> "connection canceled"
        is PairingNotSuccess -> detail ?: "pairing not success (status=$status)"
        is OptionNotSuccess -> detail ?: "option not success"
        is ConfigurationNotSuccess -> detail ?: "configuration not success"
        is SecretNotSuccess -> detail ?: "wrong pairing code or secret rejected"
        is ConnectionWaitingError -> "connection waiting error: ${throwable.message}"
        is ConnectionFailed -> {
            val message = throwable.message.orEmpty()
            when {
                message.contains("IP ADDRESS", ignoreCase = true) ->
                    "invalid address — enter TV IP like 192.168.1.50 (not placeholder text)"
                message.contains("failed to connect", ignoreCase = true) ||
                    message.contains("ECONNREFUSED", ignoreCase = true) ->
                    "cannot reach TV at that IP — check TV network settings or tap Scan for TVs"
                message.contains("timed out", ignoreCase = true) ->
                    "connection timed out — phone and TV must be on same WiFi"
                else -> "connection failed: $message"
            }
        }
        is ReceiveDataError -> "receive data error: ${throwable.message}"
        is SendDataError -> "send data error: ${throwable.message}"
        is InvalidCode -> "invalid code: $description"
        WrongCode -> "wrong code"
        NoSecAttributes -> "no sec attributes"
        NotRsaKey -> "not RSA key"
        NotPublicKey -> "not public key"
        NoKeySizeAttribute -> "no key size attribute"
        NoValueData -> "no value data"
        InvalidCertData -> "invalid cert data"
        CreateCertFromDataError -> "create cert from data error"
        NoClientPublicCertificate -> "no client public certificate"
        NoServerPublicCertificate -> "no server public certificate"
        SecTrustCopyKeyError -> "sec trust copy key error"
        is LoadCertError -> "load cert error: ${throwable.message}"
        SecPkcs12ImportNotSuccess -> "PKCS12 import not success"
        CreateTrustObjectError -> "create trust object error"
        is SecTrustCreateWithCertificatesNotSuccess -> "sec trust create not success: $status"
    }
}
