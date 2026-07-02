package com.tvremote.app.util

sealed class OperationResult {
    data object Success : OperationResult()
    data class Failure(val message: String) : OperationResult()

    val isSuccess: Boolean get() = this is Success
}
