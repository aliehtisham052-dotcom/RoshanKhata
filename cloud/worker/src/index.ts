/**
 * Roshan Khata's relay ("daakiya", the postman) — Marhala 2 of live sync, 22 Oct 2026.
 *
 * When the phone and the computer are not on the same WiFi, each leaves its
 * parcels here and picks up the other's. A parcel is the same RKL1 round body
 * the WiFi path sends, already sealed on the device with the book's own key
 * (AES-256-GCM): this Worker holds no key and can open nothing. It sees only a
 * random book id, random device ids, sizes and times.
 *
 * One Durable Object per book keeps that book's parcels in its own SQLite, in
 * order. A parcel is forgotten once every device of the book has taken it, or
 * after KEEP_DAYS, whichever is first. It is a postbox, not a backup.
 *
 * Who may use a book: the first device to say hello sets the book's pass (the
 * SHA-256 of a token each device derives from the book key, HMAC-SHA256(key,
 * "rk-relay-v1:" + book)); every later call must carry the same token. A device
 * without the book key cannot make the token.
 *
 * Optional wake-up: a device may leave an FCM token; when a parcel for it
 * arrives, a data-only push says "something is waiting" (no figure, no name).
 * Without the FCM secrets this part is simply skipped.
 */
import { DurableObject } from "cloudflare:workers";

export interface Env {
  BOOKS: DurableObjectNamespace<Daakiya>;
  LIVE: string;
  KEEP_DAYS: string;
  DAY_LIMIT: string;
  /** Other relays' health addresses, called once a day (KEEP_ALIVE in wrangler.toml); comma-separated. */
  KEEP_ALIVE?: string;
  FCM_SERVICE_ACCOUNT?: string;
  FCM_PROJECT_ID?: string;
}

const MAX_PARCEL = 1024 * 1024;
const TAKE_PAGE = 40;
/**
 * A page of parcels stays under this much base64, however many fit (22 Oct
 * 2026, the review: forty parcels of a megabyte came to some 54 MB, held whole
 * by a phone in one answer). The first parcel always goes, so a page is never
 * empty while parcels wait; `more` says to ask again.
 */
const TAKE_BYTES = 8 * 1024 * 1024;
const HEX32 = /^[0-9a-f]{32}$/;
const HEX64 = /^[0-9a-f]{64}$/;
const DEVICE = /^[0-9a-f]{16}$/;
const WAKE_GAP_MS = 30_000;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

function refuse(status: number, why: string): Response {
  return json({ ok: false, why }, status);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/v1/health") return json({ ok: true, live: env.LIVE !== "0" });
    if (env.LIVE === "0") return refuse(503, "halted");
    const m = url.pathname.match(/^\/v1\/b\/([0-9a-f]{32})\/(hello|send|take|ack|wake|leave)$/);
    if (!m) return refuse(404, "no-such-path");
    const [, book, action] = m;
    const token = passOf(request);
    if (!HEX64.test(token)) return refuse(401, "no-pass");
    const device = request.headers.get("x-device") ?? "";
    if (!DEVICE.test(device)) return refuse(400, "no-device");
    const want = action === "take" ? "GET" : "POST";
    if (request.method !== want) return refuse(405, "method");
    const stub = env.BOOKS.get(env.BOOKS.idFromName(book));
    return stub.fetch(request);
  },

  /**
   * Once a day (wrangler.toml's cron): the other relays' health addresses are called, so a free
   * Supabase project is never paused for want of use (it pauses after 7 quiet days) and its idle books go.
   */
  async scheduled(_event: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    const urls = (env.KEEP_ALIVE ?? "").split(",").map((s) => s.trim()).filter((s) => s.startsWith("https://"));
    ctx.waitUntil(Promise.all(urls.map((u) => fetch(u, { headers: { "user-agent": "rk-keepalive" } }).catch(() => null))));
  },
} satisfies ExportedHandler<Env>;

/**
 * The book's token: "Authorization: RK <hex>", or "X-RK-Pass: <hex>" (22 Oct 2026: the owner's PHP
 * hosting may drop Authorization before the script sees it, so the app sends both to every relay).
 */
