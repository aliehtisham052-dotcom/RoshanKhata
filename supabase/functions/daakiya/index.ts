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
/**
 * Bytes a book may hold and leave in a UTC day (23 Oct 2026, the review: counts alone let one book store
 * 3,000 MB a day; the free database is 500 MB for every shop). A device not seen for DEVICE_IDLE_MS goes.
 */
const BOOK_BYTES = Number(Deno.env.get("RK_BOOK_BYTES") ?? String(16 * 1024 * 1024));
const DAY_BYTES = Number(Deno.env.get("RK_DAY_BYTES") ?? String(32 * 1024 * 1024));
const DEVICE_IDLE_MS = Number(Deno.env.get("RK_DEVICE_IDLE_MS") ?? String(30 * 86_400_000));
/**
 * Parcel bytes all books together may hold (23 Oct 2026, phase 2 of the review report): the free database is
 * 500 MB for everything in the project, and BOOK_BYTES alone let anyone opening books fill 300 x 16 MiB a day.
 * Each book's own figure is kept in its row (books.bytes, put right after every parcel in or out), so the store's
 * total is a sum over at most MAX_BOOKS rows, never a scan of the parcels. Past it: 507 "full", as the hosting.
 */
const STORE_BYTES = Number(Deno.env.get("RK_STORE_BYTES") ?? String(256 * 1024 * 1024));
/** A new book is taken only while the store has at least this much room left for it (as the Worker's BOOK_FLOOR). */
const NEW_BOOK_ROOM = 64 * 1024;
/** Refusals of a parcel after which a round still answers the others' page (and keeps its ack). */
const CAPACITY = new Set(["day-limit", "day-bytes", "book-full", "full"]);
/** A round's JSON: a parcel's base64 (4/3 of MAX_PARCEL) and its few fields. */
const MAX_ROUND = MAX_PARCEL * 2;
const MAX_SMALL = 64 * 1024;
/** Books this relay holds at most, and new ones it takes in a UTC day (anyone may open a book: a limit before the plan's). */
const MAX_BOOKS = Number(Deno.env.get("RK_MAX_BOOKS") ?? "5000");
const NEW_BOOKS_DAY = Number(Deno.env.get("RK_NEW_BOOKS_DAY") ?? "300");
/** A quiet call writes "seen" at most this often. */
const SEEN_EVERY_MS = 3_600_000;
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
      ALTER TABLE rk_relay.books ADD COLUMN IF NOT EXISTS day_bytes BIGINT NOT NULL DEFAULT 0;
      ALTER TABLE rk_relay.books ENABLE ROW LEVEL SECURITY;
      ALTER TABLE rk_relay.devices ENABLE ROW LEVEL SECURITY;
      ALTER TABLE rk_relay.parcels ENABLE ROW LEVEL SECURITY;
      REVOKE ALL ON SCHEMA rk_relay FROM PUBLIC;
    `);
    // Each book's parcel bytes (phase 2, 23 Oct 2026): a store from before gets the column once, counted from its
    // parcels then; after that it is kept as parcels come and go (bookBytes).
    const has = await sql`SELECT 1 FROM information_schema.columns
                          WHERE table_schema = 'rk_relay' AND table_name = 'books' AND column_name = 'bytes'`;
    if (has.length === 0) {
      await sql.unsafe(`
        ALTER TABLE rk_relay.books ADD COLUMN IF NOT EXISTS bytes BIGINT NOT NULL DEFAULT 0;
        UPDATE rk_relay.books b SET bytes = COALESCE((SELECT SUM(octet_length(p.body)) FROM rk_relay.parcels p WHERE p.book = b.book), 0);
      `);
    }
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

/**
 * The request's body, at most [cap] bytes; null when it was longer. Past the cap it is still read to its end
 * but nothing more is kept (23 Oct 2026, the review: the whole body was held in memory before any check, and
 * Supabase's gateway holds an answer given before the body was read until the upload ends).
 */
async function readCapped(request: Request, cap: number): Promise<Uint8Array | null> {
  const declared = Number(request.headers.get("content-length") ?? "NaN");
  const reader = request.body?.getReader();
  if (!reader) return new Uint8Array(0);
  const chunks: Uint8Array[] = [];
  let n = 0;
  let over = Number.isFinite(declared) && declared > cap;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    n += value.length;
    if (n > cap) over = true;
    if (!over) chunks.push(value);
  }
  if (over) return null;
  const out = new Uint8Array(n);
  let at = 0;
  for (const c of chunks) { out.set(c, at); at += c.length; }
  return out;
}

function parseJson(bytes: Uint8Array | null): Record<string, unknown> | null {
  if (!bytes) return null;
  try {
    const o = JSON.parse(new TextDecoder().decode(bytes));
    return o && typeof o === "object" ? o as Record<string, unknown> : null;
  } catch {
    return null;
  }
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
    // The sweep runs only with the keep-alive pass (RK_KEEP_SECRET), so the open health is a plain answer.
    const keep = Deno.env.get("RK_KEEP_SECRET");
    if (keep && request.headers.get("x-rk-keep") === keep) {
      const now = Date.now();
      await sql`DELETE FROM rk_relay.books WHERE seen < ${now - IDLE_DAYS * 86_400_000}`;
      // A device nobody saw for DEVICE_IDLE_MS goes with its parcels, in books still in use too.
      await sql`DELETE FROM rk_relay.parcels p USING rk_relay.devices d
                WHERE p.book = d.book AND p.sender = d.device AND d.seen < ${now - DEVICE_IDLE_MS}`;
      await sql`DELETE FROM rk_relay.devices WHERE seen < ${now - DEVICE_IDLE_MS}`;
      // Every book's byte figure put right from its parcels (the sweep above took some; a figure kept may drift).
      await sql`UPDATE rk_relay.books b SET bytes = s.n
                FROM (SELECT b2.book, COALESCE((SELECT SUM(octet_length(p.body)) FROM rk_relay.parcels p WHERE p.book = b2.book), 0) AS n
                      FROM rk_relay.books b2) s
                WHERE b.book = s.book AND b.bytes <> s.n`;
    }
    return json({ ok: true, live });
  }
  if (!live) return refuse(503, "halted");
  const m = url.pathname.match(/\/v1\/b\/([0-9a-f]{32})\/(hello|send|take|ack|wake|leave|round)$/);
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
    // Read to its end before answering, even when too big: Supabase's gateway holds an answer given before the
    // body was read until the upload ends, and the caller timed out (22 Oct 2026, the deployed check).
    body = await readCapped(request, MAX_PARCEL);
    if (body == null) return refuse(413, "too-big");
    if (body.length === 0) return refuse(400, "empty");
  } else if (action === "ack" || action === "wake" || action === "round") {
    const raw = await readCapped(request, action === "round" ? MAX_ROUND : MAX_SMALL);
    if (raw == null) return refuse(413, "too-big");
    o = parseJson(raw);
    if (action === "round") {
      if (!o) return refuse(400, "bad-round");
      if (typeof o.parcel === "string" && o.parcel.length > 0) {
        try { body = Uint8Array.from(atob(o.parcel), (c) => c.charCodeAt(0)); } catch { return refuse(400, "bad-parcel"); }
        if (body.length > MAX_PARCEL) return refuse(413, "too-big");
      }
    }
  }

  const pass = await sha256Hex(token);
  const now = Date.now();
  return await sql.begin(async (tx) => {
    const rows = await tx`SELECT pass, pruned_past, day, day_count, day_bytes FROM rk_relay.books WHERE book = ${book} FOR UPDATE`;
    type Book = { pass: string; pruned_past: string; day: string; day_count: number; day_bytes: string };
    let b = rows[0] as unknown as Book | undefined;
    if (!b) {
      // The book's first device sets the pass, and only by a hello (never by a stray parcel).
      if (action !== "hello") throw new Answer(refuse(403, "say-hello-first"));
      const counts = (await tx`SELECT COUNT(*) AS n, COUNT(*) FILTER (WHERE created > ${now - 86_400_000}) AS d,
                                      COALESCE(SUM(bytes), 0) AS bytes FROM rk_relay.books`)[0];
      if (Number(counts.n) >= MAX_BOOKS) throw new Answer(refuse(507, "full"));
      if (Number(counts.bytes) + NEW_BOOK_ROOM > STORE_BYTES) throw new Answer(refuse(507, "full"));
      if (Number(counts.d) >= NEW_BOOKS_DAY) throw new Answer(refuse(429, "new-books"));
      await tx`INSERT INTO rk_relay.books (book, pass, created, seen) VALUES (${book}, ${pass}, ${now}, ${now})
               ON CONFLICT (book) DO NOTHING`;
      const again = await tx`SELECT pass, pruned_past, day, day_count, day_bytes FROM rk_relay.books WHERE book = ${book} FOR UPDATE`;
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
      await bookBytes(tx, book);
    }
    // Another device not seen for DEVICE_IDLE_MS goes with its parcels (23 Oct 2026, the review: a lost phone kept
    // every parcel of the book for the full week, and its place among the eight for ever).
    const idle = await tx`DELETE FROM rk_relay.devices
                          WHERE book = ${book} AND seen < ${now - DEVICE_IDLE_MS} AND device != ${device} RETURNING device`;
    for (const r of idle) await tx`DELETE FROM rk_relay.parcels WHERE book = ${book} AND sender = ${r.device as string}`;
    if (idle.length > 0) { await pruneTaken(tx, book); await bookBytes(tx, book); }
    // The book's "seen" (its idle sweep) is written when something happened, else at most once an hour:
    // a quiet round costs no row.
    if (cut != null) await tx`UPDATE rk_relay.books SET seen = ${now}, pruned_past = ${prunedPast} WHERE book = ${book}`;
    else if (action === "hello" || action === "send" || action === "leave" || (action === "round" && body))
      await tx`UPDATE rk_relay.books SET seen = ${now} WHERE book = ${book}`;
    else await tx`UPDATE rk_relay.books SET seen = ${now} WHERE book = ${book} AND seen < ${now - SEEN_EVERY_MS}`;

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
        const r = await put(tx, book, device, now, body!, b);
        if (Array.isArray(r)) throw new Answer(refuse(r[0], r[1]));
        return json({ ok: true, seq: r });
      }
      case "take": {
        const after = Math.max(0, Number(url.searchParams.get("after") ?? "0") || 0);
        return json({ ok: true, ...(await takePage(tx, book, device, now, after, prunedPast)) });
      }
      case "ack": {
        const upTo = Number(o?.upTo ?? NaN);
        if (!Number.isFinite(upTo) || upTo < 0) throw new Answer(refuse(400, "bad-ack"));
        await tx`UPDATE rk_relay.devices SET acked = GREATEST(acked, ${Math.floor(upTo)}) WHERE book = ${book} AND device = ${device}`;
        await pruneTaken(tx, book);
        return json({ ok: true });
      }
      case "round": {
        // One request for a whole round (22 Oct 2026): the acknowledgement, this device's parcel, the others' page.
        const after = Math.max(0, Number(o?.after ?? 0) || 0);
        if (o?.upTo != null) {
          const upTo = Number(o.upTo);
          if (!Number.isFinite(upTo) || upTo < 0) throw new Answer(refuse(400, "bad-ack"));
          await tx`UPDATE rk_relay.devices SET acked = GREATEST(acked, ${Math.floor(upTo)}) WHERE book = ${book} AND device = ${device}`;
          await pruneTaken(tx, book);
        }
        const r = body ? await put(tx, book, device, now, body, b) : null;
        if (Array.isArray(r)) {
          if (!CAPACITY.has(r[1])) throw new Answer(refuse(r[0], r[1]));
          // The book's limit refuses the parcel, not the round (23 Oct 2026, the review): the ack above is kept
          // (committed) and the others' page still comes, under the refusal's status.
          return json({ ok: false, why: r[1], seq: null, ...(await takePage(tx, book, device, now, after, prunedPast)) }, r[0]);
        }
        return json({ ok: true, seq: r, ...(await takePage(tx, book, device, now, after, prunedPast)) });
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
        else {
          await pruneTaken(tx, book);
          await bookBytes(tx, book);
        }
        return json({ ok: true });
      }
    }
    throw new Answer(refuse(404, "no-such-path"));
  });
}

/** One parcel in: its number, or the refusal [status, why]. */
// deno-lint-ignore no-explicit-any
async function put(tx: any, book: string, device: string, now: number, body: Uint8Array, b: { day: string; day_count: number; day_bytes: string }): Promise<number | [number, string]> {
  if (body.length === 0) return [400, "empty"];
  const stored = Number((await tx`SELECT COALESCE(SUM(octet_length(body)), 0) AS n FROM rk_relay.parcels WHERE book = ${book}`)[0].n);
  // The whole store first, as the hosting does: the other books' figures as kept, this book's counted afresh.
  const others = Number((await tx`SELECT COALESCE(SUM(bytes), 0) AS n FROM rk_relay.books WHERE book != ${book}`)[0].n);
  if (others + stored + body.length > STORE_BYTES) return [507, "full"];
  const day = new Date(now).toISOString().slice(0, 10);
  const count = b.day === day ? Number(b.day_count) : 0;
  const dayBytes = b.day === day ? Number(b.day_bytes ?? 0) : 0;
  if (count >= DAY_LIMIT) return [429, "day-limit"];
  if (dayBytes + body.length > DAY_BYTES) return [429, "day-bytes"];
  if (stored + body.length > BOOK_BYTES) return [507, "book-full"];
  await tx`UPDATE rk_relay.books SET day = ${day}, day_count = ${count + 1}, day_bytes = ${dayBytes + body.length},
           bytes = ${stored + body.length} WHERE book = ${book}`;
  b.day = day; b.day_count = count + 1; b.day_bytes = String(dayBytes + body.length);
  const seq = Number((await tx`INSERT INTO rk_relay.parcels (book, sender, at, body)
                               VALUES (${book}, ${device}, ${now}, ${body}) RETURNING seq`)[0].seq);
  await tx`UPDATE rk_relay.devices SET seen = ${now} WHERE book = ${book} AND device = ${device}`;
  return seq;
}

/** The others' parcels after [after]; a quiet page writes "seen" at most once an hour. */
// deno-lint-ignore no-explicit-any
async function takePage(tx: any, book: string, device: string, now: number, after: number, prunedPast: number) {
  const list = await tx`SELECT seq, sender, at, body FROM rk_relay.parcels
                        WHERE book = ${book} AND seq > ${after} AND sender != ${device}
                        ORDER BY seq LIMIT ${TAKE_PAGE + 1}`;
  if (list.length > 0) await tx`UPDATE rk_relay.devices SET seen = ${now} WHERE book = ${book} AND device = ${device}`;
  else await tx`UPDATE rk_relay.devices SET seen = ${now} WHERE book = ${book} AND device = ${device} AND seen < ${now - SEEN_EVERY_MS}`;
  const page: { seq: number; from: string; at: number; body: string }[] = [];
  let bytes = 0;
  for (const r of list.slice(0, TAKE_PAGE)) {
    const b64 = toBase64(new Uint8Array(r.body as Uint8Array));
    if (page.length > 0 && bytes + b64.length > TAKE_BYTES) break;
    page.push({ seq: Number(r.seq), from: r.sender as string, at: Number(r.at), body: b64 });
    bytes += b64.length;
  }
  const more = list.length > page.length;
  if (more) return { parcels: page, more, gap: after < prunedPast };
  // The whole page went: every other device's parcel up to the book's last number is in it (the book's row is
  // held FOR UPDATE, so none comes in between), and the device may read on from there (23 Oct 2026).
  const top = Number((await tx`SELECT COALESCE(MAX(seq), 0) AS s FROM rk_relay.parcels WHERE book = ${book}`)[0].s);
  return { parcels: page, more, gap: after < prunedPast, through: Math.max(after, top) };
}

/** Parcels every other device of the book has taken are gone (as cloud/worker's pruneTaken), and the book's bytes with them. */
// deno-lint-ignore no-explicit-any
async function pruneTaken(tx: any, book: string): Promise<void> {
  const gone = await tx`DELETE FROM rk_relay.parcels p WHERE p.book = ${book} AND NOT EXISTS (
                          SELECT 1 FROM rk_relay.devices d WHERE d.book = ${book} AND d.device != p.sender AND d.acked < p.seq)
                        RETURNING 1`;
  if (gone.length > 0) await bookBytes(tx, book);
}

/** The book's kept byte figure (STORE_BYTES' sum) put right from its parcels; no row is written when it is right. */
// deno-lint-ignore no-explicit-any
async function bookBytes(tx: any, book: string): Promise<void> {
  await tx`UPDATE rk_relay.books SET bytes = s.n
           FROM (SELECT COALESCE(SUM(octet_length(body)), 0) AS n FROM rk_relay.parcels WHERE book = ${book}) s
           WHERE book = ${book} AND bytes <> s.n`;
}
