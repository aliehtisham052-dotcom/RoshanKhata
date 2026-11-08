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
 * A book no second device has joined (8 Nov 2026 review, C1: anyone may make books, and a stranger's books of
 * BOOK_BYTES each filled the store for every shop). Until a second device says hello it may hold only
 * YOUNG_BOOK_BYTES, is swept YOUNG_DAYS after it was made, and is refused parcels - as a NEW book is refused -
 * once the store holds half of STORE_BYTES (the answers stay "full" and "book-full": every client knows them).
 */
const YOUNG_BOOK_BYTES = Number(Deno.env.get("RK_YOUNG_BOOK_BYTES") ?? String(256 * 1024));
const YOUNG_DAYS = Number(Deno.env.get("RK_YOUNG_DAYS") ?? "3");
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
    // Waris ki Chaabi (heir access, 4 Nov 2026): tables of their own, never touched by the books' sweeps.
    await sql.unsafe(`
      CREATE TABLE IF NOT EXISTS rk_relay.waris_vaults (
        vault TEXT PRIMARY KEY, pass TEXT NOT NULL, created BIGINT NOT NULL, last_owner BIGINT NOT NULL,
        last_any BIGINT NOT NULL, bytes BIGINT NOT NULL DEFAULT 0,
        snap_v INTEGER, snap_n INTEGER, snap_bytes BIGINT, snap_at BIGINT);
      CREATE TABLE IF NOT EXISTS rk_relay.waris_heirs (
        vault TEXT NOT NULL REFERENCES rk_relay.waris_vaults(vault) ON DELETE CASCADE, hid TEXT NOT NULL,
        pass TEXT NOT NULL, box TEXT NOT NULL, wait INTEGER NOT NULL, last_seen BIGINT, asked_at BIGINT,
        rejected_at BIGINT, allowed_at BIGINT, PRIMARY KEY (vault, hid));
      CREATE TABLE IF NOT EXISTS rk_relay.waris_chunks (
        vault TEXT NOT NULL REFERENCES rk_relay.waris_vaults(vault) ON DELETE CASCADE, v INTEGER NOT NULL,
        i INTEGER NOT NULL, n INTEGER NOT NULL, body BYTEA NOT NULL, PRIMARY KEY (vault, v, i));
      ALTER TABLE rk_relay.waris_vaults ENABLE ROW LEVEL SECURITY;
      ALTER TABLE rk_relay.waris_heirs ENABLE ROW LEVEL SECURITY;
      ALTER TABLE rk_relay.waris_chunks ENABLE ROW LEVEL SECURITY;
    `);
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
      // A book no second device ever joined, YOUNG_DAYS after it was made, goes with what it holds (C1).
      await sql`DELETE FROM rk_relay.books b WHERE b.created < ${now - YOUNG_DAYS * 86_400_000}
                AND (SELECT COUNT(*) FROM rk_relay.devices d WHERE d.book = b.book) < 2`;
      // A device nobody saw for DEVICE_IDLE_MS goes with its parcels, in books still in use too.
      await sql`DELETE FROM rk_relay.parcels p USING rk_relay.devices d
                WHERE p.book = d.book AND p.sender = d.device AND d.seen < ${now - DEVICE_IDLE_MS}`;
      await sql`DELETE FROM rk_relay.devices WHERE seen < ${now - DEVICE_IDLE_MS}`;
      // Every book's byte figure put right from its parcels (the sweep above took some; a figure kept may drift).
      await sql`UPDATE rk_relay.books b SET bytes = s.n
                FROM (SELECT b2.book, COALESCE((SELECT SUM(octet_length(p.body)) FROM rk_relay.parcels p WHERE p.book = b2.book), 0) AS n
                      FROM rk_relay.books b2) s
                WHERE b.book = s.book AND b.bytes <> s.n`;
      // Waris vaults are not books: one with no owner or heir call for WARIS_IDLE_DAYS (1095) goes, and one still bare
      // WARIS_JUNK_DAYS DAYs after its setup — no heir, no snapshot — is junk (7 Nov 2026, R1: anyone may make one).
      await sql`DELETE FROM rk_relay.waris_vaults WHERE last_any < ${now - WARIS_IDLE_DAYS * 86_400_000}`;
      await sql`DELETE FROM rk_relay.waris_vaults v WHERE v.created <= ${now - WARIS_JUNK_DAYS * WARIS_DAY_MS}
                AND v.snap_v IS NULL AND NOT EXISTS (SELECT 1 FROM rk_relay.waris_heirs h WHERE h.vault = v.vault)`;
    }
    // "push": this relay sends no push itself (R70: said, as cloud/worker says whether it can).
    return json({ ok: true, live, push: false });
  }
  if (!live) return refuse(503, "halted");
  const wm = url.pathname.match(/\/v1\/w(\/.*)?$/);
  if (wm) return await waris(request, url, wm[1] ?? "");
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
      // New books wait once the store holds half its bytes (C1): the other half stays for the shops already syncing.
      if (Number(counts.bytes) + NEW_BOOK_ROOM > Math.floor(STORE_BYTES / 2)) throw new Answer(refuse(507, "full"));
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
        return json({ ok: true, wake: t != null, push: false });
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
  // The whole store first, as the hosting does: the other books' figures as kept and this book's counted afresh.
  // The books' alone: Waris vaults have their own bytes (WARIS_BYTES), so a vault never refuses a parcel (7 Nov 2026, R1).
  const others = Number((await tx`SELECT COALESCE(SUM(bytes), 0) AS n FROM rk_relay.books WHERE book != ${book}`)[0].n);
  if (others + stored + body.length > STORE_BYTES) return [507, "full"];
  // A book no second device has joined (C1): refused as a new book is once the store holds half its bytes.
  const young = Number((await tx`SELECT COUNT(*) AS n FROM rk_relay.devices WHERE book = ${book}`)[0].n) < 2;
  if (young && others + stored + body.length > Math.floor(STORE_BYTES / 2)) return [507, "full"];
  const day = new Date(now).toISOString().slice(0, 10);
  const count = b.day === day ? Number(b.day_count) : 0;
  const dayBytes = b.day === day ? Number(b.day_bytes ?? 0) : 0;
  if (count >= DAY_LIMIT) return [429, "day-limit"];
  if (dayBytes + body.length > DAY_BYTES) return [429, "day-bytes"];
  if (stored + body.length > (young ? YOUNG_BOOK_BYTES : BOOK_BYTES)) return [507, "book-full"];
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

