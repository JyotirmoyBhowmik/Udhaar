package com.jyotirmoyb.udhaar.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatMessageType
import com.jyotirmoyb.udhaar.data.ChatThreadMetadata
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.CompoundFrequency
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.ExtensionRequestStatus
import com.jyotirmoyb.udhaar.data.InterestRatePeriod
import com.jyotirmoyb.udhaar.data.InterestType
import com.jyotirmoyb.udhaar.data.MessageDeliveryStatus
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.VerificationStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ParsedLedgerBackup(
    val exportType: String,
    val schemaVersion: Int = 7,
    val parties: List<PartyEntity>,
    val transactions: List<UdhaarTransaction>,
    val partialPayments: List<PartialPayment>,
    val chatThreads: List<ChatThreadMetadata> = emptyList(),
    val chatMessages: List<ChatMessageEntity> = emptyList(),
    val settings: Map<String, Any?> = emptyMap(),
    val attachments: Map<String, String> = emptyMap(),
    val attachmentRecords: List<Attachment> = emptyList()
)

object LedgerBackupManager {

    fun buildBackupJson(
        parties: List<PartyEntity>,
        transactions: List<UdhaarTransaction>,
        partialPayments: List<PartialPayment>,
        chatThreads: List<ChatThreadMetadata> = emptyList(),
        chatMessages: List<ChatMessageEntity> = emptyList(),
        settings: Map<String, Any?> = emptyMap(),
        attachments: Map<String, String> = emptyMap(),
        attachmentRecords: List<Attachment> = emptyList(),
        exportType: String = "FULL_LEDGER",
        statementPartyName: String? = null
    ): String {
        val root = JSONObject()
        root.put("schemaVersion", 7)
        root.put("app", "Udhaar")
        root.put("packageName", "com.jyotirmoyb.udhaar")
        root.put("exportType", exportType)
        root.put("exportedAtMillis", System.currentTimeMillis())
        if (!statementPartyName.isNullOrBlank()) {
            root.put("statementPartyName", statementPartyName)
        }

        val partiesArray = JSONArray()
        for (party in parties) {
            val obj = JSONObject().apply {
                put("partyId", party.partyId)
                put("name", party.name)
                put("phoneNumber", party.phoneNumber)
                put("alternatePhoneNumber", party.alternatePhoneNumber)
                put("upiId", party.upiId)
                put("email", party.email)
                put("tagOrNotes", party.tagOrNotes)
                put("notes", party.notes)
                put("photoUri", party.photoUri)
                put("linkedUserId", party.linkedUserId)
                put("isFavorite", party.isFavorite)
                put("lastInteractionMillis", party.lastInteractionMillis)
                put("preferredCurrency", party.preferredCurrency.name)
                put("createdAtMillis", party.createdAtMillis)
            }
            partiesArray.put(obj)
        }
        root.put("parties", partiesArray)

        val txArray = JSONArray()
        for (tx in transactions) {
            val obj = JSONObject().apply {
                put("id", tx.id)
                put("partyId", tx.partyId ?: -1L)
                put("transactionType", tx.transactionType.name)
                put("partyName", tx.partyName)
                put("phoneNumber", tx.phoneNumber)
                put("currency", tx.currency.name)
                put("principalAmount", tx.principalAmount)
                put("amountPaid", tx.amountPaid)
                put("paymentMode", tx.paymentMode.name)
                put("isInterestEnabled", tx.isInterestEnabled)
                put("interestType", tx.interestType.name)
                put("interestRate", tx.interestRate)
                put("interestRatePeriod", tx.interestRatePeriod.name)
                put("compoundFrequency", tx.compoundFrequency.name)
                put("startDateMillis", tx.startDateMillis)
                put("expiryDateMillis", tx.expiryDateMillis ?: -1L)
                put("reminderDateMillis", tx.reminderDateMillis ?: -1L)
                put("notes", tx.notes)
                put("isSettled", tx.isSettled)
                put("settledAtMillis", tx.settledAtMillis ?: -1L)
                put("createdAtMillis", tx.createdAtMillis)
                put("remoteId", tx.remoteId)
                put("verificationStatus", tx.verificationStatus.name)
                put("verifiedAtMillis", tx.verifiedAtMillis ?: -1L)
                put("counterpartyUserId", tx.counterpartyUserId)
                put("contentHash", tx.contentHash)
                put("needsMyConfirmation", tx.needsMyConfirmation)
                put("shareToken", tx.shareToken)
                put("disputeReason", tx.disputeReason)
                put("lenderName", tx.lenderName)
                put("borrowerName", tx.borrowerName)
            }
            txArray.put(obj)
        }
        root.put("transactions", txArray)

        val paymentsArray = JSONArray()
        for (payment in partialPayments) {
            val obj = JSONObject().apply {
                put("id", payment.id)
                put("transactionId", payment.transactionId)
                put("amount", payment.amount)
                put("paymentMode", payment.paymentMode.name)
                put("paidAtMillis", payment.paidAtMillis)
                put("note", payment.note)
                put("remotePaymentId", payment.remotePaymentId)
                put("recordedByUserId", payment.recordedByUserId)
                put("recordedByName", payment.recordedByName)
                put("isAcknowledged", payment.isAcknowledged)
                put("acknowledgedAtMillis", payment.acknowledgedAtMillis ?: -1L)
                put("needsMyAcknowledgement", payment.needsMyAcknowledgement)
            }
            paymentsArray.put(obj)
        }
        root.put("partialPayments", paymentsArray)

        // Phase 4: Chat Threads & Messages
        val threadsArray = JSONArray()
        for (th in chatThreads) {
            val obj = JSONObject().apply {
                put("threadKey", th.threadKey)
                put("remoteThreadId", th.remoteThreadId)
                put("threadScope", th.threadScope.name)
                put("transactionId", th.transactionId ?: -1L)
                put("partyId", th.partyId ?: -1L)
                put("partyName", th.partyName)
                put("isMuted", th.isMuted)
                put("isBlocked", th.isBlocked)
                put("isReported", th.isReported)
                put("reportReason", th.reportReason)
                put("updatedAtMillis", th.updatedAtMillis)
            }
            threadsArray.put(obj)
        }
        root.put("chatThreads", threadsArray)

        val messagesArray = JSONArray()
        for (msg in chatMessages) {
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("remoteMessageId", msg.remoteMessageId)
                put("threadKey", msg.threadKey)
                put("threadScope", msg.threadScope.name)
                put("transactionId", msg.transactionId ?: -1L)
                put("partyId", msg.partyId ?: -1L)
                put("partyName", msg.partyName)
                put("senderUserId", msg.senderUserId)
                put("senderName", msg.senderName)
                put("isFromMe", msg.isFromMe)
                put("messageType", msg.messageType.name)
                put("bodyText", msg.bodyText)
                put("attachmentUri", msg.attachmentUri)
                put("attachmentMimeType", msg.attachmentMimeType)
                put("attachmentName", msg.attachmentName)
                put("deliveryStatus", msg.deliveryStatus.name)
                put("isUnreadForMe", msg.isUnreadForMe)
                put("proposedDueDateMillis", msg.proposedDueDateMillis ?: -1L)
                put("extensionStatus", msg.extensionStatus.name)
                put("sentAtMillis", msg.sentAtMillis)
                put("deliveredAtMillis", msg.deliveredAtMillis ?: -1L)
                put("readAtMillis", msg.readAtMillis ?: -1L)
            }
            messagesArray.put(obj)
        }
        root.put("chatMessages", messagesArray)

