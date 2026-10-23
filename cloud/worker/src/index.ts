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
 *
 * The live line (23 Oct 2026): a device with the app open keeps one WebSocket
 * to its book's object ("live"), accepted with the Hibernation API so a quiet
 * line costs nothing while it waits. A round goes over it as one message
 * (Cloudflare bills incoming WebSocket messages 20 to a request; outgoing ones
 * and protocol pings are free), and a parcel left by one device is answered by
 * a one-word nudge ("new") to the others' lines, so they ask at once instead
 * of every few seconds. A device whose line is open is not woken by FCM.
 */
import { DurableObject } from "cloudflare:workers";

export interface Env {
  BOOKS: DurableObjectNamespace<Daakiya>;
  LIVE: string;
  KEEP_DAYS: string;
  DAY_LIMIT: string;
  /** Other relays' health addresses, called once a day (KEEP_ALIVE in wrangler.toml); comma-separated. */
  KEEP_ALIVE?: string;
  /** Sent with the keep-alive call as X-RK-Keep, so a relay's health is not an open door. */
  KEEP_SECRET?: string;
  FCM_SERVICE_ACCOUNT?: string;
  FCM_PROJECT_ID?: string;
  /** Limits a test (or the owner) may lower through wrangler vars; each has its default below. */
  BOOK_BYTES?: string;
  DAY_BYTES?: string;
  DEVICE_IDLE_MS?: string;
  PRUNE_EVERY_MS?: string;
  NEW_BOOKS_DAY?: string;
  MAX_BOOKS?: string;
}

/** A limit from the Worker's vars, else its default. */
function limit(v: string | undefined, fallback: number): number {
  const n = Number(v);
  return v != null && v !== "" && Number.isFinite(n) && n >= 0 ? n : fallback;
}

/**
 * Bytes a book may hold and leave in a UTC day (23 Oct 2026, the review: counts alone let one book store
 * 3,000 MB a day). The free plan's 5 GB is shared by every shop; a busy counter leaves well under 1 MB a day.
 */
const BOOK_BYTES = 16 * 1024 * 1024;
const DAY_BYTES = 32 * 1024 * 1024;
/** A device not seen this long is let go (its parcels too), so a lost phone stops pinning the book's parcels. */
const DEVICE_IDLE_MS = 30 * 86_400_000;
const PRUNE_EVERY_MS = 6 * 3600_000;
/** Refusals of a parcel after which the round still answers the others' page (and keeps the ack). */
const CAPACITY = new Set(["day-limit", "day-bytes", "book-full"]);

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
/**
 * A parcel this small rides inside the FCM message itself (23 Oct 2026): the phone takes it without asking the
 * relay. FCM's data payload is at most 4,096 bytes (Firebase's error codes page); the base64 body is kept well
 * under it with room for the keys.
 */
const CARRY_MAX_B64 = 3_400;
/** FCM marks a sender abusive near 240 messages a minute to one device: carried parcels stop well short. */
const CARRY_PER_MINUTE = 60;
/** A quiet call does not write the device's "seen" more often than this: the free plan counts rows written. */
const SEEN_EVERY_MS = 3_600_000;
/**
 * New books one Worker takes in 24 hours, and books it holds at most (22 Oct 2026: anyone may open a book).
 * 100 a day since 23 Oct (was 300): each new book may hold BOOK_BYTES, and the free plan's storage is shared.
 */
