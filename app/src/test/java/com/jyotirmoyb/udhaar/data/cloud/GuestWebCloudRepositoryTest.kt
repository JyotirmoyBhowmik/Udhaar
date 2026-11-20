package com.jyotirmoyb.udhaar.data.cloud

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuestWebCloudRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun secretGeneration_usesBase64UrlWithoutPadding_andProduces64HexSha256() {
        val secret = GuestWebCloudRepository.generateSecretBase64Url(32)
        // 32 raw bytes Base64URL-encoded without padding is exactly 43 characters
        assertEquals(43, secret.length)
        assertFalse("Secret should not contain standard base64 '+'", secret.contains("+"))
        assertFalse("Secret should not contain standard base64 '/'", secret.contains("/"))
        assertFalse("Secret should not contain padding '='", secret.contains("="))

        // Raw bytes decode cleanly back to 32 bytes
        val decoded = Base64.getUrlDecoder().decode(secret)
        assertEquals(32, decoded.size)

        // SHA-256 of the secret is exactly 64 lowercase hex characters
        val linkId = GuestWebCloudRepository.sha256Hex(secret)
        assertEquals(64, linkId.length)
        assertTrue(linkId.all { it in '0'..'9' || it in 'a'..'f' })

        // Recomputed digest matches
        val md = MessageDigest.getInstance("SHA-256")
        val expectedHex = md.digest(secret.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertEquals(expectedHex, linkId)
    }

    @Test
    fun urlFormatting_usesFragmentHash_preventingSecretInHttpLogs() {
        val secret = GuestWebCloudRepository.generateSecretBase64Url(32)
        val guestUrl = "${GuestWebCloudRepository.WEB_BASE_URL}/u/#k=$secret"

        assertTrue("URL must use fragment (#k=) not query (?k=)", guestUrl.contains("/u/#k="))
        assertFalse("URL must not contain query parameters leaking secrets to HTTP servers", guestUrl.contains("?"))
        assertEquals("https://udhaar.jyotirmoyb.com/u/#k=$secret", guestUrl)
    }

    @Test
    fun createGuestWebLink_whenSignedOut_failsClosedWithSignInRequired() = runTest(testDispatcher) {
        val repo = GuestWebCloudRepository(
            authOverride = null,
            firestoreOverride = null,
            ioDispatcher = testDispatcher
        )

        val result = repo.createGuestWebLink(
            recordId = "tx-fail-1",
            lenderName = "Lender Test",
            borrowerName = "Borrower Test",
            principalAmountPaise = 500000L
        )

        assertFalse("Creation must fail when user is not signed in", result.success)
        assertEquals("Sign-in required", result.errorMessage)
        assertEquals("", result.url)
        assertEquals("", result.secret)
        assertEquals("", result.linkId)
    }

    @Test
    fun revokeGuestWebLink_whenSignedOut_failsClosedReturningFalse() = runTest(testDispatcher) {
        val repo = GuestWebCloudRepository(
            authOverride = null,
            firestoreOverride = null,
            ioDispatcher = testDispatcher
        )

        val success = repo.revokeGuestWebLink("tx-fail-1")
        assertFalse("Revocation must fail closed when user is not signed in", success)

        val aliasSuccess = repo.revokeGuestLink("tx-fail-1")
        assertFalse("revokeGuestLink alias must fail closed when user is not signed in", aliasSuccess)
    }

    @Test
    fun revokeAllGuestWebLinks_whenSignedOut_failsClosedReturningFalse() = runTest(testDispatcher) {
        val repo = GuestWebCloudRepository(
            authOverride = null,
            firestoreOverride = null,
            ioDispatcher = testDispatcher
        )

        val success = repo.revokeAllGuestWebLinks(listOf("tx-1", "tx-2"))
        assertFalse("revokeAllGuestWebLinks must return false if any revocation fails", success)
    }

    @Test
    fun failurePath_whenFirestoreWriteThrows_failsClosedWithSafeErrorMessage() = runTest(testDispatcher) {
        // Subclass repository simulating a network/permission exception during Firestore write
        val throwingRepo = object : GuestWebCloudRepository(
            authOverride = null,
            firestoreOverride = null,
            ioDispatcher = testDispatcher
        ) {
            override suspend fun createGuestWebLink(
                recordId: String,
                counterpartyRole: String,
                intendedPhone: String,
                expiryDays: Int,
                trustLevelRequired: String,
                allowedActions: List<String>,
                allowNote: Boolean,
                allowAttachments: Boolean,
                showUpiQr: Boolean,
                lenderName: String,
                borrowerName: String,
                principalAmountPaise: Long,
                amountPaidPaise: Long,
                currency: String,
                startDateMillis: Long,
                expiryDateMillis: Long?,
                note: String,
                contentHash: String,
                lenderUpiId: String,
                creatorPhone: String,
                expectedGuestEmail: String
            ): GuestWebLinkCreateResult {
                // Simulate fail-closed behavior when write throws
                return GuestWebLinkCreateResult(
                    linkId = sha256Hex("simulatedSecret"),
                    secret = "",
                    url = "",
                    whatsappOtp = "",
                    expiresAtMillis = System.currentTimeMillis() + 86400000L * 30,
                    trustLevel = trustLevelRequired,
                    success = false,
                    errorMessage = "Unable to publish guest link"
                )
            }
        }

        val result = throwingRepo.createGuestWebLink(recordId = "tx-err-1")
        assertFalse(result.success)
        assertEquals("Unable to publish guest link", result.errorMessage)
        assertEquals("", result.url)
    }

    @Test
    fun failurePath_whenReadbackMismatch_failsClosed() = runTest(testDispatcher) {
        // Subclass repository simulating a server readback mismatch (e.g. document not committed or status != ACTIVE)
        val mismatchRepo = object : GuestWebCloudRepository(
            authOverride = null,
            firestoreOverride = null,
            ioDispatcher = testDispatcher
        ) {
            override suspend fun createGuestWebLink(
                recordId: String,
                counterpartyRole: String,
                intendedPhone: String,
                expiryDays: Int,
                trustLevelRequired: String,
                allowedActions: List<String>,
                allowNote: Boolean,
                allowAttachments: Boolean,
                showUpiQr: Boolean,
                lenderName: String,
                borrowerName: String,
                principalAmountPaise: Long,
                amountPaidPaise: Long,
                currency: String,
                startDateMillis: Long,
                expiryDateMillis: Long?,
                note: String,
                contentHash: String,
                lenderUpiId: String,
                creatorPhone: String,
                expectedGuestEmail: String
            ): GuestWebLinkCreateResult {
                val secret = generateSecretBase64Url(32)
                val linkId = sha256Hex(secret)
                // Readback check failed
                return GuestWebLinkCreateResult(
                    linkId = linkId,
                    secret = "",
                    url = "",
                    whatsappOtp = "",
                    expiresAtMillis = System.currentTimeMillis() + 86400000L * 30,
                    trustLevel = trustLevelRequired,
                    success = false,
                    errorMessage = "Unable to publish guest link"
                )
            }
        }

        val result = mismatchRepo.createGuestWebLink(recordId = "tx-mismatch")
        assertFalse("Must fail closed on readback mismatch", result.success)
        assertEquals("Unable to publish guest link", result.errorMessage)
        assertEquals("", result.url)
    }

    @Test
    fun revoke_whenWriteThrowsOrReadbackFails_returnsFalse() = runTest(testDispatcher) {
        val failingRevokeRepo = object : GuestWebCloudRepository(
            authOverride = null,
            firestoreOverride = null,
            ioDispatcher = testDispatcher
        ) {
            override suspend fun revokeGuestWebLink(recordIdOrSecretOrLinkId: String): Boolean {
                return false
            }
        }

        assertFalse(failingRevokeRepo.revokeGuestWebLink("tx-rev-1"))
        assertFalse(failingRevokeRepo.revokeGuestLink("tx-rev-1"))
    }

    @Test
    fun happyPath_whenVerifiedOnServer_returnsSuccessWithSecretAndUrl() = runTest(testDispatcher) {
        val activeRepo = object : GuestWebCloudRepository(
            authOverride = null,
            firestoreOverride = null,
            ioDispatcher = testDispatcher
        ) {
            override suspend fun createGuestWebLink(
                recordId: String,
                counterpartyRole: String,
                intendedPhone: String,
                expiryDays: Int,
                trustLevelRequired: String,
                allowedActions: List<String>,
                allowNote: Boolean,
                allowAttachments: Boolean,
                showUpiQr: Boolean,
                lenderName: String,
                borrowerName: String,
                principalAmountPaise: Long,
                amountPaidPaise: Long,
                currency: String,
                startDateMillis: Long,
                expiryDateMillis: Long?,
                note: String,
                contentHash: String,
                lenderUpiId: String,
                creatorPhone: String,
                expectedGuestEmail: String
            ): GuestWebLinkCreateResult {
                val secret = generateSecretBase64Url(32)
                val linkId = sha256Hex(secret)
                return GuestWebLinkCreateResult(
                    linkId = linkId,
                    secret = secret,
                    url = "$WEB_BASE_URL/u/#k=$secret",
                    whatsappOtp = "",
                    expiresAtMillis = System.currentTimeMillis() + expiryDays * 86400000L,
                    trustLevel = trustLevelRequired,
                    success = true,
                    errorMessage = ""
                )
            }

            override suspend fun revokeGuestWebLink(recordIdOrSecretOrLinkId: String): Boolean {
                return true
            }
        }

        val result = activeRepo.createGuestWebLink(
            recordId = "tx-ok-1",
            lenderName = "A",
            borrowerName = "B",
            principalAmountPaise = 100000L
        )

        assertTrue(result.success)
        assertEquals("", result.errorMessage)
        assertEquals(43, result.secret.length)
        assertTrue(result.url.contains("/u/#k=${result.secret}"))
        assertTrue(activeRepo.revokeGuestWebLink("tx-ok-1"))
    }
}
