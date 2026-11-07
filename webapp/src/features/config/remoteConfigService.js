/**
 * Udhaar Remote Config & Community Quota Guard Service (Spark Mode)
 * (webapp/src/features/config/remoteConfigService.js)
 * 
 * Manages Remote Config parameters:
 * - UDHAAR_TIER: "spark" | "blaze" (default "spark")
 * - signup_enabled: true | false (default true)
 * - guest_links_enabled: true | false (default true)
 * - max_client_daily_writes: 500 (default 500)
 * 
 * All user-facing pause notices are in plain wording across all 5 regional languages.
 */

const DEFAULT_CONFIG = {
  UDHAAR_TIER: "spark",
  signup_enabled: true,
  guest_links_enabled: true,
  max_client_daily_writes: 500
};

const PAUSE_MESSAGES = {
  signup_disabled: {
    en: "New accounts are paused right now to keep Udhaar free for everyone. Please check back later.",
    hi: "सभी के लिए उधार को निःशुल्क रखने के लिए नए खाते अभी रोके गए हैं। कृपया बाद में देखें।",
    bn: "সবার জন্য ধার বিনামূল্যে রাখতে নতুন অ্যাকাউন্ট এখন স্থগিত রাখা হয়েছে। অনুগ্রহ করে পরে দেখুন।",
    as: "সকলোৰে বাবে ধাৰ বিনামূলীয়া ৰাখিবলৈ নতুন একাউণ্ট এতিয়া স্থগিত ৰখা হৈছে। অনুগ্ৰহ কৰি পিছত চাওক।",
    ne: "सबैका लागि उधार निःशुल्क राख्न नयाँ खाताहरू अहिले रोकिएका छन्। कृपया पछि हेर्नुहोस्।"
  },
  guest_links_disabled: {
    en: "Creating new web links is temporarily paused to protect free community quotas.",
    hi: "निःशुल्क कोटा सुरक्षित रखने के लिए नए वेब लिंक बनाना अस्थायी रूप से रोका गया है।",
    bn: "বিনামূল্যে কোটা সুরক্ষিত রাখতে নতুন ওয়েব লিঙ্ক তৈরি সাময়িকভাবে স্থগিত রয়েছে।",
    as: "বিনামূলীয়া কোটা সুৰক্ষিত ৰাখিবলৈ নতুন ৱেব লিংক সৃষ্টি সাময়িকভাৱে স্থগিত আছে।",
    ne: "निःशुल्क कोटा सुरक्षित राख्न नयाँ वेब लिङ्क बनाउने काम अस्थायी रूपमा रोकिएको छ।"
  },
  quota_resting: {
    en: "Sync is resting until tomorrow to keep Udhaar free for everyone. Your records are safe on your device.",
    hi: "सभी के लिए उधार को निःशुल्क रखने के लिए सिंक कल तक विश्राम कर रहा है। आपके रिकॉर्ड आपके फ़ोन में सुरक्षित हैं।",
    bn: "সবার জন্য ধার বিনামূল্যে রাখতে সিঙ্ক কাল পর্যন্ত বিশ্রাম নিচ্ছে। আপনার রেকর্ড আপনার ফোনেই সুরক্ষিত আছে।",
    as: "সকলোৰে বাবে ধাৰ বিনামূলীয়া ৰাখিবলৈ চিন্ক কালিলৈকে জিৰণি লৈছে। আপোনাৰ ৰেকৰ্ড আপোনাৰ ফোনত সুৰক্ষিত আছে।",
    ne: "सबैका लागि उधार निःशुल्क राख्न सिङ्क भोलिसम्म विश्राम गर्दैछ। तपाईंको रेकर्डहरू तपाईंको यन्त्रमा सुरक्षित छन्।"
  }
};

class RemoteConfigService {
  constructor(firebaseRemoteConfig = null) {
    this.remoteConfig = firebaseRemoteConfig;
    this.values = { ...DEFAULT_CONFIG };
  }

  async initialize() {
    if (this.remoteConfig) {
      try {
        this.remoteConfig.settings = {
          minimumFetchIntervalMillis: 3600000 // 1 hour
        };
        this.remoteConfig.defaultConfig = DEFAULT_CONFIG;
        await this.remoteConfig.fetchAndActivate();
        
        const tier = this.remoteConfig.getString("UDHAAR_TIER");
        if (tier) this.values.UDHAAR_TIER = tier;
        this.values.signup_enabled = this.remoteConfig.getBoolean("signup_enabled");
        this.values.guest_links_enabled = this.remoteConfig.getBoolean("guest_links_enabled");
        const maxWrites = this.remoteConfig.getNumber("max_client_daily_writes");
        if (maxWrites > 0) this.values.max_client_daily_writes = maxWrites;
      } catch (e) {
        console.warn("Remote Config fetch failed; using default config values.", e);
      }
    }
    return this.values;
  }

  isSignupEnabled() {
    return this.values.signup_enabled !== false;
  }

  isGuestLinksEnabled() {
    return this.values.guest_links_enabled !== false;
  }

  getMaxClientDailyWrites() {
    return this.values.max_client_daily_writes || DEFAULT_CONFIG.max_client_daily_writes;
  }

  getSignupDisabledMessage(lang = "en") {
    const table = PAUSE_MESSAGES.signup_disabled;
    return table[lang] || table.en;
  }

  getGuestLinksDisabledMessage(lang = "en") {
    const table = PAUSE_MESSAGES.guest_links_disabled;
    return table[lang] || table.en;
  }

  getQuotaRestingMessage(lang = "en") {
    const table = PAUSE_MESSAGES.quota_resting;
    return table[lang] || table.en;
  }

  setOverride(key, value) {
    this.values[key] = value;
  }
}

const remoteConfigService = new RemoteConfigService();

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    DEFAULT_CONFIG,
    PAUSE_MESSAGES,
    RemoteConfigService,
    remoteConfigService
  };
}
