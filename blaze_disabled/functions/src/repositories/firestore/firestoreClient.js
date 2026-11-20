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
    const projectId =
      process.env.GCLOUD_PROJECT ||
      process.env.FIREBASE_PROJECT_ID ||
      "udhaar-test-project";
    initializeApp({ projectId });
  }
  return getFirestore();
}

async function clearCollection(db, collectionName) {
  if (!db) return;
  if (typeof db.recursiveDelete === "function") {
    await db.recursiveDelete(db.collection(collectionName));
    return;
  }
  const snap = await db.collection(collectionName).get();
  if (snap.empty) return;
  const batch = db.batch();
  for (const doc of snap.docs) {
    batch.delete(doc.ref);
  }
  await batch.commit();
}

module.exports = {
  getDb,
  setFirestoreInstance,
  clearCollection
};
