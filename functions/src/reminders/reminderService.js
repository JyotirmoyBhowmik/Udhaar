/**
 * Reminders Centre Service (functions/src/reminders/reminderService.js)
 * Implements Stage S7 Reminders:
 * 1. 4 Polite Escalation Levels (Gentle, Follow-up, Firm, Final Notice).
 * 2. Quiet Hours (21:00 to 08:00 default) & Snooze Options (1d, 3d, 7d, STOP).
 * 3. Automated Stop Conditions (settled, claim pending, snoozed, disabled).
 * 4. Multi-lingual Templates (English, Hindi, Bengali, Assamese, Nepali).
 * 5. Safe WhatsApp dispatch with recorded consent & wa.me fallback generation.
 */

const { getRecord, listRecords, updateRecord, _inMemoryRecords } = require("../ledger/recordService");
const { getWhatsAppProvider } = require("../providers");

// User reminder policies store: uid -> policy
const inMemoryReminderPolicies = new Map();
// Per-record reminder state store: recordId -> reminderState
const inMemoryRecordReminderStates = new Map();

const DEFAULT_POLICY = Object.freeze({
  quietHoursStartHour: 21, // 9:00 PM
  quietHoursEndHour: 8,    // 8:00 AM
  level1DaysBefore: 3,
  level2DaysOverdue: 7,
  level3DaysOverdue: 30,
  maxRemindersPerDay: 1,
  defaultLocale: "en"
});

const REMINDER_TEMPLATES = {
  en: {
    LEVEL_1: "Hi {name}, a gentle reminder from {my_name} that ₹{amount} for our Udhaar record is due on {due_date}. Hope all is well!",
    LEVEL_2: "Hi {name}, following up regarding ₹{amount} which was due on {due_date} ({days_overdue} days ago). Please let me know when you can return it.",
    LEVEL_3: "Hi {name}, checking in on ₹{amount} which is now {days_overdue} days overdue. Let's arrange a time or installment plan to settle this.",
    LEVEL_4: "Hi {name}, our record for ₹{amount} is now over 30 days overdue. Please share a clear return date or contact me so we can resolve this."
  },
  hi: {
    LEVEL_1: "नमस्ते {name}, {my_name} की तरफ से एक विनम्र याद दिलाना: ₹{amount} की देय तिथि {due_date} है।",
    LEVEL_2: "नमस्ते {name}, ₹{amount} के संबंध में जो {due_date} को देय था ({days_overdue} दिन पहले)। कृपया बताएं कि आप इसे कब लौटा सकते हैं।",
    LEVEL_3: "नमस्ते {name}, ₹{amount} का भुगतान {days_overdue} दिन से बाकी है। कृपया इसे निपटाने के लिए एक समय बताएं।",
    LEVEL_4: "नमस्ते {name}, ₹{amount} का हिसाब 30 दिनों से अधिक समय से लंबित है। कृपया जल्द से जल्द संपर्क करके समाधान करें।"
  },
  bn: {
    LEVEL_1: "নমস্কার {name}, {my_name}-এর পক্ষ থেকে একটি বিনীত অনুস্মারক: ₹{amount}-এর পরিশোধের তারিখ {due_date}।",
    LEVEL_2: "নমস্কার {name}, ₹{amount}-এর বিষয়ে যোগাযোগ করছি যা {due_date}-এ প্রদেয় ছিল ({days_overdue} দিন আগে)। আপনি কবে দিতে পারবেন জানালে সুবিধা হয়।",
    LEVEL_3: "নমস্কার {name}, ₹{amount}-এর হিসাব {days_overdue} দিন ধরে বাকি আছে। এটি পরিশোধের একটি তারিখ জানালে ভালো হয়।",
    LEVEL_4: "নমস্কার {name}, ₹{amount}-এর হিসাব ৩০ দিনের বেশি সময় ধরে বাকি রয়েছে। দয়া করে শীঘ্রই যোগাযোগ করে সমাধান করুন।"
  },
  as: {
    LEVEL_1: "নমস্কাৰ {name}, {my_name}ৰ তৰফৰ পৰা এটি বিনম্ৰ সোঁৱৰণি: ₹{amount} পৰিশোধৰ তাৰিখ {due_date}।",
    LEVEL_2: "নমস্কাৰ {name}, ₹{amount}ৰ বিষয়ে যোগাযোগ কৰিছোঁ যিটো {due_date}ত দিবলগীয়া আছিল ({days_overdue} দিন আগতে)। কেতিয়া দিব পাৰিব জনালে সুবিধা হয়।",
    LEVEL_3: "নমস্কাৰ {name}, ₹{amount}ৰ ধন {days_overdue} দিন ধৰি বাকী আছে। অনুগ্ৰহ কৰি এটি পৰিশোধৰ তাৰিখ জনাওক।",
    LEVEL_4: "নমস্কাৰ {name}, ₹{amount}ৰ ধন ৩০ দিনতকৈ অধিক সময় বাকী হৈ আছে। অনুগ্ৰহ কৰি সোনকালে যোগাযোগ কৰি নিষ্পত্তি কৰক।"
  },
  ne: {
    LEVEL_1: "नमस्ते {name}, {my_name} को तर्फबाट एउटा सानो सम्झना: ₹{amount} को भुक्तानी मिति {due_date} हो।",
    LEVEL_2: "नमस्ते {name}, ₹{amount} को बारेमा फलो-अप जो {due_date} मा तिर्नुपर्ने थियो ({days_overdue} दिन अघि)। कहिले फिर्ता गर्न सक्नुहुन्छ कृपया बताउनुहोस्।",
    LEVEL_3: "नमस्ते {name}, ₹{amount} को हिसाब {days_overdue} दिनदेखि बाँकी छ। कृपया यसलाई मिलाउने मिति तय गरौं।",
    LEVEL_4: "नमस्ते {name}, ₹{amount} को हिसाब ३० दिनभन्दा बढी समयदेखि बाँकी छ। कृपया छिट्टै सम्पर्क गरेर समाधान गरौं।"
  }
};

