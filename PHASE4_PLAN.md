# Void — Phase 4 Plan

## Goal
Make the BTC wallet fully functional (send + receive), prepare for F-Droid release, and add app infrastructure.

---

## 4.1 — Electrum Client (BTC network layer)
**Priority: Critical** — the wallet can't send without it.

- [ ] Implement a minimal Electrum protocol client (TCP, JSON-RPC)
  - Connect to a user-configurable Electrum server (default: a public server)
  - `blockchain.scripthash.get_history` — fetch UTXOs for the wallet's addresses
  - `blockchain.scripthash.listunspent` — get spendable UTXOs
  - `blockchain.transaction.broadcast` — broadcast signed tx
  - `blockchain.estimatefee` — fee estimation
- [ ] Connection UI: server address field, connect/disconnect, status indicator
- [ ] TLS support (Electrum servers use TLS on port 50002)
- [ ] Timeout + retry logic
- [ ] Privacy note: warn user that the server sees their addresses

**Files:** `tools/wallet/btc/ElectrumClient.kt`, `tools/wallet/btc/NetworkScreen.kt`

---

## 4.2 — BTC Send Flow
**Priority: Critical** — completes the wallet.

- [ ] UTXO management: fetch and cache wallet UTXOs via Electrum
- [ ] Coin selection: simple largest-first algorithm (privacy: note the tradeoff)
- [ ] Fee estimation: sat/vB from Electrum, user-adjustable
- [ ] Send screen: recipient address + amount + fee slider → preview → sign → broadcast
- [ ] Transaction confirmation dialog showing all outputs (verify change address)
- [ ] Broadcast via Electrum, show TXID + copy button
- [ ] Balance display on wallet home (sum of UTXOs)

**Files:** `tools/wallet/btc/SendScreen.kt`, `tools/wallet/btc/UtxoManager.kt`, update `BtcWalletScreen.kt`

---

## 4.3 — Wallet Backup & Security
**Priority: High** — a wallet without backup is a liability.

- [ ] Export seed as encrypted file (AES, user password) to shared storage
- [ ] Import wallet from seed phrase (restore flow)
- [ ] Seed verification: show 12 words, ask user to confirm word #3, #7, #11
- [ ] Auto-lock: hide seed after 30s of display
- [ ] Clipboard auto-clear: clear seed from clipboard after 60s

**Files:** `tools/wallet/btc/BackupManager.kt`, update `BtcWalletScreen.kt`

---

## 4.4 — Settings + About
**Priority: Medium** — app infrastructure.

- [ ] Settings screen:
  - Theme: system / light / dark
  - Electrum server address
  - Default alias domain
  - Tor: auto-start toggle
- [ ] About screen: version, license (GPL 3.0), links (GitHub, Tor Project)
- [ ] Persist settings via DataStore (or SharedPreferences)

**Files:** `ui/settings/SettingsScreen.kt`, `ui/about/AboutScreen.kt`, `AppSettings.kt`

---

## 4.5 — F-Droid Release Prep
**Priority: Medium** — distribution.

- [ ] Generate a release signing key (`keytool`)
- [ ] Configure `signingConfigs` in build.gradle.kts (keystore from env vars)
- [ ] Build signed release APK
- [ ] Take screenshots on emulator (5 required: dashboard, wallet, aliases, tx checker, score)
- [ ] Add screenshots to `fastlane/metadata/android/en-US/images/phoneScreenshots/`
- [ ] Create F-Droid metadata YAML (`metadata/com.voidprivacy.app.yml`)
- [ ] Test reproducible build (build twice, compare APK hashes)
- [ ] Update CI to produce signed releases on tag push

**Files:** `metadata/`, `fastlane/`, `.github/workflows/build.yml`, `app/build.gradle.kts`

---

## 4.6 — Privacy Score Enhancements
**Priority: Low** — polish.

- [ ] Add checklist items: VPN active?, DNS-over-TLS?, app permissions audit
- [ ] GrapheneOS-specific: Storage Scopes, Contact Scopes, sensor toggles
- [ ] Score history: store past scores, show trend
- [ ] Share score as text (no network — generate shareable summary)

**Files:** update `tools/score/PrivacyScoreScreen.kt`

---

## Suggested Order

```
4.1 Electrum Client  ──→  4.2 BTC Send  ──→  4.3 Backup
                                                  │
4.4 Settings + About  ────────────────────────────┤
                                                  │
4.5 F-Droid Prep  ←───────────────────────────────┘
4.6 Score Enhancements (anytime)
```

4.1 → 4.2 are the critical path (wallet is useless without send).
4.3 + 4.4 can be done in parallel.
4.5 should be last (needs a stable, feature-complete app).

---

## Out of Scope for Phase 4

| Item | Reason |
|---|---|
| XMR embedded wallet | Requires monero C++ NDK build — separate phase |
| Tor VPN routing | Needs VpnService API — complex, separate phase |
| iOS / Desktop | Different platform — separate project |
| Multi-wallet support | YAGNI until single wallet is solid |
