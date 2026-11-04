package com.jyotirmoyb.udhaar.ui.sheets

import androidx.compose.runtime.Composable
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.AuditTrailEntry
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary

/**
 * Modular sheet entrypoint for viewing a transaction's details, partial payments,
 * attachments, verification state, and immutable audit trail.
 */
@Composable
fun TransactionDetailSheet(
    summary: CalculatedUdhaarSummary,
    partialPayments: List<PartialPayment>,
    auditTrail: List<AuditTrailEntry> = emptyList(),
    linkedParty: PartyEntity? = null,
    attachments: List<Attachment> = emptyList(),
    attachmentsByPayment: Map<Long, List<Attachment>> = emptyMap(),
    installmentPlan: com.jyotirmoyb.udhaar.data.InstallmentPlan? = null,
    installments: List<com.jyotirmoyb.udhaar.data.Installment> = emptyList(),
    lenderUpiId: String = "",
    onOpenPlanBuilder: () -> Unit = {},
    onRequestNewDates: () -> Unit = {},
    onConfirmInstallmentUpiPayment: (installment: com.jyotirmoyb.udhaar.data.Installment, amountDouble: Double) -> Unit = { _, _ -> },
    onMoveInstallmentPayment: (fromSequence: Int, toSequence: Int) -> Unit = { _, _ -> },
    onAcceptProposedSchedule: () -> Unit = {},
    onDeclineProposedSchedule: () -> Unit = {},
    onDismiss: () -> Unit,
    onRecordPartialPayment: (amount: Double, paymentMode: PaymentMode, note: String) -> Unit,
    onRecordCounterpartyPayment: (amount: Double, paymentMode: PaymentMode, note: String) -> Unit = { _, _, _ -> },
    onRecordPartialPaymentWithProof: (amount: Double, paymentMode: PaymentMode, note: String, proof: Attachment?) -> Unit = { _, _, _, _ -> },
    onAcknowledgePartialPayment: (PartialPayment) -> Unit = {},
    onDeletePartialPayment: (PartialPayment) -> Unit,
    onAddAttachment: (Attachment) -> Unit = {},
    onDeleteAttachment: (Attachment) -> Unit = {},
    onOpenAttachmentViewer: (Attachment) -> Unit = {},
    onRequestVerification: () -> Unit = {},
    onConfirmOrDisputeRecord: (confirm: Boolean, disputeReason: String) -> Unit = { _, _ -> },
    onToggleSettled: () -> Unit,
    onEdit: () -> Unit,
    onDeleteTransaction: () -> Unit,
    onOpenQr: () -> Unit,
    onPayPartyViaUpi: (partyName: String, partyUpiId: String, amountInr: Double) -> Unit = { _, _, _ -> },
    onShareReceipt: () -> Unit = {},
    onOpenWhatsApp: () -> Unit,
    unreadChatCount: Int = 0,
    onOpenTransactionChat: () -> Unit = {}
) {
    RecordDetailSheet(
        summary = summary,
        partialPayments = partialPayments,
        auditTrail = auditTrail,
        linkedParty = linkedParty,
        attachments = attachments,
        attachmentsByPayment = attachmentsByPayment,
        installmentPlan = installmentPlan,
        installments = installments,
        lenderUpiId = lenderUpiId,
        onOpenPlanBuilder = onOpenPlanBuilder,
        onRequestNewDates = onRequestNewDates,
        onConfirmInstallmentUpiPayment = onConfirmInstallmentUpiPayment,
        onMoveInstallmentPayment = onMoveInstallmentPayment,
        onAcceptProposedSchedule = onAcceptProposedSchedule,
        onDeclineProposedSchedule = onDeclineProposedSchedule,
        onDismiss = onDismiss,
        onRecordPartialPayment = onRecordPartialPayment,
        onRecordCounterpartyPayment = onRecordCounterpartyPayment,
        onRecordPartialPaymentWithProof = onRecordPartialPaymentWithProof,
        onAcknowledgePartialPayment = onAcknowledgePartialPayment,
        onDeletePartialPayment = onDeletePartialPayment,
        onAddAttachment = onAddAttachment,
        onDeleteAttachment = onDeleteAttachment,
        onOpenAttachmentViewer = onOpenAttachmentViewer,
        onRequestVerification = onRequestVerification,
        onConfirmOrDisputeRecord = onConfirmOrDisputeRecord,
        onToggleSettled = onToggleSettled,
        onEdit = onEdit,
        onDeleteTransaction = onDeleteTransaction,
        onOpenQr = onOpenQr,
        onPayPartyViaUpi = onPayPartyViaUpi,
        onShareReceipt = onShareReceipt,
        onOpenWhatsApp = onOpenWhatsApp,
        unreadChatCount = unreadChatCount,
        onOpenTransactionChat = onOpenTransactionChat
    )
}
