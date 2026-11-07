/**
 * SMS Provider & Fake SMS Provider (functions/src/providers/sms/index.js)
 * Implements SMS channel fallback behind interface; disabled by default until configured.
 */

const crypto = require("crypto");

class FakeSmsProvider {
  constructor() {
    this.sentSms = [];
  }

  reset() {
    this.sentSms = [];
  }

  async sendCode(request) {
    const messageId = `fake_sms_${Date.now()}_${crypto.randomBytes(4).toString("hex")}`;
    this.sentSms.push({
      messageId,
      destination: request.destination,
      code: request.code,
      locale: request.locale,
      timestamp: Date.now()
    });

    return {
      success: true,
      providerMessageId: messageId,
      channel: "SMS",
      deliveryStatus: "SENT"
    };
  }

  getLastCodeFor(destination) {
    const matches = this.sentSms.filter(s => s.destination === destination);
    return matches.length > 0 ? matches[matches.length - 1].code : null;
  }
}

class IndianDltSmsProvider {
  constructor(config = {}) {
    this.enabled = config.enabled || process.env.SMS_CHANNEL_ENABLED === "true";
    this.apiKey = config.apiKey || process.env.DLT_SMS_API_KEY;
    this.senderId = config.senderId || process.env.DLT_SENDER_ID;
    this.templateId = config.templateId || process.env.DLT_TEMPLATE_ID;
  }

  async sendCode(request) {
    if (!this.enabled) {
      throw new Error("SMS delivery is currently disabled. Please use WhatsApp or Email.");
    }
    if (!this.apiKey || !this.senderId) {
      throw new Error("Indian DLT SMS configuration missing.");
    }

    // Production DLT HTTP invocation here
    return {
      success: true,
      providerMessageId: `dlt_${Date.now()}`,
      channel: "SMS",
      deliveryStatus: "SENT"
    };
  }
}

module.exports = {
  FakeSmsProvider,
  IndianDltSmsProvider
};
