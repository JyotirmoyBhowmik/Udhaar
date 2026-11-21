const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

test("Landing Page, Privacy Page, Terms Page & Asset Integrity", async (t) => {
  const landingHtmlPath = path.resolve(__dirname, "../web/index.html");
  const landingCssPath = path.resolve(__dirname, "../web/landing.css");
  const landingJsPath = path.resolve(__dirname, "../web/landing.js");
  const privacyHtmlPath = path.resolve(__dirname, "../web/privacy.html");
  const guestHtmlPath = path.resolve(__dirname, "../web/u/index.html");
  const guestJsPath = path.resolve(__dirname, "../web/u/guest.js");
  const sitemapPath = path.resolve(__dirname, "../web/sitemap.xml");
  const robotsPath = path.resolve(__dirname, "../web/robots.txt");
  const notFoundPath = path.resolve(__dirname, "../web/404.html");
  const assetsDir = path.resolve(__dirname, "../web/assets");

  const landingHtml = fs.readFileSync(landingHtmlPath, "utf8");
  const landingCss = fs.readFileSync(landingCssPath, "utf8");
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

    for (let i = 1; i <= 11; i++) {
      const match = files.find((f) => f.startsWith(`screenshot_${i}_`));
      assert.strictEqual(match, undefined, `Duplicate screenshot_${i}_* file must be deleted`);
    }
    const geminiDup = files.find((f) => f.startsWith("Gemini_Generated_Image_"));
    assert.strictEqual(geminiDup, undefined, "Duplicate Gemini_Generated_Image_* file must be deleted");

    assert.ok(fs.existsSync(path.join(assetsDir, "og-image.webp")), "og-image.webp must exist");
    assert.ok(fs.existsSync(path.join(assetsDir, "og-image.jpg")), "og-image.jpg must exist");
    assert.ok(fs.existsSync(path.join(assetsDir, "hero-3d-phone.webp")), "hero-3d-phone.webp must exist");
    assert.ok(fs.existsSync(path.join(assetsDir, "screen-dashboard.webp")), "screen-dashboard.webp must exist");

    assert.match(landingHtml, /<meta\s+property=["']og:image["']\s+content=["'][^"']*og-image\.jpg["']/i, "Must reference og-image.jpg");
    assert.match(landingHtml, /<meta\s+property=["']og:image:width["']\s+content=["']1200["']/i, "OG image width must be 1200");
    assert.match(landingHtml, /<meta\s+property=["']og:image:height["']\s+content=["']630["']/i, "OG image height must be 630");

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

    assert.match(sitemapContent, /<loc>https:\/\/udhaar\.jyotirmoyb\.com\/<\/loc>/i, "Sitemap root URL");
    assert.match(sitemapContent, /<loc>https:\/\/udhaar\.jyotirmoyb\.com\/privacy\.html<\/loc>/i, "Sitemap privacy URL");
    assert.match(robotsContent, /Allow:\s*\/privacy\.html/i, "Robots allow privacy");
    assert.match(robotsContent, /Disallow:\s*\/u\//i, "Robots disallow /u/");
    assert.match(robotsContent, /Disallow:\s*\/api\//i, "Robots disallow /api/");
    assert.match(robotsContent, /Sitemap:\s*https:\/\/udhaar\.jyotirmoyb\.com\/sitemap\.xml/i, "Robots sitemap link");

    assert.match(notFoundHtml, /404/i, "404 page error code");
    assert.match(notFoundHtml, /Page not found/i, "404 page title");
    assert.match(notFoundHtml, /Back to Home/i, "404 return to homepage");
  });

  await t.test("9. Privacy Page Plain-Language 'In Short', Retention Table & Account Deletion Portal", () => {
    const deleteAccountHtml = fs.readFileSync(path.resolve(__dirname, "../web/delete-account.html"), "utf8");
    assert.match(privacyHtml, /in-short-box/i, "Must have 'In short' summary box");
    assert.match(privacyHtml, /What is stored, where, and how to delete it/i, "Must have storage and deletion table");
    assert.match(privacyHtml, /href=["']\/delete-account["']/i, "Privacy page must link to /delete-account portal");
    assert.match(privacyHtml, /id=["']privacy-lang-select["']/i, "Must have language selector for privacy page");
    assert.match(privacyHtml, /mailto:admin@jyotirmoyb\.com/i, "Must support deletion email to admin");
    assert.match(privacyHtml, /On-Device Encryption &amp; Data Storage/i, "Must preserve full legal text below");
    assert.doesNotMatch(privacyHtml, /\{\{[A-Z_]+\}\}/, "Privacy page must not contain unresolved template placeholders");
    assert.match(deleteAccountHtml, /Account &amp; Data Deletion Request/i, "Delete account page must have deletion heading");
    assert.match(deleteAccountHtml, /Delete my account and data/i, "Delete account page must describe in-app deletion");
    assert.match(deleteAccountHtml, /mailto:admin@jyotirmoyb\.com/i, "Delete account page must include deletion contact email");
    assert.doesNotMatch(deleteAccountHtml, /\{\{[A-Z_]+\}\}/, "Delete account page must not contain unresolved template placeholders");
  });

  await t.test("10. Mobile Responsiveness (360px) & Emerald Look", () => {
    assert.match(landingCss, /max-width:\s*1160px/i, "Max-width container");
    assert.match(landingCss, /min-height:\s*var\(--touch-target\)/i, "48px touch targets for accessibility");
    assert.match(landingCss, /--primary:\s*#064E3B/i, "Emerald primary color scheme");
    assert.match(landingCss, /prefers-color-scheme:\s*dark/i, "Dark mode media query");
  });

  await t.test("11. Terms of Service Page in 5 Languages + Support & Grievance Contacts + Links", () => {
    const termsHtmlPath = path.resolve(__dirname, "../web/terms.html");
    const termsJsPath = path.resolve(__dirname, "../web/terms.js");
    assert.ok(fs.existsSync(termsHtmlPath), "web/terms.html must exist");
    assert.ok(fs.existsSync(termsJsPath), "web/terms.js must exist");

    const termsHtml = fs.readFileSync(termsHtmlPath, "utf8");
    const { TERMS_I18N } = require(termsJsPath);

    const languages = ["en", "hi", "bn", "as", "ne"];
    for (const lang of languages) {
      assert.ok(TERMS_I18N[lang], `TERMS_I18N must include '${lang}'`);
      assert.ok(Object.keys(TERMS_I18N[lang]).length >= 18, `TERMS_I18N['${lang}'] must have complete translations`);
      assert.match(termsHtml, new RegExp(`<option\\s+value=["']${lang}["']`, "i"), `Terms language select must include '${lang}'`);
    }

    assert.match(termsHtml, /mailto:admin@jyotirmoyb\.com/i, "Terms must include support contact admin@jyotirmoyb.com");
    assert.match(termsHtml, /mailto:grievance@jyotirmoyb\.com/i, "Terms must include grievance contact grievance@jyotirmoyb.com");
    assert.match(landingHtml, /href=["']\/terms\.html["']/i, "Landing page must link to /terms.html");
    assert.match(privacyHtml, /href=["']\/terms\.html["']/i, "Privacy page must link to /terms.html");
    assert.match(privacyHtml, /mailto:grievance@jyotirmoyb\.com/i, "Privacy page must include grievance contact");
  });

  await t.test("12. Legal Pages Load landing.css & All Public HTML Pages Have Zero CSP-Blocked Inline Styles", () => {
    const legalPages = ["privacy.html", "terms.html", "delete-account.html"];
    for (const relPath of legalPages) {
      const content = fs.readFileSync(path.resolve(__dirname, "../web", relPath), "utf8");
      assert.match(
        content,
        /<link\s+rel=["']stylesheet["']\s+href=["']\/landing\.css["']/i,
        `${relPath} must load /landing.css for shared header, navigation, table, and footer styles`
      );
    }

    const publicHtmlPages = [
      "index.html",
      "privacy.html",
      "terms.html",
      "delete-account.html",
      "404.html",
      "guide/index.html",
      "trust/index.html",
      "updates/index.html",
      "u/index.html"
    ];
    for (const relPath of publicHtmlPages) {
      const content = fs.readFileSync(path.resolve(__dirname, "../web", relPath), "utf8");
      assert.doesNotMatch(
        content,
        /\sstyle=["'][^"']*["']/i,
        `${relPath} must not contain inline style='...' attributes (blocked by strict CSP style-src 'self')`
      );
    }
  });

  await t.test("13. Single-Line Navbar, SW Navigation Fix (/ledger/), v30.0 Changelog & Ledger CSP", () => {
    const swContent = fs.readFileSync(path.resolve(__dirname, "../web/sw.js"), "utf8");
    const ledgerJs = fs.readFileSync(path.resolve(__dirname, "../web/ledger/ledger.js"), "utf8");
    const updatesJson = JSON.parse(fs.readFileSync(path.resolve(__dirname, "../web/updates.json"), "utf8"));

    // Navbar nowrap rules
    assert.match(landingCss, /\.nav-links a\s*\{[^}]*white-space:\s*nowrap/i, "Navbar links must not wrap across multiple lines");
    assert.match(landingCss, /\.btn-header-play\s*\{[^}]*white-space:\s*nowrap/i, "Header CTA button must not wrap across multiple lines");

    // Service Worker must not cache redirecting trailing-slash routes or intercept document navigations
    const shellAssetsMatch = swContent.match(/const\s+SHELL_ASSETS\s*=\s*\[[\s\S]*?\];/);
    assert.ok(shellAssetsMatch, "SW must define SHELL_ASSETS");
    assert.doesNotMatch(shellAssetsMatch[0], /"\/ledger\/"/, "SW SHELL_ASSETS must not precache redirecting /ledger/ route");
    assert.doesNotMatch(shellAssetsMatch[0], /"\/u\/"/, "SW SHELL_ASSETS must not precache redirecting /u/ route");
    assert.match(swContent, /req\.mode\s*===\s*["']navigate["']/, "SW fetch handler must let document navigations bypass cache");
    assert.match(swContent, /!resp\.redirected|!networkResponse\.redirected/, "SW must never cache redirected responses");

    // Web Ledger JS must not emit CSP-blocked inline style attributes
    assert.doesNotMatch(ledgerJs, /style=["'][^"']*["']/, "web/ledger/ledger.js must not emit inline style attributes");

    // What's New / Version changelog must include v30.0 in all 5 languages
    assert.strictEqual(updatesJson[0].version, "30.0", "Latest updates.json entry must be v30.0");
    for (const lang of ["en", "hi", "bn", "as", "ne"]) {
      assert.ok(updatesJson[0].title[lang], `v30.0 changelog title missing '${lang}'`);
      assert.ok(Array.isArray(updatesJson[0].highlights[lang]) && updatesJson[0].highlights[lang].length >= 3, `v30.0 highlights missing '${lang}'`);
    }
  });

  await t.test("14. No Default reCAPTCHA Test Key, /ledger/ Browser-Local Notice & Subpage Mobile Drawers", () => {
    const webappFirebaseSdk = fs.readFileSync(path.resolve(__dirname, "../webapp/src/api/firebaseSdk.js"), "utf8");
    const ledgerHtml = fs.readFileSync(path.resolve(__dirname, "../web/ledger/index.html"), "utf8");
    const navDrawerJs = fs.readFileSync(path.resolve(__dirname, "../web/nav-drawer.js"), "utf8");

    // reCAPTCHA must be opt-in only (no hardcoded Google test site key)
    assert.doesNotMatch(webappFirebaseSdk, /6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI/, "webapp firebaseSdk must not ship hardcoded reCAPTCHA test key");
    assert.match(webappFirebaseSdk, /const\s+DEFAULT_RECAPTCHA_V3_SITE_KEY\s*=\s*""/, "DEFAULT_RECAPTCHA_V3_SITE_KEY must default to empty string");

    // /ledger/ Browser-Local Mode banner
    assert.match(ledgerHtml, /id=["']local-mode-banner["']/i, "/ledger/index.html must display Browser-Local Mode banner");
    assert.match(ledgerHtml, /Browser-Local Mode/i, "/ledger/index.html banner must explain Browser-Local Mode");

    // Shared mobile nav drawer on all subpages
    assert.match(navDrawerJs, /mobile-menu-btn/, "nav-drawer.js must wire mobile-menu-btn");
    const subpages = ["trust/index.html", "updates/index.html", "privacy.html", "terms.html", "delete-account.html"];
    for (const relPath of subpages) {
      const html = fs.readFileSync(path.resolve(__dirname, "../web", relPath), "utf8");
      assert.match(html, /src=["']\/nav-drawer\.js["']/i, `${relPath} must load /nav-drawer.js`);
      assert.match(html, /id=["']mobile-menu-btn["']/i, `${relPath} must include mobile hamburger button`);
      assert.match(html, /id=["']mobile-drawer["']/i, `${relPath} must include mobile drawer`);
      assert.doesNotMatch(html, /\sstyle=["'][^"']*["']/i, `${relPath} must stay 100% free of inline styles`);
    }
  });
});



