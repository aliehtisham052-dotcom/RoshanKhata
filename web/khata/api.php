<?php
/*
 * Roshan Khata — a customer's own page, the shop's side (22 Oct 2026;
 * hardened the same day after the review).
 *
 * The app sends, every few minutes and only when something changed, the
 * sealed pages of the customers the shop gave a link to. Each page is sealed
 * on the phone with a key that is only in the customer's link (after the #,
 * which no browser sends to a server): this server stores bytes it cannot
 * read, as ready files the customer's browser fetches and opens itself.
 *
 * Checks and balances:
 *  - A shop is known by its id and its pass (the SHA-256 of its token, set by
 *    the shop's first request). A page belongs to the shop that first wrote
 *    it; which pages are whose is kept here, never in the page itself.
 *  - A page taken back leaves a stub ("!gone") that no shop can take again;
 *    pages not refreshed for EXPIRE_DAYS go the same way (the app refreshes
 *    every page monthly), and stubs are let go after EXPIRE_DAYS more.
 *  - One copy of the book writes a shop's pages (its writer); another copy
 *    is refused until it claims them (the app asks the owner first).
 *  - Limits per shop (pages, requests a day) and for the whole server (shops,
 *    files, new shops an hour, free disk). Nothing is written unless it can
 *    be written whole: a failed write keeps the old page and answers an error.
 *  - Only the app's own requests: JSON with its content type (so a web page
 *    elsewhere cannot make a visitor's browser post here), at most MAX_BODY.
 *  - One request at a time (one lock for the whole store): small shops,
 *    small requests, and no two requests can ever take the same new page.
 *
 * The owner's switches, as empty files: data/halt (or <store>/halt) stops
 * every request; <store>/closed refuses new shops while the others go on.
 */

ini_set('display_errors', '0');
ini_set('log_errors', '1');
error_reporting(E_ALL);

/** A limit, from the environment where the server sets one (the tests do), else its default. */
function limit($name, $default) {
    $v = getenv('RK_KHATA_' . $name);
    return ($v !== false && ctype_digit($v)) ? (int)$v : $default;
}

define('MAX_ITEMS', 200);                                   // pages put or dropped in one request
define('MAX_BLOB', 4096);                                   // characters of one sealed page
define('MAX_BODY', 1048576);                                // bytes of one request
define('MAX_LINKS', limit('MAX_LINKS', 5000));              // live pages of one shop
define('MAX_CALLS', limit('MAX_CALLS', 1500));              // requests of one shop in a UTC day
define('MAX_SHOPS', limit('MAX_SHOPS', 2000));              // shops on this server
define('MAX_FILES', limit('MAX_FILES', 100000));            // page files on this server, stubs too
define('NEW_SHOPS_HOUR', limit('NEW_SHOPS_HOUR', 20));      // new shops in one hour, all together
define('MIN_FREE', limit('MIN_FREE', 200 * 1048576));       // free bytes kept: below them nothing new is written
define('EXPIRE_DAYS', limit('EXPIRE_DAYS', 180));           // a page not refreshed this long goes
define('GONE_MARK', '!gone');                               // what a taken-back page's file holds

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');

function answer($code, $body) {
    http_response_code($code);
    echo json_encode($body);
    exit;
}
function fail($code, $why) { answer($code, ['ok' => false, 'why' => $why]); }

/**
 * Where the shops' passes and page lists live: beside the web root, never
 * served, when the hosting lets us make a folder there; else data/ here,
 * which .htaccess denies.
 */
function store_dir() {
    $env = getenv('RK_KHATA_DATA');
    if ($env !== false && $env !== '') return rtrim($env, '/');
    $root = isset($_SERVER['DOCUMENT_ROOT']) ? realpath($_SERVER['DOCUMENT_ROOT']) : false;
    if ($root) {
        $outside = dirname($root) . '/rk-khata-data';
        if (is_dir($outside) || @mkdir($outside, 0700, true)) return $outside;
    }
    return __DIR__ . '/data';
}

function read_json($path) {
    if (!file_exists($path)) return null;
    $raw = @file_get_contents($path);
    if ($raw === false) return false;
    $v = json_decode($raw, true);
    return is_array($v) ? $v : false;
}

/** [text] written whole to [path] or not at all: a temporary file of the right length, then a rename. */
function write_whole($path, $text) {
    $tmp = $path . '.tmp';
    $n = @file_put_contents($tmp, $text);
    if ($n !== strlen($text)) { @unlink($tmp); return false; }
    if (!@rename($tmp, $path)) { @unlink($tmp); return false; }
    return true;
}

/** A new page: created only if no file has that name yet (never over another's), whole or not at all. */
function create_whole($path, $text) {
    $fh = @fopen($path, 'x');
    if (!$fh) return false;
    $n = @fwrite($fh, $text);
    $ok = ($n === strlen($text)) && @fflush($fh);
    @fclose($fh);
    if (!$ok) { @unlink($path); return false; }
    return true;
}

