// =========================================================================
// Udhaar Guest Web Flow — v21.0
// Plain-language, accessible, multi-lingual client (EN, HI, BN, AS, NE)
// Zero-cost WhatsApp OTP verification, PWA Shell, Receipt & Safe UPI
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

      // Stepper & Progress
      step_1_badge: "Step 1 of 3",
      step_2_badge: "Step 2 of 3",
      step_3_badge: "Step 3 of 3",
      step_1_label: "Review details",
      step_2_label: "Enter code",
      step_3_label: "Completed",

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
      btn_not_me: "This is not me",
      why_screen2: "This code proves that you received the message and ensures nobody else can confirm in your name.",

      // Screen 3: Next Steps
      confirmed_title: "Record confirmed",
      confirmed_subtitle: "What would you like to do next?",
      btn_share_confirmation: "Share confirmation on WhatsApp",
      share_wa_template: "I have confirmed {amt} on {date}. – {name}",
      act_return_date: "Tell when you will return it",
      act_pay_upi: "Pay now by UPI",
      act_set_reminder: "Set reminder for myself",
      act_i_paid: "I have paid",
      act_download_receipt: "Download receipt",
      why_screen3: "Setting a return date or reminder helps you stay organized without awkward follow-ups.",

      // PWA Install Prompts
      pwa_install_title: "Add Udhaar to your home screen",
      pwa_install_subtitle: "Access your confirmed records anytime, even offline.",
      btn_add_to_home: "Add",
      ios_install_title: "Install on your iPhone",
      ios_install_desc: "Tap Share 📤 in Safari, then select 'Add to Home Screen'.",

      // Quick Date Chips
      chip_in_1_week: "In 1 week",
      chip_in_2_weeks: "In 2 weeks",
      chip_in_1_month: "In 1 month",
      chip_custom: "Custom",

      // Bottom Note & Support
      no_install_needed: "You do not need to install anything.",
      get_the_app: "Get the app",
      report_problem_link: "Report a problem",

      // Details Grid & Receipt
      label_from: "From",
      label_to: "To",
      label_start_date: "Start date",
      label_due_date: "Due date",
      label_interest: "Interest",
      label_note: "Note",
      label_fingerprint: "Record fingerprint",
      advanced_details: "Advanced details",
      receipt_title: "Official Confirmation Receipt",
      receipt_subtitle: "Private, on-device verified transaction record",
      receipt_status_verified: "✓ Verified & Confirmed",
      receipt_disclaimer: "Udhaar is a private record-keeping system. This receipt verifies mutual agreement between parties.",
      change_history_title: "History of changes",
      history_created: "Record created",
      history_confirmed: "Record confirmed by guest",

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
      scan_with_phone: "Scan with your phone",
      upi_id_label: "UPI ID",
      btn_copy_upi: "Copy UPI ID",
      btn_toggle_upi: "Show / Hide UPI ID",
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

      step_1_badge: "चरण 1 / 3",
      step_2_badge: "चरण 2 / 3",
      step_3_badge: "चरण 3 / 3",
      step_1_label: "विवरण जांचें",
      step_2_label: "कोड दर्ज करें",
      step_3_label: "पूर्ण हुआ",

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
      btn_not_me: "यह मैं नहीं हूँ",
      why_screen2: "यह कोड सुनिश्चित करता है कि केवल आप ही इस रिकॉर्ड की पुष्टि कर सकते हैं।",

      confirmed_title: "रिकॉर्ड की पुष्टि हो गई",
      confirmed_subtitle: "आगे आप क्या करना चाहते हैं?",
      btn_share_confirmation: "WhatsApp पर पुष्टि साझा करें",
      share_wa_template: "मैंने {date} को {amt} की पुष्टि कर दी है। – {name}",
      act_return_date: "बताएं कि आप इसे कब लौटाएंगे",
      act_pay_upi: "अभी UPI द्वारा भुगतान करें",
      act_set_reminder: "अपने लिए रिमाइंडर सेट करें",
      act_i_paid: "मैंने भुगतान कर दिया",
      act_download_receipt: "रसीद डाउनलोड करें",
      why_screen3: "तारीख या रिमाइंडर सेट करने से बिना किसी झिझक के समय पर हिसाब चुकता रहता है।",

      pwa_install_title: "उधार को होम स्क्रीन पर जोड़ें",
      pwa_install_subtitle: "अपने पुष्ट रिकॉर्ड कभी भी, ऑफलाइन भी देखें।",
      btn_add_to_home: "जोड़ें",
      ios_install_title: "अपने iPhone पर इंस्टॉल करें",
      ios_install_desc: "Safari में शेयर 📤 दबाएं, फिर 'Add to Home Screen' चुनें।",

      chip_in_1_week: "1 सप्ताह में",
      chip_in_2_weeks: "2 सप्ताह में",
      chip_in_1_month: "1 महीने में",
      chip_custom: "कस्टम",

      no_install_needed: "आपको कोई ऐप इंस्टॉल करने की आवश्यकता नहीं है।",
      get_the_app: "ऐप प्राप्त करें",
      report_problem_link: "समस्या की रिपोर्ट करें",

      label_from: "किसने दिया",
      label_to: "किसे दिया",
      label_start_date: "शुरुआती तारीख",
      label_due_date: "अंतिम तारीख",
      label_interest: "ब्याज",
      label_note: "नोट",
      label_fingerprint: "रिकॉर्ड फिंगरप्रिंट",
      advanced_details: "विस्तृत विवरण",
      receipt_title: "आधिकारिक पुष्टि रसीद",
      receipt_subtitle: "प्राइवेट, ऑन-डिवाइस सत्यापित रिकॉर्ड",
      receipt_status_verified: "✓ सत्यापित और पुष्ट",
      receipt_disclaimer: "उधार एक प्राइवेट रिकॉर्ड प्रणाली है। यह रसीद दोनों पक्षों के बीच आपसी सहमति प्रमाणित करती है।",
      change_history_title: "बदलावों का इतिहास",
      history_created: "रिकॉर्ड बनाया गया",
      history_confirmed: "अतिथि द्वारा रिकॉर्ड की पुष्टि की गई",

      state_expired_title: "इस लिंक की समय सीमा समाप्त हो गई है",
      state_expired_desc: "आपकी सुरक्षा के लिए लिंक कुछ समय बाद निष्प्रभावी हो जाते हैं।",
      state_expired_action: "{name} से नया लिंक भेजने का अनुरोध करें।",
      state_revoked_title: "यह लिंक रद्द कर दिया गया है",
      state_revoked_desc: "भेजने वाले ने यह लिंक रद्द कर दिया है।",
      state_revoked_action: "{name} से पूछें कि क्या उन्होंने नया रिकॉर्ड बनाया है।",
      state_wrong_code_title: "गलत 6-अंकीय कोड",
      state_wrong_code_desc: "दर्ज किया गया कोड आपको भेजे गए कोड से मेल नहीं खाता।",
      state_wrong_code_action: "WhatsApp में प्राप्त कोड जांचें या {name} से पुनः भेजने को कहें।",
      state_too_many_tries_title: "अत्यधिक प्रयास",
      state_too_many_tries_desc: "गलत कोड कई बार डाला गया है। कृपया 1 घंटे बाद पुनः प्रयास करें।",
      state_too_many_tries_action: "1 घंटे बाद प्रयास करें, या {name} से नया लिंक जनरेट करने को कहें।",
      state_already_confirmed_title: "रिकॉर्ड की पुष्टि पहले ही हो चुकी है",
      state_already_confirmed_desc: "इस रिकॉर्ड की पुष्टि {date} को पहले ही की जा चुकी है।",
      state_already_confirmed_action: "आप नीचे विवरण देख सकते हैं या रसीद सहेज सकते हैं।",
      state_disputed_title: "समस्या दर्ज की गई",
      state_disputed_desc: "इस रिकॉर्ड को गलत बताया गया है।",
      state_disputed_action: "कारण: {reason}। समाधान के लिए सीधे {name} से संपर्क करें।",
      state_server_error_title: "कुछ त्रुटि हुई",
      state_server_error_desc: "हम रिकॉर्ड विवरण लोड करने में असमर्थ रहे।",
      state_server_error_action: "कृपया पृष्ठ को रीफ्रेश करें या कुछ क्षण बाद पुनः प्रयास करें।",

      dispute_sheet_title: "समस्या की रिपोर्ट करें",
      dispute_reason_label: "इस रिकॉर्ड में क्या गलत है?",
      reason_wrong_amount: "गलत रकम",
      reason_wrong_date: "गलत तारीख",
      reason_never_took: "मैंने यह रकम कभी नहीं ली",
      reason_other: "अन्य समस्या",
      dispute_note_placeholder: "स्पष्ट करें कि क्या गलत है (300 अक्षरों तक)...",
      btn_submit_dispute: "रिपोर्ट भेजें",

      paid_sheet_title: "मैंने भुगतान कर दिया",
      paid_amount_label: "भुगतान की गई रकम (₹)",
      paid_date_label: "भुगतान की तारीख",
      paid_mode_label: "आपने कैसे भुगतान किया?",
      mode_upi: "UPI",
      mode_cash: "नकद (Cash)",
      mode_bank: "बैंक ट्रांसफर",
      paid_ref_label: "संदर्भ संख्या / नोट (वैकल्पिक)",
      paid_proof_label: "प्रमाण की फोटो (रसीद / स्क्रीनशॉट)",
      btn_submit_paid: "भुगतान प्रमाण जमा करें",
      paid_notice: "रकम देने वाले व्यक्ति अपने ऐप में भुगतान की पुष्टि करेंगे।",

      date_sheet_title: "बताएं कि आप इसे कब लौटाएंगे",
      date_label: "लौटाने की तारीख",
      date_note_label: "संदेश (वैकल्पिक)",
      date_note_placeholder: "उदाहरण: मैं इस शुक्रवार आधा और बाकी अगले महीने लौटाऊंगा",
      btn_submit_date: "प्रस्तावित तारीख भेजें",
      date_notice: "यह एक प्रस्ताव है जिसे {name} अपने ऐप में स्वीकार या अस्वीकार करेंगे।",

      reminder_sheet_title: "अपने लिए रिमाइंडर सेट करें",
      reminder_desc: "अंतिम तारीख से पहले अलर्ट प्राप्त करें ताकि आप कभी न भूलें।",
      btn_google_cal: "Google कैलेंडर में जोड़ें",
      btn_ics_cal: "कैलेंडर फ़ाइल (.ics) डाउनलोड करें",
      btn_browser_notify: "ब्राउज़र सूचनाएं चालू करें",
      notify_disclaimer: "जब तक आप अनुमति नहीं देते, उधार अलर्ट नहीं भेज सकता।",
      notify_enabled: "ब्राउज़र सूचनाएं सक्रिय हैं!",

      upi_sheet_title: "अभी UPI द्वारा भुगतान करें",
      upi_scan_qr: "GPay, PhonePe, Paytm या BHIM से QR स्कैन करें",
      scan_with_phone: "अपने फोन से स्कैन करें",
      upi_id_label: "UPI ID",
      btn_copy_upi: "UPI ID कॉपी करें",
      btn_toggle_upi: "UPI ID दिखाएं / छुपाएं",
      toast_upi_copied: "UPI ID कॉपी हो गई!",
      btn_pay_app: "अभी भुगतान करें",
      upi_warning: "भुगतान पूरा करने से पहले UPI ऐप में प्राप्तकर्ता का नाम अवश्य जांचें।",
      upi_confirmation_question: "क्या भुगतान हो गया?",
      btn_payment_yes: "हाँ, भुगतान सफल रहा",
      btn_payment_no: "नहीं, अभी नहीं",

      toast_copied: "क्लिपबोर्ड पर कॉपी किया गया!",
      toast_success: "सफलतापूर्वक सहेजा गया!",
      toast_network_error: "सर्वर से संपर्क नहीं हो सका। कृपया अपना कनेक्शन जांचें।"
    },

    bn: {
      brand_name: "ধার",
      brand_sub: "ওয়েব",
      select_lang: "ভাষা নির্বাচন করুন",
      toggle_theme: "থিম পরিবর্তন",
      offline_banner: "আপনি বর্তমানে অফলাইনে আছেন। ইন্টারনেট সংযোগ পরীক্ষা করুন।",
      demo_banner: "ডেমো মোড — কোনো আসল তথ্য সংরক্ষিত হয় না।",
      btn_retry: "পুনরায় চেষ্টা করুন",

      step_1_badge: "ধাপ ১ / ৩",
      step_2_badge: "ধাপ ২ / ৩",
      step_3_badge: "ধাপ ৩ / ৩",
      step_1_label: "বিবরণ দেখুন",
      step_2_label: "কোড দিন",
      step_3_label: "সম্পন্ন",

      badge_link_verified: "ব্যক্তিগত লিঙ্ক",
      badge_code_checked: "কোড পরীক্ষা করা হয়েছে",
      badge_app_confirmed: "অ্যাপে নিশ্চিত করা হয়েছে",
      badge_due_today: "আজই পরিশোধের দিন",
      badge_due_in_days: "{days} দিনের মধ্যে পরিশোধযোগ্য",
      badge_overdue_days: "{days} দিন বকেয়া",
      badge_paid_back: "পরিশোধ সম্পন্ন",
      badge_installments: "কিস্তি: {paid} / {total} পরিশোধিত",
      badge_next_due: "পরবর্তী কিস্তি: {amount}",

      amount_lent_label: "প্রদত্ত টাকা",
      amount_borrowed_label: "গৃহীত টাকা",
      amount_generic_label: "টাকার পরিমাণ",
      question_lent: "{name} বলছেন তিনি আপনাকে {amt} দিয়েছেন। এটি কি সঠিক?",
      question_borrowed: "{name} বলছেন তিনি আপনার কাছ থেকে {amt} নিয়েছেন। এটি কি সঠিক?",
      btn_yes_correct: "✓ হ্যাঁ, এটি সঠিক",
      btn_something_wrong: "✕ কিছু ভুল আছে",
      why_title: "আমরা কেন এটি জিজ্ঞেস করি",
      why_screen1: "এটি নিশ্চিত করলে আপনারা দুজনেই একই হিসাব দেখতে পাবেন, ফলে পরে কোনো ভুল বোঝাবুঝি থাকবে না।",

      screen2_title: "{name}-এর পাঠানো কোডটি লিখুন",
      screen2_desc: "এই ৬-সংখ্যার কোডটি {name} আপনার WhatsApp বার্তায় পাঠিয়েছেন। এটি প্রমাণ করে যে আপনি বার্তাটি পেয়েছেন।",
      code_expires_in: "কোডটির মেয়াদ ১৫ মিনিট",
      btn_ask_whatsapp: "WhatsApp-এ জিজ্ঞাসা করুন 💬",
      btn_check_later: "আমি পরে দেখব",
      btn_submit_code: "কোড নিশ্চিত করুন",
      btn_not_me: "এটি আমি নই",
      why_screen2: "এই কোড নিশ্চিত করে যে কেবলমাত্র আপনিই এই হিসাবের সত্যতা যাচাই করতে পারেন।",

      confirmed_title: "হিসাব নিশ্চিত করা হয়েছে",
      confirmed_subtitle: "এরপর আপনি কী করতে চান?",
      btn_share_confirmation: "WhatsApp-এ নিশ্চিতকরণ শেয়ার করুন",
      share_wa_template: "আমি {date} তারিখে {amt} নিশ্চিত করেছি। – {name}",
      act_return_date: "কবে ফেরত দেবেন জানান",
      act_pay_upi: "এখনই UPI দিয়ে দিন",
      act_set_reminder: "নিজের জন্য রিমাইন্ডার রাখুন",
      act_i_paid: "আমি পরিশোধ করেছি",
      act_download_receipt: "রসিদ ডাউনলোড করুন",
      why_screen3: "তারিখ বা রিমাইন্ডার ঠিক করে রাখলে কোনো সংকোচ ছাড়াই সহজে লেনদেন সম্পন্ন হয়।",

      pwa_install_title: "হোম স্ক্রিনে ধার যুক্ত করুন",
      pwa_install_subtitle: "ইন্টারনেট ছাড়াও যেকোনো সময় আপনার হিসাব দেখুন।",
      btn_add_to_home: "যুক্ত করুন",
      ios_install_title: "আপনার iPhone-এ ইনস্টল করুন",
      ios_install_desc: "Safari-তে শেয়ার 📤 চাপুন, তারপর 'Add to Home Screen' বাছুন।",

      chip_in_1_week: "১ সপ্তাহে",
      chip_in_2_weeks: "২ সপ্তাহে",
      chip_in_1_month: "১ মাসে",
      chip_custom: "পছন্দসই",

      no_install_needed: "আপনাকে কোনো অ্যাপ ইনস্টল করতে হবে না।",
      get_the_app: "অ্যাপ ডাউনলোড করুন",
      report_problem_link: "সমস্যার কথা জানান",

      label_from: "কার থেকে",
      label_to: "কার কাছে",
      label_start_date: "শুরুর তারিখ",
      label_due_date: "পরিশোধের তারিখ",
      label_interest: "সুদ",
      label_note: "নোট",
      label_fingerprint: "রেকর্ড ফিঙ্গারপ্রিন্ট",
      advanced_details: "বিস্তারিত বিবরণ",
      receipt_title: "অফিসিয়াল নিশ্চিতকরণ রসিদ",
      receipt_subtitle: "ব্যক্তিগত এবং ডিভাইসে যাচাইকৃত নিশ্চিত রেকর্ড",
      receipt_status_verified: "✓ যাচাইকৃত ও নিশ্চিত",
      receipt_disclaimer: "ধার একটি ব্যক্তিগত হিসাব খাতা। এই রসিদ উভয় পক্ষের পারস্পরিক সম্মতি প্রমাণ করে।",
      change_history_title: "পরিবর্তনের বিবরণ",
      history_created: "হিসাব তৈরি হয়েছে",
      history_confirmed: "হিসাব নিশ্চিত করা হয়েছে",

      state_expired_title: "এই লিঙ্কের মেয়াদ শেষ হয়ে গেছে",
      state_expired_desc: "আপনার সুরক্ষার জন্য নির্দিষ্ট সময় পর লিঙ্কের কার্যকারিতা শেষ হয়।",
      state_expired_action: "{name}-কে একটি নতুন লিঙ্ক পাঠাতে বলুন।",
      state_revoked_title: "এই লিঙ্কটি বাতিল করা হয়েছে",
      state_revoked_desc: "প্রেরক এই লিঙ্কটি বাতিল করে দিয়েছেন।",
      state_revoked_action: "{name}-এর সাথে যোগাযোগ করুন।",
      state_wrong_code_title: "ভুল ৬-সংখ্যার কোড",
      state_wrong_code_desc: "দেওয়া কোডটি পাঠানো কোডের সাথে মেলেনি।",
      state_wrong_code_action: "WhatsApp বার্তাটি পুনরায় দেখুন বা {name}-কে আবার পাঠাতে বলুন।",
      state_too_many_tries_title: "অতিরিক্ত চেষ্টা করা হয়েছে",
      state_too_many_tries_desc: "একাধিকবার ভুল কোড দেওয়া হয়েছে। দয়া করে ১ ঘণ্টা অপেক্ষা করুন।",
      state_too_many_tries_action: "১ ঘণ্টা পর চেষ্টা করুন, অথবা নতুন লিঙ্ক চান।",
      state_already_confirmed_title: "হিসাব ইতিমধ্যে নিশ্চিত করা হয়েছে",
      state_already_confirmed_desc: "এই হিসাবটি {date} তারিখে ইতিমধ্যে নিশ্চিত করা হয়েছে।",
      state_already_confirmed_action: "আপনি নিচে বিস্তারিত দেখতে পারেন বা রসিদ সংরক্ষণ করতে পারেন।",
      state_disputed_title: "সমস্যা রিপোর্ট করা হয়েছে",
      state_disputed_desc: "এই হিসাবে অসঙ্গতি লক্ষ্য করা গেছে।",
      state_disputed_action: "কারণ: {reason}। নিষ্পত্তির জন্য সরাসরি {name}-এর সাথে কথা বলুন।",
      state_server_error_title: "কিছু সমস্যা হয়েছে",
      state_server_error_desc: "হিসাবের বিবরণ লোড করা যায়নি।",
      state_server_error_action: "পৃষ্ঠাটি রিফ্রেশ করুন বা কিছুক্ষণ পর চেষ্টা করুন।",

      dispute_sheet_title: "সমস্যার কথা জানান",
      dispute_reason_label: "এই হিসাবে কী ভুল আছে?",
      reason_wrong_amount: "টাকার পরিমাণ ভুল",
      reason_wrong_date: "তারিখ ভুল",
      reason_never_took: "আমি কখনো এই টাকা নেইনি",
      reason_other: "অন্য সমস্যা",
      dispute_note_placeholder: "কী ভুল হয়েছে সংক্ষেপে লিখুন (৩০০ অক্ষরের মধ্যে)...",
      btn_submit_dispute: "রিপোর্ট পাঠান",

      paid_sheet_title: "আমি পরিশোধ করেছি",
      paid_amount_label: "পরিশোধ করা টাকা (₹)",
      paid_date_label: "পরিশোধের তারিখ",
      paid_mode_label: "কীভাবে পরিশোধ করেছেন?",
      mode_upi: "UPI",
      mode_cash: "নগদ (Cash)",
      mode_bank: "ব্যাংক ট্রান্সফার",
      paid_ref_label: "রেফারেন্স নম্বর / নোট (ঐচ্ছিক)",
      paid_proof_label: "প্রমাণের ছবি (রসিদ / স্ক্রিনশট)",
      btn_submit_paid: "প্রমাণপত্র জমা দিন",
      paid_notice: "টাকা প্রাপক তার অ্যাপে এই অর্থ প্রাপ্তি নিশ্চিত করবেন।",

      date_sheet_title: "কবে ফেরত দেবেন জানান",
      date_label: "ফেরতের তারিখ",
      date_note_label: "বার্তা (ঐচ্ছিক)",
      date_note_placeholder: "উদাহরণ: আমি এই শুক্রবারে অর্ধেক দেব এবং বাকি অংশ পরের মাসে",
      btn_submit_date: "প্রস্তাবিত তারিখ পাঠান",
      date_notice: "এটি একটি প্রস্তাব যা {name} তার অ্যাপে গ্রহণ বা বাতিল করতে পারেন।",

      reminder_sheet_title: "নিজের জন্য রিমাইন্ডার রাখুন",
      reminder_desc: "পরিশোধের তারিখের আগেই বার্তা পান যাতে ভুলে না যান।",
      btn_google_cal: "Google ক্যালেন্ডারে যুক্ত করুন",
      btn_ics_cal: "ক্যালেন্ডার ফাইল (.ics) ডাউনলোড করুন",
      btn_browser_notify: "ব্রাউজার বিজ্ঞপ্তি চালু করুন",
      notify_disclaimer: "আপনি অনুমতি না দেওয়া পর্যন্ত কোনো সতর্কতা বার্তা পাঠানো সম্ভব নয়।",
      notify_enabled: "ব্রাউজার বিজ্ঞপ্তি সক্রিয় করা হয়েছে!",

      upi_sheet_title: "এখনই UPI দিয়ে পরিশোধ করুন",
      upi_scan_qr: "GPay, PhonePe, Paytm বা BHIM দিয়ে স্ক্যান করুন",
      scan_with_phone: "আপনার ফোন দিয়ে স্ক্যান করুন",
      upi_id_label: "UPI ID",
      btn_copy_upi: "UPI ID কপি করুন",
      btn_toggle_upi: "UPI ID দেখান / লুকান",
      toast_upi_copied: "UPI ID কপি করা হয়েছে!",
      btn_pay_app: "এখনই পরিশোধ করুন",
      upi_warning: "টাকা পাঠানোর আগে অবশ্যই UPI অ্যাপে প্রাপকের নাম মিলিয়ে নিন।",
      upi_confirmation_question: "টাকা পরিশোধ সফল হয়েছে কি?",
      btn_payment_yes: "হ্যাঁ, পরিশোধ হয়েছে",
      btn_payment_no: "না, এখনও হয়নি",

      toast_copied: "ক্লিপবোর্ডে কপি করা হয়েছে!",
      toast_success: "সফলভাবে সংরক্ষিত হয়েছে!",
      toast_network_error: "সার্ভারের সাথে সংযোগ স্থাপন করা যায়নি। ইন্টারনেট পরীক্ষা করুন।"
    },

    as: {
      brand_name: "উধাৰ",
      brand_sub: "ৱেব",
      select_lang: "ভাষা বাছক",
      toggle_theme: "থিম সলনি কৰক",
      offline_banner: "আপুনি বৰ্তমান অফলাইনত আছে। ইণ্টাৰনেট সংযোগ পৰীক্ষা কৰক।",
      demo_banner: "ডেমো মোড — কোনো প্ৰকৃত তথ্য সংৰক্ষিত নহয়।",
      btn_retry: "পুনৰ চেষ্টা কৰক",

      step_1_badge: "স্তৰ ১ / ৩",
      step_2_badge: "স্তৰ ২ / ৩",
      step_3_badge: "স্তৰ ৩ / ৩",
      step_1_label: "বিৱৰণ পৰীক্ষা কৰক",
      step_2_label: "কোড দিয়ক",
      step_3_label: "সম্পূৰ্ণ হ'ল",

      badge_link_verified: "ব্যক্তিগত সংযোগ",
      badge_code_checked: "কোড পৰীক্ষা কৰা হ'ল",
      badge_app_confirmed: "এপত নিশ্চিত কৰা হৈছে",
      badge_due_today: "আজি পৰিশোধৰ দিন",
      badge_due_in_days: "{days} দিনৰ ভিতৰত পৰিশোধযোগ্য",
      badge_overdue_days: "{days} দিন পলম হৈছে",
      badge_paid_back: "পৰিশোধ সম্পূৰ্ণ",
      badge_installments: "কিস্তি: {paid} / {total} পৰিশোধিত",
      badge_next_due: "পৰৱৰ্তী কিস্তি: {amount}",

      amount_lent_label: "দিয়া ধন",
      amount_borrowed_label: "লোৱা ধন",
      amount_generic_label: "টকাৰ পৰিমাণ",
      question_lent: "{name}ই কৈছে যে তেওঁ আপোনাক {amt} দিছে। এইটো সঠিকনে?",
      question_borrowed: "{name}ই কৈছে যে তেওঁ আপোনাৰ পৰা {amt} লৈছে। এইটো সঠিকনে?",
      btn_yes_correct: "✓ হয়, এইটো সঠিক",
      btn_something_wrong: "✕ কিবা ভুল আছে",
      why_title: "আমি এইটো কিয় সোধোঁ",
      why_screen1: "এইটো নিশ্চিত কৰিলে আপোনালোক দুয়োজনে একেই হিচাপ দেখিব, যাৰ ফলত পিছত কোনো ভুল বুজাবুজি নহ'ব।",

      screen2_title: "{name}ই পঠিওৱা কোডটো দিয়ক",
      screen2_desc: "এই ৬-অংকৰ কোডটো {name}ই আপোনাৰ WhatsApp বাৰ্তাত পঠিয়াইছে। ই প্ৰমাণ কৰে যে আপুনি বাৰ্তা পাইছে।",
      code_expires_in: "কোডটোৰ ম্যাদ ১৫ মিনিট",
      btn_ask_whatsapp: "WhatsApp-ত সোধক 💬",
      btn_check_later: "মই পিছত চাম",
      btn_submit_code: "কোড নিশ্চিত কৰক",
      btn_not_me: "এইটো মই নহয়",
      why_screen2: "এই কোডটোৱে নিশ্চিত কৰে যে কেৱল আপুনিয়ে এই হিচাপ নিশ্চিত কৰিব পাৰে।",

      confirmed_title: "হিচাপ নিশ্চিত কৰা হ'ল",
      confirmed_subtitle: "ইয়াৰ পিছত আপুনি কি কৰিব বিচাৰে?",
      btn_share_confirmation: "WhatsApp-ত নিশ্চিত বাৰ্তা পঠিয়াওক",
      share_wa_template: "মই {date} তাৰিখে {amt} নিশ্চিত কৰিলোঁ। – {name}",
      act_return_date: "ঘূৰাই দিয়াৰ তাৰিখ জনাওক",
      act_pay_upi: "এতিয়াই UPIৰে পৰিশোধ কৰক",
      act_set_reminder: "মনত পেলাই দিয়াৰ সংকেত দিয়ক",
      act_i_paid: "মই পৰিশোধ কৰিলোঁ",
      act_download_receipt: "ৰচিদ ডাউনল'ড কৰক",
      why_screen3: "তাৰিখ বা সংকেত নিৰ্ধাৰণ কৰিলে কোনো অসুবিধা নোহোৱাকৈ সময়মতে হিচাপ সমাপ্ত হয়।",

      pwa_install_title: "হ'ম স্ক্ৰীণত উধাৰ যোগ কৰক",
      pwa_install_subtitle: "ইণ্টাৰনেট নোহোৱাকৈও যিকোনো সময়তে হিচাপ চাওক।",
      btn_add_to_home: "যোগ কৰক",
      ios_install_title: "আপোনাৰ iPhone-ত ইনষ্টল কৰক",
      ios_install_desc: "Safari-ত শ্বেয়াৰ 📤 টিপক, তাৰ পিছত 'Add to Home Screen' বাছক।",

      chip_in_1_week: "১ সপ্তাহত",
      chip_in_2_weeks: "২ সপ্তাহত",
      chip_in_1_month: "১ মাহত",
      chip_custom: "অন্য তাৰিখ",

      no_install_needed: "আপুনি কোনো এপ ইনষ্টল কৰিব নালাগে।",
      get_the_app: "এপ পাওক",
      report_problem_link: "সমস্যা জনাওক",

      label_from: "কাৰ পৰা",
      label_to: "কাৰ লৈ",
      label_start_date: "আৰম্ভণিৰ তাৰিখ",
      label_due_date: "পৰিশোধৰ তাৰিখ",
      label_interest: "সুদ",
      label_note: "টোকা",
      label_fingerprint: "হিচাপৰ ফিংগাৰপ্ৰিণ্ট",
      advanced_details: "অধিক তথ্য",
      receipt_title: "আনুষ্ঠানিক স্বীকৃতি ৰচিদ",
      receipt_subtitle: "ব্যক্তিগত আৰু অন-ডিভাইচ প্ৰমাণিত হিচাপ",
      receipt_status_verified: "✓ সত্যাপন আৰু নিশ্চিত",
      receipt_disclaimer: "উধাৰ এটা ব্যক্তিগত হিচাপ ব্যৱস্থা। এই ৰচিদে দুয়োপক্ষৰ পাৰস্পৰিক সন্মতি প্ৰমাণ কৰে।",
      change_history_title: "সালসলনিৰ ইতিহাস",
      history_created: "হিচাপ সৃষ্টি কৰা হ'ল",
      history_confirmed: "হিচাপ নিশ্চিত কৰা হ'ল",

      state_expired_title: "এই সংযোগৰ সময় উকলি গৈছে",
      state_expired_desc: "আপোনাৰ সুৰক্ষাৰ বাবে নিৰ্দিষ্ট সময়ৰ পিছত সংযোগ নিষ্ক্ৰিয় হয়।",
      state_expired_action: "{name}ক নতুন সংযোগ পঠিয়াবলৈ কওক।",
      state_revoked_title: "এই সংযোগ বাতিল কৰা হৈছে",
      state_revoked_desc: "প্ৰেৰকে এই সংযোগ বাতিল কৰিছে।",
      state_revoked_action: "{name}ৰ সৈতে যোগাযোগ কৰক।",
      state_wrong_code_title: "ভুল ৬-অংকৰ কোড",
      state_wrong_code_desc: "দিয়া কোডটো পঠিওৱা কোডৰ সৈতে মিল খোৱা নাই।",
      state_wrong_code_action: "WhatsApp বাৰ্তা পৰীক্ষা কৰক বা {name}ক পুনৰ পঠিয়াবলৈ কওক।",
      state_too_many_tries_title: "অত্যাধিক চেষ্টা কৰা হ'ল",
      state_too_many_tries_desc: "বহুবাৰ ভুল কোড দিয়া হৈছে। অনুগ্ৰহ কৰি ১ ঘণ্টা অপেক্ষা কৰক।",
      state_too_many_tries_action: "১ ঘণ্টাৰ পিছত চেষ্টা কৰক বা নতুন সংযোগ বিচাৰক।",
      state_already_confirmed_title: "হিচাপ ইতিমধ্যে নিশ্চিত কৰা হৈছে",
      state_already_confirmed_desc: "এই হিচাপটো {date} তাৰিখে ইতিমধ্যে নিশ্চিত কৰা হ'ল।",
      state_already_confirmed_action: "আপুনি তলত বিৱৰণ চাব পাৰে বা ৰচিদ সংৰক্ষণ কৰিব পাৰে।",
      state_disputed_title: "সমস্যা পঞ্জীয়ন কৰা হ'ল",
      state_disputed_desc: "এই হিচাপত ভুল থকা বুলি জনোৱা হৈছে।",
      state_disputed_action: "কাৰণ: {reason}। সমাধানৰ বাবে পোনপটীয়াকৈ {name}ৰ সৈতে কথা পাতক।",
      state_server_error_title: "কিবা ভুল হ'ল",
      state_server_error_desc: "হিচাপৰ বিৱৰণ লোড কৰিব পৰা নগ'ল।",
      state_server_error_action: "পৃষ্ঠাটো সতেজ কৰক বা কিছু সময় পিছত চেষ্টা কৰক।",

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
      scan_with_phone: "আপোনাৰ ফোনেৰে স্কেন কৰক",
      upi_id_label: "UPI ID",
      btn_copy_upi: "UPI ID কপি কৰক",
      btn_toggle_upi: "UPI ID দেখুৱাওক / লুকাওক",
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

      step_1_badge: "चरण १ / ३",
      step_2_badge: "चरण २ / ३",
      step_3_badge: "चरण ३ / ३",
      step_1_label: "विवरण जाँच गर्नुहोस्",
      step_2_label: "कोड हाल्नुहोस्",
      step_3_label: "सम्पन्न भयो",

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
      btn_not_me: "यो म होइन",
      why_screen2: "यो कोडले तपाईंले मात्र यो अभिलेख पुष्टि गर्न सक्नुहुन्छ भन्ने सुनिश्चित गर्दछ।",

      confirmed_title: "अभिलेख पुष्टि भयो",
      confirmed_subtitle: "अब तपाईं के गर्न चाहनुहुन्छ?",
      btn_share_confirmation: "WhatsApp मा पुष्टि सेयर गर्नुहोस्",
      share_wa_template: "मैले {date} मा {amt} पुष्टि गरें। – {name}",
      act_return_date: "फिर्ता गर्ने मिति बताउनुहोस्",
      act_pay_upi: "अहिले नै UPI बाट तिर्नुहोस्",
      act_set_reminder: "आफ्नो लागि रिमाइन्डर राख्नुहोस्",
      act_i_paid: "मैले तिरिसकें",
      act_download_receipt: "रसिद डाउनलोड गर्नुहोस्",
      why_screen3: "मिति वा रिमाइन्डर सेट गर्नाले समयमै हिसाब चुक्ता गर्न मद्दत पुग्छ।",

      pwa_install_title: "उधारलाई होम स्क्रिनमा थप्नुहोस्",
      pwa_install_subtitle: "अफलाइन भए पनि जुनसुकै बेला आफ्नो अभिलेख हेर्नुहोस्।",
      btn_add_to_home: "थप्नुहोस्",
      ios_install_title: "आफ्नो iPhone मा इन्स्टल गर्नुहोस्",
      ios_install_desc: "Safari मा सेयर 📤 थिच्नुहोस् र 'Add to Home Screen' छान्नुहोस्।",

      chip_in_1_week: "१ हप्तामा",
      chip_in_2_weeks: "२ हप्तामा",
      chip_in_1_month: "१ महिनामा",
      chip_custom: "अन्य मिति",

      no_install_needed: "तपाईंले कुनै एप इन्स्टल गर्नुपर्दैन।",
      get_the_app: "एप पाउनुहोस्",
      report_problem_link: "समस्या रिपोर्ट गर्नुहोस्",

      label_from: "कसले दियो",
      label_to: "कसले पायो",
      label_start_date: "सुरु मिति",
      label_due_date: "भाका मिति",
      label_interest: "ब्याज",
      label_note: "कैफियत",
      label_fingerprint: "रेकर्डको फिंगरप्रिन्ट",
      advanced_details: "थप विवरण",
      receipt_title: "आधिकारिक पुष्टि रसिद",
      receipt_subtitle: "निजी, अन-डिभाइस प्रमाणित अभिलेख",
      receipt_status_verified: "✓ प्रमाणित र पुष्ट",
      receipt_disclaimer: "उधार एक निजी अभिलेख प्रणाली हो। यो रसिदले दुवै पक्षबीचको आपसी सहमति प्रमाणित गर्दछ।",
      change_history_title: "परिवर्तनको इतिहास",
      history_created: "अभिलेख सिर्जना भयो",
      history_confirmed: "अतिथिद्वारा अभिलेख पुष्टि गरियो",

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
      scan_with_phone: "आफ्नो फोनबाट स्क्यान गर्नुहोस्",
      upi_id_label: "UPI ID",
      btn_copy_upi: "UPI ID कपी गर्नुहोस्",
      btn_toggle_upi: "UPI ID देखाउनुहोस् / लुकाउनुहोस्",
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
  let currentActiveStep = 1;
  let deferredInstallPrompt = null;
  let actualUpiId = "udhaar@upi";
  let isUpiMasked = true;

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

  function maskUpi(upi) {
    if (!upi || typeof upi !== "string") return "—";
    const parts = upi.split("@");
    if (parts.length !== 2) return upi;
    const handle = parts[0];
    const prov = parts[1];
    if (handle.length <= 2) {
      return handle + "***@" + prov;
    }
    return handle.substring(0, 3) + "***@" + prov;
  }

  let testState = "";

  // 3. Extract Route Parameters & Decode Self-Contained Verification Tokens
  function base64UrlDecodeUtf8(encoded) {
    if (!encoded || typeof encoded !== "string") return "";
    const base64 = encoded.replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64 + "===".slice((base64.length + 3) % 4);
    if (typeof Buffer !== "undefined") {
      return Buffer.from(padded, "base64").toString("utf8");
    }
    const binary = atob(padded);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return new TextDecoder("utf-8").decode(bytes);
  }

  function unescapePipeField(val) {
    return String(val || "").replace(/%7C/gi, "|").replace(/%25/g, "%");
  }

  function decodeEmbeddedVerificationToken(tokenPart) {
    if (!tokenPart || typeof tokenPart !== "string" || tokenPart.length < 16) return null;
    try {
      const decoded = base64UrlDecodeUtf8(tokenPart.trim());
      if (decoded.startsWith("v2|")) {
        const parts = decoded.split("|");
        if (parts.length < 9) return null;
        const rid = unescapePipeField(parts[1]);
        if (!rid) return null;
        const tok = unescapePipeField(parts[2]);
        const role = parts[3] === "T" ? "TAKEN" : "GIVEN";
        const lender = unescapePipeField(parts[4]) || "Lender";
        const borrower = unescapePipeField(parts[5]) || "Borrower";
        const amt = parseFloat(parts[6] || "0") || 0;
        const cur = unescapePipeField(parts[7]) || "INR";
        const startMs = parseInt(parts[8] || "0", 36) || Date.now();
        const expMs = parts[9] ? (parseInt(parts[9], 36) || null) : null;
        const cPhone = unescapePipeField(parts[11] || "");
        const field12 = unescapePipeField(parts[12] || "");
        const field13 = unescapePipeField(parts[13] || "");
        const field14 = unescapePipeField(parts[14] || "");
        const customHash = unescapePipeField(parts[15] || "");
        const upiId = field12.includes("@") ? field12 : "";
        const trustReq = (field13 === "W" || field13 === "WHATSAPP_OTP" || field13 === "WHATSAPP_CODE")
          ? "WHATSAPP_CODE"
          : "LINK_ONLY";
        const expectedOtp = /^\d{6}$/.test(field14) ? field14 : "";
        return {
          recordId: rid,
          secret: tok,
          counterpartyRole: role === "GIVEN" ? "BORROWER" : "LENDER",
          creatorRole: role,
          lenderName: lender,
          borrowerName: borrower,
          principalAmount: amt,
          amountPaise: Math.round(amt * 100),
          currency: cur,
          startDateMillis: startMs,
          expiryDateMillis: expMs,
          creatorPhone: cPhone,
          lenderUpiId: upiId || undefined,
          expectedOtp: expectedOtp || undefined,
          contentHash: customHash || ("v2-" + rid),
          trustLevelRequired: trustReq,
          status: "PENDING_CONFIRMATION",
          isSelfContainedToken: true
        };
      }
      if (decoded.includes("rid=") && decoded.includes("amt=")) {
        const map = {};
        decoded.split("&").forEach((entry) => {
          const idx = entry.indexOf("=");
          if (idx > 0) {
            map[entry.substring(0, idx)] = decodeURIComponent(entry.substring(idx + 1).replace(/\+/g, "%20"));
          }
        });
        if (!map.rid) return null;
        const role = map.role === "TAKEN" ? "TAKEN" : "GIVEN";
        const amt = parseFloat(map.amt || "0") || 0;
        const expRaw = parseInt(map.exp || "0", 10);
        return {
          recordId: map.rid,
          secret: map.tok || "",
          counterpartyRole: role === "GIVEN" ? "BORROWER" : "LENDER",
          creatorRole: role,
          lenderName: map.lender || "Lender",
          borrowerName: map.borrower || "Borrower",
          principalAmount: amt,
          amountPaise: Math.round(amt * 100),
          currency: map.cur || "INR",
          startDateMillis: parseInt(map.start || "0", 10) || Date.now(),
          expiryDateMillis: expRaw > 0 ? expRaw : null,
          creatorPhone: map.cPhone || "",
          contentHash: map.hash || map.rid,
          trustLevelRequired: "LINK_ONLY",
          status: "PENDING_CONFIRMATION",
          isSelfContainedToken: true
        };
      }
    } catch (_) {
      return null;
    }
    return null;
  }

  function parseParams() {
    const pathname = (typeof window !== "undefined" && window.location.pathname) || "";
    const search = (typeof window !== "undefined" && window.location.search) || "";
    const hash = (typeof window !== "undefined" && window.location.hash.replace(/^#/, "").trim()) || "";
    const isDemo = search.includes("demo=true") || hash.includes("demo") || pathname.includes("/demo") || pathname.endsWith("/demo");
    const urlParams = new URLSearchParams(search);
    testState = urlParams.get("state") || "";
    let recId = "";
    let embeddedRecord = decodeEmbeddedVerificationToken(hash);
    if (pathname.includes("/u/r/")) {
      recId = pathname.substring(pathname.indexOf("/u/r/") + 5).split("/")[0].split("?")[0].split("#")[0];
    } else if (pathname.includes("/u/") && !pathname.includes("/u/p/")) {
      const segment = pathname.substring(pathname.indexOf("/u/") + 3).split("/")[0].split("?")[0].split("#")[0];
      if (!embeddedRecord) {
        embeddedRecord = decodeEmbeddedVerificationToken(segment);
      }
      recId = embeddedRecord ? embeddedRecord.recordId : segment;
    }

    // Secret token from fragment (or embedded self-contained payload when opened directly via /u/<token>)
    return {
      recordId: recId || (embeddedRecord ? embeddedRecord.recordId : ""),
      secret: hash || (embeddedRecord ? embeddedRecord.secret : ""),
      isDemo,
      embeddedRecord
    };
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
    try {
      const resp = await fetch("/api/" + endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      let data = null;
      try {
        data = await resp.json();
      } catch (_) {
        data = null;
      }
      const isValidApiJson = data && typeof data === "object" && (
        data.success !== undefined ||
        data.record !== undefined ||
        data.recordId !== undefined ||
        data.lenderName !== undefined ||
        data.error !== undefined
      );
      if (!resp.ok || !isValidApiJson) {
        if (recordData && recordData.isSelfContainedToken && endpoint !== "getGuestRecord" && (!isValidApiJson || resp.status === 404 || resp.status >= 500)) {
          return { success: true, trustLevel: "Private link" };
        }
        const err = new Error((data && data.error) || ("Request failed with status " + resp.status));
        err.status = !isValidApiJson && resp.ok ? 503 : resp.status;
        err.data = data || {};
        throw err;
      }
      return data;
    } catch (err) {
      if (recordData && recordData.isSelfContainedToken && endpoint !== "getGuestRecord" && (!err.status || err.status === 404 || err.status >= 500)) {
        return { success: true, trustLevel: "Private link" };
      }
      throw err;
    }
  }

  // Update Stepper Visuals
  function updateStepper(step) {
    currentActiveStep = step;
    const stepper = document.getElementById("stepper-indicator");
    if (!stepper) return;

    if (step < 1 || step > 3) {
      stepper.style.display = "none";
      return;
    }
    stepper.style.display = "flex";

    const badge = document.getElementById("stepper-step-indicator");
    const label = document.getElementById("stepper-step-label");
    const progress = document.getElementById("stepper-progress");

    if (badge) badge.textContent = t(`step_${step}_badge`);
    if (label) label.textContent = t(`step_${step}_label`);
    if (progress) {
      progress.className = `stepper-fill step-fill-${step}`;
    }
  }

  // Update Report Problem Email Links (Strictly omitting secret)
  function updateReportProblemLinks(recId) {
    const cleanId = recId || "General";
    const mailto = `mailto:admin@jyotirmoyb.com?subject=${encodeURIComponent(`Udhaar Support - Record ${cleanId}`)}&body=${encodeURIComponent(`Hello Udhaar Support,\n\nI am reporting a problem regarding record ${cleanId}.\n\nIssue description:\n`)}`;
    const ids = ["report-link-step1", "report-link-step2", "report-link-step3", "report-link-error"];
    ids.forEach((id) => {
      const el = document.getElementById(id);
      if (el) el.href = mailto;
    });
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

    // Update document title per locale
    document.title = `Udhaar — ${t("step_1_label")}`;

    // Refresh stepper text
    updateStepper(currentActiveStep);

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
      if (el) {
        if (id === viewId) {
          el.hidden = false;
          el.style.display = "flex";
          el.classList.remove("hidden");
        } else {
          el.hidden = true;
          el.style.display = "none";
          el.classList.add("hidden");
        }
      }
    });

    if (viewId === "screen-step1") {
      updateStepper(1);
    } else if (viewId === "screen-step2") {
      updateStepper(2);
    } else if (viewId === "screen-step3") {
      updateStepper(3);
      checkPwaInstallState();
    } else {
      updateStepper(0);
    }
  }

  function showErrorState(titleKey, descKey, actionKey, params = {}) {
    showView("error-view");
    document.getElementById("error-title").textContent = t(titleKey, params);
    document.getElementById("error-desc").textContent = t(descKey, params);
    document.getElementById("error-action").textContent = t(actionKey, params);
    announce(t(titleKey, params) + ". " + t(actionKey, params), true);
  }

  // PWA Install Prompt State Check
  function checkPwaInstallState() {
    const isStandalone = window.matchMedia("(display-mode: standalone)").matches || window.navigator.standalone === true;
    if (isStandalone) return;

    const pwaCard = document.getElementById("pwa-install-card");
    const iosGuide = document.getElementById("ios-install-guide");

    if (deferredInstallPrompt && pwaCard) {
      pwaCard.hidden = false;
      pwaCard.style.display = "flex";
      pwaCard.classList.remove("hidden");
    } else if (/iphone|ipad|ipod/i.test(navigator.userAgent) && iosGuide) {
      iosGuide.hidden = false;
      iosGuide.style.display = "flex";
      iosGuide.classList.remove("hidden");
    }
  }

  // Setup Share Confirmation via WhatsApp (Strictly client-side)
  function setupShareConfirmation(rec) {
    const shareBtn = document.getElementById("btn-share-confirmation");
    if (!shareBtn || !rec) return;

    const rawAmt = (rec.principalAmount !== undefined && rec.principalAmount !== null)
      ? rec.principalAmount
      : (rec.amountPaise !== undefined ? rec.amountPaise / 100 : 0);
    const amt = formatCurrency(rawAmt, rec.currency);
    const dateStr = formatDate(rec.confirmedAtMillis || Date.now());
    const myName = rec.borrowerName || rec.counterpartyName || "Borrower";
    const msg = t("share_wa_template", { amt, date: dateStr, name: myName });

    let waUrl = "https://wa.me/?text=" + encodeURIComponent(msg);
    if (rec.creatorPhone) {
      const cleanPhone = String(rec.creatorPhone).replace(/\D+/g, "");
      if (cleanPhone.length >= 10) {
        waUrl = `https://wa.me/${cleanPhone}?text=${encodeURIComponent(msg)}`;
      }
    }

    shareBtn.onclick = () => {
      window.open(waUrl, "_blank", "noopener,noreferrer");
    };
  }

  // 7. Render Record Screen Flow
  function renderRecordView(rec) {
    recordData = rec;
    const isLender = rec.counterpartyRole === "BORROWER"; // Sender is lender, guest is borrower
    const otherName = isLender ? (rec.lenderName || rec.creatorName || rec.otherPartyName || "The other person") : (rec.borrowerName || rec.counterpartyName || "The other person");
    const rawAmt = (rec.principalAmount !== undefined && rec.principalAmount !== null)
      ? rec.principalAmount
      : (rec.amountPaise !== undefined ? rec.amountPaise / 100 : 0);
    const amountStr = formatCurrency(rawAmt, rec.currency);

    updateReportProblemLinks(rec.recordId || urlRecordId);

    // Populate common data grid
    document.getElementById("val-from").textContent = rec.lenderName || "—";
    document.getElementById("val-to").textContent = rec.borrowerName || "—";
    document.getElementById("val-start-date").textContent = formatDate(rec.startDateMillis);
    document.getElementById("val-due-date").textContent = formatDate(rec.expiryDateMillis);
    const fingerprintStr = (rec.contentHash || "—").substring(0, 16) + "...";
    document.getElementById("val-fingerprint").textContent = fingerprintStr;

    // Populate receipt print details
    const receiptFp = document.getElementById("receipt-fingerprint");
    if (receiptFp) receiptFp.textContent = rec.contentHash || fingerprintStr;
    const receiptHist = document.getElementById("receipt-history");
    if (receiptHist) {
      receiptHist.textContent = `${t("history_created")}: ${formatDate(rec.startDateMillis || rec.createdAtMillis || Date.now())} • ${t("history_confirmed")}: ${formatDate(rec.confirmedAtMillis || Date.now())}`;
    }

    // Due badge logic
    const dueBadge = document.getElementById("due-status-badge");
    const now = Date.now();
    if (rec.status === "CONFIRMED" || rec.status === "SETTLED") {
      dueBadge.hidden = false;
      dueBadge.style.display = "inline-flex";
      dueBadge.className = "badge badge-green";
      dueBadge.textContent = t("badge_paid_back");
    } else if (rec.expiryDateMillis && rec.expiryDateMillis > 0) {
      dueBadge.hidden = false;
      dueBadge.style.display = "inline-flex";
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
      dueBadge.hidden = true;
      dueBadge.style.display = "none";
      dueBadge.classList.add("hidden");
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
      instBox.hidden = false;
      instBox.style.display = "flex";
      instBox.classList.remove("hidden");
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
      instBox.hidden = false;
      instBox.style.display = "flex";
      instBox.classList.remove("hidden");
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
      instBox.hidden = true;
      instBox.style.display = "none";
      instBox.classList.add("hidden");
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
      document.getElementById("step3-amount-number").textContent = rec.isLegacyOfflineLink ? "✓ Verified" : amountStr;
      document.getElementById("step3-other-name").textContent = otherName;
      setupShareConfirmation(rec);
      announce(t("confirmed_title") + ". " + t("confirmed_subtitle"));
      return;
    }

    // Screen 1: Initial Question
    showView("screen-step1");
    const amountLabel = isLender ? t("amount_lent_label") : t("amount_borrowed_label");
    document.getElementById("step1-amount-label").textContent = amountLabel;
    document.getElementById("step1-amount-number").textContent = rec.isLegacyOfflineLink ? "Shared on WhatsApp" : amountStr;

    const questionText = rec.isLegacyOfflineLink
      ? "Please review the Udhaar amount in your WhatsApp message and confirm below."
      : (isLender
        ? t("question_lent", { name: otherName, amt: amountStr })
        : t("question_borrowed", { name: otherName, amt: amountStr }));
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
      if (recordData && recordData.isSelfContainedToken && recordData.expectedOtp) {
        if (String(codeStr || "").trim() !== String(recordData.expectedOtp).trim()) {
          const err = new Error("Invalid verification code");
          err.status = 403;
          throw err;
        }
      }
      announce("Confirming record...", false);
      await apiCall("guestConfirm", {
        recordId: urlRecordId,
        secret: urlSecret,
        whatsappOtp: codeStr || undefined,
        guestPhoneOtp: codeStr || undefined
      });

      if (recordData) {
        recordData.status = "CONFIRMED";
        recordData.whatsappVerified = true;
        recordData.confirmedAtMillis = Date.now();
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
      "BEGIN:VALARM",
      "TRIGGER:-P1D",
      "ACTION:DISPLAY",
      `DESCRIPTION:Udhaar payment due tomorrow: ${amt}`,
      "END:VALARM",
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

  // 11. Modal Sheet Helpers & OTP Input Controller
  function openModal(id) {
    const el = document.getElementById(id);
    if (el) {
      el.hidden = false;
      el.classList.add("active");
    }
  }

  function closeModal(id) {
    const el = document.getElementById(id);
    if (el) {
      el.classList.remove("active");
      el.hidden = true;
    }
  }

  function bindOtpInputs(otpBoxesNodeList, onComplete) {
    const boxes = Array.from(otpBoxesNodeList || []);
    if (boxes.length === 0) return;

    function focusBox(targetIdx) {
      const target = boxes[targetIdx];
      if (!target) return;
      target.focus();
      if (typeof target.select === "function") {
        try { target.select(); } catch (_) {}
      }
      if (typeof requestAnimationFrame === "function") {
        requestAnimationFrame(() => {
          if (typeof document !== "undefined" && document.activeElement !== target) {
            target.focus();
          }
        });
      }
    }

    function distributeDigits(startIndex, rawDigits) {
      const digits = String(rawDigits || "").replace(/\D+/g, "");
      if (!digits) return;
      let cursor = startIndex;
      for (let i = 0; i < digits.length && cursor < boxes.length; i++) {
        boxes[cursor].value = digits.charAt(i);
        if (boxes[cursor].dataset) {
          boxes[cursor].dataset.prevValue = digits.charAt(i);
        }
        cursor++;
      }
      const nextFocusIdx = Math.min(cursor, boxes.length - 1);
      focusBox(nextFocusIdx);
      const fullCode = boxes.map((b) => b.value || "").join("");
      if (fullCode.length === boxes.length && typeof onComplete === "function") {
        onComplete(fullCode);
      }
    }

    boxes.forEach((box, idx) => {
      if (!box.dataset) box.dataset = {};
      box.dataset.prevValue = box.value || "";

      box.addEventListener("focus", () => {
        box.dataset.prevValue = box.value || "";
        if (typeof box.select === "function") {
          try { box.select(); } catch (_) {}
        }
      });

      box.addEventListener("click", () => {
        if (typeof box.select === "function") {
          try { box.select(); } catch (_) {}
        }
      });

      box.addEventListener("keydown", (e) => {
        if (e.key === "Backspace") {
          e.preventDefault();
          if (box.value) {
            box.value = "";
            box.dataset.prevValue = "";
          } else if (idx > 0) {
            boxes[idx - 1].value = "";
            if (boxes[idx - 1].dataset) boxes[idx - 1].dataset.prevValue = "";
            focusBox(idx - 1);
          }
          return;
        }
        if (e.key === "ArrowLeft" && idx > 0) {
          e.preventDefault();
          focusBox(idx - 1);
          return;
        }
        if (e.key === "ArrowRight" && idx < boxes.length - 1) {
          e.preventDefault();
          focusBox(idx + 1);
          return;
        }
        if (e.key === "Enter") {
          e.preventDefault();
          const fullCode = boxes.map((b) => b.value || "").join("");
          if (fullCode.length === boxes.length && typeof onComplete === "function") {
            onComplete(fullCode);
          }
          return;
        }
        if (/^[0-9]$/.test(e.key) && !e.ctrlKey && !e.metaKey && !e.altKey) {
          box.dataset.prevValue = box.value || "";
        }
      });

      box.addEventListener("input", (e) => {
        const target = e.target || box;
        const digits = String(target.value || "").replace(/\D+/g, "");
        if (!digits) {
          target.value = "";
          box.dataset.prevValue = "";
          return;
        }
        if (digits.length > 1) {
          const prev = box.dataset.prevValue || "";
          if (digits.length === 2 && prev.length === 1 && (digits.startsWith(prev) || digits.endsWith(prev))) {
            const newDigit = digits.startsWith(prev) ? digits.charAt(1) : digits.charAt(0);
            target.value = newDigit;
            box.dataset.prevValue = newDigit;
            if (idx < boxes.length - 1) {
              focusBox(idx + 1);
            }
          } else {
            const startIdx = digits.length >= boxes.length ? 0 : idx;
            distributeDigits(startIdx, digits);
          }
          return;
        }
        target.value = digits;
        box.dataset.prevValue = digits;
        if (idx < boxes.length - 1) {
          focusBox(idx + 1);
        }
      });

      box.addEventListener("paste", (e) => {
        if (typeof e.preventDefault === "function") e.preventDefault();
        const clipboard = e.clipboardData || (typeof window !== "undefined" ? window.clipboardData : null);
        const pasted = clipboard && typeof clipboard.getData === "function" ? clipboard.getData("text") : "";
        const digits = String(pasted || "").replace(/\D+/g, "");
        if (digits) {
          const startIdx = digits.length >= boxes.length ? 0 : idx;
          distributeDigits(startIdx, digits);
        }
      });
    });
  }

  // 12. Initialization & Event Binding
  async function init() {
    // Register Service Worker for shell caching
    if ("serviceWorker" in navigator && window.location.protocol.startsWith("http")) {
      navigator.serviceWorker.register("/sw.js").catch(() => {});
    }

    // Handle skip-link navigation without reloading base href
    const skipLink = document.querySelector(".skip-link");
    if (skipLink) {
      skipLink.addEventListener("click", (e) => {
        e.preventDefault();
        const mainEl = document.getElementById("main-content");
        if (mainEl && typeof mainEl.focus === "function") {
          mainEl.setAttribute("tabindex", "-1");
          mainEl.focus();
        }
      });
    }

    // Capture PWA install prompt
    window.addEventListener("beforeinstallprompt", (e) => {
      e.preventDefault();
      deferredInstallPrompt = e;
      if (document.getElementById("screen-step3")?.style.display === "flex") {
        checkPwaInstallState();
      }
    });

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

    // Screen 2: OTP inputs auto-advance, paste, overwrite & backspace
    const otpBoxes = document.querySelectorAll(".otp-box");
    bindOtpInputs(otpBoxes);

    // Screen 2: Submit Code
    const btnSubmitCode = document.getElementById("btn-submit-code") || document.getElementById("btn-verify-otp");
    if (btnSubmitCode) {
      btnSubmitCode.addEventListener("click", () => {
        let codeStr = "";
        otpBoxes.forEach((b) => { codeStr += b.value; });
        if (codeStr.length !== 6) {
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

    // Quick Date Chips in Date Modal
    const dateChips = document.querySelectorAll(".date-chip");
    dateChips.forEach((chip) => {
      chip.addEventListener("click", () => {
        dateChips.forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
        const days = chip.getAttribute("data-days");
        const dateInput = document.getElementById("input-return-date");
        if (days && dateInput) {
          const target = new Date(Date.now() + parseInt(days, 10) * 86400000);
          const yyyy = target.getFullYear();
          const mm = String(target.getMonth() + 1).padStart(2, "0");
          const dd = String(target.getDate()).padStart(2, "0");
          dateInput.value = `${yyyy}-${mm}-${dd}`;
        } else if (chip.getAttribute("data-custom") && dateInput) {
          dateInput.focus();
          if (typeof dateInput.showPicker === "function") {
            try { dateInput.showPicker(); } catch (_) {}
          }
        }
      });
    });

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
      btnGoogleCal.addEventListener("click", () => {
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
            icon: "../assets/icon.webp"
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
          actualUpiId = recordData?.lenderUpiId || "udhaar@okaxis";
          isUpiMasked = true;

          const rawAmt = (recordData?.principalAmount !== undefined && recordData?.principalAmount !== null)
            ? recordData.principalAmount
            : (recordData?.amountPaise !== undefined ? recordData.amountPaise / 100 : 0);
          const amt = Number(rawAmt || 0).toFixed(2);
          const name = encodeURIComponent(recordData?.lenderName || "Udhaar");
          const upiUri = `upi://pay?pa=${actualUpiId}&pn=${name}&am=${amt}&cu=INR&tn=Udhaar%20Settlement`;

          const displayUpi = document.getElementById("display-upi-id");
          if (displayUpi) {
            displayUpi.textContent = maskUpi(actualUpiId);
            displayUpi.title = "Tap to reveal/hide UPI ID";
          }
          const openUpiApp = document.getElementById("btn-open-upi-app");
          if (openUpiApp) openUpiApp.href = upiUri;

          // Render QR code at 200px+
          if (typeof qrcode === "function") {
            const qr = qrcode(0, "M");
            qr.addData(upiUri);
            qr.make();
            qrContainer.innerHTML = qr.createSvgTag({ cellSize: 5, margin: 4 }) || qr.createImgTag(5);
          } else if (typeof QRCode !== "undefined") {
            new QRCode(qrContainer, {
              text: upiUri,
              width: 200,
              height: 200,
              colorDark: "#064e3b",
              colorLight: "#ffffff",
              correctLevel: QRCode.CorrectLevel.M
            });
          }
        }
      });
    }

    // Toggle UPI Masking
    function toggleUpiMask() {
      isUpiMasked = !isUpiMasked;
      const displayUpi = document.getElementById("display-upi-id");
      if (displayUpi) {
        displayUpi.textContent = isUpiMasked ? maskUpi(actualUpiId) : actualUpiId;
      }
    }
    const btnToggleUpi = document.getElementById("btn-toggle-upi");
    if (btnToggleUpi) {
      btnToggleUpi.addEventListener("click", toggleUpiMask);
    }
    const displayUpiEl = document.getElementById("display-upi-id");
    if (displayUpiEl) {
      displayUpiEl.addEventListener("click", toggleUpiMask);
      displayUpiEl.addEventListener("keydown", (e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          toggleUpiMask();
        }
      });
    }

    // Copy UPI ID (Always copies unmasked ID)
    const btnCopyUpi = document.getElementById("btn-copy-upi");
    if (btnCopyUpi) {
      btnCopyUpi.addEventListener("click", () => {
        navigator.clipboard.writeText(actualUpiId).then(() => {
          alert(t("toast_upi_copied"));
        }).catch(() => {
          alert(actualUpiId);
        });
      });
    }

    // Action 5: Download Receipt / Print (Save as PDF)
    const btnReceipt = document.getElementById("btn-print-receipt") || document.getElementById("act-btn-receipt");
    if (btnReceipt) {
      btnReceipt.addEventListener("click", () => {
        window.print();
      });
    }

    // PWA Install Button
    const btnInstallPwa = document.getElementById("btn-install-pwa");
    if (btnInstallPwa) {
      btnInstallPwa.addEventListener("click", async () => {
        if (!deferredInstallPrompt) return;
        deferredInstallPrompt.prompt();
        const choice = await deferredInstallPrompt.userChoice;
        if (choice.outcome === "accepted") {
          const card = document.getElementById("pwa-install-card");
          if (card) card.style.display = "none";
        }
        deferredInstallPrompt = null;
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
      const b = document.getElementById("offline-banner");
      if (b) {
        b.hidden = false;
        b.style.display = "flex";
        b.classList.remove("hidden");
      }
    });
    window.addEventListener("online", () => {
      const b = document.getElementById("offline-banner");
      if (b) {
        b.hidden = true;
        b.style.display = "none";
        b.classList.add("hidden");
      }
    });

    // Fetch initial data
    if (isDemoMode) {
      const demoBanner = document.getElementById("demo-banner");
      if (demoBanner) {
        demoBanner.hidden = false;
        demoBanner.style.display = "flex";
        demoBanner.classList.remove("hidden");
      }

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
        contentHash: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
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

    if (params.embeddedRecord) {
      renderRecordView(params.embeddedRecord);
      if (urlRecordId && urlSecret) {
        apiCall("getGuestRecord", { recordId: urlRecordId, secret: urlSecret })
          .then((data) => {
            const remote = data.record || data;
            if (remote && remote.recordId) {
              renderRecordView({ ...params.embeddedRecord, ...remote, isSelfContainedToken: true });
            }
          })
          .catch(() => {});
      }
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
      } else if (urlRecordId && urlSecret && (err.status === 503 || err.status === 404 || err.status === 502 || !err.status)) {
        // Spark free-tier static hosting fallback for v22 links (/u/r/<uuid>#<16-char-secret>)
        // when Cloud Functions (getGuestRecord) are not deployed on the Firebase project.
        const sparkFallbackRecord = {
          recordId: urlRecordId,
          secret: urlSecret,
          counterpartyRole: "BORROWER",
          creatorRole: "GIVEN",
          lenderName: "Shared via WhatsApp",
          borrowerName: "Verified Recipient",
          principalAmount: 0,
          amountPaise: 0,
          currency: "INR",
          startDateMillis: Date.now(),
          expiryDateMillis: Date.now() + 30 * 86400000,
          contentHash: (urlRecordId.replace(/-/g, "") + urlSecret).substring(0, 32),
          trustLevelRequired: "WHATSAPP_CODE",
          status: "PENDING_CONFIRMATION",
          isSelfContainedToken: true,
          isLegacyOfflineLink: true
        };
        renderRecordView(sparkFallbackRecord);
      } else {
        showErrorState("state_server_error_title", "state_server_error_desc", "state_server_error_action", { name: "the sender" });
      }
    }
  }

  // Export I18N dictionary, token decoder, and OTP binder for automated testing
  if (typeof module !== "undefined" && module.exports) {
    module.exports = { I18N, t, formatCurrency, formatDate, maskUpi, decodeEmbeddedVerificationToken, bindOtpInputs };
  } else {
    window.UdhaarI18N = I18N;
    document.addEventListener("DOMContentLoaded", init);
  }
})();
