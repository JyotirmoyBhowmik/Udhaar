package com.jyotirmoyb.udhaar.sync

import kotlin.math.max

/**
 * Hybrid Logical Clock (HLC) Implementation for Android.
 * Implements Kulkarni et al. physical-logical clock combining wall clock monotonicity
 * with Lamport logical causality and deterministic device tie-breaking.
 * Format: "millis:counter:deviceId"
 */
data class Hlc(
    val millis: Long,
    val counter: Int,
    val deviceId: String
) : Comparable<Hlc> {

    fun pack(): String = "$millis:$counter:$deviceId"

    override fun compareTo(other: Hlc): Int {
        if (this.millis != other.millis) {
            return this.millis.compareTo(other.millis)
        }
        if (this.counter != other.counter) {
            return this.counter.compareTo(other.counter)
        }
        return this.deviceId.compareTo(other.deviceId)
    }

    companion object {
        fun create(deviceId: String, wallTimeMs: Long = System.currentTimeMillis()): Hlc {
            return Hlc(millis = wallTimeMs, counter = 0, deviceId = deviceId.ifBlank { "android_client" })
        }

        fun unpack(hlcString: String?): Hlc {
            if (hlcString.isNullOrBlank()) {
                return create("unknown", 0L)
            }
            val parts = hlcString.split(":")
            if (parts.size < 3) {
                return create("unknown", 0L)
            }
            val m = parts[0].toLongOrNull() ?: 0L
            val c = parts[1].toIntOrNull() ?: 0
            val d = parts.subList(2, parts.size).joinToString(":")
            return Hlc(millis = m, counter = c, deviceId = d)
        }

        fun increment(currentHlc: Hlc, wallTimeMs: Long = System.currentTimeMillis()): Hlc {
            return if (wallTimeMs > currentHlc.millis) {
                Hlc(millis = wallTimeMs, counter = 0, deviceId = currentHlc.deviceId)
            } else {
                Hlc(millis = currentHlc.millis, counter = currentHlc.counter + 1, deviceId = currentHlc.deviceId)
            }
        }

        fun update(localHlc: Hlc, remoteHlcString: String, wallTimeMs: Long = System.currentTimeMillis()): Hlc {
            val remote = unpack(remoteHlcString)
            val maxM = max(localHlc.millis, max(remote.millis, wallTimeMs))
            val nextC = when {
                maxM == localHlc.millis && maxM == remote.millis -> max(localHlc.counter, remote.counter) + 1
                maxM == localHlc.millis -> localHlc.counter + 1
                maxM == remote.millis -> remote.counter + 1
                else -> 0
            }
            return Hlc(millis = maxM, counter = nextC, deviceId = localHlc.deviceId)
        }

        fun compare(hlcStringA: String?, hlcStringB: String?): Int {
            val a = unpack(hlcStringA)
            val b = unpack(hlcStringB)
            return a.compareTo(b)
        }
    }
}
