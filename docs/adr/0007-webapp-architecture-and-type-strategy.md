# ADR-0007: Web App Architecture and Type Strategy

- **Status**: Accepted
- **Date**: 2026-11-07
- **Deciders**: Principal Backend & Security Engineer, Web Lead, Mobile Lead, Data Governance Lead

---

## 1. Context and Problem Statement

Udhaar Platform v2 provides a responsive, local-first Progressive Web Application (PWA) operating at `app.udhaar.jyotirmoyb.com` designed to serve users in India, Nepal, and across South Asia. A significant portion of target users access the web application on entry-level Android devices running Chrome or Android WebViews over variable 3G/4G network conditions.

The initial implementation in `webapp/` was structured using plain ECMAScript modules with a lightweight custom packaging pipeline (`scripts/build-webapp.js`) and no external JavaScript runtime framework (such as React, Vue, or Angular).

We must decide whether to:
1. Rewrite the web application into **Strict TypeScript with Preact**; or
2. Retain **Plain JavaScript Modules with Strict JSDoc Type Annotations** enforced via TypeScript's compiler (`tsc --checkJs`).

---

## 2. Decision Drivers

- **Zero-Bloat Performance Budget**: The total compressed shell of the Web App must remain well below the statutory $300\text{ KB}$ performance budget to achieve sub-second Time-to-Interactive (TTI) on 3G cellular connections.
- **Low-End Hardware Memory Footprint**: Avoiding virtual DOM tree diffing and synthetic event trees that consume memory and cause garbage collection stutters on 1GB/2GB RAM Android Go devices.
- **Strict Content Security Policy (CSP)**: Compliance with enterprise banking-grade CSP (`default-src 'none'; script-src 'self'; style-src 'self'`), strictly prohibiting `unsafe-inline` and `unsafe-eval`.
- **Compile-Time Type Safety & Data Integrity**: Comprehensive static type checking across models, API responses, database transactions, and cryptographic operations without adding transpilation overhead.
- **Deterministic, Instantaneous Builds**: Sub-second deterministic asset assembly without dependency lock-in or complex bundler toolchains.

---

## 3. Considered Options

### Option 1: Preact + TypeScript (TSX)
- *Pros*: Familiar component hierarchy, JSX syntax, built-in TypeScript compilation via Vite.
- *Cons*:
  - Adds ~15–25 KB minified runtime dependency overhead.
  - Introduces virtual DOM reconciliation and reactive state churn on low-memory devices.
  - Requires continuous maintenance of JSX build transpilers and bundler plugins.
  - Increases cold start parse and compilation times on slower mobile CPUs.

### Option 2: Plain JavaScript Modules with Strict JSDoc + `tsc --checkJs` (Selected)
- *Pros*:
  - **Zero Runtime Overhead**: Direct DOM manipulation with vanilla JavaScript modules; zero virtual DOM overhead.
  - **Minimal Bundle Size**: Entire application shell is ~185 KB uncompressed (including CSS, fonts, and icons), staying far below the 300 KB budget.
  - **Strict Type Checking**: TypeScript compiler evaluates all JSDoc types via `"checkJs": true`, providing full compile-time type verification, parameter checking, and IDE auto-completion.
  - **Instantaneous Builds**: Builds in $<500\text{ ms}$ with standard Node.js scripts.
  - **Clean CSP**: No runtime code generators or dynamic evaluation needed.
- *Cons*: Requires discipline in maintaining explicit `@param`, `@returns`, and `@typedef` JSDoc annotations.

---

## 4. Decision Outcome

Chosen Option: **Option 2 — Plain JavaScript Modules with Strict JSDoc and `tsc --checkJs`**.

### Implementation Rules:
1. **TypeScript Type Checking**:
   - `webapp/tsconfig.json` is configured with `"allowJs": true`, `"checkJs": true`, and `"strict": true`.
   - CI runs `npx tsc -p webapp/tsconfig.json` to verify zero type errors across all modules in `webapp/src/`.
2. **JSDoc Typing Standards**:
   - Every class, method, function parameter, return type, and database model must have explicit JSDoc annotations (`@type`, `@param`, `@returns`, `@typedef`).
3. **Distribution Tracking**:
   - `webapp/dist/` is permanently removed from git tracking and ignored via `.gitignore`.
   - `scripts/build-webapp.js` packages the production distribution in CI prior to deployment and testing.

---

## 5. Consequences

- **Positive**:
  - Fastest possible First Contentful Paint (FCP) and Time-to-Interactive (TTI).
  - Uncompromised type safety without adding third-party runtime dependencies.
  - 100% compliant with strict banking-grade CSP (`script-src 'self'`).
- **Negative**:
  - Developers must write JSDoc comments rather than TS syntax.

---

## 6. References
- [Enterprise Production Standards (Rule 1.2 Schema Enforcement)](file:///C:/Users/TEST/antigravity/Udhaar-2026-11-03-5112b/docs/DATA_MODEL.md)
- [Performance Budgets](file:///C:/Users/TEST/antigravity/Udhaar-2026-11-03-5112b/docs/launch-checklist.md)
- [ADR-0001: Local-First Architecture](file:///C:/Users/TEST/antigravity/Udhaar-2026-11-03-5112b/docs/adr/0001-local-first-architecture.md)
