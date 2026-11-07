/**
 * Hybrid Logical Clock (HLC) Implementation (webapp/src/sync/hlc.js)
 * Implements Kulkarni et al. physical-logical clock combining wall clock monotonicity
 * with Lamport logical causality and deterministic device tie-breaking.
 */

function createHlc(deviceId, wallTimeMs = Date.now()) {
  return {
    millis: wallTimeMs,
    counter: 0,
    deviceId: String(deviceId || "unknown")
  };
}

function packHlc(hlc) {
  if (!hlc) return "";
  return `${hlc.millis}:${hlc.counter}:${hlc.deviceId}`;
}

function unpackHlc(str) {
  if (!str || typeof str !== "string") {
    return createHlc("unknown", 0);
  }
  const parts = str.split(":");
  if (parts.length < 3) {
    return createHlc("unknown", 0);
  }
  return {
    millis: Number(parts[0]) || 0,
    counter: Number(parts[1]) || 0,
    deviceId: parts.slice(2).join(":")
  };
}

function incrementHlc(currentHlc, wallTimeMs = Date.now()) {
  const current = typeof currentHlc === "string" ? unpackHlc(currentHlc) : currentHlc;
  if (wallTimeMs > current.millis) {
    return {
      millis: wallTimeMs,
      counter: 0,
      deviceId: current.deviceId
    };
  }
  return {
    millis: current.millis,
    counter: current.counter + 1,
    deviceId: current.deviceId
  };
}

function updateHlc(localHlc, remoteHlc, wallTimeMs = Date.now()) {
  const l = typeof localHlc === "string" ? unpackHlc(localHlc) : localHlc;
  const r = typeof remoteHlc === "string" ? unpackHlc(remoteHlc) : remoteHlc;

  const maxMillis = Math.max(l.millis, r.millis, wallTimeMs);
  let counter = 0;

  if (maxMillis === l.millis && maxMillis === r.millis) {
    counter = Math.max(l.counter, r.counter) + 1;
  } else if (maxMillis === l.millis) {
    counter = l.counter + 1;
  } else if (maxMillis === r.millis) {
    counter = r.counter + 1;
  } else {
    counter = 0;
  }

  return {
    millis: maxMillis,
    counter,
    deviceId: l.deviceId
  };
}

function compareHlc(hlcA, hlcB) {
  const a = typeof hlcA === "string" ? unpackHlc(hlcA) : hlcA;
  const b = typeof hlcB === "string" ? unpackHlc(hlcB) : hlcB;

  if (a.millis !== b.millis) {
    return a.millis - b.millis;
  }
  if (a.counter !== b.counter) {
    return a.counter - b.counter;
  }
  return a.deviceId.localeCompare(b.deviceId);
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    createHlc,
    packHlc,
    unpackHlc,
    incrementHlc,
    updateHlc,
    compareHlc
  };
}
