# Udhaar – Minimalist Loan & Ledger Application

**Udhaar** is a distraction-free, strictly monochrome Android application designed for tracking personal and party-based loans (money given and money taken) across Indian Rupees (`INR ₹`) and Nepalese Rupees (`NPR रू`). It combines offline-first Room persistence, real-time simple and compound interest calculation, partial and full settlement tracking, offline UPI QR code generation, and scheduled WhatsApp reminder automation with privacy controls.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product and architectural choices have been confirmed from your input and incorporated into this plan:

- **Confirmed Decision 1 – Partial & Full Repayments**: Each Udhaar loan record supports logging partial installment repayments as well as a one-tap full settlement action, dynamically updating the remaining balance and accrued interest.
- **Confirmed Decision 2 – Currency Switcher on Summary Card**: The top dashboard summary card features a crisp monochrome switcher tab (`INR ₹` | `NPR रू`) so balances for each currency are viewed cleanly and never mixed.
- **Confirmed Decision 3 – Selectable Compound Interest Frequency**: When Compound Interest is selected, users can choose the compounding frequency (`Monthly`, `Quarterly`, or `Yearly`) alongside the interest rate period (`% per month` or `% per annum`).

---

## 1. Overview & Core Concept

- **What It Does**: Provides a fast, high-contrast ledger for recording money lent (`Given`) and borrowed (`Taken`), automatically computing accrued simple or compound interest up to the current or expiry date, generating scannable UPI payment QR codes for INR settlements, and dispatching polite WhatsApp reminders with optional amount-privacy masking.
- **Target Audience / Persona**: Individuals, shopkeepers, freelancers, and cross-border traders operating in India and Nepal who want a reliable, private, offline ledger without bloated accounting menus or cloud dependencies.
- **Key Value**: Zero-clutter financial clarity—every principal amount, interest breakdown, partial payment, UPI collection QR, and WhatsApp follow-up is accessible within one or two taps.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Dashboard & Ledger Inspection**:
   - At the top, a high-contrast black-and-white split summary card displays **Total to Receive (Given)** and **Total to Pay (Taken)** for the active currency tab (`INR ₹` or `NPR रू`).
   - Below the summary card, a search bar and horizontal filter bar (`All`, `Given`, `Taken`, `Overdue/Expired`, `INR`, `NPR`, `Settled`) refine the transaction list in real time.
   - Each transaction card displays the Party Name, Payment Mode badge (`Cash`, `Online`, `UPI`, `Party Account`), Due/Expiry status badge, total outstanding amount in bold typography, and a subtle subtitle line detailing the principal, accrued interest breakdown, and any partial payments made.
2. **Creating / Editing an Udhaar Record**:
   - Users select Transaction Type (`Given` / `Taken`), Currency (`INR ₹` / `NPR रू`), and enter or pick the Party Name & Phone Number directly from Android Contacts using the zero-permission contact picker contract.
   - Users enter the Principal Amount, select a Payment Mode tag, and optionally toggle **Interest Applicable**. When enabled, users configure `Simple` vs. `Compound` interest, Rate (`% per month` or `% per annum`), Compounding Frequency (`Monthly`, `Quarterly`, `Yearly`), and Start Date.
   - Users can also set an optional Expiry/Due Date, a Scheduled Reminder Date/Time, and Notes.
3. **Partial Repayments & Settlement Sheet**:
   - Tapping a transaction opens its detail & settlement sheet showing the full interest calculation timeline, history of partial payments, an input to record a new partial payment, and a one-tap **Mark Fully Settled** toggle.
4. **UPI QR Code Generation & Sharing (Global & Per-Transaction)**:
   - From the Settings/Profile modal, users configure their default **UPI ID (VPA)**, **Payee Name**, and default currency.
   - Tapping the QR icon on the top bar or on any `Given (INR)` transaction card opens a clean white QR card rendered offline via ZXing (`upi://pay?pa=UPI_ID&pn=NAME&am=AMOUNT&cu=INR`). Users can present it on-screen for immediate scanning (Google Pay, PhonePe, Paytm) or share the rendered QR card image directly to WhatsApp.
5. **Smart WhatsApp Reminder & Privacy Toggle**:
   - Tapping the Remind/WhatsApp button on a transaction (or tapping a scheduled local notification) opens the **WhatsApp Reminder Preview Modal**.
   - Users can toggle **`[x] Hide Exact Amount in Message`** and watch the polite message template update live before launching WhatsApp (`https://wa.me/<phone>?text=<encoded_message>`).

