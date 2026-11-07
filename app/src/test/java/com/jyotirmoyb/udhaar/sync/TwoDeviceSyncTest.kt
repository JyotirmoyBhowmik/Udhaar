package com.jyotirmoyb.udhaar.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.SyncStatus
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.security.TokenStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TwoDeviceSyncTest {

    private lateinit var dbA: UdhaarDatabase
    private lateinit var dbB: UdhaarDatabase

    private lateinit var tokenStoreA: FakeTokenStore
    private lateinit var tokenStoreB: FakeTokenStore

    private lateinit var hub: InMemorySyncHub
    private lateinit var repoA: SyncRepository
    private lateinit var repoB: SyncRepository

    class FakeTokenStore(
        val devId: String,
        private var syncEnabled: Boolean = true,
        private var signedIn: Boolean = true
    ) : TokenStore {
        override fun getAuthToken(): String? = if (signedIn) "token_$devId" else null
        override fun getUserId(): String? = if (signedIn) "user_shared_account" else null
        override fun getPhoneNumber(): String? = "+919876543210"
        override fun getEmail(): String? = "user@example.com"
        override fun getDeviceId(): String = devId
        override fun isCloudSyncEnabled(): Boolean = syncEnabled
        override fun setCloudSyncEnabled(enabled: Boolean) { syncEnabled = enabled }
        override fun saveSession(authToken: String, userId: String, phoneNumber: String?, email: String?) {
            signedIn = true
        }
        override fun updateEmail(email: String) {}
        override fun clearSession() {
            signedIn = false
            syncEnabled = false
        }
        override fun isSignedIn(): Boolean = signedIn
        override fun getLastSyncTimestamp(): Long = 0L
        override fun setLastSyncTimestamp(timestamp: Long) {}
    }

    class InMemorySyncHub : SyncCloudTransport {
        val deltas = mutableListOf<SyncDeltaEntryDto>()
        var pushCallCount = 0
        var pullCallCount = 0
        private var globalSeq = 0L

        override suspend fun pushMutations(request: SyncPushRequest): SyncPushResponse {
            pushCallCount++
            val results = request.changes.map { mutation ->
                globalSeq++
                deltas.add(
                    SyncDeltaEntryDto(
                        seq = globalSeq,
                        entity = mutation.entity,
                        syncId = mutation.syncId,
                        op = mutation.op,
                        fields = mutation.fields,
                        serverRev = globalSeq,
                        hlc = mutation.hlc,
                        deviceId = request.deviceId,
                        timestampMs = System.currentTimeMillis()
                    )
                )
                SyncPushResultDto(
                    syncId = mutation.syncId,
                    status = "ACCEPTED",
                    serverRev = globalSeq
                )
            }
            return SyncPushResponse(results = results)
        }

        override suspend fun pullDeltas(cursor: Long, limit: Int): SyncPullResponse {
            pullCallCount++
            val sub = deltas.filter { it.seq > cursor }.take(limit)
            val nextCursor = sub.lastOrNull()?.seq ?: cursor
            return SyncPullResponse(
                changes = sub,
                nextCursor = nextCursor,
                hasMore = deltas.any { it.seq > nextCursor }
            )
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbA = Room.inMemoryDatabaseBuilder(context, UdhaarDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dbB = Room.inMemoryDatabaseBuilder(context, UdhaarDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        tokenStoreA = FakeTokenStore("device_A", syncEnabled = true)
        tokenStoreB = FakeTokenStore("device_B", syncEnabled = true)

        hub = InMemorySyncHub()

        repoA = SyncRepository(
            dao = dbA.udhaarDao(),
            outboxDao = dbA.syncOutboxDao(),
            transport = hub,
            tokenStore = tokenStoreA
        )

        repoB = SyncRepository(
            dao = dbB.udhaarDao(),
            outboxDao = dbB.syncOutboxDao(),
            transport = hub,
            tokenStore = tokenStoreB
        )
    }

    @After
    fun tearDown() {
        dbA.close()
        dbB.close()
    }

    @Test
    fun `two devices create records offline and converge after synchronization`() = runBlocking {
        // Device A creates Tx 1 offline
        val tx1 = repoA.createTransactionLocalFirst(
            UdhaarTransaction(partyName = "Customer Alice", principalAmountPaise = 50000L)
        )

        // Device B creates Tx 2 offline
        val tx2 = repoB.createTransactionLocalFirst(
            UdhaarTransaction(partyName = "Customer Bob", principalAmountPaise = 100000L)
        )

        // Initial state: Each device only has its own local record
        assertEquals(1, dbA.udhaarDao().getAllTransactionsSnapshot().size)
        assertEquals(1, dbB.udhaarDao().getAllTransactionsSnapshot().size)

        // Device A syncs (pushes tx1)
        repoA.sync()
        assertEquals(1, hub.deltas.size)

        // Device B syncs (pushes tx2, pulls tx1)
        repoB.sync()
        assertEquals(2, hub.deltas.size)
        val txsB = dbB.udhaarDao().getAllTransactionsSnapshot()
        assertEquals(2, txsB.size)
        assertTrue(txsB.any { it.syncId == tx1.syncId })
        assertTrue(txsB.any { it.syncId == tx2.syncId })

        // Device A syncs (pulls tx2)
        repoA.sync()
        val txsA = dbA.udhaarDao().getAllTransactionsSnapshot()
        assertEquals(2, txsA.size)
        assertTrue(txsA.any { it.syncId == tx1.syncId })
        assertTrue(txsA.any { it.syncId == tx2.syncId })

        // Both devices have identical records converged!
    }

    @Test
    fun `two devices append partial payments offline and converge by mathematical union`() = runBlocking {
        // Shared record created and synced
        val parentTx = repoA.createTransactionLocalFirst(
            UdhaarTransaction(partyName = "Merchant Charlie", principalAmountPaise = 100000L)
        )
        repoA.sync()
        repoB.sync()

        // Device A records ₹200 payment offline
        val payA = repoA.recordPartialPaymentLocalFirst(
            PartialPayment(
                transactionId = parentTx.id,
                amountPaise = 20000L,
                paymentMode = PaymentMode.UPI
            )
        )

        // Device B records ₹300 payment offline on its local transaction copy
        val localParentTxB = dbB.udhaarDao().getTransactionBySyncId(parentTx.syncId)
        assertNotNull(localParentTxB)
        val payB = repoB.recordPartialPaymentLocalFirst(
            PartialPayment(
                transactionId = localParentTxB!!.id,
                amountPaise = 30000L,
                paymentMode = PaymentMode.CASH
            )
        )

        // Sync both devices with hub
        repoA.sync()
        repoB.sync()
        repoA.sync() // Second pass to pull B's payment

        // Assert convergence: both devices must have total paid = ₹500 (union of payments)
        val updatedTxA = dbA.udhaarDao().getTransactionBySyncId(parentTx.syncId)
        val updatedTxB = dbB.udhaarDao().getTransactionBySyncId(parentTx.syncId)

        assertEquals(50000L, updatedTxA?.amountPaidPaise)
        assertEquals(50000L, updatedTxB?.amountPaidPaise)
        assertEquals(2, dbA.udhaarDao().getPartialPaymentsForTransactionSnapshot(updatedTxA!!.id).size)
        assertEquals(2, dbB.udhaarDao().getPartialPaymentsForTransactionSnapshot(updatedTxB!!.id).size)
    }

    @Test
    fun `feature flag cloud_sync_enabled off causes zero network calls`() = runBlocking {
        // Disable sync on Device A
        tokenStoreA.setCloudSyncEnabled(false)

        val beforePushCalls = hub.pushCallCount
        val beforePullCalls = hub.pullCallCount

        // Attempt write and sync
        repoA.createTransactionLocalFirst(
            UdhaarTransaction(partyName = "Offline Only User", principalAmountPaise = 20000L)
        )
        repoA.sync()

        // Verify zero transport calls occurred
        assertEquals(beforePushCalls, hub.pushCallCount)
        assertEquals(beforePullCalls, hub.pullCallCount)

        // Local record exists safely on device
        val localTxs = dbA.udhaarDao().getAllTransactionsSnapshot()
        assertEquals(1, localTxs.size)
        assertEquals(SyncStatus.LOCAL_ONLY, localTxs[0].syncStatus)
    }
}
