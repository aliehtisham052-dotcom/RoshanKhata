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
  /** Bytes every book of the account may hold together (default ACCOUNT_BYTES below). */
  ACCOUNT_BYTES?: string;
  /** Waris ki Chaabi's DAY in ms (default 86,400,000): only the tests lower it (wrangler dev --var). */
  WARIS_DAY_MS?: string;
  /** Vaults the account holds at most (default MAX_VAULTS below). */
  MAX_VAULTS?: string;
  /** Bytes every vault's chunks may hold together, apart from the books' (default WARIS_BYTES below). */
  WARIS_BYTES?: string;
  /** New vaults the ledger takes in 24 hours (default NEW_VAULTS_DAY below). */
  NEW_VAULTS_DAY?: string;
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
/**
 * Bytes all books of the account may hold together (23 Oct 2026, phase 2 of the review report): the free plan's
 * SQL storage is 5 GB (4.66 GiB) for the whole account, and BOOK_BYTES alone still let anyone opening fresh books
 * fill 100 books x 16 MiB a day. Past it the "!ledger" refuses new books and every book refuses parcels, 507 "full"
 * (the hosting's word for its own store); taking and acknowledging still work, so devices empty what is there.
 * The books' bytes alone: Waris vaults have their own, WARIS_BYTES (7 Nov 2026, R1).
 */
const ACCOUNT_BYTES = 4 * 1024 * 1024 * 1024;
/**
 * A book tells the ledger its size only once it moved this much since it last did (and at its six-hourly alarm),
 * never on every parcel: one ledger call per 64 KiB, where a busy counter leaves well under 1 MB a day.
 */
const REPORT_STEP = 64 * 1024;
/** The ledger counts every book at no less than this: an empty SQLite database and its tables are tens of KB. */
const BOOK_FLOOR = 64 * 1024;
/** While the account is full, a book asks the ledger again at most this often whether there is room. */
const FULL_RECHECK_MS = 10 * 60_000;
/** Refusals of a parcel after which the round still answers the others' page (and keeps the ack). */
const CAPACITY = new Set(["day-limit", "day-bytes", "book-full", "full"]);

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
/** Waris vaults the ledger takes (each holds at most 2 x 8 MiB of chunks while a version replaces another). */
const MAX_VAULTS = 20_000;
/**
 * Bytes every vault's chunks may hold together (7 Nov 2026, R1: anyone may make a vault and fill it). Kept by the
 * "!ledger" apart from the books' bytes, so vaults never make the account "full" for a book: ACCOUNT_BYTES (4 GiB)
 * and this (512 MiB) together stay within the free plan's 5 GB (4.66 GiB). Past it: 507 "waris-full".
 */
const WARIS_BYTES = 512 * 1024 * 1024;
/** New vaults the ledger takes in 24 hours (R1), as NEW_BOOKS_DAY for books: past it, 429 "waris-day". */
const NEW_VAULTS_DAY = 100;

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
    // "push": whether this relay can ring a phone at all (its Firebase key is set) — R70: nothing said so before.
    if (url.pathname === "/v1/health") return json({ ok: true, live: env.LIVE !== "0", push: canPush(env) });
    if (env.LIVE === "0") return refuse(503, "halted");
    // Waris ki Chaabi (4 Nov 2026): one object per vault, named "w:" + vault — never a book's name (32 hex) nor
    // the "!ledger" — so no book's pruning, alarm or count ever meets a vault's rows.
    const wm = url.pathname.match(/^\/v1\/w(\/.*)?$/);
    if (wm) {
      const r = warisRoute(wm[1] ?? "", request.method);
      if (r instanceof Response) {
        await readCapped(request, 0).catch(() => null);
        return r;
      }
      // An heir's body longer than it may be is refused by its declared length before any object is reached (R2); as
      // every refusal at this door it is read through and dropped, nothing kept (cancelled, wrangler dev reset the
      // connection under the answer).
      if (r.route === "heir-put" && Number(request.headers.get("content-length") ?? "0") > WARIS_HEIR_BODY) {
        await readCapped(request, 0).catch(() => null);
        return wNo(400, "bad");
      }
      return env.BOOKS.get(env.BOOKS.idFromName("w:" + r.vault)).fetch(request);
    }
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
    let forward: Request = request;
    if (action === "hello") {
      // One box for the whole Worker counts the books and their bytes: a stranger opening boxes at will meets a
      // limit before the plan's does (one extra request per hello only).
      const ledger = env.BOOKS.get(env.BOOKS.idFromName("!ledger"));
      const ok = await ledger.fetch(new Request("https://ledger/" + book, { method: "POST" }));
      if (ok.status !== 200) return ok;
      // The ledger's word on the account goes to the book with the hello, set here and only here (a device's own
      // header of that name is overwritten or dropped): a book learns at each hello whether there is room.
      const said = (await ok.json().catch(() => null)) as { full?: unknown } | null;
      const headers = new Headers(request.headers);
      if (typeof said?.full === "boolean") headers.set("x-rk-acct", said.full ? "full" : "room");
      else headers.delete("x-rk-acct");
      forward = new Request(request, { headers });
    }
    const stub = env.BOOKS.get(env.BOOKS.idFromName(book));
    return stub.fetch(forward);
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

