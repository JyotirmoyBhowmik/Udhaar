/**
 * Udhaar Privacy & Data Deletion Script (web/privacy.js)
 * Strict CSP compliant (zero inline scripts, zero inline styles)
 */
(function() {
  const PRIVACY_I18N = {
    en: {
      badge: "Privacy & Data Safety",
      title: "Privacy Policy & Data Deletion",
      short_title: "In short: Your records stay with you",
      short_desc: "Udhaar is designed to keep your financial records locked on your own phone. We do not sell your personal data, track your location, or read your messages. Web confirmation links expire automatically and can be revoked or deleted at any time with a single tap.",
      table_heading: "What is stored, where, and how to delete it",
      form_heading: "Delete my data request",
      form_desc: "If you opened or confirmed a link on the web and want your confirmation, date proposal, or payment record removed from our cloud servers, submit your request here. Requests are processed within 48 hours.",
      btn_submit: "Send deletion request",
      back_home: "Back to Home"
    },
    hi: {
      badge: "सुरक्षा और गोपनीयता",
      title: "गोपनीयता नीति और डेटा हटाना",
      short_title: "संक्षेप में: आपका हिसाब केवल आपके पास रहता है",
      short_desc: "उधार ऐप आपके सभी लेन-देन का हिसाब केवल आपके फोन में सुरक्षित रखता है। हम आपका डेटा कभी किसी को नहीं बेचते, न ही आपकी लोकेशन या संदेश देखते हैं। साझा किए गए लिंक अपने आप समाप्त हो जाते हैं और कभी भी हटाए जा सकते हैं।",
      table_heading: "हम क्या रखते हैं, कहाँ रहता है और इसे कैसे हटाएं",
      form_heading: "डेटा हटाने का अनुरोध",
      form_desc: "यदि आपने वेब लिंक पर पुष्टि की थी और क्लाउड सर्वर से अपना रिकॉर्ड हटाना चाहते हैं, तो यहाँ अनुरोध भेजें। 48 घंटों में कार्रवाई की जाती है।",
      btn_submit: "हटाने का अनुरोध भेजें",
      back_home: "मुख्य पृष्ठ पर जाएं"
    },
    bn: {
      badge: "নিরাপত্তা ও গোপনীয়তা",
      title: "গোপনীয়তা নীতি ও তথ্য মোছা",
      short_title: "সংক্ষেপে: আপনার হিসেব শুধুই আপনার কাছে থাকে",
      short_desc: "উধার আপনার সমস্ত আর্থিক তথ্য আপনার ফোনেই সুরক্ষিত রাখে। আমরা কোনো তথ্য বিক্রি করি না, লোকেশন ট্র্যাক করি না। ওয়েব লিংকগুলো নিজে থেকেই শেষ হয়ে যায় এবং যেকোনো সময় মুছে ফেলা যায়।",
      table_heading: "কী রাখা হয়, কোথায় থাকে এবং কীভাবে মুছবেন",
      form_heading: "তথ্য মুছে ফেলার অনুরোধ",
      form_desc: "আপনি যদি ওয়েব লিংকে নিশ্চিত করে থাকেন এবং ক্লাউড সার্ভার থেকে তথ্য মুছতে চান, তবে এখান থেকে অনুরোধ পাঠান। ৪৮ ঘণ্টার মধ্যে মোছা হবে।",
      btn_submit: "অনুরোধ পাঠান",
      back_home: "হোম পেজে ফিরুন"
    },
    as: {
      badge: "সুৰক্ষা আৰু গোপনীয়তা",
      title: "গোপনীয়তা নীতি আৰু তথ্য মচা",
      short_title: "চমুকৈ: আপোনাৰ তথ্য কেৱল আপোনাৰ ওচৰতেই থাকে",
      short_desc: "উধাৰ এপে আপোনাৰ লেনদেনৰ তথ্য কেৱল আপোনাৰ ফোনতেই সুৰক্ষিত কৰি ৰাখে। আমি তথ্য বিক্ৰী নকৰোঁ, লোকেশ্বন ট্ৰেক নকৰোঁ। ৱেব লিংকসমূহ নিৰ্দিষ্ট সময়ত নিজে মচি যায়।",
      table_heading: "কি ৰখা হয়, ক'ত থাকে আৰু কেনেদৰে মচিব",
      form_heading: "তথ্য মচাৰ অনুৰোধ",
      form_desc: "যদি আপুনি ৱেব লিংক নিশ্চিত কৰিছিল আৰু ক্লাউড চাৰ্ভাৰৰ পৰা তথ্য মচিব বিচাৰে, তেন্তে ইয়াত অনুৰোধ জনাওক। ৪৮ ঘণ্টাত মচি দিয়া হ'ব।",
      btn_submit: "অনুৰোধ পঠিয়াওক",
      back_home: "মূল পৃষ্ঠালৈ উভতি যাওক"
    },
    ne: {
      badge: "सुरक्षा र गोपनीयता",
      title: "गोपनीयता नीति र डाटा मेटाउने",
      short_title: "संक्षेपमा: तपाईंको हिसाब तपाईंसँगै रहन्छ",
      short_desc: "उधार एपले तपाईंको सबै हिसाब तपाईंको आफ्नै फोनमा सुरक्षित राख्छ। हामी डाटा बेच्दैनौं, लोकेशन ट्र्याक गर्दैनौं। वेब लिङ्कहरू निश्चित समयपछि आफैं मेटिन्छन्।",
      table_heading: "के राखिन्छ, कहाँ रहन्छ र कसरी मेटाउने",
      form_heading: "डाटा मेटाउने अनुरोध",
      form_desc: "यदि तपाईंले वेब लिङ्कमा पुष्टि गर्नुभएको थियो र क्लाउड सर्भरबाट डाटा हटाउन चाहनुहुन्छ भने यहाँ अनुरोध पठाउनुहोस्। ४८ घण्टाभित्र हटाइनेछ।",
      btn_submit: "अनुरोध पठाउनुहोस्",
      back_home: "गृहपृष्ठमा फर्कनुहोस्"
    }
  };

  const select = document.getElementById("privacy-lang-select");
  let curLang = localStorage.getItem("udhaar_lang") || "en";
  if (!PRIVACY_I18N[curLang]) curLang = "en";

  function applyPrivacyLang(l) {
    if (!PRIVACY_I18N[l]) l = "en";
    curLang = l;
    localStorage.setItem("udhaar_lang", l);
    document.documentElement.lang = l;
    if (select) select.value = l;

    const d = PRIVACY_I18N[l];
    const badgeEl = document.getElementById("privacy-badge");
    if (badgeEl) badgeEl.textContent = d.badge;
    const titleEl = document.getElementById("privacy-title");
    if (titleEl) titleEl.textContent = d.title;
    const shortTitleEl = document.getElementById("short-title");
    if (shortTitleEl) shortTitleEl.textContent = d.short_title;
    const shortDescEl = document.getElementById("short-desc");
    if (shortDescEl) shortDescEl.textContent = d.short_desc;
    const tableHeadingEl = document.getElementById("table-heading");
    if (tableHeadingEl) tableHeadingEl.textContent = d.table_heading;
    const formHeadingEl = document.getElementById("form-heading");
    if (formHeadingEl) formHeadingEl.textContent = d.form_heading;
    const formDescEl = document.getElementById("form-desc");
    if (formDescEl) formDescEl.textContent = d.form_desc;
    const btnSubmitEl = document.getElementById("btn-submit-del");
    if (btnSubmitEl) btnSubmitEl.textContent = d.btn_submit;
    const txtBackHomeEl = document.getElementById("txt-back-home");
    if (txtBackHomeEl) txtBackHomeEl.textContent = d.back_home;
  }

  applyPrivacyLang(curLang);
  if (select) {
    select.addEventListener("change", (e) => applyPrivacyLang(e.target.value));
  }

  // Theme toggle
  const themeBtn = document.getElementById("theme-btn");
  const savedTheme = localStorage.getItem("udhaar_theme") || "system";
  if (savedTheme !== "system") {
    document.documentElement.setAttribute("data-theme", savedTheme);
  }
  if (themeBtn) {
    themeBtn.addEventListener("click", () => {
      const cur = document.documentElement.getAttribute("data-theme") || "light";
      const next = cur === "dark" ? "light" : "dark";
      document.documentElement.setAttribute("data-theme", next);
      localStorage.setItem("udhaar_theme", next);
      themeBtn.textContent = next === "dark" ? "☀️" : "🌙";
    });
  }

  // Handle delete form submit
  const form = document.getElementById("delete-request-form");
  const alertBox = document.getElementById("delete-success-alert");
  if (form) {
    form.addEventListener("submit", (e) => {
      e.preventDefault();
      const name = document.getElementById("del-name") ? document.getElementById("del-name").value : "";
      const link = document.getElementById("del-link") ? document.getElementById("del-link").value : "";
      const reason = document.getElementById("del-reason") ? document.getElementById("del-reason").value : "";

      const mailtoUrl = `mailto:admin@jyotirmoyb.com?subject=Udhaar%20Cloud%20Data%20Deletion%20Request&body=Hello%20Admin,%0D%0A%0D%0APlease%20delete%20my%20cloud%20data%20from%20Udhaar.%0D%0A%0D%0AUser:%20${encodeURIComponent(name)}%0D%0ARecord%20Link:%20${encodeURIComponent(link)}%0D%0AReason:%20${encodeURIComponent(reason)}%0D%0A%0D%0AThank%20you.`;
      window.location.href = mailtoUrl;
      if (alertBox) {
        alertBox.classList.remove("hidden");
      }
    });
  }
})();
