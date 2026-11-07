# ADR-0002: Dual Firebase Hosting Origins and Region Pinning to asia-south1

- **Status**: Accepted
- **Date**: 2026-11-07
- **Deciders**: Principal Engineer, Security Lead, SRE Lead, Web Lead

---

## 1. Context and Problem Statement

Udhaar serves two distinct web audiences:
1. **Public Marketing, Information & Guest Pages**: Landing page, trust & privacy disclosures, updates, and the unauthenticated guest capability flow (`/u/`) where counterparties view records and confirm/dispute via short-lived tokens without an account.
2. **Authenticated Web Application**: The full-scale ledger portal where registered users manage their complete debt/credit accounts, people, payment histories, sensitive attachments, and credentials.

Hosting both workloads on a single origin creates security and operational hazards:
- Shared cookie scope could expose authenticated session cookies to unauthenticated guest flows.
- Strict Content-Security Policy (CSP) requirements for the full web application (e.g. `require-trusted-types-for 'script'`) may conflict with public landing page or preview requirements.
- An XSS vulnerability on the public marketing or guest pages would have an expanded blast radius into authenticated ledger data.

Furthermore, Udhaar's primary target demographic is located in India and Nepal. Network latency and data residency requirements (India Digital Personal Data Protection Act 2023) mandate strict geographic proximity.

## 2. Decision Drivers

- **Security & Blast Radius Reduction**: Strict origin isolation between public/guest flows and authenticated web app data.
- **Cookie Security**: Ensure `__session` cookies are restricted solely to the application domain and never sent to marketing/guest endpoints.
- **Low Latency & Compliance**: Server compute and database storage must reside within India (`asia-south1`, Mumbai).

## 3. Decision Outcome

Chosen Option: **Dual Firebase Hosting Origins with Geographic Pinning to `asia-south1`**.

### 1. Dual Origin Architecture

| Origin | Purpose | Target Hosting Site | Cookie / Auth Scope | CSP Policy |
| :--- | :--- | :--- | :--- | :--- |
| **`udhaar.jyotirmoyb.com`** | Marketing, Trust Center, App Links (`.well-known/assetlinks.json`), Guest Pages (`/u/**`) | `udhaar-marketing` (or default site) | No session cookies. Capability token in URL fragment (`#token`) only. | Strict CSP, no inline scripts/styles. |
| **`app.udhaar.jyotirmoyb.com`** | Full Web Ledger Application (PWA) | `udhaar-app` | `__session` (HttpOnly, Secure, SameSite=Strict). Session storage wipe on sign-out. | Ultra-strict CSP with `require-trusted-types-for 'script'`, `frame-ancestors 'none'`. |

### 2. API Routing & Rewrites
- Both Hosting sites rewrite `/api/**` to Cloud Functions v2.
- Firebase Hosting only preserves and forwards cookies named `__session`. All other cookies are stripped at the CDN edge.
- Hosting configuration on `app.udhaar.jyotirmoyb.com` forwards `__session` to the Functions runtime, enabling stateful session authentication while preventing `udhaar.jyotirmoyb.com` from accessing or writing `__session`.

### 3. Geographic Pinning to `asia-south1` (Mumbai)
- **Cloud Functions v2**: Pinned to region `asia-south1`.
- **Cloud Firestore**: Pinned to region `asia-south1`.
- **Cloud KMS**: Pinned to region `asia-south1`.
- **Cloud Storage**: Multi-region or regional bucket in `asia-south1`.

## 4. Consequences

### Positive
- Cross-Site Scripting (XSS) on marketing or guest pages cannot access the `__session` cookie or IndexedDB storage of the authenticated application.
- Sub-50ms network round-trip time (RTT) for API requests across major Indian metropolitan areas and telecoms (Jio, Airtel, Vi).
- Full compliance with Indian DPDP Act 2023 expectations regarding local data processing.

### Negative / Tradeoffs
- Requires managing two Firebase Hosting site configurations in `firebase.json` (`hosting: [ {...}, {...} ]`).
- Requires configuring DNS CNAME and SSL certificates for both domains.
