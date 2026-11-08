<?php
/**
 * Roshan Khata's relay on the owner's own hosting (Hostinger, PHP + SQLite), 22 Oct 2026.
 *
 * The third road for live sync, after the shop's WiFi and the Cloudflare relay
 * (cloud/worker) and beside the Supabase one (supabase/functions/daakiya): the
 * SAME conversation the Cloudflare Worker holds, path for path and answer for
 * answer, so the app speaks to all of them with one piece of code (LiveCloud).
 *
 * What it keeps: parcels already sealed on the device with the book's own key
 * (AES-256-GCM). It holds no key and can open nothing; it sees a random book
 * id, random device ids, sizes and times. One SQLite file per book, outside
 * the web root (rk-relay-data beside public_html), like one Durable Object per
 * book on Cloudflare. A parcel goes once every other device of the book has
 * taken it, or after KEEP_DAYS; a book nobody touched for 30 days goes.
 *
 * Who may use a book: the first hello sets the book's pass (SHA-256 of the token
 * each device makes from the book key); every later call must bring the same.
 *
 * Paths (all under the folder this file is in):
 *   GET  v1/health
 *   POST v1/b/<book>/hello | send | ack | wake | leave
 *   GET  v1/b/<book>/take?after=<seq>
 * The token comes as "Authorization: RK <hex>" and, because some hostings drop
 * that header before PHP sees it, also as "X-RK-Pass: <hex>".
 *
 * The owner's switch: an empty file named "halt" in the store answers 503 to
 * every call (the app's books are untouched).
 */

ini_set('display_errors', '0');
ini_set('log_errors', '1');
error_reporting(E_ALL);

/** A limit, from the environment where the server sets one (the tests do), else its default. */
function limit($name, $default) {
    $v = getenv('RK_RELAY_' . $name);
    return ($v !== false && ctype_digit($v)) ? (int)$v : $default;
}

define('MAX_PARCEL', 1048576);                         // bytes of one parcel (as cloud/worker)
define('TAKE_PAGE', 40);                               // parcels in one answer at most
define('TAKE_BYTES', limit('TAKE_BYTES', 4194304));    // base64 in one answer (a shared host's memory)
define('KEEP_DAYS', limit('KEEP_DAYS', 7));
define('DAY_LIMIT', limit('DAY_LIMIT', 3000));         // parcels one book may leave in a UTC day
define('MAX_DEVICES', 8);
define('MAX_BOOKS', limit('MAX_BOOKS', 2000));         // books on this server
define('IDLE_DAYS', 30);                               // a book nobody touched this long goes
define('MIN_FREE', limit('MIN_FREE', 200 * 1048576));  // free bytes kept: below them nothing new is written
define('NEW_BOOKS_DAY', limit('NEW_BOOKS_DAY', 300));  // new books taken in a UTC day, all together
define('SEEN_EVERY_MS', 3600000);                      // a quiet call writes "seen" at most this often
// Bytes (23 Oct 2026, the review): counts alone let one book store 3,000 MB a day, and the hosting
// account's quota is not the disk's free space (MIN_FREE). Every limit may be lowered by the environment.
define('BOOK_BYTES', limit('BOOK_BYTES', 16 * 1048576));        // bytes one book's parcels may hold
define('DAY_BYTES', limit('DAY_BYTES', 32 * 1048576));          // bytes one book may leave in a UTC day
define('MAX_STORE_BYTES', limit('MAX_STORE_BYTES', 1073741824)); // all books' files together
// A book no second device has joined (8 Nov 2026 review, C1: anyone may make books, and a stranger's books of
// BOOK_BYTES each filled the store for every shop). Until a second device says hello a book may hold only
// YOUNG_BOOK_BYTES, is swept YOUNG_DAYS after it was made, and is refused parcels - as a NEW book is refused -
// once the store holds half of MAX_STORE_BYTES (the answers stay 'full' and 'book-full': every client knows them).
define('YOUNG_BOOK_BYTES', limit('YOUNG_BOOK_BYTES', 262144));
define('YOUNG_DAYS', limit('YOUNG_DAYS', 3));
define('DEVICE_IDLE_MS', limit('DEVICE_IDLE_MS', 30 * 86400000)); // a device not seen this long goes
define('SWEEP_EVERY', limit('SWEEP_EVERY', 6 * 3600));          // seconds between idle-book sweeps (the tests: 0)
// Waris ki Chaabi (heir access, 4 Nov 2026): one SQLite per vault in $store/waris/, never touched by the books'
// sweeps (they read only $store/*.sqlite). Since 7 Nov 2026 (R1) the vaults' bytes are their own, WARIS_BYTES, never
// counted in MAX_STORE_BYTES: anyone may make a vault, and the books' parcels must never be refused for them.
define('WARIS_BYTES', limit('WARIS_BYTES', 256 * 1048576));      // every vault's files together
define('NEW_VAULTS_DAY', limit('NEW_VAULTS_DAY', 20));           // new vaults taken in 24 hours, all together
define('WARIS_JUNK_DAYS', 14);                                   // a vault still bare (no heir, no snapshot) this many DAYs after its setup goes
define('WARIS_DAY_MS', waris_day());                             // one DAY; only the server's environment changes it
define('WARIS_CHUNK', 524288);                                   // bytes of one snapshot chunk
define('WARIS_SNAP', 8388608);                                   // bytes of one snapshot version, all chunks
define('WARIS_MAX_N', 16);                                       // chunks of one version
define('WARIS_HEIRS', 5);                                        // heirs of one vault
define('WARIS_BOX', 4096);                                       // base64 characters of one heir's key box
define('WARIS_IDLE_DAYS', 1095);                                 // a vault nobody called this long may go
define('MAX_VAULTS', limit('MAX_VAULTS', 2000));                 // vaults on this server

/** DAY for the Waris wait: WARIS_DAY_MS from the server's environment (the tests), else 86,400,000. */
function waris_day() {
    foreach (['WARIS_DAY_MS', 'RK_RELAY_WARIS_DAY_MS'] as $n) {
        $v = getenv($n);
        if ($v !== false && ctype_digit($v) && (int)$v > 0) return (int)$v;
    }
    return 86400000;
}

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');

