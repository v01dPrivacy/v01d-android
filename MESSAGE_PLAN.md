# Void Message — Private Messaging Module

Add E2E-encrypted 1-on-1 messaging to void-android.

## Design decisions (locked)
- **Transport**: Blind store-and-forward relay (like void-server)
- **Crypto**: Signal's libsignal (X3DH + Double Ratchet) — vetted, not hand-rolled
- **Server**: Cloudflare Worker + KV (serverless, no self-hosted box)
- **Scope**: Text + file transfer
- **Spirit**: focused tool like void-veil-p2p — NOT a Signal/Telegram clone.
  No groups, no accounts, no cloud backup. Identity by QR. Optional burn-after-read.

## Architecture

```
Phone A                          Cloudflare Worker              Phone B
───────                          ─────────────────              ───────
libsignal encrypt  ──HTTPS──►   KV mailbox[token]  ◄──poll──   libsignal decrypt
(routed via Tor VPN)            (opaque blobs, TTL)            (routed via Tor VPN)
```

The Worker stores **opaque encrypted blobs** keyed by a routing token.
It never decrypts, never logs, never correlates. Zero-knowledge.

## Protocol

### Identity
- Each user = libsignal `IdentityKeyPair` (X25519), generated on-device, stored encrypted.
- **routing_token = SHA256(identity_public_key)** — stable, no registration needed.
- Fingerprint = hex of pubkey (for out-of-band verification).

### Contact exchange
- QR code = JSON { name, identity_pubkey_b64 }
- Scan → save contact (SQLCipher/encrypted prefs) → compute their routing_token.

### Messaging
- Build libsignal session from contact's PreKeyBundle (X3DH) on first message.
- Each message: `SessionCipher.encrypt(plaintext)` → CiphertextMessage → b64 blob.
- Send: `POST /send { token, blob }` to Worker.
- Receive: `GET /poll?token=mine` → blobs → `SessionCipher.decrypt` → display.
- Polling (mobile-friendly, no WebSocket).

### Files
- Hybrid: random AES-256-GCM key encrypts file body; key wrapped to recipient
  via X25519 ECIES (BouncyCastle, already in app).
- Chunked to fit relay max size; reassembled on receive.

### Cloudflare Worker (KV blind mailbox)
- `POST /send` { token, blob } → append to mailbox, TTL
- `GET /poll?token=X` → return + clear queued blobs
- Rate limit per IP, max mailbox size, message TTL
- ~100 lines. Stateless. No registration endpoint.

## Phases

1. **libsignal + Identity** — add dep, IdentityManager (keypair gen + encrypted persist),
   fingerprint, QR export. *Foundation; verify libsignal resolves first.*
2. **Signal stores + sessions** — libsignal store interfaces over encrypted storage,
   SessionManager (X3DH session build), unit tests.
3. **Cloudflare Worker relay** — KV mailbox Worker + wrangler config + RelayClient (Kotlin, via Tor).
4. **Messaging UI** — chat list, chat screen, add-contact (QR scan), send/receive + poll service.
5. **File transfer** — AES-GCM + X25519 wrap, chunking, send/receive UI.
6. **Polish** — Tor routing, dashboard tile, navigation, tests, version bump.

## Files (new)
- `tools/message/IdentityManager.kt`
- `tools/message/SignalStores.kt`
- `tools/message/SessionManager.kt`
- `tools/message/RelayClient.kt`
- `tools/message/MessageManager.kt`
- `tools/message/FileCrypto.kt`
- `tools/message/MessageStorage.kt` (contacts + messages, encrypted)
- `tools/message/MessageScreen.kt`, `ChatScreen.kt`, `AddContactScreen.kt`
- `worker/relay.ts` + `worker/wrangler.toml`
- Tests: `SignalProtocolTest.kt`, `FileCryptoTest.kt`

## Security notes
- libsignal is AGPL-3.0 — compatible with app's GPL-3.0 (open source, fine).
- Worker talks over HTTPS; client routes through Tor VPN for IP anonymity.
- Keys in EncryptedSharedPreferences / SQLCipher (existing app pattern).
- ponytail: stores backed by encrypted prefs (small contact count); move to Room if many contacts.
