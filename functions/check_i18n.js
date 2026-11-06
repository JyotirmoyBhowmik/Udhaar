/**
 * check_i18n.js
 * Automated validation script for Android and Web internationalization,
 * plain-language compliance, zero-jargon enforcement, and missing/untranslated keys.
 *
 * Run with: npm run check:i18n
 */

const fs = require("fs");
const path = require("path");

const ROOT_DIR = path.resolve(__dirname, "..");
const ANDROID_RES_DIR = path.join(ROOT_DIR, "app/src/main/res");
const WEB_DIR = path.join(ROOT_DIR, "web");

// 1. Plain-Language Banned Technical Jargon
const BANNED_WORDS = [
  "hash",
  "sha-256",
  "e.164",
  "keystore",
  "sqlcipher",
  "payload",
  "token",
  "counterparty",
  "audit",
  "vpa",
  "principal",
  "amend",
  "ledger",
  "immutable",
  "cryptographic"
];

// 2. Technical / Formatting Allow-list for Untranslated Strings
const UNTRANSLATED_ALLOW_LIST = new Set([
  "app_name",
  "currency_symbol",
  "currency_symbol_npr",
  "currency_inr",
  "currency_npr",
  "default_web_client_id",
  "fcm_token_label",
  "opt_in_label",
  "opt_out_label",
  "dash",
  "bullet",
  "tab_all",
  "tab_given",
  "tab_taken",
  "ui_fmt_symbol_301919",
  "ui_fmt_symbol_992757",
  "ui_fmt_symbol_c8354b",
  "ui_fmt_symbol_df7ffb",
  "ui_fmt_symbol_e20cea",
  "ui_str_contact_example_com_8ff597",
  "qr_card_supported_apps_footer",
  "installment_seq_fmt",
  "ui_str_npr_e09e1a",
  "ui_str_symbol_5bab61"
]);

// 3. Approved Acronyms for ALL CAPS allow-list
const APPROVED_ACRONYMS = new Set([
  "UPI", "INR", "NPR", "OTP", "QR", "PDF", "UDHAAR", "BHIM", "OK", "SMS", "VCF", "CSV", "URL", "WEB", "UI", "ID", "KB", "MB"
]);

function parseXmlStrings(filePath) {
  const content = fs.readFileSync(filePath, "utf8");
  const regex = /<string\s+name=["']([^"']+)["'][^>]*>([\s\S]*?)<\/string>/g;
  const map = new Map();
  let m;
  while ((m = regex.exec(content)) !== null) {
    map.set(m[1], m[2]);
  }
  return map;
}

let totalErrors = 0;

console.log("=========================================================");
console.log("  Udhaar Internationalization & Plain-Language Validator  ");
console.log("=========================================================\n");

// --- SECTION 1: ANDROID STRING RESOURCES ---
console.log("▶ [1/3] Validating Android String Resources...");
const enXmlPath = path.join(ANDROID_RES_DIR, "values/strings.xml");
if (!fs.existsSync(enXmlPath)) {
  console.error("❌ Base strings.xml not found at:", enXmlPath);
  process.exit(1);
}

const enStrings = parseXmlStrings(enXmlPath);
console.log(`  Found ${enStrings.size} base English strings in values/strings.xml`);

// Check Android banned words in English strings
let androidBannedCount = 0;
for (const [key, val] of enStrings.entries()) {
  const text = val.toLowerCase();
  for (const banned of BANNED_WORDS) {
    if (new RegExp(`\\b${banned}\\b`, "i").test(text)) {
      console.error(`  ❌ Banned jargon word '${banned}' found in key '${key}': "${val}"`);
      androidBannedCount++;
      totalErrors++;
    }
  }
}
if (androidBannedCount === 0) {
  console.log("  ✓ Zero banned technical jargon words in English strings.xml");
}

