# Void Message Relay — Deployment

The relay is **already deployed and live**:

```
https://v01d-relay.bot-pawing596.workers.dev
```

This URL is also the app's default (see `MessageStorage.DEFAULT_RELAY_URL`), so
the app works out of the box.

## What it is
A Cloudflare Worker + KV acting as a zero-knowledge blind mailbox:
- Stores opaque encrypted blobs keyed by routing token
- Never decrypts, never logs, never correlates
- One-shot pickup (poll clears the mailbox)
- Messages expire after 7 days (KV TTL)

## Endpoints
| Method | Path | Purpose |
|---|---|---|
| POST | `/send` `{token, blob}` | Queue a blob for a recipient token |
| GET | `/poll?token=X` | Fetch + clear all blobs for X |
| POST | `/bundle` `{token, bundle}` | Publish a public key bundle |
| GET | `/bundle?token=X` | Fetch someone's bundle |
| GET | `/health` | Liveness |

## Redeploy after changes
```bash
cd worker
npm install
npx wrangler deploy
```
Requires `npx wrangler login` (already authenticated on this machine).

## KV namespace
Binding `MAILBOX` → id `b54370c49cea41dc8818fecbf9f186ca` (set in wrangler.toml).
Recreate with `npx wrangler kv namespace create MAILBOX` if needed.

## Verify a deployment
```bash
R=https://v01d-relay.bot-pawing596.workers.dev
curl -s $R/health                                   # {"status":"ok"}
curl -s -X POST $R/send -H 'content-type: application/json' \
  -d "{\"token\":\"$(printf 'a%.0s' {1..64})\",\"blob\":\"dGVzdA==\"}"
curl -s "$R/poll?token=$(printf 'a%.0s' {1..64})"   # returns the blob once
```

## Caveats
- KV is eventually consistent: two rapid sends to the same mailbox can race.
  Acceptable for v1; move to Durable Objects if it becomes a problem.
- Rate limit: 100 req/min per IP (in-memory, per isolate).
