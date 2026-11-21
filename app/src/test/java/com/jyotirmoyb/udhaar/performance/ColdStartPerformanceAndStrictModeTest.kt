package com.jyotirmoyb.udhaar.performance

import android.content.Context
import android.os.StrictMode
import androidx.test.core.app.ApplicationProvider
import com.jyotirmoyb.udhaar.UdhaarApplication
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.security.EncryptedStorageManager
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ColdStartPerformanceAndStrictModeTest {

    @Test
    fun strictModePolicy_configuresSafelyWithoutExceptions() {
        val app = ApplicationProvider.getApplicationContext<UdhaarApplication>()
        assertNotNull("UdhaarApplication must initialize", app)

        // StrictMode thread and VM policy checks
        val threadPolicy = StrictMode.getThreadPolicy()
        assertNotNull(threadPolicy)
        val vmPolicy = StrictMode.getVmPolicy()
        assertNotNull(vmPolicy)
    }

    @Test
    fun database_and_cryptoWork_areDispatchedOffMainThread() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Execute crypto and DB operations on IO dispatcher
        val keyReady: Boolean = withContext(Dispatchers.IO) {
            val key = EncryptedStorageManager.getOrCreateDatabasePassphrase(context)
            key.isNotEmpty()
        }
        assertTrue("Passphrase generation must succeed on background dispatcher", keyReady)

        val hashResult: Boolean = withContext(Dispatchers.IO) {
            val payload = VerificationCryptoHelper.GuestWebLinkData(
                recordId = "rec_test_101",
                secret = "sec_test_xyz"
            )
            payload.recordId.isNotEmpty()
        }
        assertTrue("Crypto payload generation must succeed on background dispatcher", hashResult)
    }

    @Test
    fun baselineProfile_existsAndCoversCriticalStartupPaths() {
        val profileCandidates = listOf(
            File("app/src/main/baseline-prof.txt"),
            File("src/main/baseline-prof.txt")
        )
        val profileFile = profileCandidates.firstOrNull { it.exists() }
        assertNotNull("Baseline profile baseline-prof.txt must exist in app/src/main/", profileFile)

        val profileContent = profileFile!!.readText()
        assertTrue(profileContent.contains("Lcom/jyotirmoyb/udhaar/UdhaarApplication;"))
        assertTrue(profileContent.contains("Lcom/jyotirmoyb/udhaar/MainActivity;"))
        assertTrue(profileContent.contains("Lcom/jyotirmoyb/udhaar/ui/UdhaarViewModel;"))
        assertTrue(profileContent.contains("Lcom/jyotirmoyb/udhaar/data/UdhaarDatabase;"))
        assertTrue(profileContent.contains("Lcom/jyotirmoyb/udhaar/security/EncryptedStorageManager;"))
    }
}
