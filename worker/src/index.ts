/**
 * Void Message relay — Cloudflare Worker.
 *
 * Zero-knowledge blind mailbox. Stores OPAQUE encrypted blobs keyed by a
 * routing token (SHA256 of the recipient's identity pubkey, derived client-side).
 * The Worker never decrypts, never logs payloads, never correlates users.
 *
 * Endpoints:
 *   POST /send   { token, blob }      -> queue a blob for `token`
 *   GET  /poll?token=X                -> fetch + clear all blobs for X
 *   POST /bundle { token, bundle }    -> publish a public key bundle for `token`
 *   GET  /bundle?token=X              -> fetch the bundle for X
 *   GET  /health                      -> liveness
 *
 * Bundles are PUBLIC key material (PQXDH prekeys) — not secret — so storing
 * them on the relay is safe. They let an offline recipient be messaged: a
 * sender fetches the recipient's bundle, builds a session, and sends.
 *
 * Design notes:
 *   - No registration: the token is derived from the pubkey, so anyone with a
 *     contact's pubkey can send to their mailbox; only the owner polls it.
 *   - One-shot pickup: poll returns the queue and deletes it.
 *   - KV expirationTtl gives automatic message expiry (default 7 days).
 *   - ponytail: in-memory per-isolate rate limiter. Good enough for abuse
 *     mitigation; move to a durable counter (KV/R2) if the relay gets busy.
 */

interface Env {
  MAILBOX: KVNamespace;
  MESSAGE_TTL_SECONDS: string; // e.g. "604800" (7 days)
  MAX_MAILBOX_SIZE: string;    // e.g. "200"
  RATE_LIMIT_PER_MINUTE: string; // e.g. "60"
}

interface QueuedMessage {
  blob: string; // base64 opaque ciphertext
  // No timestamp: the fewer persisted metadata, the better. KV's own
  // expirationTtl already bounds retention.
}

// ─── In-memory rate limiter (per isolate, best-effort) ─────────────
const hits = new Map<string, number[]>();

function rateLimited(ip: string, limitPerMin: number): boolean {
  const now = Date.now();
  const window = hits.get(ip)?.filter((t) => now - t < 60_000) ?? [];
  if (window.length >= limitPerMin) {
    hits.set(ip, window);
    return true;
  }
  window.push(now);
  hits.set(ip, window);
  return false;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const ip = request.headers.get("cf-connecting-ip") ?? "unknown";
    const limit = parseInt(env.RATE_LIMIT_PER_MINUTE || "60", 10);

    if (rateLimited(ip, limit)) {
      return json({ error: "rate_limited" }, 429);
    }

    // CORS (so a future web client can use the relay too)
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders() });
    }

    try {
      if (url.pathname === "/health" && request.method === "GET") {
        return json({ status: "ok" });
      }

      if (url.pathname === "/send" && request.method === "POST") {
        return await handleSend(request, env);
      }

      if (url.pathname === "/poll" && request.method === "GET") {
        return await handlePoll(url, env);
      }

      if (url.pathname === "/bundle" && request.method === "POST") {
        return await handleBundlePut(request, env);
      }

      if (url.pathname === "/bundle" && request.method === "GET") {
        return await handleBundleGet(url, env);
      }

      return json({ error: "not_found" }, 404);
    } catch (e) {
      return json({ error: "internal" }, 500);
    }
  },
};

async function handleSend(request: Request, env: Env): Promise<Response> {
  const ttl = parseInt(env.MESSAGE_TTL_SECONDS || "604800", 10);
  const maxSize = parseInt(env.MAX_MAILBOX_SIZE || "200", 10);

  let body: { token?: string; blob?: string };
  try {
    body = await request.json();
  } catch {
    return json({ error: "bad_json" }, 400);
  }

  const token = body.token;
  const blob = body.blob;
  if (!token || !blob || typeof blob !== "string") {
    return json({ error: "missing_fields" }, 400);
  }
  // Sanity caps: token is a 64-char hex sha256; blob bounded.
  if (token.length !== 64 || blob.length > 512 * 1024) {
    return json({ error: "invalid_payload" }, 400);
  }

  const key = `mb:${token}`;
  const existing = (await env.MAILBOX.get(key, "json")) as QueuedMessage[] | null;
  const queue = existing ?? [];
  if (queue.length >= maxSize) {
    return json({ error: "mailbox_full" }, 429);
  }

  queue.push({ blob });
  await env.MAILBOX.put(key, JSON.stringify(queue), { expirationTtl: ttl });

  return json({ queued: true, count: queue.length });
}

async function handlePoll(url: URL, env: Env): Promise<Response> {
  const token = url.searchParams.get("token");
  if (!token || token.length !== 64) {
    return json({ error: "missing_token" }, 400);
  }

  const key = `mb:${token}`;
  const queue = (await env.MAILBOX.get(key, "json")) as QueuedMessage[] | null;

  // One-shot pickup: clear the mailbox after reading.
  if (queue && queue.length > 0) {
    await env.MAILBOX.delete(key);
    return json({ messages: queue.map((m) => m.blob) });
  }

  return json({ messages: [] });
}

// Publish a public key bundle (PQXDH prekeys) for a token.
async function handleBundlePut(request: Request, env: Env): Promise<Response> {
  const ttl = parseInt(env.MESSAGE_TTL_SECONDS || "604800", 10);
  let body: { token?: string; bundle?: string };
  try {
    body = await request.json();
  } catch {
    return json({ error: "bad_json" }, 400);
  }
  const token = body.token;
  const bundle = body.bundle;
  if (!token || token.length !== 64 || !bundle || typeof bundle !== "string") {
    return json({ error: "missing_fields" }, 400);
  }
  if (bundle.length > 16 * 1024) {
    return json({ error: "invalid_payload" }, 400);
  }
  await env.MAILBOX.put(`bundle:${token}`, bundle, { expirationTtl: ttl });
  return json({ published: true });
}

// Fetch the public key bundle for a token.
async function handleBundleGet(url: URL, env: Env): Promise<Response> {
  const token = url.searchParams.get("token");
  if (!token || token.length !== 64) {
    return json({ error: "missing_token" }, 400);
  }
  const bundle = await env.MAILBOX.get(`bundle:${token}`);
  if (!bundle) {
    return json({ error: "no_bundle" }, 404);
  }
  return json({ bundle });
}

function corsHeaders(): Record<string, string> {
  return {
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET, POST, OPTIONS",
    "access-control-allow-headers": "content-type",
  };
}
