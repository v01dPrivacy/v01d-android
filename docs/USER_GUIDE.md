# v01d — User Guide

v01d is a **privacy toolbox** for Android. It works fully offline for most tools, and it never sends your data anywhere unless you explicitly connect to a server (for example, to check a wallet balance). This guide explains every feature in simple words.

Works best on **GrapheneOS**, but runs on any Android 12+ phone.

---

## Contents

1. [First launch](#1-first-launch)
2. [The dashboard](#2-the-dashboard)
3. [BTC Wallet](#3-btc-wallet)
4. [Sending Bitcoin](#4-sending-bitcoin)
5. [PSBT Signer](#5-psbt-signer)
6. [XMR Wallet (Monero)](#6-xmr-wallet-monero)
7. [Secure Message](#7-secure-message)
8. [Email Aliases](#8-email-aliases)
9. [TX Privacy Check](#9-tx-privacy-check)
10. [Privacy Score](#10-privacy-score)
11. [Tor Routing](#11-tor-routing)
12. [Companion Apps](#12-companion-apps)
13. [Address Book](#13-address-book)
14. [Settings](#14-settings)
15. [App lock](#15-app-lock)
16. [Wiping all data](#16-wiping-all-data)
17. [Safety rules](#17-safety-rules)

---

## 1. First launch

The first time you open v01d:

1. **Set up a PIN** (4–6 digits). This locks the app. If you ever forget the PIN, you can still recover your wallets with your seed phrases — the PIN only locks the app, not the money.
2. You can also tap **Skip (Not Recommended)**, but a PIN is strongly advised: it protects your wallet if someone picks up your phone.
3. If your phone has a fingerprint reader, you can unlock with your fingerprint instead of the PIN.

## 2. The dashboard

The dashboard is the first screen. It is a list of tiles — each tile is one tool:

| Tile | What it does |
|---|---|
| **BTC Wallet** | Your Bitcoin wallet: create, receive, send, sign |
| **XMR Wallet** | Your Monero wallet: seed, address, balance |
| **Secure Message** | Encrypted chat with contacts |
| **Email Aliases** | Create disposable email addresses |
| **TX Privacy Check** | Scan a Bitcoin transaction for privacy leaks |
| **Privacy Score** | Check how private your phone is |
| **Tor Routing** | Route your internet traffic through Tor |
| **Companion Apps** | Quick access to other privacy apps |
| **Address Book** | Saved Bitcoin recipients |
| **Settings** | Theme, language, servers, wipe data |
| **About** | Version, license, links |

---

## 3. BTC Wallet

This is your Bitcoin wallet. Everything happens **on your phone** — the seed words never leave the device.

### Create a new wallet
- Tap **BTC Wallet → Create Wallet**.
- v01d generates 12 secret words (your **seed phrase**).
- **Write these 12 words on paper** and store the paper somewhere safe. This is the only way to recover your money if you lose your phone.
- Anyone who knows these 12 words owns your coins. Never photograph them, never type them into a website, never share them.

### Your receiving address
- The wallet screen shows your address (starts with `bc1q...`) as text and as a **QR code**.
- Tap to copy, or let the sender scan the QR.
- **Privacy tip:** generate a fresh address for each person who pays you. Reusing one address lets people connect your payments together.

### Import an existing wallet
- Tap **Import** and enter your existing 12 (or 24) seed words. v01d rebuilds the same wallet.

### Backup options
- **Show seed** — displays the 12 words again so you can check your paper copy. It auto-hides after a short time.
- **Export xpub** — copies your *public* key (starts with `xpub...`). You can give this to watch-only wallet apps or hardware-wallet software so they can *see* your balance but **never spend** it. Sharing xpub is safe for your money, but it does reveal all your addresses — only share it with tools you trust.

---

## 4. Sending Bitcoin

Open **BTC Wallet → Send**. You need internet for this step (it talks to an **Electrum server**, which is a lightweight way to check balances and send transactions without downloading the whole Bitcoin blockchain).

1. **Recipient** — paste their address, or scan their QR code.
2. **Amount** — how much to send, in BTC or sats.
3. **Fee** — the network fee. Higher fee = faster confirmation. v01d asks an Electrum server for a fair estimate; you can adjust it.
4. **Review** — a confirmation screen shows exactly where the coins go, including the **change** output (the remainder that comes back to you). Check it before confirming.
5. **Sign & broadcast** — your keys sign the transaction on the phone, then it is sent through the Electrum server. You get the **TXID** (transaction ID) — copy it to track the payment in any block explorer.

**Coin selection:** if your wallet has many small amounts ("coins"), v01d picks which ones to spend, largest first. Simple and predictable — but be aware that spending many coins at once links them together on the blockchain.

**Privacy tip:** the Electrum server can see *which addresses you ask about* (not your seed, not your keys). For stronger privacy, run v01d's Tor Routing (section 11) so even that is hidden.

**Testnet:** in Settings you can switch to **Testnet** — a play-money version of Bitcoin for trying things safely. Testnet coins are worthless and free to get.

---

## 5. PSBT Signer

A **PSBT** (Partially Signed Bitcoin Transaction) is a file format that lets one device *prepare* a transaction and another device *sign* it. Common with hardware wallets and multisig setups.

How to use:
1. Get a PSBT from another wallet (usually a long string starting with `cHNi...` or a `.psbt` file).
2. Open **PSBT Signer** and paste/import it.
3. v01d shows you the details **before signing**: inputs, outputs, destination addresses, amounts, and the fee.
4. Read carefully — this is your chance to catch a swapped address.
5. Tap **Sign**. v01d signs with your wallet's keys and gives back the signed PSBT to take back to the other wallet for broadcasting.

This all works **offline** — no internet needed.

---

## 6. XMR Wallet (Monero)

Monero is a privacy coin: amounts, sender, and receiver are hidden by default.

What v01d does with Monero:
- **Create / import a seed** — Monero uses a 25-word seed. Same rules as Bitcoin: write it on paper, never share it.
- **Show your address and QR** — starts with `4...` or `8...`. This is safe to share; it's how people pay you.
- **Node status** — you can set your own Monero node address (in the XMR screen). v01d talks to that node for balance and history. Using your own node = nobody else sees your activity.
- **Balance & history** — fetched through your node's wallet interface.
- **Sending** — v01d hands off to the **Monerujo** app (a dedicated Monero wallet) using a deep link, because building a real Monero transaction needs heavy native crypto that v01d deliberately doesn't duplicate. Install Monerujo from the Companion Apps screen or F-Droid.

---

## 7. Secure Message

Encrypted one-to-one chat, protected with the **Signal Protocol** (the same battle-tested cryptography used by Signal). Messages are end-to-end encrypted: even the relay server that passes them along only sees scrambled data.

### One-time setup (each person does this)
1. Open **Secure Message**.
2. **Relay URL** — messages travel through a small relay server. v01d's design is self-hosted: each user (or a friend/group) deploys their own free relay on Cloudflare Workers (instructions are in the `worker/DEPLOY.md` file of the project). Paste your relay's `https://...workers.dev` address. Using *your own* relay means no company ever holds your messages.
3. Your identity key is created automatically on the phone. Your **fingerprint** is shown so you can verify contacts.

### Adding a contact
- Tap **Add Contact**.
- **In person:** one person shows their QR code, the other scans it. This is the safest way — no middleman can tamper with it.
- Or paste their identity string manually.
- Compare fingerprints out loud (or in person) for maximum safety.

### Chatting
- Tap a contact to open the chat. Type and send — messages are encrypted on your phone and decrypted only on your contact's phone.
- New messages are picked up automatically by polling.
- **Files:** tap the attach (📎) button to send a photo or document (up to 256 KB — small files only).

### Good to know
- Messages are stored **on the relay only until delivered** (short TTL), then deleted.
- Chats are saved encrypted on your phone (SQLCipher).
- No accounts, no phone numbers, no email — identity is just a cryptographic key.
- Groups are not supported by design — this is a focused, minimal tool.

---

## 8. Email Aliases

Create email aliases like `mysite47x9z@yourdomain` without any app or service.

1. Open **Email Aliases**.
2. Enter a **site name** (e.g. `amazon`) and your **secret key** (any long private word/phrase you keep the same).
3. v01d computes the alias **offline** using HMAC-SHA256 — the same site always gets the same alias, but nobody can guess it or link it to you.
4. Copy the alias and use it to sign up.

**Why:** if that site gets hacked or spams you, you know exactly who leaked your address, and you can filter it out — your real address was never given to anyone.

Set your default domain (the part after `@`) in **Settings → Email Aliases**.

---

## 9. TX Privacy Check

Paste a **raw Bitcoin transaction** (the long hex string your wallet can export) and v01d analyzes it **offline** for known privacy leaks:

- **Address reuse** — the same address receiving multiple payments.
- **Peeling chains** — the classic pattern of sending most of a amount onward while keeping a little change, which links all the steps.
- **Change detection** — which output is the change coming back to you.
- **Round amounts** — round-number payments are easier to link.
- **Mixed input types** and more.

You get a simple verdict with explanations of *why* something is a leak and what to do instead. Nothing is uploaded — analysis is 100% on the phone.

---

## 10. Privacy Score

A quick health check of your **phone's** privacy posture. v01d checks things like:

- Verified boot (is the OS untampered?)
- SELinux enforcing
- GrapheneOS detected
- File-based encryption
- USB debugging off
- VPN active
- DNS-over-TLS
- and more

You get a score with a green/red breakdown, a history of past scores so you can see progress, and a shareable summary (generated as text — nothing is sent anywhere automatically).

---

## 11. Tor Routing

Tor makes your internet traffic anonymous by bouncing it through volunteer relays. v01d offers two layers:

- **Tor service** — embedded Tor (via the Guardian Project's tor-android). Apps that support SOCKS proxies can use it.
- **Tor VPN** — a full device VPN (`Settings → VPN` on your phone, allow the connection when Android asks). All your apps' TCP traffic and DNS requests then go through Tor, with **no DNS leaks**:
  - Website connections (TCP) are forwarded through Tor in both directions — requests *and* responses.
  - DNS lookups are converted to DNS-over-TCP inside the Tor tunnel.

**Limits to know about:** non-TCP traffic other than DNS (e.g. QUIC/HTTP3, video-call UDP) is blocked while the VPN is active. Sites may fall back to plain TCP automatically; if one misbehaves, toggle the VPN off for it. Tor also slows things down — that's normal.

---

## 12. Companion Apps

v01d doesn't try to do everything — it points you to the best dedicated, open-source privacy apps:

- **Monerujo** — Monero wallet (used for XMR balance & sending)
- **Auditor** — verifies your device hardware (GrapheneOS)
- **InterProfile Sharing** — GrapheneOS profile helper

Tap a companion to open its store page or website. v01d does not install anything silently.

Note: messaging apps like SimpleX or Session are not bundled as companions — v01d has its own
encrypted messaging built in.

---

## 13. Address Book

Save Bitcoin addresses you send to often, with a name. When sending, pick from the book instead of pasting — fewer typos, fewer mistakes. Stored encrypted on the device.

---

## 14. Settings

- **Theme** — follow the system, or force light/dark.
- **Language** — English, Italiano, or the system default.
- **Electrum Server** — which server v01d asks about Bitcoin balances/fees. Default is a public one; advanced users can run their own Electrum instance for full privacy.
- **Network** — Mainnet (real money) / Testnet (play money).
- **Notifications → Incoming TX Alerts** — v01d checks every 30 seconds whether new money arrived at your wallet addresses and notifies you. The check talks to the Electrum server you configured.
- **Danger Zone → Wipe All Data** — see section 16.

Settings are stored **only on this device**.

## 15. App lock

- The PIN (or fingerprint) from first launch locks the whole app.
- v01d also **auto-locks** after being in the background for a while.
- Screenshots and screen recording of the app are blocked (FLAG_SECURE).

## 16. Wiping all data

**Settings → Danger Zone → Wipe All Data.** This permanently deletes, on this phone only:

- both wallets (BTC + XMR) and their seeds
- transaction history, contacts, message history
- privacy score history, all settings, the app lock PIN

Type **WIPE** to confirm — there is no undo. (Your paper seed backups still recover your coins elsewhere — which is exactly why they matter.)

## 17. Safety rules

1. **Seeds on paper, never digital.** No photos, no cloud notes, no email.
2. **Verify contact QR codes in person** when possible.
3. **Fresh address per payment** for Bitcoin privacy.
4. **Testnet first** if you're learning — switch to Mainnet when comfortable.
5. **Read the PSBT details** before signing anything.
6. **Keep v01d updated** and prefer GrapheneOS for the full protection set.

---

*v01d is free software (GPL-3.0). No accounts, no trackers, no analytics — ever.*
