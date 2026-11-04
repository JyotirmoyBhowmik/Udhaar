package com.jyotirmoyb.udhaar.data.repository

import com.jyotirmoyb.udhaar.data.AuditEventType
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.InstallmentFrequency
import com.jyotirmoyb.udhaar.data.InstallmentInterestMode
import com.jyotirmoyb.udhaar.data.InstallmentPlan
import com.jyotirmoyb.udhaar.data.InstallmentPlanStatus
import com.jyotirmoyb.udhaar.data.InstallmentStatus
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.util.InstallmentCalculator
import com.jyotirmoyb.udhaar.util.InstallmentDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InstallmentRepositoryTest {

    private lateinit var fakeDao: FakeUdhaarDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var installmentRepository: InstallmentRepository

    @Before
    fun setup() {
        fakeDao = FakeUdhaarDao()
        settingsRepository = SettingsRepository(null, null)
        installmentRepository = InstallmentRepository(
            dao = fakeDao,
            settingsRepository = settingsRepository,
            cloudRepository = null
        )
    }

    @Test
    fun createOrUpdatePlan_insertsPlanAndInstallments_recomputesHashAndAudits() = runTest {
        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Priya",
                principalAmount = 3000.0,
                currency = CurrencyType.INR,
                verificationStatus = VerificationStatus.CONFIRMED
            )
        )
        val tx = fakeDao.getTransactionById(txId)!!

        val drafts = listOf(
            InstallmentDraft(sequence = 1, dueDateMillis = 1715000000000L, amountDuePaise = 100000L),
            InstallmentDraft(sequence = 2, dueDateMillis = 1717500000000L, amountDuePaise = 100000L),
            InstallmentDraft(sequence = 3, dueDateMillis = 1720000000000L, amountDuePaise = 100000L)
        )

        val plan = InstallmentPlan(
            transactionId = txId,
            installmentCount = 3,
            frequency = InstallmentFrequency.MONTHLY,
            startDate = 1715000000000L,
            interestMode = InstallmentInterestMode.NONE,
            totalAmountDue = 300000L
        )

        val planId = installmentRepository.createOrUpdatePlan(
            tx = tx,
            plan = plan,
            draftInstallments = drafts,
            myProfileName = "Alice"
        )
        assertTrue(planId > 0L)

        val insertedPlan = fakeDao.getInstallmentPlanById(planId)
        assertNotNull(insertedPlan)
        assertEquals(3, insertedPlan!!.installmentCount)
        assertEquals(InstallmentPlanStatus.ACTIVE, insertedPlan.status)

        val installments = fakeDao.getInstallmentsForPlanSnapshot(planId)
        assertEquals(3, installments.size)
        assertEquals(1, installments[0].sequence)
        assertEquals(100000L, installments[0].amountDue)

        // Verifies verificationStatus was reset to EDITED_AFTER_CONFIRM
        val updatedTx = fakeDao.getTransactionById(txId)!!
        assertEquals(VerificationStatus.EDITED_AFTER_CONFIRM, updatedTx.verificationStatus)
        assertTrue(updatedTx.contentHash.isNotBlank())

        val audit = fakeDao.getAuditTrailForTransactionSnapshot(txId)
        assertTrue(audit.any { it.eventType == AuditEventType.EDITED })
    }

    @Test
    fun applyPaymentToInstallments_allocatesToOldestFirst_andSpillsOver() = runTest {
        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Anil",
                principalAmount = 1500.0,
                currency = CurrencyType.INR
            )
        )
        val tx = fakeDao.getTransactionById(txId)!!

        val now = System.currentTimeMillis()
        val drafts = listOf(
            InstallmentDraft(sequence = 1, dueDateMillis = now + 86400000L * 7, amountDuePaise = 50000L),
            InstallmentDraft(sequence = 2, dueDateMillis = now + 86400000L * 14, amountDuePaise = 50000L),
            InstallmentDraft(sequence = 3, dueDateMillis = now + 86400000L * 21, amountDuePaise = 50000L)
        )
        val plan = InstallmentPlan(
            transactionId = txId,
            installmentCount = 3,
            frequency = InstallmentFrequency.MONTHLY,
            startDate = now,
            totalAmountDue = 150000L
        )
        val planId = installmentRepository.createOrUpdatePlan(tx, plan, drafts, "Bob")

        // Pay ₹750 (75000 paise) -> should fully pay #1 (50000) and partially pay #2 (25000)
        val result = installmentRepository.applyPaymentToInstallments(
            txId = txId,
            paymentAmountPaise = 75000L,
            paymentId = 101L,
            paidAtMillis = now
        )

        assertFalse(result.isPlanFullySettled)
        assertEquals(0L, result.remainingUnallocatedPaise)

        val updatedInstallments = fakeDao.getInstallmentsForPlanSnapshot(planId)
        assertEquals(InstallmentStatus.PAID, updatedInstallments[0].status)
        assertEquals(50000L, updatedInstallments[0].amountPaid)

        assertEquals(InstallmentStatus.PARTIAL, updatedInstallments[1].status)
        assertEquals(25000L, updatedInstallments[1].amountPaid)

        assertEquals(InstallmentStatus.PENDING, updatedInstallments[2].status)
        assertEquals(0L, updatedInstallments[2].amountPaid)

        // Pay remaining ₹750 (75000 paise) -> should complete #2 and #3, marking plan COMPLETED
        val result2 = installmentRepository.applyPaymentToInstallments(
            txId = txId,
            paymentAmountPaise = 75000L,
            paymentId = 102L,
            paidAtMillis = now + 86400000L * 2
        )
        assertTrue(result2.isPlanFullySettled)

        val finalPlan = fakeDao.getInstallmentPlanById(planId)!!
        assertEquals(InstallmentPlanStatus.COMPLETED, finalPlan.status)
    }

    @Test
    fun scheduleProposal_proposeAcceptAndDecline_workflow() = runTest {
        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Suresh",
                principalAmount = 2000.0,
                currency = CurrencyType.INR,
                verificationStatus = VerificationStatus.PENDING
            )
        )
        val tx = fakeDao.getTransactionById(txId)!!

        val initialDrafts = listOf(
            InstallmentDraft(sequence = 1, dueDateMillis = 1715000000000L, amountDuePaise = 100000L),
            InstallmentDraft(sequence = 2, dueDateMillis = 1717500000000L, amountDuePaise = 100000L)
        )
        val plan = InstallmentPlan(
            transactionId = txId,
            installmentCount = 2,
            frequency = InstallmentFrequency.MONTHLY,
            startDate = 1715000000000L,
            totalAmountDue = 200000L
        )
        val planId = installmentRepository.createOrUpdatePlan(tx, plan, initialDrafts, "Lender")

        // Propose new schedule (3 installments instead of 2)
        val proposedDrafts = listOf(
            InstallmentDraft(sequence = 1, dueDateMillis = 1716000000000L, amountDuePaise = 70000L),
            InstallmentDraft(sequence = 2, dueDateMillis = 1718000000000L, amountDuePaise = 70000L),
            InstallmentDraft(sequence = 3, dueDateMillis = 1720000000000L, amountDuePaise = 60000L)
        )
        installmentRepository.proposeNewSchedule(planId, txId, proposedDrafts, "Suresh")

        val proposedPlan = fakeDao.getInstallmentPlanById(planId)!!
        assertEquals("PENDING", proposedPlan.scheduleProposalStatus)
        assertTrue(proposedPlan.proposedScheduleJson.contains("70000"))

        val auditReq = fakeDao.getAuditTrailForTransactionSnapshot(txId)
        assertTrue(auditReq.any { it.eventType == AuditEventType.EXTENSION_REQUESTED })

        // Decline first to test decline flow
        installmentRepository.declineProposedSchedule(planId, txId, "Lender")
        val declinedPlan = fakeDao.getInstallmentPlanById(planId)!!
        assertEquals("DECLINED", declinedPlan.scheduleProposalStatus)
        assertTrue(declinedPlan.proposedScheduleJson.isEmpty())

        // Propose again and accept
        installmentRepository.proposeNewSchedule(planId, txId, proposedDrafts, "Suresh")
        installmentRepository.acceptProposedSchedule(planId, tx, "Lender")

        val acceptedPlan = fakeDao.getInstallmentPlanById(planId)!!
        assertEquals("ACCEPTED", acceptedPlan.scheduleProposalStatus)
        assertEquals(3, acceptedPlan.installmentCount)
        assertEquals(200000L, acceptedPlan.totalAmountDue)

        val updatedInstallments = fakeDao.getInstallmentsForPlanSnapshot(planId)
        assertEquals(3, updatedInstallments.size)
        assertEquals(70000L, updatedInstallments[0].amountDue)
        assertEquals(60000L, updatedInstallments[2].amountDue)

        val finalTx = fakeDao.getTransactionById(txId)!!
        assertEquals(VerificationStatus.CONFIRMED, finalTx.verificationStatus)
    }
}
