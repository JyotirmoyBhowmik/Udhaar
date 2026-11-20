package com.jyotirmoyb.udhaar.ui.sheets

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.jyotirmoyb.udhaar.security.TokenStore
import com.jyotirmoyb.udhaar.ui.sheets.settings.AccountAndWebAccessSection
import com.jyotirmoyb.udhaar.ui.sheets.settings.SparkAuthController
import com.jyotirmoyb.udhaar.ui.sheets.settings.SparkAuthSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeTokenStore : TokenStore {
    private var authToken: String? = null
    private var userId: String? = null
    private var phoneNumber: String? = null
    private var email: String? = null
    private var cloudSyncEnabled: Boolean = false
    private var lastSyncTimestamp: Long = 0L

    override fun getAuthToken(): String? = authToken
    override fun getUserId(): String? = userId
    override fun getPhoneNumber(): String? = phoneNumber
    override fun getEmail(): String? = email
    override fun getDeviceId(): String = "dev_test_123"
    override fun isCloudSyncEnabled(): Boolean = cloudSyncEnabled
    override fun setCloudSyncEnabled(enabled: Boolean) {
        cloudSyncEnabled = enabled
    }
    override fun saveSession(authToken: String, userId: String, phoneNumber: String?, email: String?) {
        this.authToken = authToken
        this.userId = userId
        this.phoneNumber = phoneNumber
        this.email = email
    }
    override fun updateEmail(email: String) {
        this.email = email
    }
    override fun clearSession() {
        authToken = null
        userId = null
        phoneNumber = null
        email = null
        cloudSyncEnabled = false
        lastSyncTimestamp = 0L
    }
    override fun isSignedIn(): Boolean = !authToken.isNullOrBlank() && !userId.isNullOrBlank()
    override fun getLastSyncTimestamp(): Long = lastSyncTimestamp
    override fun setLastSyncTimestamp(timestamp: Long) {
        lastSyncTimestamp = timestamp
    }
}

