# Void — Phase 8 Plan: Tor VPN + XMR Foundation + Distribution

## Goal
Implement Tor VPN routing, lay XMR wallet groundwork, and finalize distribution. Target v0.5.0.

---

## 8.1 — Tor VPN Routing (VpnService)
**Priority: Critical** — the last major privacy feature.

- [ ] Create `TorVpnService` extending `VpnService`
- [ ] Capture all app traffic via TUN interface
- [ ] Route TCP through Tor SOCKS proxy (127.0.0.1:9050)
- [ ] DNS resolution through Tor (prevent DNS leaks)
- [ ] VPN toggle in TorScreen (replaces the current placeholder toggle)
- [ ] Persistent notification while VPN active
- [ ] Kill switch: block all traffic if Tor circuit drops
- [ ] Exclude Tor service itself from VPN (prevent loop)

**Files:** `tools/tor/TorVpnService.kt`, update `TorScreen.kt`, `TorManager.kt`, `AndroidManifest.xml`

---

## 8.2 — XMR Wallet Foundation
**Priority: High** — prepare UI + document NDK build.

- [ ] XMR wallet screen: seed generation UI, address display, QR code
- [ ] XMR wallet storage (EncryptedSharedPreferences, separate from BTC)
- [ ] Document monero-android NDK build process in `docs/XMR_BUILD.md`
- [ ] Add monero-android dependency stub (commented, with build instructions)
- [ ] Wire XMR tile on dashboard to the new screen (instead of companion apps)
- [ ] Balance/send UI stubs (enabled when monero library is available)

**Files:** `tools/wallet/xmr/XmrWalletScreen.kt`, `tools/wallet/xmr/XmrWalletStorage.kt`, `docs/XMR_BUILD.md`, update `DashboardScreen.kt`, `VoidNavHost.kt`

---

## 8.3 — F-Droid Final Submission
**Priority: Medium** — complete the distribution pipeline.

- [ ] Write `docs/FDROID_SUBMISSION.md` with step-by-step merge request instructions
- [ ] Verify all dependencies are F-Droid compliant (no proprietary blobs)
- [ ] Final screenshot capture instructions
- [ ] Git tag instructions for F-Droid build system

**Files:** `docs/FDROID_SUBMISSION.md`

---

## 8.4 — Version Bump + Final Polish
**Priority: Low** — ship v0.5.0.

- [ ] Bump to versionCode 5, versionName "0.5.0"
- [ ] Update README roadmap
- [ ] Run full test suite
- [ ] Clean build

**Files:** `app/build.gradle.kts`, `README.md`

---

## Suggested Order

```
8.1 Tor VPN ──→ 8.2 XMR Foundation ──→ 8.3 F-Droid Docs ──→ 8.4 v0.5.0
```

---

## Out of Scope for Phase 8

| Item | Reason |
|---|---|
| Full XMR send/receive | Requires monero C++ NDK build — needs physical device testing |
| Incoming tx notifications | Requires persistent Electrum WebSocket — phase 9 |
| SQLCipher encrypted databases | Adds heavy dependency — phase 9 |
| Multi-wallet support | YAGNI |
| Localization (i18n) | English-first |
