package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

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
    entities = [UdhaarTransaction::class, PartialPayment::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(UdhaarTypeConverters::class)
abstract class UdhaarDatabase : RoomDatabase() {
    abstract fun udhaarDao(): UdhaarDao

    companion object {
        @Volatile
        private var INSTANCE: UdhaarDatabase? = null

        fun getInstance(context: Context): UdhaarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UdhaarDatabase::class.java,
                    "udhaar_ledger.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
