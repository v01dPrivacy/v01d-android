# Void — TX Notifications Plan

## Goal
Notify the user when new incoming BTC transactions arrive at their wallet addresses.

## Architecture

Since our ElectrumClient is request-response (not persistent WebSocket), we use a **polling foreground service**:

1. `TxNotificationService` — foreground service that polls Electrum every 30s
2. Compares current UTXOs with last-known state (stored in SharedPreferences)
3. New UTXO detected → Android notification with amount + txid
4. Toggle in Settings to enable/disable
5. Notification channel: "Incoming Transactions"

## Files

- `tools/wallet/btc/TxNotificationService.kt` — the service
- Update `SettingsScreen.kt` — enable/disable toggle
- Update `AndroidManifest.xml` — register service
- Update `proguard-rules.pro` — keep service class

## Flow

```
Service starts → poll Electrum every 30s
  → fetch UTXOs for all wallet addresses
  → compare with stored UTXO set
  → new UTXO found?
    → show notification: "Received X BTC"
    → update stored UTXO set
  → no change? → wait 30s → repeat
```
