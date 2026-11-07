package com.jyotirmoyb.udhaar.sync

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

object AnyMapSerializer : KSerializer<Map<String, Any?>> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Map<String, Any?>) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("Only JsonEncoder supported")
        val jsonObject = JsonObject(value.mapValues { (_, v) -> toElement(v) })
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): Map<String, Any?> {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Only JsonDecoder supported")
        val jsonObject = jsonDecoder.decodeJsonElement() as? JsonObject ?: return emptyMap()
        return jsonObject.mapValues { (_, v) -> fromElement(v) }
    }

    private fun toElement(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is JsonElement -> v
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is String -> JsonPrimitive(v)
        is Map<*, *> -> JsonObject(v.entries.associate { (k, item) -> k.toString() to toElement(item) })
        is List<*> -> JsonArray(v.map { toElement(it) })
        else -> JsonPrimitive(v.toString())
    }

    private fun fromElement(v: JsonElement): Any? = when (v) {
        is JsonNull -> null
        is JsonPrimitive -> {
            if (v.isString) {
                v.content
            } else {
                v.booleanOrNull
                    ?: v.longOrNull
                    ?: v.doubleOrNull
                    ?: v.content
            }
        }
        is JsonArray -> v.map { fromElement(it) }
        is JsonObject -> v.mapValues { (_, child) -> fromElement(child) }
    }
}

@Serializable
data class SyncPushRequest(
    val deviceId: String,
    val changes: List<SyncMutationDto>
)

@Serializable
data class SyncMutationDto(
    val entity: String,
    val syncId: String,
    val op: String,
    @Serializable(with = AnyMapSerializer::class)
    val fields: Map<String, Any?> = emptyMap(),
    val clientRev: Long = 1L,
    val hlc: String = "",
    val idempotencyKey: String
)

@Serializable
data class SyncPushResultDto(
    val syncId: String,
    val status: String, // "ACCEPTED", "CONFLICT", "REJECTED"
    val serverRev: Long? = null,
    @Serializable(with = AnyMapSerializer::class)
    val conflictDetails: Map<String, Any?>? = null,
    val reason: String? = null
)

@Serializable
data class SyncPushResponse(
    val results: List<SyncPushResultDto> = emptyList()
)

@Serializable
data class SyncDeltaEntryDto(
    val seq: Long,
    val entity: String,
    val syncId: String,
    val op: String,
    @Serializable(with = AnyMapSerializer::class)
    val fields: Map<String, Any?> = emptyMap(),
    val serverRev: Long,
    val hlc: String,
    val deviceId: String,
    val timestampMs: Long
)

@Serializable
data class SyncPullResponse(
    val changes: List<SyncDeltaEntryDto> = emptyList(),
    val nextCursor: Long = 0L,
    val hasMore: Boolean = false
)

// Auth & Device Models
@Serializable
data class AuthOtpStartRequest(
    val phone: String,
    val channel: String = "WHATSAPP"
)

@Serializable
data class AuthOtpStartResponse(
    val flowId: String,
    val resendAfterSeconds: Int = 30,
    val destinationMasked: String = "",
    val channel: String = "WHATSAPP"
)

@Serializable
data class AuthPhoneVerifyRequest(
    val flowId: String,
    val code: String
)

@Serializable
data class AuthPhoneVerifyResponse(
    val flowId: String,
    val phoneVerified: Boolean,
    val nextStep: String = ""
)

@Serializable
data class AuthEmailSendRequest(
    val flowId: String,
    val email: String
)

@Serializable
data class AuthEmailSendResponse(
    val flowId: String,
    val resendAfterSeconds: Int = 30,
    val destinationMasked: String = ""
)

@Serializable
data class AuthEmailVerifyRequest(
    val flowId: String,
    val code: String
)

@Serializable
data class AuthSigninVerifyRequest(
    val flowId: String,
    val code: String
)

@Serializable
data class UserProfileDto(
    val uid: String,
    val phoneMasked: String? = null,
    val emailMasked: String? = null
)

@Serializable
data class AuthSessionResponse(
    val token: String? = null,
    val customToken: String? = null,
    val uid: String,
    val user: UserProfileDto? = null
)

@Serializable
data class DeviceDto(
    val deviceId: String,
    val userAgent: String = "",
    val ipAddress: String = "",
    val lastActiveAt: Long = 0L,
    val current: Boolean = false
)

@Serializable
data class DeviceListResponse(
    val devices: List<DeviceDto> = emptyList()
)
