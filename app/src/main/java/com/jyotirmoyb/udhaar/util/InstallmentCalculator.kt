package com.jyotirmoyb.udhaar.util

import com.jyotirmoyb.udhaar.data.Installment
import com.jyotirmoyb.udhaar.data.InstallmentFrequency
import com.jyotirmoyb.udhaar.data.InstallmentInterestMode
import com.jyotirmoyb.udhaar.data.InstallmentPlan
import com.jyotirmoyb.udhaar.data.InstallmentPlanStatus
import com.jyotirmoyb.udhaar.data.InstallmentStatus
import java.util.Calendar
import kotlin.math.roundToLong

data class InstallmentDraft(
    val sequence: Int,
    var dueDateMillis: Long,
    var amountDuePaise: Long
)

data class AllocationResult(
    val updatedInstallments: List<Installment>,
    val remainingUnallocatedPaise: Long,
    val isPlanFullySettled: Boolean
)

data class InstallmentProgress(
    val totalCount: Int,
    val paidCount: Int,
    val totalDuePaise: Long,
    val totalPaidPaise: Long,
    val progressFraction: Float,
    val nextDueInstallment: Installment?,
    val hasOverdue: Boolean
)

object InstallmentCalculator {

    /**
     * Converts a decimal currency amount to the smallest unit (paise/cents).
     */
    fun toPaise(amount: Double): Long = (amount * 100.0).roundToLong()

    /**
     * Converts paise/cents back to a decimal currency amount.
     */
    fun paiseToDouble(paise: Long): Double = paise / 100.0

    /**
     * Computes the total amount due including flat interest if enabled.
     */
    fun calculateTotalAmountWithInterest(
        principalPaise: Long,
        interestMode: InstallmentInterestMode,
        flatInterestRate: Double
    ): Long {
        return if (interestMode == InstallmentInterestMode.FLAT && flatInterestRate > 0.0) {
            val interestPaise = (principalPaise * (flatInterestRate / 100.0)).roundToLong()
            principalPaise + interestPaise
        } else {
            principalPaise
        }
    }

    /**
     * Computes the due date for the i-th installment (1-based sequence).
     */
    fun computeDueDate(
        startDateMillis: Long,
        frequency: InstallmentFrequency,
        sequence: Int // 1-based
    ): Long {
        if (sequence <= 1) return startDateMillis
        val cal = Calendar.getInstance().apply { timeInMillis = startDateMillis }
        val offset = sequence - 1
        when (frequency) {
            InstallmentFrequency.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, offset * 7)
            InstallmentFrequency.EVERY_2_WEEKS -> cal.add(Calendar.DAY_OF_YEAR, offset * 14)
            InstallmentFrequency.MONTHLY, InstallmentFrequency.CUSTOM -> cal.add(Calendar.MONTH, offset)
        }
        return cal.timeInMillis
    }

    /**
     * Generates an equal split of [totalPaise] into [count] installments.
     * The last installment absorbs any remainder from integer division,
     * guaranteeing that the sum of amounts equals [totalPaise] exactly.
     */
    fun generateEqualSplit(
        totalPaise: Long,
        count: Int,
        startDateMillis: Long,
        frequency: InstallmentFrequency
    ): List<InstallmentDraft> {
        val safeCount = count.coerceAtLeast(1)
        val basePaise = totalPaise / safeCount
        val remainder = totalPaise % safeCount

        return (1..safeCount).map { sequence ->
            val due = computeDueDate(startDateMillis, frequency, sequence)
            val amt = if (sequence == safeCount) basePaise + remainder else basePaise
            InstallmentDraft(
                sequence = sequence,
                dueDateMillis = due,
                amountDuePaise = amt
            )
        }
    }

    /**
     * Validates that the sum of draft installments matches the expected total.
     */
    fun validateInstallmentsSum(
        installments: List<InstallmentDraft>,
        expectedTotalPaise: Long
    ): Boolean {
        if (installments.isEmpty()) return false
        val sum = installments.sumOf { it.amountDuePaise }
        return sum == expectedTotalPaise
    }

    /**
     * Re-evaluates status of an installment relative to [nowMillis].
     */
    fun computeInstallmentStatus(
        installment: Installment,
        nowMillis: Long = System.currentTimeMillis()
    ): InstallmentStatus {
        return when {
            installment.amountPaid >= installment.amountDue -> InstallmentStatus.PAID
            nowMillis > installment.dueDate -> InstallmentStatus.LATE
            installment.amountPaid > 0L -> InstallmentStatus.PARTIAL
            else -> InstallmentStatus.PENDING
        }
    }

    /**
     * Allocates a payment amount in paise across a list of installments.
     * Applies to the oldest unpaid/partial installment first, spilling over into subsequent installments.
     */
    fun allocatePayment(
        installments: List<Installment>,
        paymentAmountPaise: Long,
        paymentId: Long? = null,
        paidAtMillis: Long = System.currentTimeMillis()
    ): AllocationResult {
        if (paymentAmountPaise <= 0L || installments.isEmpty()) {
            return AllocationResult(
                updatedInstallments = installments,
                remainingUnallocatedPaise = 0L,
                isPlanFullySettled = installments.all { it.amountPaid >= it.amountDue }
            )
        }

        var remaining = paymentAmountPaise
        val sorted = installments.sortedBy { it.sequence }.map { it.copy() }.toMutableList()

        for (i in sorted.indices) {
            val inst = sorted[i]
            val needed = (inst.amountDue - inst.amountPaid).coerceAtLeast(0L)
            if (needed <= 0L) continue // Already paid

            val allocation = minOf(remaining, needed)
            val newPaid = inst.amountPaid + allocation
            val isPaidNow = newPaid >= inst.amountDue

            sorted[i] = inst.copy(
                amountPaid = newPaid,
                status = if (isPaidNow) InstallmentStatus.PAID else InstallmentStatus.PARTIAL,
                paidAtMillis = if (isPaidNow && inst.paidAtMillis == null) paidAtMillis else inst.paidAtMillis,
                paymentId = paymentId ?: inst.paymentId
            )

            remaining -= allocation
            if (remaining <= 0L) break
        }

        val allPaid = sorted.all { it.amountPaid >= it.amountDue }
        return AllocationResult(
            updatedInstallments = sorted,
            remainingUnallocatedPaise = remaining,
            isPlanFullySettled = allPaid
        )
    }

    /**
     * Calculates the overall progress of an installment plan.
     */
    fun computeProgress(
        installments: List<Installment>,
        nowMillis: Long = System.currentTimeMillis()
    ): InstallmentProgress {
        val totalCount = installments.size
        val paidCount = installments.count { it.status == InstallmentStatus.PAID || it.amountPaid >= it.amountDue }
        val totalDue = installments.sumOf { it.amountDue }
        val totalPaid = installments.sumOf { it.amountPaid }
        val fraction = if (totalDue > 0L) (totalPaid.toFloat() / totalDue.toFloat()).coerceIn(0f, 1f) else 0f

        val sorted = installments.sortedBy { it.sequence }
        val nextDue = sorted.firstOrNull { it.status != InstallmentStatus.PAID && it.amountPaid < it.amountDue }
        val hasOverdue = sorted.any {
            (it.status == InstallmentStatus.LATE) || (it.amountPaid < it.amountDue && nowMillis > it.dueDate)
        }

        return InstallmentProgress(
            totalCount = totalCount,
            paidCount = paidCount,
            totalDuePaise = totalDue,
            totalPaidPaise = totalPaid,
            progressFraction = fraction,
            nextDueInstallment = nextDue,
            hasOverdue = hasOverdue
        )
    }
}
