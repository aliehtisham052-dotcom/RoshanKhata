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

/** Parcels older than KEEP_DAYS go, and the line past which they went is kept (the take answer's "gap"). */
function prune_old($db, $now) {
    $cut = one($db, 'SELECT MAX(seq) FROM parcels WHERE at < ?', [$now - KEEP_DAYS * 86400000]);
    if ($cut !== null && $cut !== false) {
        $db->prepare('DELETE FROM parcels WHERE seq <= ?')->execute([(int)$cut]);
        if ((int)$cut > (int)(meta($db, 'pruned_past') ?? 0)) set_meta($db, 'pruned_past', (int)$cut);
    }
}

/** A quiet call writes "seen" at most once an hour; one that did something, always. */
function touch_seen($db, $device, $now, $always) {
    if ($always) $db->prepare('UPDATE devices SET seen = ? WHERE device = ?')->execute([$now, $device]);
    else $db->prepare('UPDATE devices SET seen = ? WHERE device = ? AND seen < ?')->execute([$now, $device, $now - SEEN_EVERY_MS]);
}

/** One parcel in: its number, or a refusal [code, why]. */
function put_parcel($db, $store, $device, $now, $body) {
    if ($body === '' || $body === false) return [400, 'empty'];
    if (strlen($body) > MAX_PARCEL) return [413, 'too-big'];
    $free = @disk_free_space($store);
    if ($free !== false && $free < MIN_FREE) return [507, 'full'];
    $key = 'count:' . gmdate('Y-m-d', intdiv($now, 1000));
    $count = (int)(meta($db, $key) ?? 0);
    if ($count >= DAY_LIMIT) return [429, 'day-limit'];
    $db->prepare("DELETE FROM meta WHERE k LIKE 'count:%' AND k != ?")->execute([$key]);
    set_meta($db, $key, $count + 1);
    $s = $db->prepare('INSERT INTO parcels (sender, at, body) VALUES (?, ?, ?)');
    $s->bindValue(1, $device);
    $s->bindValue(2, $now, PDO::PARAM_INT);
    $s->bindValue(3, $body, PDO::PARAM_LOB);
    $s->execute();
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
    return ['parcels' => $page, 'more' => count($rows) > count($page), 'gap' => $after < $prunedPast];
}

/** Now and then (one request in a hundred): books nobody touched for IDLE_DAYS are let go. */
function sweep_idle($store, $now) {
    if (mt_rand(1, 100) !== 1) return;
    foreach (glob($store . '/*.sqlite') ?: [] as $f) {
        $m = @filemtime($f);
        if ($m !== false && ($now / 1000) - $m > IDLE_DAYS * 86400) {
            @unlink($f); @unlink($f . '-wal'); @unlink($f . '-shm');
        }
    }
}

$store = store_dir();
$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$halted = file_exists($store . '/halt');

if (preg_match('#/v1/health$#', $path)) answer(200, ['ok' => true, 'live' => !$halted]);
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
        if (count($all) >= MAX_BOOKS) refuse(507, 'full');
        $free = @disk_free_space($store);
        if ($free !== false && $free < MIN_FREE) refuse(507, 'full');
        // New books in a day, all together: a stranger opening books at will meets a limit (22 Oct 2026).
        $fresh = 0;
        foreach ($all as $f) { $c = @filectime($f); if ($c !== false && time() - $c < 86400) $fresh++; }
        if ($fresh >= NEW_BOOKS_DAY) refuse(429, 'new-books');
        $db = open_book($store, $book, true);
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
    prune_old($db, $now);

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
            sweep_idle($store, $now);
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
                if (is_array($r)) { $db->exec('ROLLBACK'); refuse($r[0], $r[1]); }
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
            answer(200, ['ok' => true, 'wake' => $t !== null]);
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
