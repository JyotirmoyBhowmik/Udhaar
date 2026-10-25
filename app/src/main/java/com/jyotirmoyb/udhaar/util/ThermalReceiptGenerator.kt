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
 * and Timestamp, and shares directly via WhatsApp / FileProvider.
 */
object ThermalReceiptGenerator {

    fun createTransactionReceiptBitmap(
        summary: CalculatedUdhaarSummary,
        partialPayments: List<PartialPayment>,
        userSettings: UserSettings,
        partyUpiId: String? = null
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
                amountInr = summary.netBalanceDue,
                transactionNote = "Udhaar #${tx.id} - ${tx.partyName}"
            )
            UpiQrGenerator.generateQrBitmap(uri, sizePx = 300)
        } else {
            null
        }

        val width = 840
        val paymentRowsHeight = if (partialPayments.isEmpty()) 56 else (partialPayments.size.coerceAtMost(12) * 44 + 74)
        val qrSectionHeight = if (qrBitmap != null) 410 else 0
        val height = 1040 + paymentRowsHeight + qrSectionHeight

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
        canvas.drawText("UDHAAR • THERMAL STATEMENT", width / 2f, 82f, headerTitlePaint)
        canvas.drawText(
            "RECEIPT #${tx.id}  •  ${InterestCalculator.formatDateTime(System.currentTimeMillis())}",
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
        drawRow("PARTY NAME", tx.partyName.uppercase(), boldLabel = true)
        if (tx.phoneNumber.isNotBlank()) {
            drawRow("CONTACT PHONE", tx.phoneNumber)
        }
        drawRow("RECORD TYPE", if (isGiven) "GIVEN (TO RECEIVE)" else "TAKEN (TO PAY)")
        drawRow("CURRENCY & MODE", "${currency.displayName} • ${tx.paymentMode.label.uppercase()}")
        drawRow("START DATE", InterestCalculator.formatDate(tx.startDateMillis))
        drawRow(
            "DUE / STATUS",
            tx.expiryDateMillis?.let { InterestCalculator.formatDate(it) } ?: summary.dueStatusText
        )

        drawDashedDivider()

        // Financial Breakdown
        canvas.drawText("FINANCIAL BREAKDOWN", leftX, y, sectionTitlePaint)
        y += 44f
        drawRow("PRINCIPAL AMOUNT", InterestCalculator.formatCurrency(summary.principal, currency))
        val interestDesc = if (tx.isInterestEnabled && tx.interestRate > 0.0) {
            val typeLbl = if (tx.interestType.name == "SIMPLE") "Simple" else "Comp.${tx.compoundFrequency.label}"
            "INTEREST (${InterestCalculator.formatRate(tx.interestRate)}${tx.interestRatePeriod.shortSuffix} $typeLbl, ${summary.elapsedDays}d)"
        } else {
            "INTEREST (0% • INTEREST FREE)"
        }
        drawRow(interestDesc, "+ ${InterestCalculator.formatCurrency(summary.accruedInterest, currency)}")
        drawRow("GROSS TOTAL", InterestCalculator.formatCurrency(summary.grossTotal, currency), boldLabel = true)
        drawRow("REPAID SO FAR", "- ${InterestCalculator.formatCurrency(summary.amountPaid, currency)}")

        drawDashedDivider()

        // Partial Repayments Audit Trail Table
        canvas.drawText("PARTIAL REPAYMENTS AUDIT (${partialPayments.size})", leftX, y, sectionTitlePaint)
        y += 42f
        if (partialPayments.isEmpty()) {
            canvas.drawText("No partial installments recorded yet.", leftX, y, labelPaint)
            y += 44f
        } else {
            val chronological = partialPayments.sortedBy { it.paidAtMillis }.takeLast(12)
            chronological.forEachIndexed { idx, p ->
                val dateStr = InterestCalculator.formatDate(p.paidAtMillis)
                val notePart = if (p.note.isNotBlank()) " • ${p.note.take(16)}" else ""
                val leftCol = "${idx + 1}. $dateStr [${p.paymentMode.shortBadge}]$notePart"
                drawRow(leftCol, "- ${InterestCalculator.formatCurrency(p.amount, currency)}")
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
            if (tx.isSettled) "FINAL STATUS: SETTLED" else "NET BALANCE DUE (${currency.code})",
            leftX + 24f,
            netBoxTop + 48f,
            netLabelPaint
        )
        canvas.drawText(
            InterestCalculator.formatCurrency(summary.netBalanceDue, currency),
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
            canvas.drawText("SCAN TO SETTLE VIA UPI: $targetUpiId", width / 2f, y, qrCaptionPaint)
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
            "GENERATED OFFLINE BY UDHAAR v${BuildConfig.VERSION_NAME} • ${BuildConfig.APPLICATION_ID}",
            width / 2f,
            height - 42f,
            footerPaint
        )

        return bitmap
    }

    fun createPartyStatementReceiptBitmap(
        khata: PartyKhataSummary,
        enabledCurrencies: List<CurrencyType>,
        userSettings: UserSettings
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
                amountInr = qrAmount,
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
        canvas.drawText("UDHAAR • PARTY KHATA RECEIPT", width / 2f, 82f, headerTitlePaint)
        canvas.drawText(
            "GENERATED: ${InterestCalculator.formatDateTime(System.currentTimeMillis())}",
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

        drawRow("PARTY / ACCOUNT", party.name.uppercase())
        if (party.phoneNumber.isNotBlank()) drawRow("CONTACT PHONE", party.phoneNumber)
        if (party.upiId.isNotBlank()) drawRow("PARTY UPI VPA", party.upiId)
        drawRow("TOTAL RECORDS", "${khata.transactions.size} (${khata.activeLoanCount} ACTIVE)")

        drawDashedDivider()

        canvas.drawText("NET LEDGER POSITION", leftX, y, sectionTitlePaint)
        y += 44f
        if (inrEnabled) {
            drawRow("INR (₹) TO RECEIVE (GIVEN)", InterestCalculator.formatCurrency(khata.inrToReceive, CurrencyType.INR))
            drawRow("INR (₹) TO PAY (TAKEN)", InterestCalculator.formatCurrency(khata.inrToPay, CurrencyType.INR))
            val inrStatus = when {
                khata.inrNet > 0.01 -> "RECEIVE ${InterestCalculator.formatCurrency(khata.inrNet, CurrencyType.INR)}"
                khata.inrNet < -0.01 -> "PAY ${InterestCalculator.formatCurrency(-khata.inrNet, CurrencyType.INR)}"
                else -> "₹0 (SETTLED)"
            }
            drawRow("INR (₹) NET BALANCE", inrStatus)
        }
        if (nprEnabled) {
            drawRow("NPR (रू) TO RECEIVE (GIVEN)", InterestCalculator.formatCurrency(khata.nprToReceive, CurrencyType.NPR))
            drawRow("NPR (रू) TO PAY (TAKEN)", InterestCalculator.formatCurrency(khata.nprToPay, CurrencyType.NPR))
            val nprStatus = when {
                khata.nprNet > 0.01 -> "RECEIVE ${InterestCalculator.formatCurrency(khata.nprNet, CurrencyType.NPR)}"
                khata.nprNet < -0.01 -> "PAY ${InterestCalculator.formatCurrency(-khata.nprNet, CurrencyType.NPR)}"
                else -> "रू0 (SETTLED)"
            }
            drawRow("NPR (रू) NET BALANCE", nprStatus)
        }

        drawDashedDivider()

        canvas.drawText("ITEMIZED LOAN RECORDS (${itemsToRender.size})", leftX, y, sectionTitlePaint)
        y += 44f
        if (itemsToRender.isEmpty()) {
            canvas.drawText("No active or settled records.", leftX, y, labelPaint)
            y += 44f
        } else {
            itemsToRender.forEachIndexed { index, item ->
                val t = item.transaction
                val badge = if (t.transactionType == TransactionType.GIVEN) "GIVEN" else "TAKEN"
                val dateStr = InterestCalculator.formatDate(t.startDateMillis)
                val statusAmt = if (t.isSettled) {
                    "SETTLED (${InterestCalculator.formatCurrency(item.grossTotal, t.currency)})"
                } else {
                    InterestCalculator.formatCurrency(item.netBalanceDue, t.currency)
                }
                drawRow("${index + 1}. $dateStr [$badge • ${t.paymentMode.shortBadge}]", statusAmt)
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
            canvas.drawText("SCAN TO SETTLE NET INR BALANCE ($targetUpiId)", width / 2f, y, qrCaptionPaint)
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "GENERATED OFFLINE BY UDHAAR v${BuildConfig.VERSION_NAME} • ${BuildConfig.APPLICATION_ID}",
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
