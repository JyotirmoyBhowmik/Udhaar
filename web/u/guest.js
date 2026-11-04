// =========================================================================
// Udhaar Guest Web Flow — v18.0
// Plain-language, accessible, multi-lingual client (EN, HI, BN, AS, NE)
// =========================================================================

(function () {
  "use strict";

  // 1. Comprehensive Localization Dictionary (5 Regional Languages)
  const I18N = {
    en: {
      brand_name: "UDHAAR",
      brand_sub: "WEB",
      select_lang: "Select Language",
      toggle_theme: "Toggle theme",
      offline_banner: "You are currently offline. Check your internet connection.",
      demo_banner: "Demo preview — no real data is saved.",
      btn_retry: "Retry",

      // Badges
      badge_link_verified: "Private link",
      badge_code_checked: "Code checked",
      badge_app_confirmed: "Confirmed in the app",
      badge_due_today: "Due today",
      badge_due_in_days: "Due in {days} days",
      badge_overdue_days: "Overdue by {days} days",
      badge_paid_back: "Paid back",
      badge_installments: "Installments: {paid} of {total} paid",
      badge_next_due: "Next: {amount}",

      // Screen 1: Who and how much
      amount_lent_label: "Amount lent",
      amount_borrowed_label: "Amount borrowed",
      amount_generic_label: "Amount",
      question_lent: "{name} says they lent you {amt}. Is this right?",
      question_borrowed: "{name} says they borrowed {amt} from you. Is this right?",
      btn_yes_correct: "✓ Yes, this is correct",
      btn_something_wrong: "✕ Something is wrong",
      why_title: "Why we ask this",
      why_screen1: "Confirming this lets both of you see the exact same record, so there are no misunderstandings or forgotten amounts later.",

      // Screen 2: Enter code (Honest Code Step)
      screen2_title: "Enter the code {name} sent you",
      screen2_desc: "This 6-digit code was sent by {name} in your WhatsApp message. It proves you received the message and keeps this record secure.",
      code_expires_in: "Code valid for 15 minutes",
      btn_ask_whatsapp: "Ask on WhatsApp 💬",
      btn_check_later: "I will check later",
      btn_submit_code: "Confirm code",
      why_screen2: "This code proves that you received the message and ensures nobody else can confirm in your name.",

      // Screen 3: Next Steps
      confirmed_title: "Record confirmed",
      confirmed_subtitle: "What would you like to do next?",
      act_return_date: "Tell when you will return it",
      act_pay_upi: "Pay now by UPI",
      act_set_reminder: "Set reminder for myself",
      act_i_paid: "I have paid",
      act_download_receipt: "Download receipt",
      why_screen3: "Setting a return date or reminder helps you stay organized without awkward follow-ups.",

      // Bottom Note
      no_install_needed: "You do not need to install anything.",
      get_the_app: "Get the app",

      // Details Grid
      label_from: "From",
      label_to: "To",
      label_start_date: "Start date",
      label_due_date: "Due date",
      label_interest: "Interest",
      label_note: "Note",
      label_fingerprint: "Record fingerprint",
      advanced_details: "Advanced details",

      // Friendly Error & State Screens
      state_expired_title: "This link has expired",
      state_expired_desc: "For your security, verification links expire after some time.",
      state_expired_action: "Ask {name} to send a new link.",
      state_revoked_title: "This link was cancelled",
      state_revoked_desc: "The sender cancelled this verification link.",
      state_revoked_action: "Ask {name} if they have created a new record.",
      state_wrong_code_title: "Incorrect 6-digit code",
      state_wrong_code_desc: "The code entered did not match the one sent to you.",
      state_wrong_code_action: "Check the code sent in WhatsApp or ask {name} to resend it.",
      state_too_many_tries_title: "Too many attempts",
      state_too_many_tries_desc: "Too many incorrect codes were entered. Please wait 1 hour before trying again.",
      state_too_many_tries_action: "Wait 1 hour, or ask {name} to generate a fresh link.",
      state_already_confirmed_title: "Record already confirmed",
      state_already_confirmed_desc: "This record was already confirmed on {date}.",
      state_already_confirmed_action: "You can view the details below or save a receipt.",
      state_disputed_title: "A problem was reported",
      state_disputed_desc: "This record was reported as incorrect.",
      state_disputed_action: "Reason: {reason}. You can contact {name} directly to resolve this.",
      state_server_error_title: "Something went wrong",
      state_server_error_desc: "We were unable to load the record details.",
      state_server_error_action: "Please refresh the page or try again in a few moments.",

      // Modals: Report a problem
      dispute_sheet_title: "Report a problem",
      dispute_reason_label: "What is wrong with this record?",
      reason_wrong_amount: "Wrong amount",
      reason_wrong_date: "Wrong date",
      reason_never_took: "I never took this money",
      reason_other: "Other problem",
      dispute_note_placeholder: "Explain what is wrong (up to 300 characters)...",
      btn_submit_dispute: "Send report",

      // Modals: I have paid
      paid_sheet_title: "I have paid",
      paid_amount_label: "Amount paid (₹)",
      paid_date_label: "Date paid",
      paid_mode_label: "How did you pay?",
      mode_upi: "UPI",
      mode_cash: "Cash",
      mode_bank: "Bank transfer",
      paid_ref_label: "Reference number / note (optional)",
      paid_proof_label: "Photo of proof (receipt / screenshot)",
      btn_submit_paid: "Submit payment proof",
      paid_notice: "The lender will confirm your payment in their app.",

      // Modals: Choose return date
      date_sheet_title: "Tell when you will return it",
      date_label: "Return date",
      date_note_label: "Message (optional)",
      date_note_placeholder: "For example: I will return half this Friday and the rest next month",
      btn_submit_date: "Send proposed date",
      date_notice: "This is a proposal that {name} will accept or decline in the app.",

      // Modals: Reminder
      reminder_sheet_title: "Set reminder for myself",
      reminder_desc: "Get an alert before the due date so you never forget.",
      btn_google_cal: "Add to Google Calendar",
      btn_ics_cal: "Download Calendar file (.ics)",
      btn_browser_notify: "Turn on browser notification",
      notify_disclaimer: "Udhaar cannot send you browser alerts unless you click allow.",
      notify_enabled: "Browser alerts are enabled!",

      // Modals: UPI Pay
      upi_sheet_title: "Pay now by UPI",
      upi_scan_qr: "Scan QR with GPay, PhonePe, Paytm, or BHIM",
      upi_id_label: "UPI ID",
      btn_copy_upi: "Copy UPI ID",
      toast_upi_copied: "UPI ID copied!",
      btn_pay_app: "Pay now",
      upi_warning: "Always check the recipient name in your UPI app before completing payment.",
      upi_confirmation_question: "Did the payment go through?",
      btn_payment_yes: "Yes, payment succeeded",
      btn_payment_no: "No, not yet",

      // General Toasts
      toast_copied: "Copied to clipboard!",
      toast_success: "Saved successfully!",
      toast_network_error: "Unable to reach server. Please check your connection."
    },

    hi: {
      brand_name: "उधार",
      brand_sub: "वेब",
      select_lang: "भाषा चुनें",
      toggle_theme: "थीम बदलें",
      offline_banner: "आप अभी ऑफलाइन हैं। अपना इंटरनेट कनेक्शन जांचें।",
      demo_banner: "डेमो मोड — कोई वास्तविक डेटा सुरक्षित नहीं होता है।",
      btn_retry: "पुनः प्रयास करें",

      badge_link_verified: "प्राइवेट लिंक",
      badge_code_checked: "कोड जाँचा गया",
      badge_app_confirmed: "ऐप में पुष्टि हुई",
      badge_due_today: "आज देय है",
      badge_due_in_days: "{days} दिन में देय",
      badge_overdue_days: "{days} दिन से बकाया",
      badge_paid_back: "चुकता किया",
      badge_installments: "किस्तें: {paid} / {total} भुगतान",
      badge_next_due: "अगली किस्त: {amount}",

      amount_lent_label: "दी गई रकम",
      amount_borrowed_label: "ली गई रकम",
      amount_generic_label: "रकम",
      question_lent: "{name} का कहना है कि उन्होंने आपको {amt} दिए हैं। क्या यह सही है?",
      question_borrowed: "{name} का कहना है कि उन्होंने आपसे {amt} उधार लिए हैं। क्या यह सही है?",
      btn_yes_correct: "✓ हाँ, यह सही है",
      btn_something_wrong: "✕ कुछ गलत है",
      why_title: "हम यह क्यों पूछते हैं",
      why_screen1: "इसकी पुष्टि करने से आप दोनों को एक ही हिसाब दिखता है, जिससे बाद में कोई गलतफहमी नहीं होती।",

      screen2_title: "{name} द्वारा भेजा गया कोड दर्ज करें",
      screen2_desc: "यह 6-अंकीय कोड {name} ने आपके WhatsApp संदेश में भेजा है। इससे साबित होता है कि आपको संदेश मिला है।",
      code_expires_in: "कोड 15 मिनट के लिए मान्य है",
      btn_ask_whatsapp: "WhatsApp पर पूछें 💬",
      btn_check_later: "मैं बाद में देखूँगा",
      btn_submit_code: "कोड की पुष्टि करें",
      why_screen2: "यह कोड सुनिश्चित करता है कि केवल आप ही इस रिकॉर्ड की पुष्टि कर सकते हैं।",

      confirmed_title: "रिकॉर्ड की पुष्टि हो गई",
      confirmed_subtitle: "आगे आप क्या करना चाहते हैं?",
      act_return_date: "बताएं कि आप इसे कब लौटाएंगे",
      act_pay_upi: "अभी UPI द्वारा भुगतान करें",
      act_set_reminder: "अपने लिए रिमाइंडर सेट करें",
      act_i_paid: "मैंने भुगतान कर दिया",
      act_download_receipt: "रसीद डाउनलोड करें",
      why_screen3: "तारीख या रिमाइंडर सेट करने से बिना किसी झिझक के समय पर हिसाब चुकता रहता है।",

      no_install_needed: "आपको कोई ऐप इंस्टॉल करने की ज़रूरत नहीं है।",
      get_the_app: "ऐप प्राप्त करें",

      label_from: "किसने दिया",
      label_to: "किसे मिला",
      label_start_date: "शुरू होने की तारीख",
      label_due_date: "लौटाने की तारीख",
      label_interest: "ब्याज",
      label_note: "नोट",
      label_fingerprint: "रिकॉर्ड का फिंगरप्रिंट",
      advanced_details: "अन्य विवरण",

      state_expired_title: "यह लिंक समाप्त हो चुका है",
      state_expired_desc: "आपकी सुरक्षा के लिए सत्यापन लिंक कुछ समय बाद समाप्त हो जाते हैं।",
      state_expired_action: "{name} से नया लिंक भेजने के लिए कहें।",
      state_revoked_title: "यह लिंक रद्द कर दिया गया है",
      state_revoked_desc: "भेजने वाले ने इस लिंक को रद्द कर दिया है।",
      state_revoked_action: "{name} से पूछें कि क्या उन्होंने नया रिकॉर्ड बनाया है।",
      state_wrong_code_title: "गलत 6-अंकीय कोड",
      state_wrong_code_desc: "दर्ज किया गया कोड भेजे गए कोड से मेल नहीं खाता।",
      state_wrong_code_action: "WhatsApp संदेश में आया कोड जांचें या {name} से पुनः भेजने को कहें।",
      state_too_many_tries_title: "बहुत अधिक प्रयास",
      state_too_many_tries_desc: "गलत कोड कई बार दर्ज किया गया है। कृपया 1 घंटा प्रतीक्षा करें।",
      state_too_many_tries_action: "1 घंटे बाद प्रयास करें, या {name} से नया लिंक मांगें।",
      state_already_confirmed_title: "रिकॉर्ड पहले ही पुष्ट है",
      state_already_confirmed_desc: "इस रिकॉर्ड की पुष्टि {date} को पहले ही की जा चुकी है।",
      state_already_confirmed_action: "आप नीचे दिए गए विवरण देख सकते हैं या रसीद सहेज सकते हैं।",
      state_disputed_title: "समस्या दर्ज की गई",
      state_disputed_desc: "इस रिकॉर्ड में समस्या बताई गई है।",
      state_disputed_action: "कारण: {reason}। आप {name} से सीधे संपर्क करके इसे सुलझा सकते हैं।",
      state_server_error_title: "कुछ गलत हो गया",
      state_server_error_desc: "हम रिकॉर्ड का विवरण लोड नहीं कर सके।",
      state_server_error_action: "कृपया पेज को रीफ्रेश करें या थोड़ी देर बाद प्रयास करें।",

      dispute_sheet_title: "समस्या बताएं",
      dispute_reason_label: "इस रिकॉर्ड में क्या गलत है?",
      reason_wrong_amount: "रकम गलत है",
      reason_wrong_date: "तारीख गलत है",
      reason_never_took: "मैंने यह रकम कभी नहीं ली",
      reason_other: "अन्य समस्या",
      dispute_note_placeholder: "विस्तार से बताएं कि क्या गलत है (300 अक्षरों तक)...",
      btn_submit_dispute: "रिपोर्ट भेजें",

      paid_sheet_title: "मैंने भुगतान कर दिया",
      paid_amount_label: "भुगतान की गई रकम (₹)",
      paid_date_label: "भुगतान की तारीख",
      paid_mode_label: "आपने भुगतान कैसे किया?",
      mode_upi: "UPI",
      mode_cash: "नकद (Cash)",
      mode_bank: "बैंक ट्रांसफर",
      paid_ref_label: "संदर्भ संख्या / नोट (वैकल्पिक)",
      paid_proof_label: "भुगतान का प्रमाण (फोटो / स्क्रीनशॉट)",
      btn_submit_paid: "प्रमाण जमा करें",
      paid_notice: "रकम देने वाले व्यक्ति अपने ऐप में इसकी पुष्टि करेंगे।",

      date_sheet_title: "बताएं कि आप इसे कब लौटाएंगे",
      date_label: "लौटाने की तारीख",
      date_note_label: "संदेश (वैकल्पिक)",
      date_note_placeholder: "उदाहरण: मैं इस शुक्रवार आधा और बाकी अगले महीने लौटाऊंगा",
      btn_submit_date: "प्रस्तावित तारीख भेजें",
      date_notice: "यह एक प्रस्ताव है जिसे {name} अपने ऐप में स्वीकार या अस्वीकार करेंगे।",

      reminder_sheet_title: "अपने लिए रिमाइंडर सेट करें",
      reminder_desc: "अंतिम तारीख से पहले अलर्ट प्राप्त करें ताकि आप कभी न भूलें।",
      btn_google_cal: "गूगल कैलेंडर में जोड़ें",
      btn_ics_cal: "कैलेंडर फ़ाइल (.ics) डाउनलोड करें",
      btn_browser_notify: "ब्राउज़र सूचना चालू करें",
      notify_disclaimer: "जब तक आप अनुमति नहीं देंगे, उधार आपको सूचनाएं नहीं भेज सकता।",
      notify_enabled: "ब्राउज़र सूचनाएं चालू हैं!",

      upi_sheet_title: "अभी UPI द्वारा भुगतान करें",
      upi_scan_qr: "GPay, PhonePe, Paytm या BHIM से स्कैन करें",
      upi_id_label: "UPI ID",
      btn_copy_upi: "UPI ID कॉपी करें",
      toast_upi_copied: "UPI ID कॉपी हो गई!",
      btn_pay_app: "अभी भुगतान करें",
      upi_warning: "भुगतान करने से पहले अपने UPI ऐप में प्राप्तकर्ता का नाम अवश्य जांचें।",
      upi_confirmation_question: "क्या आपका भुगतान पूरा हो गया?",
      btn_payment_yes: "हाँ, भुगतान हो गया",
      btn_payment_no: "नहीं, अभी नहीं",

      toast_copied: "क्लिपबोर्ड पर कॉपी किया गया!",
      toast_success: "सफलतापूर्वक सहेजा गया!",
      toast_network_error: "सर्वर से संपर्क नहीं हो सका। अपना इंटरनेट जांचें।"
    },

    bn: {
      brand_name: "উধার",
      brand_sub: "ওয়েব",
      select_lang: "ভাষা বাছুন",
      toggle_theme: "থিম পরিবর্তন",
      offline_banner: "আপনি বর্তমানে অফলাইনে আছেন। ইন্টারনেট সংযোগ পরীক্ষা করুন।",
      demo_banner: "ডেমো প্রিভিউ — কোনো আসল তথ্য সেভ হয় না।",
      btn_retry: "পুনরায় চেষ্টা করুন",

      badge_link_verified: "ব্যক্তিগত লিঙ্ক",
      badge_code_checked: "কোড যাচাইকৃত",
      badge_app_confirmed: "অ্যাপে নিশ্চিত",
      badge_due_today: "আজই প্রদেয়",
      badge_due_in_days: "{days} দিনের মধ্যে প্রদেয়",
      badge_overdue_days: "{days} দিন মেয়াদোত্তীর্ণ",
      badge_paid_back: "পরিশোধিত",
      badge_installments: "কিস্তি: {paid} / {total} পরিশোধিত",
      badge_next_due: "পরবর্তী কিস্তি: {amount}",

      amount_lent_label: "দেওয়া টাকা",
      amount_borrowed_label: "নেওয়া টাকা",
      amount_generic_label: "টাকার পরিমাণ",
      question_lent: "{name} বলেছেন তিনি আপনাকে {amt} ধার দিয়েছেন। এটি কি সঠিক?",
      question_borrowed: "{name} বলেছেন তিনি আপনার থেকে {amt} ধার নিয়েছেন। এটি কি সঠিক?",
      btn_yes_correct: "✓ হ্যাঁ, এটি সঠিক",
      btn_something_wrong: "✕ কিছু ভুল আছে",
      why_title: "আমরা এটি কেন জিজ্ঞেস করি",
      why_screen1: "এটি নিশ্চিত করলে আপনারা দুজনেই একই হিসাব দেখতে পাবেন, ফলে পরে কোনো ভুল বোঝাবুঝি হবে না।",

      screen2_title: "{name} এর পাঠানো কোডটি লিখুন",
      screen2_desc: "এই ৬-সংখ্যার কোডটি {name} আপনার WhatsApp বার্তায় পাঠিয়েছেন। এটি প্রমাণ করে যে আপনি বার্তাটি পেয়েছেন।",
      code_expires_in: "কোডটি ১৫ মিনিটের জন্য বৈধ",
      btn_ask_whatsapp: "WhatsApp-এ জিজ্ঞাসা করুন 💬",
      btn_check_later: "আমি পরে দেখব",
      btn_submit_code: "কোড নিশ্চিত করুন",
      why_screen2: "এই কোডটি নিশ্চিত করে যে শুধুমাত্র আপনিই এই হিসাব নিশ্চিত করতে পারবেন।",

      confirmed_title: "হিসাব নিশ্চিত হয়েছে",
      confirmed_subtitle: "এরপর আপনি কী করতে চান?",
      act_return_date: "ফেরতের তারিখ জানান",
      act_pay_upi: "এখনই UPI দিয়ে পরিশোধ করুন",
      act_set_reminder: "নিজের জন্য অনুস্মারক সেট করুন",
      act_i_paid: "আমি পরিশোধ করেছি",
      act_download_receipt: "রসিদ ডাউনলোড করুন",
      why_screen3: "ফেরতের তারিখ বা অনুস্মারক সেট করলে সময়মতো টাকা ফেরত দেওয়া সহজ হয়।",

      no_install_needed: "আপনাকে কোনো অ্যাপ ইনস্টল করতে হবে না।",
      get_the_app: "অ্যাপটি নিন",

      label_from: "প্রাপক",
      label_to: "প্রদানকারী",
      label_start_date: "শুরুর তারিখ",
      label_due_date: "ফেরতের তারিখ",
      label_interest: "সুদ",
      label_note: "নোট",
      label_fingerprint: "রেকর্ডের ডিজিটাল ছাপ",
      advanced_details: "অন্যান্য বিবরণ",

      state_expired_title: "এই লিঙ্কের মেয়াদ শেষ",
      state_expired_desc: "আপনার সুরক্ষার জন্য লিঙ্কের মেয়াদ নির্দিষ্ট সময় পর শেষ হয়ে যায়।",
      state_expired_action: "{name} কে নতুন লিঙ্ক পাঠাতে বলুন।",
      state_revoked_title: "লিঙ্কটি বাতিল করা হয়েছে",
      state_revoked_desc: "যিনি পাঠিয়েছিলেন তিনি লিঙ্কটি বাতিল করেছেন।",
      state_revoked_action: "{name} এর সাথে কথা বলে নিশ্চিত হন।",
      state_wrong_code_title: "ভুল ৬-সংখ্যার কোড",
      state_wrong_code_desc: "আপনার দেওয়া কোডটি পাঠানো কোডের সাথে মেলেনি।",
      state_wrong_code_action: "WhatsApp বার্তা দেখে সঠিক কোড দিন অথবা {name} কে আবার পাঠাতে বলুন।",
      state_too_many_tries_title: "অনেকবার ভুল চেষ্টা",
      state_too_many_tries_desc: "একাধিকবার ভুল কোড দেওয়া হয়েছে। দয়া করে ১ ঘণ্টা অপেক্ষা করুন।",
      state_too_many_tries_action: "১ ঘণ্টা পর চেষ্টা করুন, অথবা {name} কে নতুন লিঙ্ক পাঠাতে বলুন।",
      state_already_confirmed_title: "ইতিমধ্যেই নিশ্চিত করা হয়েছে",
      state_already_confirmed_desc: "এই লেনদেনটি {date} তারিখে নিশ্চিত করা হয়েছে।",
      state_already_confirmed_action: "আপনি নিচে বিস্তারিত দেখতে পারেন অথবা রসিদ সংরক্ষণ করতে পারেন।",
      state_disputed_title: "সমস্যা জানানো হয়েছে",
      state_disputed_desc: "এই লেনদেনে কোনো ভুল রিপোর্ট করা হয়েছে।",
      state_disputed_action: "কারণ: {reason}। সমাধানের জন্য {name} এর সাথে সরাসরি যোগাযোগ করুন।",
      state_server_error_title: "কিছু সমস্যা হয়েছে",
      state_server_error_desc: "তথ্য লোড করা সম্ভব হয়নি।",
      state_server_error_action: "পৃষ্ঠাটি রিফ্রেশ করুন বা কিছুক্ষণ পর চেষ্টা করুন।",

      dispute_sheet_title: "সমস্যা জানান",
      dispute_reason_label: "এই হিসাবে কী ভুল আছে?",
      reason_wrong_amount: "টাকার পরিমাণ ভুল",
      reason_wrong_date: "তারিখ ভুল",
      reason_never_took: "আমি কখনো এই টাকা নিইনি",
      reason_other: "অন্যান্য সমস্যা",
      dispute_note_placeholder: "কী ভুল হয়েছে সংক্ষেপে লিখুন (৩০০ অক্ষরের মধ্যে)...",
      btn_submit_dispute: "রিপোর্ট পাঠান",

      paid_sheet_title: "আমি পরিশোধ করেছি",
      paid_amount_label: "পরিশোধিত টাকা (₹)",
      paid_date_label: "পরিশোধের তারিখ",
      paid_mode_label: "কীভাবে পরিশোধ করেছেন?",
      mode_upi: "UPI",
      mode_cash: "নগদ (Cash)",
      mode_bank: "ব্যাংক ট্রান্সফার",
      paid_ref_label: "রেফারেন্স নম্বর / নোট (ঐচ্ছিক)",
      paid_proof_label: "প্রমাণের ছবি (রসিদ বা স্ক্রিনশট)",
      btn_submit_paid: "প্রমাণ জমা দিন",
      paid_notice: "টাকা যিনি পাবেন তিনি অ্যাপে এটি নিশ্চিত করবেন।",

      date_sheet_title: "ফেরতের তারিখ জানান",
      date_label: "ফেরতের তারিখ",
      date_note_label: "বার্তা (ঐচ্ছিক)",
      date_note_placeholder: "উদাহরণ: এই শুক্রবারে অর্ধেক দেব, বাকিটা পরের মাসে",
      btn_submit_date: "প্রস্তাবিত তারিখ পাঠান",
      date_notice: "এটি একটি প্রস্তাব যা {name} তার অ্যাপে গ্রহণ বা প্রত্যাখ্যান করবেন।",

      reminder_sheet_title: "নিজের জন্য অনুস্মারক সেট করুন",
      reminder_desc: "সময়মতো সতর্কবার্তা পান যাতে ভুলে না যান।",
      btn_google_cal: "Google ক্যালেন্ডারে যোগ করুন",
      btn_ics_cal: "ক্যালেন্ডার ফাইল (.ics) ডাউনলোড করুন",
      btn_browser_notify: "ব্রাউজার নোটিফিকেশন চালু করুন",
      notify_disclaimer: "আপনি সম্মতি না দিলে উধার কোনো বার্তা পাঠাতে পারবে না।",
      notify_enabled: "ব্রাউজার নোটিফিকেশন সক্রিয় রয়েছে!",

      upi_sheet_title: "এখনই UPI দিয়ে পরিশোধ করুন",
      upi_scan_qr: "GPay, PhonePe, Paytm বা BHIM দিয়ে স্ক্যান করুন",
      upi_id_label: "UPI ID",
      btn_copy_upi: "UPI ID কপি করুন",
      toast_upi_copied: "UPI ID কপি করা হয়েছে!",
      btn_pay_app: "এখনই পরিশোধ করুন",
      upi_warning: "টাকা পাঠানোর আগে আপনার UPI অ্যাপে প্রাপকের নাম মিলিয়ে নিন।",
      upi_confirmation_question: "টাকা কি সফলভাবে পৌঁছেছে?",
      btn_payment_yes: "হ্যাঁ, পরিশোধ সম্পন্ন হয়েছে",
      btn_payment_no: "না, এখনো হয়নি",

      toast_copied: "কপিতে সংরক্ষিত!",
      toast_success: "সফলভাবে সংরক্ষিত!",
      toast_network_error: "সার্ভারের সাথে সংযোগ করা যায়নি। ইন্টারনেট পরীক্ষা করুন।"
    },

    as: {
      brand_name: "উধাৰ",
      brand_sub: "ৱেব",
      select_lang: "ভাষা বাছক",
      toggle_theme: "থিম সলনি কৰক",
      offline_banner: "আপুনি বৰ্তমান অফলাইনত আছে। ইণ্টাৰনেট সংযোগ পৰীক্ষা কৰক।",
      demo_banner: "ডেমো প্রিভিউ — কোনো প্ৰকৃত তথ্য সংৰক্ষণ কৰা নহয়।",
      btn_retry: "পুনৰ চেষ্টা কৰক",

      badge_link_verified: "ব্যক্তিগত লিংক",
      badge_code_checked: "ক'ড পৰীক্ষিত",
      badge_app_confirmed: "এপত নিশ্চিত",
      badge_due_today: "আজি দিবলগীয়া",
      badge_due_in_days: "{days} দিন বাকী আছে",
      badge_overdue_days: "{days} দিন পলম হৈছে",
      badge_paid_back: "পৰিশোধ কৰা",
      badge_installments: "কিস্তি: {paid} / {total} পৰিশোধিত",
      badge_next_due: "পৰৱৰ্তী কিস্তি: {amount}",

      amount_lent_label: "দিয়া ধন",
      amount_borrowed_label: "লোৱা ধন",
      amount_generic_label: "টকাৰ পৰিমাণ",
      question_lent: "{name}ই আপোনাক {amt} ধাৰলৈ দিয়া বুলি কৈছে। এইটো সঠিকনে?",
      question_borrowed: "{name}ই আপোনাৰ পৰা {amt} ধাৰলৈ লোৱা বুলি কৈছে। এইটো সঠিকনে?",
      btn_yes_correct: "✓ হয়, এইটো সঠিক",
      btn_something_wrong: "✕ কিবা ভুল আছে",
      why_title: "আমি এইটো কিয় সোধোঁ",
      why_screen1: "এইটো নিশ্চিত কৰিলে দুয়োপক্ষই একেই হিচাপ দেখিব আৰু পাছত কোনো ভুল বুজাবুজি নহয়।",

      screen2_title: "{name}ই পঠোৱা ক'ডটো দিয়ক",
      screen2_desc: "এই ৬-অংকৰ ক'ডটো {name}ই আপোনাৰ WhatsApp মেছেজত পঠিয়াইছে। ই আপুনি মেছেজ পোৱাটো নিশ্চিত কৰে।",
      code_expires_in: "ক'ডটো ১৫ মিনিটৰ বাবে বৈধ",
      btn_ask_whatsapp: "WhatsAppত সোধক 💬",
      btn_check_later: "মই পাছত চাম",
      btn_submit_code: "ক'ড নিশ্চিত কৰক",
      why_screen2: "এই ক'ডটোৱে নিশ্চিত কৰে যে কেৱল আপুনিয়েহে এই হিচাপ অনুমোদন কৰিব পাৰে।",

      confirmed_title: "হিচাপ নিশ্চিত হ'ল",
      confirmed_subtitle: "ইয়াৰ পাছত আপুনি কি কৰিব বিচাৰে?",
      act_return_date: "ঘূৰাই দিয়াৰ তাৰিখ জনাওক",
      act_pay_upi: "এতিয়াই UPIৰে পৰিশোধ কৰক",
      act_set_reminder: "মনত পেলাই দিয়াৰ সংকেত দিয়ক",
      act_i_paid: "মই পৰিশোধ কৰিলোঁ",
      act_download_receipt: "ৰচিদ ডাউনলোড কৰক",
      why_screen3: "তাৰিখ বা ৰিমাইণ্ডাৰ ছেট কৰিলে পাহৰি যোৱাৰ সম্ভাৱনা নাথাকে।",

      no_install_needed: "আপুনি কোনো এপ্ ইনষ্টল কৰিব নালাগে।",
      get_the_app: "এপ্ লওক",

      label_from: "প্ৰদানকাৰী",
      label_to: "গ্ৰহীতা",
      label_start_date: "আৰম্ভণিৰ তাৰিখ",
      label_due_date: "ঘূৰাই দিয়াৰ তাৰিখ",
      label_interest: "সুদ",
      label_note: "নোট",
      label_fingerprint: "ৰেকৰ্ডৰ চিন",
      advanced_details: "অতিৰিক্ত তথ্য",

      state_expired_title: "এই লিংকৰ ম্যাদ উকলিল",
      state_expired_desc: "আপোনাৰ সুৰক্ষাৰ বাবে লিংকৰ ম্যাদ কিছু সময় পাছত শেষ হয়।",
      state_expired_action: "{name}ক নতুন লিংক পঠিয়াবলৈ কওক।",
      state_revoked_title: "এই লিংক বাতিল কৰা হৈছে",
      state_revoked_desc: "প্ৰেৰকে এই লিংকটো বাতিল কৰিছে।",
      state_revoked_action: "{name}ৰ সৈতে যোগাযোগ কৰক।",
      state_wrong_code_title: "ভুল ৬-অংকৰ ক'ড",
      state_wrong_code_desc: "দিয়া ক'ডটো পঠোৱা ক'ডৰ লগত মিলা নাই।",
      state_wrong_code_action: "WhatsApp মেছেজ চাই সঠিক ক'ড দিয়ক নাইবা {name}ক পুনৰ পঠিয়াবলৈ কওক।",
      state_too_many_tries_title: "অত্যাধিক ভুল চেষ্টা",
      state_too_many_tries_desc: "বহুবাৰ ভুল ক'ড দিয়া হৈছে। অনুগ্ৰহ কৰি ১ ঘণ্টা অপেক্ষা কৰক।",
      state_too_many_tries_action: "১ ঘণ্টাৰ পাছত চেষ্টা কৰক বা নতুন লিংক লওক।",
      state_already_confirmed_title: "ইতিমধ্যে নিশ্চিত কৰা হৈছে",
      state_already_confirmed_desc: "এই লেনদেনটো {date} তাৰিখে ইতিমধ্যে নিশ্চিত কৰা হৈছে।",
      state_already_confirmed_action: "আপুনি তলত সবিশেষ চাব পাৰে বা ৰচিদ সংৰক্ষণ কৰিব পাৰে।",
      state_disputed_title: "সমস্যা জনোৱা হৈছে",
      state_disputed_desc: "এই লেনদেনত কোনো ভুল থকা বুলি জনোৱা হৈছে।",
      state_disputed_action: "কাৰণ: {reason}। সমাধানৰ বাবে {name}ৰ লগত কথা পাতক।",
      state_server_error_title: "কিবা ভুল হ'ল",
      state_server_error_desc: "তথ্য লোড কৰিব পৰা নগ'ল।",
      state_server_error_action: "পৃষ্ঠাটো ৰিফ্ৰেছ কৰক বা কিছু সময় পাছত চেষ্টা কৰক।",

      dispute_sheet_title: "সমস্যা জনাওক",
      dispute_reason_label: "এই হিচাপত কি ভুল আছে?",
      reason_wrong_amount: "টকাৰ পৰিমাণ ভুল",
      reason_wrong_date: "তাৰিখ ভুল",
      reason_never_took: "মই এই টকা লোৱা নাই",
      reason_other: "অন্য সমস্যা",
      dispute_note_placeholder: "কি ভুল হৈছে চমুকৈ লিখক (৩০০ আখৰৰ ভিতৰত)...",
      btn_submit_dispute: "ৰিৰ্পোট পঠিয়াওক",

      paid_sheet_title: "মই পৰিশোধ কৰিলোঁ",
      paid_amount_label: "পৰিশোধ কৰা টকা (₹)",
      paid_date_label: "পৰিশোধৰ তাৰিখ",
      paid_mode_label: "কেনেকৈ পৰিশোধ কৰিলে?",
      mode_upi: "UPI",
      mode_cash: "নগদ (Cash)",
      mode_bank: "বেংক ট্ৰান্সফাৰ",
      paid_ref_label: "ৰেফাৰেন্স নম্বৰ / নোট (ঐচ্ছিক)",
      paid_proof_label: "প্ৰমাণৰ ফটো (ৰচিদ / স্ক্ৰীণশ্বট)",
      btn_submit_paid: "প্ৰমাণ জমা দিয়ক",
      paid_notice: "প্ৰাপকে তেওঁৰ এপত এই ধন পোৱা বুলি নিশ্চিত কৰিব।",

      date_sheet_title: "ঘূৰাই দিয়াৰ তাৰিখ জনাওক",
      date_label: "ঘূৰাই দিয়াৰ তাৰিখ",
      date_note_label: "বাৰ্তা (ঐচ্ছিক)",
      date_note_placeholder: "উদাহৰণ: মই এই শুকুৰবাৰে আধা দিম আৰু বাকীখিনি অহা মাহত",
      btn_submit_date: "প্ৰস্তাৱিত তাৰিখ পঠিয়াওক",
      date_notice: "এইটো এটা প্ৰস্তাৱ যাক {name}ই তেওঁৰ এপত গ্ৰহণ বা অগ্ৰাহ্য কৰিব পাৰে।",

      reminder_sheet_title: "মনত পেলাই দিয়াৰ সংকেত দিয়ক",
      reminder_desc: "সময়মতে সংকেত পাওক যাতে পাহৰি নাযায়।",
      btn_google_cal: "Google কেলেণ্ডাৰত যোগ কৰক",
      btn_ics_cal: "কেলেণ্ডাৰ নথিপত্ৰ (.ics) ডাউনল'ড কৰক",
      btn_browser_notify: "ব্ৰাউজাৰৰ জাননী আৰম্ভ কৰক",
      notify_disclaimer: "আপুনি অনুমতি নিদিয়া পৰ্যন্ত উধাৰে জাননী পঠিয়াব নোৱাৰে।",
      notify_enabled: "ব্ৰাউজাৰৰ জাননী সক্ৰিয় হৈছে!",

      upi_sheet_title: "এতিয়াই UPIৰে পৰিশোধ কৰক",
      upi_scan_qr: "GPay, PhonePe, Paytm বা BHIMৰে স্কেন কৰক",
      upi_id_label: "UPI ID",
      btn_copy_upi: "UPI ID কপি কৰক",
      toast_upi_copied: "UPI ID কপি কৰা হ'ল!",
      btn_pay_app: "এতিয়াই পৰিশোধ কৰক",
      upi_warning: "পৰিশোধ কৰাৰ আগতে UPI এপত নাম পৰীক্ষা কৰি লওক।",
      upi_confirmation_question: "টকা পৰিশোধ সম্পন্ন হ'লনে?",
      btn_payment_yes: "হয়, পৰিশোধ হৈ গ'ল",
      btn_payment_no: "নাই, এতিয়াও হোৱা নাই",

      toast_copied: "ক্লিপবৰ্ডত সংৰক্ষিত!",
      toast_success: "সফলভাৱে সংৰক্ষণ হ'ল!",
      toast_network_error: "চাৰ্ভাৰৰ সৈতে সংযোগ হোৱা নাই। ইণ্টাৰনেট পৰীক্ষা কৰক।"
    },

    ne: {
      brand_name: "उधार",
      brand_sub: "वेब",
      select_lang: "भाषा छान्नुहोस्",
      toggle_theme: "थिम बदल्नुहोस्",
      offline_banner: "तपाईं अहिले अफलाइन हुनुहुन्छ। इन्टरनेट जाँच गर्नुहोस्।",
      demo_banner: "डेमो मोड — कुनै वास्तविक डाटा सुरक्षित हुँदैन।",
      btn_retry: "पुनः प्रयास गर्नुहोस्",

      badge_link_verified: "निजी लिङ्क",
      badge_code_checked: "कोड जाँचियो",
      badge_app_confirmed: "एपमा पुष्टि भयो",
      badge_due_today: "आजै भुक्तानी गर्नुपर्ने",
      badge_due_in_days: "{days} दिनमा भुक्तानी योग्य",
      badge_overdue_days: "{days} दिन ढिलो भएको",
      badge_paid_back: "चुक्ता भयो",
      badge_installments: "किस्ता: {paid} / {total} चुक्ता",
      badge_next_due: "अर्को किस्ता: {amount}",

      amount_lent_label: "दिएको रकम",
      amount_borrowed_label: "लिएको रकम",
      amount_generic_label: "रकम",
      question_lent: "{name} ले तपाईंलाई {amt} दिनुभएको छ भन्नुभएको छ। के यो सही हो?",
      question_borrowed: "{name} ले तपाईंसँग {amt} लिनुभएको छ भन्नुभएको छ। के यो सही हो?",
      btn_yes_correct: "✓ हो, यो सही छ",
      btn_something_wrong: "✕ केही गल्ती छ",
      why_title: "हामी यो किन सोध्छौं",
      why_screen1: "यो पुष्टि गर्दा तपाईं दुवैले एउटै हिसाब देख्नुहुनेछ र पछि कुनै असमझदारी हुनेछैन।",

      screen2_title: "{name} ले पठाएको कोड प्रविष्ट गर्नुहोस्",
      screen2_desc: "यो ६-अङ्कको कोड {name} ले तपाईंको WhatsApp मा पठाउनुभएको छ। यसले तपाईंले सन्देश पाउनुभएको पुष्टि गर्दछ।",
      code_expires_in: "कोड १५ मिनेटका लागि मान्य छ",
      btn_ask_whatsapp: "WhatsApp मा सोध्नुहोस् 💬",
      btn_check_later: "म पछि हेर्नेछु",
      btn_submit_code: "कोड पुष्टि गर्नुहोस्",
      why_screen2: "यो कोडले तपाईंले मात्र यो अभिलेख पुष्टि गर्न सक्नुहुन्छ भन्ने सुनिश्चित गर्दछ।",

      confirmed_title: "अभिलेख पुष्टि भयो",
      confirmed_subtitle: "अब तपाईं के गर्न चाहनुहुन्छ?",
      act_return_date: "फिर्ता गर्ने मिति बताउनुहोस्",
      act_pay_upi: "अहिले नै UPI बाट तिर्नुहोस्",
      act_set_reminder: "आफ्नो लागि रिमाइन्डर राख्नुहोस्",
      act_i_paid: "मैले तिरिसकें",
      act_download_receipt: "रसिद डाउनलोड गर्नुहोस्",
      why_screen3: "मिति वा रिमाइन्डर सेट गर्नाले समयमै हिसाब चुक्ता गर्न मद्दत पुग्छ।",

      no_install_needed: "तपाईंले कुनै एप इन्स्टल गर्नुपर्दैन।",
      get_the_app: "एप पाउनुहोस्",

      label_from: "कसले दियो",
      label_to: "कसले पायो",
      label_start_date: "सुरु मिति",
      label_due_date: "भाका मिति",
      label_interest: "ब्याज",
      label_note: "कैफियत",
      label_fingerprint: "रेकर्डको फिंगरप्रिन्ट",
      advanced_details: "थप विवरण",

      state_expired_title: "यो लिङ्कको म्याद सकियो",
      state_expired_desc: "तपाईंको सुरक्षाका लागि लिङ्कको म्याद केही समयपछि समाप्त हुन्छ।",
      state_expired_action: "{name} लाई नयाँ लिङ्क पठाउन भन्नुहोस्।",
      state_revoked_title: "यो लिङ्क रद्द गरिएको छ",
      state_revoked_desc: "पठाउने व्यक्तिले यो लिङ्क रद्द गर्नुभएको छ।",
      state_revoked_action: "{name} सँग सम्पर्क गर्नुहोस्।",
      state_wrong_code_title: "गलत ६-अङ्कको कोड",
      state_wrong_code_desc: "प्रविष्ट गरिएको कोड मिलेन।",
      state_wrong_code_action: "WhatsApp सन्देश जाँच गर्नुहोस् वा {name} लाई पुनः पठाउन भन्नुहोस्।",
      state_too_many_tries_title: "धेरै प्रयासहरू",
      state_too_many_tries_desc: "धेरै पटक गलत कोड हालियो। कृपया १ घण्टा पर्खनुहोस्।",
      state_too_many_tries_action: "१ घण्टापछि प्रयास गर्नुहोस्, वा नयाँ लिङ्क माग्नुहोस्।",
      state_already_confirmed_title: "अभिलेख पहिले नै पुष्ट छ",
      state_already_confirmed_desc: "यो अभिलेख {date} मा पहिले नै पुष्टि भइसकेको छ।",
      state_already_confirmed_action: "तपाईं तल विवरण हेर्न वा रसिद डाउनलोड गर्न सक्नुहुन्छ।",
      state_disputed_title: "समस्या दर्ता गरियो",
      state_disputed_desc: "यस अभिलेखमा समस्या दर्ता गरिएको छ।",
      state_disputed_action: "कारण: {reason}। समाधानका लागि {name} सँग कुरा गर्नुहोस्।",
      state_server_error_title: "केही गल्ती भयो",
      state_server_error_desc: "विवरण लोड गर्न सकिएन।",
      state_server_error_action: "कृपया पृष्ठ ताजा गर्नुहोस् वा केही समयपछि प्रयास गर्नुहोस्।",

      dispute_sheet_title: "समस्या दर्ता गर्नुहोस्",
      dispute_reason_label: "यो अभिलेखमा के गल्ती छ?",
      reason_wrong_amount: "रकम गलत छ",
      reason_wrong_date: "मिति गलत छ",
      reason_never_took: "मैले यो रकम कहिल्यै लिएको छैन",
      reason_other: "अन्य समस्या",
      dispute_note_placeholder: "के गल्ती छ स्पष्ट लेख्नुहोस् (३०० अक्षर सम्म)...",
      btn_submit_dispute: "रिपोर्ट पठाउनुहोस्",

      paid_sheet_title: "मैले तिरिसकें",
      paid_amount_label: "तिरेको रकम (रू/₹)",
      paid_date_label: "तिरेको मिति",
      paid_mode_label: "कसरी तिर्नुभयो?",
      mode_upi: "UPI / अनलाइन",
      mode_cash: "नगद (Cash)",
      mode_bank: "बैंक ट्रान्सफर",
      paid_ref_label: "सन्दर्भ नम्बर / नोट (वैकल्पिक)",
      paid_proof_label: "प्रमाणको फोटो (रसिद / स्क्रिनसट)",
      btn_submit_paid: "प्रमाण पेस गर्नुहोस्",
      paid_notice: "रकम पाउने व्यक्तिले आफ्नो एपमा पुष्टि गर्नुहुनेछ।",

      date_sheet_title: "फिर्ता गर्ने मिति बताउनुहोस्",
      date_label: "फिर्ता मिति",
      date_note_label: "सन्देश (वैकल्पिक)",
      date_note_placeholder: "उदाहरण: म यो शुक्रबार आधा र बाँकी अर्को महिना तिर्नेछु",
      btn_submit_date: "प्रस्तावित मिति पठाउनुहोस्",
      date_notice: "यो एक प्रस्ताव हो जसलाई {name} ले आफ्नो एपमा स्वीकार वा अस्वीकार गर्नुहुनेछ।",

      reminder_sheet_title: "आफ्नो लागि रिमाइन्डर राख्नुहोस्",
      reminder_desc: "भाका मिति अगावै जानकारी पाउनुहोस् ताकि नबिर्सिनुहोस्।",
      btn_google_cal: "Google क्यालेन्डरमा थप्नुहोस्",
      btn_ics_cal: "क्यालेन्डर फाइल (.ics) डाउनलोड गर्नुहोस्",
      btn_browser_notify: "ब्राउजर सूचना अन गर्नुहोस्",
      notify_disclaimer: "तपाईंले अनुमति नदिएसम्म उधारले सूचनाहरू पठाउन सक्दैन।",
      notify_enabled: "ब्राउजर सूचनाहरू सक्रिय छन्!",

      upi_sheet_title: "अहिले नै UPI बाट तिर्नुहोस्",
      upi_scan_qr: "GPay, PhonePe वा Paytm बाट स्क्यान गर्नुहोस्",
      upi_id_label: "UPI ID",
      btn_copy_upi: "UPI ID कपी गर्नुहोस्",
      toast_upi_copied: "UPI ID कपी भयो!",
      btn_pay_app: "अहिले तिर्नुहोस्",
      upi_warning: "भुक्तानी गर्नु अघि UPI एपमा प्रापकको नाम राम्ररी जाँच्नुहोस्।",
      upi_confirmation_question: "के भुक्तानी सफल भयो?",
      btn_payment_yes: "हो, भुक्तानी भयो",
      btn_payment_no: "छैन, भएको छैन",

      toast_copied: "क्लिपबोर्डमा कपी भयो!",
      toast_success: "सफलतापूर्वक सुरक्षित भयो!",
      toast_network_error: "सर्भरसँग सम्पर्क हुन सकेन। इन्टरनेट जाँच गर्नुहोस्।"
    }
  };

  // State
  let currentLang = (typeof localStorage !== "undefined" && localStorage.getItem("udhaar_lang")) || "en";
  if (!I18N[currentLang]) currentLang = "en";

  let recordData = null;
  let urlRecordId = "";
  let urlSecret = "";
  let isDemoMode = false;
  let codeTimerInterval = null;
  let codeExpiresAtMillis = 0;

  // 2. Localization Helper
  function t(key, params = {}) {
    const dict = I18N[currentLang] || I18N.en;
    let str = dict[key] || I18N.en[key] || key;
    for (const [k, v] of Object.entries(params)) {
      str = str.replace(new RegExp(`\\{${k}\\}`, "g"), v);
    }
    return str;
  }

  function announce(msg, isError = false) {
    const el = document.getElementById(isError ? "error-announcer" : "live-announcer");
    if (el) {
      el.textContent = "";
      setTimeout(() => { el.textContent = msg; }, 50);
    }
  }

  function formatCurrency(val, currency = "INR") {
    let amount = Number(val || 0);
    // If integer and >= 100000, it's stored in paise: convert to rupees
    if (amount >= 100000 && Number.isInteger(amount)) {
      amount = amount / 100;
    }
    const sym = currency === "NPR" ? "रू" : "₹";
    return sym + amount.toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 2 });
  }

  function formatDate(millis) {
    if (!millis || millis <= 0) return "—";
    const d = new Date(millis);
    return d.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
  }

  let testState = "";

  // 3. Extract Route Parameters (Hash-only secret token)
  function parseParams() {
    const pathname = (typeof window !== "undefined" && window.location.pathname) || "";
    const search = (typeof window !== "undefined" && window.location.search) || "";
    const hash = (typeof window !== "undefined" && window.location.hash.replace(/^#/, "").trim()) || "";
    const isDemo = search.includes("demo=true") || hash.includes("demo") || pathname.includes("/demo") || pathname.endsWith("/demo");
    const urlParams = new URLSearchParams(search);
    testState = urlParams.get("state") || "";
    let recId = "";
    if (pathname.includes("/u/r/")) {
      recId = pathname.substring(pathname.indexOf("/u/r/") + 5).split("/")[0].split("?")[0].split("#")[0];
    } else if (pathname.includes("/u/")) {
      recId = pathname.substring(pathname.indexOf("/u/") + 3).split("/")[0].split("?")[0].split("#")[0];
    }

    // Secret token strictly from fragment
    return { recordId: recId, secret: hash, isDemo };
  }

  // 4. API Invocation
  async function apiCall(endpoint, payload) {
    if (isDemoMode) {
      if (endpoint === "verifyGuestPhoneOtp") {
        return { success: true, trustLevel: "Code checked" };
      }
      if (endpoint === "guestConfirm") {
        return { success: true, trustLevel: "Private link" };
      }
      if (endpoint === "renewGuestCode") {
        return { success: true, expiresAtMillis: Date.now() + 15 * 60 * 1000 };
      }
      return { success: true };
    }
    const resp = await fetch("/api/" + endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    const data = await resp.json().catch(() => ({}));
    if (!resp.ok) {
      const err = new Error(data.error || ("Request failed with status " + resp.status));
      err.status = resp.status;
      err.data = data;
      throw err;
    }
    return data;
  }

  // 5. DOM Binders & Language Translation
  function applyTranslations() {
    document.documentElement.lang = currentLang;

    // Apply data-i18n attributes
    document.querySelectorAll("[data-i18n]").forEach((el) => {
      const key = el.getAttribute("data-i18n");
      el.textContent = t(key);
    });

    // Apply data-i18n-placeholder attributes
    document.querySelectorAll("[data-i18n-placeholder]").forEach((el) => {
      const key = el.getAttribute("data-i18n-placeholder");
      el.placeholder = t(key);
    });

    // Update select element
    const langSelect = document.getElementById("lang-select");
    if (langSelect) langSelect.value = currentLang;

    // Re-render current record if loaded
    if (recordData) {
      renderRecordView(recordData);
    }
  }

  // 6. View State Controllers
  function showView(viewId) {
    const views = ["loading-view", "screen-step1", "screen-step2", "screen-step3", "error-view"];
    views.forEach((id) => {
      const el = document.getElementById(id);
      if (el) el.style.display = id === viewId ? "flex" : "none";
    });
  }

  function showErrorState(titleKey, descKey, actionKey, params = {}) {
    showView("error-view");
    document.getElementById("error-title").textContent = t(titleKey, params);
    document.getElementById("error-desc").textContent = t(descKey, params);
    document.getElementById("error-action").textContent = t(actionKey, params);
    announce(t(titleKey, params) + ". " + t(actionKey, params), true);
  }

  // 7. Render Record Screen Flow
  function renderRecordView(rec) {
    recordData = rec;
    const isLender = rec.counterpartyRole === "BORROWER"; // Sender is lender, guest is borrower
    const myRole = isLender ? "BORROWER" : "LENDER";
    const otherName = isLender ? (rec.lenderName || rec.creatorName || rec.otherPartyName || "The other person") : (rec.borrowerName || rec.counterpartyName || "The other person");
    const rawAmt = (rec.principalAmount !== undefined && rec.principalAmount !== null)
      ? rec.principalAmount
      : (rec.amountPaise !== undefined ? rec.amountPaise / 100 : 0);
    const amountStr = formatCurrency(rawAmt, rec.currency);

    // Populate common data grid
    document.getElementById("val-from").textContent = rec.lenderName || "—";
    document.getElementById("val-to").textContent = rec.borrowerName || "—";
    document.getElementById("val-start-date").textContent = formatDate(rec.startDateMillis);
    document.getElementById("val-due-date").textContent = formatDate(rec.expiryDateMillis);
    document.getElementById("val-fingerprint").textContent = (rec.contentHash || "—").substring(0, 16) + "...";

    // Due badge logic
    const dueBadge = document.getElementById("due-status-badge");
    const now = Date.now();
    if (rec.status === "CONFIRMED" || rec.status === "SETTLED") {
      dueBadge.className = "badge badge-green";
      dueBadge.textContent = t("badge_paid_back");
    } else if (rec.expiryDateMillis && rec.expiryDateMillis > 0) {
      const diffDays = Math.ceil((rec.expiryDateMillis - now) / 86400000);
      if (diffDays < 0) {
        dueBadge.className = "badge badge-red";
        dueBadge.textContent = t("badge_overdue_days", { days: Math.abs(diffDays) });
      } else if (diffDays === 0) {
        dueBadge.className = "badge badge-amber";
        dueBadge.textContent = t("badge_due_today");
      } else {
        dueBadge.className = "badge badge-slate";
        dueBadge.textContent = t("badge_due_in_days", { days: diffDays });
      }
    } else {
      dueBadge.style.display = "none";
    }

    // Trust badge
    const trustBadge = document.getElementById("trust-badge");
    if (rec.whatsappVerified) {
      trustBadge.className = "badge badge-green";
      trustBadge.textContent = "✓ " + t("badge_code_checked");
    } else {
      trustBadge.className = "badge badge-slate";
      trustBadge.textContent = "✓ " + t("badge_link_verified");
    }

    // Installment progress box
    const instBox = document.getElementById("installment-progress-box");
    if (rec.plan && Array.isArray(rec.installments) && rec.installments.length > 0) {
      instBox.style.display = "flex";
      const total = rec.installments.length;
      const paid = rec.installments.filter((i) => i.status === "PAID" || (i.amountPaid || 0) >= i.amountDue).length;
      document.getElementById("inst-progress-label").textContent = t("badge_installments", { paid, total });
      const nextUnpaid = rec.installments.find((i) => i.status !== "PAID" && (i.amountPaid || 0) < i.amountDue);
      if (nextUnpaid) {
        const remainingPaise = nextUnpaid.amountDue - (nextUnpaid.amountPaid || 0);
        document.getElementById("inst-next-due").textContent = t("badge_next_due", {
          amount: formatCurrency(remainingPaise / 100, rec.currency)
        });
      } else {
        document.getElementById("inst-next-due").textContent = t("badge_paid_back");
      }
      const pct = total > 0 ? Math.round((paid / total) * 100) : 0;
      document.getElementById("inst-progress-fill").style.width = pct + "%";
    } else if (rec.installmentPlan && rec.installmentPlan.installmentCount > 0) {
      instBox.style.display = "flex";
      const total = rec.installmentPlan.installmentCount;
      const paid = rec.installmentPlan.paidCount || 0;
      document.getElementById("inst-progress-label").textContent = t("badge_installments", { paid, total });
      if (rec.installmentPlan.nextDueAmount > 0) {
        document.getElementById("inst-next-due").textContent = t("badge_next_due", {
          amount: formatCurrency(rec.installmentPlan.nextDueAmount / 100, rec.currency)
        });
      } else {
        document.getElementById("inst-next-due").textContent = t("badge_paid_back");
      }
      const pct = total > 0 ? Math.round((paid / total) * 100) : 0;
      document.getElementById("inst-progress-fill").style.width = pct + "%";
    } else {
      instBox.style.display = "none";
    }

    // Check status: Disputed
    if (rec.status === "DISPUTED") {
      showErrorState(
        "state_disputed_title",
        "state_disputed_desc",
        "state_disputed_action",
        { name: otherName, reason: rec.disputeReason || "Terms not agreed" }
      );
      return;
    }

    // Check status: Confirmed & Completed
    if (rec.status === "CONFIRMED") {
      // Direct to Screen 3 (Next Steps)
      showView("screen-step3");
      document.getElementById("step3-amount-number").textContent = amountStr;
      document.getElementById("step3-other-name").textContent = otherName;
      announce(t("confirmed_title") + ". " + t("confirmed_subtitle"));
      return;
    }

    // Screen 1: Initial Question
    showView("screen-step1");
    const amountLabel = isLender ? t("amount_lent_label") : t("amount_borrowed_label");
    document.getElementById("step1-amount-label").textContent = amountLabel;
    document.getElementById("step1-amount-number").textContent = amountStr;

    const questionText = isLender
      ? t("question_lent", { name: otherName, amt: amountStr })
      : t("question_borrowed", { name: otherName, amt: amountStr });
    document.getElementById("step1-question").textContent = questionText;
    announce(questionText);
  }

  // 8. 6-Digit Code Screen Setup
  function setupScreen2Code() {
    showView("screen-step2");
    const otherName = recordData ? (recordData.lenderName || "the sender") : "the sender";
    document.getElementById("step2-title").textContent = t("screen2_title", { name: otherName });
    document.getElementById("step2-desc").textContent = t("screen2_desc", { name: otherName });

    // WhatsApp Ask link via wa.me
    const askBtn = document.getElementById("btn-ask-wa");
    if (recordData && recordData.creatorPhone) {
      const cleanPhone = String(recordData.creatorPhone).replace(/\D+/g, "");
      askBtn.href = `https://wa.me/${cleanPhone}?text=${encodeURIComponent("Hi, please send me the 6-digit code for our Udhaar record.")}`;
    } else {
      askBtn.href = "https://wa.me/?text=" + encodeURIComponent("Hi, please send me the 6-digit code for our Udhaar record.");
    }

    // Start 15-minute countdown timer
    codeExpiresAtMillis = Date.now() + 15 * 60 * 1000;
    if (codeTimerInterval) clearInterval(codeTimerInterval);
    codeTimerInterval = setInterval(() => {
      const remainingSecs = Math.max(0, Math.floor((codeExpiresAtMillis - Date.now()) / 1000));
      const mins = Math.floor(remainingSecs / 60);
      const secs = remainingSecs % 60;
      const el = document.getElementById("code-timer-text");
      if (el) {
        if (remainingSecs > 0) {
          el.textContent = `${t("code_expires_in")} (${mins}:${secs < 10 ? "0" : ""}${secs})`;
        } else {
          el.textContent = "Code expired. Please ask for a new code.";
          clearInterval(codeTimerInterval);
        }
      }
    }, 1000);

    // Focus first OTP box
    const firstBox = document.querySelector(".otp-box");
    if (firstBox) firstBox.focus();
    announce(t("screen2_title", { name: otherName }));
  }

  // 9. Submit Confirmation with Code
  async function submitConfirmation(codeStr) {
    try {
      announce("Confirming record...", false);
      const res = await apiCall("guestConfirm", {
        recordId: urlRecordId,
        secret: urlSecret,
        whatsappOtp: codeStr || undefined,
        guestPhoneOtp: codeStr || undefined
      });

      if (recordData) {
        recordData.status = "CONFIRMED";
        recordData.whatsappVerified = true;
      }
      renderRecordView(recordData);
    } catch (err) {
      if (err.status === 403) {
        showErrorState("state_wrong_code_title", "state_wrong_code_desc", "state_wrong_code_action", {
          name: recordData?.lenderName || "the sender"
        });
      } else if (err.status === 429) {
        showErrorState("state_too_many_tries_title", "state_too_many_tries_desc", "state_too_many_tries_action", {
          name: recordData?.lenderName || "the sender"
        });
      } else {
        alert(err.message || t("toast_network_error"));
      }
    }
  }

  // 10. Calendar Generation Helpers (.ics & Google Calendar)
  function downloadIcsCalendar(rec) {
    const dueMillis = rec.expiryDateMillis || (Date.now() + 7 * 86400000);
    const dueDate = new Date(dueMillis);
    const pad = (n) => (n < 10 ? "0" + n : "" + n);
    const dtStamp = new Date().toISOString().replace(/[-:]/g, "").split(".")[0] + "Z";
    const dtStart = `${dueDate.getUTCFullYear()}${pad(dueDate.getUTCMonth() + 1)}${pad(dueDate.getUTCDate())}T090000Z`;
    const dtEnd = `${dueDate.getUTCFullYear()}${pad(dueDate.getUTCMonth() + 1)}${pad(dueDate.getUTCDate())}T100000Z`;

    const amt = formatCurrency(rec.principalAmount, rec.currency);
    const otherName = rec.lenderName || "Lender";
    const summary = `Udhaar due: ${amt} to ${otherName}`;
    const description = `Friendly reminder: ${amt} due for Udhaar record with ${otherName}. View online at https://udhaar.jyotirmoyb.com/u/r/${rec.recordId}`;

    const icsContent = [
      "BEGIN:VCALENDAR",
      "VERSION:2.0",
      "PRODID:-//Udhaar//Record Reminder//EN",
      "CALSCALE:GREGORIAN",
      "METHOD:PUBLISH",
      "BEGIN:VEVENT",
      `UID:udhaar-${rec.recordId || Date.now()}@jyotirmoyb.com`,
      `DTSTAMP:${dtStamp}`,
      `DTSTART:${dtStart}`,
      `DTEND:${dtEnd}`,
      `SUMMARY:${summary}`,
      `DESCRIPTION:${description}`,
      "STATUS:CONFIRMED",
      // Alarm 1: 1 day before
      "BEGIN:VALARM",
      "TRIGGER:-P1D",
      "ACTION:DISPLAY",
      `DESCRIPTION:Udhaar payment due tomorrow: ${amt}`,
      "END:VALARM",
      // Alarm 2: 9 AM on the day
      "BEGIN:VALARM",
      "TRIGGER:-PT0M",
      "ACTION:DISPLAY",
      `DESCRIPTION:Udhaar payment due today: ${amt}`,
      "END:VALARM",
      "END:VEVENT",
      "END:VCALENDAR"
    ].join("\r\n");

    const blob = new Blob([icsContent], { type: "text/calendar;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `udhaar-reminder-${rec.recordId || "record"}.ics`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    announce("Calendar file downloaded");
  }

  function getGoogleCalendarUrl(rec) {
    if (!rec) return "#";
    const dueMillis = rec.expiryDateMillis || (Date.now() + 7 * 86400000);
    const dueDate = new Date(dueMillis);
    const pad = (n) => (n < 10 ? "0" + n : "" + n);
    const dateStr = `${dueDate.getUTCFullYear()}${pad(dueDate.getUTCMonth() + 1)}${pad(dueDate.getUTCDate())}`;
    const dates = `${dateStr}T090000Z/${dateStr}T100000Z`;
    const rawAmt = (rec.principalAmount !== undefined && rec.principalAmount !== null)
      ? rec.principalAmount
      : (rec.amountPaise !== undefined ? rec.amountPaise / 100 : 0);
    const amt = formatCurrency(rawAmt, rec.currency);
    const otherName = rec.lenderName || "Lender";
    const text = encodeURIComponent(`Udhaar due: ${amt} to ${otherName}`);
    const details = encodeURIComponent(`Friendly reminder: ${amt} due for Udhaar record with ${otherName}. View: https://udhaar.jyotirmoyb.com/u/r/${rec.recordId || ""}`);
    return `https://calendar.google.com/calendar/render?action=TEMPLATE&text=${text}&dates=${dates}&details=${details}`;
  }

  function openGoogleCalendar(rec) {
    const gcalUrl = getGoogleCalendarUrl(rec);
    if (gcalUrl && gcalUrl !== "#") {
      window.open(gcalUrl, "_blank");
    }
  }

  // 11. Modal Sheet Helpers
  function openModal(id) {
    const el = document.getElementById(id);
    if (el) el.classList.add("active");
  }

  function closeModal(id) {
    const el = document.getElementById(id);
    if (el) el.classList.remove("active");
  }

  // 12. Initialization & Event Binding
  async function init() {
    const params = parseParams();
    urlRecordId = params.recordId;
    urlSecret = params.secret;
    isDemoMode = Boolean(params.isDemo);

    // Apply translations & theme
    applyTranslations();
    const savedTheme = localStorage.getItem("udhaar_theme") || "system";
    if (savedTheme !== "system") {
      document.documentElement.setAttribute("data-theme", savedTheme);
    }

    // Language selector
    const langSelect = document.getElementById("lang-select");
    if (langSelect) {
      langSelect.addEventListener("change", (e) => {
        currentLang = e.target.value;
        localStorage.setItem("udhaar_lang", currentLang);
        applyTranslations();
      });
    }

    // Theme toggle
    const themeBtn = document.getElementById("theme-btn");
    if (themeBtn) {
      themeBtn.addEventListener("click", () => {
        const cur = document.documentElement.getAttribute("data-theme") || "light";
        const next = cur === "dark" ? "light" : "dark";
        document.documentElement.setAttribute("data-theme", next);
        localStorage.setItem("udhaar_theme", next);
        themeBtn.textContent = next === "dark" ? "☀️" : "🌙";
      });
    }

    // Screen 1: Yes button
    const btnYes = document.getElementById("btn-confirm-yes");
    if (btnYes) {
      btnYes.addEventListener("click", () => {
        // If trustLevelRequired is LINK_ONLY, confirm immediately
        if (recordData?.trustLevelRequired === "LINK_ONLY") {
          submitConfirmation("");
        } else {
          setupScreen2Code();
        }
      });
    }

    // Screen 1: Something is wrong button
    const btnWrong = document.getElementById("btn-dispute") || document.getElementById("btn-something-wrong");
    if (btnWrong) {
      btnWrong.addEventListener("click", () => {
        openModal("dispute-modal");
      });
    }

    // Screen 2: OTP inputs auto-advance
    const otpBoxes = document.querySelectorAll(".otp-box");
    otpBoxes.forEach((box, idx) => {
      box.addEventListener("input", (e) => {
        const val = e.target.value.replace(/\D+/g, "");
        e.target.value = val ? val.charAt(0) : "";
        if (val && idx < otpBoxes.length - 1) {
          otpBoxes[idx + 1].focus();
        }
      });
      box.addEventListener("keydown", (e) => {
        if (e.key === "Backspace" && !e.target.value && idx > 0) {
          otpBoxes[idx - 1].focus();
        }
      });
    });

    // Screen 2: Submit Code
    const btnSubmitCode = document.getElementById("btn-submit-code") || document.getElementById("btn-verify-otp");
    if (btnSubmitCode) {
      btnSubmitCode.addEventListener("click", () => {
        let codeStr = "";
        otpBoxes.forEach((b) => { codeStr += b.value; });
        if (codeStr.length !== 6) {
          // If in demo mode and boxes are empty, allow demo confirmation
          if (isDemoMode && !codeStr) {
            codeStr = "123456";
          } else {
            alert("Please enter the complete 6-digit code.");
            return;
          }
        }
        submitConfirmation(codeStr);
      });
    }

    // Screen 2: Check later
    const btnLater = document.getElementById("btn-check-later");
    if (btnLater) {
      btnLater.addEventListener("click", () => {
        showView("screen-step1");
      });
    }

    // Modal Close buttons
    document.querySelectorAll("[data-close]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const modalId = btn.getAttribute("data-close");
        closeModal(modalId);
      });
    });

    // Action 1: Tell when you will return it
    const btnReturnDate = document.getElementById("btn-open-date-sheet") || document.getElementById("act-btn-date");
    if (btnReturnDate) {
      btnReturnDate.addEventListener("click", () => openModal("date-modal"));
    }
    const btnSubmitDate = document.getElementById("btn-submit-date");
    if (btnSubmitDate) {
      btnSubmitDate.addEventListener("click", async () => {
        const dateVal = document.getElementById("input-return-date")?.value;
        const noteVal = document.getElementById("input-return-note")?.value || "";
        if (!dateVal) {
          alert("Please pick a return date.");
          return;
        }
        try {
          await apiCall("guestProposeDate", {
            recordId: urlRecordId,
            secret: urlSecret,
            proposedDateMillis: new Date(dateVal).getTime(),
            note: noteVal
          });
          closeModal("date-modal");
          alert(t("toast_success"));
        } catch (err) {
          alert(err.message || t("toast_network_error"));
        }
      });
    }

    // Action 2: I have paid
    const btnPaid = document.getElementById("btn-open-paid-sheet") || document.getElementById("act-btn-paid");
    if (btnPaid) {
      btnPaid.addEventListener("click", () => openModal("paid-modal"));
    }
    const btnSubmitPaid = document.getElementById("btn-submit-paid");
    if (btnSubmitPaid) {
      btnSubmitPaid.addEventListener("click", async () => {
        const amtInput = document.getElementById("input-paid-amount");
        const amtVal = parseFloat(amtInput?.value || "0");
        const modeVal = document.getElementById("select-paid-mode")?.value || "UPI";
        const refVal = document.getElementById("input-paid-ref")?.value || "";
        if (isNaN(amtVal) || amtVal <= 0) {
          alert("Please enter a valid amount paid.");
          return;
        }
        try {
          await apiCall("guestPaymentClaim", {
            recordId: urlRecordId,
            secret: urlSecret,
            amount: amtVal * 100, // paise
            paymentMode: modeVal,
            referenceNumber: refVal
          });
          closeModal("paid-modal");
          alert(t("toast_success"));
        } catch (err) {
          alert(err.message || t("toast_network_error"));
        }
      });
    }

    // Action 3: Set reminder for myself
    const btnReminder = document.getElementById("btn-open-reminder-sheet") || document.getElementById("act-btn-reminder");
    if (btnReminder) {
      btnReminder.addEventListener("click", () => {
        openModal("reminder-modal");
        const gcalEl = document.getElementById("btn-google-cal");
        if (gcalEl && recordData) {
          gcalEl.href = getGoogleCalendarUrl(recordData);
        }
      });
    }
    const btnGoogleCal = document.getElementById("btn-google-cal");
    if (btnGoogleCal) {
      btnGoogleCal.addEventListener("click", (e) => {
        if (recordData) {
          btnGoogleCal.href = getGoogleCalendarUrl(recordData);
        }
      });
    }
    const btnIcsCal = document.getElementById("btn-ics-cal");
    if (btnIcsCal) {
      btnIcsCal.addEventListener("click", () => {
        if (recordData) downloadIcsCalendar(recordData);
      });
    }
    const btnNotify = document.getElementById("btn-browser-notify");
    if (btnNotify) {
      btnNotify.addEventListener("click", async () => {
        if (!("Notification" in window)) {
          alert("Browser notifications are not supported on this device.");
          return;
        }
        const permission = await Notification.requestPermission();
        if (permission === "granted") {
          document.getElementById("notify-status").textContent = "✓ " + t("notify_enabled");
          new Notification("Udhaar Reminder", {
            body: "Reminders active for this record.",
            icon: "../assets/hero-3d-phone.png"
          });
        }
      });
    }

    // Action 4: Pay now by UPI
    const btnPayUpi = document.getElementById("btn-open-pay-sheet") || document.getElementById("act-btn-pay-upi");
    if (btnPayUpi) {
      btnPayUpi.addEventListener("click", () => {
        openModal("upi-modal");
        const qrContainer = document.getElementById("qrcode-canvas");
        if (qrContainer) {
          qrContainer.innerHTML = "";
          const upiId = recordData?.lenderUpiId || "udhaar@upi";
          const rawAmt = (recordData?.principalAmount !== undefined && recordData?.principalAmount !== null)
            ? recordData.principalAmount
            : (recordData?.amountPaise !== undefined ? recordData.amountPaise / 100 : 0);
          const amt = Number(rawAmt || 0).toFixed(2);
          const name = encodeURIComponent(recordData?.lenderName || "Udhaar");
          const upiUri = `upi://pay?pa=${upiId}&pn=${name}&am=${amt}&cu=INR&tn=Udhaar%20Settlement`;

          const displayUpi = document.getElementById("display-upi-id");
          if (displayUpi) displayUpi.textContent = upiId;
          const openUpiApp = document.getElementById("btn-open-upi-app");
          if (openUpiApp) openUpiApp.href = upiUri;

          // Render QR
          if (typeof qrcode === "function") {
            const qr = qrcode(0, "M");
            qr.addData(upiUri);
            qr.make();
            qrContainer.innerHTML = qr.createSvgTag({ cellSize: 4, margin: 4 }) || qr.createImgTag(4);
          } else if (typeof QRCode !== "undefined") {
            new QRCode(qrContainer, {
              text: upiUri,
              width: 180,
              height: 180,
              colorDark: "#064e3b",
              colorLight: "#ffffff",
              correctLevel: QRCode.CorrectLevel.M
            });
          }
        }
      });
    }
    const btnCopyUpi = document.getElementById("btn-copy-upi");
    if (btnCopyUpi) {
      btnCopyUpi.addEventListener("click", () => {
        const upiId = document.getElementById("display-upi-id").textContent;
        navigator.clipboard.writeText(upiId).then(() => {
          alert(t("toast_upi_copied"));
        });
      });
    }

    // Action 5: Download Receipt
    const btnReceipt = document.getElementById("btn-print-receipt") || document.getElementById("act-btn-receipt");
    if (btnReceipt) {
      btnReceipt.addEventListener("click", () => {
        window.print();
      });
    }

    // Dispute submission
    const btnSubmitDispute = document.getElementById("btn-submit-dispute");
    if (btnSubmitDispute) {
      btnSubmitDispute.addEventListener("click", async () => {
        const reasonCode = document.getElementById("select-dispute-reason")?.value || "OTHER";
        const note = document.getElementById("input-dispute-note")?.value || "";
        try {
          await apiCall("guestDispute", {
            recordId: urlRecordId,
            secret: urlSecret,
            reasonCode,
            reasonText: note
          });
          closeModal("dispute-modal");
          showErrorState("state_disputed_title", "state_disputed_desc", "state_disputed_action", {
            name: recordData?.lenderName || "the sender",
            reason: note || reasonCode
          });
        } catch (err) {
          alert(err.message || t("toast_network_error"));
        }
      });
    }

    // Offline detection
    window.addEventListener("offline", () => {
      document.getElementById("offline-banner").style.display = "flex";
    });
    window.addEventListener("online", () => {
      document.getElementById("offline-banner").style.display = "none";
    });

    // Fetch initial data
    if (isDemoMode) {
      const demoBanner = document.getElementById("demo-banner");
      if (demoBanner) demoBanner.style.display = "flex";

      if (testState === "revoked") {
        showErrorState("state_revoked_title", "state_revoked_desc", "state_revoked_action", { name: "Jyotirmoy" });
        return;
      }
      if (testState === "wrong_code") {
        showErrorState("state_wrong_code_title", "state_wrong_code_desc", "state_wrong_code_action", { name: "Jyotirmoy" });
        return;
      }
      if (testState === "too_many_tries") {
        showErrorState("state_too_many_tries_title", "state_too_many_tries_desc", "state_too_many_tries_action", { name: "Jyotirmoy" });
        return;
      }
      if (testState === "server_error") {
        showErrorState("state_server_error_title", "state_server_error_desc", "state_server_error_action", { name: "the sender" });
        return;
      }

      const demoRecord = {
        recordId: "demo-tx-12345",
        counterpartyRole: "BORROWER",
        creatorRole: "GIVEN",
        creatorName: "Jyotirmoy",
        lenderName: "Jyotirmoy",
        borrowerName: "Ravi",
        principalAmount: 15000,
        amountPaise: 1500000,
        currency: "INR",
        startDateMillis: Date.now() - 5 * 86400000,
        expiryDateMillis: Date.now() + 25 * 86400000,
        dueDateMillis: Date.now() + 25 * 86400000,
        note: "Advance for home renovation materials",
        trustLevelRequired: "WHATSAPP_CODE",
        status: "PENDING_CONFIRMATION",
        verificationStatus: "PENDING",
        whatsappOtpExpiresAtMillis: Date.now() + 15 * 60 * 1000,
        whatsappCodeSent: true,
        lenderUpiId: "jyotirmoy@okaxis",
        showPaymentDetails: true,
        plan: {
          installmentCount: 3,
          frequency: "MONTHLY",
          startDate: Date.now()
        },
        installments: [
          { id: "inst-1", sequence: 1, amountDue: 500000, amountPaid: 500000, status: "PAID" },
          { id: "inst-2", sequence: 2, amountDue: 500000, amountPaid: 0, status: "PENDING" },
          { id: "inst-3", sequence: 3, amountDue: 500000, amountPaid: 0, status: "PENDING" }
        ]
      };
      renderRecordView(demoRecord);
      return;
    }

    if (!urlRecordId || !urlSecret) {
      showErrorState("state_expired_title", "state_expired_desc", "state_expired_action", { name: "the sender" });
      return;
    }

    try {
      const data = await apiCall("getGuestRecord", { recordId: urlRecordId, secret: urlSecret });
      renderRecordView(data.record || data);
    } catch (err) {
      if (err.status === 410) {
        showErrorState("state_expired_title", "state_expired_desc", "state_expired_action", { name: "the sender" });
      } else if (err.status === 403) {
        showErrorState("state_revoked_title", "state_revoked_desc", "state_revoked_action", { name: "the sender" });
      } else {
        showErrorState("state_server_error_title", "state_server_error_desc", "state_server_error_action", { name: "the sender" });
      }
    }
  }

  // Export I18N dictionary for automated testing
  if (typeof module !== "undefined" && module.exports) {
    module.exports = { I18N, t, formatCurrency, formatDate };
  } else {
    window.UdhaarI18N = I18N;
    document.addEventListener("DOMContentLoaded", init);
  }
})();
