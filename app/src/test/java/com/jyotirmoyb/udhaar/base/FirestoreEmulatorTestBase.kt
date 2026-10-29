package com.jyotirmoyb.udhaar.base

import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class FirestoreEmulatorTestBase {

    lateinit var db: FirebaseFirestore
    lateinit var auth: FirebaseAuth

    protected open val projectId = "gen-lang-client-0216629575"
    private val firestoreHost = "127.0.0.1"
    private val firestorePort = 8085
    private val authHost = "127.0.0.1"
    private val authPort = 9099

    @Before
    fun baseSetUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setProjectId(projectId)
                .setApplicationId("1:1234567890:android:abcdef")
                .setApiKey("fake-api-key-for-emulator")
                .build()
            FirebaseApp.initializeApp(context, options)
        }

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        try {
            db.useEmulator(firestoreHost, firestorePort)
            auth.useEmulator(authHost, authPort)

            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            db.firestoreSettings = settings
        } catch (_: IllegalStateException) {
            // Emulators already bound on this singleton instance
        }
    }

    @After
    fun baseTearDown() {
        runCatching { auth.signOut() }
        runCatching { clearFirestoreEmulatorData() }
        runCatching { clearAuthEmulatorUsers() }
        runCatching { db.terminate() }
        runCatching { ShadowLooper.shadowMainLooper().idle() }
        runCatching { FirebaseApp.clearInstancesForTest() }
    }

    /**
     * Runs a coroutine test block on a worker thread while pumping Robolectric's main looper
     * so Firebase Auth & Firestore Task callbacks posted to Android's main thread execute immediately.
     */
    protected fun runEmulatorTest(block: suspend CoroutineScope.() -> Unit) {
        val latch = CountDownLatch(1)
        var thrown: Throwable? = null
        val worker = Thread {
            try {
                runBlocking {
                    block()
                }
            } catch (t: Throwable) {
                thrown = t
            } finally {
                latch.countDown()
            }
        }
        worker.start()
        val mainLooper = ShadowLooper.shadowMainLooper()
        while (!latch.await(15, TimeUnit.MILLISECONDS)) {
            mainLooper.idle()
        }
        mainLooper.idle()
        thrown?.let { throw it }
    }

    protected suspend fun signInAs(email: String, password: String = "password123"): String {
        val result = try {
            auth.createUserWithEmailAndPassword(email, password).await()
        } catch (e: Exception) {
            auth.signInWithEmailAndPassword(email, password).await()
        }
        return result.user!!.uid
    }

    protected fun signOut() {
        auth.signOut()
    }

    protected suspend fun assertPermissionDenied(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected PERMISSION_DENIED, but the operation succeeded.")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(
                "Expected PERMISSION_DENIED but got ${e.code}",
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                e.code
            )
        }
    }

    private fun clearFirestoreEmulatorData() {
        val url = URL(
            "http://$firestoreHost:$firestorePort/emulator/v1/projects/$projectId/databases/(default)/documents"
        )
        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "DELETE"
            connectTimeout = 5000
            readTimeout = 5000
            responseCode
            disconnect()
        }
    }

    private fun clearAuthEmulatorUsers() {
        val url = URL("http://$authHost:$authPort/emulator/v1/projects/$projectId/accounts")
        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "DELETE"
            connectTimeout = 5000
            readTimeout = 5000
            responseCode
            disconnect()
        }
    }
}
