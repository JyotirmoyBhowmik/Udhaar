package com.jyotirmoyb.udhaar.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.base.FirestoreEmulatorTestBase
import com.jyotirmoyb.udhaar.ui.sheets.settings.FirebaseSparkAuthController
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountAndDataDeletionTest : FirestoreEmulatorTestBase() {

    @Test
    fun deleteAccountAndAllData_clearsAllFirestoreDocuments_andGuestLinks_forUser() = runEmulatorTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testUid = "user_play_compliance_deletion_test"
        val userDoc = db.collection("users").document(testUid)

        // 1. Seed user profile & subcollections
        userDoc.set(mapOf("email" to "tester@udhaar.com", "displayName" to "Tester")).await()
        userDoc.collection("records").document("rec_101").set(mapOf("amountPaise" to 500000L)).await()
        userDoc.collection("people").document("party_101").set(mapOf("name" to "Rahul")).await()
        userDoc.collection("plans").document("plan_101").set(mapOf("frequency" to "MONTHLY")).await()
        userDoc.collection("attachments").document("att_101").set(mapOf("filename" to "bill.jpg")).await()
        userDoc.collection("meta").document("syncSignal").set(mapOf("updatedAt" to 1700000000000L)).await()

        // 2. Seed owned capability guest link with actions and proof
        val linkId = "link_play_deletion_test_4262"
        val linkRef = db.collection("guestLinks").document(linkId)
        linkRef.set(
            mapOf(
                "ownerUid" to testUid,
                "status" to "ACTIVE",
                "payableAmountPaise" to 500000L,
                "schemaVersion" to 1
            )
        ).await()

        val confirmActionRef = linkRef.collection("actions").document("CONFIRM")
        confirmActionRef.set(
            mapOf(
                "actionType" to "CONFIRM",
                "status" to "PENDING",
                "guestUid" to "guest_456",
                "createdAtMs" to 1700000000000L,
                "schemaVersion" to 1
            )
        ).await()

        val claimActionRef = linkRef.collection("actions").document("CLAIM_PAYMENT")
        claimActionRef.set(
            mapOf(
                "actionType" to "CLAIM_PAYMENT",
                "status" to "PENDING",
                "guestUid" to "guest_456",
                "amountPaise" to 200000L,
                "paymentMode" to "UPI",
                "referenceNumber" to "REF123456",
                "createdAtMs" to 1700000000000L,
                "hasProof" to true,
                "schemaVersion" to 1
            )
        ).await()

        val proofImageRef = claimActionRef.collection("proof").document("image")
        proofImageRef.set(
            mapOf(
                "mime" to "image/webp",
                "dataB64" to "dGVzdF9pbWFnZV9kYXRh",
                "createdAtMs" to 1700000000000L
            )
        ).await()

        // Verify pre-deletion existence
        assertTrue("User root doc must exist before deletion", userDoc.get().await().exists())
        assertEquals(1, userDoc.collection("records").get().await().size())
        assertTrue("Guest link must exist before deletion", linkRef.get().await().exists())
        assertTrue("Proof image must exist before deletion", proofImageRef.get().await().exists())

        // 3. Execute deleteAccountAndAllData via FirebaseSparkAuthController
        val controller = FirebaseSparkAuthController(
            authProvider = { auth },
            firestoreProvider = { db }
        )

        val progressLogs = mutableListOf<String>()
        controller.deleteAccountAndAllData(
            context = context,
            uid = testUid,
            knownLinkIds = listOf(linkId),
            deleteDriveBackups = false,
            onProgress = { progressLogs.add(it) }
        )

        // 4. Assert all Firestore collections are completely empty for testUid
        assertFalse("User root doc must be deleted", userDoc.get().await().exists())
        assertEquals("Records subcollection must be 0", 0, userDoc.collection("records").get().await().size())
        assertEquals("People subcollection must be 0", 0, userDoc.collection("people").get().await().size())
        assertEquals("Plans subcollection must be 0", 0, userDoc.collection("plans").get().await().size())
        assertEquals("Attachments subcollection must be 0", 0, userDoc.collection("attachments").get().await().size())
        assertFalse("syncSignal must be deleted", userDoc.collection("meta").document("syncSignal").get().await().exists())

        // Assert guestLink and nested subcollections are deleted
        assertFalse("Guest link doc must be deleted", linkRef.get().await().exists())
        assertFalse("Proof image doc must be deleted", proofImageRef.get().await().exists())
        assertFalse("Confirm action doc must be deleted", confirmActionRef.get().await().exists())
        assertFalse("Claim action doc must be deleted", claimActionRef.get().await().exists())

        // Assert progress callbacks were invoked
        assertTrue("Progress logs should be recorded", progressLogs.isNotEmpty())
    }

    @Test
    fun regulatoryDisclaimers_and_nonLenderPositioning_areConfigured() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val disclaimer = context.getString(R.string.app_regulatory_disclaimer)
        assertNotNull("Regulatory disclaimer string must exist", disclaimer)
        assertTrue("Disclaimer must state Udhaar does not lend money", disclaimer.contains("does not lend money") || disclaimer.contains("ধার"))

        val deleteWebBtnText = context.getString(R.string.about_delete_account_web_btn)
        assertTrue("Delete web button text must exist", deleteWebBtnText.isNotBlank())
    }
}
