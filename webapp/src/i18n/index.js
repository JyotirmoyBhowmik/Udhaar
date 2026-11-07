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
    public_computer_label: "This is a public or shared computer",
    idle_lock_title: "Udhaar is Locked",
    idle_lock_subtitle: "Locked due to inactivity to protect your private records",
    unlock_button: "Unlock",
    conflict_title: "Conflict Detected",
    conflict_keep_mine: "Keep My Version",
    conflict_accept_server: "Accept Web Version",

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
    public_computer_label: "यह एक सार्वजनिक या साझा कंप्यूटर है",
    idle_lock_title: "उधार लॉक है",
    idle_lock_subtitle: "आपकी निजी जानकारी की सुरक्षा के लिए निष्क्रियता के कारण लॉक किया गया",
    unlock_button: "अनलॉक करें",
    conflict_title: "रिकॉर्ड में मतभेद पाया गया",
    conflict_keep_mine: "मेरा संस्करण रखें",
    conflict_accept_server: "वेब संस्करण स्वीकार करें",

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
    public_computer_label: "এটি একটি সার্বজনীন বা ভাগ করা কম্পিউটার",
    idle_lock_title: "উধার লক করা হয়েছে",
    idle_lock_subtitle: "আপনার ব্যক্তিগত হিসাবের সুরক্ষার জন্য নিষ্ক্রিয়তার কারণে লক করা হয়েছে",
    unlock_button: "আনলক করুন",
    conflict_title: "রেকর্ডে অমিল পাওয়া গেছে",
    conflict_keep_mine: "আমার সংস্করণ রাখুন",
    conflict_accept_server: "ওয়েব সংস্করণ গ্রহণ করুন",

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
    public_computer_label: "এইটো এটা ৰাজহুৱা বা আনৰ লগত ব্যৱহাৰ কৰা কম্পিউটাৰ",
    idle_lock_title: "উধাৰ লক কৰা হৈছে",
    idle_lock_subtitle: "আপোনাৰ ব্যক্তিগত তথ্যৰ সুৰক্ষাৰ বাবে নিষ্ক্ৰিয়তাৰ বাবে লক কৰা হৈছে",
    unlock_button: "আনলক কৰক",
    conflict_title: "ৰেৰ্কডত অমিল পোৱা গৈছে",
    conflict_keep_mine: "মোৰ সংস্কৰণ ৰাখক",
    conflict_accept_server: "ৱেব সংস্কৰণ গ্ৰহণ কৰক",

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
    public_computer_label: "यो सार्वजनिक वा साझा कम्प्युटर हो",
    idle_lock_title: "उधार लक गरिएको छ",
    idle_lock_subtitle: "तपाईंको व्यक्तिगत विवरणको सुरक्षाका लागि निष्क्रियताका कारण लक गरिएको छ",
    unlock_button: "अनलक गर्नुहोस्",
    conflict_title: "रेकर्डमा भिन्नता भेटियो",
    conflict_keep_mine: "मेरो संस्करण राख्नुहोस्",
    conflict_accept_server: "वेब संस्करण स्वीकार गर्नुहोस्",

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