// ---------------------------------------------------------------- Waris ki Chaabi (heir access, 4 Nov 2026)
//
// The WARIS protocol v1, as cloud/worker and web/relay speak it: a vault per book (an opaque sealed snapshot in
// chunks, and up to five heirs' key boxes); an heir may ask, the owner may refuse, and after the wait the heir's
// box opens. Tables rk_relay.waris_* (RLS on, no policy); the books' sweeps never touch them.

/** One DAY of the wait: WARIS_DAY_MS from the function's environment (the tests), never from a request. */
const WARIS_DAY_MS = (() => {
  const n = Number(Deno.env.get("WARIS_DAY_MS") ?? "");
  return Number.isFinite(n) && n > 0 ? Math.floor(n) : 86_400_000;
})();
const WARIS_CHUNK = 524_288;
const WARIS_SNAP = 8_388_608;
const WARIS_MAX_N = 16;
const WARIS_HEIRS = 5;
const WARIS_BOX = 4096;
const WARIS_IDLE_DAYS = 1095;
/** A vault still bare (no heir, no snapshot) this many DAYs after its setup is junk and goes at the daily sweep (R1). */
const WARIS_JUNK_DAYS = 14;
/** Vaults this relay holds at most (each may hold up to two versions' chunks while one replaces the other). */
const MAX_VAULTS = Number(Deno.env.get("RK_MAX_VAULTS") ?? "2000");
/**
 * Bytes every vault's chunks may hold together (7 Nov 2026, R1: anyone may make a vault and fill it): their own,
 * never counted in the books' STORE_BYTES, so a vault never refuses a book's parcel. Past it: 507 "waris-full".
 */
