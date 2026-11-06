/**
 * Udhaar Client-Side WebP Image Pipeline & EXIF Stripper (web/assets/image-processor.js)
 * Optimizes camera photos & receipts directly in the browser before cloud upload.
 * Constrains dimensions to 1600px, converts to image/webp (quality 0.82),
 * and completely strips GPS/camera EXIF privacy metadata via Canvas redrawing.
 * Zero external dependencies. Strict CSP compliant.
 */

(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory();
  } else {
    root.UdhaarImageProcessor = factory();
  }
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  const DEFAULT_MAX_DIM = 1600;
  const DEFAULT_QUALITY = 0.82;

  /**
   * Calculate proportional dimensions constrained to maximum bounding box
   */
  function calculateConstrainedDimensions(srcWidth, srcHeight, maxDimension = DEFAULT_MAX_DIM) {
    srcWidth = Math.max(1, Math.round(srcWidth || 1));
    srcHeight = Math.max(1, Math.round(srcHeight || 1));

    if (srcWidth <= maxDimension && srcHeight <= maxDimension) {
      return { width: srcWidth, height: srcHeight, scaled: false };
    }

    const ratio = Math.min(maxDimension / srcWidth, maxDimension / srcHeight);
    return {
      width: Math.max(1, Math.round(srcWidth * ratio)),
      height: Math.max(1, Math.round(srcHeight * ratio)),
      scaled: true
    };
  }

  /**
   * Check if image/webp canvas export is supported in current environment
   */
  function isWebPSupported() {
    if (typeof document === "undefined") return false;
    try {
      const canvas = document.createElement("canvas");
      canvas.width = 1;
      canvas.height = 1;
      return canvas.toDataURL("image/webp").indexOf("data:image/webp") === 0;
    } catch (_) {
      return false;
    }
  }

  /**
   * Process and compress image file/blob in the browser
   */
  async function processReceiptImage(fileOrBlob, options = {}) {
    if (!fileOrBlob) throw new Error("fileOrBlob is required");

    const maxDim = Number(options.maxDimension) || DEFAULT_MAX_DIM;
    const quality = typeof options.quality === "number" ? options.quality : DEFAULT_QUALITY;
    const originalBytes = fileOrBlob.size || 0;

    // Node.js or non-DOM test environment fallback
    if (typeof Image === "undefined" && typeof createImageBitmap === "undefined") {
      return {
        blob: fileOrBlob,
        originalBytes,
        compressedBytes: originalBytes,
        savedBytes: 0,
        savingsPercent: 0,
        width: 800,
        height: 600,
        mimeType: fileOrBlob.type || "image/jpeg",
        isSimulated: true
      };
    }

    // Determine target MIME type (WebP preferred, JPEG fallback)
    const targetMime = isWebPSupported() ? "image/webp" : "image/jpeg";

    // 1. Load image
    let bitmap = null;
    let imgWidth = 0;
    let imgHeight = 0;

    if (typeof createImageBitmap === "function") {
      bitmap = await createImageBitmap(fileOrBlob);
      imgWidth = bitmap.width;
      imgHeight = bitmap.height;
    } else {
      const url = URL.createObjectURL(fileOrBlob);
      bitmap = await new Promise((resolve, reject) => {
        const img = new Image();
        img.onload = () => resolve(img);
        img.onerror = () => reject(new Error("Failed to load image into element"));
        img.src = url;
      });
      imgWidth = bitmap.naturalWidth || bitmap.width;
      imgHeight = bitmap.naturalHeight || bitmap.height;
      URL.revokeObjectURL(url);
    }

    // 2. Compute constrained dimensions
    const dims = calculateConstrainedDimensions(imgWidth, imgHeight, maxDim);

    // 3. Render onto fresh Canvas (strips EXIF metadata automatically)
    let compressedBlob = null;
    if (typeof OffscreenCanvas !== "undefined") {
      const offscreen = new OffscreenCanvas(dims.width, dims.height);
      const ctx = offscreen.getContext("2d");
      ctx.drawImage(bitmap, 0, 0, dims.width, dims.height);
      compressedBlob = await offscreen.convertToBlob({ type: targetMime, quality });
    } else {
      const canvas = document.createElement("canvas");
      canvas.width = dims.width;
      canvas.height = dims.height;
      const ctx = canvas.getContext("2d");
      ctx.drawImage(bitmap, 0, 0, dims.width, dims.height);
      compressedBlob = await new Promise(resolve => {
        canvas.toBlob(resolve, targetMime, quality);
      });
    }

    if (typeof bitmap.close === "function") {
      bitmap.close();
    }

    const compressedBytes = compressedBlob.size;
    const savedBytes = Math.max(0, originalBytes - compressedBytes);
    const savingsPercent = originalBytes > 0 ? Math.round((savedBytes / originalBytes) * 100) : 0;

    return {
      blob: compressedBlob,
      originalBytes,
      compressedBytes,
      savedBytes,
      savingsPercent,
      width: dims.width,
      height: dims.height,
      mimeType: targetMime,
      isSimulated: false
    };
  }

  return {
    DEFAULT_MAX_DIM,
    DEFAULT_QUALITY,
    calculateConstrainedDimensions,
    isWebPSupported,
    processReceiptImage
  };
});
