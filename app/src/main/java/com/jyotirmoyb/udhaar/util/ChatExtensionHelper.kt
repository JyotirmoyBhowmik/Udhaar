package com.jyotirmoyb.udhaar.util

import android.content.Context
import com.jyotirmoyb.udhaar.data.AuditEventType
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatMessageType
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.ExtensionRequestStatus
import com.jyotirmoyb.udhaar.data.MessageDeliveryStatus
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.cloud.CloudAuditEvent
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.data.repository.ChatRepository
import com.jyotirmoyb.udhaar.data.repository.SettingsRepository
import com.jyotirmoyb.udhaar.notification.ChatNotificationHelper
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object ChatExtensionHelper {

    suspend fun requestDueDateExtension(
        dao: UdhaarDao,
        settingsRepository: SettingsRepository,
        cloudRepository: VerificationCloudRepository?,
        context: Context?,
        chatRepository: ChatRepository,
        transaction: UdhaarTransaction,
        proposedDueDateMillis: Long,
        note: String = "",
        reasonText: String = "",
        requestedByCounterparty: Boolean = false
    ): ChatMessageEntity {
        val effectiveNote = note.ifBlank { reasonText }
        val threadKey = ChatSyncHelper.transactionThreadKey(transaction.id)
        val meta = chatRepository.getOrCreateThreadMetadata(
            threadKey = threadKey,
            scope = ChatThreadScope.TRANSACTION,
            transactionId = transaction.id,
            remoteId = transaction.remoteId,
            partyId = transaction.partyId,
            partyName = transaction.partyName
        )
        val settings = settingsRepository.userSettings.first()
        val myName = settings.payeeName.ifBlank { "You" }
        val nowMillis = System.currentTimeMillis()
        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(proposedDueDateMillis))

        val senderName = if (requestedByCounterparty) transaction.partyName else myName
        val senderUid = if (requestedByCounterparty) {
            transaction.counterpartyUserId.ifBlank { "counterparty" }
        } else {
            settings.cloudUserId.ifBlank { "local_me" }
        }

        val textBody = buildString {
            append("$senderName requested due date extension to $dateFmt")
            if (effectiveNote.isNotBlank()) append(" — \"$effectiveNote\"")
        }

        val extMsg = ChatMessageEntity(
            remoteMessageId = "ext_${UUID.randomUUID().toString().replace("-", "").take(16)}",
            threadKey = threadKey,
            threadScope = ChatThreadScope.TRANSACTION,
            transactionId = transaction.id,
            partyId = transaction.partyId,
            partyName = transaction.partyName,
            senderUserId = senderUid,
            senderName = senderName,
            isFromMe = !requestedByCounterparty,
            messageType = ChatMessageType.EXTENSION_REQUEST,
            bodyText = textBody,
            proposedDueDateMillis = proposedDueDateMillis,
            extensionStatus = ExtensionRequestStatus.PENDING,
            deliveryStatus = MessageDeliveryStatus.DELIVERED,
            isUnreadForMe = requestedByCounterparty,
            sentAtMillis = nowMillis,
            deliveredAtMillis = nowMillis,
            readAtMillis = null
        )

        val id = dao.insertChatMessage(extMsg)
        val saved = extMsg.copy(id = id)

        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = transaction.id,
            eventType = AuditEventType.EXTENSION_REQUESTED,
            actorUserId = senderUid,
            actorName = senderName,
            summaryText = "$senderName requested due date extension to $dateFmt" +
                if (effectiveNote.isNotBlank()) " (\"$effectiveNote\")" else "",
            contentHashSnapshot = transaction.contentHash,
            isAcknowledged = false
        )

        val updatedMeta = meta.copy(updatedAtMillis = nowMillis)
        dao.upsertChatThreadMetadata(updatedMeta)

        if (requestedByCounterparty && context != null) {
            ChatNotificationHelper.showIncomingChatNotification(
                context = context,
                threadMetadata = updatedMeta,
                message = saved
            )
        }

        ChatSyncHelper.syncChatThreadAndMessageToCloudIfEnabled(
            meta = updatedMeta,
            message = saved,
            settings = settings,
            counterpartyUserId = transaction.counterpartyUserId,
            cloudRepository = cloudRepository
        )

        return saved
    }

    suspend fun respondToExtensionRequest(
        dao: UdhaarDao,
        settingsRepository: SettingsRepository,
        cloudRepository: VerificationCloudRepository?,
        chatRepository: ChatRepository,
        transaction: UdhaarTransaction? = null,
        message: ChatMessageEntity,
        accept: Boolean,
        responderName: String = "You"
    ): UdhaarTransaction {
        val tx = transaction ?: message.transactionId?.let { dao.getTransactionById(it) }
            ?: throw IllegalArgumentException("Transaction not found for extension request")
        val nowMillis = System.currentTimeMillis()
        val newStatus = if (accept) ExtensionRequestStatus.ACCEPTED else ExtensionRequestStatus.DECLINED

        val updatedMessage = message.copy(
            extensionStatus = newStatus,
            isUnreadForMe = false
        )
        dao.updateChatMessage(updatedMessage)

        val updatedTx = if (accept && message.proposedDueDateMillis != null) {
            val txWithNewDate = tx.copy(
                expiryDateMillis = message.proposedDueDateMillis
            )
            val recomputedHash = VerificationCryptoHelper.computeContentHash(
                lenderName = txWithNewDate.lenderName,
                borrowerName = txWithNewDate.borrowerName,
                principalAmount = txWithNewDate.principalAmount,
                currencyCode = txWithNewDate.currency.code,
                startDateMillis = txWithNewDate.startDateMillis,
                expiryDateMillis = txWithNewDate.expiryDateMillis,
                isInterestEnabled = txWithNewDate.isInterestEnabled,
                interestType = txWithNewDate.interestType,
                interestRate = txWithNewDate.interestRate,
                interestRatePeriod = txWithNewDate.interestRatePeriod,
                compoundFrequency = txWithNewDate.compoundFrequency
            )
            val finalTx = txWithNewDate.copy(contentHash = recomputedHash)
            dao.updateTransaction(finalTx)
            finalTx
        } else {
            tx
        }

        val dateFmt = message.proposedDueDateMillis?.let {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it))
        } ?: "proposed date"

        val settings = settingsRepository.userSettings.first()
        val myName = responderName.trim().ifEmpty { settings.payeeName.ifBlank { "You" } }
        val auditSummary = if (accept) {
            "$myName accepted due date extension to $dateFmt"
        } else {
            "$myName rejected extension request for $dateFmt"
        }

        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = tx.id,
            eventType = if (accept) AuditEventType.EXTENSION_ACCEPTED else AuditEventType.EXTENSION_DECLINED,
            actorUserId = settings.cloudUserId,
            actorName = myName,
            summaryText = auditSummary,
            contentHashSnapshot = updatedTx.contentHash,
            isAcknowledged = accept
        )

        chatRepository.sendChatMessage(
            threadKey = message.threadKey,
            scope = message.threadScope,
            transaction = updatedTx,
            partyName = tx.partyName,
            messageType = ChatMessageType.TEXT,
            textContent = auditSummary,
            fromCounterparty = false
        )

        if (settings.cloudVerificationConsent && settings.cloudUserId.isNotBlank() && tx.remoteId.isNotBlank()) {
            runCatching {
                val safeRemoteId = tx.remoteId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                if (accept && message.proposedDueDateMillis != null) {
                    cloudRepository?.updateSharedRecordTermsOrStatus(
                        remoteId = safeRemoteId,
                        updates = mapOf(
                            "expiryDateMillis" to message.proposedDueDateMillis,
                            "contentHash" to updatedTx.contentHash
                        )
                    )
                }
                val cloudThreadId = ChatSyncHelper.cloudThreadIdFor(message.threadScope, safeRemoteId, tx.partyName, message.threadKey)
                if (message.remoteMessageId.isNotBlank()) {
                    cloudRepository?.updateChatMessageDeliveryOrExtension(
                        threadId = cloudThreadId,
                        messageId = message.remoteMessageId,
                        deliveryStatus = MessageDeliveryStatus.READ.name,
                        deliveredAtMillis = message.deliveredAtMillis ?: nowMillis,
                        readAtMillis = nowMillis,
                        extensionStatus = newStatus.name
                    )
                    val participants = listOfNotNull(
                        settings.cloudUserId.takeIf { it.isNotBlank() },
                        tx.counterpartyUserId.takeIf { it.isNotBlank() }
                    ).distinct()
                    cloudRepository?.appendAuditEvent(
                        CloudAuditEvent(
                            eventId = "evt_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                            remoteId = safeRemoteId,
                            eventType = if (accept) AuditEventType.EXTENSION_ACCEPTED.name else AuditEventType.EXTENSION_DECLINED.name,
                            actorUid = settings.cloudUserId,
                            actorName = myName,
                            participantUids = participants,
                            summaryText = auditSummary,
                            contentHashSnapshot = updatedTx.contentHash,
                            acknowledged = accept,
                            eventTimeMillis = nowMillis
                        )
                    )
                }
            }
        }

        return updatedTx
    }
}
