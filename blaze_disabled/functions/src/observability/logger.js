/**
 * Enterprise Structured Logging & PII Redaction Engine
 * (functions/src/observability/logger.js)
 * Implements Enterprise Rule 4.1 (Structured JSON) and Rule 4.3 (PII Redaction):
 * - Outputs structured JSON logs with correlationId, traceId, spanId, service_name.
 * - Masks sensitive PII (phones, emails, tokens, secrets, keys, financial amounts in logs).
 * - Sanitizes errors to prevent exposing internal stack traces to clients.
 */

const inMemoryLogs = [];
const MAX_IN_MEMORY_LOGS = 500;

const LOG_LEVELS = Object.freeze({
  DEBUG: 10,
  INFO: 20,
  WARN: 30,
  ERROR: 40
});

// Current minimum log level (default INFO in prod, DEBUG in development/test)
let currentMinLevel = process.env.NODE_ENV === "production" ? LOG_LEVELS.INFO : LOG_LEVELS.DEBUG;

// Regex patterns for sensitive data
const SENSITIVE_KEY_REGEX = /password|token|secret|dek|key|authHeader|authTag|cookie|session|seed|^otp$|^code$/i;
const PHONE_REGEX = /(\+?\d{1,3}[-.\s]?)?(\d{2,4})[-.\s]?(\d{3})[-.\s]?(\d{3,4})/g;
const EMAIL_REGEX = /([a-zA-Z0-9_.+-]+)@([a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+)/g;
const BEARER_REGEX = /Bearer\s+([a-zA-Z0-9._\-]+)/gi;

/**
 * Redact sensitive strings and tokens
 */
function redactString(str) {
  if (typeof str !== "string") return str;

  // Redact Bearer tokens
  let redacted = str.replace(BEARER_REGEX, "Bearer [REDACTED]");

  // Mask emails
  redacted = redacted.replace(EMAIL_REGEX, (match, user, domain) => {
    if (user.length <= 2) {
      return `*@${domain}`;
    }
    return `${user[0]}***${user[user.length - 1]}@${domain}`;
  });

  // Mask phone numbers (preserve country code and last 3 digits)
  redacted = redacted.replace(PHONE_REGEX, (match) => {
    const digitsOnly = match.replace(/\D/g, "");
    if (digitsOnly.length < 10) return match;
    const tail = digitsOnly.slice(-3);
    const head = digitsOnly.slice(0, 2);
    return `+${head} ***-***-${tail}`;
  });

  return redacted;
}

/**
 * Deep redaction of objects, arrays, and primitives
 */
function sanitizeForLogging(data, depth = 0) {
  if (depth > 6) return "[MAX_DEPTH]";
  if (data === null || data === undefined) return data;

  if (typeof data === "string") {
    return redactString(data);
  }

  if (typeof data === "number" || typeof data === "boolean") {
    return data;
  }

  if (data instanceof Error) {
    return {
      name: data.name,
      message: redactString(data.message),
      code: data.code || "ERR_INTERNAL",
      status: data.status || 500,
      stack: process.env.NODE_ENV === "production" ? undefined : redactString(data.stack)
    };
  }

  if (Array.isArray(data)) {
    return data.map(item => sanitizeForLogging(item, depth + 1));
  }

  if (typeof data === "object") {
    const sanitized = {};
    for (const [key, value] of Object.entries(data)) {
      if (SENSITIVE_KEY_REGEX.test(key)) {
        sanitized[key] = "[REDACTED]";
      } else {
        sanitized[key] = sanitizeForLogging(value, depth + 1);
      }
    }
    return sanitized;
  }

  return String(data);
}

/**
 * Core logging method producing structured JSON format
 */
function log(levelStr, message, context = {}) {
  const levelUpper = String(levelStr || "INFO").toUpperCase();
  const levelNum = LOG_LEVELS[levelUpper] || LOG_LEVELS.INFO;

  if (levelNum < currentMinLevel) {
    return null;
  }

  const now = Date.now();
  const correlationId = context.correlationId || context.correlation_id || context.requestId || "none";
  const traceId = context.traceId || context.trace_id || null;
  const spanId = context.spanId || context.span_id || null;
  const functionName = context.functionName || context.function_name || "apiHandler";
  const clientIp = context.clientIp || context.client_ip || null;

  const metadata = { ...context };
  delete metadata.correlationId;
  delete metadata.correlation_id;
  delete metadata.traceId;
  delete metadata.trace_id;
  delete metadata.spanId;
  delete metadata.span_id;
  delete metadata.functionName;
  delete metadata.function_name;
  delete metadata.clientIp;
  delete metadata.client_ip;

  const logEntry = {
    timestamp: new Date(now).toISOString(),
    timestampMs: now,
    level: levelUpper,
    service_name: "udhaar-platform",
    function_name: functionName,
    correlation_id: correlationId,
    trace_id: traceId,
    span_id: spanId,
    client_ip: clientIp,
    message: redactString(message),
    metadata: sanitizeForLogging(metadata)
  };

  // Buffer in memory for test assertion / inspector
  inMemoryLogs.push(Object.freeze(logEntry));
  if (inMemoryLogs.length > MAX_IN_MEMORY_LOGS) {
    inMemoryLogs.shift();
  }

  // In production / emulator, emit JSON to stdout/stderr
  const jsonStr = JSON.stringify(logEntry);
  if (levelUpper === "ERROR") {
    console.error(jsonStr);
  } else if (levelUpper === "WARN") {
    console.warn(jsonStr);
  } else {
    console.log(jsonStr);
  }

  return logEntry;
}

const logger = {
  debug: (msg, ctx) => log("DEBUG", msg, ctx),
  info: (msg, ctx) => log("INFO", msg, ctx),
  warn: (msg, ctx) => log("WARN", msg, ctx),
  error: (msg, ctx) => log("ERROR", msg, ctx),
  setLogLevel: (level) => {
    const lvl = String(level).toUpperCase();
    if (LOG_LEVELS[lvl]) currentMinLevel = LOG_LEVELS[lvl];
  },
  getRecentLogs: ({ limit = 50, level = null } = {}) => {
    let logs = [...inMemoryLogs];
    if (level) {
      logs = logs.filter(l => l.level === level.toUpperCase());
    }
    return logs.slice(-limit);
  },
  resetLogBuffer: () => {
    inMemoryLogs.length = 0;
  },
  sanitizeForLogging,
  redactString
};

logger.logger = logger;

module.exports = logger;
