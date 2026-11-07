package com.jyotirmoyb.udhaar.sync

data class SyncPushRequest(
    val deviceId: String,
    val changes: List<SyncMutationDto>
)

data class SyncMutationDto(
    val entity: String,
    val syncId: String,
    val op: String,
    val fields: Map<String, Any?>,
    val clientRev: Long = 1L,
    val hlc: String = "",
    val idempotencyKey: String
)

data class SyncPushResultDto(
    val syncId: String,
    val status: String, // "ACCEPTED", "CONFLICT", "REJECTED"
    val serverRev: Long? = null,
    val conflictDetails: Map<String, Any?>? = null,
    val reason: String? = null
)

data class SyncPushResponse(
    val results: List<SyncPushResultDto>
)

data class SyncDeltaEntryDto(
    val seq: Long,
    val entity: String,
    val syncId: String,
    val op: String,
    val fields: Map<String, Any?>,
    val serverRev: Long,
    val hlc: String,
    val deviceId: String,
    val timestampMs: Long
)

data class SyncPullResponse(
    val changes: List<SyncDeltaEntryDto>,
    val nextCursor: Long,
    val hasMore: Boolean
)