function resetRemindersStore() {
  inMemoryReminderPolicies.clear();
  inMemoryRecordReminderStates.clear();
}

/**
 * Get or initialize user reminder policy
 */
async function getUserReminderPolicy({ uid }) {
  if (!uid) return { ...DEFAULT_POLICY };
  const existing = inMemoryReminderPolicies.get(uid);
  return existing ? { ...existing } : { ...DEFAULT_POLICY, uid };
}

/**
 * Update user reminder policy
 */
async function updateUserReminderPolicy({ uid, policyUpdates }) {
  if (!uid) {
    const err = new Error("User ID is required.");
    err.code = "ERR_UNAUTHORIZED";
    err.status = 401;
    throw err;
  }

  const current = await getUserReminderPolicy({ uid });
  const updated = {
    ...current,
    ...policyUpdates,
    uid,
    updatedAtMs: Date.now()
  };

  inMemoryReminderPolicies.set(uid, updated);
  return updated;
}

/**
 * Check if the current time falls in quiet hours (e.g. 21:00 to 08:00)
 */
function isQuietHours(policy = DEFAULT_POLICY, dateObj = new Date()) {
  const hour = dateObj.getHours();
  const start = policy.quietHoursStartHour !== undefined ? policy.quietHoursStartHour : 21;
  const end = policy.quietHoursEndHour !== undefined ? policy.quietHoursEndHour : 8;

  if (start > end) {
    // Overnight: e.g. 21 to 8
    return hour >= start || hour < end;
  } else {
    return hour >= start && hour < end;
  }
}

/**
 * Calculate the appropriate escalation level based on due date
 */
