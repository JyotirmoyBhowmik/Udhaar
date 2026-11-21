/**
 * Udhaar Web Guest Portal — Action Schema Builders & Validators (web/u/actionSchema.js)
 *
 * Implements strict payload construction matching `firestore.rules` under `/guestLinks/{linkId}/actions/{actionType}`.
 * Free rate-limiting is enforced by document ID (`actionType` in uppercase: CONFIRM, DISPUTE, PROPOSE_DATE, CLAIM_PAYMENT).
 * All actions are created with status = "PENDING" and schemaVersion = 1.
 */

(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
  } else {
    root.UdhaarActionSchema = factory();
  }
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const ALLOWED_PAYMENT_MODES = ["UPI", "CASH", "BANK_TRANSFER"];
  const UPI_REGEX = /^[\w.\-]{2,256}@[a-zA-Z]{2,64}$/;

  /**
   * Validates if a string is a valid UPI VPA handle.
   */
  function isValidUpi(upi) {
    if (!upi || typeof upi !== "string") return false;
    return UPI_REGEX.test(upi.trim());
  }

  /**
   * Masks a UPI ID for display to protect privacy (e.g. jyo***@okaxis).
   */
  function maskUpi(upi) {
    if (!upi || typeof upi !== "string") return "—";
    const trimmed = upi.trim();
    const parts = trimmed.split("@");
    if (parts.length !== 2) return trimmed;
    const handle = parts[0];
    const prov = parts[1];
    if (handle.length <= 2) {
      return handle + "***@" + prov;
    }
    return handle.substring(0, 3) + "***@" + prov;
  }

  /**
   * Converts a rupee amount (number or string) into an integer number of paise.
   * Throws an error on non-positive, NaN, infinite, or malformed input.
   */
  function rupeesToPaise(val) {
    if (val === null || val === undefined || val === "") {
      throw new Error("Amount is required.");
    }
    const num = Number(val);
    if (isNaN(num) || !isFinite(num)) {
      throw new Error("Invalid amount.");
    }
    if (num <= 0) {
      throw new Error("Amount must be greater than zero.");
    }
    const paise = Math.round(num * 100);
    if (!Number.isSafeInteger(paise) || paise <= 0) {
      throw new Error("Amount exceeds allowable range.");
    }
    return paise;
  }

  /**
   * Converts integer paise to floating rupees.
   */
  function paiseToRupees(paise) {
    if (typeof paise !== "number" || !Number.isInteger(paise) || paise < 0) {
      throw new Error("Paise must be a non-negative integer.");
    }
    return paise / 100;
  }

  /**
   * Builds an RFC/NPCI compliant UPI URI intent string.
   */
  function buildUpiUri({ upiId, payeeName, amountPaise, note }) {
    if (!isValidUpi(upiId)) {
      throw new Error("Valid UPI ID is required.");
    }
    const cleanName = encodeURIComponent(String(payeeName || "Udhaar").trim().slice(0, 50));
    const cleanUpi = upiId.trim();
    let uri = `upi://pay?pa=${cleanUpi}&pn=${cleanName}&cu=INR`;
    if (typeof amountPaise === "number" && amountPaise > 0) {
      const amt = (amountPaise / 100).toFixed(2);
      uri += `&am=${amt}`;
    }
    const cleanNote = encodeURIComponent(String(note || "Udhaar Settlement").trim().slice(0, 50));
    uri += `&tn=${cleanNote}`;
    return uri;
  }

  /**
   * Builds a CONFIRM proposal payload.
   * Allowed keys: ['schemaVersion', 'actionType', 'guestUid', 'status', 'createdAtMs']
   */
  function buildConfirmAction({ guestUid, nowMs }) {
    if (!guestUid || typeof guestUid !== "string") {
      throw new Error("Guest authentication UID is required for CONFIRM action.");
    }
    const createdAtMs = typeof nowMs === "number" && nowMs > 0 ? Math.floor(nowMs) : Date.now();
    return {
      schemaVersion: 1,
      actionType: "CONFIRM",
      guestUid: guestUid.trim(),
      status: "PENDING",
      createdAtMs
    };
  }

  /**
   * Builds a DISPUTE proposal payload.
   * Allowed keys: ['schemaVersion', 'actionType', 'guestUid', 'status', 'createdAtMs', 'reason']
   */
  function buildDisputeAction({ guestUid, reason, nowMs }) {
    if (!guestUid || typeof guestUid !== "string") {
      throw new Error("Guest authentication UID is required for DISPUTE action.");
    }
    const cleanReason = String(reason || "").trim().slice(0, 500);
    if (!cleanReason) {
      throw new Error("Dispute reason is required (up to 500 characters).");
    }
    const createdAtMs = typeof nowMs === "number" && nowMs > 0 ? Math.floor(nowMs) : Date.now();
    return {
      schemaVersion: 1,
      actionType: "DISPUTE",
      guestUid: guestUid.trim(),
      status: "PENDING",
      createdAtMs,
      reason: cleanReason
    };
  }

  /**
   * Builds a PROPOSE_DATE proposal payload.
   * Allowed keys: ['schemaVersion', 'actionType', 'guestUid', 'status', 'createdAtMs', 'proposedDateMs', 'reason']
   */
  function buildProposeDateAction({ guestUid, proposedDateMs, reason, nowMs }) {
    if (!guestUid || typeof guestUid !== "string") {
      throw new Error("Guest authentication UID is required for PROPOSE_DATE action.");
    }
    const createdAtMs = typeof nowMs === "number" && nowMs > 0 ? Math.floor(nowMs) : Date.now();
    const cleanProposedDate = Math.floor(Number(proposedDateMs));
    if (isNaN(cleanProposedDate) || cleanProposedDate <= createdAtMs) {
      throw new Error("Proposed date must be a valid future timestamp.");
    }

    const payload = {
      schemaVersion: 1,
      actionType: "PROPOSE_DATE",
      guestUid: guestUid.trim(),
      status: "PENDING",
      createdAtMs,
      proposedDateMs: cleanProposedDate
    };

    const cleanReason = String(reason || "").trim().slice(0, 500);
    if (cleanReason) {
      payload.reason = cleanReason;
    }

    return payload;
  }

  /**
   * Builds a CLAIM_PAYMENT proposal payload.
   * Allowed keys: ['schemaVersion', 'actionType', 'guestUid', 'status', 'createdAtMs',
   *                'amountPaise', 'paymentMode', 'referenceNumber', 'note', 'hasProof']
   */
  function buildClaimPaymentAction({
    guestUid,
    amountPaise,
    paymentMode,
    referenceNumber,
    note,
    hasProof,
    payableAmountPaise,
    nowMs
  }) {
    if (!guestUid || typeof guestUid !== "string") {
      throw new Error("Guest authentication UID is required for CLAIM_PAYMENT action.");
    }

    const createdAtMs = typeof nowMs === "number" && nowMs > 0 ? Math.floor(nowMs) : Date.now();
    const parsedAmount = Math.round(Number(amountPaise));

    if (isNaN(parsedAmount) || parsedAmount <= 0 || !Number.isInteger(parsedAmount)) {
      throw new Error("Amount must be a positive integer in paise.");
    }

    if (typeof payableAmountPaise === "number" && payableAmountPaise > 0) {
      if (parsedAmount > payableAmountPaise) {
        throw new Error(
          `Claimed amount (₹${(parsedAmount / 100).toFixed(2)}) cannot exceed payable amount (₹${(payableAmountPaise / 100).toFixed(2)}).`
        );
      }
    }

    const rawMode = String(paymentMode || "UPI").trim().toUpperCase();
    const cleanMode = ALLOWED_PAYMENT_MODES.includes(rawMode) ? rawMode : "UPI";

    const cleanRef = String(referenceNumber || "").trim().slice(0, 64);
    if (!cleanRef) {
      throw new Error("Reference number / UTR is required (up to 64 characters).");
    }

    const payload = {
      schemaVersion: 1,
      actionType: "CLAIM_PAYMENT",
      guestUid: guestUid.trim(),
      status: "PENDING",
      createdAtMs,
      amountPaise: parsedAmount,
      paymentMode: cleanMode,
      referenceNumber: cleanRef,
      hasProof: Boolean(hasProof)
    };

    const cleanNote = String(note || "").trim().slice(0, 300);
    if (cleanNote) {
      payload.note = cleanNote;
    }

    return payload;
  }

  /**
   * Validates an action payload against the rules definition for actionType.
   */
  function validateAction(actionType, doc, link) {
    if (!doc || typeof doc !== "object") return { valid: false, error: "Payload must be an object." };
    if (doc.schemaVersion !== 1) return { valid: false, error: "schemaVersion must be 1." };
    if (doc.actionType !== actionType) return { valid: false, error: `actionType mismatch: ${doc.actionType} !== ${actionType}.` };
    if (doc.status !== "PENDING") return { valid: false, error: "Initial action status must be PENDING." };
    if (typeof doc.createdAtMs !== "number" || !Number.isInteger(doc.createdAtMs)) {
      return { valid: false, error: "createdAtMs must be an integer timestamp." };
    }
    if (!doc.guestUid || typeof doc.guestUid !== "string") {
      return { valid: false, error: "guestUid is required." };
    }

    if (link && Array.isArray(link.allowedActions) && !link.allowedActions.includes(actionType)) {
      return { valid: false, error: `Action '${actionType}' is not allowed for this link.` };
    }

    const keys = Object.keys(doc);
    if (actionType === "CONFIRM") {
      const allowed = ["schemaVersion", "actionType", "guestUid", "status", "createdAtMs"];
      const extra = keys.filter((k) => !allowed.includes(k));
      if (extra.length > 0) return { valid: false, error: `Disallowed keys for CONFIRM: ${extra.join(", ")}.` };
    } else if (actionType === "DISPUTE") {
      const allowed = ["schemaVersion", "actionType", "guestUid", "status", "createdAtMs", "reason"];
      const extra = keys.filter((k) => !allowed.includes(k));
      if (extra.length > 0) return { valid: false, error: `Disallowed keys for DISPUTE: ${extra.join(", ")}.` };
      if (typeof doc.reason !== "string" || doc.reason.length > 500) {
        return { valid: false, error: "Dispute reason must be a string up to 500 chars." };
      }
    } else if (actionType === "PROPOSE_DATE") {
      const allowed = ["schemaVersion", "actionType", "guestUid", "status", "createdAtMs", "proposedDateMs", "reason"];
      const extra = keys.filter((k) => !allowed.includes(k));
      if (extra.length > 0) return { valid: false, error: `Disallowed keys for PROPOSE_DATE: ${extra.join(", ")}.` };
      if (typeof doc.proposedDateMs !== "number" || !Number.isInteger(doc.proposedDateMs)) {
        return { valid: false, error: "proposedDateMs must be an integer timestamp." };
      }
      if ("reason" in doc && (typeof doc.reason !== "string" || doc.reason.length > 500)) {
        return { valid: false, error: "reason must be a string up to 500 chars." };
      }
    } else if (actionType === "CLAIM_PAYMENT") {
      const allowed = [
        "schemaVersion",
        "actionType",
        "guestUid",
        "status",
        "createdAtMs",
        "amountPaise",
        "paymentMode",
        "referenceNumber",
        "note",
        "hasProof"
      ];
      const extra = keys.filter((k) => !allowed.includes(k));
      if (extra.length > 0) return { valid: false, error: `Disallowed keys for CLAIM_PAYMENT: ${extra.join(", ")}.` };
      if (typeof doc.amountPaise !== "number" || !Number.isInteger(doc.amountPaise) || doc.amountPaise <= 0) {
        return { valid: false, error: "amountPaise must be a positive integer." };
      }
      if (link && typeof link.payableAmountPaise === "number" && doc.amountPaise > link.payableAmountPaise) {
        return { valid: false, error: "amountPaise exceeds payableAmountPaise." };
      }
      if (!ALLOWED_PAYMENT_MODES.includes(doc.paymentMode)) {
        return { valid: false, error: `paymentMode must be one of: ${ALLOWED_PAYMENT_MODES.join(", ")}.` };
      }
      if (typeof doc.referenceNumber !== "string" || doc.referenceNumber.length > 64) {
        return { valid: false, error: "referenceNumber must be a string up to 64 chars." };
      }
      if ("note" in doc && (typeof doc.note !== "string" || doc.note.length > 300)) {
        return { valid: false, error: "note must be a string up to 300 chars." };
      }
    } else {
      return { valid: false, error: `Unknown actionType: ${actionType}.` };
    }

    return { valid: true };
  }

  return {
    ALLOWED_PAYMENT_MODES,
    UPI_REGEX,
    isValidUpi,
    maskUpi,
    rupeesToPaise,
    paiseToRupees,
    buildUpiUri,
    buildConfirmAction,
    buildDisputeAction,
    buildProposeDateAction,
    buildClaimPaymentAction,
    validateAction
  };
});
