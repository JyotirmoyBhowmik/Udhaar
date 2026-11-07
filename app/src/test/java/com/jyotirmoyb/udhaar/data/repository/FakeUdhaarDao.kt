package com.jyotirmoyb.udhaar.data.repository

import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.AuditTrailEntry
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatThreadMetadata
import com.jyotirmoyb.udhaar.data.Installment
import com.jyotirmoyb.udhaar.data.InstallmentPlan
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.ReminderEvent
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong

class FakeUdhaarDao : UdhaarDao {
    private val partyIdGen = AtomicLong(1L)
    private val txIdGen = AtomicLong(1L)
    private val paymentIdGen = AtomicLong(1L)
    private val auditIdGen = AtomicLong(1L)
    private val msgIdGen = AtomicLong(1L)
    private val attachmentIdGen = AtomicLong(1L)
    private val reminderIdGen = AtomicLong(1L)
    private val planIdGen = AtomicLong(1L)
    private val installmentIdGen = AtomicLong(1L)

    private val partiesFlow = MutableStateFlow<List<PartyEntity>>(emptyList())
    private val txFlow = MutableStateFlow<List<UdhaarTransaction>>(emptyList())
    private val paymentsFlow = MutableStateFlow<List<PartialPayment>>(emptyList())
    private val auditFlow = MutableStateFlow<List<AuditTrailEntry>>(emptyList())
    private val chatThreadsFlow = MutableStateFlow<List<ChatThreadMetadata>>(emptyList())
    private val chatMessagesFlow = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private val attachmentsFlow = MutableStateFlow<List<Attachment>>(emptyList())
    private val reminderEventsFlow = MutableStateFlow<List<ReminderEvent>>(emptyList())
    private val plansFlow = MutableStateFlow<List<InstallmentPlan>>(emptyList())
    private val installmentsFlow = MutableStateFlow<List<Installment>>(emptyList())

    // --- Parties ---
    override fun getAllParties(): Flow<List<PartyEntity>> = partiesFlow.asStateFlow()

    override suspend fun getAllPartiesSnapshot(): List<PartyEntity> = partiesFlow.value

    override suspend fun getPartyById(partyId: Long): PartyEntity? =
        partiesFlow.value.firstOrNull { it.partyId == partyId }

    override suspend fun getPartyBySyncId(syncId: String): PartyEntity? =
        partiesFlow.value.firstOrNull { it.syncId == syncId }

    override suspend fun getPartyByName(name: String): PartyEntity? =
        partiesFlow.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    override suspend fun insertParty(party: PartyEntity): Long {
        val current = partiesFlow.value.toMutableList()
        val assignedId = if (party.partyId > 0L) party.partyId else partyIdGen.getAndIncrement()
        val item = party.copy(partyId = assignedId)
        current.removeAll { it.partyId == assignedId }
        current.add(item)
        partiesFlow.value = current
        return assignedId
    }

    override suspend fun insertParties(parties: List<PartyEntity>): List<Long> {
        return parties.map { insertParty(it) }
    }

    override suspend fun updateParty(party: PartyEntity) {
        val current = partiesFlow.value.toMutableList()
        val idx = current.indexOfFirst { it.partyId == party.partyId }
        if (idx >= 0) {
            current[idx] = party
        } else {
            current.add(party)
        }
        partiesFlow.value = current
    }

    override suspend fun deleteParty(party: PartyEntity) {
        deletePartyById(party.partyId)
    }

    override suspend fun deletePartyById(partyId: Long) {
        partiesFlow.value = partiesFlow.value.filterNot { it.partyId == partyId }
    }

    override suspend fun relinkTransactionsForPartyId(sourcePartyId: Long, targetPartyId: Long, targetPartyName: String) {
        txFlow.value = txFlow.value.map {
            if (it.partyId == sourcePartyId) it.copy(partyId = targetPartyId, partyName = targetPartyName) else it
        }
    }

    override suspend fun relinkChatMessagesForPartyId(sourcePartyId: Long, targetPartyId: Long, targetPartyName: String) {
        chatMessagesFlow.value = chatMessagesFlow.value.map {
            if (it.partyId == sourcePartyId) it.copy(partyId = targetPartyId, partyName = targetPartyName) else it
        }
    }

    override suspend fun relinkChatThreadsForPartyId(sourcePartyId: Long, targetPartyId: Long, targetPartyName: String) {
        chatThreadsFlow.value = chatThreadsFlow.value.map {
            if (it.partyId == sourcePartyId) it.copy(partyId = targetPartyId, partyName = targetPartyName) else it
        }
    }