const WARIS_BYTES = Number(Deno.env.get("RK_WARIS_BYTES") ?? String(128 * 1024 * 1024));
/** New vaults taken in 24 hours, all together (R1), as NEW_BOOKS_DAY for books: past it, 429 "waris-day". */
const NEW_VAULTS_DAY = Number(Deno.env.get("RK_NEW_VAULTS_DAY") ?? "50");
const HEX16 = /^[0-9a-f]{16}$/;
const B64 = /^[A-Za-z0-9+/]+={0,2}$/;

/** Bytes every vault's chunks hold, as kept in waris_vaults.bytes (WARIS_BYTES' count, never the books'). */
// deno-lint-ignore no-explicit-any
async function warisBytes(tx: any): Promise<number> {
  return Number((await tx`SELECT COALESCE(SUM(bytes), 0) AS n FROM rk_relay.waris_vaults`)[0].n);
}

function no(status: number, body: Record<string, unknown>): Answer {
  return new Answer(json({ ok: false, ...body }, status));
}

type Heir = { hid: string; pass: string; box: string; wait: number; last_seen: string | null; asked_at: string | null;
  rejected_at: string | null; allowed_at: string | null };
const n0 = (x: string | number | null) => (x == null ? null : Number(x));
function openAt(h: Heir): number | null {
  if (h.allowed_at != null) return Number(h.allowed_at);
  if (h.asked_at != null) return Number(h.asked_at) + Number(h.wait) * WARIS_DAY_MS;
  return null;
}
function isOpen(h: Heir, now: number): boolean {
  if (h.allowed_at != null) return true;
  return h.asked_at != null && now >= Number(h.asked_at) + Number(h.wait) * WARIS_DAY_MS;
}
function query(url: URL, name: string): number | null {
  const v = url.searchParams.get(name);
  return v != null && /^[0-9]{1,12}$/.test(v) ? Number(v) : null;
}

