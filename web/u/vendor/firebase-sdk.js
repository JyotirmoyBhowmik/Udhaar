/**
 * Locally Bundled Firebase JS SDK Entrypoint (No CDN)
 * Exposes Firebase App, Auth, Firestore, and App Check modular APIs.
 */
import {
  initializeApp,
  getApps,
  getApp
} from "./firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  EmailAuthProvider,
  signInWithPopup,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  sendEmailVerification,
  sendPasswordResetEmail,
  reauthenticateWithCredential,
  reauthenticateWithPopup,
  signInAnonymously,
  onAuthStateChanged,
  signOut,
  connectAuthEmulator
} from "./firebase-auth.js";
import {
  getFirestore,
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  collection,
  collectionGroup,
  query,
  where,
  orderBy,
  limit,
  getDocs,
  writeBatch,
  connectFirestoreEmulator
} from "./firebase-firestore.js";
import {
  initializeAppCheck,
  ReCaptchaV3Provider,
  getToken as getAppCheckToken
} from "./firebase-app-check.js";

const bundledFirebaseSdk = {
  initializeApp,
  getApps,
  getApp,
  getAuth,
  GoogleAuthProvider,
  EmailAuthProvider,
  signInWithPopup,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  sendEmailVerification,
  sendPasswordResetEmail,
  reauthenticateWithCredential,
  reauthenticateWithPopup,
  signInAnonymously,
  onAuthStateChanged,
  signOut,
  connectAuthEmulator,
  getFirestore,
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  collection,
  collectionGroup,
  query,
  where,
  orderBy,
  limit,
  getDocs,
  writeBatch,
  connectFirestoreEmulator,
  initializeAppCheck,
  ReCaptchaV3Provider,
  getAppCheckToken
};

globalThis.__UDHAAR_BUNDLED_FIREBASE__ = bundledFirebaseSdk;
export default bundledFirebaseSdk;
