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
}

@Database(
    entities = [PartyEntity::class, UdhaarTransaction::class, PartialPayment::class],
    version = 3,
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

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3
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
