# v01d — Privacy Toolbox for Android

A free, open-source privacy toolbox designed for GrapheneOS (works on any Android 12+).

## Features

| Tool | Description | Offline |
|---|---|---|
| **BTC Wallet** | BIP-39 seed generation, native SegWit (bc1q) addresses, QR codes, encrypted storage, balance (with € equivalent), import from seed | ✅ |
| **BTC Send** | Coin selection, fee presets from live estimates, sign & broadcast via Electrum | ⚠️ Electrum |
| **PSBT Signer** | Parse, inspect, and sign BIP-174 PSBTs with wallet keys | ✅ |
| **XMR Wallet** | Offline Monero seed (25 words) + receive address & QR; balance/send via Monerujo | ✅ |
| **Secure Message** | E2E encrypted chat (Signal PQXDH + Double Ratchet): sealed sender, per-relation rotating relay tokens, files, burn-after-read identity, QR contact exchange | ⚠️ Relay (Tor-able) |
| **Email Aliases** | Real disposable addresses via addy.io API, or offline HMAC generator for your own domain | ✅ / API |
| **TX Privacy Check** | 7 heuristics: address reuse, peeling chains, change detection, round amounts, mixed types | ✅ |
| **Privacy Score** | Device checks: verified boot, SELinux, GrapheneOS, FBE, USB debug, VPN, DNS-over-TLS. Score history + share | ✅ |
| **Tor Routing** | System-wide Tor VPN (TCP + DNS over Tor) + embedded Tor service via tor-android | ✅ |
| **Companion Apps** | Launch/install: Monerujo, Auditor, InterProfileSharing | — |

## Requirements

- Android 12+ (API 31)
- Optimized for GrapheneOS on Pixel devices

## Build

```bash
# Requires JDK 17 + Android SDK
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk

./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Tech Stack

- Kotlin + Jetpack Compose + Material 3 (Material You)
- bitcoinj 0.16.3 (BTC wallet)
- ZXing (QR codes)
- Guardian Project tor-android (Tor service)
- AndroidX Security Crypto (encrypted wallet storage)

## License

[GPL 3.0](LICENSE)

## Roadmap

- [x] BTC wallet (generate, import, QR, encrypted storage)
- [x] BTC send (Electrum, coin selection, broadcast)
- [x] PSBT signer (parse + sign)
- [x] Email aliases (HMAC-SHA256)
- [x] TX privacy checker (7 heuristics)
- [x] Privacy score (8 device checks + history)
- [x] Embedded Tor service
- [x] Companion app launcher
- [x] Settings (theme, Electrum server, alias domain, network)
- [x] Unit tests (17 tests)
- [x] App lock (PIN + biometric)
- [x] Transaction history (Room DB)
- [x] QR scanner (BIP-21 payments)
- [x] Testnet support
- [x] Address book (Room CRUD)
- [x] Reproducible build (Dockerfile + verify script)
- [x] ProGuard/R8 hardening
- [x] Tor VPN routing (VpnService)
- [x] XMR wallet foundation (seed storage + UI)
- [x] F-Droid submission docs
- [x] SQLCipher encrypted databases
- [x] Wallet export (xpub for external signers)
- [x] FLAG_SECURE (screenshot prevention)
- [x] Secure data wipe
- [x] v1.0.0 release
- [x] XMR full wallet (pure Kotlin crypto: address gen, balance, history via RPC)
- [x] Secure messaging (Signal Protocol PQXDH + Double Ratchet, blind relay, files)
- [ ] F-Droid merge request (awaiting submission)