        // Phase 5: Settings (excluding PIN)
        if (settings.isNotEmpty()) {
            val settingsObj = JSONObject()
            for ((key, value) in settings) {
                // Security check: NEVER backup PIN or salt
                if (key.contains("pin", ignoreCase = true) || key.contains("salt", ignoreCase = true)) continue
                if (value != null) {
                    settingsObj.put(key, value)
                }
            }
            root.put("settings", settingsObj)
        }

        // Attachments map (base64 payload for backup zip/json)
        if (attachments.isNotEmpty()) {
            val attachmentsObj = JSONObject()
            for ((name, b64) in attachments) {
                attachmentsObj.put(name, b64)
            }
            root.put("attachments", attachmentsObj)
        }

        // Phase 6: Attachment Room database records
        if (attachmentRecords.isNotEmpty()) {
            val attArray = JSONArray()
            for (att in attachmentRecords) {
                val obj = JSONObject().apply {
                    put("id", att.id)
                    put("transactionId", att.transactionId)
                    put("paymentId", att.paymentId ?: -1L)
                    put("fileName", att.fileName)
                    put("mimeType", att.mimeType)
                    put("sizeBytes", att.sizeBytes)
                    put("localPath", att.localPath)
                    put("remoteUrl", att.remoteUrl)
                    put("addedAtMillis", att.addedAtMillis)
                    put("addedBy", att.addedBy)
                    put("sha256", att.sha256)
                }
                attArray.put(obj)
            }
            root.put("attachmentRecords", attArray)
        }

