> [!WARNING]
> **OBSOLETE / HISTORICAL REFERENCE (Pre-v32):** Any sections below referencing legacy Cloud Functions, Cloud KMS, Blaze-only server endpoints, or "Platform v2" are **superseded and archived** under `archive/`. For the canonical Udhaar v32.0 Spark Mode architecture, security rules, and data safety declarations, see [`docs/REALITY_CHECK.md`](REALITY_CHECK.md), [`docs/PLAY_DATA_SAFETY.md`](PLAY_DATA_SAFETY.md), and [`README.md`](../README.md).

# Udhaar — Pre-Release Manual QA Execution Results Template

This document serves as the standardized test execution record for validating pre-release builds of **Udhaar** (`com.jyotirmoyb.udhaar`). Every manual test run conducted on physical devices prior to Google Play production rollout must complete and record results using this template.

---

## 1. Test Run Metadata

| Field | Details | Notes |
| :--- | :--- | :--- |
| **Test Run Identifier** | `TR-2026-RELEASE-v1.0.0-01` | Format: `TR-<YEAR>-RELEASE-<VERSION>-<RUN>` |
| **Execution Date & Time** | `YYYY-MM-DD HH:MM IST` | |
| **QA Lead / Primary Tester** | *Name / GitHub Handle* | |
| **Git Commit SHA** | *Commit Hash (e.g. `eac4cd5`)* | Must be on `main` or release branch |
| **Release Tag** | *e.g. `v1.0.0`* | |
| **Build Artifact Tested** | `app-release.aab` / `app-release.apk` | Signed release build with R8 full mode |
| **Version Name / Code** | `versionName: 1.0.0` \| `versionCode: 30` | As shown on Settings > About screen |
| **Firebase Project** | `udhaar-prod` / `demo-udhaar-spark` | Spark (free tier) plan |
| **Primary Device 1 (Lender)** | *e.g. Google Pixel 7 (Android 14, 411dp)* | Primary testing device |
| **Secondary Device 2 (Borrower)** | *e.g. Samsung Galaxy A14 (Android 13, 360dp)* | For guest link verification |
| **Overall Release Decision** | **[ ] GO &nbsp;&nbsp;&nbsp;&nbsp; [ ] NO-GO &nbsp;&nbsp;&nbsp;&nbsp; [ ] CONDITIONAL GO** | Requires all P0 tests PASS |

---

## 2. Executive Execution Summary

| Test Category | Total Cases | P0 Count | Passed | Failed | Blocked | Skipped | Pass Rate (%) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| 1. Onboarding, Storage & App Lock | 8 | 5 | 0 | 0 | 0 | 0 | 0.0% |
| 2. Ledger, Paise Engine & Rounding | 8 | 5 | 0 | 0 | 0 | 0 | 0.0% |
| 3. Backup, Restore & Fault Tolerance | 5 | 4 | 0 | 0 | 0 | 0 | 0.0% |
| 4. Guest Flow Across 2 Devices | 13 | 8 | 0 | 0 | 0 | 0 | 0.0% |
| 5. Offline-First & Sync Conformance | 3 | 2 | 0 | 0 | 0 | 0 | 0.0% |
| 6. Daily Quota Exhaustion (Spark) | 3 | 2 | 0 | 0 | 0 | 0 | 0.0% |
| 7. Permissions & Form Factors | 6 | 1 | 0 | 0 | 0 | 0 | 0.0% |
| 8. Account & Data Deletion | 3 | 2 | 0 | 0 | 0 | 0 | 0.0% |
| 9. Upgrade & Version Migration | 1 | 1 | 0 | 0 | 0 | 0 | 0.0% |
| 10. Minified Release Build Smoke | 4 | 3 | 0 | 0 | 0 | 0 | 0.0% |
| **TOTALS** | **54** | **33** | **0** | **0** | **0** | **0** | **0.0%** |

---

## 3. Comprehensive Test Execution Grid

*Mark each case with `PASS`, `FAIL`, `BLOCKED`, or `SKIP`.*

### Section 1: Installation, Onboarding, Secure Storage & App Lock
| ID | Priority | Test Title | Device | Result | Tester Notes & Evidence Link | Defect # |
| :--- | :---: | :--- | :--- | :---: | :--- | :---: |
| `TC-ONB-01` | `P0` | Fresh Clean Installation & Cold Start | Device 1 | `UNTESTED` | | |
| `TC-ONB-02` | `P1` | 5-Language Onboarding Localization | Device 1 | `UNTESTED` | | |
| `TC-ONB-03` | `P0` | SQLCipher Hardware Key Fail-Closed | Device 1 | `UNTESTED` | | |
| `TC-ONB-04` | `P0` | Firebase Spark Google Sign-In | Device 1 | `UNTESTED` | | |
| `TC-ONB-05` | `P1` | Email / Password Sign-In & Verification | Device 1 | `UNTESTED` | | |
| `TC-ONB-06` | `P0` | PBKDF2 PIN Security Setup & Throttling | Device 1 | `UNTESTED` | | |
| `TC-ONB-07` | `P0` | Biometric Authentication Prompt | Device 1 | `UNTESTED` | | |
| `TC-ONB-08` | `P1` | Keystore Invalidation Recovery Screen | Device 1 | `UNTESTED` | | |

