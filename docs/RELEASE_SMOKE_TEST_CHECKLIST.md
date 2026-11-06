# Udhaar v22 — Release Smoke Test & Play Console R8 Verification Checklist

Run this checklist on a signed **Release (`bundleRelease` / `assembleRelease` with R8 enabled by default)** build before promoting a release to Google Play production.

---

## 0. Building the Signed Release AAB & Verifying R8 Locally

### Exact Release Build Command
R8 minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`) are **ON by default** for the `release` build type in `app/build.gradle.kts`. In addition, `bundleRelease` depends on the `verifyReleaseR8` task, which fails the build if `-Pudhaar.r8=false` is passed or if shrinking is disabled.

```bash
# 1. Verify R8 guard and build signed Release AAB + R8 mapping file
gradle :app:verifyReleaseR8 :app:bundleRelease

# (Optional) Build signed Release APK as well
gradle :app:assembleRelease

# Fast local opt-out ONLY for non-bundle local iteration (will intentionally fail if used with bundleRelease):
gradle :app:assembleRelease -Pudhaar.r8=false
```

### Local Pre-Upload Inspection (AAB Size, DEX Size & Embedded `proguard.map`)
Before uploading `app/build/outputs/bundle/release/app-release.aab` to Play Console, confirm locally that R8 ran and embedded the deobfuscation metadata:

```bash
# 1. Confirm non-empty R8 mapping file was generated on disk
ls -lh app/build/outputs/mapping/release/mapping.txt
head -n 25 app/build/outputs/mapping/release/mapping.txt

# 2. Confirm BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map is inside the AAB
unzip -l app/build/outputs/bundle/release/app-release.aab | grep -E "proguard\.map|classes.*\.dex"

# 3. Print AAB size and shrunk DEX size (must be well below the unminified 47 MB DEX size)
python3 - << 'EOF'
import os, zipfile
aab = "app/build/outputs/bundle/release/app-release.aab"
print(f"AAB size: {os.path.getsize(aab) / (1024 * 1024):.2f} MB")
with zipfile.ZipFile(aab, "r") as zf:
    dex = [i for i in zf.infolist() if i.filename.startswith("base/dex/") and i.filename.endswith(".dex")]
    total_dex_mb = sum(i.file_size for i in dex) / (1024 * 1024)
    has_map = any("com.android.tools.build.obfuscation/proguard.map" in i.filename for i in zf.infolist())
    print(f"Uncompressed DEX total ({len(dex)} file(s)): {total_dex_mb:.2f} MB")
    print(f"Embedded R8 proguard.map present: {has_map}")
EOF
```

### How to Confirm Shrinking & R8 Metadata in Google Play Console
After uploading `app-release.aab` (either via the release track or **Test and release $\rightarrow$ App bundle explorer**):

1. Open **Google Play Console** $\rightarrow$ select **Udhaar (`com.jyotirmoyb.udhaar`)**.
2. In the left navigation menu, go to **Test and release $\rightarrow$ App bundle explorer** and select version **`22 (22.0)`**.
3. Open the **Details** tab:
   - Confirm there is **no warning** stating *"This App Bundle contains Java/Kotlin code, which might be obfuscated. We recommend you upload a deobfuscation file"* or *"unoptimized DEX"*.
4. Open the **Downloads** tab:
   - Under **Assets and files**, confirm **ReTrace mapping file (`mapping.txt`)** is listed as **Available / Uploaded** (automatically extracted by Play Console from `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map` inside the AAB; if ever needed manually, upload the `r8-mapping-release` artifact `app/build/outputs/mapping/release/mapping.txt`).
   - Under **Download size** / **Install base**, confirm the estimated download size and DEX footprint reflect R8 shrinking (typically `< 10 MB` DEX instead of the unminified `47 MB` DEX).
5. Open **Monitor and improve $\rightarrow$ Android vitals $\rightarrow$ Crashes and ANRs**:
   - Confirm stack traces show deobfuscated class/method names with exact Kotlin source line numbers (`SourceFile` and `LineNumberTable` are preserved in `app/proguard-rules.pro`).

---

## 1. Automated Release Smoke Tests (Managed Device Emulator & Local JVM)

Run the automated 6-part smoke test suite covering **cold start**, **add a record**, **restore a backup**, **open a guest link**, **widget update**, and **database migration from the previous version (`v11 -> v12`)**:

```bash
# Run on the Gradle Managed Device emulator (Pixel 6 API 34, AOSP ATD)
gradle :app:pixel6Api34DebugAndroidTest

