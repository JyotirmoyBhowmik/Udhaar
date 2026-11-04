package com.jyotirmoyb.udhaar.data.repository

import com.jyotirmoyb.udhaar.data.AuditEventType
import com.jyotirmoyb.udhaar.data.Installment
import com.jyotirmoyb.udhaar.data.InstallmentPlan
import com.jyotirmoyb.udhaar.data.InstallmentPlanStatus
import com.jyotirmoyb.udhaar.data.InstallmentStatus
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.util.AllocationResult
import com.jyotirmoyb.udhaar.util.AuditTrailHelper
import com.jyotirmoyb.udhaar.util.InstallmentCalculator
import com.jyotirmoyb.udhaar.util.InstallmentDraft
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.SafeLogger
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class InstallmentRepository @Inject constructor(
    private val dao: UdhaarDao,
    private val settingsRepository: SettingsRepository,
    private val cloudRepository: VerificationCloudRepository? = null
) {
    val allInstallmentPlans: Flow<List<InstallmentPlan>> = dao.getAllInstallmentPlans()
    val allInstallments: Flow<List<Installment>> = dao.getAllInstallments()

    fun getInstallmentPlanForTransaction(transactionId: Long): Flow<InstallmentPlan?> =
        dao.getInstallmentPlanForTransaction(transactionId)

    suspend fun getInstallmentPlanSnapshot(transactionId: Long): InstallmentPlan? =
        dao.getInstallmentPlanForTransactionSnapshot(transactionId)

    fun getInstallmentsForPlan(planId: Long): Flow<List<Installment>> =
        dao.getInstallmentsForPlan(planId)

    suspend fun getInstallmentsSnapshot(planId: Long): List<Installment> =
        dao.getInstallmentsForPlanSnapshot(planId)

    suspend fun createOrUpdatePlan(
        tx: UdhaarTransaction,
        plan: InstallmentPlan,
        draftInstallments: List<InstallmentDraft>,
        myProfileName: String = "You"
    ): Long {
        val existingPlan = dao.getInstallmentPlanForTransactionSnapshot(tx.id)
        if (existingPlan != null) {
            dao.deleteInstallmentsForPlan(existingPlan.id)
            dao.deleteInstallmentPlanById(existingPlan.id)
        }

        val remoteId = plan.remotePlanId.ifBlank { "plan_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val planToInsert = plan.copy(
            id = 0L,
            transactionId = tx.id,
            remotePlanId = remoteId,
            status = InstallmentPlanStatus.ACTIVE
        )
        val planId = dao.insertInstallmentPlan(planToInsert)

        val baseInstallments = draftInstallments.sortedBy { it.sequence }.map { draft ->
            Installment(
                id = 0L,
                planId = planId,
                sequence = draft.sequence,
                dueDate = draft.dueDateMillis,
                amountDue = draft.amountDuePaise,
                amountPaid = 0L,
                status = InstallmentCalculator.computeInstallmentStatus(
                    Installment(planId = planId, sequence = draft.sequence, dueDate = draft.dueDateMillis, amountDue = draft.amountDuePaise)
                )
            )
        }

        val existingPaidPaise = InstallmentCalculator.toPaise(tx.amountPaid)
        val allocationOnSave = if (existingPaidPaise > 0L) {
            InstallmentCalculator.allocatePayment(
                installments = baseInstallments,
                paymentAmountPaise = existingPaidPaise
            )
        } else {
            AllocationResult(baseInstallments, 0L, false)
        }
        val installmentsToInsert = allocationOnSave.updatedInstallments
        dao.insertInstallments(installmentsToInsert)

        if (allocationOnSave.isPlanFullySettled) {
            dao.updateInstallmentPlan(planToInsert.copy(id = planId, status = InstallmentPlanStatus.COMPLETED))
        }

        // Recompute content hash with plan included
        val computedHash = VerificationCryptoHelper.computeTransactionContentHash(
            tx = tx,
            myProfileName = myProfileName,
            plan = planToInsert.copy(id = planId),
            installments = installmentsToInsert
        )

        val nextStatus = if (tx.verificationStatus == VerificationStatus.CONFIRMED ||
            tx.verificationStatus == VerificationStatus.DISPUTED) {
            VerificationStatus.EDITED_AFTER_CONFIRM
        } else {
            tx.verificationStatus
        }

        dao.updateTransaction(
            tx.copy(
                contentHash = computedHash,
                verificationStatus = nextStatus,
                needsMyConfirmation = false
            )
        )

        val actor = myProfileName.trim().ifEmpty { "You" }
        val totalFormatted = "${tx.currency.symbol}${InterestCalculator.formatAmount(InstallmentCalculator.paiseToDouble(plan.totalAmountDue))}"
        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = tx.id,
            eventType = AuditEventType.EDITED,
            actorName = actor,
            summaryText = "$actor set up installment plan of ${plan.installmentCount} installments ($totalFormatted total)",
            contentHashSnapshot = computedHash
        )

        return planId
    }

    suspend fun applyPaymentToInstallments(
        txId: Long,
        paymentAmountPaise: Long,
        paymentId: Long? = null,
        paidAtMillis: Long = System.currentTimeMillis()
    ): AllocationResult {
        val plan = dao.getInstallmentPlanForTransactionSnapshot(txId)
            ?: return AllocationResult(emptyList(), paymentAmountPaise, false)
        val installments = dao.getInstallmentsForPlanSnapshot(plan.id)

        val result = InstallmentCalculator.allocatePayment(
            installments = installments,
            paymentAmountPaise = paymentAmountPaise,
            paymentId = paymentId,
            paidAtMillis = paidAtMillis
        )

        dao.updateInstallments(result.updatedInstallments)

        if (result.isPlanFullySettled) {
            dao.updateInstallmentPlan(plan.copy(status = InstallmentPlanStatus.COMPLETED))
        }

        return result
    }

    suspend fun moveInstallmentPayment(
        planId: Long,
        fromSequence: Int,
        toSequence: Int
    ): List<Installment> {
        val installments = dao.getInstallmentsForPlanSnapshot(planId)
        val updated = InstallmentCalculator.movePaymentBetweenInstallments(
            installments = installments,
            fromSequence = fromSequence,
            toSequence = toSequence
        )
        dao.updateInstallments(updated)
        return updated
    }

    suspend fun recalculateInstallmentsForTotalPaid(
        txId: Long,
        totalPaidPaise: Long
    ): AllocationResult {
        val plan = dao.getInstallmentPlanForTransactionSnapshot(txId)
            ?: return AllocationResult(emptyList(), totalPaidPaise, false)
        val installments = dao.getInstallmentsForPlanSnapshot(plan.id)
        val result = InstallmentCalculator.reallocateTotalPaid(
            installments = installments,
            totalPaidPaise = totalPaidPaise
        )
        dao.updateInstallments(result.updatedInstallments)
        val targetStatus = if (result.isPlanFullySettled) InstallmentPlanStatus.COMPLETED else InstallmentPlanStatus.ACTIVE
        if (plan.status != targetStatus) {
            dao.updateInstallmentPlan(plan.copy(status = targetStatus))
        }
        return result
    }

    suspend fun proposeNewSchedule(
        planId: Long,
        txId: Long,
        proposals: List<InstallmentDraft>,
        proposerName: String
    ) {
        val plan = dao.getInstallmentPlanById(planId) ?: return
        val jsonArray = JSONArray()
        proposals.sortedBy { it.sequence }.forEach { draft ->
            val obj = JSONObject().apply {
                put("sequence", draft.sequence)
                put("dueDateMillis", draft.dueDateMillis)
                put("amountDuePaise", draft.amountDuePaise)
            }
            jsonArray.put(obj)
        }

        dao.updateInstallmentPlan(
            plan.copy(
                proposedScheduleJson = jsonArray.toString(),
                scheduleProposalStatus = "PENDING"
            )
        )

        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = txId,
            eventType = AuditEventType.EXTENSION_REQUESTED,
            actorName = proposerName,
            summaryText = "$proposerName proposed a revised installment schedule",
            contentHashSnapshot = ""
        )
    }

    suspend fun acceptProposedSchedule(
        planId: Long,
        tx: UdhaarTransaction,
        actorName: String
    ) {
        val plan = dao.getInstallmentPlanById(planId) ?: return
        val jsonStr = plan.proposedScheduleJson
        if (jsonStr.isBlank()) return

        val arr = runCatching { JSONArray(jsonStr) }.getOrNull() ?: return
        val newDrafts = mutableListOf<InstallmentDraft>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            newDrafts.add(
                InstallmentDraft(
                    sequence = obj.getInt("sequence"),
                    dueDateMillis = obj.getLong("dueDateMillis"),
                    amountDuePaise = obj.getLong("amountDuePaise")
                )
            )
        }

        dao.deleteInstallmentsForPlan(planId)
        val newInstallments = newDrafts.map { draft ->
            Installment(
                id = 0L,
                planId = planId,
                sequence = draft.sequence,
                dueDate = draft.dueDateMillis,
                amountDue = draft.amountDuePaise,
                amountPaid = 0L,
                status = InstallmentCalculator.computeInstallmentStatus(
                    Installment(planId = planId, sequence = draft.sequence, dueDate = draft.dueDateMillis, amountDue = draft.amountDuePaise)
                )
            )
        }
        dao.insertInstallments(newInstallments)

        val updatedPlan = plan.copy(
            installmentCount = newDrafts.size,
            totalAmountDue = newDrafts.sumOf { it.amountDuePaise },
            proposedScheduleJson = "",
            scheduleProposalStatus = "ACCEPTED"
        )
        dao.updateInstallmentPlan(updatedPlan)

        val newHash = VerificationCryptoHelper.computeTransactionContentHash(
            tx = tx,
            myProfileName = actorName,
            plan = updatedPlan,
            installments = newInstallments
        )

        dao.updateTransaction(
            tx.copy(
                contentHash = newHash,
                verificationStatus = VerificationStatus.CONFIRMED
            )
        )

        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = tx.id,
            eventType = AuditEventType.EXTENSION_ACCEPTED,
            actorName = actorName,
            summaryText = "$actorName accepted revised installment schedule",
            contentHashSnapshot = newHash
        )
    }

    suspend fun declineProposedSchedule(
        planId: Long,
        txId: Long,
        actorName: String
    ) {
        val plan = dao.getInstallmentPlanById(planId) ?: return
        dao.updateInstallmentPlan(
            plan.copy(
                proposedScheduleJson = "",
                scheduleProposalStatus = "DECLINED"
            )
        )
        AuditTrailHelper.appendLocalAuditEvent(
            dao = dao,
            transactionId = txId,
            eventType = AuditEventType.EXTENSION_DECLINED,
            actorName = actorName,
            summaryText = "$actorName declined revised installment schedule",
            contentHashSnapshot = ""
        )
    }
}
