package com.jyotirmoyb.udhaar.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.jyotirmoyb.udhaar.BuildConfig
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.ui.PartyKhataSummary
import java.io.File
import java.io.FileOutputStream

/**
 * Enhancement B: Thermal-Style Monochrome Statement Receipt Generator (Image & PDF Share)
 * Renders high-contrast #000000 on #FFFFFF thermal receipts offline with Party Name, Currency (INR ₹ / NPR रू),
 * Principal, Interest Rate & Breakdown, Partial Repayments Table, Net Balance Due, UPI QR code (if applicable),
 * and Timestamp, supporting localized strings and amount privacy masking.
 */
object ThermalReceiptGenerator {

    data class ThermalReceiptStrings(
        val txHeaderTitle: String = "UDHAAR • THERMAL STATEMENT",
        val partyHeaderTitle: String = "UDHAAR • PARTY KHATA RECEIPT",
        val receiptNumberPrefix: String = "RECEIPT #",
        val generatedPrefix: String = "GENERATED:",
        val partyNameLabel: String = "PARTY NAME",
        val partyAccountLabel: String = "PARTY / ACCOUNT",
        val contactPhoneLabel: String = "CONTACT PHONE",
        val partyUpiVpaLabel: String = "PARTY UPI VPA",
        val totalRecordsLabel: String = "TOTAL RECORDS",
        val activeSuffix: String = "ACTIVE",
        val recordTypeLabel: String = "RECORD TYPE",
        val givenToReceiveValue: String = "GIVEN (TO RECEIVE)",
        val takenToPayValue: String = "TAKEN (TO PAY)",
        val verificationLabel: String = "VERIFICATION",
        val contentShaLabel: String = "CONTENT SHA-256",
        val currencyModeLabel: String = "CURRENCY & MODE",
        val startDateLabel: String = "START DATE",
        val dueStatusLabel: String = "DUE / STATUS",
        val financialBreakdownTitle: String = "FINANCIAL BREAKDOWN",
        val principalAmountLabel: String = "PRINCIPAL AMOUNT",
        val interestZeroLabel: String = "INTEREST (0% • INTEREST FREE)",
        val interestPrefix: String = "INTEREST",
        val grossTotalLabel: String = "GROSS TOTAL",
        val repaidSoFarLabel: String = "REPAID SO FAR",
        val partialRepaymentsTitle: String = "PARTIAL REPAYMENTS AUDIT",
        val noPartialInstallmentsText: String = "No partial installments recorded yet.",
        val immutableAuditTimelineTitle: String = "IMMUTABLE AUDIT TIMELINE",
        val recordCreatedText: String = "RECORD CREATED",
        val finalStatusSettledText: String = "FINAL STATUS: SETTLED",
        val netBalanceDuePrefix: String = "NET BALANCE DUE",
        val scanToSettleUpiPrefix: String = "SCAN TO SETTLE VIA UPI:",
        val netLedgerPositionTitle: String = "NET LEDGER POSITION",
        val itemizedLoanRecordsTitle: String = "ITEMIZED LOAN RECORDS",
        val noActiveOrSettledRecordsText: String = "No active or settled records.",
        val scanToSettleNetInrPrefix: String = "SCAN TO SETTLE NET INR BALANCE",
        val generatedOfflineFooterPrefix: String = "GENERATED OFFLINE BY UDHAAR"
    ) {
        companion object {
            fun fromContext(context: Context): ThermalReceiptStrings {
                return ThermalReceiptStrings(
                    txHeaderTitle = context.getString(R.string.receipt_tx_header_title),
                    partyHeaderTitle = context.getString(R.string.receipt_party_header_title),
                    receiptNumberPrefix = context.getString(R.string.receipt_number_prefix),
                    generatedPrefix = context.getString(R.string.receipt_generated_prefix),
                    partyNameLabel = context.getString(R.string.receipt_party_name_label),
                    partyAccountLabel = context.getString(R.string.receipt_party_account_label),
                    contactPhoneLabel = context.getString(R.string.receipt_contact_phone_label),
                    partyUpiVpaLabel = context.getString(R.string.receipt_party_upi_vpa_label),
                    totalRecordsLabel = context.getString(R.string.receipt_total_records_label),
                    activeSuffix = context.getString(R.string.receipt_active_suffix),
                    recordTypeLabel = context.getString(R.string.receipt_record_type_label),
                    givenToReceiveValue = context.getString(R.string.receipt_given_to_receive),
                    takenToPayValue = context.getString(R.string.receipt_taken_to_pay),
                    verificationLabel = context.getString(R.string.receipt_verification_label),
                    contentShaLabel = context.getString(R.string.receipt_content_sha_label),
                    currencyModeLabel = context.getString(R.string.receipt_currency_mode_label),
                    startDateLabel = context.getString(R.string.receipt_start_date_label),
                    dueStatusLabel = context.getString(R.string.receipt_due_status_label),
                    financialBreakdownTitle = context.getString(R.string.receipt_financial_breakdown_title),
                    principalAmountLabel = context.getString(R.string.receipt_principal_amount_label),
                    interestZeroLabel = context.getString(R.string.receipt_interest_zero_label),
                    interestPrefix = context.getString(R.string.receipt_interest_prefix),
                    grossTotalLabel = context.getString(R.string.receipt_gross_total_label),
                    repaidSoFarLabel = context.getString(R.string.receipt_repaid_so_far_label),
                    partialRepaymentsTitle = context.getString(R.string.receipt_partial_repayments_title),
                    noPartialInstallmentsText = context.getString(R.string.receipt_no_partial_installments),
                    immutableAuditTimelineTitle = context.getString(R.string.receipt_immutable_audit_timeline_title),
                    recordCreatedText = context.getString(R.string.receipt_record_created),
                    finalStatusSettledText = context.getString(R.string.receipt_final_status_settled),
                    netBalanceDuePrefix = context.getString(R.string.receipt_net_balance_due_prefix),
                    scanToSettleUpiPrefix = context.getString(R.string.receipt_scan_to_settle_upi_prefix),
                    netLedgerPositionTitle = context.getString(R.string.receipt_net_ledger_position_title),
                    itemizedLoanRecordsTitle = context.getString(R.string.receipt_itemized_loan_records_title),
                    noActiveOrSettledRecordsText = context.getString(R.string.receipt_no_active_or_settled_records),
                    scanToSettleNetInrPrefix = context.getString(R.string.receipt_scan_to_settle_net_inr_prefix),
                    generatedOfflineFooterPrefix = context.getString(R.string.receipt_generated_offline_footer_prefix)
                )
            }
        }
    }

