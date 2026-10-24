# v01d — Rebrand + Plug-and-Play UX Plan

> **STATUS (implemented)** — Sections 1–6 shipped, 50/50 tests green, verified on emulator.
> Bonus found & fixed during verification: **first-launch Skip → permanent lockout**
> (isLocked never cleared when no PIN is set — `MainActivity.onComplete` now calls `LockManager.unlock()`).
> Still open: About source-URL → real public repo (blocked on repo creation); contextual "?" tips;
> optional custom domain for the relay; Worker KV capacity (1k writes/day free) before real users.
>
> **Follow-up pass (companion/metadata/settings):** removed SimpleX, Session and InviZible from
> Companion Apps (InviZible = second VPN → conflicts with the built-in Tor VPN; Android allows one
> VPN at a time). Relay metadata: dropped the queued-message timestamp (worker redeployed),
> randomized poll intervals in Message/Chat screens (fixed cadence = timing fingerprint), added an
> in-app "IP visible to relay" warning when the Tor VPN is off (TorVpnService.isVpnActive).
> Settings: removed the alias-domain section (lives in the alias screen now), renamed Electrum →
> "Bitcoin server (advanced)" with a plain-language description, removed the "void.alias"
> placeholder default.
>
> **Sealed sender + per-relation rotating tokens (SHIPPED):** RelationToken.kt derives mailbox and
> bundle tokens as SHA256(domain ‖ relationKey ‖ epoch) where relationKey = SHA256(info ‖ senderPub
> ‖ recipientPub) — direction-specific, rotated every 24h (recipients also poll epoch-1; senders
> fall back to e−1/e+1 for bundles). Envelopes are now { type, msg } — the plaintext "from" field
> is GONE (sealed sender): the recipient maps the mailbox token back to the contact. The stable
> SHA256(pubkey) token is a LOCAL id only (contacts, history, Signal addresses) and never touches
> the relay. Bundles are published per relation (epoch, epoch+1), throttled 25 min per relation;
> adding a contact publishes immediately. Worker protocol unchanged (opaque tokens). Trade-offs:
> a contact absent >48h can't receive FIRST sessions (no fresh bundle) — existing chats keep
> working; pre-release compat break with older envelope format is accepted. 8 new unit tests
> (determinism, direction separation, epoch rotation, epoch math) — 58/58 green.
>
> **Follow-up pass (companion/metadata/settings):** removed SimpleX, Session and InviZible from
> Companion Apps (InviZible = second VPN → conflicts with the built-in Tor VPN; Android allows one
> VPN at a time). Relay metadata: dropped the queued-message timestamp (worker redeployed),
> randomized poll intervals in Message/Chat screens (fixed cadence = timing fingerprint), added an
> in-app "IP visible to relay" warning when the Tor VPN is off (TorVpnService.isVpnActive).
> Settings: removed the alias-domain section (lives in the alias screen now), renamed Electrum →
> "Bitcoin server (advanced)" with a plain-language description, removed the "void.alias"
> placeholder default.

Rename the app to **v01d** and remove every wall a non-expert user hits.
Principle: **default flows must work with zero configuration; expert options stay, but hidden behind "Advanced".**

---

## 1. Rebrand Void → v01d

Display name only — identity (`applicationId`, package, keystore) stays `com.voidprivacy.app`.

- [ ] `strings.xml`: `app_name` → "v01d"; rewrite all Void mentions ("Unlock Void", "Void is Locked",
      "Void Message", "Void User", "Void Tor VPN", `score_share_summary`, `companion_description`,
      `addcontact_*`) — keep sentence flow natural
- [ ] `metadata/com.voidprivacy.app.yml`: `AutoName: v01d`
- [ ] `fastlane/metadata/android/en-US/title.txt` + `short_description.txt` + `full_description.txt`
- [ ] `README.md` title + text; `docs/USER_GUIDE.md` title
- [ ] `AboutScreen` links: source URL → final public repo (decide org before submitting)
- [ ] Keep: `applicationId`, namespaces, keystore `void-release.jks` (alias `void` is invisible to users)

