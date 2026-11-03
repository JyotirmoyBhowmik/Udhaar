package com.jyotirmoyb.udhaar.data.repository

import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.util.SafeLogger
import com.jyotirmoyb.udhaar.util.UdhaarAppError
import com.jyotirmoyb.udhaar.util.UdhaarResult
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper

/**
 * Domain repository responsible for Two-Party Verification links (`https://udhaar.jyotirmoyb.com/u/<token>`),
 * SHA-256 content hashes, payment profile sharing (`https://udhaar.jyotirmoyb.com/u/p/<token>`),
 * and Firestore cloud sync.
 */
class VerificationRepository(
    private val delegate: UdhaarRepository
) {
    suspend fun prepareVerificationShare(
        transaction: UdhaarTransaction
    ): UdhaarResult<Pair<UdhaarTransaction, String>> {
        return try {
            val pair = delegate.prepareVerificationShare(transaction)
            UdhaarResult.Success(pair)
        } catch (t: Throwable) {
            SafeLogger.e("VerificationRepository", "Failed preparing verification share link", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Verification share failed"), t)
        }
    }

    suspend fun confirmIncomingVerification(
        payload: VerificationCryptoHelper.SharedVerificationPayload
    ): UdhaarResult<UdhaarTransaction> {
        return try {
            val tx = delegate.processIncomingVerificationResponse(
                payload = payload,
                confirm = true,
                disputeReason = ""
            )
            UdhaarResult.Success(tx)
        } catch (t: Throwable) {
            SafeLogger.e("VerificationRepository", "Failed confirming incoming verification payload", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Verification confirmation failed"), t)
        }
    }

    suspend fun disputeIncomingVerification(
        payload: VerificationCryptoHelper.SharedVerificationPayload,
        disputeReason: String
    ): UdhaarResult<UdhaarTransaction> {
        return try {
            val tx = delegate.processIncomingVerificationResponse(
                payload = payload,
                confirm = false,
                disputeReason = disputeReason
            )
            UdhaarResult.Success(tx)
        } catch (t: Throwable) {
            SafeLogger.e("VerificationRepository", "Failed disputing incoming verification payload", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Dispute submission failed"), t)
        }
    }

    suspend fun confirmOrDisputeExistingTransaction(
        transaction: UdhaarTransaction,
        confirm: Boolean,
        disputeReason: String = ""
    ): UdhaarResult<Unit> {
        return try {
            delegate.confirmOrDisputeExistingTransaction(transaction, confirm, disputeReason)
            UdhaarResult.Success(Unit)
        } catch (t: Throwable) {
            SafeLogger.e("VerificationRepository", "Failed updating existing transaction verification state", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Could not update verification status"), t)
        }
    }

    suspend fun acceptSharedPaymentProfile(
        profile: VerificationCryptoHelper.SharedPaymentProfilePayload,
        targetPartyId: Long? = null
    ): UdhaarResult<PartyEntity> {
        return try {
            val party = delegate.acceptSharedPaymentProfile(profile, targetPartyId)
            UdhaarResult.Success(party)
        } catch (t: Throwable) {
            SafeLogger.e("VerificationRepository", "Failed accepting shared payment profile", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Could not import payment profile"), t)
        }
    }

    suspend fun syncProfileAndRecordsToCloud(userIdOverride: String? = null): UdhaarResult<Long> {
        return try {
            val res = delegate.syncProfileAndRecordsToCloud(userIdOverride)
            res.fold(
                onSuccess = { UdhaarResult.Success(it) },
                onFailure = { err ->
                    SafeLogger.w("VerificationRepository", "Cloud sync returned failure", err)
                    UdhaarResult.Failure(UdhaarAppError.fromThrowable(err, "Cloud sync failed"), err)
                }
            )
        } catch (t: Throwable) {
            SafeLogger.e("VerificationRepository", "Cloud sync threw exception", t)
            UdhaarResult.Failure(UdhaarAppError.fromThrowable(t, "Cloud sync failed"), t)
        }
    }
}