    fun createTransactionReceiptBitmap(
        summary: CalculatedUdhaarSummary,
        partialPayments: List<PartialPayment>,
        userSettings: UserSettings,
        partyUpiId: String? = null,
        auditTrail: List<com.jyotirmoyb.udhaar.data.AuditTrailEntry> = emptyList(),
        hideAmounts: Boolean = false,
        strings: ThermalReceiptStrings = ThermalReceiptStrings()
    ): Bitmap {
        val tx = summary.transaction
        val currency = tx.currency
        val isGiven = tx.transactionType == TransactionType.GIVEN

        // Determine if a UPI QR should be rendered on the receipt
        val targetUpiId = when {
            currency == CurrencyType.INR && isGiven && !tx.isSettled && userSettings.upiId.isNotBlank() ->
                userSettings.upiId.trim()
            currency == CurrencyType.INR && !isGiven && !tx.isSettled && !partyUpiId.isNullOrBlank() ->
                partyUpiId.trim()
            else -> ""
        }
        val targetPayee = if (isGiven) {
            userSettings.payeeName.ifBlank { "Udhaar Account" }
        } else {
            tx.partyName
        }

        val qrBitmap = if (targetUpiId.isNotEmpty() && summary.netBalanceDue > 0.0) {
            val uri = UpiQrGenerator.buildUpiUri(
                upiId = targetUpiId,
                payeeName = targetPayee,
                amountInr = if (hideAmounts) null else summary.netBalanceDue,
                transactionNote = "Udhaar #${tx.id} - ${tx.partyName}"
            )
            UpiQrGenerator.generateQrBitmap(uri, sizePx = 300)
        } else {
            null
        }

        val auditItems = auditTrail.sortedBy { it.eventTimeMillis }.takeLast(8)
        val width = 840
        val paymentRowsHeight = if (partialPayments.isEmpty()) 56 else (partialPayments.size.coerceAtMost(12) * 44 + 74)
        val auditRowsHeight = if (auditItems.isEmpty()) 110 else (auditItems.size * 44 + 140)
        val qrSectionHeight = if (qrBitmap != null) 410 else 0
        val height = 1140 + paymentRowsHeight + auditRowsHeight + qrSectionHeight

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRect(20f, 20f, width - 20f, height - 20f, borderPaint)

        val fillBlack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }

        val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val headerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textSize = 21f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 25f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        // Top Banner
        canvas.drawRect(20f, 20f, width - 20f, 160f, fillBlack)
        canvas.drawText(strings.txHeaderTitle, width / 2f, 82f, headerTitlePaint)
        canvas.drawText(
            "${strings.receiptNumberPrefix}${tx.id}  •  ${InterestCalculator.formatDateTime(System.currentTimeMillis())}",
            width / 2f,
            126f,
            headerSubPaint
        )

        var y = 215f
        val leftX = 56f
        val rightX = width - 56f

        fun drawRow(label: String, value: String, boldLabel: Boolean = false) {
            labelPaint.typeface = Typeface.create(
                Typeface.MONOSPACE,
                if (boldLabel) Typeface.BOLD else Typeface.NORMAL
            )
            labelPaint.color = if (boldLabel) Color.BLACK else Color.DKGRAY
            canvas.drawText(label, leftX, y, labelPaint)
            canvas.drawText(value, rightX, y, valuePaint)
            y += 46f
        }

        fun drawDashedDivider() {
            y += 6f
            canvas.drawLine(leftX, y, rightX, y, dashedPaint)
            y += 38f
        }

        // Party & Record Metadata
        drawRow(strings.partyNameLabel, tx.partyName.uppercase(), boldLabel = true)
        if (tx.phoneNumber.isNotBlank()) {
            drawRow(strings.contactPhoneLabel, InterestCalculator.maskPhoneNumber(tx.phoneNumber))
        }
        drawRow(
            strings.recordTypeLabel,
            if (isGiven) strings.givenToReceiveValue else strings.takenToPayValue
        )
        drawRow(strings.verificationLabel, tx.verificationStatus.shortBadge, boldLabel = true)
        val effectiveHash = tx.contentHash.ifBlank {
            VerificationCryptoHelper.computeTransactionContentHash(tx)
        }
        drawRow(strings.contentShaLabel, "${effectiveHash.take(12)}…${effectiveHash.takeLast(6)}")
        drawRow(strings.currencyModeLabel, "${currency.displayName} • ${tx.paymentMode.label.uppercase()}")
        drawRow(strings.startDateLabel, InterestCalculator.formatDate(tx.startDateMillis))
        drawRow(
            strings.dueStatusLabel,
            tx.expiryDateMillis?.let { InterestCalculator.formatDate(it) } ?: summary.dueStatusText
        )

        drawDashedDivider()

        // Financial Breakdown
        canvas.drawText(strings.financialBreakdownTitle, leftX, y, sectionTitlePaint)
        y += 44f
        drawRow(
            strings.principalAmountLabel,
            InterestCalculator.formatCurrencyPrivacy(summary.principal, currency, hideAmounts)
        )
        val interestDesc = if (tx.isInterestEnabled && tx.interestRate > 0.0) {
            val typeLbl = if (tx.interestType.name == "SIMPLE") "Simple" else "Comp.${tx.compoundFrequency.label}"
            "${strings.interestPrefix} (${InterestCalculator.formatRate(tx.interestRate)}${tx.interestRatePeriod.shortSuffix} $typeLbl, ${summary.elapsedDays}d)"
        } else {
            strings.interestZeroLabel
        }
        drawRow(
            interestDesc,
            "+ ${InterestCalculator.formatCurrencyPrivacy(summary.accruedInterest, currency, hideAmounts)}"
        )
        drawRow(
            strings.grossTotalLabel,
            InterestCalculator.formatCurrencyPrivacy(summary.grossTotal, currency, hideAmounts),
            boldLabel = true
        )
        drawRow(
            strings.repaidSoFarLabel,
            "- ${InterestCalculator.formatCurrencyPrivacy(summary.amountPaid, currency, hideAmounts)}"
        )

