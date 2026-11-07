/**
 * Communication Providers Factory (functions/src/providers/index.js)
 * Resolves active or fake communication providers based on environment.
 */

const { FakeWhatsAppProvider, WhatsAppCloudApiProvider } = require("./whatsapp");
const { FakeEmailProvider, TransactionalEmailProvider } = require("./email");
const { FakeSmsProvider, IndianDltSmsProvider } = require("./sms");
const { isProductionOrStaging } = require("../config/environmentGuard");

const fakeWhatsApp = new FakeWhatsAppProvider();
const fakeEmail = new FakeEmailProvider();
const fakeSms = new FakeSmsProvider();

const prodWhatsApp = new WhatsAppCloudApiProvider();
const prodEmail = new TransactionalEmailProvider();
const prodSms = new IndianDltSmsProvider();

function isTestOrEmulator() {
  if (isProductionOrStaging()) {
    return false;
  }
  return (
    process.env.NODE_ENV === "test" ||
    process.env.USE_FAKE_PROVIDERS === "true" ||
    process.env.FUNCTIONS_EMULATOR === "true" ||
    process.env.FIREBASE_EMULATOR_HUB !== undefined ||
    (Array.isArray(process.argv) && process.argv.some(arg => typeof arg === "string" && (arg.includes("test") || arg.endsWith(".test.js"))))
  );
}

function getWhatsAppProvider() {
  return isTestOrEmulator() ? fakeWhatsApp : prodWhatsApp;
}

function getEmailProvider() {
  return isTestOrEmulator() ? fakeEmail : prodEmail;
}

function getSmsProvider() {
  return isTestOrEmulator() ? fakeSms : prodSms;
}

module.exports = {
  isTestOrEmulator,
  getWhatsAppProvider,
  getEmailProvider,
  getSmsProvider,
  fakeWhatsApp,
  fakeEmail,
  fakeSms
};