    override suspend fun setPartyFavorite(partyId: Long, isFavorite: Boolean) {
        partiesFlow.value = partiesFlow.value.map {
            if (it.partyId == partyId) it.copy(isFavorite = isFavorite) else it
        }
    }

    override suspend fun updatePartyLastInteraction(partyId: Long, millis: Long) {
        partiesFlow.value = partiesFlow.value.map {
            if (it.partyId == partyId) it.copy(lastInteractionMillis = millis) else it
        }
    }

    override suspend fun updatePartyLanguage(partyId: Long, language: String) {
        partiesFlow.value = partiesFlow.value.map {
            if (it.partyId == partyId) it.copy(language = language) else it
        }
    }

    override suspend fun updatePartyQuietHoursOverride(partyId: Long, override: Boolean) {
        partiesFlow.value = partiesFlow.value.map {
            if (it.partyId == partyId) it.copy(quietHoursOverride = override) else it
        }
    }

    override suspend fun deleteAllParties() {
        partiesFlow.value = emptyList()
    }

    // --- Transactions ---
    override fun getAllTransactions(): Flow<List<UdhaarTransaction>> = txFlow.asStateFlow()

    override suspend fun getAllTransactionsSnapshot(): List<UdhaarTransaction> = txFlow.value

    override suspend fun getTransactionById(id: Long): UdhaarTransaction? =
        txFlow.value.firstOrNull { it.id == id }

    override suspend fun getTransactionBySyncId(syncId: String): UdhaarTransaction? =
        txFlow.value.firstOrNull { it.syncId == syncId }

    override suspend fun getTransactionByRemoteId(remoteId: String): UdhaarTransaction? =
        txFlow.value.firstOrNull { it.remoteId == remoteId }

    override fun getTransactionsWithGuestUpdates(): Flow<List<UdhaarTransaction>> =
        txFlow.map { list ->
            list.filter {
                it.pendingPaymentClaim.isNotBlank() ||
                it.pendingDateProposal.isNotBlank() ||
                it.lastGuestAction.isNotBlank()
            }
        }

    override suspend fun insertTransaction(transaction: UdhaarTransaction): Long {
        val current = txFlow.value.toMutableList()
        val assignedId = if (transaction.id > 0L) transaction.id else txIdGen.getAndIncrement()
        val item = transaction.copy(id = assignedId)
        current.removeAll { it.id == assignedId }
        current.add(item)
        txFlow.value = current
        return assignedId
    }

    override suspend fun updateTransaction(transaction: UdhaarTransaction) {
        val current = txFlow.value.toMutableList()
        val idx = current.indexOfFirst { it.id == transaction.id }
        if (idx >= 0) {
            current[idx] = transaction
        } else {
            current.add(transaction)
        }
        txFlow.value = current
    }

    override suspend fun deleteTransaction(transaction: UdhaarTransaction) {
        deleteTransactionById(transaction.id)
    }

    override suspend fun deleteTransactionById(id: Long) {
        txFlow.value = txFlow.value.filterNot { it.id == id }
    }

    override suspend fun deleteAllTransactions() {
        txFlow.value = emptyList()
    }

    override suspend fun updateTransactionSnooze(id: Long, snoozedUntilMillis: Long?) {
        txFlow.value = txFlow.value.map {
            if (it.id == id) it.copy(snoozedUntilMillis = snoozedUntilMillis) else it
        }
    }

    override suspend fun updateTransactionRemindersDisabled(id: Long, disabled: Boolean) {
        txFlow.value = txFlow.value.map {
            if (it.id == id) it.copy(remindersDisabled = disabled) else it
        }
    }

    override suspend fun updateTransactionLastReminder(id: Long, sentMillis: Long, level: Int) {
        txFlow.value = txFlow.value.map {
            if (it.id == id) it.copy(lastReminderSentMillis = sentMillis, lastReminderLevel = level) else it
        }
    }

    // --- Partial Payments ---
    override fun getPartialPaymentsForTransaction(transactionId: Long): Flow<List<PartialPayment>> =
        paymentsFlow.map { list -> list.filter { it.transactionId == transactionId } }

    override suspend fun getPartialPaymentsForTransactionSnapshot(transactionId: Long): List<PartialPayment> =
        paymentsFlow.value.filter { it.transactionId == transactionId }

    override suspend fun getPartialPaymentBySyncId(syncId: String): PartialPayment? =
        paymentsFlow.value.firstOrNull { it.syncId == syncId }

