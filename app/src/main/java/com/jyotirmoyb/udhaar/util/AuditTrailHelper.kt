package com.jyotirmoyb.udhaar.util

import com.jyotirmoyb.udhaar.data.AuditEventType
import com.jyotirmoyb.udhaar.data.AuditTrailEntry
import com.jyotirmoyb.udhaar.data.UdhaarDao
import java.util.UUID

object AuditTrailHelper {
    suspend fun appendLocalAuditEvent(
        dao: UdhaarDao,
        transactionId: Long,
        eventType: AuditEventType,
        actorUserId: String = "",
        actorName: String,
        summaryText: String,
        contentHashSnapshot: String = "",
        isAcknowledged: Boolean = false
    ) {
        dao.insertAuditTrailEntry(
            AuditTrailEntry(
                transactionId = transactionId,
                remoteEventId = "evt_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                eventType = eventType,
                actorUserId = actorUserId,
                actorName = actorName,
                summaryText = summaryText,
                contentHashSnapshot = contentHashSnapshot.ifBlank { "0".repeat(64) },
                isAcknowledged = isAcknowledged,
                eventTimeMillis = System.currentTimeMillis()
            )
        )
    }
}
