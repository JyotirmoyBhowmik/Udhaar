package com.jyotirmoyb.udhaar.security

import com.google.firebase.Timestamp
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.GuestActionV1
import com.jyotirmoyb.udhaar.data.GuestDateProposalData
import com.jyotirmoyb.udhaar.data.GuestPaymentClaimData
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.cloud.CloudAuditEvent
import com.jyotirmoyb.udhaar.data.cloud.CloudChatMessage
import com.jyotirmoyb.udhaar.data.cloud.CloudChatThread
import com.jyotirmoyb.udhaar.data.cloud.CloudPartialPayment
import com.jyotirmoyb.udhaar.data.cloud.CloudSharedRecord
import com.jyotirmoyb.udhaar.data.cloud.CloudUserProfile
import com.jyotirmoyb.udhaar.sync.SyncDeltaEntryDto
import com.jyotirmoyb.udhaar.sync.SyncMutationDto
import com.jyotirmoyb.udhaar.sync.SyncPushRequest
import com.jyotirmoyb.udhaar.sync.SyncPushResponse
import com.jyotirmoyb.udhaar.sync.SyncPushResultDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Date

/**
 * Validates Firestore model mapping, JavaBean reflection requirements,
 * serialization roundtrips, and ProGuard / R8 keep rules.
 */
class FirestoreModelMappingTest {

    @Test
    fun cloudSharedRecord_instantiatesWithDefaults_andSupportsReflection() {
        // Firestore toObject requires a no-argument constructor (which Kotlin data classes
        // provide when all properties have default values).
        val defaultRecord = CloudSharedRecord()
        assertEquals("", defaultRecord.remoteId)
        assertEquals("INR", defaultRecord.currency)
        assertEquals("CASH", defaultRecord.paymentMode)
        assertEquals(0.0, defaultRecord.principalAmount, 0.0)
        assertEquals(false, defaultRecord.isSettled)

        val populatedRecord = CloudSharedRecord(
            remoteId = "rem_456",
            creatorUid = "uid_123",
            counterpartyUid = "uid_789",
            participantUids = listOf("uid_123", "uid_789"),
            lenderName = "Aman",
            borrowerName = "Bikash",
            principalAmount = 5000.0,
            amountPaid = 1500.0,
            paymentMode = "UPI",
            isSettled = false
        )
        assertEquals("rem_456", populatedRecord.remoteId)
        assertEquals(2, populatedRecord.participantUids.size)
        assertEquals(5000.0, populatedRecord.principalAmount, 0.0)
    }

    @Test
    fun cloudUserProfile_and_subcollections_instantiateCorrectly() {
        val userProfile = CloudUserProfile()
        assertEquals("", userProfile.userId)
        assertNotNull(userProfile.createdAt)

        val partialPayment = CloudPartialPayment(
            paymentId = "pay_1",
            amount = 1000.0,
            paymentMode = "UPI"
        )
        assertEquals("pay_1", partialPayment.paymentId)

        val auditEvent = CloudAuditEvent(
            eventId = "evt_1",
            eventType = "RECORD_CREATED"
        )
        assertEquals("RECORD_CREATED", auditEvent.eventType)

        val chatThread = CloudChatThread(
            threadId = "th_1"
        )
        assertEquals("th_1", chatThread.threadId)

        val chatMessage = CloudChatMessage(
            messageId = "msg_1",
            textContent = "Payment received"
        )
        assertEquals("Payment received", chatMessage.textContent)
    }

    @Test
    fun guestActionV1_sealedHierarchy_parsesAllActionTypesFromDocumentMap() {
        val now = 1700000000000L

        // Confirm
        val confirmMap = mapOf<String, Any?>(
            "actionType" to "CONFIRM",
            "status" to "PENDING",
            "guestUid" to "guest_1",
            "createdAtMs" to now,
            "verificationLevel" to "PRIVATE_LINK"
        )
        val confirm = GuestActionV1.fromMap(confirmMap, "CONFIRM", "link_1") as? GuestActionV1.Confirm
        assertNotNull(confirm)
        assertEquals("CONFIRM", confirm!!.actionType)
        assertEquals("link_1/CONFIRM", confirm.compositeKey)

        // Dispute
        val disputeMap = mapOf<String, Any?>(
            "actionType" to "DISPUTE",
            "status" to "PENDING",
            "guestUid" to "guest_1",
            "createdAtMs" to now,
            "reason" to "Amount mismatch"
        )
        val dispute = GuestActionV1.fromMap(disputeMap, "DISPUTE", "link_1") as? GuestActionV1.Dispute
        assertNotNull(dispute)
        assertEquals("Amount mismatch", dispute!!.reason)

        // ProposeDate
        val proposeDateMap = mapOf<String, Any?>(
            "actionType" to "PROPOSE_DATE",
            "status" to "PENDING",
            "guestUid" to "guest_1",
            "createdAtMs" to now,
            "proposedDateMs" to now + 86400000L,
            "reason" to "Salary delay"
        )
        val proposeDate = GuestActionV1.fromMap(proposeDateMap, "PROPOSE_DATE", "link_1") as? GuestActionV1.ProposeDate
        assertNotNull(proposeDate)
        assertEquals(now + 86400000L, proposeDate!!.proposedDateMs)

        // ClaimPayment
        val claimMap = mapOf<String, Any?>(
            "actionType" to "CLAIM_PAYMENT",
            "status" to "PENDING",
            "guestUid" to "guest_1",
            "createdAtMs" to now,
            "amountPaise" to 250000L,
            "paymentMode" to "UPI",
            "referenceNumber" to "REF98765",
            "note" to "Paid via GPay",
            "hasProof" to true
        )
        val claim = GuestActionV1.fromMap(claimMap, "CLAIM_PAYMENT", "link_1") as? GuestActionV1.ClaimPayment
        assertNotNull(claim)
        assertEquals(250000L, claim!!.amountPaise)
        assertEquals(PaymentMode.UPI, claim.paymentMode)
        assertTrue(claim.hasProof)
    }