    override fun getAllPartialPayments(): Flow<List<PartialPayment>> = paymentsFlow.asStateFlow()

    override suspend fun getAllPartialPaymentsSnapshot(): List<PartialPayment> = paymentsFlow.value

    override suspend fun insertPartialPayment(payment: PartialPayment): Long {
        val current = paymentsFlow.value.toMutableList()
        val assignedId = if (payment.id > 0L) payment.id else paymentIdGen.getAndIncrement()
        val item = payment.copy(id = assignedId)
        current.removeAll { it.id == assignedId }
        current.add(item)
        paymentsFlow.value = current
        return assignedId
    }

    override suspend fun updatePartialPayment(payment: PartialPayment) {
        val current = paymentsFlow.value.toMutableList()
        val idx = current.indexOfFirst { it.id == payment.id }
        if (idx >= 0) {
            current[idx] = payment
        } else {
            current.add(payment)
        }
        paymentsFlow.value = current
    }

    override suspend fun deletePartialPayment(payment: PartialPayment) {
        paymentsFlow.value = paymentsFlow.value.filterNot { it.id == payment.id }
    }

    override suspend fun deletePartialPaymentsForTransaction(transactionId: Long) {
        paymentsFlow.value = paymentsFlow.value.filterNot { it.transactionId == transactionId }
    }

    override suspend fun deleteAllPartialPayments() {
        paymentsFlow.value = emptyList()
    }

    // --- Audit Trail ---
    override fun getAuditTrailForTransaction(transactionId: Long): Flow<List<AuditTrailEntry>> =
        auditFlow.map { list -> list.filter { it.transactionId == transactionId } }

    override suspend fun getAuditTrailForTransactionSnapshot(transactionId: Long): List<AuditTrailEntry> =
        auditFlow.value.filter { it.transactionId == transactionId }

    override fun getAllAuditTrailEntries(): Flow<List<AuditTrailEntry>> = auditFlow.asStateFlow()

    override suspend fun getAllAuditTrailEntriesSnapshot(): List<AuditTrailEntry> = auditFlow.value

    override suspend fun insertAuditTrailEntry(entry: AuditTrailEntry): Long {
        val current = auditFlow.value.toMutableList()
        val assignedId = if (entry.id > 0L) entry.id else auditIdGen.getAndIncrement()
        val item = entry.copy(id = assignedId)
        current.removeAll { it.id == assignedId }
        current.add(item)
        auditFlow.value = current
        return assignedId
    }

    override suspend fun deleteAuditTrailForTransaction(transactionId: Long) {
        auditFlow.value = auditFlow.value.filterNot { it.transactionId == transactionId }
    }

    override suspend fun deleteAllAuditTrail() {
        auditFlow.value = emptyList()
    }

    // --- Chat ---
    override fun getAllChatThreadMetadata(): Flow<List<ChatThreadMetadata>> = chatThreadsFlow.asStateFlow()

    override suspend fun getAllChatThreadsSnapshot(): List<ChatThreadMetadata> = chatThreadsFlow.value

    override suspend fun getChatThreadMetadata(threadKey: String): ChatThreadMetadata? =
        chatThreadsFlow.value.firstOrNull { it.threadKey == threadKey }

    override suspend fun upsertChatThreadMetadata(metadata: ChatThreadMetadata) {
        val current = chatThreadsFlow.value.toMutableList()
        current.removeAll { it.threadKey == metadata.threadKey }
        current.add(metadata)
        chatThreadsFlow.value = current
    }

    override fun getAllChatMessages(): Flow<List<ChatMessageEntity>> = chatMessagesFlow.asStateFlow()

    override fun getChatMessagesForThread(threadKey: String): Flow<List<ChatMessageEntity>> =
        chatMessagesFlow.map { list -> list.filter { it.threadKey == threadKey } }

    override suspend fun getChatMessagesForThreadSnapshot(threadKey: String): List<ChatMessageEntity> =
        chatMessagesFlow.value.filter { it.threadKey == threadKey }

    override suspend fun getChatMessageById(id: Long): ChatMessageEntity? =
        chatMessagesFlow.value.firstOrNull { it.id == id }

    override suspend fun getChatMessageByRemoteId(remoteMessageId: String): ChatMessageEntity? =
        chatMessagesFlow.value.firstOrNull { it.remoteMessageId == remoteMessageId }

    override suspend fun insertChatMessage(message: ChatMessageEntity): Long {
        val current = chatMessagesFlow.value.toMutableList()
        val assignedId = if (message.id > 0L) message.id else msgIdGen.getAndIncrement()
        val item = message.copy(id = assignedId)
        current.removeAll { it.id == assignedId }
        current.add(item)
        chatMessagesFlow.value = current
        return assignedId
    }

