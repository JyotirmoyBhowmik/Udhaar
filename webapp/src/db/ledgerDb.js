/**
 * Client-Side IndexedDB Database Layer (webapp/src/db/ledgerDb.js)
 * Implements asynchronous, non-blocking local storage for Udhaar Platform v2.
 * Compatible with modern browser IndexedDB and in-memory mock for Node unit tests.
 */

const DB_NAME = "udhaar_ledger_db";
const DB_VERSION = 2;

class MockObjectStore {
  constructor(name, keyPath, autoIncrement = false) {
    this.name = name;
    this.keyPath = keyPath;
    this.autoIncrement = autoIncrement;
    this.data = new Map();
    this.autoId = 1;
  }

  async put(item) {
    let key;
    if (this.autoIncrement && !item[this.keyPath]) {
      key = this.autoId++;
      item = { ...item, [this.keyPath]: key };
    } else {
      key = item[this.keyPath];
    }
    this.data.set(key, JSON.parse(JSON.stringify(item)));
    return key;
  }

  async get(key) {
    const found = this.data.get(key);
    return found ? JSON.parse(JSON.stringify(found)) : null;
  }

  async delete(key) {
    this.data.delete(key);
    return true;
  }

  async getAll() {
    return Array.from(this.data.values()).map(item => JSON.parse(JSON.stringify(item)));
  }

  async clear() {
    this.data.clear();
    this.autoId = 1;
  }
}

class LedgerDatabase {
  constructor() {
    this.isMock = typeof indexedDB === "undefined";
    this.mockStores = {
      records: new MockObjectStore("records", "syncId"),
      people: new MockObjectStore("people", "syncId"),
      payments: new MockObjectStore("payments", "paymentId"),
      plans: new MockObjectStore("plans", "planId"),
      sync_outbox: new MockObjectStore("sync_outbox", "id", true),
      sync_state: new MockObjectStore("sync_state", "key")
    };
    this.db = null;
  }

  async open() {
    if (this.isMock) {
      return this;
    }

    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);

      request.onupgradeneeded = (event) => {
        const db = event.target.result;

        // 1. Records store
        if (!db.objectStoreNames.contains("records")) {
          const recStore = db.createObjectStore("records", { keyPath: "syncId" });
          recStore.createIndex("recordId", "recordId", { unique: false });
          recStore.createIndex("syncStatus", "syncStatus", { unique: false });
          recStore.createIndex("status", "status", { unique: false });
        }

        // 2. People store
        if (!db.objectStoreNames.contains("people")) {
          const pStore = db.createObjectStore("people", { keyPath: "syncId" });
          pStore.createIndex("personId", "personId", { unique: false });
          pStore.createIndex("syncStatus", "syncStatus", { unique: false });
        }

        // 3. Payments store
        if (!db.objectStoreNames.contains("payments")) {
          const payStore = db.createObjectStore("payments", { keyPath: "paymentId" });
          payStore.createIndex("recordSyncId", "recordSyncId", { unique: false });
        }

        // 4. Plans store
        if (!db.objectStoreNames.contains("plans")) {
          db.createObjectStore("plans", { keyPath: "planId" });
        }

        // 5. Sync Outbox store
        if (!db.objectStoreNames.contains("sync_outbox")) {
          const outboxStore = db.createObjectStore("sync_outbox", { keyPath: "id", autoIncrement: true });
          outboxStore.createIndex("status", "status", { unique: false });
          outboxStore.createIndex("nextRetryAtMs", "nextRetryAtMs", { unique: false });
        }

        // 6. Sync State store
        if (!db.objectStoreNames.contains("sync_state")) {
          db.createObjectStore("sync_state", { keyPath: "key" });
        }
      };

      request.onsuccess = (event) => {
        this.db = event.target.result;
        resolve(this);
      };

