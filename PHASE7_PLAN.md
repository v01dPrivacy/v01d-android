# Void — Phase 7 Plan: Distribution & Polish

## Goal
Make the app distributable: reproducible builds, F-Droid submission, address book, and release hardening. Target v0.4.0.

---

## 7.1 — Address Book
**Priority: High** — save frequent recipients, auto-fill send form.

- [ ] Room entity: `ContactEntity` (id, name, address, note, createdAt)
- [ ] CRUD screen: list contacts, add/edit/delete
- [ ] SendScreen integration: pick from address book → auto-fill recipient
- [ ] QR scanner: option to save scanned address as contact
- [ ] Navigation: Address Book tile on dashboard

**Files:** `tools/contacts/ContactsDatabase.kt`, `tools/contacts/ContactsScreen.kt`, update `SendScreen.kt`, `VoidNavHost.kt`, `DashboardScreen.kt`

---

## 7.2 — Reproducible Build
**Priority: High** — F-Droid requires deterministic builds.

- [ ] Dockerfile: pinned JDK 17 + Android SDK + Gradle, deterministic build flags
- [ ] Build script: `./gradlew assembleRelease` with `--no-daemon --no-build-cache`
- [ ] Verification script: build twice, compare APK SHA-256 hashes
- [ ] Document the process in README

**Files:** `Dockerfile`, `scripts/verify-reproducible.sh`, update `README.md`

---

## 7.3 — Release Build Hardening
**Priority: Medium** — ensure R8/ProGuard doesn't break libraries.

- [ ] Verify release APK on emulator (all screens, wallet gen, Tor start)
- [ ] Update ProGuard rules for Room, bitcoinj, ZXing, tor-android, biometric
- [ ] Add missing keep rules if release build strips needed classes
- [ ] Test signed release APK install + basic flow

**Files:** `app/proguard-rules.pro`

---

## 7.4 — F-Droid Final Submission
**Priority: Medium** — complete the distribution pipeline.

- [ ] Finalize `metadata/com.voidprivacy.app.yml` with v0.4.0 build recipe
- [ ] Update fastlane screenshots (capture from emulator at correct resolution)
- [ ] Write F-Droid merge request instructions in README
- [ ] Verify all dependencies are F-Droid compliant (no proprietary blobs)
- [ ] Create git tag `v0.4.0` for F-Droid build system

**Files:** `metadata/`, `fastlane/`, `README.md`

---

## 7.5 — Version Bump + Final Polish
**Priority: Low** — ship v0.4.0.

- [ ] Bump to versionCode 4, versionName "0.4.0"
- [ ] Update README roadmap (check off completed items)
- [ ] Run full test suite
- [ ] Clean build from scratch

**Files:** `app/build.gradle.kts`, `README.md`

---

## Suggested Order

```
7.1 Address Book ──→ 7.3 Release Hardening ──→ 7.2 Reproducible Build
                                                        │
7.4 F-Droid Submission ←────────────────────────────────┤
                                                        │
7.5 Version Bump ←──────────────────────────────────────┘
```

---

## Out of Scope for Phase 7

| Item | Reason |
|---|---|
| XMR embedded wallet | Requires monero C++ NDK build — separate project |
| Tor VPN routing (VpnService) | Complex Android API — phase 8 |
| Incoming tx notifications | Requires persistent Electrum WebSocket — phase 8 |
| Multi-wallet support | YAGNI |
| Localization (i18n) | English-first |
