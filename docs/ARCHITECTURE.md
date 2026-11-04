# Udhaar v13.0 — System Architecture & Modular Package Structure

## 1. High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                             PRESENTATION LAYER (Jetpack Compose)                            │
│                                                                                             │
│  • UdhaarApplication (@HiltAndroidApp, WorkManager HiltWorkerFactory initializer)           │
│  • MainActivity (@AndroidEntryPoint, Edge-to-Edge Host, Biometric Gate, Deep-Link Handler) │
│                                                                                             │
│  • Screens & Navigation:                                                                    │
│      ├── ui/components/ (DashboardScreen, AddEditUdhaarScreen, BiometricSecurityScreen)     │
│      └── ui/people/     (PeopleDirectoryContent, PartyDetailScreen, AddEditPartySheet)      │
│                                                                                             │
│  • Modular Sheets & Dialogs (ui/sheets/ — all < 600 lines):                                │
│      ├── RecordDetailSheet.kt & TransactionDetailSheet.kt                                   │
│      ├── detail/ (TransactionDetailHeader, TransactionBalanceCard,                          │
│      │            TransactionVerificationCard, TransactionAttachmentsSection,               │
│      │            PartialPaymentSection, InstallmentHistorySection,                         │
│      │            VerificationAuditTrailSection, TransactionActionsSection)                  │
│      ├── SettingsScreen.kt & ProfileSettingsSheet.kt                                        │
│      ├── settings/ (ProfileUpiSection, SecuritySection, CurrencyThemeSection,               │
│      │              RemindersSettingsSection, BackupSection, DriveBackupCard,               │
│      │              DriveDiagnosticsCard, LocalBackupSection, CloudVerificationCard,        │
│      │              DriveBackupPassphraseDialog, DriveRestorePassphraseDialog, AboutSection)│
│      └── ui/common/CommonUiComponents.kt (Shared buttons, cards, visual transformations)    │
└──────────────────────────────────────────────┬──────────────────────────────────────────────┘
                                               │
                                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                           VIEWMODEL & STATE COORDINATION LAYER                              │
│                                                                                             │
│  • AppStateHolder (Shared UI state: active tabs, snackbars, active dialog/sheet state)      │
│  • Split ViewModels (@HiltViewModel, constructor injected, StateFlow + Lifecycle):         │
│      ├── DashboardViewModel    (Ledger totals, overdue highlights, quick action state)      │
│      ├── RecordViewModel       (Record CRUD, partial payments, settlements, attachments)    │
│      ├── PeopleViewModel       (Party directory, contact linking, per-party transactions)   │
│      ├── VerificationViewModel (SHA-256 tokens, QR codes, cloud verification statuses)      │
│      ├── ChatViewModel         (Direct messaging threads, unread counts, reminder delivery)│
│      ├── BackupViewModel       (Local JSON backup/restore, Google Drive encrypted sync)     │
│      └── SettingsViewModel     (MasterKey PIN, Biometrics, Quiet Hours, Escalation rules)   │
│  • UdhaarViewModel (Backwards-compatible delegating facade coordinating split ViewModels)   │
└──────────────────────────────────────────────┬──────────────────────────────────────────────┘
                                               │
                                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                              DEPENDENCY INJECTION (Hilt & KSP)                              │
│                                                                                             │
│  • di/DatabaseModule: Room SQLCipher UdhaarDatabase, UdhaarDao, DataStore<Preferences>, IO  │
│  • di/RepositoryModule: LedgerRepository, PartyRepository, PaymentRepository,               │
│                        AttachmentRepository, VerificationRepository, ChatRepository,        │
│                        BackupRepository, SettingsRepository, UdhaarRepository facade        │
│  • di/FirebaseModule: Nullable/No-Op resilient Firebase Auth, Firestore, Storage, Messaging │
│  • di/WorkManagerModule: Custom HiltWorkerFactory, @HiltWorker DriveBackupWorker,           │
│                          @HiltWorker DailyReminderWorker                                    │
└──────────────────────────────────────────────┬──────────────────────────────────────────────┘
                                               │
                                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                            DOMAIN REPOSITORIES (all < 500 lines)                            │
