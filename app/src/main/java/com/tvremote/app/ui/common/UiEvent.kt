package com.tvremote.app.ui.common

sealed interface UiEvent {
    data class ToastMessage(val message: String) : UiEvent
    data class ToastRes(val resId: Int) : UiEvent
}
