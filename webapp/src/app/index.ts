/**
 * Udhaar Web App Entry Point (webapp/src/app/index.ts)
 * Stage S0 Skeleton
 */

export const APP_VERSION = "2.0.0";

export function initializeApp(): void {
  console.info(`[Udhaar] Platform v${APP_VERSION} initialized in local-first replica mode.`);
}

if (typeof document !== "undefined") {
  document.addEventListener("DOMContentLoaded", () => {
    initializeApp();
  });
}
