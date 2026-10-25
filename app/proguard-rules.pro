# Add project specific ProGuard rules here.
# Keep source file and line numbers for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Room Database, DAO, TypeConverters, and Entities safe during R8 minification
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.jyotirmoyb.udhaar.data.** { *; }

# Keep JSON Backup & Statement Serialization models safe
-keep class com.jyotirmoyb.udhaar.util.ParsedLedgerBackup { *; }
-keep class com.jyotirmoyb.udhaar.util.LedgerBackupManager { *; }
-keep class com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary { *; }

# Keep ZXing QR Code Generator classes safe
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
