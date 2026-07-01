package com.tvremote.control.misc

sealed class TvResult<out T> {
    data class Success<T>(val value: T) : TvResult<T>()
    data class Failure(val error: TvRemoteError) : TvResult<Nothing>()
}
