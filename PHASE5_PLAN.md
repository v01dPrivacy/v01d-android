# Void — Phase 5 Plan: Polish, Correctness & Testing

## Goal
Wire up disconnected settings, add tests for critical logic, enhance privacy score, and polish the app for a stable v0.2.0 release.

---

## 5.1 — Wire Disconnected Settings
**Priority: Critical** — three settings exist in UI but do nothing.

- [ ] **Theme setting**: `VoidTheme` ignores `AppSettings.getTheme()` — always follows system. Read the setting and apply `darkTheme` parameter accordingly.
- [ ] **Electrum server setting**: `SendScreen` and `UtxoManager` use hardcoded `ElectrumClient.DEFAULT_HOST/PORT`. Read from `AppSettings` instead.
- [ ] **Alias domain setting**: `EmailAliasScreen` hardcodes `"void.alias"` as default. Read from `AppSettings.getAliasDomain()`.

**Files:** `Theme.kt`, `SendScreen.kt`, `UtxoManager.kt`, `EmailAliasScreen.kt`

---

## 5.2 — Unit Tests for Critical Logic
**Priority: High** — a wallet app without tests is a liability.

- [ ] `AliasGeneratorTest` — deterministic output, same key+site = same alias, different site = different alias
- [ ] `TxPrivacyAnalyzerTest` — parse a known raw tx hex, verify heuristic findings and score
- [ ] `PsbtParserTest` — parse a known base64 PSBT, verify tx extraction + witness UTXOs
- [ ] `UtxoManagerTest` — coin selection: sufficient funds, insufficient funds, exact amount, fee estimation
- [ ] `ElectrumClientTest` — scriptHash computation (known address → known hash)
- [ ] `PsbtSignerTest` — parseWitnessUtxo with known byte arrays

**Files:** `app/src/test/java/com/voidprivacy/app/…` (6 test files)

---

## 5.3 — Wallet Balance on Home Screen
**Priority: Medium** — user shouldn't need to open Send to see balance.

- [ ] Show balance (confirmed + unconfirmed) on BtcWalletScreen below the QR code
- [ ] Fetch via `UtxoManager.fetchWalletState()` in `LaunchedEffect`
- [ ] Pull-to-refresh or manual refresh button
- [ ] Show "Offline" if Electrum unreachable

**Files:** update `BtcWalletScreen.kt`

---

## 5.4 — Privacy Score Enhancements
**Priority: Medium** — richer device checks.

- [ ] VPN active check (`ConnectivityManager` → active network capabilities)
- [ ] DNS-over-TLS check (`Settings.Global` → `private_dns_mode`)
- [ ] Default browser check (is it a privacy browser?)
- [ ] Score history: store last N scores in SharedPreferences, show simple trend
- [ ] Share score as text (generate summary string, no network)

**Files:** update `PrivacyScoreScreen.kt`, `PrivacyScorer`

---

## 5.5 — Release Polish
**Priority: Medium** — v0.2.0 readiness.

- [ ] Bump version to 0.2.0 (versionCode 2)
- [ ] Verify ProGuard rules: release build + run on emulator (check R8 doesn't strip bitcoinj/Tor classes)
- [ ] Update README roadmap (check off completed items)
- [ ] Update F-Droid full_description.txt with new features (send, import, settings)
- [ ] Clone correct `session-android` repo into `repo/` (replace express-session)
- [ ] Add `repo/` to `.gitignore` (reference repos shouldn't be in the app repo)

**Files:** `build.gradle.kts`, `README.md`, `fastlane/`, `.gitignore`

---

## Suggested Order

```
5.1 Wire Settings ──→ 5.2 Unit Tests ──→ 5.3 Balance
                                              │
5.4 Score Enhancements ───────────────────────┤
                                              │
5.5 Release Polish ←──────────────────────────┘
```

5.1 first (broken features are worse than missing features).
5.2 before 5.3/5.4 (tests protect the code we're about to change).
5.5 last (version bump + docs after everything works).

---

## Out of Scope for Phase 5

| Item | Reason |
|---|---|
| XMR embedded wallet | Requires monero C++ NDK build — phase 6 |
| Tor VPN routing (VpnService) | Complex Android API — phase 6 |
| Transaction history | Needs persistent DB (Room) — phase 6 |
| Multi-wallet support | YAGNI until single wallet is solid |
| iOS / Desktop | Different platform entirely |
