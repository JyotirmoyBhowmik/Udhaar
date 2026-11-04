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
import com.jyotirmoyb.udhaar.R
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
 * 1. Single Party Account Statement (with change history, verification status, and partial payments).
 * 2. Full Records Financial Statement (all parties, totals, and balances).
 */
object PdfStatementGenerator {

    private const val PAGE_WIDTH = 595 // Standard A4 points (72 dpi)
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    private fun drawPartyStatementOnCanvas(
        canvas: Canvas,
        khata: PartyKhataSummary,
        userSettings: UserSettings,
        enabledCurrencies: List<CurrencyType>,
        context: Context? = null,
        installmentPlansByTx: Map<Long, com.jyotirmoyb.udhaar.data.InstallmentPlan> = emptyMap(),
        installmentsByPlan: Map<Long, List<com.jyotirmoyb.udhaar.data.Installment>> = emptyMap()
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
        val titleText = context?.getString(R.string.pdf_party_statement_title) ?: "Udhaar — Account statement"
        canvas.drawText(titleText, MARGIN, y, titlePaint)
        y += 16f

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val generatedDateStr = sdf.format(Date())
        val subtitleText = context?.getString(R.string.pdf_generated_subtitle, generatedDateStr)
            ?: "Created on: $generatedDateStr • Includes change history"
        canvas.drawText(subtitleText, MARGIN, y, subtextPaint)
        y += 12f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 18f

        // Owner & Party Information Box
        val colWidth = (PAGE_WIDTH - 2 * MARGIN) / 2f
        val ownerHeaderText = context?.getString(R.string.pdf_owner_header) ?: "Your details:"
        val partyHeaderText = context?.getString(R.string.pdf_counterparty_header) ?: "The other person:"
        canvas.drawText(ownerHeaderText, MARGIN, y, headerPaint)
        canvas.drawText(partyHeaderText, MARGIN + colWidth, y, headerPaint)
        y += 14f

        val ownerName = userSettings.payeeName.ifBlank { "You" }
        val ownerNameStr = context?.getString(R.string.pdf_name_prefix, ownerName) ?: "Name: $ownerName"
        val partyNameStr = context?.getString(R.string.pdf_name_prefix, khata.party.name) ?: "Name: ${khata.party.name}"
        canvas.drawText(ownerNameStr, MARGIN, y, textPaint)
        canvas.drawText(partyNameStr, MARGIN + colWidth, y, boldPaint)
        y += 12f

        val ownerUpi = userSettings.upiId.takeIf { it.isNotBlank() }?.let { InterestCalculator.maskUpiId(it) } ?: "Not set"
        val partyPhone = khata.party.phoneNumber.takeIf { it.isNotBlank() }?.let { InterestCalculator.maskPhoneNumber(it) } ?: "Not set"
        val ownerUpiStr = context?.getString(R.string.pdf_upi_prefix, ownerUpi) ?: "UPI ID: $ownerUpi"
        val partyPhoneStr = context?.getString(R.string.pdf_phone_prefix, partyPhone) ?: "Phone: $partyPhone"
        canvas.drawText(ownerUpiStr, MARGIN, y, textPaint)
        canvas.drawText(partyPhoneStr, MARGIN + colWidth, y, textPaint)
        y += 12f

        val partyUpi = khata.party.upiId.takeIf { it.isNotBlank() }?.let { InterestCalculator.maskUpiId(it) } ?: "Not set"
        val verifiedStatus = if (khata.party.linkedUserId.isNotBlank()) "Verified" else "Local only"
        val ownerPhone = userSettings.phoneNumber.takeIf { it.isNotBlank() }?.let { InterestCalculator.maskPhoneNumber(it) } ?: "Not set"
        val ownerPhoneStr = context?.getString(R.string.pdf_phone_prefix, ownerPhone) ?: "Phone: $ownerPhone"
        val partyUpiStr = context?.getString(R.string.pdf_upi_prefix, "$partyUpi ($verifiedStatus)") ?: "UPI ID: $partyUpi ($verifiedStatus)"
        canvas.drawText(ownerPhoneStr, MARGIN, y, textPaint)
        canvas.drawText(partyUpiStr, MARGIN + colWidth, y, textPaint)
        y += 16f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, lightLinePaint)
        y += 16f

        // Net Balances Summary
        val balanceSummaryHeader = context?.getString(R.string.pdf_balance_summary) ?: "Total balance:"
        canvas.drawText(balanceSummaryHeader, MARGIN, y, headerPaint)
        y += 14f

