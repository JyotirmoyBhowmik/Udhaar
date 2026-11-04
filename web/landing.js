/**
 * Udhaar Landing Page JavaScript
 * Full 5-Language Dictionary, Theme Switcher, FAQ Accordion, and Interactive Demo Routing.
 */

(function () {
  "use strict";

  const I18N = {
    en: {
      meta_title: "Udhaar — Keep clear records of money you lend and borrow",
      meta_desc: "No more forgetting. No more arguments. Private offline lending and borrowing records with zero-cost WhatsApp confirmation.",
      brand_name: "UDHAAR",
      nav_how_it_works: "How it works",
      nav_benefits: "Benefits",
      nav_trust: "Trust & Privacy",
      nav_faq: "FAQ",
      nav_privacy: "Privacy Policy",
      btn_get_play: "Get on Google Play",
      btn_try_demo: "See how the confirmation page looks",
      hero_pill: "Free • Private • Works Offline",
      hero_title: "Keep clear records of money you lend and borrow",
      hero_promise: "No more forgetting. No more arguments.",
      hero_desc: "Record personal lending and borrowing in seconds. Send a private link on WhatsApp so both people agree on the amount and date. Works 100% offline and stays locked on your phone.",
      
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

      // FAQ
      faq_badge: "Got questions?",
      faq_title: "Frequently asked questions",
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

      // Demo CTA Box
      demo_box_title: "Experience the guest flow without installing",
      demo_box_desc: "See exactly what the other person experiences when they tap your WhatsApp link. Test confirming, installments, and payment proof.",
      btn_launch_demo: "Try Interactive Demo",

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
      nav_benefits: "फायदे",
      nav_trust: "सुरक्षा और गोपनीयता",
      nav_faq: "अक्सर पूछे जाने वाले सवाल",
      nav_privacy: "गोपनीयता नीति",
      btn_get_play: "गूगल प्ले से डाउनलोड करें",
      btn_try_demo: "देखें पुष्टि पेज कैसा दिखता है",
      hero_pill: "मुफ़्त • सुरक्षित • बिना इंटरनेट काम करे",
      hero_title: "उधार दिए और लिए गए पैसों का साफ हिसाब रखें",
      hero_promise: "न भूलने का डर, न पैसों की बहस।",
      hero_desc: "उधार के लेन-देन को कुछ ही सेकंड में दर्ज करें। व्हाट्सएप पर एक निजी लिंक भेजें ताकि दोनों लोग रकम और तारीख पर सहमत रहें। पूरी तरह आपके फोन पर सुरक्षित।",

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
      benefit4_desc: "किस्त की सही रकम का UPI QR कोड बनाएं या सीधे GPay, PhonePe से भुगतान करें।",
      benefit5_title: "अपने गूगल ड्राइव पर बैकअप",
      benefit5_desc: "अपने खाते का बैकअप अपने गूगल ड्राइव में रखें, अपने पासवर्ड से पूरी तरह सुरक्षित।",
      benefit6_title: "5 भाषाओं में उपलब्ध",
      benefit6_desc: "हिंदी, बंगाली, असमिया, नेपाली और अंग्रेजी में रोजमर्रा की आसान भाषा में उपलब्ध।",

      trust_badge: "आपका डेटा केवल आपका है",
      trust_title: "हम क्या सुरक्षित रखते हैं और क्या नहीं",
      trust_subtitle: "सच्ची और ईमानदार गोपनीयता। कोई ट्रैकर नहीं, कोई डेटा बेचना नहीं।",
      th_data: "जानकारी",
      th_where: "यह कहाँ रहती है",
      th_status: "गोपनीयता की स्थिति",
      row1_data: "आपकी कॉन्टैक्ट बुक व फोन नंबर",
      row1_where: "हम कभी नहीं पढ़ते या छूते",
      row1_status: "शून्य पहुंच",
      row2_data: "बैंक खाता व UPI पिन",
      row2_where: "कभी भी नहीं मांगा जाता",
      row2_status: "शून्य पहुंच",
      row3_data: "लेन-देन का पूरा हिसाब, नोट व रसीदें",
      row3_where: "केवल आपके अपने फोन में",
      row3_status: "पूरी तरह निजी",
      row4_data: "साझा किया गया पुष्टि लिंक",
      row4_where: "अस्थायी लिंक (15 मिनट / 7 दिन में समाप्त)",
      row4_status: "रास्ते में सुरक्षित",
      row5_data: "बैकअप फाइल",
      row5_where: "आपका निजी गूगल ड्राइव",
      row5_status: "पासवर्ड से सुरक्षित",

      honest_money_title: "उधार ऐप पैसे का लेन-देन नहीं करता",
      honest_money_desc: "हम एक डिजिटल डायरी हैं, कोई बैंक या वॉलेट नहीं। सारा भुगतान आपके अपने UPI ऐप से होता है।",
      honest_legal_title: "उधार कोई कानूनी अनुबंध नहीं है",
      honest_legal_desc: "यह आपसी भरोसे और स्पष्टता के लिए है, किसी कानूनी इकरारनामे का विकल्प नहीं।",

      faq_badge: "कोई सवाल है?",
      faq_title: "अक्सर पूछे जाने वाले सवाल",
      faq1_q: "क्या उधार ऐप पूरी तरह मुफ़्त है?",
      faq1_a: "हाँ, यह 100% मुफ़्त है। इसमें कोई विज्ञापन, कोई मासिक शुल्क या छुपा हुआ चार्ज नहीं है।",
      faq2_q: "क्या ऐप का उपयोग करने के लिए फोन नंबर देना होगा?",
      faq2_a: "नहीं। आप बिना कोई फोन नंबर दर्ज किए या अकाउंट बनाए पूरी तरह अज्ञात रहकर ऐप चला सकते हैं।",
      faq3_q: "अगर फोन खो जाए या बदल जाए तो क्या होगा?",
      faq3_a: "यदि आपने गूगल ड्राइव में बैकअप लिया था, तो नए फोन में अपने पासवर्ड से सारा डेटा वापस पा सकते हैं।",
      faq4_q: "क्या दूसरा व्यक्ति मेरा पूरा खाता देख सकता है?",
      faq4_a: "नहीं। लिंक खोलने पर वह व्यक्ति केवल वही एक लेन-देन देख सकता है जो आपने उसके साथ साझा किया है।",
      faq5_q: "मैं अपना डेटा कैसे हटा सकता हूँ?",
      faq5_a: "आप ऐप में किसी भी समय कोई भी रिकॉर्ड हटा सकते हैं। वेब लिंक हटाने के लिए हमारे डिलीट फॉर्म का उपयोग करें।",

      demo_box_title: "बिना इंस्टॉल किए खुद अनुभव करके देखें",
      demo_box_desc: "देखें कि जब आप लिंक भेजते हैं तो दूसरे व्यक्ति को कैसा दिखता है। पुष्टि और भुगतान रसीद का लाइव डेमो आज़माएं।",
      btn_launch_demo: "डेमो आज़माएं",

      footer_desc: "लेन-देन का सरल, सच्चा और निजी हिसाब। भारत और नेपाल के आम लोगों के लिए विशेष रूप से निर्मित।",
      footer_product: "उत्पाद",
      footer_legal: "कानूनी",
      footer_rights: "© 2026 उधार। सर्वाधिकार सुरक्षित। ओपन-सोर्स और गोपनीयता केंद्रित।"
    },

    bn: {
      meta_title: "উধার — কাউকে ধার দেওয়া ও নেওয়ার পরিষ্কার হিসেব রাখুন",
      meta_desc: "ভুলে যাওয়ার ভয় নেই, কোনো মতভেদও নেই। সম্পূর্ণ নিরাপদ ও অফলাইন খাতা, বিনামূল্যে হোয়াটসঅ্যাপ ভেরিফিকেশন সহ।",
      brand_name: "UDHAAR",
      nav_how_it_works: "কীভাবে কাজ করে",
      nav_benefits: "সুবিধাসমূহ",
      nav_trust: "নিরাপত্তা ও গোপনীয়তা",
      nav_faq: "প্রশ্নোত্তর",
      nav_privacy: "গোপনীয়তা নীতি",
      btn_get_play: "গুগল প্লে থেকে ডাউনলোড করুন",
      btn_try_demo: "কনফার্মেশন পেজটি কেমন দেখায় দেখুন",
      hero_pill: "বিনামূল্যে • সম্পূর্ণ ব্যক্তিগত • অফলাইনে কাজ করে",
      hero_title: "কাউকে ধার দেওয়া ও নেওয়ার পরিষ্কার হিসেব রাখুন",
      hero_promise: "ভুলে যাওয়ার ভয় নেই, কোনো মতভেদও নেই।",
      hero_desc: "লেনদেনের হিসেব রাখুন কয়েক সেকেন্ডে। হোয়াটসঅ্যাপে একটি ব্যক্তিগত লিংক পাঠান যাতে উভয় পক্ষই পরিমাণ ও তারিখে একমত থাকেন। সম্পূর্ণ অফলাইন ও সুরক্ষিত।",

      steps_badge: "সহজ ৩টি ধাপ",
      steps_title: "সহজ ৩টি ধাপে কীভাবে কাজ করে",
      steps_subtitle: "কোনো ভুল বোঝাবুঝি নেই। দুই পক্ষের কাছেই থাকবে একদম স্পষ্ট হিসেব।",
      step1_num: "১",
      step1_title: "হিসেব যোগ করুন",
      step1_desc: "টাকার পরিমাণ, ফেরত দেওয়ার তারিখ এবং অপর ব্যক্তির নাম লিখুন। ১০ সেকেন্ডের কম সময় লাগে।",
      step2_num: "২",
      step2_title: "হোয়াটসঅ্যাপে লিংক পাঠান",
      step2_desc: "অ্যাপ একটি বিনীত বার্তা তৈরি করে যাতে একটি প্রাইভেট লিংক ও ৬ সংখ্যার কোড থাকে।",
      step3_num: "৩",
      step3_title: "অপর ব্যক্তি নিশ্চিত করেন",
      step3_desc: "তারা কোনো অ্যাপ ইনস্টল না করেই ফোনের ব্রাউজারে লিংক খুলে এক ট্যাপে হিসেব নিশ্চিত করতে পারেন।",

      benefits_badge: "বাস্তব জীবনের জন্য",
      benefits_title: "সহজ ভাষায় সুবিধাগুলো",
      benefits_subtitle: "লেনদেন পরিষ্কার ও সম্পর্ক সুন্দর রাখার জন্য প্রয়োজনীয় সবকিছু।",
      benefit1_title: "আপনার ফোনেই সুরক্ষিত",
      benefit1_desc: "আপনার সমস্ত তথ্য শুধুমাত্র আপনার ফোনেই সুরক্ষিত থাকে। অন্য কেউ তা দেখতে পারে না।",
      benefit2_title: "ইন্টারনেট ছাড়াও কাজ করে",
      benefit2_desc: "নেটওয়ার্ক না থাকলেও যে কোনো সময় হিসেব লিখুন ও পুরোনো হিসেব দেখুন।",
      benefit3_title: "মার্জিত তাগাদা বার্তা",
      benefit3_desc: "ভদ্র ও মার্জিত রিমাইন্ডার যা টাকা ফেরত পাওয়ার সাথে সাথে বন্ধুত্বও অটুট রাখে।",
      benefit4_title: "UPI দিয়ে সহজে পেমেন্ট",
      benefit4_desc: "সঠিক কিস্তির জন্য UPI QR কোড তৈরি করুন অথবা সরাসরি GPay, PhonePe দিয়ে পরিশোধ করুন।",
      benefit5_title: "নিজের গুগল ড্রাইভে ব্যাকআপ",
      benefit5_desc: "আপনার গুগল ড্রাইভে ব্যাকআপ রাখুন এবং নিজের গোপন পাসওয়ার্ড দিয়ে সুরক্ষিত রাখুন।",
      benefit6_title: "৫টি ভাষায় উপলব্ধ",
      benefit6_desc: "বাংলা, অসমীয়া, হিন্দি, নেপালি ও ইংরেজিতে সহজ কথ্য ভাষায় তৈরি।",

      trust_badge: "আপনার তথ্য সম্পূর্ণ আপনার",
      trust_title: "আমরা কী রাখি আর কী রাখি না",
      trust_subtitle: "স্বচ্ছ গোপনীয়তা। কোনো ট্র্যাকার নেই, কোনো তথ্য বিক্রি করা হয় না।",
      th_data: "তথ্য",
      th_where: "কোথায় থাকে",
      th_status: "সুরক্ষার অবস্থা",
      row1_data: "আপনার ফোনবুক ও পরিচিতি নম্বর",
      row1_where: "কখনোই পড়া বা সংগ্রহ করা হয় না",
      row1_status: "কোনো অ্যাক্সেস নেই",
      row2_data: "ব্যাংক অ্যাকাউন্ট ও UPI পিন",
      row2_where: "কখনোই চাওয়া হয় না",
      row2_status: "কোনো অ্যাক্সেস নেই",
      row3_data: "লেনদেনের হিসেব, নোট ও রসিদ",
      row3_where: "শুধুমাত্র আপনার ফোনে সংরক্ষিত",
      row3_status: "সম্পূর্ণ ব্যক্তিগত",
      row4_data: "শেয়ার করা কনফার্মেশন লিংক",
      row4_where: "সাময়িক লিংক (১৫ মিনিট / ৭ দিনে মেয়াদ শেষ)",
      row4_status: "সুরক্ষিত এনক্রিপশন",
      row5_data: "ব্যাকআপ ফাইল",
      row5_where: "আপনার ব্যক্তিগত গুগল ড্রাইভ",
      row5_status: "পাসওয়ার্ডে সুরক্ষিত",

      honest_money_title: "উধার কোনো টাকা আদান-প্রদান করে না",
      honest_money_desc: "আমরা একটি ডিজিটাল ডায়রি, কোনো ব্যাংক বা ওয়ালেট নই। টাকা লেনদেন আপনার UPI বা ব্যাংকের মাধ্যমেই হয়।",
      honest_legal_title: "উধার কোনো আইনি চুক্তি নয়",
      honest_legal_desc: "এটি পরস্পরের স্পষ্টতা ও বিশ্বাসের জন্য, কোনো আনুষ্ঠানিক আইনি দলিলের বিকল্প নয়।",

      faq_badge: "কোনো প্রশ্ন আছে?",
      faq_title: "সাধারণ প্রশ্নোত্তর",
      faq1_q: "উধার কি সম্পূর্ণ বিনামূল্যে ব্যবহার করা যায়?",
      faq1_a: "হ্যাঁ, এটি ১০০% ফ্রি। এতে কোনো বিজ্ঞাপন নেই, কোনো মাসিক ফি বা লুকানো খরচ নেই।",
      faq2_q: "অ্যাপটি ব্যবহার করতে কি ফোন নম্বর দিতে হবে?",
      faq2_a: "না। আপনি কোনো নম্বর না দিয়ে বা কোনো অ্যাকাউন্ট না খুলেই সম্পূর্ণ বেনামে অ্যাপটি ব্যবহার করতে পারেন।",
      faq3_q: "ফোন হারিয়ে গেলে বা বদলে গেলে কী হবে?",
      faq3_a: "যদি আপনি গুগল ড্রাইভে ব্যাকআপ রেখে থাকেন, তবে নতুন ফোনে পাসওয়ার্ড দিয়ে সব তথ্য ফিরিয়ে আনতে পারবেন।",
      faq4_q: "অপর ব্যক্তি কি আমার খাতার সব হিসেব দেখতে পাবে?",
      faq4_a: "না। আপনি যে নির্দিষ্ট হিসেবের লিংক শেয়ার করবেন, তিনি কেবল সেই হিসেবটুকুই দেখতে পাবেন।",
      faq5_q: "আমি কীভাবে আমার তথ্য ডিলিট করব?",
      faq5_a: "অ্যাপের ভেতর থেকেই যেকোনো হিসেব মুছতে পারেন। শেয়ার করা ওয়েব লিংক মুছতে আমাদের ডিলিট ফর্ম ব্যবহার করুন।",

      demo_box_title: "অ্যাপ ছাড়া কেমন দেখায় নিজে পরখ করে দেখুন",
      demo_box_desc: "আপনার পাঠানো লিংকে ক্লিক করলে অন্যজন কী দেখতে পাবেন তা কোনো ইনস্টল ছাড়াই দেখুন।",
      btn_launch_demo: "ডেমো পরীক্ষা করুন",

      footer_desc: "লেনদেনের সহজ, সৎ ও ব্যক্তিগত ডিজিটাল হিসেব। ভারত ও নেপালের সাধারণ মানুষের জন্য বিশেষভাবে নির্মিত।",
      footer_product: "প্রোডাক্ট",
      footer_legal: "আইনি",
      footer_rights: "© ২০২৬ উধার। সর্বস্বত্ব সংরক্ষিত। ওপেন-সোর্স ও গোপনীয়তা-বান্ধব।"
    },

    as: {
      meta_title: "উধাৰ — ধাৰে দিয়া আৰু লোৱা ধনৰ স্পষ্ট হিচাপ ৰাখক",
      meta_desc: "পাহৰি যোৱাৰ ভয় নাই, তৰ্ক-বিতৰ্কৰ স্থান নাই। সম্পূৰ্ণ সুৰক্ষিত আৰু অফলাইন খাতা, বিনামূলীয়া হোৱাটছএপ নিশ্চিতকৰণৰ সৈতে।",
      brand_name: "UDHAAR",
      nav_how_it_works: "কেনেদৰে কাম কৰে",
      nav_benefits: "সুবিধাসমূহ",
      nav_trust: "সুৰক্ষা আৰু গোপনীয়তা",
      nav_faq: "সঘনাই সোধা প্ৰশ্ন",
      nav_privacy: "গোপনীয়তা নীতি",
      btn_get_play: "গুগল প্লেৰ পৰা ডাউনলোড কৰক",
      btn_try_demo: "নিশ্চিতকৰণ পৃষ্ঠা কেনেকুৱা দেখায় চাওক",
      hero_pill: "বিনামূলীয়া • সম্পূৰ্ণ ব্যক্তিগত • অফলাইনত কাম কৰে",
      hero_title: "ধাৰে দিয়া আৰু লোৱা ধনৰ স্পষ্ট হিচাপ ৰাখক",
      hero_promise: "পাহৰি যোৱাৰ ভয় নাই, তৰ্ক-বিতৰ্কৰ স্থান নাই।",
      hero_desc: "লেনদেনৰ হিচাপ ৰাখক মাত্ৰ কেইছেকেণ্ডত। হোৱাটছএপত এটা লিংক পঠিয়াওক যাতে দুয়োপক্ষই ধনৰ পৰিমাণ আৰু তাৰিখত সন্মত থাকে। সম্পূৰ্ণ অফলাইন আৰু আপোনাৰ ফোনত সুৰক্ষিত।",

      steps_badge: "সহজ ৩টা পদক্ষেপ",
      steps_title: "৩টা সহজ পদক্ষেপত কেনেদৰে কাম কৰে",
      steps_subtitle: "কোনো ভুল বুজাবুজি নাই। দুয়োপক্ষৰ ওচৰতেই থাকিব স্পষ্ট হিচাপ।",
      step1_num: "১",
      step1_title: "হিচাপ যোগ কৰক",
      step1_desc: "টকাৰ পৰিমাণ, ঘূৰাই দিয়াৰ তাৰিখ আৰু আনজন ব্যক্তিৰ নাম লিখক। ১০ ছেকেণ্ডতকৈও কম সময় লাগে।",
      step2_num: "২",
      step2_title: "হোৱাটছএপত লিংক পঠিয়াওক",
      step2_desc: "এপে এটা বিনম্ৰ বাৰ্তা প্ৰস্তুত কৰে য'ত এটা ব্যক্তিগত লিংক আৰু ৬টা সংখ্যাৰ ক'ড থাকে।",
      step3_num: "৩",
      step3_title: "আনজনে নিশ্চিত কৰে",
      step3_desc: "তেওঁলোকে কোনো এপ ইনষ্টল নকৰাকৈ ফোনৰ ব্ৰাউজাৰত লিংক খুলি মুহূৰ্ততে নিশ্চিত কৰিব পাৰে।",

      benefits_badge: "বাস্তৱ জীৱনৰ বাবে",
      benefits_title: "সহজ ভাষাত সুবিধাসমূহ",
      benefits_subtitle: "লেনদেন স্পষ্ট আৰু বন্ধুত্ব অটুট ৰখাৰ সকলো সুবিধা।",
      benefit1_title: "আপোনাৰ ফোনত সুৰক্ষিত",
      benefit1_desc: "আপোনাৰ সকলো তথ্য কেৱল আপোনাৰ নিজৰ ফোনতেই সুৰক্ষিত থাকে। আন কোনেও পঢ়িব নোৱাৰে।",
      benefit2_title: "ইণ্টাৰনেট অবিহনে কাম কৰে",
      benefit2_desc: "ইণ্টাৰনেট নাথাকিলেও যেতিয়াই মন যায় নতুন হিচাপ লিখক আৰু পুৰণি হিচাপ পৰীক্ষা কৰক।",
      benefit3_title: "মাৰ্জিত স্মাৰক বাৰ্তা",
      benefit3_desc: "ভদ্ৰ আৰু সন্মানজনক স্মাৰক বাৰ্তা যিয়ে ধন ঘূৰাই পোৱাৰ লগতে সম্পৰ্কও সুৰক্ষিত ৰাখে।",
      benefit4_title: "UPI ৰে সহজ পৰিশোধ",
      benefit4_desc: "কিস্তিৰ সঠিক ধনৰ পৰিমাণৰ UPI QR ক'ড তৈয়াৰ কৰক বা পোনপটীয়াকৈ পৰিশোধ কৰক।",
      benefit5_title: "গুগল ড্ৰাইভত বেকআপ",
      benefit5_desc: "আপোনাৰ গুগল ড্ৰাইভত বেকআপ ৰাখক আৰু নিজৰ পাছৱৰ্ডেৰে সুৰক্ষিত কৰি থওক।",
      benefit6_title: "৫টা ভাষাত উপলব্ধ",
      benefit6_desc: "অসমীয়া, বঙালী, হিন্দী, নেপালী আৰু ইংৰাজী ভাষাত সহজ শব্দত উপলব্ধ।",

      trust_badge: "আপোনাৰ তথ্য আপোনাৰ নিজস্ব",
      trust_title: "আমি কি সংৰক্ষণ কৰোঁ আৰু কি নকৰোঁ",
      trust_subtitle: "সঁচা গোপনীয়তা। কোনো ট্ৰেকাৰ নাই, কোনো তথ্য বিক্ৰী কৰা নহয়।",
      th_data: "তথ্য",
      th_where: "ক'ত থাকে",
      th_status: "সুৰক্ষাৰ স্থিতি",
      row1_data: "আপোনাৰ ফোনবুক আৰু ফোন নম্বৰ",
      row1_where: "কেতিয়াও চোৱা বা সংগ্ৰহ কৰা নহয়",
      row1_status: "কোনো প্ৰৱেশাধিকাৰ নাই",
      row2_data: "বেংক একাউণ্ট আৰু UPI পিন",
      row2_where: "কেতিয়াও বিচৰা নহয়",
      row2_status: "কোনো প্ৰৱেশাধিকাৰ নাই",
      row3_data: "লেনদেনৰ হিচাপ, টোকা আৰু ৰচিদ",
      row3_where: "কেৱল আপোনাৰ নিজৰ ফোনত",
      row3_status: "সম্পূৰ্ণ ব্যক্তিগত",
      row4_data: "শ্বেয়াৰ কৰা নিশ্চিতকৰণ লিংক",
      row4_where: "অস্থায়ী লিংক (১৫ মিনিট / ৭ দিনত ম্যাদ উকলিব)",
      row4_status: "পথত সুৰক্ষিত",
      row5_data: "বেকআপ ফাইল",
      row5_where: "আপোনাৰ ব্যক্তিগত গুগল ড্ৰাইভ",
      row5_status: "পাছৱৰ্ডেৰে সুৰক্ষিত",

      honest_money_title: "উধাৰ এপে ধনৰ লেনদেন নকৰে",
      honest_money_desc: "আমি এটা ডিজিটেল বহী, কোনো বেংক বা ৱালেট নহয়। ধন পৰিশোধ আপোনাৰ নিজৰ UPI এপেৰে হয়।",
      honest_legal_title: "উধাৰ কোনো আইনী চুক্তি নহয়",
      honest_legal_desc: "ই পাৰস্পৰিক বিশ্বাস আৰু স্পষ্টতাৰ বাবেহে, কোনো আইনী চুক্তিৰ বিকল্প নহয়।",

      faq_badge: "কিবা জানিবলগীয়া আছেনে?",
      faq_title: "সঘনাই সোধা প্ৰশ্নসমূহ",
      faq1_q: "উধাৰ এপটো সম্পূৰ্ণ বিনামূলীয়ানে?",
      faq1_a: "হয়, ই ১০০% বিনামূলীয়া। কোনো বিজ্ঞাপন নাই, কোনো মাহেকীয়া মাচুল বা লুকাই থকা খৰচ নাই।",
      faq2_q: "এপটো চলাবলৈ ফোন নম্বৰ দিব লাগিবনে?",
      faq2_a: "নালাগে। আপুনি কোনো ফোন নম্বৰ নিদিয়াকৈ বা একাউণ্ট নোখোলাকৈ সম্পূৰ্ণভাৱে এপটো চলাব পাৰে।",
      faq3_q: "যদি ফোনটো হেৰাই যায় বা সলনি হয় তেন্তে কি হ'ব?",
      faq3_a: "যদি আপুনি গুগল ড্ৰাইভত বেকআপ ৰাখিছে, নতুন ফোনত নিজৰ পাছৱৰ্ড দি সকলো তথ্য ঘূৰাই পাব পাৰিব।",
      faq4_q: "আনজনে কি মোৰ খাতাৰ সকলো হিচাপ চাব পাৰিবনে?",
      faq4_a: "নোৱাৰে। আপুনি যিটো নিৰ্দিষ্ট লেনদেনৰ লিংক শ্বেয়াৰ কৰিব, তেওঁ কেৱল সেইটো লেনদেনহে দেখিব।",
      faq5_q: "মই মোৰ তথ্য কেনেকৈ মচি পেলাম?",
      faq5_a: "এপৰ ভিতৰৰ পৰাই যিকোনো সময়ত হিচাপ মচিব পাৰি। ৱেব লিংক মচিবলৈ আমাৰ ডিলিট ফৰ্ম ব্যৱহাৰ কৰক।",

      demo_box_title: "এপ অবিহনে কেনেকুৱা দেখায় নিজেই পৰীক্ষা কৰক",
      demo_box_desc: "আপুনি লিংক পঠিয়ালে আনজনে কেনেকৈ দেখে সেয়া কোনো ইনষ্টল নকৰাকৈয়ে পৰীক্ষা কৰি চাওক।",
      btn_launch_demo: "ডেমো পৰীক্ষা কৰক",

      footer_desc: "লেনদেনৰ সহজ, স্পষ্ট আৰু ব্যক্তিগত ডিজিটেল হিচাপ। ভাৰত আৰু নেপালৰ সাধাৰণ ৰাইজৰ বাবে বিশেষভাৱে নিৰ্মিত।",
      footer_product: "পণ্য",
      footer_legal: "আইনী",
      footer_rights: "© ২০২৬ উধাৰ। সৰ্বস্বত্ব সংৰক্ষিত। মুক্ত-উৎস আৰু গোপনীয়তা-কেন্দ্ৰিক।"
    },

    ne: {
      meta_title: "उधार — ऋण दिएको र लिएको पैसाको स्पष्ट हिसाब राख्नुहोस्",
      meta_desc: "बिर्सने डर छैन, कुनै विवाद पनि हुँदैन। पूर्ण रूपमा सुरक्षित र अफलाइन खाता, नि:शुल्क ह्वाट्सएप प्रमाणीकरण सहित।",
      brand_name: "UDHAAR",
      nav_how_it_works: "कसरी काम गर्छ",
      nav_benefits: "फाइदाहरू",
      nav_trust: "सुरक्षा र गोपनीयता",
      nav_faq: "प्रायः सोधिने प्रश्नहरू",
      nav_privacy: "गोपनीयता नीति",
      btn_get_play: "गुगल प्लेबाट डाउनलोड गर्नुहोस्",
      btn_try_demo: "पुष्टि गर्ने पृष्ठ कस्तो देखिन्छ हेर्नुहोस्",
      hero_pill: "नि:शुल्क • पूर्ण निजी • अफलाइनमा चल्छ",
      hero_title: "ऋण दिएको र लिएको पैसाको स्पष्ट हिसाब राख्नुहोस्",
      hero_promise: "बिर्सने डर छैन, कुनै विवाद पनि हुँदैन।",
      hero_desc: "लेनदेनको हिसाब केही सेकेन्डमै राख्नुहोस्। ह्वाट्सएपमा एउटा निजी लिङ्क पठाउनुहोस् ताकि दुवै जना रकम र मितिमा सहमत हुन सकून्। १००% अफलाइन र तपाईंको फोनमै सुरक्षित।",

      steps_badge: "सजिलो ३ चरण",
      steps_title: "३ सजिलो चरणमा कसरी काम गर्छ",
      steps_subtitle: "कुनै भ्रम छैन। दुवै जनासँग एउटै स्पष्ट हिसाब रहनेछ।",
      step1_num: "१",
      step1_title: "हिसाब थप्नुहोस्",
      step1_desc: "रकम, फिर्ता गर्ने मिति र अर्को व्यक्तिको नाम लेख्नुहोस्। १० सेकेन्डभन्दा कम समय लाग्छ।",
      step2_num: "२",
      step2_title: "ह्वाट्सएपमा लिङ्क पठाउनुहोस्",
      step2_desc: "एपले एउटा शिष्ट सन्देश तयार गर्छ जसमा पुष्टि लिङ्क र ६ अंकको कोड हुन्छ।",
      step3_num: "३",
      step3_title: "अर्को व्यक्तिले पुष्टि गर्छ",
      step3_desc: "उनीहरूले कुनै एप इन्स्टल नगरीकनै फोनको ब्राउजरमा लिङ्क खोलेर तुरुन्त पुष्टि गर्न सक्छन्।",

      benefits_badge: "दैनिक जीवनका लागि",
      benefits_title: "सजिलो भाषामा फाइदाहरू",
      benefits_subtitle: "लेनदेन सफा र सम्बन्ध सुमधुर राख्न आवश्यक सबै कुरा।",
      benefit1_title: "तपाईंको फोनमै सुरक्षित",
      benefit1_desc: "तपाईंको सबै हिसाब तपाईंको आफ्नै फोनमा सुरक्षित रहन्छ। अरू कसैले हेर्न सक्दैन।",
      benefit2_title: "इन्टरनेट बिना पनि चल्छ",
      benefit2_desc: "इन्टरनेट नभए पनि जहाँसुकै नयाँ हिसाब थप्नुहोस् र पुरानो खाता हेर्नुहोस्।",
      benefit3_title: "शिष्ट सम्झौता सन्देश",
      benefit3_desc: "पैसा फिर्ता माग्दा सम्बन्ध नबिग्रिने गरी शिष्ट र आदरपूर्ण सम्झौता सन्देशहरू।",
      benefit4_title: "UPI बाट सजिलो भुक्तानी",
      benefit4_desc: "किस्ताको सही रकमको UPI QR कोड बनाउनुहोस् वा सिधै भुक्तानी गर्नुहोस्।",
      benefit5_title: "आफ्नै गुगल ड्राइभमा ब्याकअप",
      benefit5_desc: "आफ्नो गुगल ड्राइभमा ब्याकअप राख्नुहोस् र आफ्नै पासवर्डले सुरक्षित गर्नुहोस्।",
      benefit6_title: "५ भाषाहरूमा उपलब्ध",
      benefit6_desc: "नेपाली, हिन्दी, बंगाली, असमिया र अंग्रेजीमा दैनिक बोलचालको सरल भाषामा उपलब्ध।",

      trust_badge: "तपाईंको डाटा तपाईंको मात्र हो",
      trust_title: "हामी के राख्छौं र के राख्दैनौं",
      trust_subtitle: "इमानदार गोपनीयता। कुनै ट्र्याकर छैन, कुनै डाटा बेच्ने काम हुँदैन।",
      th_data: "जानकारी",
      th_where: "यो कहाँ रहन्छ",
      th_status: "सुरक्षा स्थिति",
      row1_data: "तपाईंको सम्पर्क सूची र फोन नम्बरहरू",
      row1_where: "कहिल्यै हेरिएको वा संकलन गरिएको छैन",
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

      faq_badge: "कुनै प्रश्न छ?",
      faq_title: "प्रायः सोधिने प्रश्नहरू",
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

      demo_box_title: "इन्स्टल नगरीकनै कस्तो देखिन्छ अनुभव गर्नुहोस्",
      demo_box_desc: "तपाईंले लिङ्क पठाउँदा अर्को व्यक्तिले के देख्छन् कुनै एप बिना नै चलाएर हेर्नुहोस्।",
      btn_launch_demo: "डेमो चलाएर हेर्नुहोस्",

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
    const savedTheme = (typeof localStorage !== "undefined" && localStorage.getItem("udhaar_theme")) || "system";
    if (savedTheme !== "system") {
      document.documentElement.setAttribute("data-theme", savedTheme);
    }
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

    // FAQ Accordion
    document.querySelectorAll(".faq-question").forEach((btn) => {
      btn.addEventListener("click", () => {
        const item = btn.closest(".faq-item");
        if (item) {
          const isActive = item.classList.contains("active");
          document.querySelectorAll(".faq-item").forEach((i) => i.classList.remove("active"));
          if (!isActive) item.classList.add("active");
        }
      });
    });

    // Try Demo Button
    const tryDemoBtn = document.getElementById("btn-try-demo");
    if (tryDemoBtn) {
      tryDemoBtn.addEventListener("click", (e) => {
        e.preventDefault();
        window.location.href = "/u/?demo=true";
      });
    }

    const tryDemoHero = document.getElementById("btn-try-demo-hero");
    if (tryDemoHero) {
      tryDemoHero.addEventListener("click", (e) => {
        e.preventDefault();
        window.location.href = "/u/?demo=true";
      });
    }
  }

  if (typeof module !== "undefined" && module.exports) {
    module.exports = { I18N, t, applyLanguage };
  } else {
    window.UdhaarLanding = { I18N, t, applyLanguage };
    document.addEventListener("DOMContentLoaded", initLanding);
  }
})();
