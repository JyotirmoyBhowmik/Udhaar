#!/usr/bin/env node
/**
 * Udhaar Platform v2 — Send Test Transactional Email via Brevo API
 * (scripts/send-email-test.js)
 * 
 * Usage:
 *   node scripts/send-email-test.js <recipient_email> [sample_code]
 * 
 * Example:
 *   node scripts/send-email-test.js test@example.com 123456
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

const { TransactionalEmailProvider } = require("../functions/src/providers/email");

async function sendEmailTest(toEmail, code = "492815") {
  const apiKey = process.env.BREVO_API_KEY || process.env.EMAIL_PROVIDER_API_KEY;

  if (!apiKey) {
    console.error("❌ ERROR: BREVO_API_KEY not configured in .env.local");
    process.exit(1);
  }

  if (!toEmail) {
    console.error("Usage: node scripts/send-email-test.js <recipient_email> [sample_code]");
    process.exit(1);
  }

  console.log("======================================================");
  console.log("  Udhaar Brevo Email — Send Test Verification Code");
  console.log("======================================================");
  console.log(`To Recipient: ${toEmail}`);
  console.log(`Code Sample:  ${code}\n`);

  const provider = new TransactionalEmailProvider();

  try {
    const res = await provider.sendEmail({
      toEmail,
      code,
      locale: "en"
    });

    console.log("🎉 SUCCESS: Verification email dispatched successfully via Brevo!");
    console.log(`  ↳ Message ID: ${res.messageId}`);
  } catch (err) {
    console.error("❌ Send Email Failed:", err.message);
    if (err.message.includes("unrecognised IP address") || err.message.includes("401")) {
      console.error("\n💡 Action Required: Add your current public IP in Brevo Authorised IPs:");
      console.error("   https://app.brevo.com/security/authorised_ips");
    }
    process.exit(1);
  }
}

const recipient = process.argv[2];
const code = process.argv[3] || "492815";
sendEmailTest(recipient, code);