const NEW_BOOKS_DAY = 100;
const MAX_BOOKS = 20_000;

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
    const m = url.pathname.match(/^\/v1\/b\/([0-9a-f]{32})\/(hello|send|take|ack|wake|leave|round|live)$/);
    if (!m) return refuse(404, "no-such-path");
    const [, book, action] = m;
    const token = passOf(request);
    if (!HEX64.test(token)) return refuse(401, "no-pass");
    const device = request.headers.get("x-device") ?? "";
    if (!DEVICE.test(device)) return refuse(400, "no-device");
    const want = action === "take" || action === "live" ? "GET" : "POST";
    if (request.method !== want) return refuse(405, "method");
    if (action === "live" && request.headers.get("upgrade")?.toLowerCase() !== "websocket") return refuse(426, "websocket");
    if (action === "hello") {
      // One box for the whole Worker counts the books: a stranger opening boxes at will meets a limit
      // before the plan's does (one extra request per hello only).
      const ledger = env.BOOKS.get(env.BOOKS.idFromName("!ledger"));
      const ok = await ledger.fetch(new Request("https://ledger/" + book, { method: "POST" }));
      if (ok.status !== 200) return ok;
    }
    const stub = env.BOOKS.get(env.BOOKS.idFromName(book));
    return stub.fetch(request);
  },

  /**
   * Once a day (wrangler.toml's cron): the other relays' health addresses are called, so a free
   * Supabase project is never paused for want of use (it pauses after 7 quiet days) and its idle books go.
   */
  async scheduled(_event: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    const urls = (env.KEEP_ALIVE ?? "").split(",").map((s) => s.trim()).filter((s) => s.startsWith("https://"));
    const headers: Record<string, string> = { "user-agent": "rk-keepalive" };
    if (env.KEEP_SECRET) headers["x-rk-keep"] = env.KEEP_SECRET;
    ctx.waitUntil(Promise.all(urls.map((u) => fetch(u, { headers }).catch(() => null))));
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

/** A round's body, over HTTP ("round") or the live line. */
interface RoundBody { after?: number; upTo?: number | null; parcel?: string | null }

function utcDay(now: number): string {
  return new Date(now).toISOString().slice(0, 10);
}

export class Daakiya extends DurableObject<Env> {
  private sql: SqlStorage;
  /** Whether this object's tables are known to exist (they are made only by a book's first hello). */
  private tables = false;
  private ledgerReady = false;

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
  }

  /**
   * The book's tables, made only when [create] (a hello). Any other call to an object without them is refused
   * before anything is written (23 Oct 2026, the review: the constructor made them for every stranger's call,
   * and an object once written to stays stored until deleteAll).
   */
  private haveTables(create: boolean): boolean {
    if (this.tables) return true;
    const has = this.sql.exec(`SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'meta'`).toArray().length > 0;
    if (!has && !create) return false;
    this.sql.exec(`CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v TEXT NOT NULL)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS devices (
      device TEXT PRIMARY KEY, acked INTEGER NOT NULL DEFAULT 0, seen INTEGER NOT NULL,
      fcm TEXT, woke INTEGER NOT NULL DEFAULT 0)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS parcels (
      seq INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT NOT NULL, at INTEGER NOT NULL, body BLOB NOT NULL)`);
    // Pruning by sender reads about the rows it deletes, not every parcel times every device.
    this.sql.exec(`CREATE INDEX IF NOT EXISTS parcels_sender ON parcels (sender, seq)`);
    this.tables = true;
    return true;
  }

  /** The book emptied: the ledger forgets it, then every stored byte and the alarm go. */
  private async forget(): Promise<void> {
    const book = this.meta("book");
    if (book) {
      const ledger = this.env.BOOKS.get(this.env.BOOKS.idFromName("!ledger"));
      await ledger.fetch(new Request("https://ledger/" + book, { method: "DELETE" })).catch(() => null);
    }
    for (const ws of this.ctx.getWebSockets()) {
      try { ws.close(4000, "gone"); } catch { /* already gone */ }
    }
    await this.ctx.storage.deleteAlarm();
    await this.ctx.storage.deleteAll();
    // The instance lives on with no tables: the next call is refused until a hello makes them again.
    this.tables = false;
  }

  /** Bytes the book's parcels hold (kept as a count, put right from the parcels at each alarm). */
  private addBytes(delta: number): void {
    if (delta === 0) return;
    this.setMeta("bytes", String(Math.max(0, Number(this.meta("bytes") ?? "0") + delta)));
  }

  /** Parcels matching [where] go, and their bytes leave the count; nothing is written when none match. */
  private dropParcels(where: string, ...args: (string | number)[]): void {
    const n = this.sql.exec<{ b: number | null }>(`SELECT SUM(length(body)) AS b FROM parcels WHERE ${where}`, ...args).one().b;
    if (n == null) return;
    this.sql.exec(`DELETE FROM parcels WHERE ${where}`, ...args);
    this.addBytes(-n);
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
    if (url.hostname === "ledger") return this.ledger(url.pathname.slice(1), request.method);
    const parts = url.pathname.split("/");
    const action = parts.pop()!;
    const book = parts.pop()!;
    const token = passOf(request);
    const device = request.headers.get("x-device")!;
    const now = Date.now();

    // An object with no tables is no book yet: only a hello makes one (nothing is written for any other call).
    if (!this.haveTables(action === "hello")) return refuse(403, "say-hello-first");
    const pass = await sha256Hex(token);
    const known = this.meta("pass");
    if (known == null) {
      // The book's first device sets the pass — only on hello, never by a stray parcel.
      if (action !== "hello") return refuse(403, "say-hello-first");
      this.setMeta("pass", pass);
      this.setMeta("created", String(now));
      this.setMeta("book", book);
    } else if (!sameHex(known, pass)) {
      return refuse(403, "wrong-pass");
    } else if (action === "hello" && this.meta("book") == null) {
      // A book from before 23 Oct learns its own id, so the ledger can forget it when it empties.
      this.setMeta("book", book);
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
      case "leave": return await this.leave(device);
      case "round": return this.round(request, url, device, now);
      case "live": return this.live(device);
    }
    return refuse(404, "no-such-path");
  }

  /**
   * The Worker's one count of books (the "!ledger" object): a book is counted on its first hello (POST) and
   * forgotten when its object empties (DELETE, from forget()). The total is kept in one row and the day's
   * count reads an index (23 Oct 2026, the review: two COUNT(*) scans of every book on each refused hello).
   */
  private ledger(book: string, method: string): Response {
    if (!this.ledgerReady) {
      this.sql.exec(`CREATE TABLE IF NOT EXISTS books (book TEXT PRIMARY KEY, at INTEGER NOT NULL)`);
      this.sql.exec(`CREATE INDEX IF NOT EXISTS books_at ON books (at)`);
      this.sql.exec(`CREATE TABLE IF NOT EXISTS counts (k TEXT PRIMARY KEY, n INTEGER NOT NULL)`);
      this.ledgerReady = true;
    }
    const known = this.sql.exec(`SELECT 1 FROM books WHERE book = ?`, book).toArray().length > 0;
    if (method === "DELETE") {
      if (known) {
        this.sql.exec(`DELETE FROM books WHERE book = ?`, book);
        this.sql.exec(`UPDATE counts SET n = MAX(0, n - 1) WHERE k = 'books'`);
      }
      return json({ ok: true });
    }
    if (known) return json({ ok: true });
    const now = Date.now();
    const total = this.ledgerTotal();
    if (total >= limit(this.env.MAX_BOOKS, MAX_BOOKS)) return refuse(507, "full");
    const today = this.sql.exec<{ n: number }>(`SELECT COUNT(*) AS n FROM books WHERE at > ?`, now - 86_400_000).one().n;
    if (today >= limit(this.env.NEW_BOOKS_DAY, NEW_BOOKS_DAY)) return refuse(429, "new-books");
    this.sql.exec(`INSERT INTO books (book, at) VALUES (?, ?)`, book, now);
    this.sql.exec(`UPDATE counts SET n = n + 1 WHERE k = 'books'`);
    return json({ ok: true });
  }

  /** The ledger's total; counted once from the rows for a ledger kept before the total was. */
  private ledgerTotal(): number {
    const r = this.sql.exec<{ n: number }>(`SELECT n FROM counts WHERE k = 'books'`).toArray();
    if (r.length) return r[0].n;
    const n = this.sql.exec<{ n: number }>(`SELECT COUNT(*) AS n FROM books`).one().n;
    this.sql.exec(`INSERT INTO counts (k, n) VALUES ('books', ?)`, n);
    return n;
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
    const r = this.put(body, device, now);
    if (typeof r !== "number") return refuse(r.status, r.why);
    return json({ ok: true, seq: r });
  }

  /** One parcel in: its number, or the refusal. */
  private put(body: Uint8Array, device: string, now: number): number | { status: number; why: string } {
    if (body.length === 0) return { status: 400, why: "empty" };
    if (body.length > MAX_PARCEL) return { status: 413, why: "too-big" };
    const day = utcDay(now);
    const countKey = "count:" + day;
    // "<parcels>/<bytes>" for the day (a value from before 23 Oct holds the parcels alone).
    const [countText, dayBytesText] = (this.meta(countKey) ?? "0").split("/");
    const count = Number(countText) || 0;
    const dayBytes = Number(dayBytesText ?? "0") || 0;
    if (count >= limit(this.env.DAY_LIMIT, 3000)) return { status: 429, why: "day-limit" };
    if (dayBytes + body.length > limit(this.env.DAY_BYTES, DAY_BYTES)) return { status: 429, why: "day-bytes" };
    const stored = Number(this.meta("bytes") ?? "0");
    if (stored + body.length > limit(this.env.BOOK_BYTES, BOOK_BYTES)) return { status: 507, why: "book-full" };
    this.sql.exec(`DELETE FROM meta WHERE k LIKE 'count:%' AND k != ?`, countKey);
    this.setMeta(countKey, `${count + 1}/${dayBytes + body.length}`);
    this.setMeta("bytes", String(stored + body.length));
    const seq = this.sql.exec<{ seq: number }>(
      `INSERT INTO parcels (sender, at, body) VALUES (?, ?, ?) RETURNING seq`, device, now, body,
    ).one().seq;
    this.touch(device, now, true);
    // The open lines are nudged at once; the devices without one get the parcel itself through FCM when it is small
    // (or a wake-up to come for it).
    const live = this.nudge(device);
    this.ctx.waitUntil(this.wakeOthers(device, now, live, { seq, body }));
    return seq;
  }

  private take(url: URL, device: string, now: number): Response {
    const after = Math.max(0, Number(url.searchParams.get("after") ?? "0") || 0);
    return json({ ok: true, ...this.takePage(after, device, now) });
  }

  /** A quiet call (nothing to take) writes "seen" at most once an hour: the free plan counts rows written. */
  private touch(device: string, now: number, always: boolean): void {
    if (always) { this.sql.exec(`UPDATE devices SET seen = ? WHERE device = ?`, now, device); return; }
    this.sql.exec(`UPDATE devices SET seen = ? WHERE device = ? AND seen < ?`, now, device, now - SEEN_EVERY_MS);
  }

  private takePage(after: number, device: string, now: number): { parcels: { seq: number; from: string; at: number; body: string }[]; more: boolean; gap: boolean; through?: number } {
    const rows = this.sql.exec<{ seq: number; sender: string; at: number; body: ArrayBuffer }>(
      `SELECT seq, sender, at, body FROM parcels WHERE seq > ? AND sender != ? ORDER BY seq LIMIT ?`,
      after, device, TAKE_PAGE + 1,
    ).toArray();
    this.touch(device, now, rows.length > 0);
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
    if (more) return { parcels: page, more, gap };
    // The whole page went: every other device's parcel up to the book's last number is in it, so the device may
    // read on from there (its own parcels after its cursor are not scanned again each round; 23 Oct 2026).
    const through = this.sql.exec<{ s: number | null }>(`SELECT MAX(seq) AS s FROM parcels`).one().s ?? after;
    return { parcels: page, more, gap, through: Math.max(after, through) };
  }

  /**
   * One request for a whole round (22 Oct 2026): the acknowledgement, this
   * device's parcel and the others' page, in that order — one request where
   * there were three, so a free plan carries three times the shops.
   * Body: {"after": n, "upTo": n | null, "parcel": base64 | null}.
   */
  private async round(request: Request, url: URL, device: string, now: number): Promise<Response> {
    const o = (await request.json().catch(() => null)) as RoundBody | null;
    if (!o) return refuse(400, "bad-round");
    const r = this.roundOf(o, device, now);
    return json(r.body, r.status);
  }

  /** A round's work, for the HTTP path and the live line alike: the answer and its HTTP status. */
  private roundOf(o: RoundBody, device: string, now: number): { body: Record<string, unknown>; status: number } {
    const no = (status: number, why: string) => ({ body: { ok: false, why }, status });
    const after = Math.max(0, Number(o.after ?? 0) || 0);
    if (o.upTo != null) {
      const upTo = Number(o.upTo);
      if (!Number.isFinite(upTo) || upTo < 0) return no(400, "bad-ack");
      this.sql.exec(`UPDATE devices SET acked = MAX(acked, ?) WHERE device = ?`, upTo, device);
      this.pruneTaken();
    }
    let seq: number | null = null;
    if (typeof o.parcel === "string" && o.parcel.length > 0) {
      let body: Uint8Array;
      try { body = Uint8Array.from(atob(o.parcel), (c) => c.charCodeAt(0)); } catch { return no(400, "bad-parcel"); }
      const r = this.put(body, device, now);
      if (typeof r !== "number") {
        if (!CAPACITY.has(r.why)) return no(r.status, r.why);
        // The book's limit refuses the parcel, not the round (23 Oct 2026, the review): the ack above stays and the
        // others' page still comes, under the refusal's status; the device leaves its parcel at another relay.
        return { body: { ok: false, why: r.why, seq: null, ...this.takePage(after, device, now) }, status: r.status };
      }
      seq = r;
    }
    return { body: { ok: true, seq, ...this.takePage(after, device, now) }, status: 200 };
  }

  /**
   * The live line: one WebSocket per open app, tagged with its device. Accepted for hibernation, so the
   * object leaves memory between messages and a quiet line costs nothing; a device may hold one line
   * (a new one closes the old). Only a device the book knows (it said hello) may open one.
   */
  private live(device: string): Response {
    for (const old of this.ctx.getWebSockets(device)) {
      try { old.close(4000, "replaced"); } catch { /* already gone */ }
    }
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server, [device]);
    server.serializeAttachment({ device });
    return new Response(null, { status: 101, webSocket: client });
  }

  /** A round over the live line: {"id": n, "round": {...}} answered with {"id": n, "status": s, ...answer}. */
  async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer): Promise<void> {
    const device = (ws.deserializeAttachment() as { device?: string } | null)?.device;
    if (!device) { ws.close(4001, "no-device"); return; }
    if (this.env.LIVE === "0") { ws.close(4503, "halted"); return; }
    // The book was emptied under the line (its last device left, or a month's quiet): nothing is made again here.
    if (!this.haveTables(false)) { ws.close(4001, "gone"); return; }
    let o: { id?: number; round?: RoundBody } | null = null;
    try { o = JSON.parse(typeof message === "string" ? message : new TextDecoder().decode(message)); } catch { o = null; }
    const id = Number(o?.id ?? 0);
    // Still a device of this book (a "leave" or a month's quiet may have let it go): else the line ends.
    const known = this.sql.exec(`SELECT 1 FROM devices WHERE device = ?`, device).toArray().length > 0;
    if (!known) { ws.send(JSON.stringify({ id, status: 403, ok: false, why: "say-hello-first" })); return; }
    if (!o || !o.round || typeof o.round !== "object") { ws.send(JSON.stringify({ id, status: 400, ok: false, why: "bad-round" })); return; }
    const now = Date.now();
    const r = this.roundOf(o.round, device, now);
    // A device that only ever rounds over its line is still "seen" (at most one write an hour), so a month's
    // sweep never takes a book whose devices are simply quiet.
    this.touch(device, now, false);
    ws.send(JSON.stringify({ id, status: r.status, ...r.body }));
  }

  async webSocketClose(ws: WebSocket, code: number, reason: string): Promise<void> {
    try { ws.close(code === 1005 ? 1000 : code, reason); } catch { /* already closed */ }
  }

  async webSocketError(ws: WebSocket): Promise<void> {
    try { ws.close(1011, "error"); } catch { /* already closed */ }
  }

  /** A one-word nudge to every other device's open line: "a parcel waits, ask now" (outgoing messages are free). */
  private nudge(sender: string): Set<string> {
    const live = new Set<string>();
    for (const ws of this.ctx.getWebSockets()) {
      const d = (ws.deserializeAttachment() as { device?: string } | null)?.device;
      if (!d || d === sender) continue;
      try { ws.send('{"t":"new"}'); live.add(d); } catch { /* the line went; FCM wakes it instead */ }
    }
    return live;
  }

  private async ack(request: Request, device: string, now: number): Promise<Response> {
    const o = (await request.json().catch(() => null)) as { upTo?: number } | null;
    const upTo = Number(o?.upTo ?? NaN);
    if (!Number.isFinite(upTo) || upTo < 0) return refuse(400, "bad-ack");
    this.sql.exec(`UPDATE devices SET acked = MAX(acked, ?) WHERE device = ?`, upTo, device);
    this.touch(device, now, false);
    this.pruneTaken();
    return json({ ok: true });
  }

  private async wake(request: Request, device: string, now: number): Promise<Response> {
    const o = (await request.json().catch(() => null)) as { token?: string | null } | null;
    const t = typeof o?.token === "string" && o.token.length > 20 && o.token.length < 4096 ? o.token : null;
    this.sql.exec(`UPDATE devices SET fcm = ?, seen = ? WHERE device = ?`, t, now, device);
    return json({ ok: true, wake: t != null });
  }

  private async leave(device: string): Promise<Response> {
    for (const ws of this.ctx.getWebSockets(device)) {
      try { ws.close(4000, "left"); } catch { /* already gone */ }
    }
    this.sql.exec(`DELETE FROM devices WHERE device = ?`, device);
    this.dropParcels(`sender = ?`, device);
    const left = this.sql.exec<{ n: number }>(`SELECT COUNT(*) AS n FROM devices`).one().n;
    // The last device gone: the ledger forgets the book and every byte of it goes (awaited, 23 Oct 2026).
    if (left === 0) await this.forget();
    else this.pruneTaken();
    return json({ ok: true });
  }

  /**
   * Parcels every other device has taken are gone. Per sender (at most 8): its parcels up to the lowest ack of
   * the book's other devices, read through the (sender, seq) index — about the rows deleted, not every parcel
   * times every device (23 Oct 2026, the review). A sender alone in the book waits for nobody.
   */
  private pruneTaken(): void {
    const devices = this.sql.exec<{ device: string; acked: number }>(`SELECT device, acked FROM devices`).toArray();
    for (const s of devices) {
      const others = devices.filter((d) => d.device !== s.device).map((d) => d.acked);
      if (others.length === 0) this.dropParcels(`sender = ?`, s.device);
      else {
        const upTo = Math.min(...others);
        if (upTo > 0) this.dropParcels(`sender = ? AND seq <= ?`, s.device, upTo);
      }
    }
  }

  private async schedulePrune(now: number): Promise<void> {
    if ((await this.ctx.storage.getAlarm()) == null) await this.ctx.storage.setAlarm(now + limit(this.env.PRUNE_EVERY_MS, PRUNE_EVERY_MS));
  }

  /**
   * Every six hours: parcels older than KEEP_DAYS go; a device not seen for DEVICE_IDLE_MS goes with its parcels
   * (a lost phone no longer keeps every parcel for the full week, nor a place among the eight); a book with no
   * device left, or nobody seen for 30 days, is emptied.
   */
  async alarm(): Promise<void> {
    if (!this.haveTables(false)) return;
    const now = Date.now();
    const keep = Math.max(1, Number(this.env.KEEP_DAYS || "7")) * 86_400_000;
    // The cut is read from the oldest parcels only ("at" rises with the number), not by scanning them all.
    let cut: number | null = null;
    for (let batch = 0; batch < 20; batch++) {
      const rows = this.sql.exec<{ seq: number; at: number }>(
        `SELECT seq, at FROM parcels WHERE seq > ? ORDER BY seq LIMIT 1000`, cut ?? 0,
      ).toArray();
      let stopped = false;
      for (const r of rows) {
        if (r.at >= now - keep) { stopped = true; break; }
        cut = r.seq;
      }
      if (stopped || rows.length < 1000) break;
    }
    if (cut != null) {
      this.dropParcels(`seq <= ?`, cut);
      this.setMeta("pruned_past", String(Math.max(cut, Number(this.meta("pruned_past") ?? "0"))));
    }
    const idle = this.sql.exec<{ device: string }>(
      `SELECT device FROM devices WHERE seen < ?`, now - limit(this.env.DEVICE_IDLE_MS, DEVICE_IDLE_MS),
    ).toArray();
    for (const r of idle) {
      for (const ws of this.ctx.getWebSockets(r.device)) {
        try { ws.close(4000, "idle"); } catch { /* already gone */ }
      }
      this.sql.exec(`DELETE FROM devices WHERE device = ?`, r.device);
      this.dropParcels(`sender = ?`, r.device);
    }
    if (idle.length > 0) this.pruneTaken();
    const left = this.sql.exec<{ n: number; s: number | null }>(`SELECT COUNT(*) AS n, MAX(seen) AS s FROM devices`).one();
    if (left.n === 0 || now - (left.s ?? 0) > 30 * 86_400_000) {
      await this.forget();
      return;
    }
    // The byte count put right from the parcels themselves (a count kept by hand may drift).
    const bytes = this.sql.exec<{ b: number | null }>(`SELECT SUM(length(body)) AS b FROM parcels`).one().b ?? 0;
    if (bytes !== Number(this.meta("bytes") ?? "0")) this.setMeta("bytes", String(bytes));
    await this.ctx.storage.setAlarm(now + limit(this.env.PRUNE_EVERY_MS, PRUNE_EVERY_MS));
  }

  /** Carried parcels per device in the current minute (kept in memory: a reset only lets a few more through). */
  private carried = new Map<string, { minute: number; n: number }>();

  private async wakeOthers(sender: string, now: number, live: Set<string> = new Set(), parcel?: { seq: number; body: Uint8Array }): Promise<void> {
    if (!this.env.FCM_SERVICE_ACCOUNT || !this.env.FCM_PROJECT_ID) return;
    const rows = this.sql.exec<{ device: string; fcm: string; woke: number }>(
      `SELECT device, fcm, woke FROM devices WHERE device != ? AND fcm IS NOT NULL`, sender,
    ).toArray();
    const b64 = parcel ? toBase64(parcel.body) : null;
    for (const r of rows) {
      if (live.has(r.device)) continue;
      // The parcel itself, when it fits and the device is not being sent too many this minute: every parcel goes
      // (a skipped one would wait for the next round), so the relay stays the record and FCM only the fast road.
      const minute = Math.floor(now / 60_000);
      const c = this.carried.get(r.device);
      const n = c && c.minute === minute ? c.n : 0;
      let data: Record<string, string> | null = null;
      if (parcel && b64 && b64.length <= CARRY_MAX_B64 && n < CARRY_PER_MINUTE) {
        this.carried.set(r.device, { minute, n: n + 1 });
        data = { t: "rk-parcel", s: String(parcel.seq), f: sender, b: b64 };
      } else if (now - r.woke >= WAKE_GAP_MS) {
        data = { t: "rk-wake" };
      }
      if (!data) continue;
      this.sql.exec(`UPDATE devices SET woke = ? WHERE device = ?`, now, r.device);
      const gone = await sendWake(this.env, r.fcm, data).catch(() => false);
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

/**
 * A data-only push: "rk-wake" (a parcel waits) or "rk-parcel" (the sealed parcel itself, which only the book's
 * devices can open — Google carries ciphertext); "gone" when the device's token is no longer valid. A carried
 * parcel lives a day at FCM (the relay keeps it seven), a wake-up ten minutes.
 */
async function sendWake(env: Env, fcmToken: string, data: Record<string, string> = { t: "rk-wake" }): Promise<boolean | "gone"> {
  const ttl = data.t === "rk-parcel" ? "86400s" : "600s";
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FCM_PROJECT_ID}/messages:send`, {
    method: "POST",
    headers: { authorization: "Bearer " + (await accessToken(env)), "content-type": "application/json" },
    body: JSON.stringify({ message: { token: fcmToken, data, android: { priority: "high", ttl } } }),
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