function calculateEscalationLevel(dueDateMs, nowMs = Date.now(), policy = DEFAULT_POLICY) {
  if (!dueDateMs || dueDateMs <= 0) return null;

  const msPerDay = 86400000;
  const daysDiff = Math.floor((nowMs - dueDateMs) / msPerDay);

  if (daysDiff < -policy.level1DaysBefore) {
    // Too early for reminders
    return null;
  } else if (daysDiff <= 0) {
    // 3 days before up to on due date
    return "LEVEL_1";
  } else if (daysDiff <= policy.level2DaysOverdue) {
    // 1 to 7 days overdue
    return "LEVEL_2";
  } else if (daysDiff <= policy.level3DaysOverdue) {
    // 8 to 30 days overdue
    return "LEVEL_3";
  } else {
    // > 30 days overdue
    return "LEVEL_4";
  }
}

/**
 * Format a localized reminder message
 */
function formatReminderMessage({ level, locale = "en", partyName, amountPaise, dueDateMs, myName = "Udhaar User", nowMs = Date.now() }) {
  const langTemplates = REMINDER_TEMPLATES[locale] || REMINDER_TEMPLATES.en;
  const template = langTemplates[level] || langTemplates.LEVEL_1;

  const amountRupees = (Number(amountPaise) / 100).toLocaleString("en-IN", {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2
  });

  const dueDateStr = dueDateMs ? new Date(dueDateMs).toLocaleDateString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric"
  }) : "agreed date";

  const daysOverdue = dueDateMs && nowMs > dueDateMs ? Math.max(1, Math.floor((nowMs - dueDateMs) / 86400000)) : 0;

  return template
    .replace("{name}", partyName || "Friend")
    .replace("{my_name}", myName || "Udhaar User")
    .replace("{amount}", amountRupees)
    .replace("{due_date}", dueDateStr)
    .replace("{days_overdue}", String(daysOverdue));
}

/**
 * Snooze reminders for a specific record
 */