    @Test
    fun guestEntityPayloads_instantiateAndSerialize() {
        val claimData = GuestPaymentClaimData(
            amountPaise = 150000L,
            dateMillis = 1700000000000L,
            paymentMode = PaymentMode.UPI,
            reference = "UPI12345",
            note = "Paid back partial",
            hasProof = true,
            linkId = "link_101",
            actionId = "CLAIM_PAYMENT"
        )
        assertEquals("UPI12345", claimData.reference)
        assertEquals(1500.0, claimData.amountDouble, 0.0)

        val dateData = GuestDateProposalData(
            proposedDateMillis = 1710000000000L,
            message = "Extension requested"
        )
        assertEquals(1710000000000L, dateData.proposedDateMillis)
        assertEquals("Extension requested", dateData.message)
    }

    @Test
    fun syncModels_kotlinxSerialization_roundtripsCorrectly() {
        val json = Json { ignoreUnknownKeys = true }
        val mutation = SyncMutationDto(
            entity = "TRANSACTION",
            syncId = "tx_42",
            op = "UPSERT",
            fields = mapOf("amountPaise" to 500000L, "currency" to "INR"),
            hlc = "2026-11-21T00:00:00.000Z-0001-dev1",
            idempotencyKey = "idem_123"
        )
        val request = SyncPushRequest(
            deviceId = "device_test_1",
            changes = listOf(mutation)
        )

        val serialized = json.encodeToString(request)
        assertTrue(serialized.contains("tx_42"))
        assertTrue(serialized.contains("UPSERT"))

        val deserialized = json.decodeFromString<SyncPushRequest>(serialized)
        assertEquals(1, deserialized.changes.size)
        assertEquals("tx_42", deserialized.changes[0].syncId)
    }

    @Test
    fun proguardRules_containRequiredKeepRules_forAllCriticalSubsystems() {
        val proguardCandidates = listOf(
            File("app/proguard-rules.pro"),
            File("proguard-rules.pro")
        )
        val proguardFile = proguardCandidates.firstOrNull { it.exists() }
        assertNotNull("proguard-rules.pro must exist", proguardFile)
        val content = proguardFile!!.readText()

        // 1. Room
        assertTrue("Must keep Room entities", content.contains("@androidx.room.Entity"))
        assertTrue("Must keep Room DAOs", content.contains("@androidx.room.Dao"))

        // 2. Firebase Models / Firestore toObject
        assertTrue("Must keep Firestore PropertyName", content.contains("com.google.firebase.firestore.PropertyName"))
        assertTrue("Must keep cloud data models", content.contains("com.jyotirmoyb.udhaar.data.cloud.**"))
        assertTrue("Must keep GuestActionV1 models", content.contains("com.jyotirmoyb.udhaar.data.GuestActionV1**"))

        // 3. kotlinx.serialization & Gson
        assertTrue("Must keep @Serializable classes", content.contains("@kotlinx.serialization.Serializable"))
        assertTrue("Must keep serializer classes", content.contains("*$\$serializer"))
        assertTrue("Must keep sync package", content.contains("com.jyotirmoyb.udhaar.sync.**"))

        // 4. SQLCipher
        assertTrue("Must keep SQLCipher native JNI bridge", content.contains("net.zetetic.database.**"))
        assertTrue("Must keep SQLCipher package", content.contains("net.zetetic.database.sqlcipher.**"))

        // 5. WorkManager & Hilt
        assertTrue("Must keep ListenableWorker constructors", content.contains("androidx.work.ListenableWorker"))
        assertTrue("Must keep HiltWorker constructors", content.contains("@androidx.hilt.work.HiltWorker"))
        assertTrue("Must keep HiltViewModel classes", content.contains("@dagger.hilt.android.lifecycle.HiltViewModel"))
    }
}
