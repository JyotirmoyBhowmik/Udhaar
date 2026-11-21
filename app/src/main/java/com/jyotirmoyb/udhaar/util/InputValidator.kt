package com.jyotirmoyb.udhaar.util

import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction

/**
 * Enterprise shared input validator used across the UI and repository layers.
 * Enforces non-null assertions, strict boundaries, safe string lengths,
 * E.164 phone normalization, and integer paise range limits.
 */
object InputValidator {

    const val MIN_NAME_LENGTH = 1
    const val MAX_NAME_LENGTH = 100

    // Maximum transaction amount: ₹100,00,00,000 (100 Crore / 1 Billion INR = 100 Trillion paise)
    const val MAX_TRANSACTION_PAISE = 10_000_000_000_000_00L // 10^14 paise
    const val MIN_TRANSACTION_PAISE = 1L

    const val MAX_NOTE_LENGTH = 1000

    // Range bounds: timestamps cannot be before epoch 0 or beyond Jan 1, 2100 UTC
    const val MIN_EPOCH_MILLIS = 0L
    const val MAX_EPOCH_MILLIS = 4102444800000L

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()

        val isValid: Boolean get() = this is Valid
        val errorMessage: String? get() = (this as? Invalid)?.message
    }

    /**
     * Validates a party or user display name.
     */
    fun validatePartyName(name: String?): ValidationResult {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("Name cannot be blank.")
        }
        if (trimmed.length > MAX_NAME_LENGTH) {
            return ValidationResult.Invalid("Name cannot exceed $MAX_NAME_LENGTH characters.")
        }
        return ValidationResult.Valid
    }

    /**
     * Validates and normalizes a phone number to E.164 format.
     */
    fun validatePhoneNumber(
        phoneNumber: String?,
        currency: CurrencyType = CurrencyType.INR,
        isRequired: Boolean = false
    ): ValidationResult {
        val trimmed = phoneNumber?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return if (isRequired) ValidationResult.Invalid("Phone number is required.") else ValidationResult.Valid
        }

        val digits = trimmed.filter { it.isDigit() }
        if (digits.length < 7 || digits.length > 15) {
            return ValidationResult.Invalid("Phone number must contain between 7 and 15 digits.")
        }

        val e164 = InterestCalculator.normalizeToE164(trimmed, currency)
        if (e164.isBlank() || !e164.startsWith("+")) {
            return ValidationResult.Invalid("Phone number could not be normalised to valid standard format.")
        }

        return ValidationResult.Valid
    }

    /**
     * Validates monetary amounts strictly represented as positive integer [Long] paise.
     */
    fun validateAmountPaise(
        amountPaise: Long?,
        maxLimitPaise: Long = MAX_TRANSACTION_PAISE
    ): ValidationResult {
        if (amountPaise == null || amountPaise <= 0L) {
            return ValidationResult.Invalid("Amount must be greater than 0.")
        }
        if (amountPaise > maxLimitPaise) {
            return ValidationResult.Invalid("Amount exceeds maximum permitted limit.")
        }
        return ValidationResult.Valid
    }

    /**
     * Validates a timestamp value to prevent date overflow or invalid negative epochs.
     */
    fun validateDate(
        dateMillis: Long?,
        fieldName: String = "Date",
        isRequired: Boolean = false
    ): ValidationResult {
        if (dateMillis == null || dateMillis == 0L) {
            return if (isRequired) ValidationResult.Invalid("$fieldName is required.") else ValidationResult.Valid
        }
        if (dateMillis < MIN_EPOCH_MILLIS || dateMillis > MAX_EPOCH_MILLIS) {
            return ValidationResult.Invalid("$fieldName is outside the valid range (1970 - 2100).")
        }
        return ValidationResult.Valid
    }

    /**
     * Validates that due/expiry date is not earlier than the start date.
     */
    fun validateDateRange(startDateMillis: Long?, expiryDateMillis: Long?): ValidationResult {
        if (startDateMillis != null && startDateMillis > 0L && expiryDateMillis != null && expiryDateMillis > 0L) {
            if (expiryDateMillis < startDateMillis) {
                return ValidationResult.Invalid("Due date cannot be earlier than start date.")
            }
        }
        return ValidationResult.Valid
    }

    /**
     * Validates note string length.
     */
    fun validateNotes(notes: String?): ValidationResult {
        val trimmed = notes?.trim().orEmpty()
        if (trimmed.length > MAX_NOTE_LENGTH) {
            return ValidationResult.Invalid("Notes cannot exceed $MAX_NOTE_LENGTH characters.")
        }
        return ValidationResult.Valid
    }

    /**
     * Comprehensive boundary validation for a complete [UdhaarTransaction].
     */
    fun validateTransaction(transaction: UdhaarTransaction): ValidationResult {
        val nameRes = validatePartyName(transaction.partyName)
        if (!nameRes.isValid) return nameRes

        val phoneRes = validatePhoneNumber(transaction.phoneNumber, transaction.currency, isRequired = false)
        if (!phoneRes.isValid) return phoneRes

        val amtRes = validateAmountPaise(transaction.principalAmountPaise)
        if (!amtRes.isValid) return amtRes

        val startRes = validateDate(transaction.startDateMillis, "Start date")
        if (!startRes.isValid) return startRes

        val expiryRes = validateDate(transaction.expiryDateMillis, "Due date")
        if (!expiryRes.isValid) return expiryRes

        val rangeRes = validateDateRange(transaction.startDateMillis, transaction.expiryDateMillis)
        if (!rangeRes.isValid) return rangeRes

        val notesRes = validateNotes(transaction.notes)
        if (!notesRes.isValid) return notesRes

        return ValidationResult.Valid
    }
}
