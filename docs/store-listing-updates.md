# Google Play Store Listing Updates & Localization Index (v32.0)

> **Single Source of Truth Notice:** Canonical store listing copy lives in [`docs/STORE_LISTING.md`](STORE_LISTING.md) and canonical Play Data Safety mappings live in [`docs/PLAY_DATA_SAFETY.md`](PLAY_DATA_SAFETY.md). This summary tracks v32.0 release localization and policy alignment.

## Data Safety Declarations (Summary — see `docs/PLAY_DATA_SAFETY.md`)
- **Offline-First Default (`BuildConfig.REQUIRE_CLOUD_VERIFICATION = false`):** Users can tap **Continue locally without cloud sync** on launch with zero sign-in or cloud transmission.
- **Verified Email Cloud Access:** Optional cloud verification, two-party chat, and guest links enforce `email_verified == true` across every Firestore path.
- **Chat Safety Controls:** Report, Block, and Mute for both users and threads, persisted locally and enforced in `firestore.rules`.
- **Zero Third-Party Ad Sharing:** Zero ad SDKs and zero commercial data sharing.

## 5-Language Localization Coverage
- **English (en-IN, en-US)** — `app/src/main/res/values/strings.xml`
- **Hindi (hi-IN)** — `app/src/main/res/values-hi/strings.xml`
- **Bengali (bn-IN, bn-BD)** — `app/src/main/res/values-bn/strings.xml`
- **Assamese (as-IN)** — `app/src/main/res/values-as/strings.xml`
- **Nepali (ne-NP)** — `app/src/main/res/values-ne/strings.xml`
