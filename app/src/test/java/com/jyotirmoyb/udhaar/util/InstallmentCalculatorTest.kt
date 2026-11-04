package com.jyotirmoyb.udhaar.util

import com.jyotirmoyb.udhaar.data.Installment
import com.jyotirmoyb.udhaar.data.InstallmentFrequency
import com.jyotirmoyb.udhaar.data.InstallmentInterestMode
import com.jyotirmoyb.udhaar.data.InstallmentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallmentCalculatorTest {

    @Test
    fun equalSplitWithRounding_sumMatchesTotalPaiseExactly() {
        // ₹1,000.00 split 3 ways: 100,000 paise / 3 = 33,333 paise each with 1 paise remainder
        val totalPaise = 100_000L
        val drafts = InstallmentCalculator.generateEqualSplit(
            totalPaise = totalPaise,
            count = 3,
            startDateMillis = 1_700_000_000_000L,
            frequency = InstallmentFrequency.MONTHLY
        )

        assertEquals(3, drafts.size)
        assertEquals(33_333L, drafts[0].amountDuePaise)
        assertEquals(33_333L, drafts[1].amountDuePaise)
        assertEquals(33_334L, drafts[2].amountDuePaise) // absorbs remainder
        assertEquals(totalPaise, drafts.sumOf { it.amountDuePaise })
        assertTrue(InstallmentCalculator.validateInstallmentsSum(drafts, totalPaise))
    }

    @Test
    fun unevenSplit_validation() {
        val totalPaise = 10_000_00L // ₹10,000
        val validDrafts = listOf(
            InstallmentDraft(sequence = 1, dueDateMillis = 1_000L, amountDuePaise = 5_000_00L),
            InstallmentDraft(sequence = 2, dueDateMillis = 2_000L, amountDuePaise = 3_000_00L),
            InstallmentDraft(sequence = 3, dueDateMillis = 3_000L, amountDuePaise = 2_000_00L)
        )
        assertTrue(InstallmentCalculator.validateInstallmentsSum(validDrafts, totalPaise))

        val invalidDrafts = listOf(
            InstallmentDraft(sequence = 1, dueDateMillis = 1_000L, amountDuePaise = 5_000_00L),
            InstallmentDraft(sequence = 2, dueDateMillis = 2_000L, amountDuePaise = 3_000_00L),
            InstallmentDraft(sequence = 3, dueDateMillis = 3_000L, amountDuePaise = 1_000_00L) // Sum = 9,000 != 10,000
        )
        assertFalse(InstallmentCalculator.validateInstallmentsSum(invalidDrafts, totalPaise))
    }

    @Test
    fun flatInterestComputation_computesCorrectTotal() {
        val principalPaise = 50_000_00L // ₹50,000
        val withInterest = InstallmentCalculator.calculateTotalAmountWithInterest(
            principalPaise = principalPaise,
            interestMode = InstallmentInterestMode.FLAT,
            flatInterestRate = 10.0 // 10% flat interest
        )
        assertEquals(55_000_00L, withInterest) // ₹55,000

        val withoutInterest = InstallmentCalculator.calculateTotalAmountWithInterest(
            principalPaise = principalPaise,
            interestMode = InstallmentInterestMode.NONE,
            flatInterestRate = 10.0
        )
        assertEquals(50_000_00L, withoutInterest)
    }

    @Test
    fun paymentAllocation_allocatesOldestFirstAndSpillsOver() {
        val installments = listOf(
            Installment(id = 1L, planId = 10L, sequence = 1, dueDate = 1_000L, amountDue = 1_000_00L, amountPaid = 0L),
            Installment(id = 2L, planId = 10L, sequence = 2, dueDate = 2_000L, amountDue = 1_000_00L, amountPaid = 0L),
            Installment(id = 3L, planId = 10L, sequence = 3, dueDate = 3_000L, amountDue = 1_000_00L, amountPaid = 0L)
        )

        // Payment of ₹1,500 (150,000 paise): should pay installment 1 fully (₹1,000) and half of installment 2 (₹500)
        val result = InstallmentCalculator.allocatePayment(
            installments = installments,
            paymentAmountPaise = 150_000L,
            paymentId = 99L,
            paidAtMillis = 1_500L
        )

        val updated = result.updatedInstallments
        assertEquals(3, updated.size)

        // Installment 1 fully paid
        assertEquals(1_000_00L, updated[0].amountPaid)
        assertEquals(InstallmentStatus.PAID, updated[0].status)
        assertEquals(99L, updated[0].paymentId)
        assertEquals(1_500L, updated[0].paidAtMillis)

        // Installment 2 partially paid
        assertEquals(500_00L, updated[1].amountPaid)
        assertEquals(InstallmentStatus.PARTIAL, updated[1].status)

        // Installment 3 untouched
        assertEquals(0L, updated[2].amountPaid)
        assertEquals(InstallmentStatus.PENDING, updated[2].status)

        assertEquals(0L, result.remainingUnallocatedPaise)
        assertFalse(result.isPlanFullySettled)
    }

    @Test
    fun advancePayment_payingAllInstallments_settlesPlan() {
        val installments = listOf(
            Installment(id = 1L, planId = 10L, sequence = 1, dueDate = 1_000L, amountDue = 1_000_00L, amountPaid = 0L),
            Installment(id = 2L, planId = 10L, sequence = 2, dueDate = 2_000L, amountDue = 1_000_00L, amountPaid = 0L)
        )

        // Pay ₹2,500 (250,000 paise) in advance: both installments paid, ₹500 unallocated remainder
        val result = InstallmentCalculator.allocatePayment(
            installments = installments,
            paymentAmountPaise = 250_000L
        )

        assertTrue(result.isPlanFullySettled)
        assertEquals(InstallmentStatus.PAID, result.updatedInstallments[0].status)
        assertEquals(InstallmentStatus.PAID, result.updatedInstallments[1].status)
        assertEquals(500_00L, result.remainingUnallocatedPaise)
    }

    @Test
    fun computeInstallmentStatus_evaluatesLateWhenOverdue() {
        val now = 2_000_000L
        val overdueInst = Installment(
            id = 1L, planId = 1L, sequence = 1, dueDate = 1_000_000L, amountDue = 500_00L, amountPaid = 0L
        )
        val status = InstallmentCalculator.computeInstallmentStatus(overdueInst, nowMillis = now)
        assertEquals(InstallmentStatus.LATE, status)

        val paidInst = overdueInst.copy(amountPaid = 500_00L)
        assertEquals(InstallmentStatus.PAID, InstallmentCalculator.computeInstallmentStatus(paidInst, nowMillis = now))

        val futureInst = overdueInst.copy(dueDate = 3_000_000L)
        assertEquals(InstallmentStatus.PENDING, InstallmentCalculator.computeInstallmentStatus(futureInst, nowMillis = now))
    }

    @Test
    fun computeProgress_computesFractionNextDueAndOverdue() {
        val installments = listOf(
            Installment(id = 1L, planId = 1L, sequence = 1, dueDate = 1_000L, amountDue = 1_000_00L, amountPaid = 1_000_00L, status = InstallmentStatus.PAID),
            Installment(id = 2L, planId = 1L, sequence = 2, dueDate = 2_000L, amountDue = 1_000_00L, amountPaid = 0L, status = InstallmentStatus.PENDING),
            Installment(id = 3L, planId = 1L, sequence = 3, dueDate = 3_000L, amountDue = 1_000_00L, amountPaid = 0L, status = InstallmentStatus.PENDING)
        )

        val progress = InstallmentCalculator.computeProgress(installments, nowMillis = 1_500L)
        assertEquals(3, progress.totalCount)
        assertEquals(1, progress.paidCount)
        assertEquals(3_000_00L, progress.totalDuePaise)
        assertEquals(1_000_00L, progress.totalPaidPaise)
        assertEquals(0.333f, progress.progressFraction, 0.01f)
        assertNotNull(progress.nextDueInstallment)
        assertEquals(2, progress.nextDueInstallment?.sequence)
        assertFalse(progress.hasOverdue)
    }

    @Test
    fun movePaymentBetweenInstallments_movesPaidAmountToTargetInstallment() {
        val installments = listOf(
            Installment(
                id = 1L,
                planId = 10L,
                sequence = 1,
                dueDate = 5_000L,
                amountDue = 1_000_00L,
                amountPaid = 1_000_00L,
                status = InstallmentStatus.PAID,
                paidAtMillis = 1_200L,
                paymentId = 42L
            ),
            Installment(
                id = 2L,
                planId = 10L,
                sequence = 2,
                dueDate = 10_000L,
                amountDue = 1_000_00L,
                amountPaid = 0L,
                status = InstallmentStatus.PENDING
            )
        )

        val moved = InstallmentCalculator.movePaymentBetweenInstallments(
            installments = installments,
            fromSequence = 1,
            toSequence = 2,
            nowMillis = 2_000L
        )

        assertEquals(0L, moved[0].amountPaid)
        assertEquals(InstallmentStatus.PENDING, moved[0].status)
        assertEquals(1_000_00L, moved[1].amountPaid)
        assertEquals(InstallmentStatus.PAID, moved[1].status)
        assertEquals(42L, moved[1].paymentId)
    }
}
