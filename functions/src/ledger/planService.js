/**
 * Ledger Installment Plans Service (functions/src/ledger/planService.js)
 * Implements installment schedule generation with exact integer paise splitting,
 * remainder absorption, and installment payment tracking.
 * Backed by persistent repositories with zero module-level Map storage.
 */

const crypto = require("crypto");
const { getRecord } = require("./recordService");
const { recordPayment } = require("./paymentService");
const { getLedgerRepository } = require("../repositories");

const proxyMap = (getMapFn) => new Proxy({}, {
  get(target, prop) {
    const repo = getLedgerRepository();
    const map = (repo && repo.plans) || {};
    if (typeof map[prop] === "function") {
      return map[prop].bind(map);
    }
    return map[prop];
  }
});

const _inMemoryPlans = proxyMap(() => getLedgerRepository().plans);

/**
 * Generate installment schedule with integer paise division and remainder absorption
 */
function calculateInstallmentSchedule(principalPaise, count, startDateMs, frequency = "MONTHLY") {
  const basePaise = Math.floor(principalPaise / count);
  const remainderPaise = principalPaise % count;

  const intervalMs = frequency === "WEEKLY" ? 7 * 86400000 : 30 * 86400000;
  const installments = [];

  for (let i = 0; i < count; i++) {
    // Absorb remainder onto the first installment
    const amountPaise = i === 0 ? basePaise + remainderPaise : basePaise;
    const dueDateMs = startDateMs + (i + 1) * intervalMs;

    installments.push({
      installmentId: `inst_${i + 1}_${crypto.randomBytes(3).toString("hex")}`,
      sequence: i + 1,
      amountPaise,
      dueDateMs,
      status: "PENDING",
      paidAtMs: null
    });
  }

  return installments;
}

/**
 * Create an installment plan for an existing record
 */
async function createInstallmentPlan({ uid, recordId, totalInstallments = 3, startDateMs = null, frequency = "MONTHLY" }) {
  const count = Number(totalInstallments);
  if (!Number.isInteger(count) || count < 2 || count > 120) {
    const err = new Error("Total installments must be an integer between 2 and 120");
    err.code = "ERR_INVALID_INSTALLMENT_COUNT";
    err.status = 400;
    throw err;
  }

  const record = await getRecord({ uid, recordId });
  const start = Number(startDateMs) || record.createdAtMs || Date.now();

  const repo = getLedgerRepository();
  const existingPlans = await repo.listPlans(uid);
  const existingForRecord = existingPlans.find(p => p.recordId === recordId);
  if (existingForRecord) {
    const err = new Error("An installment plan already exists for this record");
    err.code = "ERR_PLAN_ALREADY_EXISTS";
    err.status = 400;
    throw err;
  }

  const planId = `pln_${Date.now()}_${crypto.randomBytes(4).toString("hex")}`;
  const installments = calculateInstallmentSchedule(record.principalAmountPaise, count, start, frequency);

  const plan = {
    planId,
    id: planId,
    recordId,
    totalInstallments: count,
    frequency,
    startDateMs: start,
    installments,
    createdAtMs: Date.now()
  };

  await repo.createPlan(uid, plan);
  return plan;
}

/**
 * Get installment plan for a record
 */
async function getPlanByRecordId({ uid, recordId }) {
  const repo = getLedgerRepository();
  const plans = await repo.listPlans(uid);
  const plan = plans.find(p => p.recordId === recordId);
  if (!plan) {
    const err = new Error(`No installment plan found for record ${recordId}`);
    err.code = "ERR_PLAN_NOT_FOUND";
    err.status = 404;
    throw err;
  }
  return plan;
}

/**
 * Pay a specific installment
 */
async function payInstallment({ uid, planId, installmentId, paymentMode = "UPI" }) {
  const repo = getLedgerRepository();
  const plan = await repo.getPlan(uid, planId);

  if (!plan) {
    const err = new Error(`Plan ${planId} not found`);
    err.code = "ERR_PLAN_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  const inst = plan.installments.find(i => i.installmentId === installmentId);
  if (!inst) {
    const err = new Error(`Installment ${installmentId} not found`);
    err.code = "ERR_INSTALLMENT_NOT_FOUND";
    err.status = 404;
    throw err;
  }

  if (inst.status === "PAID") {
    const err = new Error("This installment is already paid");
    err.code = "ERR_ALREADY_PAID";
    err.status = 400;
    throw err;
  }

  // Deduct from record
  const paymentRes = await recordPayment({
    uid,
    recordId: plan.recordId,
    amountPaise: inst.amountPaise,
    paymentMode,
    note: `Installment #${inst.sequence} of ${plan.totalInstallments}`
  });

  inst.status = "PAID";
  inst.paidAtMs = Date.now();

  await repo.updatePlan(uid, planId, { installments: plan.installments });

  return {
    success: true,
    installment: inst,
    payment: paymentRes
  };
}

function resetPlansStore() {
  const repo = getLedgerRepository();
  if (repo && typeof repo.clear === "function") {
    repo.clear();
  }
}

module.exports = {
  createInstallmentPlan,
  getPlanByRecordId,
  payInstallment,
  calculateInstallmentSchedule,
  resetPlansStore,
  _inMemoryPlans
};
