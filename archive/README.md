# Archived Legacy Artifacts (`archive/`)

This directory consolidates legacy pre-Spark directories that are **not deployed or used in production** by Udhaar v32.0 (Spark Mode):

- `archive/functions/` — Historical placeholder directory from an earlier server-side prototype.
- `archive/blaze_disabled/` — Archived legacy Blaze-tier Cloud Functions, Storage rules, and related test fixtures disabled when Udhaar standardized on the zero-cost Firebase Spark tier.
- `archive/admin/` — Legacy static admin console prototype for the archived Blaze API.

Production Udhaar v32.0 runs exclusively on local SQLCipher Room storage plus Firebase Spark (Authentication, Firestore Security Rules, Firestore Indexes, and Firebase Hosting) with zero Cloud Functions, zero Cloud KMS, and zero Cloud Storage buckets.
