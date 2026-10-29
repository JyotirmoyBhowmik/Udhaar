package com.jyotirmoyb.udhaar.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.jyotirmoyb.udhaar.data.CompoundFrequency
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.InterestRatePeriod
import com.jyotirmoyb.udhaar.data.InterestType
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
    val parties: List<PartyEntity>,
    val transactions: List<UdhaarTransaction>,
    val partialPayments: List<PartialPayment>
)

object LedgerBackupManager {

    fun buildBackupJson(
        parties: List<PartyEntity>,
        transactions: List<UdhaarTransaction>,
        partialPayments: List<PartialPayment>,
        exportType: String = "FULL_LEDGER",
        statementPartyName: String? = null
    ): String {
        val root = JSONObject()
        root.put("schemaVersion", 4)
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
                put("upiId", party.upiId)
                put("email", party.email)
                put("tagOrNotes", party.tagOrNotes)
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

        return root.toString(2)
    }

    fun parseBackupJson(jsonString: String): ParsedLedgerBackup {
        val root = JSONObject(jsonString)
        val exportType = root.optString("exportType", "FULL_LEDGER")

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
                    upiId = obj.optString("upiId", ""),
                    email = obj.optString("email", ""),
                    tagOrNotes = obj.optString("tagOrNotes", ""),
                    preferredCurrency = prefCurr,
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

        return ParsedLedgerBackup(
            exportType = exportType,
            parties = parties,
            transactions = transactions,
            partialPayments = partialPayments
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