### Visual Identity & Theme
- **Aesthetic Direction**: Utilitarian editorial minimalism—inspired by Swiss typographic grids and thermal receipt precision. No gradients, no colorful illustrations, no drop-shadow clutter.
- **Color Palette & Mood**:
  - Primary Background: Pure White (`#FFFFFF`)
  - Primary Foreground / Ink: Pure Black (`#000000`)
  - Inverted Surface (Summary Header & Selected Pills): Pure Black (`#000000`) with Pure White (`#FFFFFF`) text
  - Subtle Borders & Hairline Dividers: Light Neutral Grey (`#E5E5E5`)
  - Secondary / Subtitle Text: High-legibility Charcoal Grey (`#525252`)
  - Muted Surface Fill (Cards & Inputs): Crisp Off-White (`#FAFAFA`)
- **Typography & Hierarchy**:
  - Bundled local Google Fonts via `font-util`: **Space Grotesk** (bold geometric display numerals and headers) paired with **Inter** / **IBM Plex Mono** (clean body copy, tabular figures, and interest calculation subtitles).
- **Interactive Feedback & Motion**:
  - Crisp `1.dp` solid black borders on active inputs and selected filter chips, instantaneous state transitions, and subtle Material ripple feedback (`#E5E5E5`) on all 48dp+ touch targets.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Offline-First Local Storage with Room + DataStore**
  - *Chosen Approach*: Use Room SQLite database for transactions and repayment logs, paired with preferences storage for user profile settings (UPI ID, Payee Name, default currency).
  - *Why*: Guarantees 100% offline speed, zero network latency, and strict financial privacy on the user's device.
  - *Alternatives Considered*: Cloud database sync was passed over because the specification explicitly mandates private, offline-first local SQLite/Room storage.
- **Decision 2: Dynamic Real-Time Interest Engine**
  - *Chosen Approach*: Store the principal, start date, rate, rate period, interest type, and compounding frequency in Room, and dynamically compute accrued interest up to `min(CurrentDate, ExpiryDate)` (or current date if overdue tracking applies) whenever the ledger loads or updates.
  - *Why*: Ensures interest figures are always accurate to the current day without requiring background cron jobs to mutate database rows daily.
- **Decision 3: Scheduled Reminders with AlarmManager / BroadcastReceiver + Deep-Link Intent**
  - *Chosen Approach*: Schedule exact/windowed local Android notifications at the user's chosen reminder timestamp; tapping the notification launches `MainActivity` with the transaction ID pre-selected and opens the WhatsApp Reminder Preview Modal immediately.
  - *Why*: Works reliably offline and keeps the user in full control of the final WhatsApp message and privacy toggle before sending.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         UI LAYER (Jetpack Compose)                        │
│                                                                           │
│  ┌──────────────────────┐   ┌──────────────────────┐   ┌───────────────┐  │
│  │   DashboardScreen    │   │ AddEditUdhaarModal / │   │ Settings &    │  │
│  │ ┌──────────────────┐ │   │       Screen         │   │ Global UPI QR │  │
│  │ │CurrencySummaryCard│ │   ├──────────────────────┤   │     Modal     │  │
│  │ │  (INR ₹ | NPR रू) │ │   │ • Contact Picker     │   └───────┬───────┘  │
│  │ └──────────────────┘ │   │ • Interest Config    │           │          │
│  │ ┌──────────────────┐ │   │ • Reminder Scheduler │           │          │
│  │ │Filter & SearchBar│ │   └──────────┬───────────┘           │          │
│  │ └──────────────────┘ │              │                       │          │
│  │ ┌──────────────────┐ │   ┌──────────▼───────────┐   ┌───────▼───────┐  │
│  │ │TransactionLazyList│◄──►│ Repayment & Detail   │   │ WhatsAppShare │  │
│  │ │+ InterestSubtitle│ │   │ BottomSheet          │   │ Preview Modal │  │
│  │ └──────────────────┘ │   └──────────┬───────────┘   └───────┬───────┘  │
│  └──────────┬───────────┘              │                       │          │
└─────────────┼──────────────────────────┼───────────────────────┼──────────┘
              │                          ▼                       │