**Files:** strings.xml, metadata yml, fastlane/*, README.md, docs/*, AboutScreen.kt

---

## 2. Secure Message — relay, senza URL in faccia

### 2.1 Rename the Cloudflare Worker (free)
workers.dev subdomains and Workers are free. "Rename" = deploy a new worker with the same code:

1. `worker/wrangler.toml`: `name = "v01d-relay"` (keep the SAME `[[kv_namespaces]] id` → all data preserved)
2. `npx wrangler deploy` → new URL `https://v01d-relay.<subdomain>.workers.dev`
3. Optional (free feature): bind a custom domain (e.g. `relay.v01d.app`) via Workers Custom Domains
4. Delete the old worker. Old URL dies — fine, app is unreleased. Update now:
   - `MessageStorage.DEFAULT_RELAY_URL`
   - `worker/DEPLOY.md`

### 2.2 Hide the URL behind a choice
- [ ] MessageScreen setup: replace the raw URL field with two radio choices:
  - **Relay pubblico (consigliato)** — default, zero input
  - **Relay personale (avanzato)** — reveals the URL field for self-hosters
- [ ] Store mode + custom URL in `MessageStorage` (`relay_mode: public|custom`)
- [ ] One plain-language line: "I messaggi passano cifrati per un piccolo server che non può leggerli."
- [ ] Capacity note (dev/ops, not in-app): KV free tier = **1.000 scritture/giorno**. Before public
      launch either Workers Paid ($5/mo = 1M/day) or make self-host a first-class path in DEPLOY.md.

**Files:** worker/wrangler.toml, worker/DEPLOY.md, MessageStorage.kt, MessageScreen.kt

---

## 3. XMR Wallet — "Seed & Ricevi" (Option A)

Positioning: **offline wallet + receive**, operations delegated to Monerujo.

- [ ] Rework `XmrWalletScreen` into three plain sections:
  1. **Seed** — create / import / show 25 words (existing, already verified by tests)
  2. **Ricevi** — address + QR + copy + share (works offline today)
  3. **Invia / Saldo → "Apri in Monerujo"** — deep link (existing intent), with one-time hint if
     Monerujo is not installed → Companion Apps screen
- [ ] Move node + wallet-RPC config and the RPC balance card under a collapsed **"Avanzato"** card
      (still works for experts; remove `127.0.0.1` placeholder from default view)
- [ ] Strings: no "daemon", no "RPC" outside the Advanced section
- [ ] Empty/broken state must never appear in the default flow

**Files:** XmrWalletScreen.kt, XmrWalletStorage.kt, strings.xml

---

## 4. BTC Wallet — plug and play

### 4.1 Fee presets (real estimates)
`ElectrumClient.estimateFee(blocks)` already exists (returns BTC/kB).

- [ ] On SendScreen load: fetch `estimateFee(6)`, `estimateFee(3)`, `estimateFee(1)` → convert
      BTC/kB → sat/vB (×100 000) → three chips: **Lenta / Normale / Veloce** (default: Normale)
- [ ] Fallback to current default 10 sat/vB if estimate fails; keep manual field under "Avanzato"
- [ ] Show estimated total cost (amount + fee) in the review step

### 4.2 Fiat context
- [ ] Price fetch (public API, cached 5 min, optional / behind network consent) → "≈ €" next to
      amount and balance; input toggle BTC / sats / EUR on the amount field

### 4.3 Receive-first
- [ ] Wallet screen: **Receive (QR)** is the hero section; Send secondary; Advanced collapsed
- [ ] Move PSBT + xpub export into the "Avanzato" section (kept, hidden)

### 4.4 Network clarity
- [ ] Persistent banner on wallet screens: green **"MAINNET — soldi veri"** / amber
      **"TESTNET — moneta finta"** (testnet banner links to faucet hint)
- [ ] Switching network in Settings asks a confirm dialog

**Files:** SendScreen.kt, BtcWalletScreen.kt, ElectrumClient.kt (only if helper needed),
AppSettings.kt, strings.xml

---

## 5. Email Aliases — hybrid (real aliases + offline generator)

**Decision:** integrate **addy.io** (not SimpleLogin).
Why: addy.io free plan = **unlimited standard aliases** (your `username.anonaddy.me` subdomain),
API access on the free tier, open source and self-hostable.
SimpleLogin free = **10 aliases total** then hard cap → bad as a default.

### 5.1 Mode 1 (default): addy.io account
- [ ] Setup card: "Collega il tuo account addy.io (gratuito)" → paste API key
      (addy.io → Settings → API key). Validate with `GET /api/v1/api-token-details`
- [ ] Create alias: `POST https://app.addy.io/api/v1/aliases`
      `Authorization: Bearer <key>`, body `{"domain": "<chosen>", "description": "<site>",
      "format": "random"}` → show + copy returned email
- [ ] Domain picker from `GET /api/v1/usernames` + shared domains (free = standard subdomains)
- [ ] List recent aliases `GET /api/v1/aliases` (filter/sort, deactivate on tap)
- [ ] All calls HTTPS; API key stored encrypted (existing Keypref pattern)

### 5.2 Mode 2 (advanced): offline generator (today's tool, kept)
- [ ] Under "Generatore manuale / dominio proprio (avanzato)" with the 3-step in-app explainer:
      (1) serve un dominio → (2) attiva inoltro catch-all (Cloudflare Email Routing, gratis) →
      (3) incolla il dominio qui
- [ ] Persist the secret key (encrypted) so it's asked once; validate domain format; disable
      Generate until inputs valid

**Files:** EmailAliasScreen.kt (split into AddyAliasManager.kt + AliasGenerator kept), AppSettings.kt,
strings.xml

---

## 6. Explanations everywhere (in-app, not repo docs)

- [ ] New **Aiuto** screen: USER_GUIDE content rewritten in plain language, EN + IT
      (strings resources, not raw .md)
- [ ] Dashboard subtitles rewritten without jargon ("Generate HMAC aliases offline" →
      "Email alternative per non dare la tua email vera")
- [ ] Contextual "?" tips on: seed, fee, relay, fingerprint, xpub
- [ ] Fingerprint verification → word/pair-based comparison ("confronta le 6 parole") instead of hex
- [ ] Dashboard: "Inizia qui" section (Wallet → Ricevi → Prova messaggi) + collapsible
      "Strumenti avanzati" (PSBT, TX Check)

**Files:** HelpScreen.kt (new), DashboardScreen.kt, AddContactScreen.kt, strings.xml, values-it/strings.xml

---

## Order

```
1 Rebrand ─→ 2 Relay ─→ 3 XMR ─→ 4 BTC ─→ 5 Alias ─→ 6 Guide/UX copy
(each step: build + tests green + quick emulator check)
```

## Out of scope (later)
- Multiple fallback relays (needs relay health protocol)
- XMR native lib for in-app balance (NDK, big)
- Programmatic fee market data beyond Electrum estimates