// ---------------------------------------------------------------- Waris ki Chaabi (heir access, 4 Nov 2026)

const WARIS_CHUNK = 524_288;
const WARIS_SNAP = 8_388_608;
const WARIS_MAX_N = 16;
const WARIS_HEIRS = 5;
const WARIS_BOX = 4096;
/** An heir's JSON body at most (a box of 4,096 base64 characters and three fields), as the other relays (R2). */
const WARIS_HEIR_BODY = 16 * 1024;
/** A vault with no owner or heir call this long may go (the protocol's retention; the vault's own alarm). */
const WARIS_IDLE_MS = 1095 * 86_400_000;
/** A vault still bare (no heir, no snapshot) this many DAYs after its setup is junk and goes (7 Nov 2026, R1). */
const WARIS_JUNK_DAYS = 14;
const HEX16 = /^[0-9a-f]{16}$/;
const B64 = /^[A-Za-z0-9+/]+={0,2}$/;

/** One DAY of the wait: WARIS_DAY_MS from the Worker's vars (the tests), never from a request. */
function warisDay(env: Env): number {
  const n = Number(env.WARIS_DAY_MS);
  return env.WARIS_DAY_MS && Number.isFinite(n) && n > 0 ? Math.floor(n) : 86_400_000;
}

/** A body read to its end, at most [cap] bytes kept; null when it was longer (as the Supabase relay's). */
async function readCapped(request: Request, cap: number): Promise<Uint8Array | null> {
  const reader = request.body?.getReader();
  if (!reader) return new Uint8Array(0);
  const parts: Uint8Array[] = [];
  let n = 0;
  let over = false;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    n += value.length;
    if (n > cap) over = true;
    if (!over) parts.push(value);
  }
  if (over) return null;
  const out = new Uint8Array(n);
  let at = 0;
  for (const c of parts) { out.set(c, at); at += c.length; }
  return out;
}

function wNo(status: number, why: string, more: Record<string, unknown> = {}): Response {
  return json({ ok: false, why, ...more }, status);
}

/**
 * A Waris path (the part after /v1/w) and method: the route, its vault and heir, or the refusal — the same rule
 * at the Worker's door and inside the vault's object. Shape and method first (404/405), then the ids (400).
 */
function warisRoute(rest: string, method: string): { route: string; vault: string; hid: string | null } | Response {
  const m = rest.match(/^\/([^/]+)(?:\/(.*))?$/);
  if (!m) return refuse(404, "no-such-path");
  const vault = m[1];
  const tail = (m[2] ?? "").replace(/\/+$/, "");
  const top: Record<string, Record<string, string>> = {
    "": { DELETE: "vault-delete" }, setup: { POST: "setup" }, snap: { PUT: "snap" }, status: { GET: "status" },
  };
  let hid: string | null = null;
  let routes: Record<string, string>;
  if (tail in top) routes = top[tail];
  else {
    const hm = tail.match(/^heir\/([^/]+)(?:\/(reject|allow|ask|alive|open|snap))?$/);
    if (!hm) return refuse(404, "no-such-path");
    hid = hm[1];
    const sub = hm[2] ?? "";
    routes = sub === "" ? { PUT: "heir-put", DELETE: "heir-delete" }
      : { [sub === "open" || sub === "snap" ? "GET" : "POST"]: sub === "snap" ? "heir-snap" : sub };
  }
  const route = routes[method];
  if (!route) return refuse(405, "method");
  if (!HEX32.test(vault) || (hid != null && !HEX16.test(hid))) return wNo(400, "bad");
  return { route, vault, hid };
}

