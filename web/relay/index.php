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
define('DEVICE_IDLE_MS', limit('DEVICE_IDLE_MS', 30 * 86400000)); // a device not seen this long goes
define('SWEEP_EVERY', 6 * 3600);                                 // seconds between idle-book sweeps

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
 * Bytes of every book's files together, kept in $store/.size and counted again from the files at most every
 * ten minutes (the files are what the hosting account's quota measures).
 */
function store_bytes($store, $fresh = false) {
    $f = $store . '/.size';
    $m = @filemtime($f);
    if (!$fresh && $m !== false && time() - $m < 600) {
        $v = trim((string)@file_get_contents($f));
        if (ctype_digit($v)) return (int)$v;
    }
    $n = 0;
    foreach (glob($store . '/*.sqlite*') ?: [] as $x) { $z = @filesize($x); if ($z !== false) $n += $z; }
    @file_put_contents($f, (string)$n, LOCK_EX);
    return $n;
}

/** A parcel written: the kept total grows with it until the next count from the files. */
function store_grew($store, $len) {
    $h = @fopen($store . '/.size', 'c+');
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
    $key = 'count:' . gmdate('Y-m-d', intdiv($now, 1000));
    // "<parcels>/<bytes>" for the day (a value from before 23 Oct holds the parcels alone).
    $parts = explode('/', (string)(meta($db, $key) ?? '0'));
    $count = (int)$parts[0];
    $dayBytes = isset($parts[1]) ? (int)$parts[1] : 0;
    if ($count >= DAY_LIMIT) return [429, 'day-limit'];
    if ($dayBytes + $len > DAY_BYTES) return [429, 'day-bytes'];
    $stored = (int)one($db, 'SELECT COALESCE(SUM(length(body)), 0) FROM parcels');
    if ($stored + $len > BOOK_BYTES) return [507, 'book-full'];
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
    if (!$force && $m !== false && time() - $m < SWEEP_EVERY) return;
    @touch($mark);
    foreach (glob($store . '/*.sqlite') ?: [] as $f) {
        $m = @filemtime($f);
        if ($m !== false && ($now / 1000) - $m > IDLE_DAYS * 86400) {
            @unlink($f); @unlink($f . '-wal'); @unlink($f . '-shm');
        }
    }
    store_bytes($store, true);
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

$store = store_dir();
$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$halted = file_exists($store . '/halt');

if (preg_match('#/v1/health$#', $path)) {
    sweep_idle($store, (int)floor(microtime(true) * 1000));
    // 'push': this relay sends no push itself (R70: said, as cloud/worker says whether it can).
    answer(200, ['ok' => true, 'live' => !$halted, 'push' => false]);
}
if ($halted) refuse(503, 'halted');
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
        if (count($all) >= MAX_BOOKS || store_bytes($store) >= MAX_STORE_BYTES) {
            // Idle books are let go first; only a store still full refuses.
            sweep_idle($store, $now, true);
            $all = glob($store . '/*.sqlite') ?: [];
            if (count($all) >= MAX_BOOKS || store_bytes($store) >= MAX_STORE_BYTES) refuse(507, 'full');
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
