package com.jyotirmoyb.udhaar.ui.sheets.settings

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.cloud.GuestWebCloudRepository
import com.jyotirmoyb.udhaar.sync.GuestActionPollingWorker
import com.jyotirmoyb.udhaar.sync.SyncWorker
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class SparkAuthSession(
    val uid: String,
    val email: String,
    val displayName: String = "",
    val phoneNumber: String = "",
    val isEmailVerified: Boolean = false,
    val providerId: String = "password",
    val idToken: String = ""
)

interface SparkAuthController {
    fun currentSession(): SparkAuthSession?
    suspend fun signInWithGoogle(context: Context): SparkAuthSession
    suspend fun signUpWithEmailPassword(email: String, password: String, contactPhone: String = ""): SparkAuthSession
    suspend fun signInWithEmailPassword(email: String, password: String): SparkAuthSession
    suspend fun sendEmailVerification()
    suspend fun reloadSession(): SparkAuthSession?
    suspend fun sendPasswordResetEmail(email: String)
    suspend fun reauthenticateWithPassword(email: String, password: String)
    suspend fun reauthenticateWithGoogle(context: Context)
    suspend fun deleteCloudData(uid: String, knownLinkIds: List<String> = emptyList())
    suspend fun deleteAccountAndAllData(
        context: Context,
        uid: String,
        knownLinkIds: List<String> = emptyList(),
        deleteDriveBackups: Boolean = false,
        onProgress: (String) -> Unit = {}
    ) {
        deleteCloudData(uid, knownLinkIds)
    }
    suspend fun signOut()
}

internal fun extractKnownGuestLinkIds(transactions: List<UdhaarTransaction>): List<String> =
    transactions.mapNotNull { tx ->
        val token = tx.shareToken.trim()
        val isHex = token.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        when {
            token.length == 64 && isHex -> token.lowercase(Locale.ROOT)
            token.length == 32 && isHex -> GuestWebCloudRepository.sha256Hex(token.lowercase(Locale.ROOT))
            else -> null
        }
    }

