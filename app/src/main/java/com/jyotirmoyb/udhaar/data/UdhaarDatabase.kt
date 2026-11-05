package com.jyotirmoyb.udhaar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class UdhaarTypeConverters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType =
        runCatching { TransactionType.valueOf(value) }.getOrDefault(TransactionType.GIVEN)

    @TypeConverter
    fun fromCurrencyType(value: CurrencyType): String = value.name

    @TypeConverter
    fun toCurrencyType(value: String): CurrencyType =
        runCatching { CurrencyType.valueOf(value) }.getOrDefault(CurrencyType.INR)

    @TypeConverter
    fun fromPaymentMode(value: PaymentMode): String = value.name

    @TypeConverter
    fun toPaymentMode(value: String): PaymentMode =
        runCatching { PaymentMode.valueOf(value) }.getOrDefault(PaymentMode.CASH)

    @TypeConverter
    fun fromInterestType(value: InterestType): String = value.name

    @TypeConverter
    fun toInterestType(value: String): InterestType =
        runCatching { InterestType.valueOf(value) }.getOrDefault(InterestType.SIMPLE)

    @TypeConverter
    fun fromInterestRatePeriod(value: InterestRatePeriod): String = value.name

    @TypeConverter
    fun toInterestRatePeriod(value: String): InterestRatePeriod =
        runCatching { InterestRatePeriod.valueOf(value) }.getOrDefault(InterestRatePeriod.PER_MONTH)

    @TypeConverter
    fun fromCompoundFrequency(value: CompoundFrequency): String = value.name

    @TypeConverter
    fun toCompoundFrequency(value: String): CompoundFrequency =
        runCatching { CompoundFrequency.valueOf(value) }.getOrDefault(CompoundFrequency.MONTHLY)

    @TypeConverter
    fun fromVerificationStatus(value: VerificationStatus): String = value.name

    @TypeConverter
    fun toVerificationStatus(value: String): VerificationStatus =
        runCatching { VerificationStatus.valueOf(value) }.getOrDefault(VerificationStatus.LOCAL_ONLY)

    @TypeConverter
    fun fromAuditEventType(value: AuditEventType): String = value.name

    @TypeConverter
    fun toAuditEventType(value: String): AuditEventType =
        runCatching { AuditEventType.valueOf(value) }.getOrDefault(AuditEventType.CREATED)

    @TypeConverter
    fun fromChatThreadScope(value: ChatThreadScope): String = value.name

    @TypeConverter
    fun toChatThreadScope(value: String): ChatThreadScope =
        runCatching { ChatThreadScope.valueOf(value) }.getOrDefault(ChatThreadScope.TRANSACTION)

    @TypeConverter
    fun fromChatMessageType(value: ChatMessageType): String = value.name

    @TypeConverter
    fun toChatMessageType(value: String): ChatMessageType =
        runCatching { ChatMessageType.valueOf(value) }.getOrDefault(ChatMessageType.TEXT)

    @TypeConverter
    fun fromMessageDeliveryStatus(value: MessageDeliveryStatus): String = value.name

    @TypeConverter
    fun toMessageDeliveryStatus(value: String): MessageDeliveryStatus =
        runCatching { MessageDeliveryStatus.valueOf(value) }.getOrDefault(MessageDeliveryStatus.SENT)

    @TypeConverter
    fun fromExtensionRequestStatus(value: ExtensionRequestStatus): String = value.name

    @TypeConverter
    fun toExtensionRequestStatus(value: String): ExtensionRequestStatus =
        runCatching { ExtensionRequestStatus.valueOf(value) }.getOrDefault(ExtensionRequestStatus.NONE)

    @TypeConverter
    fun fromReminderChannel(value: ReminderChannel): String = value.name

    @TypeConverter
    fun toReminderChannel(value: String): ReminderChannel =
        runCatching { ReminderChannel.valueOf(value) }.getOrDefault(ReminderChannel.NOTIFICATION)

    @TypeConverter
    fun fromInstallmentFrequency(value: InstallmentFrequency): String = value.name

    @TypeConverter
    fun toInstallmentFrequency(value: String): InstallmentFrequency =
        runCatching { InstallmentFrequency.valueOf(value) }.getOrDefault(InstallmentFrequency.MONTHLY)

    @TypeConverter
    fun fromInstallmentInterestMode(value: InstallmentInterestMode): String = value.name

    @TypeConverter
    fun toInstallmentInterestMode(value: String): InstallmentInterestMode =
        runCatching { InstallmentInterestMode.valueOf(value) }.getOrDefault(InstallmentInterestMode.NONE)

    @TypeConverter
    fun fromInstallmentPlanStatus(value: InstallmentPlanStatus): String = value.name

    @TypeConverter
    fun toInstallmentPlanStatus(value: String): InstallmentPlanStatus =
        runCatching { InstallmentPlanStatus.valueOf(value) }.getOrDefault(InstallmentPlanStatus.ACTIVE)

    @TypeConverter
    fun fromInstallmentStatus(value: InstallmentStatus): String = value.name

    @TypeConverter
    fun toInstallmentStatus(value: String): InstallmentStatus =
        runCatching { InstallmentStatus.valueOf(value) }.getOrDefault(InstallmentStatus.PENDING)
}

