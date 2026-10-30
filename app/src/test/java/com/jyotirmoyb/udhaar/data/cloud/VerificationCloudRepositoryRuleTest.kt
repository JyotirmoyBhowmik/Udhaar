package com.jyotirmoyb.udhaar.data.cloud

import com.jyotirmoyb.udhaar.base.FirestoreEmulatorTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VerificationCloudRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var repository: VerificationCloudRepository
    private val sampleHash = "a".repeat(64)

    @Before
    fun setUp() {
        repository = VerificationCloudRepository(db)
    }

    @Test
    fun `user can upsert and read their own payment profile`() = runEmulatorTest {
        val lenderUid = signInAs("lender@udhaar.test")
        val profile = CloudUserProfile(
            userId = lenderUid,
            displayName = "Lender User",
            phoneNumber = "+919876543210",
            upiId = "lender@upi",
            photoUri = ""
        )
        repository.upsertUserProfile(profile)

        val fetched = repository.getUserProfile(lenderUid)
        assertNotNull(fetched)
        assertEquals("lender@upi", fetched?.upiId)
    }

    @Test
    fun `two-party verification record can be created by lender, confirmed by borrower, and blocked for outsider`() = runEmulatorTest {
        val lenderUid = signInAs("lender@udhaar.test")
        val record = CloudSharedRecord(
            remoteId = "rec-kotlin-1",
            creatorUid = lenderUid,
            counterpartyUid = "",
            participantUids = listOf(lenderUid),
            lenderName = "Lender User",
            borrowerName = "Borrower User",
            creatorRole = "GIVEN",
            currency = "INR",
            principalAmount = 5000.0,
            amountPaid = 0.0,
            paymentMode = "UPI",
            isInterestEnabled = true,
            interestType = "SIMPLE",
            interestRate = 12.0,
            interestRatePeriod = "PER_ANNUM",
            compoundFrequency = "YEARLY",
            startDateMillis = 1710000000000L,
            expiryDateMillis = -1L,
            contentHash = sampleHash,
            verificationStatus = "PENDING",
            verifiedAtMillis = -1L,
            disputeReason = "",
            encryptedNotes = "ciphertext-note",
            attachmentRefs = emptyList(),
            isSettled = false
        )
        repository.createSharedRecord(record)

        // Borrower claims and confirms the record
        val borrowerUid = signInAs("borrower@udhaar.test")
        repository.claimAndRespondSharedRecord(
            remoteId = "rec-kotlin-1",
            creatorUid = lenderUid,
            counterpartyUid = borrowerUid,
            verificationStatus = "CONFIRMED",
            verifiedAtMillis = 1710000050000L,
            disputeReason = ""
        )

        val payment = CloudPartialPayment(
            paymentId = "pay-1",
            remoteId = "rec-kotlin-1",
            recordedByUid = borrowerUid,
            recordedByName = "Borrower User",
            participantUids = listOf(lenderUid, borrowerUid),
            amount = 2000.0,
            paymentMode = "UPI",
            paidAtMillis = 1710000100000L,
            encryptedNote = "",
            isAcknowledged = false,
            acknowledgedByUid = "",
            acknowledgedAtMillis = -1L
        )
        repository.createPartialPayment(payment)

        val audit = CloudAuditEvent(
            eventId = "ev-1",
            remoteId = "rec-kotlin-1",
            eventType = "CONFIRMED",
            actorUid = borrowerUid,
            actorName = "Borrower User",
            participantUids = listOf(lenderUid, borrowerUid),
            summaryText = "Borrower confirmed record",
            contentHashSnapshot = sampleHash,
            acknowledged = true,
            eventTimeMillis = 1710000050000L
        )
        repository.appendAuditEvent(audit)

        // Switch to lender to acknowledge the partial payment
        signInAs("lender@udhaar.test")
        repository.acknowledgePartialPayment(
            remoteId = "rec-kotlin-1",
            paymentId = "pay-1",
            acknowledgedByUid = lenderUid,
            acknowledgedAtMillis = 1710000200000L
        )
        val payments = repository.listPartialPayments("rec-kotlin-1", lenderUid)
        assertEquals(1, payments.size)
        assertTrue(payments.first().isAcknowledged)

        // Switch to outsider — reading claimed record must fail with PERMISSION_DENIED
        signInAs("outsider@udhaar.test")
        assertPermissionDenied {
            repository.getSharedRecord("rec-kotlin-1")
        }
    }

    @Test
    fun `chat thread and extension request messages work for participants and deny outsiders`() = runEmulatorTest {
        val lenderUid = signInAs("lender-chat@udhaar.test")
        val borrowerUid = signInAs("borrower-chat@udhaar.test")

        // Switch back to lender to create the thread
        signInAs("lender-chat@udhaar.test")
        val thread = CloudChatThread(
            threadId = "tx_rec-chat-1",
            scope = "TRANSACTION",
            remoteId = "rec-chat-1",
            partyName = "Borrower User",
            creatorUid = lenderUid,
            counterpartyUid = borrowerUid,
            participantUids = listOf(lenderUid, borrowerUid),
            lastMessagePreview = "Thread opened",
            lastMessageAtMillis = 1710000000000L
        )
        repository.upsertChatThread(thread)

        // Borrower sends a quick reply and an extension request
        signInAs("borrower-chat@udhaar.test")
        val quickReply = CloudChatMessage(
            messageId = "msg-qr-1",
            threadId = "tx_rec-chat-1",
            remoteId = "rec-chat-1",
            senderUid = borrowerUid,
            senderName = "Borrower User",
            participantUids = listOf(lenderUid, borrowerUid),
            messageType = "QUICK_REPLY",
            textContent = "I will pay on Friday",
            proposedDueDateMillis = -1L,
            extensionStatus = "NONE",
            deliveryStatus = "SENT",
            sentAtMillis = 1710000010000L
        )
        repository.sendChatMessage(quickReply)

        val extReq = CloudChatMessage(
            messageId = "msg-ext-1",
            threadId = "tx_rec-chat-1",
            remoteId = "rec-chat-1",
            senderUid = borrowerUid,
            senderName = "Borrower User",
            participantUids = listOf(lenderUid, borrowerUid),
            messageType = "EXTENSION_REQUEST",
            textContent = "Requesting extension until next Friday",
            proposedDueDateMillis = 1711500000000L,
            extensionStatus = "PENDING",
            deliveryStatus = "SENT",
            sentAtMillis = 1710000020000L
        )
        repository.sendChatMessage(extReq)

        // Lender marks message read and accepts extension request
        signInAs("lender-chat@udhaar.test")
        repository.updateChatMessageDeliveryOrExtension(
            threadId = "tx_rec-chat-1",
            messageId = "msg-ext-1",
            deliveryStatus = "READ",
            deliveredAtMillis = 1710000030000L,
            readAtMillis = 1710000035000L,
            extensionStatus = "ACCEPTED"
        )

        val messages = repository.listChatMessages("tx_rec-chat-1", lenderUid)
        assertEquals(2, messages.size)
        val updatedExt = messages.first { it.messageId == "msg-ext-1" }
        assertEquals("ACCEPTED", updatedExt.extensionStatus)
        assertEquals("READ", updatedExt.deliveryStatus)

        // Outsider cannot read chat thread
        signInAs("outsider-chat@udhaar.test")
        assertPermissionDenied {
            repository.getChatThread("tx_rec-chat-1")
        }
    }
}
