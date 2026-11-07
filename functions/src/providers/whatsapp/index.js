/**
 * WhatsApp Cloud API Provider & Fake WhatsApp Provider (functions/src/providers/whatsapp/index.js)
 * Implements Meta WhatsApp Cloud API AUTHENTICATION template delivery
 * and in-memory simulated provider for testing and local emulators.
 */

const crypto = require("crypto");

class FakeWhatsAppProvider {
  constructor() {
    this.sentMessages = [];
    this.deliveryCallbacks = new Map();
  }

  reset() {
    this.sentMessages = [];
    this.deliveryCallbacks.clear();
  }

  async sendCode(request) {
    const messageId = `fake_wa_${Date.now()}_${crypto.randomBytes(4).toString("hex")}`;
    const entry = {
      messageId,
      destination: request.destination,
      code: request.code,
      locale: request.locale || "en",
      purpose: request.purpose,
      timestamp: Date.now(),
      status: "SENT"
    };

    this.sentMessages.push(entry);

    return {
      success: true,
      providerMessageId: messageId,
      channel: "WHATSAPP",
      deliveryStatus: "SENT"
    };
  }

  getLastCodeFor(destination) {
    const matches = this.sentMessages.filter(m => m.destination === destination);
    return matches.length > 0 ? matches[matches.length - 1].code : null;
  }
}

class WhatsAppCloudApiProvider {
  constructor(config = {}) {
    this.phoneNumberId = config.phoneNumberId || process.env.META_PHONE_NUMBER_ID;
    this.accessToken = config.accessToken || process.env.META_WHATSAPP_TOKEN;
    this.appSecret = config.appSecret || process.env.META_APP_SECRET;
  }

  async sendCode(request) {
    if (!this.phoneNumberId || !this.accessToken) {
      throw new Error("WhatsApp Cloud API configuration missing (META_PHONE_NUMBER_ID or META_WHATSAPP_TOKEN).");
    }

    // Template language mapping
    const langCode = ["hi", "bn", "ne"].includes(request.locale) ? request.locale : "en";
    const cleanPhone = request.destination.replace(/[^0-9]/g, "");

    const url = `https://graph.facebook.com/v21.0/${this.phoneNumberId}/messages`;
    const payload = {
      messaging_product: "whatsapp",
      recipient_type: "individual",
      to: cleanPhone,
      type: "template",
      template: {
        name: "udhaar_login_code",
        language: {
          code: langCode
        },
        components: [
          {
            type: "body",
            parameters: [
              {
                type: "text",
                text: request.code
              }
            ]
          },
          {
            type: "button",
            sub_type: "url",
            index: "0",
            parameters: [
              {
                type: "text",
                text: request.code
              }
            ]
          }
        ]
      }
    };

    const res = await fetch(url, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${this.accessToken}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });

    const data = await res.json();
    if (!res.ok) {
      const errDetail = data.error?.message || "WhatsApp Cloud API error";
      throw new Error(`WhatsApp send failure (${res.status}): ${errDetail}`);
    }

    const messageId = data.messages?.[0]?.id || `wa_${Date.now()}`;
    return {
      success: true,
      providerMessageId: messageId,
      channel: "WHATSAPP",
      deliveryStatus: "SENT"
    };
  }

  verifyWebhookSignature(headers, rawBody) {
    if (!this.appSecret) return false;
    const signature = headers["x-hub-signature-256"];
    if (!signature) return false;

    const expected = "sha256=" + crypto.createHmac("sha256", this.appSecret).update(rawBody).digest("hex");
    return crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expected));
  }

  parseWebhookPayload(body) {
    try {
      const entry = body?.entry?.[0];
      const change = entry?.changes?.[0];
      const statusObj = change?.value?.statuses?.[0];
      if (!statusObj) return null;

      return {
        messageId: statusObj.id,
        recipientId: statusObj.recipient_id,
        status: statusObj.status, // "sent" | "delivered" | "read" | "failed"
        timestamp: statusObj.timestamp
      };
    } catch (_) {
      return null;
    }
  }
}

module.exports = {
  FakeWhatsAppProvider,
  WhatsAppCloudApiProvider
};