│                                                                                             │
│  • LedgerRepository       (UdhaarTransaction CRUD, Hash-chained audit trail, reminders)     │
│  • PartyRepository        (PartyEntity CRUD, search, favorite toggling, balance aggregation)│
│  • PaymentRepository      (PartialPayment recordings, installment calculations, settlement) │
│  • AttachmentRepository   (Receipt/proof attachments, local cache, byte streaming)          │
│  • VerificationRepository (Mutual verification tokens, E2EE payloads, Firestore sync)      │
│  • ChatRepository         (Room + Firestore chat messages, thread delivery status)          │
│  • BackupRepository       (Encrypted JSON export/import, Google Drive REST API sync)        │
│  • SettingsRepository     (DataStore preferences, MasterKey EncryptedSharedPreferences)     │
│  • UdhaarRepository       (Backwards-compatible coordinating facade)                        │
└──────────────────────────────────────────────┬──────────────────────────────────────────────┘
                                               │
                                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                             PERSISTENCE, CRYPTO & SYSTEM LAYER                              │
│                                                                                             │
│  • UdhaarDatabase (SQLCipher AES-256 Room v7: 7 tables + indices + migrations)              │
│  • EncryptedStorageManager & PinSecurityManager (Android Keystore MasterKey AES-256 GCM)    │
│  • VerificationCryptoHelper & DriveBackupCrypto (Argon2id / PBKDF2 + AES-GCM encryption)   │
│  • VerificationCloudRepository (Cloud Firestore named DB + FCM token lifecycle)             │
│  • DriveServiceHelper (Google Drive v3 REST API via OkHttp + GmsDriveTokenProvider)         │
│  • SafeLogger & UdhaarResult (Zero-PII structured logging & typed domain results)           │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Dependency Injection Graph (`com.jyotirmoyb.udhaar.di`)

Udhaar utilizes **Hilt** (powered by KSP) for compile-time dependency injection across all application components:

```
                      ┌──────────────────────┐
                      │  UdhaarApplication   │
                      │  (@HiltAndroidApp)   │
                      └──────────┬───────────┘
                                 │
     ┌───────────────────────────┼───────────────────────────┐
     ▼                           ▼                           ▼
┌──────────────┐         ┌──────────────┐            ┌──────────────┐
│DatabaseModule│         │FirebaseModule│            │  WorkManager │
│              │         │              │            │    Module    │
│ • Database   │         │ • Auth?      │            │              │
│ • UdhaarDao  │         │ • Firestore? │            │ • HiltWorker │
│ • DataStore  │         │ • Storage?   │            │   Factory    │
│ • Dispatchers│         │ • Messaging? │            │ • Workers    │
└──────┬───────┘         └──────┬───────┘            └──────────────┘
       │                        │
       └───────────┬────────────┘
                   ▼
       ┌────────────────────────┐
       │    RepositoryModule    │
       │                        │
       │ • LedgerRepository     │
       │ • PartyRepository      │
       │ • PaymentRepository    │
       │ • AttachmentRepository │
       │ • VerificationRepo     │
       │ • ChatRepository       │
       │ • BackupRepository     │
       │ • SettingsRepository   │
       │ • UdhaarRepository     │
       └───────────┬────────────┘
                   ▼
       ┌────────────────────────┐
       │   Split ViewModels     │
       │   (@HiltViewModel)     │
       │                        │
       │ • DashboardViewModel   │
       │ • RecordViewModel      │
       │ • PeopleViewModel      │
       │ • VerificationVM       │
       │ • ChatViewModel        │
       │ • BackupViewModel      │
       │ • SettingsViewModel    │
       │ • AppStateHolder       │
       └───────────┬────────────┘
                   ▼
       ┌────────────────────────┐
       │      MainActivity      │
       │  (@AndroidEntryPoint)  │
       └────────────────────────┘
```

### Module Responsibilities:
1. **`DatabaseModule`**:
   - Provides singleton `UdhaarDatabase` configured with SQLCipher AES-256 database encryption.
   - Provides `UdhaarDao` as the primary local data access contract.
   - Provides `@IoDispatcher CoroutineDispatcher` (`Dispatchers.IO`) for background database operations.
   - Provides DataStore `Preferences` for application settings.
2. **`FirebaseModule`**:
   - Provides nullable instances of `FirebaseAuth`, `FirebaseFirestore`, `FirebaseStorage`, `FirebaseMessaging`, and `VerificationCloudRepository`.
   - Returns `null` safely when Google Services / Firebase credentials are unconfigured, preventing crashes in offline or unauthenticated builds.
3. **`WorkManagerModule`**:
   - Provides Hilt worker factory delegation for background tasks:
     - `DriveBackupWorker`: Periodic and on-demand encrypted Google Drive backups.
     - `DailyReminderWorker`: Polite escalation reminder scheduler respecting quiet hours.
