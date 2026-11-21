package com.jyotirmoyb.udhaar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestActionV1Test {

    @Test
    fun parseConfirmAction_valid() {
        val map = mapOf(
            "schemaVersion" to 1L,
            "actionType" to "CONFIRM",
            "guestUid" to "guest_123",
            "status" to "PENDING",
            "createdAtMs" to 1700000000000L,
            "verificationLevel" to "GOOGLE_VERIFIED"
        )
        val action = GuestActionV1.fromMap(map, actionId = "confirm", linkId = "link_abc")
        assertNotNull(action)
        assertTrue(action is GuestActionV1.Confirm)
        val confirm = action as GuestActionV1.Confirm
        assertEquals("link_abc", confirm.linkId)
        assertEquals("confirm", confirm.actionId)
        assertEquals("guest_123", confirm.guestUid)
        assertEquals("PENDING", confirm.status)
        assertEquals(1700000000000L, confirm.createdAtMs)
        assertEquals("GOOGLE_VERIFIED", confirm.verificationLevel)
        assertEquals("link_abc/confirm", confirm.compositeKey)
        assertEquals("confirm_1700000000000", confirm.uniqueKey)
    }

    @Test
    fun parseDisputeAction_valid() {
        val map = mapOf(
            "schemaVersion" to 1L,
            "actionType" to "DISPUTE",
            "guestUid" to "guest_456",
            "status" to "PENDING",
            "createdAtMs" to 1700000001000L,
            "reason" to "Amount was already paid in cash"
        )
        val action = GuestActionV1.fromMap(map, actionId = "dispute", linkId = "link_def")
        assertNotNull(action)
        assertTrue(action is GuestActionV1.Dispute)
        val dispute = action as GuestActionV1.Dispute
        assertEquals("Amount was already paid in cash", dispute.reason)
        assertEquals("DISPUTE", dispute.actionType)
    }

    @Test
    fun parseProposeDateAction_valid() {
        val map = mapOf(
            "schemaVersion" to 1L,
            "actionType" to "PROPOSE_DATE",
            "guestUid" to "guest_789",
            "status" to "PENDING",
            "createdAtMs" to 1700000002000L,
            "proposedDateMs" to 1750000000000L,
            "reason" to "Salary will be credited on 25th"
        )
        val action = GuestActionV1.fromMap(map, actionId = "date_1", linkId = "link_ghi")
        assertNotNull(action)
        assertTrue(action is GuestActionV1.ProposeDate)
        val prop = action as GuestActionV1.ProposeDate
        assertEquals(1750000000000L, prop.proposedDateMs)
        assertEquals("Salary will be credited on 25th", prop.reason)
    }

    @Test
    fun parseClaimPaymentAction_validModes() {
        val modes = listOf("UPI", "CASH", "BANK_TRANSFER")
        for (mode in modes) {
            val map = mapOf(
                "schemaVersion" to 1L,
                "actionType" to "CLAIM_PAYMENT",
                "guestUid" to "guest_999",
                "status" to "PENDING",
                "createdAtMs" to 1700000003000L,
                "amountPaise" to 500000L,
                "paymentMode" to mode,
                "referenceNumber" to "UPI-1234567890",
                "note" to "Paid via GPay",
                "hasProof" to true
            )
            val action = GuestActionV1.fromMap(map, actionId = "claim_1", linkId = "link_jkl")
            assertNotNull("Should parse mode: $mode", action)
            assertTrue(action is GuestActionV1.ClaimPayment)
            val claim = action as GuestActionV1.ClaimPayment
            assertEquals(500000L, claim.amountPaise)
            assertEquals(PaymentMode.valueOf(mode), claim.paymentMode)
            assertEquals("UPI-1234567890", claim.referenceNumber)
            assertEquals("Paid via GPay", claim.note)
            assertTrue(claim.hasProof)
        }
    }

    @Test
    fun parseClaimPayment_unknownPaymentMode_returnsNull() {
        val unknownModes = listOf("BITCOIN", "UNKNOWN_WALLET", "BARTER", "DOGE")
        for (badMode in unknownModes) {
            val map = mapOf(
                "schemaVersion" to 1L,
                "actionType" to "CLAIM_PAYMENT",
                "guestUid" to "guest_999",
                "status" to "PENDING",
                "createdAtMs" to 1700000003000L,
                "amountPaise" to 500000L,
                "paymentMode" to badMode,
                "referenceNumber" to "REF123"
            )
            val action = GuestActionV1.fromMap(map, actionId = "claim_bad", linkId = "link_jkl")
            assertNull("Unknown mode '$badMode' must fail parsing and return null", action)
        }
    }

    @Test
    fun parseInvalidSchemaVersion_returnsNull() {
        val map = mapOf(
            "schemaVersion" to 2L, // unsupported
            "actionType" to "CONFIRM",
            "guestUid" to "guest_123",
            "status" to "PENDING",
            "createdAtMs" to 1700000000000L
        )
        val action = GuestActionV1.fromMap(map, actionId = "confirm", linkId = "link_abc")
        assertNull("schemaVersion != 1 must return null", action)
    }

    @Test
    fun parseInvalidZeroOrNegativeAmount_returnsNull() {
        val map = mapOf(
            "schemaVersion" to 1L,
            "actionType" to "CLAIM_PAYMENT",
            "guestUid" to "guest_123",
            "status" to "PENDING",
            "createdAtMs" to 1700000000000L,
            "amountPaise" to 0L,
            "paymentMode" to "UPI"
        )
        val action = GuestActionV1.fromMap(map, actionId = "claim_zero", linkId = "link_abc")
        assertNull("amountPaise <= 0 must return null", action)
    }

    @Test
    fun parseDisputeWithoutReason_returnsNull() {
        val map = mapOf(
            "schemaVersion" to 1L,
            "actionType" to "DISPUTE",
            "guestUid" to "guest_123",
            "status" to "PENDING",
            "createdAtMs" to 1700000000000L
        )
        val action = GuestActionV1.fromMap(map, actionId = "dispute", linkId = "link_abc")
        assertNull("Dispute without reason must return null", action)
    }
}
