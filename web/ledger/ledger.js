/**
 * Udhaar Web Ledger & Two-Party Record Hub (web/ledger/ledger.js)
 * Full-scale production client with offline-first caching, email OTP account verification,
 * and WhatsApp 6-digit verification code generation.
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
      active_records: "{count} active records"
    },
    hi: {
      badge_web_ledger: "वेब बहीखाता",
      btn_sign_in: "खाता सत्यापित करें",
      btn_verified: "✓ सत्यापित खाता",
      kpi_lent_label: "आपको मिलेंगे",
      kpi_lent_sub: "कुल दिया",
      kpi_borrowed_label: "आपको देने हैं",
      kpi_borrowed_sub: "कुल लिया",
      kpi_net_label: "कुल शेष",
      kpi_net_sub: "समग्र स्थिति",
      kpi_pending_label: "लंबित",
      kpi_pending_sub: "सत्यापन आवश्यक",
      kpi_pending_hint: "व्हाट्सएप कोड की प्रतीक्षा",
      filter_all: "सभी",
      filter_lent: "मैंने दिया",
      filter_borrowed: "मैंने लिया",
      filter_pending: "लंबित",
      filter_confirmed: "पुष्ट",
      btn_record_udhaar: "उधार दर्ज करें",
      btn_record_first: "पहला उधार दर्ज करें",
      empty_title: "कोई लेनदेन नहीं मिला",
      empty_desc: "अपना पहला लेनदेन दर्ज करके शुरुआत करें। एक निजी लिंक और 6-अंकों का व्हाट्सएप कोड अपने आप बन जाएगा।",
      auth_modal_title: "खाता सत्यापन",
      auth_intro: "अपना मोबाइल नंबर और ईमेल पता दर्ज करें। आपके ईमेल पर 6-अंकों का सत्यापन कोड भेजा जाएगा।",
      label_mobile_number: "मोबाइल नंबर",
      hint_mobile_number: "दो-पक्षीय फोन सत्यापन के लिए उपयोग किया जाता है।",
      label_email_address: "ईमेल पता",
      hint_email_address: "6-अंकों का सत्यापन कोड यहाँ भेजा जाएगा।",
      btn_cancel: "रद्द करें",
      btn_send_code: "सत्यापन कोड भेजें",
      btn_sending: "भेज रहे हैं...",
      auth_otp_desc: "अपने ईमेल पर भेजा गया 6-अंकों का कोड दर्ज करें।",
      btn_back: "पीछे",
      btn_verify_signin: "सत्यापित करें और जुड़ें",
      btn_verifying: "सत्यापित कर रहे हैं...",
      btn_resend: "कोड पुनः भेजें",
      auth_success_title: "खाता सत्यापित हो गया!",
      btn_continue: "बहीखाता जारी रखें",
      create_modal_title: "नया उधार दर्ज करें",
      label_transaction_type: "लेनदेन का प्रकार",
      role_lent_label: "मैंने पैसे दिए (दिया)",
      role_borrowed_label: "मैंने पैसे लिए (लिया)",
      label_person_name: "व्यक्ति का नाम",
      label_person_phone: "मोबाइल नंबर",
      label_amount_rupees: "रुपये में राशि (₹)",
      label_due_date: "वापसी की तारीख",
      date_chip_1w: "1 सप्ताह",
      date_chip_2w: "2 सप्ताह",
      date_chip_1m: "1 महीना",
      date_chip_custom: "कस्टम",
      label_note_optional: "नोट (वैकल्पिक)",
      label_upi_optional: "आपकी यूपीआई आईडी (वैकल्पिक)",
      hint_upi: "रसीद पर दिखाई देगी ताकि दूसरा पक्ष सीधे भुगतान कर सके।",
      btn_save_record: "सहेजें और कोड बनाएं",
      btn_saving: "सहेज रहे हैं...",
      share_modal_title: "उधार सहेजा गया • साझा करने के लिए तैयार",
      share_instructions: "इसे अपने साथी के साथ व्हाट्सएप पर साझा करें। वे लेनदेन की समीक्षा कर 6-अंकों के कोड से पुष्टि कर सकते हैं।",
      label_whatsapp_code: "व्हाट्सएप सत्यापन कोड",
      hint_code_validity: "15 मिनट के लिए वैध • एक बार उपयोग",
      label_verification_link: "सत्यापन लिंक",
      btn_copy: "कॉपी करें",
      btn_copied: "कॉपी हो गया!",
      btn_share_whatsapp: "व्हाट्सएप पर भेजें",
      btn_done: "पूर्ण",
      status_confirmed: "✓ पुष्ट",
      status_pending: "⏳ कोड प्रतीक्षित",
      status_settled: "✓ चुकता",
      status_overdue: "⚠ बकाया",
      due_in_days: "{days} दिनों में देय",
      overdue_by_days: "{days} दिन बकाया",
      no_due_date: "कोई तारीख तय नहीं",
      action_share: "व्हाट्सएप",
      action_copy: "लिंक कॉपी",
      action_settle: "चुकता करें",
      toast_copied: "लिंक कॉपी हो गया!",
      even_balance: "बराबर शेष",
      net_positive: "आपको शुद्ध {amt} मिलने हैं",
      net_negative: "आपको शुद्ध {amt} देने हैं",
      active_records: "{count} सक्रिय लेनदेन"
    },
    bn: {
      badge_web_ledger: "ওয়েব খাতা",
      btn_sign_in: "অ্যাকাউন্ট যাচাই",
      btn_verified: "✓ যাচাইকৃত অ্যাকাউন্ট",
      kpi_lent_label: "আপনি পাবেন",
      kpi_lent_sub: "মোট দেওয়া",
      kpi_borrowed_label: "আপনাকে দিতে হবে",
      kpi_borrowed_sub: "মোট নেওয়া",
      kpi_net_label: "মোট ব্যালেন্স",
      kpi_net_sub: "সামগ্রিক অবস্থা",
      kpi_pending_label: "অপেক্ষারত",
      kpi_pending_sub: "যাচাই প্রয়োজন",
      kpi_pending_hint: "হোয়াটসঅ্যাপ কোডের অপেক্ষায়",
      filter_all: "সকল",
      filter_lent: "আমি দিয়েছি",
      filter_borrowed: "আমি নিয়েছি",
      filter_pending: "অপেক্ষারত",
      filter_confirmed: "নিশ্চিত",
      btn_record_udhaar: "ধার লিপিবদ্ধ করুন",
      btn_record_first: "প্রথম লেনদেন লিপিবদ্ধ করুন",
      empty_title: "কোন লেনদেন পাওয়া যায়নি",
      empty_desc: "আপনার প্রথম লেনদেন যুক্ত করে শুরু করুন। একটি সুরক্ষিত লিংক ও ৬-সংখ্যার কোড তৈরি হবে।",
      auth_modal_title: "অ্যাকাউন্ট যাচাইকরণ",
      auth_intro: "আপনার মোবাইল নম্বর এবং ইমেল ঠিকানা দিন। ইমেলে একটি ৬-সংখ্যার কোড পাঠানো হবে।",
      label_mobile_number: "মোবাইল নম্বর",
      hint_mobile_number: "দুই পক্ষের যাচাইয়ের জন্য ব্যবহৃত হয়।",
      label_email_address: "ইমেল ঠিকানা",
      hint_email_address: "৬-সংখ্যার যাচাই কোড এখানে পাঠানো হবে।",
      btn_cancel: "বাতিল",
      btn_send_code: "যাচাই কোড পাঠান",
      btn_sending: "পাঠানো হচ্ছে...",
      auth_otp_desc: "আপনার ইমেলে পাঠানো ৬-সংখ্যার কোডটি লিখুন।",
      btn_back: "পেছনে",
      btn_verify_signin: "যাচাই করে প্রবেশ করুন",
      btn_verifying: "যাচাই করা হচ্ছে...",
      btn_resend: "কোড পুনরায় পাঠান",
      auth_success_title: "অ্যাকাউন্ট যাচাই সম্পন্ন!",
      btn_continue: "খাতায় এগিয়ে যান",
      create_modal_title: "নতুন ধার যুক্ত করুন",
      label_transaction_type: "লেনদেনের ধরন",
      role_lent_label: "আমি টাকা দিয়েছি (দেওয়া)",
      role_borrowed_label: "আমি টাকা নিয়েছি (নেওয়া)",
      label_person_name: "ব্যক্তির নাম",
      label_person_phone: "মোবাইল নম্বর",
      label_amount_rupees: "টাকার পরিমাণ (₹)",
      label_due_date: "ফেরতের তারিখ",
      date_chip_1w: "১ সপ্তাহ",
      date_chip_2w: "২ সপ্তাহ",
      date_chip_1m: "১ মাস",
      date_chip_custom: "পছন্দমতো",
      label_note_optional: "নোট (ঐচ্ছিক)",
      label_upi_optional: "আপনার ইউপিআই আইডি (ঐচ্ছিক)",
      hint_upi: "রসিদে প্রদর্শিত হবে যাতে অন্য ব্যক্তি কিউআর বা অ্যাপ দিয়ে পরিশোধ করতে পারেন।",
      btn_save_record: "সংরক্ষণ ও কোড তৈরি",
      btn_saving: "সংরক্ষণ হচ্ছে...",
      share_modal_title: "সংরক্ষিত হয়েছে • পাঠানোর জন্য প্রস্তুত",
      share_instructions: "এটি হোয়াটসঅ্যাপে শেয়ার করুন। অপর পক্ষ যাচাই করে কোড দিয়ে নিশ্চিত করতে পারবেন।",
      label_whatsapp_code: "হোয়াটসঅ্যাপ যাচাই কোড",
      hint_code_validity: "১৫ মিনিট বৈধ • একক ব্যবহার",
      label_verification_link: "যাচাই লিংক",
      btn_copy: "কপি করুন",
      btn_copied: "কপি হয়েছে!",
      btn_share_whatsapp: "হোয়াটসঅ্যাপে পাঠান",
      btn_done: "সম্পন্ন",
      status_confirmed: "✓ নিশ্চিত",
      status_pending: "⏳ কোড অপেক্ষমাণ",
      status_settled: "✓ পরিশোধিত",
      status_overdue: "⚠ মেয়াদোত্তীর্ণ",
      due_in_days: "{days} দিনের মধ্যে প্রদেয়",
      overdue_by_days: "{days} দিন মেয়াদোত্তীর্ণ",
      no_due_date: "তারিখ নির্ধারিত নেই",
      action_share: "হোয়াটসঅ্যাপ",
      action_copy: "লিংক কপি",
      action_settle: "পরিশোধ চিহ্নিত করুন",
      toast_copied: "লিংক ক্লিপবোর্ডে কপি করা হয়েছে!",
      even_balance: "সমান ব্যালেন্স",
      net_positive: "আপনি মোট {amt} পাবেন",
      net_negative: "আপনাকে মোট {amt} দিতে হবে",
      active_records: "{count}টি সক্রিয় লেনদেন"
    },
    as: {
      badge_web_ledger: "ৱেব বহীখাতা",
      btn_sign_in: "একাউণ্ট নিশ্চিত কৰক",
      btn_verified: "✓ নিশ্চিত একাউণ্ট",
      kpi_lent_label: "আপুনি পাব",
      kpi_lent_sub: "মুঠ দিয়া",
      kpi_borrowed_label: "আপুনি দিব লাগিব",
      kpi_borrowed_sub: "মুঠ লোৱা",
      kpi_net_label: "মুঠ বাকী",
      kpi_net_sub: "সামগ্ৰিক স্থিতি",
      kpi_pending_label: "অপেক্ষাকৃত",
      kpi_pending_sub: "যাচাইৰ প্ৰয়োজন",
      kpi_pending_hint: "হোৱাটছএপ ক’ডলৈ অপেক্ষা",
      filter_all: "সকলো",
      filter_lent: "মই দিলোঁ",
      filter_borrowed: "মই ল’লোঁ",
      filter_pending: "অপেক্ষাকৃত",
      filter_confirmed: "নিশ্চিত",
      btn_record_udhaar: "ধাৰ লিপিবদ্ধ কৰক",
      btn_record_first: "প্ৰথম লেনদেন লিখক",
      empty_title: "কোনো লেনদেন পোৱা নগ’ল",
      empty_desc: "আপোনাৰ প্ৰথমটো ধাৰ লিপিবদ্ধ কৰক। এটা ব্যক্তিগত লিংক আৰু ৬-অংকৰ ক’ড নিজে তৈয়াৰ হ’ব।",
      auth_modal_title: "একাউণ্ট পৰীক্ষণ",
      auth_intro: "আপোনাৰ ম’বাইল নম্বৰ আৰু ইমেইল ঠিকনা দিয়ক। ইমেইললৈ এটা ৬-সংখ্যার ক’ড পঠিওৱা হ’ব।",
      label_mobile_number: "ম’বাইল নম্বৰ",
      hint_mobile_number: "দুয়োপক্ষৰ পৰীক্ষণৰ বাবে ব্যৱহাৰ হয়।",
      label_email_address: "ইমেইল ঠিকনা",
      hint_email_address: "৬-সংখ্যার ক’ড ইয়ালৈ পঠিওৱা হ’ব।",
      btn_cancel: "বাতিল",
      btn_send_code: "ক’ড পঠিয়াওক",
      btn_sending: "পঠিয়াই থকা হৈছে...",
      auth_otp_desc: "আপোনাৰ ইমেইললৈ পঠিওৱা ৬-অংকৰ ক’ডটো লিখক।",
      btn_back: "পিছলৈ",
      btn_verify_signin: "পৰীক্ষা কৰি সোমাওক",
      btn_verifying: "পৰীক্ষা চলি আছে...",
      btn_resend: "ক’ড পুনৰ পঠিয়াওক",
      auth_success_title: "একাউণ্ট নিশ্চিত হ’ল!",
      btn_continue: "বহীলৈ যাওক",
      create_modal_title: "নতুন ধাৰ লিপিবদ্ধ কৰক",
      label_transaction_type: "লেনদেনৰ প্ৰকাৰ",
      role_lent_label: "মই ধন দিলোঁ (দিয়া)",
      role_borrowed_label: "মই ধন ল’লোঁ (লোৱা)",
      label_person_name: "ব্যক্তিৰ নাম",
      label_person_phone: "ম’বাইল নম্বৰ",
      label_amount_rupees: "টকাৰ পৰিমাণ (₹)",
      label_due_date: "উভটাই দিয়াৰ তাৰিখ",
      date_chip_1w: "১ সপ্তাহ",
      date_chip_2w: "২ সপ্তাহ",
      date_chip_1m: "১ মাহ",
      date_chip_custom: "নিজে বাছক",
      label_note_optional: "টোকা (ঐচ্ছিক)",
      label_upi_optional: "আপোনাৰ ইউপিআই আই ডি (ঐচ্ছিক)",
      hint_upi: "ৰচিদত দেখা যাব যাতে আনজনে ইউপিআইৰ জৰিয়তে পৰিশোধ কৰিব পাৰে।",
      btn_save_record: "সংৰক্ষণ আৰু ক’ড প্ৰস্তুত",
      btn_saving: "সংৰক্ষণ চলি আছে...",
      share_modal_title: "সংৰক্ষণ হ’ল • পঠিয়াবলৈ সাজু",
      share_instructions: "ইয়াক হোৱাটছএপত আনজনৰ লগত অংশীদাৰ কৰক। তেওঁলোকে পৰীক্ষা কৰি ক’ড দি নিশ্চিত কৰিব পাৰিব।",
      label_whatsapp_code: "হোৱাটছএপ পৰীক্ষণ ক’ড",
      hint_code_validity: "১৫ মিনিট বৈধ • এবাৰ ব্যৱহাৰ",
      label_verification_link: "পৰীক্ষণ লিংক",
      btn_copy: "কপি কৰক",
      btn_copied: "কপি হ’ল!",
      btn_share_whatsapp: "হোৱাটছএপত পঠিয়াওক",
      btn_done: "সম্পূৰ্ণ",
      status_confirmed: "✓ নিশ্চিত",
      status_pending: "⏳ ক’ড বাকী",
      status_settled: "✓ পৰিশোধিত",
      status_overdue: "⚠ বাকী থকা",
      due_in_days: "{days} দিনৰ ভিতৰত দিব লাগে",
      overdue_by_days: "{days} দিন বাকী পৰিছে",
      no_due_date: "তাৰিখ নাই",
      action_share: "হোৱাটছএপ",
      action_copy: "লিংক কপি",
      action_settle: "পৰিশোধ চিহ্নিত কৰক",
      toast_copied: "লিংক ক্লিপব’ৰ্ডত কপি কৰা হ’ল!",
      even_balance: "সমান বাকী",
      net_positive: "আপুনি মুঠ {amt} পাব",
      net_negative: "আপুনি মুঠ {amt} দিব লাগিব",
      active_records: "{count}টা সক্ৰিয় লেনদেন"
    },
    ne: {
      badge_web_ledger: "वेब खाता",
      btn_sign_in: "खाता पुष्टि गर्नुहोस्",
      btn_verified: "✓ प्रमाणित खाता",
      kpi_lent_label: "तपाईंले पाउनुहुनेछ",
      kpi_lent_sub: "कुल दिएको",
      kpi_borrowed_label: "तपाईंले तिर्नुपर्ने",
      kpi_borrowed_sub: "कुल लिएको",
      kpi_net_label: "कुल बाँकी",
      kpi_net_sub: "समग्र अवस्था",
      kpi_pending_label: "बाँकी",
      kpi_pending_sub: "पुष्टि आवश्यक",
      kpi_pending_hint: "ह्वाट्सएप कोडको प्रतीक्षा",
      filter_all: "सबै",
      filter_lent: "मैले दिएको",
      filter_borrowed: "मैले लिएको",
      filter_pending: "बाँकी",
      filter_confirmed: "पुष्टि भएको",
      btn_record_udhaar: "उधारो दर्ता गर्नुहोस्",
      btn_record_first: "पहिलो उधारो दर्ता गर्नुहोस्",
      empty_title: "कुनै कारोबार भेटिएन",
      empty_desc: "पहिलो कारोबार दर्ता गरी सुरु गर्नुहोस्। सुरक्षित लिंक र ६-अंकको ह्वाट्सएप कोड स्वतः बन्नेछ।",
      auth_modal_title: "खाता प्रमाणीकरण",
      auth_intro: "तपाईंको मोबाइल नम्बर र इमेल ठेगाना लेख्नुहोस्। ६-अंकको कोड तपाईंको इमेलमा पठाइनेछ।",
      label_mobile_number: "मोबाइल नम्बर",
      hint_mobile_number: "दुई-पक्षीय फोन पुष्टिका लागि प्रयोग गरिन्छ।",
      label_email_address: "इमेल ठेगाना",
      hint_email_address: "६-अंकको कोड यहाँ पठाइनेछ।",
      btn_cancel: "रद्द गर्नुहोस्",
      btn_send_code: "प्रमाणीकरण कोड पठाउनुहोस्",
      btn_sending: "पठाउँदै...",
      auth_otp_desc: "इमेलमा पठाइएको ६-अंकको कोड यहाँ लेख्नुहोस्।",
      btn_back: "पछाडि",
      btn_verify_signin: "पुष्टि गरी लगइन गर्नुहोस्",
      btn_verifying: "पुष्टि गर्दै...",
      btn_resend: "कोड पुन: पठाउनुहोस्",
      auth_success_title: "खाता प्रमाणित भयो!",
      btn_continue: "खातामा जानुहोस्",
      create_modal_title: "नयाँ उधारो दर्ता गर्नुहोस्",
      label_transaction_type: "कारोबार प्रकार",
      role_lent_label: "मैले पैसा दिएँ (दिएको)",
      role_borrowed_label: "मैले पैसा लिएँ (लिएको)",
      label_person_name: "व्यक्तिको नाम",
      label_person_phone: "मोबाइल नम्बर",
      label_amount_rupees: "रुपैयाँमा रकम (₹)",
      label_due_date: "फिर्ता गर्ने मिति",
      date_chip_1w: "१ हप्ता",
      date_chip_2w: "२ हप्ता",
      date_chip_1m: "१ महिना",
      date_chip_custom: "कस्टम",
      label_note_optional: "नोट (वैकल्पिक)",
      label_upi_optional: "तपाईंको युपिआई आईडी (वैकल्पिक)",
      hint_upi: "रसिदमा देखिनेछ ताकि अर्को पक्षले भुक्तानी गर्न सकोस्।",
      btn_save_record: "सुरक्षित गरी कोड बनाउनुहोस्",
      btn_saving: "सुरक्षित गर्दै...",
      share_modal_title: "कारोबार सुरक्षित भयो • पठाउन तयार",
      share_instructions: "यसलाई ह्वाट्सएपमा पठाउनुहोस्। अर्को पक्षले ६-अंकको कोड प्रयोग गरी पुष्टि गर्न सक्नेछन्।",
      label_whatsapp_code: "ह्वाट्सएप प्रमाणीकरण कोड",
      hint_code_validity: "१५ मिनेट मान्य • एक पटक प्रयोग",
      label_verification_link: "प्रमाणीकरण लिंक",
      btn_copy: "कपी गर्नुहोस्",
      btn_copied: "कपी भयो!",
      btn_share_whatsapp: "ह्वाट्सएपमा पठाउनुहोस्",
      btn_done: "सकियो",
      status_confirmed: "✓ पुष्टि भयो",
      status_pending: "⏳ कोड बाँकी",
      status_settled: "✓ चुक्ता भयो",
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
      active_records: "{count} सक्रिय कारोबार"
    }
  };

  // 2. State & Storage Keys
  const STORAGE_KEYS = {
    RECORDS: "udhaar_web_ledger_records",
    SESSION: "udhaar_web_session",
    USER: "udhaar_web_user",
    THEME: "udhaar_theme",
    LANG: "udhaar_lang"
  };

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

  function announce(msg) {
    const el = document.getElementById("a11y-announcer");
    if (el) el.textContent = msg;
  }

  // 4. Persistence Helpers
  function loadLocalRecords() {
    try {
      const raw = localStorage.getItem(STORAGE_KEYS.RECORDS);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) return parsed;
      }
    } catch (_) {}
    return [];
  }

  function saveLocalRecords(list) {
    try {
      localStorage.setItem(STORAGE_KEYS.RECORDS, JSON.stringify(list));
    } catch (_) {}
  }

  // 5. DOM Rendering
  function renderAll() {
    applyLanguage(currentLang);
    renderKPIs();
    renderRecordList();
    renderAccountStatus();
  }

  function applyLanguage(lang) {
    currentLang = I18N[lang] ? lang : "en";
    document.documentElement.lang = currentLang;

    // Update all elements with data-i18n
    const transEls = document.querySelectorAll("[data-i18n]");
    transEls.forEach((el) => {
      const key = el.getAttribute("data-i18n");
      if (key && I18N[currentLang] && I18N[currentLang][key]) {
        el.textContent = I18N[currentLang][key];
      }
    });

    const langSelect = document.getElementById("ledger-lang-select");
    if (langSelect) langSelect.value = currentLang;
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
      let dueBadgeClass = "badge-slate";
      if (r.dueDateMillis || r.expiryDateMillis) {
        const dueMs = Number(r.dueDateMillis || r.expiryDateMillis);
        const diffDays = Math.ceil((dueMs - Date.now()) / (1000 * 60 * 60 * 24));
        if (diffDays < 0) {
          dueText = t("overdue_by_days", { days: Math.abs(diffDays) });
          dueBadgeClass = "badge-coral";
        } else if (diffDays === 0) {
          dueText = "Due today";
          dueBadgeClass = "badge-amber";
        } else {
          dueText = t("due_in_days", { days: diffDays });
          dueBadgeClass = "badge-slate";
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
          alert(t("toast_copied"));
        }
      });
    });

    grid.querySelectorAll(".btn-card-settle").forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = btn.getAttribute("data-id");
        const rec = records.find((x) => (x.recordId || x.id) === id);
        if (rec && confirm(`Mark record with ${rec.partyName || "contact"} as settled?`)) {
          rec.status = "SETTLED";
          saveLocalRecords(records);
          renderAll();
          announce("Record marked as settled.");
        }
      });
    });
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

  // 6. Network & API Calls
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
        // Merge cloud records with local records
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

  // 7. Modals: Open / Close & Focus Management
  let lastFocusedElement = null;

  function showModal(modalId) {
    const el = document.getElementById(modalId);
    if (!el) return;
    lastFocusedElement = document.activeElement;
    el.hidden = false;
    el.style.display = "flex";
    el.classList.remove("hidden");

    // Trap focus inside modal
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

  // 8. Event Wiring
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
          // Show verified summary in step 3
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
          // Show step 1
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

          // Switch to Step 2
          formAuth1.hidden = true;
          formAuth1.classList.add("hidden");
          const formAuth2 = document.getElementById("form-auth-step2");
          formAuth2.hidden = false;
          formAuth2.classList.remove("hidden");

          const sentDesc = document.getElementById("otp-sent-desc");
          if (sentDesc) sentDesc.textContent = `Enter the 6-digit code sent to ${email}.`;

          startAuthOtpTimer(res.expiresAtMillis || Date.now() + 10 * 60 * 1000);

          const firstBox = document.querySelector(".auth-otp-box");
          if (firstBox) firstBox.focus();

          // If in development and debugOtp returned, prefill or console log
          if (res.debugOtp) {
            console.log("[Dev Info] Verification code:", res.debugOtp);
          }
        } catch (err) {
          alert(err.message || "Failed to send verification code. Please check email address.");
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

    // Auth Step 2: Back button
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
          alert("Please enter the complete 6-digit code.");
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

          // Switch to Step 3 (Success)
          formAuth2.hidden = true;
          formAuth2.classList.add("hidden");
          const step3 = document.getElementById("auth-step-success");
          step3.hidden = false;
          step3.classList.remove("hidden");

          document.getElementById("profile-mobile-val").textContent = currentUser.mobileNumber || authPendingMobile;
          document.getElementById("profile-email-val").textContent = currentUser.email || authPendingEmail;

          renderAccountStatus();
          syncLedgerFromCloud();
        } catch (err) {
          alert(err.message || "Invalid or expired verification code.");
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
      if (btn) btn.addEventListener("click", () => showModal("modal-record"));
    });

    // Close Create Record Modal
    const btnCloseRecord = document.getElementById("btn-close-record");
    const btnCancelRecord = document.getElementById("btn-cancel-record");
    [btnCloseRecord, btnCancelRecord].forEach((btn) => {
      if (btn) btn.addEventListener("click", () => hideModal("modal-record"));
    });

    // Date Chips
    const dateChips = document.querySelectorAll(".date-chip");
    const dueDateInput = document.getElementById("input-due-date");
    dateChips.forEach((chip) => {
      chip.addEventListener("click", () => {
        dateChips.forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
        const days = chip.getAttribute("data-days");
        if (days && days !== "custom" && dueDateInput) {
          const d = new Date(Date.now() + parseInt(days, 10) * 86400000);
          dueDateInput.value = d.toISOString().split("T")[0];
        }
      });
    });

    // Form Create Record Submit
    const formRecord = document.getElementById("form-create-record");
    if (formRecord) {
      formRecord.addEventListener("submit", async (e) => {
        e.preventDefault();
        const role = formRecord.querySelector('input[name="recordRole"]:checked').value;
        const name = document.getElementById("input-person-name").value.trim();
        const phone = document.getElementById("input-person-phone").value.trim();
        const amtVal = parseFloat(document.getElementById("input-amount").value || "0");
        const dueDate = document.getElementById("input-due-date").value;
        const note = document.getElementById("input-note").value.trim();
        const upi = document.getElementById("input-upi").value.trim();

        if (amtVal <= 0) {
          alert("Please enter a valid amount greater than 0.");
          return;
        }

        const btnSave = document.getElementById("btn-save-record");
        const btnText = document.getElementById("btn-save-text");
        btnSave.disabled = true;
        btnText.textContent = t("btn_saving");

        const dueDateMs = dueDate ? new Date(dueDate).getTime() : null;
        const isLent = role === "GIVEN";

        // Generate local 6-digit WhatsApp OTP & token
        const localOtp = String(Math.floor(100000 + Math.random() * 900000));
        const localSecret = Array.from(crypto.getRandomValues(new Uint8Array(12)))
          .map((b) => b.toString(16).padStart(2, "0"))
          .join("");
        const localRecordId = "tx_" + Date.now();

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
          startDateMillis: Date.now(),
          dueDateMillis: dueDateMs,
          note,
          upiId: upi,
          status: "PENDING_CONFIRMATION",
          whatsappOtp: localOtp,
          secret: localSecret,
          createdAt: Date.now()
        };

        try {
          if (navigator.onLine && sessionToken) {
            // Sync to cloud
            const cloudRes = await apiCall("syncWebRecord", {
              sessionToken,
              record: {
                partyName: name,
                partyPhone: phone,
                counterpartyRole: isLent ? "BORROWER" : "LENDER",
                amountPaise: Math.round(amtVal * 100),
                currency: "INR",
                dueDateMillis: dueDateMs,
                note,
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

        // Add to local records (prepend)
        records.unshift(recordObj);
        saveLocalRecords(records);

        // Reset form and close
        formRecord.reset();
        hideModal("modal-record");
        btnSave.disabled = false;
        btnText.textContent = t("btn_save_record");

        // Re-render UI
        renderKPIs();
        renderRecordList();

        // Open WhatsApp Share Dialog
        openShareSuccessModal(recordObj);
      });
    }

    // Close Share Success Modal
    const btnCloseShare = document.getElementById("btn-close-share");
    const btnFinishShare = document.getElementById("btn-finish-share");
    [btnCloseShare, btnFinishShare].forEach((btn) => {
      if (btn) btn.addEventListener("click", () => hideModal("modal-share-success"));
    });

    // Copy Share Link Button
    const btnCopyLink = document.getElementById("btn-copy-link");
    if (btnCopyLink) {
      btnCopyLink.addEventListener("click", async () => {
        const linkInput = document.getElementById("display-share-link");
        if (linkInput && linkInput.value) {
          await copyToClipboard(linkInput.value);
          btnCopyLink.textContent = t("btn_copied");
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

  // 9. App Initialization
  function init() {
    // Theme setup
    const savedTheme = localStorage.getItem(STORAGE_KEYS.THEME);
    const prefersDark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
    const initialTheme = savedTheme || (prefersDark ? "dark" : "light");
    document.documentElement.setAttribute("data-theme", initialTheme);
    const themeIcon = document.getElementById("theme-icon");
    if (themeIcon) themeIcon.textContent = initialTheme === "dark" ? "☀️" : "🌙";

    // Language setup
    const savedLang = localStorage.getItem(STORAGE_KEYS.LANG);
    const browserLang = (navigator.language || "").split("-")[0].toLowerCase();
    currentLang = savedLang || (I18N[browserLang] ? browserLang : "en");

    // Session & User setup
    sessionToken = localStorage.getItem(STORAGE_KEYS.SESSION) || "";
    try {
      const rawUser = localStorage.getItem(STORAGE_KEYS.USER);
      if (rawUser) currentUser = JSON.parse(rawUser);
    } catch (_) {}

    // Load local records
    records = loadLocalRecords();

    // Wire events
    wireEvents();

    // Initial render
    renderAll();

    // If online, perform background sync
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
      filterRecords
    };
  } else {
    document.addEventListener("DOMContentLoaded", init);
  }
})();
