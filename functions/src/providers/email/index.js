/**
 * Transactional Email Provider & Fake Email Provider (functions/src/providers/email/index.js)
 * Implements code-only verification emails without links across 5 languages (EN, HI, BN, AS, NE).
 * Header: from no-reply@jyotirmoyb.com with SPF/DKIM/DMARC compliance.
 */

const crypto = require("crypto");

const EMAIL_TEMPLATES = {
  en: {
    subject: "Your Udhaar Verification Code",
    heading: "Verification Code",
    body: "Your 6-digit verification code for Udhaar is:",
    footer: "This code expires in 10 minutes. If you did not request this, please ignore this email."
  },
  hi: {
    subject: "आपका उधार सत्यापन कोड",
    heading: "सत्यापन कोड",
    body: "उधार के लिए आपका 6 अंकों का सत्यापन कोड है:",
    footer: "यह कोड 10 मिनट में समाप्त हो जाएगा। यदि आपने इसका अनुरोध नहीं किया है, तो कृपया इसे अनदेखा करें।"
  },
  bn: {
    subject: "আপনার উধার যাচাইকরণ কোড",
    heading: "যাচাইকরণ কোড",
    body: "উধার অ্যাকাউন্টের জন্য আপনার ৬-সংখ্যার যাচাইকরণ কোডটি হলো:",
    footer: "এই কোডটি ১০ মিনিটের মধ্যে মেয়াদোত্তীর্ণ হবে। আপনি যদি এটি অনুরোধ না করে থাকেন, তবে উপেক্ষা করুন।"
  },
  as: {
    subject: "আপোনাৰ উধাৰ প্ৰমাণীকৰণ ক'ড",
    heading: "প্ৰমাণীকৰণ ক'ড",
    body: "উধাৰ একাউণ্টৰ বাবে আপোনাৰ ৬-অংকৰ প্ৰমাণীকৰণ ক'ড হ'ল:",
    footer: "এই ক'ডটো ১০ মিনিটৰ ভিতৰত ম্যাদ উকলিব। যদি আপুনি অনুৰোধ কৰা নাই, তেন্তে আওকাণ কৰক।"
  },
  ne: {
    subject: "तपाईंको उधार प्रमाणीकरण कोड",
    heading: "प्रमाणीकरण कोड",
    body: "उधार खाताको लागि तपाईंको ६-अङ्कको प्रमाणीकरण कोड हो:",
    footer: "यो कोड १० मिनेटमा समाप्त हुनेछ। यदि तपाईंले यो अनुरोध गर्नुभएको छैन भने, कृपया बेवास्ता गर्नुहोस्।"
  }
};

class FakeEmailProvider {
  constructor() {
    this.sentEmails = [];
    this.suppressionList = new Set();
  }

  reset() {
    this.sentEmails = [];
    this.suppressionList.clear();
  }

  async sendEmail(request) {
    if (this.suppressionList.has(request.toEmail.toLowerCase())) {
      throw new Error(`Email address ${request.toEmail} is on the suppression list`);
    }

    const messageId = `fake_email_${Date.now()}_${crypto.randomBytes(4).toString("hex")}`;
    const entry = {
      messageId,
      toEmail: request.toEmail,
      subject: request.subject,
      code: request.code,
      textContent: request.textContent,
      htmlContent: request.htmlContent,
      category: request.category,
      timestamp: Date.now()
    };

    this.sentEmails.push(entry);

    return {
      success: true,
      messageId
    };
  }

  getLastCodeFor(email) {
    const norm = email.toLowerCase().trim();
    const matches = this.sentEmails.filter(e => e.toEmail.toLowerCase().trim() === norm);
    return matches.length > 0 ? matches[matches.length - 1].code : null;
  }
}

class TransactionalEmailProvider {
  constructor(config = {}) {
    this.apiKey = config.apiKey || process.env.EMAIL_PROVIDER_API_KEY;
    this.fromAddress = config.fromAddress || "Udhaar <no-reply@jyotirmoyb.com>";
    this.providerEndpoint = config.endpoint || "https://api.resend.com/emails";
  }

  buildContent(code, locale = "en") {
    const t = EMAIL_TEMPLATES[locale] || EMAIL_TEMPLATES.en;
    const textContent = `${t.heading}\n\n${t.body} ${code}\n\n${t.footer}\n\nUdhaar — Local-first financial records.`;
    const htmlContent = `<!DOCTYPE html>
<html>
<head><meta charset="utf-8"></head>
<body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; padding: 24px; color: #1e293b;">
  <div style="max-width: 480px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; padding: 32px; text-align: center;">
    <h2 style="color: #059669; margin-top: 0;">${t.heading}</h2>
    <p style="font-size: 15px; color: #475569;">${t.body}</p>
    <div style="display: inline-block; padding: 14px 28px; background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 8px; font-family: monospace; font-size: 32px; font-weight: 700; letter-spacing: 6px; color: #166534; margin: 16px 0;">
      ${code}
    </div>
    <p style="font-size: 13px; color: #64748b; margin-top: 24px;">${t.footer}</p>
    <hr style="border: none; border-top: 1px solid #f1f5f9; margin: 24px 0;">
    <p style="font-size: 11px; color: #94a3b8;">Udhaar Financial Technologies • No links are ever sent in verification emails.</p>
  </div>
</body>
</html>`;

    return {
      subject: `${code} is your Udhaar code`,
      textContent,
      htmlContent
    };
  }

  async sendEmail(request) {
    if (!this.apiKey) {
      throw new Error("Email provider API key missing (EMAIL_PROVIDER_API_KEY).");
    }

    const { subject, textContent, htmlContent } = this.buildContent(request.code, request.locale);

    const res = await fetch(this.providerEndpoint, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${this.apiKey}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        from: this.fromAddress,
        to: [request.toEmail],
        subject,
        text: textContent,
        html: htmlContent
      })
    });

    const data = await res.json();
    if (!res.ok) {
      throw new Error(`Email provider error (${res.status}): ${JSON.stringify(data)}`);
    }

    return {
      success: true,
      messageId: data.id || `email_${Date.now()}`
    };
  }
}

module.exports = {
  EMAIL_TEMPLATES,
  FakeEmailProvider,
  TransactionalEmailProvider
};
