# Udhaar v21.0.0 — Google Play Store & App Store Listing Updates

**Version**: 21.0.0 (Build Code: 21000)  
**Target Platform**: Android (Google Play) & Web PWA  

---

## 1. Google Play Console: Data Safety Declarations

Udhaar Platform v2 strictly complies with Google Play's Data Safety policies. When updating the questionnaire in the Play Console, provide the following exact responses:

### 1.1 Overview
- **Does your app collect or share any of the required user data types?**: YES.
- **Is all of the user data collected by your app encrypted in transit?**: YES (HTTPS / TLS 1.3 enforced everywhere).
- **Do you provide a way for users to request that their data be deleted?**: YES (Direct in-app account deletion under Settings > Privacy, plus statutory web erasure request form at `https://udhaar.jyotirmoyb.com/privacy.html`).

### 1.2 Data Types Collected & Shared Table

| Data Type | Collected? | Shared? | Purpose | Optional / Required |
| :--- | :--- | :--- | :--- | :--- |
| **Personal Info > Phone Number** | Yes | No | Account management, WhatsApp OTP authentication | Required |
| **Personal Info > Email Address** | Yes | No | Account verification, security alerts, statements | Required |
| **Financial Info > Purchase History / Debt Records** | Yes | No | Core app functionality (personal credit/debt ledger) | Required |
| **Contacts > Contact List** | No (Local only) | No | Optional local-only contact picker for party name autocomplete; never uploaded to server | Optional |
| **Messages > In-App Chat** | Yes | No | Ledger negotiation between borrower and lender | Optional |
| **Device or Other IDs** | Yes | No | App Check fraud prevention, push notifications | Required |

---

## 2. Android App Permissions Rationale

1. `android.permission.READ_CONTACTS`:
   - **Rationale**: Used exclusively within the device UI to allow quick selection of a friend or business contact when creating a new udhaar transaction.
   - **Privacy Guard**: The contact list is processed entirely locally on the phone using Android's native Contact Picker contract. Raw contacts are **never transmitted, synchronized, or stored on Udhaar servers**.
2. `android.permission.POST_NOTIFICATIONS`:
   - **Rationale**: Dispatches polite, scheduled repayment reminders and incoming counterparty ledger proposals. Respects Quiet Hours (9:00 PM to 8:00 AM).
3. `android.permission.USE_BIOMETRIC`:
   - **Rationale**: Optional app biometric lock (Fingerprint / Face Unlock) to safeguard confidential ledger records when sharing a device.

---

## 3. Localized Release Notes (What's New in v21.0.0)

### English (en-IN, en-US)
```text
What's New in Udhaar v21.0.0:
• Introducing Optional Web & Cloud Sync: Access your personal ledger seamlessly on the web at app.udhaar.jyotirmoyb.com or keep using it 100% offline.
• Polite Reminders Engine: Gentle, friendly repayment reminders with customizable quiet hours and smart snooze options.
• End-to-End Privacy: Field-level envelope encryption with Cloud KMS and instant crypto-shredding on account deletion.
• WebAuthn Biometric Screen Lock: Lock your ledger with fingerprint or face authentication.
• Performance & Stability: Enhanced offline sync engine, lower battery consumption, and improved dark mode styling.
```

### Hindi (hi-IN)
```text
Udhaar v21.0.0 में नया क्या है:
• वैकल्पिक वेब और क्लाउड सिंक: app.udhaar.jyotirmoyb.com पर वेब से अपना हिसाब देखें या पहले की तरह 100% ऑफलाइन उपयोग करें।
• विनम्र स्मरण (Polite Reminders): बिना किसी परेशानी के शालीन और समय पर तगादा भेजें। शांत समय (Quiet Hours) और स्नूज़ की सुविधा।
• उच्च गोपनीयता (Data Protection): क्लाउड KMS द्वारा सुरक्षित एन्क्रिप्शन और डेटा डिलीट करने पर तुरंत स्थायी निष्कासन।
• बायोमेट्रिक स्क्रीन लॉक: फिंगरप्रिंट या फेस अनलॉक से अपनी डायरी को सुरक्षित रखें।
• प्रदर्शन और सुधार: बेहतर ऑफलाइन सिंक, कम बैटरी खपत और नया डार्क मोड।
```

