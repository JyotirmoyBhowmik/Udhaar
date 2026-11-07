#!/usr/bin/env node
/**
 * Udhaar Platform v2 — Live External Provider Credential & Health Verifier
 * (scripts/verify-live-providers.js)
 * 
 * Tests live connectivity and authentication against:
 * 1. Meta WhatsApp Cloud API (Graph API v25.0)
 * 2. Brevo Transactional Email & Account API (v3)
 */

const fs = require("fs");
const path = require("path");

// Load local environment files if present
function loadEnvFile(filePath) {
  if (!fs.existsSync(filePath)) return;
  const content = fs.readFileSync(filePath, "utf8");
  content.split("\n").forEach((line) => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) return;
    const eqIdx = trimmed.indexOf("=");
    if (eqIdx !== -1) {
      const key = trimmed.slice(0, eqIdx).trim();
      const val = trimmed.slice(eqIdx + 1).trim();
      if (!process.env[key]) {
        process.env[key] = val;
      }
    }
  });
}

loadEnvFile(path.join(__dirname, "../functions/.env.local"));
loadEnvFile(path.join(__dirname, "../.env.local"));

async function testWhatsApp() {
  console.log("======================================================");
  console.log("  [1/2] Verifying Meta WhatsApp Cloud API Integration");
  console.log("======================================================");

  const phoneId = process.env.META_PHONE_NUMBER_ID || "1398055963399871";
  const token = process.env.META_WHATSAPP_TOKEN || process.env.WHATSAPP_API_TOKEN;
  const version = process.env.META_API_VERSION || "v25.0";

  if (!token) {
    console.error("❌ ERROR: META_WHATSAPP_TOKEN not found in environment.");
    return false;
  }

  const url = `https://graph.facebook.com/${version}/${phoneId}?fields=verified_name,display_phone_number,quality_rating,code_verification_status`;
  console.log(`Connecting to: https://graph.facebook.com/${version}/${phoneId}`);

  try {
    const res = await fetch(url, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    });

    const data = await res.json();
    if (!res.ok) {
      console.error(`❌ Meta WhatsApp API check failed (${res.status}):`, data.error?.message || data);
      return false;
    }

    console.log("✓ Meta WhatsApp Cloud API Authentication: SUCCESS");
    console.log(`  ↳ Phone Number ID:      ${data.id}`);
    console.log(`  ↳ Display Phone Number: ${data.display_phone_number}`);
    console.log(`  ↳ Verified Name:        ${data.verified_name || "Test Number"}`);
    console.log(`  ↳ Quality Rating:       ${data.quality_rating || "GREEN / UNRATED"}`);
    console.log(`  ↳ Verification Status:  ${data.code_verification_status || "REGISTERED"}`);
    return true;
  } catch (err) {
    console.error("❌ Network error contacting Meta WhatsApp API:", err.message);
    return false;
  }
}

async function testBrevo() {
  console.log("\n======================================================");
  console.log("  [2/2] Verifying Brevo (Sendinblue) Email API");
  console.log("======================================================");

  const apiKey = process.env.BREVO_API_KEY || process.env.EMAIL_PROVIDER_API_KEY;

  if (!apiKey) {
    console.error("❌ ERROR: BREVO_API_KEY not found in environment.");
    return false;
  }

  const url = "https://api.brevo.com/v3/account";
  console.log("Connecting to: https://api.brevo.com/v3/account");

  try {
    const res = await fetch(url, {
      method: "GET",
      headers: {
        "api-key": apiKey,
        "Accept": "application/json"
      }
    });

    const data = await res.json();
    if (!res.ok) {
      console.error(`❌ Brevo API check failed (${res.status}):`, data.message || data);
      return false;
    }

    console.log("✓ Brevo Transactional Email Authentication: SUCCESS");
    console.log(`  ↳ Account Email:     ${data.email}`);
    console.log(`  ↳ Company Name:      ${data.companyName || "Udhaar"}`);
    console.log(`  ↳ Plan Type:         ${data.plan?.[0]?.type || "FREE / PAY_AS_YOU_GO"}`);
    console.log(`  ↳ Credits Remaining: ${data.plan?.[0]?.credits !== undefined ? data.plan[0].credits : "Active"}`);
    return true;
  } catch (err) {
    console.error("❌ Network error contacting Brevo API:", err.message);
    return false;
  }
}

async function run() {
  const waOk = await testWhatsApp();
  const brevoOk = await testBrevo();

  console.log("\n------------------------------------------------------");
  if (waOk && brevoOk) {
    console.log("🎉 SUCCESS: Both Meta WhatsApp and Brevo APIs are ACTIVE and AUTHENTICATED!\n");
    process.exit(0);
  } else {
    console.warn("⚠️ Warning: One or more provider checks had issues.\n");
    process.exit(1);
  }
}

run();
