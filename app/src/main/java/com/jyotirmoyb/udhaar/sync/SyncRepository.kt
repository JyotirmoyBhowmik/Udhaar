package com.jyotirmoyb.udhaar.sync

import android.content.Context
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.SyncOutboxDao
import com.jyotirmoyb.udhaar.data.SyncOutboxEntity
import com.jyotirmoyb.udhaar.data.SyncStatus
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.security.TokenStore
import com.jyotirmoyb.udhaar.util.SafeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

interface SyncCloudTransport {
    suspend fun pushMutations(request: SyncPushRequest): SyncPushResponse
    suspend fun pullDeltas(cursor: Long, limit: Int = 100): SyncPullResponse
}

class SyncRepository(
    private val dao: UdhaarDao,
    private val outboxDao: SyncOutboxDao,
    private val transport: SyncCloudTransport? = null,
    private val tokenStore: TokenStore? = null
) {
    private var localHlc: Hlc = Hlc.create("android_${UUID.randomUUID().toString().take(8)}")
    private var lastPulledCursor: Long = 0L

    fun getLocalHlc(): Hlc = localHlc

    private fun tickHlc(): String {
        localHlc = Hlc.increment(localHlc)
        return localHlc.pack()
    }

    // -------------------------------------------------------------------------
    // Local-First Optimistic Write Paths
    // -------------------------------------------------------------------------
    suspend fun createTransactionLocalFirst(tx: UdhaarTransaction): UdhaarTransaction = withContext(Dispatchers.IO) {
        val syncId = if (tx.syncId.isNotBlank()) tx.syncId else UUID.randomUUID().toString()
        val hlcStr = tickHlc()
        val idempotencyKey = UUID.randomUUID().toString()

        val localTx = tx.copy(
            syncId = syncId,
            clientRev = 1L,
            serverRev = 0L,
            hlc = hlcStr,
            syncStatus = SyncStatus.LOCAL_ONLY,
            deletedAtMs = null
        )
        val insertedId = dao.insertTransaction(localTx)
        val finalTx = localTx.copy(id = insertedId)

        val payload = JSONObject().apply {
            put("recordId", "rec_${syncId.take(12)}")
            put("partyName", finalTx.partyName)
            put("phoneNumber", finalTx.phoneNumber)
            put("principalAmountPaise", finalTx.principalAmountPaise)
            put("remainingAmountPaise", (finalTx.principalAmountPaise - finalTx.amountPaidPaise).coerceAtLeast(0L))
            put("dueDateMs", finalTx.expiryDateMillis ?: 0L)
            put("note", finalTx.notes)
            put("status", if (finalTx.isSettled) "SETTLED" else "PENDING")
        }

        outboxDao.enqueue(
            SyncOutboxEntity(
                entity = "record",
                syncId = syncId,
                op = "CREATE",
                payloadJson = payload.toString(),
                clientRev = 1L,
                hlc = hlcStr,
                idempotencyKey = idempotencyKey
            )
        )

        finalTx
    }

    suspend fun updateTransactionLocalFirst(syncId: String, updates: (UdhaarTransaction) -> UdhaarTransaction): UdhaarTransaction? = withContext(Dispatchers.IO) {
        val existing = dao.getTransactionBySyncId(syncId) ?: return@withContext null
        val hlcStr = tickHlc()
        val updatedTx = updates(existing).copy(
            clientRev = existing.clientRev + 1L,
            hlc = hlcStr,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        dao.updateTransaction(updatedTx)

        val payload = JSONObject().apply {
            put("partyName", updatedTx.partyName)
            put("principalAmountPaise", updatedTx.principalAmountPaise)
            put("dueDateMs", updatedTx.expiryDateMillis ?: 0L)
            put("note", updatedTx.notes)
            put("status", if (updatedTx.isSettled) "SETTLED" else "PENDING")
        }

        outboxDao.enqueue(
            SyncOutboxEntity(
                entity = "record",
                syncId = syncId,
                op = "UPDATE",
                payloadJson = payload.toString(),
                clientRev = updatedTx.clientRev,
                hlc = hlcStr,
                idempotencyKey = UUID.randomUUID().toString()
            )
        )

        updatedTx
    }

    suspend fun recordPartialPaymentLocalFirst(payment: PartialPayment): PartialPayment = withContext(Dispatchers.IO) {
        val syncId = if (payment.syncId.isNotBlank()) payment.syncId else UUID.randomUUID().toString()
        val hlcStr = tickHlc()
        val idempotencyKey = UUID.randomUUID().toString()

        val localPayment = payment.copy(
            syncId = syncId,
            clientRev = 1L,
            serverRev = 0L,
            hlc = hlcStr,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        val insertedId = dao.insertPartialPayment(localPayment)
        val finalPayment = localPayment.copy(id = insertedId)

        // Optimistically update transaction amount paid
        val parentTx = dao.getTransactionById(payment.transactionId)
        if (parentTx != null) {
            val payments = dao.getPartialPaymentsForTransactionSnapshot(parentTx.id)
            val totalPaidPaise = payments.sumOf { it.amountPaise }
            val isNowSettled = totalPaidPaise >= parentTx.principalAmountPaise
            dao.updateTransaction(
                parentTx.copy(
                    amountPaidPaise = totalPaidPaise,
                    isSettled = isNowSettled,
                    settledAtMillis = if (isNowSettled) System.currentTimeMillis() else null
                )
            )
        }

        val payload = JSONObject().apply {
            put("paymentId", syncId)
            put("recordSyncId", parentTx?.syncId ?: "")
            put("amountPaise", finalPayment.amountPaise)
            put("paymentMode", finalPayment.paymentMode.name)
            put("note", finalPayment.note)
            put("timestampMs", finalPayment.paidAtMillis)
        }

        outboxDao.enqueue(
            SyncOutboxEntity(
                entity = "payment",
                syncId = syncId,
                op = "CREATE",
                payloadJson = payload.toString(),
                clientRev = 1L,
                hlc = hlcStr,
                idempotencyKey = idempotencyKey
            )
        )

        finalPayment
    }

    // -------------------------------------------------------------------------
    // Sync Hub: Drain Outbox & Pull Deltas
    // -------------------------------------------------------------------------
    suspend fun drainOutbox(): Int = withContext(Dispatchers.IO) {
        if (transport == null) return@withContext 0
        if (tokenStore != null && (!tokenStore.isSignedIn() || !tokenStore.isCloudSyncEnabled())) {
            SafeLogger.i("SyncRepository", "Cloud sync disabled or signed out. Skipping outbox drain.")
            return@withContext 0
        }
        val pending = outboxDao.getPendingMutations(now = System.currentTimeMillis(), limit = 100)
        if (pending.isEmpty()) return@withContext 0

        val dtos = pending.map { entry ->
            val json = JSONObject(entry.payloadJson)
            val fieldsMap = mutableMapOf<String, Any?>()
            json.keys().forEach { k -> fieldsMap[k] = json.opt(k) }
            SyncMutationDto(
                entity = entry.entity,
                syncId = entry.syncId,
                op = entry.op,
                fields = fieldsMap,
                clientRev = entry.clientRev,
                hlc = entry.hlc,
                idempotencyKey = entry.idempotencyKey
            )
        }

        try {
            val response = transport.pushMutations(
                SyncPushRequest(
                    deviceId = localHlc.deviceId,
                    changes = dtos
                )
            )

            for (i in response.results.indices) {
                val res = response.results[i]
                val outboxItem = pending.getOrNull(i) ?: continue

                when (res.status) {
                    "ACCEPTED" -> {
                        outboxDao.deleteById(outboxItem.id)
                        if (outboxItem.entity == "record") {
                            val tx = dao.getTransactionBySyncId(outboxItem.syncId)
                            if (tx != null) {
                                dao.updateTransaction(
                                    tx.copy(
                                        serverRev = res.serverRev ?: (tx.serverRev + 1L),
                                        syncStatus = SyncStatus.SYNCED
                                    )
                                )
                            }
                        } else if (outboxItem.entity == "payment") {
                            val pay = dao.getPartialPaymentBySyncId(outboxItem.syncId)
                            if (pay != null) {
                                dao.updatePartialPayment(pay.copy(syncStatus = SyncStatus.SYNCED))
                            }
                        }
                    }
                    "CONFLICT" -> {
                        outboxDao.deleteById(outboxItem.id)
                        val tx = dao.getTransactionBySyncId(outboxItem.syncId)
                        if (tx != null) {
                            dao.updateTransaction(
                                tx.copy(
                                    syncStatus = SyncStatus.CONFLICT,
                                    disputeReason = "Server conflict: ${res.conflictDetails}"
                                )
                            )
                        }
                    }
                    "REJECTED" -> {
                        outboxDao.deleteById(outboxItem.id)
                        SafeLogger.w("SyncRepository", "Mutation ${outboxItem.syncId} rejected by server: ${res.reason}")
                    }
                }
            }
            response.results.size
        } catch (t: Throwable) {
            SafeLogger.e("SyncRepository", "Outbox drain failed, applying exponential backoff", t)
            val now = System.currentTimeMillis()
            for (item in pending) {
                val nextAttempt = item.attempts + 1
                val baseDelay = min(1000L * 2.0.pow((nextAttempt - 1).toDouble()).toLong(), 30000L)
                val jitter = (Math.random() * 500L).toLong()
                outboxDao.update(
                    item.copy(
                        status = "FAILED",
                        attempts = nextAttempt,
                        nextRetryAtMs = now + baseDelay + jitter
                    )
                )
            }
            0
        }
    }

    suspend fun pullDeltas(): Int = withContext(Dispatchers.IO) {
        if (transport == null) return@withContext 0
        if (tokenStore != null && (!tokenStore.isSignedIn() || !tokenStore.isCloudSyncEnabled())) {
            SafeLogger.i("SyncRepository", "Cloud sync disabled or signed out. Skipping delta pull.")
            return@withContext 0
        }
        try {
            val response = transport.pullDeltas(cursor = lastPulledCursor, limit = 100)
            if (response.changes.isEmpty()) return@withContext 0

            for (change in response.changes) {
                if (change.entity == "record") {
                    val local = dao.getTransactionBySyncId(change.syncId)
                    val isDeleted = (change.fields["deletedAtMs"] as? Number)?.toLong()
                    if (local == null) {
                        // New record from server
                        val partyName = (change.fields["partyName"] as? String) ?: "Counterparty"
                        val principalPaise = (change.fields["principalAmountPaise"] as? Number)?.toLong() ?: 0L
                        val newTx = UdhaarTransaction(
                            partyName = partyName,
                            principalAmountPaise = principalPaise,
                            syncId = change.syncId,
                            serverRev = change.serverRev,
                            hlc = change.hlc,
                            syncStatus = SyncStatus.SYNCED,
                            deletedAtMs = isDeleted
                        )
                        dao.insertTransaction(newTx)
                    } else {
                        val resolution = SyncConflictPolicy.resolveTransactionConflict(
                            local = local,
                            remoteFields = change.fields,
                            remoteServerRev = change.serverRev,
                            remoteHlc = change.hlc,
                            remoteDeletedAtMs = isDeleted
                        )
                        when (resolution) {
                            is SyncConflictPolicy.TransactionResolution.ApplyRemote -> {
                                dao.updateTransaction(resolution.merged)
                            }
                            is SyncConflictPolicy.TransactionResolution.TermConflict -> {
                                dao.updateTransaction(resolution.conflicted)
                            }
                            is SyncConflictPolicy.TransactionResolution.IgnoreRemoteOlder -> {
                                // Do nothing, local is newer
                            }
                        }
                    }
                } else if (change.entity == "payment") {
                    val existingPay = dao.getPartialPaymentBySyncId(change.syncId)
                    if (existingPay == null) {
                        val recSyncId = change.fields["recordSyncId"] as? String
                        val parentTx = if (!recSyncId.isNullOrBlank()) dao.getTransactionBySyncId(recSyncId) else null
                        if (parentTx != null) {
                            val amountPaise = (change.fields["amountPaise"] as? Number)?.toLong() ?: 0L
                            val paymentModeStr = (change.fields["paymentMode"] as? String) ?: "CASH"
                            val payment = PartialPayment(
                                transactionId = parentTx.id,
                                amountPaise = amountPaise,
                                syncId = change.syncId,
                                paymentMode = com.jyotirmoyb.udhaar.data.PaymentMode.valueOf(paymentModeStr),
                                paidAtMillis = (change.fields["timestampMs"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                serverRev = change.serverRev,
                                hlc = change.hlc,
                                syncStatus = SyncStatus.SYNCED
                            )
                            dao.insertPartialPayment(payment)

                            // Recalculate transaction amount paid
                            val allPayments = dao.getPartialPaymentsForTransactionSnapshot(parentTx.id)
                            val totalPaid = allPayments.sumOf { it.amountPaise }
                            val isSettled = totalPaid >= parentTx.principalAmountPaise
                            dao.updateTransaction(
                                parentTx.copy(
                                    amountPaidPaise = totalPaid,
                                    isSettled = isSettled,
                                    settledAtMillis = if (isSettled) System.currentTimeMillis() else null
                                )
                            )
                        }
                    }
                }

                // Advance local clock causality
                if (change.hlc.isNotBlank()) {
                    localHlc = Hlc.update(localHlc, change.hlc)
                }
            }

            lastPulledCursor = response.nextCursor
            response.changes.size
        } catch (t: Throwable) {
            SafeLogger.e("SyncRepository", "Delta pull failed", t)
            0
        }
    }

    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        if (tokenStore != null && (!tokenStore.isSignedIn() || !tokenStore.isCloudSyncEnabled())) {
            SafeLogger.i("SyncRepository", "Cloud sync disabled or signed out. Zero network calls.")
            return@withContext true
        }
        drainOutbox()
        pullDeltas()
        true
    }
}
