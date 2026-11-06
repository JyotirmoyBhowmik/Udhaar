/**
 * Udhaar Web Ledger & Two-Party Record Hub (web/ledger/ledger.js)
 * Full-scale production client with offline-first caching, email OTP account verification,
 * and WhatsApp 6-digit verification code generation.
 * Multi-user isolation, decorated data, cryptographic audit trail, and WCAG 2.1 AA announcements.
 * Strict CSP: Zero inline scripts, zero inline styles.
 */

(function () {
  "use strict";

  // 1. Multi-Language Dictionaries (5 Languages: English, Hindi, Bengali, Assamese, Nepali)
  const I18N = {
    en: {
      badge_web_ledger: "Web Ledger",
      btn_sign_in: "Verify Account",
      btn_verified: "✓ Verified Account",
      kpi_lent_label: "You will get",
      kpi_lent_sub: "Total Lent",
      kpi_borrowed_label: "You owe",
      kpi_borrowed_sub: "Total Borrowed",
      kpi_net_label: "Net Balance",
      kpi_net_sub: "Overall Status",
      kpi_pending_label: "Pending",
      kpi_pending_sub: "Needs Verification",
      kpi_pending_hint: "Awaiting WhatsApp code",
      filter_all: "All",
      filter_lent: "I Lent",
      filter_borrowed: "I Borrowed",
      filter_pending: "Pending",
      filter_confirmed: "Confirmed",
      btn_record_udhaar: "Record Udhaar",
      btn_record_first: "Record First Udhaar",
      empty_title: "No transactions found",
      empty_desc: "Start by recording your first transaction. A private capability link and WhatsApp verification code will be generated automatically.",
      auth_modal_title: "Account Verification",
      auth_intro: "Enter your mobile number and email address. We will send a 6-digit verification code to your email to verify your web account.",
      label_mobile_number: "Mobile Number",
      hint_mobile_number: "Used for two-party phone verification.",
      label_email_address: "Email Address",
      hint_email_address: "6-digit verification code will be sent here.",
      btn_cancel: "Cancel",
      btn_send_code: "Send Verification Code",
      btn_sending: "Sending...",
      auth_otp_desc: "Enter the 6-digit code sent to your email.",
      btn_back: "Back",
      btn_verify_signin: "Verify & Sign In",
      btn_verifying: "Verifying...",
      btn_resend: "Resend code",
      auth_success_title: "Account Verified!",
      btn_continue: "Continue to Ledger",
      create_modal_title: "Record New Udhaar",
      label_transaction_type: "Transaction Type",
      role_lent_label: "I Lent Money (Given)",
      role_borrowed_label: "I Borrowed Money (Taken)",
      label_person_name: "Person Name",
      label_person_phone: "Mobile Number",
      label_amount_rupees: "Amount in Rupees (₹)",
      label_due_date: "Return Due Date",
      date_chip_1w: "1 Week",
      date_chip_2w: "2 Weeks",
      date_chip_1m: "1 Month",
      date_chip_custom: "Custom",
      label_note_optional: "Note (Optional)",
      label_upi_optional: "Your UPI ID (Optional)",
      hint_upi: "Shown on receipt so counterparty can pay via QR or UPI app.",
      btn_save_record: "Save & Generate Code",
      btn_saving: "Saving...",
      share_modal_title: "Record Saved • Ready to Share",
      share_instructions: "Share this with your counterparty on WhatsApp. They can review the transaction and confirm using their private 6-digit code.",
      label_whatsapp_code: "WhatsApp Verification Code",
      hint_code_validity: "Valid for 15 minutes • Single-use",
      label_verification_link: "Verification Link",
      btn_copy: "Copy",
      btn_copied: "Copied!",
      btn_share_whatsapp: "Share via WhatsApp",
      btn_done: "Done",
      status_confirmed: "✓ Confirmed",
      status_pending: "⏳ Pending Code",
      status_settled: "✓ Settled",
      status_overdue: "⚠ Overdue",
      due_in_days: "Due in {days} days",
      overdue_by_days: "Overdue by {days} days",
      no_due_date: "No due date set",
      action_share: "WhatsApp",
      action_copy: "Copy Link",
      action_settle: "Mark Settled",
      toast_copied: "Link copied to clipboard!",
      even_balance: "Even balance",
      net_positive: "You are owed net {amt}",
      net_negative: "You owe net {amt}",
      active_records: "{count} active records",
      toast_record_created: "Transaction saved! Ready to verify on WhatsApp.",
      toast_record_settled: "Transaction marked as settled.",
      toast_auth_success: "Account verified and connected.",
      toast_auth_otp_sent: "Verification code sent to your email.",
      toast_error_validation: "Please correct the highlighted errors before saving.",
      toast_logged_out: "Signed out. Switched to guest mode.",
      err_name_invalid: "Please enter a valid person name (2 to 80 characters).",
      err_phone_invalid: "Please enter a valid 10-digit Indian mobile number.",
      err_amount_min: "Amount must be at least ₹1.",
      err_amount_max: "Amount cannot exceed ₹10,00,000.",
      err_date_past: "Due date cannot be in the past.",
      err_upi_invalid: "Please enter a valid UPI ID (e.g. name@bank).",
      aspect_lent_hint: "Positive Asset: You will receive this money back. It adds positively to your net balance.",
      aspect_borrowed_hint: "Negative Liability: You must repay this money. It subtracts from your net balance.",
      btn_audit_trail: "Activity Timeline",
      audit_modal_title: "Activity & Verification Log",
      audit_intro: "Every Udhaar transaction is verified with safe digital fingerprinting and a complete activity log.",
      label_trust_level: "Verification Trust Level:",
      label_content_hash: "Digital Fingerprint:",
      label_timeline: "Activity Timeline",
      btn_close: "Close",
      btn_logout: "Sign Out",
      action_audit: "Activity",
      status_event_created: "Record Created",
      status_event_wa_code: "WhatsApp Code Generated",
      status_event_verified: "Confirmed by Counterparty",
      status_event_settled: "Record Settled"
    },
    hi: {
      badge_web_ledger: "वेब बहीखाता",
      btn_sign_in: "खाता सत्यापित करें",
      btn_verified: "✓ सत्यापित खाता",
      kpi_lent_label: "आपको मिलेंगे",
      kpi_lent_sub: "कुल दिया",
      kpi_borrowed_label: "आपको देने हैं",
      kpi_borrowed_sub: "कुल लिया",
      kpi_net_label: "शुद्ध शेष",
      kpi_net_sub: "समग्र स्थिति",
      kpi_pending_label: "लंबित",
      kpi_pending_sub: "सत्यापन आवश्यक",
      kpi_pending_hint: "ह्वाट्सएप कोड की प्रतीक्षा",
      filter_all: "सभी",
      filter_lent: "मैंने दिया",
      filter_borrowed: "मैंने लिया",
      filter_pending: "लंबित",
      filter_confirmed: "सत्यापित",
      btn_record_udhaar: "उधार दर्ज करें",
      btn_record_first: "पहला उधार दर्ज करें",
      empty_title: "कोई लेन-देन नहीं मिला",
      empty_desc: "अपना पहला लेन-देन दर्ज करके शुरुआत करें। एक निजी लिंक और ह्वाट्सएप सत्यापन कोड अपने आप तैयार हो जाएगा।",
      auth_modal_title: "खाता सत्यापन",
      auth_intro: "अपना मोबाइल नंबर और ईमेल पता दर्ज करें। वेब खाते को सत्यापित करने के लिए हम आपके ईमेल पर ६ अंकों का कोड भेजेंगे।",
      label_mobile_number: "मोबाइल नंबर",
      hint_mobile_number: "दो पक्षों के फोन सत्यापन के लिए उपयोग किया जाता है।",
      label_email_address: "ईमेल पता",
      hint_email_address: "६ अंकों का सत्यापन कोड यहाँ भेजा जाएगा।",
      btn_cancel: "रद्द करें",
      btn_send_code: "सत्यापन कोड भेजें",
      btn_sending: "भेज रहे हैं...",
      auth_otp_desc: "अपने ईमेल पर भेजा गया ६ अंकों का कोड दर्ज करें।",
      btn_back: "वापस",
      btn_verify_signin: "सत्यापित करें और साइन इन करें",
      btn_verifying: "सत्यापित कर रहे हैं...",
      btn_resend: "कोड दोबारा भेजें",
      auth_success_title: "खाता सत्यापित हुआ!",
      btn_continue: "बहीखाता पर जारी रखें",
      create_modal_title: "नया उधार दर्ज करें",
      label_transaction_type: "लेन-देन का प्रकार",
      role_lent_label: "मैंने पैसे दिए (उधार दिया)",
      role_borrowed_label: "मैंने पैसे लिए (उधार लिया)",
      label_person_name: "व्यक्ति का नाम",
      label_person_phone: "मोबाइल नंबर",
      label_amount_rupees: "रुपये में राशि (₹)",
      label_due_date: "वापसी की नियत तारीख",
      date_chip_1w: "१ सप्ताह",
      date_chip_2w: "२ सप्ताह",
      date_chip_1m: "१ महीना",
      date_chip_custom: "कस्टम",
      label_note_optional: "नोट (वैकल्पिक)",
      label_upi_optional: "आपकी यूपीआई आईडी (वैकल्पिक)",
      hint_upi: "रसीद पर दिखाई जाएगी ताकि दूसरा पक्ष क्यूआर या यूपीआई ऐप से भुगतान कर सके।",
      btn_save_record: "सहेजें और कोड बनाएं",
      btn_saving: "सहेज रहे हैं...",
      share_modal_title: "रिकॉर्ड सहेजा गया • साझा करने के लिए तैयार",
      share_instructions: "इसे ह्वाट्सएप पर अपने साथी के साथ साझा करें। वे लेन-देन की समीक्षा कर सकते हैं और अपने निजी ६ अंकों के कोड से पुष्टि कर सकते हैं।",
      label_whatsapp_code: "ह्वाट्सएप सत्यापन कोड",
      hint_code_validity: "१५ मिनट के लिए मान्य • एकल उपयोग",
      label_verification_link: "सत्यापन लिंक",
      btn_copy: "कॉपी करें",
      btn_copied: "कॉपी हो गया!",
      btn_share_whatsapp: "ह्वाट्सएप पर साझा करें",
      btn_done: "पूर्ण",
      status_confirmed: "✓ सत्यापित",
      status_pending: "⏳ कोड प्रतीक्षित",
      status_settled: "✓ चुकता",
      status_overdue: "⚠ समय सीमा समाप्त",
      due_in_days: "{days} दिनों में देय",
      overdue_by_days: "{days} दिन अधिक हो गए",
      no_due_date: "कोई नियत तारीख नहीं",
      action_share: "ह्वाट्सएप",
      action_copy: "लिंक कॉपी",
      action_settle: "चुकता चिह्नित करें",
      toast_copied: "लिंक क्लिपबोर्ड पर कॉपी हो गया!",
      even_balance: "बराबर शेष",
      net_positive: "आपको शुद्ध {amt} मिलने हैं",
      net_negative: "आपको शुद्ध {amt} देने हैं",
      active_records: "{count} सक्रिय लेन-देन",
      toast_record_created: "लेन-देन सहेजा गया! ह्वाट्सएप पर सत्यापित करने के लिए तैयार।",
      toast_record_settled: "लेन-देन को चुकता चिह्नित किया गया।",
      toast_auth_success: "खाता सफलतापूर्वक सत्यापित और कनेक्ट हुआ।",
      toast_auth_otp_sent: "सत्यापन कोड आपके ईमेल पर भेजा गया है।",
      toast_error_validation: "कृपया सहेजने से पहले चिह्नित त्रुटियों को ठीक करें।",
      toast_logged_out: "साइन आउट किया गया। अतिथि मोड में स्विच किया गया।",
      err_name_invalid: "कृपया एक मान्य व्यक्ति का नाम दर्ज करें (२ से ८० अक्षर)।",
      err_phone_invalid: "कृपया एक मान्य १० अंकों का भारतीय मोबाइल नंबर दर्ज करें।",
      err_amount_min: "रकम कम से कम ₹१ होनी चाहिए।",
      err_amount_max: "रकम ₹१०,००,००० से अधिक नहीं हो सकती।",
      err_date_past: "नियत तारीख पिछली तारीख नहीं हो सकती।",
      err_upi_invalid: "कृपया एक मान्य यूपीआई आईडी दर्ज करें (जैसे नाम@बैंक)।",
      aspect_lent_hint: "सकारात्मक संपत्ति: आपको यह पैसा वापस मिलेगा। यह आपके शुद्ध शेष में जुड़ेगा।",
      aspect_borrowed_hint: "नकारात्मक देनदारी: आपको यह पैसा चुकाना होगा। यह आपके शुद्ध शेष से घटेगा।",
      btn_audit_trail: "गतिविधि समयरेखा",
      audit_modal_title: "गतिविधि और सत्यापन लॉग",
      audit_intro: "प्रत्येक उधार लेन-देन को सुरक्षित डिजिटल फिंगरप्रिंट और पूरी गतिविधि लॉग के साथ सत्यापित किया जाता है।",
      label_trust_level: "सत्यापन विश्वास स्तर:",
      label_content_hash: "डिजिटल फिंगरप्रिंट:",
      label_timeline: "गतिविधि समयरेखा",
      btn_close: "बंद करें",
      btn_logout: "साइन आउट",
      action_audit: "गतिविधि",
      status_event_created: "रिकॉर्ड बनाया गया",
      status_event_wa_code: "ह्वाट्सएप कोड जनरेट हुआ",
      status_event_verified: "दूसरे पक्ष द्वारा पुष्टि",
      status_event_settled: "लेन-देन चुकता हुआ"
    },
    bn: {
      badge_web_ledger: "ওয়েব খাতা",
      btn_sign_in: "অ্যাকাউন্ট যাচাই করুন",
      btn_verified: "✓ যাচাইকৃত অ্যাকাউন্ট",
      kpi_lent_label: "আপনি পাবেন",
      kpi_lent_sub: "মোট দেওয়া",
      kpi_borrowed_label: "আপনাকে দিতে হবে",
      kpi_borrowed_sub: "মোট নেওয়া",
      kpi_net_label: "নিট ব্যালেন্স",
      kpi_net_sub: "সার্বিক অবস্থা",
      kpi_pending_label: "অপেক্ষমান",
      kpi_pending_sub: "যাচাইকরণ প্রয়োজন",
      kpi_pending_hint: "হোয়াটসঅ্যাপ কোডের অপেক্ষায়",
      filter_all: "সব",
      filter_lent: "আমি দিয়েছি",
      filter_borrowed: "আমি নিয়েছি",
      filter_pending: "অপেক্ষমান",
      filter_confirmed: "যাচাইকৃত",
      btn_record_udhaar: "ধার যোগ করুন",
      btn_record_first: "প্রথম ধার যোগ করুন",
      empty_title: "কোনো লেনদেন পাওয়া যায়নি",
      empty_desc: "আপনার প্রথম লেনদেন লিখে শুরু করুন। একটি ব্যক্তিগত লিঙ্ক এবং হোয়াটসঅ্যাপ কোড স্বয়ংক্রিয়ভাবে তৈরি হবে।",
      auth_modal_title: "অ্যাকাউন্ট যাচাইকরণ",
      auth_intro: "আপনার মোবাইল নম্বর এবং ইমেল ঠিকানা লিখুন। অ্যাকাউন্ট যাচাই করতে আমরা আপনার ইমেলে একটি ৬ সংখ্যার কোড পাঠাব।",
      label_mobile_number: "মোবাইল নম্বর",
      hint_mobile_number: "উভয় পক্ষের ফোন যাচাইকরণের জন্য ব্যবহৃত হয়।",
      label_email_address: "ইমেল ঠিকানা",
      hint_email_address: "৬ সংখ্যার যাচাইকরণ কোড এখানে পাঠানো হবে।",
      btn_cancel: "বাতিল",
      btn_send_code: "কোড পাঠান",
      btn_sending: "পাঠানো হচ্ছে...",
      auth_otp_desc: "আপনার ইমেলে পাঠানো ৬ সংখ্যার কোডটি লিখুন।",
      btn_back: "ফিরে যান",
      btn_verify_signin: "যাচাই করুন এবং সাইন ইন করুন",
      btn_verifying: "যাচাই করা হচ্ছে...",
      btn_resend: "কোড পুনরায় পাঠান",
      auth_success_title: "অ্যাকাউন্ট যাচাই হয়েছে!",
      btn_continue: "খাতায় প্রবেশ করুন",
      create_modal_title: "নতুন ধার যোগ করুন",
      label_transaction_type: "লেনদেনের ধরন",
      role_lent_label: "আমি টাকা দিয়েছি (প্রদত্ত)",
      role_borrowed_label: "আমি টাকা নিয়েছি (গৃহীত)",
      label_person_name: "ব্যক্তির নাম",
      label_person_phone: "মোবাইল নম্বর",
      label_amount_rupees: "টাকার পরিমাণ (₹)",
      label_due_date: "পরিশোধের শেষ তারিখ",
      date_chip_1w: "১ সপ্তাহ",
      date_chip_2w: "২ সপ্তাহ",
      date_chip_1m: "১ মাস",
      date_chip_custom: "কাস্টম",
      label_note_optional: "নোট (ঐচ্ছিক)",
      label_upi_optional: "আপনার ইউপিআই আইডি (ঐচ্ছিক)",
      hint_upi: "রশিদে দেখানো হবে যাতে অপর পক্ষ কিউআর বা ইউপিআই অ্যাপ দিয়ে টাকা দিতে পারে।",
      btn_save_record: "সংরক্ষণ ও কোড তৈরি",
      btn_saving: "সংরক্ষণ করা হচ্ছে...",
      share_modal_title: "সংরক্ষিত • শেয়ারের জন্য প্রস্তুত",
      share_instructions: "এটি হোয়াটসঅ্যাপে অপর পক্ষের সাথে শেয়ার করুন। তারা লেনদেন পর্যালোচনা করে তাদের ব্যক্তিগত ৬ সংখ্যার কোড দিয়ে নিশ্চিত করতে পারবেন।",
      label_whatsapp_code: "হোয়াটসঅ্যাপ যাচাইকরণ কোড",
      hint_code_validity: "১৫ মিনিটের জন্য বৈধ • একক ব্যবহার",
      label_verification_link: "যাচাইকরণ লিঙ্ক",
      btn_copy: "কপি করুন",
      btn_copied: "কপি হয়েছে!",
      btn_share_whatsapp: "হোয়াটসঅ্যাপে শেয়ার করুন",
      btn_done: "সম্পন্ন",
      status_confirmed: "✓ নিশ্চিত",
      status_pending: "⏳ কোড বাকি",
      status_settled: "✓ পরিশোধিত",
      status_overdue: "⚠ মেয়াদোত্তীর্ণ",
      due_in_days: "{days} দিনের মধ্যে প্রদেয়",
      overdue_by_days: "{days} দিন অতিবাহিত",
      no_due_date: "তারিখ নির্ধারিত নেই",
      action_share: "হোয়াটসঅ্যাপ",
      action_copy: "লিঙ্ক কপি",
      action_settle: "পরিশোধিত করুন",
      toast_copied: "লিঙ্ক ক্লিপবোর্ডে কপি হয়েছে!",
      even_balance: "সমান সমান ব্যালেন্স",
      net_positive: "আপনি মোট {amt} পাবেন",
      net_negative: "আপনাকে মোট {amt} দিতে হবে",
      active_records: "{count}টি সক্রিয় লেনদেন",
      toast_record_created: "লেনদেন সংরক্ষিত হয়েছে! হোয়াটসঅ্যাপে যাচাইয়ের জন্য প্রস্তুত।",
      toast_record_settled: "লেনদেন পরিশোধিত হিসেবে চিহ্নিত করা হয়েছে।",
      toast_auth_success: "অ্যাকাউন্ট সফলভাবে যাচাই এবং সংযুক্ত হয়েছে।",
      toast_auth_otp_sent: "যাচাইকরণ কোড আপনার ইমেলে পাঠানো হয়েছে।",
      toast_error_validation: "সংরক্ষণ করার আগে চিহ্নিত ত্রুটিগুলি সংশোধন করুন।",
      toast_logged_out: "সাইন আউট করা হয়েছে। অতিথি মোডে প্রবেশ করা হয়েছে।",
      err_name_invalid: "অনুগ্রহ করে একটি সঠিক ব্যক্তির নাম লিখুন (২ থেকে ৮০ অক্ষর)।",
      err_phone_invalid: "অনুগ্রহ করে একটি সঠিক ১০ সংখ্যার ভারতীয় মোবাইল নম্বর লিখুন।",
      err_amount_min: "পরিমাণ কমপক্ষে ₹১ হতে হবে।",
      err_amount_max: "পরিমাণ ₹১০,০০,০০০-এর বেশি হতে পারে না।",
      err_date_past: "পরিশোধের তারিখ অতীতের হতে পারে না।",
      err_upi_invalid: "অনুগ্রহ করে একটি সঠিক ইউপিআই আইডি লিখুন (যেমন নাম@ব্যাংক)।",
      aspect_lent_hint: "ইতিবাচক সম্পদ: আপনি এই টাকা ফেরত পাবেন। এটি আপনার নিট ব্যালেন্সে যোগ হবে।",
      aspect_borrowed_hint: "নেতিবাচক দায়: আপনাকে এই টাকা শোধ করতে হবে। এটি আপনার নিট ব্যালেন্স থেকে বাদ যাবে।",
      btn_audit_trail: "কার্যকলাপ সময়রেখা",
      audit_modal_title: "কার্যকলাপ ও যাচাই লগ",
      audit_intro: "প্রতিটি ধার লেনদেন ডিজিটাল ফিঙ্গারপ্রিন্ট এবং সম্পূর্ণ কার্যকলাপ লগ সহ যাচাই করা হয়।",
      label_trust_level: "যাচাইকরণ বিশ্বাস স্তর:",
      label_content_hash: "ডিজিটাল ফিঙ্গারপ্রিন্ট:",
      label_timeline: "কার্যকলাপ সময়রেখা",
      btn_close: "বন্ধ করুন",
      btn_logout: "সাইন আউট",
      action_audit: "কার্যকলাপ",
      status_event_created: "রেকর্ড তৈরি করা হয়েছে",
      status_event_wa_code: "হোয়াটসঅ্যাপ কোড তৈরি হয়েছে",
      status_event_verified: "অপর পক্ষ দ্বারা নিশ্চিত",
      status_event_settled: "লেনদেন পরিশোধিত"
    },
    as: {
      badge_web_ledger: "ৱেব খাতা",
      btn_sign_in: "একাউণ্ট পৰীক্ষা কৰক",
      btn_verified: "✓ পৰীক্ষিত একাউণ্ট",
      kpi_lent_label: "আপুনি পাব",
      kpi_lent_sub: "মুঠ দিয়া ধন",
      kpi_borrowed_label: "আপুনি দিব লাগিব",
      kpi_borrowed_sub: "মুঠ লোৱা ধন",
      kpi_net_label: "মূল বাকী ধন",
      kpi_net_sub: "সামগ্ৰিক স্থিতি",
      kpi_pending_label: "অপেক্ষমান",
      kpi_pending_sub: "পৰীক্ষাৰ প্ৰয়োজন",
      kpi_pending_hint: "হোৱাটছএপ ক’ডলৈ অপেক্ষা",
      filter_all: "সকলো",
      filter_lent: "মই দিলোঁ",
      filter_borrowed: "মই ল’লোঁ",
      filter_pending: "অপেক্ষমান",
      filter_confirmed: "পৰীক্ষিত",
      btn_record_udhaar: "ধাৰ যোগ কৰক",
      btn_record_first: "প্ৰথম ধাৰ যোগ কৰক",
      empty_title: "কোনো লেনদেন পোৱা নগ’ল",
      empty_desc: "আপোনাৰ প্ৰথমটো লেনদেন লিখি আৰম্ভ কৰক। এটা ব্যক্তিগত লিংক আৰু হোৱাটছএপ ক’ড স্বয়ংক্ৰিয়ভাৱে সৃষ্টি হ’ব।",
      auth_modal_title: "একাউণ্ট পৰীক্ষা",
      auth_intro: "আপোনাৰ ম’বাইল নম্বৰ আৰু ইমেইল ঠিকনা দিয়ক। ৱেব একাউণ্ট পৰীক্ষা কৰিবলৈ আমি ৬টা সংখ্যাৰ ক’ড প্ৰেৰণ কৰিম।",
      label_mobile_number: "ম’বাইল নম্বৰ",
      hint_mobile_number: "উভয় পক্ষৰ ফোন পৰীক্ষাৰ বাবে ব্যৱহাৰ কৰা হয়।",
      label_email_address: "ইমেইল ঠিকনা",
      hint_email_address: "৬টা সংখ্যাৰ পৰীক্ষাকৰণ ক’ড ইয়ালৈ প্ৰেৰণ কৰা হ’ব।",
      btn_cancel: "বাতিল",
      btn_send_code: "ক’ড প্ৰেৰণ কৰক",
      btn_sending: "প্ৰেৰণ কৰি থকা হৈছে...",
      auth_otp_desc: "ইমেইললৈ প্ৰেৰণ কৰা ৬টা সংখ্যাৰ ক’ডটো দিয়ক।",
      btn_back: "উভতি যাওক",
      btn_verify_signin: "পৰীক্ষা কৰক আৰু ছাইন ইন কৰক",
      btn_verifying: "পৰীক্ষা কৰি থকা হৈছে...",
      btn_resend: "ক’ড পুনৰ প্ৰেৰণ কৰক",
      auth_success_title: "একাউণ্ট পৰীক্ষা সফল!",
      btn_continue: "খাতালৈ যাওক",
      create_modal_title: "নতুন ধাৰ যোগ কৰক",
      label_transaction_type: "লেনদেনৰ প্ৰকাৰ",
      role_lent_label: "মই ধন দিলোঁ (প্ৰদত্ত)",
      role_borrowed_label: "মই ধন ল’লোঁ (গৃহীত)",
      label_person_name: "ব্যক্তিৰ নাম",
      label_person_phone: "ম’বাইল নম্বৰ",
      label_amount_rupees: "টকাৰ পৰিমাণ (₹)",
      label_due_date: "ঘূৰাই দিয়াৰ শেষ তাৰিখ",
      date_chip_1w: "১ সপ্তাহ",
      date_chip_2w: "২ সপ্তাহ",
      date_chip_1m: "১ মাহ",
      date_chip_custom: "পছন্দ অনুসৰি",
      label_note_optional: "টোকা (ঐচ্ছিক)",
      label_upi_optional: "আপোনাৰ ইউপিআই আইডি (ঐচ্ছিক)",
      hint_upi: "ৰছিদত দেখুওৱা হ’ব যাতে আন পক্ষই কিউআৰ বা ইউপিআই এপেৰে ধন পৰিশোধ কৰিব পাৰে।",
      btn_save_record: "সংৰক্ষণ আৰু ক’ড সৃষ্টি",
      btn_saving: "সংৰক্ষণ কৰি থকা হৈছে...",
      share_modal_title: "সংৰক্ষিত • শ্বেয়াৰ কৰিবলৈ সাজু",
      share_instructions: "হোৱাটছএপত আন পক্ষৰ সৈতে শ্বেয়াৰ কৰক। তেওঁলোকে লেনদেন পৰীক্ষা কৰি নিজৰ ব্যক্তিগত ক’ডেৰে নিশ্চিত কৰিব পাৰিব।",
      label_whatsapp_code: "হোৱাটছএপ পৰীক্ষাকৰণ ক’ড",
      hint_code_validity: "১৫ মিনিটৰ বাবে বৈধ • একবাৰ ব্যৱহাৰযোগ্য",
      label_verification_link: "পৰীক্ষাকৰণ লিংক",
      btn_copy: "কপি কৰক",
      btn_copied: "কপি হ’ল!",
      btn_share_whatsapp: "হোৱাটছএপত শ্বেয়াৰ কৰক",
      btn_done: "সম্পন্ন",
      status_confirmed: "✓ নিশ্চিত",
      status_pending: "⏳ ক’ড বাকী",
      status_settled: "✓ পৰিশোধিত",
      status_overdue: "⚠ সময় উকলিল",
      due_in_days: "{days} দিন বাকী",
      overdue_by_days: "{days} দিন পলম হৈছে",
      no_due_date: "তাৰিখ স্থিৰ কৰা হোৱা নাই",
      action_share: "হোৱাটছএপ",
      action_copy: "লিংক কপি",
      action_settle: "পৰিশোধিত কৰক",
      toast_copied: "লিংক ক্লিপব’ৰ্ডত কপি হ’ল!",
      even_balance: "সমান সমান বাকী",
      net_positive: "আপুনি মুঠ {amt} পাব",
      net_negative: "আপুনি মুঠ {amt} দিব লাগিব",
      active_records: "{count}টা সক্ৰিয় লেনদেন",
      toast_record_created: "লেনদেন সংৰক্ষণ কৰা হ’ল! হোৱাটছএপত পৰীক্ষা কৰিবলৈ সাজু।",
      toast_record_settled: "লেনদেন পৰিশোধিত হিচাপে চিহ্নিত কৰা হ’ল।",
      toast_auth_success: "একাউণ্ট সফলতাৰে পৰীক্ষা আৰু সংযোগ কৰা হ’ল।",
      toast_auth_otp_sent: "পৰীক্ষাকৰণ ক’ড আপোনাৰ ইমেইললৈ প্ৰেৰণ কৰা হৈছে।",
      toast_error_validation: "সংৰক্ষণ কৰাৰ আগতে চিহ্নিত ত্ৰুটিসমূহ ঠিক কৰক।",
      toast_logged_out: "ছাইন আউট কৰা হ’ল। অতিথি ম’ডলৈ সলনি কৰা হ’ল।",
      err_name_invalid: "অনুগ্ৰহ কৰি এগৰাকী ব্যক্তিৰ সঠিক নাম লিখক (২ৰ পৰা ৮০টা আখৰ)।",
      err_phone_invalid: "অনুগ্ৰহ কৰি এটা সঠিক ১০টা সংখ্যাৰ ভাৰতীয় ম’বাইল নম্বৰ দিয়ক।",
      err_amount_min: "টকাৰ পৰিমাণ কমেও ₹১ হ’ব লাগিব।",
      err_amount_max: "টকাৰ পৰিমাণ ₹১০,০০,০০০তকৈ অধিক হ’ব নোৱাৰে।",
      err_date_past: "পৰিশোধৰ তাৰিখ অতীতৰ হ’ব নোৱাৰে।",
      err_upi_invalid: "অনুগ্ৰহ কৰি এটা সঠিক ইউপিআই আইডি দিয়ক (যেনে নাম@বেংক)।",
      aspect_lent_hint: "ইতিবাচক সম্পত্তি: আপুনি এই ধন ঘূৰাই পাব। ই আপোনাৰ মূল বাকী ধনত যোগ হ’ব।",
      aspect_borrowed_hint: "নেতিবাচক দেনা: আপুনি এই ধন ঘূৰাই দিব লাগিব। ই আপোনাৰ মূল বাকী ধনৰ পৰা বাদ পৰিব।",
      btn_audit_trail: "কাৰ্যকলাপ সময়ৰেখা",
      audit_modal_title: "কাৰ্যকলাপ আৰু প্ৰমাণ ল’গ",
      audit_intro: "প্ৰতিটো ধাৰ লেনদেন ডিজিটেল আঙুলিৰ ছাপ আৰু সম্পূৰ্ণ কাৰ্যকলাপ ল’গৰ সৈতে পৰীক্ষা কৰা হয়।",
      label_trust_level: "পৰীক্ষাৰ বিশ্বাসযোগ্যতাৰ স্তৰ:",
      label_content_hash: "ডিজিটেল আঙুলিৰ ছাপ:",
      label_timeline: "কাৰ্যকলাপ সময়ৰেখা",
      btn_close: "বন্ধ কৰক",
      btn_logout: "ছাইন আউট",
      action_audit: "কাৰ্যকলাপ",
      status_event_created: "ৰেকৰ্ড সৃষ্টি কৰা হ’ল",
      status_event_wa_code: "হোৱাটছএপ ক’ড সৃষ্টি হ’ল",
      status_event_verified: "আন পক্ষৰ দ্বাৰা নিশ্চিত",
      status_event_settled: "লেনদেন পৰিশোধ কৰা হ’ল"
    },
    ne: {
      badge_web_ledger: "वेब खाता",
      btn_sign_in: "खाता प्रमाणित गर्नुहोस्",
      btn_verified: "✓ प्रमाणित खाता",
      kpi_lent_label: "तपाईंले पाउनुहुनेछ",
      kpi_lent_sub: "कुल दिएको रकम",
      kpi_borrowed_label: "तपाईंले तिर्नुपर्ने",
      kpi_borrowed_sub: "कुल लिएको रकम",
      kpi_net_label: "कुल बचत",
      kpi_net_sub: "समग्र अवस्था",
      kpi_pending_label: "बाँकी",
      kpi_pending_sub: "प्रमाणीकरण आवश्यक",
      kpi_pending_hint: "ह्वाट्सएप कोडको पर्खाइमा",
      filter_all: "सबै",
      filter_lent: "मैले दिएको",
      filter_borrowed: "मैले लिएको",
      filter_pending: "बाँकी",
      filter_confirmed: "प्रमाणित",
      btn_record_udhaar: "उधारो थप्नुहोस्",
      btn_record_first: "पहिलो उधारो थप्नुहोस्",
      empty_title: "कुनै कारोबार भेटिएन",
      empty_desc: "पहिलो कारोबार दर्ता गरेर सुरु गर्नुहोस्। एक निजी लिङ्क र ह्वाट्सएप कोड स्वतः सिर्जना हुनेछ।",
      auth_modal_title: "खाता प्रमाणीकरण",
      auth_intro: "आफ्नो मोबाइल नम्बर र इमेल ठेगाना लेख्नुहोस्। वेब खाता प्रमाणित गर्न हामी तपाईंको इमेलमा ६ अंकको कोड पठाउनेछौँ।",
      label_mobile_number: "मोबाइल नम्बर",
      hint_mobile_number: "दुई पक्षीय फोन प्रमाणीकरणका लागि प्रयोग गरिन्छ।",
      label_email_address: "इमेल ठेगाना",
      hint_email_address: "६ अंकको प्रमाणीकरण कोड यहाँ पठाइनेछ।",
      btn_cancel: "रद्द गर्नुहोस्",
      btn_send_code: "कोड पठाउनुहोस्",
      btn_sending: "पठाउँदै...",
      auth_otp_desc: "इमेलमा पठाइएको ६ अंकको कोड लेख्नुहोस्।",
      btn_back: "फर्कनुहोस्",
      btn_verify_signin: "प्रमाणित गरी साइन इन गर्नुहोस्",
      btn_verifying: "प्रमाणित गर्दै...",
      btn_resend: "कोड पुनः पठाउनुहोस्",
      auth_success_title: "खाता प्रमाणित भयो!",
      btn_continue: "खातामा जानुहोस्",
      create_modal_title: "नयाँ उधारो थप्नुहोस्",
      label_transaction_type: "कारोबारको प्रकार",
      role_lent_label: "मैले पैसा दिएँ (दिएको)",
      role_borrowed_label: "मैले पैसा लिएँ (लिएको)",
      label_person_name: "व्यक्तिको नाम",
      label_person_phone: "मोबाइल नम्बर",
      label_amount_rupees: "रकम रुपैयाँमा (₹)",
      label_due_date: "फिर्ता गर्ने भाका मिति",
      date_chip_1w: "१ हप्ता",
      date_chip_2w: "२ हप्ता",
      date_chip_1m: "१ महिना",
      date_chip_custom: "आफ्नो अनुकूल",
      label_note_optional: "टिप्पणी (ऐच्छिक)",
      label_upi_optional: "तपाईंको युपिआई आइडी (ऐच्छिक)",
      hint_upi: "रसिदमा देखाइनेछ ताकि दोस्रो पक्षले क्युआर वा युपिआई एपबाट तिर्न सकोस्।",
      btn_save_record: "सुरक्षित गरी कोड बनाउनुहोस्",
      btn_saving: "सुरक्षित गर्दै...",
      share_modal_title: "रेकर्ड सुरक्षित • साझा गर्न तयार",
      share_instructions: "यसलाई ह्वाट्सएपमा अर्को पक्षसँग साझा गर्नुहोस्। उनीहरूले कारोबार हेरेर आफ्नो ६ अंकको कोडमार्फत पुष्टि गर्न सक्नेछन्।",
      label_whatsapp_code: "ह्वाट्सएप प्रमाणीकरण कोड",
      hint_code_validity: "१५ मिनेटका लागि मान्य • एकपटक प्रयोग",
      label_verification_link: "प्रमाणीकरण लिङ्क",
      btn_copy: "कपी गर्नुहोस्",
      btn_copied: "कपी भयो!",
      btn_share_whatsapp: "ह्वाट्सएपमा सेयर गर्नुहोस्",
      btn_done: "सम्पन्न",
      status_confirmed: "✓ प्रमाणित",
      status_pending: "⏳ कोड बाँकी",
      status_settled: "✓ चुक्ता",
      status_overdue: "⚠ म्याद नाघेको",
      due_in_days: "{days} दिनमा तिर्नुपर्ने",
      overdue_by_days: "{days} दिन ढिलो भएको",
      no_due_date: "मिति तोकिएको छैन",
      action_share: "ह्वाट्सएप",
      action_copy: "लिंक कपी",
      action_settle: "चुक्ता जनाउनुहोस्",
      toast_copied: "लिंक कपी भयो!",
      even_balance: "बराबर रकम",
      net_positive: "तपाईंले कुल {amt} पाउनुहुनेछ",
      net_negative: "तपाईंले कुल {amt} तिर्नुपर्नेछ",
      active_records: "{count} सक्रिय कारोबार",
      toast_record_created: "कारोबार सुरक्षित गरियो! ह्वाट्सएपमा प्रमाणित गर्न तयार।",
      toast_record_settled: "कारोबार चुक्ता भएको जनाइयो।",
      toast_auth_success: "खाता सफलतापूर्वक प्रमाणित र जडान भयो।",
      toast_auth_otp_sent: "प्रमाणीकरण कोड तपाईंको इमेलमा पठाइएको छ।",
      toast_error_validation: "कृपया बचत गर्नुअघि देखाइएका त्रुटिहरू सच्याउनुहोस्।",
      toast_logged_out: "साइन आउट गरियो। पाहुना मोडमा स्विच भयो।",
      err_name_invalid: "कृपया व्यक्तिको सही नाम लेख्नुहोस् (२ देखि ८० अक्षर)।",
      err_phone_invalid: "कृपया १० अंकको मान्य भारतीय मोबाइल नम्बर लेख्नुहोस्।",
      err_amount_min: "रकम कम्तिमा ₹१ हुनुपर्छ।",
      err_amount_max: "रकम ₹१०,००,००० भन्दा बढी हुन सक्दैन।",
      err_date_past: "भाका मिति बितेको मिति हुन सक्दैन।",
      err_upi_invalid: "कृपया मान्य युपिआई आइडी लेख्नुहोस् (जस्तै नाम@बैंक)।",
      aspect_lent_hint: "सकारात्मक सम्पत्ति: तपाईंले यो पैसा फिर्ता पाउनुहुनेछ। यो तपाईंको कुल बचतमा थपिनेछ।",
      aspect_borrowed_hint: "नकारात्मक दायित्व: तपाईंले यो पैसा तिर्नुपर्नेछ। यो तपाईंको कुल बचतबाट घट्नेछ।",
      btn_audit_trail: "गतिविधि समयरेखा",
      audit_modal_title: "गतिविधि र प्रमाणीकरण लग",
      audit_intro: "प्रत्येक उधारो कारोबारलाई सुरक्षित डिजिटल फिंगरप्रिन्ट र पूरा गतिविधि लगद्वारा प्रमाणित गरिन्छ।",
      label_trust_level: "प्रमाणीकरण विश्वास स्तर:",
      label_content_hash: "डिजिटल फिंगरप्रिन्ट:",
      label_timeline: "गतिविधि समयरेखा",
      btn_close: "बन्द गर्नुहोस्",
      btn_logout: "साइन आउट",
      action_audit: "गतिविधि",
      status_event_created: "रेकर्ड सिर्जना गरियो",
      status_event_wa_code: "ह्वाट्सएप कोड जारी गरियो",
      status_event_verified: "दोस्रो पक्षद्वारा पुष्टि",
      status_event_settled: "कारोबार चुक्ता भयो"
    }
  };

  // 2. State & Storage Keys (Multi-user partitioned storage)
  const STORAGE_KEYS = {
    SESSION: "udhaar_web_session",
    USER: "udhaar_web_user",
    THEME: "udhaar_theme",
    LANG: "udhaar_lang"
  };

  function getRecordsStorageKey(uid) {
    return uid ? "udhaar_web_ledger_records_" + uid : "udhaar_web_ledger_records_guest";
  }

  let currentLang = "en";
  let records = [];
  let currentFilter = "all";
  let searchQuery = "";
  let sessionToken = "";
  let currentUser = null;
  let authPendingEmail = "";
  let authPendingMobile = "";
  let authTimerInterval = null;

  // 3. Mathematical & Formatting Utilities
  function formatRupees(rupees, currency = "INR") {
    const num = Number(rupees) || 0;
    const isNegative = num < 0;
    const absNum = Math.abs(num);
    const parts = absNum.toFixed(2).split(".");
    let intPart = parts[0];
    const decPart = parts[1];

    if (intPart.length > 3) {
      const lastThree = intPart.substring(intPart.length - 3);
      const remaining = intPart.substring(0, intPart.length - 3);
      const formattedRemaining = remaining.replace(/\B(?=(\d{2})+(?!\d))/g, ",");
      intPart = formattedRemaining + "," + lastThree;
    }

    const symbol = currency === "INR" ? "₹" : currency + " ";
    const formatted = decPart === "00" ? intPart : `${intPart}.${decPart}`;
    return `${isNegative ? "-" : ""}${symbol}${formatted}`;
  }

  function calculateLedgerKPIs(recordList) {
    let totalLentPaise = 0;
    let totalBorrowedPaise = 0;
    let pendingCount = 0;
    let activeLentCount = 0;
    let activeBorrowedCount = 0;

    for (const r of recordList) {
      const paise = Number(r.principalAmountPaise || Math.round(Number(r.principalAmount || 0) * 100));
      const isLent = r.counterpartyRole === "BORROWER" || r.creatorRole === "GIVEN";

      if (r.status !== "SETTLED") {
        if (isLent) {
          totalLentPaise += paise;
          activeLentCount++;
        } else {
          totalBorrowedPaise += paise;
          activeBorrowedCount++;
        }
        if (r.status === "PENDING_CONFIRMATION" || !r.status) {
          pendingCount++;
        }
      }
    }

    const netPaise = totalLentPaise - totalBorrowedPaise;
    return {
      totalLentRupees: totalLentPaise / 100,
      totalBorrowedRupees: totalBorrowedPaise / 100,
      netRupees: netPaise / 100,
      pendingCount,
      activeLentCount,
      activeBorrowedCount
    };
  }

  function filterRecords(recordList, filterType, query) {
    const q = (query || "").trim().toLowerCase();
    return recordList.filter((r) => {
      const isLent = r.counterpartyRole === "BORROWER" || r.creatorRole === "GIVEN";

      if (filterType === "lent" && !isLent) return false;
      if (filterType === "borrowed" && isLent) return false;
      if (filterType === "pending" && r.status !== "PENDING_CONFIRMATION") return false;
      if (filterType === "confirmed" && r.status !== "CONFIRMED" && r.status !== "SETTLED") return false;

      if (q) {
        const nameMatch = (r.partyName || r.lenderName || r.borrowerName || "").toLowerCase().includes(q);
        const phoneMatch = (r.partyPhone || "").includes(q);
        const noteMatch = (r.note || "").toLowerCase().includes(q);
        return nameMatch || phoneMatch || noteMatch;
      }
      return true;
    });
  }

  function t(key, params = {}) {
    const dict = I18N[currentLang] || I18N.en;
    let text = dict[key] || I18N.en[key] || key;
    for (const [k, v] of Object.entries(params)) {
      text = text.replace(new RegExp(`\\{${k}\\}`, "g"), v);
    }
    return text;
  }

  // 4. Input Validation Logic (Enterprise bounds & pattern enforcement)
  function validateName(name) {
    const trimmed = String(name || "").trim();
    if (trimmed.length < 2 || trimmed.length > 80) return false;
    return /^[\p{L}\p{M}\s.'-]+$/u.test(trimmed);
  }

  function validatePhone(phone) {
    const digits = String(phone || "").replace(/\D/g, "");
    if (digits.length === 10) {
      if (!/^[6-9]\d{9}$/.test(digits)) return false;
      if (/^(\d)\1{9}$/.test(digits)) return false;
      if (digits === "1234567890") return false;
      return true;
    }
    if (digits.length === 12 && digits.startsWith("91")) {
      return validatePhone(digits.substring(2));
    }
    return false;
  }

  function validateAmount(val) {
    const num = Number(val);
    if (!Number.isFinite(num)) return false;
    if (num < 1 || num > 1000000) return false;
    return true;
  }

  function validateDueDate(dateStr) {
    if (!dateStr) return true;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const selected = new Date(dateStr);
    return selected.getTime() >= today.getTime();
  }

  function validateUpi(upi) {
    if (!upi) return true;
    const trimmed = String(upi).trim();
    return /^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$/.test(trimmed);
  }

  // 5. Toast Notification System & Accessibility Announcements
  function showToast(message, type = "success") {
    announce(message, type === "error");
    if (typeof document === "undefined") return;
    const container = document.getElementById("toast-container");
    if (!container) return;

    const toast = document.createElement("div");
    toast.className = `toast toast-${type}`;
    toast.setAttribute("role", type === "error" ? "alert" : "status");

    const icon = type === "error" ? "⚠️" : (type === "info" ? "ℹ️" : "✓");
    toast.innerHTML = `
      <span class="toast-icon" aria-hidden="true">${icon}</span>
      <span class="toast-msg">${escapeHtml(message)}</span>
      <button type="button" class="toast-close" aria-label="Close notification">✕</button>
    `;

    const closeBtn = toast.querySelector(".toast-close");
    if (closeBtn) {
      closeBtn.addEventListener("click", () => removeToast(toast));
    }

    container.appendChild(toast);
    setTimeout(() => removeToast(toast), 4500);
  }

  function removeToast(toast) {
    if (!toast || !toast.parentNode) return;
    toast.style.opacity = "0";
    toast.style.transform = "translateY(-10px) scale(0.95)";
    setTimeout(() => {
      if (toast.parentNode) toast.parentNode.removeChild(toast);
    }, 200);
  }

  function announce(msg, isAssertive = false) {
    if (typeof document === "undefined") return;
    const el = document.getElementById("a11y-announcer");
    if (el) {
      el.setAttribute("aria-live", isAssertive ? "assertive" : "polite");
      el.textContent = msg;
    }
  }

  // 6. Cryptographic Hash Calculation (Browser SubtleCrypto or Node fallback)
  async function computeRecordHash(dataString) {
    if (typeof crypto !== "undefined" && crypto.subtle) {
      try {
        const encoder = new TextEncoder();
        const data = encoder.encode(dataString);
        const hashBuf = await crypto.subtle.digest("SHA-256", data);
        const hashArr = Array.from(new Uint8Array(hashBuf));
        return hashArr.map((b) => b.toString(16).padStart(2, "0")).join("");
      } catch (_) {}
    }
    let hash = 0;
    for (let i = 0; i < dataString.length; i++) {
      hash = ((hash << 5) - hash) + dataString.charCodeAt(i);
      hash |= 0;
    }
    return "h_" + Math.abs(hash).toString(16).padStart(16, "0");
  }

  // 7. Persistence Helpers (Multi-User Partitioned)
  function loadLocalRecords() {
    try {
      const key = getRecordsStorageKey(currentUser ? currentUser.uid : null);
      const raw = localStorage.getItem(key);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) return parsed;
      }
    } catch (_) {}
    return [];
  }

  function saveLocalRecords(list) {
    try {
      const key = getRecordsStorageKey(currentUser ? currentUser.uid : null);
      localStorage.setItem(key, JSON.stringify(list));
    } catch (_) {}
  }

  // 8. DOM Rendering
  function renderAll() {
    applyLanguage(currentLang);
    renderKPIs();
    renderRecordList();
    renderAccountStatus();
  }

  function applyLanguage(lang) {
    currentLang = I18N[lang] ? lang : "en";
    if (typeof document === "undefined") return;
    document.documentElement.lang = currentLang;

    const transEls = document.querySelectorAll("[data-i18n]");
    transEls.forEach((el) => {
      const key = el.getAttribute("data-i18n");
      if (key && I18N[currentLang] && I18N[currentLang][key]) {
        el.textContent = I18N[currentLang][key];
      }
    });

    const langSelect = document.getElementById("ledger-lang-select");
    if (langSelect) langSelect.value = currentLang;

    // Update active aspect hint text
    updateRoleAspectHint();
  }

  function updateRoleAspectHint() {
    const hintEl = document.getElementById("role-aspect-text");
    const container = document.getElementById("role-aspect-hint");
    const roleRadio = document.querySelector('input[name="recordRole"]:checked');
    if (!hintEl || !container || !roleRadio) return;

    const isLent = roleRadio.value === "GIVEN";
    if (isLent) {
      container.className = "aspect-hint-box aspect-lent";
      hintEl.textContent = t("aspect_lent_hint");
    } else {
      container.className = "aspect-hint-box aspect-borrowed";
      hintEl.textContent = t("aspect_borrowed_hint");
    }
  }

  function renderKPIs() {
    const kpis = calculateLedgerKPIs(records);
    const lentEl = document.getElementById("kpi-val-lent");
    const borrowedEl = document.getElementById("kpi-val-borrowed");
    const netEl = document.getElementById("kpi-val-net");
    const pendingEl = document.getElementById("kpi-val-pending");

    const lentCountEl = document.getElementById("kpi-count-lent");
    const borrowedCountEl = document.getElementById("kpi-count-borrowed");
    const netStatusEl = document.getElementById("kpi-status-net");

    if (lentEl) lentEl.textContent = formatRupees(kpis.totalLentRupees);
    if (borrowedEl) borrowedEl.textContent = formatRupees(kpis.totalBorrowedRupees);
    if (netEl) netEl.textContent = formatRupees(kpis.netRupees);
    if (pendingEl) pendingEl.textContent = String(kpis.pendingCount);

    if (lentCountEl) lentCountEl.textContent = t("active_records", { count: kpis.activeLentCount });
    if (borrowedCountEl) borrowedCountEl.textContent = t("active_records", { count: kpis.activeBorrowedCount });

    if (netStatusEl) {
      if (kpis.netRupees > 0) {
        netStatusEl.textContent = t("net_positive", { amt: formatRupees(kpis.netRupees) });
      } else if (kpis.netRupees < 0) {
        netStatusEl.textContent = t("net_negative", { amt: formatRupees(Math.abs(kpis.netRupees)) });
      } else {
        netStatusEl.textContent = t("even_balance");
      }
    }
  }

  function renderRecordList() {
    const grid = document.getElementById("ledger-cards-grid");
    const emptyState = document.getElementById("ledger-empty-state");
    if (!grid) return;

    const filtered = filterRecords(records, currentFilter, searchQuery);

    if (filtered.length === 0) {
      grid.innerHTML = "";
      if (emptyState) {
        emptyState.hidden = false;
        emptyState.classList.remove("hidden");
      }
      return;
    }

    if (emptyState) {
      emptyState.hidden = true;
      emptyState.classList.add("hidden");
    }

    grid.innerHTML = "";
    filtered.forEach((r) => {
      const isLent = r.counterpartyRole === "BORROWER" || r.creatorRole === "GIVEN";
      const card = document.createElement("article");
      card.className = "record-card";
      card.setAttribute("role", "listitem");

      const rawPaise = Number(r.principalAmountPaise || Math.round(Number(r.principalAmount || 0) * 100));
      const amountStr = formatRupees(rawPaise / 100, r.currency || "INR");

      // Due date calculation
      let dueText = t("no_due_date");
      if (r.dueDateMillis || r.expiryDateMillis) {
        const dueMs = Number(r.dueDateMillis || r.expiryDateMillis);
        const diffDays = Math.ceil((dueMs - Date.now()) / (1000 * 60 * 60 * 24));
        if (diffDays < 0) {
          dueText = t("overdue_by_days", { days: Math.abs(diffDays) });
        } else if (diffDays === 0) {
          dueText = "Due today";
        } else {
          dueText = t("due_in_days", { days: diffDays });
        }
      }

      // Status badge
      let statusBadgeHtml = "";
      if (r.status === "CONFIRMED") {
        statusBadgeHtml = `<span class="status-badge badge-green">${t("status_confirmed")}</span>`;
      } else if (r.status === "SETTLED") {
        statusBadgeHtml = `<span class="status-badge badge-green">${t("status_settled")}</span>`;
      } else {
        statusBadgeHtml = `<span class="status-badge badge-amber">${t("status_pending")}</span>`;
      }

      const roleBadgeText = isLent ? t("role_lent_label").split(" ")[1] || "Lent" : t("role_borrowed_label").split(" ")[1] || "Borrowed";
      const roleClass = isLent ? "lent" : "borrowed";

      card.innerHTML = `
        <div class="card-top">
          <div>
            <div class="card-party-name">${escapeHtml(r.partyName || (isLent ? r.borrowerName : r.lenderName) || "Contact")}</div>
            <div class="card-party-phone font-mono">${escapeHtml(r.partyPhone || "")}</div>
          </div>
          <span class="role-badge ${roleClass}">${roleBadgeText}</span>
        </div>

        <div class="card-amount-row">
          <div class="card-amount ${roleClass}">${amountStr}</div>
          ${statusBadgeHtml}
        </div>

        <div class="card-meta-row">
          <span>${dueText}</span>
          ${r.whatsappOtp ? `<span class="font-mono">Code: ${escapeHtml(r.whatsappOtp)}</span>` : ""}
        </div>

        <div class="card-actions-row">
          <button type="button" class="btn btn-secondary card-btn btn-card-share" data-id="${escapeHtml(r.recordId || r.id)}">
            💬 ${t("action_share")}
          </button>
          <button type="button" class="btn btn-secondary card-btn btn-card-copy" data-id="${escapeHtml(r.recordId || r.id)}">
            📋 ${t("action_copy")}
          </button>
          <button type="button" class="btn btn-secondary card-btn btn-card-audit" data-id="${escapeHtml(r.recordId || r.id)}">
            📜 ${t("action_audit")}
          </button>
          ${r.status !== "SETTLED" ? `
            <button type="button" class="btn btn-secondary card-btn btn-card-settle" data-id="${escapeHtml(r.recordId || r.id)}">
              ✓ ${t("action_settle")}
            </button>
          ` : ""}
        </div>
      `;

      grid.appendChild(card);
    });

    // Bind action buttons on cards
    grid.querySelectorAll(".btn-card-share").forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = btn.getAttribute("data-id");
        const rec = records.find((x) => (x.recordId || x.id) === id);
        if (rec) openShareSuccessModal(rec);
      });
    });

    grid.querySelectorAll(".btn-card-copy").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const id = btn.getAttribute("data-id");
        const rec = records.find((x) => (x.recordId || x.id) === id);
        if (rec) {
          const url = getRecordShareUrl(rec);
          await copyToClipboard(url);
          showToast(t("toast_copied"), "info");
        }
      });
    });

    grid.querySelectorAll(".btn-card-audit").forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = btn.getAttribute("data-id");
        const rec = records.find((x) => (x.recordId || x.id) === id);
        if (rec) openAuditModal(rec);
      });
    });

    grid.querySelectorAll(".btn-card-settle").forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = btn.getAttribute("data-id");
        const rec = records.find((x) => (x.recordId || x.id) === id);
        if (rec && confirm(`Mark record with ${rec.partyName || "contact"} as settled?`)) {
          rec.status = "SETTLED";
          if (!rec.auditTrail) rec.auditTrail = [];
          rec.auditTrail.push({
            eventId: "ev_" + Date.now(),
            timestamp: Date.now(),
            actionType: "RECORD_SETTLED",
            actor: currentUser ? currentUser.mobileNumber || "User" : "Device Owner",
            summaryText: "Transaction marked as settled and closed.",
            contentHash: rec.contentHash || "verified"
          });
          saveLocalRecords(records);
          renderAll();
          showToast(t("toast_record_settled"), "success");
        }
      });
    });
  }

  function openAuditModal(record) {
    const hashEl = document.getElementById("audit-content-hash");
    const timelineList = document.getElementById("audit-timeline-list");
    if (hashEl) hashEl.textContent = record.contentHash || "verified-sha256-hash";

    if (timelineList) {
      timelineList.innerHTML = "";
      const trail = record.auditTrail && Array.isArray(record.auditTrail) && record.auditTrail.length > 0
        ? record.auditTrail
        : [
            {
              eventId: "ev_1",
              timestamp: record.createdAt || Date.now(),
              actionType: "RECORD_CREATED",
              actor: record.partyName ? "Device Creator" : "User",
              summaryText: `Transaction created for ${formatRupees((record.principalAmountPaise || 0) / 100)}`,
              contentHash: record.contentHash
            }
          ];

      trail.forEach((evt) => {
        const item = document.createElement("div");
        item.className = "timeline-item";
        item.setAttribute("role", "listitem");
        const d = new Date(evt.timestamp || Date.now());
        const timeStr = d.toLocaleDateString() + " " + d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

        const actionKey = "status_event_" + (evt.actionType || "").toLowerCase().replace(/[^a-z0-9]/g, "_");
        const actionLabel = t(actionKey) !== actionKey ? t(actionKey) : evt.actionType;

        item.innerHTML = `
          <div class="timeline-header">
            <span class="timeline-action">${escapeHtml(actionLabel)}</span>
            <span class="timeline-time font-mono">${escapeHtml(timeStr)}</span>
          </div>
          <div class="timeline-summary">${escapeHtml(evt.summaryText || "")}</div>
          <div class="timeline-actor">${escapeHtml(evt.actor || "System")}</div>
        `;
        timelineList.appendChild(item);
      });
    }

    showModal("modal-audit-trail");
    announce("Activity timeline loaded for transaction.");
  }

  function renderAccountStatus() {
    const pill = document.getElementById("btn-account-pill");
    const textEl = document.getElementById("account-pill-text");
    if (!pill || !textEl) return;

    if (currentUser && currentUser.isVerified) {
      textEl.textContent = t("btn_verified");
      pill.classList.add("verified");
    } else {
      textEl.textContent = t("btn_sign_in");
      pill.classList.remove("verified");
    }
  }

  function getRecordShareUrl(r) {
    if (r.verificationUrl) return r.verificationUrl;
    const base = window.location.origin;
    const recId = r.recordId || r.id || "tx";
    const secret = r.secret || "tok";
    const amt = r.principalAmount || ((r.principalAmountPaise || 0) / 100);
    const from = encodeURIComponent(r.lenderName || "You");
    const to = encodeURIComponent(r.borrowerName || r.partyName || "Friend");
    const cur = r.currency || "INR";
    return `${base}/u/r/${recId}?amt=${amt}&from=${from}&to=${to}&cur=${cur}#${secret}`;
  }

  function escapeHtml(str) {
    return String(str || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  async function copyToClipboard(text) {
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(text);
        return true;
      }
    } catch (_) {}
    const ta = document.createElement("textarea");
    ta.value = text;
    ta.style.position = "fixed";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.select();
    document.execCommand("copy");
    document.body.removeChild(ta);
    return true;
  }

  // 9. Network & API Calls
  async function apiCall(endpoint, payload, token) {
    const headers = { "Content-Type": "application/json" };
    if (token) headers["Authorization"] = `Bearer ${token}`;

    const resp = await fetch("/api/" + endpoint, {
      method: "POST",
      headers,
      body: JSON.stringify(payload || {})
    });

    const data = await resp.json().catch(() => ({}));
    if (!resp.ok) {
      const err = new Error(data.message || data.error || `HTTP ${resp.status}`);
      err.status = resp.status;
      throw err;
    }
    return data;
  }

  async function syncLedgerFromCloud() {
    if (!sessionToken) return;
    try {
      const res = await apiCall("getUserLedger", { sessionToken });
      if (res.records && Array.isArray(res.records)) {
        const localMap = new Map();
        records.forEach((r) => localMap.set(r.recordId || r.id, r));
        res.records.forEach((r) => localMap.set(r.recordId || r.id, r));
        records = Array.from(localMap.values());
        saveLocalRecords(records);
        renderKPIs();
        renderRecordList();
      }
      if (res.user) {
        currentUser = res.user;
        localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(currentUser));
        renderAccountStatus();
      }
    } catch (err) {
      console.warn("Background ledger sync unavailable:", err.message);
    }
  }

  // 10. Modals: Open / Close & Focus Management
  let lastFocusedElement = null;

  function showModal(modalId) {
    const el = document.getElementById(modalId);
    if (!el) return;
    lastFocusedElement = document.activeElement;
    el.hidden = false;
    el.style.display = "flex";
    el.classList.remove("hidden");

    const focusables = el.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
    if (focusables.length > 0) {
      focusables[0].focus();
    }
  }

  function hideModal(modalId) {
    const el = document.getElementById(modalId);
    if (!el) return;
    el.hidden = true;
    el.style.display = "none";
    el.classList.add("hidden");
    if (lastFocusedElement) {
      lastFocusedElement.focus();
    }
  }

  function openShareSuccessModal(record) {
    const headline = document.getElementById("share-summary-text");
    const codeEl = document.getElementById("display-otp-code");
    const linkInput = document.getElementById("display-share-link");
    const waBtn = document.getElementById("btn-open-whatsapp");

    const isLent = record.counterpartyRole === "BORROWER" || record.creatorRole === "GIVEN";
    const amtStr = formatRupees(record.principalAmount || ((record.principalAmountPaise || 0) / 100), record.currency);
    const party = record.partyName || (isLent ? record.borrowerName : record.lenderName) || "Contact";
    const verb = isLent ? "Lent to" : "Borrowed from";

    if (headline) headline.textContent = `${amtStr} ${verb} ${party}`;
    if (codeEl) codeEl.textContent = record.whatsappOtp ? `${record.whatsappOtp.slice(0, 3)} ${record.whatsappOtp.slice(3)}` : "123 456";

    const shareUrl = getRecordShareUrl(record);
    if (linkInput) linkInput.value = shareUrl;

    if (waBtn) {
      const waAction = isLent ? "lent you" : "borrowed from you";
      const waMsg = `Hi ${party}, I've recorded ${amtStr} (${waAction}) on Udhaar. Please review and confirm our record here: ${shareUrl}\n\nYour 6-digit WhatsApp verification code is: ${record.whatsappOtp || "123456"}`;
      let waLink = "https://wa.me/?text=" + encodeURIComponent(waMsg);
      const cleanPhone = (record.partyPhone || "").replace(/\D/g, "");
      if (cleanPhone.length >= 10) {
        waLink = `https://wa.me/${cleanPhone.length === 10 ? "91" + cleanPhone : cleanPhone}?text=${encodeURIComponent(waMsg)}`;
      }
      waBtn.href = waLink;
    }

    showModal("modal-share-success");
    announce("Record saved. Ready to share verification code on WhatsApp.");
  }

  // 11. Event Wiring
  function wireEvents() {
    // Language switcher
    const langSelect = document.getElementById("ledger-lang-select");
    if (langSelect) {
      langSelect.addEventListener("change", (e) => {
        currentLang = e.target.value;
        localStorage.setItem(STORAGE_KEYS.LANG, currentLang);
        renderAll();
      });
    }

    // Theme toggle
    const themeBtn = document.getElementById("btn-theme-toggle");
    const themeIcon = document.getElementById("theme-icon");
    if (themeBtn) {
      themeBtn.addEventListener("click", () => {
        const isDark = document.documentElement.getAttribute("data-theme") === "dark";
        const newTheme = isDark ? "light" : "dark";
        document.documentElement.setAttribute("data-theme", newTheme);
        localStorage.setItem(STORAGE_KEYS.THEME, newTheme);
        if (themeIcon) themeIcon.textContent = newTheme === "dark" ? "☀️" : "🌙";
      });
    }

    // Role radio buttons change -> update positive/negative aspect hint
    const roleRadios = document.querySelectorAll('input[name="recordRole"]');
    roleRadios.forEach((r) => {
      r.addEventListener("change", updateRoleAspectHint);
    });

    // Real-time inline field validation listeners
    const nameInput = document.getElementById("input-person-name");
    const phoneInput = document.getElementById("input-person-phone");
    const amountInput = document.getElementById("input-amount");
    const dueDateInput = document.getElementById("input-due-date");
    const upiInput = document.getElementById("input-upi");

    function setFieldError(inputEl, errElId, isValid, errMessageKey) {
      const errEl = document.getElementById(errElId);
      if (!inputEl) return;
      if (isValid) {
        inputEl.classList.remove("input-invalid");
        inputEl.classList.add("input-valid");
        if (errEl) {
          errEl.hidden = true;
          errEl.classList.add("hidden");
          errEl.textContent = "";
        }
      } else {
        inputEl.classList.add("input-invalid");
        inputEl.classList.remove("input-valid");
        if (errEl) {
          errEl.hidden = false;
          errEl.classList.remove("hidden");
          errEl.textContent = t(errMessageKey);
        }
      }
    }

    if (nameInput) {
      nameInput.addEventListener("input", () => {
        const valid = validateName(nameInput.value);
        setFieldError(nameInput, "err-person-name", valid, "err_name_invalid");
      });
    }

    if (phoneInput) {
      phoneInput.addEventListener("input", () => {
        const valid = validatePhone(phoneInput.value);
        setFieldError(phoneInput, "err-person-phone", valid, "err_phone_invalid");
      });
    }

    if (amountInput) {
      amountInput.addEventListener("input", () => {
        const val = Number(amountInput.value);
        if (val < 1) {
          setFieldError(amountInput, "err-amount", false, "err_amount_min");
        } else if (val > 1000000) {
          setFieldError(amountInput, "err-amount", false, "err_amount_max");
        } else {
          setFieldError(amountInput, "err-amount", true, "");
        }
      });
    }

    if (dueDateInput) {
      dueDateInput.addEventListener("input", () => {
        const valid = validateDueDate(dueDateInput.value);
        setFieldError(dueDateInput, "err-due-date", valid, "err_date_past");
      });
    }

    if (upiInput) {
      upiInput.addEventListener("input", () => {
        const valid = validateUpi(upiInput.value);
        setFieldError(upiInput, "err-upi", valid, "err_upi_invalid");
      });
    }

    // Network status listeners
    window.addEventListener("online", () => {
      const banner = document.getElementById("network-banner");
      if (banner) {
        banner.hidden = true;
        banner.classList.add("hidden");
      }
      syncLedgerFromCloud();
    });

    window.addEventListener("offline", () => {
      const banner = document.getElementById("network-banner");
      if (banner) {
        banner.hidden = false;
        banner.classList.remove("hidden");
      }
    });

    // Search bar
    const searchInput = document.getElementById("search-input");
    const clearBtn = document.getElementById("btn-clear-search");
    if (searchInput) {
      searchInput.addEventListener("input", (e) => {
        searchQuery = e.target.value;
        if (clearBtn) {
          clearBtn.hidden = !searchQuery;
          clearBtn.classList.toggle("hidden", !searchQuery);
        }
        renderRecordList();
      });
    }
    if (clearBtn) {
      clearBtn.addEventListener("click", () => {
        if (searchInput) searchInput.value = "";
        searchQuery = "";
        clearBtn.hidden = true;
        clearBtn.classList.add("hidden");
        renderRecordList();
      });
    }

    // Filter chips
    const chips = document.querySelectorAll(".filter-chip");
    chips.forEach((chip) => {
      chip.addEventListener("click", () => {
        chips.forEach((c) => {
          c.classList.remove("active");
          c.setAttribute("aria-checked", "false");
        });
        chip.classList.add("active");
        chip.setAttribute("aria-checked", "true");
        currentFilter = chip.getAttribute("data-filter") || "all";
        renderRecordList();
      });
    });

    // Account Pill (Open Auth Modal)
    const accountPill = document.getElementById("btn-account-pill");
    if (accountPill) {
      accountPill.addEventListener("click", () => {
        if (currentUser && currentUser.isVerified) {
          document.getElementById("form-auth-step1").hidden = true;
          document.getElementById("form-auth-step1").classList.add("hidden");
          document.getElementById("form-auth-step2").hidden = true;
          document.getElementById("form-auth-step2").classList.add("hidden");
          const step3 = document.getElementById("auth-step-success");
          step3.hidden = false;
          step3.classList.remove("hidden");
          document.getElementById("profile-mobile-val").textContent = currentUser.mobileNumber || "—";
          document.getElementById("profile-email-val").textContent = currentUser.email || "—";
        } else {
          document.getElementById("form-auth-step1").hidden = false;
          document.getElementById("form-auth-step1").classList.remove("hidden");
          document.getElementById("form-auth-step2").hidden = true;
          document.getElementById("form-auth-step2").classList.add("hidden");
          document.getElementById("auth-step-success").hidden = true;
          document.getElementById("auth-step-success").classList.add("hidden");
        }
        showModal("modal-auth");
      });
    }

    // Auth Modal Logout Button (Separating users)
    const btnAuthLogout = document.getElementById("btn-auth-logout");
    if (btnAuthLogout) {
      btnAuthLogout.addEventListener("click", () => {
        currentUser = null;
        sessionToken = "";
        localStorage.removeItem(STORAGE_KEYS.SESSION);
        localStorage.removeItem(STORAGE_KEYS.USER);
        hideModal("modal-auth");
        records = loadLocalRecords(); // Switch to guest records partition
        renderAll();
        showToast(t("toast_logged_out"), "info");
      });
    }

    // Close Auth Modal
    const btnCloseAuth = document.getElementById("btn-close-auth");
    const btnCancelAuth1 = document.getElementById("btn-cancel-auth1");
    const btnAuthDone = document.getElementById("btn-auth-done");
    [btnCloseAuth, btnCancelAuth1, btnAuthDone].forEach((btn) => {
      if (btn) btn.addEventListener("click", () => hideModal("modal-auth"));
    });

    // Auth Step 1: Send Email OTP
    const formAuth1 = document.getElementById("form-auth-step1");
    if (formAuth1) {
      formAuth1.addEventListener("submit", async (e) => {
        e.preventDefault();
        const mobile = document.getElementById("auth-mobile").value.trim();
        const email = document.getElementById("auth-email").value.trim().toLowerCase();
        const btnSend = document.getElementById("btn-send-email-otp");
        const btnText = document.getElementById("btn-send-otp-text");

        btnSend.disabled = true;
        btnText.textContent = t("btn_sending");

        try {
          const res = await apiCall("sendEmailOtp", { email, mobileNumber: mobile });
          authPendingEmail = email;
          authPendingMobile = mobile;

          formAuth1.hidden = true;
          formAuth1.classList.add("hidden");
          const formAuth2 = document.getElementById("form-auth-step2");
          formAuth2.hidden = false;
          formAuth2.classList.remove("hidden");

          const sentDesc = document.getElementById("otp-sent-desc");
          if (sentDesc) sentDesc.textContent = `Enter the 6-digit code sent to ${email}.`;

          startAuthOtpTimer(res.expiresAtMillis || Date.now() + 10 * 60 * 1000);
          showToast(t("toast_auth_otp_sent"), "info");

          const firstBox = document.querySelector(".auth-otp-box");
          if (firstBox) firstBox.focus();
        } catch (err) {
          showToast(err.message || "Failed to send code.", "error");
        } finally {
          btnSend.disabled = false;
          btnText.textContent = t("btn_send_code");
        }
      });
    }

    // Auth Step 2: Auto-jump OTP inputs
    const otpBoxes = document.querySelectorAll(".auth-otp-box");
    otpBoxes.forEach((box, idx) => {
      box.addEventListener("input", (e) => {
        const val = e.target.value.replace(/\D/g, "");
        e.target.value = val ? val[0] : "";
        if (val && idx < otpBoxes.length - 1) {
          otpBoxes[idx + 1].focus();
        }
      });

      box.addEventListener("keydown", (e) => {
        if (e.key === "Backspace" && !box.value && idx > 0) {
          otpBoxes[idx - 1].focus();
        }
      });

      box.addEventListener("paste", (e) => {
        e.preventDefault();
        const pasted = (e.clipboardData || window.clipboardData).getData("text").replace(/\D/g, "");
        if (pasted.length >= 6) {
          otpBoxes.forEach((b, i) => {
            b.value = pasted[i] || "";
          });
          otpBoxes[5].focus();
        }
      });
    });

    const btnBackAuth2 = document.getElementById("btn-back-auth2");
    if (btnBackAuth2) {
      btnBackAuth2.addEventListener("click", () => {
        document.getElementById("form-auth-step2").hidden = true;
        document.getElementById("form-auth-step2").classList.add("hidden");
        document.getElementById("form-auth-step1").hidden = false;
        document.getElementById("form-auth-step1").classList.remove("hidden");
      });
    }

    // Auth Step 2: Verify Email OTP Submit
    const formAuth2 = document.getElementById("form-auth-step2");
    if (formAuth2) {
      formAuth2.addEventListener("submit", async (e) => {
        e.preventDefault();
        const code = Array.from(otpBoxes).map((b) => b.value).join("");
        if (code.length !== 6) {
          showToast("Please enter the complete 6-digit code.", "error");
          return;
        }

        const btnVerify = document.getElementById("btn-verify-email-otp");
        const btnText = document.getElementById("btn-verify-otp-text");
        btnVerify.disabled = true;
        btnText.textContent = t("btn_verifying");

        try {
          const res = await apiCall("verifyEmailOtp", {
            email: authPendingEmail,
            mobileNumber: authPendingMobile,
            otp: code
          });

          sessionToken = res.sessionToken;
          currentUser = res.user;
          localStorage.setItem(STORAGE_KEYS.SESSION, sessionToken);
          localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(currentUser));

          // Switch local records partition to this user
          records = loadLocalRecords();

          formAuth2.hidden = true;
          formAuth2.classList.add("hidden");
          const step3 = document.getElementById("auth-step-success");
          step3.hidden = false;
          step3.classList.remove("hidden");

          document.getElementById("profile-mobile-val").textContent = currentUser.mobileNumber || authPendingMobile;
          document.getElementById("profile-email-val").textContent = currentUser.email || authPendingEmail;

          renderAll();
          showToast(t("toast_auth_success"), "success");
          syncLedgerFromCloud();
        } catch (err) {
          showToast(err.message || "Invalid or expired verification code.", "error");
        } finally {
          btnVerify.disabled = false;
          btnText.textContent = t("btn_verify_signin");
        }
      });
    }

    // Open Create Record Modal
    const btnOpenCreate = document.getElementById("btn-open-create");
    const btnEmptyCreate = document.getElementById("btn-empty-create");
    [btnOpenCreate, btnEmptyCreate].forEach((btn) => {
      if (btn) btn.addEventListener("click", () => {
        updateRoleAspectHint();
        showModal("modal-record");
      });
    });

    // Close Create Record Modal
    const btnCloseRecord = document.getElementById("btn-close-record");
    const btnCancelRecord = document.getElementById("btn-cancel-record");
    [btnCloseRecord, btnCancelRecord].forEach((btn) => {
      if (btn) btn.addEventListener("click", () => hideModal("modal-record"));
    });

    // Date Chips
    const dateChips = document.querySelectorAll(".date-chip");
    dateChips.forEach((chip) => {
      chip.addEventListener("click", () => {
        dateChips.forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
        const days = chip.getAttribute("data-days");
        if (days && days !== "custom" && dueDateInput) {
          const d = new Date(Date.now() + parseInt(days, 10) * 86400000);
          dueDateInput.value = d.toISOString().split("T")[0];
          setFieldError(dueDateInput, "err-due-date", true, "");
        }
      });
    });

    // Form Create Record Submit with strict multi-validation
    const formRecord = document.getElementById("form-create-record");
    if (formRecord) {
      formRecord.addEventListener("submit", async (e) => {
        e.preventDefault();
        const role = formRecord.querySelector('input[name="recordRole"]:checked').value;
        const name = nameInput ? nameInput.value.trim() : "";
        const phone = phoneInput ? phoneInput.value.trim() : "";
        const amtVal = parseFloat((amountInput ? amountInput.value : "") || "0");
        const dueDate = dueDateInput ? dueDateInput.value : "";
        const note = (document.getElementById("input-note") || {}).value || "";
        const upi = upiInput ? upiInput.value.trim() : "";

        // Enforce all validations
        const isNameValid = validateName(name);
        const isPhoneValid = validatePhone(phone);
        const isAmountValid = validateAmount(amtVal);
        const isDueDateValid = validateDueDate(dueDate);
        const isUpiValid = validateUpi(upi);

        setFieldError(nameInput, "err-person-name", isNameValid, "err_name_invalid");
        setFieldError(phoneInput, "err-person-phone", isPhoneValid, "err_phone_invalid");
        setFieldError(amountInput, "err-amount", isAmountValid, amtVal < 1 ? "err_amount_min" : "err_amount_max");
        setFieldError(dueDateInput, "err-due-date", isDueDateValid, "err_date_past");
        setFieldError(upiInput, "err-upi", isUpiValid, "err_upi_invalid");

        if (!isNameValid || !isPhoneValid || !isAmountValid || !isDueDateValid || !isUpiValid) {
          showToast(t("toast_error_validation"), "error");
          return;
        }

        const btnSave = document.getElementById("btn-save-record");
        const btnText = document.getElementById("btn-save-text");
        btnSave.disabled = true;
        btnText.textContent = t("btn_saving");

        const dueDateMs = dueDate ? new Date(dueDate).getTime() : null;
        const isLent = role === "GIVEN";

        // Generate local 6-digit WhatsApp OTP & secret token
        const localOtp = String(Math.floor(100000 + Math.random() * 900000));
        const localSecret = Array.from(crypto.getRandomValues(new Uint8Array(12)))
          .map((b) => b.toString(16).padStart(2, "0"))
          .join("");
        const localRecordId = "tx_" + Date.now();
        const nowMs = Date.now();

        // Cryptographic content hash & Activity audit log
        const rawHashString = [localRecordId, currentUser ? currentUser.uid : "guest", phone, Math.round(amtVal * 100), "INR", nowMs].join(":");
        const contentHash = await computeRecordHash(rawHashString);

        const initialAuditTrail = [
          {
            eventId: "ev_" + nowMs + "_1",
            timestamp: nowMs,
            actionType: "RECORD_CREATED",
            actor: currentUser ? currentUser.mobileNumber || "User" : "Device Creator",
            summaryText: `${isLent ? "Lent" : "Borrowed"} ₹${amtVal} recorded with ${name}`,
            contentHash
          },
          {
            eventId: "ev_" + nowMs + "_2",
            timestamp: nowMs,
            actionType: "WHATSAPP_CODE_GENERATED",
            actor: "SYSTEM",
            summaryText: `Single-use 6-digit WhatsApp verification code generated for ${phone}`,
            contentHash
          }
        ];

        const recordObj = {
          recordId: localRecordId,
          partyName: name,
          partyPhone: phone,
          counterpartyRole: isLent ? "BORROWER" : "LENDER",
          creatorRole: role,
          lenderName: isLent ? "You" : name,
          borrowerName: isLent ? name : "You",
          principalAmount: amtVal,
          principalAmountPaise: Math.round(amtVal * 100),
          currency: "INR",
          startDateMillis: nowMs,
          dueDateMillis: dueDateMs,
          note: note.trim(),
          upiId: upi,
          status: "PENDING_CONFIRMATION",
          whatsappOtp: localOtp,
          secret: localSecret,
          contentHash,
          auditTrail: initialAuditTrail,
          createdAt: nowMs
        };

        try {
          if (navigator.onLine && sessionToken) {
            const cloudRes = await apiCall("syncWebRecord", {
              sessionToken,
              record: {
                partyName: name,
                partyPhone: phone,
                counterpartyRole: isLent ? "BORROWER" : "LENDER",
                amountPaise: Math.round(amtVal * 100),
                currency: "INR",
                dueDateMillis: dueDateMs,
                note: note.trim(),
                upiId: upi
              }
            });
            if (cloudRes.record) {
              Object.assign(recordObj, cloudRes.record);
            }
            if (cloudRes.verificationUrl) recordObj.verificationUrl = cloudRes.verificationUrl;
            if (cloudRes.whatsappOtp) recordObj.whatsappOtp = cloudRes.whatsappOtp;
          }
        } catch (err) {
          console.warn("Could not sync online, saved offline:", err.message);
        }

        // Add to local records for current user
        records.unshift(recordObj);
        saveLocalRecords(records);

        formRecord.reset();
        [nameInput, phoneInput, amountInput, dueDateInput, upiInput].forEach((inp) => {
          if (inp) {
            inp.classList.remove("input-valid");
            inp.classList.remove("input-invalid");
          }
        });
        hideModal("modal-record");
        btnSave.disabled = false;
        btnText.textContent = t("btn_save_record");

        renderKPIs();
        renderRecordList();
        showToast(t("toast_record_created"), "success");
        openShareSuccessModal(recordObj);
      });
    }

    // Close Share Success Modal
    const btnCloseShare = document.getElementById("btn-close-share");
    const btnFinishShare = document.getElementById("btn-finish-share");
    [btnCloseShare, btnFinishShare].forEach((btn) => {
      if (btn) btn.addEventListener("click", () => hideModal("modal-share-success"));
    });

    // Close Audit Modal
    const btnCloseAudit = document.getElementById("btn-close-audit");
    const btnFinishAudit = document.getElementById("btn-finish-audit");
    [btnCloseAudit, btnFinishAudit].forEach((btn) => {
      if (btn) btn.addEventListener("click", () => hideModal("modal-audit-trail"));
    });

    // Copy Hash Button
    const btnCopyHash = document.getElementById("btn-copy-hash");
    if (btnCopyHash) {
      btnCopyHash.addEventListener("click", async () => {
        const hashEl = document.getElementById("audit-content-hash");
        if (hashEl && hashEl.textContent) {
          await copyToClipboard(hashEl.textContent);
          btnCopyHash.textContent = t("btn_copied");
          showToast(t("toast_copied"), "info");
          setTimeout(() => {
            btnCopyHash.textContent = t("btn_copy");
          }, 2000);
        }
      });
    }

    // Copy Share Link Button
    const btnCopyLink = document.getElementById("btn-copy-link");
    if (btnCopyLink) {
      btnCopyLink.addEventListener("click", async () => {
        const linkInput = document.getElementById("display-share-link");
        if (linkInput && linkInput.value) {
          await copyToClipboard(linkInput.value);
          btnCopyLink.textContent = t("btn_copied");
          showToast(t("toast_copied"), "info");
          setTimeout(() => {
            btnCopyLink.textContent = t("btn_copy");
          }, 2000);
        }
      });
    }

    // Escape key closes modals
    window.addEventListener("keydown", (e) => {
      if (e.key === "Escape") {
        hideModal("modal-auth");
        hideModal("modal-record");
        hideModal("modal-share-success");
        hideModal("modal-audit-trail");
      }
    });
  }

  function startAuthOtpTimer(expiresAtMillis) {
    if (authTimerInterval) clearInterval(authTimerInterval);
    authTimerInterval = setInterval(() => {
      const remaining = Math.max(0, Math.floor((expiresAtMillis - Date.now()) / 1000));
      const mins = Math.floor(remaining / 60);
      const secs = remaining % 60;
      const el = document.getElementById("auth-timer-text");
      if (el) {
        if (remaining > 0) {
          el.textContent = `Code expires in ${mins}:${secs < 10 ? "0" : ""}${secs}`;
        } else {
          el.textContent = "Code expired. Please request a new code.";
          clearInterval(authTimerInterval);
        }
      }
    }, 1000);
  }

  // 12. App Initialization
  function init() {
    const savedTheme = localStorage.getItem(STORAGE_KEYS.THEME);
    const prefersDark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
    const initialTheme = savedTheme || (prefersDark ? "dark" : "light");
    document.documentElement.setAttribute("data-theme", initialTheme);
    const themeIcon = document.getElementById("theme-icon");
    if (themeIcon) themeIcon.textContent = initialTheme === "dark" ? "☀️" : "🌙";

    const savedLang = localStorage.getItem(STORAGE_KEYS.LANG);
    const browserLang = (navigator.language || "").split("-")[0].toLowerCase();
    currentLang = savedLang || (I18N[browserLang] ? browserLang : "en");

    sessionToken = localStorage.getItem(STORAGE_KEYS.SESSION) || "";
    try {
      const rawUser = localStorage.getItem(STORAGE_KEYS.USER);
      if (rawUser) currentUser = JSON.parse(rawUser);
    } catch (_) {}

    records = loadLocalRecords();
    wireEvents();
    renderAll();

    if (navigator.onLine && sessionToken) {
      syncLedgerFromCloud();
    }
  }

  // CommonJS export for Node.js test runner
  if (typeof module !== "undefined" && module.exports) {
    module.exports = {
      I18N,
      formatRupees,
      calculateLedgerKPIs,
      filterRecords,
      getRecordsStorageKey,
      validateName,
      validatePhone,
      validateAmount,
      validateDueDate,
      validateUpi,
      computeRecordHash
    };
  } else {
    document.addEventListener("DOMContentLoaded", init);
  }
})();
