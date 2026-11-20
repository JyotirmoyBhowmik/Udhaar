const crypto = require("crypto");
const { getReminderRepository, assertRepoMethod } = require("../repositories");
const { listRecords, getRecord } = require("../ledger/recordService");

const ESCALATION_TEMPLATES = {
  en: {
    1: "Hi {name}, a friendly reminder that ₹{amount} is due on {due_date}. — {my_name} via Udhaar",
    2: "Hi {name}, ₹{amount} was due on {due_date}. Please let me know when convenient. — {my_name} via Udhaar",
    3: "Hi {name}, checking in regarding the overdue balance of ₹{amount} (due {due_date}). — {my_name} via Udhaar",
    4: "Notice: Final reminder for overdue Udhaar record of ₹{amount} (due {due_date}) with {my_name}."
  },
  hi: {
    1: "नमस्ते {name}, याद दिलाना था कि ₹{amount} की देय तिथि {due_date} है। — {my_name} (Udhaar)",
    2: "नमस्ते {name}, ₹{amount} {due_date} को देय था। कृपया सुविधा अनुसार बताएं। — {my_name} (Udhaar)",
    3: "नमस्ते {name}, बकाया ₹{amount} (देय तिथि {due_date}) के संबंध में अनुस्मारक। — {my_name} (Udhaar)",
    4: "सूचना: {my_name} के साथ ₹{amount} (देय {due_date}) के बकाया रिकॉर्ड के लिए अंतिम अनुस्मारक।"
  },
  bn: {
    1: "হ্যালো {name}, মনে করিয়ে দিচ্ছি যে ₹{amount} আগামী {due_date} তারিখে পরিশোধযোগ্য। — {my_name} (Udhaar)",
    2: "হ্যালো {name}, ₹{amount} গত {due_date} তারিখে পরিশোধযোগ্য ছিল। অনুগ্রহ করে জানাবেন। — {my_name} (Udhaar)",
    3: "হ্যালো {name}, বকেয়া ₹{amount} (তারিখ {due_date}) সম্পর্কে অনুস্মারক। — {my_name} (Udhaar)",
    4: "বিজ্ঞপ্তি: {my_name}-এর সাথে ₹{amount} (তারিখ {due_date}) বকেয়া রেকর্ডের চূড়ান্ত অনুস্মারক।"
  }
};

function reminderRepo() {
  const repo = getReminderRepository();
  assertRepoMethod(repo, "getPolicy", "ReminderRepository");
  assertRepoMethod(repo, "savePolicy", "ReminderRepository");
  assertRepoMethod(repo, "getReminderState", "ReminderRepository");
  assertRepoMethod(repo, "saveReminderState", "ReminderRepository");
  assertRepoMethod(repo, "saveReminderEvent", "ReminderRepository");
  assertRepoMethod(repo, "listReminderEvents", "ReminderRepository");
  return repo;
}

const DEFAULT_POLICY = {
  enabled: true,
  quietHoursStart: 21,
  quietHoursEnd: 8,
  quietHoursStartHour: 21,
  quietHoursEndHour: 8,
  maxRemindersPerRecordPerWeek: 2,
  defaultLocale: "en",
  allowEscalationLevel4: false,
  tone: "POLITE"
};

async function getUserReminderPolicy(uidOrParams) {
  const uid = typeof uidOrParams === "object" && uidOrParams !== null ? uidOrParams.uid : uidOrParams;
  const rRepo = reminderRepo();
  const saved = await rRepo.getPolicy(uid);
  return {
    ...DEFAULT_POLICY,
    ...(saved || {}),
    uid
  };
}

async function updateUserReminderPolicy(uidOrParams, updatesArg = {}) {
  let uid = uidOrParams;
  let updates = updatesArg;
  if (typeof uidOrParams === "object" && uidOrParams !== null) {
    uid = uidOrParams.uid;
    updates = uidOrParams.policyUpdates || uidOrParams;
  }

  const rRepo = reminderRepo();
  const current = await getUserReminderPolicy(uid);
  const nextStart = updates.quietHoursStartHour !== undefined
    ? Number(updates.quietHoursStartHour)
    : (updates.quietHoursStart !== undefined ? Number(updates.quietHoursStart) : current.quietHoursStart);
  const nextEnd = updates.quietHoursEndHour !== undefined
    ? Number(updates.quietHoursEndHour)
    : (updates.quietHoursEnd !== undefined ? Number(updates.quietHoursEnd) : current.quietHoursEnd);

  const next = {
    ...current,
    ...updates,
    uid,
    quietHoursStart: nextStart,
    quietHoursEnd: nextEnd,
    quietHoursStartHour: nextStart,
    quietHoursEndHour: nextEnd,
    maxRemindersPerRecordPerWeek: updates.maxRemindersPerRecordPerWeek !== undefined
      ? Math.max(1, Math.min(7, Number(updates.maxRemindersPerRecordPerWeek)))
      : current.maxRemindersPerRecordPerWeek,
    updatedAtMs: Date.now()
  };
  await rRepo.savePolicy(uid, next);
  return next;
}

