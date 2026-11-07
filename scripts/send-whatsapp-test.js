#!/usr/bin/env node
/**
 * Udhaar Platform v2 — Send Test WhatsApp Message via Meta Cloud API
 * (scripts/send-whatsapp-test.js)
 * 
 * Usage:
 *   node scripts/send-whatsapp-test.js <recipient_phone_number> [template_name]
 * 
 * Example:
 *   node scripts/send-whatsapp-test.js +919876543210
 */

const fs = require("fs");
const path = require("path");

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

async function sendWhatsAppTest(toPhone, templateName = "hello_world") {
  const phoneId = process.env.META_PHONE_NUMBER_ID || "1398055963399871";
  const token = process.env.META_WHATSAPP_TOKEN || process.env.WHATSAPP_API_TOKEN;
  const version = process.env.META_API_VERSION || "v25.0";

  if (!token) {
    console.error("❌ ERROR: META_WHATSAPP_TOKEN not configured in .env.local");
    process.exit(1);
  }

  if (!toPhone) {
    console.error("Usage: node scripts/send-whatsapp-test.js <recipient_phone> [template_name]");
    console.error("Note: Recipient must be added under 'Manage recipients' in Meta Developer Hub if using a test number.");
    process.exit(1);
  }

  const cleanTo = toPhone.replace(/[^0-9]/g, "");
  const url = `https://graph.facebook.com/${version}/${phoneId}/messages`;

  console.log("======================================================");
  console.log("  Udhaar WhatsApp Cloud API — Send Test Message");
  console.log("======================================================");
  console.log(`From Phone ID: ${phoneId}`);
  console.log(`To Recipient:  +${cleanTo}`);
  console.log(`Template:      ${templateName}`);
  console.log(`Endpoint:      ${url}\n`);

  const payload = {
    messaging_product: "whatsapp",
    to: cleanTo,
    type: "template",
    template: {
      name: templateName,
      language: {
        code: "en_US"
      }
    }
  };

  try {
    const res = await fetch(url, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });

    const data = await res.json();
    if (!res.ok) {
      console.error(`❌ Send Failed (${res.status}):`, JSON.stringify(data.error || data, null, 2));
      if (data.error?.code === 131030) {
        console.error("\n💡 Tip: When using Meta test number, recipient phone number must first be added to the 'Manage recipients' list in Meta Developer Hub.");
      }
      process.exit(1);
    }

    console.log("🎉 SUCCESS: Test message dispatched successfully!");
    console.log(`  ↳ Message ID: ${data.messages?.[0]?.id}`);
    console.log(`  ↳ Status:     ${data.contacts?.[0]?.wa_id ? "Delivered to WhatsApp user" : "Dispatched"}`);
  } catch (err) {
    console.error("❌ Network error:", err.message);
    process.exit(1);
  }
}

const recipient = process.argv[2];
const template = process.argv[3] || "hello_world";
sendWhatsAppTest(recipient, template);