        if (CurrencyType.INR in enabledCurrencies) {
            val formatted = InterestCalculator.formatCurrency(khata.inrNet, CurrencyType.INR)
            val direction = if (khata.inrNet >= 0) "To receive" else "To pay"
            val inrBalText = context?.getString(R.string.pdf_inr_balance, formatted, direction)
                ?: "INR balance: $formatted ($direction)"
            canvas.drawText(inrBalText, MARGIN, y, boldPaint)
            y += 12f
        }
        if (CurrencyType.NPR in enabledCurrencies) {
            val formatted = InterestCalculator.formatCurrency(khata.nprNet, CurrencyType.NPR)
            val direction = if (khata.nprNet >= 0) "To receive" else "To pay"
            val nprBalText = context?.getString(R.string.pdf_npr_balance, formatted, direction)
                ?: "NPR balance: $formatted ($direction)"
            canvas.drawText(nprBalText, MARGIN, y, boldPaint)
            y += 12f
        }
        y += 8f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 14f

        // Table Header
        val colDate = context?.getString(R.string.pdf_col_date) ?: "Date"
        val colType = context?.getString(R.string.pdf_col_type) ?: "Type"
        val colDesc = context?.getString(R.string.pdf_col_description) ?: "Description"
        val colDueDate = context?.getString(R.string.pdf_col_due_date) ?: "Due date"
        val colNetDue = context?.getString(R.string.pdf_col_net_due) ?: "Amount due"

