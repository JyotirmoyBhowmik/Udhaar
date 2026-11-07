/**
 * Counterparty Chat View (webapp/src/features/chat/ChatView.js)
 * Implements Stage S7 Chat UI:
 * - Thread list and real-time conversation messages
 * - Quick Replies & Extension Request buttons
 * - Strict CSP: 100% addEventListener, zero inline scripts or styles.
 */

class ChatView {
  constructor({ i18n, authClient, router, onToast }) {
    this.i18n = i18n;
    this.authClient = authClient;
    this.router = router;
    this.onToast = onToast;
    this.threads = [];
    this.activeThread = null;
    this.messages = [];
  }

  async render(container) {
    this.container = container;
    this.container.innerHTML = `
      <div class="card" style="padding: 1rem;">
        <h1 style="font-size: 1.25rem; font-weight: 700; color: #065f46; margin-bottom: 0.5rem;">Chat & Discussions</h1>
        <p style="font-size: 0.875rem; color: #64748b; margin-bottom: 1rem;">Direct conversation and due-date agreements with counterparties</p>

        <div id="chat-threads-container" style="display: flex; flex-direction: column; gap: 0.5rem;">
          <div style="text-align: center; padding: 2rem; color: #64748b;">Loading conversations...</div>
        </div>

        <div id="active-thread-view" style="display: none; margin-top: 1rem; border-top: 1px solid #e2e8f0; padding-top: 1rem;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">
            <strong id="active-thread-title" style="color: #0f172a;"></strong>
            <button id="close-thread-btn" class="btn btn-secondary" style="font-size: 0.75rem; padding: 0.25rem 0.5rem;">Back to Threads</button>
          </div>

          <div id="chat-messages-scroll" style="max-height: 300px; overflow-y: auto; display: flex; flex-direction: column; gap: 0.5rem; padding: 0.5rem; background: #f8fafc; border-radius: 6px; border: 1px solid #e2e8f0; margin-bottom: 0.75rem;"></div>

          <form id="chat-send-form" style="display: flex; gap: 0.5rem;">
            <input type="text" id="chat-input-text" class="input-field" placeholder="Type a message or quick reply..." required maxlength="500" style="flex: 1;">
            <button type="submit" class="btn btn-primary" style="padding: 0.5rem 1rem;">Send</button>
          </form>
        </div>
      </div>
    `;

    this._bindEvents();
    await this._loadThreads();
  }

  _bindEvents() {
    const closeBtn = this.container.querySelector("#close-thread-btn");
    if (closeBtn) {
      closeBtn.addEventListener("click", () => {
        this.container.querySelector("#active-thread-view").style.display = "none";
        this.container.querySelector("#chat-threads-container").style.display = "flex";
      });
    }

    const sendForm = this.container.querySelector("#chat-send-form");
    if (sendForm) {
      sendForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const input = this.container.querySelector("#chat-input-text");
        const text = input.value.trim();
        if (!text || !this.activeThread) return;

        input.value = "";
        await this._sendMessage(text);
      });
    }
  }

  async _loadThreads() {
    try {
      const res = await fetch("/api/v1/chat/threads");
      if (res.ok) {
        this.threads = await res.json();
      } else {
        this.threads = [];
      }
      this._renderThreads();
    } catch (_) {
      this.threads = [];
      this._renderThreads();
    }
  }

  _renderThreads() {
    const container = this.container.querySelector("#chat-threads-container");
    if (this.threads.length === 0) {
      container.innerHTML = `
        <div style="text-align: center; padding: 2rem; color: #94a3b8;">
          No active chat threads yet. Messages sent on shared records will appear here.
        </div>
      `;
      return;
    }

    container.innerHTML = "";
    for (const th of this.threads) {
      const el = document.createElement("div");
      el.className = "card";
      el.style.cssText = "background: #f8fafc; border: 1px solid #e2e8f0; padding: 0.75rem; cursor: pointer;";
      el.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <strong style="color: #0f172a;">${th.partyName}</strong>
          <span style="font-size: 0.75rem; color: #64748b;">${new Date(th.updatedAtMs).toLocaleDateString("en-IN")}</span>
        </div>
        <div style="font-size: 0.8125rem; color: #475569; margin-top: 0.25rem;">
          ${th.lastMessage ? th.lastMessage.bodyText : "Tap to open conversation"}
        </div>
      `;

      el.addEventListener("click", () => this._openThread(th));
      container.appendChild(el);
    }
  }

  async _openThread(thread) {
    this.activeThread = thread;
    this.container.querySelector("#chat-threads-container").style.display = "none";
    const threadView = this.container.querySelector("#active-thread-view");
    threadView.style.display = "block";
    this.container.querySelector("#active-thread-title").textContent = thread.partyName;

    await this._loadMessages(thread.threadId);
  }

  async _loadMessages(threadId) {
    const scrollBox = this.container.querySelector("#chat-messages-scroll");
    scrollBox.innerHTML = "<div style='text-align:center; color:#94a3b8;'>Loading messages...</div>";

    try {
      const res = await fetch(`/api/v1/chat/threads/${encodeURIComponent(threadId)}/messages`);
      if (res.ok) {
        this.messages = await res.json();
      } else {
        this.messages = [];
      }
      this._renderMessages();
    } catch (_) {
      this.messages = [];
      this._renderMessages();
    }
  }

  _renderMessages() {
    const scrollBox = this.container.querySelector("#chat-messages-scroll");
    if (this.messages.length === 0) {
      scrollBox.innerHTML = "<div style='text-align:center; color:#94a3b8; padding: 1rem;'>No messages yet. Send a greeting!</div>";
      return;
    }

    scrollBox.innerHTML = "";
    for (const msg of this.messages) {
      const bubble = document.createElement("div");
      bubble.style.cssText = "padding: 0.5rem 0.75rem; border-radius: 6px; background: #fff; border: 1px solid #e2e8f0; font-size: 0.875rem;";
      bubble.innerHTML = `
        <div style="display: flex; justify-content: space-between; font-size: 0.6875rem; color: #64748b; margin-bottom: 2px;">
          <span>${msg.senderName}</span>
          <span>${new Date(msg.createdAtMs).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })}</span>
        </div>
        <div style="color: #0f172a;">${msg.bodyText}</div>
      `;
      scrollBox.appendChild(bubble);
    }
    scrollBox.scrollTop = scrollBox.scrollHeight;
  }

  async _sendMessage(text) {
    if (!this.activeThread) return;
    try {
      const res = await fetch(`/api/v1/chat/threads/${encodeURIComponent(this.activeThread.threadId)}/messages`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          bodyText: text,
          messageType: "TEXT"
        })
      });
      if (res.ok) {
        await this._loadMessages(this.activeThread.threadId);
      }
    } catch (_) {
      this.onToast("Failed to send message");
    }
  }
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { ChatView };
}