function answer($code, $body) {
    http_response_code($code);
    echo json_encode($body, JSON_UNESCAPED_SLASHES);
    exit;
}
function refuse($code, $why) { answer($code, ['ok' => false, 'why' => $why]); }

/** Beside the web root, never served, when the hosting lets us make it; else data/ here (denied by .htaccess). */
function store_dir() {
    $env = getenv('RK_RELAY_DATA');
    if ($env !== false && $env !== '') return rtrim($env, '/');
    $root = isset($_SERVER['DOCUMENT_ROOT']) ? realpath($_SERVER['DOCUMENT_ROOT']) : false;
    if ($root) {
        $outside = dirname($root) . '/rk-relay-data';
        if (is_dir($outside) || @mkdir($outside, 0700, true)) return $outside;
    }
    $inside = __DIR__ . '/data';
    if (!is_dir($inside)) @mkdir($inside, 0700, true);
    return $inside;
}

function header_value($name) {
    $key = 'HTTP_' . strtoupper(str_replace('-', '_', $name));
    if (isset($_SERVER[$key])) return (string)$_SERVER[$key];
    if (isset($_SERVER['REDIRECT_' . $key])) return (string)$_SERVER['REDIRECT_' . $key];
    return '';
}

/** The book's SQLite, made only when $create (a first hello). */
function open_book($store, $book, $create) {
    $file = $store . '/' . $book . '.sqlite';
    if (!$create && !file_exists($file)) return null;
    $db = new PDO('sqlite:' . $file, null, null, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_TIMEOUT => 10,
    ]);
    $db->exec('PRAGMA journal_mode = WAL');
    $db->exec('PRAGMA synchronous = NORMAL');
    $db->exec('CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v TEXT NOT NULL)');
    $db->exec('CREATE TABLE IF NOT EXISTS devices (
        device TEXT PRIMARY KEY, acked INTEGER NOT NULL DEFAULT 0, seen INTEGER NOT NULL,
        fcm TEXT, woke INTEGER NOT NULL DEFAULT 0)');
    $db->exec('CREATE TABLE IF NOT EXISTS parcels (
        seq INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT NOT NULL, at INTEGER NOT NULL, body BLOB NOT NULL)');
    return $db;
}

function meta($db, $k) {
    $s = $db->prepare('SELECT v FROM meta WHERE k = ?');
    $s->execute([$k]);
    $v = $s->fetchColumn();
    return $v === false ? null : $v;
}
function set_meta($db, $k, $v) {
    $db->prepare('INSERT OR REPLACE INTO meta (k, v) VALUES (?, ?)')->execute([$k, (string)$v]);
}
function one($db, $sql, $args = []) {
    $s = $db->prepare($sql);
    $s->execute($args);
    return $s->fetchColumn();
}

/** Parcels every other device has taken are gone (as cloud/worker's pruneTaken). */
function prune_taken($db) {
    if ((int)one($db, 'SELECT COUNT(*) FROM devices') === 0) return;
    $db->exec('DELETE FROM parcels WHERE NOT EXISTS (
        SELECT 1 FROM devices d WHERE d.device != parcels.sender AND d.acked < parcels.seq)');
}

/**
 * Parcels older than KEEP_DAYS go, and the line past which they went is kept (the take answer's "gap").
 * A device other than the caller not seen for DEVICE_IDLE_MS goes with its parcels (23 Oct 2026, the review:
 * a lost phone kept every parcel of the book for the full week and a place among the eight for ever).
 */
function prune_old($db, $now, $device) {
    $cut = one($db, 'SELECT MAX(seq) FROM parcels WHERE at < ?', [$now - KEEP_DAYS * 86400000]);
    if ($cut !== null && $cut !== false) {
        $db->prepare('DELETE FROM parcels WHERE seq <= ?')->execute([(int)$cut]);
        if ((int)$cut > (int)(meta($db, 'pruned_past') ?? 0)) set_meta($db, 'pruned_past', (int)$cut);
    }
    $s = $db->prepare('SELECT device FROM devices WHERE seen < ? AND device != ?');
    $s->execute([$now - DEVICE_IDLE_MS, $device]);
    $gone = $s->fetchAll(PDO::FETCH_COLUMN);
    foreach ($gone as $g) {
        $db->prepare('DELETE FROM devices WHERE device = ?')->execute([$g]);
        $db->prepare('DELETE FROM parcels WHERE sender = ?')->execute([$g]);
    }
    if ($gone) prune_taken($db);
}

/** A quiet call writes "seen" at most once an hour; one that did something, always. */
function touch_seen($db, $device, $now, $always) {
    if ($always) $db->prepare('UPDATE devices SET seen = ? WHERE device = ?')->execute([$now, $device]);
    else $db->prepare('UPDATE devices SET seen = ? WHERE device = ? AND seen < ?')->execute([$now, $device, $now - SEEN_EVERY_MS]);
}

/** Refusals of a parcel after which a round still answers the others' page (and keeps its ack). */
function capacity($why) { return in_array($why, ['day-limit', 'day-bytes', 'book-full', 'full'], true); }

/**
 * Bytes of every book's files together, kept in $store/.size-books and counted again from the files at most every
 * ten minutes (the files are what the hosting account's quota measures). The books' alone: the heirs' vaults
 * are counted apart (waris_bytes), so a vault never refuses a book's parcel (7 Nov 2026, R1). The file's name is
 * new with that: the old .size, which held the vaults too, is never read again.
 */
function store_bytes($store, $fresh = false) {
    return kept_bytes($store . '/.size-books', $store . '/*.sqlite*', $fresh);
}

/** Bytes of every Waris vault's files together (waris/), kept in waris/.size the same way: WARIS_BYTES' count. */
function waris_bytes($store, $fresh = false) {
    if (!is_dir($store . '/waris')) @mkdir($store . '/waris', 0700, true);
    return kept_bytes($store . '/waris/.size', $store . '/waris/*.sqlite*', $fresh);
}

/** Bytes of the files matching $pattern, kept in $file and counted again from them at most every ten minutes. */
function kept_bytes($file, $pattern, $fresh) {
    $m = @filemtime($file);
    if (!$fresh && $m !== false && time() - $m < 600) {
        $v = trim((string)@file_get_contents($file));
        if (ctype_digit($v)) return (int)$v;
    }
    $n = 0;
    foreach (glob($pattern) ?: [] as $x) {
        $z = @filesize($x); if ($z !== false) $n += $z;
    }
    @file_put_contents($file, (string)$n, LOCK_EX);
    return $n;
}

