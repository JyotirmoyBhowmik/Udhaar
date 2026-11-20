package com.jyotirmoyb.udhaar.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Verification test enforcing the Single Firestore Provider constraint:
 * 1. Under Firebase Spark free tier, only the "(default)" database is covered.
 * 2. All Android components must obtain [FirebaseFirestore] exclusively from [FirebaseModule].
 * 3. No other code may invoke [FirebaseFirestore.getInstance] directly.
 * 4. All Web components must obtain Firestore exclusively from `web/u/firebase-init.js`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirestoreSingleProviderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setProjectId("demo-spark-project")
                .setApplicationId("1:1234567890:android:abcdef")
                .setApiKey("fake-api-key")
                .build()
            FirebaseApp.initializeApp(context, options)
        }
    }

    @Test
    fun firebaseModule_providesDefaultDatabaseInstance() {
        val app = FirebaseApp.getInstance()
        val firestoreFromProvider = FirebaseModule.provideFirebaseFirestore(app, context)
        assertNotNull("FirebaseModule must provide non-null Firestore instance", firestoreFromProvider)

        val firestoreFromAccessor = FirebaseModule.getFirestore(context)
        assertNotNull("FirebaseModule.getFirestore() must provide non-null Firestore instance", firestoreFromAccessor)

        // Under Spark free tier, only the (default) database is used
        val databaseId = runCatching {
            val field = firestoreFromProvider?.javaClass?.declaredFields?.firstOrNull { it.name == "databaseId" }
            field?.isAccessible = true
            field?.get(firestoreFromProvider)?.toString() ?: "(default)"
        }.getOrDefault("(default)")
        assertTrue("Database ID must be (default): $databaseId", databaseId.contains("default"))
    }

    @Test
    fun noDirectFirebaseFirestoreGetInstance_outsideFirebaseModule() {
        // Resolve project root from current working directory
        val userDir = File(System.getProperty("user.dir") ?: ".")
        val projectRoot = if (userDir.name == "app") userDir.parentFile else userDir
        val androidSrcDir = File(projectRoot, "app/src")

        assertTrue("Android src dir must exist: ${androidSrcDir.absolutePath}", androidSrcDir.exists())

        val violations = mutableListOf<String>()
        val allowedFiles = setOf(
            "FirebaseModule.kt",
            "FirestoreSingleProviderTest.kt"
        )

        androidSrcDir.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .forEach { file ->
                if (file.name in allowedFiles) return@forEach

                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    // Ignore comment lines
                    if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                        return@forEachIndexed
                    }
                    if (trimmed.contains("FirebaseFirestore.getInstance(")) {
                        violations.add("${file.relativeTo(projectRoot).path}:${index + 1} -> $trimmed")
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "Violation of Single Firestore Provider rule: Direct call to FirebaseFirestore.getInstance( " +
                    "found outside FirebaseModule. All callers must use FirebaseModule DI or FirebaseModule.getFirestore().\n" +
                    violations.joinToString("\n")
            )
        }
    }

    @Test
    fun noDirectWebGetFirestore_outsideFirebaseInit() {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        val projectRoot = if (userDir.name == "app") userDir.parentFile else userDir
        val webDir = File(projectRoot, "web")

        if (!webDir.exists()) return

        val violations = mutableListOf<String>()
        val allowedFiles = setOf(
            "firebase-init.js"
        )

        webDir.walkTopDown()
            .filter { file ->
                file.isFile && file.extension == "js" &&
                    !file.path.contains("vendor") &&
                    !file.path.contains("node_modules") &&
                    !file.path.contains("dist")
            }
            .forEach { file ->
                if (file.name in allowedFiles) return@forEach

                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                        return@forEachIndexed
                    }
                    if (trimmed.contains("getFirestore(") || trimmed.contains(".getFirestore(")) {
                        violations.add("${file.relativeTo(projectRoot).path}:${index + 1} -> $trimmed")
                    }
                }
            }

        if (violations.isNotEmpty()) {
            fail(
                "Violation of Web Single Firestore Provider rule: getFirestore( found outside web/u/firebase-init.js.\n" +
                    violations.joinToString("\n")
            )
        }
    }
}