┌─────────────┴──────────────────────────────────────────────────┴──────────┐
│                     STATE & DOMAIN LAYER (MVVM)                           │
│                                                                           │
│  ┌─────────────────────────┐  ┌────────────────────┐  ┌────────────────┐  │
│  │     UdhaarViewModel     │  │ InterestCalculator │  │ UpiQrGenerator │  │
│  │ • Ledger UI StateFlow   │  │ • Simple Interest  │  │ • ZXing Matrix │  │
│  │ • Currency & Filter     │  │ • Compound (M/Q/Y) │  │ • Bitmap Share │  │
│  │ • Partial Payment Logic │  │ • Breakdown Text   │  │ • WhatsApp URI │  │
│  └────────────┬────────────┘  └────────────────────┘  └────────────────┘  │
└───────────────┼───────────────────────────────────────────────────────────┘
                ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                    DATA & SYSTEM LAYER (Offline-First)                    │
│                                                                           │
│  ┌──────────────────────────────┐      ┌───────────────────────────────┐  │
│  │     UdhaarRepository         │      │   ReminderNotificationEngine  │  │
│  │ ┌──────────────┐ ┌─────────┐ │      │ • AlarmManager Scheduling     │  │
│  │ │Room Database │ │Settings │ │      │ • NotificationReceiver        │  │
│  │ │• Transactions│ │Store    │ │      │ • Deep-Link to WhatsApp Modal │  │
│  │ │• Repayments  │ │(UPI/VPA)│ │      └───────────────────────────────┘  │
│  │ └──────────────┘ └─────────┘ │                                         │
│  └──────────────────────────────┘                                         │
└───────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State
- **`UdhaarTransaction` Entity**:
  - `id` (PrimaryKey, auto-generated Long)
  - `transactionType`: `GIVEN` (Lent / Receive) or `TAKEN` (Borrowed / Pay)
  - `partyName`: String
  - `phoneNumber`: String
  - `currency`: `INR` (`₹`) or `NPR` (`रू`)
  - `principalAmount`: Double
  - `amountPaid`: Double (sum of partial repayments logged against this record)
  - `paymentMode`: `CASH`, `ONLINE`, `UPI`, `PARTY_ACCOUNT`
  - `isInterestEnabled`: Boolean
  - `interestType`: `SIMPLE`, `COMPOUND`
  - `interestRate`: Double
  - `interestRatePeriod`: `PER_MONTH`, `PER_ANNUM`
  - `compoundFrequency`: `MONTHLY`, `QUARTERLY`, `YEARLY`
  - `startDateMillis`: Long
  - `expiryDateMillis`: Long? (optional due date)
  - `reminderDateMillis`: Long? (optional scheduled notification timestamp)
  - `notes`: String
  - `isSettled`: Boolean
  - `createdAtMillis`: Long
- **`PartialPayment` Entity**:
  - `id` (PrimaryKey), `transactionId` (ForeignKey indexed), `amount`: Double, `paidAtMillis`: Long, `note`: String
- **`UserSettings` Model**:
  - `upiId`: String (e.g., `merchant@okaxis`), `payeeName`: String, `defaultCurrency`: `INR` or `NPR`

### Interactive Component & State Mapping
- **Currency Switcher (`INR` | `NPR`)**: Toggling the currency tab on the Top Summary Card immediately switches the displayed `Total to Receive` and `Total to Pay` aggregates for that specific currency without cross-contaminating currencies.
- **Filter & Search Pipeline**: Reactive `combine()` flow in `UdhaarViewModel` filters transactions by search query (party name, phone, notes) and active filter pill (`All`, `Given`, `Taken`, `Overdue/Expired`, `INR`, `NPR`, `Settled`), recalculating live interest breakdowns for each item.
- **Interest Calculation Utility**:
  - Converts elapsed days between `startDateMillis` and calculation target date into exact months/years.
  - For **Simple Interest**: $I = P \times r \times t$.
  - For **Compound Interest**: $A = P \times (1 + \frac{r_{annual}}{n})^{n \times t_{years}}$, where $n \in \{12, 4, 1\}$ for Monthly, Quarterly, or Yearly compounding, and $I = A - P$.
  - Deducts `amountPaid` to display net remaining balance alongside the subtle breakdown subtitle (`Principal: ₹10,000 + Interest: ₹450 - Paid: ₹2,000`).
- **WhatsApp Reminder Modal & Privacy Toggle**:
  - Toggling `[x] Hide Exact Amount in Message` switches between the detailed template (including payment mode, currency symbol, total due, principal/interest breakdown, expiry date, and optional UPI ID for INR) and the privacy-safe template (referencing only party name and scheduled settlement date).
  - Sanitizes phone numbers (auto-prefixing country code `+91` for INR or `+977` for NPR if a 10-digit local number is entered without a country code) and launches `Intent.ACTION_VIEW` with `https://wa.me/<phone>?text=<encoded_message>`.
