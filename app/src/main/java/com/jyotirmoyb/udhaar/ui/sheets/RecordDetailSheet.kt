package com.jyotirmoyb.udhaar.ui.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.AuditTrailEntry
import com.jyotirmoyb.udhaar.data.Installment
import com.jyotirmoyb.udhaar.data.InstallmentPlan
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.ui.components.rememberAnimatedCurrencyText
import com.jyotirmoyb.udhaar.ui.components.rememberReducedMotionEnabled
import com.jyotirmoyb.udhaar.ui.components.udhaarSharedBounds
import com.jyotirmoyb.udhaar.ui.sheets.detail.InstallmentHistorySection
import com.jyotirmoyb.udhaar.ui.sheets.detail.InstallmentTimelineCard
import com.jyotirmoyb.udhaar.ui.sheets.detail.PartialPaymentSection
import com.jyotirmoyb.udhaar.ui.sheets.detail.TransactionActionsSection
import com.jyotirmoyb.udhaar.ui.sheets.detail.TransactionAttachmentsSection
import com.jyotirmoyb.udhaar.ui.sheets.detail.TransactionBalanceCard
import com.jyotirmoyb.udhaar.ui.sheets.detail.TransactionDetailHeader
import com.jyotirmoyb.udhaar.ui.sheets.detail.TransactionVerificationCard
import com.jyotirmoyb.udhaar.ui.sheets.detail.VerificationAuditTrailSection
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator

@Composable
fun RecordDetailSheet(
    summary: CalculatedUdhaarSummary,
    partialPayments: List<PartialPayment>,
    auditTrail: List<AuditTrailEntry> = emptyList(),
    linkedParty: PartyEntity? = null,
    attachments: List<Attachment> = emptyList(),
    attachmentsByPayment: Map<Long, List<Attachment>> = emptyMap(),
    installmentPlan: InstallmentPlan? = null,
    installments: List<Installment> = emptyList(),
    lenderUpiId: String = "",
    onOpenPlanBuilder: () -> Unit = {},
    onRequestNewDates: () -> Unit = {},
    onConfirmInstallmentUpiPayment: (installment: Installment, amountDouble: Double) -> Unit = { _, _ -> },
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
    val tx = summary.transaction
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            modifier = Modifier.testTag("delete_transaction_confirm_dialog"),
            title = {
                Text(
                    text = stringResource(R.string.ui_str_delete_transaction_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.ui_fmt_delete_transaction_confirm_body,
                        tx.partyName,
                        InterestCalculator.formatCurrency(tx.principalAmount, tx.currency)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDeleteTransaction()
                    },
                    modifier = Modifier.testTag("confirm_delete_transaction_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.ui_str_delete_d6f563))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    modifier = Modifier.testTag("cancel_delete_transaction_button")
                ) {
                    Text(stringResource(R.string.ui_str_cancel_1507c4))
                }
            }
        )
    }

    val reducedMotion = rememberReducedMotionEnabled()
    val animatedNetBalanceText = rememberAnimatedCurrencyText(
        targetAmount = summary.netBalanceDue,
        currency = tx.currency,
        hideAmount = false,
        reducedMotion = reducedMotion
    )

    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .udhaarSharedBounds("tx_card_${tx.id}")
            .testTag("transaction_detail_screen"),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
            ) {
                // Sticky Header with shared avatar and name
                TransactionDetailHeader(
                    tx = tx,
                    linkedParty = linkedParty,
                    onDismiss = onDismiss
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Net Balance & Breakdown Card
                    TransactionBalanceCard(
                        summary = summary,
                        tx = tx,
                        animatedNetBalanceText = animatedNetBalanceText
                    )

                    // Two-Party Verification Card
                    TransactionVerificationCard(
                        summary = summary,
                        tx = tx,
                        onRequestVerification = onRequestVerification,
                        onConfirmOrDisputeRecord = onConfirmOrDisputeRecord
                    )

                    // Transaction Documents & Attachments Section
                    TransactionAttachmentsSection(
                        transactionId = tx.id,
                        attachments = attachments,
                        onAddAttachment = onAddAttachment,
                        onOpenAttachmentViewer = onOpenAttachmentViewer
                    )

                    // Installment Plan & Repayment Schedule Section
                    if (installmentPlan != null) {
                        if (installmentPlan.scheduleProposalStatus == "PENDING") {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().testTag("pending_schedule_proposal_card")
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = stringResource(R.string.proposed_schedule_pending_banner),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = onDeclineProposedSchedule) {
                                            Text(stringResource(R.string.decline_schedule_btn))
                                        }
                                        Button(onClick = onAcceptProposedSchedule) {
                                            Text(stringResource(R.string.accept_schedule_btn))
                                        }
                                    }
                                }
                            }
                        }

                        InstallmentTimelineCard(
                            tx = tx,
                            plan = installmentPlan,
                            installments = installments,
                            lenderUpiId = lenderUpiId.ifBlank { linkedParty?.upiId.orEmpty() },
                            onEditPlan = onOpenPlanBuilder,
                            onRequestNewDates = onRequestNewDates,
                            onConfirmUpiPayment = onConfirmInstallmentUpiPayment,
                            modifier = Modifier.testTag("installment_timeline_card")
                        )
                    } else if (!tx.isSettled) {
                        OutlinedButton(
                            onClick = onOpenPlanBuilder,
                            modifier = Modifier.fillMaxWidth().testTag("split_into_installments_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(stringResource(R.string.split_into_installments))
                        }
                    }

                    // Partial Payment Entry Section (Only when not settled)
                    if (!tx.isSettled) {
                        PartialPaymentSection(
                            summary = summary,
                            tx = tx,
                            onRecordPartialPayment = onRecordPartialPayment,
                            onRecordCounterpartyPayment = onRecordCounterpartyPayment,
                            onRecordPartialPaymentWithProof = onRecordPartialPaymentWithProof
                        )
                    }

                    // Installment History List
                    InstallmentHistorySection(
                        tx = tx,
                        partialPayments = partialPayments,
                        attachmentsByPayment = attachmentsByPayment,
                        onAcknowledgePartialPayment = onAcknowledgePartialPayment,
                        onDeletePartialPayment = onDeletePartialPayment,
                        onOpenAttachmentViewer = onOpenAttachmentViewer
                    )

                    // Verification details section (SHA-256 hash, cloud IDs, and Audit Trail)
                    VerificationAuditTrailSection(
                        tx = tx,
                        auditTrail = auditTrail
                    )

                    // Quick Actions Row (WhatsApp + Thermal Receipt + UPI QR / Pay Party UPI), Chat, Mark Settled, Edit & Delete
                    TransactionActionsSection(
                        summary = summary,
                        tx = tx,
                        linkedParty = linkedParty,
                        unreadChatCount = unreadChatCount,
                        confirmDelete = confirmDelete,
                        onOpenTransactionChat = onOpenTransactionChat,
                        onOpenWhatsApp = onOpenWhatsApp,
                        onShareReceipt = onShareReceipt,
                        onOpenQr = onOpenQr,
                        onPayPartyViaUpi = onPayPartyViaUpi,
                        onToggleSettled = onToggleSettled,
                        onEdit = onEdit,
                        onDeleteClicked = {
                            if (confirmDelete) {
                                onDeleteTransaction()
                            } else {
                                confirmDelete = true
                            }
                        }
                    )
                }
            }
        }
    }
}
