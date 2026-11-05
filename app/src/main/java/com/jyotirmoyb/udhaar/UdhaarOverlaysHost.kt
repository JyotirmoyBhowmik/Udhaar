package com.jyotirmoyb.udhaar

import android.Manifest
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.ui.UdhaarUiState
import com.jyotirmoyb.udhaar.ui.UdhaarViewModel
import com.jyotirmoyb.udhaar.ui.components.AddEditPartyModal
import com.jyotirmoyb.udhaar.ui.components.AttachmentViewerModal
import com.jyotirmoyb.udhaar.ui.components.ChatThreadBottomSheet
import com.jyotirmoyb.udhaar.ui.components.ContactsPermissionRationaleDialog
import com.jyotirmoyb.udhaar.ui.components.InAppContactPickerModal
import com.jyotirmoyb.udhaar.ui.components.IncomingPaymentProfileAcceptSheet
import com.jyotirmoyb.udhaar.ui.components.IncomingVerificationReviewSheet
import com.jyotirmoyb.udhaar.ui.components.MergeDuplicatesModal
import com.jyotirmoyb.udhaar.ui.components.OnboardingModal
import com.jyotirmoyb.udhaar.ui.components.RequestPartyPaymentDetailsSheet
import com.jyotirmoyb.udhaar.ui.components.RestorePreviewDialog
import com.jyotirmoyb.udhaar.ui.components.ShareMyPaymentDetailsSheet
import com.jyotirmoyb.udhaar.ui.components.ShareUdhaarAppModal
import com.jyotirmoyb.udhaar.ui.components.ThermalReceiptModal
import com.jyotirmoyb.udhaar.ui.components.UpiQrCodeModal
import com.jyotirmoyb.udhaar.ui.components.VerificationShareSheet
import com.jyotirmoyb.udhaar.ui.sheets.InstallmentPlanBuilderSheet
import com.jyotirmoyb.udhaar.ui.sheets.UpdatesScreen
import com.jyotirmoyb.udhaar.ui.sheets.WebShareSheet
import com.jyotirmoyb.udhaar.ui.sheets.WhatsAppReminderModal
import com.jyotirmoyb.udhaar.util.AttachmentManager
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.ThermalReceiptGenerator