### Bengali (bn-IN, bn-BD)
```text
Udhaar v21.0.0-এ নতুন কী আছে:
• ঐচ্ছিক ওয়েব ও ক্লাউড সিঙ্ক: app.udhaar.jyotirmoyb.com ওয়েবসাইটে গিয়ে আপনার হিসাব পরিচালনা করুন অথবা সম্পূর্ণ অফলাইনে ব্যবহার করুন।
• মার্জিত তাগাদা ব্যবস্থা (Polite Reminders): কোনো অসম্মান বা বিরক্তি ছাড়া ভদ্রভাবে মনে করিয়ে দেওয়ার বার্তা, শান্ত সময় এবং স্নুজ সুবিধা।
• সর্বোচ্চ নিরাপত্তা ও গোপনীয়তা: ক্লাউড KMS এনক্রিপশন এবং অ্যাকাউন্ট মুছলে সাথে সাথে স্থায়ী ক্রিপ্টো-শ্রেডিং।
• বায়োমেট্রিক স্ক্রিন লক: ফিঙ্গারপ্রিন্ট বা ফেস আনলক দিয়ে আপনার হিসাবের খাতা সুরক্ষিত রাখুন।
• গতি ও কার্যকারিতা বৃদ্ধি: দ্রুত অফলাইন সিঙ্ক এবং নতুন ডার্ক মোড ডিজাইন।
```

### Assamese (as-IN)
```text
Udhaar v21.0.0 ত নতুন কি আছে:
• ঐচ্ছিক ৱেব আৰু ক্লাউড ছিঙ্ক: app.udhaar.jyotirmoyb.com ত আপোনাৰ হিচাপ পৰিচালনা কৰক অথবা আগৰ দৰেই সম্পূৰ্ণ অফলাইনত ব্যৱহাৰ কৰক।
• নম্ৰ সোঁৱৰণি ব্যৱস্থা (Polite Reminders): বিনম্ৰভাৱে ধন ঘূৰাই পোৱাৰ সোঁৱৰণি, শান্ত সময় (Quiet Hours) আৰু স্নুজৰ সুবিধা।
• তথ্য সুৰক্ষা আৰু গোপনীয়তা: ক্লাউড KMS দ্বাৰা সুৰক্ষিত এনক্ৰিপশ্বন আৰু একাউণ্ট ডিলিট কৰিলে লগে লগে স্থায়ীভাৱে মচি পেলোৱাৰ সুবিধা।
• বায়’মেট্ৰিক স্ক্ৰীণ লক: ফিংগাৰপ্ৰিণ্ট বা ফেচ আনলকৰ দ্বাৰা আপোনাৰ হিচাপৰ বহী সুৰক্ষিত ৰাখক।
• গতি আৰু কাৰ্যক্ষমতা বৃদ্ধি: উন্নত অফলাইন ছিঙ্ক আৰু নতুন ডাৰ্ক ম’ড।
```

### Nepali (ne-NP)
```text
Udhaar v21.0.0 मा नयाँ के छ:
• ऐच्छिक वेब र क्लाउड सिंक: app.udhaar.jyotirmoyb.com मा वेबबाट आफ्नो हिसाब हेर्नुहोस् वा पहिले जस्तै १००% अफलाइन प्रयोग गर्नुहोस्।
• नम्र सम्झना प्रणाली (Polite Reminders): कुनै असजिलो विना शिष्टतापूर्वक सम्झाउने सन्देश, शान्त समय (Quiet Hours) र स्नुज सुविधा।
• उच्च गोपनीयता र डाटा सुरक्षा: क्लाउड KMS द्वारा सुरक्षित इन्क्रिप्सन र खाता मेटाउँदा तुरुन्तै स्थायी हटाउने व्यवस्था।
• बायोमेट्रिक स्क्रिन लक: फिंगरप्रिन्ट वा फेस अनलक मार्फत आफ्नो हिसाब सुरक्षित राख्नुहोस्।
• गति र स्थिरता सुधार: छिटो अफलाइन सिंक र नयाँ डार्क मोड।
```

---

## 4. Graphic Assets & Screenshot Specifications

- **App Icon**: 512 x 512 px 32-bit PNG with alpha.
- **Feature Graphic**: 1024 x 500 px JPG or PNG (no transparency), highlighting: *"Keep clear records of money you lend and borrow. 100% Private & Polite."*
- **Phone Screenshots**: Minimum 4 screenshots at $1080 \times 2400\text{ px}$:
  1. *Dashboard & Balance Summary*: Clear positive / negative balance badges in IBM Plex Mono.
  2. *Add Transaction*: Clean entry with integer paise calculations and payment mode tags.
  3. *Polite Reminder Sheet*: Tone selector and quiet hours badge preview.
  4. *Web & Cloud Sync*: Demonstrating seamless multi-device continuity.