4. **`RepositoryModule`**:
   - Binds and provides all 8 split domain repositories and the backwards-compatible `UdhaarRepository` facade as `@Singleton` dependencies.

---

## 3. Repository Responsibilities & File Boundaries

To enforce high cohesion, loose coupling, and maintainability, the monolithic repository was split into 8 focused repositories under `com.jyotirmoyb.udhaar.data.repository.*`, with each class strictly under **500 lines**:

| Repository | File Path | Line Count | Key Responsibilities |
|---|---|---|---|
| **`LedgerRepository`** | `data/repository/LedgerRepository.kt` | 329 lines | Transaction CRUD, hash-chained audit trail entries, reminder recording, snooze calculation, settlement status toggles. |
| **`PartyRepository`** | `data/repository/PartyRepository.kt` | 134 lines | Counterparty directory management, contact lookup, phone formatting, favorite tagging, interaction timestamp tracking. |
| **`PaymentRepository`** | `data/repository/PaymentRepository.kt` | 213 lines | Partial payment recording, payment proof attachments, mutual payment acknowledgment, settlement verification. |
| **`AttachmentRepository`** | `data/repository/AttachmentRepository.kt` | 69 lines | Photo proof & receipt attachments, local cache directory cleanup, base64 payload export for encrypted backups. |
| **`VerificationRepository`** | `data/repository/VerificationRepository.kt` | 344 lines | Mutual verification tokens (SHA-256), App Link URI creation, cloud shared_records synchronization, cryptographic content hashes. |
| **`ChatRepository`** | `data/repository/ChatRepository.kt` | 187 lines | In-app peer messaging, chat threads per party, read/delivery statuses, Firestore sync for mutual chat. |
| **`BackupRepository`** | `data/repository/BackupRepository.kt` | 368 lines | Local JSON backup export/import (AES-GCM), Google Drive REST synchronization, automated backup passphrase management. |
| **`SettingsRepository`** | `data/repository/SettingsRepository.kt` | 427 lines | DataStore preference synchronization, Android Keystore MasterKey PIN/biometrics, quiet hours, escalation reminder schedules. |
| **`UdhaarRepository`** | `data/UdhaarRepository.kt` | 244 lines | Backward-compatible delegating facade coordinating all 8 specialized repositories without breaking legacy consumers. |

---

## 4. ViewModel Boundaries & State Management

ViewModels are decomposed into focused `@HiltViewModel` classes under `com.jyotirmoyb.udhaar.ui.viewmodel.*`. Screens collect immutable `StateFlow` streams using `collectAsStateWithLifecycle()`:

1. **`AppStateHolder`** (153 lines):
   - Application-wide UI coordination without polluting business ViewModels.
   - Manages active tab indices, global snackbars/toasts, active sheet/modal dialog states, and search query filters.

2. **`DashboardViewModel`** (215 lines):
   - Computes summary cards (total lent, total borrowed, net position, pending counterparty confirmations).
   - Manages overdue transaction alerts and quick action launchers.

3. **`RecordViewModel`** (313 lines):
   - Manages single transaction creation and editing lifecycles.
   - Handles partial payment logging, receipt attachments, and full settlement actions.

4. **`PeopleViewModel`** (425 lines):
   - Drives the party directory, contact book linking, and per-person transaction histories.
   - Handles party merging, quick communication links (WhatsApp, SMS, Phone).

5. **`VerificationViewModel`** (224 lines):
   - Mutual confirmation state machine (Draft, Pending, Confirmed, Disputed, Edited).
   - Generates and parses App Link deep-links and verification QR codes.

6. **`ChatViewModel`** (330 lines):
   - In-app chat threads between lenders and borrowers.
   - Real-time message streaming, unread badge calculation, and notification deep-linking.

7. **`BackupViewModel`** (520 lines):
   - Local encrypted `.udhaar` backup file generation and import previews.
   - Google Drive connection diagnostics, remote backup listing, and automated backup scheduling.

8. **`SettingsViewModel`** (202 lines):
   - User security preferences (PIN verification, Biometric prompt gates).
   - Multi-currency selections (INR/NPR toggles), theme modes, quiet hours, and reminder escalation preferences.

9. **`UdhaarViewModel`** (621 lines):
   - Backwards-compatible delegating facade coordinating the split ViewModels and preserving complete compatibility with existing screen composables.

---

