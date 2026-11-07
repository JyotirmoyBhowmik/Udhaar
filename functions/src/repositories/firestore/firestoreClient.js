/**
 * Firestore Client Initializer & Accessor (functions/src/repositories/firestore/firestoreClient.js)
 */

const { getFirestore } = require("firebase-admin/firestore");
const { getApps, initializeApp } = require("firebase-admin/app");

let overriddenDb = null;

function setFirestoreInstance(db) {
  overriddenDb = db;
}

function getDb() {
  if (overriddenDb) {
    return overriddenDb;
  }
  if (getApps().length === 0) {
    initializeApp();
  }
  return getFirestore();
}

module.exports = {
  getDb,
  setFirestoreInstance
};