### Section 2: Ledger Management, Exact Paise Engine, Installments & Rounding
| ID | Priority | Test Title | Device | Result | Tester Notes & Evidence Link | Defect # |
| :--- | :---: | :--- | :--- | :---: | :--- | :---: |
| `TC-LED-01` | `P0` | Create Given & Taken Records | Device 1 | `UNTESTED` | | |
| `TC-LED-02` | `P1` | Edit Core Terms & Audit History | Device 1 | `UNTESTED` | | |
| `TC-LED-03` | `P1` | Delete Record (Soft Delete Tombstone) | Device 1 | `UNTESTED` | | |
| `TC-LED-04` | `P0` | Record Partial Payments | Device 1 | `UNTESTED` | | |
| `TC-LED-05` | `P0` | Installment Planner Uneven & Rounding Split | Device 1 | `UNTESTED` | | |
| `TC-LED-06` | `P1` | Installment Payment Spillover Allocation | Device 1 | `UNTESTED` | | |
| `TC-LED-07` | `P0` | Large Monetary Scale (Crores / Millions) | Device 1 | `UNTESTED` | | |
| `TC-LED-08` | `P1` | Decimal Input Boundary & Precision Defense | Device 1 | `UNTESTED` | | |

### Section 3: Google Drive Encrypted Backup & Cross-Device Restore
| ID | Priority | Test Title | Device | Result | Tester Notes & Evidence Link | Defect # |
| :--- | :---: | :--- | :--- | :---: | :--- | :---: |
| `TC-BAK-01` | `P0` | Google Drive AES-256-GCM Encrypted Backup | Device 1 | `UNTESTED` | | |
| `TC-BAK-02` | `P0` | Cross-Device Restore to Second Phone | Device 2 | `UNTESTED` | | |
| `TC-BAK-03` | `P0` | Corrupted Backup File Defense (Data Survival) | Device 1 | `UNTESTED` | | |
| `TC-BAK-04` | `P0` | Wrong Decryption Passphrase Rejection | Device 1 | `UNTESTED` | | |
| `TC-BAK-05` | `P1` | Zip-Slip & Path Traversal Injection Defense | Device 1 | `UNTESTED` | | |

### Section 4: Guest Confirmation Flow Across 2 Devices (Web Capability Portal)
| ID | Priority | Test Title | Device | Result | Tester Notes & Evidence Link | Defect # |
| :--- | :---: | :--- | :--- | :---: | :--- | :---: |
| `TC-GST-01` | `P0` | Level A Private Link Creation & Anonymous Read | D1 -> D2 | `UNTESTED` | | |
| `TC-GST-02` | `P0` | Level B Google-Verified Link: Wrong Account Denied | D1 -> D2 | `UNTESTED` | | |
| `TC-GST-03` | `P0` | Level B Google-Verified Link: Right Account Allowed | D1 -> D2 | `UNTESTED` | | |
| `TC-GST-04` | `P0` | Guest `CONFIRM` Proposal Submission & Acceptance | D1 -> D2 | `UNTESTED` | | |
| `TC-GST-05` | `P1` | Guest `DISPUTE` Proposal Submission | D1 -> D2 | `UNTESTED` | | |
| `TC-GST-06` | `P1` | Guest `PROPOSE_DATE` Due Date Adjustment | D1 -> D2 | `UNTESTED` | | |
| `TC-GST-07` | `P0` | Guest `CLAIM_PAYMENT` (UPI Mode & UTR) | D1 -> D2 | `UNTESTED` | | |
| `TC-GST-08` | `P1` | Guest `CLAIM_PAYMENT` (Cash & Bank Transfer) | D1 -> D2 | `UNTESTED` | | |
| `TC-GST-09` | `P0` | Duplicate Pending Action Denial (Rate Limiting) | D1 -> D2 | `UNTESTED` | | |
| `TC-GST-10` | `P0` | Lender Rejection of Payment Claim | D1 -> D2 | `UNTESTED` | | |
| `TC-GST-11` | `P0` | Revoked Link Access Prevention | D1 -> D2 | `UNTESTED` | | |
| `TC-GST-12` | `P0` | Expired Link Access Prevention | D1 -> D2 | `UNTESTED` | | |
| `TC-GST-13` | `P1` | Missing Lender UPI Graceful UI Handling | D1 -> D2 | `UNTESTED` | | |

### Section 5: Offline-First Resilience & Sync Conformance
| ID | Priority | Test Title | Device | Result | Tester Notes & Evidence Link | Defect # |
| :--- | :---: | :--- | :--- | :---: | :--- | :---: |
| `TC-OFF-01` | `P0` | Full Offline Local Ledger Mutation | Device 1 | `UNTESTED` | | |
| `TC-OFF-02` | `P0` | Offline Action Acceptance & Reconnect Convergence | Device 1 | `UNTESTED` | | |
| `TC-OFF-03` | `P1` | Edit-Beats-Delete Conflict Resolution | Device 1 | `UNTESTED` | | |

