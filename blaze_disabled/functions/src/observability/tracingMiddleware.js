/**
 * W3C Distributed Tracing & Request Telemetry Middleware
 * (functions/src/observability/tracingMiddleware.js)
 * Implements Enterprise Rule 4.2 (Distributed Tracing):
 * - Extracts and propagates standard W3C Trace Context (traceparent: 00-{traceId}-{spanId}-01).
 * - Extracts or mints unique Correlation-ID / Request-ID across every service layer.
 * - Records request latency and status into metrics accumulator.
 * - Emits structured JSON access logs.
 */

const crypto = require("crypto");
const logger = require("./logger");
const { recordHttpRequest } = require("./metrics");

const W3C_TRACEPARENT_REGEX = /^00-([0-9a-fA-F]{32})-([0-9a-fA-F]{16})-([0-9a-fA-F]{2})$/;

function generateHex(bytes) {
  return crypto.randomBytes(bytes).toString("hex");
}

function extractClientIp(req) {
  return (
    (req.headers["x-forwarded-for"] || "").split(",")[0].trim() ||
    req.ip ||
    req.socket?.remoteAddress ||
    "127.0.0.1"
  );
}

function tracingMiddleware(req, res, next) {
  const startNs = process.hrtime.bigint();

  // 1. Inbound W3C Traceparent extraction or generation
  const inboundTraceparent = req.headers.traceparent;
  let traceId;
  let parentSpanId = null;

  if (inboundTraceparent && W3C_TRACEPARENT_REGEX.test(inboundTraceparent)) {
    const match = inboundTraceparent.match(W3C_TRACEPARENT_REGEX);
    traceId = match[1].toLowerCase();
    parentSpanId = match[2].toLowerCase();
  } else {
    traceId = generateHex(16); // 16 bytes = 32 hex chars
  }

  // Generate current execution span ID
  const spanId = generateHex(8); // 8 bytes = 16 hex chars
  const outboundTraceparent = `00-${traceId}-${spanId}-01`;

  // 2. Correlation ID extraction or generation
  const correlationId =
    req.headers["x-correlation-id"] ||
    req.headers["x-request-id"] ||
    `corr_${Date.now()}_${generateHex(4)}`;

  // 3. Attach traceContext to request object
  req.traceContext = {
    traceparent: outboundTraceparent,
    traceId,
    spanId,
    parentSpanId,
    correlationId
  };
  req.correlationId = correlationId;

  // 4. Inject standard tracing response headers
  res.setHeader("traceparent", outboundTraceparent);
  res.setHeader("X-Correlation-Id", correlationId);
  res.setHeader("X-Request-Id", correlationId);

  // 5. Track request lifecycle on finish
  res.on("finish", () => {
    const endNs = process.hrtime.bigint();
    const durationMs = Number(endNs - startNs) / 1e6;
    const clientIp = extractClientIp(req);
    const fullPath = (req.baseUrl || "") + (req.path || req.url || "/");

    // Record SLI metrics
    recordHttpRequest({
      method: req.method,
      path: fullPath,
      statusCode: res.statusCode,
      durationMs
    });

    // Structured JSON logging
    const logCtx = {
      correlationId,
      traceId,
      spanId,
      functionName: `${req.method.toUpperCase()} ${fullPath}`,
      clientIp,
      statusCode: res.statusCode,
      durationMs: Number(durationMs.toFixed(2)),
      userAgent: req.headers["user-agent"] || ""
    };

    const msg = `${req.method} ${fullPath} ${res.statusCode} in ${durationMs.toFixed(2)}ms`;

    if (res.statusCode >= 500) {
      logger.error(msg, logCtx);
    } else if (res.statusCode >= 400) {
      logger.warn(msg, logCtx);
    } else {
      logger.info(msg, logCtx);
    }
  });

  next();
}

module.exports = {
  tracingMiddleware,
  extractClientIp
};
