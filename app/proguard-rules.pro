# =============================================================================
# Udhaar Release R8 / ProGuard Configuration (app/proguard-rules.pro)
# Principle: Allow R8 full-mode optimization, dead-code elimination, and
# obfuscation while strictly preserving runtime reflection boundaries:
# Room entities/DAOs, Firebase Firestore toObject serialization, kotlinx.serialization,
# SQLCipher JNI, WorkManager workers, and Hilt dependency injection.
# =============================================================================

# -----------------------------------------------------------------------------
# 1. Crash Diagnostics & Fast Cloud Bundling
# Keep line numbers & source file attribute for ReTrace. Skip heavy multi-pass
# bytecode optimization so cloud bundleRelease finishes well within timeout
# while preserving full class/method shrinking, dead-code removal & obfuscation.
# -----------------------------------------------------------------------------
-dontoptimize
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# -----------------------------------------------------------------------------
# 2. AndroidManifest Entry Points (Activities, Receivers, Services, Providers)
# -----------------------------------------------------------------------------
-keep class com.jyotirmoyb.udhaar.UdhaarApplication { public <init>(); }
-keep class com.jyotirmoyb.udhaar.MainActivity { public <init>(); }
-keep class com.jyotirmoyb.udhaar.notification.ReminderReceiver { public <init>(); }
-keep class com.jyotirmoyb.udhaar.notification.BootRescheduleReceiver { public <init>(); }
-keep class com.jyotirmoyb.udhaar.notification.UdhaarFirebaseMessagingService { public <init>(); }
-keep class com.jyotirmoyb.udhaar.widget.UdhaarWidgetProvider { public <init>(); }

# -----------------------------------------------------------------------------
# 3. Hilt & WorkManager Assisted-Injected Workers
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
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.hilt.work.WorkerFactory { *; }
-keep @androidx.hilt.work.HiltWorker class * {
    public <init>(android.content.Context, androidx.work.WorkerParameters, ...);
}

# Hilt Components and ViewModels
-keep class * extends android.app.Application { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.app.Activity { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class dagger.hilt.** { *; }
-keep interface dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep interface javax.inject.** { *; }

# -----------------------------------------------------------------------------
# 4. Room Database, Entities, DAOs & TypeConverters
# -----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase$Callback { *; }
-keep class * extends androidx.room.migration.Migration { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}
-keepclassmembers class com.jyotirmoyb.udhaar.data.UdhaarTypeConverters { *; }

# -----------------------------------------------------------------------------
# 5. Firebase Models (Firestore toObject JavaBean Property Mapping)
# -----------------------------------------------------------------------------
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.ServerTimestamp <fields>;
    @com.google.firebase.firestore.ServerTimestamp <methods>;
    @com.google.firebase.firestore.DocumentId <fields>;
    @com.google.firebase.firestore.DocumentId <methods>;
    @com.google.firebase.firestore.Exclude <fields>;
    @com.google.firebase.firestore.Exclude <methods>;
}
-keep @com.google.firebase.firestore.IgnoreExtraProperties class * { *; }
-keep class com.jyotirmoyb.udhaar.data.cloud.** {
    public <init>(...);
    public <fields>;
    public <methods>;
}
-keep class com.jyotirmoyb.udhaar.data.GuestActionV1** {
    public <init>(...);
    public <fields>;
    public <methods>;
}
-keep class com.jyotirmoyb.udhaar.data.GuestPaymentClaimData { *; }
-keep class com.jyotirmoyb.udhaar.data.GuestDateProposalData { *; }

# -----------------------------------------------------------------------------
# 6. kotlinx.serialization & Gson Models
# -----------------------------------------------------------------------------
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keep class *$$serializer { *; }
-keepclassmembers class *$$serializer {
    public static final *$$serializer INSTANCE;
}
-keep class com.jyotirmoyb.udhaar.sync.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }

# -----------------------------------------------------------------------------
# 7. Enum values() / valueOf(String) used by Room TypeConverters & JSON Backup
# -----------------------------------------------------------------------------
-keepclassmembers enum ** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# -----------------------------------------------------------------------------
# 8. Kotlin Coroutines ServiceLoader Entry Points
# -----------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# -----------------------------------------------------------------------------
# 9. OkHttp / Okio & ZXing Non-Android Platform Warnings
# -----------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.google.zxing.**

# -----------------------------------------------------------------------------
# 10. SQLCipher Native JNI Bridge
# -----------------------------------------------------------------------------
-keep class net.zetetic.database.** { *; }
-keep interface net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep interface net.zetetic.database.sqlcipher.** { *; }

# -----------------------------------------------------------------------------
# 11. Google Tink Reflective Key Managers (AndroidX Security Crypto)
# -----------------------------------------------------------------------------
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
