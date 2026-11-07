/**
 * Platform v2 Quotas & Size Limits (functions/src/config/quotas.js)
 * Default operational budgets per user account.
 */

const USER_QUOTAS = {
  MAX_RECORDS: 10000,
  MAX_PEOPLE: 5000,
  DEFAULT_PAGE_SIZE: 25,
  MAX_PAGE_SIZE: 100,
  MAX_NAME_LENGTH: 100,
  MAX_NOTE_LENGTH: 500,
  MAX_ITEMS_PER_RECORD: 50,
  MAX_ATTACHMENT_BYTES: 50 * 1024 * 1024, // 50 MB
  MAX_RECORD_SIZE_BYTES: 30 * 1024,       // 30 KB
  MAX_EVENT_SIZE_BYTES: 10 * 1024,        // 10 KB
  MAX_PLANS_PER_RECORD: 1,
  MAX_INSTALLMENTS_PER_PLAN: 120,
  DEK_CACHE_TTL_MS: 5 * 60 * 1000        // 5 minutes
};

module.exports = {
  USER_QUOTAS
};
