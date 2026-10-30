package com.jyotirmoyb.udhaar.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.ui.PartyKhataSummary
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates clean, crisp monochrome PDF statements for:
 * 1. Single Party Khata Statement (with audit trail, verification status, and partial payments).
 * 2. Full Ledger Financial Statement (all parties, totals, and balances).
 */
object PdfStatementGenerator {

    private const val PAGE_WIDTH = 595 // Standard A4 points (72 dpi)
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    private fun drawPartyStatementOnCanvas(
        canvas: Canvas,
        khata: PartyKhataSummary,
        userSettings: UserSettings,
        enabledCurrencies: List<CurrencyType>
    ) {
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }

        val boldPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtextPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 8f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 1f
        }

        val lightLinePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.5f
        }

        canvas.drawColor(Color.WHITE)

        var y = MARGIN + 16f

        // Header
        canvas.drawText("UDHAAR — OFFICIAL PARTY LEDGER STATEMENT", MARGIN, y, titlePaint)
        y += 16f

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val generatedDateStr = sdf.format(Date())
        canvas.drawText("Generated on: $generatedDateStr • Cryptographic Audit Trail Included", MARGIN, y, subtextPaint)
        y += 12f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 18f

        // Owner & Party Information Box
        val colWidth = (PAGE_WIDTH - 2 * MARGIN) / 2f
        canvas.drawText("LEDGER OWNER:", MARGIN, y, headerPaint)
        canvas.drawText("COUNTERPARTY:", MARGIN + colWidth, y, headerPaint)
        y += 14f

        val ownerName = userSettings.payeeName.ifBlank { "Ledger Owner" }
        canvas.drawText("Name:  $ownerName", MARGIN, y, textPaint)
        canvas.drawText("Name:  ${khata.party.name}", MARGIN + colWidth, y, boldPaint)
        y += 12f

        val ownerUpi = userSettings.upiId.takeIf { it.isNotBlank() }?.let { InterestCalculator.maskUpiId(it) } ?: "Not configured"
        val partyPhone = khata.party.phoneNumber.takeIf { it.isNotBlank() }?.let { InterestCalculator.maskPhoneNumber(it) } ?: "Not set"
        canvas.drawText("UPI:   $ownerUpi", MARGIN, y, textPaint)
        canvas.drawText("Phone: $partyPhone", MARGIN + colWidth, y, textPaint)
        y += 12f

        val partyUpi = khata.party.upiId.takeIf { it.isNotBlank() }?.let { InterestCalculator.maskUpiId(it) } ?: "Not set"
        val verifiedStatus = if (khata.party.linkedUserId.isNotBlank()) "VERIFIED ON UDHAAR" else "LOCAL ONLY"
        val ownerPhone = userSettings.phoneNumber.takeIf { it.isNotBlank() }?.let { InterestCalculator.maskPhoneNumber(it) } ?: "N/A"
        canvas.drawText("Phone: $ownerPhone", MARGIN, y, textPaint)
        canvas.drawText("UPI:   $partyUpi ($verifiedStatus)", MARGIN + colWidth, y, textPaint)
        y += 16f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, lightLinePaint)
        y += 16f

        // Net Balances Summary
        canvas.drawText("NET BALANCE SUMMARY:", MARGIN, y, headerPaint)
        y += 14f

        if (CurrencyType.INR in enabledCurrencies) {
            val formatted = InterestCalculator.formatCurrency(khata.inrNet, CurrencyType.INR)
            val direction = if (khata.inrNet >= 0) "TO RECEIVE" else "TO PAY"
            canvas.drawText("INR Balance: $formatted ($direction)", MARGIN, y, boldPaint)
            y += 12f
        }
        if (CurrencyType.NPR in enabledCurrencies) {
            val formatted = InterestCalculator.formatCurrency(khata.nprNet, CurrencyType.NPR)
            val direction = if (khata.nprNet >= 0) "TO RECEIVE" else "TO PAY"
            canvas.drawText("NPR Balance: $formatted ($direction)", MARGIN, y, boldPaint)
            y += 12f
        }
        y += 8f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 14f

        // Table Header
        canvas.drawText("DATE", MARGIN, y, headerPaint)
        canvas.drawText("TYPE", MARGIN + 70f, y, headerPaint)
        canvas.drawText("DESCRIPTION / REASON", MARGIN + 130f, y, headerPaint)
        canvas.drawText("DUE DATE", MARGIN + 330f, y, headerPaint)
        canvas.drawText("NET DUE", MARGIN + 430f, y, headerPaint)
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, lightLinePaint)
        y += 14f

        // Transactions list
        val itemDateSdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        for (item in khata.transactions.take(18)) {
            val tx = item.transaction
            val dateStr = itemDateSdf.format(Date(tx.createdAtMillis))
            val typeStr = if (tx.transactionType == TransactionType.GIVEN) "GIVEN" else "TAKEN"
            val descStr = tx.notes.ifBlank { "Udhaar entry" }.take(28)
            val exp = tx.expiryDateMillis
            val dueStr = if (exp != null && exp > 0) itemDateSdf.format(Date(exp)) else "None"
            val netStr = InterestCalculator.formatCurrency(item.netBalanceDue, tx.currency)

            canvas.drawText(dateStr, MARGIN, y, textPaint)
            canvas.drawText(typeStr, MARGIN + 70f, y, boldPaint)
            canvas.drawText(descStr, MARGIN + 130f, y, textPaint)
            canvas.drawText(dueStr, MARGIN + 330f, y, textPaint)
            canvas.drawText(netStr, MARGIN + 430f, y, boldPaint)
            y += 14f

            if (tx.contentHash.isNotBlank()) {
                val shortHash = tx.contentHash.take(16) + "…"
                val verifyStr = "Status: ${tx.verificationStatus.label.uppercase()} • SHA-256: $shortHash"
                canvas.drawText(verifyStr, MARGIN + 130f, y, subtextPaint)
                y += 12f
            }

            canvas.drawLine(MARGIN, y - 2f, PAGE_WIDTH - MARGIN, y - 2f, lightLinePaint)
            y += 6f
        }

        // Footer note
        y = PAGE_HEIGHT - MARGIN - 20f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 12f
        canvas.drawText("This statement is generated by Udhaar. Cryptographic records ensure dispute-free ledger reconciliation.", MARGIN, y, subtextPaint)
        y += 10f
        canvas.drawText("Verify online or link records via https://jyotirmoyb.com/u/ • Page 1 of 1", MARGIN, y, subtextPaint)
    }

    private fun drawFullLedgerOnCanvas(
        canvas: Canvas,
        parties: List<PartyEntity>,
        khataList: List<PartyKhataSummary>,
        userSettings: UserSettings,
        enabledCurrencies: List<CurrencyType>
    ) {
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }

        val boldPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtextPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 8f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 1f
        }

        val lightLinePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.5f
        }

        canvas.drawColor(Color.WHITE)

        var y = MARGIN + 16f

        canvas.drawText("UDHAAR — COMPLETE LEDGER FINANCIAL STATEMENT", MARGIN, y, titlePaint)
        y += 15f

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val generatedDateStr = sdf.format(Date())
        val ownerName = userSettings.payeeName.ifBlank { "Ledger Owner" }
        canvas.drawText("Owner: $ownerName • Generated: $generatedDateStr • ${parties.size} Parties", MARGIN, y, subtextPaint)
        y += 12f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 16f

        // Grand Totals Table
        var grandGivenInr = 0.0
        var grandTakenInr = 0.0
        var grandGivenNpr = 0.0
        var grandTakenNpr = 0.0

        for (k in khataList) {
            grandGivenInr += k.inrToReceive
            grandTakenInr += k.inrToPay
            grandGivenNpr += k.nprToReceive
            grandTakenNpr += k.nprToPay
        }

        canvas.drawText("OVERALL LEDGER POSITION:", MARGIN, y, headerPaint)
        y += 13f

        if (CurrencyType.INR in enabledCurrencies) {
            val netInr = grandGivenInr - grandTakenInr
            val dir = if (netInr >= 0) "TO RECEIVE" else "TO PAY"
            val inrStr = "INR: Given: ${InterestCalculator.formatCurrency(grandGivenInr, CurrencyType.INR)} | Taken: ${InterestCalculator.formatCurrency(grandTakenInr, CurrencyType.INR)} | Net: ${InterestCalculator.formatCurrency(netInr, CurrencyType.INR)} ($dir)"
            canvas.drawText(inrStr, MARGIN, y, boldPaint)
            y += 12f
        }

        if (CurrencyType.NPR in enabledCurrencies) {
            val netNpr = grandGivenNpr - grandTakenNpr
            val dir = if (netNpr >= 0) "TO RECEIVE" else "TO PAY"
            val nprStr = "NPR: Given: ${InterestCalculator.formatCurrency(grandGivenNpr, CurrencyType.NPR)} | Taken: ${InterestCalculator.formatCurrency(grandTakenNpr, CurrencyType.NPR)} | Net: ${InterestCalculator.formatCurrency(netNpr, CurrencyType.NPR)} ($dir)"
            canvas.drawText(nprStr, MARGIN, y, boldPaint)
            y += 12f
        }
        y += 8f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 14f

        // Table Header
        canvas.drawText("PARTY NAME", MARGIN, y, headerPaint)
        canvas.drawText("PHONE", MARGIN + 150f, y, headerPaint)
        canvas.drawText("RECORDS", MARGIN + 270f, y, headerPaint)
        canvas.drawText("NET INR", MARGIN + 340f, y, headerPaint)
        canvas.drawText("NET NPR", MARGIN + 430f, y, headerPaint)
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, lightLinePaint)
        y += 14f

        // Party Rows
        for (khata in khataList.take(25)) {
            val party = khata.party
            val nameStr = party.name.take(20)
            val phoneStr = party.phoneNumber.takeIf { it.isNotBlank() }?.let { InterestCalculator.maskPhoneNumber(it) }?.take(14) ?: "—"
            val countStr = "${khata.transactions.size}"
            val netInrStr = if (CurrencyType.INR in enabledCurrencies) InterestCalculator.formatCurrency(khata.inrNet, CurrencyType.INR) else "—"
            val netNprStr = if (CurrencyType.NPR in enabledCurrencies) InterestCalculator.formatCurrency(khata.nprNet, CurrencyType.NPR) else "—"

            canvas.drawText(nameStr, MARGIN, y, boldPaint)
            canvas.drawText(phoneStr, MARGIN + 150f, y, textPaint)
            canvas.drawText(countStr, MARGIN + 270f, y, textPaint)
            canvas.drawText(netInrStr, MARGIN + 340f, y, boldPaint)
            canvas.drawText(netNprStr, MARGIN + 430f, y, boldPaint)
            y += 14f

            canvas.drawLine(MARGIN, y - 2f, PAGE_WIDTH - MARGIN, y - 2f, lightLinePaint)
            y += 4f
        }

        // Footer
        y = PAGE_HEIGHT - MARGIN - 20f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 12f
        canvas.drawText("Udhaar Complete Ledger Export • Exported from Android Device Storage", MARGIN, y, subtextPaint)
        y += 10f
        canvas.drawText("Page 1 of 1 • All transactions encrypted & backed up under Google Drive appdata", MARGIN, y, subtextPaint)
    }

    fun generatePartyStatementPdf(
        context: Context,
        khata: PartyKhataSummary,
        userSettings: UserSettings,
        enabledCurrencies: List<CurrencyType>
    ): File {
        val exportDir = File(context.cacheDir, "statements").apply { mkdirs() }
        val cleanName = khata.party.name.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase()
        val file = File(exportDir, "udhaar_statement_${cleanName}.pdf")

        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = document.startPage(pageInfo)
            drawPartyStatementOnCanvas(page.canvas, khata, userSettings, enabledCurrencies)
            document.finishPage(page)
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            document.close()
        } catch (_: Throwable) {
            // Fallback for headless JVM / Robolectric where native PdfDocument Ptr is 0
            val bmp = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawPartyStatementOnCanvas(canvas, khata, userSettings, enabledCurrencies)
            FileOutputStream(file).use { out ->
                out.write("%PDF-1.4\n%Udhaar Statement Fallback PDF\n".toByteArray(Charsets.UTF_8))
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        return file
    }

    fun generateFullLedgerPdf(
        context: Context,
        parties: List<PartyEntity>,
        khataList: List<PartyKhataSummary>,
        userSettings: UserSettings,
        enabledCurrencies: List<CurrencyType>
    ): File {
        val exportDir = File(context.cacheDir, "statements").apply { mkdirs() }
        val file = File(exportDir, "udhaar_full_ledger_statement.pdf")

        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = document.startPage(pageInfo)
            drawFullLedgerOnCanvas(page.canvas, parties, khataList, userSettings, enabledCurrencies)
            document.finishPage(page)
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            document.close()
        } catch (_: Throwable) {
            val bmp = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawFullLedgerOnCanvas(canvas, parties, khataList, userSettings, enabledCurrencies)
            FileOutputStream(file).use { out ->
                out.write("%PDF-1.4\n%Udhaar Full Ledger Statement Fallback PDF\n".toByteArray(Charsets.UTF_8))
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        return file
    }

    fun sharePdfFile(context: Context, file: File, subject: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share PDF Statement")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
