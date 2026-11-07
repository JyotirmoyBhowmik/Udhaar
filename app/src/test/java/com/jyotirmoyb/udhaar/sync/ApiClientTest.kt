package com.jyotirmoyb.udhaar.sync

import com.jyotirmoyb.udhaar.security.AppCheckProvider
import com.jyotirmoyb.udhaar.security.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var fakeTokenStore: FakeTokenStore
    private lateinit var fakeAppCheckProvider: FakeAppCheckProvider
    private lateinit var client: ApiClient

    class FakeTokenStore : TokenStore {
        private var token: String? = null
        private var userId: String? = null
        private var phone: String? = null
        private var email: String? = null
        private var deviceId: String = "dev_test_device_123"
        private var syncEnabled: Boolean = false
        private var lastSync: Long = 0L

        override fun getAuthToken(): String? = token
        override fun getUserId(): String? = userId
        override fun getPhoneNumber(): String? = phone
        override fun getEmail(): String? = email
        override fun getDeviceId(): String = deviceId
        override fun isCloudSyncEnabled(): Boolean = syncEnabled
        override fun setCloudSyncEnabled(enabled: Boolean) { syncEnabled = enabled }
        override fun saveSession(authToken: String, userId: String, phoneNumber: String?, email: String?) {
            this.token = authToken
            this.userId = userId
            this.phone = phoneNumber
            this.email = email
        }
        override fun updateEmail(email: String) { this.email = email }
        override fun clearSession() {
            token = null
            userId = null
            phone = null
            email = null
            syncEnabled = false
            lastSync = 0L
        }
        override fun isSignedIn(): Boolean = !token.isNullOrBlank() && !userId.isNullOrBlank()
        override fun getLastSyncTimestamp(): Long = lastSync
        override fun setLastSyncTimestamp(timestamp: Long) { lastSync = timestamp }
    }

    class FakeAppCheckProvider : AppCheckProvider {
        var token: String? = "test_play_integrity_token_456"
        override suspend fun getToken(): String? = token
        override fun getTokenSync(): String? = token
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        fakeTokenStore = FakeTokenStore()
        fakeAppCheckProvider = FakeAppCheckProvider()
        client = ApiClient(
            baseUrl = server.url("/").toString(),
            tokenStore = fakeTokenStore,
            appCheckProvider = fakeAppCheckProvider,
            allowCleartextForTests = true
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `headers include deviceId, contentType, and AppCheck token`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"flowId":"flow_123","resendAfterSeconds":30,"destinationMasked":"+91******3210","channel":"WHATSAPP"}""")
        )

        val resp = client.startSignup("+919876543210")
        assertEquals("flow_123", resp.flowId)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/auth/signup/start", recorded.path)
        assertEquals("dev_test_device_123", recorded.getHeader("X-Device-Id"))
        assertEquals("application/json; charset=utf-8", recorded.getHeader("Content-Type"))
        assertEquals("test_play_integrity_token_456", recorded.getHeader("X-Firebase-AppCheck"))
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `authenticated requests attach bearer token from tokenStore`() = runBlocking {
        fakeTokenStore.saveSession(
            authToken = "custom_jwt_session_token_xyz",
            userId = "usr_alice",
            phoneNumber = "+919876543210",
            email = "alice@example.com"
        )

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"devices":[{"deviceId":"dev_1","userAgent":"Android Udhaar","ipAddress":"10.0.0.1","lastActiveAt":1700000000000,"current":true}]}""")
        )

        val resp = client.getDevices()
        assertEquals(1, resp.devices.size)
        assertEquals("dev_1", resp.devices[0].deviceId)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/v1/auth/devices", recorded.path)
        assertEquals("Bearer custom_jwt_session_token_xyz", recorded.getHeader("Authorization"))
    }

    @Test
    fun `verifySignupEmail saves session in TokenStore`() = runBlocking {
        assertFalse(fakeTokenStore.isSignedIn())

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token":"jwt_auth_token_999","customToken":"custom_token_999","uid":"usr_new_user","user":{"uid":"usr_new_user","phoneMasked":"+91******3210","emailMasked":"n**@example.com"}}""")
        )

        val session = client.verifySignupEmail("flow_abc", "123456")
        assertEquals("usr_new_user", session.uid)
        assertEquals("jwt_auth_token_999", session.token)

        assertTrue(fakeTokenStore.isSignedIn())
        assertEquals("jwt_auth_token_999", fakeTokenStore.getAuthToken())
        assertEquals("usr_new_user", fakeTokenStore.getUserId())
        assertEquals("+91******3210", fakeTokenStore.getPhoneNumber())
        assertEquals("n**@example.com", fakeTokenStore.getEmail())
    }

    @Test
    fun `verifySignin saves session and signOut clears session in TokenStore`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token":"signin_jwt_888","customToken":"custom_signin_888","uid":"usr_bob","user":{"uid":"usr_bob","phoneMasked":"+91******1111","emailMasked":"b**@example.com"}}""")
        )

        val signinResp = client.verifySignin("flow_bob", "654321")
        assertEquals("usr_bob", signinResp.uid)
        assertTrue(fakeTokenStore.isSignedIn())
        assertEquals("signin_jwt_888", fakeTokenStore.getAuthToken())

        // Sign out
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true}""")
        )

        val signoutOk = client.signOut()
        assertTrue(signoutOk)
        assertFalse(fakeTokenStore.isSignedIn())
        assertNull(fakeTokenStore.getAuthToken())
        assertNull(fakeTokenStore.getUserId())
    }

    @Test
    fun `pushSync and pullSync serialize and parse correctly`() = runBlocking {
        fakeTokenStore.saveSession("jwt_sync_token", "usr_sync_user", null, null)

        // Push response
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"results":[{"syncId":"rec_sync_1","status":"ACCEPTED","serverRev":1}]}""")
        )

        val pushResp = client.pushSync(
            SyncPushRequest(
                deviceId = "dev_test_device_123",
                changes = listOf(
                    SyncMutationDto(
                        entity = "record",
                        syncId = "rec_sync_1",
                        op = "CREATE",
                        fields = mapOf("partyName" to "Ramesh", "principalAmountPaise" to 50000L),
                        clientRev = 1L,
                        hlc = "1700000000000:0:dev_test_device_123",
                        idempotencyKey = UUID.randomUUID().toString()
                    )
                )
            )
        )

        assertEquals(1, pushResp.results.size)
        assertEquals("rec_sync_1", pushResp.results[0].syncId)
        assertEquals("ACCEPTED", pushResp.results[0].status)

        // Pull response
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"changes":[{"seq":1,"entity":"record","syncId":"rec_remote_2","op":"CREATE","fields":{"partyName":"Suresh","principalAmountPaise":80000},"serverRev":1,"hlc":"1700000001000:0:server","deviceId":"server","timestampMs":1700000001000}],"nextCursor":1,"hasMore":false}""")
        )

        val pullResp = client.pullSync(cursor = 0L, limit = 50)
        assertEquals(1, pullResp.changes.size)
        assertEquals("rec_remote_2", pullResp.changes[0].syncId)
        assertEquals(1L, pullResp.nextCursor)
        assertFalse(pullResp.hasMore)
        assertEquals("Suresh", pullResp.changes[0].fields["partyName"])
    }
}
