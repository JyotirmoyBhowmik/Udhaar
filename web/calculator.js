/**
 * Udhaar Client-Side Repayment Calculator
 * Strict integer-paise math with remainder allocation to the last installment,
 * exactly matching Android app's InstallmentCalculator.kt and InterestCalculator.kt.
 */
(function (root, factory) {
  if (typeof module !== "undefined" && module.exports) {
    module.exports = factory();
  } else {
    root.UdhaarCalculator = factory();
  }
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  function toPaise(rupees) {
    if (isNaN(rupees) || !isFinite(rupees)) return 0;
    return Math.round(Number(rupees) * 100);
  }

  function paiseToRupees(paise) {
    return (paise / 100).toFixed(2);
  }

  function formatCurrency(paise, currencySymbol) {
    const sym = currencySymbol || "₹";
    const num = Math.floor(paise / 100);
    const fraction = String(paise % 100).padStart(2, "0");
    const formattedInt = new Intl.NumberFormat("en-IN").format(num);
    return `${sym}${formattedInt}${fraction !== "00" ? "." + fraction : ""}`;
  }

  /**
   * Computes installment schedule with integer paise precision.
   *
   * @param {Object} options
   * @param {number} options.principal - Principal in Rupees (e.g. 10000)
   * @param {string} options.interestType - "none" | "simple" | "compound"
   * @param {number} options.interestRate - Annual rate % (e.g. 12)
   * @param {number} options.installmentCount - Number of installments (e.g. 4)
   * @param {string} options.frequency - "WEEKLY" | "BIWEEKLY" | "MONTHLY"
   * @param {Date|number} [options.startDate] - Start date
   */
  function calculateSchedule(options) {
    const principalPaise = toPaise(options.principal || 0);
    const count = Math.max(1, parseInt(options.installmentCount, 10) || 1);
    const rate = Math.max(0, parseFloat(options.interestRate) || 0);
    const type = options.interestType || "none";
    const freq = options.frequency || "MONTHLY";

    // Duration in months based on frequency and count
    let durationMonths = 1;
    if (freq === "WEEKLY") durationMonths = (count * 7) / 30.4375;
    else if (freq === "BIWEEKLY") durationMonths = (count * 14) / 30.4375;
    else durationMonths = count;

    let interestPaise = 0;
    if (type === "simple" && rate > 0) {
      // Simple Interest = P * R * T / 100
      const years = durationMonths / 12.0;
      interestPaise = Math.round(principalPaise * (rate / 100.0) * years);
    } else if (type === "compound" && rate > 0) {
      // Monthly Compounding: A = P * (1 + r/(12*100))^n
      const monthlyRate = rate / (12.0 * 100.0);
      const amountPaiseDouble = principalPaise * Math.pow(1.0 + monthlyRate, durationMonths);
      interestPaise = Math.round(amountPaiseDouble - principalPaise);
    }

    const totalPaise = principalPaise + interestPaise;

    // Equal split with remainder absorbed by the final installment
    const basePaise = Math.floor(totalPaise / count);
    const remainder = totalPaise % count;

    const startDate = options.startDate ? new Date(options.startDate) : new Date();
    const installments = [];
    let runningTotal = 0;

    for (let seq = 1; seq <= count; seq++) {
      const isLast = seq === count;
      const amountPaise = isLast ? basePaise + remainder : basePaise;
      runningTotal += amountPaise;

      const dueDate = new Date(startDate);
      if (freq === "WEEKLY") {
        dueDate.setDate(dueDate.getDate() + (seq - 1) * 7);
      } else if (freq === "BIWEEKLY") {
        dueDate.setDate(dueDate.getDate() + (seq - 1) * 14);
      } else {
        dueDate.setMonth(dueDate.getMonth() + (seq - 1));
      }

      installments.push({
        sequence: seq,
        dueDate: dueDate,
        dueDateFormatted: dueDate.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }),
        amountPaise: amountPaise,
        amountFormatted: formatCurrency(amountPaise)
      });
    }

    // Safety assertion: sum must exactly equal totalPaise
    if (runningTotal !== totalPaise) {
      throw new Error(`Integrity error: sum (${runningTotal}) != total (${totalPaise})`);
    }

    return {
      principalPaise: principalPaise,
      principalFormatted: formatCurrency(principalPaise),
      interestPaise: interestPaise,
      interestFormatted: formatCurrency(interestPaise),
      totalPaise: totalPaise,
      totalFormatted: formatCurrency(totalPaise),
      perInstallmentFormatted: formatCurrency(installments[0].amountPaise),
      totalInterestFormatted: formatCurrency(interestPaise),
      totalAmountFormatted: formatCurrency(totalPaise),
      installmentCount: count,
      frequency: freq,
      installments: installments,
      summaryText: `Plan: ${formatCurrency(totalPaise)} total across ${count} installments (${formatCurrency(installments[0].amountPaise)}/installment).`
    };
  }

  return {
    toPaise: toPaise,
    paiseToRupees: paiseToRupees,
    formatCurrency: formatCurrency,
    calculateSchedule: calculateSchedule
  };
});