## 5. Modular UI Decomposition (`ui/sheets/` & `ui/common/`)

The monolithic `ModalsAndSheets.kt` (~4,554 lines) was broken down into modular, single-responsibility composables under `ui/sheets/` and `ui/common/`, with each file strictly under **600 lines**:

### Detail Sheets (`ui/sheets/detail/`):
- `TransactionDetailHeader.kt` (84 lines) — Visual header, counterparty badge, status indicators.
- `TransactionBalanceCard.kt` (92 lines) — Principal, accrued interest, amount paid, and net balance due.
- `TransactionVerificationCard.kt` (212 lines) — Mutual confirmation status, SHA-256 tokens, dispute actions.
- `TransactionAttachmentsSection.kt` (295 lines) — Media gallery, photo attachment previews, document viewers.
- `PartialPaymentSection.kt` (395 lines) — Partial payment history, manual repayment input, payment mode selection.
- `InstallmentHistorySection.kt` (214 lines) — Scheduled installment timelines and completion milestones.
- `VerificationAuditTrailSection.kt` (157 lines) — Cryptographically chained audit event log.
- `TransactionActionsSection.kt` (257 lines) — Settle, snooze, reminder dispatch, edit, and delete action triggers.

### Settings Sections (`ui/sheets/settings/`):
- `ProfileUpiSection.kt` (225 lines) — Profile photo, payee name, and UPI VPA configuration.
- `SecuritySection.kt` (307 lines) — MasterKey PIN setup, biometric unlock toggle, auto-lock timeouts.
- `CurrencyThemeSection.kt` (353 lines) — Currency preferences (INR, NPR), theme selection, font scaling.
- `RemindersSettingsSection.kt` (159 lines) — Escalation levels, quiet hours (9 PM - 8 AM default), per-person overrides.
- `DriveBackupCard.kt` (600 lines) — Google Drive connection status, auto-backup cadence, remote backup list.
- `DriveDiagnosticsCard.kt` (433 lines) — End-to-end Google Drive API diagnostics and actionable error resolution.
- `LocalBackupSection.kt` (130 lines) — Encrypted local file export and SAF file picker restore.
- `CloudVerificationCard.kt` (282 lines) — Cloud account identity, mutual verification consent, and sign-out.
- `DriveBackupPassphraseDialog.kt` (181 lines) & `DriveRestorePassphraseDialog.kt` (109 lines) — Passphrase input dialogs.
- `AboutSection.kt` (207 lines) — Version information, open-source licenses, and privacy assurances.

### Shared & Facade Components:
- `ui/common/CommonUiComponents.kt` (312 lines) — Reusable UI widgets: `SettingsSectionCard`, `BreakdownLine`, `SigningCredentialRow`, visual transformations.
- `ui/components/ModalsAndSheets.kt` (227 lines) — Lightweight backward-compatible facade routing calls to the modular sheet implementations.

---

## 6. Typed Error Handling (`UdhaarResult<T>` & `UdhaarAppError`)

All asynchronous operations (Drive sync, backup export/import, cloud verification, and database mutations) return `UdhaarResult<T>` with zero-PII structured logging (`SafeLogger`):
- `UdhaarAppError.NoNetwork` — *"No internet connection. Your local ledger is safe and will sync when back online."*
- `UdhaarAppError.SignedOut` — *"Please sign in with Google to use cloud backup or mutual verification."*
- `UdhaarAppError.QuotaExceeded` — *"Google Drive or cloud storage quota exceeded. Free up space and retry."*
- `UdhaarAppError.WrongPassphrase` — *"Incorrect backup passphrase. Please verify your passphrase and try again."*
- `UdhaarAppError.CorruptedFile` — *"The selected backup file or verification link is corrupted or tampered with."*
- `UdhaarAppError.DatabaseError` — *"Local database operation could not be completed. Please retry."*

---

## 7. Testing Strategy

1. **Pure Unit Tests (Repository Layer)**:
   - Evaluates business logic using `FakeUdhaarDao` without Android framework dependencies or database overhead.
   - Tested workflows: Transaction CRUD, hash generation, partial payment calculations, settlement triggers, snapshot exports, and JSON backup imports.
2. **Robolectric Unit Tests**:
   - Verifies encryption helpers (`DriveBackupCrypto`, `VerificationCryptoHelper`), Compose view models, and Android Keystore flows.
3. **Firestore Security Rules Tests**:
   - Validates that participants can only read and write shared records they are explicitly party to, preventing unauthorized access or data leakage.
