package com.jyotirmoyb.udhaar.data

import com.google.firebase.firestore.DocumentSnapshot

/**
 * GuestActionV1: Strongly-typed sealed hierarchy for guest actions published to
 * `/guestLinks/{linkId}/actions/{actionId}`.
 *
 * Conforms to strict schema defined by firestore.rules (schemaVersion: 1).
 */
sealed class GuestActionV1 {
    abstract val linkId: String
    abstract val actionId: String
    abstract val guestUid: String
    abstract val status: String
    abstract val createdAtMs: Long
    abstract val actionType: String

    val compositeKey: String
        get() = "$linkId/$actionId"

    val uniqueKey: String
        get() = "${actionId}_$createdAtMs"

    data class Confirm(
        override val linkId: String,
        override val actionId: String,
        override val guestUid: String,
        override val status: String,
        override val createdAtMs: Long,
        val verificationLevel: String = "PRIVATE_LINK"
    ) : GuestActionV1() {
        override val actionType: String = "CONFIRM"
    }

    data class Dispute(
        override val linkId: String,
        override val actionId: String,
        override val guestUid: String,
        override val status: String,
        override val createdAtMs: Long,
        val reason: String
    ) : GuestActionV1() {
        override val actionType: String = "DISPUTE"
    }

    data class ProposeDate(
        override val linkId: String,
        override val actionId: String,
        override val guestUid: String,
        override val status: String,
        override val createdAtMs: Long,
        val proposedDateMs: Long,
        val reason: String = ""
    ) : GuestActionV1() {
        override val actionType: String = "PROPOSE_DATE"
    }

    data class ClaimPayment(
        override val linkId: String,
        override val actionId: String,
        override val guestUid: String,
        override val status: String,
        override val createdAtMs: Long,
        val amountPaise: Long,
        val paymentMode: PaymentMode,
        val referenceNumber: String,
        val note: String,
        val hasProof: Boolean = false
    ) : GuestActionV1() {
        override val actionType: String = "CLAIM_PAYMENT"
    }

    companion object {
        fun inferActionType(actionId: String): String {
            return when {
                actionId.equals("confirm", ignoreCase = true) -> "CONFIRM"
                actionId.equals("dispute", ignoreCase = true) -> "DISPUTE"
                actionId.startsWith("claim", ignoreCase = true) -> "CLAIM_PAYMENT"
                actionId.startsWith("date", ignoreCase = true) -> "PROPOSE_DATE"
                else -> actionId.uppercase()
            }
        }

        fun fromDocument(doc: DocumentSnapshot): GuestActionV1? {
            val linkId = doc.getString("linkId")
                ?: doc.reference.parent.parent?.id
                ?: return null
            val actionId = doc.id
            val data = doc.data ?: return null
            return fromMap(data, actionId = actionId, linkId = linkId)
        }

        fun fromMap(data: Map<String, Any?>, actionId: String, linkId: String = ""): GuestActionV1? {
            val schemaVersion = (data["schemaVersion"] as? Number)?.toLong() ?: 1L
            if (schemaVersion != 1L) return null

            val guestUid = (data["guestUid"] as? String).orEmpty()
            val status = (data["status"] as? String) ?: "PENDING"
            val createdAtMs = (data["createdAtMs"] as? Number)?.toLong() ?: return null
            if (createdAtMs <= 0L) return null

            val rawType = (data["actionType"] as? String)
                ?: (data["type"] as? String)
                ?: inferActionType(actionId)

            val resolvedLinkId = (data["linkId"] as? String)?.ifBlank { linkId } ?: linkId

            return when (rawType.uppercase()) {
                "CONFIRM" -> {
                    val level = (data["verificationLevel"] as? String) ?: "PRIVATE_LINK"
                    Confirm(
                        linkId = resolvedLinkId,
                        actionId = actionId,
                        guestUid = guestUid,
                        status = status,
                        createdAtMs = createdAtMs,
                        verificationLevel = level
                    )
                }
                "DISPUTE" -> {
                    val reason = (data["reason"] as? String) ?: return null
                    if (reason.length > 500) return null
                    Dispute(
                        linkId = resolvedLinkId,
                        actionId = actionId,
                        guestUid = guestUid,
                        status = status,
                        createdAtMs = createdAtMs,
                        reason = reason
                    )
                }
                "PROPOSE_DATE" -> {
                    val proposedDateMs = (data["proposedDateMs"] as? Number)?.toLong()
                        ?: (data["proposedDueDateMillis"] as? Number)?.toLong()
                        ?: return null
                    if (proposedDateMs <= 0L) return null
                    val reason = (data["reason"] as? String).orEmpty()
                    if (reason.length > 500) return null
                    ProposeDate(
                        linkId = resolvedLinkId,
                        actionId = actionId,
                        guestUid = guestUid,
                        status = status,
                        createdAtMs = createdAtMs,
                        proposedDateMs = proposedDateMs,
                        reason = reason
                    )
                }
                "CLAIM_PAYMENT" -> {
                    val amountPaise = (data["amountPaise"] as? Number)?.toLong()
                        ?: (data["claimedAmountPaise"] as? Number)?.toLong()
                        ?: return null
                    if (amountPaise <= 0L) return null

                    val rawMode = data["paymentMode"] as? String ?: return null
                    val paymentMode = try {
                        PaymentMode.valueOf(rawMode.trim().uppercase())
                    } catch (_: Exception) {
                        return null // Unknown paymentMode -> reject as invalid
                    }

                    val ref = (data["referenceNumber"] as? String)
                        ?: (data["reference"] as? String).orEmpty()
                    if (ref.length > 64) return null

                    val note = (data["note"] as? String)
                        ?: (data["reason"] as? String).orEmpty()
                    if (note.length > 300) return null

                    val hasProof = (data["hasProof"] as? Boolean) ?: false

                    ClaimPayment(
                        linkId = resolvedLinkId,
                        actionId = actionId,
                        guestUid = guestUid,
                        status = status,
                        createdAtMs = createdAtMs,
                        amountPaise = amountPaise,
                        paymentMode = paymentMode,
                        referenceNumber = ref,
                        note = note,
                        hasProof = hasProof
                    )
                }
                else -> null
            }
        }
    }
}
