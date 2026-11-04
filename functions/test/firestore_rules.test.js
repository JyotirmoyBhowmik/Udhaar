const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

test("Firestore Security Rules Stage W1 Verification", async (t) => {
  const rulesPath = path.resolve(__dirname, "../../firestore.rules");
  const rulesContent = fs.readFileSync(rulesPath, "utf8");

  await t.test("1. webLinks collection must be strictly blocked to clients (allow read, write: if false)", () => {
    assert.match(
      rulesContent,
      /match\s+\/webLinks\/\{recordId\}\s*\{\s*allow\s+read,\s*write:\s*if\s+false;\s*\}/,
      "webLinks must reject all direct client reads and writes"
    );
  });

  await t.test("2. Creator self-confirmation must be strictly blocked", () => {
    // Verifies that when verificationStatus is CONFIRMED, creatorUid cannot match request.auth.uid
    assert.match(
      rulesContent,
      /request\.auth\.uid\s*!=\s*existing\(\)\.creatorUid/,
      "Rules must explicitly block the record creator from confirming their own record"
    );
  });

  await t.test("3. Terms modification must require EDITED_AFTER_CONFIRM status", () => {
    assert.match(
      rulesContent,
      /incoming\(\)\.verificationStatus\s*==\s*'EDITED_AFTER_CONFIRM'/,
      "Any modifications to core terms must reset verification status to EDITED_AFTER_CONFIRM"
    );
  });

  await t.test("4. Audit trail immutability: clients cannot write or tamper with /audit/{eventId}", () => {
    assert.match(
      rulesContent,
      /match\s+\/audit\/\{eventId\}\s*\{[\s\S]*?allow\s+write:\s*if\s+false;/,
      "Direct client writes to audit trail must be strictly denied"
    );
  });

  await t.test("5. Payment claims & Date proposals immutability for direct client writes", () => {
    assert.match(
      rulesContent,
      /match\s+\/payment_claims\/\{claimId\}\s*\{[\s\S]*?allow\s+write:\s*if\s+false;/,
      "Payment claims direct writes must be denied (written via Cloud Functions Admin SDK)"
    );
    assert.match(
      rulesContent,
      /match\s+\/date_proposals\/\{proposalId\}\s*\{[\s\S]*?allow\s+write:\s*if\s+false;/,
      "Date proposals direct writes must be denied (written via Cloud Functions Admin SDK)"
    );
  });

  await t.test("6. Inbox notifications collection controls", () => {
    assert.match(
      rulesContent,
      /match\s+\/inbox\/\{inboxId\}\s*\{[\s\S]*?allow\s+create:\s*if\s+false;/,
      "Direct client create to inbox notifications must be denied"
    );
    assert.match(
      rulesContent,
      /affectedKeys\(\)\.hasOnly\(\['isRead',\s*'readAtMillis',\s*'updatedAt'\]\)/,
      "Updates to inbox notifications restricted strictly to read state markers"
    );
  });

  await t.test("7. Schema extension for Guest Web Flow & Trust Badges", () => {
    const requiredFields = [
      "trustLevel",
      "verifiedPhoneMasked",
      "webLinkStatus",
      "guestUpiId",
      "lastGuestAction",
      "pendingPaymentClaim",
      "pendingDateProposal",
    ];

    for (const field of requiredFields) {
      assert.strictEqual(
        rulesContent.includes(field),
        true,
        `Field '${field}' must be defined and validated in firestore.rules`
      );
    }
  });
});
