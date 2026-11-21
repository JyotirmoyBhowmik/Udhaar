package com.jyotirmoyb.udhaar.sync

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.jyotirmoyb.udhaar.data.GuestActionV1
import com.jyotirmoyb.udhaar.di.FirebaseModule
import com.jyotirmoyb.udhaar.util.SafeLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Attaches real-time Firestore snapshot listeners while the app is in the foreground.
 * Detached completely in onStop.
 */
object ForegroundGuestActionListener {
    private val activeRegistrations = mutableListOf<ListenerRegistration>()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start(context: Context) {
        stop()

        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull() ?: return
        if (!user.isEmailVerified && !user.isAnonymous) return
        val ownerUid = user.uid
        if (ownerUid.isBlank()) return

        val firestore = FirebaseModule.getFirestore(context) ?: return

        runCatching {
            val linksReg = firestore.collection("guestLinks")
                .whereEqualTo("ownerUid", ownerUid)
                .limit(50)
                .addSnapshotListener { linksSnapshot, err ->
                    if (err != null || linksSnapshot == null) {
                        SafeLogger.w("ForegroundGuestAction", "links listener error: ${err?.message}")
                        return@addSnapshotListener
                    }

                    for (linkDoc in linksSnapshot.documents) {
                        val status = linkDoc.getString("status")
                        if (status != "ACTIVE") continue
                        val linkId = linkDoc.id

                        val actionsReg = linkDoc.reference.collection("actions")
                            .whereEqualTo("status", "PENDING")
                            .addSnapshotListener { actionsSnapshot, actErr ->
                                if (actErr != null || actionsSnapshot == null) return@addSnapshotListener
                                for (doc in actionsSnapshot.documents) {
                                    val action = GuestActionV1.fromDocument(doc)
                                    if (action == null) {
                                        scope.launch {
                                            runCatching {
                                                doc.reference.update(
                                                    mapOf(
                                                        "status" to "REJECTED",
                                                        "rejectReason" to "invalid_payload",
                                                        "reviewedAtMs" to System.currentTimeMillis()
                                                    )
                                                ).await()
                                            }
                                        }
                                        continue
                                    }

                                    scope.launch {
                                        GuestActionPollingWorker.processSingleAction(
                                            context = context,
                                            action = action,
                                            ownerUid = ownerUid
                                        )
                                    }
                                }
                            }
                        synchronized(activeRegistrations) {
                            activeRegistrations.add(actionsReg)
                        }
                    }
                }
            synchronized(activeRegistrations) {
                activeRegistrations.add(linksReg)
            }
        }
    }

    fun stop() {
        synchronized(activeRegistrations) {
            for (reg in activeRegistrations) {
                runCatching { reg.remove() }
            }
            activeRegistrations.clear()
        }
    }
}