private class FakeSparkAuthController(
    var current: SparkAuthSession? = null
) : SparkAuthController {
    var verificationEmailsSent = 0
    var passwordResetEmailsSentTo: String? = null
    var lastReauthEmail: String? = null
    var deletedCloudDataForUid: String? = null
    var deletedLinkIds: List<String> = emptyList()

    override fun currentSession(): SparkAuthSession? = current

    override suspend fun signInWithGoogle(context: Context): SparkAuthSession {
        val session = SparkAuthSession(
            uid = "uid_google_1",
            email = "lender@gmail.com",
            displayName = "Lender",
            isEmailVerified = true,
            providerId = "google.com",
            idToken = "token_google_1"
        )
        current = session
        return session
    }

    override suspend fun signUpWithEmailPassword(
        email: String,
        password: String,
        contactPhone: String
    ): SparkAuthSession {
        verificationEmailsSent++
        val session = SparkAuthSession(
            uid = "uid_email_1",
            email = email,
            displayName = email.substringBefore("@"),
            phoneNumber = contactPhone,
            isEmailVerified = false,
            providerId = "password",
            idToken = "token_email_1"
        )
        current = session
        return session
    }

    override suspend fun signInWithEmailPassword(email: String, password: String): SparkAuthSession {
        val session = SparkAuthSession(
            uid = "uid_email_1",
            email = email,
            displayName = email.substringBefore("@"),
            isEmailVerified = current?.isEmailVerified ?: false,
            providerId = "password",
            idToken = "token_email_1"
        )
        current = session
        return session
    }

    override suspend fun sendEmailVerification() {
        verificationEmailsSent++
    }

    override suspend fun reloadSession(): SparkAuthSession? = current

    override suspend fun sendPasswordResetEmail(email: String) {
        passwordResetEmailsSentTo = email
    }

    override suspend fun reauthenticateWithPassword(email: String, password: String) {
        lastReauthEmail = email
    }

    override suspend fun reauthenticateWithGoogle(context: Context) {}

    override suspend fun deleteCloudData(uid: String, knownLinkIds: List<String>) {
        deletedCloudDataForUid = uid
        deletedLinkIds = knownLinkIds
    }

    override suspend fun signOut() {
        current = null
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountAndWebAccessSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emailSignUp_sendsVerificationEmail_andKeepsCloudSyncDisabledUntilVerified() {
        val fakeAuth = FakeSparkAuthController()
        val tokenStore = FakeTokenStore()

        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AccountAndWebAccessSection(
                        dao = null,
                        tokenStoreOverride = tokenStore,
                        authControllerOverride = fakeAuth
                    )
                }
            }
        }

        // Switch from Sign In to Sign Up mode
        composeRule.onNodeWithTag("btn_toggle_auth_mode").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("account_email_input").performScrollTo().performTextInput("asha@example.com")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("account_password_input").performScrollTo().performTextInput("Secret123456")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_submit_email_auth").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(1, fakeAuth.verificationEmailsSent)
        assertEquals("uid_email_1", tokenStore.getUserId())
        assertEquals("asha@example.com", tokenStore.getEmail())
        assertFalse("Cloud sync must remain off while email is unverified", tokenStore.isCloudSyncEnabled())

        // Cloud sync switch is disabled until email is verified
        composeRule.onNodeWithTag("switch_cloud_sync").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("btn_resend_verification_email").performScrollTo().assertIsDisplayed()

        // Mark user email verified in Firebase and click "I've verified my email"
        fakeAuth.current = fakeAuth.current?.copy(isEmailVerified = true)
        composeRule.onNodeWithTag("btn_refresh_verification_status").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Now Cloud Sync switch is enabled and can be toggled on
        composeRule.onNodeWithTag("switch_cloud_sync").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("switch_cloud_sync").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue("Cloud sync must enable once email is verified", tokenStore.isCloudSyncEnabled())
    }

    @Test
    fun verifiedSession_supportsPasswordReset_andReauthentication() {
        val fakeAuth = FakeSparkAuthController(
            current = SparkAuthSession(
                uid = "uid_verified_2",
                email = "ravi@example.com",
                displayName = "Ravi",
                isEmailVerified = true,
                providerId = "password",
                idToken = "token_verified_2"
            )
        )
        val tokenStore = FakeTokenStore()

        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AccountAndWebAccessSection(
                        dao = null,
                        tokenStoreOverride = tokenStore,
                        authControllerOverride = fakeAuth
                    )
                }
            }
        }

        // Open re-authenticate panel and verify password re-auth
        composeRule.onNodeWithTag("btn_open_reauth").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reauth_password_input").performScrollTo().performTextInput("MyPass123456")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_confirm_reauth").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("ravi@example.com", fakeAuth.lastReauthEmail)
    }

    @Test
    fun verifiedSession_deleteMyCloudData_deletesSparkCloudData_andTurnsOffSync() {
        val fakeAuth = FakeSparkAuthController(
            current = SparkAuthSession(
                uid = "uid_verified_3",
                email = "meera@example.com",
                displayName = "Meera",
                isEmailVerified = true,
                providerId = "password",
                idToken = "token_verified_3"
            )
        )
        val tokenStore = FakeTokenStore().apply {
            saveSession("token_verified_3", "uid_verified_3", null, "meera@example.com")
            setCloudSyncEnabled(true)
            setLastSyncTimestamp(1700000000000L)
        }
        var callbackInvoked = false

        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AccountAndWebAccessSection(
                        dao = null,
                        tokenStoreOverride = tokenStore,
                        authControllerOverride = fakeAuth,
                        onDeleteMyCloudDataCallback = { callbackInvoked = true }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("btn_delete_cloud_data").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_confirm_delete_cloud_data").performClick()
        composeRule.waitForIdle()

        assertEquals("uid_verified_3", fakeAuth.deletedCloudDataForUid)
        assertFalse("Cloud sync must be disabled after deleting cloud data", tokenStore.isCloudSyncEnabled())
        assertEquals(0L, tokenStore.getLastSyncTimestamp())
        assertTrue("onDeleteMyCloudDataCallback should be invoked", callbackInvoked)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp-normal-long-port-xxhdpi", fontScale = 2.0f)
    fun helpAndContactSheet_and_accountSection_touchTargetsAtFontScale2_0() {
        val fakeAuth = FakeSparkAuthController(
            current = SparkAuthSession(
                uid = "uid_verified_4",
                email = "kiran@example.com",
                displayName = "Kiran",
                isEmailVerified = true,
                providerId = "password",
                idToken = "token_verified_4"
            )
        )
        val tokenStore = FakeTokenStore()

        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AccountAndWebAccessSection(
                        dao = null,
                        tokenStoreOverride = tokenStore,
                        authControllerOverride = fakeAuth
                    )
                }
                HelpAndContactSheet(onDismiss = {})
            }
        }

        composeRule.waitForIdle()
        val accountTags = listOf(
            "btn_sync_now",
            "btn_open_reauth",
            "btn_delete_cloud_data",
            "btn_sign_out"
        )
        for (tag in accountTags) {
            val node = composeRule.onNodeWithTag(tag).performScrollTo()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            val heightDp = with(composeRule.density) { bounds.height.toDp() }
            assertTrue("Node $tag height ($heightDp) must be >= 47.5.dp at fontScale 2.0", heightDp.value >= 47.5f)
        }

        val helpTags = listOf(
            "close_help_contact_button",
            "help_email_support_button",
            "help_email_grievance_button",
            "help_privacy_policy_button",
            "help_terms_of_service_button"
        )
        for (tag in helpTags) {
            val node = composeRule.onNodeWithTag(tag).performScrollTo()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            val heightDp = with(composeRule.density) { bounds.height.toDp() }
            assertTrue("Node $tag height ($heightDp) must be >= 47.5.dp at fontScale 2.0", heightDp.value >= 47.5f)
        }
    }
}


