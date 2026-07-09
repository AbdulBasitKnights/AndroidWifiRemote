package com.tvremote.app.features.iap

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object ProSubscriptionChecker {

    const val TIMEOUT_MS = 1_500L

    suspend fun check(context: Context): Boolean {
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val connector = IapManager.getIapConnector(context.applicationContext)
                var finished = false
                connector.checkProStatus { isSubscribed ->
                    if (!finished && continuation.isActive) {
                        finished = true
                        continuation.resume(isSubscribed)
                    }
                }
            }
        } ?: true
    }
}
