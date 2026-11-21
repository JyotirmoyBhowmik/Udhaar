package com.jyotirmoyb.udhaar.util

import android.content.Context

/**
 * Backward-compatible delegating object pointing to [AppLogger].
 */
object SafeLogger {

    fun redact(raw: String?): String = AppLogger.redact(raw)

    fun d(tag: String, message: String) = AppLogger.d(tag, message)

    fun i(tag: String, message: String) = AppLogger.i(tag, message)

    fun w(tag: String, message: String, throwable: Throwable? = null) = AppLogger.w(tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) = AppLogger.e(tag, message, throwable)

    fun recordNonFatal(
        tag: String,
        errorId: String,
        throwable: Throwable? = null,
        metadata: Map<String, String> = emptyMap()
    ) = AppLogger.recordNonFatal(tag, errorId, throwable, metadata)

    fun initCrashlyticsUser(context: Context) = AppLogger.initCrashlyticsUser(context)
}
