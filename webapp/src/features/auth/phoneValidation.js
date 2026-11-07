/**
 * Spark Mode Contact Phone Normalization & Validation (webapp/src/features/auth/phoneValidation.js)
 * 
 * Rules:
 * - Phone numbers in Spark Mode are optional contact details only.
 * - Allowed country calling codes: India (+91) and Nepal (+977).
 * - A free system cannot prove phone ownership: phoneStatus is always "unverified".
 * - Label: "Number not verified".
 * - Never used for login or recovery in Spark Mode.
 */

function validateAndNormalizeContactPhone(rawPhone, defaultCountry = "IN") {
  if (!rawPhone || typeof rawPhone !== "string") {
    return {
      valid: false,
      e164: "",
      country: "",
      phoneStatus: "unverified",
      label: "Number not verified",
      error: "Phone number is empty"
    };
  }

  const cleaned = rawPhone.trim().replace(/[\s\-\(\)\.]/g, "");
  let e164 = cleaned;

  if (cleaned.startsWith("+977")) {
    e164 = cleaned;
  } else if (cleaned.startsWith("977") && cleaned.length >= 13) {
    e164 = `+${cleaned}`;
  } else if (cleaned.startsWith("+91")) {
    e164 = cleaned;
  } else if (cleaned.startsWith("91") && cleaned.length === 12) {
    e164 = `+${cleaned}`;
  } else if (/^[6-9][0-9]{9}$/.test(cleaned)) {
    if (defaultCountry === "NP" && /^9[678][0-9]{8}$/.test(cleaned)) {
      e164 = `+977${cleaned}`;
    } else {
      e164 = `+91${cleaned}`;
    }
  }

  if (e164.startsWith("+91")) {
    const subscriber = e164.slice(3);
    if (!/^[6-9][0-9]{9}$/.test(subscriber)) {
      return {
        valid: false,
        e164: "",
        country: "IN",
        phoneStatus: "unverified",
        label: "Number not verified",
        error: "Invalid Indian mobile number (must be 10 digits starting with 6-9)."
      };
    }
    return {
      valid: true,
      e164,
      country: "IN",
      phoneStatus: "unverified",
      label: "Number not verified",
      error: null
    };
  }

  if (e164.startsWith("+977")) {
    const subscriber = e164.slice(4);
    if (!/^9[678][0-9]{8}$/.test(subscriber)) {
      return {
        valid: false,
        e164: "",
        country: "NP",
        phoneStatus: "unverified",
        label: "Number not verified",
        error: "Invalid Nepali mobile number (must be 10 digits starting with 96, 97, or 98)."
      };
    }
    return {
      valid: true,
      e164,
      country: "NP",
      phoneStatus: "unverified",
      label: "Number not verified",
      error: null
    };
  }

  return {
    valid: false,
    e164: "",
    country: "UNKNOWN",
    phoneStatus: "unverified",
    label: "Number not verified",
    error: "Only India (+91) and Nepal (+977) mobile numbers are currently supported."
  };
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    validateAndNormalizeContactPhone
  };
}
