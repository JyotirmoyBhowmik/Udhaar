# Udhaar Spark Mode — Quota Exhausted Incident Runbook

## 1. Overview & Trigger Conditions
Udhaar Spark Mode operates strictly on Google Firebase's free Spark plan with **zero billing account required**.
The free quota limits are:
- **Cloud Firestore Reads**: 50,000 / day
- **Cloud Firestore Writes**: 20,000 / day
- **Cloud Firestore Deletes**: 20,000 / day
- **Daily Quota Reset Time**: **12:00 AM Pacific Time (00:00 PST / 13:30 IST)**

When a project reaches its daily read or write limit, Google Cloud Firestore rejects incoming client SDK operations with:
- Error code: `resource-exhausted`
- HTTP status: `429 Too Many Requests`
- Status message: `RESOURCE_EXHAUSTED: Quota exceeded.`

---

## 2. Zero Data Loss Architecture Invariant

> [!IMPORTANT]
> **No data is ever lost when quotas are exhausted.**
> Udhaar is architected as an **offline-first local system**:
> - On Android: **Room SQLite + SQLCipher** is the single source of truth. All creations, edits, repayments, and calculations commit immediately to local disk.
> - On Web: **IndexedDB Local Storage** holds the active ledger.
> - Outbox queues mutations locally. When Firestore is resting, the local outbox holds pending mutations with exponential backoff and automatically drains them as soon as the quota resets at midnight PST.

---

## 3. User Experience & Plain-Language Communications

When Firestore raises `RESOURCE_EXHAUSTED` or when the client write limiter hits the local daily cap (`max_client_daily_writes = 500`), the client intercepts the error and displays a non-alarming, honest banner:

### User-Facing Plain-Language Notice (All 5 Languages):
- **English**: *"Sync is resting until tomorrow to keep Udhaar free for everyone. Your records are safe on your device."*
- **Hindi (हिंदी)**: *"सभी के लिए उधार को निःशुल्क रखने के लिए सिंक कल तक विश्राम कर रहा है। आपके रिकॉर्ड आपके फ़ोन में सुरक्षित हैं।"*
- **Bengali (বাংলা)**: *"সবার জন্য ধার বিনামূল্যে রাখতে সিঙ্ক কাল পর্যন্ত বিশ্রাম নিচ্ছে। আপনার রেকর্ড আপনার ফোনেই সুরক্ষিত আছে।"*
- **Assamese (অসমীয়া)**: *"সকলোৰে বাবে ধাৰ বিনামূলীয়া ৰাখিবলৈ চিন্ক কালিলৈকে জিৰণি লৈছে। আপোনাৰ ৰেকৰ্ড আপোনাৰ ফোনত সুৰক্ষিত আছে।"*
- **Nepali (नेपाली)**: *"सबैका लागि उधार निःशुल्क राख्न सिङ्क भोलिसम्म विश्राम गर्दैछ। तपाईंको रेकर्डहरू तपाईंको यन्त्रमा सुरक्षित छन्।"*

The application remains fully usable for browsing records, adding new transactions, calculating interest/installments, and viewing party ledgers.

---

## 4. Emergency Administrative Mitigation Steps

If abnormal traffic or abuse causes premature quota exhaustion:

1. **Step 1: Inspect Usage Metrics**
   - Open [Firebase Console](https://console.firebase.google.com/) > Select Udhaar project > **Firestore Database** > **Usage**.
   - Check whether reads or writes caused the exhaustion.

2. **Step 2: Engage Remote Config Kill Switches**
   - Open Firebase Console > **Remote Config**.
   - Set `signup_enabled = false` to pause new account registrations.
   - Set `guest_links_enabled = false` to pause new capability web links.
   - Publish changes. Active apps will fetch updated flags and disable link generation and registration flows immediately.

3. **Step 3: Investigate Abusive IP / User**
   - In Firestore Console, check `guestLinks` or collection `actions` for excessive entries.
   - Delete abusive documents if necessary.

4. **Step 4: Optional Blaze Upgrade (If Legitimate Organic Growth)**
   - If community growth consistently exceeds 50,000 reads/day:
     - Link a Google Cloud Billing account in the Firebase Console (upgrading to Blaze plan).
     - Update Remote Config parameter `UDHAAR_TIER = "blaze"`.
     - Follow migration steps in [ADR 0008](file:///C:/Users/TEST/antigravity/Udhaar-2026-11-03-5112b/docs/adr/0008-spark-mode-zero-cost-tier.md).