/** A parcel written: the books' kept total grows with it until the next count from the files. */
function store_grew($store, $len) { kept_grew($store . '/.size-books', $len); }

/** A vault's chunk written: the vaults' kept total grows with it until the next count from the files. */
function waris_grew($store, $len) { kept_grew($store . '/waris/.size', $len); }

function kept_grew($file, $len) {
    $h = @fopen($file, 'c+');
    if ($h === false) return;
    if (flock($h, LOCK_EX)) {
        $v = trim((string)stream_get_contents($h));
        $n = (ctype_digit($v) ? (int)$v : 0) + $len;
        ftruncate($h, 0); rewind($h); fwrite($h, (string)$n);
        flock($h, LOCK_UN);
    }
    fclose($h);
}

/** One parcel in: its number, or a refusal [code, why]. */
function put_parcel($db, $store, $device, $now, $body) {
    if ($body === '' || $body === false) return [400, 'empty'];
    $len = strlen($body);
    if ($len > MAX_PARCEL) return [413, 'too-big'];
    $free = @disk_free_space($store);
    if ($free !== false && $free < MIN_FREE) return [507, 'full'];
    if (store_bytes($store) + $len > MAX_STORE_BYTES) return [507, 'full'];
    $young = (int)one($db, 'SELECT COUNT(*) FROM devices') < 2;
    if ($young && store_bytes($store) + $len > intdiv(MAX_STORE_BYTES, 2)) return [507, 'full'];
    $key = 'count:' . gmdate('Y-m-d', intdiv($now, 1000));
    // "<parcels>/<bytes>" for the day (a value from before 23 Oct holds the parcels alone).
    $parts = explode('/', (string)(meta($db, $key) ?? '0'));
    $count = (int)$parts[0];
    $dayBytes = isset($parts[1]) ? (int)$parts[1] : 0;
    if ($count >= DAY_LIMIT) return [429, 'day-limit'];
    if ($dayBytes + $len > DAY_BYTES) return [429, 'day-bytes'];
    $stored = (int)one($db, 'SELECT COALESCE(SUM(length(body)), 0) FROM parcels');
    if ($stored + $len > ($young ? YOUNG_BOOK_BYTES : BOOK_BYTES)) return [507, 'book-full'];
    $db->prepare("DELETE FROM meta WHERE k LIKE 'count:%' AND k != ?")->execute([$key]);
    set_meta($db, $key, ($count + 1) . '/' . ($dayBytes + $len));
    $s = $db->prepare('INSERT INTO parcels (sender, at, body) VALUES (?, ?, ?)');
    $s->bindValue(1, $device);
    $s->bindValue(2, $now, PDO::PARAM_INT);
    $s->bindValue(3, $body, PDO::PARAM_LOB);
    $s->execute();
    store_grew($store, $len);
    touch_seen($db, $device, $now, true);
    return (int)$db->lastInsertId();
}

/** The others' parcels after $after (read inside the transaction), as the answer's fields. */
function take_page($db, $device, $now, $after) {
    $s = $db->prepare('SELECT seq, sender, at, body FROM parcels WHERE seq > ? AND sender != ? ORDER BY seq LIMIT ?');
    $s->execute([$after, $device, TAKE_PAGE + 1]);
    $rows = $s->fetchAll(PDO::FETCH_ASSOC);
    touch_seen($db, $device, $now, count($rows) > 0);
    $prunedPast = (int)(meta($db, 'pruned_past') ?? 0);
    $page = [];
    $bytes = 0;
    foreach (array_slice($rows, 0, TAKE_PAGE) as $r) {
        $body = is_resource($r['body']) ? stream_get_contents($r['body']) : $r['body'];
        $b64 = base64_encode($body);
        if (count($page) > 0 && $bytes + strlen($b64) > TAKE_BYTES) break;
        $page[] = ['seq' => (int)$r['seq'], 'from' => $r['sender'], 'at' => (int)$r['at'], 'body' => $b64];
        $bytes += strlen($b64);
    }
    $more = count($rows) > count($page);
    $out = ['parcels' => $page, 'more' => $more, 'gap' => $after < $prunedPast];
    // The whole page went: every other device's parcel up to the book's last number is in it, so the device may
    // read on from there instead of passing its own parcels again each round (23 Oct 2026).
    if (!$more) $out['through'] = max($after, (int)(one($db, 'SELECT MAX(seq) FROM parcels') ?: 0));
    return $out;
}

/**
 * Books nobody touched for IDLE_DAYS are let go: at most once every SWEEP_EVERY (the hourly health call
 * brings it), or at once when $force (a new book would be refused as full). 23 Oct 2026, the review: one
 * hello in a hundred swept, and clients say hello once per book, so dead books were never let go.
 */
