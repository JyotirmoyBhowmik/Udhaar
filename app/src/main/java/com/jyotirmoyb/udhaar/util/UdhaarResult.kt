package com.jyotirmoyb.udhaar.util

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException

/**
 * Structured error domain for backup, Google Drive sync, cloud verification, and database steps.
 * Maps technical exceptions to clear, actionable user-facing messages without leaking PII.
 */
sealed class UdhaarAppError(val userMessage: String, val code: String) {
    data object NoNetwork : UdhaarAppError(
        userMessage = "No internet connection. Your local ledger is safe and will sync when you are back online.",
        code = "NO_NETWORK"
    )

    data object SignedOut : UdhaarAppError(
        userMessage = "You are signed out. Please sign in with Google to use cloud backup or mutual verification.",
        code = "SIGNED_OUT"
    )

    data object QuotaExceeded : UdhaarAppError(
        userMessage = "Cloud or Google Drive storage quota exceeded. Please free up space and try again.",
        code = "QUOTA_EXCEEDED"
    )

    data object WrongPassphrase : UdhaarAppError(
        userMessage = "Incorrect backup passphrase. Please verify your passphrase and try again.",
        code = "WRONG_PASSPHRASE"
    )

    data object CorruptedFile : UdhaarAppError(
        userMessage = "The backup file or verification payload is corrupted or tampered with.",
        code = "CORRUPTED_FILE"
    )

    data class DatabaseError(val operation: String) : UdhaarAppError(
        userMessage = "Could not complete local database operation ($operation). Please retry.",
        code = "DATABASE_ERROR"
    )

    data class UnknownError(val fallbackDetail: String = "An unexpected error occurred. Please try again.") : UdhaarAppError(
        userMessage = fallbackDetail,
        code = "UNKNOWN_ERROR"
    )

    companion object {
        fun DatabaseFailure(operation: String): UdhaarAppError = DatabaseError(operation)
        fun Unknown(fallbackDetail: String): UdhaarAppError = UnknownError(fallbackDetail)

        fun fromThrowable(
            throwable: Throwable,
            defaultMessage: String = "Operation could not be completed. Please try again."
        ): UdhaarAppError {
            val msg = (throwable.message ?: "").lowercase()
            return when {
                throwable is AEADBadTagException ||
                    throwable is BadPaddingException ||
                    msg.contains("bad_decrypt") ||
                    msg.contains("tag mismatch") ||
                    msg.contains("wrong passphrase") ||
                    msg.contains("incorrect passphrase") -> WrongPassphrase

                throwable is UnknownHostException ||
                    throwable is SocketTimeoutException ||
                    msg.contains("unable to resolve host") ||
                    msg.contains("network is unreachable") ||
                    msg.contains("unavailable") ||
                    msg.contains("offline") -> NoNetwork

                msg.contains("unauthenticated") ||
                    msg.contains("signed out") ||
                    msg.contains("not signed in") ||
                    msg.contains("401") ||
                    msg.contains("auth") && msg.contains("null") -> SignedOut

                msg.contains("quota") ||
                    msg.contains("resource_exhausted") ||
                    msg.contains("insufficient storage") ||
                    msg.contains("storagequotaexceeded") ||
                    msg.contains("429") -> QuotaExceeded

                msg.contains("corrupt") ||
                    msg.contains("malformed") ||
                    msg.contains("invalid magic") ||
                    msg.contains("jsonexception") ||
                    msg.contains("tampered") -> CorruptedFile

                msg.contains("sqlite") ||
                    msg.contains("sqlcipher") ||
                    msg.contains("database") -> DatabaseError("storage")

                throwable is IOException -> NoNetwork

                else -> UnknownError(
                    SafeLogger.redact(throwable.message).ifBlank {
                        defaultMessage
                    }
                )
            }
        }
    }
}

/**
 * Small Result-based wrapper pairing success values with [UdhaarAppError].
 */
sealed class UdhaarResult<out T> {
    data class Success<T>(val data: T) : UdhaarResult<T>()
    data class Failure(val error: UdhaarAppError, val cause: Throwable? = null) : UdhaarResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): UdhaarAppError? = (this as? Failure)?.error

    inline fun <R> map(transform: (T) -> R): UdhaarResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    inline fun onSuccess(action: (T) -> Unit): UdhaarResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (UdhaarAppError) -> Unit): UdhaarResult<T> {
        if (this is Failure) action(error)
        return this
    }

    companion object {
        inline fun <T> runSafe(tag: String, operation: String, block: () -> T): UdhaarResult<T> {
            return try {
                Success(block())
            } catch (t: Throwable) {
                val appError = UdhaarAppError.fromThrowable(t)
                SafeLogger.w(tag, "Operation '$operation' failed with ${appError.code}", t)
                Failure(appError, t)
            }
        }
    }
}
