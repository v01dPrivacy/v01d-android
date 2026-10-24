# Void — Phase 9 Plan: Encryption, Export & Hardening

## Goal
Encrypt all local databases, add wallet export for external signers, expand test coverage, and harden for v1.0. Target v0.6.0.

---

## 9.1 — SQLCipher Encrypted Databases
**Priority: Critical** — contacts and tx history are stored in plaintext Room DBs.

- [ ] Add `net.zetetic:sqlcipher-android` + `androidx.sqlite:sqlite-ktx` dependencies
- [ ] Create encrypted `SupportFactory` with a key derived from the app lock PIN (or a generated key in Keystore)
- [ ] Migrate `TxDatabase` to use encrypted Room
- [ ] Migrate `ContactsDatabase` to use encrypted Room
- [ ] Handle key management: generate a random DB key on first launch, store in EncryptedSharedPreferences
- [ ] Verify existing data migrates cleanly (or start fresh with encrypted DBs)

**Files:** `app/build.gradle.kts`, `TxDatabase.kt`, `ContactsDatabase.kt`, new `DbKeyManager.kt`

---

## 9.2 — Wallet Export (xpub + addresses)
**Priority: High** — export wallet data for use with external signers (Sparrow, Coldcard, Specter).

- [ ] Export xpub (BIP-84 account-level extended public key)
- [ ] Export wallet as JSON: { xpub, addresses[], network, label }
- [ ] Share via Android share sheet (text/JSON)
- [ ] Copy xpub to clipboard
- [ ] Add "Export Wallet" button to BtcWalletScreen

**Files:** update `BtcWalletScreen.kt`, `BtcWalletEngine.kt`

---

## 9.3 — Expanded Test Coverage
**Priority: Medium** — 17 tests for 39 files is thin.

- [ ] `PsbtParserTest` — parse a known base64 PSBT, verify tx extraction
- [ ] `LockManagerTest` — PIN hashing with salt, verify/lock/unlock flow
- [ ] `NetworkHelperTest` — mainnet/testnet param selection
- [ ] `BtcWalletEngineTest` — seed generation determinism, address format
- [ ] Target: 30+ tests

**Files:** `app/src/test/java/com/voidprivacy/app/` (4 new test files)

---

## 9.4 — Accessibility + Final Hardening
**Priority: Medium** — v1.0 readiness.

- [ ] Audit all screens for missing `contentDescription` on icons/images
- [ ] Ensure minimum touch targets (48dp) on all interactive elements
- [ ] Add `android:screenReaderFocusable` where needed
- [ ] Verify FLAG_SECURE on wallet screens (prevent screenshots of seed/keys)
- [ ] Add `android:usesCleartextTraffic="false"` to manifest

**Files:** various screens, `AndroidManifest.xml`

---

## 9.5 — Version Bump v0.6.0
**Priority: Low** — ship.

- [ ] Bump to versionCode 6, versionName "0.6.0"
- [ ] Update README roadmap
- [ ] Run full test suite
- [ ] Clean build

**Files:** `app/build.gradle.kts`, `README.md`

---

## Suggested Order

```
9.1 SQLCipher ──→ 9.2 Wallet Export ──→ 9.3 Tests ──→ 9.4 Accessibility ──→ 9.5 v0.6.0
```

---

## Out of Scope for Phase 9

| Item | Reason |
|---|---|
| XMR full wallet | Requires monero C++ NDK build + physical device |
| Tor VPN bidirectional | Requires userspace TCP stack or tun2socks |
| F-Droid merge request | Ready to submit — user action |
| Incoming tx notifications | Requires persistent Electrum WebSocket — phase 10 |
| Localization (i18n) | English-first |
