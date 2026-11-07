package com.jyotirmoyb.udhaar.sync

import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.SyncStatus
import com.jyotirmoyb.udhaar.data.UdhaarTransaction

/**
 * CRDT-Lite Conflict Resolution Policy for Android.
 * Matches Stage S4 server and web client specifications:
 * 1. Partial payments & events are strictly append-only (set union).
 * 2. Concurrent core term changes (principalAmountPaise, expiryDateMillis) trigger explicit CONFLICT.
 * 3. Edit-beats-delete semantics: active remote edits resurrect locally deleted records.
 * 4. Non-term metadata (notes, names) resolves via Last-Writer-Wins governed by Hybrid Logical Clocks.
 */
object SyncConflictPolicy {

    sealed class TransactionResolution {
        data class ApplyRemote(val merged: UdhaarTransaction) : TransactionResolution()
        data class TermConflict(val conflicted: UdhaarTransaction, val conflictDetails: String) : TransactionResolution()
        object IgnoreRemoteOlder : TransactionResolution()
    }

    fun resolveTransactionConflict(
        local: UdhaarTransaction,
        remoteFields: Map<String, Any?>,
        remoteServerRev: Long,
        remoteHlc: String,
        remoteDeletedAtMs: Long?
    ): TransactionResolution {
        // 1. Edit beats delete: if local is deleted and remote has active edit
        val isLocallyDeleted = local.deletedAtMs != null
        val isRemotelyDeleted = remoteDeletedAtMs != null

        val incomingPrincipal = (remoteFields["principalAmountPaise"] as? Number)?.toLong()
        val incomingDueDate = (remoteFields["dueDateMs"] as? Number)?.toLong() ?: (remoteFields["expiryDateMillis"] as? Number)?.toLong()

        // 2. Term Conflict Check:
        // If money terms changed concurrently without local seeing the new server revision
        val termsDiffer = (incomingPrincipal != null && incomingPrincipal != local.principalAmountPaise) ||
                (incomingDueDate != null && incomingDueDate != (local.expiryDateMillis ?: 0L))

        val localHasUnsyncedEdits = local.syncStatus == SyncStatus.PENDING_PUSH || local.clientRev > 1L

        if (termsDiffer && localHasUnsyncedEdits && remoteServerRev > local.serverRev) {
            val conflictSummary = "Term conflict: Server principal=$incomingPrincipal, Local principal=${local.principalAmountPaise}"
            val conflicted = local.copy(
                syncStatus = SyncStatus.CONFLICT,
                disputeReason = conflictSummary
            )
            return TransactionResolution.TermConflict(conflicted, conflictSummary)
        }

        // 3. Last-Writer-Wins (LWW) by HLC
        val isRemoteNewerOrEqual = Hlc.compare(remoteHlc, local.hlc) >= 0

        if (!isRemoteNewerOrEqual && remoteServerRev <= local.serverRev) {
            return TransactionResolution.IgnoreRemoteOlder
        }

        // Build merged record
        val merged = local.copy(
            principalAmountPaise = incomingPrincipal ?: local.principalAmountPaise,
            expiryDateMillis = incomingDueDate ?: local.expiryDateMillis,
            partyName = (remoteFields["partyName"] as? String)?.ifBlank { null } ?: local.partyName,
            notes = (remoteFields["note"] as? String) ?: (remoteFields["notes"] as? String) ?: local.notes,
            serverRev = remoteServerRev,
            hlc = if (isRemoteNewerOrEqual) remoteHlc else local.hlc,
            syncStatus = SyncStatus.SYNCED,
            deletedAtMs = if (!isRemotelyDeleted && isLocallyDeleted) null else (remoteDeletedAtMs ?: local.deletedAtMs)
        )

        return TransactionResolution.ApplyRemote(merged)
    }

    /**
     * Compute remaining balance from original principal and append-only payments union.
     */
    fun recalculateRemainingPaise(
        principalAmountPaise: Long,
        payments: List<PartialPayment>
    ): Long {
        val totalPaid = payments.filter { it.deletedAtMs == null }.sumOf { it.amountPaise }
        return (principalAmountPaise - totalPaid).coerceAtLeast(0L)
    }
}