        return root.toString(2)
    }

    fun parseBackupJson(jsonString: String): ParsedLedgerBackup {
        val root = JSONObject(jsonString)
        val exportType = root.optString("exportType", "FULL_LEDGER")
        val schemaVersion = root.optInt("schemaVersion", 1)

        val parties = mutableListOf<PartyEntity>()
        val partiesArray = root.optJSONArray("parties") ?: JSONArray()
        for (i in 0 until partiesArray.length()) {
            val obj = partiesArray.getJSONObject(i)
            val prefCurr = runCatching {
                CurrencyType.valueOf(obj.optString("preferredCurrency", "INR"))
            }.getOrDefault(CurrencyType.INR)
            parties.add(
                PartyEntity(
                    partyId = obj.optLong("partyId", 0L),
                    name = obj.optString("name", ""),
                    phoneNumber = obj.optString("phoneNumber", ""),
                    alternatePhoneNumber = obj.optString("alternatePhoneNumber", ""),
                    upiId = obj.optString("upiId", ""),
                    email = obj.optString("email", ""),
                    tagOrNotes = obj.optString("tagOrNotes", ""),
                    notes = obj.optString("notes", ""),
                    photoUri = obj.optString("photoUri", ""),
                    linkedUserId = obj.optString("linkedUserId", ""),
                    isFavorite = obj.optBoolean("isFavorite", false),
                    preferredCurrency = prefCurr,
                    lastInteractionMillis = obj.optLong("lastInteractionMillis", obj.optLong("createdAtMillis", System.currentTimeMillis())),
                    createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
                )
            )
        }

        val transactions = mutableListOf<UdhaarTransaction>()
        val txArray = root.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until txArray.length()) {
            val obj = txArray.getJSONObject(i)
            val partyIdRaw = obj.optLong("partyId", -1L)
            val expiryRaw = obj.optLong("expiryDateMillis", -1L)
            val reminderRaw = obj.optLong("reminderDateMillis", -1L)
            val settledRaw = obj.optLong("settledAtMillis", -1L)
            val verifiedRaw = obj.optLong("verifiedAtMillis", -1L)

            transactions.add(
                UdhaarTransaction(
                    id = obj.optLong("id", 0L),
                    partyId = partyIdRaw.takeIf { it > 0L },
                    transactionType = runCatching {
                        TransactionType.valueOf(obj.optString("transactionType", "GIVEN"))
                    }.getOrDefault(TransactionType.GIVEN),
                    partyName = obj.optString("partyName", ""),
                    phoneNumber = obj.optString("phoneNumber", ""),
                    currency = runCatching {
                        CurrencyType.valueOf(obj.optString("currency", "INR"))
                    }.getOrDefault(CurrencyType.INR),
                    principalAmount = obj.optDouble("principalAmount", 0.0),
                    amountPaid = obj.optDouble("amountPaid", 0.0),
                    paymentMode = runCatching {
                        PaymentMode.valueOf(obj.optString("paymentMode", "CASH"))
                    }.getOrDefault(PaymentMode.CASH),
                    isInterestEnabled = obj.optBoolean("isInterestEnabled", false),
                    interestType = runCatching {
                        InterestType.valueOf(obj.optString("interestType", "SIMPLE"))
                    }.getOrDefault(InterestType.SIMPLE),
                    interestRate = obj.optDouble("interestRate", 0.0),
                    interestRatePeriod = runCatching {
                        InterestRatePeriod.valueOf(obj.optString("interestRatePeriod", "PER_MONTH"))
                    }.getOrDefault(InterestRatePeriod.PER_MONTH),
                    compoundFrequency = runCatching {
                        CompoundFrequency.valueOf(obj.optString("compoundFrequency", "MONTHLY"))
                    }.getOrDefault(CompoundFrequency.MONTHLY),
                    startDateMillis = obj.optLong("startDateMillis", System.currentTimeMillis()),
                    expiryDateMillis = expiryRaw.takeIf { it > 0L },
                    reminderDateMillis = reminderRaw.takeIf { it > 0L },
                    notes = obj.optString("notes", ""),
                    isSettled = obj.optBoolean("isSettled", false),
                    settledAtMillis = settledRaw.takeIf { it > 0L },
                    createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
                    remoteId = obj.optString("remoteId", ""),
                    verificationStatus = runCatching {
                        VerificationStatus.valueOf(obj.optString("verificationStatus", "LOCAL_ONLY"))
                    }.getOrDefault(VerificationStatus.LOCAL_ONLY),
                    verifiedAtMillis = verifiedRaw.takeIf { it > 0L },
                    counterpartyUserId = obj.optString("counterpartyUserId", ""),
                    contentHash = obj.optString("contentHash", ""),
                    needsMyConfirmation = obj.optBoolean("needsMyConfirmation", false),
                    shareToken = obj.optString("shareToken", ""),
                    disputeReason = obj.optString("disputeReason", ""),
                    lenderName = obj.optString("lenderName", ""),
                    borrowerName = obj.optString("borrowerName", "")
                )
            )
        }

        val partialPayments = mutableListOf<PartialPayment>()
        val paymentsArray = root.optJSONArray("partialPayments") ?: JSONArray()
        for (i in 0 until paymentsArray.length()) {
            val obj = paymentsArray.getJSONObject(i)
            val mode = runCatching {
                PaymentMode.valueOf(obj.optString("paymentMode", "CASH"))
            }.getOrDefault(PaymentMode.CASH)
            val ackAtRaw = obj.optLong("acknowledgedAtMillis", -1L)
            partialPayments.add(
                PartialPayment(
                    id = obj.optLong("id", 0L),
                    transactionId = obj.optLong("transactionId", 0L),
                    amount = obj.optDouble("amount", 0.0),
                    paymentMode = mode,
                    paidAtMillis = obj.optLong("paidAtMillis", System.currentTimeMillis()),
                    note = obj.optString("note", ""),
                    remotePaymentId = obj.optString("remotePaymentId", ""),
                    recordedByUserId = obj.optString("recordedByUserId", ""),
                    recordedByName = obj.optString("recordedByName", ""),
                    isAcknowledged = obj.optBoolean("isAcknowledged", false),
                    acknowledgedAtMillis = ackAtRaw.takeIf { it > 0L },
                    needsMyAcknowledgement = obj.optBoolean("needsMyAcknowledgement", false)
                )
            )
        }

        val chatThreads = mutableListOf<ChatThreadMetadata>()
        val threadsArray = root.optJSONArray("chatThreads") ?: JSONArray()
        for (i in 0 until threadsArray.length()) {
            val obj = threadsArray.getJSONObject(i)
            val txId = obj.optLong("transactionId", -1L).takeIf { it > 0L }
            val partyId = obj.optLong("partyId", -1L).takeIf { it > 0L }
            chatThreads.add(
                ChatThreadMetadata(
                    threadKey = obj.optString("threadKey"),
                    remoteThreadId = obj.optString("remoteThreadId"),
                    threadScope = runCatching {
                        ChatThreadScope.valueOf(obj.optString("threadScope", "TRANSACTION"))
                    }.getOrDefault(ChatThreadScope.TRANSACTION),
                    transactionId = txId,
                    partyId = partyId,
                    partyName = obj.optString("partyName"),
                    isMuted = obj.optBoolean("isMuted", false),
                    isBlocked = obj.optBoolean("isBlocked", false),
                    isReported = obj.optBoolean("isReported", false),
                    reportReason = obj.optString("reportReason"),
                    updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis())
                )
            )
        }

        val chatMessages = mutableListOf<ChatMessageEntity>()
        val messagesArray = root.optJSONArray("chatMessages") ?: JSONArray()
        for (i in 0 until messagesArray.length()) {
            val obj = messagesArray.getJSONObject(i)
            val txId = obj.optLong("transactionId", -1L).takeIf { it > 0L }
            val partyId = obj.optLong("partyId", -1L).takeIf { it > 0L }
            val extStatus = runCatching {
                ExtensionRequestStatus.valueOf(obj.optString("extensionStatus", "NONE"))
            }.getOrDefault(ExtensionRequestStatus.NONE)
            val delivStatus = runCatching {
                MessageDeliveryStatus.valueOf(obj.optString("deliveryStatus", "SENT"))
            }.getOrDefault(MessageDeliveryStatus.SENT)
            val msgType = runCatching {
                ChatMessageType.valueOf(obj.optString("messageType", "TEXT"))
            }.getOrDefault(ChatMessageType.TEXT)

            chatMessages.add(
                ChatMessageEntity(
                    id = obj.optLong("id", 0L),
                    remoteMessageId = obj.optString("remoteMessageId"),
                    threadKey = obj.optString("threadKey"),
                    threadScope = runCatching {
                        ChatThreadScope.valueOf(obj.optString("threadScope", "TRANSACTION"))
                    }.getOrDefault(ChatThreadScope.TRANSACTION),
                    transactionId = txId,
                    partyId = partyId,
                    partyName = obj.optString("partyName"),
                    senderUserId = obj.optString("senderUserId"),
                    senderName = obj.optString("senderName", "You"),
                    isFromMe = obj.optBoolean("isFromMe", true),
                    messageType = msgType,
                    bodyText = obj.optString("bodyText"),
                    attachmentUri = obj.optString("attachmentUri"),
                    attachmentMimeType = obj.optString("attachmentMimeType"),
                    attachmentName = obj.optString("attachmentName"),
                    deliveryStatus = delivStatus,
                    isUnreadForMe = obj.optBoolean("isUnreadForMe", false),
                    proposedDueDateMillis = obj.optLong("proposedDueDateMillis", -1L).takeIf { it > 0L },
                    extensionStatus = extStatus,
                    sentAtMillis = obj.optLong("sentAtMillis", System.currentTimeMillis()),
                    deliveredAtMillis = obj.optLong("deliveredAtMillis", -1L).takeIf { it > 0L },
                    readAtMillis = obj.optLong("readAtMillis", -1L).takeIf { it > 0L }
                )
            )
        }

        val settings = mutableMapOf<String, Any?>()
        val settingsObj = root.optJSONObject("settings")
        if (settingsObj != null) {
            val keys = settingsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k.contains("pin", ignoreCase = true) || k.contains("salt", ignoreCase = true)) continue
                settings[k] = settingsObj.opt(k)
            }
        }

        val attachments = mutableMapOf<String, String>()
        val attachmentsObj = root.optJSONObject("attachments")
        if (attachmentsObj != null) {
            val keys = attachmentsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                attachments[k] = attachmentsObj.optString(k)
            }
        }

        val attachmentRecords = mutableListOf<Attachment>()
        val attRecordsArray = root.optJSONArray("attachmentRecords") ?: JSONArray()
        for (i in 0 until attRecordsArray.length()) {
            val obj = attRecordsArray.getJSONObject(i)
            val paymentIdRaw = obj.optLong("paymentId", -1L).takeIf { it > 0L }
            attachmentRecords.add(
                Attachment(
                    id = obj.optLong("id", 0L),
                    transactionId = obj.optLong("transactionId", 0L),
                    paymentId = paymentIdRaw,
                    fileName = obj.optString("fileName", ""),
                    mimeType = obj.optString("mimeType", "application/octet-stream"),
                    sizeBytes = obj.optLong("sizeBytes", 0L),
                    localPath = obj.optString("localPath", ""),
                    remoteUrl = obj.optString("remoteUrl", ""),
                    addedAtMillis = obj.optLong("addedAtMillis", System.currentTimeMillis()),
                    addedBy = obj.optString("addedBy", "You"),
                    sha256 = obj.optString("sha256", "")
                )
            )
        }

        return ParsedLedgerBackup(
            exportType = exportType,
            schemaVersion = schemaVersion,
            parties = parties,
            transactions = transactions,
            partialPayments = partialPayments,
            chatThreads = chatThreads,
            chatMessages = chatMessages,
            settings = settings,
            attachments = attachments,
            attachmentRecords = attachmentRecords
        )
    }

    fun shareBackupFile(context: Context, jsonContent: String, fileNamePrefix: String = "udhaar_ledger_backup") {
        runCatching {
            val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
            val file = File(backupDir, "${fileNamePrefix}_${System.currentTimeMillis()}.json")
            file.writeText(jsonContent)
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Udhaar Offline Ledger Backup JSON attached. Open Udhaar -> Settings -> Import Ledger Backup (JSON) to restore."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Udhaar Ledger Backup").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}
