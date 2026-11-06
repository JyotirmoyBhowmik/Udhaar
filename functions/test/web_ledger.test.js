const test = require("node:test");
const assert = require("node:assert/strict");
const {
  I18N,
  formatRupees,
  numberToIndianWords,
  calculateLedgerKPIs,
  filterRecords,
  getRecordsStorageKey,
  validateName,
  validatePhone,
  validateAmount,
  validateDueDate,
  validateUpi,
  computeRecordHash
} = require("../../web/ledger/ledger.js");

test("Web Ledger I18N completeness across all 5 languages", () => {
  const baseKeys = Object.keys(I18N.en);
  assert.ok(baseKeys.length >= 70, "Expected at least 70 translation keys");

  for (const loc of ["hi", "bn", "as", "ne"]) {
    const locDict = I18N[loc];
    assert.ok(locDict, `Missing locale dictionary: ${loc}`);
    for (const key of baseKeys) {
      assert.ok(locDict[key], `Locale '${loc}' missing key: ${key}`);
      assert.ok(locDict[key].trim().length > 0, `Locale '${loc}' has empty string for key: ${key}`);
    }
  }
});

test("formatRupees formats amounts in Indian numbering with integer paise", () => {
  assert.equal(formatRupees(5000), "₹5,000");
  assert.equal(formatRupees(150000), "₹1,50,000");
  assert.equal(formatRupees(10000000), "₹1,00,00,000");
  assert.equal(formatRupees(5000.5), "₹5,000.50");
  assert.equal(formatRupees(-2500), "-₹2,500");
});

test("calculateLedgerKPIs computes lent, borrowed, net balance (positive and negative) and pending count", () => {
  // Test 1: Positive net balance (Asset)
  const recordsAsset = [
    { counterpartyRole: "BORROWER", principalAmountPaise: 500000, status: "CONFIRMED" },
    { counterpartyRole: "LENDER", principalAmountPaise: 200000, status: "CONFIRMED" },
    { counterpartyRole: "BORROWER", principalAmountPaise: 300000, status: "PENDING_CONFIRMATION" }
  ];
  const kpisAsset = calculateLedgerKPIs(recordsAsset);
  assert.equal(kpisAsset.totalLentRupees, 8000);
  assert.equal(kpisAsset.totalBorrowedRupees, 2000);
  assert.equal(kpisAsset.netRupees, 6000); // Positive asset: +₹6,000
  assert.equal(kpisAsset.pendingCount, 1);
  assert.equal(kpisAsset.activeLentCount, 2);
  assert.equal(kpisAsset.activeBorrowedCount, 1);

  // Test 2: Negative net balance (Liability)
  const recordsLiability = [
    { counterpartyRole: "BORROWER", principalAmountPaise: 100000, status: "CONFIRMED" },
    { counterpartyRole: "LENDER", principalAmountPaise: 450000, status: "CONFIRMED" }
  ];
  const kpisLiability = calculateLedgerKPIs(recordsLiability);
  assert.equal(kpisLiability.totalLentRupees, 1000);
  assert.equal(kpisLiability.totalBorrowedRupees, 4500);
  assert.equal(kpisLiability.netRupees, -3500); // Negative liability: -₹3,500

  // Test 3: Even net balance
  const recordsEven = [
    { counterpartyRole: "BORROWER", principalAmountPaise: 250000, status: "CONFIRMED" },
    { counterpartyRole: "LENDER", principalAmountPaise: 250000, status: "CONFIRMED" }
  ];
  const kpisEven = calculateLedgerKPIs(recordsEven);
  assert.equal(kpisEven.netRupees, 0);
});

test("filterRecords filters correctly by role, status, and query", () => {
  const records = [
    { partyName: "Ravi Sharma", counterpartyRole: "BORROWER", status: "CONFIRMED", note: "Groceries" },
    { partyName: "Amit Verma", counterpartyRole: "LENDER", status: "PENDING_CONFIRMATION", note: "Bike loan" },
    { partyName: "Pooja Singh", counterpartyRole: "BORROWER", status: "SETTLED", note: "Lunch" }
  ];

  assert.equal(filterRecords(records, "all", "").length, 3);
  assert.equal(filterRecords(records, "lent", "").length, 2);
  assert.equal(filterRecords(records, "borrowed", "").length, 1);
  assert.equal(filterRecords(records, "pending", "").length, 1);
  assert.equal(filterRecords(records, "confirmed", "").length, 2); // includes SETTLED
  assert.equal(filterRecords(records, "all", "Ravi").length, 1);
  assert.equal(filterRecords(records, "all", "bike").length, 1);
  assert.equal(filterRecords(records, "all", "nonexistent").length, 0);
});

