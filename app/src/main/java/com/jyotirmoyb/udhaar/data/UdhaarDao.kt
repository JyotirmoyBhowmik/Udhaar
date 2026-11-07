package com.jyotirmoyb.udhaar.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UdhaarDao {
    // --- Party Directory Queries ---
    @Query("SELECT * FROM parties ORDER BY name COLLATE NOCASE ASC")
    fun getAllParties(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllPartiesSnapshot(): List<PartyEntity>

    @Query("SELECT * FROM parties WHERE partyId = :partyId LIMIT 1")
    suspend fun getPartyById(partyId: Long): PartyEntity?

    @Query("SELECT * FROM parties WHERE syncId = :syncId LIMIT 1")
    suspend fun getPartyBySyncId(syncId: String): PartyEntity?

    @Query("SELECT * FROM parties WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getPartyByName(name: String): PartyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParty(party: PartyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParties(parties: List<PartyEntity>): List<Long>

    @Update
    suspend fun updateParty(party: PartyEntity)

    @Delete
    suspend fun deleteParty(party: PartyEntity)

    @Query("DELETE FROM parties WHERE partyId = :partyId")
    suspend fun deletePartyById(partyId: Long)

    @Query("UPDATE udhaar_transactions SET partyId = :targetPartyId, partyName = :targetPartyName WHERE partyId = :sourcePartyId")
    suspend fun relinkTransactionsForPartyId(sourcePartyId: Long, targetPartyId: Long, targetPartyName: String)

    @Query("UPDATE chat_messages SET partyId = :targetPartyId, partyName = :targetPartyName WHERE partyId = :sourcePartyId")
    suspend fun relinkChatMessagesForPartyId(sourcePartyId: Long, targetPartyId: Long, targetPartyName: String)

    @Query("UPDATE chat_thread_metadata SET partyId = :targetPartyId, partyName = :targetPartyName WHERE partyId = :sourcePartyId")
    suspend fun relinkChatThreadsForPartyId(sourcePartyId: Long, targetPartyId: Long, targetPartyName: String)

    @Query("UPDATE parties SET isFavorite = :isFavorite WHERE partyId = :partyId")
    suspend fun setPartyFavorite(partyId: Long, isFavorite: Boolean)

    @Query("UPDATE parties SET lastInteractionMillis = :millis WHERE partyId = :partyId")
    suspend fun updatePartyLastInteraction(partyId: Long, millis: Long)

    // --- Udhaar Transaction Queries ---
    @Query("SELECT * FROM udhaar_transactions ORDER BY isSettled ASC, createdAtMillis DESC")
    fun getAllTransactions(): Flow<List<UdhaarTransaction>>

    @Query("SELECT * FROM udhaar_transactions ORDER BY createdAtMillis DESC")
    suspend fun getAllTransactionsSnapshot(): List<UdhaarTransaction>

    @Query("SELECT * FROM udhaar_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): UdhaarTransaction?

    @Query("SELECT * FROM udhaar_transactions WHERE syncId = :syncId LIMIT 1")
    suspend fun getTransactionBySyncId(syncId: String): UdhaarTransaction?

    @Query("SELECT * FROM udhaar_transactions WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getTransactionByRemoteId(remoteId: String): UdhaarTransaction?

    @Query("SELECT * FROM udhaar_transactions WHERE pendingPaymentClaim != '' OR pendingDateProposal != '' OR lastGuestAction != '' ORDER BY createdAtMillis DESC")
    fun getTransactionsWithGuestUpdates(): Flow<List<UdhaarTransaction>>

    @Query("SELECT * FROM udhaar_transactions WHERE syncStatus = 'CONFLICT' ORDER BY createdAtMillis DESC")
    fun getConflictedTransactions(): Flow<List<UdhaarTransaction>>

    @Query("SELECT * FROM udhaar_transactions WHERE syncStatus = 'CONFLICT' ORDER BY createdAtMillis DESC")
    suspend fun getConflictedTransactionsSnapshot(): List<UdhaarTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: UdhaarTransaction): Long

    @Update
    suspend fun updateTransaction(transaction: UdhaarTransaction)

    @Delete
    suspend fun deleteTransaction(transaction: UdhaarTransaction)

    @Query("DELETE FROM udhaar_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    // --- Partial Payment Queries ---
    @Query("SELECT * FROM partial_payments WHERE transactionId = :transactionId ORDER BY paidAtMillis DESC")
    fun getPartialPaymentsForTransaction(transactionId: Long): Flow<List<PartialPayment>>

    @Query("SELECT * FROM partial_payments WHERE transactionId = :transactionId ORDER BY paidAtMillis DESC")
    suspend fun getPartialPaymentsForTransactionSnapshot(transactionId: Long): List<PartialPayment>

    @Query("SELECT * FROM partial_payments WHERE syncId = :syncId LIMIT 1")
    suspend fun getPartialPaymentBySyncId(syncId: String): PartialPayment?

    @Query("SELECT * FROM partial_payments ORDER BY paidAtMillis DESC")
    fun getAllPartialPayments(): Flow<List<PartialPayment>>

    @Query("SELECT * FROM partial_payments ORDER BY paidAtMillis DESC")
    suspend fun getAllPartialPaymentsSnapshot(): List<PartialPayment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartialPayment(payment: PartialPayment): Long

    @Update
    suspend fun updatePartialPayment(payment: PartialPayment)

    @Delete
    suspend fun deletePartialPayment(payment: PartialPayment)

    // --- Audit Trail Queries ---
    @Query("SELECT * FROM audit_trail_entries WHERE transactionId = :transactionId ORDER BY eventTimeMillis ASC")
    fun getAuditTrailForTransaction(transactionId: Long): Flow<List<AuditTrailEntry>>

    @Query("SELECT * FROM audit_trail_entries WHERE transactionId = :transactionId ORDER BY eventTimeMillis ASC")
    suspend fun getAuditTrailForTransactionSnapshot(transactionId: Long): List<AuditTrailEntry>

    @Query("SELECT * FROM audit_trail_entries ORDER BY eventTimeMillis ASC")
    fun getAllAuditTrailEntries(): Flow<List<AuditTrailEntry>>

    @Query("SELECT * FROM audit_trail_entries ORDER BY eventTimeMillis ASC")
    suspend fun getAllAuditTrailEntriesSnapshot(): List<AuditTrailEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditTrailEntry(entry: AuditTrailEntry): Long

    // --- Phase 4: In-App Communication Queries (Threads & Messages) ---
    @Query("SELECT * FROM chat_thread_metadata")
    fun getAllChatThreadMetadata(): Flow<List<ChatThreadMetadata>>

    fun getAllChatThreads(): Flow<List<ChatThreadMetadata>> = getAllChatThreadMetadata()

    @Query("SELECT * FROM chat_thread_metadata ORDER BY updatedAtMillis DESC")
    suspend fun getAllChatThreadsSnapshot(): List<ChatThreadMetadata>

    @Query("SELECT * FROM chat_thread_metadata WHERE threadKey = :threadKey LIMIT 1")
    suspend fun getChatThreadMetadata(threadKey: String): ChatThreadMetadata?

    suspend fun getChatThreadByKey(threadKey: String): ChatThreadMetadata? = getChatThreadMetadata(threadKey)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatThreadMetadata(metadata: ChatThreadMetadata)

    @Query("SELECT * FROM chat_messages ORDER BY sentAtMillis ASC")
    fun getAllChatMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE threadKey = :threadKey ORDER BY sentAtMillis ASC")
    fun getChatMessagesForThread(threadKey: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE threadKey = :threadKey ORDER BY sentAtMillis ASC")
    suspend fun getChatMessagesForThreadSnapshot(threadKey: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :id LIMIT 1")
    suspend fun getChatMessageById(id: Long): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE remoteMessageId = :remoteMessageId AND remoteMessageId != '' LIMIT 1")
    suspend fun getChatMessageByRemoteId(remoteMessageId: String): ChatMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    @Update
    suspend fun updateChatMessage(message: ChatMessageEntity)

    @Query("UPDATE chat_messages SET isUnreadForMe = 0, deliveryStatus = 'READ', readAtMillis = :readAtMillis WHERE threadKey = :threadKey AND isUnreadForMe = 1")
    suspend fun markThreadMessagesReadForMe(threadKey: String, readAtMillis: Long)

    suspend fun markThreadMessagesRead(threadKey: String, readAtMillis: Long) =
        markThreadMessagesReadForMe(threadKey, readAtMillis)

    @Query("SELECT * FROM chat_messages ORDER BY sentAtMillis ASC")
    suspend fun getAllChatMessagesSnapshot(): List<ChatMessageEntity>

    // --- Phase 6: Documents & Attachments ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: Attachment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<Attachment>): List<Long>

    @Delete
    suspend fun deleteAttachment(attachment: Attachment)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachmentById(id: Long)

    @Query("SELECT * FROM attachments WHERE transactionId = :transactionId ORDER BY addedAtMillis ASC")
    fun getAttachmentsForTransaction(transactionId: Long): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE transactionId = :transactionId ORDER BY addedAtMillis ASC")
    suspend fun getAttachmentsForTransactionSnapshot(transactionId: Long): List<Attachment>

    @Query("SELECT * FROM attachments WHERE paymentId = :paymentId ORDER BY addedAtMillis ASC")
    fun getAttachmentsForPayment(paymentId: Long): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE paymentId = :paymentId ORDER BY addedAtMillis ASC")
    suspend fun getAttachmentsForPaymentSnapshot(paymentId: Long): List<Attachment>

    @Query("SELECT * FROM attachments ORDER BY addedAtMillis ASC")
    fun getAllAttachmentsFlow(): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments ORDER BY addedAtMillis ASC")
    suspend fun getAllAttachmentsSnapshot(): List<Attachment>

    @Query("DELETE FROM attachments WHERE transactionId = :transactionId")
    suspend fun deleteAttachmentsForTransaction(transactionId: Long)

    @Query("DELETE FROM partial_payments WHERE transactionId = :transactionId")
    suspend fun deletePartialPaymentsForTransaction(transactionId: Long)

    @Query("DELETE FROM audit_trail_entries WHERE transactionId = :transactionId")
    suspend fun deleteAuditTrailForTransaction(transactionId: Long)

    @Query("DELETE FROM chat_messages WHERE transactionId = :transactionId")
    suspend fun deleteChatMessagesForTransaction(transactionId: Long)

    @Query("DELETE FROM chat_thread_metadata WHERE transactionId = :transactionId")
    suspend fun deleteChatThreadsForTransaction(transactionId: Long)

    @Query("DELETE FROM attachments")
    suspend fun deleteAllAttachments()

    @Query("DELETE FROM udhaar_transactions")
    suspend fun deleteAllTransactions()

    @Query("DELETE FROM parties")
    suspend fun deleteAllParties()

    @Query("DELETE FROM partial_payments")
    suspend fun deleteAllPartialPayments()

    @Query("DELETE FROM audit_trail_entries")
    suspend fun deleteAllAuditTrail()

    @Query("DELETE FROM chat_thread_metadata")
    suspend fun deleteAllChatThreads()

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllChatMessages()

    // --- Reminder Event & Snooze Queries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminderEvent(event: ReminderEvent): Long

    @Query("SELECT * FROM reminder_events WHERE recordId = :recordId ORDER BY sentTimeMillis DESC")
    fun getReminderEventsForRecord(recordId: Long): Flow<List<ReminderEvent>>

    @Query("SELECT * FROM reminder_events WHERE recordId = :recordId ORDER BY sentTimeMillis DESC")
    suspend fun getReminderEventsForRecordSnapshot(recordId: Long): List<ReminderEvent>

    @Query("UPDATE udhaar_transactions SET snoozedUntilMillis = :snoozedUntilMillis WHERE id = :id")
    suspend fun updateTransactionSnooze(id: Long, snoozedUntilMillis: Long?)

    @Query("UPDATE udhaar_transactions SET remindersDisabled = :disabled WHERE id = :id")
    suspend fun updateTransactionRemindersDisabled(id: Long, disabled: Boolean)

    @Query("UPDATE udhaar_transactions SET lastReminderSentMillis = :sentMillis, lastReminderLevel = :level WHERE id = :id")
    suspend fun updateTransactionLastReminder(id: Long, sentMillis: Long, level: Int)

    @Query("UPDATE parties SET language = :language WHERE partyId = :partyId")
    suspend fun updatePartyLanguage(partyId: Long, language: String)

    @Query("UPDATE parties SET quietHoursOverride = :override WHERE partyId = :partyId")
    suspend fun updatePartyQuietHoursOverride(partyId: Long, override: Boolean)

    @Query("DELETE FROM reminder_events")
    suspend fun deleteAllReminderEvents()

    // --- Installment Plans & Installments ---
    @Query("SELECT * FROM installment_plans WHERE transactionId = :transactionId LIMIT 1")
    fun getInstallmentPlanForTransaction(transactionId: Long): Flow<InstallmentPlan?>

    @Query("SELECT * FROM installment_plans WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getInstallmentPlanForTransactionSnapshot(transactionId: Long): InstallmentPlan?

    @Query("SELECT * FROM installment_plans WHERE id = :planId LIMIT 1")
    suspend fun getInstallmentPlanById(planId: Long): InstallmentPlan?

    @Query("SELECT * FROM installment_plans")
    fun getAllInstallmentPlans(): Flow<List<InstallmentPlan>>

    @Query("SELECT * FROM installment_plans")
    suspend fun getAllInstallmentPlansSnapshot(): List<InstallmentPlan>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallmentPlan(plan: InstallmentPlan): Long

    @Update
    suspend fun updateInstallmentPlan(plan: InstallmentPlan)

    @Delete
    suspend fun deleteInstallmentPlan(plan: InstallmentPlan)

    @Query("DELETE FROM installment_plans WHERE transactionId = :transactionId")
    suspend fun deleteInstallmentPlanForTransaction(transactionId: Long)

    @Query("DELETE FROM installment_plans WHERE id = :planId")
    suspend fun deleteInstallmentPlanById(planId: Long)

    @Query("SELECT * FROM installments WHERE planId = :planId ORDER BY sequence ASC")
    fun getInstallmentsForPlan(planId: Long): Flow<List<Installment>>

    @Query("SELECT * FROM installments WHERE planId = :planId ORDER BY sequence ASC")
    suspend fun getInstallmentsForPlanSnapshot(planId: Long): List<Installment>

    @Query("SELECT * FROM installments ORDER BY planId ASC, sequence ASC")
    fun getAllInstallments(): Flow<List<Installment>>

    @Query("SELECT * FROM installments ORDER BY planId ASC, sequence ASC")
    suspend fun getAllInstallmentsSnapshot(): List<Installment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallment(installment: Installment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallments(installments: List<Installment>): List<Long>

    @Update
    suspend fun updateInstallment(installment: Installment)

    @Update
    suspend fun updateInstallments(installments: List<Installment>)

    @Delete
    suspend fun deleteInstallment(installment: Installment)

    @Query("DELETE FROM installments WHERE planId = :planId")
    suspend fun deleteInstallmentsForPlan(planId: Long)

    @Query("DELETE FROM installment_plans")
    suspend fun deleteAllInstallmentPlans()

    @Query("DELETE FROM installments")
    suspend fun deleteAllInstallments()

    @Transaction
    suspend fun clearAllLedgerData() {
        deleteAllAttachments()
        deleteAllPartialPayments()
        deleteAllAuditTrail()
        deleteAllChatMessages()
        deleteAllChatThreads()
        deleteAllReminderEvents()
        deleteAllInstallments()
        deleteAllInstallmentPlans()
        deleteAllTransactions()
        deleteAllParties()
    }
}