function passOf(request: Request): string {
  const x = (request.headers.get("x-rk-pass") ?? "").trim();
  if (x) return x;
  const auth = request.headers.get("authorization") ?? "";
  return auth.startsWith("RK ") ? auth.slice(3).trim() : "";
}

async function sha256Hex(text: string): Promise<string> {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Compares two hex strings in time that does not depend on where they differ. */
function sameHex(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function toBase64(bytes: Uint8Array): string {
  let s = "";
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(s);
}

function utcDay(now: number): string {
  return new Date(now).toISOString().slice(0, 10);
}

export class Daakiya extends DurableObject<Env> {
  private sql: SqlStorage;

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec(`CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v TEXT NOT NULL)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS devices (
      device TEXT PRIMARY KEY, acked INTEGER NOT NULL DEFAULT 0, seen INTEGER NOT NULL,
      fcm TEXT, woke INTEGER NOT NULL DEFAULT 0)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS parcels (
      seq INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT NOT NULL, at INTEGER NOT NULL, body BLOB NOT NULL)`);
  }

  private meta(k: string): string | null {
    const r = this.sql.exec<{ v: string }>(`SELECT v FROM meta WHERE k = ?`, k).toArray();
    return r.length ? r[0].v : null;
  }

  private setMeta(k: string, v: string): void {
    this.sql.exec(`INSERT OR REPLACE INTO meta (k, v) VALUES (?, ?)`, k, v);
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const action = url.pathname.split("/").pop()!;
    const token = passOf(request);
    const device = request.headers.get("x-device")!;
    const now = Date.now();

    const pass = await sha256Hex(token);
    const known = this.meta("pass");
    if (known == null) {
      // The book's first device sets the pass — only on hello, never by a stray parcel.
      if (action !== "hello") return refuse(403, "say-hello-first");
      this.setMeta("pass", pass);
      this.setMeta("created", String(now));
    } else if (!sameHex(known, pass)) {
      return refuse(403, "wrong-pass");
    }

    const isKnown = this.sql.exec(`SELECT 1 FROM devices WHERE device = ?`, device).toArray().length > 0;
    if (!isKnown && action !== "hello") return refuse(403, "say-hello-first");

    await this.schedulePrune(now);
    switch (action) {
      case "hello": return this.hello(device, now);
      case "send": return this.send(request, device, now);
      case "take": return this.take(url, device, now);
      case "ack": return this.ack(request, device, now);
      case "wake": return this.wake(request, device, now);
      case "leave": return this.leave(device);
    }
    return refuse(404, "no-such-path");
  }

  private hello(device: string, now: number): Response {
    const devices = this.sql.exec<{ n: number }>(`SELECT COUNT(*) AS n FROM devices`).one().n;
    const isKnown = this.sql.exec(`SELECT 1 FROM devices WHERE device = ?`, device).toArray().length > 0;
    if (!isKnown && devices >= 8) return refuse(409, "too-many-devices");
    // A new device reads all that is here (a message read twice changes nothing; one skipped is a change lost).
    const top = this.sql.exec<{ s: number | null }>(`SELECT MAX(seq) AS s FROM parcels`).one().s ?? 0;
    this.sql.exec(
      `INSERT INTO devices (device, acked, seen) VALUES (?, 0, ?)
       ON CONFLICT(device) DO UPDATE SET seen = excluded.seen`,
      device, now,
    );
    const others = this.sql.exec<{ device: string; seen: number }>(
      `SELECT device, seen FROM devices WHERE device != ?`, device,
    ).toArray();
    return json({ ok: true, top, others });
  }

  private async send(request: Request, device: string, now: number): Promise<Response> {
    const body = new Uint8Array(await request.arrayBuffer());
    if (body.length === 0) return refuse(400, "empty");
    if (body.length > MAX_PARCEL) return refuse(413, "too-big");
    const day = utcDay(now);
    const countKey = "count:" + day;
    const count = Number(this.meta(countKey) ?? "0");
    if (count >= Number(this.env.DAY_LIMIT || "3000")) return refuse(429, "day-limit");
    this.sql.exec(`DELETE FROM meta WHERE k LIKE 'count:%' AND k != ?`, countKey);
    this.setMeta(countKey, String(count + 1));
    const seq = this.sql.exec<{ seq: number }>(
      `INSERT INTO parcels (sender, at, body) VALUES (?, ?, ?) RETURNING seq`, device, now, body,
    ).one().seq;
    this.sql.exec(`UPDATE devices SET seen = ? WHERE device = ?`, now, device);
    this.ctx.waitUntil(this.wakeOthers(device, now));
    return json({ ok: true, seq });
  }

  private take(url: URL, device: string, now: number): Response {
    const after = Math.max(0, Number(url.searchParams.get("after") ?? "0") || 0);
    const rows = this.sql.exec<{ seq: number; sender: string; at: number; body: ArrayBuffer }>(
      `SELECT seq, sender, at, body FROM parcels WHERE seq > ? AND sender != ? ORDER BY seq LIMIT ?`,
      after, device, TAKE_PAGE + 1,
    ).toArray();
    this.sql.exec(`UPDATE devices SET seen = ? WHERE device = ?`, now, device);
    const page: { seq: number; from: string; at: number; body: string }[] = [];
    let bytes = 0;
    for (const r of rows.slice(0, TAKE_PAGE)) {
      const body = toBase64(new Uint8Array(r.body));
      if (page.length > 0 && bytes + body.length > TAKE_BYTES) break;
      page.push({ seq: r.seq, from: r.sender, at: r.at, body });
      bytes += body.length;
    }
    const more = rows.length > page.length;
    // Parcels older than KEEP_DAYS were forgotten: a device asking from before them has missed some (it must pair again).
    const prunedPast = Number(this.meta("pruned_past") ?? "0");
    const gap = after < prunedPast;
    return json({ ok: true, parcels: page, more, gap });
  }

  private async ack(request: Request, device: string, now: number): Promise<Response> {
    const o = (await request.json().catch(() => null)) as { upTo?: number } | null;
    const upTo = Number(o?.upTo ?? NaN);
    if (!Number.isFinite(upTo) || upTo < 0) return refuse(400, "bad-ack");
    this.sql.exec(`UPDATE devices SET acked = MAX(acked, ?), seen = ? WHERE device = ?`, upTo, now, device);
    this.pruneTaken();
    return json({ ok: true });
  }

  private async wake(request: Request, device: string, now: number): Promise<Response> {
    const o = (await request.json().catch(() => null)) as { token?: string | null } | null;
    const t = typeof o?.token === "string" && o.token.length > 20 && o.token.length < 4096 ? o.token : null;
    this.sql.exec(`UPDATE devices SET fcm = ?, seen = ? WHERE device = ?`, t, now, device);
    return json({ ok: true, wake: t != null });
  }

  private leave(device: string): Response {
    this.sql.exec(`DELETE FROM devices WHERE device = ?`, device);
    this.sql.exec(`DELETE FROM parcels WHERE sender = ?`, device);
    const left = this.sql.exec<{ n: number }>(`SELECT COUNT(*) AS n FROM devices`).one().n;
    if (left === 0) this.ctx.storage.deleteAll();
    else this.pruneTaken();
    return json({ ok: true });
  }

  /** Parcels every other device has taken are gone. */
  private pruneTaken(): void {
    const devices = this.sql.exec<{ device: string; acked: number }>(`SELECT device, acked FROM devices`).toArray();
    if (devices.length === 0) return;
    // A parcel is waiting for every device except its sender: it may go once each of them acked past it.
    this.sql.exec(
      `DELETE FROM parcels WHERE NOT EXISTS (
         SELECT 1 FROM devices d WHERE d.device != parcels.sender AND d.acked < parcels.seq)`,
    );
  }

  private async schedulePrune(now: number): Promise<void> {
    if ((await this.ctx.storage.getAlarm()) == null) await this.ctx.storage.setAlarm(now + 6 * 3600_000);
  }

  /** Every six hours: parcels older than KEEP_DAYS go; a book nobody touched for 30 days is emptied. */
  async alarm(): Promise<void> {
    const now = Date.now();
    const keep = Math.max(1, Number(this.env.KEEP_DAYS || "7")) * 86_400_000;
    const cut = this.sql.exec<{ s: number | null }>(`SELECT MAX(seq) AS s FROM parcels WHERE at < ?`, now - keep).one().s;
    if (cut != null) {
      this.sql.exec(`DELETE FROM parcels WHERE seq <= ?`, cut);
      this.setMeta("pruned_past", String(cut));
    }
    const last = this.sql.exec<{ s: number | null }>(`SELECT MAX(seen) AS s FROM devices`).one().s ?? 0;
    if (now - last > 30 * 86_400_000) {
      await this.ctx.storage.deleteAll();
      return;
    }
    await this.ctx.storage.setAlarm(now + 6 * 3600_000);
  }

  private async wakeOthers(sender: string, now: number): Promise<void> {
    if (!this.env.FCM_SERVICE_ACCOUNT || !this.env.FCM_PROJECT_ID) return;
    const rows = this.sql.exec<{ device: string; fcm: string; woke: number }>(
      `SELECT device, fcm, woke FROM devices WHERE device != ? AND fcm IS NOT NULL`, sender,
    ).toArray();
    for (const r of rows) {
      if (now - r.woke < WAKE_GAP_MS) continue;
      this.sql.exec(`UPDATE devices SET woke = ? WHERE device = ?`, now, r.device);
      const gone = await sendWake(this.env, r.fcm).catch(() => false);
      if (gone === "gone") this.sql.exec(`UPDATE devices SET fcm = NULL WHERE device = ?`, r.device);
    }
  }
}

// ---------------------------------------------------------------- FCM (optional)

interface ServiceAccount { client_email: string; private_key: string; token_uri?: string }

let cachedAccess: { token: string; until: number } | null = null;

function b64url(bytes: Uint8Array | string): string {
  const b = typeof bytes === "string" ? new TextEncoder().encode(bytes) : bytes;
  return toBase64(b).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function accessToken(env: Env): Promise<string> {
  const now = Date.now();
  if (cachedAccess && cachedAccess.until > now + 60_000) return cachedAccess.token;
  const sa = JSON.parse(env.FCM_SERVICE_ACCOUNT!) as ServiceAccount;
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const iat = Math.floor(now / 1000);
  const aud = sa.token_uri ?? "https://oauth2.googleapis.com/token";
  const unsigned = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" })) + "." + b64url(JSON.stringify({
    iss: sa.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging", aud, iat, exp: iat + 3600,
  }));
  const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)));
  const res = await fetch(aud, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + unsigned + "." + b64url(sig),
  });
  if (!res.ok) throw new Error("token " + res.status);
  const o = (await res.json()) as { access_token: string; expires_in: number };
  cachedAccess = { token: o.access_token, until: now + o.expires_in * 1000 };
  return o.access_token;
}

/** A data-only push saying a parcel waits; "gone" when the device's token is no longer valid. */
async function sendWake(env: Env, fcmToken: string): Promise<boolean | "gone"> {
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FCM_PROJECT_ID}/messages:send`, {
    method: "POST",
    headers: { authorization: "Bearer " + (await accessToken(env)), "content-type": "application/json" },
    body: JSON.stringify({ message: { token: fcmToken, data: { t: "rk-wake" }, android: { priority: "high", ttl: "600s" } } }),
  });
  // Only a token FCM no longer knows is let go (22 Oct 2026, the review: any 400 dropped it, and the
  // device was never woken again until it registered anew). 404 is UNREGISTERED; a 400 is let go
  // only when FCM says the registration token itself is not valid.
  if (res.status === 404) return "gone";
  if (res.status === 400) {
    const text = await res.text().catch(() => "");
    return /registration token/i.test(text) ? "gone" : false;
  }
  return res.ok;
}