      request.onerror = (event) => {
        reject(event.target.error);
      };
    });
  }

  _getStore(storeName, mode = "readonly") {
    if (this.isMock) {
      return this.mockStores[storeName];
    }
    const tx = this.db.transaction([storeName], mode);
    return tx.objectStore(storeName);
  }

  // Record operations
  async putRecord(record) {
    if (this.isMock) return this.mockStores.records.put(record);
    return new Promise((resolve, reject) => {
      const store = this._getStore("records", "readwrite");
      const req = store.put(record);
      req.onsuccess = () => resolve(record.syncId);
      req.onerror = () => reject(req.error);
    });
  }

  async getRecord(syncId) {
    if (this.isMock) return this.mockStores.records.get(syncId);
    return new Promise((resolve, reject) => {
      const store = this._getStore("records", "readonly");
      const req = store.get(syncId);
      req.onsuccess = () => resolve(req.result || null);
      req.onerror = () => reject(req.error);
    });
  }

  async getAllRecords() {
    if (this.isMock) return this.mockStores.records.getAll();
    return new Promise((resolve, reject) => {
      const store = this._getStore("records", "readonly");
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => reject(req.error);
    });
  }

  // Payment operations
  async putPayment(payment) {
    if (this.isMock) return this.mockStores.payments.put(payment);
    return new Promise((resolve, reject) => {
      const store = this._getStore("payments", "readwrite");
      const req = store.put(payment);
      req.onsuccess = () => resolve(payment.paymentId);
      req.onerror = () => reject(req.error);
    });
  }

  async getPayment(paymentId) {
    if (this.isMock) return this.mockStores.payments.get(paymentId);
    return new Promise((resolve, reject) => {
      const store = this._getStore("payments", "readonly");
      const req = store.get(paymentId);
      req.onsuccess = () => resolve(req.result || null);
      req.onerror = () => reject(req.error);
    });
  }

  async getAllPayments() {
    if (this.isMock) return this.mockStores.payments.getAll();
    return new Promise((resolve, reject) => {
      const store = this._getStore("payments", "readonly");
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => reject(req.error);
    });
  }

  // Person operations
  async putPerson(person) {
    if (this.isMock) return this.mockStores.people.put(person);
    return new Promise((resolve, reject) => {
      const store = this._getStore("people", "readwrite");
      const req = store.put(person);
      req.onsuccess = () => resolve(person.syncId);
      req.onerror = () => reject(req.error);
    });
  }

  async getPerson(syncId) {
    if (this.isMock) return this.mockStores.people.get(syncId);
    return new Promise((resolve, reject) => {
      const store = this._getStore("people", "readonly");
      const req = store.get(syncId);
      req.onsuccess = () => resolve(req.result || null);
      req.onerror = () => reject(req.error);
    });
  }

  async getAllPeople() {
    if (this.isMock) return this.mockStores.people.getAll();
    return new Promise((resolve, reject) => {
      const store = this._getStore("people", "readonly");
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => reject(req.error);
    });
  }

  // Outbox operations
  async enqueueMutation(mutation) {
    const entry = {
      ...mutation,
      status: "PENDING",
      attempts: 0,
      nextRetryAtMs: 0,
      createdAtMs: Date.now()
    };
    if (this.isMock) return this.mockStores.sync_outbox.put(entry);
    return new Promise((resolve, reject) => {
      const store = this._getStore("sync_outbox", "readwrite");
      const req = store.put(entry);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async getPendingMutations() {
    if (this.isMock) {
      const all = await this.mockStores.sync_outbox.getAll();
      const now = Date.now();
      return all.filter(m => (m.status === "PENDING" || m.status === "FAILED") && m.nextRetryAtMs <= now);
    }
    return new Promise((resolve, reject) => {
      const store = this._getStore("sync_outbox", "readonly");
      const req = store.getAll();
      req.onsuccess = () => {
        const all = req.result || [];
        const now = Date.now();
        const pending = all.filter(m => (m.status === "PENDING" || m.status === "FAILED") && m.nextRetryAtMs <= now);
        resolve(pending);
      };
      req.onerror = () => reject(req.error);
    });
  }

  async removeMutation(id) {
    if (this.isMock) return this.mockStores.sync_outbox.delete(id);
    return new Promise((resolve, reject) => {
      const store = this._getStore("sync_outbox", "readwrite");
      const req = store.delete(id);
      req.onsuccess = () => resolve(true);
      req.onerror = () => reject(req.error);
    });
  }

  async updateMutation(mutation) {
    if (this.isMock) return this.mockStores.sync_outbox.put(mutation);
    return new Promise((resolve, reject) => {
      const store = this._getStore("sync_outbox", "readwrite");
      const req = store.put(mutation);
      req.onsuccess = () => resolve(true);
      req.onerror = () => reject(req.error);
    });
  }

  // State operations
  async setSyncState(key, value) {
    const record = { key, value, updatedAtMs: Date.now() };
    if (this.isMock) return this.mockStores.sync_state.put(record);
    return new Promise((resolve, reject) => {
      const store = this._getStore("sync_state", "readwrite");
      const req = store.put(record);
      req.onsuccess = () => resolve(true);
      req.onerror = () => reject(req.error);
    });
  }

  async getSyncState(key) {
    if (this.isMock) {
      const res = await this.mockStores.sync_state.get(key);
      return res ? res.value : null;
    }
    return new Promise((resolve, reject) => {
      const store = this._getStore("sync_state", "readonly");
      const req = store.get(key);
      req.onsuccess = () => resolve(req.result ? req.result.value : null);
      req.onerror = () => reject(req.error);
    });
  }

  async clearAll() {
    if (this.isMock) {
      for (const s of Object.values(this.mockStores)) {
        await s.clear();
      }
      return;
    }
    const storeNames = ["records", "people", "payments", "plans", "sync_outbox", "sync_state"];
    const tx = this.db.transaction(storeNames, "readwrite");
    for (const name of storeNames) {
      tx.objectStore(name).clear();
    }
    return new Promise((resolve, reject) => {
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }
}

const ledgerDb = new LedgerDatabase();

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    LedgerDatabase,
    ledgerDb
  };
}
