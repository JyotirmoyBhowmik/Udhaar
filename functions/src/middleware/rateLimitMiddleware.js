/**
 * Rate Limiting and Payload Sanitization Middleware
 * (/functions/src/middleware/rateLimitMiddleware.js)
 */

function createBucketStore() {
  let store = Object.create(null);
  return {
    get(k) { return store[k]; },
    set(k, v) { store[k] = v; },
    delete(k) { delete store[k]; },
    clear() { store = Object.create(null); }
  };
}

const ipAuthBuckets = createBucketStore();
const userBuckets = createBucketStore();

function resetRateLimits() {
  ipAuthBuckets.clear();
  userBuckets.clear();
}

function checkMaliciousKeys(obj) {
  if (!obj || typeof obj !== "object") return false;
  for (const key of Object.keys(obj)) {
    if (key.startsWith("$") || key === "__proto__" || key === "constructor" || key === "prototype") {
      return true;
    }
    if (typeof obj[key] === "object" && obj[key] !== null) {
      if (checkMaliciousKeys(obj[key])) return true;
    }
  }
  return false;
}

function maliciousPayloadGuard(req, res, next) {
  if (req.body && typeof req.body === "object") {
    if (checkMaliciousKeys(req.body)) {
      return res.status(400).json({
        type: "https://udhaar.app/errors/malicious-payload",
        title: "Bad Request",
        status: 400,
        detail: "Request contains forbidden keys or potential injection vectors.",
        error: {
          code: "ERR_MALICIOUS_PAYLOAD",
          message: "Request contains forbidden keys or potential injection vectors."
        }
      });
    }
  }
  next();
}

function rateLimitGuard(req, res, next) {
  const ip = req.headers["x-forwarded-for"] || req.ip || "127.0.0.1";
  const now = Date.now();
  const windowMs = 60 * 1000;

  if (req.path.includes("/auth/otp/send")) {
    const key = `otp_${ip}`;
    const entry = ipAuthBuckets.get(key) || { count: 0, resetAt: now + windowMs };
    if (now > entry.resetAt) {
      entry.count = 0;
      entry.resetAt = now + windowMs;
    }
    entry.count += 1;
    ipAuthBuckets.set(key, entry);

    if (entry.count > 15) {
      const retryAfterSec = Math.max(1, Math.ceil((entry.resetAt - now) / 1000));
      res.setHeader("Retry-After", String(retryAfterSec));
      return res.status(429).json({
        type: "https://udhaar.app/errors/rate-limited",
        title: "Rate Limit Exceeded",
        status: 429,
        detail: "Too many requests. Please try again later.",
        error: {
          code: "ERR_RATE_LIMITED",
          message: "Too many requests. Please try again later."
        }
      });
    }
  }

  next();
}

module.exports = {
  resetRateLimits,
  rateLimitGuard,
  maliciousPayloadGuard
};