async function getRecordReminderConfig(uid, recordId) {
  const rRepo = reminderRepo();
  const saved = await rRepo.getReminderState(recordId);
  return {
    uid,
    recordId,
    muted: false,
    paused: false,
    stopped: false,
    snoozedUntilMs: 0,
    preferredChannel: "WHATSAPP",
    locale: "en",
    lastSentAtMs: 0,
    lastEscalationLevel: 0,
    ...(saved || {})
  };
}

async function configureRecordReminder(uid, recordId, configUpdates = {}) {
  const rRepo = reminderRepo();
  const current = await getRecordReminderConfig(uid, recordId);
  const next = {
    ...current,
    ...configUpdates,
    uid,
    recordId,
    updatedAtMs: Date.now()
  };
  await rRepo.saveReminderState(recordId, next);
  return next;
}

async function snoozeRecordReminders({ uid, recordId, snoozeDays = 0, stop = false, nowMs = Date.now() }) {
  const updates = {};
  if (stop) {
    updates.muted = true;
    updates.stopped = true;
  } else if (Number(snoozeDays) > 0) {
    updates.snoozedUntilMs = nowMs + Number(snoozeDays) * 24 * 60 * 60 * 1000;
  }
  return await configureRecordReminder(uid, recordId, updates);
}

function normalizeLevelNumber(level) {
  if (typeof level === "number") {
    return Math.min(4, Math.max(1, level));
  }
  if (typeof level === "string") {
    const match = level.match(/(\d+)/);
    if (match) {
      return Math.min(4, Math.max(1, parseInt(match[1], 10)));
    }
  }
  return 1;
}

function calculateEscalationLevel(dueDateMs, nowMs = Date.now(), allowLevel4 = true) {
  if (!dueDateMs || dueDateMs <= 0) return null;
  const diffDays = (nowMs - dueDateMs) / (24 * 60 * 60 * 1000);
  if (diffDays < -3) return null;
  if (diffDays <= 0) return "LEVEL_1";
  if (diffDays <= 3) return "LEVEL_2";
  if (diffDays <= 30) return "LEVEL_3";
  return allowLevel4 ? "LEVEL_4" : "LEVEL_3";
}

function isQuietHours(hourOrPolicy, startOrDate = 21, endHour = 8) {
  let hour;
  let startHour;
  let end;

  if (hourOrPolicy && typeof hourOrPolicy === "object" && !(hourOrPolicy instanceof Date)) {
    const policy = hourOrPolicy;
    startHour = policy.quietHoursStartHour !== undefined
      ? Number(policy.quietHoursStartHour)
      : (policy.quietHoursStart !== undefined ? Number(policy.quietHoursStart) : 21);
    end = policy.quietHoursEndHour !== undefined
      ? Number(policy.quietHoursEndHour)
      : (policy.quietHoursEnd !== undefined ? Number(policy.quietHoursEnd) : 8);
    if (startOrDate instanceof Date) {
      hour = startOrDate.getHours();
    } else if (typeof startOrDate === "number") {
      hour = startOrDate;
    } else {
      hour = new Date().getHours();
    }
  } else {
    hour = typeof hourOrPolicy === "number" ? hourOrPolicy : new Date().getHours();
    startHour = Number(startOrDate);
    end = Number(endHour);
  }

  if (startHour === end) return false;
  if (startHour > end) {
    return hour >= startHour || hour < end;
  }
  return hour >= startHour && hour < end;
}

function formatReminderMessage({
  locale = "en",
  level = 1,
  counterpartyName,
  partyName,
  amountPaise = 0,
  dueDateMs,
  senderName,
  myName
}) {
  const lang = ESCALATION_TEMPLATES[locale] ? locale : "en";
  const lvl = normalizeLevelNumber(level);
  const template = ESCALATION_TEMPLATES[lang][lvl] || ESCALATION_TEMPLATES.en[1];
  const amountNum = Number(amountPaise || 0) / 100;
  const amountInr = Number.isInteger(amountNum)
    ? amountNum.toLocaleString("en-IN")
    : amountNum.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const dueDateStr = dueDateMs
    ? new Date(dueDateMs).toISOString().slice(0, 10)
    : "soon";

  return template
    .replace("{name}", counterpartyName || partyName || "Friend")
    .replace("{amount}", amountInr)
    .replace("{due_date}", dueDateStr)
    .replace("{my_name}", senderName || myName || "Udhaar User");
}