class FirebaseSparkAuthController(
    private val authProvider: () -> FirebaseAuth? = {
        runCatching { FirebaseAuth.getInstance() }.getOrNull()
    },
    private val firestoreProvider: () -> FirebaseFirestore? = {
        com.jyotirmoyb.udhaar.di.FirebaseModule.getFirestore()
    }
) : SparkAuthController {

    private fun mapUser(
        user: FirebaseUser,
        tokenOverride: String = "",
        phoneOverride: String = ""
    ): SparkAuthSession {
        val provider = user.providerData.firstOrNull { it.providerId != "firebase" }?.providerId ?: "password"
        val verified = user.isEmailVerified || provider == "google.com"
        return SparkAuthSession(
            uid = user.uid,
            email = user.email.orEmpty(),
            displayName = user.displayName.orEmpty(),
            phoneNumber = phoneOverride.ifBlank { user.phoneNumber.orEmpty() },
            isEmailVerified = verified,
            providerId = provider,
            idToken = tokenOverride.ifBlank { "firebase_session_${user.uid}" }
        )
    }

    override fun currentSession(): SparkAuthSession? {
        val user = authProvider()?.currentUser ?: return null
        if (user.isAnonymous) return null
        return mapUser(user)
    }

    override suspend fun signInWithGoogle(context: Context): SparkAuthSession {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetSignInWithGoogleOption.Builder(
            context.getString(R.string.default_web_client_id)
        ).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = credentialManager.getCredential(request = request, context = context)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val isFirebaseConfigured = runCatching {
                FirebaseApp.getApps(context).isNotEmpty()
            }.getOrDefault(false)
            if (!isFirebaseConfigured) {
                throw IllegalStateException("Firebase is not configured on this device.")
            }
            val auth = authProvider() ?: throw IllegalStateException("FirebaseAuth unavailable.")
            val firebaseCred = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCred).await()
            val user = authResult.user ?: throw IllegalStateException("Google Sign-In returned no user.")
            val idToken = runCatching { user.getIdToken(false).await().token.orEmpty() }.getOrDefault("")
            return mapUser(user, tokenOverride = idToken)
        }
        throw IllegalStateException("Unsupported credential type returned from Credential Manager.")
    }

    override suspend fun signUpWithEmailPassword(
        email: String,
        password: String,
        contactPhone: String
    ): SparkAuthSession {
        val auth = authProvider() ?: throw IllegalStateException("FirebaseAuth unavailable.")
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
        val user = result.user ?: throw IllegalStateException("Account creation failed.")
        user.sendEmailVerification().await()
        val idToken = runCatching { user.getIdToken(false).await().token.orEmpty() }.getOrDefault("")
        return mapUser(user, tokenOverride = idToken, phoneOverride = contactPhone.trim())
    }

    override suspend fun signInWithEmailPassword(email: String, password: String): SparkAuthSession {
        val auth = authProvider() ?: throw IllegalStateException("FirebaseAuth unavailable.")
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val result = auth.signInWithEmailAndPassword(cleanEmail, password).await()
        val user = result.user ?: throw IllegalStateException("Sign-in failed.")
        val idToken = runCatching { user.getIdToken(false).await().token.orEmpty() }.getOrDefault("")
        return mapUser(user, tokenOverride = idToken)
    }

    override suspend fun sendEmailVerification() {
        val user = authProvider()?.currentUser ?: throw IllegalStateException("Not signed in.")
        user.sendEmailVerification().await()
    }

    override suspend fun reloadSession(): SparkAuthSession? {
        val user = authProvider()?.currentUser ?: return null
        user.reload().await()
        val refreshed = authProvider()?.currentUser ?: return null
        val idToken = runCatching { refreshed.getIdToken(true).await().token.orEmpty() }.getOrDefault("")
        return mapUser(refreshed, tokenOverride = idToken)
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        val auth = authProvider() ?: throw IllegalStateException("FirebaseAuth unavailable.")
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        auth.sendPasswordResetEmail(cleanEmail).await()
    }

    override suspend fun reauthenticateWithPassword(email: String, password: String) {
        val user = authProvider()?.currentUser ?: throw IllegalStateException("Not signed in.")
        val cred = EmailAuthProvider.getCredential(email.trim(), password)
        user.reauthenticate(cred).await()
    }

    override suspend fun reauthenticateWithGoogle(context: Context) {
        val user = authProvider()?.currentUser ?: throw IllegalStateException("Not signed in.")
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetSignInWithGoogleOption.Builder(
            context.getString(R.string.default_web_client_id)
        ).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = credentialManager.getCredential(request = request, context = context)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            user.reauthenticate(firebaseCred).await()
        } else {
            throw IllegalStateException("Google re-authentication failed.")
        }
    }

    private suspend fun deleteUserCollections(db: FirebaseFirestore, uid: String) {
        val userDoc = db.collection("users").document(uid)
        val subcollections = listOf("records", "people", "plans", "attachments")
        for (sub in subcollections) {
            runCatching {
                val snap = userDoc.collection(sub).get().await()
                for (doc in snap.documents) {
                    runCatching { doc.reference.delete().await() }
                }
            }
        }
        runCatching { userDoc.collection("meta").document("syncSignal").delete().await() }
        runCatching { userDoc.delete().await() }
    }

    private suspend fun deleteGuestLinkAndSubcollections(db: FirebaseFirestore, linkId: String) {
        val linkRef = db.collection("guestLinks").document(linkId)
        val actionsSnap = runCatching { linkRef.collection("actions").get().await() }.getOrNull()
        if (actionsSnap != null) {
            for (actionDoc in actionsSnap.documents) {
                runCatching { actionDoc.reference.collection("proof").document("image").delete().await() }
                runCatching { actionDoc.reference.delete().await() }
            }
        }
        runCatching { linkRef.delete().await() }
    }

    override suspend fun deleteCloudData(uid: String, knownLinkIds: List<String>) {
        if (uid.isBlank()) return
        val db = firestoreProvider() ?: return
        deleteUserCollections(db, uid)
        for (linkId in knownLinkIds.distinct()) {
            if (linkId.length >= 12) {
                runCatching { db.collection("guestLinks").document(linkId).delete().await() }
            }
        }
    }

    override suspend fun deleteAccountAndAllData(
        context: Context,
        uid: String,
        knownLinkIds: List<String>,
        deleteDriveBackups: Boolean,
        onProgress: (String) -> Unit
    ) {
        val db = firestoreProvider()
        val auth = authProvider()
        val user = auth?.currentUser

        if (deleteDriveBackups) {
            onProgress(context.getString(R.string.delete_account_progress_drive))
            runCatching {
                val driveHelper = com.jyotirmoyb.udhaar.backup.DriveServiceHelper(context)
                driveHelper.deleteAllBackups()
            }
        }

        if (uid.isNotBlank() && db != null) {
            onProgress(context.getString(R.string.delete_account_progress_firestore))
            deleteUserCollections(db, uid)

            onProgress(context.getString(R.string.delete_account_progress_guest_links))
            runCatching {
                val linksSnap = db.collection("guestLinks")
                    .whereEqualTo("ownerUid", uid)
                    .limit(50)
                    .get()
                    .await()
                for (linkDoc in linksSnap.documents) {
                    deleteGuestLinkAndSubcollections(db, linkDoc.id)
                }
            }
            for (linkId in knownLinkIds.distinct()) {
                if (linkId.length >= 12) {
                    deleteGuestLinkAndSubcollections(db, linkId)
                }
            }
        }

        onProgress(context.getString(R.string.delete_account_progress_cleanup))
        SyncWorker.cancelSync(context)
        GuestActionPollingWorker.cancel(context)
        com.jyotirmoyb.udhaar.sync.CleanExpiredGuestLinksWorker.cancel(context)
        com.jyotirmoyb.udhaar.backup.DriveBackupScheduler.cancel(context)

        if (user != null) {
            onProgress(context.getString(R.string.delete_account_progress_auth))
            user.delete().await()
        }

        onProgress(context.getString(R.string.delete_account_progress_local))
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.resetInstance()
        com.jyotirmoyb.udhaar.security.EncryptedStorageManager.resetAppDataSecurely(context)
    }

    override suspend fun signOut() {
        runCatching { authProvider()?.signOut() }
    }
}