test("Multi-user isolation: 100 distinct users have completely separated storage partitions", () => {
  const guestKey = getRecordsStorageKey(null);
  assert.equal(guestKey, "udhaar_web_ledger_records_guest");

  const keysSet = new Set();
  keysSet.add(guestKey);

  for (let i = 1; i <= 100; i++) {
    const uid = `usr_${i.toString().padStart(4, "0")}`;
    const userKey = getRecordsStorageKey(uid);
    assert.equal(userKey, `udhaar_web_ledger_records_${uid}`);
    assert.equal(keysSet.has(userKey), false, `Collision detected for user key: ${userKey}`);
    keysSet.add(userKey);
  }

  assert.equal(keysSet.size, 101, "All 100 user storage partitions and guest must be strictly unique");
});

test("Comprehensive Input Validation: Name, Phone, Amount, Due Date, and UPI ID", () => {
  // Name validation
  assert.equal(validateName("Ravi Sharma"), true);
  assert.equal(validateName("Dr. A. P. J. Abdul Kalam"), true);
  assert.equal(validateName("অমিত বৰ্মন"), true); // Assamese / Bengali script
  assert.equal(validateName("अमित शर्मा"), true); // Hindi script
  assert.equal(validateName("A"), false); // too short
  assert.equal(validateName(""), false);
  assert.equal(validateName("   "), false);
  assert.equal(validateName("12345"), false); // only numbers
  assert.equal(validateName("@#$!%"), false); // pure symbols

  // Mobile validation (10-digit Indian mobile, starts with 6-9)
  assert.equal(validatePhone("9876543210"), true);
  assert.equal(validatePhone("+91 98765 43210"), true);
  assert.equal(validatePhone("6123456789"), true);
  assert.equal(validatePhone("5123456789"), false); // starts with 5 (invalid Indian mobile)
  assert.equal(validatePhone("9999999999"), false); // dummy repeating digits
  assert.equal(validatePhone("1111111111"), false);
  assert.equal(validatePhone("1234567890"), false); // dummy sequential digits
  assert.equal(validatePhone("12345"), false); // too short

  // Amount validation (₹1 to ₹10,00,000)
  assert.equal(validateAmount(1), true);
  assert.equal(validateAmount(5000), true);
  assert.equal(validateAmount(1000000), true);
  assert.equal(validateAmount(0), false); // zero not allowed
  assert.equal(validateAmount(-500), false); // negative not allowed
  assert.equal(validateAmount(1000001), false); // exceeds max 10 Lakhs
  assert.equal(validateAmount(NaN), false);

  // Due date validation (today or future)
  const tomorrow = new Date(Date.now() + 86400000).toISOString().split("T")[0];
  const yesterday = new Date(Date.now() - 86400000).toISOString().split("T")[0];
  assert.equal(validateDueDate(tomorrow), true);
  assert.equal(validateDueDate(""), true); // optional
  assert.equal(validateDueDate(null), true);
  assert.equal(validateDueDate(yesterday), false); // past date rejected

  // UPI ID validation
  assert.equal(validateUpi("ravi@okhdfcbank"), true);
  assert.equal(validateUpi("john.doe@upi"), true);
  assert.equal(validateUpi("merchant_12@icici"), true);
  assert.equal(validateUpi(""), true); // optional
  assert.equal(validateUpi(null), true);
  assert.equal(validateUpi("invalid-upi-no-at"), false);
  assert.equal(validateUpi("bad@"), false);
});

test("Cryptographic Content Hash & Activity Audit Log Structure", async () => {
  const hash1 = await computeRecordHash("tx_1001:usr_test:9876543210:500000:INR:1762400000000");
  const hash2 = await computeRecordHash("tx_1001:usr_test:9876543210:500000:INR:1762400000000");
  const hash3 = await computeRecordHash("tx_1002:usr_test:9876543210:500000:INR:1762400000000");

  assert.equal(hash1, hash2, "Identical inputs must yield identical hash");
  assert.notEqual(hash1, hash3, "Different inputs must yield different hash");
  assert.ok(hash1.length >= 16, "Hash must have valid length");
});

