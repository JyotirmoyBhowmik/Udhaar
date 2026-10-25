# Udhaar — Minimalist Monochrome Loan & Ledger Android Application

**Udhaar** is a distraction-free, strictly monochrome (`#000000` / `#FFFFFF`) offline-first Android application built with Kotlin, Jetpack Compose, and Room SQLite for tracking personal and party-based loans (**Given / Lent** and **Taken / Borrowed**) across **Indian Rupees (`INR ₹`)** and **Nepalese Rupees (`NPR रू`)**.

---

## 1. Overview & Monochrome Design Philosophy

Udhaar follows a utilitarian **Swiss-grid typographic aesthetic**:
- **Strictly Monochrome Palette:** Pure Black (`#000000`) and Pure White (`#FFFFFF`) surfaces separated by `1.dp` hairline borders (`#E5E5E5`) and high-legibility charcoal subtitles (`#525252`).
- **Zero Visual Clutter:** No gradients, no decorative illustrations, and no distracting animations—every principal figure, accrued interest breakdown, partial repayment, and UPI QR code is immediately scannable.
- **Bundled Offline Typography:** Uses locally bundled Google Fonts (`Space Grotesk` for bold geometric numerals/headers, `Inter` for clean UI labels, and `IBM Plex Mono` for tabular financial breakdowns).
- **100% Offline-First & Private:** All financial records, party profiles, and settings remain strictly on-device inside a local Room SQLite database and Jetpack DataStore, protected by a startup **Biometric Security Gate (Fingerprint / Face Unlock + PIN)**.

---

## 2. Complete Feature Breakdown

### A. Startup Biometric Security Gate (`BiometricSecurityScreen`)
- Protects sensitive financial records on launch using AndroidX `BiometricPrompt` (**Fingerprint**, **Face Unlock**, or **Device Credential**).
- Includes a configurable offline **Fallback Security PIN** (default `0000`) and a one-tap **Lock Now** button in the top bar and settings.

### B. Dual-Currency Ledger (`INR ₹` & `NPR रू`)
- **Top Split Summary Card:** Displays **Total to Receive (Given)**, **Total to Pay (Taken)**, and **Net Ledger Position** with an instant currency switcher tab (`INR (₹)` | `NPR (रू)`) so balances across currencies never mix.
- **Real-Time Search & Filtering:** Filter records by `All`, `Given`, `Taken`, `Overdue/Expired`, `INR (₹)`, `NPR (रू)`, or `Settled`.

### C. Dynamic Simple & Compound Interest Engine (`InterestCalculator`)
- Per-transaction toggle for **Interest Applicable (`Yes / No`)**.
- Supports **Simple Interest** and **Compound Interest** with selectable rate periods (`% per month` or `% per annum`) and selectable compounding frequencies (`Monthly`, `Quarterly`, `Yearly`).
- Dynamically calculates accrued interest from `Start Date` up to `Current Date` (or `Expiry Date` / `Settlement Date`) and renders a subtle breakdown subtitle on every card:
  > `Principal: ₹10,000 | Interest: ₹450 (2%/mo • Simple • 45d) | Paid: ₹2,000`

### D. Partial Repayments & One-Tap Settlement
- Log multiple partial installment repayments with dates and notes against any active loan.
- Automatically deducts partial payments from the gross total (`Principal + Accrued Interest`) and marks records settled when fully repaid, or toggle full settlement with a single tap.

### E. People & Contact Directory Management (`PartyEntity` & Per-Party Khata)
- Dedicated **People Directory (`PEOPLE DIRECTORY`)** tab alongside the main ledger.
- Create or edit contacts manually, or import directly from the phonebook via Android's zero-permission contact picker (`ContactsContract.CommonDataKinds.Phone.CONTENT_URI`).
- Automatically formats 10-digit phone numbers with `+91` for `INR` or `+977` for `NPR`.
- Stores each party's personal **UPI ID (VPA)**, email, preferred currency, and relationship tag (`Friend`, `Supplier`, `Client`, `Family`, `Staff`).
- **Single-Party Khata Sheet:** Displays all loans `Given`/`Taken` with that contact, separate `INR` and `NPR` net balances, quick buttons to **Call**, **WhatsApp**, **Email**, **Export Single-Party Statement (JSON)**, and **Pay Party via UPI**.

