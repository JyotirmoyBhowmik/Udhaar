/**
 * Udhaar Platform v2 API entry point (/functions/src/api.js)
 * Exports createApiApp factory and default apiApp instance.
 */

const { createApiApp } = require("./app");

let defaultApp = null;
function getApiApp() {
  if (!defaultApp) {
    defaultApp = createApiApp();
  }
  return defaultApp;
}

module.exports = {
  createApiApp,
  get apiApp() {
    return getApiApp();
  }
};