async function evaluatePendingReminders(uidOrParams, nowMsArg = Date.now(), currentHourLocalArg = 12) {
  let uid = uidOrParams;
  let nowMs = nowMsArg;
  let currentHourLocal = currentHourLocalArg;
  if (uidOrParams && typeof uidOrParams === "object") {
    uid = uidOrParams.uid;
    nowMs = uidOrParams.nowMs !== undefined ? uidOrParams.nowMs : Date.now();
    currentHourLocal = uidOrParams.currentHourLocal !== undefined ? uidOrParams.currentHourLocal : 12;
  }

  const policy = await getUserReminderPolicy(uid);
  if (!policy.enabled) {
    return { skippedReason: "POLICY_DISABLED", candidates: [], pendingReminders: [] };
  }

  if (isQuietHours(currentHourLocal, policy.quietHoursStart, policy.quietHoursEnd)) {
    return { skippedReason: "QUIET_HOURS", candidates: [], pendingReminders: [] };
  }

  const { items: allRecords } = await listRecords({ uid, limit: 1000 });
  const candidates = [];

  for (const record of allRecords) {
    if (record.status === "SETTLED" || record.deleted || record.isDeleted) continue;
    if (record.counterpartyRole && record.counterpartyRole !== "BORROWER") continue;
    const remaining = Number(
      record.remainingAmountPaise !== undefined
        ? record.remainingAmountPaise
        : (record.remainingPaise !== undefined ? record.remainingPaise : record.principalAmountPaise || record.amountPaise || 0)
    );
    if (remaining <= 0) continue;
    if (!record.dueDateMs) continue;

    const cfg = await getRecordReminderConfig(uid, record.recordId);
    if (cfg.muted || cfg.paused || cfg.stopped) continue;
    if (cfg.snoozedUntilMs && cfg.snoozedUntilMs > nowMs) continue;

    const level = calculateEscalationLevel(record.dueDateMs, nowMs, policy.allowEscalationLevel4);
    if (!level) continue;

    const message = formatReminderMessage({
      locale: cfg.locale || policy.defaultLocale || "en",
      level,
      counterpartyName: record.partyName || record.counterpartyName || "Friend",
      amountPaise: remaining,
      dueDateMs: record.dueDateMs,
      senderName: "You"
    });

    const cleanPhone = String(record.partyPhone || "").replace(/[^0-9]/g, "");
    const waMeUrl = `https://wa.me/${cleanPhone}?text=${encodeURIComponent(message)}`;

    candidates.push({
      recordId: record.recordId,
      partyName: record.partyName || record.counterpartyName || "Friend",
      counterpartyName: record.partyName || record.counterpartyName || "Friend",
      remainingPaise: remaining,
      remainingAmountPaise: remaining,
      dueDateMs: record.dueDateMs,
      escalationLevel: level,
      level,
      channel: cfg.preferredChannel || "WHATSAPP",
      message,
      waMeUrl
    });
  }

  return { skippedReason: null, candidates, pendingReminders: candidates };
}

async function sendRecordReminder({ uid, recordId, locale = "en", nowMs = Date.now() }) {
  const record = await getRecord({ uid, recordId });
  const level = calculateEscalationLevel(record.dueDateMs, nowMs, true) || "LEVEL_1";
  const message = formatReminderMessage({
    locale,
    level,
    partyName: record.partyName,
    amountPaise: record.remainingAmountPaise || record.principalAmountPaise,
    dueDateMs: record.dueDateMs,
    myName: "Udhaar User"
  });
  const cleanPhone = String(record.partyPhone || "").replace(/[^0-9]/g, "");
  const waMeUrl = `https://wa.me/${cleanPhone}?text=${encodeURIComponent(message)}`;
  const dispatch = await logReminderDispatch(uid, {
    recordId,
    level,
    channel: "WHATSAPP",
    message
  });
  return { ...dispatch, waMeUrl, message, level };
}

async function logReminderDispatch(uid, { recordId, level = 1, channel = "WHATSAPP", message = "", status = "SENT" }) {
  const rRepo = reminderRepo();
  const dispatchId = "rem_" + crypto.randomBytes(8).toString("hex");
  const entry = {
    id: dispatchId,
    reminderId: dispatchId,
    dispatchId,
    uid,
    recordId,
    level,
    channel,
    message,
    status,
    dispatchedAtMs: Date.now()
  };
  await rRepo.saveReminderEvent(entry);
  await configureRecordReminder(uid, recordId, {
    lastSentAtMs: entry.dispatchedAtMs,
    lastEscalationLevel: level
  });
  return entry;
}

async function getReminderDispatchHistory(uid, recordId = null) {
  const rRepo = reminderRepo();
  return await rRepo.listReminderEvents(recordId);
}

async function clearAllReminderStore() {
  const rRepo = reminderRepo();
  assertRepoMethod(rRepo, "clear", "ReminderRepository");
  await rRepo.clear();
}

const resetRemindersStore = clearAllReminderStore;

module.exports = {
  ESCALATION_TEMPLATES,
  getUserReminderPolicy,
  updateUserReminderPolicy,
  getRecordReminderConfig,
  configureRecordReminder,
  snoozeRecordReminders,
  sendRecordReminder,
  calculateEscalationLevel,
  isQuietHours,
  formatReminderMessage,
  evaluatePendingReminders,
  logReminderDispatch,
  getReminderDispatchHistory,
  clearAllReminderStore,
  resetRemindersStore
};