type Heir = {
  hid: string; pass: string; box: string; wait: number; last_seen: number | null; asked_at: number | null;
  rejected_at: number | null; allowed_at: number | null;
};

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
  /** When this object last asked the ledger about the account (in memory: a fresh instance asks at once). */
  private lastAsk = 0;

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

  /**
   * What this book takes of the account's storage: SQLite's own size of this object where the runtime says it
   * (tables, indexes and pages freed but kept, all of which the plan's storage holds), else its parcels' bytes.
   */
  private size(): number {
    try {
      const n = this.sql.databaseSize;
      if (typeof n === "number" && Number.isFinite(n) && n > 0) return n;
    } catch { /* an older runtime: the parcels' count below */ }
    return Number(this.meta("bytes") ?? "0");
  }

  /** The ledger's word that the account is full, kept in a row only when it changes (a write is counted). */
  private setAccountFull(full: boolean): void {
    if (full === (this.meta("acct_full") === "1")) return;
    if (full) this.setMeta("acct_full", "1");
    else this.sql.exec(`DELETE FROM meta WHERE k = 'acct_full'`);
  }

  /**
   * The book's size to the ledger, when it moved REPORT_STEP since it was last told (or at the alarm, [settle],
   * whenever it differs), and again every FULL_RECHECK_MS while the account is full — so a book learns that room
   * came back. The size sent is the whole figure, not a change: a report lost or sent twice changes nothing.
   * Nothing is written when nothing moved: a quiet round still writes no row.
   */
  private async reportIfDue(now: number, settle = false): Promise<void> {
    if (!this.haveTables(false)) return;
    const book = this.meta("book");
    if (!book) return;
    const size = this.size();
    const reported = Number(this.meta("reported") ?? "0");
    const full = this.meta("acct_full") === "1";
    const moved = Math.abs(size - reported) >= REPORT_STEP || (settle && size !== reported);
    if (!moved && !(full && now - this.lastAsk >= FULL_RECHECK_MS)) return;
    this.lastAsk = now;
    const ledger = this.env.BOOKS.get(this.env.BOOKS.idFromName("!ledger"));
    const res = await ledger.fetch(new Request(`https://ledger/size/${book}?bytes=${size}`, { method: "POST" })).catch(() => null);
    if (!res || res.status !== 200) return;
    const o = (await res.json().catch(() => null)) as { full?: unknown } | null;
    // The book may have been emptied while the ledger answered: nothing is made again here.
    if (!this.haveTables(false)) return;
    if (size !== reported) this.setMeta("reported", String(size));
    if (typeof o?.full === "boolean") this.setAccountFull(o.full);
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
    if (url.hostname === "ledger") return this.ledger(url, request.method);
    if (url.pathname.startsWith("/v1/w/")) return this.waris(request, url);
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
    const res = await this.act(action, request, url, device, now);
    // The account's count follows what this call stored or freed (a ledger call only once it moved 64 KiB).
    await this.reportIfDue(now).catch(() => null);
    return res;
  }

  private async act(action: string, request: Request, url: URL, device: string, now: number): Promise<Response> {
    switch (action) {
      case "hello": return this.hello(device, now, request.headers.get("x-rk-acct"));
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
   *
   * Since 23 Oct (phase 2) it also keeps each book's size as the book last told it (POST size/<book>?bytes=n,
   * from reportIfDue) and the account's sum of them, each book at no less than BOOK_FLOOR: past ACCOUNT_BYTES a
   * new book is refused 507 "full", and every answer says {"full": true|false} for the book to keep.
   */
  private ledger(url: URL, method: string): Response {
    if (!this.ledgerReady) {
      this.sql.exec(`CREATE TABLE IF NOT EXISTS books (book TEXT PRIMARY KEY, at INTEGER NOT NULL)`);
      this.sql.exec(`CREATE INDEX IF NOT EXISTS books_at ON books (at)`);
      this.sql.exec(`CREATE TABLE IF NOT EXISTS counts (k TEXT PRIMARY KEY, n INTEGER NOT NULL)`);
      // A ledger from before the sizes gains their column (an older book counts at the floor until it reports).
      const made = this.sql.exec<{ sql: string }>(`SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'books'`).one().sql;
      if (!/\bbytes\b/.test(made)) this.sql.exec(`ALTER TABLE books ADD COLUMN bytes INTEGER NOT NULL DEFAULT 0`);
      // Waris vaults (4 Nov 2026): a table of their own, so MAX_BOOKS and the day's new books never count them.
      // Since 7 Nov 2026 (R1) their chunk bytes are counted apart too ('wbytes', never the books' 'bytes'), the
      // day's new vaults read an index, and their total is kept in a row, as the books'.
      this.sql.exec(`CREATE TABLE IF NOT EXISTS vaults (vault TEXT PRIMARY KEY, at INTEGER NOT NULL, bytes INTEGER NOT NULL DEFAULT 0)`);
      this.sql.exec(`CREATE INDEX IF NOT EXISTS vaults_at ON vaults (at)`);
      if (this.sql.exec(`SELECT 1 FROM counts WHERE k = 'wbytes'`).toArray().length === 0) {
        // A ledger from before kept the vaults' bytes in 'bytes': both are counted again from the rows, apart.
        this.sql.exec(`DELETE FROM counts WHERE k = 'bytes'`);
        this.ledgerWarisBytes();
      }
      this.ledgerReady = true;
    }
    const path = url.pathname.slice(1);
    if (path.startsWith("vault/") || path.startsWith("vsize/")) return this.ledgerVaults(path, url, method);
    const cap = limit(this.env.ACCOUNT_BYTES, ACCOUNT_BYTES);
    if (path.startsWith("size/")) {
      const book = path.slice(5);
      const raw = Number(url.searchParams.get("bytes"));
      if (!HEX32.test(book) || !Number.isFinite(raw) || raw < 0) return refuse(400, "bad-size");
      const bytes = Math.floor(raw);
      const row = this.sql.exec<{ bytes: number }>(`SELECT bytes FROM books WHERE book = ?`, book).toArray();
      let total = this.ledgerBytes();
      // A book the ledger does not hold (forgotten while its report was on the way) is not made again by it.
      if (row.length && row[0].bytes !== bytes) {
        total += Math.max(bytes, BOOK_FLOOR) - Math.max(row[0].bytes, BOOK_FLOOR);
        this.sql.exec(`UPDATE books SET bytes = ? WHERE book = ?`, bytes, book);
        this.sql.exec(`UPDATE counts SET n = ? WHERE k = 'bytes'`, Math.max(0, total));
      }
      return json({ ok: true, full: total >= cap });
    }
    const book = path;
    const row = this.sql.exec<{ bytes: number }>(`SELECT bytes FROM books WHERE book = ?`, book).toArray();
    const known = row.length > 0;
    if (method === "DELETE") {
      if (known) {
        const total = this.ledgerBytes();
        this.sql.exec(`DELETE FROM books WHERE book = ?`, book);
        this.sql.exec(`UPDATE counts SET n = MAX(0, n - 1) WHERE k = 'books'`);
        this.sql.exec(`UPDATE counts SET n = ? WHERE k = 'bytes'`, Math.max(0, total - Math.max(row[0].bytes, BOOK_FLOOR)));
      }
      return json({ ok: true });
    }
    // A book already here may always say hello (its devices empty what it holds); it is told whether there is room.
    if (known) return json({ ok: true, full: this.ledgerBytes() >= cap });
    const now = Date.now();
    const total = this.ledgerTotal();
    if (total >= limit(this.env.MAX_BOOKS, MAX_BOOKS)) return refuse(507, "full");
    const bytes = this.ledgerBytes();
    if (bytes + BOOK_FLOOR > cap) return refuse(507, "full");
    const today = this.sql.exec<{ n: number }>(`SELECT COUNT(*) AS n FROM books WHERE at > ?`, now - 86_400_000).one().n;
    if (today >= limit(this.env.NEW_BOOKS_DAY, NEW_BOOKS_DAY)) return refuse(429, "new-books");
    this.sql.exec(`INSERT INTO books (book, at, bytes) VALUES (?, ?, 0)`, book, now);
    this.sql.exec(`UPDATE counts SET n = n + 1 WHERE k = 'books'`);
    this.sql.exec(`UPDATE counts SET n = ? WHERE k = 'bytes'`, bytes + BOOK_FLOOR);
    return json({ ok: true, full: bytes + BOOK_FLOOR >= cap });
  }

  /**
   * The ledger's vaults (Waris, 4 Nov 2026): POST vault/<v> counts a new vault, as a new book is counted: 507 "full"
   * past MAX_VAULTS, 507 "waris-full" past the vaults' own bytes, 429 "waris-day" past the day's new vaults (7 Nov
   * 2026, R1); a vault already here is never refused. DELETE vault/<v> forgets it; POST vsize/<v>?bytes=n[&grow=1]
   * keeps its chunk bytes — refused 507 "waris-full" when growing would pass WARIS_BYTES. A vault's bytes are its
   * chunks' (no floor: an empty vault is a few rows), and never the books' ACCOUNT_BYTES.
   */
  private ledgerVaults(path: string, url: URL, method: string): Response {
    const vault = path.slice(6);
    if (!HEX32.test(vault)) return refuse(400, "bad-vault");
    const cap = limit(this.env.WARIS_BYTES, WARIS_BYTES);
    const row = this.sql.exec<{ bytes: number }>(`SELECT bytes FROM vaults WHERE vault = ?`, vault).toArray();
    const total = this.ledgerWarisBytes();
    if (path.startsWith("vault/")) {
      if (method === "DELETE") {
        if (row.length) {
          this.sql.exec(`DELETE FROM vaults WHERE vault = ?`, vault);
          this.sql.exec(`UPDATE counts SET n = MAX(0, n - 1) WHERE k = 'vaults'`);
          this.sql.exec(`UPDATE counts SET n = ? WHERE k = 'wbytes'`, Math.max(0, total - row[0].bytes));
        }
        return json({ ok: true });
      }
      if (row.length) return json({ ok: true });
      if (this.ledgerVaultCount() >= limit(this.env.MAX_VAULTS, MAX_VAULTS)) return wNo(507, "full");
      if (total >= cap) return wNo(507, "waris-full");
      const now = Date.now();
      const today = this.sql.exec<{ n: number }>(`SELECT COUNT(*) AS n FROM vaults WHERE at > ?`, now - 86_400_000).one().n;
      if (today >= limit(this.env.NEW_VAULTS_DAY, NEW_VAULTS_DAY)) return wNo(429, "waris-day");
      this.sql.exec(`INSERT INTO vaults (vault, at, bytes) VALUES (?, ?, 0)`, vault, now);
      this.sql.exec(`UPDATE counts SET n = n + 1 WHERE k = 'vaults'`);
      return json({ ok: true });
    }
    const raw = Number(url.searchParams.get("bytes"));
    if (!Number.isFinite(raw) || raw < 0) return refuse(400, "bad-size");
    const bytes = Math.floor(raw);
    const old = row.length ? row[0].bytes : 0;
    const next = total - old + bytes;
    if (url.searchParams.get("grow") === "1" && bytes > old && next > cap) return wNo(507, "waris-full");
    if (!row.length) {
      this.sql.exec(`INSERT INTO vaults (vault, at, bytes) VALUES (?, ?, ?)`, vault, Date.now(), bytes);
      this.sql.exec(`UPDATE counts SET n = n + 1 WHERE k = 'vaults'`);
    } else if (old !== bytes) this.sql.exec(`UPDATE vaults SET bytes = ? WHERE vault = ?`, bytes, vault);
    if (next !== total) this.sql.exec(`UPDATE counts SET n = ? WHERE k = 'wbytes'`, Math.max(0, next));
    return json({ ok: true, full: next >= cap });
  }

  /** The books' bytes as the ledger counts them (each book at no less than BOOK_FLOOR); counted once from the rows. */
  private ledgerBytes(): number {
    const r = this.sql.exec<{ n: number }>(`SELECT n FROM counts WHERE k = 'bytes'`).toArray();
    if (r.length) return r[0].n;
    const n = this.sql.exec<{ n: number }>(`SELECT COALESCE(SUM(MAX(bytes, ?)), 0) AS n FROM books`, BOOK_FLOOR).one().n;
    this.sql.exec(`INSERT INTO counts (k, n) VALUES ('bytes', ?)`, n);
    return n;
  }

  /** Every vault's chunk bytes together, apart from the books' (R1); counted once from the rows. */
  private ledgerWarisBytes(): number {
    const r = this.sql.exec<{ n: number }>(`SELECT n FROM counts WHERE k = 'wbytes'`).toArray();
    if (r.length) return r[0].n;
    const n = this.sql.exec<{ n: number }>(`SELECT COALESCE(SUM(bytes), 0) AS n FROM vaults`).one().n;
    this.sql.exec(`INSERT INTO counts (k, n) VALUES ('wbytes', ?)`, n);
    return n;
  }

  /** The vaults the ledger holds, kept in one row as the books' total; counted once from the rows. */
  private ledgerVaultCount(): number {
    const r = this.sql.exec<{ n: number }>(`SELECT n FROM counts WHERE k = 'vaults'`).toArray();
    if (r.length) return r[0].n;
    const n = this.sql.exec<{ n: number }>(`SELECT COUNT(*) AS n FROM vaults`).one().n;
    this.sql.exec(`INSERT INTO counts (k, n) VALUES ('vaults', ?)`, n);
    return n;
  }

  /** The ledger's total; counted once from the rows for a ledger kept before the total was. */
  private ledgerTotal(): number {
    const r = this.sql.exec<{ n: number }>(`SELECT n FROM counts WHERE k = 'books'`).toArray();
    if (r.length) return r[0].n;
    const n = this.sql.exec<{ n: number }>(`SELECT COUNT(*) AS n FROM books`).one().n;
    this.sql.exec(`INSERT INTO counts (k, n) VALUES ('books', ?)`, n);
    return n;
  }

  private hello(device: string, now: number, acct: string | null): Response {
    // The ledger's word on the account, which the Worker put on this hello (fetch above).
    if (acct === "full") this.setAccountFull(true);
    else if (acct === "room") this.setAccountFull(false);
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
    // The account's storage is full (the ledger said so at this book's last report or hello): no new bytes.
    if (this.meta("acct_full") === "1") return { status: 507, why: "full" };
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
    await this.reportIfDue(now).catch(() => null);
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
    // "wake": the address is kept; "push": this relay can send to it (without its Firebase key it keeps it and sends
    // nothing, and the phone was told "done" — R70).
    return json({ ok: true, wake: t != null, push: canPush(this.env) });
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
    if (this.isVault()) return this.vaultAlarm();
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
    // And the ledger's figure for this book put right too, whenever it differs.
    await this.reportIfDue(now, true).catch(() => null);
    await this.ctx.storage.setAlarm(now + limit(this.env.PRUNE_EVERY_MS, PRUNE_EVERY_MS));
  }

  // -------------------------------------------------------------- Waris ki Chaabi: this object as a vault
  //
  // A vault object is named "w:" + vault and holds only w_meta, w_heirs and w_chunks (never meta/devices/parcels),
  // so a book's checks (haveTables) refuse it and a book's prune, alarm and forget never run on it; its alarm is
  // its own (vaultAlarm). The ledger keeps vaults in a table of their own: never counted among MAX_BOOKS or the
  // day's new books, and since 7 Nov 2026 (R1) their chunk bytes are their own too (WARIS_BYTES), never the books'
  // ACCOUNT_BYTES: a vault can never make the account "full" for a book's parcel.

  private vault = false;

  /** Whether this object is a Waris vault (its tables exist). */
  private isVault(): boolean {
    if (this.vault) return true;
    this.vault = this.sql.exec(`SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'w_meta'`).toArray().length > 0;
    return this.vault;
  }

  private makeVault(): void {
    this.sql.exec(`CREATE TABLE IF NOT EXISTS w_meta (k TEXT PRIMARY KEY, v TEXT NOT NULL)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS w_heirs (hid TEXT PRIMARY KEY, pass TEXT NOT NULL, box TEXT NOT NULL,
      wait INTEGER NOT NULL, last_seen INTEGER, asked_at INTEGER, rejected_at INTEGER, allowed_at INTEGER)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS w_chunks (v INTEGER NOT NULL, i INTEGER NOT NULL, n INTEGER NOT NULL,
      body BLOB NOT NULL, PRIMARY KEY (v, i))`);
    this.vault = true;
  }

  private wMeta(k: string): string | null {
    const r = this.sql.exec<{ v: string }>(`SELECT v FROM w_meta WHERE k = ?`, k).toArray();
    return r.length ? r[0].v : null;
  }

  private setWMeta(k: string, v: string | number): void {
    this.sql.exec(`INSERT OR REPLACE INTO w_meta (k, v) VALUES (?, ?)`, k, String(v));
  }

  private wSnap(): { v: number; n: number; bytes: number; at: number } | null {
    const v = this.wMeta("snap_v");
    if (v == null) return null;
    return { v: Number(v), n: Number(this.wMeta("snap_n")), bytes: Number(this.wMeta("snap_bytes")), at: Number(this.wMeta("snap_at")) };
  }

  private wHeld(): number {
    return this.sql.exec<{ b: number | null }>(`SELECT SUM(length(body)) AS b FROM w_chunks`).one().b ?? 0;
  }

  /** No heir and no snapshot (an unfinished version is none): all a junk vault holds. */
  private bare(): boolean {
    return this.wMeta("snap_v") == null && this.sql.exec(`SELECT 1 FROM w_heirs LIMIT 1`).toArray().length === 0;
  }

  /** When a vault still bare becomes junk: WARIS_JUNK_DAYS DAYs after its setup (R1). */
  private junkAt(): number {
    return Number(this.wMeta("created") ?? "0") + WARIS_JUNK_DAYS * warisDay(this.env);
  }

  /** Tells the ledger this vault's bytes ([grow]: refused 507 when they would pass the vaults' own WARIS_BYTES). */
  private async ledgerVault(vault: string, bytes: number, grow: boolean): Promise<Response | null> {
    const ledger = this.env.BOOKS.get(this.env.BOOKS.idFromName("!ledger"));
    return ledger.fetch(new Request(`https://ledger/vsize/${vault}?bytes=${bytes}${grow ? "&grow=1" : ""}`, { method: "POST" }))
      .catch(() => null);
  }

  /** The vault and every byte of it go; the ledger forgets it. */
  private async dropVault(vault: string): Promise<void> {
    const ledger = this.env.BOOKS.get(this.env.BOOKS.idFromName("!ledger"));
    await ledger.fetch(new Request("https://ledger/vault/" + vault, { method: "DELETE" })).catch(() => null);
    await this.ctx.storage.deleteAlarm();
    await this.ctx.storage.deleteAll();
    this.vault = false;
  }

  /**
   * The vault's own alarm: gone after WARIS_IDLE_MS without an owner or heir call, or when it is still bare
   * WARIS_JUNK_DAYS DAYs after its setup — junk (7 Nov 2026, R1); else asked again later.
   */
  private async vaultAlarm(): Promise<void> {
    const now = Date.now();
    const last = Number(this.wMeta("last_any") ?? "0");
    const vault = this.wMeta("vault");
    if (vault && now - last > WARIS_IDLE_MS) { await this.dropVault(vault); return; }
    if (vault && this.bare()) {
      if (now >= this.junkAt()) { await this.dropVault(vault); return; }
      await this.ctx.storage.setAlarm(this.junkAt() + 1);
      return;
    }
    await this.ctx.storage.setAlarm(Math.max(now + 86_400_000, last + WARIS_IDLE_MS + 1));
  }

  private async waris(request: Request, url: URL): Promise<Response> {
    const r = warisRoute(url.pathname.slice("/v1/w".length), request.method);
    if (r instanceof Response) return r;
    const { route, vault, hid } = r;
    const day = warisDay(this.env);
    const heirRoute = route === "ask" || route === "alive" || route === "open" || route === "heir-snap";

    // A chunk's body is read before anything is written (and before any await that could let another call in).
    let chunk: Uint8Array | null = null;
    let o: Record<string, unknown> | null = null;
    let sv = 0, si = 0, sn = 0;
    if (route === "snap") {
      // Read to its end first, keeping at most a chunk and one byte: a refusal given before the body was read
      // leaves the runtime reading a stream whose answer has gone (wrangler dev then spoils the next request).
      const got = await readCapped(request, WARIS_CHUNK);
      const q = (k: string) => { const v = url.searchParams.get(k); return v != null && /^[0-9]{1,12}$/.test(v) ? Number(v) : null; };
      const v = q("v"), i = q("i"), n = q("n");
      if (v == null || i == null || n == null || v < 1 || n < 1 || n > WARIS_MAX_N || i >= n) return wNo(400, "bad");
      sv = v; si = i; sn = n;
      if (got == null) return wNo(413, "too-big");
      if (got.length === 0) return wNo(400, "bad");
      chunk = got;
    } else if (route === "heir-put") {
      // At most WARIS_HEIR_BODY kept, however long the body runs (R2: it was read whole before the pass was looked at).
      const got = await readCapped(request, WARIS_HEIR_BODY).catch(() => null);
      if (got == null) return wNo(400, "bad");
      try { const x = JSON.parse(new TextDecoder().decode(got)); o = x && typeof x === "object" ? x : null; } catch { o = null; }
    } else {
      await readCapped(request, 0).catch(() => null);
    }
    // The pass after the body (a refusal never leaves a body unread).
    const token = passOf(request);
    if (!HEX64.test(token)) return wNo(403, "pass");
    const hash = await sha256Hex(token);

    const now = Date.now();
    if (route === "setup") {
      if (!this.isVault()) {
        // A new vault: the ledger counts it (and refuses one past MAX_VAULTS, past the vaults' own bytes or past the
        // day's new vaults); a vault already here is set up again whatever the day's count.
        const ledger = this.env.BOOKS.get(this.env.BOOKS.idFromName("!ledger"));
        const ok = await ledger.fetch(new Request("https://ledger/vault/" + vault, { method: "POST" }));
        if (ok.status !== 200) return ok;
        if (!this.isVault()) {
          this.makeVault();
          this.setWMeta("vault", vault);
          this.setWMeta("pass", hash);
          this.setWMeta("created", now);
          this.setWMeta("last_owner", now);
          this.setWMeta("last_any", now);
          // The first look comes WARIS_JUNK_DAYS DAYs on: a vault still bare then is junk (vaultAlarm).
          await this.ctx.storage.setAlarm(now + WARIS_JUNK_DAYS * day + 1);
          return json({ ok: true, created: true });
        }
      }
      const known = this.wMeta("pass");
      if (known == null || !sameHex(known, hash)) return wNo(403, "pass");
      this.setWMeta("last_owner", now);
      this.setWMeta("last_any", now);
      return json({ ok: true, created: false });
    }
    if (!this.isVault()) return wNo(404, "unknown");
    const owner = this.wMeta("pass");
    if (owner == null) return wNo(404, "unknown");
    let heir: Heir | null = null;
    if (hid != null) heir = this.sql.exec<Heir>(`SELECT * FROM w_heirs WHERE hid = ?`, hid).toArray()[0] ?? null;
    if (heirRoute) {
      if (!heir) return wNo(404, "unknown");
      if (!sameHex(heir.pass, hash)) return wNo(403, "pass");
      this.sql.exec(`UPDATE w_heirs SET last_seen = ? WHERE hid = ?`, now, hid);
      heir.last_seen = now;
    } else {
      if (!sameHex(owner, hash)) return wNo(403, "pass");
      if (hid != null && !heir && route !== "heir-put") return wNo(404, "unknown");
      this.setWMeta("last_owner", now);
    }
    this.setWMeta("last_any", now);
    const openAt = (h: Heir): number | null =>
      h.allowed_at != null ? h.allowed_at : h.asked_at != null ? h.asked_at + h.wait * day : null;
    const isOpen = (h: Heir): boolean => h.allowed_at != null || (h.asked_at != null && now >= h.asked_at + h.wait * day);

    switch (route) {
      case "snap": {
        const body = chunk!;
        const snap = this.wSnap();
        const curV = snap ? snap.v : 0;
        if (sv < curV) return wNo(409, "old");
        if (snap && sv === curV && sn !== snap.n) return wNo(400, "bad");
        const mine = this.sql.exec<{ b: number | null }>(
          `SELECT SUM(length(body)) AS b FROM w_chunks WHERE v = ? AND i != ? AND n = ?`, sv, si, sn).one().b ?? 0;
        if (mine + body.length > WARIS_SNAP) return wNo(413, "too-big");
        // What the vault would hold once this chunk is in (other unfinished versions abandoned): the vaults' own cap.
        const keep = this.sql.exec<{ b: number | null }>(
          `SELECT SUM(length(body)) AS b FROM w_chunks WHERE (v = ? OR (v = ? AND n = ? AND i != ?))`, curV, sv, sn, si).one().b ?? 0;
        const said = await this.ledgerVault(vault, keep + body.length, true);
        if (said && said.status === 507) return wNo(507, "waris-full");
        // Another call may have come in during the ledger's answer: the version is checked again.
        const now2 = this.wSnap();
        if (now2 && sv < now2.v) return wNo(409, "old");
        const cur2 = now2 ? now2.v : 0;
        this.sql.exec(`DELETE FROM w_chunks WHERE v != ? AND v != ?`, sv, cur2);
        this.sql.exec(`DELETE FROM w_chunks WHERE v = ? AND n != ?`, sv, sn);
        this.sql.exec(`INSERT OR REPLACE INTO w_chunks (v, i, n, body) VALUES (?, ?, ?, ?)`, sv, si, sn, body);
        const have = this.sql.exec<{ c: number }>(`SELECT COUNT(*) AS c FROM w_chunks WHERE v = ?`, sv).one().c;
        const complete = have === sn;
        if (complete) {
          const total = this.sql.exec<{ b: number }>(`SELECT SUM(length(body)) AS b FROM w_chunks WHERE v = ?`, sv).one().b;
          this.sql.exec(`DELETE FROM w_chunks WHERE v != ?`, sv);
          this.setWMeta("snap_v", sv);
          this.setWMeta("snap_n", sn);
          this.setWMeta("snap_bytes", total);
          this.setWMeta("snap_at", now);
        }
        // The ledger's figure put right to what is held now (a chunk replaced or versions dropped shrink it).
        await this.ledgerVault(vault, this.wHeld(), false);
        return json({ ok: true, complete });
      }
      case "heir-put": {
        const ph = o?.passHash, box = o?.box, wait = o?.wait;
        if (typeof ph !== "string" || !HEX64.test(ph) || typeof box !== "string" || box.length === 0
            || box.length > WARIS_BOX || !B64.test(box) || typeof wait !== "number" || !Number.isInteger(wait)
            || wait < 1 || wait > 90) return wNo(400, "bad");
        if (!heir) {
          const c = this.sql.exec<{ c: number }>(`SELECT COUNT(*) AS c FROM w_heirs`).one().c;
          if (c >= WARIS_HEIRS) return wNo(409, "full");
          this.sql.exec(`INSERT INTO w_heirs (hid, pass, box, wait) VALUES (?, ?, ?, ?)`, hid, ph, box, wait);
        } else if (sameHex(heir.pass, ph)) {
          this.sql.exec(`UPDATE w_heirs SET box = ?, wait = ? WHERE hid = ?`, box, wait, hid);
        } else {
          this.sql.exec(`UPDATE w_heirs SET pass = ?, box = ?, wait = ?, last_seen = NULL, asked_at = NULL,
            rejected_at = NULL, allowed_at = NULL WHERE hid = ?`, ph, box, wait, hid);
        }
        return json({ ok: true });
      }
      case "heir-delete":
        this.sql.exec(`DELETE FROM w_heirs WHERE hid = ?`, hid);
        // Its last heir gone and no snapshot: bare again, so the junk look comes WARIS_JUNK_DAYS DAYs after its setup.
        if (this.bare()) await this.ctx.storage.setAlarm(Math.max(now, this.junkAt()) + 1);
        return json({ ok: true });
      case "status": {
        const hs = this.sql.exec<Heir>(`SELECT * FROM w_heirs ORDER BY hid`).toArray();
        return json({
          ok: true, now, snap: this.wSnap(),
          heirs: hs.map((h) => ({
            hid: h.hid, wait: h.wait, lastSeen: h.last_seen, askedAt: h.asked_at, rejectedAt: h.rejected_at,
            allowedAt: h.allowed_at, openAt: openAt(h),
          })),
        });
      }
      case "reject":
        this.sql.exec(`UPDATE w_heirs SET asked_at = NULL, allowed_at = NULL, rejected_at = ? WHERE hid = ?`, now, hid);
        return json({ ok: true });
      case "allow":
        this.sql.exec(`UPDATE w_heirs SET allowed_at = ?, asked_at = COALESCE(asked_at, ?) WHERE hid = ?`, now, now, hid);
        return json({ ok: true });
      case "ask": {
        const h = heir!;
        if (h.asked_at == null) {
          if (h.rejected_at != null && now - h.rejected_at < day) return wNo(429, "wait", { retryAt: h.rejected_at + day });
          this.sql.exec(`UPDATE w_heirs SET asked_at = ? WHERE hid = ?`, now, hid);
          h.asked_at = now;
        }
        return json({ ok: true, askedAt: h.asked_at, openAt: openAt(h) });
      }
      case "alive": {
        const h = heir!;
        return json({ ok: true, askedAt: h.asked_at, openAt: openAt(h), rejectedAt: h.rejected_at, open: isOpen(h) });
      }
      case "open": {
        const h = heir!;
        if (!isOpen(h)) return wNo(403, "waiting", { openAt: openAt(h) });
        return json({ ok: true, box: h.box, snap: this.wSnap() });
      }
      case "heir-snap": {
        const h = heir!;
        if (!isOpen(h)) return wNo(403, "waiting", { openAt: openAt(h) });
        const q = (k: string) => { const v = url.searchParams.get(k); return v != null && /^[0-9]{1,12}$/.test(v) ? Number(v) : null; };
        const v = q("v"), i = q("i");
        if (v == null || i == null) return wNo(400, "bad");
        const snap = this.wSnap();
        if (!snap || snap.v !== v) return wNo(404, "absent");
        const rows = this.sql.exec<{ body: ArrayBuffer }>(`SELECT body FROM w_chunks WHERE v = ? AND i = ?`, v, i).toArray();
        if (rows.length === 0) return wNo(404, "absent");
        return new Response(rows[0].body, {
          status: 200, headers: { "content-type": "application/octet-stream", "cache-control": "no-store" },
        });
      }
      case "vault-delete":
        await this.dropVault(vault);
        return json({ ok: true });
    }
    return refuse(404, "no-such-path");
  }

  /** Carried parcels per device in the current minute (kept in memory: a reset only lets a few more through). */
  private carried = new Map<string, { minute: number; n: number }>();

  private async wakeOthers(sender: string, now: number, live: Set<string> = new Set(), parcel?: { seq: number; body: Uint8Array }): Promise<void> {
    if (!canPush(this.env)) {
      // Said where `wrangler tail` shows it: a deploy without the key passes every other check.
      console.warn("no FCM key: wake-ups and carried parcels are off");
      return;
    }
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
/** Whether this relay can send a push: both Firebase settings are there (R70). */
function canPush(env: Env): boolean {
  return !!(env.FCM_SERVICE_ACCOUNT && env.FCM_PROJECT_ID);
}

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
