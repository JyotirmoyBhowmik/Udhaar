/**
 * Udhaar Web App Production Bundle
 * Generated: 2026-11-07T07:39:09.796Z
 */
(function() {
  "use strict";

  const __modules = {};
  function __require(name) {
    if (__modules[name]) return __modules[name].exports;
    throw new Error("Module not found: " + name);
  }

  // --- Module: i18n ---
  (function() {
    const module = { exports: {} };
    const exports = module.exports;
/**
 * Udhaar Web App Internationalization (i18n) Engine (webapp/src/i18n/index.js)
 * Supports 5 languages: English (en), Hindi (hi), Bengali (bn), Assamese (as), Nepali (ne).
 * Enforces plain-language terminology, zero jargon, and 100% dictionary coverage.
 */

const DICTIONARIES = {
  en: {
    app_name: "Udhaar",
    tagline: "Clear records of money lent and borrowed",
    nav_records: "Records",
    nav_people: "People",
    nav_reminders: "Reminders",
    nav_settings: "Settings",
    sign_in: "Sign In",
    sign_up: "Sign Up",
    sign_out: "Sign Out",
    offline_badge: "Offline",
    online_badge: "Online",
    offline_notice: "You are offline. Your records are saved safely on this device.",
    
    // Sign-up stepper
    signup_title: "Create your Udhaar account",
    signup_subtitle: "Access your ledger securely across your phone and computer",
    phone_label: "Mobile Number",
    phone_hint: "We will send a 6-digit verification code on WhatsApp",
    phone_placeholder: "98765 43210",
    terms_agree_prefix: "I agree to the",
    terms_link: "Terms of Service",
    and_text: "and",
    privacy_link: "Privacy Policy",
    age_confirm_label: "I confirm that I am 18 years of age or older",
    whatsapp_consent_label: "Receive important ledger updates on WhatsApp",
    continue_button: "Continue",
    sending_code: "Sending code...",

    // OTP verification
    otp_title_whatsapp: "Enter WhatsApp code",
    otp_subtitle_whatsapp: "Enter the 6-digit code sent to your WhatsApp number",
    otp_input_label: "6-digit verification code",
    resend_code_in: "Resend code in",
    resend_code_now: "Resend Code",
    verify_code_button: "Verify Code",
    verifying: "Verifying...",
    change_number: "Change number",

    // Email step
    email_title: "Add your email address",
    email_subtitle: "Used for account recovery and important security alerts",
    email_label: "Email Address",
    email_placeholder: "name@example.com",
    email_hint: "We will send a 6-digit confirmation code to this address",
    email_disposable_error: "Temporary or disposable email addresses are not supported.",
    send_email_code: "Send Email Code",

    // Email OTP
    otp_title_email: "Enter Email code",
    otp_subtitle_email: "Enter the 6-digit code sent to your email",
    change_email: "Change email",

    // Profile onboarding
    profile_title: "What is your name?",
    profile_subtitle: "This name will appear on record confirmations and statements",
    display_name_label: "Full Name",
    display_name_placeholder: "Ravi Sharma",
    complete_signup_button: "Complete & Open Ledger",
    welcome_user: "Welcome to Udhaar",

    // Sign-in
    signin_title: "Welcome back",
    signin_subtitle: "Enter your mobile number to sign in to your ledger",
    signin_phone_label: "Registered Mobile Number",
    send_login_code: "Send Login Code",
    login_otp_title: "Enter Login Code",
    login_otp_subtitle: "Enter the 6-digit code received on WhatsApp",
    dont_have_account: "New to Udhaar? Create an account",
    already_have_account: "Already have an account? Sign in",

    // Dashboard Overview
    net_balance: "Net Balance",
    you_will_get: "You will receive",
    you_will_give: "You will give",
    total_records: "Total records",
    no_records_title: "No records yet",
    no_records_desc: "Add your first entry or connect your phone to bring over your existing khata.",
    add_first_record: "Add Record",

    // Settings
    settings_title: "Account & Settings",
    profile_heading: "Profile",
    language_switcher: "Language",
    theme_heading: "Theme",
    theme_toggle_light: "Light",
    theme_toggle_dark: "Dark",
    theme_toggle_system: "System Default",
    security_heading: "Security & Devices",
    active_devices_heading: "Signed-in Devices",
    revoke_device: "Sign Out Device",
    privacy_notice_heading: "Data Protection & Privacy",
    dpdp_summary: "Your data is protected under India DPDP Act 2023. You can export or delete your account at any time.",

    // Errors
    err_invalid_phone: "Please enter a valid 10-digit mobile number.",
    err_invalid_email: "Please enter a valid email address.",
    err_invalid_code: "The code you entered is incorrect. Please check and try again.",
    err_rate_limit: "Too many attempts. Please wait a short while before trying again.",
    err_account_locked: "Too many incorrect tries. For security, please try again in 15 minutes.",
    err_session_expired: "Your session has expired. Please sign in again.",
    err_network_offline: "You are currently offline. Check your internet connection."
  },

  hi: {
    app_name: "उधार",
    tagline: "दिए और लिए गए पैसों का स्पष्ट हिसाब",
    nav_records: "रिकॉर्ड",
    nav_people: "लोग",
    nav_reminders: "याद दिलाएं",
    nav_settings: "सेटिंग्स",
    sign_in: "साइन इन करें",
    sign_up: "खाता बनाएं",
    sign_out: "साइन आउट",
    offline_badge: "ऑफ़लाइन",
    online_badge: "ऑनलाइन",
    offline_notice: "आप ऑफ़लाइन हैं। आपके रिकॉर्ड इस डिवाइस पर सुरक्षित हैं।",

    signup_title: "अपना उधार खाता बनाएं",
    signup_subtitle: "अपने फोन और कंप्यूटर पर सुरक्षित रूप से अपना बहीखाता देखें",
    phone_label: "मोबाइल नंबर",
    phone_hint: "हम WhatsApp पर 6 अंकों का सत्यापन कोड भेजेंगे",
    phone_placeholder: "98765 43210",
    terms_agree_prefix: "मैं सहमत हूँ",
    terms_link: "सेवा की शर्तें",
    and_text: "और",
    privacy_link: "गोपनीयता नीति",
    age_confirm_label: "मैं पुष्टि करता हूँ कि मेरी आयु 18 वर्ष या उससे अधिक है",
    whatsapp_consent_label: "WhatsApp पर ज़रूरी अपडेट प्राप्त करें",
    continue_button: "आगे बढ़ें",
    sending_code: "कोड भेजा जा रहा है...",

    otp_title_whatsapp: "WhatsApp कोड दर्ज करें",
    otp_subtitle_whatsapp: "अपने WhatsApp पर आया 6 अंकों का कोड दर्ज करें",
    otp_input_label: "6 अंकों का सत्यापन कोड",
    resend_code_in: "कोड दोबारा भेजें",
    resend_code_now: "कोड दोबारा भेजें",
    verify_code_button: "कोड सत्यापित करें",
    verifying: "सत्यापित हो रहा है...",
    change_number: "नंबर बदलें",

    email_title: "अपना ईमेल पता जोड़ें",
    email_subtitle: "खाता पुनर्प्राप्ति और सुरक्षा सूचनाओं के लिए उपयोगी",
    email_label: "ईमेल पता",
    email_placeholder: "name@example.com",
    email_hint: "हम इस पते पर 6 अंकों का पुष्टि कोड भेजेंगे",
    email_disposable_error: "अस्थायी ईमेल पते मान्य नहीं हैं।",
    send_email_code: "ईमेल कोड भेजें",

    otp_title_email: "ईमेल कोड दर्ज करें",
    otp_subtitle_email: "अपने ईमेल पर आया 6 अंकों का कोड दर्ज करें",
    change_email: "ईमेल बदलें",

    profile_title: "आपका नाम क्या है?",
    profile_subtitle: "यह नाम लेनदेन की पुष्टि और रसीदों पर दिखाई देगा",
    display_name_label: "पूरा नाम",
    display_name_placeholder: "रवि शर्मा",
    complete_signup_button: "पूरा करें और खाता खोलें",
    welcome_user: "उधार में आपका स्वागत है",

    signin_title: "वापसी पर स्वागत है",
    signin_subtitle: "अपने खाते में प्रवेश के लिए मोबाइल नंबर दर्ज करें",
    signin_phone_label: "पंजीकृत मोबाइल नंबर",
    send_login_code: "लॉगिन कोड भेजें",
    login_otp_title: "लॉगिन कोड दर्ज करें",
    login_otp_subtitle: "WhatsApp पर प्राप्त 6 अंकों का कोड दर्ज करें",
    dont_have_account: "नया खाता बनाएं",
    already_have_account: "पहले से खाता है? साइन इन करें",

    net_balance: "शुद्ध शेष (कुल बैलेंस)",
    you_will_get: "आपको मिलेंगे",
    you_will_give: "आपको देने हैं",
    total_records: "कुल प्रविष्टियां",
    no_records_title: "अभी कोई रिकॉर्ड नहीं है",
    no_records_desc: "अपनी पहली प्रविष्टि जोड़ें या अपने फोन से पुराना बहीखाता लाएं।",
    add_first_record: "नया रिकॉर्ड जोड़ें",

    settings_title: "खाता और सेटिंग्स",
    profile_heading: "प्रोफ़ाइल",
    language_switcher: "भाषा",
    theme_heading: "थीम",
    theme_toggle_light: "लाइट",
    theme_toggle_dark: "डार्क",
    theme_toggle_system: "सिस्टम अनुसार",
    security_heading: "सुरक्षा और डिवाइस",
    active_devices_heading: "सक्रिय डिवाइस",
    revoke_device: "डिवाइस साइन आउट करें",
    privacy_notice_heading: "डेटा सुरक्षा और गोपनीयता",
    dpdp_summary: "आपका डेटा भारत DPDP अधिनियम 2023 के तहत सुरक्षित है। आप कभी भी अपना डेटा निकाल या हटा सकते हैं।",

    err_invalid_phone: "कृपया मान्य 10 अंकों का मोबाइल नंबर दर्ज करें।",
    err_invalid_email: "कृपया एक मान्य ईमेल पता दर्ज करें।",
    err_invalid_code: "दर्ज किया गया कोड गलत है। कृपया जांचकर पुनः प्रयास करें।",
    err_rate_limit: "बहुत सारे प्रयास किए गए। कृपया थोड़ी देर बाद पुनः प्रयास करें।",
    err_account_locked: "बार-बार गलत कोड। सुरक्षा के लिए कृपया 15 मिनट बाद प्रयास करें।",
    err_session_expired: "सत्र समाप्त हो गया है। कृपया पुनः साइन इन करें।",
    err_network_offline: "आप अभी ऑफ़लाइन हैं। अपना इंटरनेट कनेक्शन जांचें।"
  },

  bn: {
    app_name: "উধার",
    tagline: "দেনা-পাওনার স্পষ্ট ও নির্ভুল হিসাব",
    nav_records: "রেকর্ড",
    nav_people: "পরিচিত",
    nav_reminders: "স্মারক",
    nav_settings: "সেটিংস",
    sign_in: "সাইন ইন করুন",
    sign_up: "অ্যাকাউন্ট খুলুন",
    sign_out: "সাইন আউট",
    offline_badge: "অফলাইন",
    online_badge: "অনলাইন",
    offline_notice: "আপনি অফলাইনে আছেন। আপনার খাতার হিসাব এই ডিভাইসে সুরক্ষিত আছে।",

    signup_title: "আপনার উধার অ্যাকাউন্ট খুলুন",
    signup_subtitle: "মোবাইল ও কম্পিউটারে নিরাপদে আপনার খাতা ব্যবহার করুন",
    phone_label: "মোবাইল নম্বর",
    phone_hint: "আমরা WhatsApp-এ ৬ সংখ্যার যাচাইকরণ কোড পাঠাব",
    phone_placeholder: "98765 43210",
    terms_agree_prefix: "আমি সম্মত হচ্ছি",
    terms_link: "ব্যবহারের শর্তাবলী",
    and_text: "এবং",
    privacy_link: "গোপনীয়তা নীতি",
    age_confirm_label: "আমি নিশ্চিত করছি যে আমার বয়স ১৮ বছর বা তার বেশি",
    whatsapp_consent_label: "WhatsApp-এ প্রয়োজনীয় হিসাবের আপডেট পান",
    continue_button: "পরবর্তী ধাপ",
    sending_code: "কোড পাঠানো হচ্ছে...",

    otp_title_whatsapp: "WhatsApp কোড লিখুন",
    otp_subtitle_whatsapp: "আপনার WhatsApp-এ আসা ৬ সংখ্যার কোডটি লিখুন",
    otp_input_label: "৬ সংখ্যার যাচাইকরণ কোড",
    resend_code_in: "পুনরায় পাঠান",
    resend_code_now: "কোড পুনরায় পাঠান",
    verify_code_button: "কোড যাচাই করুন",
    verifying: "যাচাই করা হচ্ছে...",
    change_number: "নম্বর পরিবর্তন করুন",

    email_title: "আপনার ইমেল ঠিকানা যোগ করুন",
    email_subtitle: "অ্যাকাউন্ট পুনরুদ্ধার এবং নিরাপত্তা বার্তার জন্য প্রয়োজনীয়",
    email_label: "ইমেল ঠিকানা",
    email_placeholder: "name@example.com",
    email_hint: "আমরা এই ঠিকানায় ৬ সংখ্যার নিশ্চিতকরণ কোড পাঠাব",
    email_disposable_error: "অস্থায়ী বা নিষ্পত্তিযোগ্য ইমেল সমর্থিত নয়।",
    send_email_code: "ইমেল কোড পাঠান",

    otp_title_email: "ইমেল কোড লিখুন",
    otp_subtitle_email: "আপনার ইমেলে পাঠানো ৬ সংখ্যার কোডটি লিখুন",
    change_email: "ইমেল পরিবর্তন করুন",

    profile_title: "আপনার নাম কি?",
    profile_subtitle: "লেনদেনের স্বীকৃতি ও স্টেটমেন্টে এই নাম থাকবে",
    display_name_label: "সম্পূর্ণ নাম",
    display_name_placeholder: "রবি শর্মা",
    complete_signup_button: "সম্পন্ন করুন ও খাতা খুলুন",
    welcome_user: "উধারে আপনাকে স্বাগতম",

    signin_title: "স্বাগতম",
    signin_subtitle: "আপনার অ্যাকাউন্টে প্রবেশ করতে মোবাইল নম্বর লিখুন",
    signin_phone_label: "নিবন্ধিত মোবাইল নম্বর",
    send_login_code: "লগইন কোড পাঠান",
    login_otp_title: "লগইন কোড লিখুন",
    login_otp_subtitle: "WhatsApp-এ আসা ৬ সংখ্যার কোডটি লিখুন",
    dont_have_account: "নতুন অ্যাকাউন্ট খুলুন",
    already_have_account: "অ্যাকাউন্ট আছে? সাইন ইন করুন",

    net_balance: "মোট ব্যালেন্স",
    you_will_get: "আপনি পাবেন",
    you_will_give: "আপনাকে দিতে হবে",
    total_records: "মোট হিসাব",
    no_records_title: "এখনো কোনো হিসাব নেই",
    no_records_desc: "আপনার প্রথম হিসাব যোগ করুন বা ফোন থেকে পুরানো খাতা সিঙ্ক করুন।",
    add_first_record: "হিসাব যোগ করুন",

    settings_title: "অ্যাকাউন্ট ও সেটিংস",
    profile_heading: "প্রোফাইল",
    language_switcher: "ভাষা",
    theme_heading: "থিম",
    theme_toggle_light: "লাইট",
    theme_toggle_dark: "ডার্ক",
    theme_toggle_system: "সিস্টেম ডিফল্ট",
    security_heading: "নিরাপত্তা ও ডিভাইস",
    active_devices_heading: "যুক্ত ডিভাইসসমূহ",
    revoke_device: "ডিভাইস সাইন আউট করুন",
    privacy_notice_heading: "তথ্য সুরক্ষা ও গোপনীয়তা",
    dpdp_summary: "আপনার তথ্য ভারতীয় DPDP আইন ২০২৩ অনুসারে সম্পূর্ণ সুরক্ষিত। আপনি যেকোনো সময় আপনার তথ্য মুছে ফেলতে পারেন।",

    err_invalid_phone: "অনুগ্রহ করে সঠিক ১০ সংখ্যার মোবাইল নম্বর লিখুন।",
    err_invalid_email: "অনুগ্রহ করে একটি সঠিক ইমেল ঠিকানা দিন।",
    err_invalid_code: "ভুল কোড দেওয়া হয়েছে। অনুগ্রহ করে আবার পরীক্ষা করুন।",
    err_rate_limit: "অতিরিক্ত চেষ্টা করা হয়েছে। অনুগ্রহ করে কিছুক্ষণ পর চেষ্টা করুন।",
    err_account_locked: "একাধিকবার ভুল কোড। নিরাপত্তার স্বার্থে ১৫ মিনিট পর চেষ্টা করুন।",
    err_session_expired: "আপনার সেশনের মেয়াদ শেষ হয়েছে। পুনরায় সাইন ইন করুন।",
    err_network_offline: "আপনি অফলাইনে আছেন। ইন্টারনেট সংযোগ পরীক্ষা করুন।"
  },

  as: {
    app_name: "উধাৰ",
    tagline: "দিয়া আৰু লোৱা ধনৰ স্পষ্ট খতিয়ান",
    nav_records: "খতিয়ান",
    nav_people: "ব্যক্তি",
    nav_reminders: "স্মাৰক",
    nav_settings: "ছেটিংছ",
    sign_in: "ছাইন ইন কৰক",
    sign_up: "একাউণ্ট খোলক",
    sign_out: "ছাইন আউট",
    offline_badge: "অফলাইন",
    online_badge: "অনলাইন",
    offline_notice: "আপুনি অফলাইনত আছে। আপোনাৰ খতিয়ান এই ডিভাইচত সুৰক্ষিত।",

    signup_title: "আপোনাৰ উধাৰ একাউণ্ট খোলক",
    signup_subtitle: "মবাইল আৰু কম্পিউটাৰত সুৰক্ষিতভাৱে আপোনাৰ খতিয়ান চাওক",
    phone_label: "মবাইল নম্বৰ",
    phone_hint: "আমি WhatsApp যোগে ৬ অংকৰ প্ৰমাণীকৰণ ক'ড পঠিয়াম",
    phone_placeholder: "98765 43210",
    terms_agree_prefix: "মই সন্মত",
    terms_link: "ব্যৱহাৰৰ চৰ্তাৱলী",
    and_text: "আৰু",
    privacy_link: "গোপনীয়তা নীতি",
    age_confirm_label: "মই নিশ্চিত কৰিছোঁ যে মোৰ বয়স ১৮ বছৰ বা তাতকৈ বেছি",
    whatsapp_consent_label: "WhatsApp-ত প্ৰয়োজনীয় আপডেট লাভ কৰক",
    continue_button: "আগবাঢ়ক",
    sending_code: "ক'ড পঠিওৱা হৈছে...",

    otp_title_whatsapp: "WhatsApp ক'ড দিয়ক",
    otp_subtitle_whatsapp: "WhatsApp যোগে পোৱা ৬ অংকৰ ক'ডটো লিখক",
    otp_input_label: "৬ অংকৰ প্ৰমাণীকৰণ ক'ড",
    resend_code_in: "পুনৰ প্ৰেৰণ",
    resend_code_now: "ক'ড পুনৰ পঠিয়াওক",
    verify_code_button: "ক'ড নিশ্চিত কৰক",
    verifying: "প্ৰমাণীকৰণ চলিছে...",
    change_number: "নম্বৰ সলনি কৰক",

    email_title: "আপোনাৰ ইমেইল ঠিকনা দিয়ক",
    email_subtitle: "একাউণ্ট উদ্ধাৰ আৰু সুৰক্ষা জাননীৰ বাবে ব্যৱহৃত",
    email_label: "ইমেইল ঠিকনা",
    email_placeholder: "name@example.com",
    email_hint: "আমি এই ঠিকনাত ৬ অংকৰ নিশ্চিতকৰণ ক'ড পঠিয়াম",
    email_disposable_error: "অস্থায়ী ইমেইল সমৰ্থিত নহয়।",
    send_email_code: "ইমেইল ক'ড পঠিয়াওক",

    otp_title_email: "ইমেইল ক'ড দিয়ক",
    otp_subtitle_email: "আপোনাৰ ইমেইললৈ যোৱা ৬ অংকৰ ক'ডটো দিয়ক",
    change_email: "ইমেইল সলনি কৰক",

    profile_title: "আপোনাৰ নাম কি?",
    profile_subtitle: "লেনদেন নিশ্চিতকৰণ আৰু হিচাপত এই নাম দেখা যাব",
    display_name_label: "সম্পূৰ্ণ নাম",
    display_name_placeholder: "ৰবি শৰ্মা",
    complete_signup_button: "সম্পূৰ্ণ কৰক আৰু খাতা খোলক",
    welcome_user: "উধাৰলৈ স্বাগতম",

    signin_title: "পুনৰ স্বাগতম",
    signin_subtitle: "একাউণ্টত প্ৰৱেশ কৰিবলৈ মবাইল নম্বৰ দিয়ক",
    signin_phone_label: "পঞ্জীভুক্ত মবাইল নম্বৰ",
    send_login_code: "লগইন ক'ড পঠিয়াওক",
    login_otp_title: "লগইন ক'ড দিয়ক",
    login_otp_subtitle: "WhatsApp-ত পোৱা ৬ অংকৰ ক'ডটো দিয়ক",
    dont_have_account: "নতুন একাউণ্ট খোলক",
    already_have_account: "একাউণ্ট আছে? ছাইন ইন কৰক",

    net_balance: "মুঠ বেলেন্স",
    you_will_get: "আপুনি পাব",
    you_will_give: "আপুনি দিব লাগিব",
    total_records: "মুঠ খতিয়ান",
    no_records_title: "কোনো খতিয়ান নাই",
    no_records_desc: "আপোনাৰ প্ৰথমটো হিচাপ যোগ কৰক।",
    add_first_record: "হিচাপ যোগ কৰক",

    settings_title: "একাউণ্ট আৰু ছেটিংছ",
    profile_heading: "প্রোফাইল",
    language_switcher: "ভাষা",
    theme_heading: "থিম",
    theme_toggle_light: "লাইট",
    theme_toggle_dark: "ডাৰ্ক",
    theme_toggle_system: "ছিষ্টেম ডিফল্ট",
    security_heading: "সুৰক্ষা আৰু ডিভাইচ",
    active_devices_heading: "সংযুক্ত ডিভাইচসমূহ",
    revoke_device: "ডিভাইচ ছাইন আউট কৰক",
    privacy_notice_heading: "তথ্য সুৰক্ষা আৰু গোপনীয়তা",
    dpdp_summary: "ভাৰতীয় DPDP আইন ২০২৩ অনুসৰি আপোনাৰ তথ্য সুৰক্ষিত। আপুনি বিচৰা সময়ত তথ্য ডিলিট কৰিব পাৰে।",

    err_invalid_phone: "অনুগ্ৰহ কৰি সঠিক ১০ অংকৰ মবাইল নম্বৰ দিয়ক।",
    err_invalid_email: "অনুগ্ৰহ কৰি সঠিক ইমেইল ঠিকনা দিয়ক।",
    err_invalid_code: "ভুল ক'ড দিয়া হৈছে। পৰীক্ষা কৰি পুনৰ চেষ্টা কৰক।",
    err_rate_limit: "অধিক চেষ্টা কৰা হ'ল। অনুগ্ৰহ কৰি কিছু সময় পাছত চেষ্টা কৰক।",
    err_account_locked: "নিৰাপত্তাৰ বাবে অনুগ্ৰহ কৰি ১৫ মিনিট পাছত চেষ্টা কৰক।",
    err_session_expired: "আপোনাৰ অধিবেশন শেষ হ'ল। অনুগ্ৰহ কৰি পুনৰ ছাইন ইন কৰক।",
    err_network_offline: "আপুনি অফলাইনত আছে। ইণ্টাৰনেট সংযোগ পৰীক্ষা কৰক।"
  },

  ne: {
    app_name: "उधार",
    tagline: "दिएको र लिएको रकमको स्पष्ट हिसाब",
    nav_records: "रेकर्ड",
    nav_people: "व्यक्तिहरू",
    nav_reminders: "स्मरण",
    nav_settings: "सेटिङहरू",
    sign_in: "साइन इन गर्नुहोस्",
    sign_up: "खाता खोल्नुहोस्",
    sign_out: "साइन आउट",
    offline_badge: "अफलाइन",
    online_badge: "अनलाइन",
    offline_notice: "तपाईं अफलाइन हुनुहुन्छ। तपाईंका रेकर्डहरू यस उपकरणमा सुरक्षित छन्।",

    signup_title: "आफ्नो उधार खाता खोल्नुहोस्",
    signup_subtitle: "आफ्नो फोन र कम्प्युटरमा सुरक्षित रूपमा आफ्नो खाता हेर्नुहोस्",
    phone_label: "मोबाइल नम्बर",
    phone_hint: "हामी WhatsApp मा ६ अंकको प्रमाणीकरण कोड पठाउनेछौं",
    phone_placeholder: "98765 43210",
    terms_agree_prefix: "म सहमत छु",
    terms_link: "सेवाका सर्तहरू",
    and_text: "र",
    privacy_link: "गोपनीयता नीति",
    age_confirm_label: "म पुष्टि गर्दछु कि मेरो उमेर १८ वर्ष वा सोभन्दा बढी छ",
    whatsapp_consent_label: "WhatsApp मा महत्त्वपूर्ण अपडेटहरू प्राप्त गर्नुहोस्",
    continue_button: "अगाडि बढ्नुहोस्",
    sending_code: "कोड पठाइँदैछ...",

    otp_title_whatsapp: "WhatsApp कोड प्रविष्ट गर्नुहोस्",
    otp_subtitle_whatsapp: "तपाईंको WhatsApp मा आएको ६ अंकको कोड प्रविष्ट गर्नुहोस्",
    otp_input_label: "६ अंकको प्रमाणीकरण कोड",
    resend_code_in: "पुनः पठाउने समय",
    resend_code_now: "कोड पुनः पठाउनुहोस्",
    verify_code_button: "कोड प्रमाणित गर्नुहोस्",
    verifying: "प्रमाणित गरिँदैछ...",
    change_number: "नम्बर परिवर्तन गर्नुहोस्",

    email_title: "आफ्नो इमेल ठेगाना थप्नुहोस्",
    email_subtitle: "खाता पुनर्प्राप्ति र सुरक्षा अलर्टका लागि उपयोगी",
    email_label: "इमेल ठेगाना",
    email_placeholder: "name@example.com",
    email_hint: "हामी यस ठेगानामा ६ अंकको पुष्टि कोड पठाउनेछौं",
    email_disposable_error: "अस्थायी इमेल ठेगानाहरू मान्य छैनन्।",
    send_email_code: "इमेल कोड पठाउनुहोस्",

    otp_title_email: "इमेल कोड प्रविष्ट गर्नुहोस्",
    otp_subtitle_email: "आफ्नो इमेलमा आएको ६ अंकको कोड प्रविष्ट गर्नुहोस्",
    change_email: "इमेल परिवर्तन गर्नुहोस्",

    profile_title: "तपाईंको नाम के हो?",
    profile_subtitle: "यो नाम कारोबार पुष्टि र विवरणहरूमा देखिनेछ",
    display_name_label: "पूरा नाम",
    display_name_placeholder: "रवि शर्मा",
    complete_signup_button: "सम्पन्न गर्नुहोस् र खाता खोल्नुहोस्",
    welcome_user: "उधारमा स्वागत छ",

    signin_title: "स्वागत छ",
    signin_subtitle: "खातामा प्रवेश गर्न मोबाइल नम्बर प्रविष्ट गर्नुहोस्",
    signin_phone_label: "दर्ता गरिएको मोबाइल नम्बर",
    send_login_code: "लगइन कोड पठाउनुहोस्",
    login_otp_title: "लगइन कोड प्रविष्ट गर्नुहोस्",
    login_otp_subtitle: "WhatsApp मा प्राप्त ६ अंकको कोड प्रविष्ट गर्नुहोस्",
    dont_have_account: "नयाँ खाता बनाउनुहोस्",
    already_have_account: "पहिले नै खाता छ? साइन इन गर्नुहोस्",

    net_balance: "कुल बाँकी (नेट ब्यालेन्स)",
    you_will_get: "तपाईंले पाउनुपर्ने",
    you_will_give: "तपाईंले तिर्नुपर्ने",
    total_records: "कुल कारोबार",
    no_records_title: "अहिलेसम्म कुनै कारोबार छैन",
    no_records_desc: "पहिलो कारोबार प्रविष्टि थप्नुहोस् वा फोनबाट खाता सिङ्क गर्नुहोस्।",
    add_first_record: "नयाँ रेकर्ड थप्नुहोस्",

    settings_title: "खाता र सेटिङहरू",
    profile_heading: "प्रोफाइल",
    language_switcher: "भाषा",
    theme_heading: "थिम",
    theme_toggle_light: "लाइट",
    theme_toggle_dark: "डार्क",
    theme_toggle_system: "प्रणाली अनुसार",
    security_heading: "सुरक्षा र उपकरणहरू",
    active_devices_heading: "सक्रिय उपकरणहरू",
    revoke_device: "उपकरण साइन आउट गर्नुहोस्",
    privacy_notice_heading: "डाटा सुरक्षा र गोपनीयता",
    dpdp_summary: "तपाईंको डाटा पूर्ण रूपमा सुरक्षित छ। तपाईं कुनै पनि समय आफ्नो डाटा डाउनलोड वा मेटाउन सक्नुहुन्छ।",

    err_invalid_phone: "कृपया मान्य १० अंकको मोबाइल नम्बर प्रविष्ट गर्नुहोस्।",
    err_invalid_email: "कृपया मान्य इमेल ठेगाना प्रविष्ट गर्नुहोस्।",
    err_invalid_code: "प्रविष्ट गरिएको कोड मिलेन। कृपया फेरि प्रयास गर्नुहोस्।",
    err_rate_limit: "धेरै प्रयासहरू भए। कृपया केही बेरपछि प्रयास गर्नुहोस्।",
    err_account_locked: "सुरक्षाका लागि कृपया १५ मिनेटपछि प्रयास गर्नुहोस्।",
    err_session_expired: "सत्र समाप्त भयो। कृपया पुनः साइन इन गर्नुहोस्।",
    err_network_offline: "तपाईं हाल अफलाइन हुनुहुन्छ। इन्टरनेट जडान जाँच्नुहोस्।"
  }
};

let currentLocale = "en";

function initLocale() {
  if (typeof window !== "undefined") {
    const saved = localStorage.getItem("udhaar_locale");
    if (saved && DICTIONARIES[saved]) {
      currentLocale = saved;
    } else if (navigator.languages) {
      for (const lang of navigator.languages) {
        const prefix = lang.slice(0, 2).toLowerCase();
        if (DICTIONARIES[prefix]) {
          currentLocale = prefix;
          break;
        }
      }
    }
    document.documentElement.lang = currentLocale;
  }
  return currentLocale;
}

function setLocale(locale) {
  if (DICTIONARIES[locale]) {
    currentLocale = locale;
    if (typeof window !== "undefined") {
      localStorage.setItem("udhaar_locale", locale);
      document.documentElement.lang = locale;
    }
    return true;
  }
  return false;
}

function getLocale() {
  return currentLocale;
}

function t(key, params = {}) {
  const dict = DICTIONARIES[currentLocale] || DICTIONARIES.en;
  let text = dict[key] || DICTIONARIES.en[key] || key;
  for (const [k, v] of Object.entries(params)) {
    text = text.replace(new RegExp(`{${k}}`, "g"), String(v));
  }
  return text;
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    DICTIONARIES,
    initLocale,
    setLocale,
    getLocale,
    t
  };
}

    __modules["i18n"] = module;
  })();

  // --- Module: authClient ---
  (function() {
    const module = { exports: {} };
    const exports = module.exports;
/**
 * Udhaar Auth API Client (webapp/src/api/authClient.js)
 * Interacts with /api/v1/auth endpoints, manages CSRF protection,
 * and formats RFC 9457 Problem Details for friendly UI presentation.
 */

class AuthClient {
  constructor(baseUrl = "") {
    this.baseUrl = baseUrl;
    this.csrfToken = this._getCookie("csrf_token") || this._generateCsrfToken();
  }

  _getCookie(name) {
    if (typeof document === "undefined") return null;
    const match = document.cookie.match(new RegExp("(^| )" + name + "=([^;]+)"));
    return match ? decodeURIComponent(match[2]) : null;
  }

  _generateCsrfToken() {
    if (typeof crypto !== "undefined" && crypto.randomUUID) {
      return crypto.randomUUID();
    }
    return Math.random().toString(36).substring(2) + Date.now().toString(36);
  }

  _getHeaders() {
    const headers = {
      "Content-Type": "application/json",
      "X-CSRF-Token": this.csrfToken,
      "X-Idempotency-Key": this._generateCsrfToken()
    };
    return headers;
  }

  async _handleResponse(res) {
    let data = null;
    try {
      data = await res.json();
    } catch (_) {
      data = {};
    }

    if (!res.ok) {
      const err = new Error(data.detail || data.title || "Authentication request failed");
      err.code = data.code || "ERR_AUTH_FAILED";
      err.status = res.status;
      err.details = data.invalidParams || null;
      throw err;
    }

    return data;
  }

  async startSignup({ phoneNumber, locale = "en", isAge18Plus = true, allowWhatsAppReminders = true }) {
    const res = await fetch(`${this.baseUrl}/api/v1/auth/signup/start`, {
      method: "POST",
      headers: this._getHeaders(),
      body: JSON.stringify({
        phoneNumber,
        locale,
        isAge18Plus,
        allowWhatsAppReminders
      })
    });
    return this._handleResponse(res);
  }

  async verifyFlowOtp({ flowId, code, purpose = "SIGNUP" }) {
    const res = await fetch(`${this.baseUrl}/api/v1/auth/otp/verify`, {
      method: "POST",
      headers: this._getHeaders(),
      body: JSON.stringify({
        flowId,
        code,
        purpose
      })
    });
    return this._handleResponse(res);
  }

  async submitSignupEmail({ flowId, flowToken, email, locale = "en" }) {
    const res = await fetch(`${this.baseUrl}/api/v1/auth/signup/email`, {
      method: "POST",
      headers: this._getHeaders(),
      body: JSON.stringify({
        flowId,
        flowToken,
        email,
        locale
      })
    });
    return this._handleResponse(res);
  }

  async completeSignup({ flowId, flowToken, phoneNumber, email, displayName, isAge18Plus = true, allowWhatsAppReminders = true }) {
    const res = await fetch(`${this.baseUrl}/api/v1/auth/signup/complete`, {
      method: "POST",
      headers: this._getHeaders(),
      body: JSON.stringify({
        flowId,
        flowToken,
        phoneNumber,
        email,
        displayName,
        isAge18Plus,
        allowWhatsAppReminders,
        acceptedTermsVersion: "v2.0",
        acceptedPrivacyVersion: "v2.0",
        clientType: "WEB"
      })
    });
    return this._handleResponse(res);
  }

  async startSignin({ phoneNumber, locale = "en" }) {
    const res = await fetch(`${this.baseUrl}/api/v1/auth/signin/start`, {
      method: "POST",
      headers: this._getHeaders(),
      body: JSON.stringify({
        phoneNumber,
        locale
      })
    });
    return this._handleResponse(res);
  }

  async verifySignin({ flowId, code }) {
    const res = await fetch(`${this.baseUrl}/api/v1/auth/signin/verify`, {
      method: "POST",
      headers: this._getHeaders(),
      body: JSON.stringify({
        flowId,
        code,
        clientType: "WEB"
      })
    });
    return this._handleResponse(res);
  }

  async getMe() {
    const res = await fetch(`${this.baseUrl}/api/v1/me`, {
      method: "GET",
      headers: {
        "Accept": "application/json"
      }
    });
    return this._handleResponse(res);
  }

  async signOut() {
    const res = await fetch(`${this.baseUrl}/api/v1/auth/session/signout`, {
      method: "POST",
      headers: this._getHeaders()
    });
    return this._handleResponse(res);
  }
}

const authClient = new AuthClient();

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    AuthClient,
    authClient
  };
}

    __modules["authClient"] = module;
  })();

  // --- Module: router ---
  (function() {
    const module = { exports: {} };
    const exports = module.exports;
/**
 * Udhaar Web App Client-Side Router (webapp/src/routes/router.js)
 * Clean hash-based SPA routing with route transition lifecycle.
 */

class Router {
  constructor() {
    this.routes = new Map();
    this.currentRoute = null;
    this.currentView = null;
    this.onRouteChanged = null;

    if (typeof window !== "undefined") {
      window.addEventListener("hashchange", () => {
        this._handleRoute();
      });
    }
  }

  addRoute(path, handler) {
    this.routes.set(path, handler);
  }

  navigate(path) {
    if (typeof window !== "undefined") {
      if (window.location.hash !== path) {
        window.location.hash = path;
      } else {
        this._handleRoute();
      }
    }
  }

  getRoute() {
    if (typeof window === "undefined") return "#signin";
    const hash = window.location.hash || "#signin";
    return hash;
  }

  _handleRoute() {
    const route = this.getRoute();
    this.currentRoute = route;

    const handler = this.routes.get(route) || this.routes.get("#signin");
    if (handler) {
      if (this.currentView && typeof this.currentView.destroy === "function") {
        this.currentView.destroy();
      }
      this.currentView = handler(route);
      if (this.onRouteChanged) {
        this.onRouteChanged(route);
      }
    }
  }

  start() {
    this._handleRoute();
  }
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { Router };
}

    __modules["router"] = module;
  })();

  // --- Module: SignUpView ---
  (function() {
    const module = { exports: {} };
    const exports = module.exports;
/**
 * Udhaar Web App Sign-Up Stepper (webapp/src/features/auth/SignUpView.js)
 * Implements 5-step registration: Phone -> WhatsApp OTP -> Email -> Email OTP -> Profile Name.
 * Strict CSP: 100% addEventListener, zero inline scripts/styles.
 */

class SignUpView {
  constructor({ i18n, authClient, router, onToast }) {
    this.i18n = i18n;
    this.authClient = authClient;
    this.router = router;
    this.onToast = onToast;

    this.step = 1;
    this.flowId = null;
    this.flowToken = null;
    this.phoneNumber = "";
    this.email = "";
    this.resendTimer = null;
    this.resendSecondsLeft = 30;
  }

  render(container) {
    this.container = container;
    this._renderCurrentStep();
  }

  _renderCurrentStep() {
    this.container.innerHTML = "";
    const card = document.createElement("div");
    card.className = "card auth-container";

    if (this.step === 1) {
      this._renderStepPhone(card);
    } else if (this.step === 2) {
      this._renderStepWhatsAppOtp(card);
    } else if (this.step === 3) {
      this._renderStepEmail(card);
    } else if (this.step === 4) {
      this._renderStepEmailOtp(card);
    } else if (this.step === 5) {
      this._renderStepProfile(card);
    }

    this.container.appendChild(card);
  }

  // --- Step 1: Phone & DPDP Consents ---
  _renderStepPhone(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("signup_title")}</h1>
        <p>${t("signup_subtitle")}</p>
      </div>

      <form id="signup-phone-form">
        <div class="form-group">
          <label class="form-label" for="phone-input">${t("phone_label")}</label>
          <div class="input-wrap">
            <span class="country-prefix">+91</span>
            <input type="tel" id="phone-input" class="input-field has-prefix" placeholder="${t("phone_placeholder")}" maxlength="10" required autocomplete="tel-national">
          </div>
          <p class="input-hint">${t("phone_hint")}</p>
          <p id="phone-error" class="form-error" style="display: none;"></p>
        </div>

        <div class="checkbox-group">
          <input type="checkbox" id="age-check" class="checkbox-input" required checked>
          <label for="age-check" class="checkbox-label">${t("age_confirm_label")}</label>
        </div>

        <div class="checkbox-group">
          <input type="checkbox" id="terms-check" class="checkbox-input" required checked>
          <label for="terms-check" class="checkbox-label">
            ${t("terms_agree_prefix")} <a href="/terms.html" target="_blank">${t("terms_link")}</a> ${t("and_text")} <a href="/privacy.html" target="_blank">${t("privacy_link")}</a>
          </label>
        </div>

        <div class="checkbox-group">
          <input type="checkbox" id="wa-consent-check" class="checkbox-input" checked>
          <label for="wa-consent-check" class="checkbox-label">${t("whatsapp_consent_label")}</label>
        </div>

        <button type="submit" id="phone-submit-btn" class="btn btn-primary" style="margin-top: 1rem;">
          ${t("continue_button")}
        </button>

        <div style="text-align: center; margin-top: 1.25rem;">
          <a href="#signin" class="btn-link">${t("already_have_account")}</a>
        </div>
      </form>
    `;

    const form = card.querySelector("#signup-phone-form");
    const phoneInput = card.querySelector("#phone-input");
    const submitBtn = card.querySelector("#phone-submit-btn");
    const errEl = card.querySelector("#phone-error");

    phoneInput.focus();

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const rawDigits = phoneInput.value.replace(/[^0-9]/g, "");
      if (rawDigits.length !== 10) {
        errEl.textContent = t("err_invalid_phone");
        errEl.style.display = "block";
        return;
      }

      errEl.style.display = "none";
      submitBtn.disabled = true;
      submitBtn.textContent = t("sending_code");

      this.phoneNumber = `+91 ${rawDigits.slice(0, 5)} ${rawDigits.slice(5)}`;

      try {
        const res = await this.authClient.startSignup({
          phoneNumber: this.phoneNumber,
          locale: this.i18n.getLocale(),
          isAge18Plus: card.querySelector("#age-check").checked,
          allowWhatsAppReminders: card.querySelector("#wa-consent-check").checked
        });

        this.flowId = res.flowId;
        this.resendSecondsLeft = res.resendAfterSeconds || 30;
        this.step = 2;
        this._renderCurrentStep();
      } catch (err) {
        submitBtn.disabled = false;
        submitBtn.textContent = t("continue_button");
        errEl.textContent = err.message || t("err_rate_limit");
        errEl.style.display = "block";
      }
    });
  }

  // --- Step 2: WhatsApp OTP ---
  _renderStepWhatsAppOtp(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("otp_title_whatsapp")}</h1>
        <p>${t("otp_subtitle_whatsapp")}: <strong>${this.phoneNumber}</strong></p>
      </div>

      <form id="signup-otp-form">
        <label class="form-label" style="text-align: center;">${t("otp_input_label")}</label>
        <div class="otp-container">
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="0" required autocomplete="one-time-code">
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="1" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="2" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="3" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="4" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="5" required>
        </div>
        <p id="otp-error" class="form-error" style="text-align: center; display: none;"></p>

        <div style="text-align: center; margin: 1rem 0;">
          <span id="resend-timer-text" style="font-size: 0.875rem; color: var(--color-text-muted);">
            ${t("resend_code_in")} <strong id="countdown-val">${this.resendSecondsLeft}s</strong>
          </span>
          <button type="button" id="resend-otp-btn" class="btn-link" style="display: none; margin: 0 auto;">
            ${t("resend_code_now")}
          </button>
        </div>

        <button type="submit" id="otp-submit-btn" class="btn btn-primary">
          ${t("verify_code_button")}
        </button>

        <div style="text-align: center; margin-top: 1rem;">
          <button type="button" id="change-phone-btn" class="btn-link">
            ${t("change_number")}
          </button>
        </div>
      </form>
    `;

    this._setupOtpDigitAutoJump(card, async (code) => {
      await this._verifyPhoneOtp(card, code);
    });

    this._startResendCountdown(card, async () => {
      try {
        const res = await this.authClient.startSignup({
          phoneNumber: this.phoneNumber,
          locale: this.i18n.getLocale()
        });
        this.flowId = res.flowId;
        this.onToast("Verification code resent on WhatsApp");
      } catch (err) {
        card.querySelector("#otp-error").textContent = err.message;
        card.querySelector("#otp-error").style.display = "block";
      }
    });

    card.querySelector("#change-phone-btn").addEventListener("click", () => {
      this._clearIntervals();
      this.step = 1;
      this._renderCurrentStep();
    });

    card.querySelector("#signup-otp-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const digits = Array.from(card.querySelectorAll(".otp-digit")).map(d => d.value).join("");
      if (digits.length === 6) {
        await this._verifyPhoneOtp(card, digits);
      }
    });
  }

  async _verifyPhoneOtp(card, code) {
    const t = this.i18n.t;
    const submitBtn = card.querySelector("#otp-submit-btn");
    const errEl = card.querySelector("#otp-error");

    submitBtn.disabled = true;
    submitBtn.textContent = t("verifying");
    errEl.style.display = "none";

    try {
      const res = await this.authClient.verifyFlowOtp({
        flowId: this.flowId,
        code,
        purpose: "SIGNUP"
      });

      this.flowToken = res.flowToken;
      this._clearIntervals();
      this.step = 3;
      this._renderCurrentStep();
    } catch (err) {
      submitBtn.disabled = false;
      submitBtn.textContent = t("verify_code_button");
      errEl.textContent = err.message || t("err_invalid_code");
      errEl.style.display = "block";
    }
  }

  // --- Step 3: Email Input ---
  _renderStepEmail(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("email_title")}</h1>
        <p>${t("email_subtitle")}</p>
      </div>

      <form id="signup-email-form">
        <div class="form-group">
          <label class="form-label" for="email-input">${t("email_label")}</label>
          <input type="email" id="email-input" class="input-field" placeholder="${t("email_placeholder")}" required autocomplete="email">
          <p class="input-hint">${t("email_hint")}</p>
          <p id="email-error" class="form-error" style="display: none;"></p>
        </div>

        <button type="submit" id="email-submit-btn" class="btn btn-primary" style="margin-top: 1rem;">
          ${t("send_email_code")}
        </button>
      </form>
    `;

    const form = card.querySelector("#signup-email-form");
    const emailInput = card.querySelector("#email-input");
    const submitBtn = card.querySelector("#email-submit-btn");
    const errEl = card.querySelector("#email-error");

    emailInput.focus();

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const rawEmail = emailInput.value.trim().toLowerCase();
      if (!rawEmail.includes("@") || !rawEmail.includes(".")) {
        errEl.textContent = t("err_invalid_email");
        errEl.style.display = "block";
        return;
      }

      errEl.style.display = "none";
      submitBtn.disabled = true;
      submitBtn.textContent = t("sending_code");

      this.email = rawEmail;

      try {
        const res = await this.authClient.submitSignupEmail({
          flowId: this.flowId,
          flowToken: this.flowToken,
          email: this.email,
          locale: this.i18n.getLocale()
        });

        this.resendSecondsLeft = res.resendAfterSeconds || 30;
        this.step = 4;
        this._renderCurrentStep();
      } catch (err) {
        submitBtn.disabled = false;
        submitBtn.textContent = t("send_email_code");
        errEl.textContent = err.message || t("email_disposable_error");
        errEl.style.display = "block";
      }
    });
  }

  // --- Step 4: Email OTP ---
  _renderStepEmailOtp(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("otp_title_email")}</h1>
        <p>${t("otp_subtitle_email")}: <strong>${this.email}</strong></p>
      </div>

      <form id="email-otp-form">
        <label class="form-label" style="text-align: center;">${t("otp_input_label")}</label>
        <div class="otp-container">
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="0" required autocomplete="one-time-code">
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="1" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="2" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="3" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="4" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="5" required>
        </div>
        <p id="email-otp-error" class="form-error" style="text-align: center; display: none;"></p>

        <div style="text-align: center; margin: 1rem 0;">
          <span id="resend-timer-text" style="font-size: 0.875rem; color: var(--color-text-muted);">
            ${t("resend_code_in")} <strong id="countdown-val">${this.resendSecondsLeft}s</strong>
          </span>
          <button type="button" id="resend-otp-btn" class="btn-link" style="display: none; margin: 0 auto;">
            ${t("resend_code_now")}
          </button>
        </div>

        <button type="submit" id="email-otp-submit-btn" class="btn btn-primary">
          ${t("verify_code_button")}
        </button>

        <div style="text-align: center; margin-top: 1rem;">
          <button type="button" id="change-email-btn" class="btn-link">
            ${t("change_email")}
          </button>
        </div>
      </form>
    `;

    this._setupOtpDigitAutoJump(card, async (code) => {
      await this._verifyEmailOtp(card, code);
    });

    this._startResendCountdown(card, async () => {
      try {
        await this.authClient.submitSignupEmail({
          flowId: this.flowId,
          flowToken: this.flowToken,
          email: this.email,
          locale: this.i18n.getLocale()
        });
        this.onToast("Verification code resent to your email");
      } catch (err) {
        card.querySelector("#email-otp-error").textContent = err.message;
        card.querySelector("#email-otp-error").style.display = "block";
      }
    });

    card.querySelector("#change-email-btn").addEventListener("click", () => {
      this._clearIntervals();
      this.step = 3;
      this._renderCurrentStep();
    });

    card.querySelector("#email-otp-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const digits = Array.from(card.querySelectorAll(".otp-digit")).map(d => d.value).join("");
      if (digits.length === 6) {
        await this._verifyEmailOtp(card, digits);
      }
    });
  }

  async _verifyEmailOtp(card, code) {
    const t = this.i18n.t;
    const submitBtn = card.querySelector("#email-otp-submit-btn");
    const errEl = card.querySelector("#email-otp-error");

    submitBtn.disabled = true;
    submitBtn.textContent = t("verifying");
    errEl.style.display = "none";

    try {
      const res = await this.authClient.verifyFlowOtp({
        flowId: this.flowId,
        code,
        purpose: "SIGNUP_EMAIL"
      });

      this.flowToken = res.flowToken;
      this._clearIntervals();
      this.step = 5;
      this._renderCurrentStep();
    } catch (err) {
      submitBtn.disabled = false;
      submitBtn.textContent = t("verify_code_button");
      errEl.textContent = err.message || t("err_invalid_code");
      errEl.style.display = "block";
    }
  }

  // --- Step 5: Profile Onboarding & Final Complete ---
  _renderStepProfile(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("profile_title")}</h1>
        <p>${t("profile_subtitle")}</p>
      </div>

      <form id="signup-profile-form">
        <div class="form-group">
          <label class="form-label" for="display-name-input">${t("display_name_label")}</label>
          <input type="text" id="display-name-input" class="input-field" placeholder="${t("display_name_placeholder")}" required maxlength="100" autocomplete="name">
          <p id="profile-error" class="form-error" style="display: none;"></p>
        </div>

        <button type="submit" id="profile-submit-btn" class="btn btn-primary" style="margin-top: 1rem;">
          ${t("complete_signup_button")}
        </button>
      </form>
    `;

    const form = card.querySelector("#signup-profile-form");
    const nameInput = card.querySelector("#display-name-input");
    const submitBtn = card.querySelector("#profile-submit-btn");
    const errEl = card.querySelector("#profile-error");

    nameInput.focus();

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const displayName = nameInput.value.trim();
      if (displayName.length < 2) {
        errEl.textContent = "Please enter your name.";
        errEl.style.display = "block";
        return;
      }

      errEl.style.display = "none";
      submitBtn.disabled = true;
      submitBtn.textContent = "Setting up your ledger...";

      try {
        const res = await this.authClient.completeSignup({
          flowId: this.flowId,
          flowToken: this.flowToken,
          phoneNumber: this.phoneNumber,
          email: this.email,
          displayName,
          isAge18Plus: true,
          allowWhatsAppReminders: true
        });

        this.onToast(`${t("welcome_user")}, ${res.user.displayName}!`);
        this.router.navigate("#dashboard");
      } catch (err) {
        submitBtn.disabled = false;
        submitBtn.textContent = t("complete_signup_button");
        errEl.textContent = err.message || "Failed to create account. Please try again.";
        errEl.style.display = "block";
      }
    });
  }

  // --- Helper: Auto-Jump OTP input boxes ---
  _setupOtpDigitAutoJump(card, onComplete) {
    const inputs = card.querySelectorAll(".otp-digit");
    if (inputs.length === 0) return;

    inputs[0].focus();

    inputs.forEach((input, index) => {
      input.addEventListener("input", (e) => {
        const val = e.target.value.replace(/[^0-9]/g, "");
        e.target.value = val ? val[val.length - 1] : "";

        if (e.target.value && index < inputs.length - 1) {
          inputs[index + 1].focus();
        }

        const fullCode = Array.from(inputs).map(i => i.value).join("");
        if (fullCode.length === 6) {
          onComplete(fullCode);
        }
      });

      input.addEventListener("keydown", (e) => {
        if (e.key === "Backspace" && !e.target.value && index > 0) {
          inputs[index - 1].focus();
        }
      });

      input.addEventListener("paste", (e) => {
        e.preventDefault();
        const text = (e.clipboardData || window.clipboardData).getData("text").replace(/[^0-9]/g, "");
        if (text) {
          const chars = text.slice(0, 6).split("");
          chars.forEach((char, i) => {
            if (inputs[i]) inputs[i].value = char;
          });
          const lastIdx = Math.min(chars.length, inputs.length) - 1;
          if (inputs[lastIdx]) inputs[lastIdx].focus();
          if (chars.length === 6) {
            onComplete(chars.join(""));
          }
        }
      });
    });
  }

  _startResendCountdown(card, onResend) {
    const countdownEl = card.querySelector("#countdown-val");
    const timerTextEl = card.querySelector("#resend-timer-text");
    const resendBtn = card.querySelector("#resend-otp-btn");

    this._clearIntervals();

    this.resendTimer = setInterval(() => {
      this.resendSecondsLeft--;
      if (countdownEl) countdownEl.textContent = `${this.resendSecondsLeft}s`;

      if (this.resendSecondsLeft <= 0) {
        this._clearIntervals();
        if (timerTextEl) timerTextEl.style.display = "none";
        if (resendBtn) resendBtn.style.display = "inline-block";
      }
    }, 1000);

    if (resendBtn) {
      resendBtn.addEventListener("click", async () => {
        resendBtn.style.display = "none";
        if (timerTextEl) timerTextEl.style.display = "inline-block";
        this.resendSecondsLeft = 30;
        if (countdownEl) countdownEl.textContent = "30s";
        this._startResendCountdown(card, onResend);
        await onResend();
      });
    }
  }

  _clearIntervals() {
    if (this.resendTimer) {
      clearInterval(this.resendTimer);
      this.resendTimer = null;
    }
  }

  destroy() {
    this._clearIntervals();
  }
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { SignUpView };
}

    __modules["SignUpView"] = module;
  })();

  // --- Module: SignInView ---
  (function() {
    const module = { exports: {} };
    const exports = module.exports;
/**
 * Udhaar Web App Sign-In Stepper (webapp/src/features/auth/SignInView.js)
 * Implements WhatsApp OTP sign-in with non-enumerable responses and auto-focus.
 * Strict CSP: zero inline event handlers.
 */

class SignInView {
  constructor({ i18n, authClient, router, onToast }) {
    this.i18n = i18n;
    this.authClient = authClient;
    this.router = router;
    this.onToast = onToast;

    this.step = 1;
    this.flowId = null;
    this.phoneNumber = "";
    this.resendTimer = null;
    this.resendSecondsLeft = 30;
  }

  render(container) {
    this.container = container;
    this._renderCurrentStep();
  }

  _renderCurrentStep() {
    this.container.innerHTML = "";
    const card = document.createElement("div");
    card.className = "card auth-container";

    if (this.step === 1) {
      this._renderStepPhone(card);
    } else if (this.step === 2) {
      this._renderStepOtp(card);
    }

    this.container.appendChild(card);
  }

  // --- Step 1: Phone Number Input ---
  _renderStepPhone(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("signin_title")}</h1>
        <p>${t("signin_subtitle")}</p>
      </div>

      <form id="signin-phone-form">
        <div class="form-group">
          <label class="form-label" for="signin-phone-input">${t("signin_phone_label")}</label>
          <div class="input-wrap">
            <span class="country-prefix">+91</span>
            <input type="tel" id="signin-phone-input" class="input-field has-prefix" placeholder="${t("phone_placeholder")}" maxlength="10" required autocomplete="tel-national">
          </div>
          <p class="input-hint">${t("phone_hint")}</p>
          <p id="signin-phone-error" class="form-error" style="display: none;"></p>
        </div>

        <button type="submit" id="signin-submit-btn" class="btn btn-primary" style="margin-top: 1rem;">
          ${t("send_login_code")}
        </button>

        <div style="text-align: center; margin-top: 1.25rem;">
          <a href="#signup" class="btn-link">${t("dont_have_account")}</a>
        </div>
      </form>
    `;

    const form = card.querySelector("#signin-phone-form");
    const phoneInput = card.querySelector("#signin-phone-input");
    const submitBtn = card.querySelector("#signin-submit-btn");
    const errEl = card.querySelector("#signin-phone-error");

    phoneInput.focus();

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const rawDigits = phoneInput.value.replace(/[^0-9]/g, "");
      if (rawDigits.length !== 10) {
        errEl.textContent = t("err_invalid_phone");
        errEl.style.display = "block";
        return;
      }

      errEl.style.display = "none";
      submitBtn.disabled = true;
      submitBtn.textContent = t("sending_code");

      this.phoneNumber = `+91 ${rawDigits.slice(0, 5)} ${rawDigits.slice(5)}`;

      try {
        const res = await this.authClient.startSignin({
          phoneNumber: this.phoneNumber,
          locale: this.i18n.getLocale()
        });

        this.flowId = res.flowId;
        this.resendSecondsLeft = res.resendAfterSeconds || 30;
        this.step = 2;
        this._renderCurrentStep();
      } catch (err) {
        submitBtn.disabled = false;
        submitBtn.textContent = t("send_login_code");
        errEl.textContent = err.message || t("err_rate_limit");
        errEl.style.display = "block";
      }
    });
  }

  // --- Step 2: WhatsApp OTP Input ---
  _renderStepOtp(card) {
    const t = this.i18n.t;

    card.innerHTML = `
      <div class="auth-header">
        <h1>${t("login_otp_title")}</h1>
        <p>${t("login_otp_subtitle")}: <strong>${this.phoneNumber}</strong></p>
      </div>

      <form id="signin-otp-form">
        <label class="form-label" style="text-align: center;">${t("otp_input_label")}</label>
        <div class="otp-container">
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="0" required autocomplete="one-time-code">
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="1" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="2" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="3" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="4" required>
          <input type="text" inputmode="numeric" maxlength="1" class="otp-digit" data-idx="5" required>
        </div>
        <p id="signin-otp-error" class="form-error" style="text-align: center; display: none;"></p>

        <div style="text-align: center; margin: 1rem 0;">
          <span id="resend-timer-text" style="font-size: 0.875rem; color: var(--color-text-muted);">
            ${t("resend_code_in")} <strong id="countdown-val">${this.resendSecondsLeft}s</strong>
          </span>
          <button type="button" id="resend-otp-btn" class="btn-link" style="display: none; margin: 0 auto;">
            ${t("resend_code_now")}
          </button>
        </div>

        <button type="submit" id="signin-otp-submit-btn" class="btn btn-primary">
          ${t("verify_code_button")}
        </button>

        <div style="text-align: center; margin-top: 1rem;">
          <button type="button" id="change-phone-btn" class="btn-link">
            ${t("change_number")}
          </button>
        </div>
      </form>
    `;

    this._setupOtpDigitAutoJump(card, async (code) => {
      await this._verifyOtp(card, code);
    });

    this._startResendCountdown(card, async () => {
      try {
        const res = await this.authClient.startSignin({
          phoneNumber: this.phoneNumber,
          locale: this.i18n.getLocale()
        });
        this.flowId = res.flowId;
        this.onToast("Login code resent on WhatsApp");
      } catch (err) {
        card.querySelector("#signin-otp-error").textContent = err.message;
        card.querySelector("#signin-otp-error").style.display = "block";
      }
    });

    card.querySelector("#change-phone-btn").addEventListener("click", () => {
      this._clearIntervals();
      this.step = 1;
      this._renderCurrentStep();
    });

    card.querySelector("#signin-otp-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const digits = Array.from(card.querySelectorAll(".otp-digit")).map(d => d.value).join("");
      if (digits.length === 6) {
        await this._verifyOtp(card, digits);
      }
    });
  }

  async _verifyOtp(card, code) {
    const t = this.i18n.t;
    const submitBtn = card.querySelector("#signin-otp-submit-btn");
    const errEl = card.querySelector("#signin-otp-error");

    submitBtn.disabled = true;
    submitBtn.textContent = t("verifying");
    errEl.style.display = "none";

    try {
      const res = await this.authClient.verifySignin({
        flowId: this.flowId,
        code
      });

      this._clearIntervals();
      this.onToast(`Welcome back, ${res.user.displayName}!`);
      this.router.navigate("#dashboard");
    } catch (err) {
      submitBtn.disabled = false;
      submitBtn.textContent = t("verify_code_button");
      errEl.textContent = err.message || t("err_invalid_code");
      errEl.style.display = "block";
    }
  }

  _setupOtpDigitAutoJump(card, onComplete) {
    const inputs = card.querySelectorAll(".otp-digit");
    if (inputs.length === 0) return;

    inputs[0].focus();

    inputs.forEach((input, index) => {
      input.addEventListener("input", (e) => {
        const val = e.target.value.replace(/[^0-9]/g, "");
        e.target.value = val ? val[val.length - 1] : "";

        if (e.target.value && index < inputs.length - 1) {
          inputs[index + 1].focus();
        }

        const fullCode = Array.from(inputs).map(i => i.value).join("");
        if (fullCode.length === 6) {
          onComplete(fullCode);
        }
      });

      input.addEventListener("keydown", (e) => {
        if (e.key === "Backspace" && !e.target.value && index > 0) {
          inputs[index - 1].focus();
        }
      });

      input.addEventListener("paste", (e) => {
        e.preventDefault();
        const text = (e.clipboardData || window.clipboardData).getData("text").replace(/[^0-9]/g, "");
        if (text) {
          const chars = text.slice(0, 6).split("");
          chars.forEach((char, i) => {
            if (inputs[i]) inputs[i].value = char;
          });
          const lastIdx = Math.min(chars.length, inputs.length) - 1;
          if (inputs[lastIdx]) inputs[lastIdx].focus();
          if (chars.length === 6) {
            onComplete(chars.join(""));
          }
        }
      });
    });
  }

  _startResendCountdown(card, onResend) {
    const countdownEl = card.querySelector("#countdown-val");
    const timerTextEl = card.querySelector("#resend-timer-text");
    const resendBtn = card.querySelector("#resend-otp-btn");

    this._clearIntervals();

    this.resendTimer = setInterval(() => {
      this.resendSecondsLeft--;
      if (countdownEl) countdownEl.textContent = `${this.resendSecondsLeft}s`;

      if (this.resendSecondsLeft <= 0) {
        this._clearIntervals();
        if (timerTextEl) timerTextEl.style.display = "none";
        if (resendBtn) resendBtn.style.display = "inline-block";
      }
    }, 1000);

    if (resendBtn) {
      resendBtn.addEventListener("click", async () => {
        resendBtn.style.display = "none";
        if (timerTextEl) timerTextEl.style.display = "inline-block";
        this.resendSecondsLeft = 30;
        if (countdownEl) countdownEl.textContent = "30s";
        this._startResendCountdown(card, onResend);
        await onResend();
      });
    }
  }

  _clearIntervals() {
    if (this.resendTimer) {
      clearInterval(this.resendTimer);
      this.resendTimer = null;
    }
  }

  destroy() {
    this._clearIntervals();
  }
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { SignInView };
}

    __modules["SignInView"] = module;
  })();

  // --- Module: DashboardView ---
  (function() {
    const module = { exports: {} };
    const exports = module.exports;
/**
 * Udhaar Web App Dashboard View (webapp/src/features/records/DashboardView.js)
 * Implements Overview KPI cards, empty state, and ledger layout shell.
 */

class DashboardView {
  constructor({ i18n, authClient, router, onToast }) {
    this.i18n = i18n;
    this.authClient = authClient;
    this.router = router;
    this.onToast = onToast;
    this.user = null;
  }

  async render(container) {
    this.container = container;
    const t = this.i18n.t;

    this.container.innerHTML = `
      <section aria-label="Account Overview" class="dashboard-grid">
        <div class="kpi-card">
          <div class="kpi-title">${t("net_balance")}</div>
          <div class="kpi-amount" style="color: var(--color-primary);">₹0</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-title">${t("you_will_get")}</div>
          <div class="kpi-amount lent">₹0</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-title">${t("you_will_give")}</div>
          <div class="kpi-amount borrowed">₹0</div>
        </div>
      </section>

      <section class="card" style="text-align: center; padding: 3rem 1.5rem; margin-top: 1rem;">
        <div style="font-size: 3rem; margin-bottom: 0.75rem;">📖</div>
        <h2 style="font-family: var(--font-display); font-size: 1.25rem; font-weight: 700; margin-bottom: 0.5rem;">
          ${t("no_records_title")}
        </h2>
        <p style="font-size: 0.95rem; color: var(--color-text-muted); max-width: 420px; margin: 0 auto 1.5rem auto;">
          ${t("no_records_desc")}
        </p>
        <button type="button" id="add-record-btn" class="btn btn-primary" style="max-width: 240px; margin: 0 auto;">
          ${t("add_first_record")}
        </button>
      </section>
    `;

    const addBtn = this.container.querySelector("#add-record-btn");
    if (addBtn) {
      addBtn.addEventListener("click", () => {
        this.onToast("Create record feature is ready in Stage S3!");
      });
    }
  }

  destroy() {}
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { DashboardView };
}

    __modules["DashboardView"] = module;
  })();

  // --- Module: SettingsView ---
  (function() {
    const module = { exports: {} };
    const exports = module.exports;
/**
 * Udhaar Web App Settings View (webapp/src/features/settings/SettingsView.js)
 * Implements Profile, Language selection, Theme toggle, Signed-in devices, and DPDP privacy tools.
 */

class SettingsView {
  constructor({ i18n, authClient, router, onToast, onLocaleChange }) {
    this.i18n = i18n;
    this.authClient = authClient;
    this.router = router;
    this.onToast = onToast;
    this.onLocaleChange = onLocaleChange;
  }

  async render(container) {
    this.container = container;
    const t = this.i18n.t;
    const currentLoc = this.i18n.getLocale();

    let user = null;
    let devices = [];
    try {
      const me = await this.authClient.getMe();
      user = me.user;
      devices = me.devices || [];
    } catch (_) {
      // Offline or mock
    }

    this.container.innerHTML = `
      <div style="max-width: 600px; margin: 0 auto; display: flex; flex-direction: column; gap: 1.5rem;">
        <div class="card">
          <h2 style="font-family: var(--font-display); font-size: 1.25rem; font-weight: 700; margin-bottom: 1rem;">
            ${t("profile_heading")}
          </h2>
          <div style="display: flex; flex-direction: column; gap: 0.5rem; font-size: 0.95rem;">
            <div><strong>${t("display_name_label")}:</strong> ${user?.displayName || "User"}</div>
            <div><strong>${t("phone_label")}:</strong> ${user?.maskedPhone || "+91 ••••• ••••"}</div>
            <div><strong>${t("email_label")}:</strong> ${user?.maskedEmail || "•••••@example.com"}</div>
          </div>
        </div>

        <div class="card">
          <h2 style="font-family: var(--font-display); font-size: 1.25rem; font-weight: 700; margin-bottom: 1rem;">
            ${t("language_switcher")}
          </h2>
          <div class="form-group">
            <label for="settings-lang-select" class="form-label">${t("language_switcher")}</label>
            <select id="settings-lang-select" class="lang-select" aria-label="${t("language_switcher")}" style="width: 100%;">
              <option value="en" ${currentLoc === "en" ? "selected" : ""}>English</option>
              <option value="hi" ${currentLoc === "hi" ? "selected" : ""}>हिन्दी (Hindi)</option>
              <option value="bn" ${currentLoc === "bn" ? "selected" : ""}>বাংলা (Bengali)</option>
              <option value="as" ${currentLoc === "as" ? "selected" : ""}>অসমীয়া (Assamese)</option>
              <option value="ne" ${currentLoc === "ne" ? "selected" : ""}>नेपाली (Nepali)</option>
            </select>
          </div>
        </div>

        <div class="card">
          <h2 style="font-family: var(--font-display); font-size: 1.25rem; font-weight: 700; margin-bottom: 1rem;">
            ${t("privacy_notice_heading")}
          </h2>
          <p style="font-size: 0.875rem; color: var(--color-text-muted); line-height: 1.5; margin-bottom: 1rem;">
            ${t("dpdp_summary")}
          </p>
          <a href="/privacy.html" target="_blank" class="btn btn-secondary" style="display: inline-flex; width: auto;">
            ${t("privacy_link")}
          </a>
        </div>

        <div style="text-align: center; margin-top: 1rem;">
          <button type="button" id="signout-btn" class="btn btn-secondary" style="color: var(--color-danger); border-color: var(--color-danger);">
            ${t("sign_out")}
          </button>
        </div>
      </div>
    `;

    const langSelect = this.container.querySelector("#settings-lang-select");
    if (langSelect) {
      langSelect.addEventListener("change", (e) => {
        this.onLocaleChange(e.target.value);
        this.render(container);
      });
    }

    const signOutBtn = this.container.querySelector("#signout-btn");
    if (signOutBtn) {
      signOutBtn.addEventListener("click", async () => {
        try {
          await this.authClient.signOut();
        } catch (_) {}
        this.onToast("Signed out successfully");
        this.router.navigate("#signin");
      });
    }
  }

  destroy() {}
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { SettingsView };
}

    __modules["SettingsView"] = module;
  })();

  // --- Module: AppShell ---
  (function() {
    const module = { exports: {} };
    const exports = module.exports;
/**
 * Udhaar Web App Shell Controller (webapp/src/app/AppShell.js)
 * Coordinates layout, internationalization, routing, theme, PWA service worker,
 * and toast announcements.
 */

const { initLocale, setLocale, getLocale, t } = __require("i18n");
const { authClient } = __require("authClient");
const { Router } = __require("router");
const { SignUpView } = __require("SignUpView");
const { SignInView } = __require("SignInView");
const { DashboardView } = __require("DashboardView");
const { SettingsView } = __require("SettingsView");

class AppShell {
  constructor(rootEl) {
    this.root = rootEl;
    this.router = new Router();
    this.i18n = { initLocale, setLocale, getLocale, t };
    this.authClient = authClient;
  }

  init() {
    this.i18n.initLocale();
    this._initTheme();
    this._renderShell();
    this._setupRoutes();
    this._setupNetworkListeners();
    this._registerServiceWorker();
    this.router.start();
  }

  _initTheme() {
    if (typeof window !== "undefined") {
      const savedTheme = localStorage.getItem("udhaar_theme") || "light";
      document.documentElement.setAttribute("data-theme", savedTheme);
    }
  }

  _toggleTheme() {
    const current = document.documentElement.getAttribute("data-theme") || "light";
    const next = current === "light" ? "dark" : "light";
    document.documentElement.setAttribute("data-theme", next);
    if (typeof window !== "undefined") {
      localStorage.setItem("udhaar_theme", next);
    }
    const themeBtn = this.root.querySelector("#theme-btn");
    if (themeBtn) {
      themeBtn.textContent = next === "dark" ? "☀️" : "🌙";
    }
  }

  _renderShell() {
    const currentLoc = this.i18n.getLocale();
    const currentTheme = (typeof document !== "undefined" && document.documentElement.getAttribute("data-theme")) || "light";

    this.root.innerHTML = `
      <header class="app-header">
        <a href="#dashboard" class="brand-wrap" aria-label="Udhaar Home">
          <svg class="brand-logo" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
            <rect width="48" height="48" rx="12" fill="#059669"/>
            <path d="M14 24H34M24 14V34" stroke="white" stroke-width="4.5" stroke-linecap="round"/>
          </svg>
          <span class="brand-name">${this.i18n.t("app_name")}</span>
        </a>

        <div class="header-actions">
          <select id="shell-lang-select" class="lang-select" aria-label="${this.i18n.t("language_switcher")}">
            <option value="en" ${currentLoc === "en" ? "selected" : ""}>EN</option>
            <option value="hi" ${currentLoc === "hi" ? "selected" : ""}>हिन्दी</option>
            <option value="bn" ${currentLoc === "bn" ? "selected" : ""}>বাংলা</option>
            <option value="as" ${currentLoc === "as" ? "selected" : ""}>অসমীয়া</option>
            <option value="ne" ${currentLoc === "ne" ? "selected" : ""}>नेपाली</option>
          </select>

          <button type="button" id="theme-btn" class="theme-btn" aria-label="Toggle Theme">
            ${currentTheme === "dark" ? "☀️" : "🌙"}
          </button>
        </div>
      </header>

      <div id="offline-banner" class="offline-banner" role="status" aria-live="polite">
        <span aria-hidden="true">⚠️</span>
        <span>${this.i18n.t("offline_notice")}</span>
      </div>

      <main class="app-main">
        <div id="view-container"></div>
      </main>

      <nav id="bottom-nav" class="bottom-nav" aria-label="Main Navigation" style="display: none;">
        <a href="#dashboard" class="nav-item" data-route="#dashboard">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>
          <span>${this.i18n.t("nav_records")}</span>
        </a>
        <a href="#people" class="nav-item" data-route="#people">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
          <span>${this.i18n.t("nav_people")}</span>
        </a>
        <a href="#reminders" class="nav-item" data-route="#reminders">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
          <span>${this.i18n.t("nav_reminders")}</span>
        </a>
        <a href="#settings" class="nav-item" data-route="#settings">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
          <span>${this.i18n.t("nav_settings")}</span>
        </a>
      </nav>

      <div id="toast-container" class="toast-container" role="status" aria-live="polite"></div>
    `;

    // Listeners
    const langSelect = this.root.querySelector("#shell-lang-select");
    langSelect.addEventListener("change", (e) => {
      this.i18n.setLocale(e.target.value);
      this._renderShell();
      this.router.start();
    });

    const themeBtn = this.root.querySelector("#theme-btn");
    themeBtn.addEventListener("click", () => {
      this._toggleTheme();
    });
  }

  _setupRoutes() {
    const viewContainer = this.root.querySelector("#view-container");
    const bottomNav = this.root.querySelector("#bottom-nav");

    const onToast = (msg) => this.showToast(msg);
    const onLocaleChange = (loc) => {
      this.i18n.setLocale(loc);
      this._renderShell();
      this.router.start();
    };

    this.router.addRoute("#signup", () => {
      if (bottomNav) bottomNav.style.display = "none";
      const view = new SignUpView({
        i18n: this.i18n,
        authClient: this.authClient,
        router: this.router,
        onToast
      });
      view.render(viewContainer);
      return view;
    });

    this.router.addRoute("#signin", () => {
      if (bottomNav) bottomNav.style.display = "none";
      const view = new SignInView({
        i18n: this.i18n,
        authClient: this.authClient,
        router: this.router,
        onToast
      });
      view.render(viewContainer);
      return view;
    });

    this.router.addRoute("#dashboard", () => {
      if (bottomNav) bottomNav.style.display = "flex";
      this._updateNavHighlight("#dashboard");
      const view = new DashboardView({
        i18n: this.i18n,
        authClient: this.authClient,
        router: this.router,
        onToast
      });
      view.render(viewContainer);
      return view;
    });

    this.router.addRoute("#settings", () => {
      if (bottomNav) bottomNav.style.display = "flex";
      this._updateNavHighlight("#settings");
      const view = new SettingsView({
        i18n: this.i18n,
        authClient: this.authClient,
        router: this.router,
        onToast,
        onLocaleChange
      });
      view.render(viewContainer);
      return view;
    });

    this.router.onRouteChanged = (route) => {
      this._updateNavHighlight(route);
    };
  }

  _updateNavHighlight(route) {
    const items = this.root.querySelectorAll(".nav-item");
    items.forEach((item) => {
      if (item.getAttribute("data-route") === route) {
        item.classList.add("active");
        item.setAttribute("aria-current", "page");
      } else {
        item.classList.remove("active");
        item.removeAttribute("aria-current");
      }
    });
  }

  _setupNetworkListeners() {
    const banner = this.root.querySelector("#offline-banner");
    if (!banner) return;

    const updateStatus = () => {
      const isOnline = typeof navigator !== "undefined" ? navigator.onLine : true;
      if (!isOnline) {
        banner.classList.add("active");
      } else {
        banner.classList.remove("active");
      }
    };

    if (typeof window !== "undefined") {
      window.addEventListener("online", updateStatus);
      window.addEventListener("offline", updateStatus);
      updateStatus();
    }
  }

  _registerServiceWorker() {
    if (typeof window !== "undefined" && "serviceWorker" in navigator && location.protocol === "https:") {
      navigator.serviceWorker.register("/sw.js").catch((err) => {
        console.warn("[PWA] Service Worker registration failed:", err);
      });
    }
  }

  showToast(message, durationMs = 4000) {
    const container = this.root.querySelector("#toast-container");
    if (!container) return;

    const toast = document.createElement("div");
    toast.className = "toast";
    toast.textContent = message;

    container.appendChild(toast);

    setTimeout(() => {
      if (toast.parentNode) {
        toast.parentNode.removeChild(toast);
      }
    }, durationMs);
  }
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { AppShell };
}

    __modules["AppShell"] = module;
  })();


  // Bootstrapping AppShell
  document.addEventListener("DOMContentLoaded", function() {
    const root = document.getElementById("root");
    if (root) {
      const { AppShell } = __require("AppShell");
      const app = new AppShell(root);
      app.init();
      window.__udhaarApp = app;
    }
  });
})();
