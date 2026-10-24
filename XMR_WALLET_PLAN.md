# XMR Full Wallet Plan

## Approach: Pure Kotlin Monero crypto (no C++ NDK build)

The monero C++ NDK build takes 30-60 min and is fragile. Instead, implement
the wallet crypto in pure Kotlin using BouncyCastle (already available via bitcoinj).

### What pure Kotlin CAN do
- Seed → spend key → view key → address (Keccak-256 + ed25519 + Base58)
- Address display + QR code
- Node connectivity check (monerod JSON-RPC)
- Balance + history via monero-wallet-rpc (user's own node)
- Send via deep link to Monerujo (companion app)

### What still requires C++ (documented, not built)
- Native transaction construction (ring signatures, stealth addresses)
- Local blockchain scanning for balance without wallet-rpc

## Components

### 1. MoneroCrypto.kt (new)
- Keccak-256 via BouncyCastle KeccakDigest
- sc_reduce32 (modular reduction mod ed25519 group order L)
- ed25519 scalar mult base (BigInteger point arithmetic on twisted Edwards curve)
- Monero Base58 encoding (8-byte block encoding, NOT Bitcoin Base58)
- 25-word mnemonic decode → 32-byte seed
- Address generation: base58(network_byte || spend_pub || view_pub || checksum)

### 2. MoneroRpcClient.kt (new)
- JSON-RPC over HTTPS (same pattern as ElectrumClient)
- Daemon RPC: get_height, get_info
- Wallet RPC: get_balance, get_transfers, get_address

### 3. monero_words.txt (new resource)
- 1626-word Monero mnemonic word list (res/raw/)

### 4. XmrWalletScreen.kt (rewrite)
- Address display + QR + copy
- Node connectivity status
- Balance display (via wallet RPC)
- Transaction history
- Send via Monerujo deep link
- Seed backup (show/hide)

### 5. XmrWalletStorage.kt (update)
- Add node URL storage

### 6. Unit tests
- Known Monero test vector: seed → address verification
- Base58 encode/decode roundtrip
- sc_reduce32 correctness

## Files
- New: MoneroCrypto.kt, MoneroRpcClient.kt, res/raw/monero_words.txt
- Update: XmrWalletScreen.kt, XmrWalletStorage.kt
- New test: MoneroCryptoTest.kt
