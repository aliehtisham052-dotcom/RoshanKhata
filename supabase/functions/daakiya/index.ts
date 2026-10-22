/**
 * Roshan Khata's relay on Supabase ("daakiya", the postman), 22 Oct 2026.
 *
 * A second relay beside Cloudflare's (cloud/worker) and the owner's hosting
 * (web/relay): the SAME conversation, path for path and answer for answer, so
 * the app speaks to all three with one piece of code (LiveCloud). When one is
 * down, a device leaves its parcels at the next.
 *
 * What it keeps: parcels already sealed on the device with the book's own key
 * (AES-256-GCM). It holds no key and can open nothing; it sees a random book
 * id, random device ids, sizes and times. Tables live in their own schema,
 * rk_relay, which Supabase's public REST API does not serve, with row level
 * security on and no policy: only this function (the database's own URL,
 * given to every function by Supabase) reaches them.
 *
 * Who may use a book: the first hello sets the book's pass (SHA-256 of the
 * token each device makes from the book key); every later call must bring the
 * same. Parcels go once every other device took them, or after KEEP_DAYS; a
 * book nobody touched for 30 days goes (at the daily health call, which also
 * keeps a free project from being paused for want of use).
 *
 * Paths: GET v1/health · POST v1/b/<book>/hello|send|ack|wake|leave ·
 * GET v1/b/<book>/take?after=<seq>. Token: "Authorization: RK <hex>" or
 * "X-RK-Pass: <hex>". Deployed with JWT checking off (supabase/config.toml):
 * the book's pass is the check.
 */
import postgres from "npm:postgres@3.4.5";

const MAX_PARCEL = 1024 * 1024;
const TAKE_PAGE = 40;
const TAKE_BYTES = 8 * 1024 * 1024;
const KEEP_DAYS = Number(Deno.env.get("RK_KEEP_DAYS") ?? "7");
const DAY_LIMIT = Number(Deno.env.get("RK_DAY_LIMIT") ?? "3000");
const MAX_DEVICES = 8;
const IDLE_DAYS = 30;
const HEX32 = /^[0-9a-f]{32}$/;
const HEX64 = /^[0-9a-f]{64}$/;
const DEVICE = /^[0-9a-f]{16}$/;

const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false, max: 3, idle_timeout: 20 });

let ready: Promise<void> | null = null;
function setup(): Promise<void> {
  ready ??= (async () => {
    await sql.unsafe(`
      CREATE SCHEMA IF NOT EXISTS rk_relay;
      CREATE TABLE IF NOT EXISTS rk_relay.books (
        book TEXT PRIMARY KEY, pass TEXT NOT NULL, created BIGINT NOT NULL, seen BIGINT NOT NULL,
        pruned_past BIGINT NOT NULL DEFAULT 0, day TEXT NOT NULL DEFAULT '', day_count INTEGER NOT NULL DEFAULT 0);
      CREATE TABLE IF NOT EXISTS rk_relay.devices (
        book TEXT NOT NULL REFERENCES rk_relay.books(book) ON DELETE CASCADE, device TEXT NOT NULL,
        acked BIGINT NOT NULL DEFAULT 0, seen BIGINT NOT NULL, fcm TEXT, woke BIGINT NOT NULL DEFAULT 0,
        PRIMARY KEY (book, device));
      CREATE TABLE IF NOT EXISTS rk_relay.parcels (
        seq BIGSERIAL PRIMARY KEY, book TEXT NOT NULL REFERENCES rk_relay.books(book) ON DELETE CASCADE,
        sender TEXT NOT NULL, at BIGINT NOT NULL, body BYTEA NOT NULL);
      CREATE INDEX IF NOT EXISTS parcels_book_seq ON rk_relay.parcels (book, seq);
      ALTER TABLE rk_relay.books ENABLE ROW LEVEL SECURITY;
      ALTER TABLE rk_relay.devices ENABLE ROW LEVEL SECURITY;
      ALTER TABLE rk_relay.parcels ENABLE ROW LEVEL SECURITY;
      REVOKE ALL ON SCHEMA rk_relay FROM PUBLIC;
    `);
  })().catch((e) => { ready = null; throw e; });
  return ready;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}
function refuse(status: number, why: string): Response {
  return json({ ok: false, why }, status);
}

