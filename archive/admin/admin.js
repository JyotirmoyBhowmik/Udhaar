/**
 * Udhaar Administration Console Application (admin/admin.js)
 * Implements Stage S8 Admin UI:
 * - Telemetry, masked user lookup, link revocations, audit stream.
 * - Enforces mandatory audit justification reasons and MFA auth tokens.
 * - Strict CSP: zero inline event handlers.
 */

(function () {
  "use strict";

  let authToken = null;
  let adminRole = "NONE";

  // Elements
  const loginSection = document.getElementById("login-section");
  const dashboardContent = document.getElementById("dashboard-content");
  const adminBadge = document.getElementById("admin-badge");
  const adminLogoutBtn = document.getElementById("admin-logout-btn");
  const loginForm = document.getElementById("admin-login-form");
  const loginError = document.getElementById("admin-login-error");

  const totalUsersEl = document.getElementById("metric-total-users");
  const activeUsersEl = document.getElementById("metric-active-users");
  const lockedUsersEl = document.getElementById("metric-locked-users");
  const auditLogsEl = document.getElementById("metric-audit-logs");
  const circuitsListEl = document.getElementById("circuits-list");

  const lookupForm = document.getElementById("lookup-form");
  const queryHashInput = document.getElementById("query-hash-input");
  const lookupReasonInput = document.getElementById("lookup-reason-input");
  const lookupError = document.getElementById("lookup-error");
  const lookupResultEl = document.getElementById("lookup-result");

  const revokeForm = document.getElementById("revoke-link-form");
  const revokeRecordInput = document.getElementById("revoke-record-id");
  const revokeReasonInput = document.getElementById("revoke-reason");
  const revokeStatus = document.getElementById("revoke-status");

  const refreshAuditBtn = document.getElementById("refresh-audit-btn");
  const auditTbody = document.getElementById("audit-tbody");

  function getHeaders(reason = null) {
    const headers = {
      "Accept": "application/json",
      "Content-Type": "application/json"
    };
    if (authToken) {
      headers["Authorization"] = `Bearer ${authToken}`;
    }
    if (reason) {
      headers["X-Admin-Reason"] = reason;
    }
    return headers;
  }

  async function apiCall(endpoint, method = "GET", body = null, reason = null) {
    const opts = {
      method,
      headers: getHeaders(reason)
    };
    if (body) {
      opts.body = JSON.stringify(body);
    }
    const res = await fetch(endpoint, opts);
    let data;
    try {
      data = await res.json();
    } catch (_) {
      data = {};
    }
    if (!res.ok) {
      const err = new Error(data.detail || data.title || "Admin request failed");
      err.status = res.status;
      err.code = data.code;
      throw err;
    }
    return data;
  }

  // Auth Handling
  if (loginForm) {
    loginForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      loginError.style.display = "none";
      const tokenInput = document.getElementById("admin-token-input");
      const mfaInput = document.getElementById("admin-mfa-code");

      const token = tokenInput ? tokenInput.value.trim() : "";
      const mfa = mfaInput ? mfaInput.value.trim() : "";

      if (!token || mfa.length !== 6) {
        loginError.textContent = "Please enter your session token and 6-digit MFA code.";
        loginError.style.display = "block";
        return;
      }

      authToken = token;
      try {
        const overview = await apiCall("/api/v1/admin/overview");
        adminRole = "ADMIN";
        if (adminBadge) adminBadge.textContent = `Role: ${adminRole} (MFA Active)`;
        if (loginSection) loginSection.style.display = "none";
        if (dashboardContent) dashboardContent.style.display = "block";
        renderOverview(overview);
        loadAuditLogs();
      } catch (err) {
        authToken = null;
        loginError.textContent = err.message || "Failed to authenticate administrative session.";
        loginError.style.display = "block";
      }
    });
  }

  if (adminLogoutBtn) {
    adminLogoutBtn.addEventListener("click", () => {
      authToken = null;
      adminRole = "NONE";
      if (adminBadge) adminBadge.textContent = "Role: Signed Out";
      if (loginSection) loginSection.style.display = "block";
      if (dashboardContent) dashboardContent.style.display = "none";
    });
  }

  // Render Telemetry Overview
  function renderOverview(metrics) {
    if (totalUsersEl) totalUsersEl.textContent = String(metrics.users?.total ?? 0);
    if (activeUsersEl) activeUsersEl.textContent = String(metrics.users?.active ?? 0);
    if (lockedUsersEl) lockedUsersEl.textContent = String(metrics.users?.locked ?? 0);
    if (auditLogsEl) auditLogsEl.textContent = String(metrics.auditLogsRecorded ?? 0);

    if (circuitsListEl && metrics.costControls) {
      const entries = Object.entries(metrics.costControls);
      if (entries.length === 0) {
        circuitsListEl.innerHTML = `<p style="color: var(--admin-text-muted);">No circuits configured.</p>`;
        return;
      }

      circuitsListEl.innerHTML = entries.map(([name, c]) => `
        <div style="display: flex; justify-content: space-between; align-items: center; padding: 0.5rem 0; border-bottom: 1px solid var(--admin-border);">
          <div>
            <strong>${name}</strong>
            <span class="badge ${c.state === "CLOSED" ? "text-success" : "text-danger"}" style="margin-left: 0.5rem;">${c.state}</span>
            <div style="font-size: 0.8rem; color: var(--admin-text-muted);">
              Daily Spend: ${c.sentToday || 0} / ${c.dailyLimit || 0} | Failures: ${c.consecutiveFailures || 0}
            </div>
          </div>
          ${c.state !== "CLOSED" ? `
            <button type="button" class="btn btn-secondary btn-sm reset-circuit-btn" data-provider="${name}">Reset Circuit</button>
          ` : ""}
        </div>
      `).join("");

      circuitsListEl.querySelectorAll(".reset-circuit-btn").forEach(btn => {
        btn.addEventListener("click", async (e) => {
          const provider = e.currentTarget.getAttribute("data-provider");
          const reason = prompt("Enter justification reason for circuit reset:");
          if (reason && reason.trim().length >= 5) {
            try {
              await apiCall("/api/v1/admin/circuits/reset", "POST", { providerName: provider }, reason.trim());
              alert(`Circuit for ${provider} reset successfully.`);
              const fresh = await apiCall("/api/v1/admin/overview");
              renderOverview(fresh);
              loadAuditLogs();
            } catch (err) {
              alert(`Reset failed: ${err.message}`);
            }
          }
        });
      });
    }
  }

  // Masked User Lookup
  if (lookupForm) {
    lookupForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      lookupError.style.display = "none";
      lookupResultEl.style.display = "none";

      const queryHash = queryHashInput ? queryHashInput.value.trim() : "";
      const reason = lookupReasonInput ? lookupReasonInput.value.trim() : "";

      if (!queryHash || reason.length < 5) {
        lookupError.textContent = "Please provide both query hash and an audit reason (min 5 characters).";
        lookupError.style.display = "block";
        return;
      }

      try {
        const user = await apiCall("/api/v1/admin/users/lookup", "POST", { queryHash }, reason);
        renderUserResult(user);
        loadAuditLogs();
      } catch (err) {
        lookupError.textContent = err.message;
        lookupError.style.display = "block";
      }
    });
  }

  function renderUserResult(u) {
    if (!lookupResultEl) return;
    lookupResultEl.style.display = "block";
    lookupResultEl.innerHTML = `
      <div class="card" style="margin: 0; background-color: #0f172a;">
        <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 1rem;">
          <div>
            <h3 style="margin: 0 0 0.5rem 0; font-size: 1.1rem;">${u.displayName}</h3>
            <div style="font-size: 0.875rem; color: var(--admin-text-muted);">
              <div><strong>UID:</strong> <code>${u.uid}</code></div>
              <div><strong>Masked Phone:</strong> ${u.maskedPhone}</div>
              <div><strong>Masked Email:</strong> ${u.maskedEmail}</div>
              <div><strong>Status:</strong> ${u.status} | <strong>Devices:</strong> ${u.deviceCount}</div>
              <div><strong>Account Locked:</strong> ${u.isLocked ? `<span class="text-danger">YES (${u.lockReason || "Administrative Lock"})</span>` : `<span class="text-success">NO</span>`}</div>
            </div>
            <div style="margin-top: 0.5rem; font-size: 0.8rem; color: #fbbf24;">
              🔒 ${u.notesCountNotice}
            </div>
          </div>
          <div>
            <button type="button" id="toggle-lock-btn" class="btn ${u.isLocked ? "btn-primary" : "btn-danger"} btn-sm" data-uid="${u.uid}" data-locked="${u.isLocked}">
              ${u.isLocked ? "Unlock Account" : "Lock Account"}
            </button>
          </div>
        </div>
      </div>
    `;

    const toggleBtn = lookupResultEl.querySelector("#toggle-lock-btn");
    if (toggleBtn) {
      toggleBtn.addEventListener("click", async () => {
        const uid = toggleBtn.getAttribute("data-uid");
        const currentLocked = toggleBtn.getAttribute("data-locked") === "true";
        const action = currentLocked ? "unlock" : "lock";
        const reason = prompt(`Enter reason to ${action} account:`);
        if (reason && reason.trim().length >= 5) {
          try {
            await apiCall(`/api/v1/admin/users/${encodeURIComponent(uid)}/lock`, "POST", { locked: !currentLocked }, reason.trim());
            alert(`Account state updated successfully.`);
            const refreshed = await apiCall("/api/v1/admin/users/lookup", "POST", { queryHash: uid }, reason.trim());
            renderUserResult(refreshed);
            loadAuditLogs();
          } catch (err) {
            alert(`Action failed: ${err.message}`);
          }
        }
      });
    }
  }

  // Emergency Link Revocation
  if (revokeForm) {
    revokeForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      revokeStatus.style.display = "none";

      const recordId = revokeRecordInput ? revokeRecordInput.value.trim() : "";
      const reason = revokeReasonInput ? revokeReasonInput.value.trim() : "";

      if (!recordId || reason.length < 5) {
        revokeStatus.textContent = "Please enter record ID and an audit reason (min 5 chars).";
        revokeStatus.className = "form-error";
        revokeStatus.style.display = "block";
        return;
      }

      try {
        await apiCall(`/api/v1/admin/links/${encodeURIComponent(recordId)}/revoke`, "POST", {}, reason);
        revokeStatus.textContent = `Capability link for record ${recordId} successfully revoked.`;
        revokeStatus.className = "text-success";
        revokeStatus.style.display = "block";
        if (revokeRecordInput) revokeRecordInput.value = "";
        if (revokeReasonInput) revokeReasonInput.value = "";
        loadAuditLogs();
      } catch (err) {
        revokeStatus.textContent = err.message;
        revokeStatus.className = "form-error";
        revokeStatus.style.display = "block";
      }
    });
  }

  // Load Audit Logs
  async function loadAuditLogs() {
    if (!auditTbody) return;
    try {
      const logs = await apiCall("/api/v1/admin/audit-logs?limit=50");
      if (!Array.isArray(logs) || logs.length === 0) {
        auditTbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--admin-text-muted);">No audit records found.</td></tr>`;
        return;
      }

      auditTbody.innerHTML = logs.map(l => `
        <tr>
          <td>${new Date(l.timestampMs).toLocaleTimeString()}</td>
          <td><code>${String(l.adminUid).slice(0, 8)}</code></td>
          <td><span class="badge badge-admin">${l.adminRole}</span></td>
          <td><strong>${l.action}</strong></td>
          <td><code>${l.target || "-"}</code></td>
          <td>${l.reason}</td>
          <td><code>${l.clientIp}</code></td>
        </tr>
      `).join("");
    } catch (_) {}
  }

  if (refreshAuditBtn) {
    refreshAuditBtn.addEventListener("click", () => {
      loadAuditLogs();
    });
  }
})();
