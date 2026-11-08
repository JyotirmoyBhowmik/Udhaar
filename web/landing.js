/**
 * Udhaar Landing Page JavaScript
 * Full 5-Language Dictionary, Dynamic Site Config (Early Access Switch),
 * Interactive Demo Simulation, Integer-Paise Repayment Calculator,
 * Animated Stepper, FAQ Search & Category Filter, Mobile Drawer & Sticky Bar.
 */

(function () {
  "use strict";

  const I18N = {
    en: {
      meta_title: "Udhaar — Keep clear records of money you lend and borrow",
      meta_desc: "No more forgetting. No more arguments. Private offline lending and borrowing records with zero-cost WhatsApp confirmation.",
      brand_name: "UDHAAR",
      nav_how_it_works: "How it works",
      nav_calculator: "Calculator",
      nav_demo: "Try Demo",
      nav_benefits: "Benefits",
      nav_trust: "Trust Center",
      nav_ledger: "Web Ledger",
      nav_updates: "What's New",
      nav_faq: "FAQ",
      nav_privacy: "Privacy Policy",
      btn_get_play: "Get on Google Play",
      btn_early_access: "Join early access",
      btn_try_demo: "See how the confirmation page looks",
      hero_pill: "Free • Private • Works Offline",
      hero_title: "Keep clear records of money you lend and borrow",
      hero_promise: "No more forgetting. No more arguments.",
      hero_desc: "Record personal lending and borrowing in seconds. Send a private link on WhatsApp so both people agree on the amount and date. Works 100% offline and stays locked on your phone.",
      
      // Floating Cards
      float_ravi_title: "Ravi confirmed",
      float_ravi_val: "₹5,000",
      float_wa_title: "Reminder sent",
      float_wa_sub: "on WhatsApp",

      // Interactive Demo
      demo_widget_badge: "Interactive Preview",
      demo_widget_title: "Try creating a record right here",
      demo_widget_subtitle: "See how fast and clear it is to record an entry and share a WhatsApp confirmation link. No installation or account required.",
      demo_tab1: "Amount",
      demo_tab2: "Person",
      demo_tab3: "Installments",
      demo_tab4: "Confirmation",
      demo_step1_title: "Did you give or take money?",
      demo_gave: "You gave (Lent)",
      demo_took: "You took (Borrowed)",
      demo_amount_label: "Enter Amount (₹)",
      demo_next_person: "Next: Person Name",
      demo_step2_title: "Who is this record with?",
      demo_name_label: "Person's Name",
      demo_note_label: "Optional Note",
      demo_next_installments: "Next: Installments",
      demo_back: "Back",
      demo_step3_title: "How will it be repaid?",
      demo_installments_label: "Number of Installments",
      demo_next_preview: "Next: Preview Confirmation",
      demo_step4_title: "WhatsApp confirmation ready to send",
      demo_step4_desc: "Udhaar creates a polite WhatsApp message with a link. Both of you agree on the amount and date with zero confusion.",
      demo_restart: "Restart Demo",
      demo_copy_wa: "Copy Message",
      demo_open_guest: "Open Guest Page ↗",

      // 3 Steps
      steps_badge: "Simple 3-Step Flow",
      steps_title: "How it works in 3 steps",
      steps_subtitle: "Zero confusion. Both people stay on the exact same page.",
      step1_num: "1",
      step1_title: "Add the record",
      step1_desc: "Enter the amount, due date, and the other person's name. It takes less than 10 seconds.",
      step2_num: "2",
      step2_title: "Send the link on WhatsApp",
      step2_desc: "Udhaar prepares a polite message with a private confirmation link and 6-digit code for you to send.",
      step3_num: "3",
      step3_title: "The other person confirms",
      step3_desc: "They open the link in any mobile browser, check the details, and confirm. They do not need to install the app.",
      stepper_showing: "Showing:",

      // Repayment Calculator
      calc_badge: "Smart Planning",
      calc_title: "Plan your repayment schedule",
      calc_subtitle: "Calculate installments with exact integer paise precision. The last installment absorbs any remainder so balances match to the paisa.",
      calc_amount_label: "Principal Amount (₹)",
      calc_interest_mode_label: "Interest Mode",
      calc_int_none: "0% No Interest",
      calc_int_simple: "Simple Flat",
      calc_int_compound: "Reducing",
      calc_rate_label: "Annual Interest Rate (%)",
      calc_inst_count_label: "Number of Installments:",
      calc_freq_label: "Frequency",
      calc_freq_monthly: "Monthly",
      calc_freq_weekly: "Weekly",
      calc_kpi_per_inst: "Per Installment",
      calc_kpi_interest: "Total Interest",
      calc_kpi_total: "Total Repayment",
      calc_kpi_duration: "Duration",
      calc_th_num: "#",
      calc_th_date: "Due Date",
      calc_th_amount: "Installment",
      calc_th_balance: "Remaining",
      calc_btn_copy: "Copy Schedule Summary",

      // Benefits
      benefits_badge: "Built for Real Life",
      benefits_title: "Benefits in plain words",
      benefits_subtitle: "Everything you need to keep personal credit friendly and clear.",
      benefit1_title: "Locked on your phone",
      benefit1_desc: "Your records are encrypted and stored only on your own phone. Nobody else can access or view them.",
      benefit2_title: "Works without internet",
      benefit2_desc: "Add records, check balances, and browse transaction history anywhere, even without cellular connectivity.",
      benefit3_title: "Polite reminders",
      benefit3_desc: "Send gentle, friendly payment reminder drafts that protect your personal relationships and friendships.",
      benefit4_title: "Pay easily by UPI",
      benefit4_desc: "Generate custom UPI QR codes with the exact installment amount or pay instantly via GPay, PhonePe, or Paytm.",
      benefit5_title: "Backup to your Google Drive",
      benefit5_desc: "Securely backup your database to your personal Google Drive with a backup password only you know.",
      benefit6_title: "Works in 5 languages",
      benefit6_desc: "Available in English, Hindi, Bengali, Assamese, and Nepali with plain, everyday words.",

      // Trust Section
      trust_badge: "Your Data Belongs to You",
      trust_title: "What we store and what we do not",
      trust_subtitle: "Honest privacy. No trackers, no data sales, no surprises.",
      th_data: "Information",
      th_where: "Where it lives",
      th_status: "Privacy Status",
      row1_data: "Your contact book & phone contacts",
      row1_where: "Never touched or read",
      row1_status: "Zero access",
      row2_data: "Bank accounts & UPI PINs",
      row2_where: "Never requested or collected",
      row2_status: "Zero access",
      row3_data: "All loan records, notes & photos",
      row3_where: "Stored only on your phone",
      row3_status: "Locked & Private",
      row4_data: "Shared confirmation link",
      row4_where: "Temporary link (expires in 15 mins / 7 days)",
      row4_status: "Encrypted in transit",
      row5_data: "Backup files",
      row5_where: "Your personal Google Drive",
      row5_status: "Password encrypted",

      // Honest Notes
      honest_money_title: "Udhaar does not move or hold money",
      honest_money_desc: "We are a record-keeping notebook, not a wallet or bank. All payments are completed through your UPI app or bank.",
      honest_legal_title: "Udhaar is not a legal contract",
      honest_legal_desc: "Records and confirmation links provide mutual clarity between friends and family, but do not replace legal loan contracts.",
      trust_callout_text: "Want to inspect verified proof of our privacy claims?",
      trust_callout_link: "Visit the Udhaar Trust Center →",

      // FAQ
      faq_badge: "Got questions?",
      faq_title: "Frequently asked questions",
      faq_cat_all: "All",
      faq_cat_basics: "Basics",
      faq_cat_privacy: "Privacy",
      faq_cat_reminders: "Reminders",
      faq_cat_backup: "Backup",
      faq_cat_weblink: "Web link",
      faq1_q: "Is Udhaar completely free?",
      faq1_a: "Yes. Udhaar is 100% free with no advertisements, no monthly subscriptions, and no hidden payment fees.",
      faq2_q: "Do I need to give my phone number to use the app?",
      faq2_a: "No. You can download and use the app completely anonymously without registering a phone number or creating an account.",
      faq3_q: "What happens if I lose or change my phone?",
      faq3_a: "If you enabled backups, you can restore all your records onto your new phone from your own Google Drive using your backup password.",
      faq4_q: "Can the other person see all my other records?",
      faq4_a: "No. When you share a confirmation link, the other person can only see that one specific record you chose to share.",
      faq5_q: "How do I delete my data?",
      faq5_a: "You can delete any record in the app anytime. Deleting the app removes all local records. To remove a shared web link, use our instant deletion form.",
      faq6_q: "How do polite WhatsApp reminders work?",
      faq6_a: "Udhaar composes a kind, respectful reminder message in your chosen language. The app opens your WhatsApp with the text already filled in, so you remain in complete control before sending.",
      faq7_q: "Does the other person need an app to open a web confirmation link?",
      faq7_a: "No. The confirmation link opens directly in any standard mobile web browser (Chrome, Safari, Firefox). They do not need to install anything or register an account.",
      faq8_q: "Is my Google Drive backup encrypted?",
      faq8_a: "Yes. Backups are encrypted with a password that only you choose before they leave your phone. Nobody, not even Google, can read your ledger without your backup password.",
      faq_no_match: "No matching questions found.",
      faq_reset_search: "Reset Search",

      // Demo CTA Box
      demo_box_title: "Experience the guest flow without installing",
      demo_box_desc: "See exactly what the other person experiences when they tap your WhatsApp link. Test confirming, installments, and payment proof.",
      btn_launch_demo: "Try Interactive Demo",

      // Mobile bottom bar
      mobile_bar_tag: "100% Free & Offline",

      // Footer
      footer_desc: "Simple, honest, and private records of money you lend and borrow. Designed for everyday users in India and Nepal.",
      footer_product: "Product",
      footer_legal: "Legal",
      footer_rights: "© 2026 Udhaar. All rights reserved. Open-source and privacy-first."
    },

    hi: {
      meta_title: "उधार — पैसे के लेन-देन का साफ और सुरक्षित हिसाब रखें",
      meta_desc: "न भूलने का डर, न पैसों की बहस। पूरी तरह सुरक्षित और ऑफलाइन खाता, बिना किसी खर्च के व्हाट्सएप पुष्टि के साथ।",
      brand_name: "UDHAAR",
      nav_how_it_works: "यह कैसे काम करता है",
      nav_calculator: "कैलकुलेटर",
      nav_demo: "डेमो देखें",
      nav_benefits: "फायदे",
      nav_trust: "ट्रस्ट सेंटर",
      nav_ledger: "वेब बहीखाता",
      nav_updates: "नया क्या है",
      nav_faq: "अक्सर पूछे जाने वाले सवाल",
      nav_privacy: "गोपनीयता नीति",
      btn_get_play: "गूगल प्ले से डाउनलोड करें",
      btn_early_access: "प्रारंभिक पहुंच से जुड़ें",
      btn_try_demo: "देखें पुष्टि पेज कैसा दिखता है",
      hero_pill: "मुफ़्त • सुरक्षित • बिना इंटरनेट काम करे",
      hero_title: "उधार दिए और लिए गए पैसों का साफ हिसाब रखें",
      hero_promise: "न भूलने का डर, न पैसों की बहस।",
      hero_desc: "उधार के लेन-देन को कुछ ही सेकंड में दर्ज करें। व्हाट्सएप पर एक निजी लिंक भेजें ताकि दोनों लोग रकम और तारीख पर सहमत रहें। पूरी तरह आपके फोन पर सुरक्षित।",

      float_ravi_title: "रवि ने पुष्टि की",
      float_ravi_val: "₹5,000",
      float_wa_title: "याद दिलाया गया",
      float_wa_sub: "व्हाट्सएप पर",

      demo_widget_badge: "सीधा चलाकर देखें",
      demo_widget_title: "यहीं ब्राउज़र में एक हिसाब जोड़कर देखें",
      demo_widget_subtitle: "देखें कि लेन-देन दर्ज करना और व्हाट्सएप पुष्टि लिंक भेजना कितना आसान और तेज है।",
      demo_tab1: "रकम",
      demo_tab2: "व्यक्ति",
      demo_tab3: "किस्तें",
      demo_tab4: "पुष्टि",
      demo_step1_title: "क्या आपने पैसे दिए या लिए?",
      demo_gave: "आपने दिए (उधार दिया)",
      demo_took: "आपने लिए (उधार लिया)",
      demo_amount_label: "रकम दर्ज करें (₹)",
      demo_next_person: "आगे: व्यक्ति का नाम",
      demo_step2_title: "यह हिसाब किसके साथ है?",
      demo_name_label: "व्यक्ति का नाम",
      demo_note_label: "वैकल्पिक टिप्पणी",
      demo_next_installments: "आगे: किस्तें",
      demo_back: "पीछे",
      demo_step3_title: "पैसे कैसे वापस होंगे?",
      demo_installments_label: "किस्तों की संख्या",
      demo_next_preview: "आगे: पुष्टि संदेश देखें",
      demo_step4_title: "व्हाट्सएप पुष्टि संदेश तैयार है",
      demo_step4_desc: "उधार एक आदरपूर्ण संदेश और लिंक तैयार करता है ताकि दोनों व्यक्ति एक ही हिसाब पर सहमत रहें।",
      demo_restart: "दोबारा शुरू करें",
      demo_copy_wa: "संदेश कॉपी करें",
      demo_open_guest: "पुष्टि पेज खोलें ↗",

      steps_badge: "आसान 3 कदम",
      steps_title: "यह 3 आसान कदमों में काम करता है",
      steps_subtitle: "कोई गलतफहमी नहीं। दोनों लोगों के पास एक जैसा साफ हिसाब।",
      step1_num: "१",
      step1_title: "हिसाब जोड़ें",
      step1_desc: "रकम, वापसी की तारीख और दूसरे व्यक्ति का नाम दर्ज करें। इसमें 10 सेकंड से भी कम समय लगता है।",
      step2_num: "२",
      step2_title: "व्हाट्सएप पर लिंक भेजें",
      step2_desc: "ऐप एक आदरपूर्ण संदेश तैयार करता है जिसमें पुष्टि लिंक और 6 अंकों का कोड होता है।",
      step3_num: "३",
      step3_title: "दूसरा व्यक्ति पुष्टि करता है",
      step3_desc: "वे बिना कोई ऐप इंस्टॉल किए अपने फोन ब्राउज़र में लिंक खोलकर तुरंत पुष्टि कर सकते हैं।",
      stepper_showing: "दिखाया जा रहा है:",

      calc_badge: "सटीक हिसाब",
      calc_title: "किस्तों का सही हिसाब बनाएं",
      calc_subtitle: "पैसे की बिना किसी गोलमाल के सटीक किस्तें बनाएं। अंतिम किस्त में शेष पैसे अपने आप जुड़ जाते हैं।",
      calc_amount_label: "कुल मूल रकम (₹)",
      calc_interest_mode_label: "ब्याज का तरीका",
      calc_int_none: "0% कोई ब्याज नहीं",
      calc_int_simple: "साधारण ब्याज",
      calc_int_compound: "घटता हुआ चक्रवृद्धि",
      calc_rate_label: "वार्षिक ब्याज दर (%)",
      calc_inst_count_label: "किस्तों की संख्या:",
      calc_freq_label: "किस्त का अंतराल",
      calc_freq_monthly: "मासिक",
      calc_freq_weekly: "साप्ताहिक",
      calc_kpi_per_inst: "प्रत्येक किस्त",
      calc_kpi_interest: "कुल ब्याज",
      calc_kpi_total: "कुल भुगतान",
      calc_kpi_duration: "अवधि",
      calc_th_num: "#",
      calc_th_date: "तारीख",
      calc_th_amount: "किस्त रकम",
      calc_th_balance: "बाकी रकम",
      calc_btn_copy: "किस्त तालिका कॉपी करें",

      benefits_badge: "आपके लिए बनाया गया",
      benefits_title: "आसान शब्दों में फायदे",
      benefits_subtitle: "लेन-देन को आसान और रिश्तों को मधुर बनाए रखने की हर सुविधा।",
      benefit1_title: "आपके फोन में सुरक्षित बंद",
      benefit1_desc: "आपका पूरा हिसाब आपके फोन में सुरक्षित रहता है। इसे कोई और नहीं पढ़ सकता।",
      benefit2_title: "बिना इंटरनेट भी काम करता है",
      benefit2_desc: "कहीं भी, कभी भी बिना इंटरनेट कनेक्शन के नया हिसाब जोड़ें और पुराना खाता देखें।",
      benefit3_title: "आदरपूर्ण याद दिलाने वाले संदेश",
      benefit3_desc: "शालीन और विनम्र याद दिलाने वाले संदेश जो आपके रिश्तों और दोस्ती को बनाए रखते हैं।",
      benefit4_title: "UPI से आसानी से भुगतान",
      benefit4_desc: "QR कोड बनाएं और सीधे GPay, PhonePe या Paytm से एक क्लिक में भुगतान प्राप्त करें।",
      benefit5_title: "अपने गूगल ड्राइव में बैकअप रखें",
      benefit5_desc: "अपने गुप्त पासवर्ड के साथ अपने व्यक्तिगत गूगल ड्राइव पर सुरक्षित बैकअप लें।",
      benefit6_title: "५ भाषाओं में उपलब्ध",
      benefit6_desc: "अंग्रेजी, हिन्दी, बांग्ला, असमिया और नेपाली में सरल और रोज़मर्रा की भाषा में।",

      trust_badge: "आपका डेटा, आपका अधिकार",
      trust_title: "हम क्या संभालते हैं और क्या नहीं",
      trust_subtitle: "सच्ची ईमानदारी। कोई ट्रैकर नहीं, कोई डेटा बिक्री नहीं।",
      th_data: "जानकारी",
      th_where: "कहाँ रहती है",
      th_status: "सुरक्षा स्थिति",
      row1_data: "आपकी संपर्क सूची (फोन बुक)",
      row1_where: "कभी नहीं पढ़ी जाती",
      row1_status: "शून्य पहुँच",
      row2_data: "बैंक खाता और UPI पिन",
      row2_where: "कभी नहीं माँगा जाता",
      row2_status: "शून्य पहुँच",
      row3_data: "लेन-देन का हिसाब, नोट्स और फोटो",
      row3_where: "केवल आपके फोन में सुरक्षित",
      row3_status: "निजी और सुरक्षित",
      row4_data: "साझा किया गया पुष्टि लिंक",
      row4_where: "अस्थायी लिंक (15 मिनट / 7 दिन में समाप्त)",
      row4_status: "रास्ते में सुरक्षित",
      row5_data: "बैकअप फाइल",
      row5_where: "आपका निजी गूगल ड्राइव",
      row5_status: "पासवर्ड से सुरक्षित",

      honest_money_title: "उधार ऐप पैसों का लेन-देन नहीं करता",
      honest_money_desc: "हम केवल हिसाब रखने वाली डिजिटल डायरी हैं, कोई बैंक या वॉलेट नहीं। सारे भुगतान आपके बैंक या UPI से होते हैं।",
      honest_legal_title: "उधार कोई कानूनी अनुबंध नहीं है",
      honest_legal_desc: "यह आपसी स्पष्टता और भरोसे के लिए है, किसी कानूनी समझौते का विकल्प नहीं।",
      trust_callout_text: "हमारी गोपनीयता के तकनीकी प्रमाण देखना चाहते हैं?",
      trust_callout_link: "उधार ट्रस्ट सेंटर देखें →",

      faq_badge: "कोई सवाल है?",
      faq_title: "अक्सर पूछे जाने वाले सवाल",
      faq_cat_all: "सभी",
      faq_cat_basics: "बुनियादी",
      faq_cat_privacy: "गोपनीयता",
      faq_cat_reminders: "याद दिलाना",
      faq_cat_backup: "बैकअप",
      faq_cat_weblink: "वेब लिंक",
      faq1_q: "क्या उधार ऐप पूरी तरह मुफ़्त है?",
      faq1_a: "हाँ, उधार १००% मुफ़्त है। इसमें कोई विज्ञापन नहीं, कोई मासिक शुल्क नहीं और कोई छिपे हुए खर्च नहीं हैं।",
      faq2_q: "क्या ऐप का उपयोग करने के लिए फोन नंबर देना आवश्यक है?",
      faq2_a: "नहीं। आप बिना किसी फोन नंबर या खाते के पूरी तरह अज्ञात रहकर ऐप का उपयोग कर सकते हैं।",
      faq3_q: "यदि मेरा फोन खो जाए या बदल जाए तो क्या होगा?",
      faq3_a: "यदि आपने बैकअप लिया है, तो आप अपने गूगल ड्राइव से अपने बैकअप पासवर्ड का उपयोग करके नए फोन पर पूरा हिसाब वापस ला सकते हैं।",
      faq4_q: "क्या दूसरा व्यक्ति मेरे अन्य सभी लेन-देन देख सकता है?",
      faq4_a: "बिल्कुल नहीं। पुष्टि लिंक के जरिए दूसरा व्यक्ति केवल वही एक लेन-देन देख सकता है जो आपने उसके साथ साझा किया है।",
      faq5_q: "मैं अपना डेटा कैसे हटा सकता हूँ?",
      faq5_a: "आप ऐप में किसी भी समय कोई भी रिकॉर्ड हटा सकते हैं। वेब लिंक हटाने के लिए हमारे तत्काल डिलीट फॉर्म का उपयोग करें।",
      faq6_q: "व्हाट्सएप पर याद दिलाने वाले संदेश कैसे काम करते हैं?",
      faq6_a: "उधार आपकी भाषा में एक विनम्र संदेश तैयार करता है और सीधे व्हाट्सएप खोलता है ताकि आप भेजने से पहले पूरा नियंत्रण रख सकें।",
      faq7_q: "क्या दूसरे व्यक्ति को पुष्टि लिंक खोलने के लिए ऐप की जरूरत है?",
      faq7_a: "नहीं। लिंक किसी भी मोबाइल ब्राउज़र में तुरंत खुलता है। उन्हें कोई ऐप इंस्टॉल करने की आवश्यकता नहीं है।",
      faq8_q: "क्या मेरा गूगल ड्राइव बैकअप सुरक्षित है?",
      faq8_a: "हाँ। बैकअप आपके अपने पासवर्ड से फोन के अंदर ही सुरक्षित किया जाता है। पासवर्ड के बिना कोई भी इसे नहीं खोल सकता।",
      faq_no_match: "कोई प्रश्न नहीं मिला।",
      faq_reset_search: "खोज रीसेट करें",

      demo_box_title: "बिना इंस्टॉल किए खुद अनुभव करें",
      demo_box_desc: "देखें कि जब आप लिंक भेजते हैं तो दूसरे व्यक्ति को कैसा दिखता है। पुष्टि और किस्तों की जांच करें।",
      btn_launch_demo: "डेमो चलाकर देखें",

      mobile_bar_tag: "१००% मुफ़्त और ऑफलाइन",

      footer_desc: "लेन-देन का सरल, सच्चा और निजी हिसाब। भारत और नेपाल के आम लोगों के लिए खास तौर पर बनाया गया।",
      footer_product: "उत्पाद",
      footer_legal: "कानूनी",
      footer_rights: "© २०२६ उधार। सर्वाधिकार सुरक्षित। खुला-स्रोत और गोपनीयता-केन्द्रित।"
    },

    bn: {
      meta_title: "ধার — দেওয়া ও নেওয়া টাকার পরিষ্কার ও গোপন হিসাব রাখুন",
      meta_desc: "ভুলে যাওয়ার ভয় নেই, টাকার ঝামেলা নেই। সম্পূর্ণ অফলাইন খাতা ও নিখরচায় হোয়াটসঅ্যাপ নিশ্চিতকরণ।",
      brand_name: "UDHAAR",
      nav_how_it_works: "কীভাবে কাজ করে",
      nav_calculator: "ক্যালকুলেটর",
      nav_demo: "ডেমো দেখুন",
      nav_benefits: "সুবিধা",
      nav_trust: "ট্রাস্ট সেন্টার",
      nav_ledger: "ওয়েব খাতা",
      nav_updates: "নতুন কী",
      nav_faq: "প্রশ্নোত্তর",
      nav_privacy: "গোপনীয়তা নীতি",
      btn_get_play: "গুগল প্লে থেকে ডাউনলোড করুন",
      btn_early_access: "আর্লি অ্যাক্সেসে যোগ দিন",
      btn_try_demo: "নিশ্চিতকরণ পেজ কেমন দেখায় দেখুন",
      hero_pill: "বিনামূল্যে • নিরাপদ • ইন্টারনেট ছাড়াই চলবে",
      hero_title: "ধার দেওয়া ও নেওয়া টাকার পরিষ্কার হিসাব রাখুন",
      hero_promise: "ভুলে যাওয়ার ভয় নেই, টাকার ঝামেলা নেই।",
      hero_desc: "কয়েক সেকেন্ডে ধার ও দেনার হিসাব লিখে রাখুন। হোয়াটসঅ্যাপে একটি ব্যক্তিগত লিংক পাঠান যাতে দুজনেই টাকা ও তারিখ নিয়ে একমত থাকতে পারেন। ১০০% অফলাইন ও সম্পূর্ণ আপনার ফোনে নিরাপদ।",

      float_ravi_title: "রবি নিশ্চিত করেছে",
      float_ravi_val: "₹৫,০০০",
      float_wa_title: "স্মারক পাঠানো হয়েছে",
      float_wa_sub: "হোয়াটসঅ্যাপে",

      demo_widget_badge: "সরাসরি চালিয়ে দেখুন",
      demo_widget_title: "এখানেই একটি হিসাব লিখে পরীক্ষা করুন",
      demo_widget_subtitle: "কোনো অ্যাপ ইনস্টল না করেই দেখুন কত দ্রুত হিসাব লেখা ও হোয়াটসঅ্যাপ লিংক তৈরি করা যায়।",
      demo_tab1: "টাকা",
      demo_tab2: "ব্যক্তি",
      demo_tab3: "কিস্তি",
      demo_tab4: "নিশ্চিতকরণ",
      demo_step1_title: "টাকা দিয়েছেন নাকি নিয়েছেন?",
      demo_gave: "দিয়েছেন (ধার দেওয়া)",
      demo_took: "নিয়েছেন (ধার নেওয়া)",
      demo_amount_label: "টাকার পরিমাণ লিখুন (₹)",
      demo_next_person: "পরবর্তী: ব্যক্তির নাম",
      demo_step2_title: "হিসাবটি কার সাথে?",
      demo_name_label: "ব্যক্তির নাম",
      demo_note_label: "ঐচ্ছিক নোট",
      demo_next_installments: "পরবর্তী: কিস্তি",
      demo_back: "পিছনে",
      demo_step3_title: "টাকা কীভাবে শোধ হবে?",
      demo_installments_label: "কিস্তির সংখ্যা",
      demo_next_preview: "পরবর্তী: মেসেজ প্রিভিউ",
      demo_step4_title: "হোয়াটসঅ্যাপ মেসেজ প্রস্তুত",
      demo_step4_desc: "ধার একটি শালীন মেসেজ তৈরি করে যাতে দুজনেই পরিষ্কার হিসাব দেখতে পারেন।",
      demo_restart: "আবার শুরু করুন",
      demo_copy_wa: "মেসেজ কপি করুন",
      demo_open_guest: "নিশ্চিতকরণ পেজ খুলুন ↗",

      steps_badge: "সহজ ৩ ধাপ",
      steps_title: "৩টি সহজ ধাপে কাজ করে",
      steps_subtitle: "কোনো বিভ্রান্তি নেই। উভয়ের কাছেই থাকে একদম পরিষ্কার হিসাব।",
      step1_num: "১",
      step1_title: "হিসাব যোগ করুন",
      step1_desc: "টাকার পরিমাণ, ফেরত দেওয়ার তারিখ ও ব্যক্তির নাম লিখুন। ১০ সেকেন্ডের কম সময় লাগে।",
      step2_num: "২",
      step2_title: "হোয়াটসঅ্যাপে লিংক পাঠান",
      step2_desc: "অ্যাপ একটি বিনীত বার্তা তৈরি করে যাতে নিশ্চিতকরণ লিংক ও ৬ সংখ্যার কোড থাকে।",
      step3_num: "৩",
      step3_title: "অপরজন নিশ্চিত করেন",
      step3_desc: "তারা কোনো অ্যাপ ইনস্টল না করেই মোবাইলের ব্রাউজারে লিংকটি খুলে সাথে সাথে নিশ্চিত করতে পারেন।",
      stepper_showing: "দেখানো হচ্ছে:",

      calc_badge: "সঠিক হিসাব",
      calc_title: "কিস্তির নিখুঁত সময়সূচী তৈরি করুন",
      calc_subtitle: "এক পয়সারও হেরফের ছাড়া নির্ভুল কিস্তি বের করুন। শেষ কিস্তিতে বাকি পয়সা সমানভাবে যুক্ত হয়ে যায়।",
      calc_amount_label: "মূল টাকার পরিমাণ (₹)",
      calc_interest_mode_label: "সুদের ধরন",
      calc_int_none: "০% সুদ নেই",
      calc_int_simple: "সাধারণ ফ্ল্যাট",
      calc_int_compound: "হ্রাসমান চক্রবৃদ্ধি",
      calc_rate_label: "বার্ষিক সুদের হার (%)",
      calc_inst_count_label: "কিস্তির সংখ্যা:",
      calc_freq_label: "কিস্তির ব্যবধান",
      calc_freq_monthly: "মাসিক",
      calc_freq_weekly: "সাপ্তাহিক",
      calc_kpi_per_inst: "প্রতি কিস্তিতে",
      calc_kpi_interest: "মোট সুদ",
      calc_kpi_total: "মোট পরিশোধ",
      calc_kpi_duration: "মেয়াদ",
      calc_th_num: "#",
      calc_th_date: "তারিখ",
      calc_th_amount: "কিস্তি",
      calc_th_balance: "অবশিষ্ট",
      calc_btn_copy: "কিস্তি বিবরণী কপি করুন",

      benefits_badge: "বাস্তব জীবনের জন্য",
      benefits_title: "সহজ ভাষায় সুবিধাগুলো",
      benefits_subtitle: "লেনদেন সহজ এবং সম্পর্ক সুন্দর রাখার সব সুবিধা এখানে উপস্থিত।",
      benefit1_title: "আপনার ফোনে সুরক্ষিতভাবে বন্ধ",
      benefit1_desc: "আপনার সমস্ত হিসাব শুধুমাত্র আপনার ফোনে সংরক্ষিত থাকে। অন্য কেউ তা দেখতে পারে না।",
      benefit2_title: "ইন্টারনেট ছাড়াই কাজ করে",
      benefit2_desc: "যে কোনো জায়গায় ইন্টারনেট ছাড়া নতুন হিসাব যোগ করুন এবং পুরোনো হিসাব দেখুন।",
      benefit3_title: "ভদ্র ও বিনীত তাগাদা বার্তা",
      benefit3_desc: "বিনীত বার্তা যা বন্ধুত্ব ও ব্যক্তিগত সম্পর্কের কোনো ক্ষতি না করেই টাকা চাইতে সাহায্য করে।",
      benefit4_title: "UPI দিয়ে সহজে পেমেন্ট",
      benefit4_desc: "QR কোড তৈরি করে GPay, PhonePe বা Paytm-এর মাধ্যমে সরাসরি টাকা গ্রহণ করুন।",
      benefit5_title: "গুগল ড্রাইভে ব্যাকআপ রাখুন",
      benefit5_desc: "পাসওয়ার্ড দিয়ে সম্পূর্ণ নিরাপদে আপনার নিজস্ব গুগল ড্রাইভে ব্যাকআপ রাখুন।",
      benefit6_title: "৫টি ভাষায় কাজ করে",
      benefit6_desc: "ইংরেজি, হিন্দি, বাংলা, অসমীয়া ও নেপালিতে সহজ দৈনন্দিন ভাষায় উপলব্ধ।",

      trust_badge: "আপনার তথ্য আপনার অধিকার",
      trust_title: "আমরা কী সংরক্ষণ করি আর কী করি না",
      trust_subtitle: "স্বচ্ছ গোপনীয়তা। কোনো ট্র্যাকার নেই, কোনো তথ্য বিক্রি করা হয় না।",
      th_data: "তথ্য",
      th_where: "কোথায় থাকে",
      th_status: "সুরক্ষা অবস্থা",
      row1_data: "ফোন কন্টাক্ট ও অ্যাড্রেস বুক",
      row1_where: "কখনই পড়া বা দেখা হয় না",
      row1_status: "শূন্য অ্যাক্সেস",
      row2_data: "ব্যাংক অ্যাকাউন্ট ও UPI পিন",
      row2_where: "কখনই চাওয়া হয় না",
      row2_status: "শূন্য অ্যাক্সেস",
      row3_data: "সমস্ত হিসাব, নোট ও ছবি",
      row3_where: "শুধুমাত্র আপনার ফোনে সংরক্ষিত",
      row3_status: "লক ও সম্পূর্ণ ব্যক্তিগত",
      row4_data: "শেয়ার করা নিশ্চিতকরণ লিংক",
      row4_where: "অস্থায়ী লিংক (১৫ মিনিট / ৭ দিনে মেয়াদ শেষ)",
      row4_status: "চলাচলের সময় এনক্রিপ্ট করা",
      row5_data: "ব্যাকআপ ফাইল",
      row5_where: "আপনার ব্যক্তিগত গুগল ড্রাইভ",
      row5_status: "পাসওয়ার্ড দিয়ে এনক্রিপ্ট করা",

      honest_money_title: "ধার অ্যাপ টাকা আদান-প্রদান করে না",
      honest_money_desc: "আমরা একটি ডিজিটাল ডায়রি, কোনো ব্যাংক বা ওয়ালেট নই। সমস্ত লেনদেন আপনার নিজস্ব ব্যাংক বা UPI দিয়ে হয়।",
      honest_legal_title: "ধার কোনো আইনি চুক্তি নয়",
      honest_legal_desc: "এটি পরস্পরের মধ্যে বিশ্বাস ও স্বচ্ছতার জন্য, কোনো আইনি দলিলের বিকল্প নয়।",
      trust_callout_text: "আমাদের গোপনীয়তার প্রমাণিত নথি পরীক্ষা করতে চান?",
      trust_callout_link: "ধার ট্রাস্ট সেন্টার দেখুন →",

      faq_badge: "কোনো প্রশ্ন আছে?",
      faq_title: "সাধারণ জিজ্ঞাসা",
      faq_cat_all: "সকল",
      faq_cat_basics: "মৌলিক",
      faq_cat_privacy: "গোপনীয়তা",
      faq_cat_reminders: "স্মারক",
      faq_cat_backup: "ব্যাকআপ",
      faq_cat_weblink: "ওয়েব লিংক",
      faq1_q: "ধার অ্যাপ কি সম্পূর্ণ ফ্রি?",
      faq1_a: "হ্যাঁ, এটি ১০০% ফ্রি। কোনো বিজ্ঞাপন নেই, কোনো মাসিক চার্জ নেই এবং কোনো লুকানো ফি নেই।",
      faq2_q: "অ্যাপটি ব্যবহার করতে কি ফোন নম্বর দিতে হবে?",
      faq2_a: "না। আপনি কোনো ফোন নম্বর বা অ্যাকাউন্ট তৈরি না করেই সম্পূর্ণ গোপনে অ্যাপ ব্যবহার করতে পারেন।",
      faq3_q: "ফোন হারিয়ে গেলে বা পরিবর্তন করলে কী হবে?",
      faq3_a: "আপনি গুগল ড্রাইভে ব্যাকআপ রাখলে নতুন ফোনে পাসওয়ার্ড দিয়ে সব হিসাব সহজেই ফিরিয়ে আনতে পারবেন।",
      faq4_q: "অন্য ব্যক্তি কি আমার সমস্ত হিসাব দেখতে পাবে?",
      faq4_a: "না। আপনি যে নির্দিষ্ট হিসাবের লিংক পাঠাবেন, তারা কেবল সেই একটি হিসাবই দেখতে পাবে।",
      faq5_q: "আমি কীভাবে আমার ডেটা মুছে ফেলব?",
      faq5_a: "অ্যাপ থেকে যেকোনো সময় যেকোনো হিসাব মুছে ফেলা যায়। শেয়ার করা লিংক সরাতে আমাদের ডিলিট ফর্ম ব্যবহার করুন।",
      faq6_q: "হোয়াটসঅ্যাপ তাগাদা মেসেজ কীভাবে কাজ করে?",
      faq6_a: "ধার অ্যাপ একটি ভদ্র বার্তা লিখে সরাসরি আপনার হোয়াটসঅ্যাপ খুলে দেয়, যাতে আপনার সম্পূর্ণ নিয়ন্ত্রণে থাকে।",
      faq7_q: "অন্য জনের কি নিশ্চিত করতে অ্যাপ ডাউনলোড করতে হবে?",
      faq7_a: "না। লিংকটি যেকোনো সাধারণ মোবাইল ব্রাউজারে খোলে। কোনো অ্যাপ ইনস্টল করতে হয় না।",
      faq8_q: "আমার গুগল ড্রাইভ ব্যাকআপ কতটা নিরাপদ?",
      faq8_a: "সম্পূর্ণ নিরাপদ। ব্যাকআপ ফাইলটি আপনার পাসওয়ার্ড দিয়ে এনক্রিপ্ট হয়ে তবেই ড্রাইভে যায়।",
      faq_no_match: "কোনো প্রশ্ন খুঁজে পাওয়া যায়নি।",
      faq_reset_search: "সার্চ রিসেট করুন",

      demo_box_title: "ইনস্টল ছাড়াই অভিজ্ঞতা নিন",
      demo_box_desc: "আপনি লিংক পাঠালে অন্য ব্যক্তি ঠিক কী দেখবে তা এখনই পরীক্ষা করে দেখুন।",
      btn_launch_demo: "ডেমো চালিয়ে দেখুন",

      mobile_bar_tag: "১০০% বিনামূল্যে ও অফলাইন",

      footer_desc: "টাকা দেওয়া-নেওয়ার সহজ, সৎ ও ব্যক্তিগত হিসাব। ভারত ও নেপালের সাধারণ মানুষের জন্য তৈরি।",
      footer_product: "প্রোডাক্ট",
      footer_legal: "আইনি",
      footer_rights: "© ২০২৬ ধার। সর্বস্বত্ব সংরক্ষিত। ওপেন-সোর্স ও গোপনীয়তা-প্রথম।"
    },

    as: {
      meta_title: "ধাৰ — দিয়া আৰু লোৱা ধনৰ স্পষ্ট আৰু ব্যক্তিগত হিচাপ ৰাখক",
      meta_desc: "পাহৰি যোৱাৰ ভয় নাই, পইচাৰ তৰ্ক নাই। সম্পূৰ্ণ অফলাইন আৰু বিনামূলীয়া হোৱাটছএপ নিশ্চিতকৰণ।",
      brand_name: "UDHAAR",
      nav_how_it_works: "কেনেকৈ কাম কৰে",
      nav_calculator: "কেলকুলেটৰ",
      nav_demo: "ডেমো চাওক",
      nav_benefits: "সুবিধা",
      nav_trust: "ট্ৰাষ্ট চেণ্টাৰ",
      nav_ledger: "ৱেব বহীখাতা",
      nav_updates: "নতুন কি আছে",
      nav_faq: "সঘনাই সোধা প্ৰশ্ন",
      nav_privacy: "গোপনীয়তা নীতি",
      btn_get_play: "গুগল প্লেৰ পৰা ডাউনলোড কৰক",
      btn_early_access: "প্ৰাৰম্ভিক প্ৰৱেশত যোগ দিয়ক",
      btn_try_demo: "নিশ্চিতকৰণ পেজ কেনেকুৱা দেখায় চাওক",
      hero_pill: "বিনামূলীয়া • সুৰক্ষিত • ইন্টাৰনেট নোহোৱাকৈ চলে",
      hero_title: "ধাৰ দিয়া আৰু লোৱা ধনৰ স্পষ্ট হিচাপ ৰাখক",
      hero_promise: "পাহৰি যোৱাৰ ভয় নাই, পইচাৰ তৰ্ক নাই।",
      hero_desc: "দিয়া-লোৱা ধনৰ হিচাপ কেইছেকেণ্ডমানতে লিখি ৰাখক। হোৱাটছএপত এটা লিংক পঠিয়াওক যাতে দুয়োজনেই ধন আৰু তাৰিখক লৈ নিশ্চিত থাকে। সম্পূৰ্ণ আপোনাৰ ফোনতে সুৰক্ষিত।",

      float_ravi_title: "ৰবিয়ে নিশ্চিত কৰিলে",
      float_ravi_val: "₹৫,০০০",
      float_wa_title: "স্মাৰক পঠিওৱা হ'ল",
      float_wa_sub: "হোৱাটছএপত",

      demo_widget_badge: "পোনপটীয়াকৈ চলাই চাওক",
      demo_widget_title: "ইয়াত এটা হিচাপ লিখি পৰীক্ষা কৰক",
      demo_widget_subtitle: "কোনো এপ নোহোৱাকৈ চাওক কিমান সহজে হিচাপ ৰাখিব পাৰি আৰু হোৱাটছএপ নিশ্চিতকৰণ লিংক পঠিয়াব পাৰি।",
      demo_tab1: "পৰিমাণ",
      demo_tab2: "ব্যক্তি",
      demo_tab3: "কিস্তি",
      demo_tab4: "নিশ্চিতকৰণ",
      demo_step1_title: "আপুনি পইচা দিলে নে ললে?",
      demo_gave: "দিলে (ধাৰ দিলে)",
      demo_took: "ললে (ধাৰ ললে)",
      demo_amount_label: "টকাৰ পৰিমাণ লিখক (₹)",
      demo_next_person: "পৰৱৰ্তী: ব্যক্তিৰ নাম",
      demo_step2_title: "হিচাপ কাৰ লগত?",
      demo_name_label: "ব্যক্তিৰ নাম",
      demo_note_label: "ঐচ্ছিক টোকা",
      demo_next_installments: "পৰৱৰ্তী: কিস্তি",
      demo_back: "পিছলৈ",
      demo_step3_title: "ধন কেনেকৈ পৰিশোধ হ'ব?",
      demo_installments_label: "কিস্তিৰ সংখ্যা",
      demo_next_preview: "পৰৱৰ্তী: বাৰ্তা চাওক",
      demo_step4_title: "হোৱাটছএপ বাৰ্তা প্ৰস্তুত",
      demo_step4_desc: "ধাৰ এপে এটা ভদ্ৰ বাৰ্তা তৈয়াৰ কৰে যাতে দুয়োৰে মাজত স্পষ্টতা থাকে।",
      demo_restart: "পুনৰ আৰম্ভ কৰক",
      demo_copy_wa: "বাৰ্তা কপি কৰক",
      demo_open_guest: "নিশ্চিতকৰণ পেজ খোলক ↗",

      steps_badge: "সহজ ৩টা পদক্ষেপ",
      steps_title: "৩টা সহজ পদক্ষেপত কাম কৰে",
      steps_subtitle: "কোনো ভুল বুজাবুজি নাই। দুয়োজনেই একেখন স্পষ্ট পেজতে থাকে।",
      step1_num: "১",
      step1_title: "হিচাপ যোগ কৰক",
      step1_desc: "ধনৰ পৰিমাণ, ঘূৰাই দিয়াৰ তাৰিখ আৰু ব্যক্তিৰ নাম দিয়ক। ১০ ছেকেণ্ডতকৈও কম সময় লাগে।",
      step2_num: "২",
      step2_title: "হোৱাটছএপত লিংক পঠিয়াওক",
      step2_desc: "এপে এটা ভদ্ৰ বাৰ্তা আৰু ৬টা সংখ্যাৰ গোপন কোডসহ এটা ব্যক্তিগত লিংক তৈয়াৰ কৰে।",
      step3_num: "৩",
      step3_title: "আনজনে নিশ্চিত কৰে",
      step3_desc: "তেওঁলোকে কোনো এপ ডাউনলোড নকৰাকৈ মোবাইলৰ ব্ৰাউজাৰত লিংকটো খুলি লগে লগে নিশ্চিত কৰিব পাৰে।",
      stepper_showing: "দেখুওৱা হৈছে:",

      calc_badge: "সঠিক হিচাপ",
      calc_title: "কিস্তিৰ সময়সূচী প্ৰস্তুত কৰক",
      calc_subtitle: "এক পইচাও হীন-দেড়ি নোহোৱাকৈ কিস্তি গণনা কৰক। শেষ কিস্তিত বাকী থকা পইচা মিলি যায়।",
      calc_amount_label: "মূল ধনৰ পৰিমাণ (₹)",
      calc_interest_mode_label: "সুদৰ ধৰণ",
      calc_int_none: "০% সুদ নাই",
      calc_int_simple: "সাধাৰণ সুদ",
      calc_int_compound: "হ্ৰাসমান চক্ৰবৃদ্ধি",
      calc_rate_label: "বাৰ্ষিক সুদৰ হাৰ (%)",
      calc_inst_count_label: "কিস্তিৰ সংখ্যা:",
      calc_freq_label: "কিস্তিৰ ব্যৱধান",
      calc_freq_monthly: "মাহেকীয়া",
      calc_freq_weekly: "সাপ্তাহিক",
      calc_kpi_per_inst: "প্ৰতি কিস্তি",
      calc_kpi_interest: "মুঠ সুদ",
      calc_kpi_total: "মুঠ পৰিশোধ",
      calc_kpi_duration: "সময়সীমা",
      calc_th_num: "#",
      calc_th_date: "তাৰিখ",
      calc_th_amount: "কিস্তি",
      calc_th_balance: "বাকী থকা ধন",
      calc_btn_copy: "কিস্তিৰ তালিকা কপি কৰক",

      benefits_badge: "দৈনন্দিন জীৱনৰ বাবে",
      benefits_title: "সহজ ভাষাত সুবিধাসমূহ",
      benefits_subtitle: "লেনদেন সহজ আৰু সম্পৰ্ক মধুৰ কৰি ৰখাৰ সকলো সুবিধা ইয়াত আছে।",
      benefit1_title: "আপোনাৰ ফোনত সুৰক্ষিতভাৱে বন্ধ",
      benefit1_desc: "আপোনাৰ সকলো হিচাপ কেৱল আপোনাৰ ফোনতে বন্ধ থাকে। অন্য কোনেও পঢ়িব নোৱাৰে।",
      benefit2_title: "ইন্টাৰনেট নোহোৱাকৈ চলে",
      benefit2_desc: "যিকোনো ঠাইতে ইন্টাৰনেট অবিহনে নতুন হিচাপ লিখক আৰু পুৰণি লেনদেন চাওক।",
      benefit3_title: "ভদ্ৰ সোঁৱৰণী বাৰ্তা",
      benefit3_desc: "মধুৰ আৰু বিনম্ৰ বাৰ্তা যিয়ে বন্ধুত্ব আৰু পাৰস্পৰিক সম্পৰ্ক অটুট ৰাখে।",
      benefit4_title: "UPI-ৰে সহজে পৰিশোধ",
      benefit4_desc: "কিস্তিৰ ঠিক পৰিমাণৰ QR কোড বনাওক বা GPay, PhonePe-ৰ জৰিয়তে ধন গ্ৰহণ কৰক।",
      benefit5_title: "গুগল ড্ৰাইভত বেকআপ ৰাখক",
      benefit5_desc: "পাছৱৰ্ডৰ সৈতে আপোনাৰ নিজা গুগল ড্ৰাইভত সম্পূৰ্ণ সুৰক্ষিতভাৱে ডাটা বেকআপ লওক।",
      benefit6_title: "৫টা ভাষাত উপলব্ধ",
      benefit6_desc: "ইংৰাজী, হিন্দী, বাংলা, অসমীয়া আৰু নেপালীত সহজ আৰু ঘৰুৱা ভাষাত উপলব্ধ।",

      trust_badge: "আপোনাৰ তথ্য আপোনাৰ হাতত",
      trust_title: "আমি কি জমা ৰাখো আৰু কি নাৰাখো",
      trust_subtitle: "সঁচা সততা। কোনো ট্ৰেকাৰ নাই, কোনো তথ্য বিক্ৰী কৰা নহয়।",
      th_data: "তথ্য",
      th_where: "ক'ত থাকে",
      th_status: "সুৰক্ষাৰ স্থিতি",
      row1_data: "ফোন কন্টাক্ট আৰু ফোন বুক",
      row1_where: "কেতিয়াও চুই চোৱা নহয়",
      row1_status: "শূন্য প্ৰৱেশাধিকাৰ",
      row2_data: "বেংক একাউন্ট আৰু UPI পিন",
      row2_where: "কেতিয়াও বিচৰা নহয়",
      row2_status: "শূন্য প্ৰৱেশাধিকাৰ",
      row3_data: "সকলো হিচাপ, টোকা আৰু ৰচিদ",
      row3_where: "কেৱল আপোনাৰ ফোনতে থাকে",
      row3_status: "সম্পূৰ্ণ ব্যক্তিগত",
      row4_data: "পঠোৱা নিশ্চিতকৰণ লিংক",
      row4_where: "অস্থায়ী লিংক (১৫ মিনিট / ৭ দিনত ম্যাদ শেষ)",
      row4_status: "পথত সুৰক্ষিত",
      row5_data: "বেকআপ ফাইল",
      row5_where: "আপোনাৰ ব্যক্তিগত গুগল ড্ৰাইভ",
      row5_status: "পাছৱৰ্ডেৰে সুৰক্ষিত",

      honest_money_title: "ধাৰ এপে ধনৰ লেনদেন নকৰে",
      honest_money_desc: "আমি এখন ডিজিটেল ডায়েৰীহে, কোনো বেংক বা ৱালেট নহয়। সকলো ধন পৰিশোধ আপোনাৰ বেংক বা UPI-ৰ জৰিয়তে হয়।",
      honest_legal_title: "ধাৰ কোনো আইনী চুক্তি নহয়",
      honest_legal_desc: "ই পাৰস্পৰিক বিশ্বাস আৰু স্পষ্টতাৰ বাবেহে, কোনো আনুষ্ঠানিক আইনী নথিৰ বিকল্প নহয়।",
      trust_callout_text: "আমাক লৈ থকা সুৰক্ষাৰ প্ৰমাণ পৰীক্ষা কৰিব বিচাৰে নেকি?",
      trust_callout_link: "ধাৰ ট্ৰাষ্ট চেণ্টাৰ চাওক →",

      faq_badge: "কিবা সুধিব লগা আছে?",
      faq_title: "সঘনাই সোধা প্ৰশ্নসমূহ",
      faq_cat_all: "সকলো",
      faq_cat_basics: "মৌলিক",
      faq_cat_privacy: "গোপনীয়তা",
      faq_cat_reminders: "স্মাৰক",
      faq_cat_backup: "বেকআপ",
      faq_cat_weblink: "ৱেব লিংক",
      faq1_q: "ধাৰ এপটো সম্পূৰ্ণ বিনামূলীয়ানে?",
      faq1_a: "হয়, ই ১০০% বিনামূলীয়া। কোনো বিজ্ঞাপন নাই, কোনো মাহেকীয়া মাচুল নাই আৰু কোনো লুকোৱা মাচুল নাই।",
      faq2_q: "এপটো ব্যৱহাৰ কৰিবলৈ ফোন নম্বৰ দিব লাগিবনে?",
      faq2_a: "নালাগে। আপুনি কোনো ফোন নম্বৰ নিদিয়াকৈ বা একাউন্ট নোখোলাকৈ সম্পূৰ্ণ বেনামীভাৱে এপটো চলাব পাৰে।",
      faq3_q: "ফোনটো হেৰাই গ'লে বা সলনি হ'লে কি হ'ব?",
      faq3_a: "যদি বেকআপ ৰাখিছে, নতুন ফোনত গুগল ড্ৰাইভৰ পৰা নিজৰ পাছৱৰ্ড ব্যৱহাৰ কৰি সকলো হিচাপ ঘূৰাই আনিব পাৰিব।",
      faq4_q: "আনজনে মোৰ সকলো হিচাপ দেখা পাবনে?",
      faq4_a: "নাপায়। নিশ্চিতকৰণ লিংকটোৰে তেওঁলোকে কেৱল সেই নিৰ্দিষ্ট হিচাপটোহে দেখা পাব যিটো আপুনি পঠিয়াইছে।",
      faq5_q: "মই মোৰ তথ্য কেনেকৈ মচি পেলাম?",
      faq5_a: "এপৰ ভিতৰৰ পৰা যিকোনো সময়তে যিকোনো হিচাপ মচিব পাৰে। ৱেব লিংক মচিবলৈ আমাৰ অনলাইন ডিলিট ফৰ্ম ব্যৱহাৰ কৰক।",
      faq6_q: "হোৱাটছএপত সোঁৱৰণী বাৰ্তা কেনেকৈ কাম কৰে?",
      faq6_a: "ধাৰ এপে এটা বিনম্ৰ বাৰ্তা তৈয়াৰ কৰি হোৱাটছএপ খুলি দিয়ে, যাতে আপুনি চাই পঠিয়াব পাৰে।",
      faq7_q: "আনজনে লিংকটো চাবলৈ এপ ইনষ্টল কৰিব লাগিব নেকি?",
      faq7_a: "নালাগে। লিংকটো যিকোনো সাধাৰণ ম'বাইল ব্ৰাউজাৰতে খোল খায়। কোনো এপৰ প্ৰয়োজন নাই।",
      faq8_q: "মোৰ গুগল ড্ৰাইভ বেকআপ কিমান সুৰক্ষিত?",
      faq8_a: "সম্পূৰ্ণ সুৰক্ষিত। আপোনাৰ পাছৱৰ্ড নোহোৱাকৈ কোনেও এই ফাইল খুলিব নোৱাৰে।",
      faq_no_match: "কোনো প্ৰশ্ন বিচাৰি পোৱা নগ'ল।",
      faq_reset_search: "অনুসন্ধান ৰিছেট কৰক",

      demo_box_title: "ইনষ্টল নকৰাকৈয়ে অভিজ্ঞতা লওক",
      demo_box_desc: "আপুনি লিংক পঠিয়ালে আনজনে কেনেকৈ দেখা পায় সেয়া এপ নোহোৱাকৈ পৰীক্ষা কৰি চাওক।",
      btn_launch_demo: "ডেমো চলাই চাওক",

      mobile_bar_tag: "১০০% বিনামূলীয়া আৰু অফলাইন",

      footer_desc: "ধন দিয়া আৰু লোৱাৰ সহজ, সৎ আৰু ব্যক্তিগত ডিজিটেল হিচাপ। ভাৰত আৰু নেপালৰ সাধাৰণ মানুহৰ বাবে নিৰ্মিত।",
      footer_product: "উৎপাদন",
      footer_legal: "আইনী",
      footer_rights: "© ২০২৬ ধাৰ। সৰ্বস্বত্ব সংৰক্ষিত। মুক্ত-উৎস আৰু গোপনীয়তা-কেন্দ্ৰিক।"
    },

    ne: {
      meta_title: "उधार — दिएको र लिएको पैसाको स्पष्ट र निजी हिसाब राख्नुहोस्",
      meta_desc: "बिर्सने डर छैन, पैसाको विवाद छैन। १००% सुरक्षित अफलाइन डायरी र नि:शुल्क ह्वाट्सएप पुष्टि।",
      brand_name: "UDHAAR",
      nav_how_it_works: "कसरी काम गर्छ",
      nav_calculator: "क्यालकुलेटर",
      nav_demo: "डेमो हेर्नुहोस्",
      nav_benefits: "फाइदाहरू",
      nav_trust: "ट्रस्ट सेन्टर",
      nav_ledger: "वेब खाता",
      nav_updates: "नयाँ के छ",
      nav_faq: "प्रायः सोधिने प्रश्न",
      nav_privacy: "गोपनीयता नीति",
      btn_get_play: "गुगल प्लेबाट डाउनलोड गर्नुहोस्",
      btn_early_access: "प्रारम्भिक पहुँचमा सामेल हुनुहोस्",
      btn_try_demo: "पुष्टि पेज कस्तो देखिन्छ हेर्नुहोस्",
      hero_pill: "नि:शुल्क • सुरक्षित • इन्टरनेट बिना चल्ने",
      hero_title: "सापटी दिएको र लिएको पैसाको स्पष्ट हिसाब राख्नुहोस्",
      hero_promise: "बिर्सने डर छैन, पैसाको विवाद छैन।",
      hero_desc: "पैसाको लेनदेन केही सेकेन्डमै टिपोट गर्नुहोस्। ह्वाट्सएपमा एक निजी लिङ्क पठाउनुहोस् ताकि दुवै जना रकम र मितिमा सहमत हुन सकून्। पूर्ण रूपमा तपाईंको आफ्नै फोनमा सुरक्षित।",

      float_ravi_title: "रविले पुष्टि गरे",
      float_ravi_val: "₹५,०००",
      float_wa_title: "याद दिलाइयो",
      float_wa_sub: "ह्वाट्सएपमा",

      demo_widget_badge: "सिधै चलाएर हेर्नुहोस्",
      demo_widget_title: "यहाँ एक हिसाब टिपेर हेर्नुहोस्",
      demo_widget_subtitle: "कुनै एप इन्स्टल नगरिकनै हेर्नुहोस् कति छिटो हिसाब राख्न र ह्वाट्सएप पुष्टि लिङ्क बनाउन सकिन्छ।",
      demo_tab1: "रकम",
      demo_tab2: "व्यक्ति",
      demo_tab3: "किस्ता",
      demo_tab4: "पुष्टि",
      demo_step1_title: "पैसा दिनुभयो कि लिनुभयो?",
      demo_gave: "दिनुभयो (सापटी दिएको)",
      demo_took: "लिनुभयो (सापटी लिएको)",
      demo_amount_label: "रकम लेख्नुहोस् (₹)",
      demo_next_person: "अर्को: व्यक्तिको नाम",
      demo_step2_title: "यो हिसाब कोसँग हो?",
      demo_name_label: "व्यक्तिको नाम",
      demo_note_label: "वैकल्पिक टिप्पणी",
      demo_next_installments: "अर्को: किस्ता",
      demo_back: "पछाडि",
      demo_step3_title: "पैसा कसरी फिर्ता हुन्छ?",
      demo_installments_label: "किस्ताको सङ्ख्या",
      demo_next_preview: "अर्को: सन्देश हेर्नुहोस्",
      demo_step4_title: "ह्वाट्सएप सन्देश तयार छ",
      demo_step4_desc: "उधारले एक भद्र सन्देश बनाउँछ ताकि दुवै जना एउटै स्पष्ट हिसाबमा रहन सकून्।",
      demo_restart: "फेरि सुरु गर्नुहोस्",
      demo_copy_wa: "सन्देश कपी गर्नुहोस्",
      demo_open_guest: "पुष्टि पेज खोल्नुहोस् ↗",

      steps_badge: "सजिलो ३ कदम",
      steps_title: "यो ३ सजिलो चरणमा काम गर्छ",
      steps_subtitle: "कुनै अन्योल छैन। दुवै जनासँग एउटै स्पष्ट हिसाब हुन्छ।",
      step1_num: "१",
      step1_title: "हिसाब थप्नुहोस्",
      step1_desc: "रकम, फिर्ता गर्ने मिति र व्यक्तिको नाम लेख्नुहोस्। १० सेकेन्डभन्दा कम समय लाग्छ।",
      step2_num: "२",
      step2_title: "ह्वाट्सएपमा लिङ्क पठाउनुहोस्",
      step2_desc: "एपले एक नम्र सन्देश, पुष्टि लिङ्क र ६ अंकको गोप्य कोड तयार गरिदिन्छ।",
      step3_num: "३",
      step3_title: "अर्को व्यक्तिले पुष्टि गर्छन्",
      step3_desc: "उनीहरूले कुनै एप डाउनलोड नगरीकनै मोबाइल ब्राउजरमा लिङ्क खोलेर तुरुन्त पुष्टि गर्न सक्छन्।",
      stepper_showing: "देखाइएको छ:",

      calc_badge: "सही हिसाब",
      calc_title: "किस्ताको तालिका योजना गर्नुहोस्",
      calc_subtitle: "एक पैसाको पनि फरक नपरी किस्ता निकाल्नुहोस्। अन्तिम किस्तामा बाँकी पैसा आफैं जोडिन्छ।",
      calc_amount_label: "मूल रकम (₹)",
      calc_interest_mode_label: "ब्याजको प्रकार",
      calc_int_none: "०% ब्याज छैन",
      calc_int_simple: "साधारण ब्याज",
      calc_int_compound: "घट्दो चक्रবৃদ্ধি",
      calc_rate_label: "वार्षिक ब्याज दर (%)",
      calc_inst_count_label: "किस्ता सङ्ख्या:",
      calc_freq_label: "किस्ताको अन्तर",
      calc_freq_monthly: "मासिक",
      calc_freq_weekly: "साप्ताहिक",
      calc_kpi_per_inst: "प्रत्येक किस्ता",
      calc_kpi_interest: "कुल ब्याज",
      calc_kpi_total: "कुल भुक्तानी",
      calc_kpi_duration: "समय",
      calc_th_num: "#",
      calc_th_date: "मिति",
      calc_th_amount: "किस्ता",
      calc_th_balance: "बाँकी रकम",
      calc_btn_copy: "किस्ता तालिका कपी गर्नुहोस्",

      benefits_badge: "दैनिक जीवनका लागि",
      benefits_title: "सजिलो भाषामा फाइदाहरू",
      benefits_subtitle: "लेनदेन सजिलो र सम्बन्ध सुमधुर बनाइराख्न चाहिने सबै कुरा।",
      benefit1_title: "तपाईंको फोनमा सुरक्षित बन्द",
      benefit1_desc: "तपाईंको पूरा हिसाब केवल तपाईंको आफ्नै फोनमा सुरक्षित रहन्छ। अरू कसैले पढ्न सक्दैन।",
      benefit2_title: "इन्टरनेट बिना पनि चल्छ",
      benefit2_desc: "जहाँसुकै बिना इन्टरनेट नयाँ हिसाब थप्नुहोस् र पुरानो कारोबार हेर्नुहोस्।",
      benefit3_title: "नम्र र भद्र सम्झौना सन्देश",
      benefit3_desc: "सम्बन्ध र मित्रता नबिग्रने गरी शालीन भाषामा तयार गरिएका सम्झौना सन्देशहरू।",
      benefit4_title: "UPI मार्फत सजिलै भुक्तानी",
      benefit4_desc: "किस्ताको ठ्याक्कै रकमको QR कोड बनाउनुहोस् वा GPay, PhonePe बाट तुरुन्त भुक्तानी लिनुहोस्।",
      benefit5_title: "गुगल ड्राइभमा सुरक्षित ब्याकअप",
      benefit5_desc: "आफ्नो व्यक्तिगत गुगल ड्राइभमा गोप्य पासवर्डका साथ सुरक्षित ब्याकअप राख्नुहोस्।",
      benefit6_title: "५ भाषामा उपलब्ध",
      benefit6_desc: "अंग्रेजी, हिन्दी, बंगाली, असमिया र नेपाली भाषामा उपलब्ध।",

      trust_badge: "तपाईंको डाटा तपाईंको अधिकार",
      trust_title: "हामी के राख्छौं र के राख्दैनौं",
      trust_subtitle: "सच्चा इमानदारी। कुनै ट्र्याकर छैन, कुनै डाटा बिक्री हुँदैन।",
      th_data: "जानकारी",
      th_where: "कहाँ रहन्छ",
      th_status: "सुरक्षा अवस्था",
      row1_data: "फोन सम्पर्क र ठेगाना पुस्तिका",
      row1_where: "कहिल्यै हेरिँदैन",
      row1_status: "कुनै पहुँच छैन",
      row2_data: "बैंक खाता र UPI पिन",
      row2_where: "कहिल्यै मागिदैन",
      row2_status: "कुनै पहुँच छैन",
      row3_data: "लेनदेनको हिसाब, टिपोट र रसिदहरू",
      row3_where: "केवल तपाईंको आफ्नै फोनमा",
      row3_status: "पूर्ण रूपमा निजी",
      row4_data: "साझेदारी गरिएको पुष्टि लिङ्क",
      row4_where: "अस्थायी लिङ्क (१५ मिनेट / ७ दिनमा समाप्त हुने)",
      row4_status: "बाटोमा सुरक्षित",
      row5_data: "ब्याकअप फाइल",
      row5_where: "तपाईंको व्यक्तिगत गुगल ड्राइभ",
      row5_status: "पासवर्डले सुरक्षित",

      honest_money_title: "उधार एपले पैसा ओसारपसार गर्दैन",
      honest_money_desc: "हामी डिजिटल डायरी हौं, कुनै बैंक वा वालेट होइनौं। सबै भुक्तानी तपाईंको आफ्नै बैंक वा UPI बाट हुन्छ।",
      honest_legal_title: "उधार कुनै कानुनी सम्झौता होइन",
      honest_legal_desc: "यो आपसी स्पष्टता र विश्वासका लागि हो, कुनै औपचारिक कानुनी कागजातको विकल्प होइन।",
      trust_callout_text: "हाम्रो गोपनीयताको प्राविधिक प्रमाण हेर्न चाहनुहुन्छ?",
      trust_callout_link: "उधार ट्रस्ट सेन्टर हेर्नुहोस् →",

      faq_badge: "कुनै प्रश्न छ?",
      faq_title: "प्रायः सोधिने प्रश्नहरू",
      faq_cat_all: "सबै",
      faq_cat_basics: "आधारभूत",
      faq_cat_privacy: "गोपनीयता",
      faq_cat_reminders: "सम्झौना",
      faq_cat_backup: "ब्याकअप",
      faq_cat_weblink: "वेब लिङ्क",
      faq1_q: "के उधार एप पूर्ण रूपमा नि:शुल्क छ?",
      faq1_a: "हो, यो १००% नि:शुल्क छ। यसमा कुनै विज्ञापन, कुनै मासिक शुल्क वा लुकेको खर्च छैन।",
      faq2_q: "एप प्रयोग गर्न फोन नम्बर दिनुपर्छ?",
      faq2_a: "पर्दैन। तपाईं कुनै फोन नम्बर नदिइकन वा खाता नखोलीकनै पूर्ण रूपमा अज्ञात रहेर एप प्रयोग गर्न सक्नुहुन्छ।",
      faq3_q: "यदि फोन हरायो वा फेरियो भने के हुन्छ?",
      faq3_a: "यदि तपाईंले गुगल ड्राइभमा ब्याकअप राख्नुभएको छ भने, नयाँ फोनमा पासवर्ड हालेर सबै हिसाब फिर्ता पाउन सक्नुहुन्छ।",
      faq4_q: "के अर्को व्यक्तिले मेरो खाताको सबै हिसाब हेर्न सक्छ?",
      faq4_a: "सक्दैन। तपाईंले जुन हिसाबको लिङ्क पठाउनुहुन्छ, उसले केवल त्यही एउटा हिसाब मात्र देख्न सक्छ।",
      faq5_q: "म आफ्नो डाटा कसरी मेटाउन सक्छु?",
      faq5_a: "एप भित्रबाट कुनै पनि समयमा कुनै पनि हिसाब मेटाउन सक्नुहुन्छ। वेब लिङ्क मेटाउन हाम्रो डिलिट फारम प्रयोग गर्नुहोस्।",
      faq6_q: "ह्वाट्सएप सम्झौना सन्देश कसरी काम गर्छ?",
      faq6_a: "उधारले एक भद्र सन्देश लेखेर सीधै ह्वाट्सएप खोलिदिन्छ ताकि पठाउनु अघि तपाईंको पूरा नियन्त्रण रहोस्।",
      faq7_q: "अर्को व्यक्तिलाई लिङ्क खोल्न कुनै एप चाहिन्छ?",
      faq7_a: "चाहिँदैन। लिङ्क कुनै पनि मोबाइल ब्राउजरमा खुल्छ। एप इन्स्टल गर्नुपर्दैन।",
      faq8_q: "मेरो गुगल ड्राइभ ब्याकअप कत्तिको सुरक्षित छ?",
      faq8_a: "पूर्ण रूपमा सुरक्षित। तपाईंको गोप्य पासवर्ड बिना कसैले पनि खोल्न सक्दैन।",
      faq_no_match: "कुनै प्रश्न भेटिएन।",
      faq_reset_search: "खोज रिसेट गर्नुहोस्",

      demo_box_title: "इन्स्टल नगरीकनै कस्तो देखिन्छ अनुभव गर्नुहोस्",
      demo_box_desc: "तपाईंले लिङ्क पठाउँदा अर्को व्यक्तिले के देख्छन् कुनै एप बिना नै चलाएर हेर्नुहोस्।",
      btn_launch_demo: "डेमो चलाएर हेर्नुहोस्",

      mobile_bar_tag: "१००% नि:शुल्क र अफलाइन",

      footer_desc: "लेनदेनको सरल, इमानदार र निजी डिजिटल हिसाब। भारत र नेपालका आम मानिसहरूका लागि विशेष रूपमा निर्मित।",
      footer_product: "उत्पादन",
      footer_legal: "कानुनी",
      footer_rights: "© २०२६ उधार। सर्वाधिकार सुरक्षित। खुला-स्रोत र गोपनीयता-केन्द्रित।"
    }
  };

  let currentLang = (typeof localStorage !== "undefined" && localStorage.getItem("udhaar_lang")) || "en";
  if (!I18N[currentLang]) currentLang = "en";

  function t(key) {
    const dict = I18N[currentLang] || I18N.en;
    return dict[key] || I18N.en[key] || key;
  }

  function applyLanguage(lang) {
    if (!I18N[lang]) lang = "en";
    currentLang = lang;
    if (typeof localStorage !== "undefined") {
      localStorage.setItem("udhaar_lang", lang);
    }
    document.documentElement.lang = lang;

    // Update title & meta description
    document.title = t("meta_title");
    const metaDesc = document.querySelector('meta[name="description"]');
    if (metaDesc) metaDesc.setAttribute("content", t("meta_desc"));

    const ogTitle = document.querySelector('meta[property="og:title"]');
    if (ogTitle) ogTitle.setAttribute("content", t("meta_title"));

    const ogDesc = document.querySelector('meta[property="og:description"]');
    if (ogDesc) ogDesc.setAttribute("content", t("meta_desc"));

    // Update all data-i18n elements
    document.querySelectorAll("[data-i18n]").forEach((el) => {
      const key = el.getAttribute("data-i18n");
      const val = t(key);
      if (val) el.textContent = val;
    });

    // Update select element value
    const langSelect = document.getElementById("lang-select");
    if (langSelect) langSelect.value = lang;

    // Refresh dynamic site config CTA texts
    syncSiteConfigButtons();

    // Refresh demo preview card text
    updateDemoPreview();

    // Refresh calculator schedule
    runCalculator();
  }

  /**
   * Site Config Switch (Fix 2):
   * When PLAY_STORE_LIVE === false, update store buttons to "Join early access"
   */
  function syncSiteConfigButtons() {
    const cfg = window.SITE_CONFIG || { PLAY_STORE_LIVE: false, CLOSED_TESTING_URL: "https://play.google.com/apps/testing/com.jyotirmoyb.udhaar", PLAY_STORE_URL: "https://play.google.com/store/apps/details?id=com.jyotirmoyb.udhaar" };
    const isLive = cfg.PLAY_STORE_LIVE === true;
    const targetUrl = isLive ? cfg.PLAY_STORE_URL : cfg.CLOSED_TESTING_URL;
    const labelKey = isLive ? "btn_get_play" : "btn_early_access";
    const labelText = t(labelKey);

    document.querySelectorAll("[data-store-button]").forEach((btn) => {
      btn.setAttribute("href", targetUrl);
      const textSpan = btn.querySelector("[data-i18n]") || btn;
      if (textSpan) textSpan.textContent = labelText;
    });
  }

  /**
   * Sticky Header Shrink & Sticky Bottom Mobile Bar
   */
  function initHeaderAndBottomBar() {
    const header = document.getElementById("site-header");
    const bottomBar = document.getElementById("mobile-sticky-bar");
    let lastScrollY = window.scrollY;

    window.addEventListener("scroll", () => {
      const currentScrollY = window.scrollY;

      // Header shrink
      if (header) {
        if (currentScrollY > 36) {
          header.classList.add("scrolled");
        } else {
          header.classList.remove("scrolled");
        }
      }

      // Mobile bottom bar hide on scroll down, show on scroll up
      if (bottomBar) {
        if (currentScrollY > lastScrollY && currentScrollY > 180) {
          bottomBar.classList.add("bar-hidden");
        } else {
          bottomBar.classList.remove("bar-hidden");
        }
      }

      lastScrollY = currentScrollY;
    }, { passive: true });
  }

  /**
   * Mobile Drawer Menu with Focus Trap & Escape key handling
   */
  function initMobileDrawer() {
    const menuBtn = document.getElementById("mobile-menu-btn");
    const drawer = document.getElementById("mobile-drawer");
    const backdrop = document.getElementById("mobile-drawer-backdrop");
    const closeBtn = document.getElementById("drawer-close-btn");
    if (!menuBtn || !drawer || !backdrop) return;

    function openDrawer() {
      drawer.classList.add("open");
      backdrop.classList.add("active");
      menuBtn.setAttribute("aria-expanded", "true");
      drawer.removeAttribute("inert");
      drawer.removeAttribute("aria-hidden");
      if (closeBtn) closeBtn.focus();
    }

    function closeDrawer() {
      drawer.classList.remove("open");
      backdrop.classList.remove("active");
      menuBtn.setAttribute("aria-expanded", "false");
      drawer.setAttribute("inert", "");
      drawer.removeAttribute("aria-hidden");
      menuBtn.focus();
    }

    menuBtn.addEventListener("click", openDrawer);
    if (closeBtn) closeBtn.addEventListener("click", closeDrawer);
    backdrop.addEventListener("click", closeDrawer);

    // Close on navigation click inside drawer
    drawer.querySelectorAll(".drawer-link, .drawer-cta").forEach((link) => {
      link.addEventListener("click", closeDrawer);
    });

    // Keyboard support: Escape key closes drawer
    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape" && drawer.classList.contains("open")) {
        closeDrawer();
      }
    });
  }

  /**
   * Animated Stepper Scroll Highlight & Preview Updating (Part 1 Item 4)
   */
  function initStepperObserver() {
    const stepCards = document.querySelectorAll("#how-it-works .step-card");
    const stepperImg = document.getElementById("stepper-img");
    const stepperSource = document.getElementById("stepper-source");
    const stepperText = document.getElementById("stepper-indicator-text");
    if (!stepCards.length) return;

    const stepAssets = {
      "1": {
        img: "/assets/screen-new-record.webp",
        src: "/assets/screen-new-record-480.webp 480w, /assets/screen-new-record.webp 800w",
        label: "Step 1 — Add the record"
      },
      "2": {
        img: "/assets/screen-whatsapp.webp",
        src: "/assets/screen-whatsapp-480.webp 480w, /assets/screen-whatsapp.webp 800w",
        label: "Step 2 — Send the link on WhatsApp"
      },
      "3": {
        img: "/assets/screen-dashboard.webp",
        src: "/assets/screen-dashboard-480.webp 480w, /assets/screen-dashboard.webp 800w",
        label: "Step 3 — The other person confirms"
      }
    };

    if ("IntersectionObserver" in window) {
      const observer = new IntersectionObserver(
        (entries) => {
          entries.forEach((entry) => {
            if (entry.isIntersecting) {
              const step = entry.target.getAttribute("data-step");
              stepCards.forEach((c) => c.classList.remove("active"));
              entry.target.classList.add("active");

              const asset = stepAssets[step];
              if (asset && stepperImg && stepperSource) {
                stepperImg.src = asset.img;
                stepperSource.srcset = asset.src;
                if (stepperText) stepperText.textContent = asset.label;
              }
            }
          });
        },
        { rootMargin: "-25% 0px -40% 0px", threshold: 0.1 }
      );

      stepCards.forEach((card) => observer.observe(card));
    }
  }

  /**
   * Interactive Demo Simulation Engine (Part 1 Item 2)
   */
  const demoState = {
    step: 1,
    direction: "gave", // "gave" | "took"
    amount: 2500,
    name: "Sunil",
    note: "Emergency shop repair",
    installments: 3
  };

  function switchDemoStep(targetStep) {
    demoState.step = targetStep;

    // Update tab headers
    document.querySelectorAll(".demo-step-tab").forEach((tab) => {
      const tabNum = parseInt(tab.getAttribute("data-step-tab"), 10);
      const isActive = tabNum === targetStep;
      tab.classList.toggle("active", isActive);
      tab.setAttribute("aria-selected", isActive ? "true" : "false");
    });

    // Update panels
    for (let i = 1; i <= 4; i++) {
      const panel = document.getElementById(`demo-step-panel-${i}`);
      if (panel) {
        if (i === targetStep) {
          panel.classList.add("active");
          panel.removeAttribute("hidden");
        } else {
          panel.classList.remove("active");
          panel.setAttribute("hidden", "true");
        }
      }
    }

    if (targetStep === 3) {
      updateDemoSchedulePreview();
    } else if (targetStep === 4) {
      updateDemoPreview();
    }
  }

  function updateDemoSchedulePreview() {
    const previewContainer = document.getElementById("demo-schedule-preview");
    if (!previewContainer) return;

    const amt = demoState.amount;
    const count = demoState.installments;
    const base = Math.floor(amt / count);
    const remainder = amt % count;

    let html = "";
    for (let i = 1; i <= count; i++) {
      const isLast = i === count;
      const instAmt = isLast ? base + remainder : base;
      const dateLabel = i === 1 ? "Next month" : `${i} months`;
      html += `
        <div class="demo-schedule-pill">
          <span class="sched-seq">${i}/${count}</span>
          <span class="sched-date">${dateLabel}</span>
          <span class="sched-amt font-mono">₹${instAmt.toLocaleString("en-IN")}</span>
        </div>
      `;
    }
    previewContainer.innerHTML = html;
  }

  function updateDemoPreview() {
    const textEl = document.getElementById("demo-wa-bubble-text");
    if (!textEl) return;

    const isGave = demoState.direction === "gave";
    const amtStr = `₹${demoState.amount.toLocaleString("en-IN")}`;
    const name = demoState.name.trim() || "Sunil";
    const count = demoState.installments;

    let instBreakdown = "";
    if (count > 1) {
      const base = Math.floor(demoState.amount / count);
      const remainder = demoState.amount % count;
      const parts = [];
      for (let i = 1; i <= count; i++) {
        parts.push(`₹${(i === count ? base + remainder : base).toLocaleString("en-IN")}`);
      }
      instBreakdown = ` (${count} installments: ${parts.join(", ")})`;
    }

    let msg = "";
    if (currentLang === "hi") {
      msg = `नमस्ते ${name} जी, मैंने उधार ऐप पर ${amtStr} का हिसाब दर्ज किया है${instBreakdown}। कृपया यहाँ देखकर पुष्टि करें: https://udhaar.jyotirmoyb.com/u/?demo=true`;
    } else if (currentLang === "bn") {
      msg = `নমস্কার ${name} বাবু, আমি ধার অ্যাপে ${amtStr}-এর হিসাব লিখেছি${instBreakdown}। অনুগ্রহ করে দেখে নিশ্চিত করুন: https://udhaar.jyotirmoyb.com/u/?demo=true`;
    } else if (currentLang === "as") {
      msg = `নমস্কাৰ ${name} ডাঙৰীয়া, মই ধাৰ এপত ${amtStr}-ৰ হিচাপ অন্তৰ্ভুক্ত কৰিছো${instBreakdown}। অনুগ্ৰহ কৰি ইয়াত চাই নিশ্চিত কৰক: https://udhaar.jyotirmoyb.com/u/?demo=true`;
    } else if (currentLang === "ne") {
      msg = `नमस्ते ${name} ज्यू, मैले उधार एपमा ${amtStr} को हिसाब राखेको छु${instBreakdown}। कृपया यहाँ हेरेर पुष्टि गर्नुहोस्: https://udhaar.jyotirmoyb.com/u/?demo=true`;
    } else {
      msg = `Hi ${name}, I recorded ${amtStr} on Udhaar${instBreakdown}. Please check and confirm here: https://udhaar.jyotirmoyb.com/u/?demo=true`;
    }

    textEl.textContent = msg;
  }

  function initDemoWidget() {
    // Tab switching with Keyboard Navigation (WCAG 2.1 AA)
    const demoTabs = Array.from(document.querySelectorAll(".demo-step-tab"));
    demoTabs.forEach((tab, index) => {
      tab.addEventListener("click", () => {
        const stepNum = parseInt(tab.getAttribute("data-step-tab"), 10);
        switchDemoStep(stepNum);
      });

      tab.addEventListener("keydown", (e) => {
        let newIndex = -1;
        if (e.key === "ArrowRight") {
          newIndex = (index + 1) % demoTabs.length;
        } else if (e.key === "ArrowLeft") {
          newIndex = (index - 1 + demoTabs.length) % demoTabs.length;
        } else if (e.key === "Home") {
          newIndex = 0;
        } else if (e.key === "End") {
          newIndex = demoTabs.length - 1;
        }
        if (newIndex >= 0) {
          e.preventDefault();
          demoTabs[newIndex].focus();
          const stepNum = parseInt(demoTabs[newIndex].getAttribute("data-step-tab"), 10);
          switchDemoStep(stepNum);
        }
      });
    });

    // Step 1: Direction
    const dirGave = document.getElementById("demo-dir-gave");
    const dirTook = document.getElementById("demo-dir-took");
    if (dirGave && dirTook) {
      dirGave.addEventListener("click", () => {
        demoState.direction = "gave";
        dirGave.classList.add("active");
        dirGave.setAttribute("aria-checked", "true");
        dirTook.classList.remove("active");
        dirTook.setAttribute("aria-checked", "false");
      });
      dirTook.addEventListener("click", () => {
        demoState.direction = "took";
        dirTook.classList.add("active");
        dirTook.setAttribute("aria-checked", "true");
        dirGave.classList.remove("active");
        dirGave.setAttribute("aria-checked", "false");
      });
    }

    // Step 1: Amount presets
    const amtInput = document.getElementById("demo-amount-input");
    document.querySelectorAll(".demo-card-container .amount-chip[data-amount]").forEach((chip) => {
      chip.addEventListener("click", () => {
        document.querySelectorAll(".demo-card-container .amount-chip[data-amount]").forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
        const val = parseInt(chip.getAttribute("data-amount"), 10);
        demoState.amount = val;
        if (amtInput) amtInput.value = val;
      });
    });

    if (amtInput) {
      amtInput.addEventListener("input", () => {
        const val = Math.max(1, parseInt(amtInput.value, 10) || 0);
        demoState.amount = val;
        document.querySelectorAll(".demo-card-container .amount-chip[data-amount]").forEach((c) => {
          c.classList.toggle("active", parseInt(c.getAttribute("data-amount"), 10) === val);
        });
      });
    }

    // Step 2: Name & Note
    const nameInput = document.getElementById("demo-name-input");
    if (nameInput) {
      nameInput.addEventListener("input", () => {
        demoState.name = nameInput.value;
      });
    }

    const noteInput = document.getElementById("demo-note-input");
    if (noteInput) {
      noteInput.addEventListener("input", () => {
        demoState.note = noteInput.value;
      });
    }

    // Step 3: Installments chips
    document.querySelectorAll(".demo-card-container .amount-chip[data-inst]").forEach((chip) => {
      chip.addEventListener("click", () => {
        document.querySelectorAll(".demo-card-container .amount-chip[data-inst]").forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
        demoState.installments = parseInt(chip.getAttribute("data-inst"), 10) || 1;
        updateDemoSchedulePreview();
      });
    });

    // Step Transitions
    const btnTo2 = document.getElementById("demo-btn-to-2");
    if (btnTo2) btnTo2.addEventListener("click", () => switchDemoStep(2));

    const btnBack1 = document.getElementById("demo-btn-back-1");
    if (btnBack1) btnBack1.addEventListener("click", () => switchDemoStep(1));

    const btnTo3 = document.getElementById("demo-btn-to-3");
    if (btnTo3) btnTo3.addEventListener("click", () => switchDemoStep(3));

    const btnBack2 = document.getElementById("demo-btn-back-2");
    if (btnBack2) btnBack2.addEventListener("click", () => switchDemoStep(2));

    const btnTo4 = document.getElementById("demo-btn-to-4");
    if (btnTo4) btnTo4.addEventListener("click", () => switchDemoStep(4));

    // Step 4 actions
    const restartBtn = document.getElementById("demo-btn-restart");
    if (restartBtn) {
      restartBtn.addEventListener("click", () => {
        demoState.step = 1;
        demoState.amount = 2500;
        demoState.direction = "gave";
        demoState.name = "Sunil";
        demoState.installments = 3;
        if (amtInput) amtInput.value = 2500;
        if (nameInput) nameInput.value = "Sunil";
        switchDemoStep(1);
      });
    }

    const copyWaBtn = document.getElementById("demo-btn-copy-wa");
    if (copyWaBtn) {
      copyWaBtn.addEventListener("click", async () => {
        const textEl = document.getElementById("demo-wa-bubble-text");
        if (textEl && navigator.clipboard) {
          try {
            await navigator.clipboard.writeText(textEl.textContent.trim());
            const orig = copyWaBtn.innerHTML;
            copyWaBtn.innerHTML = "✓ Copied!";
            setTimeout(() => { copyWaBtn.innerHTML = orig; }, 2000);
          } catch (_) {}
        }
      });
    }
  }

  /**
   * Repayment Calculator Engine (Part 1 Item 3)
   */
  const calcState = {
    principal: 10000,
    interestType: "none", // "none" | "simple" | "compound"
    interestRate: 12,
    installmentCount: 3,
    frequency: "MONTHLY"
  };

  function runCalculator() {
    const calc = window.UdhaarCalculator;
    if (!calc) return;

    const schedule = calc.calculateSchedule({
      principal: calcState.principal,
      interestType: calcState.interestType,
      interestRate: calcState.interestRate,
      installmentCount: calcState.installmentCount,
      frequency: calcState.frequency
    });

    const kpiInst = document.getElementById("calc-kpi-installment");
    const kpiInterest = document.getElementById("calc-kpi-interest");
    const kpiTotal = document.getElementById("calc-kpi-total");
    const kpiDuration = document.getElementById("calc-kpi-duration");
    const tbody = document.getElementById("calc-schedule-body");

    const perInst = schedule.perInstallmentFormatted || (schedule.installments[0] && schedule.installments[0].amountFormatted) || "";
    if (kpiInst) kpiInst.textContent = perInst;
    if (kpiInterest) kpiInterest.textContent = schedule.totalInterestFormatted || schedule.interestFormatted;
    if (kpiTotal) kpiTotal.textContent = schedule.totalAmountFormatted || schedule.totalFormatted;
    if (kpiDuration) {
      const unit = calcState.frequency === "WEEKLY" ? "Weeks" : "Months";
      kpiDuration.textContent = `${calcState.installmentCount} ${unit}`;
    }

    if (tbody) {
      let rows = "";
      let remainingPaise = schedule.totalAmountPaise;

      schedule.installments.forEach((inst) => {
        remainingPaise -= inst.amountPaise;
        const remDisplay = remainingPaise <= 0 ? "₹0" : calc.formatCurrency(remainingPaise);
        rows += `
          <tr>
            <td class="font-mono">${inst.sequence}</td>
            <td>${inst.dueDateFormatted}</td>
            <td class="font-mono">${inst.amountFormatted}</td>
            <td class="font-mono">${remDisplay}</td>
          </tr>
        `;
      });
      tbody.innerHTML = rows;
    }
  }

  function initCalculator() {
    const principalInput = document.getElementById("calc-principal");
    const rateInput = document.getElementById("calc-rate");
    const rateWrap = document.getElementById("calc-rate-wrap");
    const instRange = document.getElementById("calc-installments");
    const instVal = document.getElementById("calc-inst-count-val");

    // Quick chips for principal
    document.querySelectorAll(".calc-quick-chips .amount-chip").forEach((chip) => {
      chip.addEventListener("click", () => {
        document.querySelectorAll(".calc-quick-chips .amount-chip").forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
        const val = parseInt(chip.getAttribute("data-calc-amt"), 10);
        calcState.principal = val;
        if (principalInput) principalInput.value = val;
        runCalculator();
      });
    });

    if (principalInput) {
      principalInput.addEventListener("input", () => {
        calcState.principal = Math.max(10, parseInt(principalInput.value, 10) || 0);
        runCalculator();
      });
    }

    // Interest Mode Toggle
    const intNone = document.getElementById("calc-int-none");
    const intSimple = document.getElementById("calc-int-simple");
    const intCompound = document.getElementById("calc-int-compound");

    function setInterestMode(mode) {
      calcState.interestType = mode;
      [intNone, intSimple, intCompound].forEach((btn) => {
        if (!btn) return;
        const matches = (mode === "none" && btn === intNone) ||
                        (mode === "simple" && btn === intSimple) ||
                        (mode === "compound" && btn === intCompound);
        btn.classList.toggle("active", matches);
        btn.setAttribute("aria-checked", matches ? "true" : "false");
      });

      if (rateWrap) {
        rateWrap.style.display = mode === "none" ? "none" : "block";
      }
      runCalculator();
    }

    if (intNone) intNone.addEventListener("click", () => setInterestMode("none"));
    if (intSimple) intSimple.addEventListener("click", () => setInterestMode("simple"));
    if (intCompound) intCompound.addEventListener("click", () => setInterestMode("compound"));

    if (rateInput) {
      rateInput.addEventListener("input", () => {
        calcState.interestRate = Math.max(0, parseFloat(rateInput.value) || 0);
        runCalculator();
      });
    }

    if (instRange && instVal) {
      instRange.addEventListener("input", () => {
        calcState.installmentCount = parseInt(instRange.value, 10) || 1;
        instVal.textContent = String(calcState.installmentCount);
        runCalculator();
      });
    }

    // Frequency Toggle
    const freqMonthly = document.getElementById("calc-freq-monthly");
    const freqWeekly = document.getElementById("calc-freq-weekly");

    if (freqMonthly && freqWeekly) {
      freqMonthly.addEventListener("click", () => {
        calcState.frequency = "MONTHLY";
        freqMonthly.classList.add("active");
        freqMonthly.setAttribute("aria-checked", "true");
        freqWeekly.classList.remove("active");
        freqWeekly.setAttribute("aria-checked", "false");
        runCalculator();
      });

      freqWeekly.addEventListener("click", () => {
        calcState.frequency = "WEEKLY";
        freqWeekly.classList.add("active");
        freqWeekly.setAttribute("aria-checked", "true");
        freqMonthly.classList.remove("active");
        freqMonthly.setAttribute("aria-checked", "false");
        runCalculator();
      });
    }

    // Copy Schedule Summary Button
    const copySummaryBtn = document.getElementById("calc-copy-summary-btn");
    const copyStatus = document.getElementById("calc-copy-status");
    if (copySummaryBtn) {
      copySummaryBtn.addEventListener("click", async () => {
        const calc = window.UdhaarCalculator;
        if (!calc || !navigator.clipboard) return;

        const schedule = calc.calculateSchedule({
          principal: calcState.principal,
          interestType: calcState.interestType,
          interestRate: calcState.interestRate,
          installmentCount: calcState.installmentCount,
          frequency: calcState.frequency
        });

        let summaryText = `*Udhaar Repayment Schedule*\n`;
        summaryText += `Principal: ₹${Number(schedule.principalRupees).toLocaleString("en-IN")}\n`;
        summaryText += `Total Amount: ${schedule.totalAmountFormatted}\n`;
        summaryText += `Installments: ${schedule.installmentsCount} (${calcState.frequency.toLowerCase()})\n\n`;

        schedule.installments.forEach((inst) => {
          summaryText += `• #${inst.sequence} (${inst.dueDateFormatted}): ${inst.amountFormatted}\n`;
        });
        summaryText += `\nTracked privately with Udhaar: https://udhaar.jyotirmoyb.com/`;

        try {
          await navigator.clipboard.writeText(summaryText);
          if (copyStatus) copyStatus.textContent = "✓ Schedule copied!";
          setTimeout(() => { if (copyStatus) copyStatus.textContent = ""; }, 3000);
        } catch (_) {}
      });
    }

    runCalculator();
  }

  /**
   * FAQ Search & Category Filter (Part 1 Item 5)
   */
  function initFaqSearchAndFilter() {
    const searchInput = document.getElementById("faq-search");
    const clearBtn = document.getElementById("faq-search-clear");
    const chips = document.querySelectorAll(".faq-chip");
    const faqItems = document.querySelectorAll(".faq-item");
    const noResults = document.getElementById("faq-no-results");
    const resetFilterBtn = document.getElementById("faq-reset-filter-btn");

    let currentCategory = "all";
    let searchQuery = "";

    function filterFaq() {
      let visibleCount = 0;
      const q = searchQuery.toLowerCase().trim();

      faqItems.forEach((item) => {
        const cats = (item.getAttribute("data-category") || "").toLowerCase().split(" ");
        const questionText = (item.querySelector(".faq-question span:first-child")?.textContent || "").toLowerCase();
        const answerText = (item.querySelector(".faq-answer")?.textContent || "").toLowerCase();

        const matchesCat = currentCategory === "all" || cats.includes(currentCategory);
        const matchesQuery = !q || questionText.includes(q) || answerText.includes(q);

        if (matchesCat && matchesQuery) {
          item.style.display = "block";
          visibleCount++;
        } else {
          item.style.display = "none";
        }
      });

      if (noResults) {
        noResults.style.display = visibleCount === 0 ? "block" : "none";
      }

      if (clearBtn) {
        clearBtn.style.display = q ? "flex" : "none";
      }
    }

    if (searchInput) {
      searchInput.addEventListener("input", (e) => {
        searchQuery = e.target.value;
        filterFaq();
      });
    }

    if (clearBtn) {
      clearBtn.addEventListener("click", () => {
        if (searchInput) searchInput.value = "";
        searchQuery = "";
        filterFaq();
        if (searchInput) searchInput.focus();
      });
    }

    chips.forEach((chip) => {
      chip.addEventListener("click", () => {
        chips.forEach((c) => {
          c.classList.remove("active");
          c.setAttribute("aria-selected", "false");
        });
        chip.classList.add("active");
        chip.setAttribute("aria-selected", "true");
        currentCategory = chip.getAttribute("data-category") || "all";
        filterFaq();
      });
    });

    if (resetFilterBtn) {
      resetFilterBtn.addEventListener("click", () => {
        if (searchInput) searchInput.value = "";
        searchQuery = "";
        currentCategory = "all";
        chips.forEach((c) => {
          const isAll = c.getAttribute("data-category") === "all";
          c.classList.toggle("active", isAll);
          c.setAttribute("aria-selected", isAll ? "true" : "false");
        });
        filterFaq();
      });
    }

    // Accordion Toggle
    faqItems.forEach((item) => {
      const qBtn = item.querySelector(".faq-question");
      if (qBtn) {
        qBtn.addEventListener("click", () => {
          const isActive = item.classList.contains("active");
          faqItems.forEach((i) => i.classList.remove("active"));
          if (!isActive) item.classList.add("active");
        });
      }
    });
  }

  function initLanding() {
    // Detect URL lang param if present
    const urlParams = new URLSearchParams(window.location.search);
    const paramLang = urlParams.get("lang");
    if (paramLang && I18N[paramLang]) {
      currentLang = paramLang;
    }

    applyLanguage(currentLang);

    // Language switcher
    const langSelect = document.getElementById("lang-select");
    if (langSelect) {
      langSelect.addEventListener("change", (e) => {
        applyLanguage(e.target.value);
      });
    }

    // Theme toggle
    const themeBtn = document.getElementById("theme-btn");
    const savedTheme = (typeof localStorage !== "undefined" && localStorage.getItem("udhaar_theme")) || "light";
    document.documentElement.setAttribute("data-theme", savedTheme === "dark" ? "dark" : "light");
    if (themeBtn) themeBtn.textContent = savedTheme === "dark" ? "☀️" : "🌙";
    if (themeBtn) {
      themeBtn.addEventListener("click", () => {
        const cur = document.documentElement.getAttribute("data-theme") || "light";
        const next = cur === "dark" ? "light" : "dark";
        document.documentElement.setAttribute("data-theme", next);
        if (typeof localStorage !== "undefined") {
          localStorage.setItem("udhaar_theme", next);
        }
        themeBtn.textContent = next === "dark" ? "☀️" : "🌙";
      });
    }

    // Try Demo Button
    const tryDemoBtn = document.getElementById("btn-try-demo");
    if (tryDemoBtn) {
      tryDemoBtn.addEventListener("click", (e) => {
        e.preventDefault();
        window.location.href = "/u/?demo=true";
      });
    }

    initHeaderAndBottomBar();
    initMobileDrawer();
    initStepperObserver();
    initDemoWidget();
    initCalculator();
    initFaqSearchAndFilter();
    initScrollReveal();
    updateJsonLd();
  }

  /**
   * Structured Data Sync (JSON-LD) with SITE_CONFIG (Part 4 Item 3)
   */
  function updateJsonLd() {
    const script = document.querySelector('script[type="application/ld+json"]');
    if (!script || typeof window === "undefined" || !window.SITE_CONFIG) return;
    try {
      const data = JSON.parse(script.textContent);
      const isLive = Boolean(window.SITE_CONFIG.PLAY_STORE_LIVE);
      data.downloadUrl = isLive ? window.SITE_CONFIG.PLAY_STORE_URL : window.SITE_CONFIG.CLOSED_TESTING_URL;
      script.textContent = JSON.stringify(data, null, 2);
    } catch (_) {}
  }

  /**
   * Scroll Reveal Animation Engine (Part 3 Item 6)
   */
  function initScrollReveal() {
    const revealEls = document.querySelectorAll(".reveal-on-scroll");
    if (revealEls.length === 0) return;

    const prefersReducedMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (prefersReducedMotion || !("IntersectionObserver" in window)) {
      revealEls.forEach((el) => el.classList.add("revealed"));
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("revealed");
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.15 }
    );

    revealEls.forEach((el) => observer.observe(el));
  }

  if (typeof module !== "undefined" && module.exports) {
    module.exports = { I18N, t, applyLanguage };
  } else {
    window.UdhaarLanding = { I18N, t, applyLanguage };
    document.addEventListener("DOMContentLoaded", initLanding);
  }
})();