### Section 6: Daily Quota Exhaustion (`RESOURCE_EXHAUSTED`) Fault Tolerance
| ID | Priority | Test Title | Device | Result | Tester Notes & Evidence Link | Defect # |
| :--- | :---: | :--- | :--- | :---: | :--- | :---: |
| `TC-QTA-01` | `P0` | Android Client `RESOURCE_EXHAUSTED` Interception | Device 1 | `UNTESTED` | | |
| `TC-QTA-02` | `P0` | Local Functionality During Quota Rest Period | Device 1 | `UNTESTED` | | |
| `TC-QTA-03` | `P1` | Zero-PII Crashlytics Non-Fatal Logging | Device 1 | `UNTESTED` | | |

### Section 7: Permissions, Device Form Factors & Android OS Compatibility
| ID | Priority | Test Title | Device | Result | Tester Notes & Evidence Link | Defect # |
| :--- | :---: | :--- | :--- | :---: | :--- | :---: |
| `TC-ENV-01` | `P1` | Contacts Permission Denial Graceful Fallback | Device 1 | `UNTESTED` | | |
| `TC-ENV-02` | `P1` | Android 13+ Notification Permission Denial | Device 1 | `UNTESTED` | | |
| `TC-ENV-03` | `P0` | OS Matrix: Android 10, 13, 14, 15+ Compatibility | Multi | `UNTESTED` | | |
| `TC-ENV-04` | `P1` | Compact Phone Screen (360dp Width) | Device 2 | `UNTESTED` | | |
| `TC-ENV-05` | `P1` | Dark Mode & Theme Contrast | Device 1 | `UNTESTED` | | |
| `TC-ENV-06` | `P1` | Accessibility Font Scaling (1.3x to 2.0x) | Device 1 | `UNTESTED` | | |

### Section 8: Account & Complete Data Deletion (Play Store Compliance)
| ID | Priority | Test Title | Device | Result | Tester Notes & Evidence Link | Defect # |
| :--- | :---: | :--- | :--- | :---: | :--- | :---: |
| `TC-DEL-01` | `P0` | In-App "Delete My Account & Data" Workflow | Device 1 | `UNTESTED` | | |
| `TC-DEL-02` | `P0` | Post-Deletion State Verification | Device 1 | `UNTESTED` | | |
| `TC-DEL-03` | `P1` | Public Web Deletion Request Page | Browser | `UNTESTED` | | |

### Section 9: Upgrade Test: Version Migration & Data Retention
| ID | Priority | Test Title | Device | Result | Tester Notes & Evidence Link | Defect # |
| :--- | :---: | :--- | :--- | :---: | :--- | :---: |
| `TC-UPG-01` | `P0` | Room Schema Migration (v11/v12 to Current) | Device 1 | `UNTESTED` | | |

### Section 10: Production Minified (R8 Full Mode) Build Smoke Test
| ID | Priority | Test Title | Device | Result | Tester Notes & Evidence Link | Defect # |
| :--- | :---: | :--- | :--- | :---: | :--- | :---: |
| `TC-REL-01` | `P0` | Release AAB R8 Obfuscation & Mapping Verification | Workstation | `UNTESTED` | | |
| `TC-REL-02` | `P0` | Release Build Model Mapping & Reflection | Device 1 | `UNTESTED` | | |
| `TC-REL-03` | `P0` | App Check Play Integrity Provider in Release | Device 1 | `UNTESTED` | | |
| `TC-REL-04` | `P1` | About Screen Plan Mode & Version Display | Device 1 | `UNTESTED` | | |

---

## 4. Defect Log & Triage Record

Record any defects or unexpected anomalies uncovered during test execution below:

| Defect ID | Associated Test ID | Severity (`Critical` / `Major` / `Minor`) | Summary & Steps to Reproduce | Expected vs Actual Behavior | Resolution / Fix Commit | Status (`Open` / `Fixed`) |
| :--- | :---: | :---: | :--- | :--- | :--- | :---: |
| *DF-001* | `TC-LED-04` | *Major* | *Example: Partial payment calculation drift* | *Expected ₹749.25, saw ₹749.24* | *Fixed in commit abc1234* | *Fixed* |

---

## 5. Release Governance Sign-Off

All quality gates must be certified prior to initiating Google Play rollout.

### Sign-Off Criteria:
- [ ] 100% of `P0` (Blocker) test cases marked `PASS`.
- [ ] Zero unmitigated `Critical` or `Major` security or financial balance defects open.
- [ ] Signed Release App Bundle (`.aab`) verified with R8 obfuscation map embedded.
- [ ] Spark Plan constraints (zero Cloud Functions, zero Cloud Storage) confirmed intact.

### Signatures:

| Role | Signee Name | Status / Recommendation | Date | Signature |
| :--- | :--- | :--- | :--- | :--- |
| **QA Lead** | | [ ] Approved &nbsp;&nbsp; [ ] Rejected | | ____________________ |
| **Engineering Lead** | | [ ] Approved &nbsp;&nbsp; [ ] Rejected | | ____________________ |
| **Release Manager** | | [ ] Approved &nbsp;&nbsp; [ ] Rejected | | ____________________ |