@Composable
internal fun UdhaarOverlaysHost(
    viewModel: UdhaarViewModel,
    uiState: UdhaarUiState,
    showContactsRationaleDialog: Boolean,
    onDismissContactsRationaleDialog: () -> Unit,
    contactsPermissionLauncher: ActivityResultLauncher<String>,
    isPlanProposalMode: Boolean,
    onPlanProposalModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val restoreBackup = uiState.pendingRestoreBackup
    val restoreParsed = uiState.pendingRestoreParsed
    if (uiState.showRestorePreviewDialog && restoreParsed != null && restoreBackup != null) {
        RestorePreviewDialog(
            backup = restoreBackup,
            parsed = restoreParsed,
            onMerge = { viewModel.executeDriveRestore(replaceExisting = false) },
            onReplace = { viewModel.executeDriveRestore(replaceExisting = true) },
            onDismiss = viewModel::closeRestorePreviewDialog
        )
    }

    if (uiState.showAddEditPartyModal) {
        AddEditPartyModal(
            initialParty = uiState.editingParty,
            enabledCurrencies = uiState.userSettings.enabledCurrencies,
            defaultCurrency = uiState.summaryCurrency,
            onSave = viewModel::saveParty,
            onDismiss = viewModel::closeAddEditPartyModal
        )
    }

    val activeThreadKey = uiState.activeChatThreadKey
    if (!activeThreadKey.isNullOrBlank()) {
        val activeTxId = uiState.activeChatTransactionId
        val activeTxSummary = activeTxId?.let { id -> uiState.allCalculatedItems.find { it.transaction.id == id } }
        val activeParty = uiState.allParties.find { p ->
            (uiState.activeChatPartyId != null && p.partyId == uiState.activeChatPartyId) ||
                p.name.trim().equals(uiState.activeChatPartyName.trim(), ignoreCase = true)
        }
        val resolvedPartyName = uiState.activeChatPartyName.ifBlank {
            activeTxSummary?.transaction?.partyName ?: activeParty?.name ?: "Party"
        }
        val resolvedPartyId = uiState.activeChatPartyId ?: activeTxSummary?.transaction?.partyId ?: activeParty?.partyId

        ChatThreadBottomSheet(
            threadKey = activeThreadKey,
            scope = uiState.activeChatScope,
            partyName = resolvedPartyName,
            partyId = resolvedPartyId,
            partyPhone = activeTxSummary?.transaction?.phoneNumber?.ifBlank { activeParty?.phoneNumber.orEmpty() }
                ?: activeParty?.phoneNumber.orEmpty(),
            transactionSummary = activeTxSummary,
            threadMetadata = uiState.chatThreadsByKey[activeThreadKey],
            messages = uiState.chatMessagesByThreadKey[activeThreadKey].orEmpty(),
            auditEntries = activeTxId?.let { uiState.auditTrailByTx[it].orEmpty() }.orEmpty(),
            myProfileName = uiState.userSettings.payeeName.ifBlank { "You" },
            myUpiId = uiState.userSettings.upiId,
            onDismiss = viewModel::closeChatSheet,
            onSendMessage = { messageType, textContent, attachmentUri, attachmentMimeType, fromCounterparty ->
                viewModel.sendChatMessage(
                    threadKey = activeThreadKey,
                    scope = uiState.activeChatScope,
                    transaction = activeTxSummary?.transaction,
                    partyId = resolvedPartyId,
                    partyName = resolvedPartyName,
                    messageType = messageType,
                    textContent = textContent,
                    attachmentUri = attachmentUri,
                    attachmentMimeType = attachmentMimeType,
                    fromCounterparty = fromCounterparty
                )
            },
            onRequestExtension = { proposedMillis, note, requestedByCounterparty ->
                if (activeTxSummary != null) {
                    viewModel.requestDueDateExtension(activeTxSummary.transaction, proposedMillis, note, requestedByCounterparty)
                    Toast.makeText(context, "Due-date extension proposed in chat.", Toast.LENGTH_SHORT).show()
                }
            },
            onRespondToExtension = { message, accept ->
                if (activeTxSummary != null) {
                    viewModel.respondToExtensionRequest(activeTxSummary.transaction, message, accept)
                    val msg = if (accept) "Extension accepted! Due date updated." else "Extension request declined."
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onSetTypingIndicator = { isTyping ->
                viewModel.setTypingIndicator(
                    activeThreadKey, uiState.activeChatScope, activeTxSummary?.transaction?.id,
                    activeTxSummary?.transaction?.remoteId.orEmpty(), resolvedPartyId, resolvedPartyName, isTyping
                )
            },
            onToggleMute = {
                viewModel.toggleThreadMute(
                    activeThreadKey, uiState.activeChatScope, activeTxSummary?.transaction?.id,
                    activeTxSummary?.transaction?.remoteId.orEmpty(), resolvedPartyId, resolvedPartyName
                )
            },
            onToggleBlock = {
                viewModel.toggleThreadBlock(
                    activeThreadKey, uiState.activeChatScope, activeTxSummary?.transaction?.id,
                    activeTxSummary?.transaction?.remoteId.orEmpty(), resolvedPartyId, resolvedPartyName
                )
            },
            onReportAbusiveUser = { reason ->
                viewModel.reportAbusiveUserInThread(
                    activeThreadKey, uiState.activeChatScope, activeTxSummary?.transaction?.id,
                    activeTxSummary?.transaction?.remoteId.orEmpty(), resolvedPartyId, resolvedPartyName, reason
                )
                Toast.makeText(context, "Abusive user reported and blocked.", Toast.LENGTH_LONG).show()
            },
            onOpenWhatsAppFallback = { txId ->
                viewModel.closeChatSheet()
                viewModel.openWhatsAppReminderModal(txId)
            }
        )
    }

    val verifyShareTxId = uiState.verificationShareTransactionId
    val verifyShareLink = uiState.verificationShareLink
    if (verifyShareTxId != null && !verifyShareLink.isNullOrBlank()) {
        val txSummary = uiState.allCalculatedItems.find { it.transaction.id == verifyShareTxId }
        if (txSummary != null) {
            VerificationShareSheet(
                transaction = txSummary.transaction,
                shareLink = verifyShareLink,
                settings = uiState.userSettings,
                attachments = uiState.attachmentsByTx[verifyShareTxId].orEmpty(),
                onDismiss = viewModel::closeVerificationShareModal,
                onOpenVerificationLink = { link ->
                    viewModel.closeVerificationShareModal()
                    if (!viewModel.handleIncomingDeepLinkOrToken(link)) {
                        Toast.makeText(context, "Invalid verification link.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    val webShareTxId = uiState.webShareTransactionId
    if (webShareTxId != null) {
        val txSummary = uiState.allCalculatedItems.find { it.transaction.id == webShareTxId }
        if (txSummary != null) {
            WebShareSheet(
                transaction = txSummary.transaction,
                settings = uiState.userSettings,
                existingResult = uiState.webShareResult,
                isGenerating = uiState.isGeneratingWebLink,
                onGenerateLink = { expiry, trust, note, att, upi, actions ->
                    viewModel.generateWebLink(webShareTxId, expiry, trust, note, att, upi, actions)
                },
                onRevokeLink = { viewModel.revokeWebLink(webShareTxId) },
                onDismiss = viewModel::closeWebShareModal
            )
        }
    }

    val showUpdates = uiState.showUpdatesScreen
    val transactionsWithUpdates by viewModel.transactionsWithUpdates.collectAsStateWithLifecycle(initialValue = emptyList())
    if (showUpdates) {
        UpdatesScreen(
            transactionsWithUpdates = transactionsWithUpdates,
            onConfirmPaymentClaim = viewModel::confirmPaymentClaim,
            onRejectPaymentClaim = viewModel::rejectPaymentClaim,
            onAcceptDateProposal = viewModel::acceptDateProposal,
            onDeclineDateProposal = viewModel::declineDateProposal,
            onSuggestCounterDate = viewModel::suggestCounterDate,
            onOpenTransactionDetail = viewModel::openTransactionDetail,
            onDismiss = viewModel::closeUpdatesScreen
        )
    }

    val incomingVerification = uiState.incomingVerificationPayload
    if (incomingVerification != null) {
        IncomingVerificationReviewSheet(
            payload = incomingVerification,
            onDismiss = viewModel::dismissIncomingVerification,
            onConfirmRecord = {
                viewModel.confirmOrDisputeIncomingVerification(confirm = true)
                Toast.makeText(context, "Confirmed! Mirrored record saved to your Khata.", Toast.LENGTH_LONG).show()
            },
            onDisputeRecord = { reason ->
                viewModel.confirmOrDisputeIncomingVerification(confirm = false, disputeReason = reason)
                Toast.makeText(context, "Dispute recorded on entry.", Toast.LENGTH_LONG).show()
            }
        )
    }

    if (uiState.showShareMyPaymentDetailsModal) {
        ShareMyPaymentDetailsSheet(
            settings = uiState.userSettings,
            targetParty = uiState.shareMyPaymentDetailsTargetParty,
            onDismiss = viewModel::closeShareMyPaymentDetailsModal
        )
    }

    val requestParty = uiState.requestPaymentDetailsParty
    if (requestParty != null) {
        RequestPartyPaymentDetailsSheet(
            party = requestParty,
            settings = uiState.userSettings,
            onDismiss = viewModel::closeRequestPartyPaymentDetailsModal,
            onOpenReceivedProfileLink = { link ->
                if (!viewModel.handleIncomingDeepLinkOrToken(urlOrToken = link, targetPartyId = requestParty.partyId)) {
                    Toast.makeText(context, "Invalid payment profile link.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    val incomingProfile = uiState.incomingPaymentProfilePayload
    if (incomingProfile != null) {
        IncomingPaymentProfileAcceptSheet(
            payload = incomingProfile,
            onDismiss = viewModel::dismissIncomingPaymentProfile,
            onAccept = {
                viewModel.acceptIncomingPaymentProfile()
                Toast.makeText(
                    context,
                    "Saved ${incomingProfile.displayName}'s UPI ID (${InterestCalculator.maskUpiId(incomingProfile.upiId)})",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    val receiptTxId = uiState.receiptModalTransactionId
    if (receiptTxId != null) {
        val receiptSummary = uiState.allCalculatedItems.find { it.transaction.id == receiptTxId }
        if (receiptSummary != null) {
            val payments = uiState.partialPaymentsByTx[receiptTxId].orEmpty()
            val auditTrail = uiState.auditTrailByTx[receiptTxId].orEmpty()
            val linkedParty = uiState.allParties.find { p ->
                (receiptSummary.transaction.partyId != null && p.partyId == receiptSummary.transaction.partyId) ||
                    p.name.trim().equals(receiptSummary.transaction.partyName.trim(), ignoreCase = true)
            }
            val txPlan = uiState.installmentPlansByTx[receiptTxId]
            val txInstallments = txPlan?.let { uiState.installmentsByPlan[it.id] }.orEmpty()
            val receiptStrings = remember(context) { ThermalReceiptGenerator.ThermalReceiptStrings.fromContext(context) }
            val bitmap = remember(
                receiptSummary, payments, auditTrail, uiState.userSettings, linkedParty,
                uiState.isAmountsHidden, receiptStrings, txPlan, txInstallments
            ) {
                ThermalReceiptGenerator.createTransactionReceiptBitmap(
                    summary = receiptSummary,
                    partialPayments = payments,
                    userSettings = uiState.userSettings,
                    partyUpiId = linkedParty?.upiId,
                    auditTrail = auditTrail,
                    hideAmounts = uiState.isAmountsHidden,
                    strings = receiptStrings,
                    installmentPlan = txPlan,
                    installments = txInstallments
                )
            }
            val safeParty = receiptSummary.transaction.partyName.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase()
            val formattedNet = InterestCalculator.formatCurrencyPaisePrivacy(
                receiptSummary.netBalanceDuePaise,
                receiptSummary.transaction.currency,
                uiState.isAmountsHidden
            )
            ThermalReceiptModal(
                title = stringResource(R.string.receipt_modal_tx_title),
                subtitle = "${receiptSummary.transaction.partyName.uppercase()} • $formattedNet",
                receiptBitmap = bitmap,
                fileNamePrefix = "udhaar_receipt_${safeParty}_${receiptSummary.transaction.id}",
                shareCaption = "Udhaar Statement Receipt for ${receiptSummary.transaction.partyName} — Net Balance: $formattedNet",
                onDismiss = viewModel::closeReceiptModal
            )
        }
    }

    val receiptPartyId = uiState.receiptModalPartyId
    if (receiptPartyId != null) {
        val khata = uiState.partyKhataList.find { it.party.partyId == receiptPartyId }
        if (khata != null) {
            val receiptStrings = remember(context) { ThermalReceiptGenerator.ThermalReceiptStrings.fromContext(context) }
            val bitmap = remember(khata, uiState.userSettings, uiState.isAmountsHidden, receiptStrings) {
                ThermalReceiptGenerator.createPartyStatementReceiptBitmap(
                    khata = khata,
                    enabledCurrencies = uiState.userSettings.enabledCurrencies,
                    userSettings = uiState.userSettings,
                    hideAmounts = uiState.isAmountsHidden,
                    strings = receiptStrings
                )
            }
            val safeParty = khata.party.name.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase()
            ThermalReceiptModal(
                title = stringResource(R.string.receipt_modal_party_title),
                subtitle = "${khata.party.name.uppercase()} • ${khata.transactions.size} RECORDS",
                receiptBitmap = bitmap,
                fileNamePrefix = "udhaar_khata_statement_${safeParty}",
                shareCaption = "Udhaar Party Khata Statement for ${khata.party.name}",
                onDismiss = viewModel::closeReceiptModal
            )
        }
    }

    val whatsAppTxId = uiState.whatsAppPreviewTransactionId
    if (whatsAppTxId != null) {
        val reminderSummary = uiState.allCalculatedItems.find { it.transaction.id == whatsAppTxId }
        if (reminderSummary != null) {
            val linkedParty = uiState.allParties.find { p ->
                (reminderSummary.transaction.partyId != null && p.partyId == reminderSummary.transaction.partyId) ||
                    p.name.trim().equals(reminderSummary.transaction.partyName.trim(), ignoreCase = true)
            }
            val reminderPlan = uiState.installmentPlansByTx[whatsAppTxId]
            val reminderInstallments = reminderPlan?.let { uiState.installmentsByPlan[it.id] }.orEmpty()
            WhatsAppReminderModal(
                summary = reminderSummary,
                userSettings = uiState.userSettings,
                party = linkedParty,
                fallbackPhoneNumber = linkedParty?.phoneNumber.orEmpty(),
                installmentPlan = reminderPlan,
                installments = reminderInstallments,
                onRecordReminderEvent = { level, channel ->
                    viewModel.recordReminderSent(reminderSummary.transaction.id, level, channel)
                },
                onSendInAppChat = if (linkedParty != null) {
                    { msg ->
                        viewModel.sendChatMessage(
                            threadKey = "party_${linkedParty.partyId}",
                            scope = ChatThreadScope.PARTY,
                            transaction = reminderSummary.transaction,
                            partyId = linkedParty.partyId,
                            partyName = linkedParty.name,
                            textContent = msg
                        )
                    }
                } else null,
                onDismiss = viewModel::closeWhatsAppReminderModal
            )
        }
    }

    val qrState = uiState.qrModalState
    if (qrState != null) {
        UpiQrCodeModal(
            qrState = qrState,
            userSettings = uiState.userSettings,
            onSaveUpiSettings = { upiId, payeeName, currency ->
                viewModel.saveUserSettings(upiId, payeeName, currency, closeModal = false)
            },
            onDismiss = viewModel::closeQrModal
        )
    }

    if (uiState.showShareAppModal) {
        ShareUdhaarAppModal(onDismiss = viewModel::closeShareAppModal)
    }

    val viewerAttachment = uiState.activeViewerAttachment
    if (viewerAttachment != null) {
        AttachmentViewerModal(
            attachment = viewerAttachment,
            onDismiss = viewModel::closeAttachmentViewer,
            onShare = { AttachmentManager.shareAttachment(context, viewerAttachment.localPath, viewerAttachment.mimeType) },
            onDelete = { viewModel.deleteAttachment(viewerAttachment) }
        )
    }

    if (showContactsRationaleDialog) {
        ContactsPermissionRationaleDialog(
            onRequestPermission = {
                onDismissContactsRationaleDialog()
                runCatching {
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }.onFailure {
                    Toast.makeText(context, context.getString(R.string.contact_error_no_app), Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = onDismissContactsRationaleDialog
        )
    }

    if (uiState.showInAppContactPicker) {
        InAppContactPickerModal(
            contacts = uiState.deviceContacts,
            isLoading = uiState.isLoadingContacts,
            onImportContacts = { selected ->
                if (selected.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.contact_error_read_failed), Toast.LENGTH_LONG).show()
                } else {
                    viewModel.importSelectedContacts(selected)
                    Toast.makeText(context, context.getString(R.string.contact_imported_count_fmt, selected.size), Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = viewModel::closeInAppContactPicker
        )
    }

    if (uiState.showMergeDuplicatesModal) {
        MergeDuplicatesModal(
            duplicatePairs = uiState.duplicatePartyPairs,
            onMergePair = { pair ->
                viewModel.mergeDuplicatePair(pair)
                Toast.makeText(context, "Merged ${pair.duplicateParty.name} into ${pair.primaryParty.name}.", Toast.LENGTH_SHORT).show()
            },
            onDismiss = viewModel::closeMergeDuplicatesModal
        )
    }

    if (uiState.showOnboardingModal) {
        OnboardingModal(
            onFinish = { pin, upiId, payeeName ->
                viewModel.completeOnboarding(pin, upiId, payeeName)
                Toast.makeText(context, "Welcome to Udhaar!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    val planBuilderTxId = uiState.showPlanBuilderTxId
    if (planBuilderTxId != null) {
        val targetTx = uiState.allCalculatedItems.firstOrNull { it.transaction.id == planBuilderTxId }?.transaction
        if (targetTx != null) {
            val existingPlan = uiState.installmentPlansByTx[planBuilderTxId]
            val existingInstallments = existingPlan?.let { uiState.installmentsByPlan[it.id] }.orEmpty()
            InstallmentPlanBuilderSheet(
                tx = targetTx,
                existingPlan = existingPlan,
                existingInstallments = existingInstallments,
                isProposalMode = isPlanProposalMode && existingPlan != null,
                onProposeNewDates = if (existingPlan != null) {
                    { drafts ->
                        viewModel.proposeNewInstallmentSchedule(existingPlan.id, targetTx.id, drafts)
                        onPlanProposalModeChange(false)
                    }
                } else null,
                onSavePlan = { plan, drafts ->
                    onPlanProposalModeChange(false)
                    viewModel.saveInstallmentPlan(targetTx, plan, drafts)
                },
                onDismiss = {
                    onPlanProposalModeChange(false)
                    viewModel.closeInstallmentPlanBuilder()
                }
            )
        }
    }
}