### F. Two-Way Offline UPI QR Code Generator (`UpiQrGenerator` + ZXing)
1. **Receive Payments (User's UPI QR):** Configure your own **UPI ID (VPA)** and **Payee Name** in Settings to generate a scannable monochrome UPI QR code (`upi://pay?pa=YOUR_UPI_ID&pn=NAME&am=AMOUNT&cu=INR`) globally or pre-filled with the exact due balance of any `Given (INR)` transaction. Share the rendered high-contrast QR card image directly to WhatsApp.
2. **Pay Party Back (`TAKEN` Loan Settlement):** Open a contact's Single-Party Khata and tap **Pay Party via UPI** to render the party's personal UPI QR code or launch Google Pay / PhonePe / Paytm directly via `upi://pay`.

### G. Smart WhatsApp Reminder Engine & Privacy Toggle
- Schedule local Android reminder notifications (`AlarmManager` + `ReminderReceiver`) for any active transaction. Tapping the notification deep-links directly into that record's **WhatsApp Reminder Modal**.
- **Privacy Toggle (`[x] Hide Exact Amount in Message`):**
  - **Amount Visible:** *"Hello [Name], hope you are doing well. This is a gentle reminder from Udhaar regarding the [Cash/UPI/Online] record of [₹/रू][Total Amount] (Principal: [Amount], Interest: [Interest]) due on [Expiry Date]. Kindly let me know once settled. Thank you!"* (Optionally appends your UPI ID for INR records).
  - **Amount Hidden (Privacy Mode):** *"Hello [Name], hope you are doing well. This is a gentle reminder regarding our pending account settlement scheduled for [Expiry Date]. Please check at your convenience. Thank you!"*

---

## 3. Technical Architecture & Room Database Schema

### System Architecture (MVVM + Repository)
```
┌────────────────────────────────────────────────────────────────────────────┐
│                        UI LAYER (Jetpack Compose)                          │
│  • BiometricSecurityScreen (Fingerprint / Face Unlock + PIN Gate)          │
│  • DashboardScreen (INR/NPR Split Card, Ledger List & People Directory Tab)│
│  • AddEditUdhaarScreen (Contact Picker, Interest Config, Reminder Picker)  │
│  • SinglePartyLedgerModal (Per-Party Khata, Call/WA/Email, Pay Party UPI)  │
│  • ModalsAndSheets (Repayments, UPI QR, Share App QR, Settings & Support)  │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                      DOMAIN & STATE LAYER (MVVM)                           │
│  • UdhaarViewModel (Reactive StateFlow combining Parties, Loans, Settings) │
│  • InterestCalculator (Simple & Compound M/Q/Y Math + WhatsApp Templates)  │
│  • UpiQrGenerator (Offline ZXing QR Matrix + Shareable PNG Card Renderer)  │
│  • LedgerBackupManager (Full Ledger & Single-Party JSON Export / Import)   │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   DATA & SYSTEM LAYER (Offline-First)                      │
│  • UdhaarDatabase (Room SQLite v2: parties, udhaar_transactions, payments) │
│  • DataStore Preferences (UPI VPA, Payee Name, Default Currency, Bio Lock) │
│  • ReminderScheduler & ReminderReceiver (AlarmManager Local Notifications) │
└────────────────────────────────────────────────────────────────────────────┘
```

### Room Database Entities (`udhaar_ledger.db`, Schema Version 2)
1. **`PartyEntity` (`parties` table):**
   - `partyId: Long` (PrimaryKey, auto-generated)
   - `name: String`, `phoneNumber: String` (auto-sanitized `+91` / `+977`), `upiId: String`, `email: String`, `tagOrNotes: String`, `preferredCurrency: CurrencyType`, `createdAtMillis: Long`
2. **`UdhaarTransaction` (`udhaar_transactions` table):**
   - `id: Long` (PrimaryKey, auto-generated), `partyId: Long?`
   - `transactionType: TransactionType` (`GIVEN` | `TAKEN`), `partyName: String`, `phoneNumber: String`, `currency: CurrencyType` (`INR` | `NPR`)
   - `principalAmount: Double`, `amountPaid: Double`, `paymentMode: PaymentMode` (`CASH`, `ONLINE`, `UPI`, `PARTY_ACCOUNT`)
   - `isInterestEnabled: Boolean`, `interestType: InterestType` (`SIMPLE` | `COMPOUND`), `interestRate: Double`, `interestRatePeriod: InterestRatePeriod` (`PER_MONTH` | `PER_ANNUM`), `compoundFrequency: CompoundFrequency` (`MONTHLY`, `QUARTERLY`, `YEARLY`)
   - `startDateMillis: Long`, `expiryDateMillis: Long?`, `reminderDateMillis: Long?`, `notes: String`, `isSettled: Boolean`, `settledAtMillis: Long?`, `createdAtMillis: Long`
3. **`PartialPayment` (`partial_payments` table):**
   - `id: Long` (PrimaryKey), `transactionId: Long` (ForeignKey -> `UdhaarTransaction.id` with `CASCADE` delete), `amount: Double`, `paidAtMillis: Long`, `note: String`

### Package Structure
```
com.example
├── MainActivity.kt                  # FragmentActivity host, SAF launchers & Biometric gate
├── data/
│   ├── UdhaarEntities.kt            # PartyEntity, UdhaarTransaction, PartialPayment & enums
│   ├── UdhaarDao.kt                 # Reactive Flow queries & snapshot methods
│   ├── UdhaarDatabase.kt            # Room database v2 & TypeConverters
│   └── UdhaarRepository.kt          # Single source of truth (Room + DataStore)
├── notification/
│   ├── ReminderReceiver.kt          # BroadcastReceiver for local settlement notifications
│   └── ReminderScheduler.kt         # AlarmManager scheduling & cancellation
├── ui/
│   ├── UdhaarViewModel.kt           # Ledger, People Directory, QR & Backup state management
│   ├── components/
│   │   ├── BiometricSecurityScreen.kt # Fingerprint / Face Unlock & PIN screen
│   │   ├── DashboardScreen.kt         # Ledger & People Directory tabs, summary card
│   │   ├── PeopleDirectoryScreen.kt   # People Directory, Single-Party Khata & Party Modal
│   │   ├── AddEditUdhaarScreen.kt     # Loan creation/editing with live interest preview
│   │   └── ModalsAndSheets.kt         # Detail/Repayment, UPI QR, Share App, Settings & Admin
│   └── theme/
│       ├── Color.kt                 # Strictly monochrome color tokens
│       ├── Type.kt                  # Space Grotesk, Inter & IBM Plex Mono typography
│       └── Theme.kt                 # Monochrome Material 3 theme
└── util/
    ├── InterestCalculator.kt        # Simple/Compound math, phone formatting & WA templates
    ├── UpiQrGenerator.kt            # ZXing QR generator & shareable PNG card builder
    └── LedgerBackupManager.kt       # JSON export/import for Full Ledger & Party Statements
```

---

## 4. Build Instructions & How to Give Other Users Access

### Building & Running Locally
1. Ensure Android SDK 36 and JDK 11+ are configured.
2. Build the debug APK:
   ```bash
   gradle assembleDebug
   ```
3. Run the unit and Robolectric test suite:
   ```bash
   gradle :app:testDebugUnitTest
   ```

### Giving Other Users Access to the App & Ledger Records
1. **Google Play Store Production vs. Internal/Closed Testing:**
   - **Production Track:** Any user can install directly via `https://play.google.com/store/apps/details?id=com.aistudio.udhaar.kxmzqp`.
   - **Internal / Closed Testing Track:** Add the user's Google account email to the authorized testers list in Google Play Console and share the Web Opt-In URL before they open the Play Store link.
2. **In-App Play Store QR & WhatsApp Invite:**
   - Tap the **Share** icon in the top bar (or open **Settings -> Share / Give Access to Udhaar**).
   - Another person nearby can scan the monochrome Play Store QR code right off your screen, or you can tap **Share on WhatsApp** to send the direct Play Store link.
3. **Offline JSON Ledger Backup & Single-Party Statement Transfer:**
   - **Full Ledger Backup:** Go to **Settings -> Offline Ledger Backup & Record Transfer (JSON)** and tap **Export JSON** (saves to device storage via Storage Access Framework) or **Share Backup** (shares via WhatsApp/email using `FileProvider`).
   - **Single-Party Statement:** Open any person in **People Directory** and tap **Save Statement (JSON)** or **Share Statement**.
   - **Restore / Import:** On any secondary device, open **Settings -> Import Ledger Backup (JSON)** and select the `.json` file to merge parties, loans, and repayment histories offline.

---

## 5. Admin & Developer Contact

- **Administrator & Lead Developer:** `Jyotirmoy Bhowmik`
- **Admin Contact Email:** `admin@jyotirmoyb.com`
- **In-App Support:** Navigate to **`Settings -> About & Support -> Contact Admin (admin@jyotirmoyb.com)`**, which triggers a pre-addressed `mailto:admin@jyotirmoyb.com` intent with the app version in the subject line (and automatically copies `admin@jyotirmoyb.com` to the clipboard as a fallback).