@Database(
    entities = [
        PartyEntity::class,
        UdhaarTransaction::class,
        PartialPayment::class,
        AuditTrailEntry::class,
        ChatThreadMetadata::class,
        ChatMessageEntity::class,
        Attachment::class,
        ReminderEvent::class,
        InstallmentPlan::class,
        Installment::class
    ],
    version = 12,
    exportSchema = true
)
@TypeConverters(UdhaarTypeConverters::class)
abstract class UdhaarDatabase : RoomDatabase() {
    abstract fun udhaarDao(): UdhaarDao

    companion object {
        private fun hasColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
            db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex < 0) return false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                        return true
                    }
                }
            }
            return false
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `parties` (
                        `partyId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `phoneNumber` TEXT NOT NULL,
                        `upiId` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `tagOrNotes` TEXT NOT NULL,
                        `preferredCurrency` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                if (!hasColumn(db, "udhaar_transactions", "partyId")) {
                    db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `partyId` INTEGER")
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "partial_payments", "paymentMode")) {
                    db.execSQL(
                        "ALTER TABLE `partial_payments` ADD COLUMN `paymentMode` TEXT NOT NULL DEFAULT 'CASH'"
                    )
                }
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "udhaar_transactions", "remoteId")) {
                    db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `remoteId` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "udhaar_transactions", "verificationStatus")) {
                    db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `verificationStatus` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                }
                if (!hasColumn(db, "udhaar_transactions", "verifiedAtMillis")) {
                    db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `verifiedAtMillis` INTEGER")
                }
                if (!hasColumn(db, "udhaar_transactions", "counterpartyUserId")) {
                    db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `counterpartyUserId` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "udhaar_transactions", "contentHash")) {
                    db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `contentHash` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "udhaar_transactions", "needsMyConfirmation")) {
                    db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `needsMyConfirmation` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "udhaar_transactions", "shareToken")) {
                    db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `shareToken` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "udhaar_transactions", "disputeReason")) {
                    db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `disputeReason` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "udhaar_transactions", "lenderName")) {
                    db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `lenderName` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "udhaar_transactions", "borrowerName")) {
                    db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `borrowerName` TEXT NOT NULL DEFAULT ''")
                }

                if (!hasColumn(db, "partial_payments", "remotePaymentId")) {
                    db.execSQL("ALTER TABLE `partial_payments` ADD COLUMN `remotePaymentId` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "partial_payments", "recordedByUserId")) {
                    db.execSQL("ALTER TABLE `partial_payments` ADD COLUMN `recordedByUserId` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "partial_payments", "recordedByName")) {
                    db.execSQL("ALTER TABLE `partial_payments` ADD COLUMN `recordedByName` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "partial_payments", "isAcknowledged")) {
                    db.execSQL("ALTER TABLE `partial_payments` ADD COLUMN `isAcknowledged` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "partial_payments", "acknowledgedAtMillis")) {
                    db.execSQL("ALTER TABLE `partial_payments` ADD COLUMN `acknowledgedAtMillis` INTEGER")
                }
                if (!hasColumn(db, "partial_payments", "needsMyAcknowledgement")) {
                    db.execSQL("ALTER TABLE `partial_payments` ADD COLUMN `needsMyAcknowledgement` INTEGER NOT NULL DEFAULT 0")
                }

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `audit_trail_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transactionId` INTEGER NOT NULL,
                        `remoteEventId` TEXT NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `actorUserId` TEXT NOT NULL,
                        `actorName` TEXT NOT NULL,
                        `summaryText` TEXT NOT NULL,
                        `contentHashSnapshot` TEXT NOT NULL,
                        `isAcknowledged` INTEGER NOT NULL,
                        `eventTimeMillis` INTEGER NOT NULL,
                        FOREIGN KEY(`transactionId`) REFERENCES `udhaar_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_audit_trail_entries_transactionId` ON `audit_trail_entries` (`transactionId`)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_thread_metadata` (
                        `threadKey` TEXT NOT NULL,
                        `remoteThreadId` TEXT NOT NULL,
                        `threadScope` TEXT NOT NULL,
                        `transactionId` INTEGER,
                        `partyId` INTEGER,
                        `partyName` TEXT NOT NULL,
                        `isMuted` INTEGER NOT NULL,
                        `isBlocked` INTEGER NOT NULL,
                        `isReported` INTEGER NOT NULL,
                        `reportReason` TEXT NOT NULL,
                        `counterpartyTyping` INTEGER NOT NULL,
                        `typingUpdatedAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`threadKey`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `remoteMessageId` TEXT NOT NULL,
                        `threadKey` TEXT NOT NULL,
                        `threadScope` TEXT NOT NULL,
                        `transactionId` INTEGER,
                        `partyId` INTEGER,
                        `partyName` TEXT NOT NULL,
                        `senderUserId` TEXT NOT NULL,
                        `senderName` TEXT NOT NULL,
                        `isFromMe` INTEGER NOT NULL,
                        `messageType` TEXT NOT NULL,
                        `bodyText` TEXT NOT NULL,
                        `attachmentUri` TEXT NOT NULL,
                        `attachmentMimeType` TEXT NOT NULL,
                        `attachmentName` TEXT NOT NULL,
                        `deliveryStatus` TEXT NOT NULL,
                        `isUnreadForMe` INTEGER NOT NULL,
                        `proposedDueDateMillis` INTEGER,
                        `extensionStatus` TEXT NOT NULL,
                        `sentAtMillis` INTEGER NOT NULL,
                        `deliveredAtMillis` INTEGER,
                        `readAtMillis` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_threadKey` ON `chat_messages` (`threadKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_transactionId` ON `chat_messages` (`transactionId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_partyId` ON `chat_messages` (`partyId`)"
                )
            }
        }

        private fun rebuildAttachmentsTableWithExactSchema(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `attachments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `transactionId` INTEGER NOT NULL,
                    `paymentId` INTEGER,
                    `fileName` TEXT NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `localPath` TEXT NOT NULL,
                    `remoteUrl` TEXT,
                    `addedAtMillis` INTEGER NOT NULL,
                    `addedBy` TEXT NOT NULL,
                    `sha256` TEXT NOT NULL,
                    FOREIGN KEY(`transactionId`) REFERENCES `udhaar_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`paymentId`) REFERENCES `partial_payments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("DROP TABLE IF EXISTS `attachments_new`")
            db.execSQL(
                """
                CREATE TABLE `attachments_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `transactionId` INTEGER NOT NULL,
                    `paymentId` INTEGER,
                    `fileName` TEXT NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `localPath` TEXT NOT NULL,
                    `remoteUrl` TEXT,
                    `addedAtMillis` INTEGER NOT NULL,
                    `addedBy` TEXT NOT NULL,
                    `sha256` TEXT NOT NULL,
                    FOREIGN KEY(`transactionId`) REFERENCES `udhaar_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`paymentId`) REFERENCES `partial_payments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `attachments_new` (
                    `id`, `transactionId`, `paymentId`, `fileName`, `mimeType`,
                    `sizeBytes`, `localPath`, `remoteUrl`, `addedAtMillis`, `addedBy`, `sha256`
                )
                SELECT
                    `id`, `transactionId`, `paymentId`, `fileName`, `mimeType`,
                    `sizeBytes`, `localPath`, `remoteUrl`, `addedAtMillis`, `addedBy`, `sha256`
                FROM `attachments`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `attachments`")
            db.execSQL("ALTER TABLE `attachments_new` RENAME TO `attachments`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_attachments_transactionId` ON `attachments` (`transactionId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_attachments_paymentId` ON `attachments` (`paymentId`)"
            )
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                rebuildAttachmentsTableWithExactSchema(db)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "parties", "alternatePhoneNumber")) {
                    db.execSQL("ALTER TABLE `parties` ADD COLUMN `alternatePhoneNumber` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "parties", "notes")) {
                    db.execSQL("ALTER TABLE `parties` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "parties", "photoUri")) {
                    db.execSQL("ALTER TABLE `parties` ADD COLUMN `photoUri` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "parties", "linkedUserId")) {
                    db.execSQL("ALTER TABLE `parties` ADD COLUMN `linkedUserId` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "parties", "isFavorite")) {
                    db.execSQL("ALTER TABLE `parties` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "parties", "lastInteractionMillis")) {
                    db.execSQL("ALTER TABLE `parties` ADD COLUMN `lastInteractionMillis` INTEGER NOT NULL DEFAULT 0")
                }
                rebuildAttachmentsTableWithExactSchema(db)
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "parties", "language")) {
                    db.execSQL("ALTER TABLE `parties` ADD COLUMN `language` TEXT NOT NULL DEFAULT ''")
                }
                if (!hasColumn(db, "parties", "quietHoursOverride")) {
                    db.execSQL("ALTER TABLE `parties` ADD COLUMN `quietHoursOverride` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "udhaar_transactions", "remindersDisabled")) {
                    db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `remindersDisabled` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "udhaar_transactions", "snoozedUntilMillis")) {
                    db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `snoozedUntilMillis` INTEGER")
                }
                if (!hasColumn(db, "udhaar_transactions", "lastReminderSentMillis")) {
                    db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `lastReminderSentMillis` INTEGER")
                }
                if (!hasColumn(db, "udhaar_transactions", "lastReminderLevel")) {
                    db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `lastReminderLevel` INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminder_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `recordId` INTEGER NOT NULL,
                        `level` INTEGER NOT NULL,
                        `sentTimeMillis` INTEGER NOT NULL,
                        `channel` TEXT NOT NULL,
                        FOREIGN KEY(`recordId`) REFERENCES `udhaar_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminder_events_recordId` ON `reminder_events` (`recordId`)"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `installment_plans` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transactionId` INTEGER NOT NULL,
                        `installmentCount` INTEGER NOT NULL,
                        `frequency` TEXT NOT NULL,
                        `startDate` INTEGER NOT NULL,
                        `interestMode` TEXT NOT NULL,
                        `flatInterestRate` REAL NOT NULL,
                        `totalAmountDue` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `remotePlanId` TEXT NOT NULL,
                        `proposedScheduleJson` TEXT NOT NULL,
                        `scheduleProposalStatus` TEXT NOT NULL,
                        FOREIGN KEY(`transactionId`) REFERENCES `udhaar_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_installment_plans_transactionId` ON `installment_plans` (`transactionId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `installments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `planId` INTEGER NOT NULL,
                        `sequence` INTEGER NOT NULL,
                        `dueDate` INTEGER NOT NULL,
                        `amountDue` INTEGER NOT NULL,
                        `amountPaid` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `paidAtMillis` INTEGER,
                        `paymentId` INTEGER,
                        FOREIGN KEY(`planId`) REFERENCES `installment_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`paymentId`) REFERENCES `partial_payments`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_installments_planId` ON `installments` (`planId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_installments_paymentId` ON `installments` (`paymentId`)"
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `webLinkStatus` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `lastGuestAction` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `trustLevel` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `pendingPaymentClaim` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `pendingDateProposal` TEXT NOT NULL DEFAULT ''")
            }
        }

        private fun querySingleLong(db: SupportSQLiteDatabase, sql: String): Long {
            db.query(sql).use { cursor ->
                return if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
            }
        }

        private fun migrateToPaiseOnlyAndVerifyTotals(db: SupportSQLiteDatabase) {
            // 1. Ensure intermediate columns exist if migrating from an older test schema
            if (!hasColumn(db, "udhaar_transactions", "reminderDateMillis")) {
                db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `reminderDateMillis` INTEGER")
            }
            if (!hasColumn(db, "udhaar_transactions", "settledAtMillis")) {
                db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `settledAtMillis` INTEGER")
            }
            if (!hasColumn(db, "partial_payments", "paidAtMillis")) {
                db.execSQL("ALTER TABLE `partial_payments` ADD COLUMN `paidAtMillis` INTEGER NOT NULL DEFAULT 0")
                if (hasColumn(db, "partial_payments", "paymentDateMillis")) {
                    db.execSQL("UPDATE `partial_payments` SET `paidAtMillis` = `paymentDateMillis`")
                }
            }

            // 2. Populate paise columns from legacy Double (REAL) columns where needed
            val hasTxPrincipalDouble = hasColumn(db, "udhaar_transactions", "principalAmount")
            val hasTxPaidDouble = hasColumn(db, "udhaar_transactions", "amountPaid")
            val hasPaymentDouble = hasColumn(db, "partial_payments", "amount")

            if (!hasColumn(db, "udhaar_transactions", "principalAmountPaise")) {
                db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `principalAmountPaise` INTEGER NOT NULL DEFAULT 0")
            }
            if (hasTxPrincipalDouble) {
                db.execSQL(
                    """
                    UPDATE `udhaar_transactions`
                    SET `principalAmountPaise` = CASE
                        WHEN `principalAmountPaise` != 0 THEN `principalAmountPaise`
                        ELSE CAST(ROUND(`principalAmount` * 100.0) AS INTEGER)
                    END
                    """.trimIndent()
                )
            }

            if (!hasColumn(db, "udhaar_transactions", "amountPaidPaise")) {
                db.execSQL("ALTER TABLE `udhaar_transactions` ADD COLUMN `amountPaidPaise` INTEGER NOT NULL DEFAULT 0")
            }
            if (hasTxPaidDouble) {
                db.execSQL(
                    """
                    UPDATE `udhaar_transactions`
                    SET `amountPaidPaise` = CASE
                        WHEN `amountPaidPaise` != 0 THEN `amountPaidPaise`
                        ELSE CAST(ROUND(`amountPaid` * 100.0) AS INTEGER)
                    END
                    """.trimIndent()
                )
            }

            if (!hasColumn(db, "partial_payments", "amountPaise")) {
                db.execSQL("ALTER TABLE `partial_payments` ADD COLUMN `amountPaise` INTEGER NOT NULL DEFAULT 0")
            }
            if (hasPaymentDouble) {
                db.execSQL(
                    """
                    UPDATE `partial_payments`
                    SET `amountPaise` = CASE
                        WHEN `amountPaise` != 0 THEN `amountPaise`
                        ELSE CAST(ROUND(`amount` * 100.0) AS INTEGER)
                    END
                    """.trimIndent()
                )
            }

            // 3. Record pre-rebuild counts and paise sums for verification
            val expectedTxCount = querySingleLong(db, "SELECT COUNT(*) FROM `udhaar_transactions`")
            val expectedPrincipalPaiseSum = querySingleLong(
                db,
                "SELECT COALESCE(SUM(`principalAmountPaise`), 0) FROM `udhaar_transactions`"
            )
            val expectedPaidPaiseSum = querySingleLong(
                db,
                "SELECT COALESCE(SUM(`amountPaidPaise`), 0) FROM `udhaar_transactions`"
            )
            val expectedPaymentCount = querySingleLong(db, "SELECT COUNT(*) FROM `partial_payments`")
            val expectedPaymentPaiseSum = querySingleLong(
                db,
                "SELECT COALESCE(SUM(`amountPaise`), 0) FROM `partial_payments`"
            )

            // 4. Rebuild udhaar_transactions without principalAmount / amountPaid REAL columns
            db.execSQL("DROP TABLE IF EXISTS `udhaar_transactions_new`")
            db.execSQL(
                """
                CREATE TABLE `udhaar_transactions_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `partyId` INTEGER,
                    `transactionType` TEXT NOT NULL,
                    `partyName` TEXT NOT NULL,
                    `phoneNumber` TEXT NOT NULL,
                    `currency` TEXT NOT NULL,
                    `principalAmountPaise` INTEGER NOT NULL,
                    `amountPaidPaise` INTEGER NOT NULL,
                    `paymentMode` TEXT NOT NULL,
                    `isInterestEnabled` INTEGER NOT NULL,
                    `interestType` TEXT NOT NULL,
                    `interestRate` REAL NOT NULL,
                    `interestRatePeriod` TEXT NOT NULL,
                    `compoundFrequency` TEXT NOT NULL,
                    `startDateMillis` INTEGER NOT NULL,
                    `expiryDateMillis` INTEGER,
                    `reminderDateMillis` INTEGER,
                    `remindersDisabled` INTEGER NOT NULL,
                    `snoozedUntilMillis` INTEGER,
                    `lastReminderSentMillis` INTEGER,
                    `lastReminderLevel` INTEGER NOT NULL,
                    `notes` TEXT NOT NULL,
                    `isSettled` INTEGER NOT NULL,
                    `settledAtMillis` INTEGER,
                    `createdAtMillis` INTEGER NOT NULL,
                    `remoteId` TEXT NOT NULL,
                    `verificationStatus` TEXT NOT NULL,
                    `verifiedAtMillis` INTEGER,
                    `counterpartyUserId` TEXT NOT NULL,
                    `contentHash` TEXT NOT NULL,
                    `needsMyConfirmation` INTEGER NOT NULL,
                    `shareToken` TEXT NOT NULL,
                    `disputeReason` TEXT NOT NULL,
                    `lenderName` TEXT NOT NULL,
                    `borrowerName` TEXT NOT NULL,
                    `webLinkStatus` TEXT NOT NULL,
                    `lastGuestAction` TEXT NOT NULL,
                    `trustLevel` TEXT NOT NULL,
                    `pendingPaymentClaim` TEXT NOT NULL,
                    `pendingDateProposal` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `udhaar_transactions_new` (
                    `id`, `partyId`, `transactionType`, `partyName`, `phoneNumber`, `currency`,
                    `principalAmountPaise`, `amountPaidPaise`, `paymentMode`, `isInterestEnabled`,
                    `interestType`, `interestRate`, `interestRatePeriod`, `compoundFrequency`,
                    `startDateMillis`, `expiryDateMillis`, `reminderDateMillis`, `remindersDisabled`,
                    `snoozedUntilMillis`, `lastReminderSentMillis`, `lastReminderLevel`, `notes`,
                    `isSettled`, `settledAtMillis`, `createdAtMillis`, `remoteId`,
                    `verificationStatus`, `verifiedAtMillis`, `counterpartyUserId`, `contentHash`,
                    `needsMyConfirmation`, `shareToken`, `disputeReason`, `lenderName`,
                    `borrowerName`, `webLinkStatus`, `lastGuestAction`, `trustLevel`,
                    `pendingPaymentClaim`, `pendingDateProposal`
                )
                SELECT
                    `id`, `partyId`, `transactionType`, `partyName`, `phoneNumber`, `currency`,
                    `principalAmountPaise`, `amountPaidPaise`, `paymentMode`, `isInterestEnabled`,
                    `interestType`, `interestRate`, `interestRatePeriod`, `compoundFrequency`,
                    `startDateMillis`, `expiryDateMillis`, `reminderDateMillis`, `remindersDisabled`,
                    `snoozedUntilMillis`, `lastReminderSentMillis`, `lastReminderLevel`, `notes`,
                    `isSettled`, `settledAtMillis`, `createdAtMillis`, `remoteId`,
                    `verificationStatus`, `verifiedAtMillis`, `counterpartyUserId`, `contentHash`,
                    `needsMyConfirmation`, `shareToken`, `disputeReason`, `lenderName`,
                    `borrowerName`, `webLinkStatus`, `lastGuestAction`, `trustLevel`,
                    `pendingPaymentClaim`, `pendingDateProposal`
                FROM `udhaar_transactions`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `udhaar_transactions`")
            db.execSQL("ALTER TABLE `udhaar_transactions_new` RENAME TO `udhaar_transactions`")

            // 5. Rebuild partial_payments without amount REAL column
            db.execSQL("DROP TABLE IF EXISTS `partial_payments_new`")
            db.execSQL(
                """
                CREATE TABLE `partial_payments_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `transactionId` INTEGER NOT NULL,
                    `amountPaise` INTEGER NOT NULL,
                    `paymentMode` TEXT NOT NULL,
                    `paidAtMillis` INTEGER NOT NULL,
                    `note` TEXT NOT NULL,
                    `remotePaymentId` TEXT NOT NULL,
                    `recordedByUserId` TEXT NOT NULL,
                    `recordedByName` TEXT NOT NULL,
                    `isAcknowledged` INTEGER NOT NULL,
                    `acknowledgedAtMillis` INTEGER,
                    `needsMyAcknowledgement` INTEGER NOT NULL,
                    FOREIGN KEY(`transactionId`) REFERENCES `udhaar_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `partial_payments_new` (
                    `id`, `transactionId`, `amountPaise`, `paymentMode`, `paidAtMillis`, `note`,
                    `remotePaymentId`, `recordedByUserId`, `recordedByName`, `isAcknowledged`,
                    `acknowledgedAtMillis`, `needsMyAcknowledgement`
                )
                SELECT
                    `id`, `transactionId`, `amountPaise`, `paymentMode`, `paidAtMillis`, `note`,
                    `remotePaymentId`, `recordedByUserId`, `recordedByName`, `isAcknowledged`,
                    `acknowledgedAtMillis`, `needsMyAcknowledgement`
                FROM `partial_payments`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `partial_payments`")
            db.execSQL("ALTER TABLE `partial_payments_new` RENAME TO `partial_payments`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_partial_payments_transactionId` ON `partial_payments` (`transactionId`)"
            )

            // 6. Verify post-migration row counts and paise totals
            val actualTxCount = querySingleLong(db, "SELECT COUNT(*) FROM `udhaar_transactions`")
            val actualPrincipalPaiseSum = querySingleLong(
                db,
                "SELECT COALESCE(SUM(`principalAmountPaise`), 0) FROM `udhaar_transactions`"
            )
            val actualPaidPaiseSum = querySingleLong(
                db,
                "SELECT COALESCE(SUM(`amountPaidPaise`), 0) FROM `udhaar_transactions`"
            )
            val actualPaymentCount = querySingleLong(db, "SELECT COUNT(*) FROM `partial_payments`")
            val actualPaymentPaiseSum = querySingleLong(
                db,
                "SELECT COALESCE(SUM(`amountPaise`), 0) FROM `partial_payments`"
            )

            check(actualTxCount == expectedTxCount) {
                "Migration verification failed: transaction count mismatch ($actualTxCount != $expectedTxCount)"
            }
            check(actualPrincipalPaiseSum == expectedPrincipalPaiseSum) {
                "Migration verification failed: principalAmountPaise sum mismatch ($actualPrincipalPaiseSum != $expectedPrincipalPaiseSum)"
            }
            check(actualPaidPaiseSum == expectedPaidPaiseSum) {
                "Migration verification failed: amountPaidPaise sum mismatch ($actualPaidPaiseSum != $expectedPaidPaiseSum)"
            }
            check(actualPaymentCount == expectedPaymentCount) {
                "Migration verification failed: partial_payments count mismatch ($actualPaymentCount != $expectedPaymentCount)"
            }
            check(actualPaymentPaiseSum == expectedPaymentPaiseSum) {
                "Migration verification failed: partial_payments amountPaise sum mismatch ($actualPaymentPaiseSum != $expectedPaymentPaiseSum)"
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateToPaiseOnlyAndVerifyTotals(db)
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateToPaiseOnlyAndVerifyTotals(db)
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12
        )

        @Volatile
        private var INSTANCE: UdhaarDatabase? = null

        @Volatile
        private var isBlockedFallbackInstance: Boolean = false

        fun getInstance(context: Context): UdhaarDatabase {
            val current = INSTANCE
            if (current != null && !isBlockedFallbackInstance) {
                return current
            }
            return synchronized(this) {
                val existing = INSTANCE
                if (existing != null && !isBlockedFallbackInstance) {
                    return@synchronized existing
                }
                val appCtx = context.applicationContext
                val baseBuilder = Room.databaseBuilder(
                    appCtx,
                    UdhaarDatabase::class.java,
                    "udhaar_ledger.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .fallbackToDestructiveMigrationOnDowngrade()

                val configuredBuilder = com.jyotirmoyb.udhaar.security.EncryptedStorageManager
                    .configureSqlCipherForRoom(appCtx, baseBuilder, "udhaar_ledger.db")

                // If SQLCipher failed on a fresh install or existing encrypted database, do NOT
                // open an encrypted file with plain SQLite or create an unencrypted file on disk.
                // Return a temporary in-memory database so the UI can render the blocking error screen
                // with "Export backup" and "Try again".
                if (com.jyotirmoyb.udhaar.security.EncryptedStorageManager.blockingStartupErrorFlow.value != null) {
                    val fallback = existing ?: Room.inMemoryDatabaseBuilder(
                        appCtx,
                        UdhaarDatabase::class.java
                    ).allowMainThreadQueries().build()
                    INSTANCE = fallback
                    isBlockedFallbackInstance = true
                    fallback
                } else {
                    if (isBlockedFallbackInstance) {
                        runCatching { existing?.close() }
                    }
                    var instance = configuredBuilder.build()
                    if (!com.jyotirmoyb.udhaar.security.EncryptedStorageManager.isRunningOnJvmRobolectric()) {
                        try {
                            instance.openHelper.writableDatabase
                        } catch (t: Throwable) {
                            runCatching { instance.close() }
                            val msg = t.message.orEmpty()
                            val repaired = if (msg.contains("Room cannot verify the data integrity")) {
                                runCatching {
                                    val expectedHash = Regex("Expected identity hash:\\s*([0-9a-fA-F]+)")
                                        .find(msg)?.groupValues?.getOrNull(1)
                                    val factory = com.jyotirmoyb.udhaar.security.EncryptedStorageManager.createSqlCipherFactory(appCtx)
                                    val helper = factory.create(
                                        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(appCtx)
                                            .name("udhaar_ledger.db")
                                            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(12) {
                                                override fun onCreate(db: SupportSQLiteDatabase) {}
                                                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                                            })
                                            .build()
                                    )
                                    helper.writableDatabase.use { rawDb ->
                                        migrateToPaiseOnlyAndVerifyTotals(rawDb)
                                        if (!expectedHash.isNullOrBlank()) {
                                            rawDb.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                                            rawDb.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$expectedHash')")
                                        }
                                    }
                                    helper.close()
                                    val retryBuilder = Room.databaseBuilder(
                                        appCtx,
                                        UdhaarDatabase::class.java,
                                        "udhaar_ledger.db"
                                    )
                                        .addMigrations(*ALL_MIGRATIONS)
                                        .fallbackToDestructiveMigrationOnDowngrade()
                                    val retryConfigured = com.jyotirmoyb.udhaar.security.EncryptedStorageManager
                                        .configureSqlCipherForRoom(appCtx, retryBuilder, "udhaar_ledger.db")
                                    instance = retryConfigured.build()
                                    instance.openHelper.writableDatabase
                                    com.jyotirmoyb.udhaar.security.EncryptedStorageManager.clearBlockingStartupError()
                                    com.jyotirmoyb.udhaar.security.EncryptedStorageManager.setDbEncryptionState(
                                        appCtx,
                                        com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.ENCRYPTED
                                    )
                                    true
                                }.getOrDefault(false)
                            } else {
                                false
                            }

                            if (!repaired) {
                                com.jyotirmoyb.udhaar.security.EncryptedStorageManager.recordStartupOpenFailure(
                                    appCtx,
                                    t.message ?: t.javaClass.simpleName
                                )
                                val fallback = Room.inMemoryDatabaseBuilder(
                                    appCtx,
                                    UdhaarDatabase::class.java
                                ).allowMainThreadQueries().build()
                                INSTANCE = fallback
                                isBlockedFallbackInstance = true
                                return@synchronized fallback
                            }
                        }
                    }
                    INSTANCE = instance
                    isBlockedFallbackInstance = false
                    instance
                }
            }
        }

        fun retryDatabaseEncryption(context: Context): Boolean {
            synchronized(this) {
                runCatching { INSTANCE?.close() }
                INSTANCE = null
                isBlockedFallbackInstance = false
                val appCtx = context.applicationContext
                val migratedOrReady = com.jyotirmoyb.udhaar.security.EncryptedStorageManager
                    .retryDatabaseEncryption(appCtx, "udhaar_ledger.db")
                if (migratedOrReady) {
                    val db = getInstance(appCtx)
                    // Trigger open so SupportOpenHelperFactory / Room verifies the encrypted file
                    runCatching { db.openHelper.writableDatabase }
                }
                return com.jyotirmoyb.udhaar.security.EncryptedStorageManager
                    .getDatabaseEncryptionState(appCtx) ==
                    com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.ENCRYPTED &&
                    com.jyotirmoyb.udhaar.security.EncryptedStorageManager.blockingStartupErrorFlow.value == null
            }
        }

        fun resetInstanceForTesting() {
            synchronized(this) {
                runCatching { INSTANCE?.close() }
                INSTANCE = null
                isBlockedFallbackInstance = false
            }
        }
    }
}