function is_id($v) { return is_string($v) && preg_match('/^[0-9a-f]{32}$/', $v) === 1; }

/**
 * Once a UTC day: pages not refreshed for EXPIRE_DAYS become stubs, stubs
 * older than that go, shops with nothing left and no request for that long
 * go; the server's counts are taken again from what is there.
 */
function sweep($store, $pages, &$g) {
    $cut = time() - EXPIRE_DAYS * 86400;
    $shops = 0; $files = 0;
    foreach (glob("$store/s/*.json") ?: [] as $f) {
        $s = read_json($f);
        if (!is_array($s)) { $shops++; continue; }
        $changed = false;
        foreach ($s['pages'] as $id => $at) {
            if ($at < $cut && write_whole("$pages/$id.txt", GONE_MARK)) {
                unset($s['pages'][$id]); $s['gone'][$id] = time(); $changed = true;
            }
        }
        foreach ($s['gone'] as $id => $at) {
            if ($at < $cut) {
                if (file_exists("$pages/$id.txt") && !@unlink("$pages/$id.txt")) continue;
                unset($s['gone'][$id]); $changed = true;
            }
        }
        if (!$s['pages'] && !$s['gone'] && ($s['last'] ?? $s['created']) < $cut) {
            if (@unlink($f)) continue;
        }
        if ($changed) write_whole($f, json_encode($s));
        $shops++;
        $files += count($s['pages']) + count($s['gone']);
    }
    $g['shops'] = $shops;
    $g['files'] = $files;
}

// ---------------------------------------------------------------- the request

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') fail(405, 'method');
$type = $_SERVER['CONTENT_TYPE'] ?? ($_SERVER['HTTP_CONTENT_TYPE'] ?? '');
if (stripos(trim($type), 'application/json') !== 0) fail(415, 'type');

$store = store_dir();
if (file_exists(__DIR__ . '/data/halt') || file_exists("$store/halt")) fail(503, 'halted');

$raw = file_get_contents('php://input', false, null, 0, MAX_BODY + 1);
if ($raw === false || strlen($raw) > MAX_BODY) fail(413, 'too-big');
$in = json_decode($raw, true);
if (!is_array($in) || ($in !== [] && array_keys($in) === range(0, count($in) - 1))) fail(400, 'bad-json');

$shop   = $in['shop'] ?? '';
$token  = $in['token'] ?? '';
$writer = $in['writer'] ?? '';
$claim  = ($in['claim'] ?? false) === true;
$put    = $in['put'] ?? [];
$drop   = $in['drop'] ?? [];
if (!is_id($shop)) fail(400, 'shop');
if (!is_string($token) || !preg_match('/^[0-9a-f]{64}$/', $token)) fail(401, 'token');
if (!is_id($writer)) fail(400, 'writer-id');
if (!is_array($put) || !is_array($drop) || count($put) + count($drop) > MAX_ITEMS) fail(413, 'too-many');

foreach (["$store/s"] as $d) if (!is_dir($d) && !@mkdir($d, 0700, true)) fail(500, 'store');
if (!file_exists("$store/.htaccess")) @file_put_contents("$store/.htaccess", "Require all denied\n");
$lock = @fopen("$store/lock", 'c');
if (!$lock || !flock($lock, LOCK_EX)) fail(500, 'lock');

$pages = __DIR__ . '/d';
$g = read_json("$store/g.json");
if (!is_array($g)) $g = ['shops' => 0, 'files' => 0, 'hour' => '', 'fresh' => 0, 'swept' => ''];
$today = gmdate('Y-m-d');
$hour  = gmdate('Y-m-d\TH');
if ($g['swept'] !== $today) {
    // Kept at once: a stream of refused requests must not each walk the whole store again.
    sweep($store, $pages, $g); $g['swept'] = $today;
    write_whole("$store/g.json", json_encode($g));
}
$free = @disk_free_space($store);
$low  = ($free !== false && $free < MIN_FREE);

$sfile = "$store/s/$shop.json";
$s = read_json($sfile);
if ($s === false) fail(500, 'record');               // there but unreadable: never taken as a new shop
$pass = hash('sha256', $token);
$isNew = ($s === null);
if ($isNew) {
    if (file_exists("$store/closed")) fail(403, 'closed');
    if ($low) fail(507, 'disk');
    if ($g['shops'] >= MAX_SHOPS) fail(503, 'full');
    if ($g['hour'] !== $hour) { $g['hour'] = $hour; $g['fresh'] = 0; }
    if ($g['fresh'] >= NEW_SHOPS_HOUR) fail(429, 'busy');
    $s = ['pass' => $pass, 'writer' => $writer, 'created' => time(), 'last' => time(), 'day' => '', 'calls' => 0, 'pages' => [], 'gone' => []];
} elseif (!hash_equals($s['pass'], $pass)) {
    fail(403, 'wrong-pass');
}
if (!hash_equals($s['writer'], $writer)) {
    if (!$claim) fail(409, 'writer');
    $s['writer'] = $writer;                            // this copy of the book writes the pages from now on
}
if ($s['day'] !== $today) { $s['day'] = $today; $s['calls'] = 0; }
if ($s['calls'] >= MAX_CALLS) fail(429, 'day-limit');
$s['calls']++;
$s['last'] = time();

