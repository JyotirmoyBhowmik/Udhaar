# Udhaar v20 — Release Smoke Test Checklist

Run this checklist on a signed **Release (`assembleRelease` / R8 enabled)** build before promoting a release to Google Play production.

---

## 1. Install, Cold Start & Database Migration (`MIGRATION_10_11`)
- [ ] **Fresh install cold start**: Install the R8-minified release APK on a clean device; verify onboarding modal appears and completes without crash.
- [ ] **Upgrade install (v4–v10 -> v11)**: Install over an existing Udhaar build containing INR & NPR records, partial payments, and interest; verify `MIGRATION_10_11` completes cleanly and pre/post paise totals match to the exact paisa.
- [ ] **SQLCipher encryption**: Open **Settings -> Security** and confirm the SQLCipher status badge shows **Encrypted (AES-256)**.

---

## 2. Money Precision (`Long` Paise Source of Truth)
- [ ] **Create & edit transaction**: Add a transaction for `₹1,234.56` (`123456` paise) with 2% monthly simple or compound interest; verify Dashboard Receivable/Payable cards, Net INR Position, and expanded breakdown match exact paise rounding (`HALF_UP`).
- [ ] **Partial repayments & installments**: Record partial payments (`₹234.56`, `₹500.00`) and verify remaining balance updates to `₹500.00` (`50000` paise) with zero floating-point drift.
- [ ] **Export & receipt parity**: Generate a Thermal Receipt PNG and a PDF Statement; confirm all amounts and tables render without overlapping and match the ledger paise totals.

---

## 3. Dashboard Layout (`v19` Placement), Adaptive Two-Pane & Accessibility (`fontScale = 2.0`)
- [ ] **Phone portrait (`< 600 dp`)**: Verify the exact `v19` top-to-bottom order:
  1. `Udhaar` header + subtitle + quick action icons (`Group Split`, `Updates`, `Settings`)
  2. `Active (N)` / `People (N)` / `History` pill tabs
  3. Search bar + filter chips (`All`, `Given`, `Taken`, `Overdue`, `Settled`)
  4. Compact `Total Receivable` / `Total Payable` cards + `Net INR Position` bar
  5. `Needs Attention` banner (when overdue/pending records exist)
  6. `Records (N)` header + `+ Group Split` + sort pill (`Newest`)
  7. Compact 2-line transaction cards that expand smoothly on tap
- [ ] **Empty state**: Verify no coach-marks card or duplicate CTA button overlaps the centered `+ New udhaar` FAB.
- [ ] **Tablet / Foldable (`>= 600 dp`) & Landscape**: Verify list and detail panes render side-by-side (`dashboard_adaptive_two_pane`), and `AddEditUdhaarScreen` centers its form at `<= 560 dp` (`adaptive_centered_form_container`).
- [ ] **Large font scale (`2.0x`) & 48 dp touch targets**: Set Android system font size to **200%**; verify all interactive icons and text buttons remain at least `48 dp × 48 dp` and text buttons expand vertically without clipping.

---

## 4. Backup Recovery Key, "Test my backup" & Restore
- [ ] **Generate Backup Recovery Key**: In **Settings -> Backup & Restore**, tap **Generate Recovery Key**, copy the key, and verify it is saved.
- [ ] **Test my backup**: Tap **Test my backup** (`test_my_backup_button`); verify the app performs a full AES-256-GCM encrypt + decrypt + JSON schema & paise checksum round-trip and displays the verification pass message.
- [ ] **Local `.udhaar` & JSON export/import**: Export an encrypted `.udhaar` backup via SAF and restore it; verify all parties, transactions, partial payments, and attachments are intact.

---

## 5. Guest Web Flow, Cloud Functions Secret & Storage Rules
- [ ] **Secret Manager (`UDHAAR_SERVER_SECRET`)**: Verify `UDHAAR_SERVER_SECRET` is provisioned in Google Cloud Secret Manager and bound via `defineSecret("UDHAAR_SERVER_SECRET")`. Confirm endpoints fail closed (`500` / error) if the secret is missing.
- [ ] **Storage signed upload URL**: Trigger `getProofUploadUrl` from a guest web link; verify the V4 signed URL enforces `contentType` (`image/jpeg`, `image/png`, `image/webp`, or `application/pdf`) and extension header `x-goog-content-length-range: 0,5242880` (max 5 MB).
- [ ] **Direct client upload blocked**: Verify direct client writes to Firebase Storage `shared_udhaar/{token}/proofs/*` are denied by `storage.rules` (`allow create: if false;`), and the 90-day lifecycle rule (`storage.lifecycle.json`) is active on the bucket.

---

## 6. Privacy-First Crashlytics & Screen Analytics (Opt-In Only)
- [ ] **Default OFF**: On a fresh install, verify **Settings -> Security -> Crashlytics & Screen Analytics** is **OFF** by default and zero events or crash reports are queued or transmitted.
- [ ] **Opt-In with zero PII**: Enable the toggle, navigate across screens (`dashboard`, `people_directory`, `add_edit_udhaar`, `settings`), and verify only sanitized screen IDs (`[a-z0-9_]{1,40}`) are logged — never names, phone numbers, UPI IDs, notes, or amounts.
