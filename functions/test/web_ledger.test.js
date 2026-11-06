const test = require("node:test");
const assert = require("node:assert/strict");
const { I18N, formatRupees, calculateLedgerKPIs, filterRecords } = require("../../web/ledger/ledger.js");

test("Web Ledger I18N completeness across all 5 languages", () => {
  const baseKeys = Object.keys(I18N.en);
  assert.ok(baseKeys.length >= 50, "Expected at least 50 translation keys");

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

test("calculateLedgerKPIs computes lent, borrowed, net balance and pending count", () => {
  const records = [
    { counterpartyRole: "BORROWER", principalAmountPaise: 500000, status: "CONFIRMED" },
    { counterpartyRole: "LENDER", principalAmountPaise: 200000, status: "CONFIRMED" },
    { counterpartyRole: "BORROWER", principalAmountPaise: 300000, status: "PENDING_CONFIRMATION" }
  ];
  const kpis = calculateLedgerKPIs(records);
  assert.equal(kpis.totalLentRupees, 8000);
  assert.equal(kpis.totalBorrowedRupees, 2000);
  assert.equal(kpis.netRupees, 6000);
  assert.equal(kpis.pendingCount, 1);
  assert.equal(kpis.activeLentCount, 2);
  assert.equal(kpis.activeBorrowedCount, 1);
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
