const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

test("Part 3 — Upgrade Landing Page, Privacy Page, and Asset Optimization", async (t) => {
  const landingHtmlPath = path.resolve(__dirname, "../../web/index.html");
  const landingCssPath = path.resolve(__dirname, "../../web/landing.css");
  const landingJsPath = path.resolve(__dirname, "../../web/landing.js");
  const privacyHtmlPath = path.resolve(__dirname, "../../web/privacy.html");
  const guestHtmlPath = path.resolve(__dirname, "../../web/u/index.html");
  const guestJsPath = path.resolve(__dirname, "../../web/u/guest.js");
  const sitemapPath = path.resolve(__dirname, "../../web/sitemap.xml");
  const robotsPath = path.resolve(__dirname, "../../web/robots.txt");
  const notFoundPath = path.resolve(__dirname, "../../web/404.html");
  const assetsDir = path.resolve(__dirname, "../../web/assets");

  const landingHtml = fs.readFileSync(landingHtmlPath, "utf8");
  const landingCss = fs.readFileSync(landingCssPath, "utf8");
  const landingJs = fs.readFileSync(landingJsPath, "utf8");
  const privacyHtml = fs.readFileSync(privacyHtmlPath, "utf8");
  const guestHtml = fs.readFileSync(guestHtmlPath, "utf8");
  const guestJs = fs.readFileSync(guestJsPath, "utf8");
  const sitemapContent = fs.readFileSync(sitemapPath, "utf8");
  const robotsContent = fs.readFileSync(robotsPath, "utf8");
  const notFoundHtml = fs.readFileSync(notFoundPath, "utf8");

  await t.test("1. Landing Page Plain-Language Headline, Promise & 3 Steps", () => {
    assert.match(landingHtml, /Keep clear records of money you lend and borrow/i, "Must have simple headline");
    assert.match(landingHtml, /No more forgetting\.\s*No more arguments\./i, "Must have one-line promise");
    assert.match(landingHtml, /How it works in 3 steps/i, "Must have 3 steps section heading");
    assert.match(landingHtml, /Add the record/i, "Step 1 title");
    assert.match(landingHtml, /Send the link on WhatsApp/i, "Step 2 title");
    assert.match(landingHtml, /The other person confirms/i, "Step 3 title");
  });

  await t.test("2. Benefits in Plain Everyday Words", () => {
    assert.match(landingHtml, /Locked on your phone/i, "Benefit 1");
    assert.match(landingHtml, /Works without internet/i, "Benefit 2");
    assert.match(landingHtml, /Polite reminders/i, "Benefit 3");
    assert.match(landingHtml, /Pay easily by UPI/i, "Benefit 4");
    assert.match(landingHtml, /Backup to your Google Drive/i, "Benefit 5");
    assert.match(landingHtml, /Works in 5 languages/i, "Benefit 6");
  });

  await t.test("3. Zero Banned Technical Jargon on Landing Page", () => {
    const bannedWords = [
      "Cryptographic Udhaar Khata",
      "hardware-backed SQLCipher encryption",
      "SHA-256",
      "E.164",
      "SQLCipher",
      "counterparty",
      "immutable",
      "cryptographic"
    ];

    // Check visible body text (excluding script tags if any)
    const bodyMatch = landingHtml.match(/<body[\s\S]*<\/body>/i);
    assert.ok(bodyMatch, "Landing page must contain body tag");
    const bodyWithoutScripts = bodyMatch[0].replace(/<script[\s\S]*?<\/script>/gi, "");

    for (const word of bannedWords) {
      const reg = new RegExp(`\\b${word}\\b`, "i");
      assert.doesNotMatch(bodyWithoutScripts, reg, `Landing page visible text must not contain banned jargon '${word}'`);
    }
  });

  await t.test("4. Trust Section, FAQ, and Honest Disclaimers", () => {
    assert.match(landingHtml, /What we store and what we do not/i, "Must have storage comparison section");
    assert.match(landingHtml, /Udhaar does not move or hold money/i, "Must have honest money disclaimer");
    assert.match(landingHtml, /Udhaar is not a legal contract/i, "Must have honest legal disclaimer");

    // FAQ questions
    assert.match(landingHtml, /Is Udhaar completely free\?/i, "FAQ 1: Free");
    assert.match(landingHtml, /Do I need to give my phone number to use the app\?/i, "FAQ 2: Phone number");
    assert.match(landingHtml, /What happens if I lose or change my phone\?/i, "FAQ 3: Lost phone");
    assert.match(landingHtml, /Can the other person see all my other records\?/i, "FAQ 4: Visibility");
    assert.match(landingHtml, /How do I delete my data\?/i, "FAQ 5: Delete data");
  });

  await t.test("5. Multi-Language Dictionaries & Language Switcher (5 Languages)", () => {
    const { I18N } = require(landingJsPath);
    const languages = ["en", "hi", "bn", "as", "ne"];
    for (const lang of languages) {
      assert.ok(I18N[lang], `I18N dictionary must include '${lang}'`);
      assert.ok(Object.keys(I18N[lang]).length >= 40, `'${lang}' dictionary must have full set of translations`);
      assert.match(landingHtml, new RegExp(`<option\\s+value=["']${lang}["']`, "i"), `Language select must include '${lang}'`);
      assert.match(landingHtml, new RegExp(`<link\\s+rel=["']alternate["']\\s+hreflang=["']${lang}["']`, "i"), `Head must include hreflang for '${lang}'`);
    }
    assert.match(landingHtml, /<link\s+rel=["']alternate["']\s+hreflang=["']x-default["']/i, "Must include x-default hreflang");
  });

  await t.test("6. Interactive Demo Mode Integration", () => {
    assert.match(landingHtml, /id=["']btn-try-demo["']/i, "Must have try demo button");
    assert.match(landingHtml, /\/u\/\?demo=true/i, "Must link to /u/?demo=true");
    assert.match(guestHtml, /id=["']demo-banner["']/i, "Guest page must contain demo mode banner");
    assert.match(guestJs, /isDemoMode/i, "Guest JS must handle demo mode");
    assert.match(guestJs, /demo-tx-12345/i, "Guest JS must supply demo record without backend calls");
  });

  await t.test("7. Image Assets Optimization & Deduplication", () => {
    const files = fs.readdirSync(assetsDir);

    // Duplicate screenshot files must not exist
    for (let i = 1; i <= 11; i++) {
      const match = files.find((f) => f.startsWith(`screenshot_${i}_`));
      assert.strictEqual(match, undefined, `Duplicate screenshot_${i}_* file must be deleted`);
    }
    const geminiDup = files.find((f) => f.startsWith("Gemini_Generated_Image_"));
    assert.strictEqual(geminiDup, undefined, "Duplicate Gemini_Generated_Image_* file must be deleted");

    // WebP versions must exist
    assert.ok(fs.existsSync(path.join(assetsDir, "og-image.webp")), "og-image.webp must exist");
    assert.ok(fs.existsSync(path.join(assetsDir, "og-image.jpg")), "og-image.jpg must exist");
    assert.ok(fs.existsSync(path.join(assetsDir, "hero-3d-phone.webp")), "hero-3d-phone.webp must exist");
    assert.ok(fs.existsSync(path.join(assetsDir, "screen-dashboard.webp")), "screen-dashboard.webp must exist");

    // OG Image metadata in HTML
    assert.match(landingHtml, /<meta\s+property=["']og:image["']\s+content=["'][^"']*og-image\.jpg["']/i, "Must reference og-image.jpg");
    assert.match(landingHtml, /<meta\s+property=["']og:image:width["']\s+content=["']1200["']/i, "OG image width must be 1200");
    assert.match(landingHtml, /<meta\s+property=["']og:image:height["']\s+content=["']630["']/i, "OG image height must be 630");

    // Responsive picture tags, lazy loading & dimensions
    assert.match(landingHtml, /<picture>/i, "Must use responsive <picture> elements");
    assert.match(landingHtml, /type=["']image\/webp["']/i, "Must specify type='image/webp'");
    assert.match(landingHtml, /loading=["']lazy["']/i, "Below-the-fold images must have loading='lazy'");
    assert.match(landingHtml, /width=["']\d+["']\s+height=["']\d+["']/i, "Images must have explicit width and height");
  });

  await t.test("8. SEO, Structured Data, Sitemap, Robots & 404", () => {
    assert.match(landingHtml, /<link\s+rel=["']canonical["']\s+href=["']https:\/\/udhaar\.jyotirmoyb\.com\/["']/i, "Canonical URL");
    assert.match(landingHtml, /"SoftwareApplication"/i, "SoftwareApplication structured data");
    assert.match(landingHtml, /"FinanceApplication"/i, "Application category");
    assert.match(landingHtml, /https:\/\/play\.google\.com\/store\/apps\/details\?id=com\.jyotirmoyb\.udhaar/i, "Play Store URL");

    // Sitemap & Robots
    assert.match(sitemapContent, /<loc>https:\/\/udhaar\.jyotirmoyb\.com\/<\/loc>/i, "Sitemap root URL");
    assert.match(sitemapContent, /<loc>https:\/\/udhaar\.jyotirmoyb\.com\/privacy\.html<\/loc>/i, "Sitemap privacy URL");
    assert.match(robotsContent, /Allow:\s*\/privacy\.html/i, "Robots allow privacy");
    assert.match(robotsContent, /Disallow:\s*\/u\//i, "Robots disallow /u/");
    assert.match(robotsContent, /Disallow:\s*\/api\//i, "Robots disallow /api/");
    assert.match(robotsContent, /Sitemap:\s*https:\/\/udhaar\.jyotirmoyb\.com\/sitemap\.xml/i, "Robots sitemap link");

    // 404 page
    assert.match(notFoundHtml, /404/i, "404 page error code");
    assert.match(notFoundHtml, /Page not found/i, "404 page title");
    assert.match(notFoundHtml, /Back to Home/i, "404 return to homepage");
  });

  await t.test("9. Privacy Page Plain-Language 'In Short', Retention Table & Deletion Form", () => {
    assert.match(privacyHtml, /in-short-box/i, "Must have 'In short' summary box");
    assert.match(privacyHtml, /What is stored, where, and how to delete it/i, "Must have storage and deletion table");
    assert.match(privacyHtml, /id=["']delete-request-form["']/i, "Must have delete data request form");
    assert.match(privacyHtml, /id=["']del-name["']/i, "Must have name/phone input");
    assert.match(privacyHtml, /id=["']del-link["']/i, "Must have link/ID input");
    assert.match(privacyHtml, /id=["']del-reason["']/i, "Must have reason selector");
    assert.match(privacyHtml, /id=["']privacy-lang-select["']/i, "Must have language selector for privacy page");
    assert.match(privacyHtml, /mailto:admin@jyotirmoyb\.com/i, "Must support deletion email to admin");
    assert.match(privacyHtml, /On-Device Data &amp; Hardware Encryption/i, "Must preserve full legal text below");
  });

  await t.test("10. Mobile Responsiveness (360px) & Emerald Look", () => {
    assert.match(landingCss, /max-width:\s*1160px/i, "Max-width container");
    assert.match(landingCss, /min-height:\s*var\(--touch-target\)/i, "48px touch targets for accessibility");
    assert.match(landingCss, /--primary:\s*#064E3B/i, "Emerald primary color scheme");
    assert.match(landingCss, /prefers-color-scheme:\s*dark/i, "Dark mode media query");
  });
});