test("numberToIndianWords generates accurate Indian wording across all 5 languages", () => {
  // Test 1: Zero and negative returns empty string (for clean UI placeholder)
  assert.equal(numberToIndianWords(0, "en"), "");
  assert.equal(numberToIndianWords(0, "hi"), "");
  assert.equal(numberToIndianWords(-50, "hi"), "");
  assert.equal(numberToIndianWords(null, "bn"), "");

  // Test 2: ₹500
  assert.equal(numberToIndianWords(500, "en"), "Five Hundred Rupees Only");
  assert.equal(numberToIndianWords(500, "hi"), "पाँच सौ रुपये मात्र");
  assert.equal(numberToIndianWords(500, "bn"), "পাঁচ শত টাকা মাত্র");
  assert.equal(numberToIndianWords(500, "as"), "পাঁচ শ টকা মাত্ৰ");
  assert.equal(numberToIndianWords(500, "ne"), "पाँच सय रुपैयाँ मात्र");

  // Test 3: ₹1,50,000 (1 Lakh 50 Thousand)
  assert.equal(numberToIndianWords(150000, "en"), "One Lakh Fifty Thousand Rupees Only");
  assert.equal(numberToIndianWords(150000, "hi"), "एक लाख पचास हज़ार रुपये मात्र");
  assert.equal(numberToIndianWords(150000, "bn"), "এক লাখ পঞ্চাশ হাজার টাকা মাত্র");
  assert.equal(numberToIndianWords(150000, "as"), "এক লাখ পঞ্চাশ হাজাৰ টকা মাত্ৰ");
  assert.equal(numberToIndianWords(150000, "ne"), "एक लाख पचास हजार रुपैयाँ मात्र");

  // Test 4: ₹1,00,00,000 (1 Crore)
  assert.equal(numberToIndianWords(10000000, "en"), "One Crore Rupees Only");
  assert.equal(numberToIndianWords(10000000, "hi"), "एक करोड़ रुपये मात्र");
  assert.equal(numberToIndianWords(10000000, "bn"), "এক কোটি টাকা মাত্র");
  assert.equal(numberToIndianWords(10000000, "as"), "এক কোটি টকা মাত্ৰ");
  assert.equal(numberToIndianWords(10000000, "ne"), "एक करोड रुपैयाँ मात्र");

  // Test 5: Fallback to English for unknown locales
  assert.equal(numberToIndianWords(1000, "fr"), "One Thousand Rupees Only");
});

test("Partial Repayments decrement remaining balance and update KPIs dynamically", () => {
  const recordsWithPayments = [
    {
      id: "tx_partial_1",
      counterpartyRole: "BORROWER", // Lent to customer
      principalAmountPaise: 1000000, // ₹10,000 original
      status: "CONFIRMED",
      payments: [
        { id: "pay_1", paise: 300000, amount: 3000, mode: "UPI", timestamp: 1762400000000 },
        { id: "pay_2", paise: 200000, amount: 2000, mode: "CASH", timestamp: 1762405000000 }
      ]
    },
    {
      id: "tx_partial_2",
      counterpartyRole: "LENDER", // Borrowed from supplier
      principalAmountPaise: 500000, // ₹5,000 original
      status: "CONFIRMED",
      payments: [
        { id: "pay_3", paise: 500000, amount: 5000, mode: "BANK_TRANSFER", timestamp: 1762410000000 }
      ] // Fully paid
    }
  ];

  const kpis = calculateLedgerKPIs(recordsWithPayments);

  // tx_partial_1: 10,000 - (3,000 + 2,000) = 5,000 remaining lent
  // tx_partial_2: 5,000 - 5,000 = 0 remaining borrowed
  assert.equal(kpis.totalLentRupees, 5000, "Lent KPI must reflect remaining balance of ₹5,000");
  assert.equal(kpis.totalBorrowedRupees, 0, "Borrowed KPI must reflect remaining balance of ₹0");
  assert.equal(kpis.netRupees, 5000, "Net KPI must reflect net remaining balance of +₹5,000");
});

