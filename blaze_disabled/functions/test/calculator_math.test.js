const test = require("node:test");
const assert = require("node:assert");
const calculator = require("../../web/calculator.js");

test("Web Repayment Calculator Math Tests (Integer Paise & Exact Splitting)", async (t) => {
  await t.test("1. Equal split with remainder absorption (e.g. ₹10,000 in 3 installments)", () => {
    const res = calculator.calculateSchedule({
      principal: 10000,
      interestType: "none",
      installmentCount: 3,
      frequency: "MONTHLY"
    });

    assert.strictEqual(res.principalPaise, 1000000);
    assert.strictEqual(res.totalPaise, 1000000);
    assert.strictEqual(res.installments.length, 3);

    // 1000000 / 3 = 333333 with remainder 1
    assert.strictEqual(res.installments[0].amountPaise, 333333);
    assert.strictEqual(res.installments[1].amountPaise, 333333);
    assert.strictEqual(res.installments[2].amountPaise, 333334);

    const sum = res.installments.reduce((acc, i) => acc + i.amountPaise, 0);
    assert.strictEqual(sum, res.totalPaise, "Sum of installments must match total paise exactly");
  });

  await t.test("2. Simple interest calculation matches integer paise bounds", () => {
    // ₹12,000 at 10% annual for 12 months = ₹1,200 interest -> ₹13,200 total in 4 installments
    const res = calculator.calculateSchedule({
      principal: 12000,
      interestType: "simple",
      interestRate: 10,
      installmentCount: 4,
      frequency: "MONTHLY"
    });

    assert.strictEqual(res.principalPaise, 1200000);
    // 4 months duration: 12000 * 10% * (4/12) = 400 rupees = 40000 paise
    assert.strictEqual(res.interestPaise, 40000);
    assert.strictEqual(res.totalPaise, 1240000);

    // 1240000 / 4 = 310000 each exactly
    assert.strictEqual(res.installments[0].amountPaise, 310000);
    assert.strictEqual(res.installments[1].amountPaise, 310000);
    assert.strictEqual(res.installments[2].amountPaise, 310000);
    assert.strictEqual(res.installments[3].amountPaise, 310000);

    const sum = res.installments.reduce((acc, i) => acc + i.amountPaise, 0);
    assert.strictEqual(sum, res.totalPaise);
  });

  await t.test("3. Compound interest calculation produces strictly whole paise", () => {
    const res = calculator.calculateSchedule({
      principal: 50000,
      interestType: "compound",
      interestRate: 12,
      installmentCount: 6,
      frequency: "MONTHLY"
    });

    assert.ok(Number.isInteger(res.interestPaise));
    assert.ok(Number.isInteger(res.totalPaise));
    assert.strictEqual(res.installments.length, 6);

    const sum = res.installments.reduce((acc, i) => acc + i.amountPaise, 0);
    assert.strictEqual(sum, res.totalPaise);
  });

  await t.test("4. Formatting helper produces clean localized rupee strings", () => {
    assert.strictEqual(calculator.formatCurrency(1500000), "₹15,000");
    assert.strictEqual(calculator.formatCurrency(333333), "₹3,333.33");
    assert.strictEqual(calculator.formatCurrency(50), "₹0.50");
  });
});