async function sha256Hex(text: string): Promise<string> {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
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

/** Thrown inside a transaction to roll it back and answer with this. */
class Answer extends Error {
  constructor(public res: Response) { super("answer"); }
}

Deno.serve(async (request: Request): Promise<Response> => {
  try {
    return await handle(request);
  } catch (e) {
    if (e instanceof Answer) return e.res;
    console.error("rk-relay", e instanceof Error ? e.message : String(e));
    return refuse(500, "server");
  }
});

async function handle(request: Request): Promise<Response> {
  const url = new URL(request.url);
  const live = Deno.env.get("RK_LIVE") !== "0";
  await setup();
  if (/\/v1\/health$/.test(url.pathname)) {
    // The daily call (Cloudflare's cron): a real query, so the project is never "inactive", and idle books go.
    const now = Date.now();
    await sql`DELETE FROM rk_relay.books WHERE seen < ${now - IDLE_DAYS * 86_400_000}`;
    return json({ ok: true, live });
  }
  if (!live) return refuse(503, "halted");
  const m = url.pathname.match(/\/v1\/b\/([0-9a-f]{32})\/(hello|send|take|ack|wake|leave)$/);
  if (!m) return refuse(404, "no-such-path");
  const [, book, action] = m;
  let token = (request.headers.get("x-rk-pass") ?? "").trim();
  if (!token) {
    const auth = request.headers.get("authorization") ?? "";
    token = auth.startsWith("RK ") ? auth.slice(3).trim() : "";
  }
  if (!HEX64.test(token)) return refuse(401, "no-pass");
  const device = request.headers.get("x-device") ?? "";
  if (!DEVICE.test(device)) return refuse(400, "no-device");
  const want = action === "take" ? "GET" : "POST";
  if (request.method !== want) return refuse(405, "method");
  if (!HEX32.test(book)) return refuse(404, "no-such-path");

  // A parcel is read before the transaction: a slow upload never holds the book's row.
  let body: Uint8Array | null = null;
  let o: Record<string, unknown> | null = null;
  if (action === "send") {
    const len = Number(request.headers.get("content-length") ?? "-1");
    if (len > MAX_PARCEL) return refuse(413, "too-big");
    body = new Uint8Array(await request.arrayBuffer());
    if (body.length === 0) return refuse(400, "empty");
    if (body.length > MAX_PARCEL) return refuse(413, "too-big");
  } else if (action === "ack" || action === "wake") {
    o = (await request.json().catch(() => null)) as Record<string, unknown> | null;
  }

  const pass = await sha256Hex(token);
  const now = Date.now();
  return await sql.begin(async (tx) => {
    const rows = await tx`SELECT pass, pruned_past, day, day_count FROM rk_relay.books WHERE book = ${book} FOR UPDATE`;
    type Book = { pass: string; pruned_past: string; day: string; day_count: number };
    let b = rows[0] as unknown as Book | undefined;
    if (!b) {
      // The book's first device sets the pass, and only by a hello (never by a stray parcel).
      if (action !== "hello") throw new Answer(refuse(403, "say-hello-first"));
      await tx`INSERT INTO rk_relay.books (book, pass, created, seen) VALUES (${book}, ${pass}, ${now}, ${now})
               ON CONFLICT (book) DO NOTHING`;
      const again = await tx`SELECT pass, pruned_past, day, day_count FROM rk_relay.books WHERE book = ${book} FOR UPDATE`;
      b = again[0] as unknown as Book | undefined;
    }
    if (!b || !sameHex(b.pass, pass)) throw new Answer(refuse(403, "wrong-pass"));
    const known = (await tx`SELECT 1 FROM rk_relay.devices WHERE book = ${book} AND device = ${device}`).length > 0;
    if (!known && action !== "hello") throw new Answer(refuse(403, "say-hello-first"));

    // Parcels older than KEEP_DAYS go, and the line past which they went is kept (the take answer's "gap").
    const cut = (await tx`SELECT MAX(seq) AS s FROM rk_relay.parcels WHERE book = ${book} AND at < ${now - KEEP_DAYS * 86_400_000}`)[0].s;
    let prunedPast = Number(b.pruned_past);
    if (cut != null) {
      await tx`DELETE FROM rk_relay.parcels WHERE book = ${book} AND seq <= ${cut}`;
      prunedPast = Math.max(prunedPast, Number(cut));
    }
    await tx`UPDATE rk_relay.books SET seen = ${now}, pruned_past = ${prunedPast} WHERE book = ${book}`;

    switch (action) {
      case "hello": {
        const n = Number((await tx`SELECT COUNT(*) AS n FROM rk_relay.devices WHERE book = ${book}`)[0].n);
        if (!known && n >= MAX_DEVICES) throw new Answer(refuse(409, "too-many-devices"));
        const top = Number((await tx`SELECT COALESCE(MAX(seq), 0) AS s FROM rk_relay.parcels WHERE book = ${book}`)[0].s);
        await tx`INSERT INTO rk_relay.devices (book, device, acked, seen) VALUES (${book}, ${device}, 0, ${now})
                 ON CONFLICT (book, device) DO UPDATE SET seen = EXCLUDED.seen`;
        const others = await tx`SELECT device, seen FROM rk_relay.devices WHERE book = ${book} AND device != ${device}`;
        return json({ ok: true, top, others: others.map((r) => ({ device: r.device, seen: Number(r.seen) })) });
      }
      case "send": {
        const day = new Date(now).toISOString().slice(0, 10);
        const count = b.day === day ? Number(b.day_count) : 0;
        if (count >= DAY_LIMIT) throw new Answer(refuse(429, "day-limit"));
        await tx`UPDATE rk_relay.books SET day = ${day}, day_count = ${count + 1} WHERE book = ${book}`;
        const seq = Number((await tx`INSERT INTO rk_relay.parcels (book, sender, at, body)
                                     VALUES (${book}, ${device}, ${now}, ${body!}) RETURNING seq`)[0].seq);
        await tx`UPDATE rk_relay.devices SET seen = ${now} WHERE book = ${book} AND device = ${device}`;
        return json({ ok: true, seq });
      }
      case "take": {
        const after = Math.max(0, Number(url.searchParams.get("after") ?? "0") || 0);
        const list = await tx`SELECT seq, sender, at, body FROM rk_relay.parcels
                              WHERE book = ${book} AND seq > ${after} AND sender != ${device}
                              ORDER BY seq LIMIT ${TAKE_PAGE + 1}`;
        await tx`UPDATE rk_relay.devices SET seen = ${now} WHERE book = ${book} AND device = ${device}`;
        const page: { seq: number; from: string; at: number; body: string }[] = [];
        let bytes = 0;
        for (const r of list.slice(0, TAKE_PAGE)) {
          const b64 = toBase64(new Uint8Array(r.body as Uint8Array));
          if (page.length > 0 && bytes + b64.length > TAKE_BYTES) break;
          page.push({ seq: Number(r.seq), from: r.sender as string, at: Number(r.at), body: b64 });
          bytes += b64.length;
        }
        return json({ ok: true, parcels: page, more: list.length > page.length, gap: after < prunedPast });
      }
      case "ack": {
        const upTo = Number(o?.upTo ?? NaN);
        if (!Number.isFinite(upTo) || upTo < 0) throw new Answer(refuse(400, "bad-ack"));
        await tx`UPDATE rk_relay.devices SET acked = GREATEST(acked, ${Math.floor(upTo)}), seen = ${now}
                 WHERE book = ${book} AND device = ${device}`;
        await pruneTaken(tx, book);
        return json({ ok: true });
      }
      case "wake": {
        const t = typeof o?.token === "string" && o.token.length > 20 && o.token.length < 4096 ? o.token : null;
        await tx`UPDATE rk_relay.devices SET fcm = ${t}, seen = ${now} WHERE book = ${book} AND device = ${device}`;
        return json({ ok: true, wake: t != null });
      }
      case "leave": {
        await tx`DELETE FROM rk_relay.devices WHERE book = ${book} AND device = ${device}`;
        await tx`DELETE FROM rk_relay.parcels WHERE book = ${book} AND sender = ${device}`;
        const left = Number((await tx`SELECT COUNT(*) AS n FROM rk_relay.devices WHERE book = ${book}`)[0].n);
        if (left === 0) await tx`DELETE FROM rk_relay.books WHERE book = ${book}`;
        else await pruneTaken(tx, book);
        return json({ ok: true });
      }
    }
    throw new Answer(refuse(404, "no-such-path"));
  });
}

/** Parcels every other device of the book has taken are gone (as cloud/worker's pruneTaken). */
// deno-lint-ignore no-explicit-any
async function pruneTaken(tx: any, book: string): Promise<void> {
  await tx`DELETE FROM rk_relay.parcels p WHERE p.book = ${book} AND NOT EXISTS (
             SELECT 1 FROM rk_relay.devices d WHERE d.book = ${book} AND d.device != p.sender AND d.acked < p.seq)`;
}