// Check ALL CAPS strings in English
let allCapsCount = 0;
for (const [key, val] of enStrings.entries()) {
  const clean = val.replace(/<[^>]+>/g, "").replace(/%[0-9]\$[a-z]/g, "").trim();
  if (clean.length > 2 && clean === clean.toUpperCase() && /[A-Z]/.test(clean)) {
    // Check if it's strictly an approved acronym
    const words = clean.split(/\s+/);
    const nonApproved = words.filter((w) => !APPROVED_ACRONYMS.has(w.replace(/[^A-Z]/g, "")));
    if (nonApproved.length > 0) {
      console.error(`  ❌ ALL CAPS string found in key '${key}': "${val}" (convert to sentence case)`);
      allCapsCount++;
      totalErrors++;
    }
  }
}
if (allCapsCount === 0) {
  console.log("  ✓ Zero disallowed ALL CAPS strings (approved acronyms respected)");
}

// Check Regional Translations for Android
const androidLocales = ["hi", "bn", "as", "ne"];
for (const loc of androidLocales) {
  const locXmlPath = path.join(ANDROID_RES_DIR, `values-${loc}/strings.xml`);
  if (!fs.existsSync(locXmlPath)) {
    console.error(`  ❌ Missing translations file: values-${loc}/strings.xml`);
    totalErrors++;
    continue;
  }
  const locStrings = parseXmlStrings(locXmlPath);
  console.log(`  Checking locale '${loc}': ${locStrings.size} strings`);

  // Missing keys check
  let missing = 0;
  for (const key of enStrings.keys()) {
    if (!locStrings.has(key)) {
      console.error(`  ❌ Locale '${loc}' is missing key: ${key}`);
      missing++;
      totalErrors++;
    }
  }
  if (missing === 0) {
    console.log(`    ✓ Locale '${loc}' has 0 missing keys (100% coverage)`);
  }

  // Untranslated keys check
  let untranslated = 0;
  for (const [key, enVal] of enStrings.entries()) {
    const locVal = locStrings.get(key);
    if (!locVal) continue;
    const cleanEn = enVal.trim();
    const cleanLoc = locVal.trim();

    // Skip if it matches allow list, or is purely numeric/placeholders/symbols
    if (UNTRANSLATED_ALLOW_LIST.has(key)) continue;
    if (/^[0-9%₹रू\s\.\:\,\-\_\/\•\*\+]+$/.test(cleanEn)) continue;
    if (cleanEn.startsWith("http://") || cleanEn.startsWith("https://")) continue;
    if (/^%[0-9]\$[a-z]/.test(cleanEn) && cleanEn.length < 25) continue;

    if (cleanEn.toLowerCase() === cleanLoc.toLowerCase()) {
      console.error(`  ❌ Locale '${loc}' has untranslated English string for key '${key}': "${locVal}"`);
      untranslated++;
      totalErrors++;
    }
  }
  if (untranslated === 0) {
    console.log(`    ✓ Locale '${loc}' has 0 untranslated strings`);
  }
}

// --- SECTION 2: GUEST PAGE WEB DICTIONARY ---
console.log("\n▶ [2/3] Validating Guest Web Page Dictionary (web/u/guest.js)...");
const guestJsPath = path.join(WEB_DIR, "u/guest.js");
const guestModule = require(guestJsPath);
const guestI18n = guestModule.I18N;
const guestLocales = ["en", "hi", "bn", "as", "ne"];

if (!guestI18n || !guestI18n.en) {
  console.error("  ❌ Missing I18N.en in web/u/guest.js");
  totalErrors++;
} else {
  const guestEnKeys = Object.keys(guestI18n.en);
  console.log(`  Guest English dictionary has ${guestEnKeys.length} keys`);

  for (const loc of ["hi", "bn", "as", "ne"]) {
    const locDict = guestI18n[loc] || {};
    let missing = 0;
    let untranslated = 0;

    for (const key of guestEnKeys) {
      if (!locDict[key]) {
        console.error(`  ❌ Guest '${loc}' missing key: ${key}`);
        missing++;
        totalErrors++;
      } else {
        const enVal = guestI18n.en[key].trim();
        const locVal = locDict[key].trim();
        if (enVal === locVal && enVal.length > 3 && !/^[0-9%₹रू\s\.\:\,\-\_\/]+$/.test(enVal)) {
          if (!["UDHAAR", "WEB", "UPI", "UPI ID", "GPay", "PhonePe", "Paytm", "BHIM"].includes(enVal)) {
            console.error(`  ❌ Guest '${loc}' untranslated string for '${key}': "${locVal}"`);
            untranslated++;
            totalErrors++;
          }
        }
      }
    }
    if (missing === 0 && untranslated === 0) {
      console.log(`  ✓ Guest dictionary '${loc}' complete and translated (0 missing, 0 untranslated)`);
    }
  }
}