        drawDashedDivider()

        // Partial Repayments Audit Trail Table
        canvas.drawText("${strings.partialRepaymentsTitle} (${partialPayments.size})", leftX, y, sectionTitlePaint)
        y += 42f
        if (partialPayments.isEmpty()) {
            canvas.drawText(strings.noPartialInstallmentsText, leftX, y, labelPaint)
            y += 44f
        } else {
            val chronological = partialPayments.sortedBy { it.paidAtMillis }.takeLast(12)
            chronological.forEachIndexed { idx, p ->
                val dateStr = InterestCalculator.fmtDate(p.paidAtMillis)
                val ackTag = if (p.isAcknowledged) "✓ACK" else "⏳PEND"
                val byPart = if (p.recordedByName.isNotBlank()) " by ${p.recordedByName.take(10)}" else ""
                val leftCol = "${idx + 1}. $dateStr [${p.paymentMode.shortBadge}•$ackTag]$byPart"
                drawRow(
                    leftCol,
                    "- ${InterestCalculator.formatCurrencyPrivacy(p.amount, currency, hideAmounts)}"
                )
            }
        }

        drawDashedDivider()

        // Immutable Verification & Audit Timeline Section
        canvas.drawText("${strings.immutableAuditTimelineTitle} (${auditItems.size})", leftX, y, sectionTitlePaint)
        y += 42f
        if (auditItems.isEmpty()) {
            val createdStr = InterestCalculator.fmtDate(tx.createdAtMillis)
            drawRow("1. $createdStr • ${strings.recordCreatedText}", tx.verificationStatus.shortBadge)
        } else {
            auditItems.forEachIndexed { idx, ev ->
                val dStr = InterestCalculator.fmtDate(ev.eventTimeMillis)
                val actor = ev.actorName.ifBlank { "Party" }.take(12)
                drawRow(
                    "${idx + 1}. $dStr • ${ev.eventType.label.take(22)}",
                    "$actor [${ev.contentHashSnapshot.take(6)}]"
                )
            }
        }

        drawDashedDivider()

        // Net Balance Highlight Box
        val netBoxTop = y - 12f
        val netBoxBottom = netBoxTop + 130f
        canvas.drawRect(leftX, netBoxTop, rightX, netBoxBottom, fillBlack)

