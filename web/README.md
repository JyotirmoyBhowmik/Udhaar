# Udhaar Web Hosting & Android App Links (`udhaar.jyotirmoyb.com`)

This directory contains the static web files served by Firebase Hosting for Udhaar (`com.jyotirmoyb.udhaar`):

- `web/.well-known/assetlinks.json` — Digital Asset Links manifest for Android App Links auto-verification (`https://udhaar.jyotirmoyb.com/.well-known/assetlinks.json`).
- `web/u/index.html` — Responsive read-only web viewer for shared Udhaar records and payment profiles (`https://udhaar.jyotirmoyb.com/u/...`).

---

## Where to Get Your Play App Signing SHA-256 Fingerprint

1. Open the **[Google Play Console](https://play.google.com/console/)** and select **Udhaar (`com.jyotirmoyb.udhaar`)**.
2. In the left navigation menu, go to **Test and release** → **Setup** → **App signing** (or **Release** → **Setup** → **App integrity** → **App signing** tab).
3. Under the **App signing key certificate** section (top card), locate the **SHA-256 certificate fingerprint** (formatted as 32 colon-separated hex pairs, e.g. `12:34:56:...:EF`).
4. Copy the **SHA-256 certificate fingerprint** from **App signing key certificate** (and optionally also copy the SHA-256 from **Upload key certificate** below it if you sideload upload-signed builds).

---

## Where to Paste the Fingerprint

Open `web/.well-known/assetlinks.json` and add your Play App Signing SHA-256 fingerprint as a second string inside the `"sha256_cert_fingerprints"` array:

```json
[
  {
    "relation": [
      "delegate_permission/common.handle_all_urls"
    ],
    "target": {
      "namespace": "android_app",
      "package_name": "com.jyotirmoyb.udhaar",
      "sha256_cert_fingerprints": [
        "FB:F9:7F:66:8F:61:1E:9B:49:CB:6D:A9:40:17:E8:1C:79:FE:9F:B3:57:B7:2B:15:DB:6D:D2:D9:35:9C:F0:EA",
        "PASTE_YOUR_PLAY_APP_SIGNING_SHA256_HERE"
      ]
    }
  }
]
```

Also add the same SHA-1 and SHA-256 fingerprints in the **[Firebase Console](https://console.firebase.google.com/)** under **Project settings** → **Your apps** → `com.jyotirmoyb.udhaar` → **Add fingerprint**.

---

## Deploying to Firebase Hosting & Connecting `udhaar.jyotirmoyb.com` via Cloudflare DNS

1. Deploy from the repository root:
   ```bash
   firebase deploy --only hosting,functions,firestore:rules
   ```
2. In **Firebase Console** → **Build** → **Hosting** → **Add custom domain**, enter:
   ```text
   udhaar.jyotirmoyb.com
   ```
3. In **Cloudflare Dashboard** → `jyotirmoyb.com` → **DNS** → **Records**, add the records shown by Firebase Hosting for `udhaar` with **Proxy status = DNS only (Grey Cloud)** so Firebase can provision SSL and Google's Digital Asset Links verifier can fetch `https://udhaar.jyotirmoyb.com/.well-known/assetlinks.json` directly without Cloudflare bot challenges.

