# Void — Phase 10 Plan: v1.0 Release

## Goal
Ship Void v1.0.0: secure data wipe, final polish, signed release build, and release-ready documentation.

---

## 10.1 — Secure Data Wipe
**Priority: High** — a privacy app must let users destroy all data.

- [ ] "Wipe All Data" button in Settings
- [ ] Confirmation dialog (type "WIPE" to confirm)
- [ ] Delete: BTC wallet, XMR wallet, contacts DB, tx history DB, score history, settings, app lock PIN
- [ ] Overwrite encrypted prefs before delete (best-effort)
- [ ] Restart app after wipe (back to first-launch state)

**Files:** update `SettingsScreen.kt`, new `DataWiper.kt`

---

## 10.2 — About Screen v1.0 Update
**Priority: Medium** — reflect the full feature set.

- [ ] Update feature list in AboutScreen
- [ ] Add version display (BuildConfig.VERSION_NAME)
- [ ] Add "Wipe Data" link in About
- [ ] Update "Built With" section (add SQLCipher, Room)

**Files:** update `AboutScreen.kt`

---

## 10.3 — Signed Release Build Verification
**Priority: High** — verify the release APK works.

- [ ] Build signed release APK with keystore
- [ ] Install on emulator and verify all screens
- [ ] Verify ProGuard/R8 didn't break anything
- [ ] Check APK size is reasonable

**Files:** keystore, build output

---

## 10.4 — Version Bump v1.0.0 + Final README
**Priority: Medium** — ship.

- [ ] Bump to versionCode 7, versionName "1.0.0"
- [ ] Final README: complete feature list, install instructions, build instructions, license
- [ ] Update F-Droid metadata to v1.0.0
- [ ] Run full test suite
- [ ] Clean build

**Files:** `app/build.gradle.kts`, `README.md`, `metadata/`

---

## Suggested Order

```
10.1 Data Wipe ──→ 10.2 About Update ──→ 10.3 Release Build ──→ 10.4 v1.0.0
```