# Run the same 6 smoke test journeys + R8/ProGuard verification on local JVM (Robolectric)
gradle :app:testDebugUnitTest --tests com.jyotirmoyb.udhaar.ReleaseSmokeTest
```

---

## 2. Install, Cold Start & Database Migration (`MIGRATION_10_11` & `MIGRATION_11_12`)
- [ ] **Fresh install cold start**: Install the R8-minified release build on a clean device; verify onboarding modal appears, SQLCipher key is generated, and Dashboard loads without crash.
- [ ] **Upgrade install (v4–v11 -> v12)**: Install over an existing Udhaar build containing INR & NPR records, partial payments, and interest; verify `MIGRATION_11_12` completes cleanly and pre/post paise totals match to the exact paisa.
- [ ] **SQLCipher encryption**: Open **Settings -> Security** and confirm the SQLCipher status badge shows **Encrypted (AES-256)**.

---

## 3. Money Precision (`Long` Paise Source of Truth)
- [ ] **Create & edit transaction**: Add a transaction for `₹1,234.56` (`123456` paise) with 2% monthly simple or compound interest; verify Dashboard Receivable/Payable cards, Net INR Position, and expanded breakdown match exact paise rounding (`HALF_UP`).
- [ ] **Partial repayments & installments**: Record partial payments (`₹234.56`, `₹500.00`) and verify remaining balance updates to `₹500.00` (`50000` paise) with zero floating-point drift.
- [ ] **Export & receipt parity**: Generate a Thermal Receipt PNG and a PDF Statement; confirm all amounts and tables render without overlapping and match the ledger paise totals.
- [ ] **Home screen widget update**: Add the Udhaar Summary Widget (`UdhaarWidgetProvider`) to the launcher home screen; confirm `To Receive`, `To Pay`, and `ALL CLEAR ✓ / N OVERDUE` update immediately when adding or settling a record.

---

## 4. Dashboard Layout (`v19` Placement), Adaptive Two-Pane & Accessibility (`fontScale = 2.0`)
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

## 5. Backup Recovery Key, "Test my backup" & Restore
- [ ] **Generate Backup Recovery Key**: In **Settings -> Backup & Restore**, tap **Generate Recovery Key**, copy the key, and verify it is saved.
- [ ] **Test my backup**: Tap **Test my backup** (`test_my_backup_button`); verify the app performs a full AES-256-GCM encrypt + decrypt + JSON schema & paise checksum round-trip and displays the verification pass message.
- [ ] **Local `.udhaar` & JSON export/import**: Export an encrypted `.udhaar` backup via SAF and restore it; verify all parties, transactions, partial payments, and attachments are intact.

---

## 6. Guest Web Flow, Deep Links, Cloud Functions Secret & Storage Rules
- [ ] **Open a guest link**: Open a deep link `https://udhaar.jyotirmoyb.com/u/r/<recordId>#<key>` or `https://udhaar.jyotirmoyb.com/u/<token>` on the device; verify `MainActivity` handles the `ACTION_VIEW` intent cleanly.
- [ ] **Secret Manager (`UDHAAR_SERVER_SECRET`)**: Verify `UDHAAR_SERVER_SECRET` is provisioned in Google Cloud Secret Manager and bound via `defineSecret("UDHAAR_SERVER_SECRET")`. Confirm endpoints fail closed (`500` / error) if the secret is missing.
- [ ] **Storage signed upload URL**: Trigger `getProofUploadUrl` from a guest web link; verify the V4 signed URL enforces `contentType` (`image/jpeg`, `image/png`, `image/webp`, or `application/pdf`) and extension header `x-goog-content-length-range: 0,5242880` (max 5 MB).
- [ ] **Direct client upload blocked**: Verify direct client writes to Firebase Storage `shared_udhaar/{token}/proofs/*` are denied by `storage.rules` (`allow create: if false;`), and the 90-day lifecycle rule (`storage.lifecycle.json`) is active on the bucket.

---

## 7. Privacy-First Crashlytics & Screen Analytics (Opt-In Only)
- [ ] **Default OFF**: On a fresh install, verify **Settings -> Security -> Crashlytics & Screen Analytics** is **OFF** by default and zero events or crash reports are queued or transmitted.
- [ ] **Opt-In with zero PII**: Enable the toggle, navigate across screens (`dashboard`, `people_directory`, `add_edit_udhaar`, `settings`), and verify only sanitized screen IDs (`[a-z0-9_]{1,40}`) are logged — never names, phone numbers, UPI IDs, notes, or amounts.