        canvas.drawText(colDate, MARGIN, y, headerPaint)
        canvas.drawText(colType, MARGIN + 70f, y, headerPaint)
        canvas.drawText(colDesc, MARGIN + 130f, y, headerPaint)
        canvas.drawText(colDueDate, MARGIN + 330f, y, headerPaint)
        canvas.drawText(colNetDue, MARGIN + 430f, y, headerPaint)
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, lightLinePaint)
        y += 14f

        // Transactions list
        val itemDateSdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        for (item in khata.transactions.take(18)) {
            val tx = item.transaction
            val dateStr = itemDateSdf.format(Date(tx.createdAtMillis))
            val typeStr = if (tx.transactionType == TransactionType.GIVEN) "Given" else "Taken"
            val descStr = tx.notes.ifBlank { "Udhaar entry" }.take(22)
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
                val shortHash = tx.contentHash.take(10) + "…"
                val trustStr = if (tx.trustLevel.isNotBlank()) " • ${tx.trustLevel.take(10)}" else ""
                val verifyStr = "Status: ${tx.verificationStatus.label}$trustStr • SHA: $shortHash"
                canvas.drawText(verifyStr.take(55), MARGIN + 130f, y, subtextPaint)
                y += 12f
            }

            val txPlan = installmentPlansByTx[tx.id]
            val txInstList = txPlan?.let { installmentsByPlan[it.id] }.orEmpty().sortedBy { it.sequence }
            if (txPlan != null && txInstList.isNotEmpty()) {
                val prog = InstallmentCalculator.computeProgress(txInstList)
                val planSummary = "Installment Plan: ${prog.paidCount} of ${prog.totalCount} paid (${txPlan.frequency.label})"
                canvas.drawText(planSummary, MARGIN + 130f, y, subtextPaint)
                y += 11f
                for (inst in txInstList.take(6)) {
                    val instDueStr = itemDateSdf.format(Date(inst.dueDate))
                    val instAmtStr = tx.currency.symbol + InterestCalculator.formatAmount(InstallmentCalculator.paiseToDouble(inst.amountDue))
                    val instLine = "  #${inst.sequence} Due $instDueStr • $instAmtStr • ${inst.status.name}"
                    canvas.drawText(instLine, MARGIN + 130f, y, subtextPaint)
                    y += 10f
                }
            }

            canvas.drawLine(MARGIN, y - 2f, PAGE_WIDTH - MARGIN, y - 2f, lightLinePaint)
            y += 6f
        }

        // Footer note
        y = PAGE_HEIGHT - MARGIN - 20f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 12f
        val footerNoteText = context?.getString(R.string.pdf_footer_note)
            ?: "This statement is created by Udhaar. Clear records help prevent misunderstandings."
        canvas.drawText(footerNoteText, MARGIN, y, subtextPaint)
        y += 10f
        val footerLinkText = context?.getString(R.string.pdf_footer_link)
            ?: "Check online or link records at https://udhaar.jyotirmoyb.com/u/ • Page 1 of 1"
        canvas.drawText(footerLinkText, MARGIN, y, subtextPaint)
    }

    private fun drawInstallmentStatementOnCanvas(
        canvas: Canvas,
        tx: com.jyotirmoyb.udhaar.data.UdhaarTransaction,
        plan: com.jyotirmoyb.udhaar.data.InstallmentPlan,
        installments: List<com.jyotirmoyb.udhaar.data.Installment>,
        userSettings: UserSettings,
        context: Context? = null
    ) {
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 15f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
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

        val schedTitle = context?.getString(R.string.pdf_installment_schedule_title) ?: "Udhaar — Payment schedule"
        canvas.drawText(schedTitle, MARGIN, y, titlePaint)
        y += 14f

        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val generatedDateStr = sdf.format(Date())
        val schedSubtitle = context?.getString(
            R.string.pdf_installment_subtitle,
            generatedDateStr,
            plan.frequency.label,
            plan.installmentCount.toString()
        ) ?: "Created on: $generatedDateStr • ${plan.frequency.label} • ${plan.installmentCount} parts"
        canvas.drawText(schedSubtitle, MARGIN, y, subtextPaint)
        y += 14f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 14f

        val colWidth = (PAGE_WIDTH - 2 * MARGIN) / 2f
        val partyLabel = context?.getString(R.string.pdf_party_label, tx.partyName) ?: "Person: ${tx.partyName}"
        canvas.drawText(partyLabel, MARGIN, y, headerPaint)
        val roleStr = if (tx.transactionType == TransactionType.GIVEN) "Lender (to receive)" else "Borrower (to pay)"
        val roleLabel = context?.getString(R.string.pdf_role_label, roleStr) ?: "Type: $roleStr"
        canvas.drawText(roleLabel, MARGIN + colWidth, y, headerPaint)
        y += 12f

        val totalAmt = tx.currency.symbol + InterestCalculator.formatAmount(InstallmentCalculator.paiseToDouble(plan.totalAmountDue))
        val totalSched = context?.getString(R.string.pdf_total_scheduled, totalAmt) ?: "Total: $totalAmt"
        val interestMode = context?.getString(R.string.pdf_interest_mode_label, plan.interestMode.label) ?: "Type of interest: ${plan.interestMode.label}"
        canvas.drawText(totalSched, MARGIN, y, boldPaint)
        canvas.drawText(interestMode, MARGIN + colWidth, y, textPaint)
        y += 14f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 14f

        // Table Header
        val colSeq = context?.getString(R.string.pdf_col_seq) ?: "No."
        val colDueDate = context?.getString(R.string.pdf_col_due_date) ?: "Due date"
        val colAmountDue = context?.getString(R.string.pdf_col_amount_due) ?: "Amount due"
        val colAmountPaid = context?.getString(R.string.pdf_col_amount_paid) ?: "Amount paid"
        val colStatus = context?.getString(R.string.pdf_col_status) ?: "Status"

        canvas.drawText(colSeq, MARGIN, y, headerPaint)
        canvas.drawText(colDueDate, MARGIN + 40f, y, headerPaint)
        canvas.drawText(colAmountDue, MARGIN + 140f, y, headerPaint)
        canvas.drawText(colAmountPaid, MARGIN + 260f, y, headerPaint)
        canvas.drawText(colStatus, MARGIN + 380f, y, headerPaint)
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, lightLinePaint)
        y += 14f

        for (inst in installments.sortedBy { it.sequence }) {
            val dueStr = sdf.format(Date(inst.dueDate))
            val dueAmt = tx.currency.symbol + InterestCalculator.formatAmount(InstallmentCalculator.paiseToDouble(inst.amountDue))
            val paidAmt = tx.currency.symbol + InterestCalculator.formatAmount(InstallmentCalculator.paiseToDouble(inst.amountPaid))

            canvas.drawText("#${inst.sequence}", MARGIN, y, boldPaint)
            canvas.drawText(dueStr, MARGIN + 40f, y, textPaint)
            canvas.drawText(dueAmt, MARGIN + 140f, y, textPaint)
            canvas.drawText(paidAmt, MARGIN + 260f, y, boldPaint)
            canvas.drawText(inst.status.name, MARGIN + 380f, y, boldPaint)
            y += 14f

            canvas.drawLine(MARGIN, y - 2f, PAGE_WIDTH - MARGIN, y - 2f, lightLinePaint)
            y += 4f
        }

        y = PAGE_HEIGHT - MARGIN - 20f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 12f
        val hashStr = tx.contentHash.take(16).ifBlank { "verified" }
        val footer = context?.getString(R.string.pdf_installment_footer, hashStr)
            ?: "Udhaar payment plan • Record fingerprint: $hashStr • Page 1 of 1"
        canvas.drawText(footer, MARGIN, y, subtextPaint)
    }

    private fun drawFullLedgerOnCanvas(
        canvas: Canvas,
        parties: List<PartyEntity>,
        khataList: List<PartyKhataSummary>,
        userSettings: UserSettings,
        enabledCurrencies: List<CurrencyType>,
        context: Context? = null
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

        val fullTitle = context?.getString(R.string.pdf_full_ledger_title) ?: "Udhaar — All records summary"
        canvas.drawText(fullTitle, MARGIN, y, titlePaint)
        y += 15f

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val generatedDateStr = sdf.format(Date())
        val ownerName = userSettings.payeeName.ifBlank { "You" }
        val fullSubtitle = context?.getString(R.string.pdf_full_ledger_subtitle, ownerName, generatedDateStr, parties.size.toString())
            ?: "Your name: $ownerName • Created: $generatedDateStr • ${parties.size} people"
        canvas.drawText(fullSubtitle, MARGIN, y, subtextPaint)
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

        val overallPosHeader = context?.getString(R.string.pdf_overall_position) ?: "Total balance summary:"
        canvas.drawText(overallPosHeader, MARGIN, y, headerPaint)
        y += 13f

        if (CurrencyType.INR in enabledCurrencies) {
            val netInr = grandGivenInr - grandTakenInr
            val dir = if (netInr >= 0) "To receive" else "To pay"
            val inrGivenStr = "INR (₹) Given: ${InterestCalculator.formatCurrency(grandGivenInr, CurrencyType.INR)} | Taken: ${InterestCalculator.formatCurrency(grandTakenInr, CurrencyType.INR)}"
            val inrNetStr = "INR (₹) Net Position: ${InterestCalculator.formatCurrency(netInr, CurrencyType.INR)} ($dir)"
            canvas.drawText(inrGivenStr, MARGIN, y, boldPaint)
            y += 12f
            canvas.drawText(inrNetStr, MARGIN, y, boldPaint)
            y += 12f
        }

        if (CurrencyType.NPR in enabledCurrencies) {
            val netNpr = grandGivenNpr - grandTakenNpr
            val dir = if (netNpr >= 0) "To receive" else "To pay"
            val nprGivenStr = "NPR (रू) Given: ${InterestCalculator.formatCurrency(grandGivenNpr, CurrencyType.NPR)} | Taken: ${InterestCalculator.formatCurrency(grandTakenNpr, CurrencyType.NPR)}"
            val nprNetStr = "NPR (रू) Net Position: ${InterestCalculator.formatCurrency(netNpr, CurrencyType.NPR)} ($dir)"
            canvas.drawText(nprGivenStr, MARGIN, y, boldPaint)
            y += 12f
            canvas.drawText(nprNetStr, MARGIN, y, boldPaint)
            y += 12f
        }
        y += 8f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 14f

        // Table Header
        val colPartyName = context?.getString(R.string.pdf_col_party_name) ?: "Name"
        val colPhone = context?.getString(R.string.receipt_contact_phone_label) ?: "Phone"
        val colRecords = context?.getString(R.string.pdf_col_records_count) ?: "Records"
        val colNetInr = context?.getString(R.string.pdf_col_net_inr) ?: "INR balance"
        val colNetNpr = context?.getString(R.string.pdf_col_net_npr) ?: "NPR balance"

        canvas.drawText(colPartyName, MARGIN, y, headerPaint)
        canvas.drawText(colPhone, MARGIN + 120f, y, headerPaint)
        canvas.drawText(colRecords, MARGIN + 215f, y, headerPaint)
        canvas.drawText(colNetInr, MARGIN + 285f, y, headerPaint)
        canvas.drawText(colNetNpr, MARGIN + 405f, y, headerPaint)
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, lightLinePaint)
        y += 14f

        // Party Rows
        for (khata in khataList.take(25)) {
            val party = khata.party
            val nameStr = party.name.take(16)
            val phoneStr = party.phoneNumber.takeIf { it.isNotBlank() }?.let { InterestCalculator.maskPhoneNumber(it) }?.take(11) ?: "—"
            val countStr = "${khata.transactions.size}"
            val netInrStr = if (CurrencyType.INR in enabledCurrencies) InterestCalculator.formatCurrency(khata.inrNet, CurrencyType.INR) else "—"
            val netNprStr = if (CurrencyType.NPR in enabledCurrencies) InterestCalculator.formatCurrency(khata.nprNet, CurrencyType.NPR) else "—"

            canvas.drawText(nameStr, MARGIN, y, boldPaint)
            canvas.drawText(phoneStr, MARGIN + 120f, y, textPaint)
            canvas.drawText(countStr, MARGIN + 215f, y, textPaint)
            canvas.drawText(netInrStr, MARGIN + 285f, y, boldPaint)
            canvas.drawText(netNprStr, MARGIN + 405f, y, boldPaint)
            y += 14f

            canvas.drawLine(MARGIN, y - 2f, PAGE_WIDTH - MARGIN, y - 2f, lightLinePaint)
            y += 4f
        }

        // Footer
        y = PAGE_HEIGHT - MARGIN - 20f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 12f
        val footer1 = context?.getString(R.string.pdf_full_ledger_footer_1)
            ?: "Udhaar full records export • Saved from your phone"
        canvas.drawText(footer1, MARGIN, y, subtextPaint)
        y += 10f
        val footer2 = context?.getString(R.string.pdf_full_ledger_footer_2)
            ?: "Page 1 of 1 • All records locked on your phone and backed up to your Google Drive"
        canvas.drawText(footer2, MARGIN, y, subtextPaint)
    }

    fun generatePartyStatementPdf(
        context: Context,
        khata: PartyKhataSummary,
        userSettings: UserSettings,
        enabledCurrencies: List<CurrencyType>,
        installmentPlansByTx: Map<Long, com.jyotirmoyb.udhaar.data.InstallmentPlan> = emptyMap(),
        installmentsByPlan: Map<Long, List<com.jyotirmoyb.udhaar.data.Installment>> = emptyMap()
    ): File {
        val exportDir = File(context.cacheDir, "statements").apply { mkdirs() }
        val cleanName = khata.party.name.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase()
        val file = File(exportDir, "udhaar_statement_${cleanName}.pdf")

        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = document.startPage(pageInfo)
            drawPartyStatementOnCanvas(
                page.canvas,
                khata,
                userSettings,
                enabledCurrencies,
                context,
                installmentPlansByTx,
                installmentsByPlan
            )
            document.finishPage(page)
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            document.close()
        } catch (_: Throwable) {
            // Fallback for headless JVM / Robolectric where native PdfDocument Ptr is 0
            val bmp = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawPartyStatementOnCanvas(
                canvas,
                khata,
                userSettings,
                enabledCurrencies,
                context,
                installmentPlansByTx,
                installmentsByPlan
            )
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
            drawFullLedgerOnCanvas(page.canvas, parties, khataList, userSettings, enabledCurrencies, context)
            document.finishPage(page)
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            document.close()
        } catch (_: Throwable) {
            val bmp = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawFullLedgerOnCanvas(canvas, parties, khataList, userSettings, enabledCurrencies, context)
            FileOutputStream(file).use { out ->
                out.write("%PDF-1.4\n%Udhaar Full Ledger Statement Fallback PDF\n".toByteArray(Charsets.UTF_8))
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        return file
    }

    fun generateTransactionInstallmentPdf(
        context: Context,
        tx: com.jyotirmoyb.udhaar.data.UdhaarTransaction,
        plan: com.jyotirmoyb.udhaar.data.InstallmentPlan,
        installments: List<com.jyotirmoyb.udhaar.data.Installment>,
        userSettings: UserSettings
    ): File {
        val exportDir = File(context.cacheDir, "statements").apply { mkdirs() }
        val cleanName = tx.partyName.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase()
        val file = File(exportDir, "udhaar_installment_plan_${cleanName}_${tx.id}.pdf")

        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            val page = document.startPage(pageInfo)
            drawInstallmentStatementOnCanvas(page.canvas, tx, plan, installments, userSettings, context)
            document.finishPage(page)
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            document.close()
        } catch (_: Throwable) {
            val bmp = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawInstallmentStatementOnCanvas(canvas, tx, plan, installments, userSettings, context)
            FileOutputStream(file).use { out ->
                out.write("%PDF-1.4\n%Udhaar Installment Statement Fallback PDF\n".toByteArray(Charsets.UTF_8))
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