async function waris(request: Request, url: URL, rest: string): Promise<Response> {
  const m = rest.match(/^\/([^/]+)(?:\/(.*))?$/);
  if (!m) return refuse(404, "no-such-path");
  const vault = m[1];
  const tail = (m[2] ?? "").replace(/\/+$/, "");
  let hid: string | null = null;
  let routes: Record<string, string>;
  const top: Record<string, Record<string, string>> = {
    "": { DELETE: "vault-delete" }, setup: { POST: "setup" }, snap: { PUT: "snap" }, status: { GET: "status" },
  };
  if (tail in top) routes = top[tail];
  else {
    const hm = tail.match(/^heir\/([^/]+)(?:\/(reject|allow|ask|alive|open|snap))?$/);
    if (!hm) return refuse(404, "no-such-path");
    hid = hm[1];
    const sub = hm[2] ?? "";
    routes = sub === "" ? { PUT: "heir-put", DELETE: "heir-delete" }
      : { [sub === "open" || sub === "snap" ? "GET" : "POST"]: sub === "snap" ? "heir-snap" : sub };
  }
  const route = routes[request.method];
  if (!route) return refuse(405, "method");
  if (!HEX32.test(vault) || (hid != null && !HEX16.test(hid))) return json({ ok: false, why: "bad" }, 400);
  let token = (request.headers.get("x-rk-pass") ?? "").trim();
  if (!token) {
    const auth = request.headers.get("authorization") ?? "";
    token = auth.startsWith("RK ") ? auth.slice(3).trim() : "";
  }
  if (!HEX64.test(token)) return json({ ok: false, why: "pass" }, 403);
  const hash = await sha256Hex(token);
  const heirRoute = route === "ask" || route === "alive" || route === "open" || route === "heir-snap";

  // Bodies are read before the transaction (a slow upload never holds the vault's row).
  let chunk: Uint8Array | null = null;
  let o: Record<string, unknown> | null = null;
  let sv = 0, si = 0, sn = 0;
  if (route === "snap") {
    const v = query(url, "v"), i = query(url, "i"), n = query(url, "n");
    if (v == null || i == null || n == null || v < 1 || n < 1 || n > WARIS_MAX_N || i >= n) {
      await request.body?.cancel().catch(() => null);
      return json({ ok: false, why: "bad" }, 400);
    }
    sv = v; si = i; sn = n;
    chunk = await readCapped(request, WARIS_CHUNK);
    if (chunk == null) return json({ ok: false, why: "too-big" }, 413);
    if (chunk.length === 0) return json({ ok: false, why: "bad" }, 400);
  } else if (route === "heir-put") {
    o = parseJson(await readCapped(request, 16 * 1024));
  }

  const now = Date.now();
  // A chunk's bytes come back raw, not as JSON: carried out of the transaction here.
  let raw: Uint8Array | null = null;
  const res = await sql.begin(async (tx) => {
    const rows = await tx`SELECT pass, snap_v, snap_n, snap_bytes, snap_at FROM rk_relay.waris_vaults WHERE vault = ${vault} FOR UPDATE`;
    const vrow = rows[0] as unknown as { pass: string; snap_v: number | null; snap_n: number | null; snap_bytes: string | null; snap_at: string | null } | undefined;
    if (route === "setup") {
      if (!vrow) {
        // A new vault, as a new book: the count, then the vaults' own bytes (never the books'), then the day's new
        // vaults (7 Nov 2026, R1: anyone may make one). A vault already here is set up again whatever the day's count.
        const c = (await tx`SELECT COUNT(*) AS n, COUNT(*) FILTER (WHERE created > ${now - 86_400_000}) AS d
                            FROM rk_relay.waris_vaults`)[0];
        if (Number(c.n) >= MAX_VAULTS) throw no(507, { why: "full" });
        if (await warisBytes(tx) >= WARIS_BYTES) throw no(507, { why: "waris-full" });
        if (Number(c.d) >= NEW_VAULTS_DAY) throw no(429, { why: "waris-day" });
        const made = await tx`INSERT INTO rk_relay.waris_vaults (vault, pass, created, last_owner, last_any)
                              VALUES (${vault}, ${hash}, ${now}, ${now}, ${now}) ON CONFLICT (vault) DO NOTHING RETURNING 1`;
        if (made.length > 0) return json({ ok: true, created: true });
        // Made by a call that ran beside this one: the pass decides, as for any known vault.
        const again = (await tx`SELECT pass FROM rk_relay.waris_vaults WHERE vault = ${vault} FOR UPDATE`)[0];
        if (!again || !sameHex(again.pass as string, hash)) throw no(403, { why: "pass" });
        return json({ ok: true, created: false });
      }
      if (!sameHex(vrow.pass, hash)) throw no(403, { why: "pass" });
      await tx`UPDATE rk_relay.waris_vaults SET last_owner = ${now}, last_any = ${now} WHERE vault = ${vault}`;
      return json({ ok: true, created: false });
    }
    if (!vrow) throw no(404, { why: "unknown" });
    let heir: Heir | null = null;
    if (hid != null) {
      heir = ((await tx`SELECT * FROM rk_relay.waris_heirs WHERE vault = ${vault} AND hid = ${hid} FOR UPDATE`)[0] ?? null) as unknown as Heir | null;
    }
    if (heirRoute) {
      if (!heir) throw no(404, { why: "unknown" });
      if (!sameHex(heir.pass, hash)) throw no(403, { why: "pass" });
      await tx`UPDATE rk_relay.waris_heirs SET last_seen = ${now} WHERE vault = ${vault} AND hid = ${hid}`;
      await tx`UPDATE rk_relay.waris_vaults SET last_any = ${now} WHERE vault = ${vault}`;
      heir.last_seen = String(now);
    } else {
      if (!sameHex(vrow.pass, hash)) throw no(403, { why: "pass" });
      if (hid != null && !heir && route !== "heir-put") throw no(404, { why: "unknown" });
      await tx`UPDATE rk_relay.waris_vaults SET last_owner = ${now}, last_any = ${now} WHERE vault = ${vault}`;
    }
    const snap = vrow.snap_v == null ? null
      : { v: Number(vrow.snap_v), n: Number(vrow.snap_n), bytes: Number(vrow.snap_bytes), at: Number(vrow.snap_at) };

    switch (route) {
      case "snap": {
        const body = chunk!;
        const curV = snap ? snap.v : 0;
        if (sv < curV) throw no(409, { why: "old" });
        if (snap && sv === curV && sn !== snap.n) throw no(400, { why: "bad" });
        // Any other unfinished version is abandoned, and so are this version's chunks of another count.
        await tx`DELETE FROM rk_relay.waris_chunks WHERE vault = ${vault} AND v <> ${sv} AND v <> ${curV}`;
        await tx`DELETE FROM rk_relay.waris_chunks WHERE vault = ${vault} AND v = ${sv} AND n <> ${sn}`;
        const mine = Number((await tx`SELECT COALESCE(SUM(octet_length(body)), 0) AS n FROM rk_relay.waris_chunks
                                      WHERE vault = ${vault} AND v = ${sv} AND i <> ${si}`)[0].n);
        if (mine + body.length > WARIS_SNAP) throw no(413, { why: "too-big" });
        const held = Number((await tx`SELECT COALESCE(SUM(octet_length(body)), 0) AS n FROM rk_relay.waris_chunks
                                      WHERE vault = ${vault} AND NOT (v = ${sv} AND i = ${si})`)[0].n);
        // The vaults' own bytes, never the books' (7 Nov 2026, R1).
        const otherVaults = Number((await tx`SELECT COALESCE(SUM(bytes), 0) AS n FROM rk_relay.waris_vaults WHERE vault <> ${vault}`)[0].n);
        if (otherVaults + held + body.length > WARIS_BYTES) throw no(507, { why: "waris-full" });
        await tx`INSERT INTO rk_relay.waris_chunks (vault, v, i, n, body) VALUES (${vault}, ${sv}, ${si}, ${sn}, ${body})
                 ON CONFLICT (vault, v, i) DO UPDATE SET n = EXCLUDED.n, body = EXCLUDED.body`;
        const have = Number((await tx`SELECT COUNT(*) AS n FROM rk_relay.waris_chunks WHERE vault = ${vault} AND v = ${sv}`)[0].n);
        const complete = have === sn;
        if (complete) {
          const total = Number((await tx`SELECT SUM(octet_length(body)) AS n FROM rk_relay.waris_chunks WHERE vault = ${vault} AND v = ${sv}`)[0].n);
          await tx`DELETE FROM rk_relay.waris_chunks WHERE vault = ${vault} AND v <> ${sv}`;
          await tx`UPDATE rk_relay.waris_vaults SET snap_v = ${sv}, snap_n = ${sn}, snap_bytes = ${total}, snap_at = ${now}
                   WHERE vault = ${vault}`;
        }
        await tx`UPDATE rk_relay.waris_vaults SET bytes = (SELECT COALESCE(SUM(octet_length(body)), 0)
                 FROM rk_relay.waris_chunks WHERE vault = ${vault}) WHERE vault = ${vault}`;
        return json({ ok: true, complete });
      }
      case "heir-put": {
        const ph = o?.passHash, box = o?.box, wait = o?.wait;
        if (typeof ph !== "string" || !HEX64.test(ph) || typeof box !== "string" || box.length === 0
            || box.length > WARIS_BOX || !B64.test(box) || typeof wait !== "number" || !Number.isInteger(wait)
            || wait < 1 || wait > 90) throw no(400, { why: "bad" });
        if (!heir) {
          const c = Number((await tx`SELECT COUNT(*) AS n FROM rk_relay.waris_heirs WHERE vault = ${vault}`)[0].n);
          if (c >= WARIS_HEIRS) throw no(409, { why: "full" });
          await tx`INSERT INTO rk_relay.waris_heirs (vault, hid, pass, box, wait) VALUES (${vault}, ${hid}, ${ph}, ${box}, ${wait})`;
        } else if (sameHex(heir.pass, ph)) {
          await tx`UPDATE rk_relay.waris_heirs SET box = ${box}, wait = ${wait} WHERE vault = ${vault} AND hid = ${hid}`;
        } else {
          await tx`UPDATE rk_relay.waris_heirs SET pass = ${ph}, box = ${box}, wait = ${wait}, last_seen = NULL,
                   asked_at = NULL, rejected_at = NULL, allowed_at = NULL WHERE vault = ${vault} AND hid = ${hid}`;
        }
        return json({ ok: true });
      }
      case "heir-delete": {
        await tx`DELETE FROM rk_relay.waris_heirs WHERE vault = ${vault} AND hid = ${hid}`;
        return json({ ok: true });
      }
      case "status": {
        const hs = (await tx`SELECT * FROM rk_relay.waris_heirs WHERE vault = ${vault} ORDER BY hid`) as unknown as Heir[];
        return json({
          ok: true, now, snap,
          heirs: hs.map((h) => ({
            hid: h.hid, wait: Number(h.wait), lastSeen: n0(h.last_seen), askedAt: n0(h.asked_at),
            rejectedAt: n0(h.rejected_at), allowedAt: n0(h.allowed_at), openAt: openAt(h),
          })),
        });
      }
      case "reject": {
        await tx`UPDATE rk_relay.waris_heirs SET asked_at = NULL, allowed_at = NULL, rejected_at = ${now}
                 WHERE vault = ${vault} AND hid = ${hid}`;
        return json({ ok: true });
      }
      case "allow": {
        await tx`UPDATE rk_relay.waris_heirs SET allowed_at = ${now}, asked_at = COALESCE(asked_at, ${now})
                 WHERE vault = ${vault} AND hid = ${hid}`;
        return json({ ok: true });
      }
      case "ask": {
        const h = heir!;
        if (h.asked_at == null) {
          if (h.rejected_at != null && now - Number(h.rejected_at) < WARIS_DAY_MS) {
            // Answered, not thrown: the heir's lastSeen above is kept (a refused ask is a sign of life too).
            return json({ ok: false, why: "wait", retryAt: Number(h.rejected_at) + WARIS_DAY_MS }, 429);
          }
          await tx`UPDATE rk_relay.waris_heirs SET asked_at = ${now} WHERE vault = ${vault} AND hid = ${hid}`;
          h.asked_at = String(now);
        }
        return json({ ok: true, askedAt: Number(h.asked_at), openAt: openAt(h) });
      }
      case "alive": {
        const h = heir!;
        return json({ ok: true, askedAt: n0(h.asked_at), openAt: openAt(h), rejectedAt: n0(h.rejected_at), open: isOpen(h, now) });
      }
      case "open": {
        const h = heir!;
        if (!isOpen(h, now)) return json({ ok: false, why: "waiting", openAt: openAt(h) }, 403);
        return json({ ok: true, box: h.box, snap });
      }
      case "heir-snap": {
        const h = heir!;
        if (!isOpen(h, now)) return json({ ok: false, why: "waiting", openAt: openAt(h) }, 403);
        const v = query(url, "v"), i = query(url, "i");
        if (v == null || i == null) return json({ ok: false, why: "bad" }, 400);
        if (!snap || snap.v !== v) return json({ ok: false, why: "absent" }, 404);
        const r = await tx`SELECT body FROM rk_relay.waris_chunks WHERE vault = ${vault} AND v = ${v} AND i = ${i}`;
        if (r.length === 0) return json({ ok: false, why: "absent" }, 404);
        raw = new Uint8Array(r[0].body as Uint8Array);
        return null;
      }
      case "vault-delete": {
        await tx`DELETE FROM rk_relay.waris_vaults WHERE vault = ${vault}`;
        return json({ ok: true });
      }
    }
    throw no(404, { why: "no-such-path" });
  });
  if (res) return res;
  const got = raw as Uint8Array | null;
  if (!got) return refuse(500, "server");
  // A fresh ArrayBuffer-backed copy: the driver's buffer type is not a BodyInit for Deno's types.
  const body = new Uint8Array(got.length);
  body.set(got);
  return new Response(body.buffer, {
    status: 200,
    headers: { "content-type": "application/octet-stream", "cache-control": "no-store", "content-length": String(body.length) },
  });
}
