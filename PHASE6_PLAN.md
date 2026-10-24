# Void — Phase 6 Plan: Security Hardening & Wallet Maturity

## Goal
Make the wallet production-ready: app lock, transaction history, QR scanning for payments, and testnet support. Target v0.3.0.

---

## 6.1 — App Lock (PIN + Biometric)
**Priority: Critical** — a wallet app without a lock screen is a liability.

- [ ] First-launch PIN setup (4-6 digits, stored as hashed value in EncryptedSharedPreferences)
- [ ] Lock screen on app open: PIN entry or biometric (fingerprint) via `BiometricPrompt`
- [ ] Auto-lock after configurable timeout (30s / 1m / 5m / never)
- [ ] Lock on app background (Activity lifecycle `onStop`)
- [ ] "Lock Now" button in Settings
- [ ] Wrong PIN: 3 attempts → 30s cooldown
- [ ] No PIN recovery — if forgotten, wallet is still recoverable via seed phrase (make this clear)

**Files:** `ui/lock/LockScreen.kt`, `ui/lock/PinSetupScreen.kt`, `LockManager.kt`, update `MainActivity.kt`, `SettingsScreen.kt`

---

## 6.2 — Transaction History
**Priority: High** — users need to see past transactions.

- [ ] Room database: `TxEntity` (txid, amount, address, timestamp, direction, confirmations)
- [ ] Fetch history via Electrum `blockchain.scripthash.get_history` for all wallet addresses
- [ ] History tab on BtcWalletScreen: list of incoming/outgoing txs with amount + date
- [ ] Tap tx → detail view (txid, inputs, outputs, confirmations, link to block explorer)
- [ ] Sync on wallet open + manual refresh
- [ ] Store in Room for offline access between syncs

**Files:** `tools/wallet/btc/TxDatabase.kt`, `tools/wallet/btc/TxHistoryScreen.kt`, update `BtcWalletScreen.kt`, `UtxoManager.kt`

---

## 6.3 — QR Scanner for BIP-21 Payments
**Priority: Medium** — scan a payment request QR to auto-fill send form.

- [ ] Camera permission + CameraX preview (or `zxing-android-embedded` for simplicity)
- [ ] Parse BIP-21 URIs: `bitcoin:<address>?amount=<btc>&label=<label>&message=<msg>`
- [ ] Scan button on SendScreen → opens scanner → auto-fills recipient + amount
- [ ] Also scan from dashboard (quick action)
- [ ] Handle invalid URIs gracefully

**Files:** `ui/scanner/QrScannerScreen.kt`, update `SendScreen.kt`, `AndroidManifest.xml` (CAMERA permission)

---

## 6.4 — Testnet Support
**Priority: Medium** — test without risking real funds.

- [ ] Network selector in Settings: Mainnet / Testnet
- [ ] Switch bitcoinj params (`MainNetParams` ↔ `TestNet3Params`)
- [ ] Electrum testnet server default: `electrum.blockstream.info:60002`
- [ ] Show "TESTNET" badge on wallet screen when active
- [ ] Testnet addresses start with `tb1` / `m` / `n` / `2`
- [ ] Separate wallet storage per network (don't mix mainnet/testnet keys)

**Files:** update `BtcWalletEngine.kt`, `WalletStorage.kt`, `ElectrumClient.kt`, `SendScreen.kt`, `SettingsScreen.kt`, `AppSettings.kt`

---

## 6.5 — F-Droid Submission
**Priority: Low** — final distribution step.

- [ ] Verify reproducible build: build twice in clean Docker containers, compare APK hashes
- [ ] Create F-Droid merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata)
- [ ] Add `metadata/com.voidprivacy.app.yml` build recipe with correct commit tag
- [ ] Ensure all dependencies are F-Droid compliant (no proprietary blobs)
- [ ] Take final screenshots on a real device (or high-res emulator)
- [ ] Test the full flow: install from F-Droid → generate wallet → send → receive

**Files:** `metadata/`, `fastlane/`, Dockerfile for reproducible build

---

## Suggested Order

```
6.1 App Lock ──→ 6.2 TX History ──→ 6.3 QR Scanner
                                          │
6.4 Testnet ──────────────────────────────┤
                                          │
6.5 F-Droid Submission ←──────────────────┘
```

6.1 first (security is non-negotiable for a wallet).
6.2 + 6.3 are the core wallet UX completions.
6.4 enables safe testing.
6.5 last (needs a stable, feature-complete app).

---

## Out of Scope for Phase 6

| Item | Reason |
|---|---|
| XMR embedded wallet | Requires monero C++ NDK build — phase 7 |
| Tor VPN routing (VpnService) | Complex Android API — phase 7 |
| Multi-wallet support | YAGNI |
| Address book / contacts | Nice-to-have, not essential |
| Localization (i18n) | English-first, add later |
| Incoming tx notifications | Requires persistent Electrum connection — phase 7 |