function sweep_idle($store, $now, $force = false) {
    $mark = $store . '/.swept';
    $m = @filemtime($mark);
    // A forced sweep (a new book met a full store) runs at most once a minute: it opens every book (C1).
    if ($m !== false && time() - $m < ($force ? max(60, SWEEP_EVERY) : SWEEP_EVERY)) return;
    @touch($mark);
    foreach (glob($store . '/*.sqlite') ?: [] as $f) {
        $m = @filemtime($f);
        if ($m !== false && ($now / 1000) - $m > IDLE_DAYS * 86400) {
            @unlink($f); @unlink($f . '-wal'); @unlink($f . '-shm');
            continue;
        }
        // A book no second device ever joined, YOUNG_DAYS after it was made, goes with what it holds (C1).
        try {
            $book = basename($f, '.sqlite');
            $bdb = preg_match('/^[0-9a-f]{32}$/', $book) ? open_book($store, $book, false) : null;
            if ($bdb !== null) {
                $born = (int)(meta($bdb, 'created') ?? 0);
                $few = (int)one($bdb, 'SELECT COUNT(*) FROM devices') < 2;
                $bdb = null;
                if ($few && $born > 0 && $now - $born > YOUNG_DAYS * 86400000) { @unlink($f); @unlink($f . '-wal'); @unlink($f . '-shm'); }
            }
        } catch (Throwable $e) {
            error_log('rk-relay young sweep: ' . $e->getMessage());
        }
    }
    // Waris vaults are never a book's: a vault with no owner or heir call for WARIS_IDLE_DAYS goes (every call
    // writes the vault's file, so its time is the last call's).
    foreach (glob($store . '/waris/w-*.sqlite') ?: [] as $f) {
        $m = @filemtime($f);
        if ($m !== false && ($now / 1000) - $m > WARIS_IDLE_DAYS * 86400) w_drop($store, substr(basename($f, '.sqlite'), 2));
    }
    // And a vault still bare WARIS_JUNK_DAYS DAYs after its setup — no heir, no snapshot — is junk (7 Nov 2026, R1:
    // anyone may make one). Only the bare marks are read; each vault is looked at before it goes.
    foreach (glob($store . '/waris/bare/*') ?: [] as $mark) {
        $m = @filemtime($mark);
        if ($m === false || $now - $m * 1000 < WARIS_JUNK_DAYS * WARIS_DAY_MS) continue;
        $vault = basename($mark);
        try {
            $db = preg_match('/^[0-9a-f]{32}$/', $vault) ? w_open($store, $vault, false) : null;
            $bare = $db !== null && meta($db, 'snap_v') === null && (int)one($db, 'SELECT COUNT(*) FROM heirs') === 0;
            $db = null;
            if ($bare) w_drop($store, $vault); else @unlink($mark);
        } catch (Throwable $e) {
            error_log('rk-relay waris sweep: ' . $e->getMessage());
        }
    }
    store_bytes($store, true);
    waris_bytes($store, true);
}

/**
 * New books in the last 24 hours, each marked by an empty file in $store/new when it was made (a book's own
 * file's ctime moves with every write, so it counted every busy book as new). Older marks go.
 */
function new_books_today($store) {
    $dir = $store . '/new';
    if (!is_dir($dir)) @mkdir($dir, 0700, true);
    $n = 0;
    foreach (glob($dir . '/*') ?: [] as $f) {
        $m = @filemtime($f);
        if ($m === false) continue;
        if (time() - $m < 86400) $n++; else @unlink($f);
    }
    return $n;
}

// ---------------------------------------------------------------- Waris ki Chaabi (heir access, 4 Nov 2026)

/** A vault's SQLite in $store/waris/ (the books' sweeps and counts read only $store/*.sqlite), made only when $create. */
function w_open($store, $vault, $create) {
    $dir = $store . '/waris';
    if (!is_dir($dir)) @mkdir($dir, 0700, true);
    $file = $dir . '/w-' . $vault . '.sqlite';
    if (!$create && !file_exists($file)) return null;
    $db = new PDO('sqlite:' . $file, null, null, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_TIMEOUT => 10]);
    $db->exec('PRAGMA journal_mode = WAL');
    $db->exec('PRAGMA synchronous = NORMAL');
    $db->exec('CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v TEXT NOT NULL)');
    $db->exec('CREATE TABLE IF NOT EXISTS heirs (hid TEXT PRIMARY KEY, pass TEXT NOT NULL, box TEXT NOT NULL,
        wait INTEGER NOT NULL, last_seen INTEGER, asked_at INTEGER, rejected_at INTEGER, allowed_at INTEGER)');
    $db->exec('CREATE TABLE IF NOT EXISTS chunks (v INTEGER NOT NULL, i INTEGER NOT NULL, n INTEGER NOT NULL,
        body BLOB NOT NULL, PRIMARY KEY (v, i))');
    return $db;
}

/**
 * A vault's mark (7 Nov 2026, R1), an empty file named by the vault: waris/new/ (made at its setup: the day's count of
 * new vaults, as new_books_today) and waris/bare/ (no heir and no snapshot yet: the junk sweep's list, dated from the
 * setup). Both go with the vault.
 */
function w_mark($store, $kind, $vault) {
    $dir = $store . '/waris/' . $kind;
    if (!is_dir($dir)) @mkdir($dir, 0700, true);
    return $dir . '/' . $vault;
}

/** Vaults made in the last 24 hours that are still here (older marks go). */
function new_vaults_today($store) {
    $n = 0;
    foreach (glob($store . '/waris/new/*') ?: [] as $f) {
        $m = @filemtime($f);
        if ($m === false) continue;
        if (time() - $m < 86400) $n++; else @unlink($f);
    }
    return $n;
}

/** A vault goes: its files and its marks. */
function w_drop($store, $vault) {
    $f = $store . '/waris/w-' . $vault . '.sqlite';
    @unlink($f); @unlink($f . '-wal'); @unlink($f . '-shm');
    @unlink($store . '/waris/new/' . $vault);
    @unlink($store . '/waris/bare/' . $vault);
}

/**
 * Whether the disk may take $len more bytes of a vault: it keeps MIN_FREE and all the books may still grow to
 * (MAX_STORE_BYTES), so a vault never takes the books' room (7 Nov 2026, R1).
 */
function w_disk($store, $len) {
    $free = @disk_free_space($store);
    return $free === false || $free - $len >= MIN_FREE + max(0, MAX_STORE_BYTES - store_bytes($store));
}

function w_int_or_null($x) { return $x === null ? null : (int)$x; }

/** openAt: allowedAt if set, else askedAt + wait x DAY if asked, else null. */
function w_open_at($h) {
    if ($h['allowed_at'] !== null) return (int)$h['allowed_at'];
    if ($h['asked_at'] !== null) return (int)$h['asked_at'] + (int)$h['wait'] * WARIS_DAY_MS;
    return null;
}
function w_is_open($h, $now) {
    if ($h['allowed_at'] !== null) return true;
    return $h['asked_at'] !== null && $now >= (int)$h['asked_at'] + (int)$h['wait'] * WARIS_DAY_MS;
}

/** The current complete snapshot, or null. */
function w_snap($db) {
    $v = meta($db, 'snap_v');
    if ($v === null) return null;
    return ['v' => (int)$v, 'n' => (int)meta($db, 'snap_n'), 'bytes' => (int)meta($db, 'snap_bytes'), 'at' => (int)meta($db, 'snap_at')];
}

