/**
 * Udhaar Capability Link & WhatsApp Intent Service (Spark Mode)
 * (webapp/src/features/links/capabilityLinkService.js)
 * 
 * In Spark Mode:
 * 1. Links format: https://udhaar.jyotirmoyb.com/u/r/<recordId>#<secret>
 * 2. Secret: 128-bit cryptographically secure high-entropy token generated on device.
 * 3. linkId: SHA-256(secret) stored in Firestore /guestLinks/{linkId}.
 * 4. Sharing: Native WhatsApp intent (wa.me/?text=...) without Meta Business API.
 * 5. Zero photo uploads: Proofs are referenced by transaction reference number or text note.
 */

const { remoteConfigService } = require("../config/remoteConfigService");

/**
 * Generate a 128-bit random hex string.
 */
function generateSecretToken() {
  if (typeof crypto !== "undefined" && crypto.getRandomValues) {
    const bytes = new Uint8Array(16);
    crypto.getRandomValues(bytes);
    return Array.from(bytes).map(b => b.toString(16).padStart(2, "0")).join("");
  } else {
    const nodeCrypto = require("node:crypto");
    return nodeCrypto.randomBytes(16).toString("hex");
  }
}

/**
 * Compute SHA-256 hash of a string, returning lowercase hex string (64 characters).
 */
async function computeSha256(input) {
  if (typeof crypto !== "undefined" && crypto.subtle) {
    const enc = new TextEncoder();
    const hashBuffer = await crypto.subtle.digest("SHA-256", enc.encode(input));
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, "0")).join("");
  } else {
    const nodeCrypto = require("node:crypto");
    return nodeCrypto.createHash("sha256").update(input, "utf8").digest("hex");
  }
}

class CapabilityLinkService {
  constructor(firestoreInstance, authInstance, baseUrl = "https://udhaar.jyotirmoyb.com") {
    this.db = firestoreInstance;
    this.auth = authInstance;
    this.baseUrl = baseUrl.replace(/\/+$/, "");
  }

  /**
   * Create a capability guest link for a record.
   */
  async createCapabilityLink(record, options = {}) {
    if (!remoteConfigService.isGuestLinksEnabled()) {
      const lang = options.lang || "en";
      const pauseMsg = remoteConfigService.getGuestLinksDisabledMessage(lang);
      const err = new Error(pauseMsg);
      err.code = "ERR_GUEST_LINKS_DISABLED";
      throw err;
    }

    const user = this.auth?.currentUser;
    if (!user) throw new Error("Must be signed in to create guest capability links.");
    if (!user.emailVerified) {
      throw new Error("Must verify your email address before creating guest links.");
    }

    const secret = generateSecretToken();
    const linkId = await computeSha256(secret);
    const recordId = record.id || record.recordId;
    const ttlDays = options.ttlDays || 30;
    const expiresAtMs = Date.now() + ttlDays * 24 * 60 * 60 * 1000;

    const linkDoc = {
      linkId,
      recordId,
      ownerUid: user.uid,
      lenderName: record.lenderName || user.displayName || "Lender",
      borrowerName: record.partyName || record.borrowerName || "Borrower",
      counterpartyRole: record.counterpartyRole || "BORROWER",
      principalAmountPaise: Number(record.principalAmountPaise) || 0,
      dueDateMs: record.dueDateMs || null,
      status: "ACTIVE", // ACTIVE, REVOKED, EXPIRED
      createdAtMs: Date.now(),
      expiresAtMs
    };

    if (this.db) {
      await this.db.collection("guestLinks").doc(linkId).set(linkDoc);
    }

    const fullUrl = `${this.baseUrl}/u/r/${recordId}#${secret}`;

    return {
      linkId,
      secret,
      fullUrl,
      expiresAtMs,
      linkDoc
    };
  }

  /**
   * Revoke an active guest link.
   */
  async revokeCapabilityLink(linkId) {
    const user = this.auth?.currentUser;
    if (!user) throw new Error("Must be signed in to revoke links.");

    if (this.db) {
      await this.db.collection("guestLinks").doc(linkId).update({
        status: "REVOKED",
        revokedAtMs: Date.now()
      });
    }
    return { success: true, linkId, status: "REVOKED" };
  }

  /**
   * Build WhatsApp Intent URL to share capability link directly from user's WhatsApp.
   * Does NOT use Meta Business API or unofficial libraries.
   */
  buildWhatsAppShareUrl(fullUrl, { partyName, amountPaise, phone } = {}) {
    const amountRupees = amountPaise ? `₹${(amountPaise / 100).toLocaleString("en-IN")}` : "";
    const greeting = partyName ? `Namaste ${partyName}` : "Namaste";
    const amountText = amountRupees ? ` for ${amountRupees}` : "";
    
    const message = `${greeting}, please review and confirm our transaction record${amountText} on Udhaar:\n\n${fullUrl}\n\n(No app installation required. Tap the link to view details, confirm, or propose repayments.)`;

    let targetPhone = "";
    if (phone) {
      // Clean phone digits for wa.me link
      targetPhone = phone.replace(/[^0-9]/g, "");
    }

    if (targetPhone) {
      return `https://wa.me/${targetPhone}?text=${encodeURIComponent(message)}`;
    }
    return `https://wa.me/?text=${encodeURIComponent(message)}`;
  }

  /**
   * Lender Approval: Fetch pending guest actions via collection group query.
   */
  async fetchPendingActionsForOwner() {
    const user = this.auth?.currentUser;
    if (!user) return [];

    if (!this.db) return [];

    try {
      const snap = await this.db.collectionGroup("actions")
        .where("ownerUid", "==", user.uid)
        .get();

      return snap.docs.map(doc => ({
        actionId: doc.id,
        ...doc.data()
      }));
    } catch (err) {
      console.warn("Collection group query for actions failed:", err);
      return [];
    }
  }
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    generateSecretToken,
    computeSha256,
    CapabilityLinkService
  };
}
