package com.jyotirmoyb.udhaar

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.SyncOutboxEntity
import com.jyotirmoyb.udhaar.data.SyncStatus
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.sync.Hlc
import com.jyotirmoyb.udhaar.sync.SyncCloudTransport
import com.jyotirmoyb.udhaar.sync.SyncConflictPolicy
import com.jyotirmoyb.udhaar.sync.SyncDeltaEntryDto
import com.jyotirmoyb.udhaar.sync.SyncPullResponse
import com.jyotirmoyb.udhaar.sync.SyncPushRequest
import com.jyotirmoyb.udhaar.sync.SyncPushResponse
import com.jyotirmoyb.udhaar.sync.SyncPushResultDto
import com.jyotirmoyb.udhaar.sync.SyncRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomMigration12To13Test {

    @Test
    fun `migration 12 to 13 adds sync columns, default LOCAL_ONLY status and outbox table`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // In-memory SQLite
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // 1. Create v12 udhaar_transactions
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `udhaar_transactions` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `partyId` INTEGER,
                                `transactionType` TEXT NOT NULL,
                                `partyName` TEXT NOT NULL,
                                `phoneNumber` TEXT NOT NULL,
                                `currency` TEXT NOT NULL,
                                `principalAmountPaise` INTEGER NOT NULL DEFAULT 0,
                                `amountPaidPaise` INTEGER NOT NULL DEFAULT 0,
                                `paymentMode` TEXT NOT NULL,
                                `isInterestEnabled` INTEGER NOT NULL,
                                `interestType` TEXT NOT NULL,
                                `interestRate` REAL NOT NULL,
                                `interestRatePeriod` TEXT NOT NULL,
                                `compoundFrequency` TEXT NOT NULL,
                                `startDateMillis` INTEGER NOT NULL,
                                `expiryDateMillis` INTEGER,
                                `reminderDateMillis` INTEGER,
                                `remindersDisabled` INTEGER NOT NULL DEFAULT 0,
                                `snoozedUntilMillis` INTEGER,
                                `lastReminderSentMillis` INTEGER,
                                `lastReminderLevel` INTEGER NOT NULL DEFAULT 0,
                                `notes` TEXT NOT NULL,
                                `isSettled` INTEGER NOT NULL,
                                `settledAtMillis` INTEGER,
                                `createdAtMillis` INTEGER NOT NULL DEFAULT 0,
                                `remoteId` TEXT NOT NULL DEFAULT '',
                                `verificationStatus` TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
                                `verifiedAtMillis` INTEGER,
                                `counterpartyUserId` TEXT NOT NULL DEFAULT '',
                                `contentHash` TEXT NOT NULL DEFAULT '',
                                `needsMyConfirmation` INTEGER NOT NULL DEFAULT 0,
                                `shareToken` TEXT NOT NULL DEFAULT '',
                                `disputeReason` TEXT NOT NULL DEFAULT '',
                                `lenderName` TEXT NOT NULL DEFAULT '',
                                `borrowerName` TEXT NOT NULL DEFAULT '',
                                `webLinkStatus` TEXT NOT NULL DEFAULT '',
                                `lastGuestAction` TEXT NOT NULL DEFAULT '',
                                `trustLevel` TEXT NOT NULL DEFAULT '',
                                `pendingPaymentClaim` TEXT NOT NULL DEFAULT '',
                                `pendingDateProposal` TEXT NOT NULL DEFAULT ''
                            )
                            """.trimIndent()
                        )

                        // 2. Create v12 parties
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `parties` (
                                `partyId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `name` TEXT NOT NULL,
                                `phoneNumber` TEXT NOT NULL,
                                `alternatePhoneNumber` TEXT NOT NULL DEFAULT '',
                                `upiId` TEXT NOT NULL DEFAULT '',
                                `email` TEXT NOT NULL DEFAULT '',
                                `tagOrNotes` TEXT NOT NULL DEFAULT '',
                                `notes` TEXT NOT NULL DEFAULT '',
                                `photoUri` TEXT NOT NULL DEFAULT '',
                                `linkedUserId` TEXT NOT NULL DEFAULT '',
                                `isFavorite` INTEGER NOT NULL DEFAULT 0,
                                `preferredCurrency` TEXT NOT NULL DEFAULT 'INR',
                                `language` TEXT NOT NULL DEFAULT '',
                                `quietHoursOverride` INTEGER NOT NULL DEFAULT 0,
                                `lastInteractionMillis` INTEGER NOT NULL DEFAULT 0,
                                `createdAtMillis` INTEGER NOT NULL DEFAULT 0
                            )
                            """.trimIndent()
                        )

                        // 3. Create v12 partial_payments
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `partial_payments` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `transactionId` INTEGER NOT NULL,
                                `amountPaise` INTEGER NOT NULL DEFAULT 0,
                                `paymentMode` TEXT NOT NULL DEFAULT 'CASH',
                                `paidAtMillis` INTEGER NOT NULL,
                                `note` TEXT NOT NULL,
                                `remotePaymentId` TEXT NOT NULL DEFAULT '',
                                `recordedByUserId` TEXT NOT NULL DEFAULT '',
                                `recordedByName` TEXT NOT NULL DEFAULT '',
                                `isAcknowledged` INTEGER NOT NULL DEFAULT 1,
                                `acknowledgedAtMillis` INTEGER,
                                `needsMyAcknowledgement` INTEGER NOT NULL DEFAULT 0
                            )
                            """.trimIndent()
                        )

                        // 4. Create v12 installment_plans
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `installment_plans` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `transactionId` INTEGER NOT NULL,
                                `installmentCount` INTEGER NOT NULL,
                                `frequency` TEXT NOT NULL,
                                `startDate` INTEGER NOT NULL,
                                `interestMode` TEXT NOT NULL,
                                `flatInterestRate` REAL NOT NULL,
                                `totalAmountDue` INTEGER NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                `status` TEXT NOT NULL,
                                `remotePlanId` TEXT NOT NULL,
                                `proposedScheduleJson` TEXT NOT NULL,
                                `scheduleProposalStatus` TEXT NOT NULL
                            )
                            """.trimIndent()
                        )

                        // 5. Create v12 installments
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `installments` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `planId` INTEGER NOT NULL,
                                `sequence` INTEGER NOT NULL,
                                `dueDate` INTEGER NOT NULL,
                                `amountDue` INTEGER NOT NULL,
                                `amountPaid` INTEGER NOT NULL,
                                `status` TEXT NOT NULL,
                                `paidAtMillis` INTEGER,
                                `paymentId` INTEGER
                            )
                            """.trimIndent()
                        )

                        // Insert pre-migration sample data
                        db.execSQL(
                            """
                            INSERT INTO `parties` (`partyId`, `name`, `phoneNumber`, `preferredCurrency`, `createdAtMillis`)
                            VALUES (1, 'Ramesh Kumar', '+919876543210', 'INR', 1700000000000)
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO `udhaar_transactions` (
                                `id`, `partyId`, `transactionType`, `partyName`, `phoneNumber`, `currency`,
                                `principalAmountPaise`, `amountPaidPaise`, `paymentMode`, `isInterestEnabled`,
                                `interestType`, `interestRate`, `interestRatePeriod`, `compoundFrequency`,
                                `startDateMillis`, `notes`, `isSettled`, `createdAtMillis`
                            ) VALUES (
                                1, 1, 'GIVEN', 'Ramesh Kumar', '+919876543210', 'INR',
                                500000, 200000, 'UPI', 0,
                                'SIMPLE', 0.0, 'PER_MONTH', 'MONTHLY',
                                1700000000000, 'Pre-migration transaction', 0, 1700000000000
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO `partial_payments` (`id`, `transactionId`, `amountPaise`, `paidAtMillis`, `paymentMode`, `note`)
                            VALUES (1, 1, 200000, 1700005000000, 'UPI', 'First partial payment')
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        val db = helper.writableDatabase

        // Execute MIGRATION_12_13
        UdhaarDatabase.MIGRATION_12_13.migrate(db)

        // 1. Verify udhaar_transactions columns & values
        db.query("SELECT syncId, serverRev, clientRev, hlc, syncStatus, deletedAtMs FROM udhaar_transactions WHERE id = 1").use { c ->
            assertTrue("Transaction row should exist", c.moveToFirst())
            val syncId = c.getString(0)
            val serverRev = c.getLong(1)
            val clientRev = c.getLong(2)
            val hlc = c.getString(3)
            val syncStatus = c.getString(4)
            val isDeleted = !c.isNull(5)

            assertTrue("syncId should be populated with deterministic prefix", syncId.startsWith("tx_1_"))
            assertEquals(0L, serverRev)
            assertEquals(1L, clientRev)
            assertEquals("", hlc)
            assertEquals("LOCAL_ONLY", syncStatus)
            assertFalse("deletedAtMs should be null", isDeleted)
        }

        // 2. Verify parties columns & values
        db.query("SELECT syncId, syncStatus, serverRev, clientRev FROM parties WHERE partyId = 1").use { c ->
            assertTrue("Party row should exist", c.moveToFirst())
            val syncId = c.getString(0)
            val syncStatus = c.getString(1)
            assertTrue("Party syncId should have prefix per_", syncId.startsWith("per_1_"))
            assertEquals("LOCAL_ONLY", syncStatus)
        }

        // 3. Verify partial_payments columns & values
        db.query("SELECT syncId, syncStatus FROM partial_payments WHERE id = 1").use { c ->
            assertTrue("Payment row should exist", c.moveToFirst())
            val syncId = c.getString(0)
            val syncStatus = c.getString(1)
            assertTrue("Payment syncId should have prefix pay_", syncId.startsWith("pay_1_"))
            assertEquals("LOCAL_ONLY", syncStatus)
        }

        // 4. Verify sync_outbox table exists and is queryable
        db.query("SELECT COUNT(*) FROM sync_outbox").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0L, c.getLong(0))
        }

        // 5. Verify indices exist
        db.query("PRAGMA index_list('udhaar_transactions')").use { c ->
            var foundSyncIdIndex = false
            while (c.moveToNext()) {
                val idxName = c.getString(1)
                if (idxName.contains("syncId")) foundSyncIdIndex = true
            }
            assertTrue("udhaar_transactions should have index on syncId", foundSyncIdIndex)
        }
    }

    @Test
    fun `sync outbox dao enqueues, queries, updates backoff, and deletes`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, UdhaarDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val outboxDao = db.syncOutboxDao()

        assertEquals(0, outboxDao.getTotalCount())

        // 1. Enqueue mutation
        val entryId = outboxDao.enqueue(
            SyncOutboxEntity(
                entity = "record",
                syncId = "test_sync_123",
                op = "CREATE",
                payloadJson = "{\"principalAmountPaise\":500000}",
                idempotencyKey = "idem_key_1"
            )
        )
        assertTrue(entryId > 0)
        assertEquals(1, outboxDao.getTotalCount())

        // 2. Query pending mutations
        val pending = outboxDao.getPendingMutations(now = System.currentTimeMillis() + 1000)
        assertEquals(1, pending.size)
        assertEquals("test_sync_123", pending[0].syncId)

        // 3. Update with failure and backoff
        val failed = pending[0].copy(
            status = "FAILED",
            attempts = 1,
            nextRetryAtMs = System.currentTimeMillis() + 60000L
        )
        outboxDao.update(failed)

        // Querying immediately should not return it due to future retry time
        val pendingNow = outboxDao.getPendingMutations(now = System.currentTimeMillis())
        assertEquals(0, pendingNow.size)

        // 4. Delete on success
        outboxDao.deleteById(entryId)
        assertEquals(0, outboxDao.getTotalCount())

        db.close()
    }

    @Test
    fun `hlc increments monotonically and catches up with remote causality`() {
        var hlc = Hlc.create("devA", 1700000000000L)
        val packed1 = hlc.pack()
        assertEquals("1700000000000:0:devA", packed1)

        // Same physical millisecond -> counter increments
        hlc = Hlc.increment(hlc, 1700000000000L)
        assertEquals("1700000000000:1:devA", hlc.pack())

        // Wall clock advances -> counter resets
        hlc = Hlc.increment(hlc, 1700000001000L)
        assertEquals("1700000001000:0:devA", hlc.pack())

        // Receive higher remote timestamp -> catches up
        val remoteHlc = "1700000005000:2:devB"
        hlc = Hlc.update(hlc, remoteHlc, 1700000001000L)
        assertEquals(1700000005000L, hlc.millis)
        assertEquals(3, hlc.counter)
        assertEquals("devA", hlc.deviceId)

        // Causal comparison
        assertTrue(Hlc.compare(hlc.pack(), packed1) > 0)
    }

    @Test
    fun `sync conflict policy handles append-only payments, term conflicts, and edit-beats-delete`() {
        val baseTx = UdhaarTransaction(
            id = 1,
            partyName = "Pooja",
            principalAmountPaise = 1000000L,
            amountPaidPaise = 0L,
            expiryDateMillis = 1750000000000L,
            serverRev = 1L,
            clientRev = 1L,
            hlc = "1700000000000:0:local",
            syncStatus = SyncStatus.SYNCED
        )

        // 1. Partial payments append-only recalculation
        val payments = listOf(
            PartialPayment(id = 1, transactionId = 1, amountPaise = 300000L),
            PartialPayment(id = 2, transactionId = 1, amountPaise = 400000L)
        )
        val remaining = SyncConflictPolicy.recalculateRemainingPaise(baseTx.principalAmountPaise, payments)
        assertEquals(300000L, remaining)

        // 2. Term conflict detection (concurrent principal amount modification)
        val localWithPendingEdit = baseTx.copy(
            principalAmountPaise = 1200000L,
            clientRev = 2L,
            syncStatus = SyncStatus.PENDING_PUSH
        )
        val remoteFieldsConflict = mapOf<String, Any?>(
            "principalAmountPaise" to 800000L,
            "partyName" to "Pooja"
        )
        val resolutionConflict = SyncConflictPolicy.resolveTransactionConflict(
            local = localWithPendingEdit,
            remoteFields = remoteFieldsConflict,
            remoteServerRev = 2L,
            remoteHlc = "1700000005000:0:server",
            remoteDeletedAtMs = null
        )
        assertTrue("Concurrent principal change should trigger TermConflict", resolutionConflict is SyncConflictPolicy.TransactionResolution.TermConflict)

        // 3. Edit-beats-delete resurrection
        val locallyDeletedTx = baseTx.copy(deletedAtMs = 1700001000000L)
        val remoteEditFields = mapOf<String, Any?>(
            "principalAmountPaise" to 1000000L,
            "partyName" to "Pooja Sharma",
            "note" to "Resurrected note"
        )
        val resolutionResurrect = SyncConflictPolicy.resolveTransactionConflict(
            local = locallyDeletedTx,
            remoteFields = remoteEditFields,
            remoteServerRev = 2L,
            remoteHlc = "1700000008000:0:remote",
            remoteDeletedAtMs = null
        )
        assertTrue(resolutionResurrect is SyncConflictPolicy.TransactionResolution.ApplyRemote)
        val merged = (resolutionResurrect as SyncConflictPolicy.TransactionResolution.ApplyRemote).merged
        assertNull("Locally deleted record must be un-deleted by active remote edit", merged.deletedAtMs)
        assertEquals("Pooja Sharma", merged.partyName)
        assertEquals("Resurrected note", merged.notes)
    }

    @Test
    fun `sync repository end-to-end local-first write, outbox drainage and delta pull`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, UdhaarDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.udhaarDao()
        val outboxDao = db.syncOutboxDao()

        // Mock transport connecting to simulated server
        val transport = object : SyncCloudTransport {
            val serverPushedChanges = mutableListOf<com.jyotirmoyb.udhaar.sync.SyncMutationDto>()

            override suspend fun pushMutations(request: SyncPushRequest): SyncPushResponse {
                serverPushedChanges.addAll(request.changes)
                return SyncPushResponse(
                    results = request.changes.map {
                        SyncPushResultDto(syncId = it.syncId, status = "ACCEPTED", serverRev = 1L)
                    }
                )
            }

            override suspend fun pullDeltas(cursor: Long, limit: Int): SyncPullResponse {
                return SyncPullResponse(
                    changes = listOf(
                        SyncDeltaEntryDto(
                            seq = 1L,
                            entity = "record",
                            syncId = "remote_sync_rec_1",
                            op = "CREATE",
                            fields = mapOf(
                                "partyName" to "Remote Counterparty",
                                "principalAmountPaise" to 750000L
                            ),
                            serverRev = 1L,
                            hlc = "1700000010000:0:server",
                            deviceId = "server",
                            timestampMs = 1700000010000L
                        )
                    ),
                    nextCursor = 1L,
                    hasMore = false
                )
            }
        }

        val repository = SyncRepository(dao = dao, outboxDao = outboxDao, transport = transport)

        // 1. Optimistic write while offline
        val createdTx = repository.createTransactionLocalFirst(
            UdhaarTransaction(partyName = "Local Customer", principalAmountPaise = 500000L)
        )
        assertEquals(SyncStatus.LOCAL_ONLY, createdTx.syncStatus)
        assertEquals(1, outboxDao.getTotalCount())

        // 2. Drain outbox
        val drainedCount = repository.drainOutbox()
        assertEquals(1, drainedCount)
        assertEquals(0, outboxDao.getTotalCount())

        val syncedLocalTx = dao.getTransactionBySyncId(createdTx.syncId)
        assertNotNull(syncedLocalTx)
        assertEquals(SyncStatus.SYNCED, syncedLocalTx?.syncStatus)
        assertEquals(1L, syncedLocalTx?.serverRev)

        // 3. Pull delta from server
        val pulledCount = repository.pullDeltas()
        assertEquals(1, pulledCount)

        val pulledTx = dao.getTransactionBySyncId("remote_sync_rec_1")
        assertNotNull(pulledTx)
        assertEquals("Remote Counterparty", pulledTx?.partyName)
        assertEquals(750000L, pulledTx?.principalAmountPaise)
        assertEquals(SyncStatus.SYNCED, pulledTx?.syncStatus)

        db.close()
    }
}