        val netLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val netValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            if (tx.isSettled) strings.finalStatusSettledText else "${strings.netBalanceDuePrefix} (${currency.code})",
            leftX + 24f,
            netBoxTop + 48f,
            netLabelPaint
        )
        canvas.drawText(
            InterestCalculator.formatCurrencyPrivacy(summary.netBalanceDue, currency, hideAmounts),
            rightX - 24f,
            netBoxTop + 98f,
            netValuePaint
        )

        y = netBoxBottom + 48f

        // Optional UPI QR Section
        if (qrBitmap != null) {
            val qrLeft = (width - qrBitmap.width) / 2f
            canvas.drawBitmap(qrBitmap, qrLeft, y, null)
            y += qrBitmap.height + 36f
            val qrCaptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 22f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("${strings.scanToSettleUpiPrefix} ${InterestCalculator.maskUpiId(targetUpiId)}", width / 2f, y, qrCaptionPaint)
            y += 38f
        }

        // Footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "${strings.generatedOfflineFooterPrefix} v${BuildConfig.VERSION_NAME} • ${BuildConfig.APPLICATION_ID}",
            width / 2f,
            height - 42f,
            footerPaint
        )

        return bitmap
    }

    fun createPartyStatementReceiptBitmap(
        khata: PartyKhataSummary,
        enabledCurrencies: List<CurrencyType>,
        userSettings: UserSettings,
        hideAmounts: Boolean = false,
        strings: ThermalReceiptStrings = ThermalReceiptStrings()
    ): Bitmap {
        val party = khata.party
        val inrEnabled = CurrencyType.INR in enabledCurrencies
        val nprEnabled = CurrencyType.NPR in enabledCurrencies

        val targetUpiId = when {
            inrEnabled && khata.inrNet > 0.01 && userSettings.upiId.isNotBlank() -> userSettings.upiId.trim()
            inrEnabled && khata.inrNet < -0.01 && party.upiId.isNotBlank() -> party.upiId.trim()
            else -> ""
        }
        val qrAmount = kotlin.math.abs(khata.inrNet).takeIf { it > 0.01 }
        val qrBitmap = if (targetUpiId.isNotEmpty() && qrAmount != null) {
            val uri = UpiQrGenerator.buildUpiUri(
                upiId = targetUpiId,
                payeeName = if (khata.inrNet > 0) userSettings.payeeName.ifBlank { "Udhaar" } else party.name,
                amountInr = if (hideAmounts) null else qrAmount,
                transactionNote = "Party Khata Settlement - ${party.name}"
            )
            UpiQrGenerator.generateQrBitmap(uri, sizePx = 280)
        } else {
            null
        }

        val itemsToRender = khata.transactions.take(15)
        val width = 840
        val rowsHeight = if (itemsToRender.isEmpty()) 60 else (itemsToRender.size * 52 + 80)
        val qrHeight = if (qrBitmap != null) 390 else 0
        val height = 880 + rowsHeight + qrHeight

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRect(20f, 20f, width - 20f, height - 20f, borderPaint)

        val fillBlack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }
        val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val headerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textSize = 21f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 23f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 23f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 25f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        // Top Banner
        canvas.drawRect(20f, 20f, width - 20f, 160f, fillBlack)
        canvas.drawText(strings.partyHeaderTitle, width / 2f, 82f, headerTitlePaint)
        canvas.drawText(
            "${strings.generatedPrefix} ${InterestCalculator.formatDateTime(System.currentTimeMillis())}",
            width / 2f,
            126f,
            headerSubPaint
        )

        var y = 215f
        val leftX = 56f
        val rightX = width - 56f

        fun drawRow(label: String, value: String) {
            canvas.drawText(label, leftX, y, labelPaint)
            canvas.drawText(value, rightX, y, valuePaint)
            y += 45f
        }

        fun drawDashedDivider() {
            y += 6f
            canvas.drawLine(leftX, y, rightX, y, dashedPaint)
            y += 38f
        }

        drawRow(strings.partyAccountLabel, party.name.uppercase())
        if (party.phoneNumber.isNotBlank()) drawRow(strings.contactPhoneLabel, InterestCalculator.maskPhoneNumber(party.phoneNumber))
        if (party.upiId.isNotBlank()) drawRow(strings.partyUpiVpaLabel, InterestCalculator.maskUpiId(party.upiId))
        drawRow(strings.totalRecordsLabel, "${khata.transactions.size} (${khata.activeLoanCount} ${strings.activeSuffix})")

        drawDashedDivider()

        canvas.drawText(strings.netLedgerPositionTitle, leftX, y, sectionTitlePaint)
        y += 44f
        if (inrEnabled) {
            drawRow(
                "INR (₹) ${strings.givenToReceiveValue}",
                InterestCalculator.formatCurrencyPrivacy(khata.inrToReceive, CurrencyType.INR, hideAmounts)
            )
            drawRow(
                "INR (₹) ${strings.takenToPayValue}",
                InterestCalculator.formatCurrencyPrivacy(khata.inrToPay, CurrencyType.INR, hideAmounts)
            )
            val inrStatus = when {
                hideAmounts -> InterestCalculator.formatCurrencyPrivacy(0.0, CurrencyType.INR, true)
                khata.inrNet > 0.01 -> "RECEIVE ${InterestCalculator.formatCurrency(khata.inrNet, CurrencyType.INR)}"
                khata.inrNet < -0.01 -> "PAY ${InterestCalculator.formatCurrency(-khata.inrNet, CurrencyType.INR)}"
                else -> "₹0 (SETTLED)"
            }
            drawRow("INR (₹) NET BALANCE", inrStatus)
        }
        if (nprEnabled) {
            drawRow(
                "NPR (रू) ${strings.givenToReceiveValue}",
                InterestCalculator.formatCurrencyPrivacy(khata.nprToReceive, CurrencyType.NPR, hideAmounts)
            )
            drawRow(
                "NPR (रू) ${strings.takenToPayValue}",
                InterestCalculator.formatCurrencyPrivacy(khata.nprToPay, CurrencyType.NPR, hideAmounts)
            )
            val nprStatus = when {
                hideAmounts -> InterestCalculator.formatCurrencyPrivacy(0.0, CurrencyType.NPR, true)
                khata.nprNet > 0.01 -> "RECEIVE ${InterestCalculator.formatCurrency(khata.nprNet, CurrencyType.NPR)}"
                khata.nprNet < -0.01 -> "PAY ${InterestCalculator.formatCurrency(-khata.nprNet, CurrencyType.NPR)}"
                else -> "रू0 (SETTLED)"
            }
            drawRow("NPR (रू) NET BALANCE", nprStatus)
        }

        drawDashedDivider()

        canvas.drawText("${strings.itemizedLoanRecordsTitle} (${itemsToRender.size})", leftX, y, sectionTitlePaint)
        y += 44f
        if (itemsToRender.isEmpty()) {
            canvas.drawText(strings.noActiveOrSettledRecordsText, leftX, y, labelPaint)
            y += 44f
        } else {
            itemsToRender.forEachIndexed { index, item ->
                val t = item.transaction
                val badge = if (t.transactionType == TransactionType.GIVEN) "GIVEN" else "TAKEN"
                val verBadge = t.verificationStatus.shortBadge
                val dateStr = InterestCalculator.formatDate(t.startDateMillis)
                val statusAmt = if (t.isSettled) {
                    "SETTLED (${InterestCalculator.formatCurrencyPrivacy(item.grossTotal, t.currency, hideAmounts)})"
                } else {
                    InterestCalculator.formatCurrencyPrivacy(item.netBalanceDue, t.currency, hideAmounts)
                }
                drawRow("${index + 1}. $dateStr [$badge • $verBadge]", statusAmt)
            }
        }

        if (qrBitmap != null) {
            drawDashedDivider()
            val qrLeft = (width - qrBitmap.width) / 2f
            canvas.drawBitmap(qrBitmap, qrLeft, y, null)
            y += qrBitmap.height + 34f
            val qrCaptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 22f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("${strings.scanToSettleNetInrPrefix} (${InterestCalculator.maskUpiId(targetUpiId)})", width / 2f, y, qrCaptionPaint)
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "${strings.generatedOfflineFooterPrefix} v${BuildConfig.VERSION_NAME} • ${BuildConfig.APPLICATION_ID}",
            width / 2f,
            height - 40f,
            footerPaint
        )

        return bitmap
    }

    fun shareReceiptBitmapAsPng(
        context: Context,
        bitmap: Bitmap,
        fileNamePrefix: String,
        captionText: String
    ) {
        runCatching {
            val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
            val imageFile = File(receiptsDir, "${fileNamePrefix}_${System.currentTimeMillis()}.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, captionText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Thermal Receipt Image").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    fun shareReceiptBitmapAsPdf(
        context: Context,
        bitmap: Bitmap,
        fileNamePrefix: String,
        captionText: String
    ) {
        runCatching {
            val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
            val pdfFile = File(receiptsDir, "${fileNamePrefix}_${System.currentTimeMillis()}.pdf")

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDocument.finishPage(page)

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, captionText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Statement PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}
