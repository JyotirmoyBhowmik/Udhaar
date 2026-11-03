# Udhaar v13.0 — System Architecture & Modular Package Structure

## 1. High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                          PRESENTATION LAYER (Jetpack Compose)                       │
│                                                                                     │
│  • MainActivity (Edge-to-Edge Host, Biometric Gate, Deep-Link & SAF Launchers)      │
│  • ui/components/  (DashboardScreen, AddEditUdhaarScreen, BiometricSecurityScreen,  │
│                     VerificationSheets, ContactModals, UxEnhancementComponents)     │
│  • ui/people/      (PeopleDirectoryContent, PartyDetailScreen, AddEditPartySheet,   │
│                     DashboardSummaryComponents)                                     │
│  • ui/sheets/      (TransactionDetailSheet, AttachmentViewerSheet,                  │
│                     ThermalReceiptSheet, WhatsAppReminderSheet, UpiQrCodeSheet,     │
│                     ShareUdhaarAppSheet, ProfileSettingsSheet, RestorePreviewSheet) │
└──────────────────────────────────────────┬──────────────────────────────────────────┘
                                           ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                     VIEWMODEL & DI LAYER (Constructor Injection)                    │
│                                                                                     │
│  • di/UdhaarAppContainer      (Application-scoped DI graph without static globals)  │
│  • ui/UdhaarViewModel         (Unified reactive coordinator for existing screens)   │
│  • ui/viewmodel/                                                                    │
│      ├── LedgerViewModel       (Parties, Loans, Repayments, Audit Trail, Filters)   │
│      ├── VerificationViewModel (App Links, SHA-256 Tokens, Cloud Mutual Confirm)    │
│      ├── ChatViewModel         (Per-Party Chat Threads, Messages, FCM Deep Links)   │
│      ├── BackupViewModel       (JSON Export/Import, Encrypted Google Drive Sync)    │
│      └── SettingsViewModel     (Security PIN, Biometrics, Theme, Currency, UPI)     │
└──────────────────────────────────────────┬──────────────────────────────────────────┘
                                           ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                             DOMAIN REPOSITORY LAYER                                 │
│                                                                                     │
│  • data/UdhaarRepository      (Coordinator facade)                                  │
│  • data/repository/                                                                 │
│      ├── LedgerRepository       (Room CRUD, Hash-Chained Audit Trail, Attachments)  │
│      ├── VerificationRepository (Firestore shared_records & verification tokens)    │
│      ├── ChatRepository         (Firestore & Room chat_threads / chat_messages)     │
│      ├── BackupRepository       (LedgerBackupManager + DriveServiceHelper + Crypto) │
│      └── SettingsRepository     (EncryptedStorageManager + DataStore Preferences)   │
└──────────────────────────────────────────┬──────────────────────────────────────────┘
                                           ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                        PERSISTENCE, CRYPTO & SYSTEM LAYER                           │
│                                                                                     │
│  • UdhaarDatabase (SQLCipher AES-256 Room v7: 7 tables + indices + migrations)      │
│  • EncryptedStorageManager (Keystore MasterKey + EncryptedSharedPreferences)        │
│  • VerificationCloudRepository (Firestore named DB + FCM token lifecycle)           │
│  • SafeLogger & UdhaarResult (Zero-PII structured error handling & telemetry)       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Dependency Injection (`di/UdhaarAppContainer.kt`)

`UdhaarAppContainer` wires dependencies via constructor injection:
1. **Database & Preferences:** `UdhaarDatabase` (`UdhaarDao`), `EncryptedStorageManager`, and `DataStore<Preferences>`.
2. **Cloud Service:** `VerificationCloudRepository` (Firestore + Firebase Auth + Firebase Messaging).
3. **Domain Repositories:**
   - `LedgerRepository`
   - `VerificationRepository`
   - `ChatRepository`
   - `BackupRepository`
   - `SettingsRepository`
4. **Domain ViewModels:**
   - `LedgerViewModel`
   - `VerificationViewModel`
   - `ChatViewModel`
   - `BackupViewModel`
   - `SettingsViewModel`

---

## 3. Typed Error Handling (`UdhaarResult<T>` & `UdhaarAppError`)

All backup, Google Drive sync, cloud verification, and database operations map exceptions into `UdhaarResult<T>` with user-friendly messages and zero-PII logging (`SafeLogger`):
- `UdhaarAppError.NoNetwork` — *"No internet connection. Your local ledger is safe and will sync when back online."*
- `UdhaarAppError.SignedOut` — *"Please sign in with Google to use cloud backup or mutual verification."*
- `UdhaarAppError.QuotaExceeded` — *"Google Drive or cloud storage quota exceeded. Free up space and retry."*
- `UdhaarAppError.WrongPassphrase` — *"Incorrect backup passphrase. Please verify your passphrase and try again."*
- `UdhaarAppError.CorruptedFile` — *"The selected backup file or verification link is corrupted or tampered with."*
- `UdhaarAppError.DatabaseError` — *"Local database operation could not be completed. Please retry."*
