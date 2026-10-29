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
}

@Database(
    entities = [
        PartyEntity::class,
        UdhaarTransaction::class,
        PartialPayment::class,
        AuditTrailEntry::class
    ],
    version = 4,
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

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4
        )

        @Volatile
        private var INSTANCE: UdhaarDatabase? = null

        fun getInstance(context: Context): UdhaarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UdhaarDatabase::class.java,
                    "udhaar_ledger.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