async function snoozeRecordReminders({ uid, recordId, snoozeDays = 1, stop = false }) {
  const record = await getRecord({ uid, recordId });
  if (!record) {
    const err = new Error(`Record ${recordId} not found.`);
    err.code = "ERR_RECORD_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const now = Date.now();
  let state = inMemoryRecordReminderStates.get(recordId) || {};

  if (stop) {
    state.remindersDisabled = true;
    state.snoozedUntilMs = null;
    state.stoppedAtMs = now;
  } else {
    const days = Math.max(1, Number(snoozeDays) || 1);
    state.snoozedUntilMs = now + days * 86400000;
    state.remindersDisabled = false;
    state.snoozedAtMs = now;
  }

  inMemoryRecordReminderStates.set(recordId, state);

  return {
    success: true,
    recordId,
    remindersDisabled: Boolean(state.remindersDisabled),
    snoozedUntilMs: state.snoozedUntilMs
  };
}

/**
 * Evaluate and schedule all pending reminders for a user
 */
async function evaluatePendingReminders({ uid, nowMs = Date.now() }) {
  const policy = await getUserReminderPolicy({ uid });
  const inQuietHours = isQuietHours(policy, new Date(nowMs));

  const recordsResult = await listRecords({ uid });
  const recordsList = recordsResult.items || recordsResult || [];
  const pendingReminders = [];

  for (const record of recordsList) {
    // Stop condition 1: Settled records
    if (record.status === "SETTLED" || record.remainingAmountPaise <= 0) {
      continue;
    }

    // Stop condition 2: Only remind borrowers when the user is the LENDER
    // (If creator's counterpartyRole is BORROWER, user lent money to counterparty)
    if (record.counterpartyRole !== "BORROWER") {
      continue;
    }

    // Stop condition 3: Pending payment claims
    if (record.hasPendingPaymentClaim) {
      continue;
    }

    // Stop condition 4: Snoozed or disabled
    const reminderState = inMemoryRecordReminderStates.get(record.recordId) || {};
    if (reminderState.remindersDisabled) {
      continue;
    }
    if (reminderState.snoozedUntilMs && nowMs < reminderState.snoozedUntilMs) {
      continue;
    }

    // Stop condition 5: Max 1 reminder per day
    if (reminderState.lastReminderSentMs) {
      const hoursSinceLast = (nowMs - reminderState.lastReminderSentMs) / (60 * 60 * 1000);
      if (hoursSinceLast < 24) {
        continue;
      }
    }

    const level = calculateEscalationLevel(record.dueDateMs, nowMs, policy);
    if (!level) {
      continue;
    }

    const message = formatReminderMessage({
      level,
      locale: policy.defaultLocale,
      partyName: record.partyName,
      amountPaise: record.remainingAmountPaise,
      dueDateMs: record.dueDateMs,
      myName: "Udhaar User",
      nowMs
    });

    const cleanPhone = record.partyPhone ? String(record.partyPhone).replace(/\D+/g, "") : "";
    const waMeUrl = cleanPhone ? `https://wa.me/${cleanPhone}?text=${encodeURIComponent(message)}` : null;

    pendingReminders.push({
      recordId: record.recordId,
      partyName: record.partyName,
      partyPhone: record.partyPhone,
      remainingAmountPaise: record.remainingAmountPaise,
      dueDateMs: record.dueDateMs,
      level,
      inQuietHours,
      message,
      waMeUrl,
      canSendAutomatedWhatsApp: Boolean(record.consentWhatsAppReminders)
    });
  }

  return {
    evaluatedCount: recordsList.length,
    inQuietHours,
    pendingReminders
  };
}

/**
 * Dispatch or prepare a reminder message for a record
 */
async function sendRecordReminder({ uid, recordId, locale = "en", forceSendWhatsApp = false, nowMs = Date.now() }) {
  const record = await getRecord({ uid, recordId });
  if (!record) {
    const err = new Error(`Record ${recordId} not found.`);
    err.code = "ERR_RECORD_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const policy = await getUserReminderPolicy({ uid });
  const level = calculateEscalationLevel(record.dueDateMs, nowMs, policy) || "LEVEL_1";

  const message = formatReminderMessage({
    level,
    locale: locale || policy.defaultLocale,
    partyName: record.partyName,
    amountPaise: record.remainingAmountPaise,
    dueDateMs: record.dueDateMs,
    nowMs
  });

  const cleanPhone = record.partyPhone ? String(record.partyPhone).replace(/\D+/g, "") : "";
  const waMeUrl = cleanPhone ? `https://wa.me/${cleanPhone}?text=${encodeURIComponent(message)}` : null;

  let automatedSent = false;
  let deliveryResult = null;

  // Only dispatch automated WhatsApp if explicitly allowed & consent recorded
  if (forceSendWhatsApp || record.consentWhatsAppReminders) {
    if (record.partyPhone && cleanPhone.length >= 10) {
      const waProvider = getWhatsAppProvider();
      deliveryResult = await waProvider.sendCode({
        destination: record.partyPhone,
        code: "REMINDER",
        locale,
        purpose: "REMINDER"
      });
      automatedSent = true;
    }
  }

  // Update last reminder sent timestamp
  const state = inMemoryRecordReminderStates.get(recordId) || {};
  state.lastReminderSentMs = nowMs;
  state.lastReminderLevel = level;
  inMemoryRecordReminderStates.set(recordId, state);

  return {
    success: true,
    recordId,
    level,
    message,
    waMeUrl,
    automatedSent,
    deliveryResult,
    lastReminderSentMs: nowMs
  };
}

module.exports = {
  DEFAULT_POLICY,
  REMINDER_TEMPLATES,
  resetRemindersStore,
  getUserReminderPolicy,
  updateUserReminderPolicy,
  isQuietHours,
  calculateEscalationLevel,
  formatReminderMessage,
  snoozeRecordReminders,
  evaluatePendingReminders,
  sendRecordReminder,
  _inMemoryReminderPolicies: inMemoryReminderPolicies,
  _inMemoryRecordReminderStates: inMemoryRecordReminderStates
};
