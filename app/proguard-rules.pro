# Add project specific ProGuard rules here.
# Keep source file and line numbers for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep entry point Activities and BroadcastReceivers
-keep class com.example.MainActivity { *; }
-keep class com.jyotirmoyb.udhaar.MainActivity { *; }
-keep class com.jyotirmoyb.udhaar.notification.ReminderReceiver { *; }

# Keep Room Database, generated implementations, DAO, TypeConverters, and Entities safe during R8 minification
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase$Callback { *; }
-keep class * extends androidx.room.migration.Migration { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.jyotirmoyb.udhaar.data.** { *; }
-keepclassmembers class com.jyotirmoyb.udhaar.data.UdhaarTypeConverters { *; }

# Keep Security & Backup / Statement Serialization models safe
-keep class com.jyotirmoyb.udhaar.security.** { *; }
-keep class com.jyotirmoyb.udhaar.util.ParsedLedgerBackup { *; }
-keep class com.jyotirmoyb.udhaar.util.LedgerBackupManager { *; }
-keep class com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary { *; }

# Keep Enums used in TypeConverters and Preferences DataStore
-keepclassmembers enum com.jyotirmoyb.udhaar.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep AndroidX DataStore & Coroutines
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class androidx.datastore.** { *; }

# Keep AndroidX Biometric
-keep class androidx.biometric.** { *; }

# Keep ZXing QR Code Generator classes safe
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Keep AndroidX WorkManager
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Keep OkHttp & Google Auth Identity
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.google.android.gms.auth.api.identity.** { *; }
-keep class com.jyotirmoyb.udhaar.backup.** { *; }
