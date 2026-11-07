# =============================================================================
# Udhaar Release R8 / ProGuard Configuration (app/proguard-rules.pro)
# Principle: Allow R8 to shrink, optimize, and obfuscate all application and
# library classes except those strictly accessed via JNI, Room runtime cursor
# mapping, Hilt/WorkManager constructor reflection, or Enum.valueOf(String).
# =============================================================================

# -----------------------------------------------------------------------------
# 1. Crash Diagnostics: Keep line numbers & source file attribute for ReTrace
# -----------------------------------------------------------------------------
-dontoptimize
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# -----------------------------------------------------------------------------
# 2. AndroidManifest Entry Points (Activities, Receivers, Services, Providers)
# Only keep the class names and Android lifecycle/constructor entry points so
# private helper methods and lambdas inside these classes are still optimized.
# -----------------------------------------------------------------------------
-keep class com.jyotirmoyb.udhaar.UdhaarApplication { public <init>(); }
-keep class com.jyotirmoyb.udhaar.MainActivity { public <init>(); }
-keep class com.jyotirmoyb.udhaar.notification.ReminderReceiver { public <init>(); }
-keep class com.jyotirmoyb.udhaar.notification.BootRescheduleReceiver { public <init>(); }
-keep class com.jyotirmoyb.udhaar.notification.UdhaarFirebaseMessagingService { public <init>(); }
-keep class com.jyotirmoyb.udhaar.widget.UdhaarWidgetProvider { public <init>(); }

# -----------------------------------------------------------------------------
# 3. Hilt & WorkManager Assisted-Injected Workers
# Only keep the Worker constructors invoked by HiltWorkerFactory / WorkerFactory.
# -----------------------------------------------------------------------------
-keep class com.jyotirmoyb.udhaar.backup.DriveBackupWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters, ...);
}
-keep class com.jyotirmoyb.udhaar.notification.ReminderWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters, ...);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# -----------------------------------------------------------------------------
# 4. Room Database, Entities, DAOs & TypeConverters (Narrow Rules Only)
# Note: Repositories (UdhaarRepository, VerificationCloudRepository,
# GuestWebCloudRepository), calculators, and managers use explicit Kotlin code
# and org.json.JSONObject — they are NOT kept here so R8 can shrink & obfuscate
# them.
# -----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase$Callback { *; }
-keep class * extends androidx.room.migration.Migration { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class com.jyotirmoyb.udhaar.data.UdhaarTypeConverters { *; }

# -----------------------------------------------------------------------------
# 5. Enum values() / valueOf(String) used by Room TypeConverters & JSON Backup
# -----------------------------------------------------------------------------
-keepclassmembers enum com.jyotirmoyb.udhaar.data.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum com.jyotirmoyb.udhaar.ui.theme.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# -----------------------------------------------------------------------------
# 6. Kotlin Coroutines ServiceLoader Entry Points
# -----------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# -----------------------------------------------------------------------------
# 7. OkHttp / Okio & ZXing Non-Android Platform Warnings
# (No -keep on com.google.zxing.** so unused 1D/Aztec/PDF417 decoders are stripped
# and only QRCodeWriter / BitMatrix used by UpiQrGenerator are retained.)
# -----------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.google.zxing.**

# -----------------------------------------------------------------------------
# 8. Remaining Broad Rules (With Explicit Technical Justification)
# -----------------------------------------------------------------------------

# BROAD RULE EXPLANATION 1 — SQLCipher Native JNI Bridge:
# net.zetetic:sqlcipher-android's native C/C++ library (libsqlcipher.so) looks
# up Java classes, fields (e.g., mNativeHandle), and callback methods in
# net.zetetic.database.sqlcipher.* and net.zetetic.database.* by hardcoded
# string names via JNI FindClass / GetFieldID / GetMethodID at runtime.
# Renaming or stripping any member in these two JNI packages causes immediate
# UnsatisfiedLinkError or NoSuchFieldError when opening the encrypted Room DB.
-keep class net.zetetic.database.** { *; }
-keep interface net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep interface net.zetetic.database.sqlcipher.** { *; }

# BROAD RULE EXPLANATION 2 — Google Tink Reflective Key Managers (AndroidX Security Crypto):
# androidx.security.crypto.MasterKey and EncryptedSharedPreferences initialize
# Tink AEAD / DeterministicAead primitives (AesGcmKeyManager, AesSivKeyManager)
# and shaded Protobuf message builders via Class.forName(...) reflection.
# Stripping or renaming Tink key manager or protobuf classes causes
# GeneralSecurityException / InvalidProtocolBufferException on startup.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