// What is asked, checked before anything is written.
$refused = [];
$writes  = [];                                         // id => blob, for pages this shop holds
$creates = [];                                         // id => blob, for new pages
$seen    = [];
foreach ($put as $p) {
    if (!is_array($p)) { $refused[] = ['id' => '', 'why' => 'bad']; continue; }
    $id = $p['id'] ?? ''; $blob = $p['blob'] ?? '';
    if (!is_id($id)) { $refused[] = ['id' => '', 'why' => 'bad']; continue; }
    if (isset($seen[$id])) continue;
    $seen[$id] = true;
    if (!is_string($blob) || $blob === '' || strlen($blob) > MAX_BLOB || !preg_match('/^[A-Za-z0-9_-]+$/', $blob)) { $refused[] = ['id' => $id, 'why' => 'bad']; continue; }
    if (isset($s['gone'][$id])) { $refused[] = ['id' => $id, 'why' => 'gone']; continue; }
    if (isset($s['pages'][$id])) { $writes[$id] = $blob; continue; }
    if (file_exists("$pages/$id.txt")) { $refused[] = ['id' => $id, 'why' => 'taken']; continue; }
    if ($low) { $refused[] = ['id' => $id, 'why' => 'disk']; continue; }
    if (count($s['pages']) + count($creates) >= MAX_LINKS || $g['files'] + count($creates) >= MAX_FILES) { $refused[] = ['id' => $id, 'why' => 'full']; continue; }
    $creates[$id] = $blob;
}
$removes = [];                                         // this shop's pages taken back
$claims  = [];                                         // ids never written, taken back: kept so no one takes them
$removed = 0;
foreach ($drop as $id) {
    if (!is_id($id) || isset($seen[$id])) continue;
    $seen[$id] = true;
    if (isset($s['pages'][$id])) { $removes[] = $id; continue; }
    if (isset($s['gone'][$id]) || file_exists("$pages/$id.txt")) { $removed++; continue; }   // gone already, or not ours
    if (!$low && $g['files'] + count($creates) + count($claims) < MAX_FILES) $claims[] = $id; else $removed++;
}

// New pages first; each one that cannot be written whole is refused, and nothing of it is kept.
$made = [];
foreach ($creates as $id => $blob) {
    if (create_whole("$pages/$id.txt", $blob)) $made[] = $id;
    else $refused[] = ['id' => $id, 'why' => 'disk'];
}
foreach ($claims as $id) {
    if (create_whole("$pages/$id.txt", GONE_MARK)) { $s['gone'][$id] = time(); $removed++; }
    else $refused[] = ['id' => $id, 'why' => 'disk'];
}
foreach ($made as $id) $s['pages'][$id] = time();
// The shop's record before any existing page changes: if it cannot be written, the new pages go again.
if (!write_whole($sfile, json_encode($s))) {
    foreach ($made as $id) @unlink("$pages/$id.txt");
    foreach ($claims as $id) if (isset($s['gone'][$id])) @unlink("$pages/$id.txt");
    fail(507, 'disk');
}
$written = count($made);
foreach ($writes as $id => $blob) {
    if (write_whole("$pages/$id.txt", $blob)) { $s['pages'][$id] = time(); $written++; }
    else $refused[] = ['id' => $id, 'why' => 'disk'];
}
foreach ($removes as $id) {
    // The page goes; its stub stays, so no other shop can take the id. Without room for a stub, the page goes anyway.
    if (write_whole("$pages/$id.txt", GONE_MARK) || @unlink("$pages/$id.txt") || !file_exists("$pages/$id.txt")) {
        unset($s['pages'][$id]); $s['gone'][$id] = time(); $removed++;
    } else {
        $refused[] = ['id' => $id, 'why' => 'disk'];
    }
}
// The times and the stubs. Not kept: the app is told, and sends it all again (every write here can be repeated).
if (($writes || $removes) && !write_whole($sfile, json_encode($s))) fail(507, 'disk');
if ($isNew) { $g['shops']++; $g['fresh']++; }
$g['files'] += count($made) + count(array_filter($claims, function ($id) use ($s) { return isset($s['gone'][$id]); }));
write_whole("$store/g.json", json_encode($g));          // a lost count is taken again by the next day's sweep

answer(200, ['ok' => true, 'written' => $written, 'removed' => $removed, 'refused' => $refused]);
