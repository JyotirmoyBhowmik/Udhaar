/**
 * OpenTelemetry-Compatible In-Process Tracing Engine
 * (/functions/src/observability/tracing.js)
 */

const crypto = require("crypto");

const recentSpans = [];

function clearSpans() {
  recentSpans.length = 0;
}

function getRecentSpans(limit = 50) {
  return recentSpans.slice(-limit);
}

class Span {
  constructor(name, options = {}) {
    this.name = name;
    this.traceId = options.traceId || crypto.randomBytes(16).toString("hex");
    this.spanId = crypto.randomBytes(8).toString("hex");
    this.attributes = { ...(options.attributes || {}) };
    this.status = "OK";
    this.startTime = Date.now();
  }

  setAttribute(key, value) {
    this.attributes[key] = value;
  }

  setStatus(status) {
    this.status = status;
  }

  end() {
    this.endTime = Date.now();
    this.durationMs = this.endTime - this.startTime;
    recentSpans.push(this);
  }
}

async function withSpan(name, options, fn) {
  if (typeof options === "function") {
    fn = options;
    options = {};
  }
  const span = new Span(name, options);
  try {
    const result = await fn(span);
    span.end();
    return result;
  } catch (err) {
    span.status = "ERROR";
    span.setAttribute("error", err.message);
    span.end();
    throw err;
  }
}

module.exports = {
  clearSpans,
  getRecentSpans,
  withSpan,
  Span
};