/** A small JSON body (at most $cap bytes), or null. */
function w_json_in($cap) {
    $len = isset($_SERVER['CONTENT_LENGTH']) ? (int)$_SERVER['CONTENT_LENGTH'] : -1;
    if ($len > $cap) return null;
    $raw = file_get_contents('php://input', false, null, 0, $cap + 1);
    if ($raw === false || strlen($raw) > $cap) return null;
    $o = json_decode($raw, true);
    return is_array($o) ? $o : null;
}

/** A query parameter that must be a plain non-negative integer, else null. */
function w_q($name) {
    $v = $_GET[$name] ?? null;
    return (is_string($v) && $v !== '' && strlen($v) <= 12 && ctype_digit($v)) ? (int)$v : null;
}

/** The 12 Waris routes (WARIS protocol v1) under /v1/w/<vault>. Every answer ends the request. */
function waris($store, $rest) {
    $method = $_SERVER['REQUEST_METHOD'] ?? '';
    if (!preg_match('#^/([^/]+)(?:/(.*))?$#', $rest, $m)) refuse(404, 'no-such-path');
    $vault = $m[1];
    $tail = rtrim($m[2] ?? '', '/');
    $hid = null;
    $routes = [
        '' => ['DELETE' => 'vault-delete'], 'setup' => ['POST' => 'setup'], 'snap' => ['PUT' => 'snap'],
        'status' => ['GET' => 'status'],
    ];
    if (isset($routes[$tail])) {
        $byMethod = $routes[$tail];
    } elseif (preg_match('#^heir/([^/]+)(?:/(reject|allow|ask|alive|open|snap))?$#', $tail, $hm)) {
        $hid = $hm[1];
        $sub = $hm[2] ?? '';
        $byMethod = $sub === '' ? ['PUT' => 'heir-put', 'DELETE' => 'heir-delete']
            : [($sub === 'open' || $sub === 'snap') ? 'GET' : 'POST' => ($sub === 'snap' ? 'heir-snap' : $sub)];
    } else {
        refuse(404, 'no-such-path');
    }
    if (!isset($byMethod[$method])) refuse(405, 'method');
    $route = $byMethod[$method];
    if (!preg_match('/^[0-9a-f]{32}$/', $vault)) refuse(400, 'bad');
    if ($hid !== null && !preg_match('/^[0-9a-f]{16}$/', $hid)) refuse(400, 'bad');
    $token = trim(header_value('X-RK-Pass'));
    if ($token === '') {
        $auth = header_value('Authorization');
        if (strncmp($auth, 'RK ', 3) === 0) $token = trim(substr($auth, 3));
    }
    if (!preg_match('/^[0-9a-f]{64}$/', $token)) refuse(403, 'pass');
    $hash = hash('sha256', $token);
    $now = (int)floor(microtime(true) * 1000);
    $heirRoute = in_array($route, ['ask', 'alive', 'open', 'heir-snap'], true);

    $db = null;
    try {
        $db = w_open($store, $vault, false);
        if ($db === null) {
            if ($route !== 'setup') refuse(404, 'unknown');
            // A new vault, as a new book: while the server holds fewer than MAX_VAULTS, the vaults' own bytes have room
            // (never the books', 7 Nov 2026, R1), and fewer than NEW_VAULTS_DAY were made in 24 hours (anyone may make one).
            if (count(glob($store . '/waris/w-*.sqlite') ?: []) >= MAX_VAULTS) refuse(507, 'full');
            if (waris_bytes($store) >= WARIS_BYTES || !w_disk($store, 0)) refuse(507, 'waris-full');
            if (new_vaults_today($store) >= NEW_VAULTS_DAY) refuse(429, 'waris-day');
            $db = w_open($store, $vault, true);
            // Made today (the day's count) and bare until an heir or a snapshot comes (the junk sweep's list): marked as
            // the file is made, so even a setup cut short leaves nothing the sweep cannot find.
            @touch(w_mark($store, 'new', $vault));
            @touch(w_mark($store, 'bare', $vault));
        }
        $db->exec('BEGIN IMMEDIATE');
        $no = function ($code, $body) use ($db) {
            try { $db->exec('ROLLBACK'); } catch (Throwable $_) {}
            answer($code, ['ok' => false] + $body);
        };
        $done = function ($body, $code = 200) use ($db) {
            $db->exec('COMMIT');
            answer($code, ['ok' => true] + $body);
        };
        $owner = meta($db, 'pass');
        if ($route === 'setup') {
            if ($owner === null) {
                set_meta($db, 'pass', $hash);
                set_meta($db, 'created', $now);
                set_meta($db, 'last_owner', $now);
                $done(['created' => true]);
            }
            if (!hash_equals($owner, $hash)) $no(403, ['why' => 'pass']);
            set_meta($db, 'last_owner', $now);
            $done(['created' => false]);
        }
        // A file left by a setup that never finished is no vault.
        if ($owner === null) $no(404, ['why' => 'unknown']);
        $heir = null;
        if ($hid !== null) {
            $s = $db->prepare('SELECT * FROM heirs WHERE hid = ?');
            $s->execute([$hid]);
            $heir = $s->fetch(PDO::FETCH_ASSOC) ?: null;
        }
        if ($heirRoute) {
            if ($heir === null) $no(404, ['why' => 'unknown']);
            if (!hash_equals($heir['pass'], $hash)) $no(403, ['why' => 'pass']);
            // Every heir call: that heir's lastSeen.
            $db->prepare('UPDATE heirs SET last_seen = ? WHERE hid = ?')->execute([$now, $hid]);
            $heir['last_seen'] = $now;
        } else {
            if (!hash_equals($owner, $hash)) $no(403, ['why' => 'pass']);
            if ($hid !== null && $heir === null && $route !== 'heir-put') $no(404, ['why' => 'unknown']);
            set_meta($db, 'last_owner', $now);
        }

        switch ($route) {
            case 'snap': {
                $v = w_q('v'); $i = w_q('i'); $n = w_q('n');
                if ($v === null || $i === null || $n === null || $v < 1 || $n < 1 || $n > WARIS_MAX_N || $i >= $n) $no(400, ['why' => 'bad']);
                $len = isset($_SERVER['CONTENT_LENGTH']) ? (int)$_SERVER['CONTENT_LENGTH'] : -1;
                if ($len > WARIS_CHUNK) $no(413, ['why' => 'too-big']);
                $body = file_get_contents('php://input', false, null, 0, WARIS_CHUNK + 1);
                if ($body === false || $body === '') $no(400, ['why' => 'bad']);
                $len = strlen($body);
                if ($len > WARIS_CHUNK) $no(413, ['why' => 'too-big']);
                $cur = w_snap($db);
                $curV = $cur === null ? 0 : $cur['v'];
                if ($v < $curV) $no(409, ['why' => 'old']);
                if ($v === $curV && $n !== $cur['n']) $no(400, ['why' => 'bad']);
                // Any other unfinished version is abandoned (the owner sends one version at a time), and so are
                // this version's chunks of another count.
                $db->prepare('DELETE FROM chunks WHERE v != ? AND v != ?')->execute([$v, $curV]);
                $db->prepare('DELETE FROM chunks WHERE v = ? AND n != ?')->execute([$v, $n]);
                $mine = (int)one($db, 'SELECT COALESCE(SUM(length(body)), 0) FROM chunks WHERE v = ? AND i != ?', [$v, $i]);
                if ($mine + $len > WARIS_SNAP) $no(413, ['why' => 'too-big']);
                $all = (int)one($db, 'SELECT COALESCE(SUM(length(body)), 0) FROM chunks WHERE NOT (v = ? AND i = ?)', [$v, $i]);
                if ($all + $len > 2 * WARIS_SNAP) $no(413, ['why' => 'too-big']);
                // The vaults' own bytes, never the books' (7 Nov 2026, R1).
                if (waris_bytes($store) + $len > WARIS_BYTES || !w_disk($store, $len)) $no(507, ['why' => 'waris-full']);
                $s = $db->prepare('INSERT OR REPLACE INTO chunks (v, i, n, body) VALUES (?, ?, ?, ?)');
                $s->bindValue(1, $v, PDO::PARAM_INT);
                $s->bindValue(2, $i, PDO::PARAM_INT);
                $s->bindValue(3, $n, PDO::PARAM_INT);
                $s->bindValue(4, $body, PDO::PARAM_LOB);
                $s->execute();
                $complete = (int)one($db, 'SELECT COUNT(*) FROM chunks WHERE v = ?', [$v]) === $n;
                if ($complete) {
                    set_meta($db, 'snap_v', $v);
                    set_meta($db, 'snap_n', $n);
                    set_meta($db, 'snap_bytes', (int)one($db, 'SELECT SUM(length(body)) FROM chunks WHERE v = ?', [$v]));
                    set_meta($db, 'snap_at', $now);
                    $db->prepare('DELETE FROM chunks WHERE v != ?')->execute([$v]);
                }
                $db->exec('COMMIT');
                waris_grew($store, $len);
                // A snapshot: the vault is no longer bare (an unfinished version is none).
                if ($complete) @unlink($store . '/waris/bare/' . $vault);
                answer(200, ['ok' => true, 'complete' => $complete]);
            }
            case 'heir-put': {
                $o = w_json_in(16384);
                $ph = is_array($o) ? ($o['passHash'] ?? null) : null;
                $box = is_array($o) ? ($o['box'] ?? null) : null;
                $wait = is_array($o) ? ($o['wait'] ?? null) : null;
                if (!is_string($ph) || !preg_match('/^[0-9a-f]{64}$/', $ph)
                    || !is_string($box) || $box === '' || strlen($box) > WARIS_BOX || !preg_match('#^[A-Za-z0-9+/]+={0,2}$#', $box)
                    || !is_int($wait) || $wait < 1 || $wait > 90) $no(400, ['why' => 'bad']);
                if ($heir === null) {
                    if ((int)one($db, 'SELECT COUNT(*) FROM heirs') >= WARIS_HEIRS) $no(409, ['why' => 'full']);
                    $db->prepare('INSERT INTO heirs (hid, pass, box, wait) VALUES (?, ?, ?, ?)')->execute([$hid, $ph, $box, $wait]);
                } elseif (hash_equals($heir['pass'], $ph)) {
                    $db->prepare('UPDATE heirs SET box = ?, wait = ? WHERE hid = ?')->execute([$box, $wait, $hid]);
                } else {
                    $db->prepare('UPDATE heirs SET pass = ?, box = ?, wait = ?, last_seen = NULL, asked_at = NULL,
                        rejected_at = NULL, allowed_at = NULL WHERE hid = ?')->execute([$ph, $box, $wait, $hid]);
                }
                $db->exec('COMMIT');
                // An heir: the vault is no longer bare.
                @unlink($store . '/waris/bare/' . $vault);
                answer(200, ['ok' => true]);
            }
            case 'heir-delete': {
                $db->prepare('DELETE FROM heirs WHERE hid = ?')->execute([$hid]);
                // Its last heir gone and no snapshot: bare again, dated from its setup (the junk sweep's rule).
                $bare = (int)one($db, 'SELECT COUNT(*) FROM heirs') === 0 && meta($db, 'snap_v') === null;
                $created = (int)(meta($db, 'created') ?? $now);
                $db->exec('COMMIT');
                if ($bare) @touch(w_mark($store, 'bare', $vault), intdiv($created, 1000));
                answer(200, ['ok' => true]);
            }
            case 'status': {
                $rows = $db->query('SELECT * FROM heirs ORDER BY hid')->fetchAll(PDO::FETCH_ASSOC);
                $heirs = array_map(function ($h) {
                    return ['hid' => $h['hid'], 'wait' => (int)$h['wait'], 'lastSeen' => w_int_or_null($h['last_seen']),
                        'askedAt' => w_int_or_null($h['asked_at']), 'rejectedAt' => w_int_or_null($h['rejected_at']),
                        'allowedAt' => w_int_or_null($h['allowed_at']), 'openAt' => w_open_at($h)];
                }, $rows);
                $done(['now' => $now, 'snap' => w_snap($db), 'heirs' => $heirs]);
            }
            case 'reject': {
                $db->prepare('UPDATE heirs SET asked_at = NULL, allowed_at = NULL, rejected_at = ? WHERE hid = ?')->execute([$now, $hid]);
                $done([]);
            }
            case 'allow': {
                $db->prepare('UPDATE heirs SET allowed_at = ?, asked_at = COALESCE(asked_at, ?) WHERE hid = ?')->execute([$now, $now, $hid]);
                $done([]);
            }
            case 'ask': {
                if ($heir['asked_at'] === null) {
                    if ($heir['rejected_at'] !== null && $now - (int)$heir['rejected_at'] < WARIS_DAY_MS) {
                        // The heir's lastSeen is still written: a refused ask is a sign of life too.
                        $db->exec('COMMIT');
                        answer(429, ['ok' => false, 'why' => 'wait', 'retryAt' => (int)$heir['rejected_at'] + WARIS_DAY_MS]);
                    }
                    $db->prepare('UPDATE heirs SET asked_at = ? WHERE hid = ?')->execute([$now, $hid]);
                    $heir['asked_at'] = $now;
                }
                $done(['askedAt' => (int)$heir['asked_at'], 'openAt' => w_open_at($heir)]);
            }
            case 'alive': {
                $done(['askedAt' => w_int_or_null($heir['asked_at']), 'openAt' => w_open_at($heir),
                    'rejectedAt' => w_int_or_null($heir['rejected_at']), 'open' => w_is_open($heir, $now)]);
            }
            case 'open': {
                if (!w_is_open($heir, $now)) {
                    $db->exec('COMMIT');
                    answer(403, ['ok' => false, 'why' => 'waiting', 'openAt' => w_open_at($heir)]);
                }
                $done(['box' => $heir['box'], 'snap' => w_snap($db)]);
            }
            case 'heir-snap': {
                if (!w_is_open($heir, $now)) {
                    $db->exec('COMMIT');
                    answer(403, ['ok' => false, 'why' => 'waiting', 'openAt' => w_open_at($heir)]);
                }
                $v = w_q('v'); $i = w_q('i');
                if ($v === null || $i === null) { $db->exec('COMMIT'); answer(400, ['ok' => false, 'why' => 'bad']); }
                $cur = w_snap($db);
                $body = false;
                if ($cur !== null && $cur['v'] === $v) $body = one($db, 'SELECT body FROM chunks WHERE v = ? AND i = ?', [$v, $i]);
                $db->exec('COMMIT');
                if ($body === false || $body === null) answer(404, ['ok' => false, 'why' => 'absent']);
                if (is_resource($body)) $body = stream_get_contents($body);
                http_response_code(200);
                header('Content-Type: application/octet-stream');
                header('Content-Length: ' . strlen($body));
                echo $body;
                exit;
            }
            case 'vault-delete': {
                $db->exec('COMMIT');
                $db = null;
                w_drop($store, $vault);
                waris_bytes($store, true);
                answer(200, ['ok' => true]);
            }
        }
        $no(404, ['why' => 'no-such-path']);
    } catch (Throwable $e) {
        error_log('rk-relay waris: ' . $e->getMessage());
        if ($db instanceof PDO) { try { $db->exec('ROLLBACK'); } catch (Throwable $_) {} }
        refuse(500, 'server');
    }
}

$store = store_dir();
$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$halted = file_exists($store . '/halt');

if (preg_match('#/v1/health$#', $path)) {
    sweep_idle($store, (int)floor(microtime(true) * 1000));
    // 'push': this relay sends no push itself (R70: said, as cloud/worker says whether it can).
    answer(200, ['ok' => true, 'live' => !$halted, 'push' => false]);
}
if ($halted) refuse(503, 'halted');
if (preg_match('#/v1/w(/.*)?$#', $path, $wm)) waris($store, $wm[1] ?? '');
if (!preg_match('#/v1/b/([0-9a-f]{32})/(hello|send|take|ack|wake|leave|round)$#', $path, $m)) refuse(404, 'no-such-path');
$book = $m[1];
$action = $m[2];

$token = trim(header_value('X-RK-Pass'));
if ($token === '') {
    $auth = header_value('Authorization');
    if (strncmp($auth, 'RK ', 3) === 0) $token = trim(substr($auth, 3));
}
if (!preg_match('/^[0-9a-f]{64}$/', $token)) refuse(401, 'no-pass');
$device = header_value('X-Device');
if (!preg_match('/^[0-9a-f]{16}$/', $device)) refuse(400, 'no-device');
$want = $action === 'take' ? 'GET' : 'POST';
if (($_SERVER['REQUEST_METHOD'] ?? '') !== $want) refuse(405, 'method');

$now = (int)floor(microtime(true) * 1000);
$pass = hash('sha256', $token);

try {
    $db = open_book($store, $book, false);
    if ($db === null) {
        // The book's first device sets the pass, and only by a hello (never by a stray parcel).
        if ($action !== 'hello') refuse(403, 'say-hello-first');
        $all = glob($store . '/*.sqlite') ?: [];
        // New books wait once the store holds half its bytes (C1): the other half stays for the shops already syncing.
        if (count($all) >= MAX_BOOKS || store_bytes($store) >= intdiv(MAX_STORE_BYTES, 2)) {
            // Idle books are let go first; only a store still full refuses.
            sweep_idle($store, $now, true);
            $all = glob($store . '/*.sqlite') ?: [];
            if (count($all) >= MAX_BOOKS || store_bytes($store) >= intdiv(MAX_STORE_BYTES, 2)) refuse(507, 'full');
        }
        $free = @disk_free_space($store);
        if ($free !== false && $free < MIN_FREE) refuse(507, 'full');
        // New books in a day, all together: a stranger opening books at will meets a limit (22 Oct 2026).
        if (new_books_today($store) >= NEW_BOOKS_DAY) refuse(429, 'new-books');
        $db = open_book($store, $book, true);
        @touch($store . '/new/' . $book);
    }
    $db->exec('BEGIN IMMEDIATE');
    $known = meta($db, 'pass');
    if ($known === null) {
        if ($action !== 'hello') { $db->exec('ROLLBACK'); refuse(403, 'say-hello-first'); }
        set_meta($db, 'pass', $pass);
        set_meta($db, 'created', $now);
    } elseif (!hash_equals($known, $pass)) {
        $db->exec('ROLLBACK');
        refuse(403, 'wrong-pass');
    }
    $isKnown = one($db, 'SELECT 1 FROM devices WHERE device = ?', [$device]) !== false;
    if (!$isKnown && $action !== 'hello') { $db->exec('ROLLBACK'); refuse(403, 'say-hello-first'); }
    prune_old($db, $now, $device);

    switch ($action) {
        case 'hello': {
            $count = (int)one($db, 'SELECT COUNT(*) FROM devices');
            if (!$isKnown && $count >= MAX_DEVICES) { $db->exec('ROLLBACK'); refuse(409, 'too-many-devices'); }
            $top = (int)(one($db, 'SELECT MAX(seq) FROM parcels') ?: 0);
            $db->prepare('INSERT INTO devices (device, acked, seen) VALUES (?, 0, ?)
                ON CONFLICT(device) DO UPDATE SET seen = excluded.seen')->execute([$device, $now]);
            $s = $db->prepare('SELECT device, seen FROM devices WHERE device != ?');
            $s->execute([$device]);
            $others = array_map(function ($r) { return ['device' => $r['device'], 'seen' => (int)$r['seen']]; },
                $s->fetchAll(PDO::FETCH_ASSOC));
            $db->exec('COMMIT');
            answer(200, ['ok' => true, 'top' => $top, 'others' => $others]);
        }
        case 'send': {
            $len = isset($_SERVER['CONTENT_LENGTH']) ? (int)$_SERVER['CONTENT_LENGTH'] : -1;
            if ($len > MAX_PARCEL) { $db->exec('ROLLBACK'); refuse(413, 'too-big'); }
            $body = file_get_contents('php://input', false, null, 0, MAX_PARCEL + 1);
            $r = put_parcel($db, $store, $device, $now, $body);
            if (is_array($r)) { $db->exec('ROLLBACK'); refuse($r[0], $r[1]); }
            $db->exec('COMMIT');
            answer(200, ['ok' => true, 'seq' => $r]);
        }
        case 'take': {
            $page = take_page($db, $device, $now, max(0, (int)($_GET['after'] ?? 0)));
            $db->exec('COMMIT');
            answer(200, ['ok' => true] + $page);
        }
        case 'ack': {
            $o = json_decode(file_get_contents('php://input', false, null, 0, 4096), true);
            $upTo = is_array($o) && isset($o['upTo']) && is_numeric($o['upTo']) ? (int)$o['upTo'] : -1;
            if ($upTo < 0) { $db->exec('ROLLBACK'); refuse(400, 'bad-ack'); }
            $db->prepare('UPDATE devices SET acked = MAX(acked, ?) WHERE device = ?')->execute([$upTo, $device]);
            touch_seen($db, $device, $now, false);
            prune_taken($db);
            $db->exec('COMMIT');
            answer(200, ['ok' => true]);
        }
        case 'round': {
            // One request for a whole round (22 Oct 2026): the acknowledgement, this device's parcel, the others' page.
            $len = isset($_SERVER['CONTENT_LENGTH']) ? (int)$_SERVER['CONTENT_LENGTH'] : -1;
            if ($len > MAX_PARCEL * 2) { $db->exec('ROLLBACK'); refuse(413, 'too-big'); }
            $o = json_decode(file_get_contents('php://input', false, null, 0, MAX_PARCEL * 2), true);
            if (!is_array($o)) { $db->exec('ROLLBACK'); refuse(400, 'bad-round'); }
            $after = isset($o['after']) && is_numeric($o['after']) ? max(0, (int)$o['after']) : 0;
            if (isset($o['upTo']) && $o['upTo'] !== null) {
                if (!is_numeric($o['upTo']) || (int)$o['upTo'] < 0) { $db->exec('ROLLBACK'); refuse(400, 'bad-ack'); }
                $db->prepare('UPDATE devices SET acked = MAX(acked, ?) WHERE device = ?')->execute([(int)$o['upTo'], $device]);
                prune_taken($db);
            }
            $seq = null;
            if (isset($o['parcel']) && is_string($o['parcel']) && $o['parcel'] !== '') {
                $body = base64_decode($o['parcel'], true);
                if ($body === false) { $db->exec('ROLLBACK'); refuse(400, 'bad-parcel'); }
                $r = put_parcel($db, $store, $device, $now, $body);
                if (is_array($r) && !capacity($r[1])) { $db->exec('ROLLBACK'); refuse($r[0], $r[1]); }
                if (is_array($r)) {
                    // The book's limit refuses the parcel, not the round (23 Oct 2026, the review): the ack above
                    // stays and the others' page still comes, under the refusal's status.
                    $page = take_page($db, $device, $now, $after);
                    $db->exec('COMMIT');
                    answer($r[0], ['ok' => false, 'why' => $r[1], 'seq' => null] + $page);
                }
                $seq = $r;
            }
            $page = take_page($db, $device, $now, $after);
            $db->exec('COMMIT');
            answer(200, ['ok' => true, 'seq' => $seq] + $page);
        }
        case 'wake': {
            // Kept like cloud/worker's, so a device may say it; this relay sends no push itself.
            $o = json_decode(file_get_contents('php://input', false, null, 0, 8192), true);
            $t = is_array($o) && isset($o['token']) && is_string($o['token'])
                && strlen($o['token']) > 20 && strlen($o['token']) < 4096 ? $o['token'] : null;
            $db->prepare('UPDATE devices SET fcm = ?, seen = ? WHERE device = ?')->execute([$t, $now, $device]);
            $db->exec('COMMIT');
            answer(200, ['ok' => true, 'wake' => $t !== null, 'push' => false]);
        }
        case 'leave': {
            $db->prepare('DELETE FROM devices WHERE device = ?')->execute([$device]);
            $db->prepare('DELETE FROM parcels WHERE sender = ?')->execute([$device]);
            $left = (int)one($db, 'SELECT COUNT(*) FROM devices');
            if ($left > 0) prune_taken($db);
            $db->exec('COMMIT');
            if ($left === 0) {
                $db = null;
                $f = $store . '/' . $book . '.sqlite';
                @unlink($f); @unlink($f . '-wal'); @unlink($f . '-shm');
            }
            answer(200, ['ok' => true]);
        }
    }
    refuse(404, 'no-such-path');
} catch (Throwable $e) {
    error_log('rk-relay: ' . $e->getMessage());
    // The transaction was opened by hand (BEGIN IMMEDIATE), so PDO does not know of it: rolled back by hand.
    if (isset($db) && $db instanceof PDO) { try { $db->exec('ROLLBACK'); } catch (Throwable $_) {} }
    refuse(500, 'server');
}