    override suspend fun updateChatMessage(message: ChatMessageEntity) {
        val current = chatMessagesFlow.value.toMutableList()
        val idx = current.indexOfFirst { it.id == message.id }
        if (idx >= 0) {
            current[idx] = message
        } else {
            current.add(message)
        }
        chatMessagesFlow.value = current
    }

    override suspend fun markThreadMessagesReadForMe(threadKey: String, readAtMillis: Long) {
        chatMessagesFlow.value = chatMessagesFlow.value.map {
            if (it.threadKey == threadKey && it.isUnreadForMe) {
                it.copy(isUnreadForMe = false, deliveryStatus = com.jyotirmoyb.udhaar.data.MessageDeliveryStatus.READ, readAtMillis = readAtMillis)
            } else {
                it
            }
        }
    }

    override suspend fun getAllChatMessagesSnapshot(): List<ChatMessageEntity> = chatMessagesFlow.value

    override suspend fun deleteChatMessagesForTransaction(transactionId: Long) {
        chatMessagesFlow.value = chatMessagesFlow.value.filterNot { it.transactionId == transactionId }
    }

    override suspend fun deleteChatThreadsForTransaction(transactionId: Long) {
        chatThreadsFlow.value = chatThreadsFlow.value.filterNot { it.transactionId == transactionId }
    }

    override suspend fun deleteAllChatThreads() {
        chatThreadsFlow.value = emptyList()
    }

    override suspend fun deleteAllChatMessages() {
        chatMessagesFlow.value = emptyList()
    }

    // --- Attachments ---
    override suspend fun insertAttachment(attachment: Attachment): Long {
        val current = attachmentsFlow.value.toMutableList()
        val assignedId = if (attachment.id > 0L) attachment.id else attachmentIdGen.getAndIncrement()
        val item = attachment.copy(id = assignedId)
        current.removeAll { it.id == assignedId }
        current.add(item)
        attachmentsFlow.value = current
        return assignedId
    }

    override suspend fun insertAttachments(attachments: List<Attachment>): List<Long> {
        return attachments.map { insertAttachment(it) }
    }

    override suspend fun deleteAttachment(attachment: Attachment) {
        deleteAttachmentById(attachment.id)
    }

    override suspend fun deleteAttachmentById(id: Long) {
        attachmentsFlow.value = attachmentsFlow.value.filterNot { it.id == id }
    }

    override fun getAttachmentsForTransaction(transactionId: Long): Flow<List<Attachment>> =
        attachmentsFlow.map { list -> list.filter { it.transactionId == transactionId } }

    override suspend fun getAttachmentsForTransactionSnapshot(transactionId: Long): List<Attachment> =
        attachmentsFlow.value.filter { it.transactionId == transactionId }

    override fun getAttachmentsForPayment(paymentId: Long): Flow<List<Attachment>> =
        attachmentsFlow.map { list -> list.filter { it.paymentId == paymentId } }

    override suspend fun getAttachmentsForPaymentSnapshot(paymentId: Long): List<Attachment> =
        attachmentsFlow.value.filter { it.paymentId == paymentId }

    override fun getAllAttachmentsFlow(): Flow<List<Attachment>> = attachmentsFlow.asStateFlow()

    override suspend fun getAllAttachmentsSnapshot(): List<Attachment> = attachmentsFlow.value

    override suspend fun deleteAttachmentsForTransaction(transactionId: Long) {
        attachmentsFlow.value = attachmentsFlow.value.filterNot { it.transactionId == transactionId }
    }

    override suspend fun deleteAllAttachments() {
        attachmentsFlow.value = emptyList()
    }

    // --- Reminder Events ---
    override suspend fun insertReminderEvent(event: ReminderEvent): Long {
        val current = reminderEventsFlow.value.toMutableList()
        val assignedId = if (event.id > 0L) event.id else reminderIdGen.getAndIncrement()
        val item = event.copy(id = assignedId)
        current.removeAll { it.id == assignedId }
        current.add(item)
        reminderEventsFlow.value = current
        return assignedId
    }

    override fun getReminderEventsForRecord(recordId: Long): Flow<List<ReminderEvent>> =
        reminderEventsFlow.map { list -> list.filter { it.recordId == recordId } }

    override suspend fun getReminderEventsForRecordSnapshot(recordId: Long): List<ReminderEvent> =
        reminderEventsFlow.value.filter { it.recordId == recordId }

