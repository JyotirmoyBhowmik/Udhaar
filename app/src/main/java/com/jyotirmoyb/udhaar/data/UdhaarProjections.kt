package com.jyotirmoyb.udhaar.data

/**
 * Lightweight projection for Paging 3 history and search queries.
 * Avoids `SELECT *` over wide tables containing large notes/JSON blobs.
 */
data class TransactionHistoryProjection(
    val id: Long,
    val partyId: Long?,
    val transactionType: TransactionType,
    val partyName: String,
    val phoneNumber: String,
    val currency: CurrencyType,
    val principalAmountPaise: Long,
    val amountPaidPaise: Long,
    val paymentMode: PaymentMode,
    val startDateMillis: Long,
    val expiryDateMillis: Long?,
    val isSettled: Boolean,
    val settledAtMillis: Long?,
    val verificationStatus: VerificationStatus,
    val createdAtMillis: Long
)

/**
 * Lightweight projection for Paging 3 people search queries.
 */
data class PartySearchProjection(
    val partyId: Long,
    val name: String,
    val phoneNumber: String,
    val preferredCurrency: CurrencyType,
    val isFavorite: Boolean,
    val lastInteractionMillis: Long
)