// --- SECTION 3: LANDING PAGE WEB DICTIONARY ---
console.log("\n▶ [3/4] Validating Landing Page Dictionary (web/landing.js)...");
const landingJsPath = path.join(WEB_DIR, "landing.js");
const landingModule = require(landingJsPath);
const landingI18n = landingModule.I18N;

if (!landingI18n || !landingI18n.en) {
  console.error("  ❌ Missing I18N.en in web/landing.js");
  totalErrors++;
} else {
  const landingEnKeys = Object.keys(landingI18n.en);
  console.log(`  Landing page English dictionary has ${landingEnKeys.length} keys`);

  for (const loc of ["hi", "bn", "as", "ne"]) {
    const locDict = landingI18n[loc] || {};
    let missing = 0;
    let untranslated = 0;

    for (const key of landingEnKeys) {
      if (!locDict[key]) {
        console.error(`  ❌ Landing '${loc}' missing key: ${key}`);
        missing++;
        totalErrors++;
      } else {
        const enVal = landingI18n.en[key].trim();
        const locVal = locDict[key].trim();
        if (enVal === locVal && enVal.length > 3 && !/^[0-9%₹रू\s\.\:\,\-\_\/]+$/.test(enVal)) {
          if (!["UDHAAR", "FAQ", "UPI", "GPay", "PhonePe", "Paytm", "1", "2", "3"].includes(enVal)) {
            console.error(`  ❌ Landing '${loc}' untranslated string for '${key}': "${locVal}"`);
            untranslated++;
            totalErrors++;
          }
        }
      }
    }
    if (missing === 0 && untranslated === 0) {
      console.log(`  ✓ Landing dictionary '${loc}' complete and translated (0 missing, 0 untranslated)`);
    }
  }
}

// --- SECTION 4: WEB LEDGER & INDIAN WORKFLOWS DICTIONARY ---
console.log("\n▶ [4/4] Validating Web Ledger & Indian UX Dictionary (web/ledger/ledger.js)...");
const ledgerJsPath = path.join(WEB_DIR, "ledger/ledger.js");
const ledgerModule = require(ledgerJsPath);
const ledgerI18n = ledgerModule.I18N;

if (!ledgerI18n || !ledgerI18n.en) {
  console.error("  ❌ Missing I18N.en in web/ledger/ledger.js");
  totalErrors++;
} else {
  const ledgerEnKeys = Object.keys(ledgerI18n.en);
  console.log(`  Web Ledger English dictionary has ${ledgerEnKeys.length} keys`);

  for (const loc of ["hi", "bn", "as", "ne"]) {
    const locDict = ledgerI18n[loc] || {};
    let missing = 0;
    let untranslated = 0;

    for (const key of ledgerEnKeys) {
      if (!locDict[key]) {
        console.error(`  ❌ Ledger '${loc}' missing key: ${key}`);
        missing++;
        totalErrors++;
      } else {
        const enVal = ledgerI18n.en[key].trim();
        const locVal = locDict[key].trim();
        if (enVal === locVal && enVal.length > 3 && !/^[0-9%₹रू\s\.\:\,\-\_\/]+$/.test(enVal)) {
          if (!["UDHAAR", "UPI", "UPI ID", "GPay", "PhonePe", "Paytm", "BHIM", "CSV", "WhatsApp", "SMS"].includes(enVal)) {
            console.error(`  ❌ Ledger '${loc}' untranslated string for '${key}': "${locVal}"`);
            untranslated++;
            totalErrors++;
          }
        }
      }
    }
    if (missing === 0 && untranslated === 0) {
      console.log(`  ✓ Ledger dictionary '${loc}' complete and translated (0 missing, 0 untranslated)`);
    }
  }
}

console.log("\n---------------------------------------------------------");
if (totalErrors === 0) {
  console.log("🎉 SUCCESS: All 5 languages verified! Zero missing keys, zero untranslated text, zero jargon!");
  process.exit(0);
} else {
  console.error(`❌ FAILURE: Found ${totalErrors} issue(s). Please review the logs above.`);
  process.exit(1);
}