    override suspend fun deleteAllReminderEvents() {
        reminderEventsFlow.value = emptyList()
    }

    // --- Installment Plans & Installments ---
    override fun getInstallmentPlanForTransaction(transactionId: Long): Flow<InstallmentPlan?> =
        plansFlow.map { list -> list.firstOrNull { it.transactionId == transactionId } }

    override suspend fun getInstallmentPlanForTransactionSnapshot(transactionId: Long): InstallmentPlan? =
        plansFlow.value.firstOrNull { it.transactionId == transactionId }

    override suspend fun getInstallmentPlanById(planId: Long): InstallmentPlan? =
        plansFlow.value.firstOrNull { it.id == planId }

    override fun getAllInstallmentPlans(): Flow<List<InstallmentPlan>> = plansFlow.asStateFlow()

    override suspend fun getAllInstallmentPlansSnapshot(): List<InstallmentPlan> = plansFlow.value

    override suspend fun insertInstallmentPlan(plan: InstallmentPlan): Long {
        val current = plansFlow.value.toMutableList()
        val assignedId = if (plan.id > 0L) plan.id else planIdGen.getAndIncrement()
        val item = plan.copy(id = assignedId)
        current.removeAll { it.id == assignedId || it.transactionId == plan.transactionId }
        current.add(item)
        plansFlow.value = current
        return assignedId
    }

    override suspend fun updateInstallmentPlan(plan: InstallmentPlan) {
        val current = plansFlow.value.toMutableList()
        val idx = current.indexOfFirst { it.id == plan.id }
        if (idx >= 0) {
            current[idx] = plan
        } else {
            current.add(plan)
        }
        plansFlow.value = current
    }

    override suspend fun deleteInstallmentPlan(plan: InstallmentPlan) {
        deleteInstallmentPlanById(plan.id)
    }

    override suspend fun deleteInstallmentPlanForTransaction(transactionId: Long) {
        val matchingPlan = plansFlow.value.firstOrNull { it.transactionId == transactionId }
        if (matchingPlan != null) {
            deleteInstallmentPlanById(matchingPlan.id)
        }
    }

    override suspend fun deleteInstallmentPlanById(planId: Long) {
        plansFlow.value = plansFlow.value.filterNot { it.id == planId }
        deleteInstallmentsForPlan(planId)
    }

    override fun getInstallmentsForPlan(planId: Long): Flow<List<Installment>> =
        installmentsFlow.map { list -> list.filter { it.planId == planId }.sortedBy { it.sequence } }

    override suspend fun getInstallmentsForPlanSnapshot(planId: Long): List<Installment> =
        installmentsFlow.value.filter { it.planId == planId }.sortedBy { it.sequence }

    override fun getAllInstallments(): Flow<List<Installment>> =
        installmentsFlow.map { list -> list.sortedWith(compareBy({ it.planId }, { it.sequence })) }

    override suspend fun getAllInstallmentsSnapshot(): List<Installment> =
        installmentsFlow.value.sortedWith(compareBy({ it.planId }, { it.sequence }))

    override suspend fun insertInstallment(installment: Installment): Long {
        val current = installmentsFlow.value.toMutableList()
        val assignedId = if (installment.id > 0L) installment.id else installmentIdGen.getAndIncrement()
        val item = installment.copy(id = assignedId)
        current.removeAll { it.id == assignedId }
        current.add(item)
        installmentsFlow.value = current
        return assignedId
    }

    override suspend fun insertInstallments(installments: List<Installment>): List<Long> {
        return installments.map { insertInstallment(it) }
    }

    override suspend fun updateInstallment(installment: Installment) {
        val current = installmentsFlow.value.toMutableList()
        val idx = current.indexOfFirst { it.id == installment.id }
        if (idx >= 0) {
            current[idx] = installment
        } else {
            current.add(installment)
        }
        installmentsFlow.value = current
    }

    override suspend fun updateInstallments(installments: List<Installment>) {
        installments.forEach { updateInstallment(it) }
    }

    override suspend fun deleteInstallment(installment: Installment) {
        installmentsFlow.value = installmentsFlow.value.filterNot { it.id == installment.id }
    }

    override suspend fun deleteInstallmentsForPlan(planId: Long) {
        installmentsFlow.value = installmentsFlow.value.filterNot { it.planId == planId }
    }

    override suspend fun deleteAllInstallmentPlans() {
        plansFlow.value = emptyList()
    }

    override suspend fun deleteAllInstallments() {
        installmentsFlow.value = emptyList()
    }
}
